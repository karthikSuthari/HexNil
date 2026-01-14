package com.example.hexnil.calibration;

import android.content.Context;
import android.os.BatteryManager;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.model.BatteryHealth;
import com.example.hexnil.model.BatterySnapshot;
import com.example.hexnil.model.CalibrationData;

import java.util.ArrayList;
import java.util.List;

/**
 * CalibrationIntelligence - Advanced SOC Calibration and Drift Analysis Engine
 * 
 * Provides OEM-grade State of Charge (SOC) calibration intelligence including:
 * - Reported SOC vs estimated actual SOC comparison
 * - Multi-method drift calculation with confidence scoring
 * - Drift direction and velocity analysis
 * - Sudden drop detection with pattern recognition
 * - Calibration quality scoring with success criteria
 * - Mathematical formulas with documented methodology
 * 
 * SOC DRIFT FORMULAS:
 * ==================
 * 
 * 1. INSTANTANEOUS DRIFT:
 *    drift = estimatedSOC - reportedSOC
 *    drift > 0: Battery under-reporting (has more charge than shown)
 *    drift < 0: Battery over-reporting (has less charge than shown)
 * 
 * 2. WEIGHTED AVERAGE DRIFT (Exponential Moving Average):
 *    EMA_drift = α × current_drift + (1-α) × previous_EMA
 *    where α = smoothing factor (0.1 for stability, 0.3 for responsiveness)
 * 
 * 3. DRIFT VELOCITY (Rate of Change):
 *    drift_velocity = Δdrift / Δtime (percent per hour)
 *    Indicates how quickly calibration is degrading
 * 
 * 4. CALIBRATION QUALITY SCORE:
 *    quality = 100 - (|avg_drift| × 2.5) - (sudden_drops × 8) - (drift_velocity × 5)
 *    Penalties: drift > 5% (-12.5), drops (-8 each), velocity > 1%/hr (-5)
 * 
 * 5. CONFIDENCE SCORE:
 *    confidence = base + sample_bonus + stability_bonus - volatility_penalty
 *    base = 40 (minimum baseline)
 *    sample_bonus = min(30, samples × 2)
 *    stability_bonus = (30 - |drift_velocity| × 10)
 *    volatility_penalty = stddev(drift) × 5
 * 
 * 6. CALIBRATION SUCCESS CRITERIA:
 *    - Average drift < 3%
 *    - No sudden drops in last 7 days
 *    - Drift velocity < 0.5%/hr
 *    - At least one full charge cycle (0→100%) in last 30 days
 *    - At least one deep discharge (100→5%) in last 30 days
 * 
 * SUDDEN DROP DETECTION:
 * =====================
 * A sudden drop is detected when:
 * - Level drops ≥ 5% in ≤ 15 minutes
 * - Rate of change > 20%/hour
 * - No corresponding energy discharge (coulomb counter mismatch)
 * 
 * @author HexNil Battery Intelligence Team
 * @version 2.0.0
 */
public class CalibrationIntelligence {
    
    private static final String TAG = "CalibrationIntelligence";
    
    // ========== DRIFT THRESHOLDS ==========
    
    /** Minor drift - no action needed */
    public static final float DRIFT_MINOR_THRESHOLD = 3.0f;
    
    /** Moderate drift - worth monitoring */
    public static final float DRIFT_MODERATE_THRESHOLD = 5.0f;
    
    /** Significant drift - calibration recommended */
    public static final float DRIFT_SIGNIFICANT_THRESHOLD = 8.0f;
    
    /** Severe drift - calibration required */
    public static final float DRIFT_SEVERE_THRESHOLD = 12.0f;
    
    /** Critical drift - possible battery issue */
    public static final float DRIFT_CRITICAL_THRESHOLD = 20.0f;
    
    // ========== SUDDEN DROP THRESHOLDS ==========
    
    /** Minimum drop to be considered sudden (%) */
    public static final int SUDDEN_DROP_MIN_PERCENT = 5;
    
    /** Maximum time for drop to be sudden (minutes) */
    public static final int SUDDEN_DROP_MAX_MINUTES = 15;
    
    /** Rate threshold for sudden drop (%/hour) */
    public static final float SUDDEN_DROP_RATE_THRESHOLD = 20.0f;
    
    // ========== CALIBRATION QUALITY THRESHOLDS ==========
    
    /** Excellent calibration score */
    public static final float QUALITY_EXCELLENT = 90.0f;
    
    /** Good calibration score */
    public static final float QUALITY_GOOD = 75.0f;
    
    /** Fair calibration score */
    public static final float QUALITY_FAIR = 50.0f;
    
    /** Poor calibration score */
    public static final float QUALITY_POOR = 25.0f;
    
    // ========== EMA SMOOTHING FACTORS ==========
    
    /** Stable smoothing (slow response, less noise) */
    public static final float EMA_STABLE = 0.1f;
    
    /** Balanced smoothing */
    public static final float EMA_BALANCED = 0.2f;
    
    /** Responsive smoothing (fast response, more noise) */
    public static final float EMA_RESPONSIVE = 0.3f;
    
    // ========== INSTANCE VARIABLES ==========
    
    private final Context context;
    private final HexNilDatabase database;
    
