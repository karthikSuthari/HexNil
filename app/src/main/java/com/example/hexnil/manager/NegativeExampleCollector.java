package com.example.hexnil.manager;

import android.content.Context;
import android.util.Log;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.database.NegativeExampleDao;
import com.example.hexnil.model.NegativeExample;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Automatically collects ground-truth negative examples (known-normal high-stress
 * scenarios) for ML training. When the battery monitoring loop detects a foreground
 * app or device state that is known to cause legitimate high drain, temperature, or
 * CPU usage, this collector records a labeled NegativeExample so the anomaly
 * detection model learns not to flag these conditions as problems.
 *
 * Deduplication: the same condition type + foreground app combination is recorded
 * at most once per 30-minute window.
 */
public class NegativeExampleCollector {

    private static final String TAG = "NegExampleCollector";

    // ========================================================================
    // DEDUPLICATION
    // ========================================================================

    /** 30-minute dedup window -- don't record same condition type + app within 30 min. */
    private static final long DEDUP_WINDOW_MS = 30 * 60 * 1000L;

    // ========================================================================
    // KNOWN PACKAGE LISTS
    // ========================================================================

    private static final String[] KNOWN_GAMING_PACKAGES = {
            "com.supercell.clashofclans",
            "com.epicgames.fortnite",
            "com.activision.callofduty.shooter",
            "com.tencent.ig",
            "com.miHoYo.GenshinImpact",
            "com.garena.game.codm"
    };

    private static final String[] KNOWN_BENCHMARK_PACKAGES = {
            "com.antutu.ABenchMark",
            "com.primatelabs.geekbench6",
            "com.futuremark.dmandroid.application",
            "com.cpuid.cpu_z"
    };

    private static final String[] KNOWN_NAVIGATION_PACKAGES = {
            "com.google.android.apps.maps",
            "com.waze",
            "com.sygic.aura"
    };

    private static final String[] KNOWN_CAMERA_PACKAGES = {
            "com.android.camera",
            "com.google.android.GoogleCamera"
    };

    private static final String[] KNOWN_VIDEO_CALL_PACKAGES = {
            "com.google.android.apps.meetings",
            "us.zoom.videomeetings",
            "com.microsoft.teams"
    };

    // ========================================================================
    // DEPENDENCIES
    // ========================================================================

    private final Context context;
    private final HexNilDatabase database;

    // ========================================================================
    // CONSTRUCTOR
    // ========================================================================

    public NegativeExampleCollector(Context context, HexNilDatabase database) {
        this.context = context.getApplicationContext();
        this.database = database;
    }

    // ========================================================================
    // PUBLIC API
    // ========================================================================

    /**
     * Main entry point called from the battery monitoring loop. Inspects the
     * current foreground app and device state to determine whether a known-normal
     * high-stress condition is active. If so, records a {@link NegativeExample}
     * (subject to deduplication).
     *
     * @return the inserted {@link NegativeExample}, or {@code null} if no
     *         applicable condition was detected or the record was deduplicated.
     */
    public NegativeExample collectIfApplicable(String foregroundApp,
                                                String foregroundAppName,
                                                int batteryLevel,
                                                float temperature,
                                                int cpuUsage,
                                                float drainRate,
                                                String deviceFingerprint) {
        boolean isCharging = drainRate < 0;
        String conditionType = detectConditionType(foregroundApp, cpuUsage,
                temperature, drainRate, isCharging);

        if (conditionType == null) {
            return null;
        }

        // Dedup check: look for a similar record within the window
        long dedupSince = System.currentTimeMillis() - DEDUP_WINDOW_MS;
        NegativeExampleDao dao = database.negativeExampleDao();
        NegativeExample existing = dao.findSimilarRecent(conditionType,
                foregroundApp, dedupSince);
        if (existing != null) {
            Log.d(TAG, "Dedup: skipping " + conditionType + " for " + foregroundApp);
            return null;
        }

        // Build and persist the example
        String expectedBehavior = getExpectedBehavior(conditionType);
        NegativeExample example = NegativeExample.create(
                conditionType,
                deviceFingerprint,
                expectedBehavior,
                conditionType, // detectedActivity mirrors conditionType
                foregroundApp);

        example.foregroundAppName = foregroundAppName;
        example.batteryLevel = batteryLevel;
        example.temperature = temperature;
        example.cpuUsage = cpuUsage;
        example.drainRate = drainRate;
        example.metricsJson = captureMetricsSnapshot(batteryLevel, temperature,
                cpuUsage, drainRate);
        example.normalCauseRootCause = conditionType;

        long id = dao.insert(example);
        example.id = id;
        Log.i(TAG, "Collected negative example id=" + id
                + " type=" + conditionType + " app=" + foregroundApp);
        return example;
    }

