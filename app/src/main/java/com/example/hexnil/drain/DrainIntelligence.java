package com.example.hexnil.drain;

import android.content.Context;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.model.BatterySnapshot;
import com.example.hexnil.model.DrainEvent;

import java.util.List;

/**
 * DrainIntelligence - OEM-grade drain analysis engine for comprehensive battery
 * drain detection.
 * 
 * Provides intelligent drain analysis including:
 * - Screen-on vs screen-off drain detection and tracking
 * - Drain rate calculation per hour with adaptive baselines
 * - Expected vs actual drain comparison using device learning
 * - Idle drain benchmarks against industry standards
 * - Deep sleep effectiveness measurement
 * - Component-level drain attribution (display, CPU, network, sensors)
 * - Anomaly detection with severity scoring
 * - Drain pattern recognition and trending
 * 
 * Uses exponential moving averages and statistical analysis to provide
 * accurate drain insights and actionable recommendations.
 */
public class DrainIntelligence {

    private static final String TAG = "DrainIntelligence";

    // ====================================================================================
    // DRAIN RATE BENCHMARKS (% per hour) - Based on industry standards
    // ====================================================================================

    // Screen-on drain benchmarks
    public static final float SCREEN_ON_EXCELLENT = 6.0f; // Under 6%/hr = excellent
    public static final float SCREEN_ON_GOOD = 10.0f; // Under 10%/hr = good
    public static final float SCREEN_ON_AVERAGE = 15.0f; // Under 15%/hr = average
    public static final float SCREEN_ON_POOR = 20.0f; // Under 20%/hr = poor
    public static final float SCREEN_ON_CRITICAL = 25.0f; // Over 25%/hr = critical

    // Screen-off (idle) drain benchmarks
    public static final float IDLE_EXCELLENT = 0.3f; // Under 0.3%/hr = excellent
    public static final float IDLE_GOOD = 0.5f; // Under 0.5%/hr = good
    public static final float IDLE_AVERAGE = 1.0f; // Under 1.0%/hr = average
    public static final float IDLE_POOR = 2.0f; // Under 2.0%/hr = poor
    public static final float IDLE_CRITICAL = 3.0f; // Over 3.0%/hr = critical

    // Doze mode drain benchmarks
    public static final float DOZE_EXCELLENT = 0.1f; // Under 0.1%/hr = excellent
    public static final float DOZE_GOOD = 0.2f; // Under 0.2%/hr = good
    public static final float DOZE_AVERAGE = 0.4f; // Under 0.4%/hr = average
    public static final float DOZE_POOR = 0.8f; // Under 0.8%/hr = poor

    // Deep sleep effectiveness thresholds
    public static final float DEEP_SLEEP_EXCELLENT = 80.0f; // 80%+ time in deep sleep
    public static final float DEEP_SLEEP_GOOD = 60.0f; // 60%+ time in deep sleep
    public static final float DEEP_SLEEP_POOR = 40.0f; // Under 40% is poor

    // Anomaly thresholds
    public static final float ANOMALY_THRESHOLD = 1.5f; // 50% above expected
    public static final float SEVERE_ANOMALY_THRESHOLD = 2.5f; // 150% above expected
    public static final float CRITICAL_ANOMALY_THRESHOLD = 4.0f; // 300% above expected

    // EMA smoothing factors
    private static final float EMA_FAST_ALPHA = 0.3f; // Fast response to changes
    private static final float EMA_SLOW_ALPHA = 0.1f; // Slow baseline tracking

    // ====================================================================================
    // INSTANCE FIELDS
    // ====================================================================================

    private final Context context;
    private final HexNilDatabase database;
    private final PowerManager powerManager;

    // Adaptive baselines using EMA
    private float emaScreenOnDrainRate = 12.0f; // Starting estimate
    private float emaScreenOffDrainRate = 0.7f; // Starting estimate
    private float emaDozeDrainRate = 0.3f; // Starting estimate
    private float emaDeepSleepEffectiveness = 70.0f; // Starting estimate

    // Tracking state
    private long lastAnalysisTime = 0;
    private int consecutiveAnomalies = 0;
    private float peakDrainRateToday = 0;
    private float totalDrainToday = 0;

    // Deep sleep tracking
    private long deepSleepStartTime = 0;
    private long totalDeepSleepTime = 0;
    private long totalIdleTime = 0;

    // ====================================================================================
    // CONSTRUCTOR
    // ====================================================================================

    public DrainIntelligence(Context context, HexNilDatabase database) {
        this.context = context;
        this.database = database;
        this.powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        loadHistoricalBaselines();
    }

