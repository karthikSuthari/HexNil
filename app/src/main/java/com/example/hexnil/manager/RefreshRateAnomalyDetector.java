package com.example.hexnil.manager;

import android.content.Context;
import android.util.Log;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.model.RefreshRateAnomaly;
import com.example.hexnil.model.RefreshRateSession;

import org.json.JSONObject;

import java.util.List;

/**
 * OEM-Grade Refresh Rate Anomaly Detector
 * 
 * Provides deterministic, evidence-based anomaly classification.
 * NO AI guessing - every cause is backed by measurable evidence.
 * 
 * Anomaly Types & Detection Rules:
 * 
 * 1. REFRESH_STUCK_HIGH
 * Rule: 120Hz for >5min with low CPU usage (<20%)
 * Cause: APP_FORCED_HIGH_REFRESH or SYSTEM_REFRESH_BUG
 * 
 * 2. HIGH_REFRESH_IDLE
 * Rule: >90Hz on home screen for >60s without touch events
 * Cause: IDLE_REFRESH_WASTE
 * 
 * 3. UNEXPECTED_REFRESH_SPIKE
 * Rule: Jump from <60Hz to >90Hz during low activity
 * Cause: APP_FORCED_HIGH_REFRESH
 * 
 * 4. THERMAL_FORCED_REFRESH_DROP
 * Rule: Refresh drops when temperature >42°C
 * Cause: THERMAL_THROTTLING
 * 
 * 5. APP_FORCED_HIGH_REFRESH
 * Rule: >120Hz + CPU <15% + brightness <100
 * Cause: APP_FORCED_HIGH_REFRESH
 * 
 * 6. SYSTEM_REFRESH_BUG
 * Rule: High refresh persists across 3+ different apps
 * Cause: SYSTEM_REFRESH_BUG
 */
public class RefreshRateAnomalyDetector {

    private static final String TAG = "RefreshRateAnomalyDetector";

    // Detection thresholds
    private static final float THERMAL_THROTTLE_TEMP = 42f; // °C
    private static final float CRITICAL_TEMP = 45f; // °C
    private static final float HIGH_HZ_THRESHOLD = 90f; // Hz
    private static final float MAX_HZ_THRESHOLD = 120f; // Hz
    private static final float LOW_CPU_THRESHOLD = 20f; // %
    private static final float VERY_LOW_CPU_THRESHOLD = 15f; // %
    private static final float LOW_BRIGHTNESS_THRESHOLD = 100f; // 0-255
    private static final long STUCK_HIGH_DURATION_MS = 300000; // 5 minutes
    private static final long IDLE_DURATION_MS = 60000; // 1 minute
    private static final int SYSTEM_BUG_APP_COUNT = 3; // Apps affected

    private final Context context;
    private final HexNilDatabase database;

    // State for cross-session anomaly detection
    private int consecutiveHighHzApps = 0;
    private String lastHighHzApp = "";
    private long lastHighHzTime = 0;

    public RefreshRateAnomalyDetector(Context context, HexNilDatabase database) {
        this.context = context.getApplicationContext();
        this.database = database;
    }

    /**
     * Analyze a completed session for anomalies.
     * Called when a session ends.
     */
    public void analyzeSession(RefreshRateSession session) {
        if (session == null)
            return;

        Log.d(TAG, "Analyzing session: " + session.packageName);

        // Check for high refresh ratio
        float highHzPercent = session.getHighHzPercentage();

        // Check for system-wide refresh bug
        checkSystemRefreshBug(session);

        // Check for app-specific anomalies
        if (!session.sessionType.equals("HOME") && session.avgRefreshRateHz >= HIGH_HZ_THRESHOLD) {
            if (session.avgCpuUsage < VERY_LOW_CPU_THRESHOLD &&
                    session.avgBrightness < LOW_BRIGHTNESS_THRESHOLD) {
                // Already detected during sampling, but log for analysis
                Log.d(TAG, "Session had app-forced high refresh: " + session.packageName);
            }
        }
    }

    /**
     * Check for SYSTEM_REFRESH_BUG across multiple sessions.
     * Detected when high refresh persists across 3+ different apps.
     */
    private void checkSystemRefreshBug(RefreshRateSession session) {
        if (session.avgRefreshRateHz < HIGH_HZ_THRESHOLD) {
            // Reset counter if this app was not high Hz
            consecutiveHighHzApps = 0;
            lastHighHzApp = "";
            return;
        }

        // Different app with high Hz?
        if (!session.packageName.equals(lastHighHzApp)) {
            consecutiveHighHzApps++;
            lastHighHzApp = session.packageName;
            lastHighHzTime = System.currentTimeMillis();

            if (consecutiveHighHzApps >= SYSTEM_BUG_APP_COUNT) {
                // System-wide refresh bug detected!
                RefreshRateAnomaly anomaly = RefreshRateAnomaly.systemRefreshBug(
                        session.id,
                        session.avgRefreshRateHz,
                        consecutiveHighHzApps,
                        buildSystemBugEvidence(session));

                database.refreshRateAnomalyDao().insert(anomaly);
                Log.w(TAG, "SYSTEM_REFRESH_BUG detected across " + consecutiveHighHzApps + " apps");

                // Reset after detection
                consecutiveHighHzApps = 0;
            }
        }
    }