    /**
     * Determines the condition type based on the foreground app and device state.
     *
     * @return a condition type string, or {@code null} if no known condition matches.
     */
    public String detectConditionType(String foregroundApp,
                                       int cpuUsage,
                                       float temperature,
                                       float drainRate,
                                       boolean isCharging) {
        if (foregroundApp != null) {
            if (matchesAny(foregroundApp, KNOWN_GAMING_PACKAGES)) {
                return "gaming_session";
            }
            if (matchesAny(foregroundApp, KNOWN_BENCHMARK_PACKAGES)) {
                return "benchmark";
            }
            if (matchesAny(foregroundApp, KNOWN_NAVIGATION_PACKAGES)) {
                return "navigation";
            }
            if (matchesAny(foregroundApp, KNOWN_CAMERA_PACKAGES)) {
                return "camera_recording";
            }
            if (matchesAny(foregroundApp, KNOWN_VIDEO_CALL_PACKAGES)) {
                return "video_call";
            }
        }

        if (cpuUsage > 80) {
            return "high_cpu_sustained";
        }
        if (temperature > 40) {
            return "thermal_spike";
        }
        if (isCharging && drainRate < -5) {
            return "fast_charging";
        }

        return null;
    }

    /**
     * Returns a human-readable explanation of why the given condition type is
     * considered normal battery behaviour.
     */
    public String getExpectedBehavior(String conditionType) {
        if (conditionType == null) {
            return "Known normal condition";
        }
        switch (conditionType) {
            case "gaming_session":
                return "High drain and temperature expected during gaming sessions";
            case "benchmark":
                return "Maximum CPU/GPU utilization during benchmark is normal";
            case "navigation":
                return "GPS + screen + data usage causes elevated drain during navigation";
            case "camera_recording":
                return "Camera + ISP + encoding causes high power draw";
            case "video_call":
                return "Camera + mic + network + encoding is high drain";
            case "high_cpu_sustained":
                return "Sustained high CPU from compute-intensive app is expected";
            case "thermal_spike":
                return "Temperature spike from heavy workload within thermal envelope";
            case "fast_charging":
                return "High temperature during fast charging is normal behavior";
            case "low_battery":
                return "Erratic readings at low battery levels are expected";
            default:
                return "Known normal condition";
        }
    }

    /**
     * Builds a JSON string containing the current telemetry metrics for storage
     * in the {@code metricsJson} field of a {@link NegativeExample}.
     */
    public String captureMetricsSnapshot(int batteryLevel,
                                          float temperature,
                                          int cpuUsage,
                                          float drainRate) {
        try {
            JSONObject json = new JSONObject();
            json.put("batteryLevel", batteryLevel);
            json.put("temperature", temperature);
            json.put("cpuUsage", cpuUsage);
            json.put("drainRate", drainRate);
            json.put("timestamp", System.currentTimeMillis());
            return json.toString();
        } catch (Exception e) {
            Log.e(TAG, "Failed to build metrics JSON", e);
            return "{}";
        }
    }

    /**
     * Explicit method to record a gaming session negative example. Handles
     * deduplication internally.
     *
     * @param packageName      foreground game package
     * @param appName          user-visible game name
     * @param batteryLevel     current battery level
     * @param temperature      current temperature in celsius
     * @param cpuUsage         current CPU usage percentage
     * @param drainRate        current drain rate in %/hr
     * @param deviceFingerprint device identifier
     * @param durationMs       how long the gaming session has been running
     * @return inserted {@link NegativeExample} or {@code null} if deduplicated
     */
    public NegativeExample collectGamingSession(String packageName,
                                                 String appName,
                                                 int batteryLevel,
                                                 float temperature,
                                                 int cpuUsage,
                                                 float drainRate,
                                                 String deviceFingerprint,
                                                 long durationMs) {
        NegativeExampleDao dao = database.negativeExampleDao();

        // Dedup check
        long dedupSince = System.currentTimeMillis() - DEDUP_WINDOW_MS;
        NegativeExample existing = dao.findSimilarRecent("gaming_session",
                packageName, dedupSince);
        if (existing != null) {
            Log.d(TAG, "Dedup: skipping gaming_session for " + packageName);
            return null;
        }

        NegativeExample example = NegativeExample.create(
                "gaming_session",
                deviceFingerprint,
                getExpectedBehavior("gaming_session"),
                "gaming_session",
                packageName);

        example.foregroundAppName = appName;
        example.batteryLevel = batteryLevel;
        example.temperature = temperature;
        example.cpuUsage = cpuUsage;
        example.drainRate = drainRate;
        example.durationMs = durationMs;
        example.metricsJson = captureMetricsSnapshot(batteryLevel, temperature,
                cpuUsage, drainRate);
        example.normalCauseRootCause = "gaming_session";

        long id = dao.insert(example);
        example.id = id;
        Log.i(TAG, "Collected gaming negative example id=" + id
                + " app=" + packageName + " duration=" + durationMs + "ms");
        return example;
    }

