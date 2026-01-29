package com.example.hexnil.charging;

import com.example.hexnil.model.BatterySnapshot;
import com.example.hexnil.model.ChargingSession;

/**
 * OEM-grade charging safety scoring and analysis.
 * 
 * Provides comprehensive safety assessment for:
 * - Real-time snapshot evaluation
 * - Complete session scoring
 * - Battery degradation impact from charging
 * - Charger compatibility assessment
 * - OEM safety compliance validation
 * 
 * Safety Model:
 * The safety score is a weighted combination of multiple factors,
 * each with severity levels and degradation implications.
 * 
 * Scoring Scale:
 * - 90-100: Excellent (OEM-grade charging)
 * - 75-89:  Good (Safe charging)
 * - 60-74:  Acceptable (Minor concerns)
 * - 40-59:  Warning (Action recommended)
 * - 20-39:  Poor (Potential damage)
 * - 0-19:   Critical (Immediate action required)
 */
public class ChargingSafetyScorer {

    // ============================================================================
    // SAFETY THRESHOLDS
    // ============================================================================
    
    // Temperature thresholds (°C)
    public static final float TEMP_OPTIMAL_MAX = 35.0f;
    public static final float TEMP_ACCEPTABLE_MAX = 40.0f;
    public static final float TEMP_WARNING = 43.0f;
    public static final float TEMP_DANGER = 46.0f;
    public static final float TEMP_CRITICAL = 50.0f;
    
    // Voltage thresholds (mV)
    public static final float VOLTAGE_OPTIMAL_MIN = 3700f;
    public static final float VOLTAGE_OPTIMAL_MAX = 4350f;
    public static final float VOLTAGE_WARNING_MAX = 4450f;
    public static final float VOLTAGE_DANGER_MAX = 4500f;
    public static final float VOLTAGE_CRITICAL_MAX = 4550f;
    
    // Current thresholds (mA) - absolute values
    public static final float CURRENT_OPTIMAL_MAX = 2000f;
    public static final float CURRENT_ACCEPTABLE_MAX = 2500f;
    public static final float CURRENT_WARNING_MAX = 3000f;
    public static final float CURRENT_DANGER_MAX = 4000f;
    
    // Fluctuation thresholds
    public static final int FLUCTUATION_ACCEPTABLE = 3;
    public static final int FLUCTUATION_WARNING = 6;
    public static final int FLUCTUATION_DANGER = 10;
    
    // Interruption thresholds
    public static final int INTERRUPTION_ACCEPTABLE = 2;
    public static final int INTERRUPTION_WARNING = 5;
    public static final int INTERRUPTION_DANGER = 10;
    
    // ============================================================================
    // WEIGHT FACTORS
    // ============================================================================
    
    private static final float WEIGHT_TEMPERATURE = 0.30f;
    private static final float WEIGHT_VOLTAGE = 0.25f;
    private static final float WEIGHT_CURRENT = 0.20f;
    private static final float WEIGHT_STABILITY = 0.15f;
    private static final float WEIGHT_EFFICIENCY = 0.10f;
    
    // ============================================================================
    // SAFETY RESULT CLASSES
    // ============================================================================
    
    /**
     * Severity level for safety assessments.
     */
    public enum SafetySeverity {
        EXCELLENT("excellent", 0),
        GOOD("good", 1),
        ACCEPTABLE("acceptable", 2),
        WARNING("warning", 3),
        POOR("poor", 4),
        CRITICAL("critical", 5);
        
        public final String code;
        public final int level;
        
        SafetySeverity(String code, int level) {
            this.code = code;
            this.level = level;
        }
        
        public static SafetySeverity fromScore(float score) {
            if (score >= 90) return EXCELLENT;
            if (score >= 75) return GOOD;
            if (score >= 60) return ACCEPTABLE;
            if (score >= 40) return WARNING;
            if (score >= 20) return POOR;
            return CRITICAL;
        }
    }
    