    /**
     * Load historical data to calibrate baselines
     */
    private void loadHistoricalBaselines() {
        try {
            long weekAgo = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L;

            // Load screen-on baseline
            Float screenOnAvg = database.drainEventDao().getAverageScreenOnDrain(weekAgo);
            if (screenOnAvg != null && screenOnAvg > 0) {
                emaScreenOnDrainRate = screenOnAvg;
            }

            // Load screen-off baseline
            Float screenOffAvg = database.drainEventDao().getAverageScreenOffDrain(weekAgo);
            if (screenOffAvg != null && screenOffAvg > 0) {
                emaScreenOffDrainRate = screenOffAvg;
            }

            // Load doze baseline
            Float dozeAvg = database.idleDrainSessionDao().getAverageDozeEffectiveness(weekAgo);
            if (dozeAvg != null && dozeAvg > 0) {
                emaDozeDrainRate = dozeAvg / 100.0f; // Convert percentage
            }

            // Load deep sleep effectiveness
            Float deepSleepAvg = database.idleDrainSessionDao().getAverageDeepSleep(weekAgo);
            if (deepSleepAvg != null && deepSleepAvg > 0) {
                emaDeepSleepEffectiveness = deepSleepAvg;
            }

            Log.d(TAG, String.format(
                    "Loaded baselines - ScreenOn: %.1f%%/hr, ScreenOff: %.2f%%/hr, Doze: %.2f%%/hr, DeepSleep: %.1f%%",
                    emaScreenOnDrainRate, emaScreenOffDrainRate, emaDozeDrainRate, emaDeepSleepEffectiveness));
        } catch (Exception e) {
            Log.w(TAG, "Failed to load historical baselines, using defaults", e);
        }
    }

    // ====================================================================================
    // MAIN ANALYSIS METHODS
    // ====================================================================================

    /**
     * Analyze a drain event and compute all intelligence metrics
     */
    public DrainAnalysisResult analyzeDrainEvent(DrainEvent event, BatterySnapshot currentSnapshot) {
        DrainAnalysisResult result = new DrainAnalysisResult();

        if (event == null || currentSnapshot == null) {
            result.isValid = false;
            result.analysisNotes = "Invalid input - null event or snapshot";
            return result;
        }

        result.isValid = true;
        result.timestamp = System.currentTimeMillis();
        result.snapshotId = currentSnapshot.id;

        // Basic drain metrics
        result.drainAmount = event.drainAmount;
        result.drainRatePerHour = event.drainRatePerHour;
        result.durationMinutes = event.getDurationHours() * 60;

        // Screen state analysis
        result.isScreenOn = event.screenOn;
        result.screenState = determineScreenState(currentSnapshot);
        result.screenStateGrade = gradeScreenStateDrain(event.drainRatePerHour, result.screenState);

        // Expected vs actual drain
        result.expectedDrainRate = calculateExpectedDrainRate(currentSnapshot);
        result.expectedDrain = result.expectedDrainRate * event.getDurationHours();
        result.drainDelta = event.drainAmount - result.expectedDrain;
        result.drainEfficiency = result.expectedDrain > 0
                ? (result.expectedDrain / Math.max(0.1f, event.drainAmount)) * 100
                : 100;

        // Anomaly detection
        result.anomalyScore = calculateAnomalyScore(event, result);
        result.isAnomalous = result.anomalyScore > 0.5f;
        result.anomalySeverity = classifyAnomalySeverity(result.anomalyScore);

        // Benchmark comparison
        result.benchmarkResult = benchmarkDrainRate(event.drainRatePerHour, result.screenState);
        result.benchmarkDelta = calculateBenchmarkDelta(event.drainRatePerHour, result.screenState);

        // Component attribution
        result.displayDrainPct = estimateDisplayDrain(currentSnapshot, event);
        result.cpuDrainPct = estimateCpuDrain(currentSnapshot, event);
        result.networkDrainPct = estimateNetworkDrain(currentSnapshot, event);
        result.sensorDrainPct = estimateSensorDrain(currentSnapshot, event);
        result.otherDrainPct = 100 - result.displayDrainPct - result.cpuDrainPct
                - result.networkDrainPct - result.sensorDrainPct;
        result.primaryDrainComponent = determinePrimaryComponent(result);

        // Deep sleep analysis
        result.deepSleepEffectiveness = calculateDeepSleepEffectiveness(currentSnapshot);
        result.deepSleepGrade = gradeDeepSleepEffectiveness(result.deepSleepEffectiveness);
        result.isDeepSleepActive = currentSnapshot.isDozeMode;

        // Idle drain quality
        result.idleDrainQuality = assessIdleDrainQuality(event, currentSnapshot);
        result.idleBenchmarkResult = benchmarkIdleDrain(event.drainRatePerHour);

        // Update adaptive baselines
        updateAdaptiveBaselines(event, currentSnapshot);

        // Generate recommendations
        result.recommendations = generateDrainRecommendations(result, currentSnapshot);
        result.analysisNotes = generateAnalysisNotes(result);

        // Update tracking
        lastAnalysisTime = result.timestamp;
        if (result.isAnomalous) {
            consecutiveAnomalies++;
        } else {
            consecutiveAnomalies = 0;
        }

        if (event.drainRatePerHour > peakDrainRateToday) {
            peakDrainRateToday = event.drainRatePerHour;
        }
        totalDrainToday += event.drainAmount;

        return result;
    }

