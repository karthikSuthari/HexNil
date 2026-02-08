# HexNil ML Training Pipeline Architecture
## Production-Ready Machine Learning System for OEM-Grade Android Diagnostics

> **Classification**: OEM-Grade ML System Design
> **Version**: 1.0.0
> **Target Accuracy**: ≥90% prediction accuracy
> **Audience**: Principal ML Engineers, Android Systems Architects, OEM Diagnostics Leads

---

## Executive Summary

This document specifies a production-ready machine learning training pipeline for HexNil designed to achieve ≥90% prediction accuracy for:
- Battery drain anomalies
- Thermal regressions
- Performance degradation
- App & system misbehavior
- OTA update impact analysis

The pipeline prioritizes **explainability**, **reproducibility**, and **OEM trust** through deterministic feature engineering, confidence-bounded predictions, and comprehensive data validation.

---

## 1. Pipeline Architecture Overview

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    RAW TELEMETRY DATA (Room DB)                            │
│  battery_snapshots, charging_sessions, thermal_events, drain_events...     │
└──────────────────────────────┬─────────────────────────────────────────────┘
                               │
                               ▼
┌────────────────────────────────────────────────────────────────────────────┐
│              1️⃣ DATA VALIDATION & TRUST LAYER                              │
│  • Dataset completeness validation                                          │
│  • Baseline continuity verification                                         │
│  • Corruption detection                                                     │
│  • Confidence score assignment (0-100)                                      │
│  • Session quality gating                                                   │
└──────────────────────────────┬─────────────────────────────────────────────┘
                               │
                               ▼
┌────────────────────────────────────────────────────────────────────────────┐
│              2️⃣ FEATURE ENGINEERING PIPELINE                                │
│  • Delta-based features (pre/post OTA)                                      │
│  • Device-normalized metrics                                                │
│  • Statistical aggregations (rolling windows)                               │
│  • Temporal features (hour-of-day, day-of-week)                            │
│  • Correlation features (thermal + drain)                                   │
└──────────────────────────────┬─────────────────────────────────────────────┘
                               │
                               ▼
┌────────────────────────────────────────────────────────────────────────────┐
│              3️⃣ LABELING STRATEGY                                           │
│  • Rule-assisted labeling with confidence weights                           │
│  • Semi-supervised learning integration                                     │
│  • Expert review for ambiguous cases                                        │
│  • Multi-class soft labels (not binary only)                               │
└──────────────────────────────┬─────────────────────────────────────────────┘
                               │
                               ▼
┌────────────────────────────────────────────────────────────────────────────┐
│              4️⃣ MODEL SELECTION & TRAINING                                  │
│  • Problem-specific model recommendations                                   │
│  • Explainable models (GBDT, Random Forest)                                │
│  • Cross-device validation strategy                                         │
│  • Time-aware train/validation splits                                       │
│  • Hyperparameter optimization                                              │
└──────────────────────────────┬─────────────────────────────────────────────┘
                               │
                               ▼
┌────────────────────────────────────────────────────────────────────────────┐
│              5️⃣ CONFIDENCE & EXPLAINABILITY LAYER                           │
│  • Prediction confidence scores (calibrated)                                │
│  • SHAP/feature importance for interpretability                            │
│  • Human-readable reasoning generation                                      │
│  • Uncertainty quantification                                               │
└──────────────────────────────┬─────────────────────────────────────────────┘
                               │
                               ▼
┌────────────────────────────────────────────────────────────────────────────┐
│              6️⃣ FAILURE & SAFETY HANDLING                                   │
│  • Low-confidence rejection thresholds                                      │
│  • Fallback to deterministic rules                                          │
│  • Alert generation for uncertainty                                         │
│  • Audit trail for all predictions                                          │
└──────────────────────────────┬─────────────────────────────────────────────┘
                               │
                               ▼
┌────────────────────────────────────────────────────────────────────────────┐
│              7️⃣ CONTINUOUS LEARNING & MODEL VERSIONING                      │
│  • Automated retraining triggers                                            │
│  • Model drift detection                                                    │
│  • A/B testing framework                                                    │
│  • Dataset and model versioning                                             │
└────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Data Validation & Trust Layer

### 2.1 Dataset Quality Requirements

**Minimum Dataset Requirements for 90%+ Accuracy**:
```python
MINIMUM_REQUIREMENTS = {
    "battery_drain_detection": {
        "min_snapshots": 1000,
        "min_devices": 5,
        "min_observation_hours": 72,
        "min_drain_events": 50,
        "required_tables": ["battery_snapshots", "drain_events", "app_battery_usage"]
    },
    "thermal_regression": {
        "min_snapshots": 500,
        "min_devices": 5,
        "min_observation_hours": 48,
        "min_thermal_events": 20,
        "required_tables": ["battery_snapshots", "thermal_events", "thermal_heat_buckets"]
    },
    "performance_degradation": {
        "min_snapshots": 1000,
        "min_devices": 5,
        "min_observation_hours": 96,
        "min_battery_health_records": 30,
        "required_tables": ["battery_snapshots", "battery_health", "charging_sessions"]
    },
    "ota_update_impact": {
        "min_snapshots_pre": 500,
        "min_snapshots_post": 500,
        "min_devices": 3,
        "min_days_pre": 7,
        "min_days_post": 7,
        "required_tables": ["battery_snapshots", "update_comparisons"]
    }
}
```

### 2.2 Data Validation Checks

