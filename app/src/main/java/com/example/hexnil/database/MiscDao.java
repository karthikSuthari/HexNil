package com.example.hexnil.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.hexnil.model.CalibrationData;
import com.example.hexnil.model.StressTestResult;
import com.example.hexnil.model.UpdateComparison;
import com.example.hexnil.model.AIAnalysisResult;
import com.example.hexnil.model.DeviceInfo;
import com.example.hexnil.model.ComplianceReport;

import java.util.List;

@Dao
public interface MiscDao {
    
    // ==================================================================================
    //                              CALIBRATION DATA - BASIC CRUD
    // ==================================================================================
    
    @Insert
    long insertCalibration(CalibrationData data);
    
    @Update
    void updateCalibration(CalibrationData data);
    
    @Query("SELECT * FROM calibration_data ORDER BY timestamp DESC LIMIT 1")
    CalibrationData getLatestCalibration();
    
    @Query("SELECT * FROM calibration_data WHERE timestamp >= :startTime ORDER BY timestamp DESC")
    List<CalibrationData> getCalibrationHistory(long startTime);
    
    @Query("SELECT * FROM calibration_data WHERE needsCalibration = 1 ORDER BY timestamp DESC LIMIT 1")
    CalibrationData getCalibrationNeeded();
    
    @Query("SELECT * FROM calibration_data WHERE hasSuddenDrops = 1")
    List<CalibrationData> getSuddenDropRecords();
    
    // ==================================================================================
    //                              DRIFT ANALYSIS QUERIES
    // ==================================================================================
    
    /** Get average drift over a time period */
    @Query("SELECT AVG(levelDrift) FROM calibration_data WHERE timestamp >= :startTime")
    float getAverageDrift(long startTime);
    
    /** Get maximum absolute drift */
    @Query("SELECT MAX(ABS(levelDrift)) FROM calibration_data WHERE timestamp >= :startTime")
    float getMaxDrift(long startTime);
    
    /** Get drift standard deviation */
    @Query("SELECT AVG((levelDrift - (SELECT AVG(levelDrift) FROM calibration_data WHERE timestamp >= :startTime)) * " +
           "(levelDrift - (SELECT AVG(levelDrift) FROM calibration_data WHERE timestamp >= :startTime))) " +
           "FROM calibration_data WHERE timestamp >= :startTime")
    float getDriftVariance(long startTime);
    
    /** Get calibration records with significant drift (>8%) */
    @Query("SELECT * FROM calibration_data WHERE ABS(levelDrift) > 8 AND timestamp >= :startTime ORDER BY timestamp DESC")
    List<CalibrationData> getSignificantDriftRecords(long startTime);
    
    /** Get calibration records with severe drift (>12%) */
    @Query("SELECT * FROM calibration_data WHERE ABS(levelDrift) > 12 AND timestamp >= :startTime ORDER BY timestamp DESC")
    List<CalibrationData> getSevereDriftRecords(long startTime);
    
    /** Count records by drift direction */
    @Query("SELECT COUNT(*) FROM calibration_data WHERE driftDirection = :direction AND timestamp >= :startTime")
    int countByDriftDirection(String direction, long startTime);
    
    /** Get drift trend (records ordered by time for analysis) */
    @Query("SELECT * FROM calibration_data WHERE timestamp >= :startTime ORDER BY timestamp ASC LIMIT :limit")
    List<CalibrationData> getDriftTrendData(long startTime, int limit);
    
    /** Get average drift velocity */
    @Query("SELECT AVG(driftVelocity) FROM calibration_data WHERE timestamp >= :startTime AND driftVelocity != 0")
    float getAverageDriftVelocity(long startTime);
    
    // ==================================================================================
    //                              SUDDEN DROP ANALYSIS
    // ==================================================================================
    
    /** Count total sudden drops */
    @Query("SELECT SUM(suddenDropCount) FROM calibration_data WHERE timestamp >= :startTime")
    int getTotalSuddenDrops(long startTime);
    
    /** Get average drop size */
    @Query("SELECT AVG(avgDropSize) FROM calibration_data WHERE hasSuddenDrops = 1 AND timestamp >= :startTime")
    float getAverageDropSize(long startTime);
    
    /** Get worst drop ever */
    @Query("SELECT MAX(worstDrop) FROM calibration_data WHERE timestamp >= :startTime")
    int getWorstDropEver(long startTime);
    
