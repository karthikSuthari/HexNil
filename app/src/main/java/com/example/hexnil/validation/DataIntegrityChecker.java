package com.example.hexnil.validation;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.hexnil.model.BatterySnapshot;
import com.example.hexnil.model.ChargingSession;
import com.example.hexnil.model.DrainEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Data Integrity Checker - Detects Data Quality Issues
 * 
 * Catches critical data problems:
 * 1. FROZEN SNAPSHOTS - Same values repeating (stuck sensor?)
 * 2. INCOMPLETE SESSIONS - Started but never ended
 * 3. MISSING DATA - Gaps in expected data
 * 4. ANOMALOUS PATTERNS - Values that don't make physical sense
 * 
 * OEM Quality: These are the same checks device manufacturers run.
 */
public class DataIntegrityChecker {
    
    private static final String TAG = "DataIntegrityChecker";
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // CONFIGURATION THRESHOLDS
    // ═══════════════════════════════════════════════════════════════════════════════
    
    // Frozen data detection
    private static final int FROZEN_SNAPSHOT_THRESHOLD = 10;      // 10+ identical = frozen
    private static final long FROZEN_DURATION_THRESHOLD_MS = TimeUnit.MINUTES.toMillis(30);
    
    // Gap detection
    private static final long EXPECTED_SNAPSHOT_INTERVAL_MS = TimeUnit.MINUTES.toMillis(5);
    private static final long GAP_WARNING_THRESHOLD_MS = TimeUnit.MINUTES.toMillis(30);
    private static final long GAP_CRITICAL_THRESHOLD_MS = TimeUnit.HOURS.toMillis(2);
    
    // Stale session detection
    private static final long STALE_SESSION_THRESHOLD_MS = TimeUnit.HOURS.toMillis(12);
    
    // Anomaly detection
    private static final int MAX_LEVEL_JUMP_PER_MINUTE = 5;  // Max 5% per minute = 300%/hr
    private static final int MAX_TEMP_JUMP_PER_MINUTE = 50;  // Max 5°C per minute
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // INTEGRITY CHECK RESULTS
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Comprehensive integrity check result.
     */
    public static class IntegrityCheckResult {
        private final long checkTimestamp;
        private final int totalRecordsChecked;
        private final List<IntegrityIssue> issues;
        private final Map<String, Integer> issueCounts;
        private final float integrityScore;  // 0-100
        
        public IntegrityCheckResult(long checkTimestamp, int totalRecordsChecked,
                                     List<IntegrityIssue> issues, float integrityScore) {
            this.checkTimestamp = checkTimestamp;
            this.totalRecordsChecked = totalRecordsChecked;
            this.issues = issues != null ? issues : new ArrayList<>();
            this.integrityScore = integrityScore;
            
            // Count issues by type
            this.issueCounts = new HashMap<>();
            for (IntegrityIssue issue : this.issues) {
                String type = issue.getType().name();
                issueCounts.put(type, issueCounts.getOrDefault(type, 0) + 1);
            }
        }
        
        public long getCheckTimestamp() { return checkTimestamp; }
        public int getTotalRecordsChecked() { return totalRecordsChecked; }
        public List<IntegrityIssue> getIssues() { return issues; }
        public Map<String, Integer> getIssueCounts() { return issueCounts; }
        public float getIntegrityScore() { return integrityScore; }
        
        public boolean hasIssues() { return !issues.isEmpty(); }
        public boolean hasCriticalIssues() {
            for (IntegrityIssue issue : issues) {
                if (issue.getSeverity() == IssueSeverity.CRITICAL) {
                    return true;
                }
            }
            return false;
        }
        
        public int getCriticalCount() {
            int count = 0;
            for (IntegrityIssue issue : issues) {
                if (issue.getSeverity() == IssueSeverity.CRITICAL) count++;
            }
            return count;
        }
        
