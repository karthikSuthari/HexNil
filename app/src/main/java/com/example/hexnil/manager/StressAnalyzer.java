package com.example.hexnil.manager;

import java.util.ArrayList;
import java.util.List;

/**
 * OEM-Grade Stress Analyzer
 * 
 * Deterministic stress calculation engine using sliding window analysis.
 * Computes stress scores based on multiple correlated factors:
 * 
 * 1. CPU Trend (30% weight): Average CPU + acceleration
 * 2. GPU Trend (25% weight): Average GPU + acceleration
 * 3. Thermal Rise (25% weight): Temperature delta + peak
 * 4. Drain Rate (15% weight): Drain rate vs baseline
 * 5. Throttle Events (5% weight): Throttle count × severity
 * 
 * Stress Categories:
 * - LOW (0-25): Short spikes, no sustained load
 * - MEDIUM (26-50): Sustained load without throttling
 * - HIGH (51-75): Sustained load with thermal rise
 * - CRITICAL (76-100): Throttling active, system instability
 * 
 * All calculations are deterministic and explainable.
 */
public class StressAnalyzer {

    private static final String TAG = "StressAnalyzer";

    // =========================================================================
    // CONFIGURATION
    // =========================================================================

    /** Sliding window size in milliseconds (5 minutes default) */
    private static final long WINDOW_SIZE_MS = 5 * 60 * 1000;

    /** Minimum samples required for reliable analysis */
    private static final int MIN_SAMPLES_FOR_ANALYSIS = 10;

    /** Maximum samples to keep in buffer */
    private static final int MAX_BUFFER_SIZE = 100;

    // Scoring weights
    private static final float WEIGHT_CPU = 0.30f;
    private static final float WEIGHT_GPU = 0.25f;
    private static final float WEIGHT_THERMAL = 0.25f;
    private static final float WEIGHT_DRAIN = 0.15f;
    private static final float WEIGHT_THROTTLE = 0.05f;

    // Thresholds
    private static final float HIGH_CPU_THRESHOLD = 60.0f;
    private static final float HIGH_GPU_THRESHOLD = 50.0f;
    private static final float HIGH_TEMP_THRESHOLD = 40.0f;
    private static final float CRITICAL_TEMP_THRESHOLD = 45.0f;
    private static final float HIGH_DRAIN_RATE = 20.0f; // %/hour

    // Category boundaries
    private static final float LOW_MAX = 25.0f;
    private static final float MEDIUM_MAX = 50.0f;
    private static final float HIGH_MAX = 75.0f;

    // =========================================================================
    // DATA STRUCTURES
    // =========================================================================

    /**
     * Individual sample for stress analysis.
     */
    public static class StressSample {
        public long timestamp;
        public int cpuPercent;
        public int gpuPercent;
        public float temperature;
        public int batteryLevel;
        public float drainRatePerHour;
        public boolean isThrottled;
        public int throttleLevel;
        public int thermalStatus; // PowerManager.THERMAL_STATUS_X

        // Behavioral signals
        public boolean isScreenOn;
        public int brightness;
        public String foregroundApp;
        public boolean isCharging;

        public StressSample() {
            this.timestamp = System.currentTimeMillis();
        }

        public StressSample(int cpuPercent, int gpuPercent, float temperature,
                int batteryLevel, float drainRatePerHour,
                boolean isThrottled, int throttleLevel, int thermalStatus,
                boolean isScreenOn, int brightness, String foregroundApp, boolean isCharging) {
            this();
            this.cpuPercent = cpuPercent;
            this.gpuPercent = gpuPercent;
            this.temperature = temperature;
            this.batteryLevel = batteryLevel;
            this.drainRatePerHour = drainRatePerHour;
            this.isThrottled = isThrottled;
            this.throttleLevel = throttleLevel;
            this.thermalStatus = thermalStatus;
            this.isScreenOn = isScreenOn;
            this.brightness = brightness;
            this.foregroundApp = foregroundApp;
            this.isCharging = isCharging;
        }
    }

    /**
     * Stress analysis result with full breakdown.
     */
    public static class StressResult {
        /** Overall stress score (0-100) */
        public float stressScore = 0;

        /** Stress category: LOW, MEDIUM, HIGH, CRITICAL */
        public String stressCategory = "LOW";

        /** CPU contribution to score (0-30) */
        public float cpuContribution = 0;

