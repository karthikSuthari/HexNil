"""
Tests for HexNil ML Training Pipeline

Validates data validation, model selection, and safety mechanisms.
"""

import pytest
import pandas as pd
import numpy as np
from unittest.mock import Mock, MagicMock

# Import ML pipeline modules
import sys
import os

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from backend.ml_pipeline.data_validation import (
    DataValidator,
    SessionQualityGate,
    ValidationResult,
    MINIMUM_REQUIREMENTS,
)
from backend.ml_pipeline.models import (
    get_model_for_problem,
    get_feature_list,
    MODEL_CONFIGS,
    PROBLEM_TO_MODEL,
)
from backend.ml_pipeline.safety import (
    SafetyGate,
    predict_with_confidence,
    PredictionResult,
    safe_prediction_with_fallback,
)


class TestDataValidation:
    """Test data validation and trust layer."""

    def test_minimum_requirements_defined(self):
        """Verify minimum requirements are defined for all problem types."""
        assert "battery_drain_detection" in MINIMUM_REQUIREMENTS
        assert "thermal_regression" in MINIMUM_REQUIREMENTS
        assert "ota_update_impact" in MINIMUM_REQUIREMENTS
        assert "performance_degradation" in MINIMUM_REQUIREMENTS

    def test_validate_complete_dataset(self):
        """Test validation on a complete, high-quality dataset."""
        # Create synthetic dataset meeting requirements
        n_samples = 1500
        df = pd.DataFrame(
            {
                "device_id": ["device_1"] * n_samples,
                "timestamp": np.arange(0, n_samples * 300000, 300000),  # 5-min intervals
                "battery_level": np.random.randint(20, 100, n_samples),
                "temperature": np.random.randint(250, 400, n_samples),  # 25-40°C
                "voltage": np.random.randint(3500, 4200, n_samples),
                "drain_rate_instant": np.random.uniform(2, 10, n_samples),
            }
        )

        validator = DataValidator()
        result = validator.validate_dataset(df, "battery_drain_detection")

        assert isinstance(result, ValidationResult)
        assert result.confidence > 60.0, f"Expected confidence > 60, got {result.confidence}"
        assert result.passed is True

    def test_validate_insufficient_data(self):
        """Test validation rejects dataset with insufficient samples."""
        # Create tiny dataset
        df = pd.DataFrame(
            {
                "device_id": ["device_1"] * 50,
                "timestamp": np.arange(0, 50 * 300000, 300000),
                "battery_level": np.random.randint(20, 100, 50),
                "temperature": np.random.randint(250, 400, 50),
            }
        )

        validator = DataValidator()
        result = validator.validate_dataset(df, "battery_drain_detection")

        assert result.passed is False
        assert "Insufficient data" in str(result.issues)

    def test_validate_corrupted_data(self):
        """Test validation detects corrupted sensor data."""
        n_samples = 1500
        df = pd.DataFrame(
            {
                "device_id": ["device_1"] * n_samples,
                "timestamp": np.arange(0, n_samples * 300000, 300000),
                "battery_level": [0] * n_samples,  # All zeros - corrupted
                "temperature": np.random.randint(250, 400, n_samples),
                "voltage": np.random.randint(3500, 4200, n_samples),
            }
        )

        validator = DataValidator()
        result = validator.validate_dataset(df, "battery_drain_detection")

        assert "mostly zeros" in str(result.issues).lower()
        assert result.confidence < 70.0

    def test_quality_gate_accept(self):
        """Test quality gate accepts high-quality data."""
        n_samples = 1500
        df = pd.DataFrame(
            {
                "device_id": ["device_1"] * n_samples,
                "timestamp": np.arange(0, n_samples * 300000, 300000),
                "battery_level": np.random.randint(20, 100, n_samples),
                "temperature": np.random.randint(250, 400, n_samples),
                "voltage": np.random.randint(3500, 4200, n_samples),
            }
        )

        gate = SessionQualityGate()
        decision = gate.evaluate_session(df, "battery_drain_detection")

        assert decision.decision in ["ACCEPT", "REVIEW"]
        assert decision.confidence > 60.0

    def test_quality_gate_reject(self):
        """Test quality gate rejects low-quality data."""
        # Create insufficient dataset
        df = pd.DataFrame(
            {
                "device_id": ["device_1"] * 10,
                "timestamp": np.arange(0, 10 * 300000, 300000),
                "battery_level": [0] * 10,  # Corrupted
            }
        )

        gate = SessionQualityGate()
        decision = gate.evaluate_session(df, "battery_drain_detection")

        assert decision.decision == "REJECT"
        assert decision.confidence < 60.0


class TestModelSelection:
    """Test model selection and configuration."""

    def test_problem_to_model_mapping(self):
        """Verify all problem types have model mappings."""
        for problem_type in PROBLEM_TO_MODEL.keys():
            model_name, config = get_model_for_problem(problem_type)
            assert model_name in MODEL_CONFIGS
            assert "model_type" in config
            assert "params" in config
            assert "justification" in config

    def test_unknown_problem_type(self):
        """Test error handling for unknown problem type."""
        with pytest.raises(ValueError, match="Unknown problem type"):
            get_model_for_problem("invalid_problem")

    def test_feature_lists_defined(self):
        """Verify feature lists are defined for all problem types."""
        for problem_type in PROBLEM_TO_MODEL.keys():
            features = get_feature_list(problem_type)
            assert isinstance(features, list)
            if problem_type != "app_misbehavior":  # Has minimal features
                assert len(features) > 0

    def test_model_configs_have_required_fields(self):
        """Verify all model configs have required fields."""
        required_fields = ["model_type", "class", "params", "justification"]

        for model_name, config in MODEL_CONFIGS.items():
            for field in required_fields:
                assert field in config, f"{model_name} missing {field}"

            # Check params have n_estimators (for tree models)
            if config["model_type"] in ["lightgbm", "xgboost", "sklearn"]:
                assert "n_estimators" in config["params"] or "GradientBoosting" in config["class"]


