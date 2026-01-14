package com.example.hexnil.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI Output Structure - OEM-Grade Structured Output Format
 * 
 * Defines the exact output format for all AI analysis results.
 * Structured for both engineering dashboards and API consumption.
 * 
 * OUTPUT HIERARCHY:
 * 1. Executive Summary (human-readable, non-technical)
 * 2. Root Cause Analysis (technical with evidence)
 * 3. Deterministic Measurements (exact values, auditable)
 * 4. AI Interpretations (insights with confidence)
 * 5. Recommendations (prioritized, actionable)
 * 6. Metadata (timestamps, versions, data quality)
 * 
 * OEM Integration: JSON-serializable, audit-compliant, dashboard-ready.
 */
public class AIOutputStructure {
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // SEVERITY LEVELS
    // ═══════════════════════════════════════════════════════════════════════════════
    
    public enum Severity {
        CRITICAL(4, "Critical", "#FF0000", "Immediate action required - battery safety concern"),
        HIGH(3, "High", "#FF6600", "Action recommended within 24 hours"),
        MEDIUM(2, "Medium", "#FFCC00", "Monitor and address when convenient"),
        LOW(1, "Low", "#00CC00", "Informational - no immediate action needed"),
        INFO(0, "Info", "#0066CC", "Status update - everything normal");
        
        private final int level;
        private final String label;
        private final String color;
        private final String description;
        
        Severity(int level, String label, String color, String description) {
            this.level = level;
            this.label = label;
            this.color = color;
            this.description = description;
        }
        
        public int getLevel() { return level; }
        public String getLabel() { return label; }
        public String getColor() { return color; }
        public String getDescription() { return description; }
    }
    
    public enum ActionUrgency {
        IMMEDIATE("Immediate action required"),
        WITHIN_24_HOURS("Address within 24 hours"),
        WITHIN_WEEK("Address within one week"),
        WHEN_CONVENIENT("Address when convenient"),
        MONITOR_ONLY("Monitor - no action needed");
        
        private final String description;
        
        ActionUrgency(String description) {
            this.description = description;
        }
        
        public String getDescription() { return description; }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // STRUCTURED OUTPUT CLASSES
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Complete AI Analysis Report - Top-level output structure.
     */
    public static class AIAnalysisReport {
        // Metadata
        private final String reportId;
        private final String reportVersion;
        private final long generatedTimestamp;
        private final String analysisType;
        private final String deviceId;
        
        // Data Quality
        private final DataQualitySection dataQuality;
        
        // Executive Summary
        private final ExecutiveSummary executiveSummary;
        
        // Root Cause
        private final RootCauseSection rootCause;
        
        // Measurements (Deterministic)
        private final MeasurementsSection measurements;
        
        // AI Insights
        private final AIInsightsSection aiInsights;
        
        // Recommendations
        private final RecommendationsSection recommendations;
        
        // Compliance
        private final ComplianceSection compliance;
        
        public AIAnalysisReport(String reportId, String analysisType, String deviceId,
                                 DataQualitySection dataQuality,
                                 ExecutiveSummary executiveSummary,
                                 RootCauseSection rootCause,
                                 MeasurementsSection measurements,
                                 AIInsightsSection aiInsights,
                                 RecommendationsSection recommendations,
                                 ComplianceSection compliance) {
            this.reportId = reportId;
            this.reportVersion = "1.0.0";
            this.generatedTimestamp = System.currentTimeMillis();
            this.analysisType = analysisType;
            this.deviceId = deviceId;
            this.dataQuality = dataQuality;
            this.executiveSummary = executiveSummary;
            this.rootCause = rootCause;
            this.measurements = measurements;
            this.aiInsights = aiInsights;
            this.recommendations = recommendations;
            this.compliance = compliance;
        }
        
