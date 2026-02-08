"""
Data Validation & Trust Layer

Validates dataset quality and assigns confidence scores for ML training.
Ensures only high-quality data is used for model training and inference.
"""

from __future__ import annotations

import logging
from dataclasses import dataclass
from typing import Dict, List, Optional

import pandas as pd
import numpy as np

logger = logging.getLogger("hexnil.ml.validation")


# Minimum dataset requirements for 90%+ accuracy
MINIMUM_REQUIREMENTS = {
    "battery_drain_detection": {
        "min_snapshots": 1000,
        "min_devices": 5,
        "min_observation_hours": 72,
        "min_drain_events": 50,
        "required_tables": ["battery_snapshots", "drain_events", "app_battery_usage"],
    },
    "thermal_regression": {
        "min_snapshots": 500,
        "min_devices": 5,
        "min_observation_hours": 48,
        "min_thermal_events": 20,
        "required_tables": ["battery_snapshots", "thermal_events", "thermal_heat_buckets"],
    },
    "performance_degradation": {
        "min_snapshots": 1000,
        "min_devices": 5,
        "min_observation_hours": 96,
        "min_battery_health_records": 30,
        "required_tables": ["battery_snapshots", "battery_health", "charging_sessions"],
    },
    "ota_update_impact": {
        "min_snapshots_pre": 500,
        "min_snapshots_post": 500,
        "min_devices": 3,
        "min_days_pre": 7,
        "min_days_post": 7,
        "required_tables": ["battery_snapshots", "update_comparisons"],
    },
}


@dataclass
class Check:
    """Single validation check result."""

    name: str
    passed: bool
    score: float  # 0.0-1.0
    issue: Optional[str] = None


@dataclass
class ValidationResult:
    """Overall validation result with confidence score."""

    passed: bool
    confidence: float  # 0-100
    issues: List[str]

    def __repr__(self):
        status = "PASS" if self.passed else "FAIL"
        return f"ValidationResult({status}, confidence={self.confidence:.1f}, issues={len(self.issues)})"


@dataclass
class GateDecision:
    """Quality gate decision for dataset."""

    decision: str  # ACCEPT, REVIEW, REJECT
    confidence: float
    reasoning: str
    issues: List[str]