    /**
     * Determine the current screen state category
     */
    private String determineScreenState(BatterySnapshot snapshot) {
        if (snapshot.isScreenOnOrFalse()) {
            int brightness = snapshot.screenBrightness != null ? snapshot.screenBrightness : 0;
            if (brightness > 200) return "screen_on_bright";
            if (brightness > 100) return "screen_on_medium";
            return "screen_on_dim";
        } else if (snapshot.isDozeMode) {
            return "doze";
        } else {
            return "screen_off";
        }
    }

    /**
     * Grade drain rate based on screen state
     */
    private String gradeScreenStateDrain(float drainRate, String screenState) {
        if (screenState.startsWith("screen_on")) {
            if (drainRate < SCREEN_ON_EXCELLENT)
                return "A+";
            if (drainRate < SCREEN_ON_GOOD)
                return "A";
            if (drainRate < SCREEN_ON_AVERAGE)
                return "B";
            if (drainRate < SCREEN_ON_POOR)
                return "C";
            if (drainRate < SCREEN_ON_CRITICAL)
                return "D";
            return "F";
        } else if ("doze".equals(screenState)) {
            if (drainRate < DOZE_EXCELLENT)
                return "A+";
            if (drainRate < DOZE_GOOD)
                return "A";
            if (drainRate < DOZE_AVERAGE)
                return "B";
            if (drainRate < DOZE_POOR)
                return "C";
            return "D";
        } else {
            // Screen off / idle
            if (drainRate < IDLE_EXCELLENT)
                return "A+";
            if (drainRate < IDLE_GOOD)
                return "A";
            if (drainRate < IDLE_AVERAGE)
                return "B";
            if (drainRate < IDLE_POOR)
                return "C";
            if (drainRate < IDLE_CRITICAL)
                return "D";
            return "F";
        }
    }

    // ====================================================================================
    // EXPECTED DRAIN CALCULATION
    // ====================================================================================

    /**
     * Calculate expected drain rate based on current device state
     */
    public float calculateExpectedDrainRate(BatterySnapshot snapshot) {
        float baseDrain = 0;

        if (snapshot.isScreenOnOrFalse()) {
            baseDrain = emaScreenOnDrainRate;
            int bright = snapshot.screenBrightness != null ? snapshot.screenBrightness : 0;
            float brightnessMultiplier = 0.7f + (bright / 255f) * 0.6f;
            baseDrain *= brightnessMultiplier;
        } else if (snapshot.isDozeMode) {
            baseDrain = emaDozeDrainRate;
        } else {
            baseDrain = emaScreenOffDrainRate;
        }
        if (snapshot.cpuLoadTotal != null && snapshot.cpuLoadTotal > 50) {
            baseDrain *= 1.0f + (snapshot.cpuLoadTotal - 50) / 100f;
        }

        // Temperature adjustment - high temp increases drain
        if (snapshot.temperature > 35) {
            baseDrain *= 1.0f + (snapshot.temperature - 35) / 30f;
        }

        // Network adjustment
        if ("mobile".equals(snapshot.networkState)) {
            baseDrain *= 1.15f; // Mobile data uses more power
        }

        Integer sig = snapshot.signalStrength;
        if (sig != null && sig < -100) {
            baseDrain *= 1.2f;
        } else if (sig != null && sig < -85) {
            baseDrain *= 1.1f;
        }
        if (snapshot.gpsEnabled != null && snapshot.gpsEnabled != 0) {
            baseDrain += 3.0f;
        }
        if (snapshot.bluetoothEnabled != null && snapshot.bluetoothEnabled != 0) {
            baseDrain += 0.3f;
        }
        if (Boolean.TRUE.equals(snapshot.isWifiEnabled()) && !snapshot.isScreenOnOrFalse()) {
            baseDrain += 0.1f;
        }

        return baseDrain;
    }

    /**
     * Calculate expected drain for a specific duration and state
     */
    public float calculateExpectedDrain(float durationHours, String screenState, BatterySnapshot snapshot) {
        float baseRate = 0;

        switch (screenState) {
            case "screen_on_bright":
                baseRate = SCREEN_ON_AVERAGE * 1.3f;
                break;
            case "screen_on_medium":
                baseRate = SCREEN_ON_AVERAGE;
                break;
            case "screen_on_dim":
                baseRate = SCREEN_ON_AVERAGE * 0.7f;
                break;
            case "doze":
                baseRate = DOZE_AVERAGE;
                break;
            case "screen_off":
            default:
                baseRate = IDLE_AVERAGE;
                break;
        }

        if (snapshot != null && snapshot.cpuLoadTotal != null && snapshot.cpuLoadTotal > 50) {
            baseRate *= 1.0f + (snapshot.cpuLoadTotal - 50) / 100f;
        }

        return baseRate * durationHours;
    }