    // Rolling statistics
    private final List<DriftSample> driftHistory;
    private float emaDrift = 0f;
    private float driftVelocity = 0f;
    private int suddenDropCount = 0;
    private long lastSampleTimestamp = 0L;
    private int lastReportedLevel = -1;
    
    // Calibration tracking
    private long lastFullChargeTimestamp = 0L;
    private long lastDeepDischargeTimestamp = 0L;
    private int fullCycleCount = 0;
    private boolean hasValidCalibration = false;
    
    public CalibrationIntelligence(Context context, HexNilDatabase database) {
        this.context = context;
        this.database = database;
        this.driftHistory = new ArrayList<>();
        
        // Load historical state
        loadHistoricalState();
    }
    
    // ==================================================================================
    //                              MAIN ANALYSIS METHODS
    // ==================================================================================
    
    /**
     * Analyze a battery snapshot for calibration intelligence
     * 
     * @param snapshot Current battery snapshot
     * @param estimatedSOC Estimated actual SOC from SOCEstimator
     * @return Complete calibration analysis
     */
    public CalibrationAnalysis analyzeSnapshot(BatterySnapshot snapshot, float estimatedSOC) {
        if (snapshot == null) {
            return CalibrationAnalysis.invalid("Null snapshot");
        }
        
        int reportedSOC = snapshot.level;
        long timestamp = snapshot.timestamp;
        
        // Calculate instantaneous drift
        float instantDrift = estimatedSOC - reportedSOC;
        
        // Determine drift direction
        DriftDirection direction = classifyDriftDirection(instantDrift);
        
        // Update EMA drift
        updateEMADrift(instantDrift);
        
        // Calculate drift velocity
        float velocity = calculateDriftVelocity(instantDrift, timestamp);
        
        // Detect sudden drops
        SuddenDropResult dropResult = detectSuddenDrop(snapshot);
        
        // Calculate calibration quality
        float quality = calculateCalibrationQuality();
        
        // Calculate confidence in our analysis
        float confidence = calculateConfidence();
        
        // Check calibration success criteria
        CalibrationSuccessResult successResult = evaluateCalibrationSuccess();
        
        // Calculate health impact
        float healthImpact = calculateHealthImpact();
        
        // Update tracking
        updateTrackingState(snapshot, instantDrift, timestamp);
        
        // Build complete analysis
        CalibrationAnalysis analysis = new CalibrationAnalysis();
        analysis.timestamp = timestamp;
        analysis.reportedSOC = reportedSOC;
        analysis.estimatedSOC = estimatedSOC;
        analysis.instantaneousDrift = instantDrift;
        analysis.emaDrift = emaDrift;
        analysis.driftDirection = direction;
        analysis.driftVelocity = velocity;
        analysis.driftSeverity = classifyDriftSeverity(Math.abs(emaDrift));
        analysis.suddenDropDetected = dropResult.detected;
        analysis.suddenDropDetails = dropResult;
        analysis.totalSuddenDrops = suddenDropCount;
        analysis.calibrationQuality = quality;
        analysis.qualityGrade = gradeQuality(quality);
        analysis.confidence = confidence;
        analysis.confidenceGrade = gradeConfidence(confidence);
        analysis.isCalibrated = successResult.isCalibrated;
        analysis.calibrationSuccessCriteria = successResult;
        analysis.needsCalibration = !successResult.isCalibrated || quality < QUALITY_FAIR;
        analysis.calibrationUrgency = determineCalibrationUrgency(analysis);
        analysis.healthImpactScore = healthImpact;
        analysis.daysSinceFullCycle = calculateDaysSinceFullCycle();
        analysis.valid = true;
        
        return analysis;
    }
    
    /**
     * Get historical drift analysis for a time period
     */
    public DriftHistoryAnalysis analyzeDriftHistory(long startTime, long endTime) {
        List<CalibrationData> history = database.miscDao().getCalibrationHistory(startTime);
        
        if (history == null || history.isEmpty()) {
            return DriftHistoryAnalysis.empty();
        }
        
        // Filter to time range
        List<CalibrationData> filtered = new ArrayList<>();
        for (CalibrationData data : history) {
            if (data.timestamp >= startTime && data.timestamp <= endTime) {
                filtered.add(data);
            }
        }
        
        if (filtered.isEmpty()) {
            return DriftHistoryAnalysis.empty();
        }
        
        // Calculate statistics
        float sumDrift = 0f;
        float minDrift = Float.MAX_VALUE;
        float maxDrift = Float.MIN_VALUE;
        int underReportingCount = 0;
        int overReportingCount = 0;
        int stableCount = 0;
        int suddenDropTotal = 0;
        
        for (CalibrationData data : filtered) {
            sumDrift += data.levelDrift;
            minDrift = Math.min(minDrift, data.levelDrift);
            maxDrift = Math.max(maxDrift, data.levelDrift);
            
            if (data.levelDrift > DRIFT_MINOR_THRESHOLD) {
                underReportingCount++;
            } else if (data.levelDrift < -DRIFT_MINOR_THRESHOLD) {
                overReportingCount++;
            } else {
                stableCount++;
            }
            
            suddenDropTotal += data.suddenDropCount;
        }
        
        float avgDrift = sumDrift / filtered.size();
        
        // Calculate standard deviation
        float sumSquares = 0f;
        for (CalibrationData data : filtered) {
            float diff = data.levelDrift - avgDrift;
            sumSquares += diff * diff;
        }
        float stdDev = (float) Math.sqrt(sumSquares / filtered.size());
        
        // Calculate drift trend (regression slope)
        float driftTrend = calculateDriftTrend(filtered);
        
        // Build result
        DriftHistoryAnalysis analysis = new DriftHistoryAnalysis();
        analysis.startTime = startTime;
        analysis.endTime = endTime;
        analysis.sampleCount = filtered.size();
        analysis.avgDrift = avgDrift;
        analysis.minDrift = minDrift;
        analysis.maxDrift = maxDrift;
        analysis.stdDev = stdDev;
        analysis.driftRange = maxDrift - minDrift;
        analysis.driftTrend = driftTrend;
        analysis.trendDirection = driftTrend > 0.1f ? "worsening" : driftTrend < -0.1f ? "improving" : "stable";
        analysis.underReportingCount = underReportingCount;
        analysis.overReportingCount = overReportingCount;
        analysis.stableCount = stableCount;
        analysis.dominantPattern = determineDominantPattern(underReportingCount, overReportingCount, stableCount);
        analysis.totalSuddenDrops = suddenDropTotal;
        analysis.avgSuddenDropsPerDay = (float) suddenDropTotal / Math.max(1, (endTime - startTime) / (24 * 60 * 60 * 1000f));
        analysis.volatilityScore = calculateVolatility(stdDev, analysis.driftRange);
        
        return analysis;
    }
    
