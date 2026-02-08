"""
Failure & Safety Handling

Prevents low-confidence predictions from reaching users.
Provides fallback to deterministic rules when ML is uncertain.
"""

from __future__ import annotations

import logging
from dataclasses import dataclass
from typing import Optional, Dict, Any

import numpy as np

logger = logging.getLogger("hexnil.ml.safety")


@dataclass
class PredictionResult:
    """Result of a prediction with confidence and metadata."""

    prediction: Any  # Model prediction (class, value, etc.)
    confidence: float  # 0.0-1.0
    uncertainty: float  # 0.0-1.0
    gate_decision: str  # ACCEPT, FALLBACK, REJECT
    reasoning: str
    shap_values: Optional[Dict[str, float]] = None
    fallback_used: bool = False


class SafetyGate:
    """
    Prevents low-confidence predictions from reaching users.

    Decision levels:
    - ACCEPT: High confidence (>= 0.85) and low uncertainty (< 0.15)
    - FALLBACK: Moderate confidence (>= 0.70), use deterministic rules as backup
    - REJECT: Low confidence (< 0.70), insufficient data
    """

    MIN_CONFIDENCE_THRESHOLD = 0.70  # 70% minimum
    HIGH_CONFIDENCE_THRESHOLD = 0.85  # 85% for full trust
    MAX_UNCERTAINTY_THRESHOLD = 0.15  # 15% maximum uncertainty

    def gate_prediction(
        self,
        prediction: Any,
        confidence: float,
        uncertainty: float,
    ) -> tuple[str, str]:
        """
        Returns (ACCEPT/FALLBACK/REJECT, reasoning).

        Args:
            prediction: Model prediction
            confidence: Prediction confidence (0-1)
            uncertainty: Model uncertainty (0-1)

        Returns:
            (decision, reasoning) tuple
        """
        if confidence >= self.HIGH_CONFIDENCE_THRESHOLD and uncertainty < self.MAX_UNCERTAINTY_THRESHOLD:
            decision = "ACCEPT"
            reasoning = f"High confidence ({confidence:.2f}), low uncertainty ({uncertainty:.2f})"

        elif confidence >= self.MIN_CONFIDENCE_THRESHOLD:
            decision = "FALLBACK"
            reasoning = (
                f"Moderate confidence ({confidence:.2f}), "
                f"use deterministic rules as backup"
            )

        else:
            decision = "REJECT"
            reasoning = f"Low confidence ({confidence:.2f}), insufficient data"

        logger.info(f"Safety gate: {decision} - {reasoning}")

        return decision, reasoning


def predict_with_confidence(model, X):
    """
    Return prediction + calibrated confidence.

    Args:
        model: Trained classifier with predict_proba method
        X: Input features

    Returns:
        (predictions, confidences) tuple
    """
    try:
        probabilities = model.predict_proba(X)
        predictions = model.predict(X)

        # Confidence = max probability per sample
        confidences = np.max(probabilities, axis=1)

        return predictions, confidences

    except AttributeError:
        # Model doesn't have predict_proba (e.g., regressor)
        predictions = model.predict(X)
        # For regressors, use dummy confidence
        confidences = np.full(len(predictions), 0.75)

        return predictions, confidences