    // ====================================================================================
    // ANOMALY DETECTION
    // ====================================================================================

    /**
     * Calculate anomaly score (0-1 scale, higher = more anomalous)
     */
    private float calculateAnomalyScore(DrainEvent event, DrainAnalysisResult result) {
        float score = 0;

        // Compare to expected drain
        if (result.expectedDrain > 0) {
            float ratio = event.drainAmount / result.expectedDrain;
            if (ratio > CRITICAL_ANOMALY_THRESHOLD) {
                score = 1.0f;
            } else if (ratio > SEVERE_ANOMALY_THRESHOLD) {
                score = 0.8f + (ratio - SEVERE_ANOMALY_THRESHOLD)
                        / (CRITICAL_ANOMALY_THRESHOLD - SEVERE_ANOMALY_THRESHOLD) * 0.2f;
            } else if (ratio > ANOMALY_THRESHOLD) {
                score = 0.5f + (ratio - ANOMALY_THRESHOLD) / (SEVERE_ANOMALY_THRESHOLD - ANOMALY_THRESHOLD) * 0.3f;
            } else if (ratio > 1.0f) {
                score = (ratio - 1.0f) / (ANOMALY_THRESHOLD - 1.0f) * 0.5f;
            }
        }

        // Compare to benchmark
        float benchmarkDrain = getBenchmarkDrainRate(result.screenState);
        if (benchmarkDrain > 0) {
            float benchmarkRatio = event.drainRatePerHour / benchmarkDrain;
            if (benchmarkRatio > 2.0f) {
                score = Math.max(score, 0.7f);
            } else if (benchmarkRatio > 1.5f) {
                score = Math.max(score, 0.5f);
            }
        }

        // Consider consecutive anomalies (escalation)
        if (consecutiveAnomalies > 0) {
            score = Math.min(1.0f, score + consecutiveAnomalies * 0.1f);
        }

        return Math.min(1.0f, Math.max(0f, score));
    }

    /**
     * Classify anomaly severity
     */
    private String classifyAnomalySeverity(float anomalyScore) {
        if (anomalyScore >= 0.8f)
            return "CRITICAL";
        if (anomalyScore >= 0.6f)
            return "SEVERE";
        if (anomalyScore >= 0.4f)
            return "MODERATE";
        if (anomalyScore >= 0.2f)
            return "MILD";
        return "NORMAL";
    }

    /**
     * Get benchmark drain rate for a screen state
     */
    public float getBenchmarkDrainRate(String screenState) {
        switch (screenState) {
            case "screen_on_bright":
                return SCREEN_ON_AVERAGE * 1.2f;
            case "screen_on_medium":
                return SCREEN_ON_AVERAGE;
            case "screen_on_dim":
                return SCREEN_ON_AVERAGE * 0.8f;
            case "doze":
                return DOZE_AVERAGE;
            case "screen_off":
            default:
                return IDLE_AVERAGE;
        }
    }

    // ====================================================================================
    // BENCHMARK COMPARISON
    // ====================================================================================

    /**
     * Benchmark drain rate against industry standards
     */
    public String benchmarkDrainRate(float drainRate, String screenState) {
        float excellent, good, average, poor;

        if (screenState.startsWith("screen_on")) {
            excellent = SCREEN_ON_EXCELLENT;
            good = SCREEN_ON_GOOD;
            average = SCREEN_ON_AVERAGE;
            poor = SCREEN_ON_POOR;
        } else if ("doze".equals(screenState)) {
            excellent = DOZE_EXCELLENT;
            good = DOZE_GOOD;
            average = DOZE_AVERAGE;
            poor = DOZE_POOR;
        } else {
            excellent = IDLE_EXCELLENT;
            good = IDLE_GOOD;
            average = IDLE_AVERAGE;
            poor = IDLE_POOR;
        }

        if (drainRate < excellent)
            return "EXCELLENT";
        if (drainRate < good)
            return "GOOD";
        if (drainRate < average)
            return "AVERAGE";
        if (drainRate < poor)
            return "POOR";
        return "CRITICAL";
    }

    /**
     * Calculate delta from benchmark
     */
    private float calculateBenchmarkDelta(float drainRate, String screenState) {
        float benchmark = getBenchmarkDrainRate(screenState);
        return drainRate - benchmark;
    }

    /**
     * Benchmark idle drain specifically
     */
    public String benchmarkIdleDrain(float drainRate) {
        if (drainRate < IDLE_EXCELLENT)
            return "EXCELLENT";
        if (drainRate < IDLE_GOOD)
            return "GOOD";
        if (drainRate < IDLE_AVERAGE)
            return "AVERAGE";
        if (drainRate < IDLE_POOR)
            return "POOR";
        return "CRITICAL";
    }

