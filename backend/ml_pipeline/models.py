"""
Model Selection and Configuration

Problem-specific model recommendations with OEM-grade justifications.
No deep learning - focus on explainable tree-based models.
"""

from __future__ import annotations

from typing import Dict, Any, Tuple
import logging

logger = logging.getLogger("hexnil.ml.models")


# Model configurations per problem type
MODEL_CONFIGS = {
    "battery_drain_lgbm": {
        "model_type": "lightgbm",
        "class": "LGBMClassifier",
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
            "random_state": 42,
            "verbose": -1,
        },
        "justification": (
            "Balanced complexity for 1000+ samples. Handles non-linear drain patterns. "
            "Fast inference (<50ms). Native feature importance for explainability."
        ),
    },
    "thermal_random_forest": {
        "model_type": "sklearn",
        "class": "RandomForestClassifier",
        "params": {
            "n_estimators": 150,
            "max_depth": 10,
            "min_samples_split": 10,
            "min_samples_leaf": 5,
            "max_features": "sqrt",
            "class_weight": "balanced",
            "random_state": 42,
            "n_jobs": -1,
        },
        "justification": (
            "Robust to thermal noise. Multiple decision paths provide human-readable rules. "
            "Handles class imbalance. Less prone to overfitting than GBDT."
        ),
    },
    "ota_impact_xgboost": {
        "model_type": "xgboost",
        "class": "XGBClassifier",
        "params": {
            "n_estimators": 150,
            "max_depth": 6,
            "learning_rate": 0.1,
            "subsample": 0.8,
            "colsample_bytree": 0.8,
            "gamma": 1,
            "objective": "binary:logistic",
            "eval_metric": "logloss",
            "random_state": 42,
            "use_label_encoder": False,
        },
        "justification": (
            "Fast convergence on small post-OTA datasets. Regularization prevents overfitting. "
            "Paired with statistical t-test for baseline confidence."
        ),
    },
    "performance_gbm_regressor": {
        "model_type": "sklearn",
        "class": "GradientBoostingRegressor",
        "params": {
            "n_estimators": 200,
            "max_depth": 6,
            "learning_rate": 0.05,
            "min_samples_split": 10,
            "min_samples_leaf": 5,
            "subsample": 0.8,
            "random_state": 42,
        },
        "justification": (
            "Predicts continuous health degradation rate. Captures temporal patterns. "
            "SHAP values explain which factors contribute to degradation."
        ),
    },
    "app_misbehavior_xgboost": {
        "model_type": "xgboost",
        "class": "XGBClassifier",
        "params": {
            "n_estimators": 150,
            "max_depth": 7,
            "learning_rate": 0.1,
            "subsample": 0.8,
            "colsample_bytree": 0.8,
            "objective": "multi:softmax",
            "num_class": 3,  # normal, inefficient, problematic
            "eval_metric": "mlogloss",
            "random_state": 42,
            "use_label_encoder": False,
        },
        "justification": (
            "Multi-class classification for app behavior. Feature importance identifies "
            "misbehaving app characteristics. Fast training on sparse app usage data."
        ),
    },
}


# Map problem types to recommended models
PROBLEM_TO_MODEL = {
    "battery_drain_detection": "battery_drain_lgbm",
    "thermal_regression": "thermal_random_forest",
    "ota_update_impact": "ota_impact_xgboost",
    "performance_degradation": "performance_gbm_regressor",
    "app_misbehavior": "app_misbehavior_xgboost",
}


def get_model_for_problem(problem_type: str) -> Tuple[str, Dict[str, Any]]:
    """
    Get recommended model configuration for a problem type.

    Args:
        problem_type: One of PROBLEM_TO_MODEL keys

    Returns:
        (model_name, config_dict) tuple

    Raises:
        ValueError: If problem type is unknown
    """
    if problem_type not in PROBLEM_TO_MODEL:
        raise ValueError(
            f"Unknown problem type: {problem_type}. "
            f"Available: {list(PROBLEM_TO_MODEL.keys())}"
        )

    model_name = PROBLEM_TO_MODEL[problem_type]
    config = MODEL_CONFIGS[model_name]

    logger.info(
        f"Selected model for {problem_type}: {model_name} "
        f"({config['model_type']}.{config['class']})"
    )

    return model_name, config


def create_model(model_name: str):
    """
    Instantiate a model from configuration.

    Args:
        model_name: Key from MODEL_CONFIGS

    Returns:
        Instantiated model object
    """
    if model_name not in MODEL_CONFIGS:
        raise ValueError(f"Unknown model: {model_name}")

    config = MODEL_CONFIGS[model_name]
    model_type = config["model_type"]
    class_name = config["class"]
    params = config["params"]

    if model_type == "lightgbm":
        import lightgbm as lgb

        model = getattr(lgb, class_name)(**params)

    elif model_type == "xgboost":
        import xgboost as xgb

        model = getattr(xgb, class_name)(**params)

    elif model_type == "sklearn":
        from sklearn.ensemble import (
            RandomForestClassifier,
            GradientBoostingRegressor,
        )

        if class_name == "RandomForestClassifier":
            model = RandomForestClassifier(**params)
        elif class_name == "GradientBoostingRegressor":
            model = GradientBoostingRegressor(**params)
        else:
            raise ValueError(f"Unknown sklearn class: {class_name}")

    else:
        raise ValueError(f"Unknown model type: {model_type}")

    logger.info(f"Created model: {model_name} with {params.get('n_estimators', 'N/A')} estimators")

    return model


# High-impact feature sets per problem type
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
        "power_save_mode",
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
        "time_since_charge",
    ],
    "ota_update_impact": [
        "drain_rate_delta",
        "temp_max_delta",
        "drain_rate_mean_6h",
        "temp_max_6h",
        "days_since_update",
        "screen_drain_ratio",
        "battery_level",
        "is_post_update",
    ],
    "performance_degradation": [
        "health_percentage",
        "capacity_fade",
        "cycle_count",
        "drain_rate_mean_24h",
        "temp_max_24h",
        "fast_charge_count",
        "deep_discharge_count",
        "days_since_first_use",
    ],
    "app_misbehavior": [
        "foreground_time",
        "background_time",
        "battery_percent_used",
        "drain_per_hour",
        "wakelock_count",
        "network_bytes",
        "alarm_count",
        "is_system_app",
    ],
}


def get_feature_list(problem_type: str) -> list:
    """
    Get minimal high-impact feature set for a problem type.

    Args:
        problem_type: Problem being solved

    Returns:
        List of feature names
    """
    if problem_type not in HIGH_IMPACT_FEATURES:
        logger.warning(f"No feature list for {problem_type}, using empty list")
        return []

    features = HIGH_IMPACT_FEATURES[problem_type]
    logger.info(f"Feature list for {problem_type}: {len(features)} features")

    return features
