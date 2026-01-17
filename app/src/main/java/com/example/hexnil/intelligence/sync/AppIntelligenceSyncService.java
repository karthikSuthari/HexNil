package com.example.hexnil.intelligence.sync;

import android.content.Context;
import android.provider.Settings;
import android.util.Log;

import com.example.hexnil.BuildConfig;
import com.example.hexnil.intelligence.database.AppIntelligenceDatabase;
import com.example.hexnil.intelligence.database.entities.AppAnomaly;
import com.example.hexnil.intelligence.database.entities.AppBatteryAttribution;
import com.example.hexnil.intelligence.database.entities.AppRegistry;
import com.example.hexnil.intelligence.database.entities.AppThermalImpact;
import com.example.hexnil.intelligence.database.entities.AppUsageSession;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service to sync App Intelligence data to the HexNil backend server.
 * 
 * Reads data from the local AppIntelligenceDatabase and sends it to the
 * FastAPI backend via HTTP POST /api/v1/sync endpoint.
 * 
 * This service should be triggered when:
 * - Device connects to WiFi (same network as backend server)
 * - User manually requests sync
 * - Periodic background sync
 */
public class AppIntelligenceSyncService {

    private static final String TAG = "AppIntelligenceSync";

    // Default backend URL - can be overridden via SharedPreferences
    private static final String DEFAULT_BACKEND_URL = "http://192.168.1.100:8000";
    private static final String SYNC_ENDPOINT = "/api/v1/sync";

    private static final String PREFS_NAME = "hexnil_sync_prefs";
    private static final String PREF_BACKEND_URL = "backend_url";
    private static final String PREF_LAST_SYNC_TIME = "last_app_intelligence_sync";

    private final Context context;
    private final ExecutorService executor;

    public AppIntelligenceSyncService(Context context) {
        this.context = context.getApplicationContext();
        this.executor = Executors.newSingleThreadExecutor();
    }

    /**
     * Sync all App Intelligence data to the backend.
     * 
     * @param callback Callback for sync result
     */
    public void syncAll(SyncCallback callback) {
        executor.execute(() -> {
            try {
                AppIntelligenceDatabase db = AppIntelligenceDatabase.getInstance(context);
                if (db == null) {
                    callback.onFailure("App Intelligence module is disabled");
                    return;
                }

                // Get all data to sync
                List<AppRegistry> registryList = db.appRegistryDao().getAll();
                List<AppUsageSession> sessionList = db.appUsageSessionDao().getAllCompleted();
                List<AppBatteryAttribution> attributionList = db.appBatteryAttributionDao().getAll();
                List<AppThermalImpact> thermalList = db.appThermalImpactDao().getAll();
                List<AppAnomaly> anomalyList = db.appAnomalyDao().getAll();

                // Build sync payload
                JSONObject payload = buildSyncPayload(
                        registryList, sessionList, attributionList, thermalList, anomalyList);

                // Send to backend
                String backendUrl = getBackendUrl();
                SyncResult result = sendToBackend(backendUrl + SYNC_ENDPOINT, payload);

                if (result.success) {
                    // Update last sync time
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                            .edit()
                            .putLong(PREF_LAST_SYNC_TIME, System.currentTimeMillis())
                            .apply();

                    callback.onSuccess(result);
                } else {
                    callback.onFailure(result.error);
                }

            } catch (Exception e) {
                Log.e(TAG, "Sync failed", e);
                callback.onFailure(e.getMessage());
            }
        });
    }

