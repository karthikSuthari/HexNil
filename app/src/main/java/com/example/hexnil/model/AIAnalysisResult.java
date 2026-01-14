package com.example.hexnil.model;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * Stores AI/ML analysis results from HexNil-AI
 * Central intelligence for battery issue classification and recommendations
 * 
 * ANTI-HALLUCINATION ARCHITECTURE:
 * - aiReadiness: Whether AI was allowed to run
 * - dataQuality: Input data quality metrics
 * - deterministicResults: Exact calculations (reproducible)
 * - aiInterpretations: AI-generated insights (with confidence)
 * - confidenceMetrics: Per-component confidence scores
 * 
 * OEM Integration: Full audit trail for engineering dashboards.
 */
@Entity(tableName = "ai_analysis_results")
public class AIAnalysisResult {
    
    @PrimaryKey(autoGenerate = true)
    public long id;
    
    public long timestamp;
    public long analysisStartTime;
    public long analysisEndTime;
    
    // Analysis scope
    public String analysisType;          // "health", "drain", "thermal", "charging", "comprehensive"
    public String analysisPeriod;        // "24h", "7d", "30d", "all"
    public int dataPointsAnalyzed;
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // AI READINESS - Anti-Hallucination Gate Status
    // ═══════════════════════════════════════════════════════════════════════════════
    
    public boolean aiWasAllowedToRun;        // Did data meet minimum requirements
    public String aiBlockedReason;            // Why AI was blocked (if blocked)
    public float aiReadinessScore;            // 0-1 readiness score
    public String aiReadinessDetails;         // JSON with full readiness check results
    
    // Data requirement checks
    public int snapshotsAvailable;            // How many snapshots were available
    public int snapshotsRequired;             // Minimum required
    public long observationHoursAvailable;    // How many hours of data
    public long observationHoursRequired;     // Minimum required
    public int chargingSessionsAvailable;     // Charging sessions available
    public int drainEventsAvailable;          // Drain events available
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // DATA QUALITY METRICS
    // ═══════════════════════════════════════════════════════════════════════════════
    
    public float dataCompletenessScore;       // 0-1 how complete the data is
    public float dataConsistencyScore;        // 0-1 how consistent timestamps/values are
    public float dataCoverageScore;           // 0-1 how much of usage is captured
    public String dataQualityGrade;           // "A", "B", "C", "D", "F"
    public String dataGaps;                   // JSON array of detected data gaps
    public String dataQualityIssues;          // JSON array of quality issues
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // DETERMINISTIC RESULTS - Exact values, no AI interpretation
    // ═══════════════════════════════════════════════════════════════════════════════
    
    // These are EXACT calculations that same input always produces same output
    public String deterministicResults;       // JSON of all deterministic calculations
    
    // Temperature (deterministic - exact measurements)
    public float tempMinCelsius;              // Exact minimum temperature
    public float tempMaxCelsius;              // Exact maximum temperature
    public float tempMeanCelsius;             // Exact mean temperature
    public float tempStdDevCelsius;           // Exact standard deviation
    
    // Voltage (deterministic - exact measurements)
    public int voltageMinMv;                  // Exact minimum voltage
    public int voltageMaxMv;                  // Exact maximum voltage
    public int voltageMeanMv;                 // Exact mean voltage
    
    // Drain (deterministic - exact calculations)
    public float drainRateMeanPerHour;        // Exact mean drain rate
    public float drainRateMaxPerHour;         // Exact max drain rate
    public float drainRateStdDev;             // Exact drain rate variance
    
    // Charge cycles (deterministic - exact count)
    public float equivalentChargeCycles;      // Exact calculated cycles
    public int fullDischargeCount;            // Exact full discharge count
    
    // Time distribution (deterministic - exact durations)
    public long screenOnTimeMs;               // Exact screen on time
    public long chargingTimeMs;               // Exact charging time
    public long highTempTimeMs;               // Exact time at high temp
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // AI INTERPRETATIONS - Insights with confidence scores
    // ═══════════════════════════════════════════════════════════════════════════════
    
    // These are AI-generated insights that include confidence scores
    public String aiInterpretations;          // JSON of all AI interpretations
    
    // Health pattern inference
    public String healthPatternInference;     // AI health assessment
    public float healthPatternConfidence;     // 0-1 confidence in assessment
    public String healthPatternEvidence;      // JSON array of evidence citations
    
    // Usage pattern inference
    public String usagePatternInference;      // AI usage classification
    public float usagePatternConfidence;      // 0-1 confidence
    public String usagePatternCategory;       // "power_user", "normal", "light"
    