        // Getters
        public String getReportId() { return reportId; }
        public String getReportVersion() { return reportVersion; }
        public long getGeneratedTimestamp() { return generatedTimestamp; }
        public String getAnalysisType() { return analysisType; }
        public String getDeviceId() { return deviceId; }
        public DataQualitySection getDataQuality() { return dataQuality; }
        public ExecutiveSummary getExecutiveSummary() { return executiveSummary; }
        public RootCauseSection getRootCause() { return rootCause; }
        public MeasurementsSection getMeasurements() { return measurements; }
        public AIInsightsSection getAiInsights() { return aiInsights; }
        public RecommendationsSection getRecommendations() { return recommendations; }
        public ComplianceSection getCompliance() { return compliance; }
        
        /**
         * Convert to JSON-compatible map for API responses.
         */
        public Map<String, Object> toJsonMap() {
            Map<String, Object> json = new HashMap<>();
            
            // Metadata
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("report_id", reportId);
            metadata.put("version", reportVersion);
            metadata.put("generated_timestamp", generatedTimestamp);
            metadata.put("analysis_type", analysisType);
            metadata.put("device_id", deviceId);
            json.put("metadata", metadata);
            
            // Sections
            json.put("data_quality", dataQuality.toJsonMap());
            json.put("executive_summary", executiveSummary.toJsonMap());
            json.put("root_cause", rootCause.toJsonMap());
            json.put("measurements", measurements.toJsonMap());
            json.put("ai_insights", aiInsights.toJsonMap());
            json.put("recommendations", recommendations.toJsonMap());
            json.put("compliance", compliance.toJsonMap());
            
            return json;
        }
    }
    
    /**
     * Data Quality Section - Shows reliability of analysis.
     */
    public static class DataQualitySection {
        private final int totalDataPoints;
        private final long dataSpanMs;
        private final double completenessScore;        // 0.0 - 1.0
        private final double consistencyScore;         // 0.0 - 1.0
        private final double coverageScore;            // 0.0 - 1.0
        private final boolean meetsMinimumRequirements;
        private final List<String> dataGaps;
        private final List<String> qualityIssues;
        private final String overallQualityGrade;      // A, B, C, D, F
        
        public DataQualitySection(int totalDataPoints, long dataSpanMs,
                                   double completenessScore, double consistencyScore,
                                   double coverageScore, boolean meetsMinimumRequirements,
                                   List<String> dataGaps, List<String> qualityIssues) {
            this.totalDataPoints = totalDataPoints;
            this.dataSpanMs = dataSpanMs;
            this.completenessScore = completenessScore;
            this.consistencyScore = consistencyScore;
            this.coverageScore = coverageScore;
            this.meetsMinimumRequirements = meetsMinimumRequirements;
            this.dataGaps = dataGaps;
            this.qualityIssues = qualityIssues;
            
            // Calculate grade
            double avgScore = (completenessScore + consistencyScore + coverageScore) / 3;
            if (avgScore >= 0.90) this.overallQualityGrade = "A";
            else if (avgScore >= 0.80) this.overallQualityGrade = "B";
            else if (avgScore >= 0.70) this.overallQualityGrade = "C";
            else if (avgScore >= 0.60) this.overallQualityGrade = "D";
            else this.overallQualityGrade = "F";
        }
        
        public Map<String, Object> toJsonMap() {
            Map<String, Object> json = new HashMap<>();
            json.put("total_data_points", totalDataPoints);
            json.put("data_span_ms", dataSpanMs);
            json.put("data_span_hours", dataSpanMs / 3600000.0);
            json.put("completeness_score", completenessScore);
            json.put("consistency_score", consistencyScore);
            json.put("coverage_score", coverageScore);
            json.put("meets_minimum_requirements", meetsMinimumRequirements);
            json.put("data_gaps", dataGaps);
            json.put("quality_issues", qualityIssues);
            json.put("overall_quality_grade", overallQualityGrade);
            return json;
        }
        
