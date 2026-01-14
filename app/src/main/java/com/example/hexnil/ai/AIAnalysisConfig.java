package com.example.hexnil.ai;

import java.util.HashMap;
import java.util.Map;

/**
 * AI Analysis Configuration - Safety Boundaries and Thresholds
 * 
 * Central configuration for all AI analysis parameters.
 * Defines clear boundaries between deterministic and AI analysis.
 * All thresholds are explicitly documented and justified.
 * 
 * CONFIGURATION CATEGORIES:
 * 1. Data Requirements - Minimum data for AI to run
 * 2. Confidence Thresholds - When AI assertions are allowed
 * 3. Safety Limits - Boundaries AI cannot cross
 * 4. Analysis Boundaries - What AI can/cannot interpret
 * 5. Output Constraints - Limits on AI output
 * 
 * OEM Integration: All values are configurable and auditable.
 */
public class AIAnalysisConfig {
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // DATA REQUIREMENTS - Minimum data before AI analysis is permitted
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Minimum number of battery snapshots required for AI analysis.
     * Justification: Statistical significance requires sufficient sample size.
     * Lower values increase risk of pattern misidentification.
     */
    public static final int MIN_SNAPSHOTS_FOR_AI = 100;
    
    /**
     * Minimum number of battery snapshots for high-confidence analysis.
     */
    public static final int MIN_SNAPSHOTS_HIGH_CONFIDENCE = 500;
    
    /**
     * Minimum observation period in hours before AI analysis.
     * Justification: Need to capture full usage cycles including sleep patterns.
     */
    public static final int MIN_OBSERVATION_HOURS = 24;
    
    /**
     * Minimum observation period for high-confidence patterns.
     */
    public static final int MIN_OBSERVATION_HOURS_HIGH_CONFIDENCE = 72;
    
    /**
     * Minimum charging sessions to analyze charging behavior.
     */
    public static final int MIN_CHARGING_SESSIONS = 5;
    
    /**
     * Minimum drain events for drain pattern analysis.
     */
    public static final int MIN_DRAIN_EVENTS = 10;
    
    /**
     * Minimum temperature readings for thermal analysis.
     */
    public static final int MIN_THERMAL_READINGS = 50;
    
    /**
     * Minimum data completeness ratio (0.0 - 1.0).
     * Ratio of non-null/valid values to total expected values.
     */
    public static final double MIN_DATA_COMPLETENESS = 0.80;
    
    /**
     * Maximum acceptable data gap in hours.
     * Gaps larger than this reduce confidence.
     */
    public static final int MAX_ACCEPTABLE_GAP_HOURS = 2;
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // CONFIDENCE THRESHOLDS - When AI is allowed to make assertions
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Minimum confidence score for AI to make any assertion.
     * Below this, AI must say "insufficient data" or "uncertain".
     */
    public static final double MIN_CONFIDENCE_FOR_ASSERTION = 0.60;
    
    /**
     * Minimum confidence for AI to make a recommendation.
     * Recommendations affect user behavior, require higher confidence.
     */
    public static final double MIN_CONFIDENCE_FOR_RECOMMENDATION = 0.70;
    
    /**
     * Minimum confidence for AI to claim a root cause.
     * Root cause claims have significant weight, require high confidence.
     */
    public static final double MIN_CONFIDENCE_FOR_ROOT_CAUSE = 0.75;
    
    /**
     * Minimum confidence for AI to suggest hardware issue.
     * Hardware claims can lead to service visits, require highest confidence.
     */
    public static final double MIN_CONFIDENCE_FOR_HARDWARE_CLAIM = 0.85;
    
    /**
     * Minimum evidence points required for any assertion.
     * Need multiple data points to avoid single-point conclusions.
     */
    public static final int MIN_EVIDENCE_POINTS = 3;
    
    /**
     * Minimum evidence points for high-impact claims.
     */
    public static final int MIN_EVIDENCE_POINTS_HIGH_IMPACT = 5;
    
    /**
     * Minimum correlation coefficient for claiming correlation.
     * Below this, two factors are considered uncorrelated.
     */
    public static final double MIN_CORRELATION_THRESHOLD = 0.50;
    
    /**
     * Minimum R-squared for trend claims.
     */
    public static final double MIN_R_SQUARED_FOR_TREND = 0.60;
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // SAFETY LIMITS - Hard boundaries AI cannot cross
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * AI can NEVER claim battery is "safe" if temperature exceeded this.
     * Based on Li-ion thermal runaway thresholds.
     */
    public static final double UNSAFE_TEMPERATURE_THRESHOLD_CELSIUS = 60.0;
    