    // Charging behavior inference
    public String chargingBehaviorInference;  // AI charging assessment
    public float chargingBehaviorConfidence;  // 0-1 confidence
    
    // Thermal stress inference
    public String thermalStressInference;     // AI thermal assessment
    public float thermalStressConfidence;     // 0-1 confidence
    public String thermalStressLevel;         // "LOW", "MODERATE", "HIGH", "CRITICAL"
    
    // Drain anomaly inference
    public String drainAnomalyInference;      // AI drain anomaly assessment
    public float drainAnomalyConfidence;      // 0-1 confidence
    public boolean drainAnomaliesDetected;    // Whether anomalies were found
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // ROOT CAUSE ANALYSIS - With confidence and evidence
    // ═══════════════════════════════════════════════════════════════════════════════
    
    public String rootCause;                  // Identified root cause
    public String rootCauseCode;              // Machine-readable code (e.g., "THERMAL_DEGRADATION")
    public float rootCauseConfidence;         // 0-1 confidence in root cause
    public String rootCauseConfidenceLevel;   // "VERY_HIGH", "HIGH", "MODERATE", "LOW", "INSUFFICIENT"
    public String rootCauseExplanation;       // Detailed explanation
    public String rootCauseEvidence;          // JSON array of evidence citations
    public String contributingFactors;        // JSON of contributing factors
    public String alternativeHypotheses;      // JSON of other possible causes
    public String rootCauseCaveats;           // Known limitations of the analysis
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // EXISTING FIELDS - Issue detection, severity, recommendations, etc.
    // ═══════════════════════════════════════════════════════════════════════════════
    
    // Issue detection
    public boolean issuesDetected;
    public int issueCount;
    public String detectedIssues;             // JSON array of issues
    
    // Primary issue
    public String primaryIssue;               // Main identified problem
    public String issueCategory;              // "hardware", "firmware", "app", "user_behavior", "environment"
    public float issueConfidence;             // Confidence in diagnosis (0-1)
    
    // Severity assessment
    public String severity;                   // "info", "low", "medium", "high", "critical"
    public float riskScore;                   // 0-100 risk score
    public boolean requiresAction;            // Needs immediate attention
    public String urgency;                    // "none", "low", "medium", "high", "immediate"
    
    // Recommendations
    public String recommendations;            // JSON array of recommendations
    public String primaryRecommendation;      // Top recommendation
    public String engineerActions;            // Actions for OEM engineers
    public String userActions;                // Actions for end users
    
    // Fix predictions
    public String suggestedFixes;             // JSON of potential fixes
    public float fixSuccessProbability;       // Likelihood fix will work
    public String fixComplexity;              // "simple", "moderate", "complex"
    
    // Comparative analysis
    public float comparedToNormal;            // How device compares to baseline
    public float comparedToFleet;             // How device compares to fleet average
    public String fleetPosition;              // "top_10%", "average", "bottom_10%", etc.
    
    // Trend analysis
    public String trend;                      // "improving", "stable", "degrading"
    public float trendSlope;                  // Rate of change
    public String trendPrediction;            // Where things are headed
    
    // Battery score
    public float overallBatteryScore;         // 0-100 overall health
    public float healthScore;                 // Health component
    public float performanceScore;            // Performance component
    public float safetyScore;                 // Safety component
    public float reliabilityScore;            // Reliability component
    
    // Report generation
    public String executiveSummary;           // High-level summary
    public String technicalSummary;           // Technical details
    public String oemReportData;              // JSON for OEM dashboard
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // CONFIDENCE METRICS - Per-component confidence tracking
    // ═══════════════════════════════════════════════════════════════════════════════
    
    public float overallConfidenceScore;      // 0-1 overall analysis confidence
    public String overallConfidenceLevel;     // "VERY_HIGH", "HIGH", "MODERATE", "LOW", "INSUFFICIENT"
    public int evidencePointCount;            // Number of evidence points supporting analysis
    public String confidenceBreakdown;        // JSON with per-component confidence
    
    // Safety check results
    public boolean passedSafetyChecks;        // Did all safety thresholds pass
    public String safetyViolations;           // JSON of any safety violations
    public boolean hasCriticalSafetyIssue;    // Immediate safety concern
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // COMPLIANCE & AUDIT
    // ═══════════════════════════════════════════════════════════════════════════════
    
    public boolean hasComplianceIssues;
    public String complianceNotes;
    
    // Audit trail
    public String analysisVersion;            // Version of analysis algorithm
    public String configurationUsed;          // JSON of config values used
    public String disclaimers;                // JSON of required disclaimers
    public boolean includesAIContent;         // Whether output contains AI interpretations
    public String aiContentMarkers;           // Which parts are AI-generated
    