        // Getters
        public int getTotalDataPoints() { return totalDataPoints; }
        public long getDataSpanMs() { return dataSpanMs; }
        public double getCompletenessScore() { return completenessScore; }
        public double getConsistencyScore() { return consistencyScore; }
        public double getCoverageScore() { return coverageScore; }
        public boolean meetsMinimumRequirements() { return meetsMinimumRequirements; }
        public List<String> getDataGaps() { return dataGaps; }
        public List<String> getQualityIssues() { return qualityIssues; }
        public String getOverallQualityGrade() { return overallQualityGrade; }
    }
    
    /**
     * Executive Summary - Human-readable overview.
     */
    public static class ExecutiveSummary {
        private final String headline;                 // One-line summary
        private final String synopsis;                 // 2-3 sentence overview
        private final Severity overallSeverity;
        private final int issueCount;
        private final String batteryHealthStatus;      // Good, Fair, Poor, Critical
        private final double batteryHealthScore;       // 0-100
        private final String primaryConcern;           // Most important issue
        private final String primaryRecommendation;    // Most important action
        private final ActionUrgency urgency;
        
        public ExecutiveSummary(String headline, String synopsis, Severity overallSeverity,
                                 int issueCount, String batteryHealthStatus,
                                 double batteryHealthScore, String primaryConcern,
                                 String primaryRecommendation, ActionUrgency urgency) {
            this.headline = headline;
            this.synopsis = synopsis;
            this.overallSeverity = overallSeverity;
            this.issueCount = issueCount;
            this.batteryHealthStatus = batteryHealthStatus;
            this.batteryHealthScore = batteryHealthScore;
            this.primaryConcern = primaryConcern;
            this.primaryRecommendation = primaryRecommendation;
            this.urgency = urgency;
        }
        
        public Map<String, Object> toJsonMap() {
            Map<String, Object> json = new HashMap<>();
            json.put("headline", headline);
            json.put("synopsis", synopsis);
            json.put("overall_severity", overallSeverity.name());
            json.put("severity_level", overallSeverity.getLevel());
            json.put("severity_color", overallSeverity.getColor());
            json.put("issue_count", issueCount);
            json.put("battery_health_status", batteryHealthStatus);
            json.put("battery_health_score", batteryHealthScore);
            json.put("primary_concern", primaryConcern);
            json.put("primary_recommendation", primaryRecommendation);
            json.put("urgency", urgency.name());
            json.put("urgency_description", urgency.getDescription());
            return json;
        }
        
        // Getters
        public String getHeadline() { return headline; }
        public String getSynopsis() { return synopsis; }
        public Severity getOverallSeverity() { return overallSeverity; }
        public int getIssueCount() { return issueCount; }
        public String getBatteryHealthStatus() { return batteryHealthStatus; }
        public double getBatteryHealthScore() { return batteryHealthScore; }
        public String getPrimaryConcern() { return primaryConcern; }
        public String getPrimaryRecommendation() { return primaryRecommendation; }
        public ActionUrgency getUrgency() { return urgency; }
    }
    
    /**
     * Root Cause Section - Technical analysis with evidence.
     */
    public static class RootCauseSection {
        private final String primaryRootCause;
        private final String rootCauseCode;            // Machine-readable code
        private final double confidenceScore;
        private final String confidenceLevel;          // VERY_HIGH, HIGH, MODERATE, LOW
        private final String explanation;              // Technical explanation
        private final String evidenceSummary;          // How we determined this
        private final List<String> evidenceCitations;  // Specific data points
        private final List<String> contributingFactors;
        private final List<String> alternativeHypotheses;  // Other possibilities
        private final List<String> caveats;            // Known limitations
        
        public RootCauseSection(String primaryRootCause, String rootCauseCode,
                                 double confidenceScore, String explanation,
                                 String evidenceSummary, List<String> evidenceCitations,
                                 List<String> contributingFactors,
                                 List<String> alternativeHypotheses,
                                 List<String> caveats) {
            this.primaryRootCause = primaryRootCause;
            this.rootCauseCode = rootCauseCode;
            this.confidenceScore = confidenceScore;
            this.confidenceLevel = determineConfidenceLevel(confidenceScore);
            this.explanation = explanation;
            this.evidenceSummary = evidenceSummary;
            this.evidenceCitations = evidenceCitations;
            this.contributingFactors = contributingFactors;
            this.alternativeHypotheses = alternativeHypotheses;
            this.caveats = caveats;
        }
        