```python
class DataValidator:
    """
    Validates data quality and assigns confidence scores.
    """

    def validate_dataset(self, dataset: pd.DataFrame, problem_type: str) -> ValidationResult:
        """
        Returns ValidationResult with confidence score (0-100) and issues list.
        """
        checks = [
            self._check_completeness(dataset, problem_type),
            self._check_temporal_continuity(dataset),
            self._check_value_ranges(dataset),
            self._check_corruption(dataset),
            self._check_baseline_stability(dataset)
        ]

        confidence = self._compute_confidence(checks)
        return ValidationResult(
            passed=confidence >= 60,
            confidence=confidence,
            issues=[c.issue for c in checks if not c.passed]
        )

    def _check_completeness(self, dataset: pd.DataFrame, problem_type: str) -> Check:
        """Verify minimum data requirements are met."""
        requirements = MINIMUM_REQUIREMENTS[problem_type]

        checks = {
            "snapshots": len(dataset) >= requirements.get("min_snapshots", 0),
            "devices": dataset["device_id"].nunique() >= requirements.get("min_devices", 1),
            "hours": self._observation_hours(dataset) >= requirements.get("min_observation_hours", 0),
        }

        passed = all(checks.values())
        return Check(
            name="completeness",
            passed=passed,
            score=sum(checks.values()) / len(checks),
            issue=None if passed else f"Insufficient data: {checks}"
        )

    def _check_temporal_continuity(self, dataset: pd.DataFrame) -> Check:
        """Detect gaps in time series that indicate data loss."""
        dataset = dataset.sort_values("timestamp")
        time_diffs = dataset["timestamp"].diff()

        # Flag gaps > 2 hours as problematic
        large_gaps = (time_diffs > 2 * 3600 * 1000).sum()
        gap_ratio = large_gaps / len(dataset)

        passed = gap_ratio < 0.1  # Allow up to 10% gaps
        return Check(
            name="temporal_continuity",
            passed=passed,
            score=1.0 - gap_ratio,
            issue=f"Large time gaps: {large_gaps} ({gap_ratio:.1%})" if not passed else None
        )

    def _check_value_ranges(self, dataset: pd.DataFrame) -> Check:
        """Validate sensor readings are within physically possible ranges."""
        issues = []

        if "battery_level" in dataset.columns:
            invalid_levels = ((dataset["battery_level"] < 0) | (dataset["battery_level"] > 100)).sum()
            if invalid_levels > 0:
                issues.append(f"Invalid battery levels: {invalid_levels}")

        if "temperature" in dataset.columns:
            # Temperature in tenths of Celsius, expect 0-700 (0-70°C)
            invalid_temps = ((dataset["temperature"] < 0) | (dataset["temperature"] > 700)).sum()
            if invalid_temps > 0:
                issues.append(f"Invalid temperatures: {invalid_temps}")

        if "voltage" in dataset.columns:
            # Voltage in mV, expect 2500-4500
            invalid_volts = ((dataset["voltage"] < 2500) | (dataset["voltage"] > 4500)).sum()
            if invalid_volts > 0:
                issues.append(f"Invalid voltages: {invalid_volts}")

        passed = len(issues) == 0
        return Check(
            name="value_ranges",
            passed=passed,
            score=1.0 if passed else 0.5,
            issue="; ".join(issues) if issues else None
        )

    def _check_corruption(self, dataset: pd.DataFrame) -> Check:
        """Detect corrupted or reset data patterns."""
        issues = []

        # Check for all-zero columns (sensor failure)
        for col in ["battery_level", "temperature", "voltage"]:
            if col in dataset.columns:
                if (dataset[col] == 0).sum() / len(dataset) > 0.5:
                    issues.append(f"{col} is mostly zeros")

        # Check for constant values (stuck sensor)
        for col in ["battery_level", "temperature"]:
            if col in dataset.columns:
                if dataset[col].nunique() < 5:
                    issues.append(f"{col} has too few unique values")

        passed = len(issues) == 0
        return Check(
            name="corruption",
            passed=passed,
            score=1.0 if passed else 0.3,
            issue="; ".join(issues) if issues else None
        )

    def _check_baseline_stability(self, dataset: pd.DataFrame) -> Check:
        """Ensure sufficient stable baseline period for comparison."""
        # For OTA analysis, need stable pre-update period
        if "is_post_update" not in dataset.columns:
            return Check("baseline_stability", True, 1.0, None)

        pre_data = dataset[dataset["is_post_update"] == False]
        if len(pre_data) < 100:
            return Check(
                "baseline_stability",
                False,
                0.5,
                "Insufficient pre-update baseline data"
            )

        # Check stability: standard deviation should be reasonable
        if "drain_rate_per_hour" in pre_data.columns:
            drain_cv = pre_data["drain_rate_per_hour"].std() / (pre_data["drain_rate_per_hour"].mean() + 1e-6)
            if drain_cv > 2.0:
                return Check(
                    "baseline_stability",
                    False,
                    0.6,
                    f"High baseline variability (CV={drain_cv:.2f})"
                )

        return Check("baseline_stability", True, 1.0, None)

    def _compute_confidence(self, checks: List[Check]) -> float:
        """Compute overall dataset confidence from checks."""
        if not checks:
            return 0.0

        # Weighted average of check scores
        weights = {
            "completeness": 0.30,
            "temporal_continuity": 0.20,
            "value_ranges": 0.20,
            "corruption": 0.20,
            "baseline_stability": 0.10
        }

        total_score = sum(
            c.score * weights.get(c.name, 0.1)
            for c in checks
        )

        return min(100.0, total_score * 100)
```

### 2.3 Session Quality Gating

```python
class SessionQualityGate:
    """
    Determines if a data session is trustworthy for training/inference.
    """

    MIN_CONFIDENCE = 60.0  # Minimum confidence to use data

    def evaluate_session(self, dataset: pd.DataFrame, problem_type: str) -> GateDecision:
        """
        Returns ACCEPT, REVIEW, or REJECT with reasoning.
        """
        validator = DataValidator()
        result = validator.validate_dataset(dataset, problem_type)

        if result.confidence >= 85:
            decision = "ACCEPT"
            reasoning = "High-quality data with strong confidence"
        elif result.confidence >= self.MIN_CONFIDENCE:
            decision = "REVIEW"
            reasoning = f"Acceptable quality but review recommended: {result.issues}"
        else:
            decision = "REJECT"
            reasoning = f"Low confidence ({result.confidence:.1f}): {result.issues}"

        return GateDecision(
            decision=decision,
            confidence=result.confidence,
            reasoning=reasoning,
            issues=result.issues
        )
```

---

## 3. Feature Engineering Pipeline

### 3.1 Feature Categories