    /** Get records with multiple drops */
    @Query("SELECT * FROM calibration_data WHERE suddenDropCount >= :minDrops AND timestamp >= :startTime ORDER BY suddenDropCount DESC")
    List<CalibrationData> getRecordsWithMultipleDrops(int minDrops, long startTime);
    
    /** Get drops by hour analysis */
    @Query("SELECT peakDropHour, COUNT(*) as count FROM calibration_data WHERE hasSuddenDrops = 1 AND timestamp >= :startTime GROUP BY peakDropHour ORDER BY count DESC")
    List<HourCount> getDropsByHour(long startTime);
    
    // ==================================================================================
    //                              CALIBRATION QUALITY ANALYSIS
    // ==================================================================================
    
    /** Get average calibration quality */
    @Query("SELECT AVG(calibrationQuality) FROM calibration_data WHERE timestamp >= :startTime")
    float getAverageCalibrationQuality(long startTime);
    
    /** Get records with poor calibration (<50%) */
    @Query("SELECT * FROM calibration_data WHERE calibrationQuality < 50 AND timestamp >= :startTime ORDER BY timestamp DESC")
    List<CalibrationData> getPoorCalibrationRecords(long startTime);
    
    /** Get records with critical calibration (<25%) */
    @Query("SELECT * FROM calibration_data WHERE calibrationQuality < 25 AND timestamp >= :startTime ORDER BY timestamp DESC")
    List<CalibrationData> getCriticalCalibrationRecords(long startTime);
    
    /** Count calibrated records */
    @Query("SELECT COUNT(*) FROM calibration_data WHERE isCalibrated = 1 AND timestamp >= :startTime")
    int countCalibratedRecords(long startTime);
    
    /** Get calibration quality trend */
    @Query("SELECT * FROM calibration_data WHERE timestamp >= :startTime ORDER BY timestamp ASC")
    List<CalibrationData> getCalibrationQualityTrend(long startTime);
    
    /** Get quality distribution */
    @Query("SELECT CASE " +
           "WHEN calibrationQuality >= 90 THEN 'Excellent' " +
           "WHEN calibrationQuality >= 75 THEN 'Good' " +
           "WHEN calibrationQuality >= 50 THEN 'Fair' " +
           "WHEN calibrationQuality >= 25 THEN 'Poor' " +
           "ELSE 'Critical' END as grade, COUNT(*) as count " +
           "FROM calibration_data WHERE timestamp >= :startTime " +
           "GROUP BY grade ORDER BY count DESC")
    List<GradeCount> getQualityDistribution(long startTime);
    
    // ==================================================================================
    //                              SUCCESS CRITERIA ANALYSIS
    // ==================================================================================
    
    /** Count records meeting all criteria */
    @Query("SELECT COUNT(*) FROM calibration_data WHERE criteriaMet >= 5 AND timestamp >= :startTime")
    int countFullyPassing(long startTime);
    
    /** Count records meeting most criteria (4+) */
    @Query("SELECT COUNT(*) FROM calibration_data WHERE criteriaMet >= 4 AND timestamp >= :startTime")
    int countMostlyPassing(long startTime);
    
    /** Get average criteria met */
    @Query("SELECT AVG(criteriaMet) FROM calibration_data WHERE timestamp >= :startTime")
    float getAverageCriteriaMet(long startTime);
    
    /** Get records failing specific criterion */
    @Query("SELECT * FROM calibration_data WHERE avgDriftPassing = 0 AND timestamp >= :startTime ORDER BY timestamp DESC")
    List<CalibrationData> getFailingDriftCriterion(long startTime);
    
    @Query("SELECT * FROM calibration_data WHERE velocityPassing = 0 AND timestamp >= :startTime ORDER BY timestamp DESC")
    List<CalibrationData> getFailingVelocityCriterion(long startTime);
    
    @Query("SELECT * FROM calibration_data WHERE noRecentDropsPassing = 0 AND timestamp >= :startTime ORDER BY timestamp DESC")
    List<CalibrationData> getFailingDropsCriterion(long startTime);
    
    // ==================================================================================
    //                              ESTIMATION METHOD ANALYSIS
    // ==================================================================================
    
    /** Get average confidence by method */
    @Query("SELECT AVG(chargeCounterConfidence) FROM calibration_data WHERE chargeCounterConfidence > 0 AND timestamp >= :startTime")
    float getAvgChargeCounterConfidence(long startTime);
    
