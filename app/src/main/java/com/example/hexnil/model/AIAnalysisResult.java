package com.example.hexnil.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Stores AI/ML analysis results from HexNil-AI
 * Central intelligence for battery issue classification and recommendations
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
    
    // Issue detection
    public boolean issuesDetected;
    public int issueCount;
    public String detectedIssues;        // JSON array of issues
    
    // Primary issue
    public String primaryIssue;          // Main identified problem
    public String issueCategory;         // "hardware", "firmware", "app", "user_behavior", "environment"
    public float issueConfidence;        // Confidence in diagnosis (0-1)
    
    // Root cause analysis
    public String rootCause;             // Identified root cause
    public String rootCauseExplanation;  // Detailed explanation
    public String contributingFactors;   // JSON of contributing factors
    
    // Severity assessment
    public String severity;              // "info", "low", "medium", "high", "critical"
    public float riskScore;              // 0-100 risk score
    public boolean requiresAction;       // Needs immediate attention
    public String urgency;               // "none", "low", "medium", "high", "immediate"
    
    // Recommendations
    public String recommendations;       // JSON array of recommendations
    public String primaryRecommendation; // Top recommendation
    public String engineerActions;       // Actions for OEM engineers
    public String userActions;           // Actions for end users
    
    // Fix predictions
    public String suggestedFixes;        // JSON of potential fixes
    public float fixSuccessProbability;  // Likelihood fix will work
    public String fixComplexity;         // "simple", "moderate", "complex"
    
    // Comparative analysis
    public float comparedToNormal;       // How device compares to baseline
    public float comparedToFleet;        // How device compares to fleet average
    public String fleetPosition;         // "top_10%", "average", "bottom_10%", etc.
    
    // Trend analysis
    public String trend;                 // "improving", "stable", "degrading"
    public float trendSlope;             // Rate of change
    public String trendPrediction;       // Where things are headed
    
    // Battery score
    public float overallBatteryScore;    // 0-100 overall health
    public float healthScore;            // Health component
    public float performanceScore;       // Performance component
    public float safetyScore;            // Safety component
    public float reliabilityScore;       // Reliability component
    
    // Report generation
    public String executiveSummary;      // High-level summary
    public String technicalSummary;      // Technical details
    public String oemReportData;         // JSON for OEM dashboard
    
    // Compliance
    public boolean hasComplianceIssues;
    public String complianceNotes;
    
    // Learning data
    public boolean feedbackReceived;     // User provided feedback
    public boolean wasAccurate;          // Was analysis accurate
    public String feedbackNotes;         // Feedback details
    
    public AIAnalysisResult() {
        this.timestamp = System.currentTimeMillis();
    }
    
    public String getSeverityEmoji() {
        switch (severity) {
            case "critical": return "CRITICAL";
            case "high": return "HIGH";
            case "medium": return "MEDIUM";
            case "low": return "LOW";
            default: return "INFO";
        }
    }
    
    public boolean needsOEMAttention() {
        return "critical".equals(severity) || "high".equals(severity) ||
               "hardware".equals(issueCategory) || "firmware".equals(issueCategory);
    }
    
    public long getAnalysisDuration() {
        return analysisEndTime - analysisStartTime;
    }
}
