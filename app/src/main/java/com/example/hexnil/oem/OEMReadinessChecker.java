package com.example.hexnil.oem;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OEM Readiness Checker - Production Readiness Assessment
 * 
 * Evaluates HexNil's capabilities against OEM-grade requirements:
 * 
 * CATEGORIES:
 * 1. PRODUCTION_READY - Fully functional, no additional access needed
 * 2. NEEDS_MORE_DATA - Feature works but requires data accumulation
 * 3. NEEDS_FIRMWARE_ACCESS - Requires privileged/OEM access
 * 4. NOT_AVAILABLE - Cannot be implemented without root/system access
 * 
 * This assessment is HONEST about limitations.
 * No false claims. No overpromising.
 * 
 * TARGET: OEM Integration Partners, QA Teams, Certification Bodies
 */
public class OEMReadinessChecker {
    
    private static final String TAG = "OEMReadinessChecker";
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // READINESS LEVELS
    // ═══════════════════════════════════════════════════════════════════════════════
    
    public enum ReadinessLevel {
        PRODUCTION_READY("Production Ready", "✅", 
                "Fully functional with standard Android permissions"),
        
        NEEDS_MORE_DATA("Needs More Data", "📊", 
                "Feature functional but accuracy improves with data accumulation"),
        
        NEEDS_FIRMWARE_ACCESS("Needs Firmware Access", "🔧", 
                "Requires OEM/system-level access for full functionality"),
        
        LIMITED_BY_API("Limited by API", "⚠️", 
                "Restricted by Android API limitations, partial functionality"),
        
        NOT_AVAILABLE("Not Available", "❌", 
                "Cannot be implemented without root or privileged access");
        
        public final String label;
        public final String icon;
        public final String description;
        