    /**
     * AI can NEVER claim battery is healthy if voltage ever went below this.
     * Deep discharge can cause irreversible damage.
     */
    public static final int UNSAFE_VOLTAGE_THRESHOLD_MV = 2800;
    
    /**
     * AI can NEVER claim battery is healthy if voltage exceeded this.
     * Overcharging risk threshold.
     */
    public static final int UNSAFE_VOLTAGE_HIGH_THRESHOLD_MV = 4500;
    
    /**
     * AI must flag safety concern if capacity below this percentage.
     */
    public static final int CRITICAL_CAPACITY_PERCENT = 60;
    
    /**
     * AI must recommend service if degradation rate exceeds this.
     * Percent capacity loss per 100 cycles.
     */
    public static final double ABNORMAL_DEGRADATION_RATE = 5.0;
    
    /**
     * AI can never claim specific cycle count remaining.
     * Lifespan predictions are too uncertain for specific claims.
     */
    public static final boolean ALLOW_LIFESPAN_PREDICTION = false;
    
    /**
     * AI can never diagnose specific hardware component failure.
     * Only physical inspection can confirm hardware issues.
     */
    public static final boolean ALLOW_SPECIFIC_HARDWARE_DIAGNOSIS = false;
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // ANALYSIS BOUNDARIES - What AI can vs cannot interpret
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Categories of analysis AI is ALLOWED to perform.
     */
    public static class AIAllowedAnalysis {
        // Pattern recognition
        public static final boolean USAGE_PATTERN_CLASSIFICATION = true;
        public static final boolean CHARGING_BEHAVIOR_ANALYSIS = true;
        public static final boolean DRAIN_ANOMALY_DETECTION = true;
        public static final boolean THERMAL_STRESS_ASSESSMENT = true;
        public static final boolean CORRELATION_IDENTIFICATION = true;
        
        // Insights
        public static final boolean HEALTH_TREND_INTERPRETATION = true;
        public static final boolean BEHAVIOR_IMPACT_EXPLANATION = true;
        public static final boolean ISSUE_PRIORITIZATION = true;
        public static final boolean ROOT_CAUSE_SYNTHESIS = true;
        
        // Recommendations
        public static final boolean USAGE_OPTIMIZATION_SUGGESTIONS = true;
        public static final boolean CHARGING_HABIT_RECOMMENDATIONS = true;
        public static final boolean GENERAL_MAINTENANCE_ADVICE = true;
    }
    
    /**
     * Categories of analysis AI is NOT ALLOWED to perform.
     * These must stay deterministic or be explicitly disclaimed.
     */
    public static class AIForbiddenAnalysis {
        // Exact values (must come from DeterministicAnalyzer)
        public static final String EXACT_PERCENTAGE_CLAIMS = "AI cannot report exact percentages";
        public static final String EXACT_TIME_PREDICTIONS = "AI cannot predict exact times";
        public static final String EXACT_CYCLE_COUNTS = "AI cannot state exact cycle counts";
        public static final String EXACT_CAPACITY_VALUES = "AI cannot claim exact mAh values";
        
        // Hardware diagnosis
        public static final String CELL_FAILURE_DIAGNOSIS = "AI cannot diagnose cell failure";
        public static final String BMS_MALFUNCTION_DIAGNOSIS = "AI cannot diagnose BMS issues";
        public static final String CONNECTOR_DAMAGE_DIAGNOSIS = "AI cannot diagnose physical damage";
        
        // Future predictions
        public static final String LIFESPAN_PREDICTION = "AI cannot predict remaining lifespan";
        public static final String FAILURE_DATE_PREDICTION = "AI cannot predict failure date";
        
        // Definitive claims
        public static final String DEFINITIVE_SAFE_CLAIM = "AI cannot definitively claim battery is safe";
        public static final String WARRANTY_ASSESSMENT = "AI cannot assess warranty status";
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // OUTPUT CONSTRAINTS - Limits on what AI can output
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Maximum number of issues AI can report.
     * Prevents overwhelming output, forces prioritization.
     */
    public static final int MAX_ISSUES_REPORTED = 5;
    