    /**
     * Analyze voltage-SOC correlation for accuracy assessment
     */
    public VoltageCorrelationAnalysis analyzeVoltageCorrelation(List<BatterySnapshot> snapshots) {
        if (snapshots == null || snapshots.size() < 10) {
            return VoltageCorrelationAnalysis.insufficient();
        }
        
        // Collect voltage-SOC pairs
        List<float[]> pairs = new ArrayList<>();
        for (BatterySnapshot snap : snapshots) {
            if (snap.voltage > 0 && !snap.isCharging) { // Only use discharge data
                pairs.add(new float[]{snap.level, snap.voltage / 1000f}); // Convert mV to V
            }
        }
        
        if (pairs.size() < 10) {
            return VoltageCorrelationAnalysis.insufficient();
        }
        
        // Expected voltage curve for Li-ion (typical 3.0V - 4.2V)
        // 100% → ~4.2V, 50% → ~3.7V, 20% → ~3.5V, 5% → ~3.3V, 0% → ~3.0V
        
        float voltageAt100 = 0f, countAt100 = 0;
        float voltageAt80 = 0f, countAt80 = 0;
        float voltageAt50 = 0f, countAt50 = 0;
        float voltageAt20 = 0f, countAt20 = 0;
        float voltageAt5 = 0f, countAt5 = 0;
        
        for (float[] pair : pairs) {
            int level = (int) pair[0];
            float voltage = pair[1];
            
            if (level >= 98) { voltageAt100 += voltage; countAt100++; }
            else if (level >= 78 && level <= 82) { voltageAt80 += voltage; countAt80++; }
            else if (level >= 48 && level <= 52) { voltageAt50 += voltage; countAt50++; }
            else if (level >= 18 && level <= 22) { voltageAt20 += voltage; countAt20++; }
            else if (level >= 3 && level <= 7) { voltageAt5 += voltage; countAt5++; }
        }
        
        // Calculate averages
        if (countAt100 > 0) voltageAt100 /= countAt100;
        if (countAt80 > 0) voltageAt80 /= countAt80;
        if (countAt50 > 0) voltageAt50 /= countAt50;
        if (countAt20 > 0) voltageAt20 /= countAt20;
        if (countAt5 > 0) voltageAt5 /= countAt5;
        
        // Expected values (typical Li-ion)
        float expectedAt100 = 4.2f;
        float expectedAt80 = 3.95f;
        float expectedAt50 = 3.7f;
        float expectedAt20 = 3.5f;
        float expectedAt5 = 3.3f;
        
        // Calculate deviations
        float deviation100 = countAt100 > 0 ? Math.abs(voltageAt100 - expectedAt100) : 0f;
        float deviation80 = countAt80 > 0 ? Math.abs(voltageAt80 - expectedAt80) : 0f;
        float deviation50 = countAt50 > 0 ? Math.abs(voltageAt50 - expectedAt50) : 0f;
        float deviation20 = countAt20 > 0 ? Math.abs(voltageAt20 - expectedAt20) : 0f;
        float deviation5 = countAt5 > 0 ? Math.abs(voltageAt5 - expectedAt5) : 0f;
        
        // Calculate correlation score (0-100)
        float avgDeviation = (deviation100 + deviation80 + deviation50 + deviation20 + deviation5) / 5f;
        float correlationScore = Math.max(0, 100 - avgDeviation * 200); // 0.5V deviation = 0 score
        
        // Build result
        VoltageCorrelationAnalysis analysis = new VoltageCorrelationAnalysis();
        analysis.sampleCount = pairs.size();
        analysis.voltageAt100 = voltageAt100;
        analysis.voltageAt80 = voltageAt80;
        analysis.voltageAt50 = voltageAt50;
        analysis.voltageAt20 = voltageAt20;
        analysis.voltageAt5 = voltageAt5;
        analysis.expectedAt100 = expectedAt100;
        analysis.expectedAt80 = expectedAt80;
        analysis.expectedAt50 = expectedAt50;
        analysis.expectedAt20 = expectedAt20;
        analysis.expectedAt5 = expectedAt5;
        analysis.deviation100 = deviation100;
        analysis.deviation80 = deviation80;
        analysis.deviation50 = deviation50;
        analysis.deviation20 = deviation20;
        analysis.deviation5 = deviation5;
        analysis.avgDeviation = avgDeviation;
        analysis.correlationScore = correlationScore;
        analysis.correlationGrade = gradeCorrelation(correlationScore);
        analysis.sufficient = true;
        
        // Determine if curve is stretched or compressed
        float measuredRange = voltageAt100 - voltageAt5;
        float expectedRange = expectedAt100 - expectedAt5;
        if (measuredRange > 0 && expectedRange > 0) {
            float rangeRatio = measuredRange / expectedRange;
            analysis.curveStretched = rangeRatio > 1.1f;
            analysis.curveCompressed = rangeRatio < 0.9f;
            analysis.rangeRatio = rangeRatio;
        }
        
        return analysis;
    }
    
