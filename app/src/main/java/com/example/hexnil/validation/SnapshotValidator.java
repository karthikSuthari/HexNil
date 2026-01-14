package com.example.hexnil.validation;

import android.util.Log;

import com.example.hexnil.model.BatterySnapshot;

/**
 * OEM-Grade Snapshot Validation System
 * 
 * Ensures every battery snapshot meets production quality standards:
 * - Fresh timestamp (not reused)
 * - Valid sensor readings (not frozen/stale)
 * - Proper sampling intervals
 * - Anomaly detection for sensor failures
 * 
 * This validator prevents the critical bug where snapshots are reused
 * or contain frozen/cached data from BatteryManager.
 */
public class SnapshotValidator {

    private static final String TAG = "SnapshotValidator";

    // Validation thresholds
    public static final long MIN_SNAPSHOT_INTERVAL_MS = 5000;        // 5 seconds minimum between snapshots
    public static final long MAX_TIMESTAMP_DRIFT_MS = 2000;          // 2 seconds max drift from wall clock
    public static final int MAX_CONSECUTIVE_IDENTICAL_VALUES = 3;    // Max identical readings before flagging
    public static final long STALE_DATA_THRESHOLD_MS = 120000;       // Data older than 2 mins is stale
    
    // Voltage bounds (millivolts) - typical Li-ion range
    public static final int MIN_VALID_VOLTAGE_MV = 2500;             // Below this = dead battery or sensor failure
    public static final int MAX_VALID_VOLTAGE_MV = 4500;             // Above this = sensor error
    
    // Temperature bounds (tenths of degree Celsius)
    public static final int MIN_VALID_TEMP_DECIDEGC = -200;          // -20°C
    public static final int MAX_VALID_TEMP_DECIDEGC = 800;           // 80°C (safety limit)
    
    // Current bounds (microamperes)
    public static final int MAX_VALID_CURRENT_UA = 10000000;         // 10A max (extreme fast charging)
    public static final int MIN_VALID_CURRENT_UA = -10000000;        // -10A (extreme discharge)

    /**
     * Validation result containing detailed status and quality metrics
     */
    public static class ValidationResult {
        public boolean isValid;
        public int qualityScore;           // 0-100
        public String invalidReason;
        public ValidationFlags flags;
        
        public ValidationResult() {
            this.isValid = true;
            this.qualityScore = 100;
            this.flags = new ValidationFlags();
        }
        
        public void fail(String reason) {
            this.isValid = false;
            this.invalidReason = reason;
            this.qualityScore = 0;
        }
        
        public void deduct(int points, String reason) {
            this.qualityScore = Math.max(0, this.qualityScore - points);
            if (this.invalidReason == null) {
                this.invalidReason = reason;
            } else {
                this.invalidReason += "; " + reason;
            }
        }
    }
    
    /**
     * Detailed validation flags for OEM diagnostics
     */
    public static class ValidationFlags {
        public boolean timestampValid = true;
        public boolean timestampFresh = true;
        public boolean voltageInRange = true;
        public boolean temperatureInRange = true;
        public boolean currentInRange = true;
        public boolean levelValid = true;
        public boolean statusValid = true;
        public boolean notDuplicate = true;
        public boolean sensorResponsive = true;
        public boolean chargeCounterValid = true;
        public boolean dataConsistent = true;
        
        public int getFlagCount() {
            int count = 0;
            if (!timestampValid) count++;
            if (!timestampFresh) count++;
            if (!voltageInRange) count++;
            if (!temperatureInRange) count++;
            if (!currentInRange) count++;
            if (!levelValid) count++;
            if (!statusValid) count++;
            if (!notDuplicate) count++;
            if (!sensorResponsive) count++;
            if (!chargeCounterValid) count++;
            if (!dataConsistent) count++;
            return count;
        }
    }

    /**
     * Validates a snapshot for freshness and data quality
     * @param snapshot The snapshot to validate
     * @param previousSnapshot Optional previous snapshot for comparison
     * @return ValidationResult with detailed status
     */
    public static ValidationResult validate(BatterySnapshot snapshot, BatterySnapshot previousSnapshot) {
        ValidationResult result = new ValidationResult();
        
        if (snapshot == null) {
            result.fail("Snapshot is null");
            return result;
        }
        
        // 1. Timestamp validation
        validateTimestamp(snapshot, previousSnapshot, result);
        
        // 2. Voltage validation
        validateVoltage(snapshot, result);
        
        // 3. Temperature validation
        validateTemperature(snapshot, result);
        
        // 4. Current validation
        validateCurrent(snapshot, result);
        
        // 5. Level validation
        validateLevel(snapshot, result);
        
        // 6. Status validation
        validateStatus(snapshot, result);
        
        // 7. Charge counter validation
        validateChargeCounter(snapshot, result);
        
        // 8. Duplicate detection (if previous snapshot exists)
        if (previousSnapshot != null) {
            validateNotDuplicate(snapshot, previousSnapshot, result);
            validateSensorResponsiveness(snapshot, previousSnapshot, result);
            validateDataConsistency(snapshot, previousSnapshot, result);
        }
        
        // Final quality score adjustment
        int flagPenalty = result.flags.getFlagCount() * 8;
        result.qualityScore = Math.max(0, result.qualityScore - flagPenalty);
        
        // Log validation issues for OEM diagnostics
        if (!result.isValid || result.qualityScore < 70) {
            Log.w(TAG, "Snapshot validation issue: valid=" + result.isValid + 
                    ", quality=" + result.qualityScore + 
                    ", reason=" + result.invalidReason);
        }
        
        return result;
    }
    
