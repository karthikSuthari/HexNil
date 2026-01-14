package com.example.hexnil.validation;

import android.content.Context;
import android.os.StatFs;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Database Health Monitor - Comprehensive Database Monitoring
 * 
 * Monitors all aspects of database health:
 * - Size and growth trends
 * - Table record counts
 * - Query performance
 * - Integrity issues
 * - Storage availability
 * - Fragmentation
 * 
 * OEM Integration: Same monitoring used in production devices.
 */
public class DatabaseHealthMonitor {
    
    private static final String TAG = "DatabaseHealthMonitor";
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // THRESHOLDS
    // ═══════════════════════════════════════════════════════════════════════════════
    
    // Size thresholds
    private static final long DATABASE_SIZE_WARNING_MB = 100;     // Warn at 100MB
    private static final long DATABASE_SIZE_CRITICAL_MB = 500;    // Critical at 500MB
    
    // Storage thresholds
    private static final long FREE_SPACE_WARNING_MB = 200;        // Warn when < 200MB free
    private static final long FREE_SPACE_CRITICAL_MB = 50;        // Critical when < 50MB free
    
    // Record count thresholds
    private static final int SNAPSHOT_COUNT_WARNING = 200000;
    private static final int SNAPSHOT_COUNT_CRITICAL = 500000;
    
    // Performance thresholds
    private static final long QUERY_SLOW_THRESHOLD_MS = 500;      // Slow query threshold
    private static final long QUERY_VERY_SLOW_THRESHOLD_MS = 2000;
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // HEALTH STATUS
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Overall database health status.
     */
    public enum HealthStatus {
        HEALTHY,        // All systems normal
        DEGRADED,       // Some issues but functional
        WARNING,        // Action recommended
        CRITICAL        // Immediate action required
    }
    
    /**
     * Comprehensive database health report.
     */
    public static class HealthReport {
        private final long timestamp;
        private final HealthStatus overallStatus;
        private final float healthScore;  // 0-100
        
        // Size metrics
        private final long databaseSizeBytes;
        private final long availableStorageBytes;
        private final float storageUsagePercent;
        
        // Record counts
        private final Map<String, Integer> tableCounts;
        private final int totalRecordCount;
        
        // Integrity
        private final DataIntegrityChecker.IntegrityCheckResult integrityResult;
        
        // Performance
        private final Map<String, Long> queryLatencies;
        private final int slowQueryCount;
        
        // Issues
        private final List<HealthIssue> issues;
        
        // Recommendations
        private final List<String> recommendations;
        
        public HealthReport(long timestamp, HealthStatus overallStatus, float healthScore,
                            long databaseSizeBytes, long availableStorageBytes, float storageUsagePercent,
                            Map<String, Integer> tableCounts, int totalRecordCount,
                            DataIntegrityChecker.IntegrityCheckResult integrityResult,
                            Map<String, Long> queryLatencies, int slowQueryCount,
                            List<HealthIssue> issues, List<String> recommendations) {
            this.timestamp = timestamp;
            this.overallStatus = overallStatus;
            this.healthScore = healthScore;
            this.databaseSizeBytes = databaseSizeBytes;
            this.availableStorageBytes = availableStorageBytes;
            this.storageUsagePercent = storageUsagePercent;
            this.tableCounts = tableCounts != null ? tableCounts : new HashMap<>();
            this.totalRecordCount = totalRecordCount;
            this.integrityResult = integrityResult;
            this.queryLatencies = queryLatencies != null ? queryLatencies : new HashMap<>();
            this.slowQueryCount = slowQueryCount;
            this.issues = issues != null ? issues : new ArrayList<>();
            this.recommendations = recommendations != null ? recommendations : new ArrayList<>();
        }
        
