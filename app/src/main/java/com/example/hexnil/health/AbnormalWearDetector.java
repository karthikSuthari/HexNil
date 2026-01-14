package com.example.hexnil.health;

import android.util.Log;
import java.util.ArrayList;
import java.util.List;

/**
 * OEM-Grade Abnormal Wear Detection System
 * 
 * Detects premature or abnormal battery degradation by comparing actual wear
 * to expected baseline curves for lithium-ion batteries.
 * 
 * ============================================================================
 * DETECTION METHODOLOGY
 * ============================================================================
 * 
 * BASELINE WEAR CURVES:
 * Li-ion batteries follow predictable degradation patterns:
 * 
 * Phase 1 (First 50 cycles): ~0.01-0.02% fade per cycle
 * Phase 2 (50-300 cycles): ~0.03-0.05% fade per cycle  
 * Phase 3 (300-500 cycles): ~0.04-0.06% fade per cycle
 * Phase 4 (500+ cycles): Accelerating degradation
 * 
 * ABNORMAL CONDITIONS:
 * 
 * 1. PREMATURE CAPACITY LOSS
 *    - >15% fade in first 100 cycles (expect <5%)
 *    - >30% fade in first 300 cycles (expect <15%)
 *    
 * 2. SUDDEN CAPACITY DROP
 *    - >5% drop in single week
 *    - >10% drop in single month
 *    
 * 3. ACCELERATING DEGRADATION
 *    - Degradation rate increasing >50% month over month
 *    - Current rate > 3x initial rate
 *    
 * 4. ABNORMAL TEMPERATURE BEHAVIOR
 *    - Operating >45°C frequently
 *    - Large temp spikes (>10°C in 5 minutes)
 *    
 * 5. VOLTAGE INSTABILITY
 *    - Rapid voltage drop under load
 *    - Voltage fluctuations at rest
 *    
 * 6. INTERNAL RESISTANCE GROWTH
 *    - >50% increase from baseline
 *    - Rapid resistance increase (>20% in month)
 * 
 * SEVERITY LEVELS:
 * 0-2: Normal or minor deviation
 * 3-5: Warning - monitor closely  
 * 6-8: Alert - schedule inspection
 * 9-10: Critical - immediate attention
 */
public class AbnormalWearDetector {

    private static final String TAG = "AbnormalWearDetector";

    // =========================================================================
    // THRESHOLDS - CAPACITY LOSS
    // =========================================================================
    
    /** Maximum acceptable fade at 100 cycles (%) */
    public static final float MAX_FADE_AT_100_CYCLES = 5.0f;
    
    /** Warning fade at 100 cycles (%) */
    public static final float WARN_FADE_AT_100_CYCLES = 8.0f;
    
    /** Maximum acceptable fade at 300 cycles (%) */
    public static final float MAX_FADE_AT_300_CYCLES = 15.0f;
    
    /** Warning fade at 300 cycles (%) */
    public static final float WARN_FADE_AT_300_CYCLES = 20.0f;
    
    /** Maximum acceptable fade at 500 cycles (%) */
    public static final float MAX_FADE_AT_500_CYCLES = 25.0f;

    // =========================================================================
    // THRESHOLDS - SUDDEN DROP
    // =========================================================================
    
    /** Maximum weekly capacity drop before warning (%) */
    public static final float MAX_WEEKLY_DROP = 2.0f;
    
    /** Critical weekly capacity drop (%) */
    public static final float CRITICAL_WEEKLY_DROP = 5.0f;
    
    /** Maximum monthly capacity drop before warning (%) */
    public static final float MAX_MONTHLY_DROP = 5.0f;
    
    /** Critical monthly capacity drop (%) */
    public static final float CRITICAL_MONTHLY_DROP = 10.0f;

    // =========================================================================
    // THRESHOLDS - DEGRADATION ACCELERATION
    // =========================================================================
    
    /** Acceleration threshold (current rate / previous rate) */
    public static final float DEGRADATION_ACCELERATION_THRESHOLD = 1.5f;
    
    /** Severe acceleration threshold */
    public static final float DEGRADATION_ACCELERATION_SEVERE = 2.0f;
    
    /** Rate vs initial rate threshold */
    public static final float RATE_VS_INITIAL_THRESHOLD = 3.0f;