def compute_uncertainty(model, X, n_estimators: Optional[int] = None):
    """
    Use ensemble disagreement as uncertainty measure.

    Args:
        model: Tree ensemble model (Random Forest, GBDT)
        X: Input features
        n_estimators: Number of trees to use (default: all)

    Returns:
        Array of uncertainty scores (0-1) per sample
    """
    try:
        # Get per-tree predictions
        if hasattr(model, "estimators_"):
            # Sklearn models
            trees = model.estimators_[:n_estimators] if n_estimators else model.estimators_
            tree_predictions = np.array([tree.predict(X) for tree in trees])

        elif hasattr(model, "predict") and hasattr(model, "n_estimators"):
            # LightGBM/XGBoost - approximate with prediction variance
            # Use iteration_range to get predictions from subsets
            n_trees = n_estimators or model.n_estimators
            step = max(1, n_trees // 10)

            tree_predictions = []
            for i in range(0, n_trees, step):
                # Predict using trees up to index i
                if hasattr(model, "predict_proba"):
                    probs = model.predict_proba(X, iteration_range=(0, i + step))
                    pred = np.argmax(probs, axis=1)
                else:
                    pred = model.predict(X, iteration_range=(0, i + step))
                tree_predictions.append(pred)

            tree_predictions = np.array(tree_predictions)

        else:
            # Fallback: use prediction probabilities as uncertainty proxy
            if hasattr(model, "predict_proba"):
                probs = model.predict_proba(X)
                # Uncertainty = 1 - max_prob (entropy-like)
                uncertainty = 1 - np.max(probs, axis=1)
                return uncertainty
            else:
                # Cannot compute uncertainty, return low values
                return np.full(len(X), 0.1)

        # Compute disagreement: proportion of trees that disagree with majority
        if len(tree_predictions.shape) == 2:
            majority_vote = np.apply_along_axis(
                lambda x: np.bincount(x.astype(int)).argmax(), axis=0, arr=tree_predictions
            )

            uncertainty = 1 - np.mean(tree_predictions == majority_vote, axis=0)
        else:  # pragma: no cover - tree_predictions always 2D from ensemble models
            # For regressors, use standard deviation
            uncertainty = np.std(tree_predictions, axis=0)
            # Normalize to [0, 1]
            if uncertainty.max() > 0:
                uncertainty = uncertainty / uncertainty.max()

        return uncertainty

    except Exception as e:
        logger.warning(f"Failed to compute uncertainty: {e}")
        return np.full(len(X), 0.2)  # Default moderate uncertainty


def safe_prediction_with_fallback(
    model,
    X,
    fallback_rules: Optional[Any] = None,
    return_shap: bool = False,
):
    """
    Use ML model if confident, otherwise apply deterministic rules.

    Args:
        model: Trained ML model
        X: Input features (DataFrame or array)
        fallback_rules: Deterministic rule function (X) -> predictions
        return_shap: Whether to compute SHAP values

    Returns:
        PredictionResult object
    """
    gate = SafetyGate()

    # Get predictions and confidence
    predictions, confidences = predict_with_confidence(model, X)

    # Compute uncertainty
    uncertainties = compute_uncertainty(model, X)

    # For single prediction, extract scalars
    if len(predictions) == 1:
        prediction = predictions[0]
        confidence = float(confidences[0])
        uncertainty = float(uncertainties[0])
    else:
        # Multiple predictions: use mean confidence/uncertainty
        prediction = predictions
        confidence = float(np.mean(confidences))
        uncertainty = float(np.mean(uncertainties))

    # Gate decision
    decision, reasoning = gate.gate_prediction(prediction, confidence, uncertainty)

    # Compute SHAP values if requested
    shap_values = None
    if return_shap and decision == "ACCEPT":
        try:
            import shap

            explainer = shap.TreeExplainer(model)
            shap_vals = explainer.shap_values(X)

            # For single prediction, convert to dict
            if len(X) == 1:
                feature_names = X.columns if hasattr(X, "columns") else [f"f{i}" for i in range(X.shape[1])]
                shap_values = dict(zip(feature_names, shap_vals[0]))

        except Exception as e:
            logger.warning(f"Failed to compute SHAP values: {e}")

    # Use fallback if needed
    fallback_used = False
    if decision == "FALLBACK" and fallback_rules is not None:
        logger.info("Using fallback deterministic rules")
        prediction = fallback_rules(X)
        confidence = 0.6  # Fixed confidence for rule-based
        fallback_used = True

    elif decision == "REJECT":
        logger.warning("Prediction rejected due to low confidence")
        prediction = None
        confidence = 0.0

    return PredictionResult(
        prediction=prediction,
        confidence=confidence,
        uncertainty=uncertainty,
        gate_decision=decision,
        reasoning=reasoning,
        shap_values=shap_values,
        fallback_used=fallback_used,
    )


def generate_uncertainty_alert(device_id: str, prediction_result: PredictionResult) -> Dict[str, Any]:
    """
    Generate alert when model is uncertain.

    Args:
        device_id: Device identifier
        prediction_result: Prediction result to check

    Returns:
        Alert dict if uncertainty is high, None otherwise
    """
    if prediction_result.confidence >= 0.70:
        return None  # No alert needed

    alert = {
        "device_id": device_id,
        "alert_type": "MODEL_UNCERTAINTY",
        "confidence": prediction_result.confidence,
        "uncertainty": prediction_result.uncertainty,
        "gate_decision": prediction_result.gate_decision,
        "reason": prediction_result.reasoning,
        "action_required": "Collect more data or review manually",
    }

    logger.warning(
        f"Uncertainty alert for {device_id}: "
        f"confidence={alert['confidence']:.2f}, "
        f"uncertainty={alert['uncertainty']:.2f}"
    )

    return alert