        public int getWarningCount() {
            int count = 0;
            for (IntegrityIssue issue : issues) {
                if (issue.getSeverity() == IssueSeverity.WARNING) count++;
            }
            return count;
        }
    }
    
    /**
     * Individual integrity issue.
     */
    public static class IntegrityIssue {
        private final IssueType type;
        private final IssueSeverity severity;
        private final String description;
        private final long startTimestamp;
        private final long endTimestamp;
        private final int affectedRecordCount;
        private final String entityType;
        private final List<Long> affectedIds;
        private final String suggestedAction;
        
        public IntegrityIssue(IssueType type, IssueSeverity severity, String description,
                              long startTimestamp, long endTimestamp, int affectedRecordCount,
                              String entityType, List<Long> affectedIds, String suggestedAction) {
            this.type = type;
            this.severity = severity;
            this.description = description;
            this.startTimestamp = startTimestamp;
            this.endTimestamp = endTimestamp;
            this.affectedRecordCount = affectedRecordCount;
            this.entityType = entityType;
            this.affectedIds = affectedIds != null ? affectedIds : new ArrayList<>();
            this.suggestedAction = suggestedAction;
        }
        
        public IssueType getType() { return type; }
        public IssueSeverity getSeverity() { return severity; }
        public String getDescription() { return description; }
        public long getStartTimestamp() { return startTimestamp; }
        public long getEndTimestamp() { return endTimestamp; }
        public int getAffectedRecordCount() { return affectedRecordCount; }
        public String getEntityType() { return entityType; }
        public List<Long> getAffectedIds() { return affectedIds; }
        public String getSuggestedAction() { return suggestedAction; }
    }
    
    public enum IssueType {
        FROZEN_DATA,           // Same values repeating
        INCOMPLETE_SESSION,    // Session started but not ended
        DATA_GAP,              // Missing expected data
        ANOMALOUS_JUMP,        // Physically impossible value change
        DUPLICATE_TIMESTAMP,   // Multiple records at same timestamp
        FUTURE_TIMESTAMP,      // Timestamp in the future
        INVALID_VALUE,         // Value outside valid range
        ORPHANED_RECORD,       // Record with no related data
        STALE_DATA             // Very old incomplete data
    }
    
