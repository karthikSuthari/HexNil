package com.example.hexnil.validation;

import android.os.BatteryManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.hexnil.model.AIAnalysisResult;
import com.example.hexnil.model.BatteryHealth;
import com.example.hexnil.model.BatterySnapshot;
import com.example.hexnil.model.ChargingSession;
import com.example.hexnil.model.DrainEvent;
import com.example.hexnil.model.ThermalEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Entity Contracts - Room Entity Validation Rules
 * 
 * Defines the EXACT requirements for every entity field:
 * - Required vs optional fields
 * - Valid value ranges
 * - Relationship rules between fields
 * - Business logic constraints
 * 
 * CORE PRINCIPLES:
 * 1. No garbage in - validate before insert
 * 2. Fail fast - reject invalid data immediately
 * 3. Clear error messages - what's wrong and why
 * 4. Recoverable - provide default/fix when possible
 * 
 * OEM Integration: Contract violations are logged for quality metrics.
 */
public class EntityContracts {
    
    private static final String TAG = "EntityContracts";
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // VALIDATION RESULT STRUCTURE
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Result of contract validation for any entity.
     */
    public static class ContractValidationResult {
        private final boolean valid;
        private final String entityType;
        private final List<String> violations;
        private final List<String> warnings;
        private final Map<String, Object> correctedValues;
        private final boolean canAutocorrect;
        
        public ContractValidationResult(boolean valid, String entityType,
                                         List<String> violations, List<String> warnings,
                                         Map<String, Object> correctedValues, boolean canAutocorrect) {
            this.valid = valid;
            this.entityType = entityType;
            this.violations = violations != null ? violations : new ArrayList<>();
            this.warnings = warnings != null ? warnings : new ArrayList<>();
            this.correctedValues = correctedValues != null ? correctedValues : new HashMap<>();
            this.canAutocorrect = canAutocorrect;
        }
        
        public boolean isValid() { return valid; }
        public String getEntityType() { return entityType; }
        public List<String> getViolations() { return violations; }
        public List<String> getWarnings() { return warnings; }
        public Map<String, Object> getCorrectedValues() { return correctedValues; }
        public boolean canAutocorrect() { return canAutocorrect; }
        
        public boolean hasViolations() { return !violations.isEmpty(); }
        public boolean hasWarnings() { return !warnings.isEmpty(); }
        
        public String getSummary() {
            if (valid) {
                return entityType + ": VALID" + (warnings.isEmpty() ? "" : " with " + warnings.size() + " warnings");
            }
            return entityType + ": INVALID - " + violations.size() + " violations";
        }
        
        public static ContractValidationResult valid(String entityType) {
            return new ContractValidationResult(true, entityType, null, null, null, false);
        }
        
        public static ContractValidationResult validWithWarnings(String entityType, List<String> warnings) {
            return new ContractValidationResult(true, entityType, null, warnings, null, false);
        }
        
        public static ContractValidationResult invalid(String entityType, List<String> violations) {
            return new ContractValidationResult(false, entityType, violations, null, null, false);
        }
        
