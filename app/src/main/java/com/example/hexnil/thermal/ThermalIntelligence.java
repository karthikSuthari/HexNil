package com.example.hexnil.thermal;

import android.os.SystemClock;

import com.example.hexnil.model.BatterySnapshot;
import com.example.hexnil.model.ThermalEvent;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * OEM-Grade Thermal Intelligence Engine
 * 
 * Provides advanced thermal analysis including:
 * - Real-time temperature tracking with trend detection
 * - Spike detection with rate-of-change analysis
 * - Sustained heat detection with duration tracking
 * - OEM-safe threshold enforcement
 * - Thermal zone management
 */
public class ThermalIntelligence {

    private static final String TAG = "ThermalIntelligence";

    // =====================================================================
    // OEM-Safe Temperature Thresholds (°C)
    // =====================================================================
    
    // Battery temperature thresholds
    public static final float TEMP_OPTIMAL_MAX = 35.0f;      // Ideal operating range
    public static final float TEMP_NORMAL_MAX = 38.0f;       // Normal operation
    public static final float TEMP_ELEVATED = 40.0f;         // Elevated - monitor closely
    public static final float TEMP_WARNING = 42.0f;          // Warning - reduce load
    public static final float TEMP_HIGH = 44.0f;             // High - throttle charging
    public static final float TEMP_CRITICAL = 46.0f;         // Critical - stop fast charge
    public static final float TEMP_DANGER = 48.0f;           // Danger - emergency measures
    public static final float TEMP_SHUTDOWN = 50.0f;         // Shutdown threshold
    
    // Charging-specific thresholds (more conservative)
    public static final float TEMP_CHARGE_FAST_MAX = 40.0f;  // Max for fast charging
    public static final float TEMP_CHARGE_NORMAL_MAX = 43.0f;// Max for normal charging
    public static final float TEMP_CHARGE_STOP = 45.0f;      // Stop all charging
    
    // Spike detection thresholds
    public static final float SPIKE_RATE_WARNING = 2.0f;     // °C per minute - warning
    public static final float SPIKE_RATE_CRITICAL = 4.0f;    // °C per minute - critical
    public static final float SPIKE_DELTA_MIN = 3.0f;        // Minimum delta for spike
    
    // Sustained heat thresholds
    public static final long SUSTAINED_WARNING_MS = 2 * 60 * 1000;   // 2 minutes
    public static final long SUSTAINED_HIGH_MS = 5 * 60 * 1000;      // 5 minutes
    public static final long SUSTAINED_CRITICAL_MS = 10 * 60 * 1000; // 10 minutes
    
    // History tracking
    private static final int HISTORY_SIZE = 60;  // Track last 60 samples
    private static final long SAMPLE_WINDOW_MS = 5 * 60 * 1000;  // 5 minute window
    
    // =====================================================================
    // Thermal State Tracking
    // =====================================================================
    
    private final LinkedList<ThermalSample> temperatureHistory = new LinkedList<>();
    private ThermalState currentState = ThermalState.OPTIMAL;
    private long elevatedStartTime = 0;
    private long warningStartTime = 0;
    private long criticalStartTime = 0;
    private float baselineTemp = 30.0f;
    private float peakTemp = 0;
    private float minTemp = 100;
    
    // Spike tracking
    private float lastTemp = 0;
    private long lastTempTime = 0;
    private boolean spikeActive = false;
    private long spikeStartTime = 0;
    private float spikeStartTemp = 0;
    
    // Sustained heat tracking
    private SustainedHeatState sustainedState = SustainedHeatState.NONE;
    private long sustainedStartTime = 0;
    
    // =====================================================================
    // Enums
    // =====================================================================
    
    public enum ThermalState {
        OPTIMAL,      // <35°C - Perfect
        NORMAL,       // 35-38°C - Normal operation
        ELEVATED,     // 38-42°C - Elevated, monitoring
        WARNING,      // 42-44°C - Warning, reduce load
        HIGH,         // 44-46°C - High, throttle
        CRITICAL,     // 46-48°C - Critical
        DANGER,       // 48-50°C - Danger
        EMERGENCY     // >50°C - Emergency shutdown
    }
    
