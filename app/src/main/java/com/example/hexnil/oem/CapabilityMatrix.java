package com.example.hexnil.oem;

import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Capability Matrix - Feature-by-Feature OEM Integration Guide
 * 
 * Provides a structured view of:
 * 1. What HexNil can do TODAY without any special access
 * 2. What HexNil COULD do with OEM integration
 * 3. The specific technical requirements for each enhancement
 * 
 * This matrix serves as a technical spec for OEM integration discussions.
 */
public class CapabilityMatrix {
    
    private static final String TAG = "CapabilityMatrix";
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // CAPABILITY LEVELS
    // ═══════════════════════════════════════════════════════════════════════════════
    
    public enum CapabilityLevel {
        FULL("Full", "✅", 100, 
                "Complete functionality with standard Android APIs"),
        
        HIGH("High", "🟢", 85, 
                "Most functionality available, minor limitations"),
        
        MODERATE("Moderate", "🟡", 60, 
                "Core functionality works, accuracy limitations"),
        
        LOW("Low", "🟠", 35, 
                "Limited functionality, significant gaps"),
        
        NONE("None", "🔴", 0, 
                "Cannot implement without privileged access"),
        
        OEM_ENHANCED("OEM Enhanced", "⭐", 100, 
                "Full capability with OEM integration");
        
        public final String label;
        public final String icon;
        public final int score;
        public final String description;
        