        private String determineConfidenceLevel(double score) {
            if (score >= 0.90) return "VERY_HIGH";
            if (score >= 0.80) return "HIGH";
            if (score >= 0.70) return "MODERATE";
            if (score >= 0.60) return "LOW";
            return "INSUFFICIENT";
        }
        
        public Map<String, Object> toJsonMap() {
            Map<String, Object> json = new HashMap<>();
            json.put("primary_root_cause", primaryRootCause);
            json.put("root_cause_code", rootCauseCode);
            json.put("confidence_score", confidenceScore);
            json.put("confidence_percent", String.format("%.0f%%", confidenceScore * 100));
            json.put("confidence_level", confidenceLevel);
            json.put("explanation", explanation);
            json.put("evidence_summary", evidenceSummary);
            json.put("evidence_citations", evidenceCitations);
            json.put("evidence_count", evidenceCitations.size());
            json.put("contributing_factors", contributingFactors);
            json.put("alternative_hypotheses", alternativeHypotheses);
            json.put("caveats", caveats);
            return json;
        }
        
        // Getters
        public String getPrimaryRootCause() { return primaryRootCause; }
        public String getRootCauseCode() { return rootCauseCode; }
        public double getConfidenceScore() { return confidenceScore; }
        public String getConfidenceLevel() { return confidenceLevel; }
        public String getExplanation() { return explanation; }
        public String getEvidenceSummary() { return evidenceSummary; }
        public List<String> getEvidenceCitations() { return evidenceCitations; }
        public List<String> getContributingFactors() { return contributingFactors; }
        public List<String> getAlternativeHypotheses() { return alternativeHypotheses; }
        public List<String> getCaveats() { return caveats; }
    }
    
    /**
     * Measurements Section - All deterministic/exact values.
     */
    public static class MeasurementsSection {
        private final Map<String, MeasurementValue> measurements;
        private final String disclaimer;
        
        public MeasurementsSection() {
            this.measurements = new HashMap<>();
            this.disclaimer = "All values are exact measurements calculated deterministically. " +
                    "Same input data will always produce identical values.";
        }
        
        public void addMeasurement(String key, MeasurementValue value) {
            measurements.put(key, value);
        }
        
        public MeasurementValue getMeasurement(String key) {
            return measurements.get(key);
        }
        
        public Map<String, Object> toJsonMap() {
            Map<String, Object> json = new HashMap<>();
            json.put("disclaimer", disclaimer);
            
            Map<String, Object> values = new HashMap<>();
            for (Map.Entry<String, MeasurementValue> entry : measurements.entrySet()) {
                values.put(entry.getKey(), entry.getValue().toJsonMap());
            }
            json.put("values", values);
            
            return json;
        }
        
        public Map<String, MeasurementValue> getMeasurements() { return measurements; }
    }
    
    /**
     * Individual measurement value with metadata.
     */
    public static class MeasurementValue {
        private final String name;
        private final Object value;
        private final String unit;
        private final String formula;
        private final long calculatedAt;
        private final boolean isWithinNormalRange;
        
        public MeasurementValue(String name, Object value, String unit, 
                                 String formula, boolean isWithinNormalRange) {
            this.name = name;
            this.value = value;
            this.unit = unit;
            this.formula = formula;
            this.calculatedAt = System.currentTimeMillis();
            this.isWithinNormalRange = isWithinNormalRange;
        }
        
        public Map<String, Object> toJsonMap() {
            Map<String, Object> json = new HashMap<>();
            json.put("name", name);
            json.put("value", value);
            json.put("unit", unit);
            json.put("formula", formula);
            json.put("calculated_at", calculatedAt);
            json.put("is_within_normal_range", isWithinNormalRange);
            return json;
        }
        
