package com.example.hexnil.manager;

import android.content.Context;
import android.os.Build;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.model.BatteryHealth;
import com.example.hexnil.model.BatterySnapshot;
import com.example.hexnil.model.SafetyEvent;

/**
 * OEM-Grade Safety Monitoring System
 * 
 * Performs comprehensive safety checks and persists SafetyEvent records when
 * thresholds are exceeded.
 * 
 * FIXED: Previously didn't populate deviceModel, firmwareVersion,
 * swellingRiskScore,
 * protectionAction, regulatoryStandard, and other compliance fields.
 * Now populates all available fields for complete safety event tracking.
 */
public class SafetyMonitor {

    private static final String TAG = "SafetyMonitor";

    // =========================================================================
    // SAFETY THRESHOLDS
    // =========================================================================

    // Temperature thresholds (°C)
    private static final float OVER_TEMP_WARNING = 45.0f;
    private static final float OVER_TEMP_CRITICAL = 48.0f;
    private static final float OVER_TEMP_EMERGENCY = 52.0f;

    // Voltage thresholds (mV)
    private static final float OVER_VOLTAGE_WARNING = 4450f;
    private static final float OVER_VOLTAGE_CRITICAL = 4500f;
    private static final float LOW_VOLTAGE_WARNING = 3400f;
    private static final float LOW_VOLTAGE_CRITICAL = 3200f;

    // Current thresholds (mA)
    private static final float OVER_CURRENT_WARNING = 2500f;
    private static final float OVER_CURRENT_CRITICAL = 3500f;

    // Regulatory thresholds (IEC 62133)
    private static final float REGULATORY_MAX_TEMP = 55.0f;
    private static final float REGULATORY_MAX_VOLTAGE = 4400f;

    // Swelling risk thresholds
    private static final float SWELLING_VOLTAGE_ANOMALY_THRESHOLD = 0.05f; // 5% deviation
    private static final float SWELLING_CAPACITY_ANOMALY_THRESHOLD = 0.10f; // 10% deviation

    private final Context context;
    private final HexNilDatabase database;

    // Device info (cached)
    private final String deviceModel;
    private final String firmwareVersion;
    
    // Tracking for resolution and abuse counting
    private SafetyEvent pendingResolution = null;
    private long pendingResolutionStartTime = 0;
    private float lowestVoltageTracked = Float.MAX_VALUE;
    
    // Abuse event counts (persisted per session)
    private int overTempAbuseCount = 0;
    private int overVoltageAbuseCount = 0;
    private int overCurrentAbuseCount = 0;
    private int overDischargeAbuseCount = 0;

    // ML Infrastructure
    private final DeviceProfileManager deviceProfileManager;

    public SafetyMonitor(Context context, HexNilDatabase database) {
        this.context = context;
        this.database = database;
        this.deviceModel = Build.MODEL;
        this.firmwareVersion = Build.FINGERPRINT;
        this.deviceProfileManager = new DeviceProfileManager(context, database);
    }

    // =========================================================================
    // MAIN SAFETY CHECK
    // =========================================================================

    public void checkSafety(BatterySnapshot snapshot) {
        if (snapshot == null)
            return;
            
        // Check if pending event has been resolved
        checkResolution(snapshot);

        float tempC = snapshot.getTemperatureCelsius();
        float voltage = snapshot.voltage;
        float current = Math.abs(snapshot.chargeRate);

        boolean overTemp = tempC >= OVER_TEMP_WARNING;
        boolean overVoltage = voltage >= OVER_VOLTAGE_WARNING;
        boolean lowVoltage = voltage <= LOW_VOLTAGE_WARNING && !snapshot.isCharging;
        boolean overCurrent = current >= OVER_CURRENT_WARNING;

        if (!overTemp && !overVoltage && !lowVoltage && !overCurrent) {
            return;
        }

        SafetyEvent event = createBaseEvent(snapshot);

        if (overTemp) {
            populateOverTempEvent(event, snapshot);
        } else if (overVoltage) {
            populateOverVoltageEvent(event, snapshot);
        } else if (lowVoltage) {
            populateLowVoltageEvent(event, snapshot);
        } else if (overCurrent) {
            populateOverCurrentEvent(event, snapshot);
        }

        // Calculate swelling risk for all safety events
        calculateSwellingRisk(event, snapshot);

        // Check regulatory compliance
        checkRegulatoryCompliance(event);

        database.safetyEventDao().insert(event);
    }