    public enum SustainedHeatState {
        NONE,         // No sustained heat
        BUILDING,     // Heat building up
        WARNING,      // 2+ minutes above threshold
        HIGH,         // 5+ minutes above threshold  
        CRITICAL      // 10+ minutes above threshold
    }
    
    public enum ThermalTrend {
        COOLING_FAST,    // Dropping >1°C/min
        COOLING,         // Dropping 0.2-1°C/min
        STABLE,          // ±0.2°C/min
        WARMING,         // Rising 0.2-1°C/min
        WARMING_FAST,    // Rising 1-2°C/min
        SPIKING          // Rising >2°C/min
    }
    
    public enum ThermalZone {
        COLD("<35°C", 0, 35),
        NORMAL("35-40°C", 35, 40),
        WARM("40-45°C", 40, 45),
        HOT(">45°C", 45, 100);
        
        public final String label;
        public final float minTemp;
        public final float maxTemp;
        
        ThermalZone(String label, float min, float max) {
            this.label = label;
            this.minTemp = min;
            this.maxTemp = max;
        }
        
        public static ThermalZone fromTemp(float tempC) {
            if (tempC < 35) return COLD;
            if (tempC < 40) return NORMAL;
            if (tempC < 45) return WARM;
            return HOT;
        }
    }
    
    // =====================================================================
    // Data Classes
    // =====================================================================
    
    public static class ThermalSample {
        public final long timestamp;
        public final float temperature;
        public final boolean isCharging;
        public final int cpuLoad;
        public final int gpuLoad;
        public final String foregroundApp;
        public final int batteryLevel;
        
        public ThermalSample(long timestamp, float temp, boolean charging, 
                            int cpu, int gpu, String app, int level) {
            this.timestamp = timestamp;
            this.temperature = temp;
            this.isCharging = charging;
            this.cpuLoad = cpu;
            this.gpuLoad = gpu;
            this.foregroundApp = app;
            this.batteryLevel = level;
        }
    }
    
    public static class ThermalAnalysis {
        public ThermalState state;
        public ThermalTrend trend;
        public ThermalZone zone;
        public SustainedHeatState sustainedState;
        
        public float currentTemp;
        public float peakTemp;
        public float minTemp;
        public float avgTemp;
        public float baselineTemp;
        
        public float rateOfChange;         // °C per minute
        public float deltaFromBaseline;
        
        public boolean isSpike;
        public float spikeMagnitude;
        public long spikeDuration;
        
        public boolean isSustainedHeat;
        public long sustainedDuration;
        
        public boolean shouldThrottleCharging;
        public boolean shouldStopFastCharge;
        public boolean shouldStopCharging;
        public boolean isEmergency;
        
        public String thermalAdvice;
        public int thermalScore;           // 0-100
        
        public String primaryCause;        // charging, cpu, gpu, app, ambient
        public float causeConfidence;      // 0-1
    }
    
    public static class SpikeDetectionResult {
        public boolean isSpike;
        public float rateOfChange;          // °C per minute
        public float magnitude;             // Total temperature rise
        public long duration;               // Duration in ms
        public String severity;             // "none", "warning", "critical"
        public float peakTemp;
        public float startTemp;
        public String likelyCause;
    }
    
    public static class SustainedHeatResult {
        public boolean isSustained;
        public SustainedHeatState state;
        public long duration;               // Duration above threshold
        public float avgTemp;               // Average during sustained period
        public float peakTemp;              // Peak during sustained period
        public float threshold;             // Threshold being exceeded
        public String severity;             // "none", "warning", "high", "critical"
        public float degradationRisk;       // 0-1 battery degradation risk
    }
    
    // =====================================================================
    // Main Analysis Methods
    // =====================================================================
    