    private static void validateTimestamp(BatterySnapshot snapshot, BatterySnapshot previous, ValidationResult result) {
        long now = System.currentTimeMillis();
        
        // Check timestamp is set
        if (snapshot.timestamp <= 0) {
            result.fail("Invalid timestamp: " + snapshot.timestamp);
            result.flags.timestampValid = false;
            return;
        }
        
        // Check timestamp is not in the future
        if (snapshot.timestamp > now + MAX_TIMESTAMP_DRIFT_MS) {
            result.deduct(20, "Timestamp in future by " + (snapshot.timestamp - now) + "ms");
            result.flags.timestampValid = false;
        }
        
        // Check timestamp is not too old (stale data)
        if (now - snapshot.timestamp > STALE_DATA_THRESHOLD_MS) {
            result.deduct(30, "Stale timestamp: " + (now - snapshot.timestamp) + "ms old");
            result.flags.timestampFresh = false;
        }
        
        // Check interval from previous snapshot
        if (previous != null && snapshot.timestamp <= previous.timestamp) {
            result.fail("Timestamp not advancing: current=" + snapshot.timestamp + 
                    ", previous=" + previous.timestamp);
            result.flags.timestampValid = false;
        }
        
        // Check minimum interval
        if (previous != null) {
            long interval = snapshot.timestamp - previous.timestamp;
            if (interval < MIN_SNAPSHOT_INTERVAL_MS) {
                result.deduct(10, "Snapshot interval too short: " + interval + "ms");
            }
        }
    }
    
    private static void validateVoltage(BatterySnapshot snapshot, ValidationResult result) {
        if (snapshot.voltage < MIN_VALID_VOLTAGE_MV) {
            result.deduct(15, "Voltage too low: " + snapshot.voltage + "mV");
            result.flags.voltageInRange = false;
        } else if (snapshot.voltage > MAX_VALID_VOLTAGE_MV) {
            result.deduct(15, "Voltage too high: " + snapshot.voltage + "mV");
            result.flags.voltageInRange = false;
        }
        
        // Zero voltage is a sensor failure
        if (snapshot.voltage == 0) {
            result.deduct(25, "Voltage sensor returned zero");
            result.flags.voltageInRange = false;
            result.flags.sensorResponsive = false;
        }
    }
    
    private static void validateTemperature(BatterySnapshot snapshot, ValidationResult result) {
        if (snapshot.temperature < MIN_VALID_TEMP_DECIDEGC) {
            result.deduct(15, "Temperature too low: " + (snapshot.temperature / 10.0f) + "°C");
            result.flags.temperatureInRange = false;
        } else if (snapshot.temperature > MAX_VALID_TEMP_DECIDEGC) {
            result.deduct(15, "Temperature too high: " + (snapshot.temperature / 10.0f) + "°C");
            result.flags.temperatureInRange = false;
        }
        
        // Zero temperature is suspicious (should be around 200-300 = 20-30°C typically)
        if (snapshot.temperature == 0) {
            result.deduct(10, "Temperature sensor returned zero");
            result.flags.temperatureInRange = false;
        }
    }
    
    private static void validateCurrent(BatterySnapshot snapshot, ValidationResult result) {
        if (snapshot.currentNow < MIN_VALID_CURRENT_UA || snapshot.currentNow > MAX_VALID_CURRENT_UA) {
            result.deduct(10, "Current out of range: " + snapshot.currentNow + "µA");
            result.flags.currentInRange = false;
        }
        
        // Consistency check: charging state should match current direction
        if (snapshot.isCharging && snapshot.currentNow < 0) {
            // Some devices report negative current even when charging (convention varies)
            // This is a soft warning, not a hard failure
            result.deduct(5, "Charging but current negative (device convention may vary)");
        }
    }
    
    private static void validateLevel(BatterySnapshot snapshot, ValidationResult result) {
        if (snapshot.level < 0 || snapshot.level > 100) {
            result.fail("Invalid battery level: " + snapshot.level);
            result.flags.levelValid = false;
        }
    }
    
    private static void validateStatus(BatterySnapshot snapshot, ValidationResult result) {
        // Valid status values from BatteryManager
        // BATTERY_STATUS_UNKNOWN = 1
        // BATTERY_STATUS_CHARGING = 2
        // BATTERY_STATUS_DISCHARGING = 3
        // BATTERY_STATUS_NOT_CHARGING = 4
        // BATTERY_STATUS_FULL = 5
        if (snapshot.status < 1 || snapshot.status > 5) {
            result.deduct(10, "Invalid battery status: " + snapshot.status);
            result.flags.statusValid = false;
        }
    }
    