    // =========================================================================
    // BASE EVENT CREATION
    // =========================================================================

    private SafetyEvent createBaseEvent(BatterySnapshot snapshot) {
        SafetyEvent event = new SafetyEvent();
        event.timestamp = snapshot.timestamp;
        event.batteryLevel = snapshot.level;
        event.wasCharging = snapshot.isCharging;
        event.chargingType = snapshot.chargingType;
        event.activity = snapshot.foregroundApp;

        // Populate device info (CRITICAL FIX)
        event.deviceModel = deviceModel;
        event.firmwareVersion = firmwareVersion;
        event.batteryPartNumber = getBatteryPartNumber();

        // Base temperature and voltage for all events
        event.batteryTemperature = snapshot.getTemperatureCelsius();
        event.voltage = snapshot.voltage;
        event.current = snapshot.chargeRate;
        
        // Ambient temperature estimation (battery temp - thermal offset based on load)
        float loadFactor = Math.abs(snapshot.chargeRate) / 3000f; // Normalize by typical max current
        float thermalOffset = 3f + (loadFactor * 8f); // 3-11°C offset based on load
        event.ambientTemperature = Math.max(15f, snapshot.getTemperatureCelsius() - thermalOffset);
        
        // Track lowest voltage seen
        if (snapshot.voltage < lowestVoltageTracked) {
            lowestVoltageTracked = snapshot.voltage;
        }
        event.lowestVoltage = lowestVoltageTracked;
        
        // Calculate expected voltage for current battery level
        event.expectedVoltage = estimateExpectedVoltage(snapshot.level);

        // ML Infrastructure: populate device fingerprint and detection method
        event.detectionMethod = "heuristic";
        try {
            event.deviceFingerprint = deviceProfileManager.getCurrentFingerprint();
        } catch (Exception ignored) { }

        return event;
    }

    // =========================================================================
    // EVENT TYPE POPULATION
    // =========================================================================

    private void populateOverTempEvent(SafetyEvent event, BatterySnapshot snapshot) {
        float tempC = snapshot.getTemperatureCelsius();

        event.eventType = "over_temp";
        event.thresholdValue = OVER_TEMP_WARNING;
        event.measurementUnit = "°C";
        event.triggerValue = tempC;

        // Severity classification
        if (tempC >= OVER_TEMP_EMERGENCY) {
            event.severity = "emergency";
            event.wasThermalShutdown = true;
            event.protectionAction = "thermal_shutdown";
        } else if (tempC >= OVER_TEMP_CRITICAL) {
            event.severity = "critical";
            event.protectionAction = "throttle_charging";
        } else {
            event.severity = "warning";
            event.protectionAction = "monitor";
        }

        event.wasProtectionTriggered = tempC >= OVER_TEMP_CRITICAL;
        
        // Abuse tracking
        overTempAbuseCount++;
        event.abuseType = "thermal_abuse";
        event.abuseCount = overTempAbuseCount;
        event.abuseSeverity = Math.min(1.0f, (tempC - OVER_TEMP_WARNING) / 15f);
        
        // Track for resolution
        pendingResolution = event;
        pendingResolutionStartTime = System.currentTimeMillis();
    }