    @Query("SELECT AVG(voltageConfidence) FROM calibration_data WHERE voltageConfidence > 0 AND timestamp >= :startTime")
    float getAvgVoltageConfidence(long startTime);
    
    @Query("SELECT AVG(energyConfidence) FROM calibration_data WHERE energyConfidence > 0 AND timestamp >= :startTime")
    float getAvgEnergyConfidence(long startTime);
    
    @Query("SELECT AVG(capacityConfidence) FROM calibration_data WHERE capacityConfidence > 0 AND timestamp >= :startTime")
    float getAvgCapacityConfidence(long startTime);
    
    /** Count primary method usage */
    @Query("SELECT primaryMethod, COUNT(*) as count FROM calibration_data WHERE timestamp >= :startTime GROUP BY primaryMethod ORDER BY count DESC")
    List<MethodCount> getPrimaryMethodDistribution(long startTime);
    
    /** Get average method agreement */
    @Query("SELECT AVG(methodAgreement) FROM calibration_data WHERE timestamp >= :startTime")
    float getAverageMethodAgreement(long startTime);
    
    /** Get records with low method agreement (<50%) */
    @Query("SELECT * FROM calibration_data WHERE methodAgreement < 50 AND timestamp >= :startTime ORDER BY timestamp DESC")
    List<CalibrationData> getLowMethodAgreementRecords(long startTime);
    
    // ==================================================================================
    //                              CONFIDENCE ANALYSIS
    // ==================================================================================
    
    /** Get average overall confidence */
    @Query("SELECT AVG(overallConfidence) FROM calibration_data WHERE timestamp >= :startTime")
    float getAverageOverallConfidence(long startTime);
    
    /** Get records with low confidence (<50%) */
    @Query("SELECT * FROM calibration_data WHERE overallConfidence < 50 AND timestamp >= :startTime ORDER BY timestamp DESC")
    List<CalibrationData> getLowConfidenceRecords(long startTime);
    
    /** Get average confidence interval width */
    @Query("SELECT AVG(intervalWidth) FROM calibration_data WHERE timestamp >= :startTime")
    float getAverageConfidenceIntervalWidth(long startTime);
    
    /** Get confidence grade distribution */
    @Query("SELECT confidenceGrade as grade, COUNT(*) as count FROM calibration_data WHERE timestamp >= :startTime GROUP BY confidenceGrade ORDER BY count DESC")
    List<GradeCount> getConfidenceDistribution(long startTime);
    
    // ==================================================================================
    //                              VOLTAGE CORRELATION ANALYSIS
    // ==================================================================================
    
    /** Get average voltage correlation score */
    @Query("SELECT AVG(voltageCorrelationScore) FROM calibration_data WHERE voltageCorrelationScore > 0 AND timestamp >= :startTime")
    float getAverageVoltageCorrelation(long startTime);
    
    /** Get records with poor voltage correlation (<50%) */
    @Query("SELECT * FROM calibration_data WHERE voltageCorrelationScore < 50 AND voltageCorrelationScore > 0 AND timestamp >= :startTime")
    List<CalibrationData> getPoorVoltageCorrelation(long startTime);
    
    /** Count stretched vs compressed curves */
    @Query("SELECT COUNT(*) FROM calibration_data WHERE curveStretched = 1 AND timestamp >= :startTime")
    int countStretchedCurves(long startTime);
    
    @Query("SELECT COUNT(*) FROM calibration_data WHERE curveCompressed = 1 AND timestamp >= :startTime")
    int countCompressedCurves(long startTime);
    
    // ==================================================================================
    //                              URGENCY AND RECOMMENDATIONS
    // ==================================================================================
    
    /** Get records by urgency level */
    @Query("SELECT * FROM calibration_data WHERE calibrationUrgency = :urgency AND timestamp >= :startTime ORDER BY timestamp DESC")
    List<CalibrationData> getByUrgency(String urgency, long startTime);
    
    /** Count records by urgency */
    @Query("SELECT calibrationUrgency, COUNT(*) as count FROM calibration_data WHERE timestamp >= :startTime GROUP BY calibrationUrgency ORDER BY count DESC")
    List<UrgencyCount> getUrgencyDistribution(long startTime);
    
    /** Get most recent urgent calibration needed */
    @Query("SELECT * FROM calibration_data WHERE calibrationUrgency IN ('critical', 'high') ORDER BY timestamp DESC LIMIT 1")
    CalibrationData getUrgentCalibrationNeeded();
    