**Raw Features** (Direct from sensors):
- `battery_level`, `temperature`, `voltage`, `current_now`
- `screen_on`, `is_charging`, `power_save_mode`
- `quality_score` from validation

**Derived Features** (Single-point calculations):
- `drain_rate_instant = (level_t - level_t-1) / time_delta_hours`
- `temp_delta_5min = temperature_t - temperature_t-5min`
- `voltage_normalized = (voltage - 3700) / 800`  # Normalize to [0, 1]

**Statistical Features** (Rolling windows):
- `drain_rate_mean_1h`, `drain_rate_std_1h`
- `temp_mean_1h`, `temp_max_1h`, `temp_std_1h`
- `level_slope_6h` (linear regression slope)

**Delta Features** (Pre/Post OTA comparison):
- `drain_rate_delta = drain_rate_post - drain_rate_pre`
- `temp_max_delta = temp_max_post - temp_max_pre`
- `health_delta = health_post - health_pre`

**Device-Normalized Features**:
- `drain_rate_zscore = (drain_rate - device_mean) / device_std`
- `temp_percentile_device = percentile_rank(temp, device_hist)`

**Temporal Features**:
- `hour_of_day`, `day_of_week`, `is_weekend`
- `time_since_charge`, `time_since_full_charge`
- `days_since_update`

**Correlation Features**:
- `thermal_drain_correlation` (rolling 24h)
- `screen_drain_ratio = drain_screen_on / drain_screen_off`

### 3.2 Feature Engineering Implementation

```python
class FeatureEngineer:
    """
    Transforms raw telemetry into ML-ready features.
    """

    def engineer_features(
        self,
        raw_data: pd.DataFrame,
        problem_type: str,
        device_baselines: Optional[Dict[str, DeviceBaseline]] = None
    ) -> pd.DataFrame:
        """
        Main feature engineering pipeline.
        """
        df = raw_data.copy()

        # Sort by device and timestamp
        df = df.sort_values(["device_id", "timestamp"])

        # 1. Derive instant features
        df = self._add_instant_features(df)

        # 2. Add rolling window features
        df = self._add_rolling_features(df)

        # 3. Add device-normalized features
        if device_baselines:
            df = self._add_normalized_features(df, device_baselines)

        # 4. Add temporal features
        df = self._add_temporal_features(df)

        # 5. Problem-specific features
        if problem_type == "ota_update_impact":
            df = self._add_ota_delta_features(df)
        elif problem_type == "battery_drain_detection":
            df = self._add_drain_correlation_features(df)
        elif problem_type == "thermal_regression":
            df = self._add_thermal_features(df)

        # 6. Feature selection (remove NaNs, low-variance features)
        df = self._select_features(df, problem_type)

        return df

    def _add_instant_features(self, df: pd.DataFrame) -> pd.DataFrame:
        """Compute instant derivatives."""
        for device in df["device_id"].unique():
            mask = df["device_id"] == device

            # Battery drain rate
            df.loc[mask, "drain_rate_instant"] = (
                -df.loc[mask, "battery_level"].diff() /
                (df.loc[mask, "timestamp"].diff() / 3600000)
            )

            # Temperature delta
            df.loc[mask, "temp_delta_5min"] = df.loc[mask, "temperature"].diff()

            # Voltage change rate
            df.loc[mask, "voltage_change_rate"] = (
                df.loc[mask, "voltage"].diff() /
                (df.loc[mask, "timestamp"].diff() / 1000)
            )

        # Normalize voltage
        df["voltage_normalized"] = (df["voltage"] - 3700) / 800

        return df

    def _add_rolling_features(self, df: pd.DataFrame) -> pd.DataFrame:
        """Compute rolling window statistics."""
        WINDOWS = {
            "1h": 12,   # Assuming 5-min sampling
            "6h": 72,
            "24h": 288
        }

        for device in df["device_id"].unique():
            mask = df["device_id"] == device
            device_df = df[mask]

            for window_name, window_size in WINDOWS.items():
                # Drain rate statistics
                df.loc[mask, f"drain_rate_mean_{window_name}"] = (
                    device_df["drain_rate_instant"].rolling(window_size, min_periods=3).mean()
                )
                df.loc[mask, f"drain_rate_std_{window_name}"] = (
                    device_df["drain_rate_instant"].rolling(window_size, min_periods=3).std()
                )

                # Temperature statistics
                df.loc[mask, f"temp_mean_{window_name}"] = (
                    device_df["temperature"].rolling(window_size, min_periods=3).mean()
                )
                df.loc[mask, f"temp_max_{window_name}"] = (
                    device_df["temperature"].rolling(window_size, min_periods=3).max()
                )
                df.loc[mask, f"temp_std_{window_name}"] = (
                    device_df["temperature"].rolling(window_size, min_periods=3).std()
                )

        return df

    def _add_normalized_features(
        self,
        df: pd.DataFrame,
        baselines: Dict[str, DeviceBaseline]
    ) -> pd.DataFrame:
        """Normalize features per device to remove device-specific biases."""
        for device_id, baseline in baselines.items():
            mask = df["device_id"] == device_id

            # Z-score normalization
            df.loc[mask, "drain_rate_zscore"] = (
                (df.loc[mask, "drain_rate_instant"] - baseline.mean_drain_rate) /
                (baseline.std_drain_rate + 1e-6)
            )

            df.loc[mask, "temp_zscore"] = (
                (df.loc[mask, "temperature"] - baseline.mean_temp) /
                (baseline.std_temp + 1e-6)
            )

        return df

    def _add_temporal_features(self, df: pd.DataFrame) -> pd.DataFrame:
        """Add time-based features."""
        df["datetime"] = pd.to_datetime(df["timestamp"], unit="ms")
        df["hour_of_day"] = df["datetime"].dt.hour
        df["day_of_week"] = df["datetime"].dt.dayofweek
        df["is_weekend"] = (df["day_of_week"] >= 5).astype(int)

        # Time since last charge
        for device in df["device_id"].unique():
            mask = df["device_id"] == device
            device_df = df[mask].copy()

            charge_events = device_df[device_df["is_charging"] == True].index
            df.loc[mask, "time_since_charge"] = 0

            for i, idx in enumerate(device_df.index):
                if idx in charge_events:
                    last_charge_time = df.loc[idx, "timestamp"]
                else:
                    relevant_charges = charge_events[charge_events < idx]
                    if len(relevant_charges) > 0:
                        last_charge_idx = relevant_charges[-1]
                        last_charge_time = df.loc[last_charge_idx, "timestamp"]
                        df.loc[idx, "time_since_charge"] = (
                            df.loc[idx, "timestamp"] - last_charge_time
                        ) / 3600000  # Hours

        return df

    def _add_ota_delta_features(self, df: pd.DataFrame) -> pd.DataFrame:
        """Add pre/post update delta features."""
        if "is_post_update" not in df.columns:
            return df

        for device in df["device_id"].unique():
            mask = df["device_id"] == device
            device_df = df[mask]

            pre_data = device_df[device_df["is_post_update"] == False]
            post_data = device_df[device_df["is_post_update"] == True]

            if len(pre_data) > 0 and len(post_data) > 0:
                # Compute baselines
                pre_drain_mean = pre_data["drain_rate_instant"].mean()
                post_drain_mean = post_data["drain_rate_instant"].mean()

                pre_temp_max = pre_data["temperature"].max()
                post_temp_max = post_data["temperature"].max()

                # Assign deltas
                df.loc[mask, "drain_rate_delta"] = post_drain_mean - pre_drain_mean
                df.loc[mask, "temp_max_delta"] = post_temp_max - pre_temp_max

        return df

    def _add_drain_correlation_features(self, df: pd.DataFrame) -> pd.DataFrame:
        """Add features correlating drain with other factors."""
        for device in df["device_id"].unique():
            mask = df["device_id"] == device
            device_df = df[mask]

            # Compute rolling correlation between temperature and drain
            window = 288  # 24h
            df.loc[mask, "thermal_drain_corr_24h"] = (
                device_df["temperature"]
                .rolling(window, min_periods=20)
                .corr(device_df["drain_rate_instant"])
            )

            # Screen-on vs screen-off drain ratio
            screen_on_drain = device_df[device_df["screen_on"] == True]["drain_rate_instant"].mean()
            screen_off_drain = device_df[device_df["screen_on"] == False]["drain_rate_instant"].mean()

            if screen_off_drain > 0:
                df.loc[mask, "screen_drain_ratio"] = screen_on_drain / screen_off_drain

        return df

    def _add_thermal_features(self, df: pd.DataFrame) -> pd.DataFrame:
        """Add thermal-specific features."""
        # Time spent in thermal zones
        df["temp_zone"] = pd.cut(
            df["temperature"],
            bins=[0, 300, 380, 420, 450, 1000],
            labels=["cool", "normal", "warm", "hot", "critical"]
        )

        # Temperature acceleration
        for device in df["device_id"].unique():
            mask = df["device_id"] == device
            df.loc[mask, "temp_acceleration"] = (
                df.loc[mask, "temperature"].diff().diff()
            )

        return df

    def _select_features(self, df: pd.DataFrame, problem_type: str) -> pd.DataFrame:
        """Remove low-quality features."""
        # Drop features with >50% NaN
        threshold = 0.5
        nan_ratio = df.isnull().sum() / len(df)
        features_to_keep = nan_ratio[nan_ratio < threshold].index

        df = df[features_to_keep]

        # Drop low-variance features
        numeric_cols = df.select_dtypes(include=[np.number]).columns
        for col in numeric_cols:
            if df[col].std() < 1e-6:
                df = df.drop(columns=[col])

        return df
```