    /**
     * Perform comprehensive thermal analysis on a battery snapshot.
     */
    public ThermalAnalysis analyze(BatterySnapshot snapshot) {
        float tempC = snapshot.getTemperatureCelsius();
        long now = snapshot.timestamp > 0 ? snapshot.timestamp : System.currentTimeMillis();
        
        // Add to history
        ThermalSample sample = new ThermalSample(
            now, tempC, snapshot.isCharging,
            snapshot.cpuLoadTotal > 0 ? snapshot.cpuLoadTotal : snapshot.cpuUsage,
            snapshot.gpuLoad,
            snapshot.topAppPackage != null ? snapshot.topAppPackage : snapshot.foregroundApp,
            snapshot.level
        );
        addSample(sample);
        
        // Build analysis
        ThermalAnalysis analysis = new ThermalAnalysis();
        analysis.currentTemp = tempC;
        analysis.zone = ThermalZone.fromTemp(tempC);
        
        // Update tracking
        updateState(tempC, now);
        analysis.state = currentState;
        
        // Calculate statistics
        calculateStats(analysis);
        
        // Detect trend
        analysis.trend = detectTrend();
        analysis.rateOfChange = calculateRateOfChange();
        analysis.deltaFromBaseline = tempC - baselineTemp;
        
        // Spike detection
        SpikeDetectionResult spike = detectSpike(tempC, now);
        analysis.isSpike = spike.isSpike;
        analysis.spikeMagnitude = spike.magnitude;
        analysis.spikeDuration = spike.duration;
        
        // Sustained heat detection
        SustainedHeatResult sustained = detectSustainedHeat(tempC, now);
        analysis.sustainedState = sustained.state;
        analysis.isSustainedHeat = sustained.isSustained;
        analysis.sustainedDuration = sustained.duration;
        
        // Charging recommendations
        analysis.shouldThrottleCharging = tempC >= TEMP_HIGH || 
            (snapshot.isCharging && tempC >= TEMP_CHARGE_FAST_MAX);
        analysis.shouldStopFastCharge = tempC >= TEMP_CHARGE_FAST_MAX;
        analysis.shouldStopCharging = tempC >= TEMP_CHARGE_STOP;
        analysis.isEmergency = tempC >= TEMP_DANGER;
        
        // Identify primary cause
        identifyPrimaryCause(analysis, snapshot);
        
        // Generate advice and score
        analysis.thermalAdvice = generateAdvice(analysis, snapshot.isCharging);
        analysis.thermalScore = calculateThermalScore(analysis);
        
        // Update tracking variables
        lastTemp = tempC;
        lastTempTime = now;
        
        return analysis;
    }
    
    /**
     * Detect temperature spikes (rapid temperature rises).
     */
    public SpikeDetectionResult detectSpike(float currentTemp, long timestamp) {
        SpikeDetectionResult result = new SpikeDetectionResult();
        result.isSpike = false;
        result.severity = "none";
        
        if (lastTempTime == 0 || temperatureHistory.size() < 2) {
            return result;
        }
        
        // Calculate rate of change
        long timeDelta = timestamp - lastTempTime;
        if (timeDelta <= 0) return result;
        
        float tempDelta = currentTemp - lastTemp;
        float ratePerMinute = (tempDelta / timeDelta) * 60000;  // °C per minute
        
        result.rateOfChange = ratePerMinute;
        
        // Check for spike start
        if (!spikeActive && ratePerMinute >= SPIKE_RATE_WARNING) {
            spikeActive = true;
            spikeStartTime = lastTempTime;
            spikeStartTemp = lastTemp;
        }
        
        // Check for spike end or continuation
        if (spikeActive) {
            float magnitude = currentTemp - spikeStartTemp;
            long duration = timestamp - spikeStartTime;
            
            if (ratePerMinute < 0.5f || magnitude < 0) {
                // Spike ended
                spikeActive = false;
                if (magnitude >= SPIKE_DELTA_MIN) {
                    result.isSpike = true;
                    result.magnitude = magnitude;
                    result.duration = duration;
                    result.peakTemp = currentTemp;
                    result.startTemp = spikeStartTemp;
                    result.severity = ratePerMinute >= SPIKE_RATE_CRITICAL ? "critical" : "warning";
                    result.likelyCause = identifySpikeCause();
                }
            } else {
                // Spike continuing
                result.isSpike = true;
                result.magnitude = magnitude;
                result.duration = duration;
                result.peakTemp = Math.max(currentTemp, peakTemp);
                result.startTemp = spikeStartTemp;
                result.severity = ratePerMinute >= SPIKE_RATE_CRITICAL ? "critical" : "warning";
                result.likelyCause = identifySpikeCause();
            }
        }
        
        return result;
    }
    