    // Learning data
    public boolean feedbackReceived;          // User provided feedback
    public boolean wasAccurate;               // Was analysis accurate
    public String feedbackNotes;              // Feedback details
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // OEM STRUCTURED OUTPUT
    // ═══════════════════════════════════════════════════════════════════════════════
    
    public String structuredReport;           // Full structured JSON report
    public String headline;                   // One-line summary
    public String synopsis;                   // 2-3 sentence overview
    public String batteryHealthStatus;        // "Excellent", "Good", "Fair", "Poor", "Critical"
    public String primaryConcern;             // Most important issue
    public String actionUrgency;              // "IMMEDIATE", "WITHIN_24_HOURS", "WITHIN_WEEK", etc.
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // CONSTRUCTORS & METHODS
    // ═══════════════════════════════════════════════════════════════════════════════
    
    public AIAnalysisResult() {
        this.timestamp = System.currentTimeMillis();
        this.analysisVersion = "1.0.0";
        this.includesAIContent = false;
    }
    
    /**
     * Create a result for when AI was blocked due to insufficient data.
     */
    @Ignore
    public static AIAnalysisResult createBlockedResult(String reason, float readinessScore,
                                                        int snapshotsAvailable, int snapshotsRequired) {
        AIAnalysisResult result = new AIAnalysisResult();
        result.aiWasAllowedToRun = false;
        result.aiBlockedReason = reason;
        result.aiReadinessScore = readinessScore;
        result.snapshotsAvailable = snapshotsAvailable;
        result.snapshotsRequired = snapshotsRequired;
        result.includesAIContent = false;
        result.headline = "Insufficient Data for Analysis";
        result.synopsis = "AI analysis was blocked: " + reason + ". Continue collecting data.";
        return result;
    }
    
    public String getSeverityEmoji() {
        switch (severity != null ? severity : "") {
            case "critical": return "CRITICAL";
            case "high": return "HIGH";
            case "medium": return "MEDIUM";
            case "low": return "LOW";
            default: return "INFO";
        }
    }
    
    public boolean needsOEMAttention() {
        return "critical".equals(severity) || "high".equals(severity) ||
               "hardware".equals(issueCategory) || "firmware".equals(issueCategory) ||
               hasCriticalSafetyIssue;
    }
    
    public long getAnalysisDuration() {
        return analysisEndTime - analysisStartTime;
    }
    
    /**
     * Check if this analysis has high-confidence results.
     */
    public boolean hasHighConfidenceResults() {
        return aiWasAllowedToRun && overallConfidenceScore >= 0.75;
    }
    
    /**
     * Check if AI interpretations should be trusted.
     */
    public boolean canTrustAIInterpretations() {
        return aiWasAllowedToRun && 
               overallConfidenceScore >= 0.60 &&
               evidencePointCount >= 3;
    }
    
    /**
     * Get a summary of data quality for UI display.
     */
    public String getDataQualitySummary() {
        if (dataQualityGrade == null) return "Not assessed";
        return "Grade " + dataQualityGrade + 
               " (Completeness: " + String.format("%.0f%%", dataCompletenessScore * 100) +
               ", Consistency: " + String.format("%.0f%%", dataConsistencyScore * 100) + ")";
    }
    
    /**
     * Get confidence level text for UI display.
     */
    public String getConfidenceDisplayText() {
        if (!aiWasAllowedToRun) {
            return "Analysis not performed - insufficient data";
        }
        
        String level = overallConfidenceLevel != null ? overallConfidenceLevel : "UNKNOWN";
        int percent = (int)(overallConfidenceScore * 100);
        
        switch (level) {
            case "VERY_HIGH":
                return "Very High Confidence (" + percent + "%)";
            case "HIGH":
                return "High Confidence (" + percent + "%)";
            case "MODERATE":
                return "Moderate Confidence (" + percent + "%) - verify key findings";
            case "LOW":
                return "Low Confidence (" + percent + "%) - treat as preliminary";
            default:
                return "Insufficient Data (" + percent + "%)";
        }
    }
    
    /**
     * Check if deterministic results are available (always accurate).
     */
    public boolean hasDeterministicResults() {
        return deterministicResults != null && !deterministicResults.isEmpty();
    }
    
    /**
     * Check if AI interpretations are available (require confidence check).
     */
    public boolean hasAIInterpretations() {
        return aiWasAllowedToRun && aiInterpretations != null && !aiInterpretations.isEmpty();
    }
}
