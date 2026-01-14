package com.example.hexnil.oem;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.BatteryManager;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Limitations Registry - Comprehensive Documentation of HexNil Limitations
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * HONEST ASSESSMENT OF WHAT WE CANNOT DO
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * This class documents ALL limitations of HexNil when running as a standard
 * Android application without:
 * - Root access
 * - System signature
 * - OEM/privileged permissions
 * - BATTERY_STATS permission
 * 
 * Purpose:
 * 1. Transparency for users about data accuracy
 * 2. Guide OEM partners on value-adds
 * 3. Set realistic expectations for certification
 * 4. Document workarounds and their trade-offs
 */
public class LimitationsRegistry {
    
    private static final String TAG = "LimitationsRegistry";
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // LIMITATION CATEGORIES
    // ═══════════════════════════════════════════════════════════════════════════════
    
    public enum LimitationCategory {
        PERMISSION_BLOCKED("Permission Blocked",
                "Requires signature/privileged/root permissions not available to 3rd party apps"),
        
        API_UNAVAILABLE("API Unavailable",
                "No public Android API exists for this functionality"),
        
        DEVICE_DEPENDENT("Device Dependent",
                "API exists but implementation varies significantly by OEM"),
        
        ACCURACY_LIMITED("Accuracy Limited",
                "Data available but accuracy is inherently limited by indirect measurement"),
        
        DATA_DEPENDENT("Data Dependent",
                "Feature works but requires data accumulation for optimal accuracy");
        
        public final String label;
        public final String description;
        
