package com.example.hexnil.intelligence;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.text.format.DateUtils;
import android.util.Log;

import com.example.hexnil.intelligence.database.AppIntelligenceDatabase;
import com.example.hexnil.intelligence.database.dao.AppAnomalyDao;
import com.example.hexnil.intelligence.database.dao.AppBatteryAttributionDao;
import com.example.hexnil.intelligence.database.dao.AppRegistryDao;
import com.example.hexnil.intelligence.database.dao.AppThermalImpactDao;
import com.example.hexnil.intelligence.database.dao.AppUsageSessionDao;
import com.example.hexnil.intelligence.database.entities.AppAnomaly;
import com.example.hexnil.intelligence.database.entities.AppBatteryAttribution;
import com.example.hexnil.intelligence.database.entities.AppRegistry;
import com.example.hexnil.intelligence.database.entities.AppThermalImpact;
import com.example.hexnil.intelligence.database.entities.AppUsageSession;
import com.example.hexnil.model.BatterySnapshot;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * App Intelligence Collector - Automatic data collection for the App
 * Intelligence module.
 * 
 * This collector integrates with the BatteryMonitorService to automatically:
 * - Track per-app battery usage
 * - Capture usage context (foreground/background, network, charging)
 * - Monitor thermal impact per app
 * - Detect and record anomalies
 * 
 * ISOLATION: This operates on the separate hexnil_app_intelligence.db database.
 * If the module is disabled, all operations gracefully no-op.
 */
public class AppIntelligenceCollector {

    private static final String TAG = "AppIntelligenceCol";

    // Collection intervals
    private static final long COLLECTION_INTERVAL_MS = 5 * 60 * 1000; // 5 minutes
    private static final long THERMAL_CHECK_INTERVAL_MS = 60 * 1000; // 1 minute

    // Anomaly thresholds
    private static final float HIGH_DRAIN_RATE_THRESHOLD = 15.0f; // %/hr
    private static final float EXCESSIVE_DRAIN_RATE_THRESHOLD = 25.0f; // %/hr
    private static final float HIGH_TEMP_THRESHOLD = 40.0f; // °C
    private static final float CRITICAL_TEMP_THRESHOLD = 45.0f; // °C
    private static final long EXCESSIVE_BACKGROUND_MS = 10 * 60 * 1000; // 10 minutes

    private final Context context;
    private final UsageStatsManager usageStatsManager;
    private final PackageManager packageManager;
    private final ExecutorService executor;

    // Session tracking
    private final Map<String, String> activeSessionIds = new HashMap<>();
    private long lastCollectionTime = 0;
    private long lastThermalCheckTime = 0;

    // State cache
    private float lastTemperature = 0;
    private boolean lastCharging = false;
    private String lastNetworkType = "unknown";

    public AppIntelligenceCollector(Context context) {
        this.context = context.getApplicationContext();
        this.usageStatsManager = (UsageStatsManager) this.context.getSystemService(Context.USAGE_STATS_SERVICE);
        this.packageManager = this.context.getPackageManager();
        this.executor = Executors.newSingleThreadExecutor();
    }

    // =========================================================================
    // MAIN COLLECTION METHOD (Called by BatteryMonitorService)
    // =========================================================================

    /**
     * Collect app intelligence data based on the current battery snapshot.
     * This is the main entry point called from BatteryMonitorService.runAnalysis().
     * 
     * @param snapshot The current battery snapshot
     */
    public void collect(BatterySnapshot snapshot) {
        if (!AppIntelligenceController.isEnabled(context)) {
            return; // Module disabled, no-op
        }

        executor.execute(() -> {
            try {
                long now = System.currentTimeMillis();

                // Update state
                lastTemperature = snapshot.getTemperatureCelsius();
                lastCharging = snapshot.isCharging;
                lastNetworkType = snapshot.networkState != null ? snapshot.networkState : "unknown";

                // Collect app usage data (every 5 minutes)
                if (now - lastCollectionTime >= COLLECTION_INTERVAL_MS) {
                    collectAppUsageData(snapshot);
                    lastCollectionTime = now;
                }

                // Check thermal impact (every minute)
                if (now - lastThermalCheckTime >= THERMAL_CHECK_INTERVAL_MS) {
                    checkThermalImpact(snapshot);
                    lastThermalCheckTime = now;
                }

            } catch (Exception e) {
                Log.e(TAG, "Error collecting app intelligence", e);
            }
        });
    }

    // =========================================================================
    // APP USAGE COLLECTION
    // =========================================================================