        CapabilityLevel(String label, String icon, int score, String description) {
            this.label = label;
            this.icon = icon;
            this.score = score;
            this.description = description;
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // CAPABILITY ENTRY
    // ═══════════════════════════════════════════════════════════════════════════════
    
    public static class CapabilityEntry {
        public final String id;
        public final String category;
        public final String capability;
        
        // Current state (without OEM support)
        public final CapabilityLevel currentLevel;
        public final String currentImplementation;
        public final List<String> currentLimitations;
        public final String currentAccuracy;
        
        // OEM-enhanced state
        public final CapabilityLevel oemLevel;
        public final String oemImplementation;
        public final List<String> oemRequirements;
        public final String oemAccuracy;
        
        // Integration details
        public final String technicalSpec;
        public final String integrationEffort;
        public final String priority;
        
        public CapabilityEntry(String id, String category, String capability,
                                CapabilityLevel currentLevel, String currentImplementation,
                                List<String> currentLimitations, String currentAccuracy,
                                CapabilityLevel oemLevel, String oemImplementation,
                                List<String> oemRequirements, String oemAccuracy,
                                String technicalSpec, String integrationEffort, String priority) {
            this.id = id;
            this.category = category;
            this.capability = capability;
            this.currentLevel = currentLevel;
            this.currentImplementation = currentImplementation;
            this.currentLimitations = currentLimitations;
            this.currentAccuracy = currentAccuracy;
            this.oemLevel = oemLevel;
            this.oemImplementation = oemImplementation;
            this.oemRequirements = oemRequirements;
            this.oemAccuracy = oemAccuracy;
            this.technicalSpec = technicalSpec;
            this.integrationEffort = integrationEffort;
            this.priority = priority;
        }
        
        public int getUpliftScore() {
            return oemLevel.score - currentLevel.score;
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // MATRIX REPORT
    // ═══════════════════════════════════════════════════════════════════════════════
    
    public static class MatrixReport {
        public final long generatedTimestamp;
        public final String deviceModel;
        public final int apiLevel;
        
        public final Map<String, List<CapabilityEntry>> capabilitiesByCategory;
        public final int totalCapabilities;
        public final int currentFullCount;
        public final int currentNoneCount;
        public final int totalUpliftPotential;
        
        public final List<CapabilityEntry> highPriorityIntegrations;
        public final List<CapabilityEntry> quickWins;
        
        public MatrixReport(long generatedTimestamp, String deviceModel, int apiLevel,
                             Map<String, List<CapabilityEntry>> capabilitiesByCategory,
                             List<CapabilityEntry> highPriorityIntegrations,
                             List<CapabilityEntry> quickWins) {
            this.generatedTimestamp = generatedTimestamp;
            this.deviceModel = deviceModel;
            this.apiLevel = apiLevel;
            this.capabilitiesByCategory = capabilitiesByCategory;
            
            int total = 0, fullCount = 0, noneCount = 0, uplift = 0;
            for (List<CapabilityEntry> entries : capabilitiesByCategory.values()) {
                for (CapabilityEntry entry : entries) {
                    total++;
                    if (entry.currentLevel == CapabilityLevel.FULL) fullCount++;
                    if (entry.currentLevel == CapabilityLevel.NONE) noneCount++;
                    uplift += entry.getUpliftScore();
                }
            }
            this.totalCapabilities = total;
            this.currentFullCount = fullCount;
            this.currentNoneCount = noneCount;
            this.totalUpliftPotential = uplift;
            
            this.highPriorityIntegrations = highPriorityIntegrations;
            this.quickWins = quickWins;
        }
        
        public float getCurrentCapabilityScore() {
            if (totalCapabilities == 0) return 0;
            int totalScore = 0;
            for (List<CapabilityEntry> entries : capabilitiesByCategory.values()) {
                for (CapabilityEntry entry : entries) {
                    totalScore += entry.currentLevel.score;
                }
            }
            return totalScore * 100f / (totalCapabilities * 100);
        }
        
        public float getOEMCapabilityScore() {
            if (totalCapabilities == 0) return 0;
            int totalScore = 0;
            for (List<CapabilityEntry> entries : capabilitiesByCategory.values()) {
                for (CapabilityEntry entry : entries) {
                    totalScore += entry.oemLevel.score;
                }
            }
            return totalScore * 100f / (totalCapabilities * 100);
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // MATRIX GENERATOR
    // ═══════════════════════════════════════════════════════════════════════════════
    
    private final Context context;
    
    public CapabilityMatrix(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }
    
    /**
     * Generate complete capability matrix.
     */
    public MatrixReport generateMatrix() {
        Map<String, List<CapabilityEntry>> matrix = new LinkedHashMap<>();
        
        // Build matrix by category
        matrix.put("Core Monitoring", buildCoreMonitoringCapabilities());
        matrix.put("Battery Health", buildBatteryHealthCapabilities());
        matrix.put("Charging Intelligence", buildChargingCapabilities());
        matrix.put("Thermal Analysis", buildThermalCapabilities());
        matrix.put("Drain Analysis", buildDrainCapabilities());
        matrix.put("Calibration", buildCalibrationCapabilities());
        matrix.put("AI Intelligence", buildAICapabilities());
        matrix.put("Data Quality", buildDataQualityCapabilities());
        
        // Identify high-priority integrations
        List<CapabilityEntry> highPriority = new ArrayList<>();
        List<CapabilityEntry> quickWins = new ArrayList<>();
        
        for (List<CapabilityEntry> entries : matrix.values()) {
            for (CapabilityEntry entry : entries) {
                if ("HIGH".equals(entry.priority) && entry.getUpliftScore() >= 50) {
                    highPriority.add(entry);
                }
                if ("Low".equals(entry.integrationEffort) && entry.getUpliftScore() >= 30) {
                    quickWins.add(entry);
                }
            }
        }
        
        return new MatrixReport(
                System.currentTimeMillis(),
                Build.MODEL,
                Build.VERSION.SDK_INT,
                matrix,
                highPriority,
                quickWins
        );
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // CATEGORY BUILDERS
    // ═══════════════════════════════════════════════════════════════════════════════
    
    private List<CapabilityEntry> buildCoreMonitoringCapabilities() {
        List<CapabilityEntry> entries = new ArrayList<>();
        
        entries.add(new CapabilityEntry(
                "CORE-001", "Core Monitoring", "Battery Level (SOC)",
                CapabilityLevel.FULL,
                "BatteryManager.EXTRA_LEVEL provides 0-100% with 1% resolution",
                createList(),
                "100% accurate (same as system)",
                CapabilityLevel.OEM_ENHANCED,
                "Fuel gauge provides additional details (DOD%, QMax)",
                createList("Fuel gauge HAL binding"),
                "100% + degradation awareness",
                "Read StateOfCharge register from fuel gauge IC",
                "Low",
                "LOW"
        ));
        
        entries.add(new CapabilityEntry(
                "CORE-002", "Core Monitoring", "Battery Voltage",
                CapabilityLevel.FULL,
                "BatteryManager.EXTRA_VOLTAGE in millivolts",
                createList("Resolution varies 10-50mV by device"),
                "±50mV typical",
                CapabilityLevel.OEM_ENHANCED,
                "Direct ADC reading from fuel gauge",
                createList("Fuel gauge HAL binding"),
                "±5mV",
                "Read Voltage register from fuel gauge IC",
                "Low",
                "LOW"
        ));
        
        entries.add(new CapabilityEntry(
                "CORE-003", "Core Monitoring", "Battery Temperature",
                CapabilityLevel.FULL,
                "BatteryManager.EXTRA_TEMPERATURE in 0.1°C",
                createList("Single sensor location", "Pack-level not cell-level"),
                "±2°C",
                CapabilityLevel.OEM_ENHANCED,
                "Multi-point thermal sensing",
                createList("Thermal HAL access", "Additional NTC sensors"),
                "±0.5°C with location awareness",
                "Multiple thermal zone access for comprehensive monitoring",
                "Medium",
                "MEDIUM"
        ));
        
        entries.add(new CapabilityEntry(
                "CORE-004", "Core Monitoring", "Current Flow (mA)",
                CapabilityLevel.MODERATE,
                "BatteryManager.PROPERTY_CURRENT_NOW on supported devices",
                createList("Returns 0 on many devices", "Not mandated by CDD"),
                "±10% when available, N/A otherwise",
                CapabilityLevel.OEM_ENHANCED,
                "Guaranteed current sensing from fuel gauge",
                createList("Fuel gauge HAL binding", "Device certification requirement"),
                "±2%",
                "Ensure PROPERTY_CURRENT_NOW returns valid data on all SKUs",
                "Medium",
                "HIGH"
        ));
        
        entries.add(new CapabilityEntry(
                "CORE-005", "Core Monitoring", "Charge Status",
                CapabilityLevel.FULL,
                "BatteryManager.EXTRA_STATUS (charging/discharging/full)",
                createList(),
                "100% accurate",
                CapabilityLevel.FULL,
                "Same - no enhancement needed",
                createList(),
                "100%",
                "N/A",
                "None",
                "LOW"
        ));
        
        entries.add(new CapabilityEntry(
                "CORE-006", "Core Monitoring", "Charger Type",
                CapabilityLevel.HIGH,
                "BatteryManager.EXTRA_PLUGGED (USB, AC, Wireless)",
                createList("Cannot identify fast charge protocol"),
                "Type accurate, protocol unknown",
                CapabilityLevel.OEM_ENHANCED,
                "Full charging protocol identification",
                createList("Charging IC driver extension", "USB-PD stack access"),
                "Type + Protocol (QC/PD/VOOC)",
                "Expose charge_type from /sys/class/power_supply",
                "Medium",
                "MEDIUM"
        ));
        
        return entries;
    }
    
    private List<CapabilityEntry> buildBatteryHealthCapabilities() {
        List<CapabilityEntry> entries = new ArrayList<>();
        
        entries.add(new CapabilityEntry(
                "HLTH-001", "Battery Health", "Health Status",
                CapabilityLevel.HIGH,
                "BatteryManager.EXTRA_HEALTH (GOOD/OVERHEAT/DEAD/etc.)",
                createList("Coarse granularity - major states only"),
                "Categorical (no percentage)",
                CapabilityLevel.OEM_ENHANCED,
                "Exact SOH% from fuel gauge",
                createList("Fuel gauge HAL binding"),
                "SOH% with ±2% accuracy",
                "Read StateOfHealth register from fuel gauge IC",
                "Low",
                "HIGH"
        ));
        
        entries.add(new CapabilityEntry(
                "HLTH-002", "Battery Health", "Capacity Estimation (mAh)",
                CapabilityLevel.MODERATE,
                "Estimated from charge/discharge cycle analysis",
                createList("Requires full cycles", "5-15% error margin"),
                "±5-15%",
                CapabilityLevel.OEM_ENHANCED,
                "Direct FCC reading from fuel gauge",
                createList("Fuel gauge HAL binding"),
                "±2%",
                "Read FullChargeCapacity register from fuel gauge IC",
                "Low",
                "HIGH"
        ));
        
        entries.add(new CapabilityEntry(
                "HLTH-003", "Battery Health", "Cycle Count",
                CapabilityLevel.MODERATE,
                "Accumulated from observed partial cycles",
                createList("Cumulative estimation", "No baseline reference"),
                "±10-20%",
                CapabilityLevel.OEM_ENHANCED,
                "Direct cycle count from fuel gauge",
                createList("Fuel gauge HAL binding"),
                "Exact count",
                "Read CycleCount register from fuel gauge IC",
                "Low",
                "MEDIUM"
        ));
        
        entries.add(new CapabilityEntry(
                "HLTH-004", "Battery Health", "Design Capacity",
                CapabilityLevel.LOW,
                "Device database lookup or user input",
                createList("Not in public API", "Database may be outdated"),
                "±5% if in database",
                CapabilityLevel.OEM_ENHANCED,
                "Direct design capacity from fuel gauge",
                createList("Fuel gauge HAL binding or SystemProperty"),
                "Exact value",
                "Expose charge_full_design via API or SystemProperty",
                "Low",
                "MEDIUM"
        ));
        
        entries.add(new CapabilityEntry(
                "HLTH-005", "Battery Health", "Remaining Capacity",
                CapabilityLevel.MODERATE,
                "Calculated as (level% × estimated_capacity)",
                createList("Compound error from SOC and capacity estimates"),
                "±10-20%",
                CapabilityLevel.OEM_ENHANCED,
                "Direct RM reading from fuel gauge",
                createList("Fuel gauge HAL binding"),
                "±2%",
                "Read RemainingCapacity register from fuel gauge IC",
                "Low",
                "MEDIUM"
        ));
        
        return entries;
    }
    
    private List<CapabilityEntry> buildChargingCapabilities() {
        List<CapabilityEntry> entries = new ArrayList<>();
        
        entries.add(new CapabilityEntry(
                "CHRG-001", "Charging Intelligence", "Charging Session Tracking",
                CapabilityLevel.FULL,
                "Event-driven tracking via POWER_CONNECTED/DISCONNECTED",
                createList(),
                "100%",
                CapabilityLevel.FULL,
                "Same - no enhancement needed",
                createList(),
                "100%",
                "N/A",
                "None",
                "LOW"
        ));
        
        entries.add(new CapabilityEntry(
                "CHRG-002", "Charging Intelligence", "Charging Rate (%/hr)",
                CapabilityLevel.FULL,
                "Calculated from level change over time",
                createList("SOC non-linearity affects accuracy"),
                "±5% relative",
                CapabilityLevel.OEM_ENHANCED,
                "Direct current integration for accurate rate",
                createList("Reliable PROPERTY_CURRENT_NOW"),
                "±2%",
                "Ensure current measurement availability",
                "Medium",
                "MEDIUM"
        ));
        
        entries.add(new CapabilityEntry(
                "CHRG-003", "Charging Intelligence", "Charging Wattage",
                CapabilityLevel.LOW,
                "Estimated as V × I when current available",
                createList("Current often unavailable", "±20% error when available"),
                "±20% or N/A",
                CapabilityLevel.OEM_ENHANCED,
                "Direct power reading from USB-PD or charging IC",
                createList("USB-PD stack access", "Charging IC driver"),
                "±3%",
                "Expose negotiated power from USB-PD or proprietary charging",
                "Medium",
                "HIGH"
        ));
        
        entries.add(new CapabilityEntry(
                "CHRG-004", "Charging Intelligence", "Fast Charge Detection",
                CapabilityLevel.MODERATE,
                "Inferred from charging rate threshold (>15%/hr)",
                createList("Cannot identify specific protocol"),
                "Detection yes/no, protocol unknown",
                CapabilityLevel.OEM_ENHANCED,
                "Full protocol identification",
                createList("Charging IC driver extension"),
                "Protocol name and version",
                "Expose quick_charge_type or equivalent",
                "Medium",
                "HIGH"
        ));
        
        entries.add(new CapabilityEntry(
                "CHRG-005", "Charging Intelligence", "Charger Quality Analysis",
                CapabilityLevel.HIGH,
                "Voltage stability and temperature monitoring",
                createList("Observable effects only"),
                "Anomaly detection confidence",
                CapabilityLevel.OEM_ENHANCED,
                "Communication protocol analysis",
                createList("Charging IC protocol access"),
                "Charger certification status",
                "Expose charger authentication results",
                "High",
                "LOW"
        ));
        
        return entries;
    }
    
    private List<CapabilityEntry> buildThermalCapabilities() {
        List<CapabilityEntry> entries = new ArrayList<>();
        
        entries.add(new CapabilityEntry(
                "THRM-001", "Thermal Analysis", "Battery Temperature",
                CapabilityLevel.FULL,
                "Continuous monitoring via BatteryManager",
                createList("Single sensor point"),
                "±2°C",
                CapabilityLevel.OEM_ENHANCED,
                "Multi-sensor battery monitoring",
                createList("Additional battery sensors"),
                "±0.5°C with hotspot detection",
                "Expose multiple battery thermal zones",
                "Medium",
                "MEDIUM"
        ));
        
        entries.add(new CapabilityEntry(
                "THRM-002", "Thermal Analysis", "Thermal Event Detection",
                CapabilityLevel.FULL,
                "Threshold monitoring with hysteresis",
                createList("Reactive only, no prediction"),
                "Detection reliable",
                CapabilityLevel.OEM_ENHANCED,
                "Predictive thermal management",
                createList("Thermal framework integration"),
                "Prediction + Detection",
                "Access thermal_engine predictions",
                "High",
                "MEDIUM"
        ));
        
        entries.add(new CapabilityEntry(
                "THRM-003", "Thermal Analysis", "CPU/GPU Temperature",
                CapabilityLevel.NONE,
                "Cannot access without elevated permissions",
                createList("/sys/class/thermal blocked", "No public API"),
                "N/A",
                CapabilityLevel.OEM_ENHANCED,
                "Full thermal zone access",
                createList("Custom permission or HAL binding"),
                "All zones with ±1°C",
                "Grant access to thermal_zone*/temp",
                "Low",
                "HIGH"
        ));
        
        entries.add(new CapabilityEntry(
                "THRM-004", "Thermal Analysis", "Skin Temperature",
                CapabilityLevel.NONE,
                "No public API",
                createList("API does not exist"),
                "N/A",
                CapabilityLevel.OEM_ENHANCED,
                "Direct skin temperature sensor access",
                createList("Thermal HAL 2.0+ binding"),
                "±0.5°C",
                "Expose skin_temp thermal zone",
                "Low",
                "MEDIUM"
        ));
        
        entries.add(new CapabilityEntry(
                "THRM-005", "Thermal Analysis", "Thermal Throttling Status",
                CapabilityLevel.MODERATE,
                "ThermalManager.registerThermalStatusCallback (API 29+)",
                createList("Only status level, no temperatures"),
                "Status only (NONE to SHUTDOWN)",
                CapabilityLevel.OEM_ENHANCED,
                "Full throttling details",
                createList("Thermal framework integration"),
                "Status + temperatures + mitigation actions",
                "Expose thermal_engine decisions",
                "Medium",
                "MEDIUM"
        ));
        
        return entries;
    }
    
    private List<CapabilityEntry> buildDrainCapabilities() {
        List<CapabilityEntry> entries = new ArrayList<>();
        
        entries.add(new CapabilityEntry(
                "DRAN-001", "Drain Analysis", "Screen-On Drain Rate",
                CapabilityLevel.FULL,
                "Correlation of battery level with screen state",
                createList(),
                "±5%",
                CapabilityLevel.FULL,
                "Same - screen state reliable",
                createList(),
                "±5%",
                "N/A",
                "None",
                "LOW"
        ));
        
        entries.add(new CapabilityEntry(
                "DRAN-002", "Drain Analysis", "Idle/Standby Drain",
                CapabilityLevel.HIGH,
                "Measurement during screen-off periods",
                createList("Cannot distinguish app vs system wakeups"),
                "±10%",
                CapabilityLevel.OEM_ENHANCED,
                "Wakeup source attribution",
                createList("BATTERY_STATS permission"),
                "±5% with source identification",
                "Grant BATTERY_STATS permission",
                "Low",
                "HIGH"
        ));
        
        entries.add(new CapabilityEntry(
                "DRAN-003", "Drain Analysis", "Per-App Battery Usage",
                CapabilityLevel.NONE,
                "Cannot access - BATTERY_STATS is signature-level",
                createList("Fundamental Android permission restriction"),
                "N/A",
                CapabilityLevel.OEM_ENHANCED,
                "Full per-app attribution like Settings app",
                createList("BATTERY_STATS permission via OEM signing"),
                "Same as system Settings",
                "Pre-sign HexNil with platform key or grant privileged permission",
                "Low",
                "HIGH"
        ));
        
        entries.add(new CapabilityEntry(
                "DRAN-004", "Drain Analysis", "Wakelock Attribution",
                CapabilityLevel.NONE,
                "Cannot access PowerManagerService wakelock data",
                createList("Requires BATTERY_STATS or shell/root"),
                "N/A",
                CapabilityLevel.OEM_ENHANCED,
                "Full wakelock tracking per app",
                createList("BATTERY_STATS permission"),
                "Complete wakelock attribution",
                "Grant BATTERY_STATS permission",
                "Low",
                "HIGH"
        ));
        
        entries.add(new CapabilityEntry(
                "DRAN-005", "Drain Analysis", "Anomalous Drain Detection",
                CapabilityLevel.HIGH,
                "Statistical analysis of drain patterns",
                createList("Attribution limited to time, not causation"),
                "Detection reliable, cause unknown",
                CapabilityLevel.OEM_ENHANCED,
                "Anomaly detection with process attribution",
                createList("BATTERY_STATS permission"),
                "Detection + Exact cause",
                "Grant BATTERY_STATS permission",
                "Low",
                "MEDIUM"
        ));
        
        return entries;
    }
    
    private List<CapabilityEntry> buildCalibrationCapabilities() {
        List<CapabilityEntry> entries = new ArrayList<>();
        
        entries.add(new CapabilityEntry(
                "CALB-001", "Calibration", "SOC Accuracy",
                CapabilityLevel.HIGH,
                "Uses device-reported level%, assumes fuel gauge accuracy",
                createList("No independent verification possible"),
                "Same as device fuel gauge",
                CapabilityLevel.OEM_ENHANCED,
                "Cross-validation with fuel gauge internals",
                createList("Fuel gauge HAL binding"),
                "Verified accuracy",
                "Expose QMax and DOD% for SOC validation",
                "Medium",
                "MEDIUM"
        ));
        
        entries.add(new CapabilityEntry(
                "CALB-002", "Calibration", "Calibration Event Detection",
                CapabilityLevel.MODERATE,
                "Detects full charge (100%) and deep discharge events",
                createList("Requires user to perform full cycles"),
                "Event detection reliable",
                CapabilityLevel.OEM_ENHANCED,
                "Fuel gauge calibration status",
                createList("Fuel gauge HAL binding"),
                "Direct calibration status",
                "Expose calibration_needed flag from fuel gauge",
                "Low",
                "MEDIUM"
        ));
        
        entries.add(new CapabilityEntry(
                "CALB-003", "Calibration", "SOC Drift Detection",
                CapabilityLevel.MODERATE,
                "Discharge curve analysis over time",
                createList("Indirect detection", "Needs historical data"),
                "Drift detected, magnitude estimated",
                CapabilityLevel.OEM_ENHANCED,
                "Direct drift tracking from fuel gauge",
                createList("Fuel gauge HAL binding"),
                "Exact drift measurement",
                "Expose QMax changes over time",
                "Medium",
                "LOW"
        ));
        
        return entries;
    }
    
    private List<CapabilityEntry> buildAICapabilities() {
        List<CapabilityEntry> entries = new ArrayList<>();
        
        entries.add(new CapabilityEntry(
                "AI-001", "AI Intelligence", "Statistical Analysis",
                CapabilityLevel.FULL,
                "Trend detection, pattern recognition, statistical inference",
                createList(),
                "Deterministic calculations",
                CapabilityLevel.FULL,
                "Same - pure math on data",
                createList(),
                "Same",
                "N/A",
                "None",
                "LOW"
        ));
        
        entries.add(new CapabilityEntry(
                "AI-002", "AI Intelligence", "Root Cause Analysis",
                CapabilityLevel.HIGH,
                "AI-powered inference with anti-hallucination guardrails",
                createList("Limited by input data quality"),
                "Confidence-scored insights",
                CapabilityLevel.OEM_ENHANCED,
                "Enhanced with additional data sources",
                createList("Additional sensor access", "BATTERY_STATS"),
                "Higher confidence with more data",
                "Provide additional data sources",
                "Varies",
                "MEDIUM"
        ));
        
        entries.add(new CapabilityEntry(
                "AI-003", "AI Intelligence", "Predictive Battery Life",
                CapabilityLevel.MODERATE,
                "Extrapolation from historical drain rates",
                createList("Accuracy varies with usage consistency"),
                "±15-30%",
                CapabilityLevel.OEM_ENHANCED,
                "Hardware time-to-empty from fuel gauge",
                createList("Fuel gauge HAL binding"),
                "±5%",
                "Read TimeToEmpty register from fuel gauge",
                "Low",
                "HIGH"
        ));
        
        entries.add(new CapabilityEntry(
                "AI-004", "AI Intelligence", "Health Predictions",
                CapabilityLevel.MODERATE,
                "Degradation trend analysis",
                createList("Requires 90+ days for trend accuracy"),
                "Directional prediction",
                CapabilityLevel.OEM_ENHANCED,
                "Enhanced with direct SOH tracking",
                createList("Fuel gauge HAL binding"),
                "Quantitative prediction",
                "Expose historical SOH% readings",
                "Medium",
                "MEDIUM"
        ));
        
        return entries;
    }
    
    private List<CapabilityEntry> buildDataQualityCapabilities() {
        List<CapabilityEntry> entries = new ArrayList<>();
        
        entries.add(new CapabilityEntry(
                "DATA-001", "Data Quality", "Data Validation",
                CapabilityLevel.FULL,
                "Contract validation, range checking, duplicate detection",
                createList(),
                "100% validated data",
                CapabilityLevel.FULL,
                "Same - fully implemented",
                createList(),
                "100%",
                "N/A",
                "None",
                "LOW"
        ));
        
        entries.add(new CapabilityEntry(
                "DATA-002", "Data Quality", "Integrity Checking",
                CapabilityLevel.FULL,
                "Frozen data, gaps, anomalies, incomplete sessions",
                createList(),
                "Comprehensive checking",
                CapabilityLevel.FULL,
                "Same - fully implemented",
                createList(),
                "100%",
                "N/A",
                "None",
                "LOW"
        ));
        
        entries.add(new CapabilityEntry(
                "DATA-003", "Data Quality", "Data Retention",
                CapabilityLevel.FULL,
                "Configurable per-entity retention with automatic cleanup",
                createList(),
                "Reliable retention",
                CapabilityLevel.FULL,
                "Same - fully implemented",
                createList(),
                "100%",
                "N/A",
                "None",
                "LOW"
        ));
        
        entries.add(new CapabilityEntry(
                "DATA-004", "Data Quality", "Database Health",
                CapabilityLevel.FULL,
                "Size monitoring, performance tracking, corruption detection",
                createList(),
                "Comprehensive monitoring",
                CapabilityLevel.FULL,
                "Same - fully implemented",
                createList(),
                "100%",
                "N/A",
                "None",
                "LOW"
        ));
        
        return entries;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // UTILITY
    // ═══════════════════════════════════════════════════════════════════════════════
    
    private List<String> createList(String... items) {
        List<String> list = new ArrayList<>();
        for (String item : items) {
            list.add(item);
        }
        return list;
    }
    
    /**
     * Generate ASCII table representation of matrix.
     */
    public String generateMatrixTable(MatrixReport report) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("\n");
        sb.append("╔════════════════════════════════════════════════════════════════════════════════╗\n");
        sb.append("║                    HEXNIL CAPABILITY MATRIX                                    ║\n");
        sb.append("╠════════════════════════════════════════════════════════════════════════════════╣\n");
        sb.append(String.format("║  Device: %-68s ║\n", report.deviceModel));
        sb.append(String.format("║  Current Score: %.0f%%  │  OEM-Enhanced Score: %.0f%%                           ║\n", 
                report.getCurrentCapabilityScore(), report.getOEMCapabilityScore()));
        sb.append("╠════════════════════════════════════════════════════════════════════════════════╣\n");
        sb.append("║  LEGEND: ✅ Full  🟢 High  🟡 Moderate  🟠 Low  🔴 None  ⭐ OEM Enhanced      ║\n");
        sb.append("╚════════════════════════════════════════════════════════════════════════════════╝\n\n");
        
        for (Map.Entry<String, List<CapabilityEntry>> category : report.capabilitiesByCategory.entrySet()) {
            sb.append(String.format("┌─ %s ─────────────────────────────────────────────────────────\n", 
                    category.getKey().toUpperCase()));
            
            for (CapabilityEntry entry : category.getValue()) {
                sb.append(String.format("│ %s → %s  %s\n",
                        entry.currentLevel.icon,
                        entry.oemLevel.icon,
                        entry.capability));
            }
            sb.append("└────────────────────────────────────────────────────────────────────────\n\n");
        }
        
        // High Priority Integrations
        if (!report.highPriorityIntegrations.isEmpty()) {
            sb.append("★ HIGH PRIORITY OEM INTEGRATIONS:\n");
            for (CapabilityEntry entry : report.highPriorityIntegrations) {
                sb.append(String.format("  • %s (+%d%% uplift)\n", 
                        entry.capability, entry.getUpliftScore()));
            }
            sb.append("\n");
        }
        
        // Quick Wins
        if (!report.quickWins.isEmpty()) {
            sb.append("⚡ QUICK WINS (Low Effort, High Impact):\n");
            for (CapabilityEntry entry : report.quickWins) {
                sb.append(String.format("  • %s (+%d%% uplift, %s effort)\n", 
                        entry.capability, entry.getUpliftScore(), entry.integrationEffort));
            }
        }
        
        return sb.toString();
    }
}