        public static ContractValidationResult autocorrectable(String entityType, List<String> warnings,
                                                                Map<String, Object> corrections) {
            return new ContractValidationResult(true, entityType, null, warnings, corrections, true);
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // BATTERY SNAPSHOT CONTRACT
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * BatterySnapshot contract - defines all field requirements.
     */
    public static class BatterySnapshotContract {
        
        // Required fields
        public static final boolean TIMESTAMP_REQUIRED = true;
        public static final boolean LEVEL_REQUIRED = true;
        
        // Valid ranges
        public static final int LEVEL_MIN = 0;
        public static final int LEVEL_MAX = 100;
        public static final int VOLTAGE_MIN_MV = 2500;      // Absolute minimum for Li-ion
        public static final int VOLTAGE_MAX_MV = 5000;      // Max with tolerance
        public static final int TEMPERATURE_MIN_TENTHS = 0;  // 0°C in tenths
        public static final int TEMPERATURE_MAX_TENTHS = 800; // 80°C in tenths
        
        // Timestamp constraints
        public static final long MIN_VALID_TIMESTAMP = 1577836800000L; // Jan 1, 2020
        public static final long MAX_FUTURE_TOLERANCE_MS = TimeUnit.MINUTES.toMillis(5);
        
        // Uniqueness constraints
        public static final long MIN_SNAPSHOT_INTERVAL_MS = 1000; // Min 1 second between snapshots
        
        // Status values (BatteryManager constants)
        public static final int[] VALID_STATUS_VALUES = {
                BatteryManager.BATTERY_STATUS_UNKNOWN,
                BatteryManager.BATTERY_STATUS_CHARGING,
                BatteryManager.BATTERY_STATUS_DISCHARGING,
                BatteryManager.BATTERY_STATUS_NOT_CHARGING,
                BatteryManager.BATTERY_STATUS_FULL
        };
        
        /**
         * Validate a BatterySnapshot against its contract.
         */
        public static ContractValidationResult validate(@NonNull BatterySnapshot snapshot) {
            List<String> violations = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            Map<String, Object> corrections = new HashMap<>();
            
            // === REQUIRED FIELD CHECKS ===
            
            // Timestamp - required, must be valid
            if (snapshot.timestamp <= 0) {
                violations.add("timestamp is required and must be positive");
            } else if (snapshot.timestamp < MIN_VALID_TIMESTAMP) {
                violations.add("timestamp is before minimum valid date (Jan 1, 2020)");
            } else if (snapshot.timestamp > System.currentTimeMillis() + MAX_FUTURE_TOLERANCE_MS) {
                violations.add("timestamp is in the future");
            }
            
            // Level - required, 0-100
            if (snapshot.level < LEVEL_MIN || snapshot.level > LEVEL_MAX) {
                violations.add("level must be between " + LEVEL_MIN + " and " + LEVEL_MAX + 
                              ", got: " + snapshot.level);
            }
            
            // === RANGE CHECKS ===
            
            // Voltage - optional but must be in range if present
            if (snapshot.voltage > 0) {
                if (snapshot.voltage < VOLTAGE_MIN_MV) {
                    warnings.add("voltage " + snapshot.voltage + "mV is below expected minimum");
                } else if (snapshot.voltage > VOLTAGE_MAX_MV) {
                    warnings.add("voltage " + snapshot.voltage + "mV is above expected maximum");
                }
            }
            
            // Temperature - optional but must be in range if present
            if (snapshot.temperature > 0) {
                if (snapshot.temperature < TEMPERATURE_MIN_TENTHS) {
                    warnings.add("temperature " + (snapshot.temperature / 10.0) + "°C is below freezing");
                } else if (snapshot.temperature > TEMPERATURE_MAX_TENTHS) {
                    warnings.add("temperature " + (snapshot.temperature / 10.0) + "°C exceeds safe limit");
                }
            }
            
            // === CONSISTENCY CHECKS ===
            
            // Status should be valid BatteryManager constant
            boolean validStatus = false;
            for (int valid : VALID_STATUS_VALUES) {
                if (snapshot.status == valid) {
                    validStatus = true;
                    break;
                }
            }
            if (!validStatus && snapshot.status != 0) {
                warnings.add("status " + snapshot.status + " is not a recognized BatteryManager status");
            }
            
            // isCharging should match status
            boolean statusCharging = snapshot.status == BatteryManager.BATTERY_STATUS_CHARGING ||
                                      snapshot.status == BatteryManager.BATTERY_STATUS_FULL;
            if (snapshot.isCharging != statusCharging && snapshot.status != 0) {
                warnings.add("isCharging=" + snapshot.isCharging + " doesn't match status=" + snapshot.status);
            }
            
            // === BUILD RESULT ===
            
            if (!violations.isEmpty()) {
                return ContractValidationResult.invalid("BatterySnapshot", violations);
            }
            
            if (!warnings.isEmpty()) {
                return ContractValidationResult.validWithWarnings("BatterySnapshot", warnings);
            }
            
            return ContractValidationResult.valid("BatterySnapshot");
        }
        
        /**
         * Check if this snapshot is potentially frozen (duplicate of previous).
         */
        public static boolean isPotentiallyFrozen(@NonNull BatterySnapshot current,
                                                   @Nullable BatterySnapshot previous) {
            if (previous == null) return false;
            
            // If all key values are identical and timestamp is different, might be frozen
            return current.level == previous.level &&
                   current.voltage == previous.voltage &&
                   current.temperature == previous.temperature &&
                   current.status == previous.status &&
                   current.isCharging == previous.isCharging &&
                   current.timestamp != previous.timestamp;
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // CHARGING SESSION CONTRACT
    // ═══════════════════════════════════════════════════════════════════════════════
    
    public static class ChargingSessionContract {
        
        // Required fields
        public static final boolean START_TIME_REQUIRED = true;
        public static final boolean START_LEVEL_REQUIRED = true;
        
        // Valid ranges
        public static final int LEVEL_MIN = 0;
        public static final int LEVEL_MAX = 100;
        
        // Duration constraints
        public static final long MIN_SESSION_DURATION_MS = TimeUnit.SECONDS.toMillis(30);  // Min 30 sec
        public static final long MAX_SESSION_DURATION_MS = TimeUnit.HOURS.toMillis(24);    // Max 24 hours
        
        // Charging rate constraints
        public static final float MIN_CHARGING_RATE = 0.1f;    // At least 0.1%/hour
        public static final float MAX_CHARGING_RATE = 200.0f;  // Max 200%/hour (super fast)
        
        /**
         * Validate a ChargingSession against its contract.
         */
        public static ContractValidationResult validate(@NonNull ChargingSession session) {
            List<String> violations = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            
            // === REQUIRED FIELD CHECKS ===
            
            if (session.startTime <= 0) {
                violations.add("startTime is required and must be positive");
            }
            
            if (session.startLevel < LEVEL_MIN || session.startLevel > LEVEL_MAX) {
                violations.add("startLevel must be between " + LEVEL_MIN + " and " + LEVEL_MAX);
            }
            
            // === SESSION COMPLETENESS ===
            
            boolean isComplete = session.endTime > 0 && session.endTime > session.startTime;
            
            if (isComplete) {
                // Duration checks
                long duration = session.endTime - session.startTime;
                
                if (duration < MIN_SESSION_DURATION_MS) {
                    warnings.add("session duration " + (duration / 1000) + "s is very short");
                }
                
                if (duration > MAX_SESSION_DURATION_MS) {
                    warnings.add("session duration exceeds 24 hours - possibly incomplete");
                }
                
                // Level change checks
                if (session.endLevel < session.startLevel) {
                    violations.add("endLevel cannot be less than startLevel for charging session");
                }
                
                if (session.endLevel > LEVEL_MAX) {
                    violations.add("endLevel cannot exceed " + LEVEL_MAX);
                }
                
                // Charging rate sanity
                int levelGain = session.endLevel - session.startLevel;
                double durationHours = duration / (double) TimeUnit.HOURS.toMillis(1);
                double rate = levelGain / durationHours;
                
                if (levelGain > 0 && rate < MIN_CHARGING_RATE) {
                    warnings.add("charging rate " + String.format("%.2f", rate) + "%/hr is suspiciously slow");
                }
                
                if (rate > MAX_CHARGING_RATE) {
                    warnings.add("charging rate " + String.format("%.2f", rate) + "%/hr is suspiciously fast");
                }
            }
            
            // === BUILD RESULT ===
            
            if (!violations.isEmpty()) {
                return ContractValidationResult.invalid("ChargingSession", violations);
            }
            
            if (!isComplete) {
                warnings.add("session is incomplete (missing endTime or endLevel)");
            }
            
            if (!warnings.isEmpty()) {
                return ContractValidationResult.validWithWarnings("ChargingSession", warnings);
            }
            
            return ContractValidationResult.valid("ChargingSession");
        }
        
        /**
         * Check if session is incomplete.
         */
        public static boolean isIncomplete(@NonNull ChargingSession session) {
            return session.endTime <= 0 || session.endTime <= session.startTime ||
                   session.endLevel < session.startLevel;
        }
        
        /**
         * Check if session is stale (started long ago but never completed).
         */
        public static boolean isStale(@NonNull ChargingSession session, long maxAgeMs) {
            if (!isIncomplete(session)) return false;
            return System.currentTimeMillis() - session.startTime > maxAgeMs;
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // DRAIN EVENT CONTRACT
    // ═══════════════════════════════════════════════════════════════════════════════
    
    public static class DrainEventContract {
        
        // Valid ranges
        public static final int LEVEL_MIN = 0;
        public static final int LEVEL_MAX = 100;
        
        // Duration constraints
        public static final long MIN_EVENT_DURATION_MS = TimeUnit.SECONDS.toMillis(60);   // Min 1 min
        public static final long MAX_EVENT_DURATION_MS = TimeUnit.HOURS.toMillis(48);     // Max 48 hours
        
        // Drain rate constraints
        public static final float MIN_DRAIN_RATE = 0.0f;      // Can be 0 if plugged in
        public static final float MAX_DRAIN_RATE = 100.0f;    // Max 100%/hour (extreme)
        
        /**
         * Validate a DrainEvent against its contract.
         */
        public static ContractValidationResult validate(@NonNull DrainEvent event) {
            List<String> violations = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            
            // === REQUIRED FIELD CHECKS ===
            
            if (event.startTime <= 0) {
                violations.add("startTime is required and must be positive");
            }
            
            if (event.endTime <= 0) {
                violations.add("endTime is required and must be positive");
            }
            
            if (event.endTime > 0 && event.startTime > 0 && event.endTime <= event.startTime) {
                violations.add("endTime must be after startTime");
            }
            
            // === LEVEL CHECKS ===
            
            if (event.startLevel < LEVEL_MIN || event.startLevel > LEVEL_MAX) {
                violations.add("startLevel must be between " + LEVEL_MIN + " and " + LEVEL_MAX);
            }
            
            if (event.endLevel < LEVEL_MIN || event.endLevel > LEVEL_MAX) {
                violations.add("endLevel must be between " + LEVEL_MIN + " and " + LEVEL_MAX);
            }
            
            // For drain events, end level should be <= start level
            if (event.endLevel > event.startLevel + 1) {  // +1 tolerance for measurement variance
                warnings.add("endLevel > startLevel suggests this isn't a pure drain event");
            }
            
            // === DURATION CHECKS ===
            
            if (event.startTime > 0 && event.endTime > event.startTime) {
                long duration = event.endTime - event.startTime;
                
                if (duration < MIN_EVENT_DURATION_MS) {
                    warnings.add("event duration " + (duration / 1000) + "s is very short");
                }
                
                if (duration > MAX_EVENT_DURATION_MS) {
                    warnings.add("event duration exceeds 48 hours - possibly spanning multiple periods");
                }
            }
            
            // === DRAIN RATE CHECKS ===
            
            if (event.drainRatePerHour < MIN_DRAIN_RATE) {
                warnings.add("negative drain rate suggests charging, not draining");
            }
            
            if (event.drainRatePerHour > MAX_DRAIN_RATE) {
                warnings.add("drain rate " + event.drainRatePerHour + "%/hr is extremely high");
            }
            
            // === BUILD RESULT ===
            
            if (!violations.isEmpty()) {
                return ContractValidationResult.invalid("DrainEvent", violations);
            }
            
            if (!warnings.isEmpty()) {
                return ContractValidationResult.validWithWarnings("DrainEvent", warnings);
            }
            
            return ContractValidationResult.valid("DrainEvent");
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // THERMAL EVENT CONTRACT
    // ═══════════════════════════════════════════════════════════════════════════════
    
    public static class ThermalEventContract {
        
        // Temperature ranges (in tenths of degree Celsius or actual Celsius)
        public static final float TEMP_MIN_CELSIUS = -20.0f;   // -20°C
        public static final float TEMP_MAX_CELSIUS = 80.0f;    // 80°C
        public static final float TEMP_WARNING_CELSIUS = 40.0f; // 40°C
        public static final float TEMP_CRITICAL_CELSIUS = 45.0f; // 45°C
        
        /**
         * Validate a ThermalEvent against its contract.
         */
        public static ContractValidationResult validate(@NonNull ThermalEvent event) {
            List<String> violations = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            
            // === REQUIRED CHECKS ===
            
            if (event.timestamp <= 0) {
                violations.add("timestamp is required and must be positive");
            }
            
            // === TEMPERATURE RANGE CHECKS (using batteryTemp field) ===
            
            if (event.batteryTemp < TEMP_MIN_CELSIUS || event.batteryTemp > TEMP_MAX_CELSIUS) {
                violations.add("batteryTemp " + event.batteryTemp + "°C is outside valid range");
            }
            
            if (event.peakTemperature > 0 && event.peakTemperature > TEMP_MAX_CELSIUS) {
                warnings.add("peakTemperature " + event.peakTemperature + "°C exceeds maximum");
            }
            
            // === SEVERITY CONSISTENCY ===
            
            if (event.batteryTemp >= TEMP_CRITICAL_CELSIUS && 
                !"critical".equalsIgnoreCase(event.severity) &&
                !"high".equalsIgnoreCase(event.severity)) {
                warnings.add("batteryTemp " + event.batteryTemp + 
                            "°C should have high/critical severity");
            }
            
            // === BUILD RESULT ===
            
            if (!violations.isEmpty()) {
                return ContractValidationResult.invalid("ThermalEvent", violations);
            }
            
            if (!warnings.isEmpty()) {
                return ContractValidationResult.validWithWarnings("ThermalEvent", warnings);
            }
            
            return ContractValidationResult.valid("ThermalEvent");
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // BATTERY HEALTH CONTRACT
    // ═══════════════════════════════════════════════════════════════════════════════
    
    public static class BatteryHealthContract {
        
        // Health percentage ranges
        public static final float HEALTH_MIN = 0.0f;
        public static final float HEALTH_MAX = 100.0f;
        public static final float HEALTH_SUSPICIOUS_LOW = 30.0f;
        public static final float HEALTH_SUSPICIOUS_HIGH = 102.0f;  // Slight tolerance
        
        // Capacity ranges (mAh)
        public static final float CAPACITY_MIN = 500.0f;
        public static final float CAPACITY_MAX = 20000.0f;
        
        // Cycle count
        public static final float CYCLES_MIN = 0.0f;
        public static final float CYCLES_MAX = 5000.0f;  // Most batteries degrade well before this
        
        /**
         * Validate a BatteryHealth record against its contract.
         */
        public static ContractValidationResult validate(@NonNull BatteryHealth health) {
            List<String> violations = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            
            // === REQUIRED CHECKS ===
            
            if (health.timestamp <= 0) {
                violations.add("timestamp is required and must be positive");
            }
            
            // === HEALTH PERCENTAGE CHECKS (using healthPercentage field) ===
            
            if (health.healthPercentage < HEALTH_MIN || health.healthPercentage > HEALTH_MAX + 5) {
                violations.add("healthPercentage " + health.healthPercentage + " is outside valid range");
            }
            
            if (health.healthPercentage < HEALTH_SUSPICIOUS_LOW) {
                warnings.add("healthPercentage " + health.healthPercentage + "% is suspiciously low");
            }
            
            if (health.healthPercentage > HEALTH_SUSPICIOUS_HIGH) {
                warnings.add("healthPercentage " + health.healthPercentage + "% exceeds 100%");
            }
            
            // === CAPACITY CHECKS (using estimatedCapacityMah field) ===
            
            if (health.estimatedCapacityMah > 0) {
                if (health.estimatedCapacityMah < CAPACITY_MIN) {
                    warnings.add("estimatedCapacityMah " + health.estimatedCapacityMah + "mAh is very low");
                }
                if (health.estimatedCapacityMah > CAPACITY_MAX) {
                    warnings.add("estimatedCapacityMah " + health.estimatedCapacityMah + "mAh seems high");
                }
            }
            
            if (health.designCapacityMah > 0) {
                if (health.designCapacityMah < CAPACITY_MIN || health.designCapacityMah > CAPACITY_MAX) {
                    warnings.add("designCapacityMah " + health.designCapacityMah + "mAh is outside typical range");
                }
            }
            
            // === CYCLE COUNT CHECKS ===
            
            if (health.cycleCount < CYCLES_MIN) {
                violations.add("cycleCount cannot be negative");
            }
            
            if (health.cycleCount > CYCLES_MAX) {
                warnings.add("cycleCount " + health.cycleCount + " is extremely high");
            }
            
            // === BUILD RESULT ===
            
            if (!violations.isEmpty()) {
                return ContractValidationResult.invalid("BatteryHealth", violations);
            }
            
            if (!warnings.isEmpty()) {
                return ContractValidationResult.validWithWarnings("BatteryHealth", warnings);
            }
            
            return ContractValidationResult.valid("BatteryHealth");
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // AI ANALYSIS RESULT CONTRACT
    // ═══════════════════════════════════════════════════════════════════════════════
    
    public static class AIAnalysisResultContract {
        
        // Confidence ranges
        public static final float CONFIDENCE_MIN = 0.0f;
        public static final float CONFIDENCE_MAX = 1.0f;
        
        // Score ranges
        public static final float SCORE_MIN = 0.0f;
        public static final float SCORE_MAX = 100.0f;
        
        // Valid severity values
        public static final String[] VALID_SEVERITIES = {
                "info", "low", "medium", "high", "critical"
        };
        
        /**
         * Validate an AIAnalysisResult against its contract.
         */
        public static ContractValidationResult validate(@NonNull AIAnalysisResult result) {
            List<String> violations = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            
            // === REQUIRED CHECKS ===
            
            if (result.timestamp <= 0) {
                violations.add("timestamp is required and must be positive");
            }
            
            // === CONFIDENCE CHECKS ===
            
            if (result.overallConfidenceScore < CONFIDENCE_MIN || 
                result.overallConfidenceScore > CONFIDENCE_MAX) {
                violations.add("overallConfidenceScore must be between 0 and 1");
            }
            
            if (result.rootCauseConfidence < CONFIDENCE_MIN || 
                result.rootCauseConfidence > CONFIDENCE_MAX) {
                violations.add("rootCauseConfidence must be between 0 and 1");
            }
            
            // === SCORE CHECKS ===
            
            if (result.overallBatteryScore < SCORE_MIN || result.overallBatteryScore > SCORE_MAX) {
                warnings.add("overallBatteryScore " + result.overallBatteryScore + " is outside 0-100 range");
            }
            
            // === SEVERITY VALIDATION ===
            
            if (result.severity != null && !result.severity.isEmpty()) {
                boolean validSeverity = false;
                for (String valid : VALID_SEVERITIES) {
                    if (valid.equalsIgnoreCase(result.severity)) {
                        validSeverity = true;
                        break;
                    }
                }
                if (!validSeverity) {
                    warnings.add("severity '" + result.severity + "' is not a recognized value");
                }
            }
            
            // === ANTI-HALLUCINATION CHECKS ===
            
            // If AI wasn't allowed to run, certain fields should be empty
            if (!result.aiWasAllowedToRun) {
                if (result.overallConfidenceScore > 0.5) {
                    warnings.add("AI was blocked but confidence score is high - inconsistent");
                }
                if (result.rootCause != null && !result.rootCause.isEmpty() &&
                    !"INSUFFICIENT_DATA".equals(result.rootCauseCode)) {
                    warnings.add("AI was blocked but rootCause is set - inconsistent");
                }
            }
            
            // === BUILD RESULT ===
            
            if (!violations.isEmpty()) {
                return ContractValidationResult.invalid("AIAnalysisResult", violations);
            }
            
            if (!warnings.isEmpty()) {
                return ContractValidationResult.validWithWarnings("AIAnalysisResult", warnings);
            }
            
            return ContractValidationResult.valid("AIAnalysisResult");
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // CROSS-ENTITY VALIDATION RULES
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Validate relationship between charging session and snapshots.
     */
    public static ContractValidationResult validateSessionSnapshotConsistency(
            @NonNull ChargingSession session,
            @NonNull List<BatterySnapshot> snapshots) {
        
        List<String> violations = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Find snapshots during the session
        int snapshotsInSession = 0;
        int chargingSnapshots = 0;
        
        for (BatterySnapshot snap : snapshots) {
            if (snap.timestamp >= session.startTime && 
                (session.endTime <= 0 || snap.timestamp <= session.endTime)) {
                snapshotsInSession++;
                if (snap.isCharging) {
                    chargingSnapshots++;
                }
            }
        }
        
        // Should have at least some snapshots during session
        if (snapshotsInSession == 0) {
            warnings.add("No snapshots found during charging session period");
        }
        
        // Most snapshots during charging should show isCharging=true
        if (snapshotsInSession > 0) {
            double chargingRatio = (double) chargingSnapshots / snapshotsInSession;
            if (chargingRatio < 0.5) {
                warnings.add("Only " + String.format("%.0f%%", chargingRatio * 100) + 
                            " of snapshots during session show charging");
            }
        }
        
        if (!violations.isEmpty()) {
            return ContractValidationResult.invalid("Session-Snapshot", violations);
        }
        
        if (!warnings.isEmpty()) {
            return ContractValidationResult.validWithWarnings("Session-Snapshot", warnings);
        }
        
        return ContractValidationResult.valid("Session-Snapshot");
    }
    
    /**
     * Validate temporal consistency of snapshots (no time travel, reasonable gaps).
     */
    public static ContractValidationResult validateSnapshotSequence(
            @NonNull List<BatterySnapshot> snapshots) {
        
        if (snapshots.size() < 2) {
            return ContractValidationResult.valid("SnapshotSequence");
        }
        
        List<String> violations = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Sort by timestamp
        List<BatterySnapshot> sorted = new ArrayList<>(snapshots);
        sorted.sort((a, b) -> Long.compare(a.timestamp, b.timestamp));
        
        int timeReversals = 0;
        int largeGaps = 0;
        int duplicateTimestamps = 0;
        
        for (int i = 1; i < sorted.size(); i++) {
            long prevTime = sorted.get(i - 1).timestamp;
            long currTime = sorted.get(i).timestamp;
            
            // Check for duplicate timestamps
            if (currTime == prevTime) {
                duplicateTimestamps++;
            }
            
            // Check for time reversals (shouldn't happen after sort, but check IDs)
            if (sorted.get(i).id < sorted.get(i - 1).id && currTime < prevTime) {
                timeReversals++;
            }
            
            // Check for large gaps (> 2 hours)
            long gap = currTime - prevTime;
            if (gap > TimeUnit.HOURS.toMillis(2)) {
                largeGaps++;
            }
        }
        
        if (timeReversals > 0) {
            violations.add(timeReversals + " time reversal(s) detected in sequence");
        }
        
        if (duplicateTimestamps > sorted.size() * 0.1) {
            warnings.add(duplicateTimestamps + " duplicate timestamps (>" + 
                        String.format("%.0f%%", 100.0 * duplicateTimestamps / sorted.size()) + ")");
        }
        
        if (largeGaps > sorted.size() * 0.05) {
            warnings.add(largeGaps + " large gaps (>2hr) in data");
        }
        
        if (!violations.isEmpty()) {
            return ContractValidationResult.invalid("SnapshotSequence", violations);
        }
        
        if (!warnings.isEmpty()) {
            return ContractValidationResult.validWithWarnings("SnapshotSequence", warnings);
        }
        
        return ContractValidationResult.valid("SnapshotSequence");
    }
}