    // =========================================================================
    // THRESHOLDS - TEMPERATURE
    // =========================================================================
    
    /** Dangerous operating temperature (°C) */
    public static final float TEMP_DANGEROUS = 45.0f;
    
    /** Concerning operating temperature (°C) */
    public static final float TEMP_CONCERNING = 40.0f;
    
    /** Temperature spike threshold (°C in 5 min) */
    public static final float TEMP_SPIKE_THRESHOLD = 10.0f;
    
    /** Low temp warning (°C) */
    public static final float TEMP_LOW_WARNING = 5.0f;

    // =========================================================================
    // THRESHOLDS - RESISTANCE
    // =========================================================================
    
    /** Resistance increase warning threshold (%) */
    public static final float RESISTANCE_INCREASE_WARNING = 30.0f;
    
    /** Resistance increase critical threshold (%) */
    public static final float RESISTANCE_INCREASE_CRITICAL = 50.0f;
    
    /** Monthly resistance growth warning (%) */
    public static final float MONTHLY_RESISTANCE_GROWTH_WARNING = 10.0f;

    // =========================================================================
    // BASELINE WEAR MODEL
    // =========================================================================

    /**
     * Calculate expected capacity fade for a given cycle count
     * Based on typical Li-ion degradation curve
     * 
     * @param cycleCount Number of equivalent full cycles
     * @param batteryQuality Quality factor (0.8=budget, 1.0=standard, 1.2=premium)
     * @return Expected capacity fade percentage
     */
    public static float getExpectedFade(float cycleCount, float batteryQuality) {
        if (cycleCount < 0) return 0;
        
        float qualityFactor = 1.0f / batteryQuality; // Lower quality = higher fade
        
        // Piecewise linear model based on typical Li-ion behavior
        float fade;
        
        if (cycleCount <= 50) {
            // Phase 1: Initial conditioning
            fade = cycleCount * 0.015f * qualityFactor;
        } else if (cycleCount <= 300) {
            // Phase 2: Normal linear degradation
            fade = 0.75f + (cycleCount - 50) * 0.04f * qualityFactor;
        } else if (cycleCount <= 500) {
            // Phase 3: Accelerating degradation
            fade = 10.75f + (cycleCount - 300) * 0.05f * qualityFactor;
        } else {
            // Phase 4: End of life region
            fade = 20.75f + (cycleCount - 500) * 0.06f * qualityFactor;
        }
        
        return Math.min(fade, 100); // Cap at 100%
    }

    /**
     * Get expected fade at standard quality
     */
    public static float getExpectedFade(float cycleCount) {
        return getExpectedFade(cycleCount, 1.0f);
    }

    // =========================================================================
    // DETECTION METHODS
    // =========================================================================

    /**
     * Comprehensive wear analysis
     * 
     * @param metrics Current battery metrics
     * @return Detailed analysis result
     */
    public static WearAnalysis analyze(BatteryMetrics metrics) {
        WearAnalysis analysis = new WearAnalysis();
        
        // 1. Check cycle-based wear
        analyzeCapacityVsCycles(metrics, analysis);
        
        // 2. Check sudden drops
        analyzeSuddenDrops(metrics, analysis);
        
        // 3. Check degradation acceleration
        analyzeAcceleration(metrics, analysis);
        
        // 4. Check temperature behavior
        analyzeTemperature(metrics, analysis);
        
        // 5. Check resistance growth
        analyzeResistance(metrics, analysis);
        
        // Calculate overall assessment
        calculateOverallAssessment(analysis);
        
        return analysis;
    }