    // ====================================================================================
    // COMPONENT ATTRIBUTION
    // ====================================================================================

    /**
     * Estimate display component drain percentage
     */
    private float estimateDisplayDrain(BatterySnapshot snapshot, DrainEvent event) {
        if (!snapshot.isScreenOnOrFalse())
            return 0;
        int bright = snapshot.screenBrightness != null ? snapshot.screenBrightness : 0;
        float brightness = bright / 255f;
        float baseDisplayDrain = 35f + brightness * 15f; // 35-50%

        // Adjust for screen on time
        if (event.screenOnTime > 0 && event.endTime > event.startTime) {
            float screenOnRatio = (float) event.screenOnTime / (event.endTime - event.startTime);
            baseDisplayDrain *= screenOnRatio;
        }

        return Math.min(60f, Math.max(0f, baseDisplayDrain));
    }

    /**
     * Estimate CPU component drain percentage
     */
    private float estimateCpuDrain(BatterySnapshot snapshot, DrainEvent event) {
        float cpuLoad = snapshot.cpuLoadTotal != null ? snapshot.cpuLoadTotal : 0;
        float baseCpuDrain = 10f + (cpuLoad / 100f) * 20f; // 10-30%

        return Math.min(40f, Math.max(5f, baseCpuDrain));
    }

    /**
     * Estimate network component drain percentage
     */
    private float estimateNetworkDrain(BatterySnapshot snapshot, DrainEvent event) {
        float networkDrain = 5f; // Base network drain

        // Mobile data uses more
        if ("mobile".equals(snapshot.networkState)) {
            networkDrain = 15f;

            if (snapshot.signalStrength != null && snapshot.signalStrength < -100) {
                networkDrain *= 1.5f;
            }
        } else if ("wifi".equals(snapshot.networkState)) {
            networkDrain = 8f;
        }

        return Math.min(30f, Math.max(2f, networkDrain));
    }

    /**
     * Estimate sensor component drain percentage
     */
    private float estimateSensorDrain(BatterySnapshot snapshot, DrainEvent event) {
        float sensorDrain = 2f; // Base sensor drain

        if (snapshot.gpsEnabled != null && snapshot.gpsEnabled != 0) {
            sensorDrain += 15f;
        }
        if (snapshot.bluetoothEnabled != null && snapshot.bluetoothEnabled != 0) {
            sensorDrain += 3f;
        }

        return Math.min(25f, Math.max(1f, sensorDrain));
    }

    /**
     * Determine primary drain component
     */
    private String determinePrimaryComponent(DrainAnalysisResult result) {
        float maxDrain = 0;
        String primary = "system";

        if (result.displayDrainPct > maxDrain) {
            maxDrain = result.displayDrainPct;
            primary = "display";
        }
        if (result.cpuDrainPct > maxDrain) {
            maxDrain = result.cpuDrainPct;
            primary = "cpu";
        }
        if (result.networkDrainPct > maxDrain) {
            maxDrain = result.networkDrainPct;
            primary = "network";
        }
        if (result.sensorDrainPct > maxDrain) {
            maxDrain = result.sensorDrainPct;
            primary = "sensors";
        }

        return primary;
    }

    // ====================================================================================
    // DEEP SLEEP ANALYSIS
    // ====================================================================================

    /**
     * Calculate deep sleep effectiveness
     */
    public float calculateDeepSleepEffectiveness(BatterySnapshot snapshot) {
        if (snapshot.isScreenOnOrFalse())
            return 0;
        float effectiveness = 50f;
        if (snapshot.isDozeMode) {
            effectiveness += 30f;
        }
        Integer cpu = snapshot.cpuLoadTotal;
        if (cpu != null) {
            if (cpu < 5) effectiveness += 10f;
            else if (cpu < 15) effectiveness += 5f;
            else if (cpu > 30) effectiveness -= 15f;
        }

        // No active apps is good
        if (snapshot.foregroundApp == null || snapshot.foregroundApp.isEmpty()) {
            effectiveness += 5f;
        }

        // Good temperature
        if (snapshot.temperature < 30) {
            effectiveness += 5f;
        } else if (snapshot.temperature > 38) {
            effectiveness -= 10f;
        }

        return Math.min(100f, Math.max(0f, effectiveness));
    }

    /**
     * Grade deep sleep effectiveness
     */
    public String gradeDeepSleepEffectiveness(float effectiveness) {
        if (effectiveness >= DEEP_SLEEP_EXCELLENT)
            return "EXCELLENT";
        if (effectiveness >= DEEP_SLEEP_GOOD)
            return "GOOD";
        if (effectiveness >= DEEP_SLEEP_POOR)
            return "FAIR";
        return "POOR";
    }

    /**
     * Track deep sleep state changes
     */
    public void trackDeepSleepState(boolean isDeepSleep, long timestamp) {
        if (isDeepSleep) {
            if (deepSleepStartTime == 0) {
                deepSleepStartTime = timestamp;
            }
        } else {
            if (deepSleepStartTime > 0) {
                totalDeepSleepTime += timestamp - deepSleepStartTime;
                deepSleepStartTime = 0;
            }
        }
    }

