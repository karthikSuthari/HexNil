package com.example.hexnil.ai;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.hexnil.model.BatterySnapshot;
import com.example.hexnil.model.ChargingSession;
import com.example.hexnil.model.DrainEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * AI Readiness Validator - Anti-Hallucination Gateway
 * 
 * This class is the CRITICAL gatekeeper that determines when AI analysis is allowed.
 * AI analysis is BLOCKED unless ALL minimum data requirements are satisfied.
 * 
 * CORE PRINCIPLE: It's better to say "insufficient data" than to hallucinate.
 * 
 * Validates:
 * - Minimum sample counts (configurable per analysis type)
 * - Time coverage requirements (minimum history duration)
 * - Data quality thresholds (completeness, consistency)
 * - Cross-validation requirements (multiple data sources)
 * 
 * OEM Integration: Provides clear rejection reasons for engineering dashboards.
 */
public class AIReadinessValidator {
    
    private static final String TAG = "AIReadinessValidator";
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // MINIMUM DATA REQUIREMENTS - STRICT THRESHOLDS (NON-NEGOTIABLE)
    // ═══════════════════════════════════════════════════════════════════════════════
    
    // Battery Health Analysis Requirements
    public static final int MIN_SNAPSHOTS_HEALTH_ANALYSIS = 100;         // At least 100 battery readings
    public static final long MIN_HISTORY_HOURS_HEALTH = 24;              // At least 24 hours of data
    public static final int MIN_CHARGE_CYCLES_DEGRADATION = 5;           // At least 5 charge cycles
    public static final double MIN_SOC_RANGE_COVERAGE = 0.50;            // Must have seen 50%+ SOC range
    
    // Charging Behavior Analysis Requirements
    public static final int MIN_CHARGING_SESSIONS = 5;                   // At least 5 charging sessions
    public static final long MIN_TOTAL_CHARGING_MINUTES = 60;            // At least 60 min total charge time
    public static final int MIN_CHARGER_TYPES_OBSERVED = 1;              // At least 1 charger type
    
    // Drain Analysis Requirements
    public static final int MIN_DRAIN_EVENTS = 10;                       // At least 10 drain events
    public static final long MIN_DRAIN_OBSERVATION_HOURS = 12;           // At least 12 hours observed
    public static final int MIN_SCREEN_STATE_TRANSITIONS = 5;            // At least 5 screen on/off cycles
    
    // Thermal Analysis Requirements
    public static final int MIN_THERMAL_READINGS = 50;                   // At least 50 temperature readings
    public static final double MIN_TEMP_VARIANCE_CELSIUS = 2.0;          // Must see 2°C+ temperature range
    
    // Calibration Analysis Requirements
    public static final int MIN_FULL_CYCLES_CALIBRATION = 3;             // At least 3 full 0-100% cycles
    public static final int MIN_EDGE_SOC_READINGS = 10;                  // At least 10 readings at <5% or >95%
    
    // Data Quality Requirements (applies to all analyses)
    public static final double MIN_DATA_COMPLETENESS_RATIO = 0.80;       // 80%+ fields populated
    public static final double MIN_TIMESTAMP_CONSISTENCY_RATIO = 0.95;   // 95%+ timestamps in order
    public static final double MAX_DUPLICATE_RATIO = 0.10;               // Max 10% duplicate entries
    
    // Cross-Validation Requirements
    public static final int MIN_CORROBORATING_DATA_SOURCES = 2;          // At least 2 data sources must agree
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // VALIDATION RESULT STRUCTURE
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Validation result indicating whether AI analysis is permitted.
     * Contains detailed rejection reasons for engineering visibility.
     */
    public static class ReadinessResult {
        private final boolean ready;
        private final String analysisType;
        private final List<String> passedChecks;
        private final List<String> failedChecks;
        private final Map<String, Object> metrics;
        private final double readinessScore;           // 0.0 - 1.0
        private final long estimatedTimeToReady;       // Milliseconds until minimum requirements met
        private final String primaryBlocker;            // Main reason blocking AI analysis
        private final String recommendation;            // What to do next
        
        public ReadinessResult(boolean ready, String analysisType, 
                               List<String> passedChecks, List<String> failedChecks,
                               Map<String, Object> metrics, double readinessScore,
                               long estimatedTimeToReady, String primaryBlocker,
                               String recommendation) {
            this.ready = ready;
            this.analysisType = analysisType;
            this.passedChecks = passedChecks;
            this.failedChecks = failedChecks;
            this.metrics = metrics;
            this.readinessScore = readinessScore;
            this.estimatedTimeToReady = estimatedTimeToReady;
            this.primaryBlocker = primaryBlocker;
            this.recommendation = recommendation;
        }
        