    /**
     * Analyze capacity loss vs expected for cycle count
     */
    private static void analyzeCapacityVsCycles(BatteryMetrics metrics, WearAnalysis analysis) {
        float actualFade = metrics.capacityFade;
        float expectedFade = getExpectedFade(metrics.cycleCount, metrics.batteryQualityFactor);
        
        analysis.expectedFade = expectedFade;
        analysis.fadeDeviation = actualFade - expectedFade;
        analysis.fadeDeviationPercent = expectedFade > 0 
                ? (analysis.fadeDeviation / expectedFade) * 100 : 0;
        
        // Check thresholds based on cycle count
        if (metrics.cycleCount <= 100) {
            if (actualFade > WARN_FADE_AT_100_CYCLES) {
                analysis.addFlag(WearFlag.PREMATURE_CAPACITY_LOSS, 
                        String.format("%.1f%% fade in %.0f cycles (max %.0f%%)", 
                                actualFade, metrics.cycleCount, WARN_FADE_AT_100_CYCLES),
                        actualFade > MAX_FADE_AT_100_CYCLES ? 3 : 2);
            }
        } else if (metrics.cycleCount <= 300) {
            if (actualFade > WARN_FADE_AT_300_CYCLES) {
                analysis.addFlag(WearFlag.PREMATURE_CAPACITY_LOSS,
                        String.format("%.1f%% fade in %.0f cycles (max %.0f%%)",
                                actualFade, metrics.cycleCount, WARN_FADE_AT_300_CYCLES),
                        actualFade > MAX_FADE_AT_300_CYCLES ? 3 : 2);
            }
        }
        
        // Check deviation from expected
        if (analysis.fadeDeviation > 10) {
            analysis.addFlag(WearFlag.EXCESSIVE_WEAR_DEVIATION,
                    String.format("Fade %.1f%% vs expected %.1f%% (+%.1f%% deviation)",
                            actualFade, expectedFade, analysis.fadeDeviation),
                    analysis.fadeDeviation > 15 ? 3 : 2);
        }
    }

    /**
     * Analyze sudden capacity drops
     */
    private static void analyzeSuddenDrops(BatteryMetrics metrics, WearAnalysis analysis) {
        // Weekly drop
        if (metrics.weeklyCapacityDrop > CRITICAL_WEEKLY_DROP) {
            analysis.addFlag(WearFlag.SUDDEN_CAPACITY_DROP,
                    String.format("%.1f%% drop this week (critical threshold: %.1f%%)",
                            metrics.weeklyCapacityDrop, CRITICAL_WEEKLY_DROP),
                    4);
            analysis.hasSuddenDrop = true;
        } else if (metrics.weeklyCapacityDrop > MAX_WEEKLY_DROP) {
            analysis.addFlag(WearFlag.SUDDEN_CAPACITY_DROP,
                    String.format("%.1f%% drop this week (warning threshold: %.1f%%)",
                            metrics.weeklyCapacityDrop, MAX_WEEKLY_DROP),
                    2);
            analysis.hasSuddenDrop = true;
        }
        
        // Monthly drop
        if (metrics.monthlyCapacityDrop > CRITICAL_MONTHLY_DROP) {
            analysis.addFlag(WearFlag.SUDDEN_CAPACITY_DROP,
                    String.format("%.1f%% drop this month (critical threshold: %.1f%%)",
                            metrics.monthlyCapacityDrop, CRITICAL_MONTHLY_DROP),
                    4);
            analysis.hasSuddenDrop = true;
        } else if (metrics.monthlyCapacityDrop > MAX_MONTHLY_DROP) {
            analysis.addFlag(WearFlag.SUDDEN_CAPACITY_DROP,
                    String.format("%.1f%% drop this month (warning threshold: %.1f%%)",
                            metrics.monthlyCapacityDrop, MAX_MONTHLY_DROP),
                    2);
            analysis.hasSuddenDrop = true;
        }
    }

    /**
     * Analyze degradation rate acceleration
     */
    private static void analyzeAcceleration(BatteryMetrics metrics, WearAnalysis analysis) {
        if (metrics.previousDegradationRate <= 0 || metrics.currentDegradationRate <= 0) {
            return; // Not enough data
        }
        
        float acceleration = metrics.currentDegradationRate / metrics.previousDegradationRate;
        analysis.degradationAcceleration = acceleration;
        
        if (acceleration > DEGRADATION_ACCELERATION_SEVERE) {
            analysis.addFlag(WearFlag.ACCELERATING_DEGRADATION,
                    String.format("Degradation rate increased %.1fx (was %.2f%%/mo, now %.2f%%/mo)",
                            acceleration, metrics.previousDegradationRate, 
                            metrics.currentDegradationRate),
                    3);
            analysis.hasAcceleratingDegradation = true;
        } else if (acceleration > DEGRADATION_ACCELERATION_THRESHOLD) {
            analysis.addFlag(WearFlag.ACCELERATING_DEGRADATION,
                    String.format("Degradation rate increased %.1fx (was %.2f%%/mo, now %.2f%%/mo)",
                            acceleration, metrics.previousDegradationRate,
                            metrics.currentDegradationRate),
                    2);
            analysis.hasAcceleratingDegradation = true;
        }
        
        // Check rate vs initial
        if (metrics.initialDegradationRate > 0) {
            float rateVsInitial = metrics.currentDegradationRate / metrics.initialDegradationRate;
            if (rateVsInitial > RATE_VS_INITIAL_THRESHOLD) {
                analysis.addFlag(WearFlag.ACCELERATING_DEGRADATION,
                        String.format("Current rate %.1fx initial rate (%.2f%% vs %.2f%%/mo)",
                                rateVsInitial, metrics.currentDegradationRate,
                                metrics.initialDegradationRate),
                        3);
            }
        }
    }