    /**
     * Detect sustained high temperature conditions.
     */
    public SustainedHeatResult detectSustainedHeat(float currentTemp, long timestamp) {
        SustainedHeatResult result = new SustainedHeatResult();
        result.isSustained = false;
        result.state = SustainedHeatState.NONE;
        result.severity = "none";
        result.degradationRisk = 0;
        
        float threshold = TEMP_WARNING;  // 42°C threshold for sustained heat
        
        if (currentTemp >= threshold) {
            if (sustainedStartTime == 0) {
                sustainedStartTime = timestamp;
                sustainedState = SustainedHeatState.BUILDING;
            }
            
            long duration = timestamp - sustainedStartTime;
            result.duration = duration;
            result.threshold = threshold;
            result.peakTemp = peakTemp;
            result.avgTemp = calculateRecentAverage(duration);
            
            if (duration >= SUSTAINED_CRITICAL_MS) {
                result.state = SustainedHeatState.CRITICAL;
                result.severity = "critical";
                result.isSustained = true;
                result.degradationRisk = 0.8f + Math.min(0.2f, (currentTemp - TEMP_CRITICAL) * 0.05f);
            } else if (duration >= SUSTAINED_HIGH_MS) {
                result.state = SustainedHeatState.HIGH;
                result.severity = "high";
                result.isSustained = true;
                result.degradationRisk = 0.4f + (duration - SUSTAINED_HIGH_MS) / (float)(SUSTAINED_CRITICAL_MS - SUSTAINED_HIGH_MS) * 0.4f;
            } else if (duration >= SUSTAINED_WARNING_MS) {
                result.state = SustainedHeatState.WARNING;
                result.severity = "warning";
                result.isSustained = true;
                result.degradationRisk = 0.1f + (duration - SUSTAINED_WARNING_MS) / (float)(SUSTAINED_HIGH_MS - SUSTAINED_WARNING_MS) * 0.3f;
            } else {
                result.state = SustainedHeatState.BUILDING;
                result.degradationRisk = duration / (float)SUSTAINED_WARNING_MS * 0.1f;
            }
            
            sustainedState = result.state;
        } else {
            // Temperature dropped below threshold
            sustainedStartTime = 0;
            sustainedState = SustainedHeatState.NONE;
        }
        
        return result;
    }
    
    /**
     * Evaluate if current conditions are safe for charging.
     */
    public ChargingSafetyResult evaluateChargingSafety(float tempC, boolean isFastCharging) {
        ChargingSafetyResult result = new ChargingSafetyResult();
        
        result.currentTemp = tempC;
        result.isFastCharging = isFastCharging;
        
        if (isFastCharging) {
            result.maxAllowedTemp = TEMP_CHARGE_FAST_MAX;
            result.isSafe = tempC < TEMP_CHARGE_FAST_MAX;
            result.shouldReduceRate = tempC >= (TEMP_CHARGE_FAST_MAX - 2);
            result.shouldStopFastCharge = tempC >= TEMP_CHARGE_FAST_MAX;
            result.shouldStopCharging = tempC >= TEMP_CHARGE_STOP;
        } else {
            result.maxAllowedTemp = TEMP_CHARGE_NORMAL_MAX;
            result.isSafe = tempC < TEMP_CHARGE_NORMAL_MAX;
            result.shouldReduceRate = tempC >= (TEMP_CHARGE_NORMAL_MAX - 2);
            result.shouldStopFastCharge = false;
            result.shouldStopCharging = tempC >= TEMP_CHARGE_STOP;
        }
        
        result.headroom = result.maxAllowedTemp - tempC;
        result.thermalMargin = result.headroom / result.maxAllowedTemp;
        
        // Recommendation
        if (result.shouldStopCharging) {
            result.recommendation = "STOP_CHARGING";
            result.reason = "Temperature too high for safe charging";
        } else if (result.shouldStopFastCharge) {
            result.recommendation = "REDUCE_TO_NORMAL";
            result.reason = "Temperature too high for fast charging";
        } else if (result.shouldReduceRate) {
            result.recommendation = "REDUCE_RATE";
            result.reason = "Temperature approaching limit";
        } else {
            result.recommendation = "CONTINUE";
            result.reason = "Temperature within safe range";
        }
        
        return result;
    }
    