        // Getters
        public String getName() { return name; }
        public Object getValue() { return value; }
        public String getUnit() { return unit; }
        public String getFormula() { return formula; }
        public long getCalculatedAt() { return calculatedAt; }
        public boolean isWithinNormalRange() { return isWithinNormalRange; }
    }
    
    /**
     * AI Insights Section - All AI-generated interpretations.
     */
    public static class AIInsightsSection {
        private final List<AIInsight> insights;
        private final double overallConfidence;
        private final String disclaimer;
        
        public AIInsightsSection(List<AIInsight> insights, double overallConfidence) {
            this.insights = insights;
            this.overallConfidence = overallConfidence;
            this.disclaimer = "AI insights are interpretations based on patterns in data. " +
                    "Each insight includes a confidence score. Lower confidence indicates " +
                    "greater uncertainty and should be treated as hypothesis rather than fact.";
        }
        
        public Map<String, Object> toJsonMap() {
            Map<String, Object> json = new HashMap<>();
            json.put("disclaimer", disclaimer);
            json.put("overall_confidence", overallConfidence);
            json.put("overall_confidence_percent", String.format("%.0f%%", overallConfidence * 100));
            json.put("insight_count", insights.size());
            
            List<Map<String, Object>> insightList = new ArrayList<>();
            for (AIInsight insight : insights) {
                insightList.add(insight.toJsonMap());
            }
            json.put("insights", insightList);
            
            return json;
        }
        
        public List<AIInsight> getInsights() { return insights; }
        public double getOverallConfidence() { return overallConfidence; }
    }
    
    /**
     * Individual AI insight.
     */
    public static class AIInsight {
        private final String category;
        private final String insight;
        private final double confidence;
        private final String confidenceLevel;
        private final List<String> evidence;
        private final List<String> caveats;
        private final boolean actionable;
        
        public AIInsight(String category, String insight, double confidence,
                          List<String> evidence, List<String> caveats, boolean actionable) {
            this.category = category;
            this.insight = insight;
            this.confidence = confidence;
            this.confidenceLevel = determineConfidenceLevel(confidence);
            this.evidence = evidence;
            this.caveats = caveats;
            this.actionable = actionable;
        }
        
        private String determineConfidenceLevel(double score) {
            if (score >= 0.90) return "VERY_HIGH";
            if (score >= 0.80) return "HIGH";
            if (score >= 0.70) return "MODERATE";
            if (score >= 0.60) return "LOW";
            return "INSUFFICIENT";
        }
        
        public Map<String, Object> toJsonMap() {
            Map<String, Object> json = new HashMap<>();
            json.put("category", category);
            json.put("insight", insight);
            json.put("confidence", confidence);
            json.put("confidence_percent", String.format("%.0f%%", confidence * 100));
            json.put("confidence_level", confidenceLevel);
            json.put("evidence", evidence);
            json.put("evidence_count", evidence.size());
            json.put("caveats", caveats);
            json.put("actionable", actionable);
            return json;
        }
        
        // Getters
        public String getCategory() { return category; }
        public String getInsight() { return insight; }
        public double getConfidence() { return confidence; }
        public String getConfidenceLevel() { return confidenceLevel; }
        public List<String> getEvidence() { return evidence; }
        public List<String> getCaveats() { return caveats; }
        public boolean isActionable() { return actionable; }
    }
    
    /**
     * Recommendations Section - Actionable items.
     */
    public static class RecommendationsSection {
        private final List<Recommendation> userRecommendations;
        private final List<Recommendation> engineerRecommendations;
        private final String primaryAction;
        private final ActionUrgency urgency;
        