        LimitationCategory(String label, String description) {
            this.label = label;
            this.description = description;
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // LIMITATION MODEL
    // ═══════════════════════════════════════════════════════════════════════════════
    
    public static class Limitation {
        public final String id;
        public final String title;
        public final LimitationCategory category;
        public final String description;
        public final String impact;
        public final String workaround;
        public final String oemSolution;
        public final String technicalDetails;
        public final boolean affectsAccuracy;
        public final boolean affectsFunctionality;
        public final int severityScore; // 1-10, 10 = most severe
        
        public Limitation(String id, String title, LimitationCategory category,
                          String description, String impact, String workaround,
                          String oemSolution, String technicalDetails,
                          boolean affectsAccuracy, boolean affectsFunctionality,
                          int severityScore) {
            this.id = id;
            this.title = title;
            this.category = category;
            this.description = description;
            this.impact = impact;
            this.workaround = workaround;
            this.oemSolution = oemSolution;
            this.technicalDetails = technicalDetails;
            this.affectsAccuracy = affectsAccuracy;
            this.affectsFunctionality = affectsFunctionality;
            this.severityScore = Math.min(10, Math.max(1, severityScore));
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // REGISTRY
    // ═══════════════════════════════════════════════════════════════════════════════
    
    private final Context context;
    private final List<Limitation> allLimitations;
    
    public LimitationsRegistry(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.allLimitations = buildLimitationsRegistry();
    }
    
    /**
     * Get all registered limitations.
     */
    public List<Limitation> getAllLimitations() {
        return new ArrayList<>(allLimitations);
    }
    
    /**
     * Get limitations by category.
     */
    public List<Limitation> getLimitationsByCategory(LimitationCategory category) {
        List<Limitation> filtered = new ArrayList<>();
        for (Limitation lim : allLimitations) {
            if (lim.category == category) {
                filtered.add(lim);
            }
        }
        return filtered;
    }
    
    /**
     * Get limitations that affect accuracy.
     */
    public List<Limitation> getAccuracyLimitations() {
        List<Limitation> filtered = new ArrayList<>();
        for (Limitation lim : allLimitations) {
            if (lim.affectsAccuracy) {
                filtered.add(lim);
            }
        }
        return filtered;
    }
    
    /**
     * Get limitations that affect functionality.
     */
    public List<Limitation> getFunctionalityLimitations() {
        List<Limitation> filtered = new ArrayList<>();
        for (Limitation lim : allLimitations) {
            if (lim.affectsFunctionality) {
                filtered.add(lim);
            }
        }
        return filtered;
    }
    
    /**
     * Get total severity score (sum of all limitation severities).
     */
    public int getTotalSeverityScore() {
        int total = 0;
        for (Limitation lim : allLimitations) {
            total += lim.severityScore;
        }
        return total;
    }
    
    /**
     * Get severity breakdown by category.
     */
    public java.util.Map<LimitationCategory, Integer> getSeverityByCategory() {
        java.util.Map<LimitationCategory, Integer> map = new java.util.EnumMap<>(LimitationCategory.class);
        for (LimitationCategory cat : LimitationCategory.values()) {
            map.put(cat, 0);
        }
        for (Limitation lim : allLimitations) {
            int current = map.get(lim.category);
            map.put(lim.category, current + lim.severityScore);
        }
        return map;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // BUILD REGISTRY
    // ═══════════════════════════════════════════════════════════════════════════════
    
    private List<Limitation> buildLimitationsRegistry() {
        List<Limitation> limitations = new ArrayList<>();
        
        // ─────────────────────────────────────────────────────────────────────────
        // PERMISSION-BLOCKED LIMITATIONS
        // ─────────────────────────────────────────────────────────────────────────
        
        limitations.add(new Limitation(
                "LIM-001",
                "No Per-App Battery Usage",
                LimitationCategory.PERMISSION_BLOCKED,
                "BATTERY_STATS permission is signature|privileged level. Cannot access " +
                "UsageStatsManager.queryBatteryStats() or BatteryStatsHelper.",
                "Cannot show which apps consume the most battery. Users must rely on " +
                "system Settings app for this information.",
                "Correlate battery drain with app foreground time from UsageStatsManager " +
                "(requires PACKAGE_USAGE_STATS which user can grant in Settings).",
                "OEM can grant BATTERY_STATS permission via system signature or " +
                "pre-install HexNil as privileged app.",
                "Permission: android.permission.BATTERY_STATS\n" +
                "Protection Level: signature|privileged|development\n" +
                "Defined in: frameworks/base/core/res/AndroidManifest.xml",
                true, true, 9
        ));
        
        limitations.add(new Limitation(
                "LIM-002",
                "No Wakelock Attribution",
                LimitationCategory.PERMISSION_BLOCKED,
                "Cannot access which apps or processes hold wakelocks preventing sleep. " +
                "PowerManagerService wakelock data requires BATTERY_STATS.",
                "Cannot identify apps causing excessive standby drain. Wakelock " +
                "abuse is a leading cause of battery drain.",
                "Detect high idle drain as anomaly; suggest user check " +
                "'Battery optimization' settings.",
                "OEM can provide wakelock attribution via custom API or grant " +
                "BATTERY_STATS permission.",
                "WakeLock tracking in PowerManagerService requires:\n" +
                "- BATTERY_STATS for queryTimedWakeLocks\n" +
                "- DEVICE_POWER for immediate stats\n" +
                "- Or dumpsys power which needs shell/root",
                true, true, 8
        ));
        
        limitations.add(new Limitation(
                "LIM-003",
                "No Thermal Zone Access",
                LimitationCategory.PERMISSION_BLOCKED,
                "Cannot read /sys/class/thermal/* or ThermalManager zone temperatures. " +
                "These require elevated permissions or SELinux context.",
                "Only battery temperature available. Cannot monitor CPU, GPU, skin, " +
                "or ambient temperatures for comprehensive thermal analysis.",
                "Use battery temperature as proxy; implement heuristics based on " +
                "known usage patterns (gaming = high CPU = indirect correlation).",
                "OEM can expose thermal zones via custom API or bind HexNil to " +
                "Thermal HAL callbacks.",
                "/sys/class/thermal/thermal_zone*/temp requires:\n" +
                "- shell/root user OR\n" +
                "- SELinux context with sysfs_thermal access\n" +
                "ThermalManager.registerThermalStatusCallback (API 29+) gives:\n" +
                "- THERMAL_STATUS_NONE to THERMAL_STATUS_SHUTDOWN\n" +
                "- But NOT actual temperatures",
                true, true, 7
        ));
        
        limitations.add(new Limitation(
                "LIM-004",
                "No Direct Fuel Gauge Access",
                LimitationCategory.PERMISSION_BLOCKED,
                "Cannot read fuel gauge IC registers (SOH, FCC, RM, cycle count, " +
                "QMax, DOD). Requires kernel driver access or I2C.",
                "All capacity and health metrics are ESTIMATES derived from " +
                "observable data, not direct readings from the fuel gauge chip.",
                "Implement sophisticated estimation algorithms using charge/discharge " +
                "cycles, voltage curves, and coulomb counting when available.",
                "OEM can expose fuel gauge data via HAL binding or custom " +
                "BatteryService extension.",
                "Fuel gauge ICs (TI BQ27xxx, Maxim MAX17xxx, etc.) provide:\n" +
                "- StateOfHealth (SOH%)\n" +
                "- FullChargeCapacity (FCC mAh)\n" +
                "- RemainingCapacity (RM mAh)\n" +
                "- CycleCount\n" +
                "- TimeToEmpty/TimeToFull\n" +
                "Access requires: /sys/class/power_supply/*/uevent (root)",
                true, false, 8
        ));
        
        // ─────────────────────────────────────────────────────────────────────────
        // API-UNAVAILABLE LIMITATIONS
        // ─────────────────────────────────────────────────────────────────────────
        
        limitations.add(new Limitation(
                "LIM-005",
                "No Charging Protocol Identification",
                LimitationCategory.API_UNAVAILABLE,
                "No public API to identify charging protocol (Quick Charge 2/3/4/5, " +
                "USB-PD, VOOC, Warp, SuperCharge, etc.).",
                "Cannot tell user which fast charging standard is active or if " +
                "their charger is using optimal protocol.",
                "Classify as 'Fast Charging' based on charging rate exceeding " +
                "threshold (>15%/hr or >18W estimated).",
                "OEM can expose charging IC protocol information via custom API " +
                "or SystemProperties.",
                "Charging protocol info typically in:\n" +
                "- /sys/class/power_supply/*/charge_type\n" +
                "- /sys/class/power_supply/*/quick_charge_type\n" +
                "- Proprietary paths vary by OEM/SoC vendor\n" +
                "USB-PD info from USB controller driver",
                false, true, 5
        ));
        
        limitations.add(new Limitation(
                "LIM-006",
                "No Skin Temperature",
                LimitationCategory.API_UNAVAILABLE,
                "No public API for phone surface/skin temperature sensors. " +
                "Required for comfort estimation and thermal throttling prediction.",
                "Cannot estimate how hot the phone feels to touch, which is " +
                "different from internal battery temperature.",
                "Estimate from battery temperature with offset based on " +
                "known thermal transfer characteristics.",
                "OEM Thermal HAL provides skin temperature via multiple sensors " +
                "across device body.",
                "Skin temperature sensors typically named:\n" +
                "- thermal_zone_skin\n" +
                "- front_temp, back_temp\n" +
                "- shell_temp\n" +
                "Reported in /sys/class/thermal or via Thermal HAL 2.0+",
                true, false, 4
        ));
        
        // ─────────────────────────────────────────────────────────────────────────
        // DEVICE-DEPENDENT LIMITATIONS
        // ─────────────────────────────────────────────────────────────────────────
        
        limitations.add(new Limitation(
                "LIM-007",
                "Current Measurement Unavailable on Many Devices",
                LimitationCategory.DEVICE_DEPENDENT,
                "BatteryManager.PROPERTY_CURRENT_NOW returns 0, MIN_VALUE, or " +
                "throws exception on many devices. Not mandated by CDD.",
                "Cannot calculate accurate charging wattage or drain rate in mA. " +
                "Must fall back to %/hour estimation.",
                "Use level change over time (Δ%/Δt) with estimated capacity to " +
                "approximate current. Mark accuracy as 'estimated'.",
                "OEM can guarantee PROPERTY_CURRENT_NOW availability in device " +
                "certification requirements.",
                "PROPERTY_CURRENT_NOW availability:\n" +
                "- Google Pixel: Usually available\n" +
                "- Samsung: Varies by model\n" +
                "- Xiaomi: Often available\n" +
                "- Some budget devices: Not available\n" +
                "Returns μA (microamps) on most devices, but mA on some",
                true, false, 6
        ));
        
        limitations.add(new Limitation(
                "LIM-008",
                "Charge Counter Unreliable",
                LimitationCategory.DEVICE_DEPENDENT,
                "BatteryManager.PROPERTY_CHARGE_COUNTER returns 0 or resets " +
                "unpredictably on many devices. Not persistent across reboots.",
                "Coulomb counting for capacity estimation is unreliable. Cannot " +
                "track cumulative charge accurately.",
                "Use charge counter when available with sanity checking; fall back " +
                "to level-based estimation otherwise.",
                "OEM can expose persistent coulomb counter from fuel gauge with " +
                "guaranteed behavior.",
                "PROPERTY_CHARGE_COUNTER behavior:\n" +
                "- Returns μAh (microamp-hours)\n" +
                "- Should be cumulative coulomb count\n" +
                "- Resets on some devices at: reboot, full charge, charger disconnect\n" +
                "- Some devices return total capacity instead of remaining",
                true, false, 5
        ));
        
        limitations.add(new Limitation(
                "LIM-009",
                "Design Capacity Not Accessible",
                LimitationCategory.DEVICE_DEPENDENT,
                "No reliable API to get original battery design capacity (mAh). " +
                "Build.* properties don't include battery specs.",
                "Cannot calculate SOH as (current_capacity / design_capacity). " +
                "Must use database of known device capacities or user input.",
                "Maintain device database with known capacities; allow manual entry; " +
                "use conservative defaults (4000mAh) when unknown.",
                "OEM can provide design capacity via SystemProperties or custom API.",
                "Design capacity sources (all unreliable for 3rd party):\n" +
                "- /sys/class/power_supply/*/charge_full_design (root)\n" +
                "- SystemProperties (OEM-specific)\n" +
                "- Hardcoded in healthd/BatteryService\n" +
                "- Not in any public API",
                true, false, 4
        ));
        
        // ─────────────────────────────────────────────────────────────────────────
        // ACCURACY-LIMITED LIMITATIONS
        // ─────────────────────────────────────────────────────────────────────────
        
        limitations.add(new Limitation(
                "LIM-010",
                "SOC Non-Linearity Not Correctable",
                LimitationCategory.ACCURACY_LIMITED,
                "Battery level % is non-linear with actual capacity. 100% to 80% " +
                "depletes faster than 30% to 10% due to voltage curve.",
                "Drain rate calculations assuming linear SOC are inaccurate. " +
                "Time-to-empty estimates vary in accuracy.",
                "Apply known Li-ion discharge curve correction; track actual " +
                "per-10% drain times for device-specific calibration.",
                "OEM fuel gauge provides linearized SOC accounting for voltage " +
                "curve and temperature effects.",
                "Li-ion discharge curve:\n" +
                "- 100%-80%: ~3.9V-4.2V (steep)\n" +
                "- 80%-20%: ~3.6V-3.9V (flat)\n" +
                "- 20%-0%: ~3.0V-3.6V (steep)\n" +
                "Modern fuel gauges use DOD% not just voltage",
                true, false, 3
        ));
        
        limitations.add(new Limitation(
                "LIM-011",
                "Single Battery Sensor Location",
                LimitationCategory.ACCURACY_LIMITED,
                "Battery temperature is measured at one point on the battery pack. " +
                "Thermal gradients within pack are not captured.",
                "Hotspots within battery pack may go undetected. Temperature " +
                "reading may lag actual cell temperature.",
                "Apply thermal inertia model; flag rapid temperature changes as " +
                "potentially dangerous even if absolute value is normal.",
                "OEM may have multiple thermal sensors on battery pack and cells.",
                "Battery pack temperature sensor:\n" +
                "- Typically NTC thermistor\n" +
                "- Location varies by design\n" +
                "- Response time: seconds to minutes\n" +
                "- Accuracy: ±1-2°C",
                true, false, 3
        ));
        
        limitations.add(new Limitation(
                "LIM-012",
                "Estimated Capacity Has Inherent Error",
                LimitationCategory.ACCURACY_LIMITED,
                "Without fuel gauge access, capacity estimation from charge cycles " +
                "has 5-15% error margin depending on data quality.",
                "Reported mAh values should be treated as estimates, not exact. " +
                "Health percentage has proportional uncertainty.",
                "Report confidence intervals; require multiple full cycles for " +
                "initial calibration; continuously refine with more data.",
                "Fuel gauge provides capacity with <2% error using impedance " +
                "tracking and coulomb counting.",
                "Estimation error sources:\n" +
                "- SOC non-linearity: ±3-5%\n" +
                "- Temperature effects: ±2-3%\n" +
                "- Charging efficiency: ±1-2%\n" +
                "- Measurement noise: ±1-2%\n" +
                "Cumulative: 5-15% depending on conditions",
                true, false, 4
        ));
        
        // ─────────────────────────────────────────────────────────────────────────
        // DATA-DEPENDENT LIMITATIONS
        // ─────────────────────────────────────────────────────────────────────────
        
        limitations.add(new Limitation(
                "LIM-013",
                "AI Accuracy Requires Data Accumulation",
                LimitationCategory.DATA_DEPENDENT,
                "AI insights and predictions require minimum 7 days of data for " +
                "baseline establishment; optimal after 30 days.",
                "New installations show 'Insufficient data' messages. First-week " +
                "analysis has reduced accuracy.",
                "Provide clear progress indicator; use conservative confidence " +
                "scores; defer AI features until minimum data threshold.",
                "OEM can pre-seed with device-class baseline data for immediate " +
                "reasonable predictions.",
                "Data requirements:\n" +
                "- Minimum viable: 3 days\n" +
                "- Baseline establishment: 7 days\n" +
                "- Pattern recognition: 14 days\n" +
                "- Full accuracy: 30+ days\n" +
                "- Degradation trends: 90+ days",
                true, false, 3
        ));
        
        limitations.add(new Limitation(
                "LIM-014",
                "Cycle Count Needs Full Cycles",
                LimitationCategory.DATA_DEPENDENT,
                "Equivalent cycle count calculation requires observing actual " +
                "charge/discharge to accumulate. Partial cycles less accurate.",
                "Initial cycle count is 0 or estimated. Users who rarely fully " +
                "cycle may have inaccurate counts.",
                "Accumulate partial cycles (e.g., 40% to 90% = 0.5 cycle); explain " +
                "estimation methodology to users.",
                "Fuel gauge tracks precise cycle count from manufacturing.",
                "Cycle calculation:\n" +
                "- Full cycle = 100% capacity discharged\n" +
                "- 0-100% charge = 1 cycle\n" +
                "- 50-100% + 50-100% = 1 cycle\n" +
                "- 80-100% × 5 = 1 cycle\n" +
                "HexNil accumulates sum(Δlevel%)/100",
                true, false, 2
        ));
        
        return limitations;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // RUNTIME CHECKS
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Check current device's actual limitations (which may vary).
     */
    public DeviceCapabilities checkDeviceCapabilities() {
        BatteryManager batteryManager = (BatteryManager) 
                context.getSystemService(Context.BATTERY_SERVICE);
        
        boolean hasCurrentNow = false;
        boolean hasChargeCounter = false;
        boolean hasBatteryCapacity = false;
        
        if (batteryManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Test PROPERTY_CURRENT_NOW (API 21+)
            // Constant value: 2
            long currentNow = batteryManager.getLongProperty(2);
            hasCurrentNow = currentNow != Long.MIN_VALUE && currentNow != 0;
            
            // Test PROPERTY_CHARGE_COUNTER (API 21+)
            // Constant value: 1
            long chargeCounter = batteryManager.getLongProperty(1);
            hasChargeCounter = chargeCounter != Long.MIN_VALUE && chargeCounter != 0;
            
            // Test PROPERTY_CAPACITY (API 21+)
            // Constant value: 4
            // Note: This is percentage capacity, not mAh
            // But its availability indicates fuel gauge sophistication
            int capacity = batteryManager.getIntProperty(4);
            hasBatteryCapacity = capacity > 0 && capacity <= 100;
        }
        
        // Check for usage stats permission
        boolean hasUsageStats = false;
        try {
            android.app.AppOpsManager appOps = (android.app.AppOpsManager) 
                    context.getSystemService(Context.APP_OPS_SERVICE);
            int mode = appOps.checkOpNoThrow(
                    android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    context.getPackageName());
            hasUsageStats = mode == android.app.AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            // Ignore
        }
        
        return new DeviceCapabilities(
                Build.MANUFACTURER,
                Build.MODEL,
                Build.VERSION.SDK_INT,
                hasCurrentNow,
                hasChargeCounter,
                hasBatteryCapacity,
                hasUsageStats
        );
    }
    
    public static class DeviceCapabilities {
        public final String manufacturer;
        public final String model;
        public final int apiLevel;
        public final boolean hasCurrentMeasurement;
        public final boolean hasChargeCounter;
        public final boolean hasBatteryCapacity;
        public final boolean hasUsageStatsPermission;
        
        public DeviceCapabilities(String manufacturer, String model, int apiLevel,
                                   boolean hasCurrentMeasurement, boolean hasChargeCounter,
                                   boolean hasBatteryCapacity, boolean hasUsageStatsPermission) {
            this.manufacturer = manufacturer;
            this.model = model;
            this.apiLevel = apiLevel;
            this.hasCurrentMeasurement = hasCurrentMeasurement;
            this.hasChargeCounter = hasChargeCounter;
            this.hasBatteryCapacity = hasBatteryCapacity;
            this.hasUsageStatsPermission = hasUsageStatsPermission;
        }
        
        public int getCapabilityScore() {
            int score = 0;
            if (hasCurrentMeasurement) score += 30;
            if (hasChargeCounter) score += 25;
            if (hasBatteryCapacity) score += 15;
            if (hasUsageStatsPermission) score += 20;
            // Base score for always-available features
            score += 10;
            return score;
        }
        
        public String getSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Device: %s %s (API %d)\n", manufacturer, model, apiLevel));
            sb.append(String.format("Current Measurement: %s\n", hasCurrentMeasurement ? "✅" : "❌"));
            sb.append(String.format("Charge Counter: %s\n", hasChargeCounter ? "✅" : "❌"));
            sb.append(String.format("Battery Capacity: %s\n", hasBatteryCapacity ? "✅" : "❌"));
            sb.append(String.format("Usage Stats: %s\n", hasUsageStatsPermission ? "✅" : "❌"));
            sb.append(String.format("Capability Score: %d/100", getCapabilityScore()));
            return sb.toString();
        }
    }
}
