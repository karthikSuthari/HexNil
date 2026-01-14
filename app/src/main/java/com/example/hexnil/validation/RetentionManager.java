package com.example.hexnil.validation;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Retention Manager - Data Cleanup & Retention Strategy
 * 
 * Manages data lifecycle with configurable retention policies:
 * 
 * DATA TYPES & DEFAULT RETENTION:
 * - BatterySnapshot: 90 days (high volume, keep for trends)
 * - ChargingSession: 180 days (moderate volume, important history)
 * - DrainEvent: 90 days (derived data, can regenerate)
 * - ThermalEvent: 60 days (event-based, less critical)
 * - BatteryHealth: 365 days (low volume, long-term tracking)
 * - AIAnalysisResult: 30 days (can regenerate)
 * - CalibrationEvent: 365 days (rare but important)
 * 
 * CLEANUP STRATEGIES:
 * 1. Age-based: Delete records older than retention period
 * 2. Count-based: Keep only latest N records
 * 3. Quality-based: Prioritize keeping high-quality records
 * 4. Aggregation: Compress old data into summaries
 * 
 * OEM Integration: Same policies used by device manufacturers.
 */
public class RetentionManager {
    
    private static final String TAG = "RetentionManager";
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // RETENTION POLICIES
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Retention policy for a specific entity type.
     */
    public static class RetentionPolicy {
        private final String entityType;
        private final long maxAgeDays;
        private final int maxRecordCount;
        private final CleanupStrategy strategy;
        private final boolean aggregateBeforeDelete;
        private final int aggregationIntervalHours;
        
        public RetentionPolicy(String entityType, long maxAgeDays, int maxRecordCount,
                                CleanupStrategy strategy, boolean aggregateBeforeDelete,
                                int aggregationIntervalHours) {
            this.entityType = entityType;
            this.maxAgeDays = maxAgeDays;
            this.maxRecordCount = maxRecordCount;
            this.strategy = strategy;
            this.aggregateBeforeDelete = aggregateBeforeDelete;
            this.aggregationIntervalHours = aggregationIntervalHours;
        }
        
        public String getEntityType() { return entityType; }
        public long getMaxAgeDays() { return maxAgeDays; }
        public long getMaxAgeMillis() { return TimeUnit.DAYS.toMillis(maxAgeDays); }
        public int getMaxRecordCount() { return maxRecordCount; }
        public CleanupStrategy getStrategy() { return strategy; }
        public boolean shouldAggregateBeforeDelete() { return aggregateBeforeDelete; }
        public int getAggregationIntervalHours() { return aggregationIntervalHours; }
        
        public long getCutoffTimestamp() {
            return System.currentTimeMillis() - getMaxAgeMillis();
        }
    }
    
    public enum CleanupStrategy {
        AGE_BASED,          // Delete based on age
        COUNT_BASED,        // Keep only N most recent
        HYBRID,             // Age first, then count
        QUALITY_AWARE,      // Keep high-quality records longer
        AGGREGATION_FIRST   // Aggregate before deleting
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // DEFAULT POLICIES
    // ═══════════════════════════════════════════════════════════════════════════════
    
    private static final Map<String, RetentionPolicy> DEFAULT_POLICIES = new HashMap<>();
    
