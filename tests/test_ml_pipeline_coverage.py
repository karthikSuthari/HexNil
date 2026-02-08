"""
Additional tests for HexNil ML Pipeline - comprehensive coverage.

Covers feature engineering, model creation, uncertainty computation,
alert generation, and edge cases in data validation.
"""

import pytest
import pandas as pd
import numpy as np
from unittest.mock import Mock, MagicMock, patch
import sys
import os

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from backend.ml_pipeline.feature_engineering import FeatureEngineer
from backend.ml_pipeline.models import (
    create_model,
    get_model_for_problem,
    get_feature_list,
    MODEL_CONFIGS,
    PROBLEM_TO_MODEL,
)
from backend.ml_pipeline.safety import (
    SafetyGate,
    predict_with_confidence,
    compute_uncertainty,
    safe_prediction_with_fallback,
    generate_uncertainty_alert,
    PredictionResult,
)
from backend.ml_pipeline.data_validation import (
    DataValidator,
    SessionQualityGate,
    ValidationResult,
    GateDecision,
    Check,
    MINIMUM_REQUIREMENTS,
)


# ---------------------------------------------------------------------------
# Feature Engineering Tests
# ---------------------------------------------------------------------------
class TestFeatureEngineerInit:
    """Test FeatureEngineer initialization."""

    def test_default_window_sizes(self):
        fe = FeatureEngineer()
        assert fe.WINDOW_1H == 3_600_000
        assert fe.WINDOW_6H == 21_600_000
        assert fe.WINDOW_24H == 86_400_000

    def test_custom_window_sizes(self):
        fe = FeatureEngineer(window_sizes={"1h": 100, "6h": 200, "24h": 300})
        assert fe.WINDOW_1H == 100
        assert fe.WINDOW_6H == 200
        assert fe.WINDOW_24H == 300

    def test_partial_custom_window_sizes(self):
        fe = FeatureEngineer(window_sizes={"1h": 999})
        assert fe.WINDOW_1H == 999
        assert fe.WINDOW_6H == 21_600_000  # default