    /**
     * Real-time safety assessment result.
     */
    public static class RealTimeSafetyResult {
        public float overallScore;
        public SafetySeverity severity;
        public float temperatureScore;
        public float voltageScore;
        public float currentScore;
        public String primaryConcern;
        public String recommendation;
        public boolean requiresImmediateAction;
        public float estimatedDegradationImpact;
        
        public String getStatusCode() {
            return severity.code;
        }
    }
    
    /**
     * Session-level safety assessment result.
     */
    public static class SessionSafetyResult {
        public float overallScore;
        public SafetySeverity severity;
        public float temperatureScore;
        public float voltageScore;
        public float currentScore;
        public float stabilityScore;
        public float efficiencyScore;
        public int warningCount;
        public int criticalEventCount;
        public String primaryIssue;
        public String[] recommendations;
        public float batteryDegradationPercent;
        public boolean passedOEMCompliance;
        public String complianceNotes;
        
        public String getSafetyStatus() {
            if (severity == SafetySeverity.CRITICAL) return "danger";
            if (severity == SafetySeverity.POOR || severity == SafetySeverity.WARNING) return "warning";
            return "safe";
        }
    }
    
    /**
     * Degradation impact assessment.
     */
    public static class DegradationImpact {
        public float immediateCapacityLoss;     // mAh lost this session
        public float cumulativeStress;          // 0-100 stress level
        public float cycleEquivalent;           // How many "cycles" this session counts as
        public float temperatureContribution;   // % of degradation from heat
        public float voltageContribution;       // % from voltage stress
        public float currentContribution;       // % from high current
        public String riskLevel;
        public String mitigationAdvice;
    }
    
    // ============================================================================
    // REAL-TIME SCORING
    // ============================================================================
    
    /**
     * Evaluate safety in real-time from a single snapshot.
     * 
     * @param snapshot Current battery snapshot
     * @return RealTimeSafetyResult with immediate assessment
     */
    public static RealTimeSafetyResult evaluateRealTime(BatterySnapshot snapshot) {
        RealTimeSafetyResult result = new RealTimeSafetyResult();
        
        // Temperature scoring
        float temp = snapshot.getTemperatureCelsius();
        result.temperatureScore = scoreTemperature(temp);
        
        // Voltage scoring
        result.voltageScore = scoreVoltage(snapshot.voltage);
        
        // Current scoring
        result.currentScore = scoreCurrent(Math.abs(snapshot.chargeRate));
        
        // Calculate overall score
        result.overallScore = (result.temperatureScore * WEIGHT_TEMPERATURE) +
                              (result.voltageScore * WEIGHT_VOLTAGE) +
                              (result.currentScore * WEIGHT_CURRENT);
        
        // Normalize (since we're only using 3 of 5 weights)
        float normalizer = WEIGHT_TEMPERATURE + WEIGHT_VOLTAGE + WEIGHT_CURRENT;
        result.overallScore = result.overallScore / normalizer;
        
        // Determine severity
        result.severity = SafetySeverity.fromScore(result.overallScore);
        
        // Identify primary concern
        result.primaryConcern = identifyPrimaryConcern(
            result.temperatureScore, 
            result.voltageScore, 
            result.currentScore,
            temp, 
            snapshot.voltage, 
            snapshot.chargeRate
        );
        
        // Generate recommendation
        result.recommendation = generateRealtimeRecommendation(result, snapshot);
        
        // Check for immediate action
        result.requiresImmediateAction = temp >= TEMP_DANGER || 
                                          snapshot.voltage >= VOLTAGE_DANGER_MAX ||
                                          Math.abs(snapshot.chargeRate) >= CURRENT_DANGER_MAX;
        
        // Estimate degradation impact
        result.estimatedDegradationImpact = estimateInstantDegradation(snapshot);
        
        return result;
    }
    