    // ==================================================================================
    //                              DRIFT CALCULATION METHODS
    // ==================================================================================
    
    /**
     * Calculate instantaneous drift between reported and estimated SOC
     * 
     * Formula: drift = estimatedSOC - reportedSOC
     * - Positive drift = under-reporting (battery has more charge than shown)
     * - Negative drift = over-reporting (battery has less charge than shown)
     */
    public float calculateInstantaneousDrift(int reportedSOC, float estimatedSOC) {
        return estimatedSOC - reportedSOC;
    }
    
    /**
     * Update Exponential Moving Average of drift
     * 
     * Formula: EMA = α × current + (1-α) × previous
     * Uses balanced smoothing factor of 0.2
     */
    private void updateEMADrift(float currentDrift) {
        if (driftHistory.isEmpty()) {
            emaDrift = currentDrift;
        } else {
            emaDrift = EMA_BALANCED * currentDrift + (1 - EMA_BALANCED) * emaDrift;
        }
    }
    
    /**
     * Calculate drift velocity (rate of change)
     * 
     * Formula: velocity = Δdrift / Δtime (%/hour)
     */
    private float calculateDriftVelocity(float currentDrift, long timestamp) {
        if (driftHistory.isEmpty() || lastSampleTimestamp == 0) {
            return 0f;
        }
        
        // Get drift from ~1 hour ago
        DriftSample hourAgoDrift = null;
        long hourAgo = timestamp - (60 * 60 * 1000);
        
        for (int i = driftHistory.size() - 1; i >= 0; i--) {
            DriftSample sample = driftHistory.get(i);
            if (sample.timestamp <= hourAgo) {
                hourAgoDrift = sample;
                break;
            }
        }
        
        if (hourAgoDrift == null && !driftHistory.isEmpty()) {
            hourAgoDrift = driftHistory.get(0);
        }
        
        if (hourAgoDrift == null) {
            return 0f;
        }
        
        float deltaDrift = currentDrift - hourAgoDrift.drift;
        float deltaHours = (timestamp - hourAgoDrift.timestamp) / (60f * 60f * 1000f);
        
        if (deltaHours < 0.1f) {
            return 0f; // Not enough time elapsed
        }
        
        driftVelocity = deltaDrift / deltaHours;
        return driftVelocity;
    }
    
    /**
     * Calculate drift trend from historical data using linear regression
     */
    private float calculateDriftTrend(List<CalibrationData> history) {
        if (history == null || history.size() < 3) {
            return 0f;
        }
        
        // Simple linear regression: slope = Σ(xi - x̄)(yi - ȳ) / Σ(xi - x̄)²
        long firstTime = history.get(history.size() - 1).timestamp;
        
        float sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        int n = history.size();
        
        for (CalibrationData data : history) {
            float x = (data.timestamp - firstTime) / (24f * 60f * 60f * 1000f); // Days since start
            float y = data.levelDrift;
            
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
        }
        
        float meanX = sumX / n;
        float meanY = sumY / n;
        
        float numerator = sumXY - n * meanX * meanY;
        float denominator = sumX2 - n * meanX * meanX;
        
        if (Math.abs(denominator) < 0.001f) {
            return 0f;
        }
        
        return numerator / denominator; // Drift change per day
    }
    
    // ==================================================================================
    //                            SUDDEN DROP DETECTION
    // ==================================================================================
    
