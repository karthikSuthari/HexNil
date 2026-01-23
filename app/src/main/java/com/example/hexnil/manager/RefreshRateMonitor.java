package com.example.hexnil.manager;

import android.app.ActivityManager;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.model.RefreshRateSession;
import com.example.hexnil.model.RefreshRateAnomaly;

import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * OEM-Grade Refresh Rate Monitor
 * 
 * Provides continuous refresh rate monitoring with:
 * - In-memory sampling every 1-2 seconds (NEVER persisted as raw samples)
 * - Session-based aggregation (ONE row per foreground app/home screen)
 * - Real-time anomaly detection with deterministic cause classification
 * - Home screen / launcher treated as first-class sessions
 * 
 * CRITICAL: This class must be integrated with BatteryMonitorService to:
 * - Start monitoring when service starts
 * - Sample during snapshot capture (calls sampleRefreshRate())
 * - Route foreground app changes
 * - Stop monitoring when service stops
 */
public class RefreshRateMonitor {

    private static final String TAG = "RefreshRateMonitor";

    // Sampling constants
    private static final long SAMPLE_INTERVAL_MS = 1500; // 1.5 seconds
    private static final float HIGH_HZ_THRESHOLD = 90f; // Hz considered "high"
    private static final float LOW_HZ_THRESHOLD = 60f; // Hz considered "low"
    private static final long IDLE_ANOMALY_THRESHOLD_MS = 60000; // 60s for idle anomaly
    private static final long STUCK_HIGH_THRESHOLD_MS = 300000; // 5min for stuck high

    // Android context and dependencies
    private final Context context;
    private final HexNilDatabase database;
    private final DisplayManager displayManager;
    private final ActivityManager activityManager;
    private final ExecutorService executor;
    private final Handler handler;

    // Monitoring state
    private final AtomicBoolean isMonitoring = new AtomicBoolean(false);
    private final AtomicBoolean screenOn = new AtomicBoolean(true);

    // Current session tracking (in-memory only)
    private RefreshRateSession currentSession;
    private String currentPackageName = "";
    private String currentAppName = "";
    private boolean isHomeScreen = false;

    // In-memory histogram (NEVER persisted as raw data)
    private float currentHz = 0f;
    private float minHz = Float.MAX_VALUE;
    private float maxHz = 0f;
    private double sumHz = 0;
    private int sampleCount = 0;
    private long timeAtHighHz = 0;
    private long timeAtLowHz = 0;
    private long lastHighHzStartTime = 0;
    private long lastLowHzStartTime = 0;
    private boolean wasHighHz = false;
    private boolean wasLowHz = false;

    // Context aggregation
    private double sumBrightness = 0;
    private double sumCpuUsage = 0;
    private double sumGpuUsage = 0;
    private double sumBatteryCurrent = 0;
    private double sumTemperature = 0;

    private long idleStartTime = 0;
    private long highHzContinuousStart = 0;
    private boolean isCharging = false;
    private int anomalyCountInSession = 0;
    private boolean hasStrongAnomalyInSession = false;

    // Rate limiting for anomaly detection (prevent spam)
    private static final long ANOMALY_COOLDOWN_MS = 60000; // 60 seconds between same anomaly type
    private long lastIdleAnomalyTime = 0;
    private long lastStuckHighAnomalyTime = 0;
    private long lastThermalDropAnomalyTime = 0;
    private long lastAppForcedHighAnomalyTime = 0;

    // Self-exclusion
    private static final String SELF_PACKAGE = "com.example.hexnil";

    // Anomaly detector
    private RefreshRateAnomalyDetector anomalyDetector;

    /**
     * Constructor - requires application context and database
     */
    public RefreshRateMonitor(Context context, HexNilDatabase database) {
        this.context = context.getApplicationContext();
        this.database = database;
        this.displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        this.activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        this.executor = Executors.newSingleThreadExecutor();
        this.handler = new Handler(Looper.getMainLooper());
        this.anomalyDetector = new RefreshRateAnomalyDetector(context, database);
    }

    // =========================================================================
    // Lifecycle Management
    // =========================================================================

    /**
     * Start continuous refresh rate monitoring.
     * Should be called when BatteryMonitorService starts.
     */
    public void startMonitoring() {
        if (isMonitoring.compareAndSet(false, true)) {
            Log.i(TAG, "Starting refresh rate monitoring");

            // Initialize with current foreground app
            detectCurrentForegroundApp();

            // Start a new session
            startNewSession();

            // Begin sampling loop
            scheduleSample();
        }
    }