        public boolean isReady() { return ready; }
        public String getAnalysisType() { return analysisType; }
        public List<String> getPassedChecks() { return passedChecks; }
        public List<String> getFailedChecks() { return failedChecks; }
        public Map<String, Object> getMetrics() { return metrics; }
        public double getReadinessScore() { return readinessScore; }
        public long getEstimatedTimeToReady() { return estimatedTimeToReady; }
        public String getPrimaryBlocker() { return primaryBlocker; }
        public String getRecommendation() { return recommendation; }
        
        /**
         * Get a human-readable summary for engineering dashboards.
         */
        public String getEngineeringSummary() {
            StringBuilder summary = new StringBuilder();
            summary.append("AI Readiness for ").append(analysisType).append(": ");
            
            if (ready) {
                summary.append("✓ READY (Score: ")
                       .append(String.format("%.1f%%", readinessScore * 100))
                       .append(")");
            } else {
                summary.append("✗ BLOCKED (Score: ")
                       .append(String.format("%.1f%%", readinessScore * 100))
                       .append(") - ").append(primaryBlocker);
            }
            
            return summary.toString();
        }
        
        /**
         * Get OEM-formatted JSON-compatible map for API responses.
         */
        public Map<String, Object> toOEMResponse() {
            Map<String, Object> response = new HashMap<>();
            response.put("ai_ready", ready);
            response.put("analysis_type", analysisType);
            response.put("readiness_score", readinessScore);
            response.put("passed_checks_count", passedChecks.size());
            response.put("failed_checks_count", failedChecks.size());
            response.put("failed_checks", failedChecks);
            response.put("primary_blocker", primaryBlocker);
            response.put("recommendation", recommendation);
            response.put("estimated_time_to_ready_ms", estimatedTimeToReady);
            response.put("metrics", metrics);
            return response;
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // VALIDATION METHODS - BY ANALYSIS TYPE
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Validate readiness for BATTERY HEALTH analysis.
     * Requires: snapshots, charge cycles, SOC coverage.
     */
    public ReadinessResult validateHealthAnalysisReadiness(
            @NonNull List<BatterySnapshot> snapshots,
            int observedChargeCycles,
            double socRangeCoverage) {
        
        List<String> passed = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        Map<String, Object> metrics = new HashMap<>();
        
        // Check 1: Minimum snapshot count
        int snapshotCount = snapshots.size();
        metrics.put("snapshot_count", snapshotCount);
        if (snapshotCount >= MIN_SNAPSHOTS_HEALTH_ANALYSIS) {
            passed.add("Snapshot count: " + snapshotCount + " >= " + MIN_SNAPSHOTS_HEALTH_ANALYSIS);
        } else {
            failed.add("Insufficient snapshots: " + snapshotCount + " < " + MIN_SNAPSHOTS_HEALTH_ANALYSIS);
        }
        
        // Check 2: History duration
        long historyHours = calculateHistoryHours(snapshots);
        metrics.put("history_hours", historyHours);
        if (historyHours >= MIN_HISTORY_HOURS_HEALTH) {
            passed.add("History duration: " + historyHours + "h >= " + MIN_HISTORY_HOURS_HEALTH + "h");
        } else {
            failed.add("Insufficient history: " + historyHours + "h < " + MIN_HISTORY_HOURS_HEALTH + "h");
        }
        
        // Check 3: Charge cycles
        metrics.put("charge_cycles", observedChargeCycles);
        if (observedChargeCycles >= MIN_CHARGE_CYCLES_DEGRADATION) {
            passed.add("Charge cycles: " + observedChargeCycles + " >= " + MIN_CHARGE_CYCLES_DEGRADATION);
        } else {
            failed.add("Insufficient charge cycles: " + observedChargeCycles + " < " + MIN_CHARGE_CYCLES_DEGRADATION);
        }
        
        // Check 4: SOC range coverage
        metrics.put("soc_range_coverage", socRangeCoverage);
        if (socRangeCoverage >= MIN_SOC_RANGE_COVERAGE) {
            passed.add("SOC coverage: " + String.format("%.0f%%", socRangeCoverage * 100) + 
                       " >= " + String.format("%.0f%%", MIN_SOC_RANGE_COVERAGE * 100));
        } else {
            failed.add("Insufficient SOC coverage: " + String.format("%.0f%%", socRangeCoverage * 100) + 
                       " < " + String.format("%.0f%%", MIN_SOC_RANGE_COVERAGE * 100));
        }
        
        // Check 5: Data quality
        DataQualityResult quality = assessSnapshotQuality(snapshots);
        metrics.put("data_completeness", quality.completeness);
        metrics.put("timestamp_consistency", quality.timestampConsistency);
        metrics.put("duplicate_ratio", quality.duplicateRatio);
        
        if (quality.completeness >= MIN_DATA_COMPLETENESS_RATIO) {
            passed.add("Data completeness: " + String.format("%.0f%%", quality.completeness * 100));
        } else {
            failed.add("Low data completeness: " + String.format("%.0f%%", quality.completeness * 100));
        }
        
        if (quality.timestampConsistency >= MIN_TIMESTAMP_CONSISTENCY_RATIO) {
            passed.add("Timestamp consistency: " + String.format("%.0f%%", quality.timestampConsistency * 100));
        } else {
            failed.add("Timestamp inconsistency: " + String.format("%.0f%%", quality.timestampConsistency * 100));
        }
        
        return buildResult("BATTERY_HEALTH", passed, failed, metrics);
    }
    
    /**
     * Validate readiness for CHARGING BEHAVIOR analysis.
     * Requires: charging sessions, total charge time, charger diversity.
     */
    public ReadinessResult validateChargingAnalysisReadiness(
            @NonNull List<ChargingSession> sessions,
            @NonNull List<BatterySnapshot> snapshots) {
        
        List<String> passed = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        Map<String, Object> metrics = new HashMap<>();
        
        // Check 1: Minimum session count
        int sessionCount = sessions.size();
        metrics.put("session_count", sessionCount);
        if (sessionCount >= MIN_CHARGING_SESSIONS) {
            passed.add("Charging sessions: " + sessionCount + " >= " + MIN_CHARGING_SESSIONS);
        } else {
            failed.add("Insufficient sessions: " + sessionCount + " < " + MIN_CHARGING_SESSIONS);
        }
        
        // Check 2: Total charging time
        long totalChargingMinutes = calculateTotalChargingMinutes(sessions);
        metrics.put("total_charging_minutes", totalChargingMinutes);
        if (totalChargingMinutes >= MIN_TOTAL_CHARGING_MINUTES) {
            passed.add("Total charging time: " + totalChargingMinutes + "min >= " + MIN_TOTAL_CHARGING_MINUTES + "min");
        } else {
            failed.add("Insufficient charging time: " + totalChargingMinutes + "min < " + MIN_TOTAL_CHARGING_MINUTES + "min");
        }
        
        // Check 3: Charger types observed
        int chargerTypes = countDistinctChargerTypes(sessions);
        metrics.put("charger_types", chargerTypes);
        if (chargerTypes >= MIN_CHARGER_TYPES_OBSERVED) {
            passed.add("Charger types: " + chargerTypes + " >= " + MIN_CHARGER_TYPES_OBSERVED);
        } else {
            failed.add("No charger type data available");
        }
        
        // Check 4: Supporting snapshot data
        List<BatterySnapshot> chargingSnapshots = filterChargingSnapshots(snapshots);
        int chargingSnapshotCount = chargingSnapshots.size();
        metrics.put("charging_snapshots", chargingSnapshotCount);
        if (chargingSnapshotCount >= MIN_SNAPSHOTS_HEALTH_ANALYSIS / 2) {
            passed.add("Charging snapshots: " + chargingSnapshotCount);
        } else {
            failed.add("Insufficient charging snapshots: " + chargingSnapshotCount);
        }
        
        // Check 5: Session data quality
        double sessionQuality = assessSessionQuality(sessions);
        metrics.put("session_quality", sessionQuality);
        if (sessionQuality >= MIN_DATA_COMPLETENESS_RATIO) {
            passed.add("Session data quality: " + String.format("%.0f%%", sessionQuality * 100));
        } else {
            failed.add("Low session data quality: " + String.format("%.0f%%", sessionQuality * 100));
        }
        
        return buildResult("CHARGING_BEHAVIOR", passed, failed, metrics);
    }
    
    /**
     * Validate readiness for DRAIN ANALYSIS.
     * Requires: drain events, observation time, screen state data.
     */
    public ReadinessResult validateDrainAnalysisReadiness(
            @NonNull List<DrainEvent> drainEvents,
            @NonNull List<BatterySnapshot> snapshots) {
        
        List<String> passed = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        Map<String, Object> metrics = new HashMap<>();
        
        // Check 1: Minimum drain events
        int eventCount = drainEvents.size();
        metrics.put("drain_event_count", eventCount);
        if (eventCount >= MIN_DRAIN_EVENTS) {
            passed.add("Drain events: " + eventCount + " >= " + MIN_DRAIN_EVENTS);
        } else {
            failed.add("Insufficient drain events: " + eventCount + " < " + MIN_DRAIN_EVENTS);
        }
        
        // Check 2: Observation duration
        long observationHours = calculateDrainObservationHours(drainEvents);
        metrics.put("observation_hours", observationHours);
        if (observationHours >= MIN_DRAIN_OBSERVATION_HOURS) {
            passed.add("Observation time: " + observationHours + "h >= " + MIN_DRAIN_OBSERVATION_HOURS + "h");
        } else {
            failed.add("Insufficient observation: " + observationHours + "h < " + MIN_DRAIN_OBSERVATION_HOURS + "h");
        }
        
        // Check 3: Screen state transitions
        int screenTransitions = countScreenStateTransitions(snapshots);
        metrics.put("screen_transitions", screenTransitions);
        if (screenTransitions >= MIN_SCREEN_STATE_TRANSITIONS) {
            passed.add("Screen transitions: " + screenTransitions + " >= " + MIN_SCREEN_STATE_TRANSITIONS);
        } else {
            failed.add("Insufficient screen data: " + screenTransitions + " < " + MIN_SCREEN_STATE_TRANSITIONS);
        }
        
        // Check 4: Drain rate variance (need to see different drain rates)
        double drainRateVariance = calculateDrainRateVariance(drainEvents);
        metrics.put("drain_rate_variance", drainRateVariance);
        if (drainRateVariance > 0.5) {
            passed.add("Drain rate variance: " + String.format("%.2f", drainRateVariance));
        } else {
            failed.add("Insufficient drain variance: " + String.format("%.2f", drainRateVariance));
        }
        
        return buildResult("DRAIN_ANALYSIS", passed, failed, metrics);
    }
    
    /**
     * Validate readiness for THERMAL ANALYSIS.
     * Requires: temperature readings with variance.
     */
    public ReadinessResult validateThermalAnalysisReadiness(
            @NonNull List<BatterySnapshot> snapshots) {
        
        List<String> passed = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        Map<String, Object> metrics = new HashMap<>();
        
        // Check 1: Minimum thermal readings
        List<BatterySnapshot> thermalSnapshots = filterSnapshotsWithTemperature(snapshots);
        int thermalCount = thermalSnapshots.size();
        metrics.put("thermal_reading_count", thermalCount);
        if (thermalCount >= MIN_THERMAL_READINGS) {
            passed.add("Thermal readings: " + thermalCount + " >= " + MIN_THERMAL_READINGS);
        } else {
            failed.add("Insufficient thermal data: " + thermalCount + " < " + MIN_THERMAL_READINGS);
        }
        
        // Check 2: Temperature variance
        double tempVariance = calculateTemperatureVariance(thermalSnapshots);
        metrics.put("temperature_variance", tempVariance);
        if (tempVariance >= MIN_TEMP_VARIANCE_CELSIUS) {
            passed.add("Temperature variance: " + String.format("%.1f°C", tempVariance) + 
                       " >= " + String.format("%.1f°C", MIN_TEMP_VARIANCE_CELSIUS));
        } else {
            failed.add("Insufficient temp variance: " + String.format("%.1f°C", tempVariance) + 
                       " < " + String.format("%.1f°C", MIN_TEMP_VARIANCE_CELSIUS));
        }
        
        // Check 3: Temperature range coverage
        double[] tempRange = getTemperatureRange(thermalSnapshots);
        metrics.put("temp_min", tempRange[0]);
        metrics.put("temp_max", tempRange[1]);
        
        boolean hasNormalRange = tempRange[0] < 35.0 && tempRange[1] > 25.0;
        if (hasNormalRange) {
            passed.add("Normal temp range observed: " + String.format("%.1f°C - %.1f°C", tempRange[0], tempRange[1]));
        } else {
            failed.add("Limited temperature range: " + String.format("%.1f°C - %.1f°C", tempRange[0], tempRange[1]));
        }
        
        return buildResult("THERMAL_ANALYSIS", passed, failed, metrics);
    }
    
    /**
     * Validate readiness for CALIBRATION ANALYSIS.
     * Requires: full charge cycles, edge SOC readings.
     */
    public ReadinessResult validateCalibrationAnalysisReadiness(
            @NonNull List<BatterySnapshot> snapshots,
            int fullCycleCount) {
        
        List<String> passed = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        Map<String, Object> metrics = new HashMap<>();
        
        // Check 1: Full charge cycles
        metrics.put("full_cycles", fullCycleCount);
        if (fullCycleCount >= MIN_FULL_CYCLES_CALIBRATION) {
            passed.add("Full cycles: " + fullCycleCount + " >= " + MIN_FULL_CYCLES_CALIBRATION);
        } else {
            failed.add("Insufficient full cycles: " + fullCycleCount + " < " + MIN_FULL_CYCLES_CALIBRATION);
        }
        
        // Check 2: Edge SOC readings (< 5% or > 95%)
        int edgeReadings = countEdgeSOCReadings(snapshots);
        metrics.put("edge_soc_readings", edgeReadings);
        if (edgeReadings >= MIN_EDGE_SOC_READINGS) {
            passed.add("Edge SOC readings: " + edgeReadings + " >= " + MIN_EDGE_SOC_READINGS);
        } else {
            failed.add("Insufficient edge readings: " + edgeReadings + " < " + MIN_EDGE_SOC_READINGS);
        }
        
        // Check 3: SOC reporting granularity
        int socGranularity = assessSOCGranularity(snapshots);
        metrics.put("soc_granularity", socGranularity);
        if (socGranularity >= 20) {  // At least 20 distinct SOC values observed
            passed.add("SOC granularity: " + socGranularity + " distinct values");
        } else {
            failed.add("Limited SOC granularity: " + socGranularity + " distinct values");
        }
        
        // Check 4: Charging curve data
        boolean hasChargingCurve = hasCompleteChargingCurve(snapshots);
        metrics.put("has_charging_curve", hasChargingCurve);
        if (hasChargingCurve) {
            passed.add("Complete charging curve data available");
        } else {
            failed.add("Incomplete charging curve data");
        }
        
        return buildResult("CALIBRATION_ANALYSIS", passed, failed, metrics);
    }
    
    /**
     * Validate readiness for COMPREHENSIVE AI analysis (all modules).
     * This is the strictest validation - requires all sub-validations to pass.
     */
    public ReadinessResult validateComprehensiveAnalysisReadiness(
            @NonNull List<BatterySnapshot> snapshots,
            @NonNull List<ChargingSession> sessions,
            @NonNull List<DrainEvent> drainEvents,
            int chargeCycles,
            int fullCycles,
            double socCoverage) {
        
        List<String> passed = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        Map<String, Object> metrics = new HashMap<>();
        
        // Run all sub-validations
        ReadinessResult health = validateHealthAnalysisReadiness(snapshots, chargeCycles, socCoverage);
        ReadinessResult charging = validateChargingAnalysisReadiness(sessions, snapshots);
        ReadinessResult drain = validateDrainAnalysisReadiness(drainEvents, snapshots);
        ReadinessResult thermal = validateThermalAnalysisReadiness(snapshots);
        ReadinessResult calibration = validateCalibrationAnalysisReadiness(snapshots, fullCycles);
        
        // Aggregate results
        metrics.put("health_ready", health.isReady());
        metrics.put("health_score", health.getReadinessScore());
        metrics.put("charging_ready", charging.isReady());
        metrics.put("charging_score", charging.getReadinessScore());
        metrics.put("drain_ready", drain.isReady());
        metrics.put("drain_score", drain.getReadinessScore());
        metrics.put("thermal_ready", thermal.isReady());
        metrics.put("thermal_score", thermal.getReadinessScore());
        metrics.put("calibration_ready", calibration.isReady());
        metrics.put("calibration_score", calibration.getReadinessScore());
        
        // Add to passed/failed based on sub-results
        if (health.isReady()) passed.add("Health analysis: READY"); else failed.add("Health analysis: BLOCKED");
        if (charging.isReady()) passed.add("Charging analysis: READY"); else failed.add("Charging analysis: BLOCKED");
        if (drain.isReady()) passed.add("Drain analysis: READY"); else failed.add("Drain analysis: BLOCKED");
        if (thermal.isReady()) passed.add("Thermal analysis: READY"); else failed.add("Thermal analysis: BLOCKED");
        if (calibration.isReady()) passed.add("Calibration analysis: READY"); else failed.add("Calibration analysis: BLOCKED");
        
        // Cross-validation: Data source agreement
        int agreeing = countAgreeingDataSources(snapshots, sessions, drainEvents);
        metrics.put("agreeing_data_sources", agreeing);
        if (agreeing >= MIN_CORROBORATING_DATA_SOURCES) {
            passed.add("Cross-validation: " + agreeing + " data sources agree");
        } else {
            failed.add("Cross-validation failed: only " + agreeing + " sources agree");
        }
        
        return buildResult("COMPREHENSIVE_AI", passed, failed, metrics);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════════════════
    
    private static class DataQualityResult {
        double completeness;
        double timestampConsistency;
        double duplicateRatio;
        
        DataQualityResult(double completeness, double timestampConsistency, double duplicateRatio) {
            this.completeness = completeness;
            this.timestampConsistency = timestampConsistency;
            this.duplicateRatio = duplicateRatio;
        }
    }
    
    private long calculateHistoryHours(List<BatterySnapshot> snapshots) {
        if (snapshots.isEmpty()) return 0;
        
        long minTime = Long.MAX_VALUE;
        long maxTime = Long.MIN_VALUE;
        
        for (BatterySnapshot snapshot : snapshots) {
            if (snapshot.timestamp < minTime) minTime = snapshot.timestamp;
            if (snapshot.timestamp > maxTime) maxTime = snapshot.timestamp;
        }
        
        return TimeUnit.MILLISECONDS.toHours(maxTime - minTime);
    }
    
    private DataQualityResult assessSnapshotQuality(List<BatterySnapshot> snapshots) {
        if (snapshots.isEmpty()) {
            return new DataQualityResult(0.0, 0.0, 0.0);
        }
        
        int totalFields = 0;
        int populatedFields = 0;
        int consistentTimestamps = 0;
        Map<Long, Integer> timestampCounts = new HashMap<>();
        
        long prevTimestamp = 0;
        for (BatterySnapshot snapshot : snapshots) {
            // Count essential fields
            totalFields += 5;  // level, voltage, temperature, timestamp, status
            if (snapshot.level >= 0) populatedFields++;
            if (snapshot.voltage > 0) populatedFields++;
            if (snapshot.temperature > 0) populatedFields++;
            if (snapshot.timestamp > 0) populatedFields++;
            if (snapshot.status >= 0) populatedFields++;  // status is an int
            
            // Check timestamp consistency
            if (snapshot.timestamp > prevTimestamp) {
                consistentTimestamps++;
            }
            prevTimestamp = snapshot.timestamp;
            
            // Track duplicates
            timestampCounts.merge(snapshot.timestamp, 1, Integer::sum);
        }
        
        double completeness = (double) populatedFields / totalFields;
        double consistency = snapshots.size() > 1 ? 
                (double) consistentTimestamps / (snapshots.size() - 1) : 1.0;
        
        long duplicates = timestampCounts.values().stream().filter(c -> c > 1).count();
        double duplicateRatio = (double) duplicates / snapshots.size();
        
        return new DataQualityResult(completeness, consistency, duplicateRatio);
    }
    
    private long calculateTotalChargingMinutes(List<ChargingSession> sessions) {
        long totalMs = 0;
        for (ChargingSession session : sessions) {
            if (session.endTime > session.startTime) {
                totalMs += (session.endTime - session.startTime);
            }
        }
        return TimeUnit.MILLISECONDS.toMinutes(totalMs);
    }
    
    private int countDistinctChargerTypes(List<ChargingSession> sessions) {
        Map<String, Boolean> types = new HashMap<>();
        for (ChargingSession session : sessions) {
            if (session.chargingType != null && !session.chargingType.isEmpty()) {
                types.put(session.chargingType, true);
            }
        }
        return Math.max(types.size(), sessions.isEmpty() ? 0 : 1);
    }
    
    private List<BatterySnapshot> filterChargingSnapshots(List<BatterySnapshot> snapshots) {
        List<BatterySnapshot> charging = new ArrayList<>();
        for (BatterySnapshot snapshot : snapshots) {
            if (snapshot.isCharging) {
                charging.add(snapshot);
            }
        }
        return charging;
    }
    
    private double assessSessionQuality(List<ChargingSession> sessions) {
        if (sessions.isEmpty()) return 0.0;
        
        int complete = 0;
        for (ChargingSession session : sessions) {
            boolean hasStart = session.startTime > 0;
            boolean hasEnd = session.endTime > 0;
            boolean hasLevels = session.startLevel >= 0 && session.endLevel >= 0;
            
            if (hasStart && hasEnd && hasLevels) {
                complete++;
            }
        }
        
        return (double) complete / sessions.size();
    }
    
    private long calculateDrainObservationHours(List<DrainEvent> events) {
        if (events.isEmpty()) return 0;
        
        long minTime = Long.MAX_VALUE;
        long maxTime = Long.MIN_VALUE;
        
        for (DrainEvent event : events) {
            if (event.startTime < minTime) minTime = event.startTime;
            if (event.endTime > maxTime) maxTime = event.endTime;
        }
        
        return TimeUnit.MILLISECONDS.toHours(maxTime - minTime);
    }
    
    private int countScreenStateTransitions(List<BatterySnapshot> snapshots) {
        int transitions = 0;
        boolean lastScreenOn = false;
        boolean first = true;
        
        for (BatterySnapshot snapshot : snapshots) {
            boolean on = snapshot.isScreenOnOrFalse();
            if (first) {
                lastScreenOn = on;
                first = false;
            } else if (on != lastScreenOn) {
                transitions++;
                lastScreenOn = on;
            }
        }
        
        return transitions;
    }
    
    private double calculateDrainRateVariance(List<DrainEvent> events) {
        if (events.size() < 2) return 0.0;
        
        List<Double> rates = new ArrayList<>();
        for (DrainEvent event : events) {
            if (event.drainRatePerHour > 0) {
                rates.add((double) event.drainRatePerHour);
            }
        }
        
        if (rates.size() < 2) return 0.0;
        
        double mean = 0;
        for (double rate : rates) mean += rate;
        mean /= rates.size();
        
        double variance = 0;
        for (double rate : rates) {
            variance += Math.pow(rate - mean, 2);
        }
        variance /= rates.size();
        
        return Math.sqrt(variance);  // Return standard deviation
    }
    
    private List<BatterySnapshot> filterSnapshotsWithTemperature(List<BatterySnapshot> snapshots) {
        List<BatterySnapshot> withTemp = new ArrayList<>();
        for (BatterySnapshot snapshot : snapshots) {
            if (snapshot.temperature > 0) {
                withTemp.add(snapshot);
            }
        }
        return withTemp;
    }
    
    private double calculateTemperatureVariance(List<BatterySnapshot> snapshots) {
        double[] range = getTemperatureRange(snapshots);
        return range[1] - range[0];
    }
    
    private double[] getTemperatureRange(List<BatterySnapshot> snapshots) {
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        
        for (BatterySnapshot snapshot : snapshots) {
            double temp = snapshot.temperature / 10.0;  // Convert from tenths
            if (temp > 0) {
                if (temp < min) min = temp;
                if (temp > max) max = temp;
            }
        }
        
        if (min == Double.MAX_VALUE) min = 0;
        if (max == Double.MIN_VALUE) max = 0;
        
        return new double[]{min, max};
    }
    
    private int countEdgeSOCReadings(List<BatterySnapshot> snapshots) {
        int count = 0;
        for (BatterySnapshot snapshot : snapshots) {
            if (snapshot.level < 5 || snapshot.level > 95) {
                count++;
            }
        }
        return count;
    }
    
    private int assessSOCGranularity(List<BatterySnapshot> snapshots) {
        Map<Integer, Boolean> distinctLevels = new HashMap<>();
        for (BatterySnapshot snapshot : snapshots) {
            distinctLevels.put(snapshot.level, true);
        }
        return distinctLevels.size();
    }
    
    private boolean hasCompleteChargingCurve(List<BatterySnapshot> snapshots) {
        // Check if we have readings across the full charging range
        boolean hasLow = false;   // < 20%
        boolean hasMid = false;   // 40-60%
        boolean hasHigh = false;  // > 80%
        
        for (BatterySnapshot snapshot : snapshots) {
            if (snapshot.isCharging) {
                if (snapshot.level < 20) hasLow = true;
                if (snapshot.level >= 40 && snapshot.level <= 60) hasMid = true;
                if (snapshot.level > 80) hasHigh = true;
            }
        }
        
        return hasLow && hasMid && hasHigh;
    }
    
    private int countAgreeingDataSources(List<BatterySnapshot> snapshots, 
                                          List<ChargingSession> sessions,
                                          List<DrainEvent> events) {
        int agreeing = 0;
        
        // Check if snapshots have meaningful data
        if (snapshots.size() >= 10) agreeing++;
        
        // Check if sessions correlate with snapshot charging events
        if (sessions.size() > 0 && hasCorrelatingChargingData(snapshots, sessions)) agreeing++;
        
        // Check if drain events correlate with snapshot drain patterns
        if (events.size() > 0 && hasCorrelatingDrainData(snapshots, events)) agreeing++;
        
        return agreeing;
    }
    
    private boolean hasCorrelatingChargingData(List<BatterySnapshot> snapshots, 
                                                List<ChargingSession> sessions) {
        // Check if at least one session's timeframe has matching snapshots
        for (ChargingSession session : sessions) {
            for (BatterySnapshot snapshot : snapshots) {
                if (snapshot.timestamp >= session.startTime && 
                    snapshot.timestamp <= session.endTime &&
                    snapshot.isCharging) {
                    return true;
                }
            }
        }
        return false;
    }
    
    private boolean hasCorrelatingDrainData(List<BatterySnapshot> snapshots,
                                            List<DrainEvent> events) {
        // Check if at least one drain event's timeframe has matching snapshots
        for (DrainEvent event : events) {
            for (BatterySnapshot snapshot : snapshots) {
                if (snapshot.timestamp >= event.startTime && 
                    snapshot.timestamp <= event.endTime &&
                    !snapshot.isCharging) {
                    return true;
                }
            }
        }
        return false;
    }
    
    private ReadinessResult buildResult(String analysisType, 
                                         List<String> passed, 
                                         List<String> failed,
                                         Map<String, Object> metrics) {
        
        boolean ready = failed.isEmpty();
        double score = passed.isEmpty() ? 0.0 : 
                (double) passed.size() / (passed.size() + failed.size());
        
        String primaryBlocker = failed.isEmpty() ? "None" : failed.get(0);
        
        // Estimate time to ready (rough heuristic)
        long estimatedTime = 0;
        if (!ready) {
            // Base estimate: 1 hour per failed check
            estimatedTime = TimeUnit.HOURS.toMillis(failed.size());
        }
        
        String recommendation;
        if (ready) {
            recommendation = "AI analysis can proceed. All minimum requirements satisfied.";
        } else if (failed.size() == 1) {
            recommendation = "Continue data collection. One requirement remaining: " + primaryBlocker;
        } else {
            recommendation = "Continue data collection. " + failed.size() + 
                    " requirements remaining. Primary: " + primaryBlocker;
        }
        
        Log.d(TAG, "Readiness check for " + analysisType + ": " + 
              (ready ? "READY" : "BLOCKED") + " (Score: " + String.format("%.1f%%", score * 100) + ")");
        
        return new ReadinessResult(ready, analysisType, passed, failed, 
                                   metrics, score, estimatedTime, primaryBlocker, recommendation);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // STATIC UTILITY METHODS
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Quick check if AI analysis is allowed without full validation.
     * Use for pre-screening before expensive operations.
     */
    public static boolean quickCheckMinimumRequirements(int snapshotCount, 
                                                         int sessionCount,
                                                         int drainEventCount,
                                                         long historyHours) {
        return snapshotCount >= MIN_SNAPSHOTS_HEALTH_ANALYSIS / 2 &&
               sessionCount >= MIN_CHARGING_SESSIONS / 2 &&
               drainEventCount >= MIN_DRAIN_EVENTS / 2 &&
               historyHours >= MIN_HISTORY_HOURS_HEALTH / 2;
    }
    
    /**
     * Get the minimum requirements as a human-readable summary.
     */
    public static String getMinimumRequirementsSummary() {
        return "Minimum Requirements for AI Analysis:\n" +
               "• Battery Snapshots: " + MIN_SNAPSHOTS_HEALTH_ANALYSIS + "\n" +
               "• History Duration: " + MIN_HISTORY_HOURS_HEALTH + " hours\n" +
               "• Charge Cycles: " + MIN_CHARGE_CYCLES_DEGRADATION + "\n" +
               "• Charging Sessions: " + MIN_CHARGING_SESSIONS + "\n" +
               "• Drain Events: " + MIN_DRAIN_EVENTS + "\n" +
               "• Thermal Readings: " + MIN_THERMAL_READINGS + "\n" +
               "• Data Completeness: " + String.format("%.0f%%", MIN_DATA_COMPLETENESS_RATIO * 100);
    }
}