    /**
     * Detect sudden drops in battery level
     * 
     * Criteria:
     * - Drop ≥ 5% in ≤ 15 minutes
     * - OR rate > 20%/hour
     */
    private SuddenDropResult detectSuddenDrop(BatterySnapshot snapshot) {
        SuddenDropResult result = new SuddenDropResult();
        result.detected = false;
        
        if (lastReportedLevel < 0 || lastSampleTimestamp == 0) {
            return result;
        }
        
        int dropPercent = lastReportedLevel - snapshot.level;
        long deltaMs = snapshot.timestamp - lastSampleTimestamp;
        float deltaMinutes = deltaMs / 60000f;
        
        // Only consider drops (positive delta)
        if (dropPercent <= 0) {
            return result;
        }
        
        // Calculate rate (%/hour)
        float ratePerHour = (dropPercent / deltaMinutes) * 60f;
        
        // Check sudden drop criteria
        boolean isSudden = (dropPercent >= SUDDEN_DROP_MIN_PERCENT && deltaMinutes <= SUDDEN_DROP_MAX_MINUTES)
                        || (ratePerHour >= SUDDEN_DROP_RATE_THRESHOLD);
        
        if (isSudden) {
            result.detected = true;
            result.dropPercent = dropPercent;
            result.dropDurationMinutes = deltaMinutes;
            result.dropRatePerHour = ratePerHour;
            result.previousLevel = lastReportedLevel;
            result.currentLevel = snapshot.level;
            result.timestamp = snapshot.timestamp;
            
            // Classify severity
            if (dropPercent >= 15 || ratePerHour >= 60) {
                result.severity = "critical";
            } else if (dropPercent >= 10 || ratePerHour >= 40) {
                result.severity = "high";
            } else if (dropPercent >= 7 || ratePerHour >= 30) {
                result.severity = "moderate";
            } else {
                result.severity = "low";
            }
            
            suddenDropCount++;
        }
        
        return result;
    }
    
    /**
     * Analyze sudden drop patterns over time
     */
    public SuddenDropPatternAnalysis analyzeSuddenDropPatterns(long startTime) {
        List<CalibrationData> history = database.miscDao().getCalibrationHistory(startTime);
        
        SuddenDropPatternAnalysis analysis = new SuddenDropPatternAnalysis();
        
        if (history == null || history.isEmpty()) {
            return analysis;
        }
        
        int totalDrops = 0;
        float totalDropSize = 0f;
        int maxDrop = 0;
        int[] dropsByHour = new int[24];
        int[] dropsByLevel = new int[10]; // 0-10%, 10-20%, etc.
        
        for (CalibrationData data : history) {
            if (data.hasSuddenDrops) {
                totalDrops += data.suddenDropCount;
                totalDropSize += data.avgDropSize * data.suddenDropCount;
                maxDrop = Math.max(maxDrop, data.worstDrop);
                
                // Track by hour
                int hour = (int) ((data.timestamp / (60 * 60 * 1000)) % 24);
                dropsByHour[hour] += data.suddenDropCount;
                
                // Track by level
                int levelBucket = Math.min(9, data.reportedLevel / 10);
                dropsByLevel[levelBucket] += data.suddenDropCount;
            }
        }
        
        analysis.totalSuddenDrops = totalDrops;
        analysis.avgDropSize = totalDrops > 0 ? totalDropSize / totalDrops : 0f;
        analysis.maxDrop = maxDrop;
        analysis.dropsByHour = dropsByHour;
        analysis.dropsByLevelBucket = dropsByLevel;
        
        // Find peak hour
        int peakHour = 0;
        int peakDrops = 0;
        for (int i = 0; i < 24; i++) {
            if (dropsByHour[i] > peakDrops) {
                peakDrops = dropsByHour[i];
                peakHour = i;
            }
        }
        analysis.peakDropHour = peakHour;
        
        // Find problematic level range
        int problemLevel = 0;
        int problemDrops = 0;
        for (int i = 0; i < 10; i++) {
            if (dropsByLevel[i] > problemDrops) {
                problemDrops = dropsByLevel[i];
                problemLevel = i * 10;
            }
        }
        analysis.problemLevelRange = problemLevel + "-" + (problemLevel + 10) + "%";
        
        // Determine pattern
        if (totalDrops == 0) {
            analysis.pattern = "none";
        } else if (problemLevel <= 20) {
            analysis.pattern = "low_battery_drops";
        } else if (problemLevel >= 80) {
            analysis.pattern = "high_battery_drops";
        } else {
            analysis.pattern = "random_drops";
        }
        
        return analysis;
    }
    
    // ==================================================================================
    //                         CALIBRATION QUALITY SCORING
    // ==================================================================================
    
    /**
     * Calculate calibration quality score (0-100)
     * 
     * Formula: quality = 100 - drift_penalty - drop_penalty - velocity_penalty
     * - drift_penalty = |avg_drift| × 2.5
     * - drop_penalty = sudden_drops × 8
     * - velocity_penalty = |drift_velocity| × 5
     */
    private float calculateCalibrationQuality() {
        float driftPenalty = Math.abs(emaDrift) * 2.5f;
        float dropPenalty = suddenDropCount * 8f;
        float velocityPenalty = Math.abs(driftVelocity) * 5f;
        
        // Bonus for recent full cycles
        float cycleBonus = 0f;
        long daysSinceFullCycle = calculateDaysSinceFullCycle();
        if (daysSinceFullCycle < 7) {
            cycleBonus = 10f;
        } else if (daysSinceFullCycle < 14) {
            cycleBonus = 5f;
        }
        
        float quality = 100f - driftPenalty - dropPenalty - velocityPenalty + cycleBonus;
        return clamp(quality, 0f, 100f);
    }
    