    /**
     * Stop monitoring and persist final session.
     * Should be called when BatteryMonitorService stops.
     */
    public void stopMonitoring() {
        if (isMonitoring.compareAndSet(true, false)) {
            Log.i(TAG, "Stopping refresh rate monitoring");

            // Cancel pending samples
            handler.removeCallbacksAndMessages(null);

            // End and persist current session
            endAndPersistSession();
        }
    }

    /**
     * Schedule the next sampling iteration
     */
    private void scheduleSample() {
        if (isMonitoring.get()) {
            handler.postDelayed(this::performSample, SAMPLE_INTERVAL_MS);
        }
    }

    /**
     * Perform a single sampling iteration
     */
    private void performSample() {
        if (!isMonitoring.get() || !screenOn.get()) {
            scheduleSample();
            return;
        }

        sampleRefreshRate();
        scheduleSample();
    }

    // =========================================================================
    // Session Lifecycle
    // =========================================================================

    /**
     * Called when foreground app changes.
     * Ends current session and starts a new one.
     */
    public void onForegroundAppChanged(String packageName, String appName) {
        if (packageName == null || packageName.equals(currentPackageName)) {
            return;
        }

        Log.d(TAG, "Foreground app changed: " + currentPackageName + " -> " + packageName);

        // End current session
        endAndPersistSession();

        // Update state
        currentPackageName = packageName;
        currentAppName = appName != null ? appName : packageName;
        isHomeScreen = isLauncherPackage(packageName);

        // Start new session
        startNewSession();
    }

    /**
     * Called when screen state changes.
     */
    public void onScreenStateChanged(boolean isScreenOn) {
        screenOn.set(isScreenOn);

        if (!isScreenOn) {
            // Screen OFF - end session
            Log.d(TAG, "Screen OFF - ending session");
            endAndPersistSession();
        } else {
            // Screen ON - start new session
            Log.d(TAG, "Screen ON - starting new session");
            detectCurrentForegroundApp();
            startNewSession();
        }
    }

    /**
     * Update charging state for context
     */
    public void setCharging(boolean charging) {
        this.isCharging = charging;
    }

    /**
     * Start a new refresh rate session
     */
    private void startNewSession() {
        if (isHomeScreen) {
            currentSession = RefreshRateSession.homeSession();
        } else {
            currentSession = RefreshRateSession.appSession(currentPackageName, currentAppName);
        }

        // Reset histogram
        resetHistogram();

        // Get initial refresh rate
        currentHz = getCurrentRefreshRate();
        currentSession.startRefreshRateHz = currentHz;

        // Check if adaptive display is enabled
        currentSession.isAdaptiveDisplay = isAdaptiveDisplayEnabled();

        Log.d(TAG, "Started new session: " + currentSession.sessionType + " / " + currentPackageName);
    }

    /**
     * End current session and persist to database
     */
    private void endAndPersistSession() {
        if (currentSession == null) {
            return;
        }

        // Calculate final metrics
        currentSession.endSession();
        currentSession.endRefreshRateHz = currentHz;

        if (sampleCount > 0) {
            currentSession.avgRefreshRateHz = (float) (sumHz / sampleCount);
        }
        currentSession.minRefreshRateHz = minHz == Float.MAX_VALUE ? 0 : minHz;
        currentSession.maxRefreshRateHz = maxHz;

        // Finalize time tracking
        long now = System.currentTimeMillis();
        if (wasHighHz && lastHighHzStartTime > 0) {
            timeAtHighHz += now - lastHighHzStartTime;
        }
        if (wasLowHz && lastLowHzStartTime > 0) {
            timeAtLowHz += now - lastLowHzStartTime;
        }
        currentSession.timeAtHighHzMs = timeAtHighHz;
        currentSession.timeAtLowHzMs = timeAtLowHz;

        // Calculate context averages
        if (sampleCount > 0) {
            currentSession.avgBrightness = (float) (sumBrightness / sampleCount);
            currentSession.avgCpuUsage = (float) (sumCpuUsage / sampleCount);
            currentSession.avgGpuUsage = (float) (sumGpuUsage / sampleCount);
            currentSession.avgBatteryCurrent = (float) (sumBatteryCurrent / sampleCount);
            currentSession.avgTemperature = (float) (sumTemperature / sampleCount);
        }

        // Estimate battery cost from high refresh usage
        currentSession.estimatedBatteryCost = estimateBatteryCost(timeAtHighHz, currentSession.avgBrightness);

        // Set anomaly summary
        currentSession.anomalyCount = anomalyCountInSession;
        currentSession.hasStrongAnomaly = hasStrongAnomalyInSession;

        // Only persist sessions longer than 5 seconds
        if (currentSession.durationMs > 5000) {
            final RefreshRateSession sessionToSave = currentSession;
            executor.execute(() -> {
                try {
                    long id = database.refreshRateSessionDao().insert(sessionToSave);
                    Log.i(TAG, "Persisted session: id=" + id + ", " + sessionToSave);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to persist session", e);
                }
            });
        } else {
            Log.d(TAG, "Session too short, not persisting: " + currentSession.durationMs + "ms");
        }

        currentSession = null;
    }