    /**
     * Score temperature on 0-100 scale.
     */
    private static float scoreTemperature(float tempC) {
        if (tempC <= TEMP_OPTIMAL_MAX) return 100f;
        if (tempC <= TEMP_ACCEPTABLE_MAX) {
            return 100f - ((tempC - TEMP_OPTIMAL_MAX) / (TEMP_ACCEPTABLE_MAX - TEMP_OPTIMAL_MAX)) * 15f;
        }
        if (tempC <= TEMP_WARNING) {
            return 85f - ((tempC - TEMP_ACCEPTABLE_MAX) / (TEMP_WARNING - TEMP_ACCEPTABLE_MAX)) * 25f;
        }
        if (tempC <= TEMP_DANGER) {
            return 60f - ((tempC - TEMP_WARNING) / (TEMP_DANGER - TEMP_WARNING)) * 30f;
        }
        if (tempC <= TEMP_CRITICAL) {
            return 30f - ((tempC - TEMP_DANGER) / (TEMP_CRITICAL - TEMP_DANGER)) * 25f;
        }
        return Math.max(0f, 5f - (tempC - TEMP_CRITICAL));
    }
    
    /**
     * Score voltage on 0-100 scale.
     */
    private static float scoreVoltage(float voltageMv) {
        // Too low is also bad (indicates issues)
        if (voltageMv < 3400) return 50f;
        if (voltageMv < VOLTAGE_OPTIMAL_MIN) {
            return 50f + ((voltageMv - 3400f) / (VOLTAGE_OPTIMAL_MIN - 3400f)) * 50f;
        }
        if (voltageMv <= VOLTAGE_OPTIMAL_MAX) return 100f;
        if (voltageMv <= VOLTAGE_WARNING_MAX) {
            return 100f - ((voltageMv - VOLTAGE_OPTIMAL_MAX) / (VOLTAGE_WARNING_MAX - VOLTAGE_OPTIMAL_MAX)) * 20f;
        }
        if (voltageMv <= VOLTAGE_DANGER_MAX) {
            return 80f - ((voltageMv - VOLTAGE_WARNING_MAX) / (VOLTAGE_DANGER_MAX - VOLTAGE_WARNING_MAX)) * 40f;
        }
        if (voltageMv <= VOLTAGE_CRITICAL_MAX) {
            return 40f - ((voltageMv - VOLTAGE_DANGER_MAX) / (VOLTAGE_CRITICAL_MAX - VOLTAGE_DANGER_MAX)) * 30f;
        }
        return Math.max(0f, 10f - (voltageMv - VOLTAGE_CRITICAL_MAX) * 0.1f);
    }
    
    /**
     * Score current on 0-100 scale.
     */
    private static float scoreCurrent(float currentMa) {
        if (currentMa <= CURRENT_OPTIMAL_MAX) return 100f;
        if (currentMa <= CURRENT_ACCEPTABLE_MAX) {
            return 100f - ((currentMa - CURRENT_OPTIMAL_MAX) / (CURRENT_ACCEPTABLE_MAX - CURRENT_OPTIMAL_MAX)) * 15f;
        }
        if (currentMa <= CURRENT_WARNING_MAX) {
            return 85f - ((currentMa - CURRENT_ACCEPTABLE_MAX) / (CURRENT_WARNING_MAX - CURRENT_ACCEPTABLE_MAX)) * 25f;
        }
        if (currentMa <= CURRENT_DANGER_MAX) {
            return 60f - ((currentMa - CURRENT_WARNING_MAX) / (CURRENT_DANGER_MAX - CURRENT_WARNING_MAX)) * 35f;
        }
        return Math.max(0f, 25f - (currentMa - CURRENT_DANGER_MAX) * 0.02f);
    }
    
