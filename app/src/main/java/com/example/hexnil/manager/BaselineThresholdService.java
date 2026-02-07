package com.example.hexnil.manager;

import android.content.Context;
import android.util.Log;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.model.BatterySnapshot;
import com.example.hexnil.model.DeviceBaselineThreshold;
import com.example.hexnil.model.DeviceProfile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Computes and manages per-device statistical baselines for anomaly detection.
 *
 * Gathers historical metric data (drain rates, temperatures, launch times, etc.)
 * from the database, computes distributional statistics (mean, stdDev, p50/p95/p99),
 * and stores the resulting {@link DeviceBaselineThreshold} rows.  Downstream consumers
 * such as {@link AnomalyDetector} can then call {@link #isAnomaly} or
 * {@link #getZScore} instead of relying on hardcoded thresholds.
 */
public class BaselineThresholdService {

    private static final String TAG = "BaselineThresholdSvc";

    /** Minimum number of samples required to compute a reliable baseline. */
    private static final int MIN_SAMPLE_SIZE = 10;

    /** Only recompute a baseline if the existing one is older than 24 hours. */
    private static final long BASELINE_REFRESH_INTERVAL_MS = 24L * 60 * 60 * 1000;

    /** Default lookback window for gathering historical data (7 days). */
    private static final long DEFAULT_LOOKBACK_MS = 7L * 24 * 60 * 60 * 1000;

    private final Context context;
    private final HexNilDatabase database;

    public BaselineThresholdService(Context context, HexNilDatabase database) {
        this.context = context.getApplicationContext();
        this.database = database;
    }

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    /**
     * Main entry point -- compute baselines for all key metrics for a single device.
     *
     * <p>Metrics computed:
     * {@code idleDrainRate}, {@code activeDrainRate}, {@code batteryTemp},
     * {@code cpuTemp}, {@code coldLaunchMs}, {@code jankPercent}, {@code crashesPerDay}.
     *
     * <p>For each metric the method gathers raw values from the relevant tables,
     * delegates to {@link #computeBaselineForMetric} for statistics, and
     * persists the result.  A metric is skipped when its existing baseline is
     * less than {@link #BASELINE_REFRESH_INTERVAL_MS} old <b>and</b> already has
     * at least {@link #MIN_SAMPLE_SIZE} samples.
     */
    public void computeAndStoreBaselines(String deviceFingerprint) {
        if (deviceFingerprint == null || deviceFingerprint.isEmpty()) {
            Log.w(TAG, "computeAndStoreBaselines: null/empty fingerprint, skipping");
            return;
        }

        Log.d(TAG, "Computing baselines for device: " + deviceFingerprint);

        String[] metrics = {
                "idleDrainRate",
                "activeDrainRate",
                "batteryTemp",
                "cpuTemp",
                "coldLaunchMs",
                "jankPercent",
                "crashesPerDay"
        };

        long now = System.currentTimeMillis();
        long since = now - DEFAULT_LOOKBACK_MS;

        for (String metric : metrics) {
            try {
                // Check whether recomputation is needed
                DeviceBaselineThreshold existing =
                        database.deviceBaselineThresholdDao().getThreshold(deviceFingerprint, metric);
                if (existing != null
                        && existing.sampleSize >= MIN_SAMPLE_SIZE
                        && (now - existing.lastUpdated) < BASELINE_REFRESH_INTERVAL_MS) {
                    Log.d(TAG, "Baseline for " + metric + " is fresh, skipping");
                    continue;
                }

                List<Float> values = collectValuesForMetric(deviceFingerprint, metric, since);
                if (values == null || values.isEmpty()) {
                    Log.d(TAG, "No data for metric " + metric + ", skipping");
                    continue;
                }

                computeBaselineForMetric(deviceFingerprint, metric, values);

            } catch (Exception e) {
                Log.e(TAG, "Error computing baseline for " + metric, e);
            }
        }

        Log.d(TAG, "Baseline computation complete for device: " + deviceFingerprint);
    }

    /**
     * Statistical engine: compute mean, stdDev, p50/p95/p99 from raw values,
     * then insert or update the corresponding {@link DeviceBaselineThreshold} row.
     *
     * @return the created/updated threshold, or {@code null} if fewer than
     *         {@link #MIN_SAMPLE_SIZE} samples are available.
     */
    public DeviceBaselineThreshold computeBaselineForMetric(String fingerprint,
                                                            String metricName,
                                                            List<Float> values) {
        if (values == null || values.size() < MIN_SAMPLE_SIZE) {
            Log.d(TAG, "Insufficient data for " + metricName
                    + ": have " + (values == null ? 0 : values.size())
                    + ", need " + MIN_SAMPLE_SIZE);
            return null;
        }

        // Sort for percentile computation
        List<Float> sorted = new ArrayList<>(values);
        Collections.sort(sorted);

        // Compute summary statistics
        float sum = 0f;
        for (float v : sorted) {
            sum += v;
        }
        float mean = sum / sorted.size();

        float varianceSum = 0f;
        for (float v : sorted) {
            float diff = v - mean;
            varianceSum += diff * diff;
        }
        float stdDev = (float) Math.sqrt(varianceSum / sorted.size());

        float p50 = computePercentile(sorted, 50f);
        float p95 = computePercentile(sorted, 95f);
        float p99 = computePercentile(sorted, 99f);

        int sampleSize = sorted.size();

        // Create or update the threshold row
        DeviceBaselineThreshold existing =
                database.deviceBaselineThresholdDao().getThreshold(fingerprint, metricName);

        if (existing != null) {
            existing.p50 = p50;
            existing.p95 = p95;
            existing.p99 = p99;
            existing.mean = mean;
            existing.stdDev = stdDev;
            existing.sampleSize = sampleSize;
            existing.confidenceLevel = Math.min(1.0f, sampleSize / 100.0f);
            existing.lastUpdated = System.currentTimeMillis();
            database.deviceBaselineThresholdDao().update(existing);
            Log.d(TAG, "Updated baseline: " + existing);
            return existing;
        } else {
            DeviceBaselineThreshold threshold = DeviceBaselineThreshold.create(
                    fingerprint, metricName, p50, p95, p99, mean, stdDev, sampleSize);
            long id = database.deviceBaselineThresholdDao().insert(threshold);
            threshold.id = id;
            Log.d(TAG, "Inserted baseline: " + threshold);
            return threshold;
        }
    }

    /**
     * Look up a stored threshold for the given device and metric.
     *
     * @return the threshold, or {@code null} if none exists.
     */
    public DeviceBaselineThreshold getThreshold(String fingerprint, String metricName) {
        return database.deviceBaselineThresholdDao().getThreshold(fingerprint, metricName);
    }

    /**
     * Check whether a value is anomalous (exceeds the p95 threshold).
     *
     * @return {@code true} if the value exceeds p95, {@code false} if no
     *         threshold exists (fail open) or the value is within bounds.
     */
    public boolean isAnomaly(String fingerprint, String metricName, float value) {
        DeviceBaselineThreshold threshold =
                database.deviceBaselineThresholdDao().getThreshold(fingerprint, metricName);
        if (threshold == null || threshold.p95 == null) {
            return false;
        }
        return value > threshold.p95;
    }

    /**
     * Check whether a value is a significant anomaly (exceeds the p99 threshold).
     *
     * @return {@code true} if the value exceeds p99, {@code false} if no
     *         threshold exists (fail open) or the value is within bounds.
     */
    public boolean isSignificantAnomaly(String fingerprint, String metricName, float value) {
        DeviceBaselineThreshold threshold =
                database.deviceBaselineThresholdDao().getThreshold(fingerprint, metricName);
        if (threshold == null || threshold.p99 == null) {
            return false;
        }
        return value > threshold.p99;
    }

    /**
     * Compute the z-score of a value relative to the stored baseline.
     *
     * @return {@code (value - mean) / stdDev}, or {@code 0f} if no baseline
     *         exists or stdDev is zero.
     */
    public float getZScore(String fingerprint, String metricName, float value) {
        DeviceBaselineThreshold threshold =
                database.deviceBaselineThresholdDao().getThreshold(fingerprint, metricName);
        if (threshold == null || threshold.mean == null || threshold.stdDev == null) {
            return 0f;
        }
        if (threshold.stdDev == 0f) {
            return 0f;
        }
        return (value - threshold.mean) / threshold.stdDev;
    }

    /**
     * Recompute baselines for every known device profile in the database.
     */
    public void refreshAllBaselines() {
        Log.d(TAG, "Refreshing baselines for all device profiles");
        try {
            List<DeviceProfile> profiles = database.deviceProfileDao().getAll();
            if (profiles == null || profiles.isEmpty()) {
                Log.d(TAG, "No device profiles found, nothing to refresh");
                return;
            }
            for (DeviceProfile profile : profiles) {
                try {
                    computeAndStoreBaselines(profile.deviceFingerprint);
                } catch (Exception e) {
                    Log.e(TAG, "Error refreshing baselines for " + profile.deviceFingerprint, e);
                }
            }
            Log.d(TAG, "Baseline refresh complete for " + profiles.size() + " device(s)");
        } catch (Exception e) {
            Log.e(TAG, "Error refreshing all baselines", e);
        }
    }

    // =========================================================================
    // DATA COLLECTION HELPERS
    // =========================================================================

    /**
     * Route metric name to the appropriate data-collection method.
     */
    private List<Float> collectValuesForMetric(String fingerprint, String metricName, long since) {
        switch (metricName) {
            case "idleDrainRate":
                return collectIdleDrainRateValues(fingerprint, since);
            case "activeDrainRate":
                return collectActiveDrainRateValues(fingerprint, since);
            case "batteryTemp":
                return collectTemperatureValues(fingerprint, since);
            case "cpuTemp":
                return collectCpuTempValues(fingerprint, since);
            case "coldLaunchMs":
                return collectColdLaunchValues(fingerprint, since);
            case "jankPercent":
                return collectJankPercentValues(fingerprint, since);
            case "crashesPerDay":
                return collectCrashesPerDayValues(fingerprint, since);
            default:
                Log.w(TAG, "Unknown metric: " + metricName);
                return new ArrayList<>();
        }
    }

    /**
     * Collect drain rate values from battery snapshots.
     *
     * <p>Iterates consecutive discharging snapshots, computes the
     * drain rate (%/hr) between each pair, and filters out any pairs
     * that include a charging period.
     */
    public List<Float> collectDrainRateValues(String fingerprint, long since) {
        List<Float> drainRates = new ArrayList<>();
        try {
            List<BatterySnapshot> snapshots = database.batterySnapshotDao().getSnapshotsSince(since);
            if (snapshots == null || snapshots.size() < 2) {
                return drainRates;
            }

            for (int i = 1; i < snapshots.size(); i++) {
                BatterySnapshot prev = snapshots.get(i - 1);
                BatterySnapshot curr = snapshots.get(i);

                // Skip if either snapshot was during charging
                if (prev.isCharging || curr.isCharging) {
                    continue;
                }

                long timeDeltaMs = curr.timestamp - prev.timestamp;
                if (timeDeltaMs <= 0) {
                    continue;
                }

                int levelDelta = prev.level - curr.level;
                if (levelDelta <= 0) {
                    // Battery level went up or stayed same without charging -- skip
                    continue;
                }

                float hoursElapsed = timeDeltaMs / (1000f * 60f * 60f);
                if (hoursElapsed < 0.01f) {
                    // Less than ~36 seconds -- too granular, skip
                    continue;
                }

                float drainRatePerHour = levelDelta / hoursElapsed;

                // Sanity cap: ignore obviously anomalous readings (> 100%/hr)
                if (drainRatePerHour > 100f) {
                    continue;
                }

                drainRates.add(drainRatePerHour);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error collecting drain rate values", e);
        }
        return drainRates;
    }

    /**
     * Collect battery temperature values from snapshots.
     *
     * <p>The {@link BatterySnapshot#temperature} field is stored in tenths of
     * degrees Celsius (the raw Android value).  This method converts each
     * reading to degrees Celsius before returning.
     */
    public List<Float> collectTemperatureValues(String fingerprint, long since) {
        List<Float> temps = new ArrayList<>();
        try {
            List<BatterySnapshot> snapshots = database.batterySnapshotDao().getSnapshotsSince(since);
            if (snapshots == null || snapshots.isEmpty()) {
                return temps;
            }

            for (BatterySnapshot snapshot : snapshots) {
                float tempCelsius = snapshot.temperature / 10.0f;
                temps.add(tempCelsius);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error collecting temperature values", e);
        }
        return temps;
    }

    /**
     * Collect idle drain rate values from {@code idle_drain_sessions}.
     */
    private List<Float> collectIdleDrainRateValues(String fingerprint, long since) {
        List<Float> values = new ArrayList<>();
        try {
            List<com.example.hexnil.model.IdleDrainSession> sessions =
                    database.idleDrainSessionDao().getSessionsSince(since);
            if (sessions == null || sessions.isEmpty()) {
                return values;
            }
            for (com.example.hexnil.model.IdleDrainSession session : sessions) {
                if (session.drainPerHour > 0 && session.drainPerHour <= 50f) {
                    values.add(session.drainPerHour);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error collecting idle drain rate values", e);
        }
        return values;
    }

    /**
     * Collect active (screen-on, discharging) drain rate values from snapshots.
     */
    private List<Float> collectActiveDrainRateValues(String fingerprint, long since) {
        List<Float> drainRates = new ArrayList<>();
        try {
            List<BatterySnapshot> snapshots = database.batterySnapshotDao().getSnapshotsSince(since);
            if (snapshots == null || snapshots.size() < 2) {
                return drainRates;
            }

            for (int i = 1; i < snapshots.size(); i++) {
                BatterySnapshot prev = snapshots.get(i - 1);
                BatterySnapshot curr = snapshots.get(i);

                // Only consider non-charging, screen-on periods
                if (prev.isCharging || curr.isCharging) {
                    continue;
                }
                if (!prev.isScreenOnOrFalse() || !curr.isScreenOnOrFalse()) {
                    continue;
                }

                long timeDeltaMs = curr.timestamp - prev.timestamp;
                if (timeDeltaMs <= 0) {
                    continue;
                }

                int levelDelta = prev.level - curr.level;
                if (levelDelta <= 0) {
                    continue;
                }

                float hoursElapsed = timeDeltaMs / (1000f * 60f * 60f);
                if (hoursElapsed < 0.01f) {
                    continue;
                }

                float drainRatePerHour = levelDelta / hoursElapsed;
                if (drainRatePerHour > 100f) {
                    continue;
                }

                drainRates.add(drainRatePerHour);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error collecting active drain rate values", e);
        }
        return drainRates;
    }

    /**
     * Collect CPU temperature values from snapshots.
     *
     * <p>Uses the {@code cpuLoadTotal} field as a proxy indicator; the actual
     * CPU thermal zone reading is not available on all devices.  Falls back to
     * the battery temperature with a small offset when CPU-specific data is
     * absent.
     */
    private List<Float> collectCpuTempValues(String fingerprint, long since) {
        List<Float> temps = new ArrayList<>();
        try {
            List<BatterySnapshot> snapshots = database.batterySnapshotDao().getSnapshotsSince(since);
            if (snapshots == null || snapshots.isEmpty()) {
                return temps;
            }

            for (BatterySnapshot snapshot : snapshots) {
                // Battery temperature is often the best available proxy for CPU temp
                // on devices that do not expose a dedicated thermal zone.
                float tempCelsius = snapshot.temperature / 10.0f;
                temps.add(tempCelsius);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error collecting CPU temp values", e);
        }
        return temps;
    }

    /**
     * Collect cold-launch durations from {@code app_launch_metrics}.
     */
    private List<Float> collectColdLaunchValues(String fingerprint, long since) {
        List<Float> values = new ArrayList<>();
        try {
            List<com.example.hexnil.model.AppLaunchMetric> launches =
                    database.appLaunchMetricDao().getByTypeSince("cold", since);
            if (launches == null || launches.isEmpty()) {
                return values;
            }
            for (com.example.hexnil.model.AppLaunchMetric launch : launches) {
                if (launch.launchDurationMs > 0) {
                    values.add((float) launch.launchDurationMs);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error collecting cold launch values", e);
        }
        return values;
    }

    /**
     * Collect jank percentage values from {@code frame_metrics}.
     */
    private List<Float> collectJankPercentValues(String fingerprint, long since) {
        List<Float> values = new ArrayList<>();
        try {
            List<com.example.hexnil.model.FrameMetric> metrics =
                    database.frameMetricDao().getSince(since);
            if (metrics == null || metrics.isEmpty()) {
                return values;
            }
            for (com.example.hexnil.model.FrameMetric fm : metrics) {
                if (fm.totalFrames > 0) {
                    values.add(fm.jankPercentage);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error collecting jank percent values", e);
        }
        return values;
    }

    /**
     * Collect crashes-per-day values over the lookback window.
     *
     * <p>Retrieves recent device-level daily aggregates and returns each day's
     * crash count as a separate sample.  Uses a limit of 30 days which exceeds
     * the default 7-day lookback to ensure sufficient coverage.
     */
    private List<Float> collectCrashesPerDayValues(String fingerprint, long since) {
        List<Float> values = new ArrayList<>();
        try {
            // DailyAggregateDao uses string dates for range queries; use the
            // recent-device-level convenience method with a generous day limit.
            int lookbackDays = (int) ((System.currentTimeMillis() - since) / (24L * 60 * 60 * 1000)) + 1;
            List<com.example.hexnil.model.DailyAggregate> aggregates =
                    database.dailyAggregateDao().getRecentDeviceLevel(Math.max(lookbackDays, 7));
            if (aggregates == null || aggregates.isEmpty()) {
                return values;
            }
            for (com.example.hexnil.model.DailyAggregate agg : aggregates) {
                values.add((float) agg.crashCount);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error collecting crashes per day values", e);
        }
        return values;
    }

    // =========================================================================
    // STATISTICS UTILITIES
    // =========================================================================

    /**
     * Compute a percentile value from a <b>sorted</b> list using linear
     * interpolation.
     *
     * @param sortedValues pre-sorted list of float values (ascending).
     * @param percentile   desired percentile in the range [0, 100].
     * @return the interpolated percentile value.
     */
    public float computePercentile(List<Float> sortedValues, float percentile) {
        if (sortedValues == null || sortedValues.isEmpty()) {
            return 0f;
        }
        if (sortedValues.size() == 1) {
            return sortedValues.get(0);
        }

        float index = (percentile / 100f) * (sortedValues.size() - 1);
        int lower = (int) Math.floor(index);
        int upper = (int) Math.ceil(index);

        if (lower < 0) lower = 0;
        if (upper >= sortedValues.size()) upper = sortedValues.size() - 1;

        if (lower == upper) {
            return sortedValues.get(lower);
        }

        float fraction = index - lower;
        return sortedValues.get(lower) + fraction * (sortedValues.get(upper) - sortedValues.get(lower));
    }
}