    private void populateOverVoltageEvent(SafetyEvent event, BatterySnapshot snapshot) {
        event.eventType = "over_voltage";
        event.thresholdValue = OVER_VOLTAGE_WARNING;
        event.measurementUnit = "mV";
        event.triggerValue = snapshot.voltage;

        if (snapshot.voltage >= OVER_VOLTAGE_CRITICAL) {
            event.severity = "critical";
            event.protectionAction = "stop_charging";
            event.wasProtectionTriggered = true;
        } else {
            event.severity = "warning";
            event.protectionAction = "monitor";
            event.wasProtectionTriggered = false;
        }
        
        // Abuse tracking
        overVoltageAbuseCount++;
        event.abuseType = "voltage_abuse";
        event.abuseCount = overVoltageAbuseCount;
        event.abuseSeverity = Math.min(1.0f, (snapshot.voltage - OVER_VOLTAGE_WARNING) / 100f);
        
        // Track for resolution
        pendingResolution = event;
        pendingResolutionStartTime = System.currentTimeMillis();
    }

    private void populateLowVoltageEvent(SafetyEvent event, BatterySnapshot snapshot) {
        event.eventType = "over_discharge";
        event.thresholdValue = LOW_VOLTAGE_WARNING;
        event.measurementUnit = "mV";
        event.triggerValue = snapshot.voltage;

        if (snapshot.voltage <= LOW_VOLTAGE_CRITICAL) {
            event.severity = "critical";
            event.protectionAction = "shutdown";
            event.wasEmergencyShutdown = true;
            event.wasProtectionTriggered = true;
        } else {
            event.severity = "warning";
            event.protectionAction = "low_power_mode";
            event.wasProtectionTriggered = false;
        }
        
        // Abuse tracking
        overDischargeAbuseCount++;
        event.abuseType = "deep_discharge";
        event.abuseCount = overDischargeAbuseCount;
        event.abuseSeverity = Math.min(1.0f, (LOW_VOLTAGE_WARNING - snapshot.voltage) / 200f);
        
        // Track for resolution
        pendingResolution = event;
        pendingResolutionStartTime = System.currentTimeMillis();
    }

    private void populateOverCurrentEvent(SafetyEvent event, BatterySnapshot snapshot) {
        float current = Math.abs(snapshot.chargeRate);

        event.eventType = "over_current";
        event.thresholdValue = OVER_CURRENT_WARNING;
        event.measurementUnit = "mA";
        event.triggerValue = current;
        event.expectedCurrent = 2000f; // Normal charging current

        if (current >= OVER_CURRENT_CRITICAL) {
            event.severity = "critical";
            event.protectionAction = "limit_current";
            event.wasProtectionTriggered = true;
        } else {
            event.severity = "warning";
            event.protectionAction = "monitor";
            event.wasProtectionTriggered = false;
        }
        
        // Abuse tracking
        overCurrentAbuseCount++;
        event.abuseType = "current_abuse";
        event.abuseCount = overCurrentAbuseCount;
        event.abuseSeverity = Math.min(1.0f, (current - OVER_CURRENT_WARNING) / 1500f);
        
        // Track for resolution
        pendingResolution = event;
        pendingResolutionStartTime = System.currentTimeMillis();
    }

    // =========================================================================
    // SWELLING RISK CALCULATION
    // =========================================================================

