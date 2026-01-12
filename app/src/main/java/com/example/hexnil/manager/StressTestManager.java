package com.example.hexnil.manager;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.model.StressTestResult;

/**
 * Orchestrates scripted stress tests and computes deterministic scores.
 */
public class StressTestManager {

    private final HexNilDatabase database;
    private StressTestResult active;
    private float cpuSum;
    private float gpuSum;
    private float memSum;
    private float tempSum;
    private int sampleCount;

    public StressTestManager(HexNilDatabase database) {
        this.database = database;
    }

    // Scripted runners for reproducible scenarios
    public void startGamingStress(int durationMinutes, int startLevel, float startTemperature) {
        startTest("gaming", "Gaming Stress", durationMinutes,
                "{\"load\":\"gpu+cpu\",\"network\":\"wifi\",\"brightness\":80}",
                startLevel, startTemperature);
    }

    public void startCameraGpsNetwork(int durationMinutes, int startLevel, float startTemperature) {
        startTest("camera_gps_network", "Camera+GPS+Network", durationMinutes,
                "{\"camera\":true,\"gps\":true,\"network\":\"cell+wifi\"}",
                startLevel, startTemperature);
    }

    public void startChargingStress(int durationMinutes, int startLevel, float startTemperature) {
        startTest("charging", "Continuous Charging", durationMinutes,
                "{\"charger\":\"fast\",\"screen\":\"off\"}",
                startLevel, startTemperature);
    }

    public void startThermalFlag(int durationMinutes, int startLevel, float startTemperature) {
        startTest("thermal", "High Temp Scenario", durationMinutes,
                "{\"environment\":\"hot\",\"load\":\"mixed\"}",
                startLevel, startTemperature);
        if (active != null) {
            active.highTemperatureScenario = true;
        }
    }

    public void startTest(String testType, String testName, int durationMinutes, String parameters, int startLevel, float startTemperature) {
        active = new StressTestResult();
        active.testType = testType;
        active.testName = testName;
        active.testDurationMinutes = durationMinutes;
        active.testParameters = parameters;
        active.testStartTime = System.currentTimeMillis();
        active.startLevel = startLevel;
        active.startTemperature = startTemperature;
        resetAccumulators();
    }

    public void recordSample(int level, float temperatureC, float cpuUsage, float gpuUsage, float memoryUsage, boolean throttled) {
        if (active == null) return;
        cpuSum += cpuUsage;
        gpuSum += gpuUsage;
        memSum += memoryUsage;
        tempSum += temperatureC;
        sampleCount++;

        active.maxCpuUsage = Math.max(active.maxCpuUsage, cpuUsage);
        active.maxGpuUsage = Math.max(active.maxGpuUsage, gpuUsage);
        active.maxMemoryUsage = Math.max(active.maxMemoryUsage, memoryUsage);
        active.maxTemperature = Math.max(active.maxTemperature, temperatureC);
        if (throttled) {
            active.thermalThrottleEvents += 1;
        }
        if (active.highTemperatureScenario || temperatureC >= 45f) {
            active.highTemperatureScenario = true;
        }
    }

    public void recordSafetyTrigger(String note) {
        if (active == null) return;
        active.safetyTriggerCount += 1;
        if (active.safetyNotes == null || active.safetyNotes.isEmpty()) {
            active.safetyNotes = note;
        } else {
            active.safetyNotes = active.safetyNotes + " | " + note;
        }
    }

    public StressTestResult finishTest(int endLevel, float endTemperature, int crashCount, int errorCount, int restartCount, boolean completed) {
        if (active == null) return null;
        active.testEndTime = System.currentTimeMillis();
        active.endLevel = endLevel;
        active.endTemperature = endTemperature;
        active.testCompleted = completed;
        active.crashCount = crashCount;
        active.errorCount = errorCount;
        active.restartCount = restartCount;

        long durationMs = Math.max(1, active.testEndTime - active.testStartTime);
        float hours = durationMs / 3_600_000f;
        active.drainAmount = Math.max(0, active.startLevel - active.endLevel);
        active.drainRate = hours > 0 ? active.drainAmount / hours : 0f;
        active.peakDrainRate = active.drainRate;

        active.avgCpuUsage = sampleCount > 0 ? cpuSum / sampleCount : active.maxCpuUsage;
        active.avgGpuUsage = sampleCount > 0 ? gpuSum / sampleCount : active.maxGpuUsage;
        active.avgMemoryUsage = sampleCount > 0 ? memSum / sampleCount : active.maxMemoryUsage;
        active.avgTemperature = sampleCount > 0 ? tempSum / sampleCount : active.maxTemperature;

        float baselineDrain = baselineDrainRate(active.testType);
        float baselineThermal = baselineThermalPeak(active.testType);
        active.baselineDrain = baselineDrain;
        active.drainVariance = active.drainRate - baselineDrain;
        active.baselineThermal = baselineThermal;
        active.thermalVariance = active.maxTemperature - baselineThermal;

        active.chargeGained = active.drainAmount < 0 ? Math.abs((int) active.drainAmount) : 0;
        active.avgChargingCurrent = 0;
        active.maxChargingCurrent = 0;

        // Charging stress: mark interruptions and charging stability inputs
        // chargingInterruptions is tracked during snapshot accumulation

        active.calculateScores();
        active.recommendations = buildRecommendations(active);
        active.verdictDetails = buildVerdict(active);

        database.miscDao().insertStressTest(active);
        StressTestResult result = active;
        active = null;
        return result;
    }

    private void resetAccumulators() {
        cpuSum = 0f;
        gpuSum = 0f;
        memSum = 0f;
        tempSum = 0f;
        sampleCount = 0;
    }

    private float baselineDrainRate(String type) {
        if ("gaming".equals(type)) return 18f;
        if ("camera".equals(type)) return 15f;
        if ("gps".equals(type)) return 12f;
        if ("thermal".equals(type)) return 10f;
        return 8f;
    }

    private float baselineThermalPeak(String type) {
        if ("gaming".equals(type)) return 44f;
        if ("camera".equals(type)) return 42f;
        if ("gps".equals(type)) return 40f;
        if ("thermal".equals(type)) return 41f;
        return 38f;
    }

    private String buildRecommendations(StressTestResult result) {
        StringBuilder sb = new StringBuilder();
        if (result.drainRate > result.baselineDrain * 1.2f) {
            sb.append("High drain observed; optimize GPU/CPU load. ");
        }
        if (result.maxTemperature > result.baselineThermal + 3f) {
            sb.append("Thermals elevated; check throttling and cooling paths. ");
        }
        if (!result.testCompleted || result.crashCount > 0) {
            sb.append("Stability issues detected; review crash logs. ");
        }
        if (sb.length() == 0) {
            sb.append("Within expected range.");
        }
        return sb.toString().trim();
    }

    private String buildVerdict(StressTestResult result) {
        if (!result.passed) return "Fail due to low score";
        if ("marginal".equals(result.verdict)) return "Marginal pass; monitor thermals";
        return "Pass";
    }
}
