package com.example.hexnil.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Records results from battery stress tests
 * Validates device behavior under extreme conditions
 */
@Entity(tableName = "stress_test_results")
public class StressTestResult {
    
    @PrimaryKey(autoGenerate = true)
    public long id;
    
    public long timestamp;
    public long testStartTime;
    public long testEndTime;
    
    // Test configuration
    public String testType;              // "gaming", "camera", "charging", "combined", "thermal"
    public String testName;              // Human-readable test name
    public int testDurationMinutes;
    public String testParameters;        // JSON of test config
    public boolean highTemperatureScenario; // Flag if test targets hot conditions
    
    // Battery metrics
    public int startLevel;
    public int endLevel;
    public float drainAmount;
    public float drainRate;              // %/hour
    public float peakDrainRate;          // Maximum drain rate observed
    
    // Thermal metrics
    public float startTemperature;
    public float maxTemperature;
    public float endTemperature;
    public float avgTemperature;
    public int thermalThrottleEvents;    // Times device throttled due to heat
    public long thermalThrottleTime;     // Total time throttled
    public int safetyTriggerCount;       // Times safety/thermal triggers fired
    public String safetyNotes;           // Notes for safety triggers
    
    // Performance metrics
    public float avgCpuUsage;
    public float maxCpuUsage;
    public float avgGpuUsage;
    public float maxGpuUsage;
    public float avgMemoryUsage;
    public float maxMemoryUsage;
    
    // Charging stress (if applicable)
    public int chargeGained;             // % charged during test
    public float avgChargingCurrent;
    public float maxChargingCurrent;
    public int chargingInterruptions;
    
    // Stability metrics
    public boolean testCompleted;        // Did test finish without issues
    public int crashCount;               // App/system crashes
    public int restartCount;             // Forced restarts
    public int errorCount;               // Errors during test
    public String failureReasons;        // If test failed, why
    
    // Benchmark scores
    public float drainScore;             // 0-100 (higher = better)
    public float thermalScore;           // 0-100 (higher = better)
    public float stabilityScore;         // 0-100 (higher = better)
    public float thermalResilienceScore; // 0-100 (lower peaks + fewer throttles)
    public float chargingStabilityScore; // 0-100 (interruptions/overheats)
    public float overallScore;           // Combined score
    
    // Comparison with baseline
    public float baselineDrain;          // Expected drain for this test
    public float drainVariance;          // % difference from baseline
    public float baselineThermal;        // Expected max temp
    public float thermalVariance;        // Difference from baseline
    
    // Pass/Fail verdict
    public boolean passed;
    public String verdict;               // "pass", "marginal", "fail"
    public String verdictDetails;        // Explanation of verdict
    
    // Detailed logs
    public String testLogs;              // JSON of timestamped events
    public String recommendations;       // Suggestions based on results
    
    public StressTestResult() {
        this.timestamp = System.currentTimeMillis();
    }
    
    public void calculateScores() {
        // Drain score (lower drain = higher score)
        if (drainRate < 10) drainScore = 100;
        else if (drainRate < 20) drainScore = 80;
        else if (drainRate < 30) drainScore = 60;
        else if (drainRate < 40) drainScore = 40;
        else drainScore = 20;
        
        // Thermal score (lower temp = higher score)
        if (maxTemperature < 38) thermalScore = 100;
        else if (maxTemperature < 42) thermalScore = 80;
        else if (maxTemperature < 45) thermalScore = 60;
        else if (maxTemperature < 48) thermalScore = 40;
        else thermalScore = 20;

        // Thermal resilience blends peak temp and throttling frequency
        float throttlePenalty = Math.min(40, thermalThrottleEvents * 8f);
        thermalResilienceScore = Math.max(0, thermalScore - throttlePenalty);
        
        // Stability score
        if (testCompleted && crashCount == 0 && errorCount == 0) {
            stabilityScore = 100;
        } else if (testCompleted) {
            stabilityScore = 100 - (crashCount * 20) - (errorCount * 5);
        } else {
            stabilityScore = 30;
        }
        stabilityScore = Math.max(0, stabilityScore);

        // Charging stability (interruptions, thermal during charge)
        if ("charging".equals(testType)) {
            float interruptionPenalty = Math.min(40, chargingInterruptions * 10f);
            float thermalPenalty = maxTemperature > 45 ? 20 : 0;
            chargingStabilityScore = Math.max(0, 100 - interruptionPenalty - thermalPenalty);
        } else {
            chargingStabilityScore = 100; // Not applicable, keep neutral
        }
        
        // Overall score (weighted average)
        overallScore = (drainScore * 0.30f) + (thermalResilienceScore * 0.30f)
                + (stabilityScore * 0.20f) + (chargingStabilityScore * 0.20f);
        
        // Set verdict
        if (overallScore >= 80) {
            passed = true;
            verdict = "pass";
        } else if (overallScore >= 60) {
            passed = true;
            verdict = "marginal";
        } else {
            passed = false;
            verdict = "fail";
        }
    }
}