    /**
     * Collect per-app battery usage data from UsageStatsManager.
     */
    private void collectAppUsageData(BatterySnapshot snapshot) {
        AppIntelligenceDatabase db = AppIntelligenceDatabase.getInstance(context);
        if (db == null)
            return;

        long now = System.currentTimeMillis();
        long windowMs = COLLECTION_INTERVAL_MS;
        long periodStart = now - windowMs;
        long periodEnd = now;

        if (usageStatsManager == null) {
            Log.w(TAG, "UsageStatsManager not available");
            return;
        }

        List<UsageStats> stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, periodStart, periodEnd);

        if (stats == null || stats.isEmpty()) {
            return;
        }

        AppRegistryDao registryDao = db.appRegistryDao();
        AppBatteryAttributionDao attributionDao = db.appBatteryAttributionDao();

        int recorded = 0;

        for (UsageStats usage : stats) {
            if (usage.getTotalTimeInForeground() <= 0)
                continue;

            String packageName = usage.getPackageName();
            long foregroundTime = usage.getTotalTimeInForeground();

            try {
                // Ensure app is registered
                ensureAppRegistered(registryDao, packageName);

                // Create battery attribution record
                String sessionId = getOrCreateSessionId(packageName);

                AppBatteryAttribution attr = new AppBatteryAttribution();
                attr.packageName = packageName;
                attr.sessionId = sessionId;
                attr.periodStart = periodStart;
                attr.periodEnd = periodEnd;

                // Calculate estimated battery usage
                float hoursActive = foregroundTime / 3600000.0f;
                attr.batteryPercentUsed = hoursActive * 10.0f; // Estimate ~10%/hr
                attr.drainRatePerHour = hoursActive > 0 ? attr.batteryPercentUsed / hoursActive : 0;

                // Set context
                attr.chargingState = snapshot.isCharging ? "charging" : "discharging";
                attr.networkType = lastNetworkType;
                attr.screenState = snapshot.isScreenOnOrFalse() ? "on" : "off";

                // Calculate scores
                attr.calculateScores();

                // Check for anomalies
                if (attr.drainRatePerHour >= HIGH_DRAIN_RATE_THRESHOLD) {
                    checkDrainAnomaly(db, packageName, sessionId, attr);
                }

                // Save to database
                attributionDao.insert(attr);
                recorded++;

                // Update registry aggregates
                updateRegistryFromAttribution(registryDao, attr);

            } catch (Exception e) {
                Log.w(TAG, "Error processing app: " + packageName, e);
            }
        }