### 3.3 Minimal High-Impact Feature Set

To avoid overfitting and ensure interpretability, we identify the **top 15-20 features** per problem type:

```python
HIGH_IMPACT_FEATURES = {
    "battery_drain_detection": [
        "drain_rate_mean_1h",
        "drain_rate_std_1h",
        "screen_drain_ratio",
        "thermal_drain_corr_24h",
        "temp_mean_1h",
        "time_since_charge",
        "hour_of_day",
        "is_weekend",
        "battery_level",
        "screen_on",
        "power_save_mode"
    ],
    "thermal_regression": [
        "temp_max_1h",
        "temp_mean_1h",
        "temp_std_1h",
        "temp_acceleration",
        "is_charging",
        "drain_rate_instant",
        "screen_on",
        "hour_of_day",
        "voltage_normalized",
        "time_since_charge"
    ],
    "ota_update_impact": [
        "drain_rate_delta",
        "temp_max_delta",
        "drain_rate_mean_6h",
        "temp_max_6h",
        "days_since_update",
        "screen_drain_ratio",
        "battery_level",
        "is_post_update"
    ]
}
```

---

## 4. Labeling Strategy

### 4.1 Semi-Supervised + Rule-Assisted Labeling

Labels are generated using a hybrid approach:

**Rule-Based Labels** (High Confidence):
```python
def generate_rule_based_labels(df: pd.DataFrame, problem_type: str) -> pd.DataFrame:
    """
    Apply deterministic rules to generate labels with confidence scores.
    """
    if problem_type == "battery_drain_detection":
        # Anomaly if drain rate > 15%/hr sustained for 1h
        df["label"] = (
            (df["drain_rate_mean_1h"] > 15) &
            (df["drain_rate_std_1h"] < 5)  # Sustained, not spike
        ).astype(int)

        # Confidence: higher for extreme values
        df["label_confidence"] = np.clip(
            (df["drain_rate_mean_1h"] - 10) / 15,  # Scale 10-25%/hr to 0-1
            0.5,
            1.0
        )

    elif problem_type == "thermal_regression":
        # Thermal issue if temp > 43°C OR temp increased >8°C from baseline
        df["label"] = (
            (df["temp_max_1h"] > 430) |
            ((df["temp_max_1h"] - df["temp_mean_baseline"]) > 80)
        ).astype(int)

        df["label_confidence"] = np.clip(
            (df["temp_max_1h"] - 400) / 100,  # Scale 40-50°C to 0-1
            0.6,
            1.0
        )

    elif problem_type == "ota_update_impact":
        # Regression if drain increased >2%/hr or temp increased >3°C
        df["label"] = (
            (df["drain_rate_delta"] > 2) |
            (df["temp_max_delta"] > 30)
        ).astype(int)

        df["label_confidence"] = np.clip(
            abs(df["drain_rate_delta"]) / 5,  # Scale 0-5%/hr to 0-1
            0.6,
            1.0
        )

    return df
```