        /** GPU contribution to score (0-25) */
        public float gpuContribution = 0;

        /** Thermal contribution to score (0-25) */
        public float thermalContribution = 0;

        /** Drain rate contribution to score (0-15) */
        public float drainContribution = 0;

        /** Throttle contribution to score (0-5) */
        public float throttleContribution = 0;

        /** Human-readable explanation of contributing factors */
        public String explanation = "";

        /** Whether analysis is reliable (sufficient samples) */
        public boolean isReliable = false;

        /** Number of samples used in calculation */
        public int sampleCount = 0;

        /** Analysis window duration in ms */
        public long windowDurationMs = 0;

        /** Average CPU in window */
        public float avgCpu = 0;

        /** Average GPU in window */
        public float avgGpu = 0;

        /** Average temperature in window */
        public float avgTemp = 0;

        /** Peak temperature in window */
        public float peakTemp = 0;

        /** Temperature rise during window */
        public float tempRise = 0;

        /** Throttle event count in window */
        public int throttleCount = 0;

        // Inference results
        public String inferredLoad = "LOW";
        public String confidenceLevel = "LOW";
        public String detectionMethod = "Direct hardware counters";
        public List<String> observedSignals = new ArrayList<>();

        @Override
        public String toString() {
            if ("Indirect behavioral inference".equals(detectionMethod)) {
                return "System Load Analysis\n" +
                        "CPU/GPU Raw Metrics: Restricted (Android security)\n" +
                        "Load Detection Method: Indirect behavioral inference\n" +
                        "Observed Signals: " + observedSignals + "\n" +
                        "Inferred CPU/GPU Load: " + inferredLoad + "\n" +
                        "Stress Score: " + String.format("%.0f", stressScore) + " / 100\n" +
                        "Stress Category: " + stressCategory + "\n" +
                        "Confidence Level: " + confidenceLevel;
            }
            return "StressResult{" +
                    "score=" + String.format("%.1f", stressScore) +
                    ", category=" + stressCategory +
                    ", reliable=" + isReliable +
                    ", samples=" + sampleCount + "}";
        }
    }

    // =========================================================================
    // STATE
    // =========================================================================

    /** Sliding window sample buffer */
    private final List<StressSample> sampleBuffer = new ArrayList<>();

    /** Lock for thread safety */
    private final Object bufferLock = new Object();

    // =========================================================================
    // CONSTRUCTOR
    // =========================================================================

    public StressAnalyzer() {
        // Default constructor
    }

    // =========================================================================
    // SAMPLE MANAGEMENT
    // =========================================================================

    /**
     * Add a sample to the sliding window.
     * 
     * @param sample The stress sample to add
     */
    public void addSample(StressSample sample) {
        if (sample == null)
            return;

        synchronized (bufferLock) {
            // Add new sample
            sampleBuffer.add(sample);

            // Prune old samples outside window
            long cutoff = System.currentTimeMillis() - WINDOW_SIZE_MS;
            while (!sampleBuffer.isEmpty() && sampleBuffer.get(0).timestamp < cutoff) {
                sampleBuffer.remove(0);
            }

            // Enforce max buffer size
            while (sampleBuffer.size() > MAX_BUFFER_SIZE) {
                sampleBuffer.remove(0);
            }
        }
    }

    /**
     * Clear the sample buffer (call on session reset).
     */
    public void reset() {
        synchronized (bufferLock) {
            sampleBuffer.clear();
        }
    }

    /**
     * Get current buffer size.
     */
    public int getBufferSize() {
        synchronized (bufferLock) {
            return sampleBuffer.size();
        }
    }

    // =========================================================================
    // STRESS ANALYSIS
    // =========================================================================