    public static class ChargingSafetyResult {
        public float currentTemp;
        public float maxAllowedTemp;
        public boolean isFastCharging;
        public boolean isSafe;
        public boolean shouldReduceRate;
        public boolean shouldStopFastCharge;
        public boolean shouldStopCharging;
        public float headroom;             // Degrees below limit
        public float thermalMargin;        // 0-1 safety margin
        public String recommendation;      // CONTINUE, REDUCE_RATE, REDUCE_TO_NORMAL, STOP_CHARGING
        public String reason;
    }
    
    // =====================================================================
    // OEM Threshold Evaluation
    // =====================================================================
    
    /**
     * Evaluate temperature against OEM thresholds.
     */
    public OEMThresholdResult evaluateOEMThresholds(float tempC) {
        OEMThresholdResult result = new OEMThresholdResult();
        result.temperature = tempC;
        
        // Determine threshold level
        if (tempC < TEMP_OPTIMAL_MAX) {
            result.level = "optimal";
            result.nextThreshold = TEMP_OPTIMAL_MAX;
            result.headroom = TEMP_OPTIMAL_MAX - tempC;
            result.severity = 0;
        } else if (tempC < TEMP_NORMAL_MAX) {
            result.level = "normal";
            result.nextThreshold = TEMP_NORMAL_MAX;
            result.headroom = TEMP_NORMAL_MAX - tempC;
            result.severity = 1;
        } else if (tempC < TEMP_ELEVATED) {
            result.level = "elevated";
            result.nextThreshold = TEMP_ELEVATED;
            result.headroom = TEMP_ELEVATED - tempC;
            result.severity = 2;
        } else if (tempC < TEMP_WARNING) {
            result.level = "warning";
            result.nextThreshold = TEMP_WARNING;
            result.headroom = TEMP_WARNING - tempC;
            result.severity = 3;
        } else if (tempC < TEMP_HIGH) {
            result.level = "high";
            result.nextThreshold = TEMP_HIGH;
            result.headroom = TEMP_HIGH - tempC;
            result.severity = 4;
        } else if (tempC < TEMP_CRITICAL) {
            result.level = "critical";
            result.nextThreshold = TEMP_CRITICAL;
            result.headroom = TEMP_CRITICAL - tempC;
            result.severity = 5;
        } else if (tempC < TEMP_DANGER) {
            result.level = "danger";
            result.nextThreshold = TEMP_DANGER;
            result.headroom = TEMP_DANGER - tempC;
            result.severity = 6;
        } else {
            result.level = "emergency";
            result.nextThreshold = TEMP_SHUTDOWN;
            result.headroom = TEMP_SHUTDOWN - tempC;
            result.severity = 7;
        }
        
        // OEM compliance
        result.passesOEMCompliance = tempC < TEMP_CRITICAL;
        result.requiresAction = tempC >= TEMP_WARNING;
        result.requiresEmergencyAction = tempC >= TEMP_DANGER;
        
        return result;
    }
    