    /**
     * Identify the primary concern from scores.
     */
    private static String identifyPrimaryConcern(
            float tempScore, float voltageScore, float currentScore,
            float temp, float voltage, float current) {
        
        float minScore = Math.min(tempScore, Math.min(voltageScore, currentScore));
        
        if (minScore >= 85) {
            return "none";
        }
        
        if (tempScore == minScore) {
            if (temp >= TEMP_DANGER) {
                return "critical_temperature";
            } else if (temp >= TEMP_WARNING) {
                return "high_temperature";
            } else {
                return "elevated_temperature";
            }
        }
        
        if (voltageScore == minScore) {
            if (voltage >= VOLTAGE_DANGER_MAX) {
                return "critical_overvoltage";
            } else if (voltage >= VOLTAGE_WARNING_MAX) {
                return "high_voltage";
            } else if (voltage < VOLTAGE_OPTIMAL_MIN) {
                return "low_voltage";
            } else {
                return "voltage_concern";
            }
        }
        
        if (currentScore == minScore) {
            if (current >= CURRENT_DANGER_MAX) {
                return "critical_overcurrent";
            } else if (current >= CURRENT_WARNING_MAX) {
                return "high_current";
            } else {
                return "current_concern";
            }
        }
        
        return "multiple_concerns";
    }
    
    /**
     * Generate actionable recommendation for real-time conditions.
     */
    private static String generateRealtimeRecommendation(RealTimeSafetyResult result, BatterySnapshot snapshot) {
        if (result.severity == SafetySeverity.EXCELLENT || result.severity == SafetySeverity.GOOD) {
            return "Charging safely. No action required.";
        }
        
        StringBuilder rec = new StringBuilder();
        
        if (snapshot.getTemperatureCelsius() >= TEMP_WARNING) {
            rec.append("Reduce thermal load: ");
            if (snapshot.isScreenOnOrFalse()) {
                rec.append("Turn off screen. ");
            }
            rec.append("Move to cooler location. ");
            if (snapshot.getTemperatureCelsius() >= TEMP_DANGER) {
                rec.append("DISCONNECT CHARGER IF TEMP CONTINUES RISING. ");
            }
        }
        
        if (snapshot.voltage >= VOLTAGE_WARNING_MAX) {
            rec.append("Voltage elevated. ");
            if (snapshot.voltage >= VOLTAGE_DANGER_MAX) {
                rec.append("Consider using different charger. ");
            }
        }
        
        if (Math.abs(snapshot.chargeRate) >= CURRENT_WARNING_MAX) {
            rec.append("High current draw. ");
            if (!snapshot.isFastCharging) {
                rec.append("Check cable quality. ");
            }
        }
        
        if (rec.length() == 0) {
            rec.append("Monitor conditions. Minor concerns detected.");
        }
        
        return rec.toString().trim();
    }
    
    /**
     * Estimate instantaneous degradation impact.
     * Returns percentage of expected degradation per hour at these conditions.
     */
    private static float estimateInstantDegradation(BatterySnapshot snapshot) {
        float degradation = 0.01f; // Base: 0.01% per hour under perfect conditions
        
        // Temperature multiplier (Arrhenius equation approximation)
        float temp = snapshot.getTemperatureCelsius();
        if (temp > 35) {
            // Each 10°C doubles degradation rate
            float tempExcess = temp - 35;
            degradation *= Math.pow(2, tempExcess / 10.0);
        }
        
        // High voltage stress (exponential above 4.2V)
        if (snapshot.voltage > 4200) {
            float voltageExcess = (snapshot.voltage - 4200) / 100f;
            degradation *= (1 + voltageExcess * 0.5f);
        }
        
        // High current stress
        float current = Math.abs(snapshot.chargeRate);
        if (current > 2000) {
            degradation *= (1 + (current - 2000) / 5000f);
        }
        
        return degradation;
    }
    
    // ============================================================================
    // SESSION-LEVEL SCORING
    // ============================================================================
    