class TestFeatureEngineerTransform:
    """Test transform method for each problem type."""

    def _make_df(self, n=100, with_screen=False, with_drain=False,
                 with_voltage=False, with_ota=False):
        data = {
            "timestamp": np.arange(0, n * 300000, 300000),
            "battery_level": np.random.randint(20, 100, n),
            "temperature": np.random.randint(250, 400, n),
        }
        if with_screen:
            data["screen_on"] = np.random.choice([0, 1], n)
        if with_drain:
            data["drain_rate_instant"] = np.random.uniform(2, 15, n)
        if with_voltage:
            data["voltage"] = np.random.randint(3500, 4200, n)
        if with_ota:
            data["is_post_update"] = [0] * (n // 2) + [1] * (n - n // 2)
        return pd.DataFrame(data)

    def test_transform_battery_drain(self):
        df = self._make_df(100, with_screen=True, with_drain=True, with_voltage=True)
        fe = FeatureEngineer()
        result = fe.transform(df, "battery_drain_detection")

        assert "hour_of_day" in result.columns
        assert "is_weekend" in result.columns
        assert "drain_rate_mean_1h" in result.columns
        assert "screen_drain_ratio" in result.columns
        assert "thermal_drain_corr_24h" in result.columns
        assert "power_save_mode" in result.columns
        assert "time_since_charge" in result.columns

    def test_transform_thermal_regression(self):
        df = self._make_df(100)
        fe = FeatureEngineer()
        result = fe.transform(df, "thermal_regression")

        assert "temp_acceleration" in result.columns
        assert "is_charging" in result.columns

    def test_transform_thermal_no_battery(self):
        """Thermal features when battery_level is absent."""
        df = pd.DataFrame({
            "timestamp": np.arange(0, 50 * 300000, 300000),
            "temperature": np.random.randint(250, 400, 50),
        })
        fe = FeatureEngineer()
        result = fe.transform(df, "thermal_regression")
        assert "is_charging" in result.columns
        assert (result["is_charging"] == 0).all()

    def test_transform_ota_update_impact(self):
        df = self._make_df(100, with_drain=True, with_ota=True)
        fe = FeatureEngineer()
        result = fe.transform(df, "ota_update_impact")

        assert "drain_rate_delta" in result.columns
        assert "temp_max_delta" in result.columns
        assert "days_since_update" in result.columns

    def test_transform_ota_no_post_update(self):
        """OTA features when is_post_update column is missing."""
        df = self._make_df(50, with_drain=True)
        fe = FeatureEngineer()
        result = fe.transform(df, "ota_update_impact")
        assert "is_post_update" in result.columns
        assert "days_since_update" in result.columns
        # All pre-update, so days_since_update=0
        assert (result["days_since_update"] == 0).all()

    def test_transform_performance_degradation(self):
        df = self._make_df(100)
        fe = FeatureEngineer()
        result = fe.transform(df, "performance_degradation")

        assert "health_percentage" in result.columns
        assert "capacity_fade" in result.columns
        assert "cycle_count" in result.columns
        assert "fast_charge_count" in result.columns
        assert "deep_discharge_count" in result.columns
        assert "days_since_first_use" in result.columns

    def test_transform_performance_no_timestamp(self):
        """Performance features with minimal columns."""
        df = pd.DataFrame({
            "timestamp": np.arange(0, 20 * 300000, 300000),
            "battery_level": np.random.randint(20, 100, 20),
        })
        fe = FeatureEngineer()
        result = fe.transform(df, "performance_degradation")
        assert "days_since_first_use" in result.columns

    def test_transform_app_misbehavior(self):
        df = self._make_df(50)
        fe = FeatureEngineer()
        result = fe.transform(df, "app_misbehavior")

        assert "drain_per_hour" in result.columns
        assert "foreground_time" in result.columns
        assert "is_system_app" in result.columns

    def test_transform_app_with_battery_percent(self):
        """App features with battery_percent_used present."""
        df = pd.DataFrame({
            "timestamp": np.arange(0, 20 * 300000, 300000),
            "battery_level": np.random.randint(20, 100, 20),
            "temperature": np.random.randint(250, 400, 20),
            "battery_percent_used": np.random.uniform(0.5, 5, 20),
            "foreground_time": np.random.randint(60, 3600, 20),
            "background_time": np.random.randint(0, 1800, 20),
        })
        fe = FeatureEngineer()
        result = fe.transform(df, "app_misbehavior")
        assert "drain_per_hour" in result.columns
        assert (result["drain_per_hour"] > 0).any()

    def test_transform_unknown_problem_type(self):
        """Unknown problem type still returns temporal + rolling features."""
        df = self._make_df(30)
        fe = FeatureEngineer()
        result = fe.transform(df, "unknown_type")
        assert "hour_of_day" in result.columns

    def test_rolling_stats_short_series(self):
        """Rolling stats with very short series."""
        df = pd.DataFrame({
            "timestamp": [0, 300000, 600000],
            "battery_level": [50, 60, 70],
            "temperature": [300, 310, 320],
        })
        fe = FeatureEngineer()
        result = fe.transform(df, "battery_drain_detection")
        assert len(result) == 3

    def test_rolling_stats_single_row(self):
        """Rolling stats with a single row."""
        df = pd.DataFrame({
            "timestamp": [0],
            "battery_level": [50],
            "temperature": [300],
        })
        fe = FeatureEngineer()
        result = fe.transform(df, "battery_drain_detection")
        assert len(result) == 1

    def test_rolling_stats_zero_interval(self):
        """Rolling stats when all timestamps are identical."""
        df = pd.DataFrame({
            "timestamp": [1000, 1000, 1000],
            "battery_level": [50, 60, 70],
            "temperature": [300, 310, 320],
            "drain_rate_instant": [5.0, 6.0, 7.0],
            "voltage": [3800, 3900, 4000],
        })
        fe = FeatureEngineer()
        result = fe.transform(df, "battery_drain_detection")
        assert "battery_level_mean_1h" in result.columns

    def test_battery_drain_no_screen_no_drain(self):
        """Battery drain features without screen_on or drain_rate_instant."""
        df = pd.DataFrame({
            "timestamp": np.arange(0, 20 * 300000, 300000),
            "battery_level": np.random.randint(20, 100, 20),
            "temperature": np.random.randint(250, 400, 20),
        })
        fe = FeatureEngineer()
        result = fe.transform(df, "battery_drain_detection")
        assert result["screen_drain_ratio"].iloc[0] == 1.0
        assert result["thermal_drain_corr_24h"].iloc[0] == 0.0


# ---------------------------------------------------------------------------
# Model Creation Tests
# ---------------------------------------------------------------------------
class TestCreateModel:
    """Test create_model function."""

    def test_create_lightgbm_model(self):
        model = create_model("battery_drain_lgbm")
        assert model is not None
        assert hasattr(model, "fit")
        assert hasattr(model, "predict")

    def test_create_xgboost_model(self):
        model = create_model("ota_impact_xgboost")
        assert model is not None
        assert hasattr(model, "fit")

    def test_create_random_forest_model(self):
        model = create_model("thermal_random_forest")
        assert model is not None
        assert hasattr(model, "fit")

    def test_create_gradient_boosting_model(self):
        model = create_model("performance_gbm_regressor")
        assert model is not None
        assert hasattr(model, "fit")

    def test_create_app_misbehavior_model(self):
        model = create_model("app_misbehavior_xgboost")
        assert model is not None

    def test_create_unknown_model(self):
        with pytest.raises(ValueError, match="Unknown model"):
            create_model("nonexistent_model")

    def test_get_feature_list_unknown(self):
        features = get_feature_list("nonexistent_problem")
        assert features == []


# ---------------------------------------------------------------------------
# Compute Uncertainty Tests
# ---------------------------------------------------------------------------
class TestComputeUncertainty:
    """Test compute_uncertainty function."""

    def test_uncertainty_sklearn_estimators(self):
        """Test with a model that has estimators_ (like sklearn RF)."""
        from sklearn.ensemble import RandomForestClassifier

        X_train = np.random.rand(50, 3)
        y_train = np.random.choice([0, 1], 50)
        model = RandomForestClassifier(n_estimators=10, random_state=42)
        model.fit(X_train, y_train)

        X_test = np.random.rand(5, 3)
        uncertainty = compute_uncertainty(model, X_test)
        assert len(uncertainty) == 5
        assert all(0 <= u <= 1 for u in uncertainty)

    def test_uncertainty_predict_proba_fallback(self):
        """Test with a model that has predict_proba but no estimators_."""
        mock_model = Mock()
        del mock_model.estimators_
        del mock_model.n_estimators
        mock_model.predict_proba.return_value = np.array([[0.3, 0.7], [0.6, 0.4]])

        X = np.random.rand(2, 3)
        uncertainty = compute_uncertainty(mock_model, X)
        assert len(uncertainty) == 2
        assert uncertainty[0] == pytest.approx(0.3)  # 1 - 0.7
        assert uncertainty[1] == pytest.approx(0.4)  # 1 - 0.6

    def test_uncertainty_no_predict_proba_fallback(self):
        """Test with a model that has neither estimators_ nor predict_proba."""
        mock_model = Mock(spec=[])  # empty spec, no attributes

        X = np.random.rand(3, 2)
        uncertainty = compute_uncertainty(mock_model, X)
        assert len(uncertainty) == 3
        assert all(u == pytest.approx(0.1) for u in uncertainty)

    def test_uncertainty_exception_fallback(self):
        """Test that exceptions return default uncertainty."""
        mock_model = Mock()
        mock_model.estimators_ = "bad_data"  # Will cause iteration error

        X = np.random.rand(4, 2)
        uncertainty = compute_uncertainty(mock_model, X)
        assert len(uncertainty) == 4
        assert all(u == pytest.approx(0.2) for u in uncertainty)


# ---------------------------------------------------------------------------
# Predict With Confidence Tests
# ---------------------------------------------------------------------------
class TestPredictWithConfidence:
    """Test predict_with_confidence function."""

    def test_regressor_model_no_proba(self):
        """Test with a model that doesn't have predict_proba."""
        mock_model = Mock(spec=["predict"])
        mock_model.predict.return_value = np.array([1.5, 2.5, 3.5])

        X = np.random.rand(3, 4)
        predictions, confidences = predict_with_confidence(mock_model, X)

        assert len(predictions) == 3
        assert len(confidences) == 3
        assert all(c == 0.75 for c in confidences)


# ---------------------------------------------------------------------------
# Safe Prediction With Fallback Tests
# ---------------------------------------------------------------------------
class TestSafePredictionFallback:
    """Test safe_prediction_with_fallback additional branches."""

    def test_fallback_with_rules(self):
        """Test FALLBACK path with deterministic rules provided."""
        mock_model = Mock()
        mock_model.predict_proba.return_value = np.array([[0.28, 0.72]])
        mock_model.predict.return_value = np.array([1])

        def mock_uncertainty(model, X):
            return np.array([0.25])

        fallback_fn = Mock(return_value=np.array([0]))

        import backend.ml_pipeline.safety as safety_module
        original = safety_module.compute_uncertainty
        safety_module.compute_uncertainty = mock_uncertainty

        try:
            X = pd.DataFrame(np.random.rand(1, 5))
            result = safe_prediction_with_fallback(
                mock_model, X, fallback_rules=fallback_fn
            )

            assert result.gate_decision == "FALLBACK"
            assert result.fallback_used is True
            assert result.confidence == 0.6
            fallback_fn.assert_called_once()
        finally:
            safety_module.compute_uncertainty = original

    def test_multiple_predictions(self):
        """Test with multiple samples (multi-prediction path)."""
        mock_model = Mock()
        mock_model.predict_proba.return_value = np.array([
            [0.1, 0.9],
            [0.2, 0.8],
            [0.05, 0.95],
        ])
        mock_model.predict.return_value = np.array([1, 1, 1])

        def mock_uncertainty(model, X):
            return np.array([0.05, 0.1, 0.03])

        import backend.ml_pipeline.safety as safety_module
        original = safety_module.compute_uncertainty
        safety_module.compute_uncertainty = mock_uncertainty

        try:
            X = pd.DataFrame(np.random.rand(3, 5))
            result = safe_prediction_with_fallback(mock_model, X)

            assert result.gate_decision == "ACCEPT"
            assert len(result.prediction) == 3
        finally:
            safety_module.compute_uncertainty = original


# ---------------------------------------------------------------------------
# Generate Uncertainty Alert Tests
# ---------------------------------------------------------------------------
class TestGenerateUncertaintyAlert:
    """Test generate_uncertainty_alert function."""

    def test_no_alert_high_confidence(self):
        result = PredictionResult(
            prediction=1, confidence=0.85, uncertainty=0.1,
            gate_decision="ACCEPT", reasoning="High confidence"
        )
        alert = generate_uncertainty_alert("device_123", result)
        assert alert is None

    def test_alert_low_confidence(self):
        result = PredictionResult(
            prediction=None, confidence=0.45, uncertainty=0.5,
            gate_decision="REJECT", reasoning="Low confidence"
        )
        alert = generate_uncertainty_alert("device_456", result)
        assert alert is not None
        assert alert["device_id"] == "device_456"
        assert alert["alert_type"] == "MODEL_UNCERTAINTY"
        assert alert["confidence"] == 0.45
        assert alert["action_required"] == "Collect more data or review manually"


# ---------------------------------------------------------------------------
# Data Validation Edge Cases
# ---------------------------------------------------------------------------
class TestDataValidationEdgeCases:
    """Test edge cases in data validation."""

    def test_unknown_problem_type(self):
        validator = DataValidator()
        result = validator.validate_dataset(pd.DataFrame(), "unknown_type")
        assert result.passed is False
        assert result.confidence == 0.0

    def test_ota_validation_with_post_update(self):
        """Test OTA-specific completeness checks."""
        n = 1200
        df = pd.DataFrame({
            "device_id": [f"device_{i % 5}" for i in range(n)],
            "timestamp": np.arange(0, n * 300000, 300000),
            "battery_level": np.random.randint(20, 100, n),
            "temperature": np.random.randint(250, 400, n),
            "voltage": np.random.randint(3500, 4200, n),
            "is_post_update": [False] * 600 + [True] * 600,
        })
        validator = DataValidator()
        result = validator.validate_dataset(df, "ota_update_impact")
        assert isinstance(result, ValidationResult)

    def test_no_timestamp_column(self):
        """Temporal continuity check with no timestamp."""
        df = pd.DataFrame({
            "device_id": ["d1"] * 50,
            "battery_level": np.random.randint(20, 100, 50),
        })
        validator = DataValidator()
        result = validator.validate_dataset(df, "battery_drain_detection")
        # Should not crash, temporal continuity passes by default

    def test_invalid_battery_levels(self):
        """Value range check catches invalid battery levels."""
        n = 1500
        df = pd.DataFrame({
            "device_id": ["d1"] * n,
            "timestamp": np.arange(0, n * 300000, 300000),
            "battery_level": [-10] * n,  # Invalid
            "temperature": np.random.randint(250, 400, n),
        })
        validator = DataValidator()
        result = validator.validate_dataset(df, "battery_drain_detection")
        assert any("battery" in issue.lower() for issue in result.issues)

    def test_invalid_temperatures(self):
        """Value range check catches invalid temperatures."""
        n = 1500
        df = pd.DataFrame({
            "device_id": ["d1"] * n,
            "timestamp": np.arange(0, n * 300000, 300000),
            "battery_level": np.random.randint(20, 100, n),
            "temperature": [800] * n,  # Too hot - invalid
        })
        validator = DataValidator()
        result = validator.validate_dataset(df, "battery_drain_detection")
        assert any("temperature" in issue.lower() for issue in result.issues)

    def test_invalid_voltages(self):
        """Value range check catches invalid voltages."""
        n = 1500
        df = pd.DataFrame({
            "device_id": ["d1"] * n,
            "timestamp": np.arange(0, n * 300000, 300000),
            "battery_level": np.random.randint(20, 100, n),
            "temperature": np.random.randint(250, 400, n),
            "voltage": [1000] * n,  # Too low
        })
        validator = DataValidator()
        result = validator.validate_dataset(df, "battery_drain_detection")
        assert any("voltage" in issue.lower() for issue in result.issues)

    def test_stuck_sensor_temperature(self):
        """Corruption check detects stuck temperature sensor."""
        n = 1500
        df = pd.DataFrame({
            "device_id": ["d1"] * n,
            "timestamp": np.arange(0, n * 300000, 300000),
            "battery_level": np.random.randint(20, 100, n),
            "temperature": [350] * n,  # Constant - stuck
        })
        validator = DataValidator()
        result = validator.validate_dataset(df, "battery_drain_detection")
        assert any("unique" in issue.lower() for issue in result.issues)

    def test_baseline_stability_insufficient_pre(self):
        """Baseline stability with too few pre-update samples."""
        df = pd.DataFrame({
            "device_id": ["d1"] * 200,
            "timestamp": np.arange(0, 200 * 300000, 300000),
            "battery_level": np.random.randint(20, 100, 200),
            "temperature": np.random.randint(250, 400, 200),
            "voltage": np.random.randint(3500, 4200, 200),
            "is_post_update": [False] * 50 + [True] * 150,  # Only 50 pre
        })
        validator = DataValidator()
        result = validator.validate_dataset(df, "ota_update_impact")
        assert any("baseline" in issue.lower() for issue in result.issues)

    def test_baseline_stability_high_variability(self):
        """Baseline stability with high coefficient of variation."""
        n = 300
        # Create drain rates where pre-update data has CV > 2.0
        # Most values near 0.001, a few outliers at 1000 => mean~5, std~70 => CV~14
        pre_drain = np.concatenate([
            np.full(190, 0.001),
            np.full(10, 1000.0),
        ])
        df = pd.DataFrame({
            "device_id": [f"device_{i % 5}" for i in range(n)],
            "timestamp": np.arange(0, n * 300000, 300000),
            "battery_level": np.random.randint(20, 100, n),
            "temperature": np.random.randint(250, 400, n),
            "voltage": np.random.randint(3500, 4200, n),
            "is_post_update": [False] * 200 + [True] * 100,
            "drain_rate_per_hour": np.concatenate([
                pre_drain,
                np.random.uniform(5, 10, 100),
            ]),
        })
        validator = DataValidator()
        result = validator.validate_dataset(df, "ota_update_impact")
        # Should flag high baseline variability
        assert any("variability" in str(issue).lower() or "baseline" in str(issue).lower()
                    for issue in result.issues)

    def test_observation_hours_empty(self):
        """Observation hours with too few rows."""
        df = pd.DataFrame({
            "device_id": ["d1"],
            "timestamp": [0],
            "battery_level": [50],
        })
        validator = DataValidator()
        assert validator._observation_hours(df) == 0.0

    def test_observation_hours_no_timestamp(self):
        df = pd.DataFrame({"battery_level": [50, 60]})
        validator = DataValidator()
        assert validator._observation_hours(df) == 0.0

    def test_large_time_gaps(self):
        """Temporal continuity fails with many large gaps."""
        timestamps = list(range(0, 50 * 300000, 300000))
        # Insert huge gaps
        for i in range(0, len(timestamps), 5):
            timestamps[i] += 10_000_000  # 10000s gap
        n = len(timestamps)
        df = pd.DataFrame({
            "device_id": ["d1"] * n,
            "timestamp": timestamps,
            "battery_level": np.random.randint(20, 100, n),
            "temperature": np.random.randint(250, 400, n),
        })
        validator = DataValidator()
        result = validator.validate_dataset(df, "battery_drain_detection")
        # Should still return a result

    def test_empty_checks_confidence(self):
        """Compute confidence with empty checks list."""
        validator = DataValidator()
        assert validator._compute_confidence([]) == 0.0

    def test_validation_result_repr(self):
        result = ValidationResult(passed=True, confidence=85.5, issues=[])
        assert "PASS" in repr(result)
        result2 = ValidationResult(passed=False, confidence=30.0, issues=["bad"])
        assert "FAIL" in repr(result2)


class TestSessionQualityGateEdgeCases:
    """Additional quality gate tests."""

    def test_gate_accept_high_quality(self):
        """Quality gate accepts when confidence >= 85."""
        n = 2000
        df = pd.DataFrame({
            "device_id": [f"device_{i % 10}" for i in range(n)],
            "timestamp": np.arange(0, n * 300000, 300000),
            "battery_level": np.random.randint(20, 100, n),
            "temperature": np.random.randint(250, 400, n),
            "voltage": np.random.randint(3500, 4200, n),
        })
        gate = SessionQualityGate()
        decision = gate.evaluate_session(df, "battery_drain_detection")
        assert decision.decision == "ACCEPT"
        assert decision.confidence >= 85

    def test_gate_decision_fields(self):
        """Verify GateDecision has all fields."""
        decision = GateDecision(
            decision="ACCEPT",
            confidence=90.0,
            reasoning="Good",
            issues=[],
        )
        assert decision.decision == "ACCEPT"
        assert decision.confidence == 90.0


# ---------------------------------------------------------------------------
# Check and ValidationResult dataclass tests
# ---------------------------------------------------------------------------
class TestDataclasses:
    """Test dataclass construction."""

    def test_check_with_issue(self):
        c = Check(name="test", passed=False, score=0.5, issue="Something wrong")
        assert c.issue == "Something wrong"

    def test_check_without_issue(self):
        c = Check(name="test", passed=True, score=1.0)
        assert c.issue is None


# ---------------------------------------------------------------------------
# Model Configs Completeness
# ---------------------------------------------------------------------------
class TestModelConfigsCompleteness:
    """Ensure all PROBLEM_TO_MODEL entries resolve."""

    def test_all_problem_types_have_configs(self):
        for problem_type, model_name in PROBLEM_TO_MODEL.items():
            assert model_name in MODEL_CONFIGS
            config = MODEL_CONFIGS[model_name]
            assert "model_type" in config
            assert "class" in config
            assert "params" in config
            assert "justification" in config


# ---------------------------------------------------------------------------
# Additional Safety Coverage: compute_uncertainty branches
# ---------------------------------------------------------------------------
class TestComputeUncertaintyBranches:
    """Cover LightGBM/XGBoost and regressor paths in compute_uncertainty."""

    def test_lgbm_xgb_path_with_predict_proba(self):
        """Cover lines 134-147: model with n_estimators + predict_proba."""
        mock_model = Mock()
        mock_model.n_estimators = 10
        # Remove estimators_ so it takes the second branch
        del mock_model.estimators_
        mock_model.predict_proba.return_value = np.array([[0.3, 0.7], [0.6, 0.4]])

        X = np.random.rand(2, 3)
        result = compute_uncertainty(mock_model, X)
        assert len(result) == 2

    def test_lgbm_xgb_path_without_predict_proba(self):
        """Cover the predict path (no predict_proba) inside iteration loop."""
        mock_model = Mock(spec=["predict", "n_estimators"])
        mock_model.n_estimators = 5
        mock_model.predict.return_value = np.array([1, 0])

        X = np.random.rand(2, 3)
        result = compute_uncertainty(mock_model, X)
        assert len(result) == 2

    def test_regressor_std_uncertainty(self):
        """Cover lines 169-172: regressor std-based uncertainty."""
        # Create a model with estimators_ where predictions are continuous
        from sklearn.tree import DecisionTreeRegressor
        X_train = np.random.rand(20, 3)
        y_train = np.random.rand(20)

        from sklearn.ensemble import RandomForestRegressor
        model = RandomForestRegressor(n_estimators=5, random_state=42)
        model.fit(X_train, y_train)

        X_test = np.random.rand(3, 3)
        result = compute_uncertainty(model, X_test)
        assert len(result) == 3
        assert all(0 <= u <= 1 for u in result)


# ---------------------------------------------------------------------------
# Additional Safety Coverage: SHAP values & REJECT path
# ---------------------------------------------------------------------------
class TestSafePredictionShapAndReject:
    """Cover SHAP computation and REJECT paths."""

    def test_reject_prediction(self):
        """Cover REJECT path in safe_prediction_with_fallback."""
        mock_model = Mock()
        # Very low confidence predictions
        mock_model.predict_proba.return_value = np.array([[0.35, 0.65]])
        mock_model.predict.return_value = np.array([1])

        def mock_uncertainty(model, X):
            return np.array([0.9])  # Very high uncertainty -> REJECT

        import backend.ml_pipeline.safety as safety_module
        original = safety_module.compute_uncertainty
        safety_module.compute_uncertainty = mock_uncertainty

        try:
            X = pd.DataFrame(np.random.rand(1, 5))
            result = safe_prediction_with_fallback(mock_model, X)
            assert result.gate_decision == "REJECT"
            assert result.prediction is None
            assert result.confidence == 0.0
        finally:
            safety_module.compute_uncertainty = original

    def test_shap_values_computation(self):
        """Cover SHAP values computation (lines 224-235)."""
        mock_model = Mock()
        mock_model.predict_proba.return_value = np.array([[0.05, 0.95]])
        mock_model.predict.return_value = np.array([1])

        def mock_uncertainty(model, X):
            return np.array([0.02])

        import backend.ml_pipeline.safety as safety_module
        original_unc = safety_module.compute_uncertainty
        safety_module.compute_uncertainty = mock_uncertainty

        # Mock shap module
        mock_shap = Mock()
        mock_explainer = Mock()
        mock_explainer.shap_values.return_value = np.array([[0.1, -0.2, 0.3, 0.05, -0.1]])
        mock_shap.TreeExplainer.return_value = mock_explainer
        import sys
        original_shap = sys.modules.get("shap")
        sys.modules["shap"] = mock_shap

        try:
            X = pd.DataFrame(np.random.rand(1, 5), columns=["f0", "f1", "f2", "f3", "f4"])
            result = safe_prediction_with_fallback(mock_model, X, return_shap=True)
            assert result.gate_decision == "ACCEPT"
            assert result.shap_values is not None
        finally:
            safety_module.compute_uncertainty = original_unc
            if original_shap is not None:
                sys.modules["shap"] = original_shap
            else:
                sys.modules.pop("shap", None)

    def test_shap_exception_handled(self):
        """Cover SHAP exception path."""
        mock_model = Mock()
        mock_model.predict_proba.return_value = np.array([[0.05, 0.95]])
        mock_model.predict.return_value = np.array([1])

        def mock_uncertainty(model, X):
            return np.array([0.02])

        import backend.ml_pipeline.safety as safety_module
        original_unc = safety_module.compute_uncertainty
        safety_module.compute_uncertainty = mock_uncertainty

        # Make shap raise an exception
        mock_shap = Mock()
        mock_shap.TreeExplainer.side_effect = Exception("shap error")
        import sys
        original_shap = sys.modules.get("shap")
        sys.modules["shap"] = mock_shap

        try:
            X = pd.DataFrame(np.random.rand(1, 5))
            result = safe_prediction_with_fallback(mock_model, X, return_shap=True)
            assert result.gate_decision == "ACCEPT"
            assert result.shap_values is None
        finally:
            safety_module.compute_uncertainty = original_unc
            if original_shap is not None:
                sys.modules["shap"] = original_shap
            else:
                sys.modules.pop("shap", None)

    def test_shap_values_multi_prediction(self):
        """Cover SHAP path when len(X) > 1 (branch 231->239 skip)."""
        mock_model = Mock()
        mock_model.predict_proba.return_value = np.array([
            [0.05, 0.95],
            [0.03, 0.97],
        ])
        mock_model.predict.return_value = np.array([1, 1])

        def mock_uncertainty(model, X):
            return np.array([0.02, 0.03])

        import backend.ml_pipeline.safety as safety_module
        original_unc = safety_module.compute_uncertainty
        safety_module.compute_uncertainty = mock_uncertainty

        mock_shap = Mock()
        mock_explainer = Mock()
        mock_explainer.shap_values.return_value = np.array([
            [0.1, -0.2, 0.3, 0.05, -0.1],
            [0.2, -0.1, 0.4, 0.02, -0.05],
        ])
        mock_shap.TreeExplainer.return_value = mock_explainer
        import sys
        original_shap = sys.modules.get("shap")
        sys.modules["shap"] = mock_shap

        try:
            X = pd.DataFrame(np.random.rand(2, 5), columns=["f0", "f1", "f2", "f3", "f4"])
            result = safe_prediction_with_fallback(mock_model, X, return_shap=True)
            assert result.gate_decision == "ACCEPT"
            # Multi-prediction: shap_values is NOT converted to dict
            assert result.shap_values is None  # len(X) != 1, so dict conversion skipped
        finally:
            safety_module.compute_uncertainty = original_unc
            if original_shap is not None:
                sys.modules["shap"] = original_shap
            else:
                sys.modules.pop("shap", None)


# ---------------------------------------------------------------------------
# Additional Data Validation Coverage: REVIEW path, completeness branches
# ---------------------------------------------------------------------------
class TestDataValidationReviewPath:
    """Cover the REVIEW decision path and remaining branches."""

    def test_quality_gate_review(self):
        """Cover lines 339-340: REVIEW decision when confidence in [60, 85)."""
        # Mock the DataValidator to return a confidence in [60, 85)
        gate = SessionQualityGate()
        with patch("backend.ml_pipeline.data_validation.DataValidator.validate_dataset") as mock_val:
            mock_val.return_value = ValidationResult(
                passed=True, confidence=72.0, issues=["Minor data issue"]
            )
            decision = gate.evaluate_session(pd.DataFrame(), "battery_drain_detection")
            assert decision.decision == "REVIEW"
            assert "review" in decision.reasoning.lower()
            assert decision.confidence == 72.0

    def test_completeness_no_device_id(self):
        """Cover branch where device_id column is missing."""
        df = pd.DataFrame({
            "timestamp": np.arange(0, 500 * 300000, 300000),
            "battery_level": np.random.randint(20, 100, 500),
        })
        validator = DataValidator()
        result = validator.validate_dataset(df, "battery_drain_detection")
        # Should still run but miss device count check
        assert isinstance(result.passed, bool)

    def test_completeness_no_timestamp(self):
        """Cover branch where timestamp column is missing for observation hours."""
        df = pd.DataFrame({
            "device_id": [f"d{i}" for i in range(500)],
            "battery_level": np.random.randint(20, 100, 500),
        })
        validator = DataValidator()
        result = validator.validate_dataset(df, "battery_drain_detection")
        assert isinstance(result.passed, bool)

    def test_baseline_stability_no_drain_rate(self):
        """Cover branch 272->280 where drain_rate_per_hour is not present."""
        n = 400
        df = pd.DataFrame({
            "device_id": [f"device_{i % 5}" for i in range(n)],
            "timestamp": np.arange(0, n * 300000, 300000),
            "battery_level": np.random.randint(20, 100, n),
            "temperature": np.random.randint(250, 400, n),
            "is_post_update": [False] * 250 + [True] * 150,
            # No drain_rate_per_hour column
        })
        validator = DataValidator()
        result = validator.validate_dataset(df, "ota_update_impact")
        assert isinstance(result.passed, bool)

    def test_baseline_stability_low_variability(self):
        """Cover branch 272->280 where drain_cv <= 2.0 (stable baseline)."""
        n = 400
        df = pd.DataFrame({
            "device_id": [f"device_{i % 5}" for i in range(n)],
            "timestamp": np.arange(0, n * 300000, 300000),
            "battery_level": np.random.randint(20, 100, n),
            "temperature": np.random.randint(250, 400, n),
            "is_post_update": [False] * 250 + [True] * 150,
            # Stable drain rate (low CV, well below 2.0)
            "drain_rate_per_hour": np.random.uniform(4.5, 5.5, n),
        })
        validator = DataValidator()
        result = validator.validate_dataset(df, "ota_update_impact")
        assert isinstance(result.passed, bool)

    def test_value_ranges_no_columns(self):
        """Cover branches where battery/temp/voltage columns are absent."""
        df = pd.DataFrame({
            "device_id": [f"d{i}" for i in range(100)],
            "timestamp": np.arange(0, 100 * 300000, 300000),
            "some_other_col": np.ones(100),
        })
        validator = DataValidator()
        result = validator.validate_dataset(df, "battery_drain_detection")
        assert isinstance(result.passed, bool)


# ---------------------------------------------------------------------------
# Additional Feature Engineering Coverage: branch paths
# ---------------------------------------------------------------------------
class TestFeatureEngineerBranches:
    """Cover remaining branches in feature_engineering.py."""

    def test_battery_drain_with_screen_and_drain_rate(self):
        """Cover screen_drain_ratio calculation with screen_on and drain_rate columns."""
        n = 30
        df = pd.DataFrame({
            "timestamp": np.arange(0, n * 300000, 300000),
            "battery_level": np.random.randint(20, 100, n),
            "temperature": np.random.randint(280, 350, n),
            "screen_on": [1, 0] * 15,
            "drain_rate_instant": np.random.uniform(0.5, 3.0, n),
            "power_save_mode": [0] * n,
        })
        fe = FeatureEngineer()
        result = fe.transform(df, "battery_drain_detection")
        assert "screen_drain_ratio" in result.columns
        assert "thermal_drain_corr_24h" in result.columns
        assert "time_since_charge" in result.columns

    def test_ota_with_drain_rate_instant(self):
        """Cover OTA features with drain_rate_instant present."""
        df = pd.DataFrame({
            "timestamp": np.arange(0, 40 * 300000, 300000),
            "battery_level": np.random.randint(20, 100, 40),
            "temperature": np.random.randint(280, 350, 40),
            "drain_rate_instant": np.random.uniform(0.5, 3.0, 40),
            "is_post_update": [False] * 20 + [True] * 20,
        })
        fe = FeatureEngineer()
        result = fe.transform(df, "ota_update_impact")
        assert "drain_rate_delta" in result.columns
        assert "temp_max_delta" in result.columns
        assert "days_since_update" in result.columns

    def test_performance_with_existing_columns(self):
        """Cover performance features when all columns already exist."""
        df = pd.DataFrame({
            "timestamp": np.arange(0, 20 * 300000, 300000),
            "battery_level": np.random.randint(20, 100, 20),
            "temperature": np.random.randint(280, 350, 20),
            "health_percentage": np.random.uniform(80, 100, 20),
            "capacity_fade": np.random.uniform(0, 5, 20),
            "cycle_count": np.arange(100, 120),
            "fast_charge_count": np.arange(20),
            "deep_discharge_count": np.arange(20),
        })
        fe = FeatureEngineer()
        result = fe.transform(df, "performance_degradation")
        assert "days_since_first_use" in result.columns

    def test_app_features_without_battery_percent(self):
        """Cover app features when battery_percent_used is missing."""
        df = pd.DataFrame({
            "timestamp": np.arange(0, 20 * 300000, 300000),
            "battery_level": np.random.randint(20, 100, 20),
            "temperature": np.random.randint(280, 350, 20),
            "foreground_time": np.random.randint(60, 3600, 20),
        })
        fe = FeatureEngineer()
        result = fe.transform(df, "app_misbehavior")
        assert "drain_per_hour" in result.columns
        # Should use default 0.0 since battery_percent_used is missing
        assert (result["drain_per_hour"] == 0.0).all()

    def test_thermal_features_without_battery(self):
        """Cover thermal without battery_level for is_charging default."""
        df = pd.DataFrame({
            "timestamp": np.arange(0, 20 * 300000, 300000),
            "temperature": np.random.randint(280, 350, 20),
        })
        fe = FeatureEngineer()
        result = fe.transform(df, "thermal_regression")
        assert "is_charging" in result.columns
        assert (result["is_charging"] == 0).all()

    def test_rolling_stats_with_large_dataset(self):
        """Cover rolling stats computation with enough data points."""
        n = 100
        df = pd.DataFrame({
            "timestamp": np.arange(0, n * 300000, 300000),
            "battery_level": np.random.randint(20, 100, n),
            "temperature": np.random.randint(250, 400, n),
            "voltage": np.random.randint(3500, 4200, n),
        })
        fe = FeatureEngineer()
        result = fe.transform(df, "battery_drain_detection")
        # Check that rolling stats were computed for numeric columns
        assert any("mean" in col for col in result.columns)

    def test_ota_all_pre_update(self):
        """Cover OTA when all data is pre-update (no post-update markers)."""
        df = pd.DataFrame({
            "timestamp": np.arange(0, 30 * 300000, 300000),
            "battery_level": np.random.randint(20, 100, 30),
            "temperature": np.random.randint(280, 350, 30),
            "is_post_update": [False] * 30,
            "drain_rate_instant": np.random.uniform(1, 3, 30),
        })
        fe = FeatureEngineer()
        result = fe.transform(df, "ota_update_impact")
        assert "days_since_update" in result.columns
        assert (result["days_since_update"] == 0).all()

    def test_battery_drain_no_battery_level(self):
        """Cover branch 163->178: battery_level not in columns."""
        df = pd.DataFrame({
            "timestamp": np.arange(0, 20 * 300000, 300000),
            "temperature": np.random.randint(280, 350, 20),
        })
        fe = FeatureEngineer()
        result = fe.transform(df, "battery_drain_detection")
        assert "screen_drain_ratio" in result.columns

    def test_thermal_with_existing_is_charging(self):
        """Cover branch 204->210: is_charging already in columns."""
        df = pd.DataFrame({
            "timestamp": np.arange(0, 20 * 300000, 300000),
            "temperature": np.random.randint(280, 350, 20),
            "is_charging": [0, 1] * 10,
        })
        fe = FeatureEngineer()
        result = fe.transform(df, "thermal_regression")
        assert "is_charging" in result.columns
        # Should keep original values
        assert result["is_charging"].sum() == 10

    def test_ota_no_drain_rate_no_temperature(self):
        """Cover branches 224->228 and 228->233: no drain_rate_instant, no temperature."""
        df = pd.DataFrame({
            "timestamp": np.arange(0, 30 * 300000, 300000),
            "battery_level": np.random.randint(20, 100, 30),
            "is_post_update": [False] * 15 + [True] * 15,
        })
        fe = FeatureEngineer()
        result = fe.transform(df, "ota_update_impact")
        assert "days_since_update" in result.columns
        assert "drain_rate_delta" not in result.columns
        assert "temp_max_delta" not in result.columns

    def test_app_with_existing_drain_per_hour(self):
        """Cover branch 280->284: drain_per_hour already exists."""
        df = pd.DataFrame({
            "timestamp": np.arange(0, 20 * 300000, 300000),
            "battery_level": np.random.randint(20, 100, 20),
            "drain_per_hour": np.random.uniform(0.5, 3.0, 20),
        })
        fe = FeatureEngineer()
        result = fe.transform(df, "app_misbehavior")
        assert "drain_per_hour" in result.columns
        # Should keep original values since already present
        assert (result["drain_per_hour"] > 0).any()

    def test_thermal_without_temperature(self):
        """Cover branch 204->210: thermal without temperature column."""
        df = pd.DataFrame({
            "timestamp": np.arange(0, 20 * 300000, 300000),
            "battery_level": np.random.randint(20, 100, 20),
        })
        fe = FeatureEngineer()
        result = fe.transform(df, "thermal_regression")
        # temp_acceleration should NOT be present since no temperature
        assert "temp_acceleration" not in result.columns
        # is_charging should still be derived from battery_level
        assert "is_charging" in result.columns


# ---------------------------------------------------------------------------
# Additional Models Coverage: direct create_model with edge cases
# ---------------------------------------------------------------------------
class TestCreateModelEdgeCases:
    """Cover remaining branches in create_model."""

    def test_create_model_unknown_sklearn_class(self):
        """Cover lines 196-197: unknown sklearn class."""
        import backend.ml_pipeline.models as models_module
        original = models_module.MODEL_CONFIGS.copy()
        models_module.MODEL_CONFIGS["_test_bad_sklearn"] = {
            "model_type": "sklearn",
            "class": "NonExistentClass",
            "params": {},
            "justification": "test",
        }
        try:
            with pytest.raises(ValueError, match="Unknown sklearn class"):
                create_model("_test_bad_sklearn")
        finally:
            del models_module.MODEL_CONFIGS["_test_bad_sklearn"]

    def test_create_model_unknown_model_type(self):
        """Cover lines 198-199: unknown model_type."""
        import backend.ml_pipeline.models as models_module
        models_module.MODEL_CONFIGS["_test_bad_type"] = {
            "model_type": "pytorch",
            "class": "SomeClass",
            "params": {},
            "justification": "test",
        }
        try:
            with pytest.raises(ValueError, match="Unknown model type"):
                create_model("_test_bad_type")
        finally:
            del models_module.MODEL_CONFIGS["_test_bad_type"]


if __name__ == "__main__":
    pytest.main([__file__, "-v", "--tb=short"])