        // Getters
        public long getTimestamp() { return timestamp; }
        public HealthStatus getOverallStatus() { return overallStatus; }
        public float getHealthScore() { return healthScore; }
        public long getDatabaseSizeBytes() { return databaseSizeBytes; }
        public long getAvailableStorageBytes() { return availableStorageBytes; }
        public float getStorageUsagePercent() { return storageUsagePercent; }
        public Map<String, Integer> getTableCounts() { return tableCounts; }
        public int getTotalRecordCount() { return totalRecordCount; }
        public DataIntegrityChecker.IntegrityCheckResult getIntegrityResult() { return integrityResult; }
        public Map<String, Long> getQueryLatencies() { return queryLatencies; }
        public int getSlowQueryCount() { return slowQueryCount; }
        public List<HealthIssue> getIssues() { return issues; }
        public List<String> getRecommendations() { return recommendations; }
        
        public boolean hasIssues() { return !issues.isEmpty(); }
        public boolean hasCriticalIssues() {
            for (HealthIssue issue : issues) {
                if (issue.getSeverity() == HealthStatus.CRITICAL) return true;
            }
            return false;
        }
        
        public String getSummary() {
            return String.format("Database Health: %s (Score: %.0f/100) - Size: %s, Records: %d, Issues: %d",
                    overallStatus.name(),
                    healthScore,
                    formatBytes(databaseSizeBytes),
                    totalRecordCount,
                    issues.size());
        }
    }
    
    /**
     * Individual health issue.
     */
    public static class HealthIssue {
        private final String category;
        private final HealthStatus severity;
        private final String description;
        private final String recommendation;
        
        public HealthIssue(String category, HealthStatus severity, 
                           String description, String recommendation) {
            this.category = category;
            this.severity = severity;
            this.description = description;
            this.recommendation = recommendation;
        }
        
        public String getCategory() { return category; }
        public HealthStatus getSeverity() { return severity; }
        public String getDescription() { return description; }
        public String getRecommendation() { return recommendation; }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // INSTANCE FIELDS
    // ═══════════════════════════════════════════════════════════════════════════════
    
    private final Context context;
    private final DataIntegrityChecker integrityChecker;
    
    // Query performance tracking
    private final Map<String, List<Long>> queryLatencyHistory;
    private static final int LATENCY_HISTORY_SIZE = 100;
    
    // Growth tracking
    private final List<SizeSnapshot> sizeHistory;
    private static final int SIZE_HISTORY_SIZE = 30;  // 30 days
    
