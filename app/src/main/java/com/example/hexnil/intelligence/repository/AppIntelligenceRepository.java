package com.example.hexnil.intelligence.repository;

import android.content.Context;

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

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Repository for App Intelligence database operations.
 * Provides a unified interface for all app intelligence data access.
 * 
 * All operations are safe - if module is disabled, operations either:
 * - Return null/empty for queries
 * - No-op for inserts/updates
 */
public class AppIntelligenceRepository {

    private final Context context;
    private final ExecutorService executor;

    public AppIntelligenceRepository(Context context) {
        this.context = context.getApplicationContext();
        this.executor = Executors.newSingleThreadExecutor();
    }

    // =========================================================================
    // DATABASE ACCESS
    // =========================================================================

    private AppIntelligenceDatabase getDatabase() {
        return AppIntelligenceDatabase.getInstance(context);
    }

    private AppRegistryDao getRegistryDao() {
        AppIntelligenceDatabase db = getDatabase();
        return db != null ? db.appRegistryDao() : null;
    }

    private AppUsageSessionDao getSessionDao() {
        AppIntelligenceDatabase db = getDatabase();
        return db != null ? db.appUsageSessionDao() : null;
    }

    private AppBatteryAttributionDao getAttributionDao() {
        AppIntelligenceDatabase db = getDatabase();
        return db != null ? db.appBatteryAttributionDao() : null;
    }

    private AppThermalImpactDao getThermalDao() {
        AppIntelligenceDatabase db = getDatabase();
        return db != null ? db.appThermalImpactDao() : null;
    }

    private AppAnomalyDao getAnomalyDao() {
        AppIntelligenceDatabase db = getDatabase();
        return db != null ? db.appAnomalyDao() : null;
    }

    // =========================================================================
    // APP REGISTRY OPERATIONS
    // =========================================================================

    /**
     * Register or update an app in the registry.
     */
    public void registerApp(String packageName, String appName, String category,
            boolean isSystem, boolean isPreinstalled) {
        executor.execute(() -> {
            AppRegistryDao dao = getRegistryDao();
            if (dao == null)
                return;

            AppRegistry existing = dao.getByPackageName(packageName);
            if (existing != null) {
                existing.appName = appName;
                existing.appCategory = category;
                existing.lastSeenAt = System.currentTimeMillis();
                existing.updatedAt = existing.lastSeenAt;
                dao.update(existing);
            } else {
                AppRegistry registry = AppRegistry.create(packageName, appName, category,
                        isSystem, isPreinstalled);
                dao.insert(registry);
            }
        });
    }

    /**
     * Get an app from the registry.
     */
    public AppRegistry getApp(String packageName) {
        AppRegistryDao dao = getRegistryDao();
        return dao != null ? dao.getByPackageName(packageName) : null;
    }

    /**
     * Get top draining apps.
     */
    public List<AppRegistry> getTopDrainApps(int limit) {
        AppRegistryDao dao = getRegistryDao();
        return dao != null ? dao.getTopDrainApps(limit) : null;
    }

    /**
     * Get most problematic apps (by anomaly count).
     */
    public List<AppRegistry> getMostProblematicApps(int limit) {
        AppRegistryDao dao = getRegistryDao();
        return dao != null ? dao.getMostProblematicApps(limit) : null;
    }

    // =========================================================================
    // USAGE SESSION OPERATIONS
    // =========================================================================

    /**
     * Start a new usage session for an app.
     */
    public AppUsageSession startSession(String packageName, int batteryLevel,
            String networkType, String chargingState) {
        AppUsageSessionDao dao = getSessionDao();
        if (dao == null)
            return null;

        AppUsageSession session = AppUsageSession.start(packageName, batteryLevel,
                networkType, chargingState);
        long id = dao.insert(session);
        session.id = id;
        return session;
    }

    /**
     * End an ongoing session.
     */
    public void endSession(String sessionId, int batteryLevel) {
        executor.execute(() -> {
            AppUsageSessionDao dao = getSessionDao();
            if (dao == null)
                return;
            dao.endSession(sessionId, System.currentTimeMillis(), batteryLevel);
        });
    }

    /**
     * Get sessions for an app since a given time.
     */
    public List<AppUsageSession> getSessionsSince(long since) {
        AppUsageSessionDao dao = getSessionDao();
        return dao != null ? dao.getSessionsSince(since) : null;
    }

    // =========================================================================
    // BATTERY ATTRIBUTION OPERATIONS
    // =========================================================================

    /**
     * Record battery usage for an app.
     */
    public void recordBatteryUsage(AppBatteryAttribution attribution) {
        executor.execute(() -> {
            AppBatteryAttributionDao dao = getAttributionDao();
            if (dao == null)
                return;

            attribution.calculateScores();
            dao.insert(attribution);

            // Update app registry aggregates
            updateRegistryAggregates(attribution);
        });
    }

    /**
     * Get top battery drainers since a given time.
     */
    public List<AppBatteryAttribution> getTopDrainers(long since, int limit) {
        AppBatteryAttributionDao dao = getAttributionDao();
        return dao != null ? dao.getTopDrainers(since, limit) : null;
    }

    /**
     * Get overnight drainers for a given time window.
     */
    public List<AppBatteryAttribution> getOvernightDrainers(long overnightStart, long overnightEnd) {
        AppBatteryAttributionDao dao = getAttributionDao();
        return dao != null ? dao.getOvernightDrainers(overnightStart, overnightEnd) : null;
    }