    /**
     * Analyze temperature behavior
     */
    private static void analyzeTemperature(BatteryMetrics metrics, WearAnalysis analysis) {
        // Check max operating temperature
        if (metrics.maxTemperatureC > TEMP_DANGEROUS) {
            analysis.addFlag(WearFlag.HIGH_TEMPERATURE_OPERATION,
                    String.format("Max temp %.1f°C exceeds danger threshold (%.1f°C)",
                            metrics.maxTemperatureC, TEMP_DANGEROUS),
                    4);
            analysis.hasTemperatureIssue = true;
        } else if (metrics.maxTemperatureC > TEMP_CONCERNING) {
            analysis.addFlag(WearFlag.HIGH_TEMPERATURE_OPERATION,
                    String.format("Max temp %.1f°C exceeds concern threshold (%.1f°C)",
                            metrics.maxTemperatureC, TEMP_CONCERNING),
                    2);
            analysis.hasTemperatureIssue = true;
        }
        
        // Check average temperature
        if (metrics.avgTemperatureC > TEMP_CONCERNING) {
            analysis.addFlag(WearFlag.HIGH_TEMPERATURE_OPERATION,
                    String.format("Average temp %.1f°C is elevated (ideal: 20-25°C)",
                            metrics.avgTemperatureC),
                    2);
            analysis.hasTemperatureIssue = true;
        }
        
        // Check temperature spikes
        if (metrics.maxTempSpikeC > TEMP_SPIKE_THRESHOLD) {
            analysis.addFlag(WearFlag.TEMPERATURE_SPIKE,
                    String.format("Temp spike of %.1f°C detected (threshold: %.1f°C)",
                            metrics.maxTempSpikeC, TEMP_SPIKE_THRESHOLD),
                    3);
            analysis.hasTemperatureIssue = true;
        }
        
        // Check low temperature operation
        if (metrics.minTemperatureC < TEMP_LOW_WARNING) {
            analysis.addFlag(WearFlag.LOW_TEMPERATURE_OPERATION,
                    String.format("Operating at low temp %.1f°C (can damage lithium plating)",
                            metrics.minTemperatureC),
                    2);
            analysis.hasTemperatureIssue = true;
        }
        
        // Calculate thermal stress score
        analysis.thermalStressScore = calculateThermalStress(metrics);
    }

    /**
     * Analyze internal resistance
     */
    private static void analyzeResistance(BatteryMetrics metrics, WearAnalysis analysis) {
        if (metrics.baselineResistance <= 0 || metrics.currentResistance <= 0) {
            return; // No resistance data
        }
        
        float resistanceIncrease = ((metrics.currentResistance - metrics.baselineResistance) 
                / metrics.baselineResistance) * 100;
        analysis.resistanceIncrease = resistanceIncrease;
        
        if (resistanceIncrease > RESISTANCE_INCREASE_CRITICAL) {
            analysis.addFlag(WearFlag.HIGH_INTERNAL_RESISTANCE,
                    String.format("Resistance increased %.1f%% from baseline (was %.1f mΩ, now %.1f mΩ)",
                            resistanceIncrease, metrics.baselineResistance, 
                            metrics.currentResistance),
                    4);
            analysis.hasResistanceIssue = true;
        } else if (resistanceIncrease > RESISTANCE_INCREASE_WARNING) {
            analysis.addFlag(WearFlag.HIGH_INTERNAL_RESISTANCE,
                    String.format("Resistance increased %.1f%% from baseline",
                            resistanceIncrease),
                    2);
            analysis.hasResistanceIssue = true;
        }
        
        // Check monthly growth rate
        if (metrics.monthlyResistanceGrowth > MONTHLY_RESISTANCE_GROWTH_WARNING) {
            analysis.addFlag(WearFlag.RAPID_RESISTANCE_GROWTH,
                    String.format("Resistance growing %.1f%%/month (threshold: %.1f%%)",
                            metrics.monthlyResistanceGrowth, 
                            MONTHLY_RESISTANCE_GROWTH_WARNING),
                    2);
            analysis.hasResistanceIssue = true;
        }
    }