    private static void validateChargeCounter(BatterySnapshot snapshot, ValidationResult result) {
        // Charge counter should be positive if available
        if (snapshot.chargeCounter < 0 && snapshot.chargeCounter != Long.MIN_VALUE) {
            result.deduct(10, "Negative charge counter: " + snapshot.chargeCounter);
            result.flags.chargeCounterValid = false;
        }
        
        // Extremely large values might indicate overflow or sensor error
        if (snapshot.chargeCounter > 100000000) { // > 100000 mAh
            result.deduct(5, "Unusually large charge counter");
            result.flags.chargeCounterValid = false;
        }
    }
    
    private static void validateNotDuplicate(BatterySnapshot current, BatterySnapshot previous, ValidationResult result) {
        // Check if this is an exact duplicate (same timestamp = definitely reused)
        if (current.timestamp == previous.timestamp) {
            result.fail("Duplicate timestamp detected - snapshot was reused");
            result.flags.notDuplicate = false;
            return;
        }
        
        // Check if all key values are identical (sensor frozen)
        boolean allIdentical = 
                current.level == previous.level &&
                current.voltage == previous.voltage &&
                current.temperature == previous.temperature &&
                current.currentNow == previous.currentNow &&
                current.chargeCounter == previous.chargeCounter;
        
        if (allIdentical) {
            // Values can legitimately be identical for short periods, but flag it
            result.deduct(15, "All sensor values identical to previous snapshot");
            result.flags.sensorResponsive = false;
        }
    }
    
    private static void validateSensorResponsiveness(BatterySnapshot current, BatterySnapshot previous, ValidationResult result) {
        long timeDelta = current.timestamp - previous.timestamp;
        
        if (timeDelta <= 0) return; // Already flagged in timestamp validation
        
        // For intervals > 1 minute, we expect SOME change in readings
        if (timeDelta > 60000) {
            // At least voltage or current should show some variance
            boolean voltageChanged = Math.abs(current.voltage - previous.voltage) > 0;
            boolean currentChanged = Math.abs(current.currentNow - previous.currentNow) > 0;
            boolean tempChanged = Math.abs(current.temperature - previous.temperature) > 0;
            
            if (!voltageChanged && !currentChanged && !tempChanged) {
                result.deduct(20, "No sensor variance over " + (timeDelta / 1000) + " seconds");
                result.flags.sensorResponsive = false;
            }
        }
    }
    
    private static void validateDataConsistency(BatterySnapshot current, BatterySnapshot previous, ValidationResult result) {
        long timeDelta = current.timestamp - previous.timestamp;
        
        if (timeDelta <= 0) return;
        
        // Level should not jump more than 10% per minute under normal conditions
        // (Fast charging can do ~2-3% per minute max)
        float minutesElapsed = timeDelta / 60000.0f;
        if (minutesElapsed > 0) {
            int levelChange = Math.abs(current.level - previous.level);
            float changePerMinute = levelChange / minutesElapsed;
            
            // More than 5%/minute is suspicious unless just plugged in/out
            if (changePerMinute > 5 && minutesElapsed > 0.5f) {
                result.deduct(10, "Unusual level change rate: " + String.format("%.1f", changePerMinute) + "%/min");
                result.flags.dataConsistent = false;
            }
        }
        
        // Voltage should not jump more than 500mV instantly
        int voltageDelta = Math.abs(current.voltage - previous.voltage);
        if (voltageDelta > 500 && timeDelta < 30000) {
            result.deduct(10, "Large voltage jump: " + voltageDelta + "mV in " + (timeDelta / 1000) + "s");
            result.flags.dataConsistent = false;
        }
    }
    
    /**
     * Quick check if snapshot has minimum required data
     */
    public static boolean hasMinimumData(BatterySnapshot snapshot) {
        if (snapshot == null) return false;
        return snapshot.timestamp > 0 &&
               snapshot.level >= 0 && snapshot.level <= 100 &&
               snapshot.voltage > 0;
    }
    
    /**
     * Creates a diagnostic string for OEM logging
     */
    public static String getDiagnosticString(BatterySnapshot snapshot, ValidationResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("SNAPSHOT_DIAG[");
        sb.append("id=").append(snapshot.id);
        sb.append(",ts=").append(snapshot.timestamp);
        sb.append(",level=").append(snapshot.level);
        sb.append(",voltage=").append(snapshot.voltage);
        sb.append(",temp=").append(snapshot.temperature);
        sb.append(",current=").append(snapshot.currentNow);
        sb.append(",valid=").append(result.isValid);
        sb.append(",quality=").append(result.qualityScore);
        if (result.invalidReason != null) {
            sb.append(",reason=").append(result.invalidReason);
        }
        sb.append("]");
        return sb.toString();
    }
}