    /** Get upcoming calibration due dates */
    @Query("SELECT * FROM calibration_data WHERE nextCalibrationDate > 0 AND nextCalibrationDate <= :beforeTime ORDER BY nextCalibrationDate ASC")
    List<CalibrationData> getUpcomingCalibrations(long beforeTime);
    
    // ==================================================================================
    //                              HEALTH IMPACT ANALYSIS
    // ==================================================================================
    
    /** Get average health impact score */
    @Query("SELECT AVG(healthImpactScore) FROM calibration_data WHERE timestamp >= :startTime")
    float getAverageHealthImpact(long startTime);
    
    /** Get records with high health impact (>30) */
    @Query("SELECT * FROM calibration_data WHERE healthImpactScore > 30 AND timestamp >= :startTime ORDER BY healthImpactScore DESC")
    List<CalibrationData> getHighHealthImpactRecords(long startTime);
    
    /** Count records affecting health accuracy */
    @Query("SELECT COUNT(*) FROM calibration_data WHERE affectsHealthAccuracy = 1 AND timestamp >= :startTime")
    int countAffectingHealthAccuracy(long startTime);
    
    // ==================================================================================
    //                              PATTERN ANALYSIS
    // ==================================================================================
    
    /** Get dominant pattern distribution */
    @Query("SELECT dominantPattern, COUNT(*) as count FROM calibration_data WHERE timestamp >= :startTime GROUP BY dominantPattern ORDER BY count DESC")
    List<PatternCount> getDominantPatternDistribution(long startTime);
    
    /** Get trend direction distribution */
    @Query("SELECT trendDirection, COUNT(*) as count FROM calibration_data WHERE timestamp >= :startTime GROUP BY trendDirection ORDER BY count DESC")
    List<TrendCount> getTrendDirectionDistribution(long startTime);
    
    /** Get records with worsening trend */
    @Query("SELECT * FROM calibration_data WHERE trendDirection = 'worsening' AND timestamp >= :startTime ORDER BY timestamp DESC")
    List<CalibrationData> getWorseningTrendRecords(long startTime);
    
    // ==================================================================================
    //                              AGGREGATE STATISTICS
    // ==================================================================================
    
    /** Get comprehensive statistics for a time period */
    @Query("SELECT COUNT(*) as totalRecords, " +
           "AVG(calibrationQuality) as avgQuality, " +
           "AVG(levelDrift) as avgDrift, " +
           "MAX(ABS(levelDrift)) as maxDrift, " +
           "SUM(suddenDropCount) as totalDrops, " +
           "AVG(overallConfidence) as avgConfidence, " +
           "AVG(healthImpactScore) as avgHealthImpact " +
           "FROM calibration_data WHERE timestamp >= :startTime")
    CalibrationStats getCalibrationStats(long startTime);
    
    /** Get calibration success rate */
    @Query("SELECT (CAST(SUM(CASE WHEN isCalibrated = 1 THEN 1 ELSE 0 END) AS FLOAT) / COUNT(*)) * 100 " +
           "FROM calibration_data WHERE timestamp >= :startTime")
    float getCalibrationSuccessRate(long startTime);
    
    /** Count total records */
    @Query("SELECT COUNT(*) FROM calibration_data WHERE timestamp >= :startTime")
    int getCalibrationRecordCount(long startTime);
    
    // ==================================================================================
    //                              RESULT CLASSES FOR AGGREGATE QUERIES
    // ==================================================================================
    
    class HourCount {
        public int peakDropHour;
        public int count;
    }
    
    class GradeCount {
        public String grade;
        public int count;
    }
    
    class MethodCount {
        public String primaryMethod;
        public int count;
    }
    
    class UrgencyCount {
        public String calibrationUrgency;
        public int count;
    }
    
    class PatternCount {
        public String dominantPattern;
        public int count;
    }
    
    class TrendCount {
        public String trendDirection;
        public int count;
    }
    
    class CalibrationStats {
        public int totalRecords;
        public float avgQuality;
        public float avgDrift;
        public float maxDrift;
        public int totalDrops;
        public float avgConfidence;
        public float avgHealthImpact;
    }
    
    // Stress Test Results
    @Insert
    long insertStressTest(StressTestResult result);
    
    @Update
    void updateStressTest(StressTestResult result);
    