    /**
     * Comprehensive safety evaluation for a completed charging session.
     * 
     * @param session Completed charging session
     * @return SessionSafetyResult with full analysis
     */
    public static SessionSafetyResult evaluateSession(ChargingSession session) {
        SessionSafetyResult result = new SessionSafetyResult();
        
        // Temperature score (based on max and average)
        result.temperatureScore = scoreSessionTemperature(session);
        
        // Voltage score
        result.voltageScore = scoreSessionVoltage(session);
        
        // Current score
        result.currentScore = scoreSessionCurrent(session);
        
        // Stability score
        result.stabilityScore = scoreSessionStability(session);
        
        // Efficiency score
        result.efficiencyScore = scoreSessionEfficiency(session);
        
        // Calculate weighted overall score
        result.overallScore = (result.temperatureScore * WEIGHT_TEMPERATURE) +
                              (result.voltageScore * WEIGHT_VOLTAGE) +
                              (result.currentScore * WEIGHT_CURRENT) +
                              (result.stabilityScore * WEIGHT_STABILITY) +
                              (result.efficiencyScore * WEIGHT_EFFICIENCY);
        
        // Determine severity
        result.severity = SafetySeverity.fromScore(result.overallScore);
        
        // Count events
        result.warningCount = session.overTempEvents + session.overVoltageEvents + session.overCurrentEvents;
        result.criticalEventCount = countCriticalEvents(session);
        
        // Identify primary issue
        result.primaryIssue = identifySessionPrimaryIssue(session, result);
        
        // Generate recommendations
        result.recommendations = generateSessionRecommendations(session, result);
        
        // Calculate battery degradation
        result.batteryDegradationPercent = calculateSessionDegradation(session);
        
        // OEM compliance check
        result.passedOEMCompliance = checkOEMCompliance(session, result);
        result.complianceNotes = generateComplianceNotes(session, result);
        
        return result;
    }
    
    /**
     * Score session temperature safety.
     */
    private static float scoreSessionTemperature(ChargingSession session) {
        float score = 100f;
        
        // Max temperature impact (most important)
        if (session.maxTemperature > TEMP_CRITICAL) {
            score -= 50;
        } else if (session.maxTemperature > TEMP_DANGER) {
            score -= 35;
        } else if (session.maxTemperature > TEMP_WARNING) {
            score -= 20;
        } else if (session.maxTemperature > TEMP_ACCEPTABLE_MAX) {
            score -= 10;
        }
        
        // Average temperature impact
        if (session.avgTemperature > TEMP_WARNING) {
            score -= 15;
        } else if (session.avgTemperature > TEMP_ACCEPTABLE_MAX) {
            score -= 8;
        }
        
        // Temperature rise during session
        float tempRise = session.maxTemperature - session.startTemperature;
        if (tempRise > 15) {
            score -= 15; // Excessive heat buildup
        } else if (tempRise > 10) {
            score -= 8;
        }
        
        // Over-temp event penalty
        score -= session.overTempEvents * 5;
        
        return Math.max(0f, score);
    }
    
    /**
     * Score session voltage safety.
     */
    private static float scoreSessionVoltage(ChargingSession session) {
        float score = 100f;
        
        // Max voltage impact
        if (session.maxVoltage > VOLTAGE_CRITICAL_MAX) {
            score -= 50;
        } else if (session.maxVoltage > VOLTAGE_DANGER_MAX) {
            score -= 30;
        } else if (session.maxVoltage > VOLTAGE_WARNING_MAX) {
            score -= 15;
        } else if (session.maxVoltage > VOLTAGE_OPTIMAL_MAX) {
            score -= 5;
        }
        
        // Over-voltage event penalty
        score -= session.overVoltageEvents * 8;
        
        // Voltage fluctuation penalty
        score -= session.voltageFluctuations * 2;
        
        return Math.max(0f, score);
    }
    
    /**
     * Score session current safety.
     */
    private static float scoreSessionCurrent(ChargingSession session) {
        float score = 100f;
        
        // Max current impact
        if (session.maxCurrent > CURRENT_DANGER_MAX) {
            score -= 30;
        } else if (session.maxCurrent > CURRENT_WARNING_MAX) {
            score -= 15;
        } else if (session.maxCurrent > CURRENT_ACCEPTABLE_MAX) {
            score -= 8;
        }
        
        // Over-current event penalty
        score -= session.overCurrentEvents * 6;
        
        // Current fluctuation penalty
        score -= session.currentFluctuations * 3;
        
        return Math.max(0f, score);
    }
    