    /**
     * Set the backend server URL.
     */
    public void setBackendUrl(String url) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_BACKEND_URL, url)
                .apply();
    }

    /**
     * Get the configured backend URL.
     */
    public String getBackendUrl() {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(PREF_BACKEND_URL, DEFAULT_BACKEND_URL);
    }

    /**
     * Get the device ID for sync identification.
     */
    private String getDeviceId() {
        return Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
    }

    /**
     * Build the sync payload JSON.
     */
    private JSONObject buildSyncPayload(
            List<AppRegistry> registryList,
            List<AppUsageSession> sessionList,
            List<AppBatteryAttribution> attributionList,
            List<AppThermalImpact> thermalList,
            List<AppAnomaly> anomalyList) throws JSONException {

        JSONObject payload = new JSONObject();
        String deviceId = getDeviceId();

        // Device info
        JSONObject deviceInfo = new JSONObject();
        deviceInfo.put("device_id", deviceId);
        deviceInfo.put("device_model", android.os.Build.MODEL);
        deviceInfo.put("android_version", android.os.Build.VERSION.RELEASE);
        deviceInfo.put("app_version", BuildConfig.VERSION_NAME);
        payload.put("device_info", deviceInfo);

        // App Registry
        JSONArray registryArray = new JSONArray();
        if (registryList != null) {
            for (AppRegistry r : registryList) {
                JSONObject obj = new JSONObject();
                obj.put("device_id", deviceId);
                obj.put("package_name", r.packageName);
                obj.put("app_name", r.appName);
                obj.put("app_category", r.appCategory);
                obj.put("is_system_app", r.isSystemApp);
                obj.put("is_preinstalled", r.isPreinstalled);
                obj.put("first_seen_at", r.firstSeenAt);
                obj.put("last_seen_at", r.lastSeenAt);
                obj.put("total_sessions", r.totalSessions);
                obj.put("total_battery_used_pct", r.totalBatteryUsedPct);
                obj.put("avg_drain_per_hour", r.avgDrainPerHour);
                obj.put("total_foreground_ms", r.totalForegroundMs);
                obj.put("total_background_ms", r.totalBackgroundMs);
                obj.put("anomaly_count", r.anomalyCount);
                obj.put("tracking_enabled", r.trackingEnabled);
                obj.put("created_at", r.createdAt);
                obj.put("updated_at", r.updatedAt);
                registryArray.put(obj);
            }
        }
        payload.put("app_registry", registryArray);

        // App Usage Sessions
        JSONArray sessionArray = new JSONArray();
        if (sessionList != null) {
            for (AppUsageSession s : sessionList) {
                JSONObject obj = new JSONObject();
                obj.put("device_id", deviceId);
                obj.put("session_id", s.sessionId);
                obj.put("package_name", s.packageName);
                obj.put("session_start", s.sessionStart);
                obj.put("session_end", s.sessionEnd);
                obj.put("duration_ms", s.durationMs);
                obj.put("foreground_time_ms", s.foregroundTimeMs);
                obj.put("background_time_ms", s.backgroundTimeMs);
                obj.put("screen_on_time_ms", s.screenOnTimeMs);
                obj.put("screen_off_time_ms", s.screenOffTimeMs);
                obj.put("network_type", s.networkType);
                obj.put("charging_state", s.chargingState);
                obj.put("battery_level_start", s.batteryLevelStart);
                obj.put("battery_level_end", s.batteryLevelEnd);
                obj.put("doze_mode_active", s.dozeModeActive);
                obj.put("power_save_mode", s.powerSaveMode);
                obj.put("created_at", s.createdAt);
                sessionArray.put(obj);
            }
        }
        payload.put("app_usage_sessions", sessionArray);

        // App Battery Attribution
        JSONArray attrArray = new JSONArray();
        if (attributionList != null) {
            for (AppBatteryAttribution a : attributionList) {
                JSONObject obj = new JSONObject();
                obj.put("device_id", deviceId);
                obj.put("package_name", a.packageName);
                obj.put("session_id", a.sessionId);
                obj.put("period_start", a.periodStart);
                obj.put("period_end", a.periodEnd);
                obj.put("battery_percent_used", a.batteryPercentUsed);
                obj.put("drain_rate_per_hour", a.drainRatePerHour);
                obj.put("cpu_time_ms", a.cpuTimeMs);
                obj.put("network_bytes", a.networkBytes);
                obj.put("wakelock_time_ms", a.wakelockTimeMs);
                obj.put("partial_wakelock_ms", a.partialWakelockMs);
                obj.put("alarm_wakeups", a.alarmWakeups);
                obj.put("network_wakeups", a.networkWakeups);
                obj.put("gps_requests", a.gpsRequests);
                obj.put("charging_state", a.chargingState);
                obj.put("network_type", a.networkType);
                obj.put("screen_state", a.screenState);
                obj.put("efficiency_score", a.efficiencyScore);
                obj.put("drain_score", a.drainScore);
                obj.put("background_abuse_score", a.backgroundAbuseScore);
                obj.put("category_avg_drain", a.categoryAvgDrain);
                obj.put("deviation_from_avg", a.deviationFromAvg);
                obj.put("drain_reasons_json", a.drainReasonsJson);
                obj.put("created_at", a.createdAt);
                attrArray.put(obj);
            }
        }
        payload.put("app_battery_attribution", attrArray);

        // App Thermal Impact
        JSONArray thermalArray = new JSONArray();
        if (thermalList != null) {
            for (AppThermalImpact t : thermalList) {
                JSONObject obj = new JSONObject();
                obj.put("device_id", deviceId);
                obj.put("package_name", t.packageName);
                obj.put("session_id", t.sessionId);
                obj.put("period_start", t.periodStart);
                obj.put("period_end", t.periodEnd);
                obj.put("duration_ms", t.durationMs);
                obj.put("avg_temperature_c", t.avgTemperatureC);
                obj.put("max_temperature_c", t.maxTemperatureC);
                obj.put("min_temperature_c", t.minTemperatureC);
                obj.put("temperature_rise_c", t.temperatureRiseC);
                obj.put("avg_cpu_load", t.avgCpuLoad);
                obj.put("max_cpu_load", t.maxCpuLoad);
                obj.put("avg_gpu_load", t.avgGpuLoad);
                obj.put("thermal_spike_count", t.thermalSpikeCount);
                obj.put("sustained_heat_duration_ms", t.sustainedHeatDurationMs);
                obj.put("crossed_warning_threshold", t.crossedWarningThreshold);
                obj.put("crossed_critical_threshold", t.crossedCriticalThreshold);
                obj.put("thermal_impact_score", t.thermalImpactScore);
                obj.put("thermal_risk_level", t.thermalRiskLevel);
                obj.put("primary_heat_source", t.primaryHeatSource);
                obj.put("created_at", t.createdAt);
                thermalArray.put(obj);
            }
        }
        payload.put("app_thermal_impact", thermalArray);

        // App Anomalies
        JSONArray anomalyArray = new JSONArray();
        if (anomalyList != null) {
            for (AppAnomaly an : anomalyList) {
                JSONObject obj = new JSONObject();
                obj.put("device_id", deviceId);
                obj.put("anomaly_id", deviceId + "_" + an.id); // Generate unique ID
                obj.put("package_name", an.packageName);
                obj.put("session_id", an.sessionId);
                obj.put("anomaly_type", an.anomalyType);
                obj.put("severity", an.severity);
                obj.put("confidence_score", an.confidenceScore);
                obj.put("expected_value", an.expectedValue);
                obj.put("actual_value", an.actualValue);
                obj.put("deviation_percent", an.deviationPercent);
                obj.put("period_start", an.periodStart);
                obj.put("period_end", an.periodEnd);
                obj.put("data_points_analyzed", an.dataPointsAnalyzed);
                obj.put("battery_level_at_detection", an.batteryLevel);
                obj.put("was_charging", an.wasCharging);
                obj.put("screen_on", an.screenOn);
                obj.put("network_state", an.networkType);
                obj.put("description", an.causeReason);
                obj.put("root_cause", an.rootCauseHypothesis);
                obj.put("recommendation", an.recommendation);
                obj.put("expected_impact", an.impactDescription);
                obj.put("is_resolved", an.resolved);
                obj.put("resolved_at", an.resolvedAt);
                obj.put("created_at", an.createdAt);
                obj.put("detected_at", an.detectedAt);
                anomalyArray.put(obj);
            }
        }
        payload.put("app_anomalies", anomalyArray);

        // Empty arrays for required fields (existing battery data not synced here)
        payload.put("battery_snapshots", new JSONArray());
        payload.put("charging_sessions", new JSONArray());
        payload.put("thermal_events", new JSONArray());
        payload.put("drain_events", new JSONArray());
        payload.put("idle_drain_sessions", new JSONArray());
        payload.put("battery_health", new JSONArray());
        payload.put("safety_events", new JSONArray());
        payload.put("calibration_data", new JSONArray());
        payload.put("ai_analysis_results", new JSONArray());

        return payload;
    }

    /**
     * Send payload to backend server.
     */
    private SyncResult sendToBackend(String urlString, JSONObject payload) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(30000);

            // Write payload
            byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(body.length);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body);
            }

            int responseCode = conn.getResponseCode();

            if (responseCode == 200) {
                Log.i(TAG, "Sync successful");
                return new SyncResult(true, "Sync successful", null);
            } else {
                String error = "HTTP " + responseCode;
                Log.e(TAG, "Sync failed: " + error);
                return new SyncResult(false, null, error);
            }

        } catch (Exception e) {
            Log.e(TAG, "Network error", e);
            return new SyncResult(false, null, e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * Shutdown the executor service.
     */
    public void shutdown() {
        executor.shutdown();
    }

    // =========================================================================
    // RESULT CLASSES
    // =========================================================================

    public static class SyncResult {
        public final boolean success;
        public final String message;
        public final String error;

        public SyncResult(boolean success, String message, String error) {
            this.success = success;
            this.message = message;
            this.error = error;
        }
    }

    public interface SyncCallback {
        void onSuccess(SyncResult result);

        void onFailure(String error);
    }
}