    public static class OEMThresholdResult {
        public float temperature;
        public String level;               // optimal, normal, elevated, warning, high, critical, danger, emergency
        public float nextThreshold;        // Next threshold above current temp
        public float headroom;             // Degrees until next threshold
        public int severity;               // 0-7 severity level
        public boolean passesOEMCompliance;
        public boolean requiresAction;
        public boolean requiresEmergencyAction;
    }
    
    // =====================================================================
    // Helper Methods
    // =====================================================================
    
    private void addSample(ThermalSample sample) {
        temperatureHistory.addLast(sample);
        while (temperatureHistory.size() > HISTORY_SIZE) {
            temperatureHistory.removeFirst();
        }
        
        // Trim old samples
        long cutoff = sample.timestamp - SAMPLE_WINDOW_MS;
        while (!temperatureHistory.isEmpty() && temperatureHistory.getFirst().timestamp < cutoff) {
            temperatureHistory.removeFirst();
        }
    }
    
    private void updateState(float tempC, long timestamp) {
        // Update peak/min
        peakTemp = Math.max(peakTemp, tempC);
        minTemp = Math.min(minTemp, tempC);
        
        // Update baseline (rolling average of recent lows)
        if (tempC < baselineTemp + 2) {
            baselineTemp = baselineTemp * 0.95f + tempC * 0.05f;
        }
        
        // Determine current state
        ThermalState newState;
        if (tempC >= TEMP_SHUTDOWN) {
            newState = ThermalState.EMERGENCY;
        } else if (tempC >= TEMP_DANGER) {
            newState = ThermalState.DANGER;
        } else if (tempC >= TEMP_CRITICAL) {
            newState = ThermalState.CRITICAL;
        } else if (tempC >= TEMP_HIGH) {
            newState = ThermalState.HIGH;
        } else if (tempC >= TEMP_WARNING) {
            newState = ThermalState.WARNING;
        } else if (tempC >= TEMP_ELEVATED) {
            newState = ThermalState.ELEVATED;
        } else if (tempC >= TEMP_NORMAL_MAX) {
            newState = ThermalState.NORMAL;
        } else {
            newState = ThermalState.OPTIMAL;
        }
        
        // Track duration at elevated levels
        if (newState.ordinal() >= ThermalState.ELEVATED.ordinal()) {
            if (elevatedStartTime == 0) elevatedStartTime = timestamp;
        } else {
            elevatedStartTime = 0;
        }
        
        if (newState.ordinal() >= ThermalState.WARNING.ordinal()) {
            if (warningStartTime == 0) warningStartTime = timestamp;
        } else {
            warningStartTime = 0;
        }
        
        if (newState.ordinal() >= ThermalState.CRITICAL.ordinal()) {
            if (criticalStartTime == 0) criticalStartTime = timestamp;
        } else {
            criticalStartTime = 0;
        }
        
        currentState = newState;
    }
    
    private void calculateStats(ThermalAnalysis analysis) {
        if (temperatureHistory.isEmpty()) {
            analysis.peakTemp = analysis.currentTemp;
            analysis.minTemp = analysis.currentTemp;
            analysis.avgTemp = analysis.currentTemp;
            analysis.baselineTemp = baselineTemp;
            return;
        }
        
        float sum = 0;
        float max = Float.MIN_VALUE;
        float min = Float.MAX_VALUE;
        
        for (ThermalSample sample : temperatureHistory) {
            sum += sample.temperature;
            max = Math.max(max, sample.temperature);
            min = Math.min(min, sample.temperature);
        }
        
        analysis.avgTemp = sum / temperatureHistory.size();
        analysis.peakTemp = max;
        analysis.minTemp = min;
        analysis.baselineTemp = baselineTemp;
    }
    
    private ThermalTrend detectTrend() {
        if (temperatureHistory.size() < 3) {
            return ThermalTrend.STABLE;
        }
        
        float rate = calculateRateOfChange();
        
        if (rate <= -1.0f) return ThermalTrend.COOLING_FAST;
        if (rate <= -0.2f) return ThermalTrend.COOLING;
        if (rate >= 2.0f) return ThermalTrend.SPIKING;
        if (rate >= 1.0f) return ThermalTrend.WARMING_FAST;
        if (rate >= 0.2f) return ThermalTrend.WARMING;
        return ThermalTrend.STABLE;
    }
    
