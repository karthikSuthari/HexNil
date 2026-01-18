package com.example.hexnil.ai;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.model.AIAnalysisResult;
import com.example.hexnil.model.AnomalyRecord;
import com.example.hexnil.model.AppBatteryUsage;
import com.example.hexnil.model.BatterySnapshot;
import com.example.hexnil.model.DrainEvent;
import com.example.hexnil.model.ThermalEvent;
import com.example.hexnil.model.UpdateComparison;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * HexNil-AI Phase-2: OEM-Grade Thermal & Performance Intelligence Engine
 * 
 * OBJECTIVE: Identify root causes of heating, throttling, and performance
 * degradation
 * by correlating CPU, GPU, thermal, app, and background service behavior.
 * 
 * ANTI-HALLUCINATION ARCHITECTURE:
 * - Deterministic metrics computed FIRST before any AI interpretation
 * - Confidence scoring (0-100%) on all conclusions
 * - Reject conclusions with confidence < 60%
 * - Evidence-based explanations using actual metrics only
 * 
 * ROOT CAUSE ATTRIBUTION PRIORITY (MANDATORY ORDER):
 * 1. Safety / Thermal Critical
 * 2. Thermal Throttling
 * 3. System / Firmware Service
 * 4. User App (Foreground)
 * 5. User App (Background)
 * 6. Charging-related heat
 * 7. Environmental / External
 * 8. Unknown (if confidence < 60%)
 * 
 * OEM INTEGRATION: Full audit trail for engineering dashboards.
 */
public class ThermalPerformanceIntelligence {

    private static final String TAG = "ThermalPerfIntel";

    // ════════════════════════════════════════════════════════════════════════════════
    // THERMAL & PERFORMANCE THRESHOLDS (Per Phase-2 Specification)
    // ════════════════════════════════════════════════════════════════════════════════

    // Temperature thresholds
    private static final float SUSTAINED_TEMP_THRESHOLD = 42.0f; // °C
    private static final float CRITICAL_TEMP_THRESHOLD = 45.0f; // °C

    // Duration thresholds
    private static final long SUSTAINED_MIN_DURATION_MS = 2 * 60 * 1000; // 2 minutes

    // Rate thresholds
    private static final float RISE_RATE_THRESHOLD = 1.5f; // °C/min

    // Drain thresholds
    private static final float HIGH_DRAIN_RATE = 15.0f; // %/hr

    // Confidence thresholds
    private static final int MIN_CONFIDENCE_FOR_ASSERTION = 60;
    private static final int MIN_CONFIDENCE_FOR_ROOT_CAUSE = 75;
    private static final int MIN_EVIDENCE_POINTS = 3;

    private final HexNilDatabase database;

    // ════════════════════════════════════════════════════════════════════════════════
    // OUTPUT STRUCTURES
    // ════════════════════════════════════════════════════════════════════════════════

    /**
     * Severity levels for thermal/performance issues.
     */
    public enum Severity {
        INFO("info"),
        LOW("low"),
        MEDIUM("medium"),
        HIGH("high"),
        CRITICAL("critical");

        private final String value;