        ReadinessLevel(String label, String icon, String description) {
            this.label = label;
            this.icon = icon;
            this.description = description;
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // FEATURE ASSESSMENT
    // ═══════════════════════════════════════════════════════════════════════════════
    
    public static class FeatureAssessment {
        public final String featureName;
        public final String category;
        public final ReadinessLevel readinessLevel;
        public final String currentCapability;
        public final String limitation;
        public final String oemEnhancement;
        public final int confidencePercent;
        public final List<String> evidence;
        
        public FeatureAssessment(String featureName, String category, ReadinessLevel level,
                                  String currentCapability, String limitation,
                                  String oemEnhancement, int confidencePercent,
                                  List<String> evidence) {
            this.featureName = featureName;
            this.category = category;
            this.readinessLevel = level;
            this.currentCapability = currentCapability;
            this.limitation = limitation;
            this.oemEnhancement = oemEnhancement;
            this.confidencePercent = confidencePercent;
            this.evidence = evidence != null ? evidence : new ArrayList<>();
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // READINESS REPORT
    // ═══════════════════════════════════════════════════════════════════════════════
    
    public static class ReadinessReport {
        public final long timestamp;
        public final String appVersion;
        public final int apiLevel;
        public final String deviceModel;
        public final String manufacturer;
        
        public final List<FeatureAssessment> productionReady;
        public final List<FeatureAssessment> needsMoreData;
        public final List<FeatureAssessment> needsFirmwareAccess;
        public final List<FeatureAssessment> limitedByApi;
        public final List<FeatureAssessment> notAvailable;
        
        public final int overallReadinessScore;
        public final String overallVerdict;
        public final List<String> keyStrengths;
        public final List<String> keyLimitations;
        public final List<String> oemRecommendations;
        
        public ReadinessReport(long timestamp, String appVersion, int apiLevel,
                                String deviceModel, String manufacturer,
                                List<FeatureAssessment> productionReady,
                                List<FeatureAssessment> needsMoreData,
                                List<FeatureAssessment> needsFirmwareAccess,
                                List<FeatureAssessment> limitedByApi,
                                List<FeatureAssessment> notAvailable,
                                int overallReadinessScore, String overallVerdict,
                                List<String> keyStrengths, List<String> keyLimitations,
                                List<String> oemRecommendations) {
            this.timestamp = timestamp;
            this.appVersion = appVersion;
            this.apiLevel = apiLevel;
            this.deviceModel = deviceModel;
            this.manufacturer = manufacturer;
            this.productionReady = productionReady;
            this.needsMoreData = needsMoreData;
            this.needsFirmwareAccess = needsFirmwareAccess;
            this.limitedByApi = limitedByApi;
            this.notAvailable = notAvailable;
            this.overallReadinessScore = overallReadinessScore;
            this.overallVerdict = overallVerdict;
            this.keyStrengths = keyStrengths;
            this.keyLimitations = keyLimitations;
            this.oemRecommendations = oemRecommendations;
        }
        
        public int getTotalFeatures() {
            return productionReady.size() + needsMoreData.size() + 
                   needsFirmwareAccess.size() + limitedByApi.size() + notAvailable.size();
        }
        
        public float getProductionReadyPercentage() {
            int total = getTotalFeatures();
            if (total == 0) return 0;
            return (productionReady.size() + needsMoreData.size()) * 100f / total;
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // GENERATE READINESS REPORT
    // ═══════════════════════════════════════════════════════════════════════════════
    
    private final Context context;
    
    public OEMReadinessChecker(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }
    
    /**
     * Generate comprehensive OEM readiness report.
     */
    public ReadinessReport generateReadinessReport() {
        List<FeatureAssessment> productionReady = new ArrayList<>();
        List<FeatureAssessment> needsMoreData = new ArrayList<>();
        List<FeatureAssessment> needsFirmwareAccess = new ArrayList<>();
        List<FeatureAssessment> limitedByApi = new ArrayList<>();
        List<FeatureAssessment> notAvailable = new ArrayList<>();
        
        // Assess all features
        List<FeatureAssessment> allAssessments = assessAllFeatures();
        
        // Categorize
        for (FeatureAssessment assessment : allAssessments) {
            switch (assessment.readinessLevel) {
                case PRODUCTION_READY:
                    productionReady.add(assessment);
                    break;
                case NEEDS_MORE_DATA:
                    needsMoreData.add(assessment);
                    break;
                case NEEDS_FIRMWARE_ACCESS:
                    needsFirmwareAccess.add(assessment);
                    break;
                case LIMITED_BY_API:
                    limitedByApi.add(assessment);
                    break;
                case NOT_AVAILABLE:
                    notAvailable.add(assessment);
                    break;
            }
        }
        
        // Calculate overall score
        int overallScore = calculateOverallScore(productionReady.size(), needsMoreData.size(),
                needsFirmwareAccess.size(), limitedByApi.size(), notAvailable.size());
        
        // Generate verdict
        String verdict = generateVerdict(overallScore, productionReady.size(), 
                needsFirmwareAccess.size(), notAvailable.size());
        
        // Key strengths and limitations
        List<String> strengths = generateKeyStrengths(productionReady);
        List<String> limitations = generateKeyLimitations(notAvailable, limitedByApi);
        List<String> recommendations = generateOEMRecommendations(needsFirmwareAccess);
        
        String appVersion = getAppVersion();
        
        return new ReadinessReport(
                System.currentTimeMillis(),
                appVersion,
                Build.VERSION.SDK_INT,
                Build.MODEL,
                Build.MANUFACTURER,
                productionReady,
                needsMoreData,
                needsFirmwareAccess,
                limitedByApi,
                notAvailable,
                overallScore,
                verdict,
                strengths,
                limitations,
                recommendations
        );
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // FEATURE ASSESSMENTS
    // ═══════════════════════════════════════════════════════════════════════════════
    
    private List<FeatureAssessment> assessAllFeatures() {
        List<FeatureAssessment> assessments = new ArrayList<>();
        
        // ─────────────────────────────────────────────────────────────────────────
        // BATTERY SNAPSHOT SYSTEM
        // ─────────────────────────────────────────────────────────────────────────
        
        assessments.add(new FeatureAssessment(
                "Real-time Battery Level Monitoring",
                "Core Monitoring",
                ReadinessLevel.PRODUCTION_READY,
                "Captures battery level (0-100%) every configurable interval via BatteryManager",
                "None - fully supported by public Android API",
                "N/A - already optimal",
                100,
                createEvidence("BatteryManager.EXTRA_LEVEL", "ACTION_BATTERY_CHANGED broadcast")
        ));
        
        assessments.add(new FeatureAssessment(
                "Battery Voltage Monitoring",
                "Core Monitoring",
                ReadinessLevel.PRODUCTION_READY,
                "Captures voltage in millivolts via BatteryManager",
                "Resolution varies by device (typically 10-50mV steps)",
                "Firmware can provide higher resolution voltage data",
                95,
                createEvidence("BatteryManager.EXTRA_VOLTAGE")
        ));
        
        assessments.add(new FeatureAssessment(
                "Battery Temperature Monitoring",
                "Core Monitoring",
                ReadinessLevel.PRODUCTION_READY,
                "Captures temperature in tenths of degree Celsius",
                "Single sensor location (battery pack), not cell-level",
                "OEM can provide multi-point thermal sensors",
                95,
                createEvidence("BatteryManager.EXTRA_TEMPERATURE")
        ));
        
        assessments.add(new FeatureAssessment(
                "Charging Status Detection",
                "Core Monitoring",
                ReadinessLevel.PRODUCTION_READY,
                "Detects USB, AC, Wireless charging via plug type",
                "Cannot differentiate charging protocols (QC, PD, etc.)",
                "Firmware can expose charging IC protocol details",
                90,
                createEvidence("BatteryManager.EXTRA_PLUGGED", "EXTRA_STATUS")
        ));
        
        assessments.add(new FeatureAssessment(
                "Current Flow Measurement",
                "Core Monitoring",
                ReadinessLevel.LIMITED_BY_API,
                "Available on API 21+ via BatteryManager.PROPERTY_CURRENT_NOW",
                "Not all devices expose this; returns 0 or MIN_VALUE on many devices",
                "OEM can guarantee current measurement availability",
                70,
                createEvidence("BatteryManager.PROPERTY_CURRENT_NOW", "Device-dependent availability")
        ));
        
        assessments.add(new FeatureAssessment(
                "Charge Counter (Coulomb Counting)",
                "Core Monitoring",
                ReadinessLevel.LIMITED_BY_API,
                "Available via PROPERTY_CHARGE_COUNTER on supported devices",
                "Many devices return 0 or unsupported; resets on reboot",
                "OEM can provide persistent coulomb counter from fuel gauge IC",
                60,
                createEvidence("BatteryManager.PROPERTY_CHARGE_COUNTER", "Hardware fuel gauge dependent")
        ));
        
        // ─────────────────────────────────────────────────────────────────────────
        // BATTERY HEALTH & DEGRADATION
        // ─────────────────────────────────────────────────────────────────────────
        
        assessments.add(new FeatureAssessment(
                "Battery Health Status",
                "Health Analysis",
                ReadinessLevel.PRODUCTION_READY,
                "Reports GOOD, OVERHEAT, DEAD, OVER_VOLTAGE, COLD, UNKNOWN",
                "Coarse granularity - only major health states",
                "Firmware provides SOH% from fuel gauge chip",
                85,
                createEvidence("BatteryManager.EXTRA_HEALTH")
        ));
        
        assessments.add(new FeatureAssessment(
                "Estimated Battery Capacity",
                "Health Analysis",
                ReadinessLevel.NEEDS_MORE_DATA,
                "Estimates capacity via charge/discharge cycle analysis",
                "Requires multiple full cycles (0-100%) for accuracy; ~5-10% margin",
                "OEM fuel gauge provides direct mAh reading with <2% accuracy",
                75,
                createEvidence("Derived from level changes over current integration")
        ));
        
        assessments.add(new FeatureAssessment(
                "Cycle Count Tracking",
                "Health Analysis",
                ReadinessLevel.NEEDS_MORE_DATA,
                "Estimates equivalent full cycles from partial charges",
                "Cumulative estimation; loses accuracy without baseline reference",
                "Fuel gauge IC tracks actual cycle count persistently",
                70,
                createEvidence("Calculated from sum of partial cycles")
        ));
        
        assessments.add(new FeatureAssessment(
                "State of Health (SOH) Percentage",
                "Health Analysis",
                ReadinessLevel.NEEDS_FIRMWARE_ACCESS,
                "Estimated from capacity degradation over time",
                "Indirect estimation; no direct access to fuel gauge SOH register",
                "OEM fuel gauge (e.g., TI BQ27xxx) provides direct SOH%",
                65,
                createEvidence("Estimated via (current_capacity / design_capacity) * 100")
        ));
        
        assessments.add(new FeatureAssessment(
                "Full Charge Capacity (FCC)",
                "Health Analysis",
                ReadinessLevel.NEEDS_FIRMWARE_ACCESS,
                "Estimated from full charge cycles",
                "Requires rare 0-100% charge cycles for update",
                "Fuel gauge provides real-time FCC in mAh",
                55,
                createEvidence("Estimated from coulomb counting during full charges")
        ));
        
        assessments.add(new FeatureAssessment(
                "Remaining Capacity (RM)",
                "Health Analysis",
                ReadinessLevel.LIMITED_BY_API,
                "Calculated as (level% × estimated_capacity)",
                "Depends on SOC accuracy; cumulative error",
                "Fuel gauge provides direct RM in mAh",
                70,
                createEvidence("Derived calculation")
        ));
        
        // ─────────────────────────────────────────────────────────────────────────
        // CHARGING ANALYSIS
        // ─────────────────────────────────────────────────────────────────────────
        
        assessments.add(new FeatureAssessment(
                "Charging Session Tracking",
                "Charging Analysis",
                ReadinessLevel.PRODUCTION_READY,
                "Tracks start/end time, level gain, duration, average rate",
                "None - event-driven tracking works reliably",
                "N/A",
                100,
                createEvidence("ACTION_POWER_CONNECTED/DISCONNECTED broadcasts")
        ));
        
        assessments.add(new FeatureAssessment(
                "Charging Rate Calculation",
                "Charging Analysis",
                ReadinessLevel.PRODUCTION_READY,
                "Calculates %/hour and mAh/hour from level changes",
                "Indirect calculation; affected by SOC non-linearity",
                "OEM can provide direct wattage from charging IC",
                85,
                createEvidence("Derived from Δlevel/Δtime")
        ));
        
        assessments.add(new FeatureAssessment(
                "Fast Charging Detection",
                "Charging Analysis",
                ReadinessLevel.LIMITED_BY_API,
                "Inferred from charging rate exceeding threshold (e.g., >15%/hr)",
                "Cannot identify protocol (QC2/3/4, PD, VOOC, etc.)",
                "Charging IC provides protocol identification",
                60,
                createEvidence("Threshold-based inference only")
        ));
        
        assessments.add(new FeatureAssessment(
                "Charging Wattage",
                "Charging Analysis",
                ReadinessLevel.NEEDS_FIRMWARE_ACCESS,
                "Estimated as V × I when current available",
                "Current often unavailable; estimated values have ±20% error",
                "OEM can read direct wattage from USB-PD or proprietary IC",
                50,
                createEvidence("Calculated when PROPERTY_CURRENT_NOW available")
        ));
        
        assessments.add(new FeatureAssessment(
                "Charger Safety Analysis",
                "Charging Analysis",
                ReadinessLevel.PRODUCTION_READY,
                "Monitors voltage stability, temperature during charging",
                "Limited to observable effects, not charger internals",
                "OEM can monitor charger communication protocols",
                80,
                createEvidence("Anomaly detection on voltage/temperature patterns")
        ));
        
        // ─────────────────────────────────────────────────────────────────────────
        // THERMAL MONITORING
        // ─────────────────────────────────────────────────────────────────────────
        
        assessments.add(new FeatureAssessment(
                "Battery Temperature Tracking",
                "Thermal Analysis",
                ReadinessLevel.PRODUCTION_READY,
                "Continuous temperature monitoring with event detection",
                "Single sensor point; battery pack temperature only",
                "OEM has access to CPU, GPU, skin, ambient sensors",
                90,
                createEvidence("BatteryManager.EXTRA_TEMPERATURE")
        ));
        
        assessments.add(new FeatureAssessment(
                "Thermal Event Detection",
                "Thermal Analysis",
                ReadinessLevel.PRODUCTION_READY,
                "Detects overheating (>40°C), critical (>45°C) events",
                "Reactive detection only; cannot predict thermal throttling",
                "OEM thermal framework provides proactive throttling data",
                85,
                createEvidence("Threshold monitoring and trend analysis")
        ));
        
        assessments.add(new FeatureAssessment(
                "CPU/GPU Thermal Zones",
                "Thermal Analysis",
                ReadinessLevel.NOT_AVAILABLE,
                "Not accessible without system permissions",
                "Requires READ_THERMAL_ZONES or root access",
                "OEM thermal HAL provides all zone temperatures",
                0,
                createEvidence("/sys/class/thermal requires elevated permissions")
        ));
        
        assessments.add(new FeatureAssessment(
                "Skin Temperature",
                "Thermal Analysis",
                ReadinessLevel.NOT_AVAILABLE,
                "Not accessible to third-party apps",
                "No public API for skin temperature sensors",
                "OEM HAL provides skin temperature for comfort estimation",
                0,
                createEvidence("No public Android API")
        ));
        
        // ─────────────────────────────────────────────────────────────────────────
        // DRAIN ANALYSIS
        // ─────────────────────────────────────────────────────────────────────────
        
        assessments.add(new FeatureAssessment(
                "Screen-On Drain Rate",
                "Drain Analysis",
                ReadinessLevel.PRODUCTION_READY,
                "Correlates battery drain with screen state",
                "None - screen state reliably detectable",
                "N/A",
                95,
                createEvidence("ACTION_SCREEN_ON/OFF + battery level correlation")
        ));
        
        assessments.add(new FeatureAssessment(
                "Idle/Standby Drain Rate",
                "Drain Analysis",
                ReadinessLevel.PRODUCTION_READY,
                "Measures drain during screen-off periods",
                "Cannot distinguish app wakeups from system wakeups",
                "OEM has detailed wakelock attribution",
                85,
                createEvidence("Screen-off battery delta over time")
        ));
        
        assessments.add(new FeatureAssessment(
                "Per-App Battery Usage",
                "Drain Analysis",
                ReadinessLevel.NOT_AVAILABLE,
                "Cannot access per-app battery statistics",
                "BATTERY_STATS permission is signature-level (system apps only)",
                "OEM system service provides UsageStatsManager.queryBatteryStats",
                0,
                createEvidence("Manifest.permission.BATTERY_STATS is signature|privileged")
        ));
        
        assessments.add(new FeatureAssessment(
                "Wakelock Attribution",
                "Drain Analysis",
                ReadinessLevel.NOT_AVAILABLE,
                "Cannot identify which apps hold wakelocks",
                "Requires BATTERY_STATS or dumpsys access",
                "OEM can access PowerManagerService wakelock tracking",
                0,
                createEvidence("No public API for wakelock attribution")
        ));
        
        assessments.add(new FeatureAssessment(
                "Anomalous Drain Detection",
                "Drain Analysis",
                ReadinessLevel.PRODUCTION_READY,
                "Detects abnormal drain patterns via statistical analysis",
                "Attribution limited to time correlation, not causation",
                "OEM can pinpoint exact process causing drain",
                80,
                createEvidence("Baseline comparison and anomaly algorithms")
        ));
        
        // ─────────────────────────────────────────────────────────────────────────
        // CALIBRATION & SOC
        // ─────────────────────────────────────────────────────────────────────────
        
        assessments.add(new FeatureAssessment(
                "SOC Estimation",
                "Calibration",
                ReadinessLevel.PRODUCTION_READY,
                "Uses reported level% from BatteryManager",
                "Relies on device's fuel gauge accuracy; no independent verification",
                "OEM can expose raw fuel gauge data for cross-validation",
                90,
                createEvidence("BatteryManager.EXTRA_LEVEL as primary SOC source")
        ));
        
        assessments.add(new FeatureAssessment(
                "Calibration Event Detection",
                "Calibration",
                ReadinessLevel.NEEDS_MORE_DATA,
                "Detects full charge (100%) and full discharge events",
                "Requires user to actually perform full cycles",
                "Fuel gauge provides calibration status register",
                70,
                createEvidence("Full cycle detection for recalibration prompts")
        ));
        
        assessments.add(new FeatureAssessment(
                "SOC Drift Detection",
                "Calibration",
                ReadinessLevel.NEEDS_MORE_DATA,
                "Identifies SOC inaccuracy via discharge curve analysis",
                "Requires historical data; indirect detection",
                "Fuel gauge QMax/DOD tracking provides direct drift data",
                65,
                createEvidence("Discharge curve shape analysis over time")
        ));
        
        // ─────────────────────────────────────────────────────────────────────────
        // AI ANALYSIS
        // ─────────────────────────────────────────────────────────────────────────
        
        assessments.add(new FeatureAssessment(
                "Deterministic Battery Analysis",
                "AI Analysis",
                ReadinessLevel.PRODUCTION_READY,
                "Statistical analysis, trend detection, pattern recognition",
                "None - pure math on available data",
                "N/A",
                100,
                createEvidence("Statistical algorithms on captured data")
        ));
        
        assessments.add(new FeatureAssessment(
                "AI-Powered Insights",
                "AI Analysis",
                ReadinessLevel.NEEDS_MORE_DATA,
                "Root cause analysis, predictive insights with confidence scores",
                "Accuracy improves with 7+ days of data; limited by input data quality",
                "OEM data enrichment improves AI accuracy significantly",
                75,
                createEvidence("AI inference engine with anti-hallucination guardrails")
        ));
        
        assessments.add(new FeatureAssessment(
                "Predictive Battery Life",
                "AI Analysis",
                ReadinessLevel.NEEDS_MORE_DATA,
                "Estimates time-to-empty based on usage patterns",
                "Prediction accuracy varies with usage consistency",
                "OEM fuel gauge provides hardware time-to-empty estimate",
                70,
                createEvidence("Historical drain rate extrapolation")
        ));
        
        // ─────────────────────────────────────────────────────────────────────────
        // DATA INTEGRITY
        // ─────────────────────────────────────────────────────────────────────────
        
        assessments.add(new FeatureAssessment(
                "Data Validation & Integrity",
                "Data Quality",
                ReadinessLevel.PRODUCTION_READY,
                "Contract validation, frozen data detection, gap detection",
                "None - fully implemented validation pipeline",
                "N/A",
                100,
                createEvidence("EntityContracts, DataIntegrityChecker, ValidationDao")
        ));
        
        assessments.add(new FeatureAssessment(
                "Data Retention Management",
                "Data Quality",
                ReadinessLevel.PRODUCTION_READY,
                "Configurable retention policies, automatic cleanup",
                "None - fully implemented retention system",
                "N/A",
                100,
                createEvidence("RetentionManager with per-entity policies")
        ));
        
        assessments.add(new FeatureAssessment(
                "Database Health Monitoring",
                "Data Quality",
                ReadinessLevel.PRODUCTION_READY,
                "Size tracking, performance monitoring, integrity checks",
                "None - comprehensive monitoring",
                "N/A",
                100,
                createEvidence("DatabaseHealthMonitor")
        ));
        
        return assessments;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // SCORING & VERDICT
    // ═══════════════════════════════════════════════════════════════════════════════
    
    private int calculateOverallScore(int productionReady, int needsData, 
                                       int needsFirmware, int limitedApi, int notAvailable) {
        // Weighted scoring
        // Production Ready: 100 points each
        // Needs Data: 80 points each (will get to 100 with usage)
        // Limited by API: 50 points each
        // Needs Firmware: 30 points each
        // Not Available: 0 points
        
        int total = productionReady + needsData + needsFirmware + limitedApi + notAvailable;
        if (total == 0) return 0;
        
        int score = (productionReady * 100 + needsData * 80 + limitedApi * 50 + 
                     needsFirmware * 30 + notAvailable * 0) / total;
        
        return Math.min(100, Math.max(0, score));
    }
    
    private String generateVerdict(int score, int productionReady, 
                                    int needsFirmware, int notAvailable) {
        StringBuilder verdict = new StringBuilder();
        
        if (score >= 80) {
            verdict.append("READY FOR OEM INTEGRATION - ");
            verdict.append("HexNil provides comprehensive battery intelligence using ");
            verdict.append("publicly available Android APIs. ");
        } else if (score >= 60) {
            verdict.append("PARTIALLY READY - ");
            verdict.append("Core functionality is production-ready, but advanced ");
            verdict.append("features require OEM partnership for optimal accuracy. ");
        } else {
            verdict.append("REQUIRES OEM PARTNERSHIP - ");
            verdict.append("Significant features need firmware-level access. ");
        }
        
        verdict.append(String.format("%d features are production-ready. ", productionReady));
        
        if (needsFirmware > 0) {
            verdict.append(String.format("%d features would benefit from OEM access. ", needsFirmware));
        }
        
        if (notAvailable > 0) {
            verdict.append(String.format("%d features require privileged access. ", notAvailable));
        }
        
        return verdict.toString();
    }
    
    private List<String> generateKeyStrengths(List<FeatureAssessment> productionReady) {
        List<String> strengths = new ArrayList<>();
        
        strengths.add("Complete battery monitoring with no special permissions required");
        strengths.add("Foreground service architecture ensures continuous data collection");
        strengths.add("OEM-grade data validation prevents garbage-in-garbage-out");
        strengths.add("Anti-hallucination AI with confidence scoring");
        strengths.add("Comprehensive charging session and thermal event tracking");
        strengths.add("Works on any Android device API 26+ without modification");
        
        return strengths;
    }
    
    private List<String> generateKeyLimitations(List<FeatureAssessment> notAvailable,
                                                 List<FeatureAssessment> limitedApi) {
        List<String> limitations = new ArrayList<>();
        
        limitations.add("NO ROOT ACCESS: Cannot access /sys/class/power_supply directly");
        limitations.add("NO BATTERY_STATS: Cannot get per-app battery usage attribution");
        limitations.add("NO WAKELOCK DATA: Cannot identify which apps prevent sleep");
        limitations.add("NO THERMAL ZONES: Cannot read CPU/GPU/skin temperatures");
        limitations.add("CURRENT FLOW: PROPERTY_CURRENT_NOW returns 0 on many devices");
        limitations.add("CHARGE COUNTER: Not available or resets on many devices");
        limitations.add("CHARGING PROTOCOL: Cannot identify QC/PD/VOOC/etc.");
        limitations.add("FUEL GAUGE: No direct access to SOH, FCC, RM registers");
        
        return limitations;
    }
    
    private List<String> generateOEMRecommendations(List<FeatureAssessment> needsFirmware) {
        List<String> recommendations = new ArrayList<>();
        
        recommendations.add("Provide HAL binding for fuel gauge IC (TI BQ27xxx, Maxim, etc.)");
        recommendations.add("Expose charging IC protocol information (PD version, QC level)");
        recommendations.add("Grant access to thermal zone data via custom permission");
        recommendations.add("Provide wakelock attribution API for battery optimization");
        recommendations.add("Share SOH%, FCC, cycle count from fuel gauge registers");
        recommendations.add("Enable current measurement guarantee across all device SKUs");
        
        return recommendations;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // UTILITY METHODS
    // ═══════════════════════════════════════════════════════════════════════════════
    
    private List<String> createEvidence(String... items) {
        List<String> evidence = new ArrayList<>();
        for (String item : items) {
            evidence.add(item);
        }
        return evidence;
    }
    
    private String getAppVersion() {
        try {
            return context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "unknown";
        }
    }
}