    /**
     * Calculate confidence in our calibration analysis
     * 
     * Formula: confidence = base + sample_bonus + stability_bonus - volatility_penalty
     */
    private float calculateConfidence() {
        float base = 40f;
        
        // Sample bonus (more samples = more confidence)
        float sampleBonus = Math.min(30f, driftHistory.size() * 0.5f);
        
        // Stability bonus (low drift velocity = stable)
        float stabilityBonus = Math.max(0f, 20f - Math.abs(driftVelocity) * 10f);
        
        // Volatility penalty (high variance = less reliable)
        float volatilityPenalty = 0f;
        if (driftHistory.size() >= 5) {
            float variance = calculateDriftVariance();
            volatilityPenalty = Math.min(20f, variance * 2f);
        }
        
        // Full cycle bonus
        float cycleBonus = hasValidCalibration ? 10f : 0f;
        
        float confidence = base + sampleBonus + stabilityBonus + cycleBonus - volatilityPenalty;
        return clamp(confidence, 0f, 100f);
    }
    
    private float calculateDriftVariance() {
        if (driftHistory.size() < 2) return 0f;
        
        float sum = 0f;
        for (DriftSample sample : driftHistory) {
            sum += sample.drift;
        }
        float mean = sum / driftHistory.size();
        
        float sumSquares = 0f;
        for (DriftSample sample : driftHistory) {
            float diff = sample.drift - mean;
            sumSquares += diff * diff;
        }
        
        return sumSquares / driftHistory.size();
    }
    
    // ==================================================================================
    //                        CALIBRATION SUCCESS EVALUATION
    // ==================================================================================
    
    /**
     * Evaluate if calibration meets success criteria
     * 
     * Criteria:
     * - Average drift < 3%
     * - No sudden drops in last 7 days
     * - Drift velocity < 0.5%/hr
     * - Full charge cycle in last 30 days
     * - Deep discharge in last 30 days
     */
    private CalibrationSuccessResult evaluateCalibrationSuccess() {
        CalibrationSuccessResult result = new CalibrationSuccessResult();
        
        // Criterion 1: Average drift
        result.avgDriftPassing = Math.abs(emaDrift) < DRIFT_MINOR_THRESHOLD;
        result.avgDrift = emaDrift;
        result.avgDriftThreshold = DRIFT_MINOR_THRESHOLD;
        
        // Criterion 2: No recent sudden drops
        long sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L);
        int recentDrops = countRecentSuddenDrops(sevenDaysAgo);
        result.noRecentDropsPassing = recentDrops == 0;
        result.recentDropCount = recentDrops;
        
        // Criterion 3: Drift velocity
        result.velocityPassing = Math.abs(driftVelocity) < 0.5f;
        result.driftVelocity = driftVelocity;
        result.velocityThreshold = 0.5f;
        