    public DatabaseHealthMonitor(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.integrityChecker = new DataIntegrityChecker();
        this.queryLatencyHistory = new HashMap<>();
        this.sizeHistory = new ArrayList<>();
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // SIZE MONITORING
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Size snapshot for growth tracking.
     */
    public static class SizeSnapshot {
        public final long timestamp;
        public final long sizeBytes;
        public final int recordCount;
        
        public SizeSnapshot(long timestamp, long sizeBytes, int recordCount) {
            this.timestamp = timestamp;
            this.sizeBytes = sizeBytes;
            this.recordCount = recordCount;
        }
    }
    
    /**
     * Get database file size.
     */
    public long getDatabaseSize(String databaseName) {
        File dbFile = context.getDatabasePath(databaseName);
        if (dbFile.exists()) {
            // Include WAL and SHM files
            long size = dbFile.length();
            
            File walFile = new File(dbFile.getPath() + "-wal");
            if (walFile.exists()) {
                size += walFile.length();
            }
            
            File shmFile = new File(dbFile.getPath() + "-shm");
            if (shmFile.exists()) {
                size += shmFile.length();
            }
            
            return size;
        }
        return 0;
    }
    
    /**
     * Get available storage space.
     */
    public long getAvailableStorage() {
        File dataDir = context.getFilesDir();
        StatFs statFs = new StatFs(dataDir.getPath());
        return statFs.getAvailableBytes();
    }
    
    /**
     * Calculate storage usage percentage for app.
     */
    public float calculateStorageUsage(long databaseSizeBytes) {
        long available = getAvailableStorage();
        long total = available + databaseSizeBytes;  // Approximate total
        if (total == 0) return 0;
        return (float) databaseSizeBytes / total * 100;
    }
    
    /**
     * Record current size for growth tracking.
     */
    public void recordSizeSnapshot(long sizeBytes, int recordCount) {
        SizeSnapshot snapshot = new SizeSnapshot(System.currentTimeMillis(), sizeBytes, recordCount);
        sizeHistory.add(snapshot);
        
        // Keep only recent history
        while (sizeHistory.size() > SIZE_HISTORY_SIZE) {
            sizeHistory.remove(0);
        }
    }
    
    /**
     * Calculate daily growth rate.
     */
    public float calculateDailyGrowthRate() {
        if (sizeHistory.size() < 2) return 0;
        
        SizeSnapshot oldest = sizeHistory.get(0);
        SizeSnapshot newest = sizeHistory.get(sizeHistory.size() - 1);
        
        long timeDelta = newest.timestamp - oldest.timestamp;
        if (timeDelta <= 0) return 0;
        
        long sizeDelta = newest.sizeBytes - oldest.sizeBytes;
        double days = timeDelta / (double) TimeUnit.DAYS.toMillis(1);
        
        return (float) (sizeDelta / days);
    }
    
    /**
     * Estimate days until storage full.
     */
    public int estimateDaysUntilFull() {
        float dailyGrowth = calculateDailyGrowthRate();
        if (dailyGrowth <= 0) return Integer.MAX_VALUE;
        
        long availableStorage = getAvailableStorage();
        return (int) (availableStorage / dailyGrowth);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // QUERY PERFORMANCE MONITORING
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Record query execution time.
     */
    public void recordQueryLatency(String queryName, long latencyMs) {
        List<Long> history = queryLatencyHistory.get(queryName);
        if (history == null) {
            history = new ArrayList<>();
            queryLatencyHistory.put(queryName, history);
        }
        
        history.add(latencyMs);
        
        // Keep only recent history
        while (history.size() > LATENCY_HISTORY_SIZE) {
            history.remove(0);
        }
        
        // Log slow queries
        if (latencyMs > QUERY_VERY_SLOW_THRESHOLD_MS) {
            Log.w(TAG, "VERY SLOW QUERY: " + queryName + " took " + latencyMs + "ms");
        } else if (latencyMs > QUERY_SLOW_THRESHOLD_MS) {
            Log.d(TAG, "Slow query: " + queryName + " took " + latencyMs + "ms");
        }
    }
    
    /**
     * Get average latency for a query.
     */
    public long getAverageQueryLatency(String queryName) {
        List<Long> history = queryLatencyHistory.get(queryName);
        if (history == null || history.isEmpty()) return 0;
        
        long sum = 0;
        for (Long latency : history) {
            sum += latency;
        }
        return sum / history.size();
    }
    
    /**
     * Get count of slow queries in recent history.
     */
    public int getSlowQueryCount() {
        int count = 0;
        for (List<Long> history : queryLatencyHistory.values()) {
            for (Long latency : history) {
                if (latency > QUERY_SLOW_THRESHOLD_MS) {
                    count++;
                }
            }
        }
        return count;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // HEALTH REPORT GENERATION
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Generate comprehensive health report.
     */
    public HealthReport generateHealthReport(
            String databaseName,
            Map<String, Integer> tableCounts,
            DataIntegrityChecker.IntegrityCheckResult integrityResult) {
        
        long timestamp = System.currentTimeMillis();
        List<HealthIssue> issues = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();
        
        // === SIZE ANALYSIS ===
        long databaseSize = getDatabaseSize(databaseName);
        long availableStorage = getAvailableStorage();
        float storageUsage = calculateStorageUsage(databaseSize);
        
        // Check database size
        long sizeMB = databaseSize / (1024 * 1024);
        if (sizeMB > DATABASE_SIZE_CRITICAL_MB) {
            issues.add(new HealthIssue("SIZE", HealthStatus.CRITICAL,
                    "Database size (" + sizeMB + "MB) exceeds critical threshold",
                    "Run cleanup immediately to reduce database size"));
        } else if (sizeMB > DATABASE_SIZE_WARNING_MB) {
            issues.add(new HealthIssue("SIZE", HealthStatus.WARNING,
                    "Database size (" + sizeMB + "MB) exceeds warning threshold",
                    "Consider running cleanup to prevent future issues"));
        }
        
        // Check available storage
        long availableMB = availableStorage / (1024 * 1024);
        if (availableMB < FREE_SPACE_CRITICAL_MB) {
            issues.add(new HealthIssue("STORAGE", HealthStatus.CRITICAL,
                    "Available storage (" + availableMB + "MB) critically low",
                    "Free up space immediately - app may malfunction"));
        } else if (availableMB < FREE_SPACE_WARNING_MB) {
            issues.add(new HealthIssue("STORAGE", HealthStatus.WARNING,
                    "Available storage (" + availableMB + "MB) is low",
                    "Consider freeing space or reducing retention periods"));
        }
        
        // Check growth rate
        int daysUntilFull = estimateDaysUntilFull();
        if (daysUntilFull < 7) {
            issues.add(new HealthIssue("GROWTH", HealthStatus.CRITICAL,
                    "At current growth rate, storage will be full in ~" + daysUntilFull + " days",
                    "Reduce data collection rate or run aggressive cleanup"));
        } else if (daysUntilFull < 30) {
            issues.add(new HealthIssue("GROWTH", HealthStatus.WARNING,
                    "At current growth rate, storage will be full in ~" + daysUntilFull + " days",
                    "Plan for cleanup or storage expansion"));
        }
        
        // === RECORD COUNT ANALYSIS ===
        int totalRecords = 0;
        for (Integer count : tableCounts.values()) {
            totalRecords += count;
        }
        
        Integer snapshotCount = tableCounts.get("battery_snapshots");
        if (snapshotCount != null) {
            if (snapshotCount > SNAPSHOT_COUNT_CRITICAL) {
                issues.add(new HealthIssue("RECORDS", HealthStatus.CRITICAL,
                        "Too many snapshots (" + snapshotCount + ") - may impact performance",
                        "Run cleanup to remove old snapshots"));
            } else if (snapshotCount > SNAPSHOT_COUNT_WARNING) {
                issues.add(new HealthIssue("RECORDS", HealthStatus.WARNING,
                        "High snapshot count (" + snapshotCount + ")",
                        "Consider adjusting retention policy"));
            }
        }
        
        // === INTEGRITY ANALYSIS ===
        if (integrityResult != null) {
            if (integrityResult.hasCriticalIssues()) {
                issues.add(new HealthIssue("INTEGRITY", HealthStatus.CRITICAL,
                        integrityResult.getCriticalCount() + " critical integrity issues found",
                        "Review and fix data integrity issues"));
            } else if (integrityResult.hasIssues()) {
                issues.add(new HealthIssue("INTEGRITY", HealthStatus.WARNING,
                        integrityResult.getWarningCount() + " integrity warnings",
                        "Review data quality issues"));
            }
        }
        
        // === PERFORMANCE ANALYSIS ===
        int slowQueries = getSlowQueryCount();
        Map<String, Long> avgLatencies = new HashMap<>();
        for (String query : queryLatencyHistory.keySet()) {
            avgLatencies.put(query, getAverageQueryLatency(query));
        }
        
        if (slowQueries > 50) {
            issues.add(new HealthIssue("PERFORMANCE", HealthStatus.WARNING,
                    slowQueries + " slow queries detected recently",
                    "Consider adding indices or optimizing queries"));
        }
        
        // === CALCULATE OVERALL STATUS ===
        HealthStatus overallStatus = calculateOverallStatus(issues);
        float healthScore = calculateHealthScore(issues, integrityResult, sizeMB, availableMB);
        
        // === GENERATE RECOMMENDATIONS ===
        recommendations.addAll(generateRecommendations(issues, tableCounts, databaseSize));
        
        // Record size snapshot for tracking
        recordSizeSnapshot(databaseSize, totalRecords);
        
        return new HealthReport(
                timestamp,
                overallStatus,
                healthScore,
                databaseSize,
                availableStorage,
                storageUsage,
                tableCounts,
                totalRecords,
                integrityResult,
                avgLatencies,
                slowQueries,
                issues,
                recommendations
        );
    }
    
    /**
     * Calculate overall status from issues.
     */
    private HealthStatus calculateOverallStatus(List<HealthIssue> issues) {
        boolean hasCritical = false;
        boolean hasWarning = false;
        boolean hasDegraded = false;
        
        for (HealthIssue issue : issues) {
            switch (issue.getSeverity()) {
                case CRITICAL:
                    hasCritical = true;
                    break;
                case WARNING:
                    hasWarning = true;
                    break;
                case DEGRADED:
                    hasDegraded = true;
                    break;
            }
        }
        
        if (hasCritical) return HealthStatus.CRITICAL;
        if (hasWarning) return HealthStatus.WARNING;
        if (hasDegraded) return HealthStatus.DEGRADED;
        return HealthStatus.HEALTHY;
    }
    
    /**
     * Calculate health score (0-100).
     */
    private float calculateHealthScore(List<HealthIssue> issues, 
                                        DataIntegrityChecker.IntegrityCheckResult integrityResult,
                                        long sizeMB, long availableMB) {
        float score = 100.0f;
        
        // Deduct for issues
        for (HealthIssue issue : issues) {
            switch (issue.getSeverity()) {
                case CRITICAL:
                    score -= 25;
                    break;
                case WARNING:
                    score -= 10;
                    break;
                case DEGRADED:
                    score -= 5;
                    break;
            }
        }
        
        // Factor in integrity score
        if (integrityResult != null) {
            score = score * 0.7f + integrityResult.getIntegrityScore() * 0.3f;
        }
        
        // Factor in storage health
        if (availableMB < FREE_SPACE_CRITICAL_MB) {
            score -= 20;
        } else if (availableMB < FREE_SPACE_WARNING_MB) {
            score -= 10;
        }
        
        return Math.max(0, Math.min(100, score));
    }
    
    /**
     * Generate actionable recommendations.
     */
    private List<String> generateRecommendations(List<HealthIssue> issues,
                                                  Map<String, Integer> tableCounts,
                                                  long databaseSize) {
        List<String> recommendations = new ArrayList<>();
        
        // Gather all issue recommendations
        for (HealthIssue issue : issues) {
            if (issue.getRecommendation() != null && !issue.getRecommendation().isEmpty()) {
                recommendations.add(issue.getRecommendation());
            }
        }
        
        // Add proactive recommendations
        if (recommendations.isEmpty()) {
            recommendations.add("Database health is good - no action required");
        }
        
        // Add scheduling recommendations
        long sizeMB = databaseSize / (1024 * 1024);
        if (sizeMB > 50) {
            recommendations.add("Consider enabling automatic daily cleanup");
        }
        
        return recommendations;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // QUICK HEALTH CHECK
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Quick health check - returns true if critical issues exist.
     */
    public boolean hasCriticalIssues(String databaseName) {
        long availableStorage = getAvailableStorage();
        long databaseSize = getDatabaseSize(databaseName);
        
        // Critical storage
        if (availableStorage < FREE_SPACE_CRITICAL_MB * 1024 * 1024) {
            return true;
        }
        
        // Critical database size
        if (databaseSize > DATABASE_SIZE_CRITICAL_MB * 1024 * 1024) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Quick storage check.
     */
    public boolean isStorageLow() {
        long availableStorage = getAvailableStorage();
        return availableStorage < FREE_SPACE_WARNING_MB * 1024 * 1024;
    }
    
    /**
     * Quick storage check - critical level.
     */
    public boolean isStorageCritical() {
        long availableStorage = getAvailableStorage();
        return availableStorage < FREE_SPACE_CRITICAL_MB * 1024 * 1024;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // UTILITY
    // ═══════════════════════════════════════════════════════════════════════════════
    
    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