class TestSafetyMechanisms:
    """Test safety gate and uncertainty handling."""

    def test_safety_gate_accept(self):
        """Test safety gate accepts high-confidence predictions."""
        gate = SafetyGate()
        decision, reasoning = gate.gate_prediction(
            prediction=1, confidence=0.92, uncertainty=0.08
        )

        assert decision == "ACCEPT"
        assert "High confidence" in reasoning

    def test_safety_gate_fallback(self):
        """Test safety gate triggers fallback for moderate confidence."""
        gate = SafetyGate()
        decision, reasoning = gate.gate_prediction(
            prediction=1, confidence=0.75, uncertainty=0.20
        )

        assert decision == "FALLBACK"
        assert "Moderate confidence" in reasoning

    def test_safety_gate_reject(self):
        """Test safety gate rejects low-confidence predictions."""
        gate = SafetyGate()
        decision, reasoning = gate.gate_prediction(
            prediction=1, confidence=0.55, uncertainty=0.40
        )

        assert decision == "REJECT"
        assert "Low confidence" in reasoning

    def test_predict_with_confidence(self):
        """Test confidence extraction from model predictions."""
        # Mock model with predict_proba
        mock_model = Mock()
        mock_model.predict_proba.return_value = np.array([[0.2, 0.8], [0.7, 0.3]])
        mock_model.predict.return_value = np.array([1, 0])

        X = np.random.rand(2, 5)
        predictions, confidences = predict_with_confidence(mock_model, X)

        assert len(predictions) == 2
        assert len(confidences) == 2
        assert confidences[0] == 0.8  # Max of [0.2, 0.8]
        assert confidences[1] == 0.7  # Max of [0.7, 0.3]

    def test_safe_prediction_with_fallback_accept(self):
        """Test safe prediction accepts high-confidence model output."""
        # Mock high-confidence model
        mock_model = Mock()
        mock_model.predict_proba.return_value = np.array([[0.05, 0.95]])
        mock_model.predict.return_value = np.array([1])

        # Mock uncertainty computation
        def mock_uncertainty(model, X):
            return np.array([0.05])

        import backend.ml_pipeline.safety as safety_module

        original_compute = safety_module.compute_uncertainty
        safety_module.compute_uncertainty = mock_uncertainty

        try:
            X = pd.DataFrame(np.random.rand(1, 5))
            result = safe_prediction_with_fallback(mock_model, X)

            assert isinstance(result, PredictionResult)
            assert result.gate_decision == "ACCEPT"
            assert result.confidence >= 0.85
            assert result.fallback_used is False

        finally:
            safety_module.compute_uncertainty = original_compute

    def test_safe_prediction_with_fallback_reject(self):
        """Test safe prediction rejects low-confidence output."""
        # Mock low-confidence model
        mock_model = Mock()
        mock_model.predict_proba.return_value = np.array([[0.45, 0.55]])
        mock_model.predict.return_value = np.array([1])

        def mock_uncertainty(model, X):
            return np.array([0.45])

        import backend.ml_pipeline.safety as safety_module

        original_compute = safety_module.compute_uncertainty
        safety_module.compute_uncertainty = mock_uncertainty

        try:
            X = pd.DataFrame(np.random.rand(1, 5))
            result = safe_prediction_with_fallback(mock_model, X, fallback_rules=None)

            assert result.gate_decision == "REJECT"
            assert result.prediction is None

        finally:
            safety_module.compute_uncertainty = original_compute


class TestIntegration:
    """Integration tests for end-to-end pipeline."""

    def test_pipeline_flow_battery_drain(self):
        """Test complete pipeline flow for battery drain detection."""
        # 1. Create dataset
        n_samples = 1200
        df = pd.DataFrame(
            {
                "device_id": ["device_1"] * n_samples,
                "timestamp": np.arange(0, n_samples * 300000, 300000),
                "battery_level": np.random.randint(20, 100, n_samples),
                "temperature": np.random.randint(250, 400, n_samples),
                "voltage": np.random.randint(3500, 4200, n_samples),
                "screen_on": np.random.choice([0, 1], n_samples),
                "drain_rate_instant": np.random.uniform(2, 15, n_samples),
            }
        )

        # 2. Validate data
        validator = DataValidator()
        validation = validator.validate_dataset(df, "battery_drain_detection")
        assert validation.passed is True

        # 3. Get model configuration
        model_name, config = get_model_for_problem("battery_drain_detection")
        assert model_name == "battery_drain_lgbm"
        assert config["model_type"] == "lightgbm"

        # 4. Get feature list
        features = get_feature_list("battery_drain_detection")
        assert len(features) > 0
        assert "drain_rate_mean_1h" in features

    def test_pipeline_minimum_dataset_sizes(self):
        """Verify minimum dataset sizes in requirements are reasonable."""
        for problem_type, reqs in MINIMUM_REQUIREMENTS.items():
            if "min_snapshots" in reqs:
                # Minimum should be at least 100 for statistical significance
                assert reqs["min_snapshots"] >= 100, f"{problem_type} min too low"

            if "min_devices" in reqs:
                # Should require at least 3 devices for cross-device validation
                assert reqs["min_devices"] >= 3, f"{problem_type} needs more devices"


if __name__ == "__main__":
    pytest.main([__file__, "-v", "--tb=short"])