    /**
     * Analyze current stress level from the sliding window.
     * 
     * @return StressResult with full breakdown
     */
    public StressResult analyze() {
        StressResult result = new StressResult();

        synchronized (bufferLock) {
            result.sampleCount = sampleBuffer.size();

            // Check minimum sample requirement
            if (sampleBuffer.size() < MIN_SAMPLES_FOR_ANALYSIS) {
                result.isReliable = false;
                result.explanation = "Insufficient samples (" + sampleBuffer.size() +
                        "/" + MIN_SAMPLES_FOR_ANALYSIS + " required)";
                return result;
            }

            result.isReliable = true;

            // Calculate window duration
            StressSample first = sampleBuffer.get(0);
            StressSample last = sampleBuffer.get(sampleBuffer.size() - 1);
            result.windowDurationMs = last.timestamp - first.timestamp;

            // Calculate metrics
            calculateMetrics(result);

            // Calculate contributions
            calculateCpuContribution(result);
            calculateGpuContribution(result);
            calculateThermalContribution(result);
            calculateDrainContribution(result);
            calculateThrottleContribution(result);

            // Sum contributions
            result.stressScore = result.cpuContribution + result.gpuContribution +
                    result.thermalContribution + result.drainContribution +
                    result.throttleContribution;

            // Clamp to 0-100
            result.stressScore = Math.max(0, Math.min(100, result.stressScore));

            // Determine category
            result.stressCategory = categorize(result.stressScore);

            // Behavioral Inference (Android 10+ restricted fallback)
            runBehavioralInference(result);

            // Build explanation
            result.explanation = buildExplanation(result);
        }

        return result;
    }

    /**
     * Quick stress check without full analysis.
     * 
     * @return Current stress category
     */
    public String getCurrentCategory() {
        return analyze().stressCategory;
    }

    /**
     * Quick stress score check.
     * 
     * @return Current stress score (0-100)
     */
    public float getCurrentScore() {
        return analyze().stressScore;
    }

    // =========================================================================
    // METRIC CALCULATION
    // =========================================================================

    private void calculateMetrics(StressResult result) {
        float cpuSum = 0, gpuSum = 0, tempSum = 0;
        float peakTemp = Float.MIN_VALUE;
        float firstTemp = 0, lastTemp = 0;
        int throttleCount = 0;
        int validGpuCount = 0;

        for (int i = 0; i < sampleBuffer.size(); i++) {
            StressSample s = sampleBuffer.get(i);

            // CPU average (ignore -1 values)
            if (s.cpuPercent >= 0) {
                cpuSum += s.cpuPercent;
            }

            // GPU average (ignore -1 values)
            if (s.gpuPercent >= 0) {
                gpuSum += s.gpuPercent;
                validGpuCount++;
            }

            // Temperature
            tempSum += s.temperature;
            peakTemp = Math.max(peakTemp, s.temperature);

            if (i == 0)
                firstTemp = s.temperature;
            if (i == sampleBuffer.size() - 1)
                lastTemp = s.temperature;

            // Throttle events
            if (s.isThrottled) {
                throttleCount++;
            }
        }

        int count = sampleBuffer.size();
        result.avgCpu = cpuSum / count;
        result.avgGpu = validGpuCount > 0 ? gpuSum / validGpuCount : 0;
        result.avgTemp = tempSum / count;
        result.peakTemp = peakTemp;
        result.tempRise = lastTemp - firstTemp;
        result.throttleCount = throttleCount;
    }

    // =========================================================================
    // CONTRIBUTION CALCULATIONS
    // =========================================================================

    /**
     * CPU Contribution (0-30 points, 30% weight)
     * 
     * Factors:
     * - Average CPU load
     * - Sustained high CPU (acceleration)
     */
    private void calculateCpuContribution(StressResult result) {
        float maxPoints = 100 * WEIGHT_CPU; // 30 points

        // Base: Average CPU normalized to 0-100
        float cpuScore = result.avgCpu;

        // Bonus for sustained high CPU
        if (result.avgCpu > HIGH_CPU_THRESHOLD) {
            float sustainedBonus = (result.avgCpu - HIGH_CPU_THRESHOLD) * 0.5f;
            cpuScore += sustainedBonus;
        }

        // Normalize to contribution range
        result.cpuContribution = Math.min(maxPoints, (cpuScore / 100) * maxPoints);
    }

    /**
     * GPU Contribution (0-25 points, 25% weight)
     * 
     * Factors:
     * - Average GPU load
     * - Sustained high GPU
     */
    private void calculateGpuContribution(StressResult result) {
        float maxPoints = 100 * WEIGHT_GPU; // 25 points

        // If no GPU data, don't contribute negative score
        if (result.avgGpu <= 0) {
            result.gpuContribution = 0;
            return;
        }

        float gpuScore = result.avgGpu;

        // Bonus for sustained high GPU
        if (result.avgGpu > HIGH_GPU_THRESHOLD) {
            float sustainedBonus = (result.avgGpu - HIGH_GPU_THRESHOLD) * 0.5f;
            gpuScore += sustainedBonus;
        }

        result.gpuContribution = Math.min(maxPoints, (gpuScore / 100) * maxPoints);
    }