    /**
     * Get app drain summary for AI analysis.
     */
    public List<AppBatteryAttributionDao.AppDrainSummary> getAppDrainSummary(long since, int limit) {
        AppBatteryAttributionDao dao = getAttributionDao();
        return dao != null ? dao.getAppDrainSummary(since, limit) : null;
    }

    // =========================================================================
    // THERMAL IMPACT OPERATIONS
    // =========================================================================

    /**
     * Record thermal impact for an app.
     */
    public void recordThermalImpact(AppThermalImpact impact) {
        executor.execute(() -> {
            AppThermalImpactDao dao = getThermalDao();
            if (dao == null)
                return;

            impact.calculateThermalScore();
            dao.insert(impact);
        });
    }

    /**
     * Get apps with thermal spikes since a given time.
     */
    public List<AppThermalImpact> getThermalSpikeRecords(long since) {
        AppThermalImpactDao dao = getThermalDao();
        return dao != null ? dao.getThermalSpikeRecords(since) : null;
    }

    /**
     * Get thermal spike summary for AI analysis.
     */
    public List<AppThermalImpactDao.AppThermalSummary> getThermalSpikeSummary(long since, int limit) {
        AppThermalImpactDao dao = getThermalDao();
        return dao != null ? dao.getThermalSpikeSummary(since, limit) : null;
    }

    // =========================================================================
    // ANOMALY OPERATIONS
    // =========================================================================

    /**
     * Record an anomaly for an app.
     */
    public void recordAnomaly(AppAnomaly anomaly) {
        executor.execute(() -> {
            AppAnomalyDao dao = getAnomalyDao();
            if (dao == null)
                return;
            dao.insert(anomaly);

            // Update anomaly count in registry
            AppRegistryDao registryDao = getRegistryDao();
            if (registryDao != null) {
                registryDao.incrementAnomalyCount(anomaly.packageName, System.currentTimeMillis());
            }
        });
    }

    /**
     * Get unresolved anomalies.
     */
    public List<AppAnomaly> getUnresolvedAnomalies() {
        AppAnomalyDao dao = getAnomalyDao();
        return dao != null ? dao.getUnresolved() : null;
    }

    /**
     * Get high priority unresolved anomalies.
     */
    public List<AppAnomaly> getUnresolvedHighPriority() {
        AppAnomalyDao dao = getAnomalyDao();
        return dao != null ? dao.getUnresolvedHighPriority() : null;
    }

    /**
     * Get anomalies for an app.
     */
    public List<AppAnomaly> getAnomaliesByPackage(String packageName) {
        AppAnomalyDao dao = getAnomalyDao();
        return dao != null ? dao.getByPackageName(packageName) : null;
    }

    /**
     * Resolve an anomaly.
     */
    public void resolveAnomaly(long anomalyId) {
        executor.execute(() -> {
            AppAnomalyDao dao = getAnomalyDao();
            if (dao == null)
                return;
            dao.resolve(anomalyId, System.currentTimeMillis());
        });
    }

    /**
     * Get anomaly summary for AI analysis.
     */
    public List<AppAnomalyDao.AppAnomalySummary> getAnomalySummary(long since, int limit) {
        AppAnomalyDao dao = getAnomalyDao();
        return dao != null ? dao.getAnomalySummary(since, limit) : null;
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    /**
     * Update app registry aggregates from a battery attribution record.
     */
    private void updateRegistryAggregates(AppBatteryAttribution attribution) {
        AppRegistryDao registryDao = getRegistryDao();
        if (registryDao == null)
            return;

        AppRegistry registry = registryDao.getByPackageName(attribution.packageName);
        if (registry != null) {
            registry.recordSession();
            registry.totalBatteryUsedPct += attribution.batteryPercentUsed;

            // Running average for drain rate
            registry.avgDrainPerHour = ((registry.avgDrainPerHour * (registry.totalSessions - 1))
                    + attribution.drainRatePerHour) / registry.totalSessions;

            registryDao.update(registry);
        }
    }

    // =========================================================================
    // MAINTENANCE
    // =========================================================================

    /**
     * Clean up old data to maintain database size.
     * 
     * @param retentionDays Number of days to retain data
     */
    public void cleanupOldData(int retentionDays) {
        executor.execute(() -> {
            long cutoff = System.currentTimeMillis() - (retentionDays * 24L * 60 * 60 * 1000);

            AppUsageSessionDao sessionDao = getSessionDao();
            if (sessionDao != null)
                sessionDao.deleteOlderThan(cutoff);

            AppBatteryAttributionDao attrDao = getAttributionDao();
            if (attrDao != null)
                attrDao.deleteOlderThan(cutoff);

            AppThermalImpactDao thermalDao = getThermalDao();
            if (thermalDao != null)
                thermalDao.deleteOlderThan(cutoff);

            // Keep anomalies longer for pattern detection
            long anomalyCutoff = System.currentTimeMillis() - (retentionDays * 2L * 24 * 60 * 60 * 1000);
            AppAnomalyDao anomalyDao = getAnomalyDao();
            if (anomalyDao != null)
                anomalyDao.deleteOldResolved(anomalyCutoff);
        });
    }

    /**
     * Shutdown the executor service.
     */
    public void shutdown() {
        executor.shutdown();
    }
}