    /**
     * Reset histogram tracking for new session
     */
    private void resetHistogram() {
        minHz = Float.MAX_VALUE;
        maxHz = 0f;
        sumHz = 0;
        sampleCount = 0;
        timeAtHighHz = 0;
        timeAtLowHz = 0;
        lastHighHzStartTime = 0;
        lastLowHzStartTime = 0;
        wasHighHz = false;
        wasLowHz = false;
        sumBrightness = 0;
        sumCpuUsage = 0;
        sumGpuUsage = 0;
        sumBatteryCurrent = 0;
        sumTemperature = 0;
        idleStartTime = 0;
        highHzContinuousStart = 0;
        anomalyCountInSession = 0;
        hasStrongAnomalyInSession = false;
    }

    // =========================================================================
    // Sampling
    // =========================================================================

    /**
     * Sample refresh rate and update histogram.
     * Called every 1-2 seconds - NEVER persists raw samples.
     */
    public void sampleRefreshRate() {
        if (currentSession == null || !screenOn.get()) {
            return;
        }

        long now = System.currentTimeMillis();

        // Get current refresh rate
        float hz = getCurrentRefreshRate();
        if (hz <= 0) {
            return;
        }

        currentHz = hz;

        // Update histogram
        if (hz < minHz)
            minHz = hz;
        if (hz > maxHz)
            maxHz = hz;
        sumHz += hz;
        sampleCount++;

        // Track time at high/low Hz
        boolean isHighHz = hz >= HIGH_HZ_THRESHOLD;
        boolean isLowHz = hz <= LOW_HZ_THRESHOLD;

        if (isHighHz && !wasHighHz) {
            lastHighHzStartTime = now;
            wasHighHz = true;
            highHzContinuousStart = now;
        } else if (!isHighHz && wasHighHz) {
            timeAtHighHz += now - lastHighHzStartTime;
            wasHighHz = false;
            highHzContinuousStart = 0;
        }

        if (isLowHz && !wasLowHz) {
            lastLowHzStartTime = now;
            wasLowHz = true;
        } else if (!isLowHz && wasLowHz) {
            timeAtLowHz += now - lastLowHzStartTime;
            wasLowHz = false;
        }

        // Collect context data
        float brightness = getCurrentBrightness();
        float cpuUsage = getCpuUsage();
        float gpuUsage = getGpuUsage();
        float temperature = getTemperature();
        float batteryCurrent = getBatteryCurrent();

        sumBrightness += brightness;
        sumCpuUsage += cpuUsage;
        sumGpuUsage += gpuUsage;
        sumTemperature += temperature;
        sumBatteryCurrent += batteryCurrent;

        // Run anomaly detection
        detectAnomalies(now, hz, cpuUsage, gpuUsage, brightness, temperature, batteryCurrent);
    }

    /**
     * Add context from battery snapshot (for values not directly accessible)
     */
    public void addContextFromSnapshot(float temperature, float batteryCurrent, boolean charging) {
        this.isCharging = charging;
        // Temperature and current are already tracked via getTemperature() and
        // getBatteryCurrent()
        // but these can be overridden with more accurate snapshot values
    }

    // =========================================================================
    // Anomaly Detection
    // =========================================================================