    /**
     * Thermal Contribution (0-25 points, 25% weight)
     * 
     * Factors:
     * - Peak temperature
     * - Temperature rise during window
     * - Sustained high temperature
     */
    private void calculateThermalContribution(StressResult result) {
        float maxPoints = 100 * WEIGHT_THERMAL; // 25 points
        float thermalScore = 0;

        // Peak temperature scoring
        if (result.peakTemp >= CRITICAL_TEMP_THRESHOLD) {
            thermalScore += 60; // Critical temperature
        } else if (result.peakTemp >= HIGH_TEMP_THRESHOLD) {
            thermalScore += 30 + ((result.peakTemp - HIGH_TEMP_THRESHOLD) * 6);
        } else if (result.peakTemp >= 35) {
            thermalScore += (result.peakTemp - 35) * 6;
        }

        // Temperature rise bonus
        if (result.tempRise > 3) {
            thermalScore += 10 + (result.tempRise - 3) * 2;
        } else if (result.tempRise > 1) {
            thermalScore += result.tempRise * 3;
        }

        // Sustained high temp
        if (result.avgTemp > HIGH_TEMP_THRESHOLD) {
            thermalScore += (result.avgTemp - HIGH_TEMP_THRESHOLD) * 3;
        }

        result.thermalContribution = Math.min(maxPoints, (Math.min(100, thermalScore) / 100) * maxPoints);
    }

    /**
     * Drain Contribution (0-15 points, 15% weight)
     * 
     * Factors:
     * - Drain rate vs normal baseline
     */
    private void calculateDrainContribution(StressResult result) {
        float maxPoints = 100 * WEIGHT_DRAIN; // 15 points

        // Calculate average drain rate from samples
        float drainSum = 0;
        int count = 0;
        for (StressSample s : sampleBuffer) {
            if (s.drainRatePerHour > 0) {
                drainSum += s.drainRatePerHour;
                count++;
            }
        }

        if (count == 0) {
            result.drainContribution = 0;
            return;
        }

        float avgDrain = drainSum / count;
        float drainScore = 0;

        // Score based on drain rate
        if (avgDrain > HIGH_DRAIN_RATE * 2) {
            drainScore = 100; // Very high drain
        } else if (avgDrain > HIGH_DRAIN_RATE) {
            drainScore = 50 + ((avgDrain - HIGH_DRAIN_RATE) / HIGH_DRAIN_RATE) * 50;
        } else {
            drainScore = (avgDrain / HIGH_DRAIN_RATE) * 50;
        }

        result.drainContribution = Math.min(maxPoints, (drainScore / 100) * maxPoints);
    }

    /**
     * Throttle Contribution (0-5 points, 5% weight)
     * 
     * Factors:
     * - Number of throttle events
     * - Throttle severity
     */
    private void calculateThrottleContribution(StressResult result) {
        float maxPoints = 100 * WEIGHT_THROTTLE; // 5 points

        if (result.throttleCount == 0) {
            result.throttleContribution = 0;
            return;
        }

        // Calculate throttle percentage
        float throttleRatio = (float) result.throttleCount / sampleBuffer.size();

        // Weight by severity
        float severityWeight = 1.0f;
        for (StressSample s : sampleBuffer) {
            // Check either manual throttle level or system thermal status
            if ((s.isThrottled && s.throttleLevel >= 2) || s.thermalStatus >= 2 /* MODERATE */) {
                severityWeight = 1.5f;
                break;
            }
        }

        float throttleScore = throttleRatio * 100 * severityWeight;
        result.throttleContribution = Math.min(maxPoints, (Math.min(100, throttleScore) / 100) * maxPoints);
    }

    // =========================================================================
    // CATEGORIZATION
    // =========================================================================

    private String categorize(float score) {
        if (score <= LOW_MAX)
            return "LOW";
        if (score <= MEDIUM_MAX)
            return "MEDIUM";
        if (score <= HIGH_MAX)
            return "HIGH";
        return "CRITICAL";
    }

    // =========================================================================
    // EXPLANATION
    // =========================================================================