    /**
     * Maximum number of recommendations AI can provide.
     */
    public static final int MAX_RECOMMENDATIONS = 5;
    
    /**
     * Maximum number of contributing factors AI can list.
     */
    public static final int MAX_CONTRIBUTING_FACTORS = 3;
    
    /**
     * AI must include caveat if confidence below this threshold.
     */
    public static final double MANDATORY_CAVEAT_THRESHOLD = 0.80;
    
    /**
     * AI must include "needs verification" if confidence below this.
     */
    public static final double NEEDS_VERIFICATION_THRESHOLD = 0.70;
    
    /**
     * Required disclaimers that must appear in AI output.
     */
    public static final String[] REQUIRED_DISCLAIMERS = {
            "AI analysis is based on available data patterns",
            "Confidence scores indicate certainty level",
            "Hardware issues require physical inspection",
            "Recommendations are suggestions, not guarantees"
    };
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // BATTERY THRESHOLDS - Reference values for analysis
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Normal operating temperature range (Celsius).
     */
    public static final double TEMP_OPTIMAL_MIN = 20.0;
    public static final double TEMP_OPTIMAL_MAX = 35.0;
    public static final double TEMP_WARNING_THRESHOLD = 40.0;
    public static final double TEMP_CRITICAL_THRESHOLD = 45.0;
    
    /**
     * Normal voltage range (mV).
     */
    public static final int VOLTAGE_MIN_NORMAL = 3400;
    public static final int VOLTAGE_MAX_NORMAL = 4350;
    public static final int VOLTAGE_LOW_WARNING = 3200;
    public static final int VOLTAGE_HIGH_WARNING = 4400;
    
    /**
     * Drain rate thresholds (%/hour).
     */
    public static final double DRAIN_RATE_EXCELLENT = 2.0;
    public static final double DRAIN_RATE_GOOD = 5.0;
    public static final double DRAIN_RATE_NORMAL = 8.0;
    public static final double DRAIN_RATE_HIGH = 12.0;
    public static final double DRAIN_RATE_EXCESSIVE = 20.0;
    
    /**
     * Charging rate thresholds (%/hour).
     */
    public static final double CHARGE_RATE_SLOW = 10.0;
    public static final double CHARGE_RATE_NORMAL = 30.0;
    public static final double CHARGE_RATE_FAST = 60.0;
    public static final double CHARGE_RATE_RAPID = 100.0;
    
    /**
     * Battery health thresholds.
     */
    public static final double HEALTH_EXCELLENT = 95.0;
    public static final double HEALTH_GOOD = 85.0;
    public static final double HEALTH_FAIR = 75.0;
    public static final double HEALTH_POOR = 65.0;
    public static final double HEALTH_CRITICAL = 50.0;
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // CONFIGURATION HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Get all configuration values as a map for logging/auditing.
     */
    public static Map<String, Object> getAllConfigValues() {
        Map<String, Object> config = new HashMap<>();
        
        // Data requirements
        config.put("min_snapshots_for_ai", MIN_SNAPSHOTS_FOR_AI);
        config.put("min_snapshots_high_confidence", MIN_SNAPSHOTS_HIGH_CONFIDENCE);
        config.put("min_observation_hours", MIN_OBSERVATION_HOURS);
        config.put("min_charging_sessions", MIN_CHARGING_SESSIONS);
        config.put("min_drain_events", MIN_DRAIN_EVENTS);
        config.put("min_data_completeness", MIN_DATA_COMPLETENESS);
        
        // Confidence thresholds
        config.put("min_confidence_assertion", MIN_CONFIDENCE_FOR_ASSERTION);
        config.put("min_confidence_recommendation", MIN_CONFIDENCE_FOR_RECOMMENDATION);
        config.put("min_confidence_root_cause", MIN_CONFIDENCE_FOR_ROOT_CAUSE);
        config.put("min_evidence_points", MIN_EVIDENCE_POINTS);
        
        // Safety limits
        config.put("unsafe_temp_celsius", UNSAFE_TEMPERATURE_THRESHOLD_CELSIUS);
        config.put("unsafe_voltage_low_mv", UNSAFE_VOLTAGE_THRESHOLD_MV);
        config.put("unsafe_voltage_high_mv", UNSAFE_VOLTAGE_HIGH_THRESHOLD_MV);
        
        // Output constraints
        config.put("max_issues_reported", MAX_ISSUES_REPORTED);
        config.put("max_recommendations", MAX_RECOMMENDATIONS);
        
        return config;
    }
    