    /**
     * Calculate thermal stress score (0-100, higher = worse)
     */
    private static float calculateThermalStress(BatteryMetrics metrics) {
        float stress = 0;
        
        // High temp stress
        if (metrics.avgTemperatureC > 35) {
            stress += (metrics.avgTemperatureC - 35) * 5;
        } else if (metrics.avgTemperatureC > 30) {
            stress += (metrics.avgTemperatureC - 30) * 2;
        }
        
        // Max temp stress
        if (metrics.maxTemperatureC > 45) {
            stress += (metrics.maxTemperatureC - 45) * 3;
        }
        
        // Low temp stress
        if (metrics.minTemperatureC < 10) {
            stress += (10 - metrics.minTemperatureC) * 2;
        }
        
        // Temperature variance stress
        float tempRange = metrics.maxTemperatureC - metrics.minTemperatureC;
        if (tempRange > 20) {
            stress += (tempRange - 20);
        }
        
        return Math.min(100, stress);
    }

    /**
     * Calculate overall assessment
     */
    private static void calculateOverallAssessment(WearAnalysis analysis) {
        // Sum severity scores
        int totalSeverity = 0;
        for (WearFlagEntry flag : analysis.flags) {
            totalSeverity += flag.severity;
        }
        
        analysis.totalSeverityScore = Math.min(10, totalSeverity);
        
        // Determine wear level
        if (analysis.totalSeverityScore >= 8) {
            analysis.wearLevel = WearLevel.CRITICAL;
        } else if (analysis.totalSeverityScore >= 5) {
            analysis.wearLevel = WearLevel.ALERT;
        } else if (analysis.totalSeverityScore >= 2) {
            analysis.wearLevel = WearLevel.WARNING;
        } else {
            analysis.wearLevel = WearLevel.NORMAL;
        }
        
        // Check for specific concerning combinations
        if (analysis.hasSuddenDrop && analysis.hasTemperatureIssue) {
            analysis.hasSwellingRisk = true;
            if (analysis.wearLevel.ordinal() < WearLevel.ALERT.ordinal()) {
                analysis.wearLevel = WearLevel.ALERT;
            }
        }
        
        // Generate recommendation
        analysis.recommendation = generateRecommendation(analysis);
    }

    /**
     * Generate actionable recommendation
     */
    private static String generateRecommendation(WearAnalysis analysis) {
        StringBuilder rec = new StringBuilder();
        
        switch (analysis.wearLevel) {
            case CRITICAL:
                rec.append("IMMEDIATE ACTION REQUIRED: ");
                if (analysis.hasSwellingRisk) {
                    rec.append("Battery may be swelling. Stop using device and inspect battery immediately. ");
                } else if (analysis.totalSeverityScore >= 9) {
                    rec.append("Battery replacement strongly recommended. Risk of failure. ");
                }
                break;
                
            case ALERT:
                rec.append("SCHEDULE INSPECTION: ");
                if (analysis.hasAcceleratingDegradation) {
                    rec.append("Degradation accelerating faster than normal. Plan battery replacement. ");
                } else if (analysis.hasResistanceIssue) {
                    rec.append("Internal resistance elevated. Battery efficiency compromised. ");
                }
                break;
                
            case WARNING:
                rec.append("MONITOR CLOSELY: ");
                if (analysis.hasTemperatureIssue) {
                    rec.append("Reduce thermal stress - avoid charging in hot environments. ");
                } else if (analysis.fadeDeviation > 10) {
                    rec.append("Degradation above expected. Consider usage pattern changes. ");
                }
                break;
                
            case NORMAL:
                rec.append("Battery health is within normal parameters. Continue routine monitoring.");
                break;
        }
        
        return rec.toString().trim();
    }