**Semi-Supervised Refinement**:
```python
def refine_labels_with_clustering(df: pd.DataFrame, features: List[str]) -> pd.DataFrame:
    """
    Use clustering to identify ambiguous cases that need expert review.
    """
    from sklearn.cluster import DBSCAN
    from sklearn.preprocessing import StandardScaler

    # Standardize features
    scaler = StandardScaler()
    X_scaled = scaler.fit_transform(df[features])

    # Apply DBSCAN to find outliers
    clustering = DBSCAN(eps=0.5, min_samples=10)
    df["cluster"] = clustering.fit_predict(X_scaled)

    # Mark outliers (cluster == -1) as needing review
    df["needs_review"] = (df["cluster"] == -1)

    # For outliers with rule-based labels, reduce confidence
    df.loc[df["needs_review"], "label_confidence"] *= 0.7

    return df
```

### 4.2 Handling Ambiguous Cases

```python
class LabelReviewQueue:
    """
    Manages cases that need expert review.
    """

    def queue_for_review(
        self,
        df: pd.DataFrame,
        confidence_threshold: float = 0.7
    ) -> pd.DataFrame:
        """
        Separate high-confidence from review-needed samples.
        """
        high_confidence = df[df["label_confidence"] >= confidence_threshold]
        review_queue = df[df["label_confidence"] < confidence_threshold]

        # Store review queue for expert labeling
        review_queue.to_csv("data/review_queue.csv", index=False)

        return high_confidence
```

### 4.3 Confidence-Weighted Training

Labels include confidence scores that are used during model training:

```python
# In model training:
sample_weights = df["label_confidence"].values
model.fit(X_train, y_train, sample_weight=sample_weights)
```

---

## 5. Model Selection (No Deep Learning First)

### 5.1 Recommended Models Per Problem Type

**Battery Drain Anomaly Detection**:
- **Primary**: Gradient Boosting Decision Trees (LightGBM/XGBoost)
- **Justification**:
  - Handles non-linear relationships (drain vs temp, screen state)
  - Native feature importance for explainability
  - Robust to missing data
  - Fast inference for OEM production use
- **Alternative**: Random Forest (more stable, less overfitting)

**Thermal Regression Detection**:
- **Primary**: Random Forest Classifier
- **Justification**:
  - Multiple decision paths provide human-readable rules
  - Less sensitive to outliers than GBDT
  - Can output probability distributions for confidence
- **Alternative**: Isolation Forest (unsupervised, for novelty detection)

**Performance Degradation**:
- **Primary**: Gradient Boosting Regressor (for health score prediction)
- **Justification**:
  - Predicts continuous health degradation rate
  - Captures complex temporal patterns
  - SHAP values explain feature contributions
- **Secondary**: XGBoost Classifier (for binary degradation detection)

**OTA Update Impact**:
- **Primary**: Paired t-test + Random Forest
- **Justification**:
  - Statistical test provides baseline confidence
  - Random Forest learns device-specific patterns
  - Explainable: "drain increased because X, Y, Z"
- **Alternative**: Logistic Regression (for simple regression/improvement classification)

**App Misbehavior Detection**:
- **Primary**: XGBoost Classifier
- **Justification**:
  - Multi-class classification (normal, inefficient, problematic)
  - Feature importance identifies misbehaving app characteristics
  - Fast training on sparse app usage data

### 5.2 Model Configuration Examples

```python
MODEL_CONFIGS = {
    "battery_drain_lgbm": {
        "model_type": "lightgbm.LGBMClassifier",
        "params": {
            "n_estimators": 200,
            "max_depth": 7,
            "learning_rate": 0.05,
            "num_leaves": 31,
            "min_child_samples": 20,
            "subsample": 0.8,
            "colsample_bytree": 0.8,
            "objective": "binary",
            "metric": "auc",
            "random_state": 42
        },
        "justification": "Balanced complexity for 1000+ samples, prevents overfitting"
    },
    "thermal_random_forest": {
        "model_type": "sklearn.ensemble.RandomForestClassifier",
        "params": {
            "n_estimators": 150,
            "max_depth": 10,
            "min_samples_split": 10,
            "min_samples_leaf": 5,
            "max_features": "sqrt",
            "class_weight": "balanced",
            "random_state": 42
        },
        "justification": "Robust to noise, interpretable trees, handles class imbalance"
    },
    "ota_impact_xgboost": {
        "model_type": "xgboost.XGBClassifier",
        "params": {
            "n_estimators": 150,
            "max_depth": 6,
            "learning_rate": 0.1,
            "subsample": 0.8,
            "colsample_bytree": 0.8,
            "gamma": 1,
            "objective": "binary:logistic",
            "eval_metric": "logloss",
            "random_state": 42
        },
        "justification": "Fast convergence, regularization prevents overfitting on small post-OTA datasets"
    }
}
```

---

## 6. Training Strategy

### 6.1 Cross-Device Validation

**Challenge**: Different devices have different baselines (battery capacity, thermal characteristics).

**Solution**: Device-stratified k-fold cross-validation

```python
from sklearn.model_selection import GroupKFold

def cross_device_validation(X, y, device_ids, model, n_splits=5):
    """
    Ensure each fold has different devices in train/val.
    """
    gkf = GroupKFold(n_splits=n_splits)

    scores = []
    for train_idx, val_idx in gkf.split(X, y, groups=device_ids):
        X_train, X_val = X.iloc[train_idx], X.iloc[val_idx]
        y_train, y_val = y.iloc[train_idx], y.iloc[val_idx]

        model.fit(X_train, y_train)
        val_score = model.score(X_val, y_val)
        scores.append(val_score)

    return np.mean(scores), np.std(scores)
```

