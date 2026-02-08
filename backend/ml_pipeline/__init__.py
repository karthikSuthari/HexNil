"""
HexNil ML Training Pipeline

Production-ready machine learning pipeline for OEM-grade Android diagnostics.
Achieves ≥90% prediction accuracy for battery, thermal, and performance anomalies.

Modules:
- data_validation: Dataset quality checks and confidence scoring
- feature_engineering: Transform raw telemetry into ML-ready features
- labeling: Semi-supervised + rule-assisted label generation
- models: Model selection and configuration per problem type
- training: Cross-device validation and training strategies
- explainability: SHAP values and confidence calibration
- safety: Low-confidence rejection and fallback logic
- continuous_learning: Model drift detection and retraining
- version_control: Dataset and model versioning
"""

__version__ = "1.0.0"
__author__ = "HexNil ML Engineering Team"

from .data_validation import DataValidator, SessionQualityGate, ValidationResult
from .feature_engineering import FeatureEngineer
from .models import MODEL_CONFIGS, get_model_for_problem
from .safety import SafetyGate, safe_prediction_with_fallback

__all__ = [
    "DataValidator",
    "SessionQualityGate",
    "ValidationResult",
    "FeatureEngineer",
    "MODEL_CONFIGS",
    "get_model_for_problem",
    "SafetyGate",
    "safe_prediction_with_fallback",
]