        Severity(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    /**
     * Root cause categories (priority order).
     */
    public enum RootCauseCategory {
        SAFETY_CRITICAL("safety", 1),
        THERMAL_THROTTLING("throttling", 2),
        SYSTEM_SERVICE("system", 3),
        FIRMWARE("firmware", 4),
        APP_FOREGROUND("app", 5),
        APP_BACKGROUND("app", 6),
        CHARGING("charging", 7),
        ENVIRONMENTAL("environmental", 8),
        HARDWARE("hardware", 9),
        UNKNOWN("unknown", 10);

        private final String category;
        private final int priority;

        RootCauseCategory(String category, int priority) {
            this.category = category;
            this.priority = priority;
        }

        public String getCategory() {
            return category;
        }

        public int getPriority() {
            return priority;
        }
    }

    /**
     * Deterministic metrics computed from raw data.
     * These MUST be calculated before any AI interpretation.
     */
    public static class DeterministicMetrics {
        // Temperature metrics
        public float maxTemperature = Float.MIN_VALUE;
        public float minTemperature = Float.MAX_VALUE;
        public float avgTemperature = 0;
        public float temperatureRiseRate = 0; // °C/min

        // Sustained heat tracking
        public long sustainedHeatDurationMs = 0;
        public long criticalTempDurationMs = 0;
        public boolean hasSustainedHeat = false;
        public boolean hasCriticalTemp = false;

        // Screen state correlation
        public float avgTempScreenOn = 0;
        public float avgTempScreenOff = 0;
        public long screenOnTimeMs = 0;
        public long screenOffTimeMs = 0;

        // Drain correlation
        public float avgDrainRate = 0;
        public float maxDrainRate = 0;
        public float drainTempCorrelation = 0; // -1 to 1

        // Thermal throttling
        public boolean thermalThrottlingDetected = false;
        public int throttlingEventCount = 0;

        // Post-update delta (if available)
        public boolean hasUpdateComparison = false;
        public float preUpdateAvgTemp = 0;
        public float postUpdateAvgTemp = 0;
        public float updateTempDelta = 0;
        public boolean postUpdateRegression = false;

        // CPU/GPU (when available)
        public boolean cpuDataAvailable = false;
        public boolean gpuDataAvailable = false;
        public float avgCpuLoad = 0;
        public float avgGpuLoad = 0;

        // Data quality
        public int snapshotCount = 0;
        public long timeWindowMs = 0;
        public float dataQualityScore = 0;

        public boolean hasEnoughData() {
            return snapshotCount >= 10 && timeWindowMs >= 5 * 60 * 1000; // 10 samples, 5 min
        }
    }

    /**
     * Root cause analysis result.
     */
    public static class RootCauseResult {
        public RootCauseCategory category;
        public String source; // Package name or system service
        public String explanation; // Technical evidence-based explanation
        public int confidence; // 0-100
        public List<String> evidence; // List of evidence citations
        public List<String> caveats; // Known limitations

        public RootCauseResult() {
            evidence = new ArrayList<>();
            caveats = new ArrayList<>();
        }

        public boolean meetsConfidenceThreshold() {
            return confidence >= MIN_CONFIDENCE_FOR_ROOT_CAUSE;
        }
    }

    /**
     * Complete Phase-2 output structure (strict JSON format).
     */
    public static class Phase2Output {
        // Thermal/Performance Summary
        public Severity severity = Severity.INFO;
        public int riskScore = 0;
        public int confidence = 0;
        public boolean sustainedHeating = false;
        public boolean thermalThrottlingDetected = false;

        // Root Cause
        public RootCauseResult rootCause;

        // Evidence
        public DeterministicMetrics evidence;

        // Recommendations
        public List<String> userActions = new ArrayList<>();
        public List<String> engineerActions = new ArrayList<>();

        // Storage instructions
        public boolean saveResults = true;
        public List<String> targetTables = new ArrayList<>();

        // Timestamp
        public long timestamp = System.currentTimeMillis();
        public long periodStart;
        public long periodEnd;

        public Phase2Output() {
            targetTables.add("anomaly_records");
            targetTables.add("ai_analysis_results");
        }

        /**
         * Convert to the strict JSON format specified in Phase-2.
         */
        public String toStrictJson() {
            try {
                JSONObject json = new JSONObject();

                // thermalPerformanceSummary block
                JSONObject summary = new JSONObject();
                summary.put("severity", severity.getValue());
                summary.put("riskScore", riskScore);
                summary.put("confidence", confidence);
                summary.put("sustainedHeating", sustainedHeating);
                summary.put("thermalThrottlingDetected", thermalThrottlingDetected);
                json.put("thermalPerformanceSummary", summary);

                // rootCause block
                JSONObject rootCauseObj = new JSONObject();
                if (rootCause != null && rootCause.meetsConfidenceThreshold()) {
                    rootCauseObj.put("category", rootCause.category.getCategory());
                    rootCauseObj.put("source", rootCause.source != null ? rootCause.source : "unknown");
                    rootCauseObj.put("explanation", rootCause.explanation);
                    rootCauseObj.put("confidence", rootCause.confidence);
                } else {
                    rootCauseObj.put("category", "unknown");
                    rootCauseObj.put("source", "insufficient_data");
                    rootCauseObj.put("explanation", "Confidence too low to attribute root cause");
                    rootCauseObj.put("confidence", rootCause != null ? rootCause.confidence : 0);
                }
                json.put("rootCause", rootCauseObj);

                // evidence block
                JSONObject evidenceObj = new JSONObject();
                if (evidence != null) {
                    evidenceObj.put("maxTemperature", String.format(Locale.US, "%.1f°C", evidence.maxTemperature));
                    evidenceObj.put("heatingDuration", (evidence.sustainedHeatDurationMs / 60000) + " minutes");
                    evidenceObj.put("drainRate", String.format(Locale.US, "%.1f%%/hr", evidence.avgDrainRate));
                    evidenceObj.put("screenState", evidence.screenOnTimeMs > evidence.screenOffTimeMs ? "on" : "off");
                    evidenceObj.put("postUpdateRegression", evidence.postUpdateRegression);
                }
                json.put("evidence", evidenceObj);

                // recommendations block
                JSONObject recsObj = new JSONObject();
                recsObj.put("userActions", new JSONArray(userActions));
                recsObj.put("engineerActions", new JSONArray(engineerActions));
                json.put("recommendations", recsObj);

                // storageInstructions block
                JSONObject storageObj = new JSONObject();
                storageObj.put("saveResults", saveResults);
                storageObj.put("tables", new JSONArray(targetTables));
                json.put("storageInstructions", storageObj);

                return json.toString(2);
            } catch (JSONException e) {
                Log.e(TAG, "Error generating JSON output", e);
                return "{}";
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ════════════════════════════════════════════════════════════════════════════════

    public ThermalPerformanceIntelligence(HexNilDatabase database) {
        this.database = database;
    }

    // ════════════════════════════════════════════════════════════════════════════════
    // MAIN ANALYSIS ENTRY POINT
    // ════════════════════════════════════════════════════════════════════════════════

    /**
     * Run complete Phase-2 thermal/performance analysis.
     * 
     * @param startTime Period start (ms since epoch)
     * @param endTime   Period end (ms since epoch)
     * @return Phase2Output with full analysis results
     */
    @NonNull
    public Phase2Output runAnalysis(long startTime, long endTime) {
        Log.i(TAG, "Starting Phase-2 analysis: " + startTime + " to " + endTime);

        Phase2Output output = new Phase2Output();
        output.periodStart = startTime;
        output.periodEnd = endTime;

        // STEP 1: Compute deterministic metrics (REQUIRED before AI interpretation)
        DeterministicMetrics metrics = computeDeterministicMetrics(startTime, endTime);
        output.evidence = metrics;

        // STEP 2: Check data sufficiency
        if (!metrics.hasEnoughData()) {
            Log.w(TAG, "Insufficient data for analysis");
            output.saveResults = false;
            output.rootCause = createInsufficientDataResult(metrics);
            return output;
        }

        // STEP 3: Determine severity based on thresholds
        output.severity = determineSeverity(metrics);
        output.sustainedHeating = metrics.hasSustainedHeat;
        output.thermalThrottlingDetected = metrics.thermalThrottlingDetected;

        // STEP 4: Calculate risk score
        output.riskScore = calculateRiskScore(metrics);

        // STEP 5: Attribute root cause (priority order)
        output.rootCause = attributeRootCause(metrics, startTime, endTime);
        output.confidence = output.rootCause.confidence;

        // STEP 6: Generate recommendations
        generateRecommendations(output);

        // STEP 7: Determine if results should be stored
        output.saveResults = output.rootCause.confidence >= MIN_CONFIDENCE_FOR_ASSERTION;

        Log.i(TAG, "Phase-2 analysis complete: severity=" + output.severity.getValue()
                + ", confidence=" + output.confidence + "%");

        return output;
    }

    // ════════════════════════════════════════════════════════════════════════════════
    // DETERMINISTIC METRICS COMPUTATION
    // ════════════════════════════════════════════════════════════════════════════════

    /**
     * Compute all deterministic metrics from raw data.
     * These are exact calculations that same input always produces same output.
     */
    @NonNull
    public DeterministicMetrics computeDeterministicMetrics(long startTime, long endTime) {
        DeterministicMetrics metrics = new DeterministicMetrics();
        metrics.timeWindowMs = endTime - startTime;

        // Fetch raw data
        List<BatterySnapshot> snapshots = database.batterySnapshotDao()
                .getSnapshotsInRange(startTime, endTime);
        List<ThermalEvent> thermalEvents = database.thermalEventDao()
                .getEventsSince(startTime);
        List<DrainEvent> drainEvents = database.drainEventDao()
                .getEventsSince(startTime);

        metrics.snapshotCount = snapshots.size();

        if (snapshots.isEmpty()) {
            return metrics;
        }

        // Temperature analysis
        computeTemperatureMetrics(snapshots, metrics);

        // Screen state correlation
        computeScreenStateMetrics(snapshots, metrics);

        // Drain correlation
        computeDrainMetrics(drainEvents, metrics);

        // Drain-to-temperature correlation
        computeDrainTempCorrelation(snapshots, metrics);

        // Thermal throttling detection
        detectThermalThrottling(thermalEvents, metrics);

        // Post-update comparison
        checkUpdateRegression(metrics);

        // CPU/GPU data availability check
        checkCpuGpuAvailability(snapshots, metrics);

        // Data quality score
        computeDataQualityScore(metrics);

        return metrics;
    }

    private void computeTemperatureMetrics(List<BatterySnapshot> snapshots, DeterministicMetrics metrics) {
        float sum = 0;
        Long sustainedHeatStart = null;
        Long criticalTempStart = null;
        Long lastTimestamp = null;

        for (int i = 0; i < snapshots.size(); i++) {
            BatterySnapshot snap = snapshots.get(i);
            float tempC = snap.temperature / 10.0f; // Convert from tenths

            // Min/max
            metrics.maxTemperature = Math.max(metrics.maxTemperature, tempC);
            metrics.minTemperature = Math.min(metrics.minTemperature, tempC);
            sum += tempC;

            // Sustained heat tracking (≥42°C for ≥2 min)
            if (tempC >= SUSTAINED_TEMP_THRESHOLD) {
                if (sustainedHeatStart == null) {
                    sustainedHeatStart = snap.timestamp;
                }
            } else {
                if (sustainedHeatStart != null) {
                    long duration = snap.timestamp - sustainedHeatStart;
                    if (duration >= SUSTAINED_MIN_DURATION_MS) {
                        metrics.sustainedHeatDurationMs += duration;
                        metrics.hasSustainedHeat = true;
                    }
                    sustainedHeatStart = null;
                }
            }

            // Critical temp tracking (≥45°C at any time)
            if (tempC >= CRITICAL_TEMP_THRESHOLD) {
                metrics.hasCriticalTemp = true;
                if (criticalTempStart == null) {
                    criticalTempStart = snap.timestamp;
                }
            } else {
                if (criticalTempStart != null) {
                    metrics.criticalTempDurationMs += snap.timestamp - criticalTempStart;
                    criticalTempStart = null;
                }
            }

            // Temperature rise rate (°C/min)
            if (lastTimestamp != null && i > 0) {
                BatterySnapshot prevSnap = snapshots.get(i - 1);
                float prevTempC = prevSnap.temperature / 10.0f;
                long deltaMs = snap.timestamp - lastTimestamp;
                if (deltaMs > 0) {
                    float ratePerMin = ((tempC - prevTempC) / deltaMs) * 60000;
                    metrics.temperatureRiseRate = Math.max(metrics.temperatureRiseRate, ratePerMin);
                }
            }
            lastTimestamp = snap.timestamp;
        }

        // Finalize sustained heat if still ongoing
        if (sustainedHeatStart != null && snapshots.size() > 0) {
            long lastTs = snapshots.get(snapshots.size() - 1).timestamp;
            long duration = lastTs - sustainedHeatStart;
            if (duration >= SUSTAINED_MIN_DURATION_MS) {
                metrics.sustainedHeatDurationMs += duration;
                metrics.hasSustainedHeat = true;
            }
        }

        metrics.avgTemperature = sum / snapshots.size();
    }

    private void computeScreenStateMetrics(List<BatterySnapshot> snapshots, DeterministicMetrics metrics) {
        float sumScreenOn = 0, sumScreenOff = 0;
        int countScreenOn = 0, countScreenOff = 0;
        long lastScreenOnTs = 0, lastScreenOffTs = 0;
        Boolean lastScreenState = null;

        for (BatterySnapshot snap : snapshots) {
            float tempC = snap.temperature / 10.0f;

            if (snap.screenOn) {
                sumScreenOn += tempC;
                countScreenOn++;
                if (lastScreenState != null && !lastScreenState) {
                    // Screen just turned on
                    lastScreenOnTs = snap.timestamp;
                }
            } else {
                sumScreenOff += tempC;
                countScreenOff++;
                if (lastScreenState != null && lastScreenState) {
                    // Screen just turned off
                    lastScreenOffTs = snap.timestamp;
                }
            }

            lastScreenState = snap.screenOn;
        }

        metrics.avgTempScreenOn = countScreenOn > 0 ? sumScreenOn / countScreenOn : 0;
        metrics.avgTempScreenOff = countScreenOff > 0 ? sumScreenOff / countScreenOff : 0;

        // Estimate screen time from snapshot distribution
        long totalTimeMs = metrics.timeWindowMs;
        if (snapshots.size() > 1) {
            float screenOnRatio = (float) countScreenOn / snapshots.size();
            metrics.screenOnTimeMs = (long) (totalTimeMs * screenOnRatio);
            metrics.screenOffTimeMs = totalTimeMs - metrics.screenOnTimeMs;
        }
    }

    private void computeDrainMetrics(List<DrainEvent> drainEvents, DeterministicMetrics metrics) {
        if (drainEvents.isEmpty()) {
            return;
        }

        float sum = 0;
        for (DrainEvent event : drainEvents) {
            sum += event.drainRatePerHour;
            metrics.maxDrainRate = Math.max(metrics.maxDrainRate, event.drainRatePerHour);
        }
        metrics.avgDrainRate = sum / drainEvents.size();
    }

    private void computeDrainTempCorrelation(List<BatterySnapshot> snapshots, DeterministicMetrics metrics) {
        // Pearson correlation between temperature and drain rate
        if (snapshots.size() < 10) {
            return;
        }

        // Calculate drain rates between consecutive snapshots
        List<Float> temps = new ArrayList<>();
        List<Float> drains = new ArrayList<>();

        for (int i = 1; i < snapshots.size(); i++) {
            BatterySnapshot prev = snapshots.get(i - 1);
            BatterySnapshot curr = snapshots.get(i);

            long deltaMs = curr.timestamp - prev.timestamp;
            if (deltaMs <= 0)
                continue;

            int drainPct = prev.level - curr.level;
            if (drainPct < 0)
                continue; // Charging

            float drainPerHour = (drainPct / (deltaMs / 3600000.0f));
            float temp = curr.temperature / 10.0f;

            temps.add(temp);
            drains.add(drainPerHour);
        }

        if (temps.size() < 5)
            return;

        // Calculate Pearson correlation
        float sumT = 0, sumD = 0;
        for (int i = 0; i < temps.size(); i++) {
            sumT += temps.get(i);
            sumD += drains.get(i);
        }
        float meanT = sumT / temps.size();
        float meanD = sumD / drains.size();

        float numerator = 0, denomT = 0, denomD = 0;
        for (int i = 0; i < temps.size(); i++) {
            float diffT = temps.get(i) - meanT;
            float diffD = drains.get(i) - meanD;
            numerator += diffT * diffD;
            denomT += diffT * diffT;
            denomD += diffD * diffD;
        }

        float denom = (float) Math.sqrt(denomT * denomD);
        metrics.drainTempCorrelation = denom > 0 ? numerator / denom : 0;
    }

    private void detectThermalThrottling(List<ThermalEvent> thermalEvents, DeterministicMetrics metrics) {
        for (ThermalEvent event : thermalEvents) {
            // Throttling is indicated by requiresAction being true (thermal throttle
            // framework activated)
            // or by critical/danger severity which would trigger throttling
            if (event.requiresAction ||
                    "critical".equals(event.severity) ||
                    "danger".equals(event.severity) ||
                    "emergency".equals(event.severity)) {
                metrics.thermalThrottlingDetected = true;
                metrics.throttlingEventCount++;
            }
        }
    }

    private void checkUpdateRegression(DeterministicMetrics metrics) {
        // Check for recent update comparisons
        try {
            UpdateComparison comparison = database.miscDao().getLatestUpdateComparison();
            if (comparison != null && comparison.postAvgTemperature > 0) {
                metrics.hasUpdateComparison = true;
                metrics.preUpdateAvgTemp = comparison.preAvgTemperature;
                metrics.postUpdateAvgTemp = comparison.postAvgTemperature;
                metrics.updateTempDelta = comparison.postAvgTemperature - comparison.preAvgTemperature;

                // Regression if post-update temp is 3°C+ higher
                metrics.postUpdateRegression = metrics.updateTempDelta >= 3.0f;
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not fetch update comparison", e);
        }
    }

    private void checkCpuGpuAvailability(List<BatterySnapshot> snapshots, DeterministicMetrics metrics) {
        // CPU/GPU data is not directly available via standard Android APIs
        // Mark as unavailable per Phase-2 spec
        metrics.cpuDataAvailable = false;
        metrics.gpuDataAvailable = false;
        metrics.avgCpuLoad = 0;
        metrics.avgGpuLoad = 0;
    }

    private void computeDataQualityScore(DeterministicMetrics metrics) {
        float score = 0;

        // Sample count contribution (0-40 points)
        if (metrics.snapshotCount >= 100)
            score += 40;
        else if (metrics.snapshotCount >= 50)
            score += 30;
        else if (metrics.snapshotCount >= 20)
            score += 20;
        else if (metrics.snapshotCount >= 10)
            score += 10;

        // Time window contribution (0-30 points)
        long hoursOfData = metrics.timeWindowMs / (60 * 60 * 1000);
        if (hoursOfData >= 24)
            score += 30;
        else if (hoursOfData >= 12)
            score += 25;
        else if (hoursOfData >= 4)
            score += 20;
        else if (hoursOfData >= 1)
            score += 10;

        // Temperature range validity (0-20 points)
        if (metrics.maxTemperature < 60 && metrics.minTemperature > 10)
            score += 20;
        else if (metrics.maxTemperature < 70 && metrics.minTemperature > 0)
            score += 10;

        // Data availability (0-10 points)
        if (metrics.avgDrainRate > 0)
            score += 5;
        if (metrics.screenOnTimeMs > 0 || metrics.screenOffTimeMs > 0)
            score += 5;

        metrics.dataQualityScore = score;
    }

    // ════════════════════════════════════════════════════════════════════════════════
    // SEVERITY DETERMINATION
    // ════════════════════════════════════════════════════════════════════════════════

    private Severity determineSeverity(DeterministicMetrics metrics) {
        // CRITICAL: ≥45°C at any time
        if (metrics.hasCriticalTemp) {
            return Severity.CRITICAL;
        }

        // HIGH: ≥42°C sustained ≥2 min OR rise rate ≥1.5°C/min
        if (metrics.hasSustainedHeat || metrics.temperatureRiseRate >= RISE_RATE_THRESHOLD) {
            return Severity.HIGH;
        }

        // MEDIUM: Drain ≥15%/hr with elevated temperature OR thermal throttling
        if ((metrics.avgDrainRate >= HIGH_DRAIN_RATE && metrics.avgTemperature >= 38)
                || metrics.thermalThrottlingDetected) {
            return Severity.MEDIUM;
        }

        // LOW: Any thermal event recorded
        if (metrics.maxTemperature >= 40) {
            return Severity.LOW;
        }

        return Severity.INFO;
    }

    // ════════════════════════════════════════════════════════════════════════════════
    // RISK SCORE CALCULATION
    // ════════════════════════════════════════════════════════════════════════════════

    private int calculateRiskScore(DeterministicMetrics metrics) {
        float score = 0;

        // Temperature contribution (0-40 points)
        if (metrics.hasCriticalTemp)
            score += 40;
        else if (metrics.hasSustainedHeat)
            score += 30;
        else if (metrics.maxTemperature >= 42)
            score += 20;
        else if (metrics.maxTemperature >= 40)
            score += 10;

        // Thermal throttling (0-20 points)
        if (metrics.thermalThrottlingDetected) {
            score += Math.min(20, metrics.throttlingEventCount * 5);
        }

        // Rise rate (0-15 points)
        if (metrics.temperatureRiseRate >= RISE_RATE_THRESHOLD)
            score += 15;
        else if (metrics.temperatureRiseRate >= 1.0)
            score += 10;

        // Drain correlation (0-15 points)
        if (metrics.avgDrainRate >= HIGH_DRAIN_RATE && metrics.avgTemperature >= 40) {
            score += 15;
        }

        // Post-update regression (0-10 points)
        if (metrics.postUpdateRegression)
            score += 10;

        return Math.min(100, Math.round(score));
    }

    // ════════════════════════════════════════════════════════════════════════════════
    // ROOT CAUSE ATTRIBUTION (PRIORITY ORDER)
    // ════════════════════════════════════════════════════════════════════════════════

    @NonNull
    private RootCauseResult attributeRootCause(DeterministicMetrics metrics, long startTime, long endTime) {
        RootCauseResult result = new RootCauseResult();

        // Priority 1: Safety / Thermal Critical
        if (checkSafetyCritical(metrics, result)) {
            return result;
        }

        // Priority 2: Thermal Throttling
        if (checkThermalThrottling(metrics, result)) {
            return result;
        }

        // Priority 3: System / Firmware Service
        if (checkSystemFirmware(metrics, result)) {
            return result;
        }

        // Priority 4 & 5: User App (Foreground / Background)
        if (checkAppCauses(metrics, startTime, endTime, result)) {
            return result;
        }

        // Priority 6: Charging-related heat
        if (checkChargingHeat(metrics, startTime, endTime, result)) {
            return result;
        }

        // Priority 7: Environmental / External
        if (checkEnvironmental(metrics, result)) {
            return result;
        }

        // Priority 8: Unknown (confidence < 60%)
        result.category = RootCauseCategory.UNKNOWN;
        result.source = "insufficient_evidence";
        result.explanation = "Unable to determine root cause with sufficient confidence. " +
                "Multiple factors may be contributing, or data is insufficient for attribution.";
        result.confidence = Math.min(59, calculateBaseConfidence(metrics));
        result.caveats.add("Confidence below threshold for root cause assertion");

        return result;
    }

    private boolean checkSafetyCritical(DeterministicMetrics metrics, RootCauseResult result) {
        if (metrics.hasCriticalTemp && metrics.criticalTempDurationMs > 30000) {
            result.category = RootCauseCategory.SAFETY_CRITICAL;
            result.source = "battery_thermal_safety";
            result.explanation = String.format(Locale.US,
                    "Critical temperature (≥%.1f°C) detected for %d seconds. " +
                            "Battery thermal safety threshold exceeded. Immediate action required.",
                    metrics.maxTemperature, metrics.criticalTempDurationMs / 1000);
            result.confidence = 95;
            result.evidence.add("Max temperature: " + metrics.maxTemperature + "°C");
            result.evidence.add("Critical duration: " + (metrics.criticalTempDurationMs / 1000) + "s");
            return true;
        }
        return false;
    }

    private boolean checkThermalThrottling(DeterministicMetrics metrics, RootCauseResult result) {
        if (metrics.thermalThrottlingDetected) {
            result.category = RootCauseCategory.THERMAL_THROTTLING;
            result.source = "thermal_throttle_framework";
            result.explanation = String.format(Locale.US,
                    "Thermal throttling triggered %d times. Device performance was reduced " +
                            "to manage temperature. Avg temp: %.1f°C, Max temp: %.1f°C",
                    metrics.throttlingEventCount, metrics.avgTemperature, metrics.maxTemperature);
            result.confidence = 90;
            result.evidence.add("Throttling events: " + metrics.throttlingEventCount);
            result.evidence.add("Avg temperature: " + metrics.avgTemperature + "°C");
            return true;
        }
        return false;
    }

    private boolean checkSystemFirmware(DeterministicMetrics metrics, RootCauseResult result) {
        // Post-update regression indicates firmware issue
        if (metrics.postUpdateRegression && metrics.updateTempDelta >= 3.0f) {
            result.category = RootCauseCategory.FIRMWARE;
            result.source = "firmware_update_regression";
            result.explanation = String.format(Locale.US,
                    "Temperature increased %.1f°C after recent OTA update. " +
                            "Pre-update avg: %.1f°C, Post-update avg: %.1f°C. " +
                            "Likely firmware/driver regression.",
                    metrics.updateTempDelta, metrics.preUpdateAvgTemp, metrics.postUpdateAvgTemp);
            result.confidence = 85;
            result.evidence.add("Pre-update temp: " + metrics.preUpdateAvgTemp + "°C");
            result.evidence.add("Post-update temp: " + metrics.postUpdateAvgTemp + "°C");
            result.evidence.add("Delta: +" + metrics.updateTempDelta + "°C");
            return true;
        }

        // Screen-off heating indicates background system issue
        if (metrics.avgTempScreenOff > metrics.avgTempScreenOn &&
                metrics.avgTempScreenOff >= 40 &&
                metrics.screenOffTimeMs > metrics.screenOnTimeMs) {
            result.category = RootCauseCategory.SYSTEM_SERVICE;
            result.source = "background_system_service";
            result.explanation = String.format(Locale.US,
                    "Higher temperature during screen-off (%.1f°C) than screen-on (%.1f°C). " +
                            "Background system service(s) causing heating while device is idle.",
                    metrics.avgTempScreenOff, metrics.avgTempScreenOn);
            result.confidence = 75;
            result.evidence.add("Screen-off temp: " + metrics.avgTempScreenOff + "°C");
            result.evidence.add("Screen-on temp: " + metrics.avgTempScreenOn + "°C");
            return true;
        }

        return false;
    }

    private boolean checkAppCauses(DeterministicMetrics metrics, long startTime, long endTime,
            RootCauseResult result) {
        // Get app battery usage data
        List<AppBatteryUsage> appUsage = database.appBatteryUsageDao()
                .getUsageSince(startTime);

        if (appUsage.isEmpty()) {
            return false;
        }

        // Sort by drain rate
        Collections.sort(appUsage, (a, b) -> Float.compare(b.drainPerHour, a.drainPerHour));

        // Check top draining app
        AppBatteryUsage topApp = appUsage.get(0);

        // Foreground app causing heating
        if (topApp.foregroundTime > topApp.backgroundTime &&
                topApp.drainPerHour >= HIGH_DRAIN_RATE &&
                metrics.avgTempScreenOn >= 40) {
            result.category = RootCauseCategory.APP_FOREGROUND;
            result.source = topApp.packageName;
            result.explanation = String.format(Locale.US,
                    "App '%s' consuming %.1f%%/hr while in foreground. " +
                            "Heavy app usage during active use causing elevated temperature.",
                    topApp.appName != null ? topApp.appName : topApp.packageName,
                    topApp.drainPerHour);
            result.confidence = 80;
            result.evidence.add("App: " + topApp.packageName);
            result.evidence.add("Drain rate: " + topApp.drainPerHour + "%/hr");
            result.evidence.add("Foreground time: " + (topApp.foregroundTime / 60000) + "min");
            return true;
        }

        // Background app causing heating
        if (topApp.backgroundTime > topApp.foregroundTime &&
                topApp.drainPerHour >= 5.0f &&
                metrics.avgTempScreenOff >= 38) {
            result.category = RootCauseCategory.APP_BACKGROUND;
            result.source = topApp.packageName;
            result.explanation = String.format(Locale.US,
                    "App '%s' consuming %.1f%%/hr while in background. " +
                            "Background activity causing heating while screen is off.",
                    topApp.appName != null ? topApp.appName : topApp.packageName,
                    topApp.drainPerHour);
            result.confidence = 75;
            result.evidence.add("App: " + topApp.packageName);
            result.evidence.add("Background drain rate: " + topApp.drainPerHour + "%/hr");
            result.evidence.add("Background time: " + (topApp.backgroundTime / 60000) + "min");
            return true;
        }

        return false;
    }

    private boolean checkChargingHeat(DeterministicMetrics metrics, long startTime, long endTime,
            RootCauseResult result) {
        // Check for charging sessions during the period
        List<BatterySnapshot> snapshots = database.batterySnapshotDao()
                .getSnapshotsInRange(startTime, endTime);

        int chargingCount = 0;
        float chargingTempSum = 0;

        for (BatterySnapshot snap : snapshots) {
            if (snap.isCharging) {
                chargingCount++;
                chargingTempSum += snap.temperature / 10.0f;
            }
        }

        if (chargingCount > snapshots.size() * 0.3) { // >30% of snapshots were charging
            float avgChargingTemp = chargingTempSum / chargingCount;
            if (avgChargingTemp >= 40) {
                result.category = RootCauseCategory.CHARGING;
                result.source = "charging_thermal";
                result.explanation = String.format(Locale.US,
                        "Charging-related heating detected. Average temperature %.1f°C while charging. " +
                                "Normal for fast charging but monitor if sustained above 42°C.",
                        avgChargingTemp);
                result.confidence = 70;
                result.evidence.add("Avg charging temp: " + avgChargingTemp + "°C");
                result.evidence.add("Charging snapshots: " + chargingCount);
                return true;
            }
        }

        return false;
    }

    private boolean checkEnvironmental(DeterministicMetrics metrics, RootCauseResult result) {
        // Environmental cause if baseline temp is high but no app/system indicator
        if (metrics.minTemperature >= 35 &&
                metrics.avgDrainRate < HIGH_DRAIN_RATE &&
                !metrics.thermalThrottlingDetected) {
            result.category = RootCauseCategory.ENVIRONMENTAL;
            result.source = "ambient_temperature";
            result.explanation = String.format(Locale.US,
                    "Minimum recorded temperature %.1f°C suggests environmental factors. " +
                            "Device may be in hot environment or direct sunlight.",
                    metrics.minTemperature);
            result.confidence = 65;
            result.evidence.add("Min temperature: " + metrics.minTemperature + "°C");
            result.evidence.add("Drain rate normal: " + metrics.avgDrainRate + "%/hr");
            result.caveats.add("Environmental cause is inferred, not directly measurable");
            return true;
        }

        return false;
    }

    private int calculateBaseConfidence(DeterministicMetrics metrics) {
        int confidence = 30; // Base

        if (metrics.snapshotCount >= 50)
            confidence += 15;
        else if (metrics.snapshotCount >= 20)
            confidence += 10;

        if (metrics.dataQualityScore >= 70)
            confidence += 15;
        else if (metrics.dataQualityScore >= 50)
            confidence += 10;

        return confidence;
    }

    private RootCauseResult createInsufficientDataResult(DeterministicMetrics metrics) {
        RootCauseResult result = new RootCauseResult();
        result.category = RootCauseCategory.UNKNOWN;
        result.source = "insufficient_data";
        result.explanation = String.format(Locale.US,
                "Insufficient data for analysis. Available: %d snapshots, %.0f%% quality. " +
                        "Require minimum 10 snapshots over 5 minutes.",
                metrics.snapshotCount, metrics.dataQualityScore);
        result.confidence = 0;
        result.caveats.add("INSUFFICIENT DATA - analysis cannot be performed");
        return result;
    }

    // ════════════════════════════════════════════════════════════════════════════════
    // RECOMMENDATION GENERATION
    // ════════════════════════════════════════════════════════════════════════════════

    private void generateRecommendations(Phase2Output output) {
        RootCauseResult root = output.rootCause;
        DeterministicMetrics metrics = output.evidence;

        switch (root.category) {
            case SAFETY_CRITICAL:
                output.userActions.add("Stop using the device immediately and allow it to cool");
                output.userActions.add("Do not charge the device until it has cooled below 40°C");
                output.userActions.add("If heating recurs, contact manufacturer support");
                output.engineerActions.add("Review thermal protection firmware thresholds");
                output.engineerActions.add("Check battery thermal sensor calibration");
                output.engineerActions.add("Collect thermal traces for lab analysis");
                break;

            case THERMAL_THROTTLING:
                output.userActions.add("Reduce heavy app usage (gaming, video)");
                output.userActions.add("Remove phone case to improve heat dissipation");
                output.userActions.add("Avoid using device while charging");
                output.engineerActions.add("Review throttling thresholds vs actual thermal performance");
                output.engineerActions.add("Check CPU/GPU governor configuration");
                break;

            case FIRMWARE:
                output.userActions.add("Wait for next firmware update with thermal fix");
                output.userActions.add("Report issue to manufacturer if persistent");
                output.engineerActions.add("Compare thermal profiles pre/post OTA");
                output.engineerActions.add("Review driver changes in recent update");
                output.engineerActions.add("Prepare rollback if regression confirmed");
                break;

            case SYSTEM_SERVICE:
                output.userActions.add("Check for stuck system processes in Settings > Battery");
                output.userActions.add("Restart device to clear hung services");
                output.engineerActions.add("Identify high-CPU background services");
                output.engineerActions.add("Review wakelock patterns");
                break;

            case APP_FOREGROUND:
            case APP_BACKGROUND:
                output.userActions.add("Check app '" + root.source + "' for updates");
                output.userActions.add("Clear app cache or reinstall if issue persists");
                output.userActions.add("Restrict background activity for the app");
                output.engineerActions.add("Review app CPU usage profile");
                output.engineerActions.add("Check for inefficient rendering or polling");
                break;

            case CHARGING:
                output.userActions.add("Use standard charger instead of fast charger in hot environments");
                output.userActions.add("Do not use device heavily while charging");
                output.userActions.add("Charge in cooler environments when possible");
                output.engineerActions.add("Review charging thermal limits");
                output.engineerActions.add("Consider dynamic charging rate based on temperature");
                break;

            case ENVIRONMENTAL:
                output.userActions.add("Move device to cooler location");
                output.userActions.add("Avoid direct sunlight exposure");
                output.userActions.add("Remove protective case if present");
                output.engineerActions.add("No engineering action required - environmental cause");
                break;

            default:
                output.userActions.add("Monitor device temperature");
                output.userActions.add("Restart device if heating persists");
                output.engineerActions.add("Collect additional diagnostic data");
                output.engineerActions.add("Review thermal logs for patterns");
        }
    }

    // ════════════════════════════════════════════════════════════════════════════════
    // DATABASE STORAGE
    // ════════════════════════════════════════════════════════════════════════════════

    /**
     * Store Phase-2 analysis results to Room DB.
     * Writes to both anomaly_records and ai_analysis_results tables.
     */
    public void storeResults(Phase2Output output) {
        if (!output.saveResults) {
            Log.i(TAG, "Skipping storage - saveResults=false");
            return;
        }

        // Store as anomaly record if severity is medium or higher
        if (output.severity.ordinal() >= Severity.MEDIUM.ordinal()) {
            AnomalyRecord anomaly = createAnomalyRecord(output);
            database.anomalyRecordDao().insert(anomaly);
            Log.i(TAG, "Stored anomaly record: " + anomaly.title);
        }

        // Store as AI analysis result
        AIAnalysisResult result = createAIAnalysisResult(output);
        database.miscDao().insertAIAnalysis(result);
        Log.i(TAG, "Stored AI analysis result: " + result.headline);
    }

    private AnomalyRecord createAnomalyRecord(Phase2Output output) {
        AnomalyRecord record = AnomalyRecord.thermalAnomaly(
                output.evidence.maxTemperature,
                output.evidence.avgTemperature,
                output.rootCause.explanation);

        record.severity = output.severity.getValue();
        record.confidenceScore = output.confidence;
        record.rootCause = output.rootCause.category.getCategory();
        record.sourceComponent = "ThermalPerformanceIntelligence";
        record.periodStart = output.periodStart;
        record.periodEnd = output.periodEnd;
        record.technicalDetails = output.toStrictJson();

        if (!output.userActions.isEmpty()) {
            record.recommendation = output.userActions.get(0);
        }

        return record;
    }

    private AIAnalysisResult createAIAnalysisResult(Phase2Output output) {
        AIAnalysisResult result = new AIAnalysisResult();

        result.timestamp = output.timestamp;
        result.analysisType = "phase2_thermal";
        result.analysisPeriod = ((output.periodEnd - output.periodStart) / 3600000) + "h";
        result.dataPointsAnalyzed = output.evidence.snapshotCount;

        // AI readiness
        result.aiWasAllowedToRun = true;
        result.aiReadinessScore = output.evidence.dataQualityScore / 100f;

        // Deterministic results
        result.tempMaxCelsius = output.evidence.maxTemperature;
        result.tempMinCelsius = output.evidence.minTemperature;
        result.tempMeanCelsius = output.evidence.avgTemperature;
        result.drainRateMeanPerHour = output.evidence.avgDrainRate;

        // Root cause
        result.rootCause = output.rootCause.explanation;
        result.rootCauseCode = output.rootCause.category.name();
        result.rootCauseConfidence = output.rootCause.confidence / 100f;
        result.rootCauseConfidenceLevel = output.confidence >= 90 ? "VERY_HIGH"
                : output.confidence >= 75 ? "HIGH" : output.confidence >= 60 ? "MODERATE" : "LOW";

        // Severity
        result.severity = output.severity.getValue();
        result.riskScore = output.riskScore;
        result.requiresAction = output.severity.ordinal() >= Severity.HIGH.ordinal();

        // Recommendations
        result.userActions = String.join("; ", output.userActions);
        result.engineerActions = String.join("; ", output.engineerActions);

        // Structured output
        result.structuredReport = output.toStrictJson();
        result.headline = "Phase-2 Thermal Analysis: " + output.severity.getValue().toUpperCase();

        // Thermal-specific
        result.thermalStressLevel = output.severity.getValue().toUpperCase();
        result.thermalStressConfidence = output.confidence / 100f;

        // Confidence metrics
        result.overallConfidenceScore = output.confidence / 100f;
        result.overallConfidenceLevel = result.rootCauseConfidenceLevel;
        result.evidencePointCount = output.rootCause.evidence.size();
        result.includesAIContent = false; // Phase-2 is deterministic

        return result;
    }
}