class DataValidator:
    """
    Validates data quality and assigns confidence scores.

    Checks:
    - Dataset completeness (minimum samples, devices, hours)
    - Temporal continuity (no large gaps)
    - Value ranges (physically valid sensor readings)
    - Corruption detection (stuck/zero sensors)
    - Baseline stability (for OTA comparisons)
    """

    def validate_dataset(self, dataset: pd.DataFrame, problem_type: str) -> ValidationResult:
        """
        Validates dataset and returns confidence score (0-100).

        Args:
            dataset: DataFrame with telemetry data
            problem_type: One of MINIMUM_REQUIREMENTS keys

        Returns:
            ValidationResult with pass/fail, confidence, and issues
        """
        if problem_type not in MINIMUM_REQUIREMENTS:
            logger.warning(f"Unknown problem type: {problem_type}")
            return ValidationResult(False, 0.0, [f"Unknown problem type: {problem_type}"])

        checks = [
            self._check_completeness(dataset, problem_type),
            self._check_temporal_continuity(dataset),
            self._check_value_ranges(dataset),
            self._check_corruption(dataset),
            self._check_baseline_stability(dataset),
        ]

        confidence = self._compute_confidence(checks)
        issues = [c.issue for c in checks if not c.passed and c.issue]

        passed = confidence >= 60.0  # Minimum threshold

        logger.info(
            f"Validation: {problem_type}, confidence={confidence:.1f}, "
            f"passed={passed}, issues={len(issues)}"
        )

        return ValidationResult(passed=passed, confidence=confidence, issues=issues)

    def _check_completeness(self, dataset: pd.DataFrame, problem_type: str) -> Check:
        """Verify minimum data requirements are met."""
        requirements = MINIMUM_REQUIREMENTS[problem_type]
        checks = {}

        # Check snapshots
        if "min_snapshots" in requirements:
            checks["snapshots"] = len(dataset) >= requirements["min_snapshots"]

        # Check devices
        if "min_devices" in requirements and "device_id" in dataset.columns:
            checks["devices"] = dataset["device_id"].nunique() >= requirements["min_devices"]

        # Check observation hours
        if "min_observation_hours" in requirements and "timestamp" in dataset.columns:
            observation_hours = self._observation_hours(dataset)
            checks["hours"] = observation_hours >= requirements["min_observation_hours"]

        # Check for OTA-specific requirements
        if "min_snapshots_pre" in requirements and "is_post_update" in dataset.columns:
            pre_count = (dataset["is_post_update"] == False).sum()
            post_count = (dataset["is_post_update"] == True).sum()
            checks["pre_ota"] = pre_count >= requirements["min_snapshots_pre"]
            checks["post_ota"] = post_count >= requirements["min_snapshots_post"]

        passed = all(checks.values()) if checks else False
        score = sum(checks.values()) / len(checks) if checks else 0.0

        issue = None if passed else f"Insufficient data: {checks}"

        return Check(name="completeness", passed=passed, score=score, issue=issue)

    def _check_temporal_continuity(self, dataset: pd.DataFrame) -> Check:
        """Detect gaps in time series that indicate data loss."""
        if "timestamp" not in dataset.columns:
            return Check("temporal_continuity", True, 1.0, None)

        dataset = dataset.sort_values("timestamp")
        time_diffs = dataset["timestamp"].diff()

        # Flag gaps > 2 hours (7200000 ms) as problematic
        large_gaps = (time_diffs > 7200000).sum()
        gap_ratio = large_gaps / max(len(dataset), 1)

        passed = gap_ratio < 0.1  # Allow up to 10% gaps
        score = max(0.0, 1.0 - gap_ratio)

        issue = f"Large time gaps: {large_gaps} ({gap_ratio:.1%})" if not passed else None

        return Check(name="temporal_continuity", passed=passed, score=score, issue=issue)

    def _check_value_ranges(self, dataset: pd.DataFrame) -> Check:
        """Validate sensor readings are within physically possible ranges."""
        issues = []

        # Battery level: 0-100%
        if "battery_level" in dataset.columns:
            invalid_levels = (
                (dataset["battery_level"] < 0) | (dataset["battery_level"] > 100)
            ).sum()
            if invalid_levels > 0:
                issues.append(f"Invalid battery levels: {invalid_levels}")

        # Temperature: 0-700 (0-70°C in tenths)
        if "temperature" in dataset.columns:
            invalid_temps = (
                (dataset["temperature"] < 0) | (dataset["temperature"] > 700)
            ).sum()
            if invalid_temps > 0:
                issues.append(f"Invalid temperatures: {invalid_temps}")

        # Voltage: 2500-4500 mV
        if "voltage" in dataset.columns:
            invalid_volts = (
                (dataset["voltage"] < 2500) | (dataset["voltage"] > 4500)
            ).sum()
            if invalid_volts > 0:
                issues.append(f"Invalid voltages: {invalid_volts}")

        passed = len(issues) == 0
        score = 1.0 if passed else 0.5

        return Check(
            name="value_ranges",
            passed=passed,
            score=score,
            issue="; ".join(issues) if issues else None,
        )

    def _check_corruption(self, dataset: pd.DataFrame) -> Check:
        """Detect corrupted or reset data patterns."""
        issues = []

        # Check for all-zero columns (sensor failure)
        for col in ["battery_level", "temperature", "voltage"]:
            if col in dataset.columns:
                zero_ratio = (dataset[col] == 0).sum() / max(len(dataset), 1)
                if zero_ratio > 0.5:
                    issues.append(f"{col} is mostly zeros ({zero_ratio:.1%})")

        # Check for constant values (stuck sensor)
        for col in ["battery_level", "temperature"]:
            if col in dataset.columns:
                unique_count = dataset[col].nunique()
                if unique_count < 5:
                    issues.append(f"{col} has too few unique values ({unique_count})")

        passed = len(issues) == 0
        score = 1.0 if passed else 0.3

        return Check(
            name="corruption",
            passed=passed,
            score=score,
            issue="; ".join(issues) if issues else None,
        )

    def _check_baseline_stability(self, dataset: pd.DataFrame) -> Check:
        """Ensure sufficient stable baseline period for comparison."""
        # Only relevant for OTA analysis
        if "is_post_update" not in dataset.columns:
            return Check("baseline_stability", True, 1.0, None)

        pre_data = dataset[dataset["is_post_update"] == False]
        if len(pre_data) < 100:
            return Check(
                "baseline_stability",
                False,
                0.5,
                "Insufficient pre-update baseline data",
            )

        # Check stability: coefficient of variation should be reasonable
        if "drain_rate_per_hour" in pre_data.columns:
            drain_mean = pre_data["drain_rate_per_hour"].mean()
            drain_std = pre_data["drain_rate_per_hour"].std()
            drain_cv = drain_std / (drain_mean + 1e-6) if drain_mean > 0 else 0

            if drain_cv > 2.0:
                return Check(
                    "baseline_stability",
                    False,
                    0.6,
                    f"High baseline variability (CV={drain_cv:.2f})",
                )

        return Check("baseline_stability", True, 1.0, None)

    def _observation_hours(self, dataset: pd.DataFrame) -> float:
        """Calculate total observation hours from dataset."""
        if "timestamp" not in dataset.columns or len(dataset) < 2:
            return 0.0

        time_span_ms = dataset["timestamp"].max() - dataset["timestamp"].min()
        return time_span_ms / 3600000  # Convert to hours

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
            "baseline_stability": 0.10,
        }

        total_score = sum(c.score * weights.get(c.name, 0.1) for c in checks)

        return min(100.0, total_score * 100)


class SessionQualityGate:
    """
    Determines if a data session is trustworthy for training/inference.

    Decision levels:
    - ACCEPT: High-quality data (confidence >= 85%)
    - REVIEW: Acceptable quality but needs review (60% <= confidence < 85%)
    - REJECT: Low quality, do not use (confidence < 60%)
    """

    MIN_CONFIDENCE = 60.0  # Minimum confidence to use data

    def evaluate_session(self, dataset: pd.DataFrame, problem_type: str) -> GateDecision:
        """
        Returns ACCEPT, REVIEW, or REJECT with reasoning.

        Args:
            dataset: DataFrame with telemetry data
            problem_type: Problem being solved

        Returns:
            GateDecision with decision, confidence, reasoning
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

        logger.info(
            f"Quality gate: {decision}, confidence={result.confidence:.1f}, "
            f"problem={problem_type}"
        )

        return GateDecision(
            decision=decision,
            confidence=result.confidence,
            reasoning=reasoning,
            issues=result.issues,
        )
