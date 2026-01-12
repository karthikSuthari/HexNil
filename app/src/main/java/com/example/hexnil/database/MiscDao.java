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
    
    // Calibration Data
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