        public RecommendationsSection(List<Recommendation> userRecommendations,
                                       List<Recommendation> engineerRecommendations,
                                       String primaryAction, ActionUrgency urgency) {
            this.userRecommendations = userRecommendations;
            this.engineerRecommendations = engineerRecommendations;
            this.primaryAction = primaryAction;
            this.urgency = urgency;
        }
        
        public Map<String, Object> toJsonMap() {
            Map<String, Object> json = new HashMap<>();
            json.put("primary_action", primaryAction);
            json.put("urgency", urgency.name());
            json.put("urgency_description", urgency.getDescription());
            
            List<Map<String, Object>> userList = new ArrayList<>();
            for (Recommendation rec : userRecommendations) {
                userList.add(rec.toJsonMap());
            }
            json.put("user_recommendations", userList);
            
            List<Map<String, Object>> engList = new ArrayList<>();
            for (Recommendation rec : engineerRecommendations) {
                engList.add(rec.toJsonMap());
            }
            json.put("engineer_recommendations", engList);
            
            return json;
        }
        
        // Getters
        public List<Recommendation> getUserRecommendations() { return userRecommendations; }
        public List<Recommendation> getEngineerRecommendations() { return engineerRecommendations; }
        public String getPrimaryAction() { return primaryAction; }
        public ActionUrgency getUrgency() { return urgency; }
    }
    
    /**
     * Individual recommendation.
     */
    public static class Recommendation {
        private final int priority;                    // 1 = highest
        private final String action;
        private final String rationale;
        private final String expectedImpact;
        private final ActionUrgency urgency;
        private final String category;                 // "CHARGING", "THERMAL", "USAGE", etc.
        
        public Recommendation(int priority, String action, String rationale,
                               String expectedImpact, ActionUrgency urgency, String category) {
            this.priority = priority;
            this.action = action;
            this.rationale = rationale;
            this.expectedImpact = expectedImpact;
            this.urgency = urgency;
            this.category = category;
        }
        
        public Map<String, Object> toJsonMap() {
            Map<String, Object> json = new HashMap<>();
            json.put("priority", priority);
            json.put("action", action);
            json.put("rationale", rationale);
            json.put("expected_impact", expectedImpact);
            json.put("urgency", urgency.name());
            json.put("urgency_description", urgency.getDescription());
            json.put("category", category);
            return json;
        }
        
        // Getters
        public int getPriority() { return priority; }
        public String getAction() { return action; }
        public String getRationale() { return rationale; }
        public String getExpectedImpact() { return expectedImpact; }
        public ActionUrgency getUrgency() { return urgency; }
        public String getCategory() { return category; }
    }
    
    /**
     * Compliance Section - Regulatory and safety compliance.
     */
    public static class ComplianceSection {
        private final boolean hasComplianceIssues;
        private final List<String> complianceNotes;
        private final boolean meetsSafetyStandards;
        private final List<String> safetyFlags;
        private final String auditTrailNote;
        
        public ComplianceSection(boolean hasComplianceIssues, List<String> complianceNotes,
                                  boolean meetsSafetyStandards, List<String> safetyFlags) {
            this.hasComplianceIssues = hasComplianceIssues;
            this.complianceNotes = complianceNotes;
            this.meetsSafetyStandards = meetsSafetyStandards;
            this.safetyFlags = safetyFlags;
            this.auditTrailNote = "All analysis inputs, calculations, and outputs are logged " +
                    "for audit purposes. Deterministic results are reproducible.";
        }
        
        public Map<String, Object> toJsonMap() {
            Map<String, Object> json = new HashMap<>();
            json.put("has_compliance_issues", hasComplianceIssues);
            json.put("compliance_notes", complianceNotes);
            json.put("meets_safety_standards", meetsSafetyStandards);
            json.put("safety_flags", safetyFlags);
            json.put("audit_trail_note", auditTrailNote);
            return json;
        }
        