    /**
     * Score session stability.
     */
    private static float scoreSessionStability(ChargingSession session) {
        float score = 100f;
        
        // Interruption penalties
        if (session.interruptionCount >= INTERRUPTION_DANGER) {
            score -= 40;
        } else if (session.interruptionCount >= INTERRUPTION_WARNING) {
            score -= 25;
        } else if (session.interruptionCount >= INTERRUPTION_ACCEPTABLE) {
            score -= 10;
        } else if (session.interruptionCount > 0) {
            score -= session.interruptionCount * 3;
        }
        
        // Total fluctuation impact
        int totalFluctuations = session.currentFluctuations + session.voltageFluctuations;
        if (totalFluctuations >= FLUCTUATION_DANGER * 2) {
            score -= 30;
        } else if (totalFluctuations >= FLUCTUATION_WARNING * 2) {
            score -= 15;
        } else {
            score -= totalFluctuations * 1.5f;
        }
        
        // Use curve stability score if available
        if (session.curveStabilityScore > 0) {
            // Blend with calculated stability
            score = (score + session.curveStabilityScore) / 2f;
        }
        
        return Math.max(0f, score);
    }
    
    /**
     * Score session efficiency.
     */
    private static float scoreSessionEfficiency(ChargingSession session) {
        if (session.chargingEfficiency <= 0) {
            return 70f; // Default if not calculated
        }
        
        // Efficiency scoring
        // 95-100%: Perfect (some chargers can exceed 95%)
        // 85-95%: Excellent
        // 75-85%: Good
        // 65-75%: Acceptable
        // 50-65%: Poor
        // <50%: Very poor
        
        if (session.chargingEfficiency >= 95) return 100f;
        if (session.chargingEfficiency >= 85) return 85f + (session.chargingEfficiency - 85) * 1.5f;
        if (session.chargingEfficiency >= 75) return 70f + (session.chargingEfficiency - 75) * 1.5f;
        if (session.chargingEfficiency >= 65) return 50f + (session.chargingEfficiency - 65) * 2f;
        if (session.chargingEfficiency >= 50) return 25f + (session.chargingEfficiency - 50) * 1.67f;
        return Math.max(0f, session.chargingEfficiency / 2f);
    }
    
    /**
     * Count critical safety events in session.
     */
    private static int countCriticalEvents(ChargingSession session) {
        int count = 0;
        
        if (session.maxTemperature >= TEMP_DANGER) count++;
        if (session.maxVoltage >= VOLTAGE_DANGER_MAX) count++;
        if (session.maxCurrent >= CURRENT_DANGER_MAX) count++;
        if (session.overTempEvents > 3) count++;
        if (session.overVoltageEvents > 2) count++;
        if (session.overCurrentEvents > 5) count++;
        
        return count;
    }
    
    /**
     * Identify primary safety issue for session.
     */
    private static String identifySessionPrimaryIssue(ChargingSession session, SessionSafetyResult result) {
        if (result.severity == SafetySeverity.EXCELLENT || result.severity == SafetySeverity.GOOD) {
            return "none";
        }
        
        // Find lowest score
        float minScore = Math.min(result.temperatureScore,
                         Math.min(result.voltageScore,
                         Math.min(result.currentScore,
                         Math.min(result.stabilityScore, result.efficiencyScore))));
        
        if (result.temperatureScore == minScore) {
            return session.maxTemperature >= TEMP_DANGER ? "critical_heat" : "thermal_stress";
        }
        if (result.voltageScore == minScore) {
            return session.maxVoltage >= VOLTAGE_DANGER_MAX ? "voltage_danger" : "voltage_concern";
        }
        if (result.currentScore == minScore) {
            return session.maxCurrent >= CURRENT_DANGER_MAX ? "overcurrent_danger" : "current_concern";
        }
        if (result.stabilityScore == minScore) {
            return session.interruptionCount >= INTERRUPTION_WARNING ? "unstable_charging" : "minor_instability";
        }
        if (result.efficiencyScore == minScore) {
            return "poor_efficiency";
        }
        
        return "multiple_issues";
    }
    