        if (recorded > 0) {
            Log.d(TAG, "Collected " + recorded + " app battery records");
        }
    }

    /**
     * Ensure an app is registered in the app registry.
     */
    private void ensureAppRegistered(AppRegistryDao dao, String packageName) {
        if (dao.exists(packageName)) {
            return;
        }

        AppRegistry registry = new AppRegistry();
        registry.packageName = packageName;

        try {
            ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
            registry.appName = packageManager.getApplicationLabel(appInfo).toString();
            registry.isSystemApp = (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            registry.isPreinstalled = (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;

            // Guess category based on package patterns
            registry.appCategory = guessAppCategory(packageName);

        } catch (PackageManager.NameNotFoundException e) {
            registry.appName = packageName;
            registry.appCategory = "unknown";
        }

        dao.insert(registry);
    }

    /**
     * Guess app category from package name patterns.
     */
    private String guessAppCategory(String packageName) {
        String pkg = packageName.toLowerCase();

        if (pkg.contains("game") || pkg.contains("unity") || pkg.contains("cocos")) {
            return "game";
        } else if (pkg.contains("instagram") || pkg.contains("facebook") ||
                pkg.contains("twitter") || pkg.contains("social") ||
                pkg.contains("whatsapp") || pkg.contains("telegram")) {
            return "social";
        } else if (pkg.contains("youtube") || pkg.contains("netflix") ||
                pkg.contains("spotify") || pkg.contains("video") ||
                pkg.contains("music") || pkg.contains("player")) {
            return "streaming";
        } else if (pkg.contains("camera") || pkg.contains("photo")) {
            return "camera";
        } else if (pkg.contains("browser") || pkg.contains("chrome")) {
            return "browser";
        } else if (pkg.contains("maps") || pkg.contains("navigation")) {
            return "navigation";
        } else if (pkg.contains("launcher") || pkg.contains("settings")) {
            return "system";
        }

        return "other";
    }

    /**
     * Get or create a session ID for an app.
     */
    private String getOrCreateSessionId(String packageName) {
        synchronized (activeSessionIds) {
            if (!activeSessionIds.containsKey(packageName)) {
                activeSessionIds.put(packageName, UUID.randomUUID().toString());
            }
            return activeSessionIds.get(packageName);
        }
    }

    /**
     * Update registry aggregates from a battery attribution.
     */
    private void updateRegistryFromAttribution(AppRegistryDao dao, AppBatteryAttribution attr) {
        AppRegistry registry = dao.getByPackageName(attr.packageName);
        if (registry != null) {
            registry.lastSeenAt = System.currentTimeMillis();
            registry.updatedAt = registry.lastSeenAt;
            registry.totalSessions++;
            registry.totalBatteryUsedPct += attr.batteryPercentUsed;

            // Running average
            registry.avgDrainPerHour = ((registry.avgDrainPerHour * (registry.totalSessions - 1))
                    + attr.drainRatePerHour) / registry.totalSessions;

            dao.update(registry);
        }
    }

    // =========================================================================
    // THERMAL IMPACT TRACKING
    // =========================================================================

    /**
     * Check thermal impact of currently active apps.
     */
    private void checkThermalImpact(BatterySnapshot snapshot) {
        if (lastTemperature < 35) {
            return; // Temperature is fine, skip tracking
        }

        AppIntelligenceDatabase db = AppIntelligenceDatabase.getInstance(context);
        if (db == null)
            return;

        String topApp = snapshot.topAppPackage;
        if (topApp == null || topApp.isEmpty())
            return;

        AppThermalImpactDao thermalDao = db.appThermalImpactDao();

        long now = System.currentTimeMillis();
        String sessionId = getOrCreateSessionId(topApp);

        AppThermalImpact impact = new AppThermalImpact();
        impact.packageName = topApp;
        impact.sessionId = sessionId;
        impact.periodStart = now - THERMAL_CHECK_INTERVAL_MS;
        impact.periodEnd = now;
        impact.durationMs = THERMAL_CHECK_INTERVAL_MS;

        // Set temperature stats
        impact.avgTemperatureC = lastTemperature;
        impact.maxTemperatureC = lastTemperature;
        impact.minTemperatureC = lastTemperature;
        impact.temperatureRiseC = 0; // Would need historical data

        // Set load metrics
        impact.avgCpuLoad = snapshot.cpuLoadTotal != null ? snapshot.cpuLoadTotal : 0;
        impact.maxCpuLoad = snapshot.cpuLoadTotal != null ? snapshot.cpuLoadTotal : 0;
        impact.avgGpuLoad = snapshot.gpuLoad != null ? snapshot.gpuLoad : 0;

        // Check thresholds
        if (lastTemperature >= HIGH_TEMP_THRESHOLD) {
            impact.crossedWarningThreshold = true;
            impact.thermalSpikeCount = 1;
        }
        if (lastTemperature >= CRITICAL_TEMP_THRESHOLD) {
            impact.crossedCriticalThreshold = true;
        }

        // Calculate score
        impact.calculateThermalScore();

        // Save to database
        thermalDao.insert(impact);

        // Check for thermal anomaly
        if (impact.hasSignificantImpact()) {
            recordThermalAnomaly(db, topApp, sessionId, impact);
        }

        Log.d(TAG, "Thermal: " + topApp + " - " + lastTemperature + "°C, score=" + impact.thermalImpactScore);
    }

    // =========================================================================
    // ANOMALY DETECTION
    // =========================================================================

    /**
     * Check for drain anomaly and record if detected.
     */
    private void checkDrainAnomaly(AppIntelligenceDatabase db, String packageName,
            String sessionId, AppBatteryAttribution attr) {
        AppAnomalyDao anomalyDao = db.appAnomalyDao();

        float expected = 8.0f; // Expected ~8%/hr for typical app
        float actual = attr.drainRatePerHour;

        String reason;
        if (actual >= EXCESSIVE_DRAIN_RATE_THRESHOLD) {
            reason = "Excessive battery drain detected: " + String.format("%.1f%%/hr", actual);
        } else {
            reason = "High battery drain detected: " + String.format("%.1f%%/hr", actual);
        }

        AppAnomaly anomaly = AppAnomaly.excessiveDrain(packageName, sessionId, expected, actual, reason);
        anomaly.setContext(
                -1, // Unknown battery level
                lastCharging,
                true, // Assume screen on for foreground apps
                lastNetworkType);
        anomaly.setPeriod(attr.periodStart, attr.periodEnd, 1);
        anomaly.setRecommendations(
                "Consider restricting background activity for this app",
                "Significant battery drain affecting device runtime",
                "App may be performing excessive background processing");

        // Save anomaly
        anomalyDao.insert(anomaly);

        // Update anomaly count in registry
        AppRegistryDao registryDao = db.appRegistryDao();
        registryDao.incrementAnomalyCount(packageName, System.currentTimeMillis());

        Log.w(TAG, "Drain anomaly recorded: " + packageName + " - " + actual + "%/hr");
    }

    /**
     * Record a thermal anomaly.
     */
    private void recordThermalAnomaly(AppIntelligenceDatabase db, String packageName,
            String sessionId, AppThermalImpact impact) {
        AppAnomalyDao anomalyDao = db.appAnomalyDao();

        String reason = "App caused device temperature to reach " +
                String.format("%.1f°C", impact.maxTemperatureC);

        AppAnomaly anomaly = AppAnomaly.thermalSpike(
                packageName, sessionId, impact.maxTemperatureC, impact.avgTemperatureC, reason);

        anomaly.setContext(-1, lastCharging, true, lastNetworkType);
        anomaly.setPeriod(impact.periodStart, impact.periodEnd, 1);
        anomaly.setRecommendations(
                "Close the app or reduce usage to cool down device",
                "High temperature may degrade battery health over time",
                "App is likely performing CPU/GPU intensive operations");

        anomalyDao.insert(anomaly);

        // Update anomaly count
        AppRegistryDao registryDao = db.appRegistryDao();
        registryDao.incrementAnomalyCount(packageName, System.currentTimeMillis());

        Log.w(TAG, "Thermal anomaly recorded: " + packageName + " - " + impact.maxTemperatureC + "°C");
    }

    // =========================================================================
    // SESSION MANAGEMENT
    // =========================================================================

    /**
     * Start a new session (called when app comes to foreground).
     */
    public void startAppSession(String packageName, int batteryLevel) {
        if (!AppIntelligenceController.isEnabled(context))
            return;

        executor.execute(() -> {
            AppIntelligenceDatabase db = AppIntelligenceDatabase.getInstance(context);
            if (db == null)
                return;

            String sessionId = UUID.randomUUID().toString();
            synchronized (activeSessionIds) {
                activeSessionIds.put(packageName, sessionId);
            }

            AppUsageSessionDao sessionDao = db.appUsageSessionDao();
            AppUsageSession session = AppUsageSession.start(
                    packageName, batteryLevel, lastNetworkType,
                    lastCharging ? "charging" : "discharging");
            session.sessionId = sessionId;
            sessionDao.insert(session);

            Log.d(TAG, "Started session for: " + packageName);
        });
    }

    /**
     * End an app session (called when app goes to background).
     */
    public void endAppSession(String packageName, int batteryLevel) {
        if (!AppIntelligenceController.isEnabled(context))
            return;

        executor.execute(() -> {
            AppIntelligenceDatabase db = AppIntelligenceDatabase.getInstance(context);
            if (db == null)
                return;

            String sessionId;
            synchronized (activeSessionIds) {
                sessionId = activeSessionIds.remove(packageName);
            }

            if (sessionId != null) {
                AppUsageSessionDao sessionDao = db.appUsageSessionDao();
                sessionDao.endSession(sessionId, System.currentTimeMillis(), batteryLevel);
                Log.d(TAG, "Ended session for: " + packageName);
            }
        });
    }

    // =========================================================================
    // MAINTENANCE
    // =========================================================================

    /**
     * Clean up old data to maintain database size.
     * 
     * @param retentionDays Number of days to retain data
     */
    public void cleanup(int retentionDays) {
        if (!AppIntelligenceController.isEnabled(context))
            return;

        executor.execute(() -> {
            AppIntelligenceDatabase db = AppIntelligenceDatabase.getInstance(context);
            if (db == null)
                return;

            long cutoff = System.currentTimeMillis() - (retentionDays * 24L * 60 * 60 * 1000);

            db.appUsageSessionDao().deleteOlderThan(cutoff);
            db.appBatteryAttributionDao().deleteOlderThan(cutoff);
            db.appThermalImpactDao().deleteOlderThan(cutoff);

            // Keep anomalies longer for pattern detection
            long anomalyCutoff = System.currentTimeMillis() - (retentionDays * 2L * 24 * 60 * 60 * 1000);
            db.appAnomalyDao().deleteOldResolved(anomalyCutoff);

            Log.d(TAG, "Cleanup complete - removed data older than " + retentionDays + " days");
        });
    }

    /**
     * Shutdown the collector.
     */
    public void shutdown() {
        executor.shutdown();
    }
}