        // Criterion 4: Recent full charge
        long thirtyDaysAgo = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000L);
        result.hasRecentFullCharge = lastFullChargeTimestamp > thirtyDaysAgo;
        result.daysSinceFullCharge = (int) ((System.currentTimeMillis() - lastFullChargeTimestamp) / (24 * 60 * 60 * 1000));
        
        // Criterion 5: Recent deep discharge
        result.hasRecentDeepDischarge = lastDeepDischargeTimestamp > thirtyDaysAgo;
        result.daysSinceDeepDischarge = (int) ((System.currentTimeMillis() - lastDeepDischargeTimestamp) / (24 * 60 * 60 * 1000));
        
        // Overall calibration status
        result.criteriaMet = 0;
        if (result.avgDriftPassing) result.criteriaMet++;
        if (result.noRecentDropsPassing) result.criteriaMet++;
        if (result.velocityPassing) result.criteriaMet++;
        if (result.hasRecentFullCharge) result.criteriaMet++;
        if (result.hasRecentDeepDischarge) result.criteriaMet++;
        
        result.totalCriteria = 5;
        result.isCalibrated = result.criteriaMet >= 4; // Need at least 4/5 criteria
        result.calibrationScore = (result.criteriaMet / 5f) * 100f;
        
        return result;
    }
    
    private int countRecentSuddenDrops(long since) {
        List<CalibrationData> recent = database.miscDao().getCalibrationHistory(since);
        if (recent == null) return 0;
        
        int count = 0;
        for (CalibrationData data : recent) {
            if (data.hasSuddenDrops) {
                count += data.suddenDropCount;
            }
        }
        return count;
    }
    
    // ==================================================================================
    //                            CLASSIFICATION METHODS
    // ==================================================================================
    
    private DriftDirection classifyDriftDirection(float drift) {
        if (drift > DRIFT_MINOR_THRESHOLD) {
            return DriftDirection.UNDER_REPORTING;
        } else if (drift < -DRIFT_MINOR_THRESHOLD) {
            return DriftDirection.OVER_REPORTING;
        } else {
            return DriftDirection.STABLE;
        }
    }
    
    private DriftSeverity classifyDriftSeverity(float absDrift) {
        if (absDrift >= DRIFT_CRITICAL_THRESHOLD) {
            return DriftSeverity.CRITICAL;
        } else if (absDrift >= DRIFT_SEVERE_THRESHOLD) {
            return DriftSeverity.SEVERE;
        } else if (absDrift >= DRIFT_SIGNIFICANT_THRESHOLD) {
            return DriftSeverity.SIGNIFICANT;
        } else if (absDrift >= DRIFT_MODERATE_THRESHOLD) {
            return DriftSeverity.MODERATE;
        } else if (absDrift >= DRIFT_MINOR_THRESHOLD) {
            return DriftSeverity.MINOR;
        } else {
            return DriftSeverity.NONE;
        }
    }
    
    private String gradeQuality(float quality) {
        if (quality >= QUALITY_EXCELLENT) return "Excellent";
        if (quality >= QUALITY_GOOD) return "Good";
        if (quality >= QUALITY_FAIR) return "Fair";
        if (quality >= QUALITY_POOR) return "Poor";
        return "Critical";
    }
    
    private String gradeConfidence(float confidence) {
        if (confidence >= 90) return "Very High";
        if (confidence >= 75) return "High";
        if (confidence >= 50) return "Moderate";
        if (confidence >= 25) return "Low";
        return "Very Low";
    }
    
    private String gradeCorrelation(float score) {
        if (score >= 90) return "Excellent";
        if (score >= 75) return "Good";
        if (score >= 50) return "Fair";
        if (score >= 25) return "Poor";
        return "Unreliable";
    }
    
    private CalibrationUrgency determineCalibrationUrgency(CalibrationAnalysis analysis) {
        // Critical urgency
        if (analysis.driftSeverity == DriftSeverity.CRITICAL ||
            analysis.totalSuddenDrops >= 10 ||
            analysis.calibrationQuality < 20) {
            return CalibrationUrgency.CRITICAL;
        }
        
        // High urgency
        if (analysis.driftSeverity == DriftSeverity.SEVERE ||
            analysis.totalSuddenDrops >= 5 ||
            analysis.calibrationQuality < 40) {
            return CalibrationUrgency.HIGH;
        }
        
        // Moderate urgency
        if (analysis.driftSeverity == DriftSeverity.SIGNIFICANT ||
            analysis.totalSuddenDrops >= 3 ||
            analysis.calibrationQuality < 60) {
            return CalibrationUrgency.MODERATE;
        }
        
        // Low urgency
        if (analysis.needsCalibration) {
            return CalibrationUrgency.LOW;
        }
        
        return CalibrationUrgency.NONE;
    }
    
    private String determineDominantPattern(int under, int over, int stable) {
        if (stable > under && stable > over) return "stable";
        if (under > over) return "under_reporting";
        if (over > under) return "over_reporting";
        return "mixed";
    }
    
    private float calculateVolatility(float stdDev, float range) {
        // Volatility score based on standard deviation and range
        float stdDevScore = Math.min(50f, stdDev * 5f);
        float rangeScore = Math.min(50f, range * 2f);
        return stdDevScore + rangeScore;
    }
    
    // ==================================================================================
    //                              HEALTH IMPACT
    // ==================================================================================
    
    /**
     * Calculate how calibration issues impact battery health perception
     */
    private float calculateHealthImpact() {
        // Drift impact (larger drift = more impact on health readings)
        float driftImpact = Math.abs(emaDrift) * 0.5f;
        
        // Drop impact (sudden drops indicate unreliable readings)
        float dropImpact = suddenDropCount * 2f;
        
        // Velocity impact (fast-changing drift = unreliable)
        float velocityImpact = Math.abs(driftVelocity) * 3f;
        
        // Age impact (old calibration = less reliable health data)
        long daysSinceCycle = calculateDaysSinceFullCycle();
        float ageImpact = Math.min(20f, daysSinceCycle * 0.5f);
        
        return clamp(driftImpact + dropImpact + velocityImpact + ageImpact, 0f, 100f);
    }
    
    private long calculateDaysSinceFullCycle() {
        long lastCycle = Math.max(lastFullChargeTimestamp, lastDeepDischargeTimestamp);
        if (lastCycle == 0) {
            return 999; // Unknown
        }
        return (System.currentTimeMillis() - lastCycle) / (24 * 60 * 60 * 1000);
    }
    
    // ==================================================================================
    //                              STATE MANAGEMENT
    // ==================================================================================
    
    private void loadHistoricalState() {
        // Load latest calibration data to restore state
        CalibrationData latest = database.miscDao().getLatestCalibration();
        if (latest != null) {
            emaDrift = latest.avgDrift;
            suddenDropCount = latest.suddenDropCount;
            lastFullChargeTimestamp = latest.lastFullCharge;
            lastDeepDischargeTimestamp = latest.lastDeepDischarge;
            fullCycleCount = latest.fullChargeCount;
            hasValidCalibration = latest.isCalibrated;
        }
    }
    
    private void updateTrackingState(BatterySnapshot snapshot, float drift, long timestamp) {
        // Add to drift history
        DriftSample sample = new DriftSample();
        sample.timestamp = timestamp;
        sample.drift = drift;
        sample.reportedLevel = snapshot.level;
        driftHistory.add(sample);
        
        // Trim history to last 24 hours
        long cutoff = timestamp - (24 * 60 * 60 * 1000);
        while (!driftHistory.isEmpty() && driftHistory.get(0).timestamp < cutoff) {
            driftHistory.remove(0);
        }
        
        // Update full charge tracking
        if (snapshot.level >= 99 && snapshot.isCharging) {
            lastFullChargeTimestamp = timestamp;
            fullCycleCount++;
        }
        
        // Update deep discharge tracking
        if (snapshot.level <= 5 && !snapshot.isCharging) {
            lastDeepDischargeTimestamp = timestamp;
        }
        
        // Update tracking variables
        lastSampleTimestamp = timestamp;
        lastReportedLevel = snapshot.level;
    }
    
    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
    
    // ==================================================================================
    //                              DATA CLASSES
    // ==================================================================================
    
    public enum DriftDirection {
        UNDER_REPORTING,  // Battery has more charge than shown
        OVER_REPORTING,   // Battery has less charge than shown
        STABLE           // Accurate reporting
    }
    
    public enum DriftSeverity {
        NONE,        // < 3%
        MINOR,       // 3-5%
        MODERATE,    // 5-8%
        SIGNIFICANT, // 8-12%
        SEVERE,      // 12-20%
        CRITICAL     // > 20%
    }
    
    public enum CalibrationUrgency {
        NONE,        // No calibration needed
        LOW,         // Optional calibration
        MODERATE,    // Recommended within month
        HIGH,        // Recommended within week
        CRITICAL     // Immediate calibration needed
    }
    
    private static class DriftSample {
        long timestamp;
        float drift;
        int reportedLevel;
    }
    
    public static class CalibrationAnalysis {
        public long timestamp;
        public int reportedSOC;
        public float estimatedSOC;
        public float instantaneousDrift;
        public float emaDrift;
        public DriftDirection driftDirection;
        public float driftVelocity;
        public DriftSeverity driftSeverity;
        public boolean suddenDropDetected;
        public SuddenDropResult suddenDropDetails;
        public int totalSuddenDrops;
        public float calibrationQuality;
        public String qualityGrade;
        public float confidence;
        public String confidenceGrade;
        public boolean isCalibrated;
        public CalibrationSuccessResult calibrationSuccessCriteria;
        public boolean needsCalibration;
        public CalibrationUrgency calibrationUrgency;
        public float healthImpactScore;
        public long daysSinceFullCycle;
        public boolean valid;
        public String invalidReason;
        
        public static CalibrationAnalysis invalid(String reason) {
            CalibrationAnalysis a = new CalibrationAnalysis();
            a.valid = false;
            a.invalidReason = reason;
            return a;
        }
    }
    
    public static class SuddenDropResult {
        public boolean detected;
        public int dropPercent;
        public float dropDurationMinutes;
        public float dropRatePerHour;
        public int previousLevel;
        public int currentLevel;
        public long timestamp;
        public String severity;
    }
    
    public static class CalibrationSuccessResult {
        public boolean isCalibrated;
        public float calibrationScore;
        public int criteriaMet;
        public int totalCriteria;
        
        public boolean avgDriftPassing;
        public float avgDrift;
        public float avgDriftThreshold;
        
        public boolean noRecentDropsPassing;
        public int recentDropCount;
        
        public boolean velocityPassing;
        public float driftVelocity;
        public float velocityThreshold;
        
        public boolean hasRecentFullCharge;
        public int daysSinceFullCharge;
        
        public boolean hasRecentDeepDischarge;
        public int daysSinceDeepDischarge;
    }
    
    public static class DriftHistoryAnalysis {
        public long startTime;
        public long endTime;
        public int sampleCount;
        public float avgDrift;
        public float minDrift;
        public float maxDrift;
        public float stdDev;
        public float driftRange;
        public float driftTrend;
        public String trendDirection;
        public int underReportingCount;
        public int overReportingCount;
        public int stableCount;
        public String dominantPattern;
        public int totalSuddenDrops;
        public float avgSuddenDropsPerDay;
        public float volatilityScore;
        
        public static DriftHistoryAnalysis empty() {
            DriftHistoryAnalysis a = new DriftHistoryAnalysis();
            a.sampleCount = 0;
            return a;
        }
    }
    
    public static class VoltageCorrelationAnalysis {
        public int sampleCount;
        public boolean sufficient;
        
        public float voltageAt100;
        public float voltageAt80;
        public float voltageAt50;
        public float voltageAt20;
        public float voltageAt5;
        
        public float expectedAt100;
        public float expectedAt80;
        public float expectedAt50;
        public float expectedAt20;
        public float expectedAt5;
        
        public float deviation100;
        public float deviation80;
        public float deviation50;
        public float deviation20;
        public float deviation5;
        
        public float avgDeviation;
        public float correlationScore;
        public String correlationGrade;
        
        public boolean curveStretched;
        public boolean curveCompressed;
        public float rangeRatio;
        
        public static VoltageCorrelationAnalysis insufficient() {
            VoltageCorrelationAnalysis a = new VoltageCorrelationAnalysis();
            a.sufficient = false;
            return a;
        }
    }
    
    public static class SuddenDropPatternAnalysis {
        public int totalSuddenDrops;
        public float avgDropSize;
        public int maxDrop;
        public int[] dropsByHour;
        public int[] dropsByLevelBucket;
        public int peakDropHour;
        public String problemLevelRange;
        public String pattern;
    }
}