    private float calculateRateOfChange() {
        if (temperatureHistory.size() < 2) return 0;
        
        ThermalSample oldest = temperatureHistory.getFirst();
        ThermalSample newest = temperatureHistory.getLast();
        
        long timeDelta = newest.timestamp - oldest.timestamp;
        if (timeDelta <= 0) return 0;
        
        float tempDelta = newest.temperature - oldest.temperature;
        return (tempDelta / timeDelta) * 60000;  // °C per minute
    }
    
    private float calculateRecentAverage(long windowMs) {
        if (temperatureHistory.isEmpty()) return 0;
        
        long cutoff = temperatureHistory.getLast().timestamp - windowMs;
        float sum = 0;
        int count = 0;
        
        for (ThermalSample sample : temperatureHistory) {
            if (sample.timestamp >= cutoff) {
                sum += sample.temperature;
                count++;
            }
        }
        
        return count > 0 ? sum / count : 0;
    }
    
    private String identifySpikeCause() {
        if (temperatureHistory.isEmpty()) return "unknown";
        
        // Check recent samples for patterns
        int chargingCount = 0;
        int highCpuCount = 0;
        int highGpuCount = 0;
        
        for (ThermalSample sample : temperatureHistory) {
            if (sample.isCharging) chargingCount++;
            if (sample.cpuLoad > 70) highCpuCount++;
            if (sample.gpuLoad > 70) highGpuCount++;
        }
        
        int total = temperatureHistory.size();
        if (chargingCount > total * 0.7) return "charging";
        if (highCpuCount > total * 0.5) return "cpu_load";
        if (highGpuCount > total * 0.5) return "gpu_load";
        return "unknown";
    }
    
    private void identifyPrimaryCause(ThermalAnalysis analysis, BatterySnapshot snapshot) {
        float chargingScore = 0;
        float cpuScore = 0;
        float gpuScore = 0;
        float appScore = 0;
        
        // Charging contribution
        if (snapshot.isCharging) {
            chargingScore = 0.5f;
            if (Math.abs(snapshot.chargeRate) > 2000) chargingScore += 0.3f;
            if (Math.abs(snapshot.chargeRate) > 3000) chargingScore += 0.2f;
        }
        
        // CPU contribution
        int cpuLoad = snapshot.cpuLoadTotal > 0 ? snapshot.cpuLoadTotal : snapshot.cpuUsage;
        if (cpuLoad > 70) {
            cpuScore = 0.6f + (cpuLoad - 70) / 100.0f;
        } else if (cpuLoad > 50) {
            cpuScore = 0.3f + (cpuLoad - 50) / 100.0f;
        }
        
        // GPU contribution
        if (snapshot.gpuLoad > 70) {
            gpuScore = 0.5f + (snapshot.gpuLoad - 70) / 100.0f;
        } else if (snapshot.gpuLoad > 50) {
            gpuScore = 0.2f + (snapshot.gpuLoad - 50) / 100.0f;
        }
        
        // Determine primary cause
        float maxScore = Math.max(Math.max(chargingScore, cpuScore), Math.max(gpuScore, appScore));
        
        if (maxScore < 0.2f) {
            analysis.primaryCause = "ambient";
            analysis.causeConfidence = 0.5f;
        } else if (chargingScore == maxScore) {
            analysis.primaryCause = "charging";
            analysis.causeConfidence = chargingScore;
        } else if (cpuScore == maxScore) {
            analysis.primaryCause = "cpu";
            analysis.causeConfidence = cpuScore;
        } else if (gpuScore == maxScore) {
            analysis.primaryCause = "gpu";
            analysis.causeConfidence = gpuScore;
        } else {
            analysis.primaryCause = "app";
            analysis.causeConfidence = appScore;
        }
    }
    