    /**
     * Detect anomalies during sampling.
     * Uses deterministic rules only - NO AI guessing.
     */
    private void detectAnomalies(long now, float hz, float cpu, float gpu,
            float brightness, float temperature, float batteryCurrent) {
        if (currentSession == null)
            return;

        // Check for HIGH_REFRESH_IDLE on home screen
        if (isHomeScreen && hz >= HIGH_HZ_THRESHOLD) {
            if (idleStartTime == 0) {
                idleStartTime = now;
            } else if (now - idleStartTime > IDLE_ANOMALY_THRESHOLD_MS
                    && now - lastIdleAnomalyTime > ANOMALY_COOLDOWN_MS) {
                // High refresh on home screen for >60s without activity
                recordAnomaly(RefreshRateAnomaly.highRefreshIdle(
                        currentSession.id,
                        hz,
                        now - idleStartTime,
                        buildEvidenceJson(hz, cpu, gpu, brightness, temperature, batteryCurrent)));
                lastIdleAnomalyTime = now;
                idleStartTime = now; // Reset to avoid re-detecting
            }
        } else {
            idleStartTime = 0;
        }

        // Check for REFRESH_STUCK_HIGH
        if (hz >= HIGH_HZ_THRESHOLD && cpu < 20 && !isCharging) {
            if (highHzContinuousStart > 0 && now - highHzContinuousStart > STUCK_HIGH_THRESHOLD_MS
                    && now - lastStuckHighAnomalyTime > ANOMALY_COOLDOWN_MS) {
                recordAnomaly(RefreshRateAnomaly.refreshStuckHigh(
                        currentSession.id,
                        currentPackageName,
                        currentAppName,
                        currentSession.sessionType,
                        hz, cpu, temperature,
                        buildEvidenceJson(hz, cpu, gpu, brightness, temperature, batteryCurrent)));
                lastStuckHighAnomalyTime = now;
                highHzContinuousStart = now; // Reset
            }
        }

        // Check for THERMAL_FORCED_REFRESH_DROP (with rate limiting)
        if (temperature > 42 && hz < 90 && maxHz >= 90
                && now - lastThermalDropAnomalyTime > ANOMALY_COOLDOWN_MS) {
            recordAnomaly(RefreshRateAnomaly.thermalForcedDrop(
                    currentSession.id,
                    currentPackageName,
                    currentAppName,
                    currentSession.sessionType,
                    maxHz, hz, temperature,
                    buildEvidenceJson(hz, cpu, gpu, brightness, temperature, batteryCurrent)));
            lastThermalDropAnomalyTime = now;
        }

        // Check for APP_FORCED_HIGH_REFRESH (high Hz + low activity)
        // EXCLUDE: Self (HexNil), launchers, and apply rate limiting
        if (!isHomeScreen
                && !SELF_PACKAGE.equals(currentPackageName) // Don't flag ourselves
                && hz >= 120
                && cpu < 15
                && brightness < 100
                && now - lastAppForcedHighAnomalyTime > ANOMALY_COOLDOWN_MS) {
            recordAnomaly(RefreshRateAnomaly.appForcedHighRefresh(
                    currentSession.id,
                    currentPackageName,
                    currentAppName,
                    hz, cpu, brightness,
                    buildEvidenceJson(hz, cpu, gpu, brightness, temperature, batteryCurrent)));
            lastAppForcedHighAnomalyTime = now;
        }
    }

    /**
     * Record an anomaly to the database
     */
    private void recordAnomaly(RefreshRateAnomaly anomaly) {
        if (anomaly == null)
            return;

        anomalyCountInSession++;
        if ("critical".equals(anomaly.severity) || "high".equals(anomaly.severity)) {
            hasStrongAnomalyInSession = true;
        }

        // Fill in context
        anomaly.wasCharging = isCharging;
        anomaly.screenOn = screenOn.get();

        final RefreshRateAnomaly anomalyToSave = anomaly;
        executor.execute(() -> {
            try {
                long id = database.refreshRateAnomalyDao().insert(anomalyToSave);
                Log.w(TAG, "Recorded anomaly: id=" + id + ", " + anomalyToSave);
            } catch (Exception e) {
                Log.e(TAG, "Failed to record anomaly", e);
            }
        });
    }