    /**
     * Calculate deep sleep percentage over total idle time
     */
    public float getDeepSleepPercentage() {
        if (totalIdleTime == 0)
            return 0;
        return (totalDeepSleepTime * 100f) / totalIdleTime;
    }

    // ====================================================================================
    // IDLE DRAIN QUALITY
    // ====================================================================================

    /**
     * Assess idle drain quality
     */
    private String assessIdleDrainQuality(DrainEvent event, BatterySnapshot snapshot) {
        if (event.screenOn)
            return "N/A"; // Not idle

        float drainRate = event.drainRatePerHour;

        // Check if in doze mode
        if (snapshot.isDozeMode) {
            if (drainRate < 0.2f)
                return "OPTIMAL";
            if (drainRate < 0.4f)
                return "GOOD";
            if (drainRate < 0.8f)
                return "ACCEPTABLE";
            return "SUBOPTIMAL";
        } else {
            if (drainRate < 0.4f)
                return "OPTIMAL";
            if (drainRate < 0.7f)
                return "GOOD";
            if (drainRate < 1.2f)
                return "ACCEPTABLE";
            return "SUBOPTIMAL";
        }
    }

    // ====================================================================================
    // ADAPTIVE BASELINE UPDATES
    // ====================================================================================

    /**
     * Update adaptive baselines using EMA
     */
    private void updateAdaptiveBaselines(DrainEvent event, BatterySnapshot snapshot) {
        if (event.drainRatePerHour <= 0 || event.getDurationHours() < 0.1f)
            return;

        // Only update if drain seems reasonable (not extreme outlier)
        if (event.isExcessiveDrain())
            return;

        if (snapshot.isScreenOnOrFalse()) {
            emaScreenOnDrainRate = emaScreenOnDrainRate * (1 - EMA_SLOW_ALPHA)
                    + event.drainRatePerHour * EMA_SLOW_ALPHA;
        } else if (snapshot.isDozeMode) {
            // Update doze baseline
            emaDozeDrainRate = emaDozeDrainRate * (1 - EMA_SLOW_ALPHA)
                    + event.drainRatePerHour * EMA_SLOW_ALPHA;
        } else {
            // Update screen-off baseline
            emaScreenOffDrainRate = emaScreenOffDrainRate * (1 - EMA_SLOW_ALPHA)
                    + event.drainRatePerHour * EMA_SLOW_ALPHA;
        }

        // Update deep sleep effectiveness
        float currentEffectiveness = calculateDeepSleepEffectiveness(snapshot);
        emaDeepSleepEffectiveness = emaDeepSleepEffectiveness * (1 - EMA_SLOW_ALPHA)
                + currentEffectiveness * EMA_SLOW_ALPHA;
    }

    // ====================================================================================
    // RECOMMENDATIONS
    // ====================================================================================

    /**
     * Generate drain recommendations
     */
    private String generateDrainRecommendations(DrainAnalysisResult result, BatterySnapshot snapshot) {
        StringBuilder recs = new StringBuilder();

        // High screen-on drain
        if (result.isScreenOn && result.drainRatePerHour > SCREEN_ON_AVERAGE) {
            int bright = snapshot.screenBrightness != null ? snapshot.screenBrightness : 0;
            if (bright > 150) {
                recs.append("• Reduce screen brightness to save battery\n");
            }
            if ("mobile".equals(snapshot.networkState)) {
                recs.append("• Switch to WiFi when available for lower drain\n");
            }
        }

        // High idle drain
        if (!result.isScreenOn && result.drainRatePerHour > IDLE_AVERAGE) {
            recs.append("• Check background apps for excessive activity\n");
            if (!snapshot.isDozeMode) {
                recs.append("• Enable battery saver to improve Doze effectiveness\n");
            }
        }

        // Poor deep sleep
        if (result.deepSleepEffectiveness < DEEP_SLEEP_POOR) {
            recs.append("• Limit apps with background restrictions\n");
            recs.append("• Check for wakelocks preventing deep sleep\n");
        }

        // Anomalous drain
        if (result.isAnomalous) {
            recs.append("• Unusual drain detected - review recent app activity\n");
            if (consecutiveAnomalies > 2) {
                recs.append("• Persistent high drain - consider restarting device\n");
            }
        }

        if (snapshot.gpsEnabled != null && snapshot.gpsEnabled != 0 && !result.isScreenOn) {
            recs.append("• GPS is active in background - check location permissions\n");
        }

        // High temperature
        if (snapshot.temperature > 38) {
            recs.append("• Device is warm - let it cool for better efficiency\n");
        }

        return recs.length() > 0 ? recs.toString().trim() : "Battery drain is within normal parameters";
    }