    public enum IssueSeverity {
        INFO,       // Informational, no action needed
        WARNING,    // Should investigate
        CRITICAL    // Needs immediate attention
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // FROZEN SNAPSHOT DETECTION
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Detect frozen snapshots - same values repeating indicating stuck sensor.
     */
    public List<IntegrityIssue> detectFrozenSnapshots(@NonNull List<BatterySnapshot> snapshots) {
        List<IntegrityIssue> issues = new ArrayList<>();
        
        if (snapshots.size() < FROZEN_SNAPSHOT_THRESHOLD) {
            return issues;  // Not enough data to detect frozen
        }
        
        // Sort by timestamp
        List<BatterySnapshot> sorted = new ArrayList<>(snapshots);
        sorted.sort((a, b) -> Long.compare(a.timestamp, b.timestamp));
        
        int consecutiveIdentical = 1;
        BatterySnapshot frozenStart = null;
        List<Long> frozenIds = new ArrayList<>();
        
        for (int i = 1; i < sorted.size(); i++) {
            BatterySnapshot prev = sorted.get(i - 1);
            BatterySnapshot curr = sorted.get(i);
            
            if (isIdenticalSnapshot(prev, curr)) {
                if (consecutiveIdentical == 1) {
                    frozenStart = prev;
                    frozenIds.clear();
                    frozenIds.add(prev.id);
                }
                consecutiveIdentical++;
                frozenIds.add(curr.id);
            } else {
                // Check if we found a frozen sequence
                if (consecutiveIdentical >= FROZEN_SNAPSHOT_THRESHOLD && frozenStart != null) {
                    BatterySnapshot frozenEnd = sorted.get(i - 1);
                    long duration = frozenEnd.timestamp - frozenStart.timestamp;
                    
                    IssueSeverity severity = duration > FROZEN_DURATION_THRESHOLD_MS ? 
                            IssueSeverity.CRITICAL : IssueSeverity.WARNING;
                    
                    issues.add(new IntegrityIssue(
                            IssueType.FROZEN_DATA,
                            severity,
                            "Found " + consecutiveIdentical + " identical snapshots over " +
                            formatDuration(duration) + " - possible stuck sensor",
                            frozenStart.timestamp,
                            frozenEnd.timestamp,
                            consecutiveIdentical,
                            "BatterySnapshot",
                            new ArrayList<>(frozenIds),
                            severity == IssueSeverity.CRITICAL ? 
                                    "Investigate battery sensor - may need service restart" :
                                    "Monitor for continued frozen data"
                    ));
                }
                
                consecutiveIdentical = 1;
                frozenStart = null;
                frozenIds.clear();
            }
        }
        
        // Check final sequence
        if (consecutiveIdentical >= FROZEN_SNAPSHOT_THRESHOLD && frozenStart != null) {
            BatterySnapshot frozenEnd = sorted.get(sorted.size() - 1);
            long duration = frozenEnd.timestamp - frozenStart.timestamp;
            
            IssueSeverity severity = duration > FROZEN_DURATION_THRESHOLD_MS ? 
                    IssueSeverity.CRITICAL : IssueSeverity.WARNING;
            
            issues.add(new IntegrityIssue(
                    IssueType.FROZEN_DATA,
                    severity,
                    "Found " + consecutiveIdentical + " identical snapshots over " +
                    formatDuration(duration) + " (ongoing)",
                    frozenStart.timestamp,
                    frozenEnd.timestamp,
                    consecutiveIdentical,
                    "BatterySnapshot",
                    frozenIds,
                    "ACTIVE ISSUE: Battery data is currently frozen"
            ));
        }
        
        return issues;
    }
    