    /**
     * Generate actionable recommendations for session issues.
     */
    private static String[] generateSessionRecommendations(ChargingSession session, SessionSafetyResult result) {
        java.util.List<String> recs = new java.util.ArrayList<>();
        
        if (result.temperatureScore < 70) {
            recs.add("Charge in cooler environment or with case removed");
            if (session.maxTemperature >= TEMP_DANGER) {
                recs.add("CRITICAL: Avoid using device while charging");
            }
        }
        
        if (result.voltageScore < 70) {
            recs.add("Consider using OEM-certified charger");
            if (session.overVoltageEvents > 0) {
                recs.add("Inspect charging cable for damage");
            }
        }
        
        if (result.currentScore < 70) {
            recs.add("Check charger compatibility with device");
            if (!session.isFastCharging && session.maxCurrent > CURRENT_WARNING_MAX) {
                recs.add("Unexpected high current - try different charger");
            }
        }
        
        if (result.stabilityScore < 70) {
            if (session.interruptionCount >= INTERRUPTION_WARNING) {
                recs.add("Check for loose connection or damaged port");
            }
            recs.add("Avoid moving device while charging");
        }
        
        if (result.efficiencyScore < 60) {
            recs.add("Low charging efficiency - charger may be degraded");
            recs.add("Try using shorter, thicker charging cable");
        }
        
        if (recs.isEmpty()) {
            recs.add("Continue monitoring charging behavior");
        }
        
        return recs.toArray(new String[0]);
    }
    
    /**
     * Calculate estimated battery degradation from this charging session.
     * Returns percentage of capacity lost.
     */
    private static float calculateSessionDegradation(ChargingSession session) {
        if (session.chargingDuration == 0) return 0f;
        
        float durationHours = session.chargingDuration / 3_600_000f;
        float baseDegradation = 0.01f * durationHours; // 0.01% per hour baseline
        
        // Temperature factor (Arrhenius)
        float tempFactor = 1.0f;
        if (session.avgTemperature > 35) {
            tempFactor = (float) Math.pow(2, (session.avgTemperature - 35) / 10.0);
        }
        
        // High voltage stress (especially above 80% SOC)
        float voltageFactor = 1.0f;
        if (session.maxVoltage > 4300) {
            voltageFactor = 1.0f + (session.maxVoltage - 4300) / 500f;
        }
        
        // Current stress factor
        float currentFactor = 1.0f;
        if (session.avgCurrent > 1500) {
            currentFactor = 1.0f + (session.avgCurrent - 1500) / 3000f;
        }
        
        // Safety event penalties
        float eventPenalty = 1.0f;
        eventPenalty += session.overTempEvents * 0.05f;
        eventPenalty += session.overVoltageEvents * 0.08f;
        eventPenalty += session.overCurrentEvents * 0.03f;
        
        return baseDegradation * tempFactor * voltageFactor * currentFactor * eventPenalty;
    }
    
    /**
     * Check OEM compliance for charging session.
     */
    private static boolean checkOEMCompliance(ChargingSession session, SessionSafetyResult result) {
        // OEM compliance requirements:
        // 1. Max temperature < 45°C
        // 2. Max voltage < 4.5V (4500mV)
        // 3. No more than 2 over-current events
        // 4. Overall safety score >= 60
        // 5. No critical events
        
        if (session.maxTemperature >= 45) return false;
        if (session.maxVoltage >= 4500) return false;
        if (session.overCurrentEvents > 2) return false;
        if (result.overallScore < 60) return false;
        if (result.criticalEventCount > 0) return false;
        
        return true;
    }
    