    private void calculateSwellingRisk(SafetyEvent event, BatterySnapshot snapshot) {
        // Get latest health data for comparison
        BatteryHealth health = database.batteryHealthDao().getLatest();

        float voltageAnomaly = 0f;
        float capacityAnomaly = 0f;
        float resistanceAnomaly = 0f;

        if (health != null) {
            // Check voltage anomaly - compare to expected for this level
            float expectedVoltage = estimateExpectedVoltage(snapshot.level);
            if (expectedVoltage > 0) {
                voltageAnomaly = Math.abs(snapshot.voltage - expectedVoltage) / expectedVoltage;
            }

            // Check capacity anomaly
            if (health.designCapacityMah > 0 && health.currentCapacityMah > 0) {
                float expectedCapacity = health.designCapacityMah * (snapshot.level / 100f);
                capacityAnomaly = Math.abs(health.currentCapacityMah - expectedCapacity) / health.designCapacityMah;
            }

            // Check resistance anomaly (high internal resistance indicates swelling)
            if (health.internalResistance > 0) {
                float normalResistance = 100f; // mΩ baseline
                resistanceAnomaly = Math.max(0, (health.internalResistance - normalResistance) / normalResistance);
            }
        }

        // Populate anomaly fields
        event.voltageAnomaly = voltageAnomaly;
        event.capacityAnomaly = capacityAnomaly;
        event.resistanceAnomaly = resistanceAnomaly;

        // Calculate combined swelling risk score (0-1)
        float tempRisk = snapshot.getTemperatureCelsius() > 40 ? (snapshot.getTemperatureCelsius() - 40) / 15f : 0f;
        float voltageRisk = voltageAnomaly > SWELLING_VOLTAGE_ANOMALY_THRESHOLD ? voltageAnomaly * 2 : 0f;
        float capacityRisk = capacityAnomaly > SWELLING_CAPACITY_ANOMALY_THRESHOLD ? capacityAnomaly : 0f;
        float resistanceRisk = resistanceAnomaly * 0.5f;

        event.swellingRiskScore = Math.min(1.0f,
                (tempRisk * 0.3f) + (voltageRisk * 0.25f) + (capacityRisk * 0.25f) + (resistanceRisk * 0.2f));

        // Flag if swelling risk is significant
        if (event.swellingRiskScore > 0.5f) {
            event.abuseType = "swelling_risk";
            event.abuseSeverity = event.swellingRiskScore;
        }
    }

    private float estimateExpectedVoltage(int level) {
        // Linear approximation of Li-ion voltage curve
        // 3.0V at 0%, 4.2V at 100%
        return 3000f + (level * 12f); // Returns mV
    }

    // =========================================================================
    // REGULATORY COMPLIANCE
    // =========================================================================

    private void checkRegulatoryCompliance(SafetyEvent event) {
        boolean exceedsIEC = false;

        // IEC 62133 safety limits
        if (event.batteryTemperature >= REGULATORY_MAX_TEMP) {
            exceedsIEC = true;
        }
        if (event.voltage >= REGULATORY_MAX_VOLTAGE) {
            exceedsIEC = true;
        }

        event.exceedsRegulatory = exceedsIEC;
        event.regulatoryStandard = "IEC 62133";
        event.requiresReport = exceedsIEC || "emergency".equals(event.severity);
    }

    // =========================================================================
    // SPECIALIZED SAFETY CHECKS
    // =========================================================================

    public void checkLowBatterySafety() {
        SafetyEvent event = new SafetyEvent();
        event.eventType = "over_discharge";
        event.severity = "warning";
        event.wasCharging = false;
        event.measurementUnit = "%";
        event.triggerValue = 15;
        event.thresholdValue = 15;
        event.batteryLevel = 15;
        event.protectionAction = "low_power_mode";

        // Device info
        event.deviceModel = deviceModel;
        event.firmwareVersion = firmwareVersion;
        event.regulatoryStandard = "IEC 62133";
        event.exceedsRegulatory = false;
        event.requiresReport = false;

        // ML Infrastructure
        event.detectionMethod = "heuristic";
        try {
            event.deviceFingerprint = deviceProfileManager.getCurrentFingerprint();
        } catch (Exception ignored) { }

        database.safetyEventDao().insert(event);
    }