### 6.2 Time-Aware Train/Validation Split

**Challenge**: OTA updates and battery aging introduce temporal drift.

**Solution**: Time-based split (train on old data, validate on recent data)

```python
def time_aware_split(df, train_ratio=0.8):
    """
    Split by timestamp to simulate real-world deployment.
    """
    df = df.sort_values("timestamp")
    split_idx = int(len(df) * train_ratio)

    train_df = df.iloc[:split_idx]
    val_df = df.iloc[split_idx:]

    return train_df, val_df
```

### 6.3 Metrics and Evaluation

**Primary Metrics**:
- **Accuracy**: ≥90% (target)
- **Precision**: ≥85% (minimize false alarms for OEMs)
- **Recall**: ≥85% (catch real issues)
- **F1 Score**: ≥87% (balanced)
- **AUC-ROC**: ≥0.92 (discrimination ability)

**False Positive Control**:
```python
from sklearn.metrics import precision_recall_curve

def optimize_threshold_for_precision(y_true, y_prob, target_precision=0.90):
    """
    Find probability threshold that achieves target precision.
    """
    precisions, recalls, thresholds = precision_recall_curve(y_true, y_prob)

    # Find threshold where precision >= target
    valid_idx = np.where(precisions >= target_precision)[0]
    if len(valid_idx) == 0:
        return 0.5  # Fallback

    best_idx = valid_idx[np.argmax(recalls[valid_idx])]
    return thresholds[best_idx]
```

### 6.4 Minimum Dataset Size for 90%+ Accuracy

Based on empirical ML research and HexNil's data characteristics:

| Problem Type | Min Samples (Train) | Min Devices | Min Days | Justification |
|--------------|---------------------|-------------|----------|---------------|
| Battery Drain | 1000 | 5 | 7 | Need diverse usage patterns across devices |
| Thermal | 500 | 5 | 5 | Thermal events are rarer, less data needed |
| Performance Degradation | 1000 | 5 | 14 | Degradation is slow, need longer observation |
| OTA Impact | 300 (pre) + 300 (post) | 3 | 7+7 | Paired comparison reduces sample requirement |

**Scaling Expectations**:
- **500 samples**: 75-80% accuracy
- **1000 samples**: 85-90% accuracy
- **2000+ samples**: 90-95% accuracy

---

## 7. Confidence & Explainability Layer

### 7.1 Prediction Confidence Calibration

```python
from sklearn.calibration import CalibratedClassifierCV

def calibrate_model(base_model, X_train, y_train):
    """
    Calibrate probability outputs to match true frequencies.
    """
    calibrated_model = CalibratedClassifierCV(
        base_model,
        method="isotonic",  # Non-parametric calibration
        cv=5
    )
    calibrated_model.fit(X_train, y_train)
    return calibrated_model
```

**Confidence Output**:
```python
def predict_with_confidence(model, X):
    """
    Return prediction + calibrated confidence.
    """
    probabilities = model.predict_proba(X)
    predictions = model.predict(X)
    confidences = np.max(probabilities, axis=1)

    return predictions, confidences
```

### 7.2 Feature Attribution with SHAP

```python
import shap

def explain_prediction(model, X_sample, feature_names):
    """
    Generate human-readable explanation using SHAP values.
    """
    explainer = shap.TreeExplainer(model)
    shap_values = explainer.shap_values(X_sample)

    # Get top contributing features
    feature_importance = pd.DataFrame({
        "feature": feature_names,
        "impact": shap_values[0]
    }).sort_values("impact", ascending=False)

    return feature_importance.head(5)
```

**Example Explanation Output**:
```
Prediction: BATTERY_DRAIN_ANOMALY (Confidence: 92%)

Top Contributing Factors:
1. drain_rate_mean_1h = 18.3%/hr (expected: 8.5%/hr) → +0.35 impact
2. thermal_drain_corr_24h = 0.82 (high correlation) → +0.18 impact
3. screen_drain_ratio = 4.2 (screen drains 4x faster than idle) → +0.12 impact
4. temp_mean_1h = 41.2°C (elevated) → +0.08 impact
5. time_since_charge = 8.2 hours (prolonged drain) → +0.05 impact

Reasoning: Device is draining significantly faster than baseline (18.3 vs 8.5%/hr),
strongly correlated with elevated temperature (41.2°C). Screen usage pattern shows
abnormal drain disparity compared to idle state.

Recommendation: Investigate thermal-intensive apps running during screen-on periods.
```

### 7.3 Uncertainty Quantification

```python
def compute_uncertainty(model, X, n_estimators=100):
    """
    Use ensemble disagreement as uncertainty measure.
    """
    # For tree ensembles, get per-tree predictions
    tree_predictions = np.array([
        tree.predict(X) for tree in model.estimators_
    ])

    # Uncertainty = proportion of trees that disagree with majority
    majority_vote = np.apply_along_axis(
        lambda x: np.bincount(x).argmax(),
        axis=0,
        arr=tree_predictions
    )

    uncertainty = 1 - np.mean(tree_predictions == majority_vote, axis=0)

    return uncertainty
```

---

## 8. Failure & Safety Handling

### 8.1 Low-Confidence Rejection

```python
class SafetyGate:
    """
    Prevents low-confidence predictions from reaching users.
    """

    MIN_CONFIDENCE_THRESHOLD = 0.70  # 70% minimum

    def gate_prediction(self, prediction, confidence, uncertainty):
        """
        Returns ACCEPT, FALLBACK, or REJECT.
        """
        if confidence >= 0.85 and uncertainty < 0.15:
            return "ACCEPT", "High confidence, low uncertainty"

        elif confidence >= self.MIN_CONFIDENCE_THRESHOLD:
            return "FALLBACK", f"Moderate confidence ({confidence:.2f}), use deterministic rules as backup"

        else:
            return "REJECT", f"Low confidence ({confidence:.2f}), insufficient data"
```

### 8.2 Fallback to Deterministic Rules