    /**
     * Generate analysis notes
     */
    private String generateAnalysisNotes(DrainAnalysisResult result) {
        StringBuilder notes = new StringBuilder();

        notes.append(String.format("Screen state: %s | ", result.screenState));
        notes.append(String.format("Drain rate: %.1f%%/hr (%s) | ", result.drainRatePerHour, result.benchmarkResult));
        notes.append(String.format("Expected: %.1f%% Actual: %.1f%% | ", result.expectedDrain, result.drainAmount));
        notes.append(String.format("Primary: %s (%.0f%%) | ", result.primaryDrainComponent,
                getPrimaryComponentPercentage(result)));

        if (result.isAnomalous) {
            notes.append(String.format("ANOMALY: %s (score: %.2f)", result.anomalySeverity, result.anomalyScore));
        } else {
            notes.append("Normal operation");
        }

        return notes.toString();
    }

    private float getPrimaryComponentPercentage(DrainAnalysisResult result) {
        switch (result.primaryDrainComponent) {
            case "display":
                return result.displayDrainPct;
            case "cpu":
                return result.cpuDrainPct;
            case "network":
                return result.networkDrainPct;
            case "sensors":
                return result.sensorDrainPct;
            default:
                return result.otherDrainPct;
        }
    }

    // ====================================================================================
    // HISTORICAL ANALYSIS
    // ====================================================================================

    /**
     * Get drain trend analysis for a time period
     */
    public DrainTrendAnalysis analyzeDrainTrend(long startTime, long endTime) {
        DrainTrendAnalysis trend = new DrainTrendAnalysis();
        trend.startTime = startTime;
        trend.endTime = endTime;

        try {
            List<DrainEvent> events = database.drainEventDao().getEventsBetween(startTime, endTime);

            if (events == null || events.isEmpty()) {
                trend.hasData = false;
                return trend;
            }

            trend.hasData = true;
            trend.totalEvents = events.size();

            float totalDrain = 0;
            float totalScreenOnDrain = 0;
            float totalScreenOffDrain = 0;
            int screenOnEvents = 0;
            int screenOffEvents = 0;
            int anomalyCount = 0;
            float maxDrainRate = 0;
            float minDrainRate = Float.MAX_VALUE;

            for (DrainEvent event : events) {
                totalDrain += event.drainAmount;

                if (event.screenOn) {
                    totalScreenOnDrain += event.drainRatePerHour;
                    screenOnEvents++;
                } else {
                    totalScreenOffDrain += event.drainRatePerHour;
                    screenOffEvents++;
                }

                if (event.isAnomalous) {
                    anomalyCount++;
                }

                if (event.drainRatePerHour > maxDrainRate) {
                    maxDrainRate = event.drainRatePerHour;
                }
                if (event.drainRatePerHour < minDrainRate && event.drainRatePerHour > 0) {
                    minDrainRate = event.drainRatePerHour;
                }
            }

            trend.totalDrainAmount = totalDrain;
            trend.avgScreenOnDrainRate = screenOnEvents > 0 ? totalScreenOnDrain / screenOnEvents : 0;
            trend.avgScreenOffDrainRate = screenOffEvents > 0 ? totalScreenOffDrain / screenOffEvents : 0;
            trend.maxDrainRate = maxDrainRate;
            trend.minDrainRate = minDrainRate == Float.MAX_VALUE ? 0 : minDrainRate;
            trend.anomalyCount = anomalyCount;
            trend.anomalyPercentage = (anomalyCount * 100f) / events.size();

            // Determine trend direction
            if (events.size() >= 3) {
                float firstHalfAvg = 0;
                float secondHalfAvg = 0;
                int halfPoint = events.size() / 2;

                for (int i = 0; i < halfPoint; i++) {
                    firstHalfAvg += events.get(i).drainRatePerHour;
                }
                for (int i = halfPoint; i < events.size(); i++) {
                    secondHalfAvg += events.get(i).drainRatePerHour;
                }

                firstHalfAvg /= halfPoint;
                secondHalfAvg /= (events.size() - halfPoint);

                float change = secondHalfAvg - firstHalfAvg;
                float changePercent = firstHalfAvg > 0 ? (change / firstHalfAvg) * 100 : 0;

                trend.trendDirection = change > 0.5f ? "INCREASING" : change < -0.5f ? "DECREASING" : "STABLE";
                trend.trendChangePercent = changePercent;
            } else {
                trend.trendDirection = "INSUFFICIENT_DATA";
                trend.trendChangePercent = 0;
            }

            // Grade overall drain
            float overallAvg = totalDrain / Math.max(1, (endTime - startTime) / 3600000f);
            trend.overallGrade = gradeOverallDrain(overallAvg, trend.avgScreenOnDrainRate, trend.avgScreenOffDrainRate);

        } catch (Exception e) {
            Log.e(TAG, "Failed to analyze drain trend", e);
            trend.hasData = false;
        }

        return trend;
    }