    @Query("SELECT * FROM stress_test_results ORDER BY timestamp DESC")
    List<StressTestResult> getAllStressTests();
    
    @Query("SELECT * FROM stress_test_results WHERE testType = :type ORDER BY timestamp DESC")
    List<StressTestResult> getStressTestsByType(String type);
    
    @Query("SELECT * FROM stress_test_results WHERE passed = 0")
    List<StressTestResult> getFailedStressTests();
    
    @Query("SELECT * FROM stress_test_results ORDER BY timestamp DESC LIMIT 1")
    StressTestResult getLatestStressTest();
    
    // Update Comparisons
    @Insert
    long insertUpdateComparison(UpdateComparison comparison);
    
    @Update
    void updateUpdateComparison(UpdateComparison comparison);
    
    @Query("SELECT * FROM update_comparisons ORDER BY timestamp DESC")
    List<UpdateComparison> getAllUpdateComparisons();
    
    @Query("SELECT * FROM update_comparisons WHERE hasDrainRegression = 1 OR hasThermalRegression = 1")
    List<UpdateComparison> getRegressionComparisons();
    
    @Query("SELECT * FROM update_comparisons ORDER BY timestamp DESC LIMIT 1")
    UpdateComparison getLatestUpdateComparison();
    
    // AI Analysis Results
    @Insert
    long insertAIAnalysis(AIAnalysisResult result);
    
    @Update
    void updateAIAnalysis(AIAnalysisResult result);
    
    @Query("SELECT * FROM ai_analysis_results ORDER BY timestamp DESC")
    List<AIAnalysisResult> getAllAIAnalyses();
    
    @Query("SELECT * FROM ai_analysis_results ORDER BY timestamp DESC")
    List<AIAnalysisResult> getAllAIAnalysisResults();
    
    @Query("SELECT * FROM ai_analysis_results WHERE analysisType = :type ORDER BY timestamp DESC")
    List<AIAnalysisResult> getAIAnalysesByType(String type);
    
    @Query("SELECT * FROM ai_analysis_results WHERE severity = 'critical' OR severity = 'high'")
    List<AIAnalysisResult> getCriticalAIAnalyses();
    
    @Query("SELECT * FROM ai_analysis_results ORDER BY timestamp DESC LIMIT 1")
    AIAnalysisResult getLatestAIAnalysis();
    
    @Query("SELECT * FROM ai_analysis_results WHERE requiresAction = 1")
    List<AIAnalysisResult> getActionRequiredAnalyses();
    
    // Device Info
    @Insert
    long insertDeviceInfo(DeviceInfo info);
    
    @Update
    void updateDeviceInfo(DeviceInfo info);
    
    @Query("SELECT * FROM device_info ORDER BY timestamp DESC LIMIT 1")
    DeviceInfo getDeviceInfo();
    
    // Compliance Reports
    @Insert
    long insertComplianceReport(ComplianceReport report);
    
    @Update
    void updateComplianceReport(ComplianceReport report);
    
    @Query("SELECT * FROM compliance_reports ORDER BY timestamp DESC")
    List<ComplianceReport> getAllComplianceReports();
    
    @Query("SELECT * FROM compliance_reports WHERE safetyCompliant = 0")
    List<ComplianceReport> getNonCompliantReports();
    
    @Query("SELECT * FROM compliance_reports ORDER BY timestamp DESC LIMIT 1")
    ComplianceReport getLatestComplianceReport();
    
    @Query("SELECT * FROM compliance_reports WHERE reportType = :type ORDER BY timestamp DESC")
    List<ComplianceReport> getComplianceReportsByType(String type);
    
    // Cleanup queries
    @Query("DELETE FROM calibration_data WHERE timestamp < :beforeTime")
    void deleteOldCalibrationData(long beforeTime);
    
    @Query("DELETE FROM stress_test_results WHERE timestamp < :beforeTime")
    void deleteOldStressTests(long beforeTime);
    
    @Query("DELETE FROM update_comparisons WHERE timestamp < :beforeTime")
    void deleteOldUpdateComparisons(long beforeTime);
    
    @Query("DELETE FROM ai_analysis_results WHERE timestamp < :beforeTime")
    void deleteOldAIAnalyses(long beforeTime);
    
    @Query("DELETE FROM compliance_reports WHERE timestamp < :beforeTime")
    void deleteOldComplianceReports(long beforeTime);
}