```python
def safe_prediction_with_fallback(model, X, fallback_rules):
    """
    Use ML model if confident, otherwise apply deterministic rules.
    """
    prediction, confidence = predict_with_confidence(model, X)
    uncertainty = compute_uncertainty(model, X)

    gate = SafetyGate()
    decision, reason = gate.gate_prediction(prediction, confidence, uncertainty)

    if decision == "ACCEPT":
        return prediction, confidence, reason

    elif decision == "FALLBACK":
        # Apply deterministic rules from DeterministicAnalyzer
        fallback_prediction = fallback_rules.evaluate(X)
        return fallback_prediction, 0.6, f"ML uncertain, used rule-based: {reason}"

    else:  # REJECT
        return None, 0.0, f"Cannot make prediction: {reason}"
```

### 8.3 Alert Generation for Uncertainty

```python
def generate_uncertainty_alert(device_id, prediction_result):
    """
    Log and alert when model is uncertain.
    """
    if prediction_result["confidence"] < 0.70:
        alert = {
            "device_id": device_id,
            "timestamp": utc_ms(),
            "alert_type": "MODEL_UNCERTAINTY",
            "confidence": prediction_result["confidence"],
            "reason": prediction_result["reason"],
            "action_required": "Collect more data or review manually"
        }

        # Store in ai_anomalies table for OEM review
        store_alert(alert)
```

### 8.4 Audit Trail

```python
def log_prediction(device_id, prediction_result, features_used):
    """
    Store full prediction details for auditing.
    """
    record = {
        "device_id": device_id,
        "timestamp": utc_ms(),
        "prediction": prediction_result["prediction"],
        "confidence": prediction_result["confidence"],
        "uncertainty": prediction_result["uncertainty"],
        "features_json": json.dumps(features_used.to_dict()),
        "shap_values_json": json.dumps(prediction_result["shap_values"]),
        "model_version": MODEL_VERSION,
        "gate_decision": prediction_result["gate_decision"]
    }

    # Store in ml_prediction_audit table
    store_audit_record(record)
```

---

## 9. Continuous Learning Plan

### 9.1 Retraining Triggers

```python
class RetrainingOrchestrator:
    """
    Determines when models need retraining.
    """

    def check_retraining_needed(self, model_metadata):
        """
        Returns True if retraining should be triggered.
        """
        triggers = [
            self._check_model_age(model_metadata),
            self._check_data_drift(model_metadata),
            self._check_performance_degradation(model_metadata),
            self._check_ota_update_deployed(model_metadata)
        ]

        return any(triggers)

    def _check_model_age(self, metadata):
        """Retrain every 30 days."""
        days_since_training = (utc_ms() - metadata["trained_at"]) / 86400000
        return days_since_training > 30

    def _check_data_drift(self, metadata):
        """Detect feature distribution changes."""
        current_stats = compute_feature_statistics(recent_data)
        training_stats = metadata["training_feature_stats"]

        # KL divergence to detect drift
        drift_score = compute_kl_divergence(current_stats, training_stats)
        return drift_score > 0.2

    def _check_performance_degradation(self, metadata):
        """Retrain if validation accuracy drops."""
        recent_accuracy = evaluate_on_recent_data(metadata["model"])
        training_accuracy = metadata["validation_accuracy"]

        return recent_accuracy < training_accuracy - 0.05  # 5% drop

    def _check_ota_update_deployed(self, metadata):
        """Force retrain after major OTA updates."""
        latest_update = get_latest_ota_version()
        model_update = metadata["trained_on_ota_version"]

        return latest_update != model_update
```

### 9.2 Model Drift Detection

```python
from scipy.stats import ks_2samp

def detect_feature_drift(train_data, production_data, feature_name):
    """
    Use Kolmogorov-Smirnov test to detect distribution changes.
    """
    train_values = train_data[feature_name].dropna()
    prod_values = production_data[feature_name].dropna()

    statistic, p_value = ks_2samp(train_values, prod_values)

    # p_value < 0.05 indicates significant distribution change
    return {
        "feature": feature_name,
        "ks_statistic": statistic,
        "p_value": p_value,
        "drift_detected": p_value < 0.05
    }
```

### 9.3 A/B Testing Framework

```python
class ModelABTest:
    """
    Gradually roll out new models while monitoring performance.
    """

    def deploy_with_ab_test(self, new_model, old_model, rollout_pct=0.10):
        """
        Route 10% of traffic to new model, 90% to old model.
        """
        self.new_model = new_model
        self.old_model = old_model
        self.rollout_pct = rollout_pct

    def predict(self, X, device_id):
        """
        Route prediction based on device ID hash.
        """
        hash_val = hash(device_id) % 100

        if hash_val < self.rollout_pct * 100:
            # Use new model
            prediction, confidence = self.new_model.predict_with_confidence(X)
            model_version = "v2_candidate"
        else:
            # Use old model
            prediction, confidence = self.old_model.predict_with_confidence(X)
            model_version = "v1_stable"

        # Log for comparison
        log_ab_prediction(device_id, prediction, confidence, model_version)

        return prediction, confidence
```

### 9.4 Dataset and Model Versioning

```python
import hashlib

class MLVersionControl:
    """
    Version control for datasets and models.
    """

    def version_dataset(self, dataset_df):
        """
        Generate unique hash for dataset version.
        """
        # Hash based on data content + metadata
        data_hash = hashlib.sha256(
            dataset_df.to_json().encode()
        ).hexdigest()[:12]

        metadata = {
            "dataset_id": f"hexnil_data_{data_hash}",
            "created_at": utc_ms(),
            "n_samples": len(dataset_df),
            "n_devices": dataset_df["device_id"].nunique(),
            "date_range": (dataset_df["timestamp"].min(), dataset_df["timestamp"].max()),
            "feature_schema": list(dataset_df.columns)
        }

        # Store metadata
        store_dataset_version(metadata)

        return metadata["dataset_id"]

    def version_model(self, model, training_metadata):
        """
        Version trained model with full reproducibility info.
        """
        model_version = f"v{training_metadata['model_type']}_{utc_ms()}"

        metadata = {
            "model_id": model_version,
            "model_type": training_metadata["model_type"],
            "trained_at": utc_ms(),
            "dataset_id": training_metadata["dataset_id"],
            "hyperparameters": training_metadata["params"],
            "validation_accuracy": training_metadata["accuracy"],
            "feature_list": training_metadata["features"],
            "training_duration_sec": training_metadata["duration"]
        }

        # Store model artifact + metadata
        save_model(model, model_version)
        store_model_metadata(metadata)

        return model_version
```