    /**
     * Check if two snapshots are identical (ignoring id and exact timestamp).
     */
    private boolean isIdenticalSnapshot(BatterySnapshot a, BatterySnapshot b) {
        return a.level == b.level &&
               a.voltage == b.voltage &&
               a.temperature == b.temperature &&
               a.status == b.status &&
               a.isCharging == b.isCharging &&
               a.screenOn == b.screenOn;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // INCOMPLETE SESSION DETECTION
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Detect incomplete charging sessions.
     */
    public List<IntegrityIssue> detectIncompleteSessions(@NonNull List<ChargingSession> sessions) {
        List<IntegrityIssue> issues = new ArrayList<>();
        long now = System.currentTimeMillis();
        
        for (ChargingSession session : sessions) {
            if (EntityContracts.ChargingSessionContract.isIncomplete(session)) {
                long age = now - session.startTime;
                
                IssueSeverity severity;
                String suggestedAction;
                
                if (age > STALE_SESSION_THRESHOLD_MS * 2) {
                    severity = IssueSeverity.CRITICAL;
                    suggestedAction = "Session abandoned - consider deletion or auto-close";
                } else if (age > STALE_SESSION_THRESHOLD_MS) {
                    severity = IssueSeverity.WARNING;
                    suggestedAction = "Session stale - may need manual closure";
                } else {
                    severity = IssueSeverity.INFO;
                    suggestedAction = "Recently started session - monitoring";
                }
                
                List<Long> ids = new ArrayList<>();
                ids.add(session.id);
                
                issues.add(new IntegrityIssue(
                        IssueType.INCOMPLETE_SESSION,
                        severity,
                        "Charging session started " + formatDuration(age) + " ago but never completed",
                        session.startTime,
                        now,
                        1,
                        "ChargingSession",
                        ids,
                        suggestedAction
                ));
            }
        }
        
        return issues;
    }
    
    /**
     * Detect stale sessions that should be auto-closed.
     */
    public List<ChargingSession> findStaleSessions(@NonNull List<ChargingSession> sessions) {
        List<ChargingSession> stale = new ArrayList<>();
        
        for (ChargingSession session : sessions) {
            if (EntityContracts.ChargingSessionContract.isStale(session, STALE_SESSION_THRESHOLD_MS)) {
                stale.add(session);
            }
        }
        
        return stale;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // DATA GAP DETECTION
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Detect gaps in snapshot data.
     */
    public List<IntegrityIssue> detectDataGaps(@NonNull List<BatterySnapshot> snapshots) {
        List<IntegrityIssue> issues = new ArrayList<>();
        
        if (snapshots.size() < 2) {
            return issues;
        }
        
        // Sort by timestamp
        List<BatterySnapshot> sorted = new ArrayList<>(snapshots);
        sorted.sort((a, b) -> Long.compare(a.timestamp, b.timestamp));
        
        for (int i = 1; i < sorted.size(); i++) {
            long prevTime = sorted.get(i - 1).timestamp;
            long currTime = sorted.get(i).timestamp;
            long gap = currTime - prevTime;
            
            if (gap >= GAP_CRITICAL_THRESHOLD_MS) {
                issues.add(new IntegrityIssue(
                        IssueType.DATA_GAP,
                        IssueSeverity.CRITICAL,
                        "Large data gap of " + formatDuration(gap) + " detected",
                        prevTime,
                        currTime,
                        0,
                        "BatterySnapshot",
                        null,
                        "Check if service was stopped or device was off"
                ));
            } else if (gap >= GAP_WARNING_THRESHOLD_MS) {
                issues.add(new IntegrityIssue(
                        IssueType.DATA_GAP,
                        IssueSeverity.WARNING,
                        "Data gap of " + formatDuration(gap) + " detected",
                        prevTime,
                        currTime,
                        0,
                        "BatterySnapshot",
                        null,
                        "Minor gap - may indicate doze mode or service restart"
                ));
            }
        }
        
        return issues;
    }
    
    /**
     * Calculate expected vs actual data points.
     */
    public float calculateDataCompleteness(@NonNull List<BatterySnapshot> snapshots,
                                            long startTime, long endTime) {
        if (snapshots.isEmpty() || endTime <= startTime) {
            return 0.0f;
        }
        
        long duration = endTime - startTime;
        int expectedCount = (int) (duration / EXPECTED_SNAPSHOT_INTERVAL_MS);
        
        // Count snapshots in range
        int actualCount = 0;
        for (BatterySnapshot snap : snapshots) {
            if (snap.timestamp >= startTime && snap.timestamp <= endTime) {
                actualCount++;
            }
        }
        
        if (expectedCount == 0) return 100.0f;
        
        return Math.min(100.0f, (float) actualCount / expectedCount * 100);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // ANOMALY DETECTION
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Detect physically impossible value changes.
     */
    public List<IntegrityIssue> detectAnomalousJumps(@NonNull List<BatterySnapshot> snapshots) {
        List<IntegrityIssue> issues = new ArrayList<>();
        
        if (snapshots.size() < 2) {
            return issues;
        }
        
        // Sort by timestamp
        List<BatterySnapshot> sorted = new ArrayList<>(snapshots);
        sorted.sort((a, b) -> Long.compare(a.timestamp, b.timestamp));
        
        for (int i = 1; i < sorted.size(); i++) {
            BatterySnapshot prev = sorted.get(i - 1);
            BatterySnapshot curr = sorted.get(i);
            
            long timeDelta = curr.timestamp - prev.timestamp;
            if (timeDelta <= 0) continue;
            
            double minutesDelta = timeDelta / (double) TimeUnit.MINUTES.toMillis(1);
            if (minutesDelta < 1) minutesDelta = 1;  // Minimum 1 minute for calculation
            
            // Check battery level jump
            int levelJump = Math.abs(curr.level - prev.level);
            double levelJumpPerMinute = levelJump / minutesDelta;
            
            if (levelJumpPerMinute > MAX_LEVEL_JUMP_PER_MINUTE) {
                List<Long> ids = new ArrayList<>();
                ids.add(prev.id);
                ids.add(curr.id);
                
                issues.add(new IntegrityIssue(
                        IssueType.ANOMALOUS_JUMP,
                        IssueSeverity.WARNING,
                        "Battery level jumped " + levelJump + "% in " + 
                        formatDuration(timeDelta) + " (" + 
                        String.format("%.1f", levelJumpPerMinute) + "%/min)",
                        prev.timestamp,
                        curr.timestamp,
                        2,
                        "BatterySnapshot",
                        ids,
                        "Possible sensor glitch or data corruption"
                ));
            }
            
            // Check temperature jump
            int tempJump = Math.abs(curr.temperature - prev.temperature);
            double tempJumpPerMinute = tempJump / minutesDelta;
            
            if (tempJumpPerMinute > MAX_TEMP_JUMP_PER_MINUTE && curr.temperature > 0 && prev.temperature > 0) {
                List<Long> ids = new ArrayList<>();
                ids.add(prev.id);
                ids.add(curr.id);
                
                issues.add(new IntegrityIssue(
                        IssueType.ANOMALOUS_JUMP,
                        IssueSeverity.WARNING,
                        "Temperature jumped " + String.format("%.1f", tempJump / 10.0) + "°C in " + 
                        formatDuration(timeDelta),
                        prev.timestamp,
                        curr.timestamp,
                        2,
                        "BatterySnapshot",
                        ids,
                        "Unusual thermal change - verify sensor accuracy"
                ));
            }
        }
        
        return issues;
    }
    
    /**
     * Detect duplicate timestamps.
     */
    public List<IntegrityIssue> detectDuplicateTimestamps(@NonNull List<BatterySnapshot> snapshots) {
        List<IntegrityIssue> issues = new ArrayList<>();
        
        Map<Long, List<BatterySnapshot>> byTimestamp = new HashMap<>();
        for (BatterySnapshot snap : snapshots) {
            List<BatterySnapshot> list = byTimestamp.get(snap.timestamp);
            if (list == null) {
                list = new ArrayList<>();
                byTimestamp.put(snap.timestamp, list);
            }
            list.add(snap);
        }
        
        for (Map.Entry<Long, List<BatterySnapshot>> entry : byTimestamp.entrySet()) {
            if (entry.getValue().size() > 1) {
                List<Long> ids = new ArrayList<>();
                for (BatterySnapshot snap : entry.getValue()) {
                    ids.add(snap.id);
                }
                
                issues.add(new IntegrityIssue(
                        IssueType.DUPLICATE_TIMESTAMP,
                        IssueSeverity.WARNING,
                        entry.getValue().size() + " snapshots at identical timestamp",
                        entry.getKey(),
                        entry.getKey(),
                        entry.getValue().size(),
                        "BatterySnapshot",
                        ids,
                        "Remove duplicates - keep most complete record"
                ));
            }
        }
        
        return issues;
    }
    
    /**
     * Detect future timestamps.
     */
    public List<IntegrityIssue> detectFutureTimestamps(@NonNull List<BatterySnapshot> snapshots) {
        List<IntegrityIssue> issues = new ArrayList<>();
        long now = System.currentTimeMillis();
        long tolerance = TimeUnit.MINUTES.toMillis(5);
        
        List<Long> futureIds = new ArrayList<>();
        long furthestFuture = now;
        
        for (BatterySnapshot snap : snapshots) {
            if (snap.timestamp > now + tolerance) {
                futureIds.add(snap.id);
                if (snap.timestamp > furthestFuture) {
                    furthestFuture = snap.timestamp;
                }
            }
        }
        
        if (!futureIds.isEmpty()) {
            issues.add(new IntegrityIssue(
                    IssueType.FUTURE_TIMESTAMP,
                    IssueSeverity.CRITICAL,
                    futureIds.size() + " snapshots have timestamps in the future (up to " +
                    formatDuration(furthestFuture - now) + " ahead)",
                    now,
                    furthestFuture,
                    futureIds.size(),
                    "BatterySnapshot",
                    futureIds,
                    "CRITICAL: Check system clock - data unreliable"
            ));
        }
        
        return issues;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // COMPREHENSIVE INTEGRITY CHECK
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Run all integrity checks on snapshot data.
     */
    public IntegrityCheckResult checkSnapshotIntegrity(@NonNull List<BatterySnapshot> snapshots) {
        List<IntegrityIssue> allIssues = new ArrayList<>();
        
        // Run all checks
        allIssues.addAll(detectFrozenSnapshots(snapshots));
        allIssues.addAll(detectDataGaps(snapshots));
        allIssues.addAll(detectAnomalousJumps(snapshots));
        allIssues.addAll(detectDuplicateTimestamps(snapshots));
        allIssues.addAll(detectFutureTimestamps(snapshots));
        
        // Calculate integrity score
        float score = calculateIntegrityScore(snapshots.size(), allIssues);
        
        return new IntegrityCheckResult(
                System.currentTimeMillis(),
                snapshots.size(),
                allIssues,
                score
        );
    }
    
    /**
     * Run all integrity checks on charging sessions.
     */
    public IntegrityCheckResult checkSessionIntegrity(@NonNull List<ChargingSession> sessions) {
        List<IntegrityIssue> allIssues = new ArrayList<>();
        
        allIssues.addAll(detectIncompleteSessions(sessions));
        
        // Calculate integrity score
        float score = calculateIntegrityScore(sessions.size(), allIssues);
        
        return new IntegrityCheckResult(
                System.currentTimeMillis(),
                sessions.size(),
                allIssues,
                score
        );
    }
    
    /**
     * Calculate an integrity score based on issues found.
     */
    private float calculateIntegrityScore(int totalRecords, List<IntegrityIssue> issues) {
        if (totalRecords == 0) return 100.0f;
        
        float score = 100.0f;
        
        for (IntegrityIssue issue : issues) {
            switch (issue.getSeverity()) {
                case CRITICAL:
                    score -= 20;
                    break;
                case WARNING:
                    score -= 5;
                    break;
                case INFO:
                    score -= 1;
                    break;
            }
        }
        
        // Also penalize for number of affected records
        int totalAffected = 0;
        for (IntegrityIssue issue : issues) {
            totalAffected += issue.getAffectedRecordCount();
        }
        
        float affectedRatio = (float) totalAffected / totalRecords;
        score -= affectedRatio * 30;  // Up to 30% penalty for affected records
        
        return Math.max(0, Math.min(100, score));
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // UTILITY METHODS
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Format duration for human-readable output.
     */
    private String formatDuration(long millis) {
        if (millis < TimeUnit.MINUTES.toMillis(1)) {
            return (millis / 1000) + "s";
        } else if (millis < TimeUnit.HOURS.toMillis(1)) {
            return (millis / TimeUnit.MINUTES.toMillis(1)) + "m";
        } else if (millis < TimeUnit.DAYS.toMillis(1)) {
            long hours = millis / TimeUnit.HOURS.toMillis(1);
            long mins = (millis % TimeUnit.HOURS.toMillis(1)) / TimeUnit.MINUTES.toMillis(1);
            return hours + "h " + mins + "m";
        } else {
            long days = millis / TimeUnit.DAYS.toMillis(1);
            long hours = (millis % TimeUnit.DAYS.toMillis(1)) / TimeUnit.HOURS.toMillis(1);
            return days + "d " + hours + "h";
        }
    }
}