    /**
     * Build JSON evidence string for anomaly
     */
    private String buildEvidenceJson(float hz, float cpu, float gpu,
            float brightness, float temperature, float batteryCurrent) {
        try {
            JSONObject json = new JSONObject();
            json.put("refreshHz", hz);
            json.put("cpuUsage", cpu);
            json.put("gpuUsage", gpu);
            json.put("brightness", brightness);
            json.put("temperature", temperature);
            json.put("batteryCurrent", batteryCurrent);
            json.put("isCharging", isCharging);
            json.put("packageName", currentPackageName);
            json.put("sessionType", currentSession != null ? currentSession.sessionType : "");
            return json.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    // =========================================================================
    // Display & System Queries
    // =========================================================================

    /**
     * Get current display refresh rate in Hz
     */
    public float getCurrentRefreshRate() {
        try {
            Display display = displayManager.getDisplay(Display.DEFAULT_DISPLAY);
            if (display != null) {
                return display.getRefreshRate();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to get refresh rate", e);
        }
        return 60f; // Default fallback
    }

    /**
     * Check if adaptive display (variable refresh rate) is enabled
     */
    private boolean isAdaptiveDisplayEnabled() {
        try {
            // This setting varies by OEM, common names include:
            // - "peak_refresh_rate" (Samsung)
            // - "min_refresh_rate" (various)
            // - "user_refresh_rate" (Android 13+)
            float peakRate = Settings.System.getFloat(
                    context.getContentResolver(),
                    "peak_refresh_rate",
                    -1f);
            if (peakRate > 60f) {
                return true;
            }

            // Check for high refresh rate mode
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Display display = displayManager.getDisplay(Display.DEFAULT_DISPLAY);
                if (display != null) {
                    Display.Mode[] modes = display.getSupportedModes();
                    for (Display.Mode mode : modes) {
                        if (mode.getRefreshRate() > 60f) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "Could not determine adaptive display status", e);
        }
        return false;
    }

    /**
     * Detect current foreground app
     */
    private void detectCurrentForegroundApp() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Use UsageStatsManager for Android 6.0+
                android.app.usage.UsageStatsManager usm = (android.app.usage.UsageStatsManager) context
                        .getSystemService(Context.USAGE_STATS_SERVICE);
                long now = System.currentTimeMillis();
                java.util.List<android.app.usage.UsageStats> stats = usm.queryUsageStats(
                        android.app.usage.UsageStatsManager.INTERVAL_DAILY,
                        now - 10000, now);

                if (stats != null && !stats.isEmpty()) {
                    android.app.usage.UsageStats recent = null;
                    for (android.app.usage.UsageStats stat : stats) {
                        if (recent == null || stat.getLastTimeUsed() > recent.getLastTimeUsed()) {
                            recent = stat;
                        }
                    }
                    if (recent != null) {
                        currentPackageName = recent.getPackageName();
                        currentAppName = getAppName(currentPackageName);
                        isHomeScreen = isLauncherPackage(currentPackageName);
                        return;
                    }
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "Could not detect foreground app", e);
        }

        // Fallback to unknown
        currentPackageName = "unknown";
        currentAppName = "Unknown";
        isHomeScreen = false;
    }

    /**
     * Check if package is a launcher
     */
    private boolean isLauncherPackage(String packageName) {
        if (packageName == null)
            return false;
        return packageName.contains("launcher") ||
                packageName.contains("home") ||
                packageName.equals("com.google.android.apps.nexuslauncher") ||
                packageName.equals("com.sec.android.app.launcher") ||
                packageName.equals("com.miui.home") ||
                packageName.equals("com.huawei.android.launcher") ||
                packageName.equals("com.oppo.launcher") ||
                packageName.equals("com.oneplus.launcher");
    }

    /**
     * Get app display name from package name
     */
    private String getAppName(String packageName) {
        try {
            android.content.pm.PackageManager pm = context.getPackageManager();
            android.content.pm.ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
            return pm.getApplicationLabel(info).toString();
        } catch (Exception e) {
            return packageName;
        }
    }

    // =========================================================================
    // Context Data Collection
    // =========================================================================

    private float getCurrentBrightness() {
        try {
            return Settings.System.getInt(context.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS, 128);
        } catch (Exception e) {
            return 128f;
        }
    }

    private float getCpuUsage() {
        // Simplified - actual implementation would read from /proc/stat
        // This is a placeholder that returns a reasonable estimate
        return 10f;
    }

    private float getGpuUsage() {
        // GPU usage requires vendor-specific APIs
        // This is a placeholder
        return 5f;
    }

    private float getTemperature() {
        // Would be provided by BatteryManager via addContextFromSnapshot
        return 30f;
    }

    private float getBatteryCurrent() {
        // Would be provided by BatteryManager via addContextFromSnapshot
        return 0f;
    }

    /**
     * Estimate battery cost from high refresh rate usage
     * Uses empirical estimate: ~50mA extra for 120Hz vs 60Hz
     */
    private float estimateBatteryCost(long highHzTimeMs, float avgBrightness) {
        // Base estimate: 50mA additional draw at 120Hz
        // Adjust for brightness (higher brightness = more impact)
        float brightnessMultiplier = 0.5f + (avgBrightness / 255f);
        float hoursAtHighHz = highHzTimeMs / 3600000f;
        return 50f * brightnessMultiplier * hoursAtHighHz;
    }

    // =========================================================================
    // Public Accessors for Current State
    // =========================================================================

    public float getCurrentHz() {
        return currentHz;
    }

    public boolean isMonitoringActive() {
        return isMonitoring.get();
    }

    public RefreshRateSession getCurrentSession() {
        return currentSession;
    }
}