    /**
     * Validate if current data meets minimum requirements.
     */
    public static boolean meetsMinimumDataRequirements(int snapshotCount, 
                                                        long observationHours,
                                                        double completeness) {
        return snapshotCount >= MIN_SNAPSHOTS_FOR_AI &&
               observationHours >= MIN_OBSERVATION_HOURS &&
               completeness >= MIN_DATA_COMPLETENESS;
    }
    
    /**
     * Determine confidence level category from score.
     */
    public static String getConfidenceLevelCategory(double confidenceScore) {
        if (confidenceScore >= 0.90) return "VERY_HIGH";
        if (confidenceScore >= 0.80) return "HIGH";
        if (confidenceScore >= 0.70) return "MODERATE";
        if (confidenceScore >= 0.60) return "LOW";
        return "INSUFFICIENT";
    }
    
    /**
     * Check if confidence score allows assertion.
     */
    public static boolean canMakeAssertion(double confidenceScore) {
        return confidenceScore >= MIN_CONFIDENCE_FOR_ASSERTION;
    }
    
    /**
     * Check if confidence score allows recommendation.
     */
    public static boolean canMakeRecommendation(double confidenceScore) {
        return confidenceScore >= MIN_CONFIDENCE_FOR_RECOMMENDATION;
    }
    
    /**
     * Check if confidence score allows root cause claim.
     */
    public static boolean canClaimRootCause(double confidenceScore) {
        return confidenceScore >= MIN_CONFIDENCE_FOR_ROOT_CAUSE;
    }
    
    /**
     * Get temperature assessment category.
     */
    public static String getTemperatureCategory(double tempCelsius) {
        if (tempCelsius < TEMP_OPTIMAL_MIN) return "LOW";
        if (tempCelsius <= TEMP_OPTIMAL_MAX) return "OPTIMAL";
        if (tempCelsius <= TEMP_WARNING_THRESHOLD) return "ELEVATED";
        if (tempCelsius <= TEMP_CRITICAL_THRESHOLD) return "WARNING";
        return "CRITICAL";
    }
    
    /**
     * Get drain rate assessment category.
     */
    public static String getDrainRateCategory(double drainRatePerHour) {
        if (drainRatePerHour <= DRAIN_RATE_EXCELLENT) return "EXCELLENT";
        if (drainRatePerHour <= DRAIN_RATE_GOOD) return "GOOD";
        if (drainRatePerHour <= DRAIN_RATE_NORMAL) return "NORMAL";
        if (drainRatePerHour <= DRAIN_RATE_HIGH) return "HIGH";
        return "EXCESSIVE";
    }
    
    /**
     * Get health status category.
     */
    public static String getHealthCategory(double healthPercent) {
        if (healthPercent >= HEALTH_EXCELLENT) return "EXCELLENT";
        if (healthPercent >= HEALTH_GOOD) return "GOOD";
        if (healthPercent >= HEALTH_FAIR) return "FAIR";
        if (healthPercent >= HEALTH_POOR) return "POOR";
        return "CRITICAL";
    }
    
    /**
     * Check if any safety threshold was violated.
     */
    public static boolean hasSafetyViolation(double maxTempCelsius, 
                                              int minVoltageMv, 
                                              int maxVoltageMv) {
        return maxTempCelsius >= UNSAFE_TEMPERATURE_THRESHOLD_CELSIUS ||
               minVoltageMv <= UNSAFE_VOLTAGE_THRESHOLD_MV ||
               maxVoltageMv >= UNSAFE_VOLTAGE_HIGH_THRESHOLD_MV;
    }
    
    /**
     * Get required disclaimers for given confidence level.
     */
    public static String[] getRequiredDisclaimers(double confidenceScore) {
        if (confidenceScore >= 0.90) {
            return new String[]{REQUIRED_DISCLAIMERS[0]};  // Basic disclaimer only
        } else if (confidenceScore >= 0.80) {
            return new String[]{REQUIRED_DISCLAIMERS[0], REQUIRED_DISCLAIMERS[1]};
        } else if (confidenceScore >= 0.70) {
            return new String[]{REQUIRED_DISCLAIMERS[0], REQUIRED_DISCLAIMERS[1], REQUIRED_DISCLAIMERS[3]};
        } else {
            return REQUIRED_DISCLAIMERS;  // All disclaimers
        }
    }
}