        // Getters
        public boolean hasComplianceIssues() { return hasComplianceIssues; }
        public List<String> getComplianceNotes() { return complianceNotes; }
        public boolean meetsSafetyStandards() { return meetsSafetyStandards; }
        public List<String> getSafetyFlags() { return safetyFlags; }
        public String getAuditTrailNote() { return auditTrailNote; }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // BUILDER FOR EASY CONSTRUCTION
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Builder for constructing AIAnalysisReport.
     */
    public static class ReportBuilder {
        private String reportId;
        private String analysisType;
        private String deviceId;
        private DataQualitySection dataQuality;
        private ExecutiveSummary executiveSummary;
        private RootCauseSection rootCause;
        private MeasurementsSection measurements;
        private AIInsightsSection aiInsights;
        private RecommendationsSection recommendations;
        private ComplianceSection compliance;
        
        public ReportBuilder() {
            this.reportId = java.util.UUID.randomUUID().toString();
            this.measurements = new MeasurementsSection();
        }
        
        public ReportBuilder setReportId(String reportId) {
            this.reportId = reportId;
            return this;
        }
        
        public ReportBuilder setAnalysisType(String analysisType) {
            this.analysisType = analysisType;
            return this;
        }
        
        public ReportBuilder setDeviceId(String deviceId) {
            this.deviceId = deviceId;
            return this;
        }
        
        public ReportBuilder setDataQuality(DataQualitySection dataQuality) {
            this.dataQuality = dataQuality;
            return this;
        }
        
        public ReportBuilder setExecutiveSummary(ExecutiveSummary executiveSummary) {
            this.executiveSummary = executiveSummary;
            return this;
        }
        
        public ReportBuilder setRootCause(RootCauseSection rootCause) {
            this.rootCause = rootCause;
            return this;
        }
        
        public ReportBuilder setMeasurements(MeasurementsSection measurements) {
            this.measurements = measurements;
            return this;
        }
        
        public ReportBuilder addMeasurement(String key, MeasurementValue value) {
            this.measurements.addMeasurement(key, value);
            return this;
        }
        
        public ReportBuilder setAiInsights(AIInsightsSection aiInsights) {
            this.aiInsights = aiInsights;
            return this;
        }
        
        public ReportBuilder setRecommendations(RecommendationsSection recommendations) {
            this.recommendations = recommendations;
            return this;
        }
        
        public ReportBuilder setCompliance(ComplianceSection compliance) {
            this.compliance = compliance;
            return this;
        }
        
        public AIAnalysisReport build() {
            // Apply defaults for missing sections
            if (dataQuality == null) {
                dataQuality = new DataQualitySection(0, 0, 0, 0, 0, false, 
                        new ArrayList<>(), List.of("Data quality not assessed"));
            }
            
            if (executiveSummary == null) {
                executiveSummary = new ExecutiveSummary(
                        "Analysis Incomplete",
                        "Unable to generate complete analysis.",
                        Severity.INFO, 0, "Unknown", 0,
                        "No analysis performed",
                        "Collect more data",
                        ActionUrgency.MONITOR_ONLY
                );
            }
            
            if (rootCause == null) {
                rootCause = new RootCauseSection(
                        "UNKNOWN", "UNK_000", 0,
                        "Root cause not determined",
                        "Insufficient data for analysis",
                        new ArrayList<>(), new ArrayList<>(),
                        new ArrayList<>(), new ArrayList<>()
                );
            }
            
            if (aiInsights == null) {
                aiInsights = new AIInsightsSection(new ArrayList<>(), 0);
            }
            
            if (recommendations == null) {
                recommendations = new RecommendationsSection(
                        new ArrayList<>(), new ArrayList<>(),
                        "Continue data collection",
                        ActionUrgency.MONITOR_ONLY
                );
            }
            
            if (compliance == null) {
                compliance = new ComplianceSection(false, new ArrayList<>(), true, new ArrayList<>());
            }
            
            return new AIAnalysisReport(
                    reportId, analysisType, deviceId,
                    dataQuality, executiveSummary, rootCause,
                    measurements, aiInsights, recommendations, compliance
            );
        }
    }
}