    static {
        // Battery Snapshots - 90 days, max 100K records, aggregate old data
        DEFAULT_POLICIES.put("BatterySnapshot", new RetentionPolicy(
                "BatterySnapshot",
                90,                     // 90 days retention
                100000,                 // Max 100K records
                CleanupStrategy.AGGREGATION_FIRST,
                true,                   // Aggregate before delete
                24                      // Aggregate to daily summaries
        ));
        
        // Charging Sessions - 180 days, more important for patterns
        DEFAULT_POLICIES.put("ChargingSession", new RetentionPolicy(
                "ChargingSession",
                180,                    // 180 days retention
                10000,                  // Max 10K records
                CleanupStrategy.HYBRID,
                false,                  // Don't aggregate
                0
        ));
        
        // Drain Events - 90 days, derived data
        DEFAULT_POLICIES.put("DrainEvent", new RetentionPolicy(
                "DrainEvent",
                90,                     // 90 days retention
                50000,                  // Max 50K records
                CleanupStrategy.AGE_BASED,
                true,                   // Aggregate before delete
                24
        ));
        
        // Thermal Events - 60 days, event-based
        DEFAULT_POLICIES.put("ThermalEvent", new RetentionPolicy(
                "ThermalEvent",
                60,                     // 60 days retention
                20000,                  // Max 20K records
                CleanupStrategy.AGE_BASED,
                false,
                0
        ));
        
        // Battery Health - 365 days, low volume, important
        DEFAULT_POLICIES.put("BatteryHealth", new RetentionPolicy(
                "BatteryHealth",
                365,                    // Full year retention
                5000,                   // Max 5K records
                CleanupStrategy.QUALITY_AWARE,
                false,
                0
        ));
        
        // AI Analysis Results - 30 days, can regenerate
        DEFAULT_POLICIES.put("AIAnalysisResult", new RetentionPolicy(
                "AIAnalysisResult",
                30,                     // 30 days retention
                1000,                   // Max 1K records
                CleanupStrategy.AGE_BASED,
                false,
                0
        ));
        
        // Calibration Events - 365 days, rare but important
        DEFAULT_POLICIES.put("CalibrationEvent", new RetentionPolicy(
                "CalibrationEvent",
                365,                    // Full year retention
                500,                    // Max 500 records
                CleanupStrategy.AGE_BASED,
                false,
                0
        ));
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // CLEANUP PLAN
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Cleanup plan for an entity type.
     */
    public static class CleanupPlan {
        private final String entityType;
        private final long cutoffTimestamp;
        private final int estimatedRecordsToDelete;
        private final int estimatedRecordsToAggregate;
        private final long estimatedSpaceSavedBytes;
        private final List<String> actions;
        private final boolean requiresConfirmation;
        
        public CleanupPlan(String entityType, long cutoffTimestamp,
                           int estimatedRecordsToDelete, int estimatedRecordsToAggregate,
                           long estimatedSpaceSavedBytes, List<String> actions,
                           boolean requiresConfirmation) {
            this.entityType = entityType;
            this.cutoffTimestamp = cutoffTimestamp;
            this.estimatedRecordsToDelete = estimatedRecordsToDelete;
            this.estimatedRecordsToAggregate = estimatedRecordsToAggregate;
            this.estimatedSpaceSavedBytes = estimatedSpaceSavedBytes;
            this.actions = actions != null ? actions : new ArrayList<>();
            this.requiresConfirmation = requiresConfirmation;
        }
        
        public String getEntityType() { return entityType; }
        public long getCutoffTimestamp() { return cutoffTimestamp; }
        public int getEstimatedRecordsToDelete() { return estimatedRecordsToDelete; }
        public int getEstimatedRecordsToAggregate() { return estimatedRecordsToAggregate; }
        public long getEstimatedSpaceSavedBytes() { return estimatedSpaceSavedBytes; }
        public List<String> getActions() { return actions; }
        public boolean requiresConfirmation() { return requiresConfirmation; }
        
        public boolean hasWork() {
            return estimatedRecordsToDelete > 0 || estimatedRecordsToAggregate > 0;
        }
    }
    
    /**
     * Result of cleanup execution.
     */
    public static class CleanupResult {
        private final String entityType;
        private final boolean success;
        private final int recordsDeleted;
        private final int recordsAggregated;
        private final long spaceSavedBytes;
        private final long executionTimeMs;
        private final String errorMessage;
        
        public CleanupResult(String entityType, boolean success,
                              int recordsDeleted, int recordsAggregated,
                              long spaceSavedBytes, long executionTimeMs, String errorMessage) {
            this.entityType = entityType;
            this.success = success;
            this.recordsDeleted = recordsDeleted;
            this.recordsAggregated = recordsAggregated;
            this.spaceSavedBytes = spaceSavedBytes;
            this.executionTimeMs = executionTimeMs;
            this.errorMessage = errorMessage;
        }
        
        public String getEntityType() { return entityType; }
        public boolean isSuccess() { return success; }
        public int getRecordsDeleted() { return recordsDeleted; }
        public int getRecordsAggregated() { return recordsAggregated; }
        public long getSpaceSavedBytes() { return spaceSavedBytes; }
        public long getExecutionTimeMs() { return executionTimeMs; }
        public String getErrorMessage() { return errorMessage; }
        
        public static CleanupResult success(String entityType, int deleted, int aggregated,
                                             long spaceSaved, long timeMs) {
            return new CleanupResult(entityType, true, deleted, aggregated, spaceSaved, timeMs, null);
        }
        
        public static CleanupResult failed(String entityType, String error) {
            return new CleanupResult(entityType, false, 0, 0, 0, 0, error);
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // INSTANCE FIELDS
    // ═══════════════════════════════════════════════════════════════════════════════
    
    private final Map<String, RetentionPolicy> policies;
    private final List<CleanupListener> listeners;
    private long lastCleanupTimestamp;
    private boolean cleanupInProgress;
    
    public RetentionManager() {
        this.policies = new HashMap<>(DEFAULT_POLICIES);
        this.listeners = new ArrayList<>();
        this.lastCleanupTimestamp = 0;
        this.cleanupInProgress = false;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // POLICY MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Get policy for an entity type.
     */
    public RetentionPolicy getPolicy(String entityType) {
        return policies.get(entityType);
    }
    
    /**
     * Update policy for an entity type.
     */
    public void setPolicy(String entityType, RetentionPolicy policy) {
        policies.put(entityType, policy);
        Log.i(TAG, "Updated retention policy for " + entityType + 
              ": " + policy.getMaxAgeDays() + " days, max " + policy.getMaxRecordCount() + " records");
    }
    
    /**
     * Get all policies.
     */
    public Map<String, RetentionPolicy> getAllPolicies() {
        return new HashMap<>(policies);
    }
    
    /**
     * Reset to default policies.
     */
    public void resetToDefaults() {
        policies.clear();
        policies.putAll(DEFAULT_POLICIES);
        Log.i(TAG, "Reset all retention policies to defaults");
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // CLEANUP PLANNING
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Create a cleanup plan for a specific entity type.
     * 
     * @param entityType The entity type
     * @param currentRecordCount Current number of records
     * @param oldestRecordTimestamp Timestamp of oldest record
     * @param averageRecordSizeBytes Average size per record
     */
    public CleanupPlan createCleanupPlan(String entityType, int currentRecordCount,
                                          long oldestRecordTimestamp, int averageRecordSizeBytes) {
        
        RetentionPolicy policy = policies.get(entityType);
        if (policy == null) {
            // No policy - use default age-based
            policy = new RetentionPolicy(entityType, 90, 50000, 
                    CleanupStrategy.AGE_BASED, false, 0);
        }
        
        List<String> actions = new ArrayList<>();
        int estimatedDeletes = 0;
        int estimatedAggregates = 0;
        boolean requiresConfirmation = false;
        
        long cutoffTimestamp = policy.getCutoffTimestamp();
        
        // Calculate estimated deletes based on strategy
        switch (policy.getStrategy()) {
            case AGE_BASED:
                if (oldestRecordTimestamp < cutoffTimestamp) {
                    // Estimate based on record age distribution (assume even distribution)
                    long oldestAge = System.currentTimeMillis() - oldestRecordTimestamp;
                    long ageToDelete = System.currentTimeMillis() - cutoffTimestamp;
                    double deleteRatio = (double) ageToDelete / oldestAge;
                    estimatedDeletes = (int) (currentRecordCount * deleteRatio);
                    actions.add("Delete records older than " + policy.getMaxAgeDays() + " days");
                }
                break;
                
            case COUNT_BASED:
                if (currentRecordCount > policy.getMaxRecordCount()) {
                    estimatedDeletes = currentRecordCount - policy.getMaxRecordCount();
                    actions.add("Delete oldest records to maintain max " + policy.getMaxRecordCount());
                }
                break;
                
            case HYBRID:
                // First apply age-based
                if (oldestRecordTimestamp < cutoffTimestamp) {
                    long oldestAge = System.currentTimeMillis() - oldestRecordTimestamp;
                    long ageToDelete = System.currentTimeMillis() - cutoffTimestamp;
                    double deleteRatio = (double) ageToDelete / oldestAge;
                    estimatedDeletes = (int) (currentRecordCount * deleteRatio);
                    actions.add("Delete records older than " + policy.getMaxAgeDays() + " days");
                }
                // Then apply count-based
                int remaining = currentRecordCount - estimatedDeletes;
                if (remaining > policy.getMaxRecordCount()) {
                    int additional = remaining - policy.getMaxRecordCount();
                    estimatedDeletes += additional;
                    actions.add("Additional deletion to maintain max " + policy.getMaxRecordCount());
                }
                break;
                
            case QUALITY_AWARE:
                // Delete old low-quality records first
                if (oldestRecordTimestamp < cutoffTimestamp || 
                    currentRecordCount > policy.getMaxRecordCount()) {
                    estimatedDeletes = Math.max(
                            currentRecordCount - policy.getMaxRecordCount(),
                            0
                    );
                    actions.add("Delete oldest low-quality records first");
                }
                break;
                
            case AGGREGATION_FIRST:
                if (oldestRecordTimestamp < cutoffTimestamp) {
                    // Estimate records to aggregate
                    long aggregationCutoff = System.currentTimeMillis() - 
                            TimeUnit.DAYS.toMillis(policy.getMaxAgeDays() / 2);
                    
                    if (oldestRecordTimestamp < aggregationCutoff) {
                        estimatedAggregates = estimatedDeletes / 2;  // Rough estimate
                        actions.add("Aggregate records older than " + (policy.getMaxAgeDays() / 2) + 
                                   " days into " + policy.getAggregationIntervalHours() + "-hour summaries");
                    }
                    
                    estimatedDeletes = estimatedDeletes / 2;
                    actions.add("Delete records older than " + policy.getMaxAgeDays() + " days");
                }
                break;
        }
        
        // Large deletions require confirmation
        if (estimatedDeletes > 10000) {
            requiresConfirmation = true;
            actions.add("⚠️ Large cleanup - requires confirmation");
        }
        
        long estimatedSpaceSaved = (long) estimatedDeletes * averageRecordSizeBytes;
        
        return new CleanupPlan(
                entityType,
                cutoffTimestamp,
                estimatedDeletes,
                estimatedAggregates,
                estimatedSpaceSaved,
                actions,
                requiresConfirmation
        );
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // CLEANUP QUERIES (For DAO to execute)
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Get the SQL WHERE clause for age-based cleanup.
     */
    public String getAgeBasedDeleteWhereClause(String entityType, String timestampColumn) {
        RetentionPolicy policy = policies.get(entityType);
        if (policy == null) return null;
        
        return timestampColumn + " < " + policy.getCutoffTimestamp();
    }
    
    /**
     * Get cutoff timestamp for a specific entity type.
     */
    public long getCutoffTimestamp(String entityType) {
        RetentionPolicy policy = policies.get(entityType);
        if (policy == null) {
            // Default 90 days
            return System.currentTimeMillis() - TimeUnit.DAYS.toMillis(90);
        }
        return policy.getCutoffTimestamp();
    }
    
    /**
     * Get max record count for a specific entity type.
     */
    public int getMaxRecordCount(String entityType) {
        RetentionPolicy policy = policies.get(entityType);
        if (policy == null) return 50000;  // Default
        return policy.getMaxRecordCount();
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // CLEANUP SCHEDULING
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Check if cleanup should run.
     */
    public boolean shouldRunCleanup() {
        if (cleanupInProgress) return false;
        
        // Run cleanup at most once per day
        long dayInMillis = TimeUnit.DAYS.toMillis(1);
        return System.currentTimeMillis() - lastCleanupTimestamp > dayInMillis;
    }
    
    /**
     * Mark cleanup as started.
     */
    public void markCleanupStarted() {
        cleanupInProgress = true;
        Log.i(TAG, "Database cleanup started");
    }
    
    /**
     * Mark cleanup as completed.
     */
    public void markCleanupCompleted(List<CleanupResult> results) {
        cleanupInProgress = false;
        lastCleanupTimestamp = System.currentTimeMillis();
        
        int totalDeleted = 0;
        int totalAggregated = 0;
        long totalSpaceSaved = 0;
        
        for (CleanupResult result : results) {
            if (result.isSuccess()) {
                totalDeleted += result.getRecordsDeleted();
                totalAggregated += result.getRecordsAggregated();
                totalSpaceSaved += result.getSpaceSavedBytes();
            }
        }
        
        Log.i(TAG, "Database cleanup completed: " + totalDeleted + " deleted, " +
              totalAggregated + " aggregated, " + formatBytes(totalSpaceSaved) + " saved");
        
        // Notify listeners
        for (CleanupListener listener : listeners) {
            listener.onCleanupCompleted(results);
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // AGGRESSIVE CLEANUP (Emergency space reclamation)
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Configuration for aggressive cleanup when storage is critically low.
     */
    public static class AggressiveCleanupConfig {
        public final long snapshotRetentionDays;
        public final long sessionRetentionDays;
        public final long eventRetentionDays;
        public final int maxSnapshotsToKeep;
        public final boolean deleteAllAIResults;
        
        public AggressiveCleanupConfig(long snapshotRetentionDays, long sessionRetentionDays,
                                        long eventRetentionDays, int maxSnapshotsToKeep,
                                        boolean deleteAllAIResults) {
            this.snapshotRetentionDays = snapshotRetentionDays;
            this.sessionRetentionDays = sessionRetentionDays;
            this.eventRetentionDays = eventRetentionDays;
            this.maxSnapshotsToKeep = maxSnapshotsToKeep;
            this.deleteAllAIResults = deleteAllAIResults;
        }
    }
    
    /**
     * Get aggressive cleanup configuration based on available space.
     */
    public AggressiveCleanupConfig getAggressiveConfig(long availableSpaceMB) {
        if (availableSpaceMB < 50) {
            // CRITICAL: Less than 50MB free
            return new AggressiveCleanupConfig(7, 14, 3, 5000, true);
        } else if (availableSpaceMB < 100) {
            // WARNING: Less than 100MB free
            return new AggressiveCleanupConfig(14, 30, 7, 10000, true);
        } else if (availableSpaceMB < 200) {
            // CAUTION: Less than 200MB free
            return new AggressiveCleanupConfig(30, 60, 14, 25000, false);
        }
        
        // Normal operation
        return null;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // LISTENERS
    // ═══════════════════════════════════════════════════════════════════════════════
    
    public interface CleanupListener {
        void onCleanupCompleted(List<CleanupResult> results);
    }
    
    public void addCleanupListener(CleanupListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }
    
    public void removeCleanupListener(CleanupListener listener) {
        listeners.remove(listener);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // UTILITY
    // ═══════════════════════════════════════════════════════════════════════════════
    
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