    private String generateAdvice(ThermalAnalysis analysis, boolean isCharging) {
        StringBuilder advice = new StringBuilder();
        
        switch (analysis.state) {
            case EMERGENCY:
                advice.append("EMERGENCY: Immediately disconnect charger and allow device to cool. ");
                break;
            case DANGER:
                advice.append("DANGER: Stop all heavy usage. Disconnect charger. Move to cooler location. ");
                break;
            case CRITICAL:
                advice.append("Critical temperature. Stop fast charging. Close heavy apps. ");
                break;
            case HIGH:
                advice.append("High temperature. Reduce charging rate. Avoid intensive tasks. ");
                break;
            case WARNING:
                advice.append("Temperature elevated. Monitor closely. ");
                if (isCharging) advice.append("Consider reducing charge rate. ");
                break;
            case ELEVATED:
                advice.append("Slightly elevated temperature. Normal for heavy use. ");
                break;
            default:
                advice.append("Temperature normal. ");
        }
        
        if (analysis.isSpike) {
            advice.append("Temperature spike detected. ");
        }
        
        if (analysis.isSustainedHeat && analysis.sustainedState == SustainedHeatState.CRITICAL) {
            advice.append("SUSTAINED HIGH TEMPERATURE - battery degradation risk! ");
        }
        
        return advice.toString().trim();
    }
    
    private int calculateThermalScore(ThermalAnalysis analysis) {
        int score = 100;
        
        // Deduct based on temperature
        if (analysis.currentTemp >= TEMP_DANGER) {
            score -= 60;
        } else if (analysis.currentTemp >= TEMP_CRITICAL) {
            score -= 45;
        } else if (analysis.currentTemp >= TEMP_HIGH) {
            score -= 30;
        } else if (analysis.currentTemp >= TEMP_WARNING) {
            score -= 20;
        } else if (analysis.currentTemp >= TEMP_ELEVATED) {
            score -= 10;
        } else if (analysis.currentTemp >= TEMP_NORMAL_MAX) {
            score -= 5;
        }
        
        // Deduct for spikes
        if (analysis.isSpike) {
            score -= (int)(analysis.spikeMagnitude * 3);
        }
        
        // Deduct for sustained heat
        if (analysis.isSustainedHeat) {
            switch (analysis.sustainedState) {
                case CRITICAL:
                    score -= 25;
                    break;
                case HIGH:
                    score -= 15;
                    break;
                case WARNING:
                    score -= 8;
                    break;
                default:
                    break;
            }
        }
        
        // Bonus for cooling trend
        if (analysis.trend == ThermalTrend.COOLING_FAST) {
            score += 5;
        }
        
        return Math.max(0, Math.min(100, score));
    }
    
    // =====================================================================
    // State Getters
    // =====================================================================
    
    public ThermalState getCurrentState() {
        return currentState;
    }
    
    public float getPeakTemperature() {
        return peakTemp;
    }
    
    public float getBaselineTemperature() {
        return baselineTemp;
    }
    
    public long getElevatedDuration() {
        return elevatedStartTime > 0 ? System.currentTimeMillis() - elevatedStartTime : 0;
    }
    
    public long getWarningDuration() {
        return warningStartTime > 0 ? System.currentTimeMillis() - warningStartTime : 0;
    }
    
    public long getCriticalDuration() {
        return criticalStartTime > 0 ? System.currentTimeMillis() - criticalStartTime : 0;
    }
    
    public List<ThermalSample> getRecentHistory() {
        return new ArrayList<>(temperatureHistory);
    }
    
    /**
     * Reset tracking state (e.g., on device restart).
     */
    public void reset() {
        temperatureHistory.clear();
        currentState = ThermalState.OPTIMAL;
        elevatedStartTime = 0;
        warningStartTime = 0;
        criticalStartTime = 0;
        peakTemp = 0;
        minTemp = 100;
        lastTemp = 0;
        lastTempTime = 0;
        spikeActive = false;
        sustainedState = SustainedHeatState.NONE;
        sustainedStartTime = 0;
    }
}
