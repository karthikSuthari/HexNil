package com.example.hexnil.manager;

import android.content.Context;
import android.util.Log;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.model.BatterySnapshot;
import com.example.hexnil.model.DeviceBaselineThreshold;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Extracts ML features from raw battery telemetry data.
 *
 * Transforms a window of {@link BatterySnapshot} records into a fixed-width
 * numerical feature vector suitable for anomaly detection, regression models,
 * or any downstream ML pipeline in the HexNil battery diagnostic system.
 *
 * Features include statistical aggregates of drain rate, temperature, voltage,
 * current draw, CPU/memory usage, screen-on and charging ratios, and optional
 * z-score enrichment from per-device baselines stored in
 * {@link DeviceBaselineThreshold}.
 */
public class FeatureExtractor {

    private static final String TAG = "FeatureExtractor";

    private final Context context;
    private final HexNilDatabase database;

    public FeatureExtractor(Context context, HexNilDatabase database) {
        this.context = context.getApplicationContext();
        this.database = database;
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Extracts a complete feature vector from a window of battery snapshots.
     *
     * @param snapshots          ordered list of snapshots (oldest first)
     * @param deviceFingerprint  device identifier for baseline lookups; may be null
     * @return a {@link FeatureVector} containing all computed features,
     *         or an empty vector if fewer than 2 snapshots are provided
     */
    public FeatureVector extractFeatures(List<BatterySnapshot> snapshots, String deviceFingerprint) {
        FeatureVector fv = new FeatureVector();

        if (snapshots == null || snapshots.size() < 2) {
            Log.w(TAG, "extractFeatures: need at least 2 snapshots, got "
                    + (snapshots == null ? 0 : snapshots.size()));
            return fv;
        }

        try {
            // --- Drain rates ---
            List<Float> drainRates = computeDrainRates(snapshots);
            if (!drainRates.isEmpty()) {
                fv.put("drainRate_mean", computeMean(drainRates));
                fv.put("drainRate_max", computeMax(drainRates));
                fv.put("drainRate_stddev", computeStdDev(drainRates));
            } else {
                fv.put("drainRate_mean", 0f);
                fv.put("drainRate_max", 0f);
                fv.put("drainRate_stddev", 0f);
            }

            // --- Temperature ---
            List<Float> temps = computeTemperatures(snapshots);
            if (!temps.isEmpty()) {
                fv.put("temperature_mean", computeMean(temps));
                fv.put("temperature_max", computeMax(temps));
                fv.put("temperature_min", computeMin(temps));
                fv.put("temperature_stddev", computeStdDev(temps));
            } else {
                fv.put("temperature_mean", 0f);
                fv.put("temperature_max", 0f);
                fv.put("temperature_min", 0f);
                fv.put("temperature_stddev", 0f);
            }

            // --- Voltage ---
            List<Float> voltages = new ArrayList<>(snapshots.size());
            for (BatterySnapshot s : snapshots) {
                voltages.add((float) s.voltage);
            }
            fv.put("voltage_mean", computeMean(voltages));
            fv.put("voltage_min", computeMin(voltages));
            float vFirst = voltages.get(0);
            float vLast = voltages.get(voltages.size() - 1);
            fv.put("voltage_delta", vLast - vFirst);

            // --- Current draw ---
            List<Float> currents = new ArrayList<>();
            for (BatterySnapshot s : snapshots) {
                if (s.currentNow != null) {
                    // currentNow is in microamps; convert to milliamps (absolute value)
                    currents.add(Math.abs(s.currentNow / 1000.0f));
                }
            }
            if (!currents.isEmpty()) {
                fv.put("currentDraw_mean", computeMean(currents));
                fv.put("currentDraw_max", computeMax(currents));
            } else {
                fv.put("currentDraw_mean", 0f);
                fv.put("currentDraw_max", 0f);
            }

            // --- Battery level ---
            BatterySnapshot first = snapshots.get(0);
            BatterySnapshot last = snapshots.get(snapshots.size() - 1);
            fv.put("batteryLevel_start", (float) first.level);
            fv.put("batteryLevel_end", (float) last.level);
            fv.put("batteryLevel_delta", (float) (last.level - first.level));

            // --- Screen-on ratio ---
            int screenOnCount = 0;
            int screenKnownCount = 0;
            for (BatterySnapshot s : snapshots) {
                if (s.screenOn != null) {
                    screenKnownCount++;
                    if (s.screenOn == 1) {
                        screenOnCount++;
                    }
                }
            }
            fv.put("screenOnRatio", screenKnownCount > 0
                    ? (float) screenOnCount / screenKnownCount
                    : 0f);

            // --- Charging ratio ---
            int chargingCount = 0;
            for (BatterySnapshot s : snapshots) {
                // status == 2 means BATTERY_STATUS_CHARGING
                if (s.status == 2) {
                    chargingCount++;
                }
            }
            fv.put("chargingRatio", (float) chargingCount / snapshots.size());

            // --- CPU usage ---
            List<Float> cpuValues = new ArrayList<>(snapshots.size());
            for (BatterySnapshot s : snapshots) {
                cpuValues.add((float) s.cpuUsage);
            }
            fv.put("cpuUsage_mean", computeMean(cpuValues));
            fv.put("cpuUsage_max", computeMax(cpuValues));

            // --- Memory usage ---
            List<Float> memValues = new ArrayList<>(snapshots.size());
            for (BatterySnapshot s : snapshots) {
                memValues.add((float) s.memoryUsage);
            }
            fv.put("memoryUsage_mean", computeMean(memValues));

            // --- Snapshot count and window duration ---
            fv.put("snapshotCount", (float) snapshots.size());
            fv.put("windowDurationMs", (float) (last.timestamp - first.timestamp));

            // --- Baseline z-scores (optional enrichment) ---
            if (deviceFingerprint != null && !deviceFingerprint.isEmpty()) {
                addBaselineFeatures(fv, deviceFingerprint);
            }

        } catch (Exception e) {
            Log.e(TAG, "extractFeatures: error computing features", e);
        }

        return fv;
    }

    /**
     * Same as {@link #extractFeatures} but returns a {@link JSONObject}.
     *
     * @param snapshots          ordered list of snapshots
     * @param deviceFingerprint  device identifier for baseline lookups; may be null
     * @return JSONObject representation of the feature vector, or an empty
     *         JSONObject on error
     */
    public JSONObject extractFeaturesAsJson(List<BatterySnapshot> snapshots, String deviceFingerprint) {
        FeatureVector fv = extractFeatures(snapshots, deviceFingerprint);
        try {
            return fv.toJson();
        } catch (Exception e) {
            Log.e(TAG, "extractFeaturesAsJson: conversion failed", e);
            return new JSONObject();
        }
    }

    // =========================================================================
    // Feature computation helpers
    // =========================================================================

    /**
     * Computes the drain rate (percent per hour) between each pair of
     * consecutive snapshots.
     *
     * @param snapshots at least 2 snapshots ordered by timestamp
     * @return list of drain rates; positive means battery is draining
     */
    List<Float> computeDrainRates(List<BatterySnapshot> snapshots) {
        List<Float> rates = new ArrayList<>();
        for (int i = 1; i < snapshots.size(); i++) {
            BatterySnapshot prev = snapshots.get(i - 1);
            BatterySnapshot curr = snapshots.get(i);

            long dtMs = curr.timestamp - prev.timestamp;
            if (dtMs <= 0) {
                continue; // skip duplicate or out-of-order timestamps
            }

            float dtHours = dtMs / 3600000.0f;
            // Positive drain rate means battery level decreased
            float rate = (prev.level - curr.level) / dtHours;
            rates.add(rate);
        }
        return rates;
    }

    /**
     * Extracts temperature values from snapshots, converting from tenths of
     * degrees Celsius to degrees Celsius.
     *
     * @param snapshots list of snapshots
     * @return list of temperatures in degrees Celsius
     */
    List<Float> computeTemperatures(List<BatterySnapshot> snapshots) {
        List<Float> temps = new ArrayList<>(snapshots.size());
        for (BatterySnapshot s : snapshots) {
            temps.add(s.temperature / 10.0f);
        }
        return temps;
    }

    /**
     * Enriches the feature vector with z-score features derived from per-device
     * baseline thresholds stored in the database.
     *
     * Currently adds:
     * <ul>
     *     <li>{@code z_drainRate} &mdash; z-score of drainRate_mean vs.
     *         "idleDrainRate" baseline</li>
     *     <li>{@code z_temperature} &mdash; z-score of temperature_mean vs.
     *         "batteryTemp" baseline</li>
     * </ul>
     *
     * If a baseline is not found or has insufficient data (stdDev &le; 0),
     * the corresponding z-score is set to 0.
     *
     * @param fv                 feature vector to enrich (modified in place)
     * @param deviceFingerprint  device identifier for baseline lookup
     */
    void addBaselineFeatures(FeatureVector fv, String deviceFingerprint) {
        try {
            // z-score for drain rate
            DeviceBaselineThreshold drainBaseline =
                    database.deviceBaselineThresholdDao().getThreshold(deviceFingerprint, "idleDrainRate");
            if (drainBaseline != null && drainBaseline.mean != null && drainBaseline.stdDev != null
                    && drainBaseline.stdDev > 0) {
                float drainMean = fv.get("drainRate_mean");
                float zDrain = (drainMean - drainBaseline.mean) / drainBaseline.stdDev;
                fv.put("z_drainRate", zDrain);
            } else {
                fv.put("z_drainRate", 0f);
            }

            // z-score for temperature
            DeviceBaselineThreshold tempBaseline =
                    database.deviceBaselineThresholdDao().getThreshold(deviceFingerprint, "batteryTemp");
            if (tempBaseline != null && tempBaseline.mean != null && tempBaseline.stdDev != null
                    && tempBaseline.stdDev > 0) {
                float tempMean = fv.get("temperature_mean");
                float zTemp = (tempMean - tempBaseline.mean) / tempBaseline.stdDev;
                fv.put("z_temperature", zTemp);
            } else {
                fv.put("z_temperature", 0f);
            }
        } catch (Exception e) {
            Log.e(TAG, "addBaselineFeatures: error looking up baselines", e);
            fv.put("z_drainRate", 0f);
            fv.put("z_temperature", 0f);
        }
    }

    // =========================================================================
    // Statistical utilities
    // =========================================================================

    /**
     * Computes the arithmetic mean of a list of values.
     *
     * @param values non-empty list
     * @return mean value, or 0 if the list is empty
     */
    float computeMean(List<Float> values) {
        if (values == null || values.isEmpty()) {
            return 0f;
        }
        float sum = 0f;
        for (float v : values) {
            sum += v;
        }
        return sum / values.size();
    }

    /**
     * Computes the population standard deviation of a list of values.
     *
     * @param values non-empty list
     * @return standard deviation, or 0 if the list has fewer than 2 elements
     */
    float computeStdDev(List<Float> values) {
        if (values == null || values.size() < 2) {
            return 0f;
        }
        float mean = computeMean(values);
        float sumSqDiff = 0f;
        for (float v : values) {
            float diff = v - mean;
            sumSqDiff += diff * diff;
        }
        return (float) Math.sqrt(sumSqDiff / values.size());
    }

    /**
     * Returns the maximum value in the list.
     *
     * @param values non-empty list
     * @return maximum value, or {@code -Float.MAX_VALUE} if the list is empty
     */
    float computeMax(List<Float> values) {
        if (values == null || values.isEmpty()) {
            return -Float.MAX_VALUE;
        }
        float max = -Float.MAX_VALUE;
        for (float v : values) {
            if (v > max) {
                max = v;
            }
        }
        return max;
    }

    /**
     * Returns the minimum value in the list.
     *
     * @param values non-empty list
     * @return minimum value, or {@code Float.MAX_VALUE} if the list is empty
     */
    float computeMin(List<Float> values) {
        if (values == null || values.isEmpty()) {
            return Float.MAX_VALUE;
        }
        float min = Float.MAX_VALUE;
        for (float v : values) {
            if (v < min) {
                min = v;
            }
        }
        return min;
    }

    // =========================================================================
    // Inner class: FeatureVector
    // =========================================================================

    /**
     * A named, ordered collection of float-valued ML features.
     *
     * Maintains insertion order via {@link LinkedHashMap} so that
     * {@link #toFloatArray()} and {@link #getFeatureNames()} return values
     * in a deterministic, consistent order suitable for feeding into a model.
     */
    public static class FeatureVector {

        private final Map<String, Float> features;

        public FeatureVector() {
            this.features = new LinkedHashMap<>();
        }

        /**
         * Stores a feature value. If the key already exists it is overwritten.
         *
         * @param name  feature name (must not be null)
         * @param value feature value
         */
        public void put(String name, float value) {
            features.put(name, value);
        }

        /**
         * Retrieves a feature value by name.
         *
         * @param name feature name
         * @return the value, or 0 if the feature is not present
         */
        public float get(String name) {
            Float v = features.get(name);
            return v != null ? v : 0f;
        }

        /**
         * Converts this feature vector to a {@link JSONObject}.
         *
         * @return JSONObject with feature names as keys and float values
         * @throws JSONException if JSON construction fails
         */
        public JSONObject toJson() throws JSONException {
            JSONObject json = new JSONObject();
            for (Map.Entry<String, Float> entry : features.entrySet()) {
                json.put(entry.getKey(), entry.getValue().doubleValue());
            }
            return json;
        }

        /**
         * Returns all feature values as a float array, in insertion order.
         *
         * @return float array of feature values
         */
        public float[] toFloatArray() {
            float[] arr = new float[features.size()];
            int i = 0;
            for (Float v : features.values()) {
                arr[i++] = v;
            }
            return arr;
        }

        /**
         * Returns the ordered list of feature names.
         *
         * @return list of feature name strings in insertion order
         */
        public List<String> getFeatureNames() {
            return new ArrayList<>(features.keySet());
        }

        /**
         * Returns the number of features in this vector.
         *
         * @return feature count
         */
        public int size() {
            return features.size();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("FeatureVector{");
            boolean first = true;
            for (Map.Entry<String, Float> entry : features.entrySet()) {
                if (!first) {
                    sb.append(", ");
                }
                sb.append(entry.getKey()).append('=');
                sb.append(String.format("%.4f", entry.getValue()));
                first = false;
            }
            sb.append('}');
            return sb.toString();
        }
    }
}