    private String buildExplanation(StressResult result) {
        StringBuilder sb = new StringBuilder();

        // List top contributors
        List<String> factors = new ArrayList<>();

        if (result.cpuContribution > 10) {
            factors.add("CPU " + String.format("%.0f", result.avgCpu) + "%");
        }
        if (result.gpuContribution > 8) {
            factors.add("GPU " + String.format("%.0f", result.avgGpu) + "%");
        }
        if (result.thermalContribution > 8) {
            factors.add("Temp " + String.format("%.1f", result.peakTemp) + "°C");
        }
        if (result.drainContribution > 5) {
            factors.add("High drain");
        }
        if (result.throttleContribution > 2) {
            factors.add("Throttling");
        }

        if (factors.isEmpty()) {
            sb.append("Normal operation");
        } else {
            sb.append(String.join(", ", factors));
        }

        return sb.toString();
    }

    // =========================================================================
    // BEHAVIORAL INFERENCE ENGINE
    // =========================================================================

    private void runBehavioralInference(StressResult result) {
        boolean isRestricted = result.avgCpu <= 0 && result.avgGpu <= 0;

        if (isRestricted) {
            result.detectionMethod = "Indirect behavioral inference";
            result.observedSignals.clear();

            // Collect signals from window
            boolean screenOn = false;
            float maxDrain = 0;
            String topApp = "unknown";
            boolean isCharging = false;

            for (StressSample s : sampleBuffer) {
                if (s.isScreenOn)
                    screenOn = true;
                if (s.drainRatePerHour > maxDrain)
                    maxDrain = s.drainRatePerHour;
                if (s.foregroundApp != null)
                    topApp = s.foregroundApp;
                if (s.isCharging)
                    isCharging = true;
            }

            // Signal logging
            if (screenOn)
                result.observedSignals.add("Screen ON");
            if (maxDrain > 0)
                result.observedSignals.add("Drain rate " + String.format("%.1f", maxDrain) + "%/hr");
            if (result.tempRise > 0)
                result.observedSignals.add("Temp delta +" + String.format("%.1f", result.tempRise) + "°C");
            if (result.peakTemp > 35)
                result.observedSignals.add("Thermal peak " + String.format("%.1f", result.peakTemp) + "°C");
            if (isCharging)
                result.observedSignals.add("Charging state");

            String category = getAppCategory(topApp);
            if (!"idle".equals(category)) {
                result.observedSignals.add("Active category: " + category);
            }

            // Inference Logic
            float inferenceScore = 0;

            // 1. High drain + rising temperature + screen ON → High CPU/GPU load inferred
            if (maxDrain > 15 && result.tempRise > 0.5 && screenOn) {
                result.inferredLoad = "HIGH";
                result.confidenceLevel = "HIGH";
                inferenceScore = 75 + (maxDrain - 15) + (result.tempRise * 5);
            }
            // 2. Mid drain + mid temp rise OR gaming
            else if ((maxDrain > 8 && result.tempRise > 0.2) || ("gaming".equals(category) && screenOn)) {
                result.inferredLoad = "MODERATE";
                result.confidenceLevel = "MODERATE";
                inferenceScore = 40 + (maxDrain - 8) * 2;
                if ("gaming".equals(category))
                    inferenceScore += 15;
            }
            // 3. Stable drain + stable temperature → Low CPU/GPU load inferred
            else {
                result.inferredLoad = "LOW";
                result.confidenceLevel = maxDrain < 5 ? "HIGH" : "MODERATE";
                inferenceScore = Math.max(0, maxDrain * 2);
            }

            // Penalize sustained heat
            if (result.peakTemp > 42) {
                inferenceScore += 10;
                result.confidenceLevel = "HIGH"; // Heat is a strong signal
            }

            // Apply inferred score and category
            result.stressScore = Math.min(100, inferenceScore);
            result.stressCategory = categorize(result.stressScore);
        }
    }

    private String getAppCategory(String packageName) {
        if (packageName == null)
            return "idle";
        String p = packageName.toLowerCase();

        // Gaming patterns
        if (p.contains("game") || p.contains("tencent") || p.contains("mihoyo") ||
                p.contains("unity") || p.contains("pubg") || p.contains("roblox")) {
            return "gaming";
        }

        // Video patterns
        if (p.contains("video") || p.contains("youtube") || p.contains("netflix") ||
                p.contains("vlc") || p.contains("player") || p.contains("streaming")) {
            return "video";
        }

        return "general";
    }
}