    /**
     * Generate OEM compliance notes.
     */
    private static String generateComplianceNotes(ChargingSession session, SessionSafetyResult result) {
        StringBuilder notes = new StringBuilder();
        
        if (result.passedOEMCompliance) {
            notes.append("PASS: Session meets OEM safety standards. ");
        } else {
            notes.append("FAIL: ");
            if (session.maxTemperature >= 45) {
                notes.append("MaxTemp exceeded 45°C. ");
            }
            if (session.maxVoltage >= 4500) {
                notes.append("MaxVoltage exceeded 4.5V. ");
            }
            if (session.overCurrentEvents > 2) {
                notes.append("Over-current events > 2. ");
            }
            if (result.overallScore < 60) {
                notes.append("Safety score below threshold. ");
            }
            if (result.criticalEventCount > 0) {
                notes.append("Critical event detected. ");
            }
        }
        
        notes.append(String.format("Score: %.1f/%s", result.overallScore, result.severity.code));
        
        return notes.toString();
    }
    
    // ============================================================================
    // DEGRADATION IMPACT ANALYSIS
    // ============================================================================
    
    /**
     * Calculate detailed degradation impact for a session.
     * 
     * @param session Completed charging session
     * @param designCapacityMah Battery design capacity
     * @return Detailed degradation impact analysis
     */
    public static DegradationImpact calculateDegradationImpact(ChargingSession session, float designCapacityMah) {
        DegradationImpact impact = new DegradationImpact();
        
        float durationHours = session.chargingDuration / 3_600_000f;
        
        // Calculate individual contributions
        float tempDeg = calculateTemperatureDegradation(session, durationHours);
        float voltageDeg = calculateVoltageDegradation(session, durationHours);
        float currentDeg = calculateCurrentDegradation(session, durationHours);
        
        float totalDegPercent = tempDeg + voltageDeg + currentDeg;
        
        // Calculate mAh lost
        impact.immediateCapacityLoss = (totalDegPercent / 100f) * designCapacityMah;
        
        // Stress level (0-100)
        impact.cumulativeStress = Math.min(100f, totalDegPercent * 1000f);
        
        // Cycle equivalent (based on depth of discharge charged)
        impact.cycleEquivalent = session.chargedAmount / 100f;
        
        // Contribution percentages
        float totalContrib = tempDeg + voltageDeg + currentDeg;
        if (totalContrib > 0) {
            impact.temperatureContribution = (tempDeg / totalContrib) * 100f;
            impact.voltageContribution = (voltageDeg / totalContrib) * 100f;
            impact.currentContribution = (currentDeg / totalContrib) * 100f;
        }
        
        // Risk level
        if (totalDegPercent > 0.05f) {
            impact.riskLevel = "high";
            impact.mitigationAdvice = "Reduce charging temperature and avoid overnight charging at 100%";
        } else if (totalDegPercent > 0.02f) {
            impact.riskLevel = "moderate";
            impact.mitigationAdvice = "Consider charging to 80% for daily use";
        } else {
            impact.riskLevel = "low";
            impact.mitigationAdvice = "Current charging habits are battery-friendly";
        }
        
        return impact;
    }
    
    private static float calculateTemperatureDegradation(ChargingSession session, float hours) {
        if (session.avgTemperature <= 25) return 0.005f * hours;
        if (session.avgTemperature <= 35) return 0.01f * hours;
        
        // Exponential increase above 35°C
        float excess = session.avgTemperature - 35;
        return 0.01f * hours * (float) Math.pow(2, excess / 10.0);
    }
    
    private static float calculateVoltageDegradation(ChargingSession session, float hours) {
        if (session.maxVoltage <= 4200) return 0.005f * hours;
        
        // Stress increases significantly above 4.2V
        float excess = (session.maxVoltage - 4200) / 1000f;
        return 0.005f * hours * (1 + excess * 3);
    }
    
    private static float calculateCurrentDegradation(ChargingSession session, float hours) {
        if (session.avgCurrent <= 1000) return 0.003f * hours;
        if (session.avgCurrent <= 2000) return 0.005f * hours;
        
        // Higher current = more degradation
        return 0.005f * hours * (session.avgCurrent / 2000f);
    }
}