    /**
     * Build evidence JSON for system bug anomaly
     */
    private String buildSystemBugEvidence(RefreshRateSession session) {
        try {
            JSONObject json = new JSONObject();
            json.put("refreshHz", session.avgRefreshRateHz);
            json.put("affectedAppCount", consecutiveHighHzApps);
            json.put("currentPackage", session.packageName);
            json.put("sessionDurationMs", session.durationMs);
            json.put("highHzTimeMs", session.timeAtHighHzMs);
            return json.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * Classify the cause of a detected anomaly.
     * Returns the most likely cause based on evidence.
     */
    public static String classifyCause(float hz, float cpu, float gpu,
            float temperature, float brightness,
            boolean isHomeScreen, long duration) {

        // Priority 1: Thermal throttling
        if (temperature > THERMAL_THROTTLE_TEMP && hz < HIGH_HZ_THRESHOLD) {
            return "THERMAL_THROTTLING";
        }

        // Priority 2: Idle waste on home screen
        if (isHomeScreen && hz >= HIGH_HZ_THRESHOLD && duration > IDLE_DURATION_MS) {
            return "IDLE_REFRESH_WASTE";
        }

        // Priority 3: App forcing high refresh with low activity
        if (!isHomeScreen && hz >= MAX_HZ_THRESHOLD && cpu < VERY_LOW_CPU_THRESHOLD) {
            return "APP_FORCED_HIGH_REFRESH";
        }

        // Priority 4: System bug (would need cross-session context)
        // This is handled by checkSystemRefreshBug()

        // Default: unknown
        return "UNKNOWN";
    }

    /**
     * Calculate severity based on anomaly characteristics
     */
    public static String calculateSeverity(String anomalyType, float hz,
            float temperature, long duration) {
        switch (anomalyType) {
            case "SYSTEM_REFRESH_BUG":
                return "critical";

            case "THERMAL_FORCED_REFRESH_DROP":
                return temperature > CRITICAL_TEMP ? "critical" : "high";

            case "REFRESH_STUCK_HIGH":
                return duration > STUCK_HIGH_DURATION_MS * 2 ? "critical" : "high";

            case "APP_FORCED_HIGH_REFRESH":
                return hz >= MAX_HZ_THRESHOLD ? "high" : "medium";

            case "HIGH_REFRESH_IDLE":
                return duration > IDLE_DURATION_MS * 5 ? "high" : "medium";

            case "UNEXPECTED_REFRESH_SPIKE":
                return "medium";

            default:
                return "low";
        }
    }

    /**
     * Calculate confidence score based on evidence strength
     */
    public static float calculateConfidence(String anomalyType, float hz, float cpu,
            float temperature, long duration) {
        float confidence = 50f; // Base confidence

        // Add confidence based on evidence clarity
        switch (anomalyType) {
            case "THERMAL_FORCED_REFRESH_DROP":
                // Very clear relationship between temp and refresh
                confidence = 95f + (temperature - THERMAL_THROTTLE_TEMP);
                break;

            case "HIGH_REFRESH_IDLE":
                // Clear if high Hz on home screen
                confidence = 90f + Math.min(10f, duration / 60000f);
                break;

            case "REFRESH_STUCK_HIGH":
                // More confident with lower CPU
                confidence = 85f + Math.max(0, (LOW_CPU_THRESHOLD - cpu) / 2);
                break;

            case "APP_FORCED_HIGH_REFRESH":
                // Confidence based on how low CPU is
                confidence = 80f + Math.max(0, (VERY_LOW_CPU_THRESHOLD - cpu));
                break;

            case "SYSTEM_REFRESH_BUG":
                // Highly confident if detected across multiple apps
                confidence = 92f;
                break;

            default:
                confidence = 70f;
        }

        return Math.min(100f, Math.max(0f, confidence));
    }

    /**
     * Get recent anomalies for a package
     */
    public List<RefreshRateAnomaly> getRecentAnomaliesForPackage(String packageName, long since) {
        return database.refreshRateAnomalyDao().getByPackageSince(packageName, since);
    }

    /**
     * Get anomaly statistics for reporting
     */
    public AnomalyStats getAnomalyStats(long since) {
        AnomalyStats stats = new AnomalyStats();
        stats.totalCount = database.refreshRateAnomalyDao().countSince(since);
        stats.criticalCount = database.refreshRateAnomalyDao().countCriticalSince(since);
        stats.thermalThrottleCount = database.refreshRateAnomalyDao()
                .countByTypeSince("THERMAL_FORCED_REFRESH_DROP", since);
        stats.idleWasteCount = database.refreshRateAnomalyDao()
                .countByTypeSince("HIGH_REFRESH_IDLE", since);
        stats.appForcedCount = database.refreshRateAnomalyDao()
                .countByTypeSince("APP_FORCED_HIGH_REFRESH", since);
        stats.systemBugCount = database.refreshRateAnomalyDao()
                .countByTypeSince("SYSTEM_REFRESH_BUG", since);
        return stats;
    }

    /**
     * Statistics holder for anomaly reporting
     */
    public static class AnomalyStats {
        public int totalCount;
        public int criticalCount;
        public int thermalThrottleCount;
        public int idleWasteCount;
        public int appForcedCount;
        public int systemBugCount;

        @Override
        public String toString() {
            return "AnomalyStats{" +
                    "total=" + totalCount +
                    ", critical=" + criticalCount +
                    ", thermal=" + thermalThrottleCount +
                    ", idle=" + idleWasteCount +
                    ", appForced=" + appForcedCount +
                    ", systemBug=" + systemBugCount +
                    '}';
        }
    }
}