    public void checkHighTemperatureSafety(int tempTenths) {
        float tempC = tempTenths / 10.0f;
        if (tempC < OVER_TEMP_WARNING)
            return;

        SafetyEvent event = new SafetyEvent();
        event.timestamp = System.currentTimeMillis();
        event.eventType = "over_temp";
        event.batteryTemperature = tempC;
        event.thresholdValue = OVER_TEMP_WARNING;
        event.measurementUnit = "°C";
        event.triggerValue = tempC;
        event.wasCharging = false;

        // ML Infrastructure
        event.detectionMethod = "heuristic";
        try {
            event.deviceFingerprint = deviceProfileManager.getCurrentFingerprint();
        } catch (Exception ignored) { }

        // Severity classification
        if (tempC >= OVER_TEMP_EMERGENCY) {
            event.severity = "emergency";
            event.protectionAction = "thermal_shutdown";
            event.wasThermalShutdown = true;
        } else if (tempC >= OVER_TEMP_CRITICAL) {
            event.severity = "critical";
            event.protectionAction = "throttle";
        } else {
            event.severity = "warning";
            event.protectionAction = "monitor";
        }

        event.wasProtectionTriggered = tempC >= OVER_TEMP_CRITICAL;

        // Device info
        event.deviceModel = deviceModel;
        event.firmwareVersion = firmwareVersion;

        // Regulatory
        event.exceedsRegulatory = tempC >= REGULATORY_MAX_TEMP;
        event.regulatoryStandard = "IEC 62133";
        event.requiresReport = event.exceedsRegulatory;

        database.safetyEventDao().insert(event);
    }

    // =========================================================================
    // HELPERS
    // =========================================================================
    
    /**
     * Check if a pending safety event has been resolved.
     */
    private void checkResolution(BatterySnapshot snapshot) {
        if (pendingResolution == null || pendingResolutionStartTime == 0) {
            return;
        }
        
        boolean resolved = false;
        String eventType = pendingResolution.eventType;
        
        if ("over_temp".equals(eventType)) {
            // Resolved if temperature dropped below warning threshold
            resolved = snapshot.getTemperatureCelsius() < OVER_TEMP_WARNING;
        } else if ("over_voltage".equals(eventType)) {
            // Resolved if voltage dropped below warning threshold
            resolved = snapshot.voltage < OVER_VOLTAGE_WARNING;
        } else if ("over_discharge".equals(eventType)) {
            // Resolved if voltage recovered (charging started)
            resolved = snapshot.voltage > LOW_VOLTAGE_WARNING || snapshot.isCharging;
        } else if ("over_current".equals(eventType)) {
            // Resolved if current dropped below warning threshold
            resolved = Math.abs(snapshot.chargeRate) < OVER_CURRENT_WARNING;
        }
        
        if (resolved) {
            long resolutionTime = System.currentTimeMillis() - pendingResolutionStartTime;
            
            // Update the most recent safety event of this type
            SafetyEvent latest = database.safetyEventDao().getLatestByType(eventType);
            if (latest != null && !latest.wasResolved) {
                latest.wasResolved = true;
                latest.resolutionTime = resolutionTime;
                database.safetyEventDao().update(latest);
            }
            
            // Clear pending
            pendingResolution = null;
            pendingResolutionStartTime = 0;
        }
    }

    private String getBatteryPartNumber() {
        // Battery part number is typically OEM-specific
        // Return device model as placeholder
        return Build.MODEL + "-" + Build.MANUFACTURER;
    }

    /**
     * Check if a specific safety condition is currently triggered.
     */
    public boolean isTemperatureWarning(BatterySnapshot snapshot) {
        return snapshot != null && snapshot.getTemperatureCelsius() >= OVER_TEMP_WARNING;
    }

    public boolean isTemperatureCritical(BatterySnapshot snapshot) {
        return snapshot != null && snapshot.getTemperatureCelsius() >= OVER_TEMP_CRITICAL;
    }

    public boolean isVoltageWarning(BatterySnapshot snapshot) {
        return snapshot != null &&
                (snapshot.voltage >= OVER_VOLTAGE_WARNING || snapshot.voltage <= LOW_VOLTAGE_WARNING);
    }
}