    /**
     * Grade overall drain performance
     */
    private String gradeOverallDrain(float avgDrain, float screenOnAvg, float screenOffAvg) {
        int score = 0;

        // Grade screen-on performance
        if (screenOnAvg < SCREEN_ON_EXCELLENT)
            score += 3;
        else if (screenOnAvg < SCREEN_ON_GOOD)
            score += 2;
        else if (screenOnAvg < SCREEN_ON_AVERAGE)
            score += 1;

        // Grade screen-off performance
        if (screenOffAvg < IDLE_EXCELLENT)
            score += 3;
        else if (screenOffAvg < IDLE_GOOD)
            score += 2;
        else if (screenOffAvg < IDLE_AVERAGE)
            score += 1;

        if (score >= 5)
            return "A";
        if (score >= 4)
            return "B";
        if (score >= 2)
            return "C";
        return "D";
    }

    // ====================================================================================
    // GETTERS FOR CURRENT STATE
    // ====================================================================================

    public float getEmaScreenOnDrainRate() {
        return emaScreenOnDrainRate;
    }

    public float getEmaScreenOffDrainRate() {
        return emaScreenOffDrainRate;
    }

    public float getEmaDozeDrainRate() {
        return emaDozeDrainRate;
    }

    public float getEmaDeepSleepEffectiveness() {
        return emaDeepSleepEffectiveness;
    }

    public int getConsecutiveAnomalies() {
        return consecutiveAnomalies;
    }

    public float getPeakDrainRateToday() {
        return peakDrainRateToday;
    }

    public float getTotalDrainToday() {
        return totalDrainToday;
    }

    // Baseline getters (aliases for EMA rates used by DrainAnalyzer)
    public float getBaselineScreenOnRate() {
        return emaScreenOnDrainRate;
    }

    public float getBaselineScreenOffRate() {
        return emaScreenOffDrainRate;
    }

    public float getBaselineDozeDrainRate() {
        return emaDozeDrainRate;
    }

    // Deep sleep timing getters
    public long getDeepSleepTimeMs() {
        return totalDeepSleepTime;
    }

    public long getLightSleepTimeMs() {
        return Math.max(0, totalIdleTime - totalDeepSleepTime);
    }

    // Trend tracking (computed from recent events)
    private float rollingAvgDrain = 0f;
    private String trendDirection = "STABLE";
    private float trendChangePercent = 0f;
    private boolean patternAnomaly = false;

    public String getTrendDirection() {
        return trendDirection;
    }

    public float getTrendChangePercent() {
        return trendChangePercent;
    }

    public float getRollingAverageDrain() {
        return rollingAvgDrain;
    }

    public boolean isPatternAnomaly() {
        return patternAnomaly || consecutiveAnomalies >= 3;
    }

    /**
     * Reset daily counters
     */
    public void resetDailyCounters() {
        peakDrainRateToday = 0;
        totalDrainToday = 0;
        consecutiveAnomalies = 0;
        rollingAvgDrain = 0;
        trendDirection = "STABLE";
        trendChangePercent = 0;
        patternAnomaly = false;
    }

    // ====================================================================================
    // RESULT CLASSES
    // ====================================================================================

    /**
     * Result of drain analysis
     */
    public static class DrainAnalysisResult {
        public boolean isValid;
        public long timestamp;
        public long snapshotId;

        // Basic metrics
        public float drainAmount;
        public float drainRatePerHour;
        public float durationMinutes;

        // Screen state
        public boolean isScreenOn;
        public String screenState;
        public String screenStateGrade;

        // Expected vs actual
        public float expectedDrainRate;
        public float expectedDrain;
        public float drainDelta;
        public float drainEfficiency;

        // Anomaly
        public float anomalyScore;
        public boolean isAnomalous;
        public String anomalySeverity;

        // Benchmark
        public String benchmarkResult;
        public float benchmarkDelta;

        // Component attribution
        public float displayDrainPct;
        public float cpuDrainPct;
        public float networkDrainPct;
        public float sensorDrainPct;
        public float otherDrainPct;
        public String primaryDrainComponent;

        // Deep sleep
        public float deepSleepEffectiveness;
        public String deepSleepGrade;
        public boolean isDeepSleepActive;

        // Idle quality
        public String idleDrainQuality;
        public String idleBenchmarkResult;

        // Output
        public String recommendations;
        public String analysisNotes;
    }

    /**
     * Result of drain trend analysis
     */
    public static class DrainTrendAnalysis {
        public long startTime;
        public long endTime;
        public boolean hasData;

        public int totalEvents;
        public float totalDrainAmount;
        public float avgScreenOnDrainRate;
        public float avgScreenOffDrainRate;
        public float maxDrainRate;
        public float minDrainRate;

        public int anomalyCount;
        public float anomalyPercentage;

        public String trendDirection;
        public float trendChangePercent;
        public String overallGrade;
    }
}