    /**
     * Records a fast-charging negative example. High temperature during fast
     * charging is expected behaviour and should not trigger anomaly alerts.
     *
     * @param chargeCurrent     charging current in mA
     * @param temperature       current temperature in celsius
     * @param batteryLevel      current battery level
     * @param deviceFingerprint device identifier
     * @return inserted {@link NegativeExample} or {@code null} if deduplicated
     */
    public NegativeExample collectFastChargingExample(float chargeCurrent,
                                                       float temperature,
                                                       int batteryLevel,
                                                       String deviceFingerprint) {
        NegativeExampleDao dao = database.negativeExampleDao();

        // Dedup check
        long dedupSince = System.currentTimeMillis() - DEDUP_WINDOW_MS;
        NegativeExample existing = dao.findSimilarRecent("fast_charging",
                null, dedupSince);
        if (existing != null) {
            Log.d(TAG, "Dedup: skipping fast_charging example");
            return null;
        }

        NegativeExample example = NegativeExample.create(
                "fast_charging",
                deviceFingerprint,
                getExpectedBehavior("fast_charging"),
                "fast_charging",
                null);

        example.batteryLevel = batteryLevel;
        example.temperature = temperature;
        example.cpuUsage = 0;
        example.drainRate = -chargeCurrent; // negative = charging
        example.normalCauseRootCause = "fast_charging";

        try {
            JSONObject json = new JSONObject();
            json.put("batteryLevel", batteryLevel);
            json.put("temperature", temperature);
            json.put("chargeCurrent", chargeCurrent);
            json.put("timestamp", System.currentTimeMillis());
            example.metricsJson = json.toString();
        } catch (Exception e) {
            Log.e(TAG, "Failed to build fast-charging metrics JSON", e);
            example.metricsJson = "{}";
        }

        long id = dao.insert(example);
        example.id = id;
        Log.i(TAG, "Collected fast-charging negative example id=" + id
                + " current=" + chargeCurrent + "mA temp=" + temperature + "C");
        return example;
    }

    /**
     * Returns collection statistics summarising the current state of the negative
     * example data store.
     */
    public CollectionStats getCollectionStatistics() {
        NegativeExampleDao dao = database.negativeExampleDao();

        CollectionStats stats = new CollectionStats();
        stats.totalExamples = dao.getTotalCount();
        stats.unusedForTraining = dao.getUnusedTrainingExamples().size();
        stats.distinctDevices = dao.getDistinctDeviceCount();

        List<NegativeExampleDao.ConditionTypeCount> typeCounts =
                dao.getCountByConditionType();
        Map<String, Integer> countByType = new HashMap<>();
        for (NegativeExampleDao.ConditionTypeCount tc : typeCounts) {
            countByType.put(tc.conditionType, tc.count);
        }
        stats.countByType = countByType;

        return stats;
    }

    /**
     * Deletes negative examples older than the specified retention period.
     *
     * @param retentionDays number of days to keep; examples older than this are
     *                      deleted.
     */
    public void cleanupOldExamples(long retentionDays) {
        long cutoffMs = System.currentTimeMillis()
                - (retentionDays * 24 * 60 * 60 * 1000L);
        database.negativeExampleDao().deleteOlderThan(cutoffMs);
        Log.i(TAG, "Cleaned up negative examples older than "
                + retentionDays + " days");
    }

    // ========================================================================
    // INNER CLASSES
    // ========================================================================

    /**
     * Summary statistics for the negative example collection.
     */
    public static class CollectionStats {
        public int totalExamples;
        public int unusedForTraining;
        public int distinctDevices;
        public Map<String, Integer> countByType;
    }

    // ========================================================================
    // PRIVATE HELPERS
    // ========================================================================

    /**
     * Returns {@code true} if the given package name matches any entry in the
     * provided array.
     */
    private boolean matchesAny(String packageName, String[] knownPackages) {
        for (String known : knownPackages) {
            if (known.equals(packageName)) {
                return true;
            }
        }
        return false;
    }
}