    // =========================================================================
    // DATA CLASSES
    // =========================================================================

    public enum WearFlag {
        PREMATURE_CAPACITY_LOSS,
        EXCESSIVE_WEAR_DEVIATION,
        SUDDEN_CAPACITY_DROP,
        ACCELERATING_DEGRADATION,
        HIGH_TEMPERATURE_OPERATION,
        LOW_TEMPERATURE_OPERATION,
        TEMPERATURE_SPIKE,
        HIGH_INTERNAL_RESISTANCE,
        RAPID_RESISTANCE_GROWTH,
        VOLTAGE_INSTABILITY,
        SWELLING_RISK
    }

    public enum WearLevel {
        NORMAL,     // 0-1 severity
        WARNING,    // 2-4 severity  
        ALERT,      // 5-7 severity
        CRITICAL    // 8-10 severity
    }

    public static class WearFlagEntry {
        public WearFlag flag;
        public String description;
        public int severity; // 1-4
        
        public WearFlagEntry(WearFlag flag, String description, int severity) {
            this.flag = flag;
            this.description = description;
            this.severity = severity;
        }
    }

    public static class WearAnalysis {
        // Flags
        public List<WearFlagEntry> flags = new ArrayList<>();
        
        // Capacity analysis
        public float expectedFade = 0;
        public float fadeDeviation = 0;
        public float fadeDeviationPercent = 0;
        
        // Conditions detected
        public boolean hasSuddenDrop = false;
        public boolean hasAcceleratingDegradation = false;
        public boolean hasTemperatureIssue = false;
        public boolean hasResistanceIssue = false;
        public boolean hasSwellingRisk = false;
        
        // Scores
        public float degradationAcceleration = 1.0f;
        public float resistanceIncrease = 0;
        public float thermalStressScore = 0;
        public int totalSeverityScore = 0;
        
        // Overall assessment
        public WearLevel wearLevel = WearLevel.NORMAL;
        public String recommendation = "";
        
        public void addFlag(WearFlag flag, String description, int severity) {
            flags.add(new WearFlagEntry(flag, description, severity));
        }
        
        public boolean hasAbnormalWear() {
            return wearLevel != WearLevel.NORMAL;
        }
        
        public String getSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("Wear Level: ").append(wearLevel).append("\n");
            sb.append("Severity Score: ").append(totalSeverityScore).append("/10\n");
            
            if (!flags.isEmpty()) {
                sb.append("Issues:\n");
                for (WearFlagEntry flag : flags) {
                    sb.append("  - [").append(flag.severity).append("] ")
                      .append(flag.description).append("\n");
                }
            }
            
            sb.append("\nRecommendation: ").append(recommendation);
            return sb.toString();
        }
    }

    /**
     * Input metrics for wear analysis
     */
    public static class BatteryMetrics {
        // Capacity metrics
        public float capacityFade = 0;           // Current total fade (%)
        public float cycleCount = 0;             // Equivalent full cycles
        public float batteryQualityFactor = 1.0f; // Quality factor (0.8-1.2)
        
        // Recent changes
        public float weeklyCapacityDrop = 0;     // Capacity drop in last week (%)
        public float monthlyCapacityDrop = 0;    // Capacity drop in last month (%)
        
        // Degradation rates
        public float currentDegradationRate = 0;  // Current rate (%/month)
        public float previousDegradationRate = 0; // Previous period rate (%/month)
        public float initialDegradationRate = 0;  // Rate at start of tracking (%/month)
        
        // Temperature data
        public float avgTemperatureC = 25;       // Average operating temp
        public float maxTemperatureC = 25;       // Max recorded temp
        public float minTemperatureC = 25;       // Min recorded temp
        public float maxTempSpikeC = 0;          // Largest temp increase in short period
        
        // Resistance data
        public float baselineResistance = 0;     // Initial/baseline resistance (mΩ)
        public float currentResistance = 0;      // Current resistance (mΩ)
        public float monthlyResistanceGrowth = 0; // Monthly resistance increase (%)
    }
}