---

## 10. OEM-Grade Justifications

### 10.1 Why This Pipeline is OEM-Ready

**Data Quality Assurance**:
- Explicit confidence scoring on all data (60+ threshold)
- Validation checks for corruption, gaps, and sensor failures
- Rejection of low-quality sessions prevents garbage-in-garbage-out

**Explainability First**:
- SHAP values provide feature-level attribution
- Human-readable reasoning for every prediction
- Deterministic fallback rules for safety

**Reproducibility**:
- Dataset versioning with content hashing
- Model versioning with full hyperparameter tracking
- Time-aware splits prevent data leakage

**Safety Mechanisms**:
- Confidence thresholds prevent misdiagnosis
- Uncertainty quantification flags ambiguous cases
- Audit trail for regulatory compliance

**OEM Fleet Compatibility**:
- Cross-device validation prevents device-specific overfitting
- Device-normalized features handle hardware variance
- Works with tabular data (no GPU infrastructure needed)

### 10.2 Risk Mitigation

| Risk | Mitigation Strategy |
|------|---------------------|
| **Overfitting to specific devices** | Cross-device validation + device normalization |
| **Temporal drift from OTA updates** | Time-aware splits + automatic retraining triggers |
| **False positive alarms** | Precision-optimized thresholds + confidence gating |
| **Silent misdiagnosis** | Uncertainty quantification + audit trail |
| **Black-box predictions** | SHAP explanations + feature importance |
| **Data poisoning** | Validation checks + quality scoring |
| **Model staleness** | Drift detection + scheduled retraining |

### 10.3 Minimum Viable Deployment

**Phase 1: Deterministic Baseline** (No ML)
- Deploy `DeterministicAnalyzer` rules only
- Establish accuracy baseline (expected: 70-75%)
- Collect labeled production data

**Phase 2: ML Augmentation** (ML + Fallback)
- Train initial models on 1000+ samples
- Use SafetyGate with 70% confidence threshold
- Fallback to Phase 1 rules when uncertain
- Expected accuracy: 85-90%

**Phase 3: Full ML Production** (ML Primary)
- Achieve 90%+ accuracy with 2000+ samples
- Lower confidence threshold to 60%
- Deterministic rules only for edge cases
- Continuous learning activated

---

## 11. Implementation Roadmap

### 11.1 Prerequisites

**Infrastructure**:
- MySQL database with HexNil schema
- Python 3.8+ environment
- ML libraries: `scikit-learn`, `lightgbm`, `xgboost`, `shap`

**Data Requirements**:
- Minimum 1000 labeled samples per problem type
- At least 5 different devices
- 7+ days of continuous telemetry

### 11.2 Development Phases

**Week 1-2: Data Pipeline**
- Implement `DataValidator`
- Implement `FeatureEngineer`
- Create labeling scripts
- Generate first training dataset

**Week 3-4: Model Development**
- Train baseline models (Random Forest, LGBM)
- Implement cross-device validation
- Tune hyperparameters
- Achieve 85%+ accuracy

**Week 5-6: Explainability & Safety**
- Integrate SHAP explanations
- Implement `SafetyGate`
- Add confidence calibration
- Create audit logging

**Week 7-8: Continuous Learning**
- Implement drift detection
- Build retraining pipeline
- Set up A/B testing framework
- Deploy version control

### 11.3 Success Metrics

**Technical Metrics**:
- [ ] Accuracy ≥ 90% on held-out test set
- [ ] Precision ≥ 85% (false positive rate < 15%)
- [ ] Recall ≥ 85% (false negative rate < 15%)
- [ ] Average prediction confidence ≥ 75%
- [ ] Inference latency < 100ms per prediction

**OEM Metrics**:
- [ ] 100% of predictions have human-readable explanations
- [ ] Zero silent misdiagnoses (all low-confidence cases flagged)
- [ ] Model retraining completes within 4 hours
- [ ] Dataset quality confidence ≥ 80% on production data
- [ ] Full audit trail for all predictions

---

## 12. Appendix: Python Module Structure

```
backend/
├── ml_pipeline/
│   ├── __init__.py
│   ├── data_validation.py         # DataValidator, SessionQualityGate
│   ├── feature_engineering.py     # FeatureEngineer
│   ├── labeling.py                # Label generation, review queue
│   ├── models.py                  # Model configs, training functions
│   ├── training.py                # Cross-device validation, time splits
│   ├── explainability.py          # SHAP, confidence calibration
│   ├── safety.py                  # SafetyGate, fallback logic
│   ├── continuous_learning.py     # Retraining, drift detection
│   └── version_control.py         # Dataset/model versioning
├── ml_models/                     # Trained model artifacts
│   ├── battery_drain_v1.pkl
│   ├── thermal_v1.pkl
│   └── ota_impact_v1.pkl
└── ml_datasets/                   # Versioned training datasets
    ├── hexnil_data_a1b2c3d4.parquet
    └── metadata.json
```

---

## Conclusion

This ML training pipeline is designed for **OEM production readiness**, prioritizing:
1. **Accuracy** through rigorous validation and cross-device testing
2. **Explainability** through SHAP values and deterministic fallbacks
3. **Safety** through confidence gating and uncertainty quantification
4. **Reproducibility** through version control and audit trails
5. **Continuous improvement** through automated retraining and drift detection

By following this architecture, HexNil achieves ≥90% prediction accuracy while maintaining the trust and debuggability required for OEM deployment.

---

*Document Version: 1.0.0*
*Author: Principal ML Engineering Team*
*Classification: OEM-Internal Use*
