"""
Feature Engineering

Transform raw telemetry data into ML-ready features.
Computes rolling statistics, ratios, and temporal features
from raw sensor snapshots.
"""

from __future__ import annotations

import logging
from typing import Dict, List, Optional

import pandas as pd
import numpy as np

logger = logging.getLogger("hexnil.ml.features")


class FeatureEngineer:
    """
    Transforms raw telemetry snapshots into ML-ready feature vectors.

    Computes:
    - Rolling statistics (mean, std, max over time windows)
    - Rate-of-change features (drain rate, thermal acceleration)
    - Ratio features (screen drain ratio, charge efficiency)
    - Temporal features (hour of day, weekend flag)
    - Delta features for OTA comparisons
    """

    # Default rolling window sizes in milliseconds
    WINDOW_1H = 3_600_000
    WINDOW_6H = 21_600_000
    WINDOW_24H = 86_400_000

    def __init__(self, window_sizes: Optional[Dict[str, int]] = None):
        """
        Args:
            window_sizes: Custom window sizes in ms. Keys: '1h', '6h', '24h'.
        """
        if window_sizes:
            self.WINDOW_1H = window_sizes.get("1h", self.WINDOW_1H)
            self.WINDOW_6H = window_sizes.get("6h", self.WINDOW_6H)
            self.WINDOW_24H = window_sizes.get("24h", self.WINDOW_24H)

    def transform(self, df: pd.DataFrame, problem_type: str) -> pd.DataFrame:
        """
        Apply feature engineering pipeline for a given problem type.

        Args:
            df: Raw telemetry DataFrame with timestamp, battery_level, temperature, etc.
            problem_type: Problem type to engineer features for.

        Returns:
            DataFrame with engineered features appended.
        """
        df = df.copy()
        df = df.sort_values("timestamp").reset_index(drop=True)

        # Common temporal features
        df = self._add_temporal_features(df)

        # Rolling statistics
        df = self._add_rolling_stats(df)

        # Problem-specific features
        if problem_type == "battery_drain_detection":
            df = self._add_battery_drain_features(df)
        elif problem_type == "thermal_regression":
            df = self._add_thermal_features(df)
        elif problem_type == "ota_update_impact":
            df = self._add_ota_features(df)
        elif problem_type == "performance_degradation":
            df = self._add_performance_features(df)
        elif problem_type == "app_misbehavior":
            df = self._add_app_features(df)

        return df

    def _add_temporal_features(self, df: pd.DataFrame) -> pd.DataFrame:
        """Add time-based features."""
        if "timestamp" in df.columns:
            # Convert ms timestamp to datetime-like features
            # Assuming timestamp is in milliseconds since epoch
            seconds = df["timestamp"] / 1000.0
            df["hour_of_day"] = (seconds % 86400) / 3600
            df["is_weekend"] = ((seconds // 86400) % 7).isin([5, 6]).astype(int)

        return df

    def _add_rolling_stats(self, df: pd.DataFrame) -> pd.DataFrame:
        """Add rolling window statistics for key columns."""
        if "timestamp" not in df.columns:
            return df

        # Estimate number of rows per window based on median interval
        if len(df) > 1:
            median_interval = df["timestamp"].diff().median()
            if median_interval > 0:
                rows_1h = max(1, int(self.WINDOW_1H / median_interval))
                rows_6h = max(1, int(self.WINDOW_6H / median_interval))
                rows_24h = max(1, int(self.WINDOW_24H / median_interval))
            else:
                rows_1h, rows_6h, rows_24h = 12, 72, 288
        else:
            rows_1h, rows_6h, rows_24h = 12, 72, 288

        # Battery level rolling stats
        if "battery_level" in df.columns:
            df["battery_level_mean_1h"] = (
                df["battery_level"].rolling(window=rows_1h, min_periods=1).mean()
            )
            df["battery_level_std_1h"] = (
                df["battery_level"].rolling(window=rows_1h, min_periods=1).std().fillna(0)
            )

        # Temperature rolling stats
        if "temperature" in df.columns:
            df["temp_mean_1h"] = (
                df["temperature"].rolling(window=rows_1h, min_periods=1).mean()
            )
            df["temp_std_1h"] = (
                df["temperature"].rolling(window=rows_1h, min_periods=1).std().fillna(0)
            )
            df["temp_max_1h"] = (
                df["temperature"].rolling(window=rows_1h, min_periods=1).max()
            )
            df["temp_max_6h"] = (
                df["temperature"].rolling(window=rows_6h, min_periods=1).max()
            )
            df["temp_max_24h"] = (
                df["temperature"].rolling(window=rows_24h, min_periods=1).max()
            )

        # Drain rate rolling stats
        if "drain_rate_instant" in df.columns:
            df["drain_rate_mean_1h"] = (
                df["drain_rate_instant"].rolling(window=rows_1h, min_periods=1).mean()
            )
            df["drain_rate_std_1h"] = (
                df["drain_rate_instant"].rolling(window=rows_1h, min_periods=1).std().fillna(0)
            )
            df["drain_rate_mean_6h"] = (
                df["drain_rate_instant"].rolling(window=rows_6h, min_periods=1).mean()
            )
            df["drain_rate_mean_24h"] = (
                df["drain_rate_instant"].rolling(window=rows_24h, min_periods=1).mean()
            )

        # Voltage rolling stats
        if "voltage" in df.columns:
            df["voltage_normalized"] = (
                (df["voltage"] - df["voltage"].min())
                / (df["voltage"].max() - df["voltage"].min() + 1e-6)
            )

        return df

    def _add_battery_drain_features(self, df: pd.DataFrame) -> pd.DataFrame:
        """Features specific to battery drain detection."""
        # Time since last charge
        if "battery_level" in df.columns and "timestamp" in df.columns:
            # Detect charge events (battery level increases)
            level_diff = df["battery_level"].diff()
            charge_mask = level_diff > 5  # Charging if level jumps by >5%
            charge_times = df.loc[charge_mask, "timestamp"]

            df["time_since_charge"] = 0.0
            for idx in df.index:
                prior_charges = charge_times[charge_times <= df.loc[idx, "timestamp"]]
                if len(prior_charges) > 0:
                    df.loc[idx, "time_since_charge"] = (
                        df.loc[idx, "timestamp"] - prior_charges.iloc[-1]
                    ) / 3_600_000  # hours

        # Screen drain ratio
        if "screen_on" in df.columns and "drain_rate_instant" in df.columns:
            screen_on_drain = df.loc[df["screen_on"] == 1, "drain_rate_instant"].mean()
            screen_off_drain = df.loc[df["screen_on"] == 0, "drain_rate_instant"].mean()
            df["screen_drain_ratio"] = screen_on_drain / (screen_off_drain + 1e-6)
        else:
            df["screen_drain_ratio"] = 1.0

        # Thermal-drain correlation (rolling 24h)
        if "temperature" in df.columns and "drain_rate_instant" in df.columns:
            df["thermal_drain_corr_24h"] = (
                df["temperature"]
                .rolling(window=288, min_periods=2)
                .corr(df["drain_rate_instant"])
                .fillna(0)
            )
        else:
            df["thermal_drain_corr_24h"] = 0.0

        # Power save mode (default to 0 if not present)
        if "power_save_mode" not in df.columns:
            df["power_save_mode"] = 0

        return df

    def _add_thermal_features(self, df: pd.DataFrame) -> pd.DataFrame:
        """Features specific to thermal regression."""
        if "temperature" in df.columns:
            # Temperature acceleration (second derivative)
            temp_diff = df["temperature"].diff()
            df["temp_acceleration"] = temp_diff.diff().fillna(0)

        # Charging state
        if "is_charging" not in df.columns:
            if "battery_level" in df.columns:
                df["is_charging"] = (df["battery_level"].diff() > 0).astype(int)
            else:
                df["is_charging"] = 0

        return df

    def _add_ota_features(self, df: pd.DataFrame) -> pd.DataFrame:
        """Features specific to OTA update impact analysis."""
        if "is_post_update" not in df.columns:
            df["is_post_update"] = 0

        # Delta features: difference from pre-update baseline
        if "drain_rate_instant" in df.columns:
            pre_mean = df.loc[df["is_post_update"] == 0, "drain_rate_instant"].mean()
            df["drain_rate_delta"] = df["drain_rate_instant"] - (pre_mean if not np.isnan(pre_mean) else 0)

        if "temperature" in df.columns:
            pre_temp_max = df.loc[df["is_post_update"] == 0, "temperature"].max()
            df["temp_max_delta"] = df["temperature"] - (pre_temp_max if not np.isnan(pre_temp_max) else 0)

        # Days since update
        if "timestamp" in df.columns and "is_post_update" in df.columns:
            post_mask = df["is_post_update"] == 1
            if post_mask.any():
                update_time = df.loc[post_mask, "timestamp"].min()
                df["days_since_update"] = (df["timestamp"] - update_time) / 86_400_000
                df["days_since_update"] = df["days_since_update"].clip(lower=0)
            else:
                df["days_since_update"] = 0

        return df

    def _add_performance_features(self, df: pd.DataFrame) -> pd.DataFrame:
        """Features specific to performance degradation."""
        # Health percentage
        if "health_percentage" not in df.columns:
            df["health_percentage"] = 100.0

        # Capacity fade
        if "capacity_fade" not in df.columns:
            df["capacity_fade"] = 0.0

        # Cycle count
        if "cycle_count" not in df.columns:
            df["cycle_count"] = 0

        # Fast charge & deep discharge counts
        if "fast_charge_count" not in df.columns:
            df["fast_charge_count"] = 0
        if "deep_discharge_count" not in df.columns:
            df["deep_discharge_count"] = 0

        # Days since first use
        if "timestamp" in df.columns:
            df["days_since_first_use"] = (
                (df["timestamp"] - df["timestamp"].min()) / 86_400_000
            )
        else:
            df["days_since_first_use"] = 0

        return df

    def _add_app_features(self, df: pd.DataFrame) -> pd.DataFrame:
        """Features specific to app misbehavior detection."""
        # Drain per hour
        if "battery_percent_used" in df.columns and "foreground_time" in df.columns:
            total_time = df["foreground_time"] + df.get("background_time", 0)
            df["drain_per_hour"] = df["battery_percent_used"] / (total_time / 3600 + 1e-6)
        elif "drain_per_hour" not in df.columns:
            df["drain_per_hour"] = 0.0

        # Default columns if not present
        for col in ["foreground_time", "background_time", "wakelock_count",
                     "network_bytes", "alarm_count", "is_system_app"]:
            if col not in df.columns:
                df[col] = 0

        return df
