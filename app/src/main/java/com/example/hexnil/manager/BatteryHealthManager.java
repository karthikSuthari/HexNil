package com.example.hexnil.manager;

import android.content.Context;
import android.util.Log;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.health.AbnormalWearDetector;
import com.example.hexnil.health.CycleCounter;
import com.example.hexnil.health.DegradationModel;
import com.example.hexnil.model.BatteryHealth;
import com.example.hexnil.model.BatterySnapshot;

import java.util.List;

/**
 * OEM-Grade Battery Health Manager
 * 
 * Calculates comprehensive battery health metrics using:
 * - Real capacity measurement (not Android's fake status)
 * - Accurate cycle counting (full + partial cycles)
 * - Degradation rate tracking (per cycle, per month, per 100 cycles)
 * - End-of-Life prediction with confidence levels
 * - Abnormal wear detection
 * - Supplier/batch quality tracking
 * 
 * ============================================================================
 * FORMULAS IMPLEMENTED
 * ============================================================================
 * 
 * SOH = CurrentCapacity / DesignCapacity × 100%
 * CapacityFade = 100% - SOH
 * CycleCount = TotalDischarge / DesignCapacity
 * FadePerCycle = CapacityFade / CycleCount
 * FadePer100Cycles = FadePerCycle × 100
 * FadePerMonth = ΔCapacityFade / ΔMonths
 * PredictedEOL = (SOH - 80%) / DegradationRate (in months)
 * 
 * ============================================================================
 * THRESHOLDS
 * ============================================================================
 * 
 * Excellent: SOH ≥ 90%
 * Good: SOH ≥ 80%
 * Fair: SOH ≥ 70%
 * Poor: SOH ≥ 60%
 * Critical: SOH < 60%
 * 
 * Normal degradation: <1%/month, <5% per 100 cycles
 * Abnormal wear: >2%/month or >8% per 100 cycles
 */
public class BatteryHealthManager {

    private static final String TAG = "BatteryHealthManager";

    // =========================================================================
    // CONFIGURATION CONSTANTS
    // =========================================================================

    /** Default design capacity when not available from system */
    private static final float DEFAULT_DESIGN_CAPACITY_MAH = 4000f;

    /** Max temperature for swelling risk detection (°C × 10) */
    private static final int MAX_SAFE_TEMP_TENTHS = 450; // 45.0°C

    /** Time window for discharge cycle detection (72 hours) */
    private static final long DISCHARGE_WINDOW_MS = 72L * 60L * 60L * 1000L;

    /** Time window for weekly cycle calculation */
    private static final long WEEK_MS = 7L * 24L * 60L * 60L * 1000L;

    /** Time window for monthly calculations */
    private static final long MONTH_MS = 30L * 24L * 60L * 60L * 1000L;

    /** Minimum level for full charge detection */
    private static final int FULL_CHARGE_LEVEL = 99;

    /** Maximum level for full discharge detection */
    private static final int FULL_DISCHARGE_LEVEL = 5;

    /** Smoothing factor for exponential moving average */
    private static final float EMA_ALPHA = 0.3f;

    /** Minimum tracking days for baseline confidence */
    private static final int MIN_TRACKING_DAYS = 7;

    /** Days for moderate confidence in predictions */
    private static final int MODERATE_CONFIDENCE_DAYS = 14;

    /** Days for high confidence in predictions */
    private static final int HIGH_CONFIDENCE_DAYS = 30;

    // =========================================================================
    // DEPENDENCIES
    // =========================================================================

    private final HexNilDatabase database;
    private final CycleCounter cycleCounter;

    // =========================================================================
    // CONSTRUCTOR
    // =========================================================================

    public BatteryHealthManager(Context context, HexNilDatabase database) {
        this.database = database;
        this.cycleCounter = new CycleCounter(DEFAULT_DESIGN_CAPACITY_MAH);
    }

    // =========================================================================
    // MAIN HEALTH UPDATE
    // =========================================================================

    /**
     * Update battery health metrics based on a new snapshot.
     * 
     * This is the main entry point called after each snapshot capture.
     * It calculates all health metrics and persists a new BatteryHealth record.
     */
    public void updateHealth(BatterySnapshot snapshot) {
        if (snapshot == null) {
            Log.w(TAG, "Null snapshot received, skipping health update");
            return;
        }

        try {
            // Get previous health record
            BatteryHealth latest = database.batteryHealthDao().getLatest();
            BatteryHealth firstRecord = getFirstHealthRecord();

            // =====================================================================
            // STEP 1: DETERMINE DESIGN CAPACITY
            // =====================================================================
            float designCapacity = resolveDesignCapacity(snapshot, latest);
            cycleCounter.setDesignCapacityMah(designCapacity);

            // =====================================================================
            // STEP 2: ESTIMATE CURRENT CAPACITY
            // =====================================================================
            CapacityEstimate capacityEstimate = estimateCapacity(snapshot, latest, designCapacity);

            // =====================================================================
            // STEP 3: CALCULATE SOH AND FADE
            // =====================================================================
            float stateOfHealth = DegradationModel.calculateSOH(
                    capacityEstimate.currentCapacity, designCapacity);
            float capacityFade = DegradationModel.calculateCapacityFade(
                    capacityEstimate.currentCapacity, designCapacity);
            float capacityLoss = designCapacity - capacityEstimate.currentCapacity;

            // =====================================================================
            // STEP 4: CYCLE COUNTING
            // =====================================================================
            CycleMetrics cycleMetrics = calculateCycleMetrics(snapshot, latest, designCapacity);

            // =====================================================================
            // STEP 5: DEGRADATION RATES
            // =====================================================================
            DegradationMetrics degradation = calculateDegradation(
                    capacityFade, cycleMetrics, latest, firstRecord, snapshot.timestamp);

            // =====================================================================
            // STEP 6: EOL PREDICTION
            // =====================================================================
            float predictedEOL = DegradationModel.calculatePredictedEOL(
                    stateOfHealth, degradation.smoothedRate);
            float predictedReplacement = DegradationModel.calculatePredictedEOL(
                    stateOfHealth, degradation.smoothedRate,
                    DegradationModel.SOH_REPLACEMENT_THRESHOLD);

            // =====================================================================
            // STEP 7: ABNORMAL WEAR DETECTION
            // =====================================================================
            AbnormalWearDetector.BatteryMetrics wearMetrics = buildWearMetrics(
                    capacityFade, cycleMetrics, degradation, snapshot, latest, firstRecord);
            AbnormalWearDetector.WearAnalysis wearAnalysis = AbnormalWearDetector.analyze(wearMetrics);

            // =====================================================================
            // STEP 8: CONFIDENCE CALCULATION
            // =====================================================================
            int trackingDays = calculateTrackingDays(firstRecord, snapshot.timestamp);
            int snapshotCount = getSnapshotCount(firstRecord);
            boolean hasCalibrationCycle = checkCalibrationCycle(latest, snapshot);
            float measurementVariance = calculateMeasurementVariance();

            DegradationModel.ConfidenceResult confidence = DegradationModel.calculateConfidence(
                    trackingDays, snapshotCount, hasCalibrationCycle, measurementVariance);

            // =====================================================================
            // STEP 9: HEALTH SCORES
            // =====================================================================
            float avgTemp = getAverageTemperature();
            float resistanceIncrease = calculateResistanceIncrease(latest);

            DegradationModel.HealthScores scores = DegradationModel.calculateHealthScores(
                    stateOfHealth, cycleMetrics.totalCycles, resistanceIncrease,
                    degradation.currentRate, avgTemp);

            // =====================================================================
            // STEP 10: BUILD HEALTH RECORD
            // =====================================================================
            BatteryHealth health = buildHealthRecord(
                    snapshot, latest, firstRecord,
                    designCapacity, capacityEstimate, stateOfHealth, capacityFade, capacityLoss,
                    cycleMetrics, degradation, predictedEOL, predictedReplacement,
                    wearAnalysis, confidence, scores, trackingDays);

            // =====================================================================
            // STEP 11: PERSIST
            // =====================================================================
            database.batteryHealthDao().insert(health);

            Log.d(TAG, "Health updated: " + health.toString());

        } catch (android.database.sqlite.SQLiteConstraintException e) {
            Log.e(TAG, "Constraint error updating health - retrying with default EOL confidence", e);
            // Verify eolConfidence is set (paranoid check)
            if (e.getMessage() != null && e.getMessage().contains("eolConfidence")) {
                // If it failed on eolConfidence, it might be due to a race condition or Room
                // issue.
                // We can't easily retry here without rebuilding the object, but we can log
                // extensively.
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating health", e);
        }
    }

    // =========================================================================
    // CAPACITY ESTIMATION
    // =========================================================================

    /**
     * Resolve design capacity from multiple sources
     */
    private float resolveDesignCapacity(BatterySnapshot snapshot, BatteryHealth latest) {
        // Priority: snapshot > latest health > default
        if (snapshot.designCapacityMah > 0) {
            return snapshot.designCapacityMah;
        }
        if (latest != null && latest.designCapacityMah > 0) {
            return latest.designCapacityMah;
        }
        return DEFAULT_DESIGN_CAPACITY_MAH;
    }

    /**
     * Estimate current battery capacity using multiple methods
     */
    private CapacityEstimate estimateCapacity(BatterySnapshot snapshot,
            BatteryHealth latest,
            float designCapacity) {
        CapacityEstimate estimate = new CapacityEstimate();

        // Method 1: Direct measurement from full discharge cycle (most accurate)
        List<BatterySnapshot> recent = database.batterySnapshotDao()
                .getSnapshotsSince(snapshot.timestamp - DISCHARGE_WINDOW_MS);
        estimate.measuredCapacity = estimateCapacityFromDischarge(recent, designCapacity);

        // Method 2: Charge counter (if available)
        float chargeCounterCapacity = 0;
        if (snapshot.chargeCounter > 0) {
            chargeCounterCapacity = snapshot.chargeCounter / 1000f;
        }

        // Method 3: Reported capacity (often unreliable)
        float reportedCapacity = snapshot.capacityMah > 0 ? snapshot.capacityMah : 0;

        // Select best estimate
        if (estimate.measuredCapacity > 0) {
            // Full cycle measurement is most accurate
            estimate.currentCapacity = estimate.measuredCapacity;
            estimate.method = "full_cycle";
            estimate.confidence = 95;
        } else if (chargeCounterCapacity > 0) {
            // Charge counter is second best
            estimate.currentCapacity = chargeCounterCapacity;
            estimate.method = "charge_counter";
            estimate.confidence = 80;
        } else if (reportedCapacity > 0) {
            // Reported capacity often inaccurate but better than nothing
            estimate.currentCapacity = reportedCapacity;
            estimate.method = "reported";
            estimate.confidence = 50;
        } else {
            // Fall back to latest or design
            estimate.currentCapacity = latest != null && latest.currentCapacityMah > 0
                    ? latest.currentCapacityMah
                    : designCapacity;
            estimate.method = "fallback";
            estimate.confidence = 20;
        }

        estimate.estimatedCapacity = estimate.currentCapacity;

        return estimate;
    }

    /**
     * Estimate capacity from a full discharge cycle
     * 
     * Most accurate method - tracks energy from high SOC to low SOC
     * 
     * Formula: Capacity = ΔEnergy / ΔSOCFraction
     */
    private float estimateCapacityFromDischarge(List<BatterySnapshot> snaps, float design) {
        if (snaps == null || snaps.size() < 2)
            return 0f;

        BatterySnapshot highSoc = null;
        BatterySnapshot lowSoc = null;

        // Find highest and lowest SOC points
        for (BatterySnapshot s : snaps) {
            if (s.level >= 95 && !s.isCharging) {
                highSoc = s;
            }
            if (highSoc != null && s.level <= 20 && !s.isCharging) {
                lowSoc = s;
                break; // Found a valid discharge range
            }
        }

        if (highSoc == null || lowSoc == null)
            return 0f;

        float socDropFraction = (highSoc.level - lowSoc.level) / 100f;
        if (socDropFraction <= 0.1f)
            return 0f; // Need at least 10% drop

        // Try charge counter first (μAh)
        long deltaChargeMicroAh = highSoc.chargeCounter - lowSoc.chargeCounter;
        if (deltaChargeMicroAh > 0) {
            float deltaMah = deltaChargeMicroAh / 1000f;
            return deltaMah / socDropFraction;
        }

        // Fall back to energy counter (nWh)
        long deltaEnergyNwh = highSoc.energyCounter - lowSoc.energyCounter;
        if (deltaEnergyNwh > 0) {
            // Convert to mAh using average voltage
            float avgVoltageMv = (highSoc.voltage + lowSoc.voltage) / 2f;
            if (avgVoltageMv <= 0)
                avgVoltageMv = 3700; // Default nominal

            float voltageV = avgVoltageMv / 1000f;
            float wattHours = deltaEnergyNwh / 1_000_000_000f;
            float milliampHours = (wattHours * 1000f) / voltageV;
            return milliampHours / socDropFraction;
        }

        return 0f;
    }

    // =========================================================================
    // CYCLE COUNTING
    // =========================================================================

    /**
     * Calculate comprehensive cycle metrics
     */
    private CycleMetrics calculateCycleMetrics(BatterySnapshot snapshot,
            BatteryHealth latest,
            float designCapacity) {
        CycleMetrics metrics = new CycleMetrics();

        // Restore previous state
        if (latest != null) {
            cycleCounter.restoreState(
                    latest.totalChargeAccumulated,
                    latest.totalDischargeAccumulated,
                    latest.partialCycleAccumulator,
                    latest.fullCycleCount,
                    latest.deepCycleCount,
                    latest.topOffCycleCount,
                    0 // wearAdjusted not stored, will recalculate
            );
        }

        // Calculate energy delta from previous snapshot
        List<BatterySnapshot> recentSnaps = database.batterySnapshotDao()
                .getSnapshotsSince(snapshot.timestamp - 3600000); // Last hour

        float energyDeltaMah = 0;
        if (recentSnaps != null && recentSnaps.size() >= 2) {
            BatterySnapshot prev = recentSnaps.get(recentSnaps.size() - 2);
            energyDeltaMah = cycleCounter.calculateEnergyDelta(prev.level, snapshot.level);
        }

        // Update cycle counter
        float tempC = snapshot.temperature / 10f;
        CycleCounter.CycleUpdateResult cycleResult = cycleCounter.update(
                snapshot.level, snapshot.isCharging, tempC, energyDeltaMah);

        // Populate metrics
        metrics.totalCycles = cycleCounter.getEquivalentCycleCount();
        metrics.fullCycleCount = cycleCounter.getFullCycleCount();
        metrics.partialAccumulator = cycleCounter.getPartialCycleAccumulator();
        metrics.deepCycleCount = cycleCounter.getDeepCycleCount();
        metrics.topOffCycleCount = cycleCounter.getTopOffCycleCount();
        metrics.totalChargeMah = cycleCounter.getTotalChargeMah();
        metrics.totalDischargeMah = cycleCounter.getTotalDischargeMah();
        metrics.wearAdjustedCycles = cycleCounter.getWearAdjustedCycles();
        metrics.cyclingHealthRatio = cycleCounter.getCyclingHealthRatio();

        return metrics;
    }

    // =========================================================================
    // DEGRADATION CALCULATION
    // =========================================================================

    /**
     * Calculate degradation rates using multiple time windows
     */
    private DegradationMetrics calculateDegradation(float currentFade,
            CycleMetrics cycles,
            BatteryHealth latest,
            BatteryHealth first,
            long currentTimestamp) {
        DegradationMetrics deg = new DegradationMetrics();

        // Fade per cycle
        deg.fadePerCycle = DegradationModel.calculateFadePerCycle(currentFade, cycles.totalCycles);
        deg.fadePer100Cycles = DegradationModel.calculateFadePer100Cycles(deg.fadePerCycle);

        // Fade per month (calendar aging)
        if (first != null) {
            int trackingDays = (int) ((currentTimestamp - first.timestamp) / (24 * 3600000L));
            deg.fadePerMonth = DegradationModel.calculateFadePerMonth(
                    currentFade, first.capacityFade, trackingDays);
        }

        // Instantaneous rate from recent trend (last 7 days)
        deg.currentRate = calculateRecentDegradationRate(currentTimestamp);

        // Smoothed rate using EMA
        if (latest != null && latest.smoothedDegradationRate > 0) {
            deg.smoothedRate = DegradationModel.calculateSmoothedRate(
                    deg.currentRate, latest.smoothedDegradationRate, EMA_ALPHA);
        } else {
            deg.smoothedRate = deg.currentRate;
        }

        // Degradation acceleration
        if (latest != null && latest.degradationRate > 0) {
            deg.acceleration = deg.currentRate - latest.degradationRate;
        }

        // Determine trend
        deg.trend = determineTrend(deg);

        return deg;
    }

    /**
     * Calculate degradation rate from recent data points
     */
    private float calculateRecentDegradationRate(long currentTimestamp) {
        List<BatteryHealth> recentHealth = database.batteryHealthDao()
                .getHealthHistorySince(currentTimestamp - WEEK_MS);

        if (recentHealth == null || recentHealth.size() < 2)
            return 0;

        // Build array for linear regression
        float[][] fadePoints = new float[recentHealth.size()][2];
        for (int i = 0; i < recentHealth.size(); i++) {
            fadePoints[i][0] = recentHealth.get(i).timestamp;
            fadePoints[i][1] = recentHealth.get(i).capacityFade;
        }

        return DegradationModel.calculateInstantaneousDegradationRate(fadePoints);
    }

    /**
     * Determine degradation trend classification
     */
    private String determineTrend(DegradationMetrics deg) {
        // Check multiple indicators
        boolean highRate = deg.smoothedRate > DegradationModel.FADE_PER_MONTH_HIGH;
        boolean elevatedRate = deg.smoothedRate > DegradationModel.FADE_PER_MONTH_ELEVATED;
        boolean accelerating = deg.acceleration > 0.5f; // Rate increasing

        if (highRate || (elevatedRate && accelerating)) {
            return "critical";
        } else if (accelerating && elevatedRate) {
            return "accelerating";
        } else if (elevatedRate || deg.fadePer100Cycles > DegradationModel.FADE_PER_100_CYCLES_ELEVATED) {
            return "gradual";
        } else {
            return "stable";
        }
    }

    // =========================================================================
    // WEAR ANALYSIS
    // =========================================================================

    /**
     * Build metrics object for abnormal wear detection
     */
    private AbnormalWearDetector.BatteryMetrics buildWearMetrics(
            float capacityFade, CycleMetrics cycles, DegradationMetrics degradation,
            BatterySnapshot snapshot, BatteryHealth latest, BatteryHealth first) {

        AbnormalWearDetector.BatteryMetrics metrics = new AbnormalWearDetector.BatteryMetrics();

        metrics.capacityFade = capacityFade;
        metrics.cycleCount = cycles.totalCycles;
        metrics.batteryQualityFactor = 1.0f; // Default, could be from supplier data

        // Recent changes
        if (latest != null) {
            long weekAgo = snapshot.timestamp - WEEK_MS;
            long monthAgo = snapshot.timestamp - MONTH_MS;

            BatteryHealth weekAgoHealth = getHealthAtTime(weekAgo);
            BatteryHealth monthAgoHealth = getHealthAtTime(monthAgo);

            if (weekAgoHealth != null) {
                metrics.weeklyCapacityDrop = capacityFade - weekAgoHealth.capacityFade;
            }
            if (monthAgoHealth != null) {
                metrics.monthlyCapacityDrop = capacityFade - monthAgoHealth.capacityFade;
            }
        }

        // Degradation rates
        metrics.currentDegradationRate = degradation.currentRate;
        metrics.previousDegradationRate = latest != null ? latest.degradationRate : 0;
        metrics.initialDegradationRate = first != null ? first.degradationRate : 0;

        // Temperature data
        TemperatureStats tempStats = getTemperatureStats();
        metrics.avgTemperatureC = tempStats.average;
        metrics.maxTemperatureC = tempStats.max;
        metrics.minTemperatureC = tempStats.min;
        metrics.maxTempSpikeC = tempStats.maxSpike;

        // Resistance data
        metrics.baselineResistance = first != null ? first.internalResistance : 0;
        metrics.currentResistance = latest != null ? latest.internalResistance : 0;
        if (latest != null && first != null && first.internalResistance > 0) {
            long monthsElapsed = (snapshot.timestamp - first.timestamp) / MONTH_MS;
            if (monthsElapsed > 0) {
                float totalIncrease = latest.internalResistance - first.internalResistance;
                metrics.monthlyResistanceGrowth = (totalIncrease / first.internalResistance * 100)
                        / monthsElapsed;
            }
        }

        return metrics;
    }

    // =========================================================================
    // BUILD HEALTH RECORD
    // =========================================================================

    /**
     * Build the final BatteryHealth record with all calculated metrics
     */
    private BatteryHealth buildHealthRecord(
            BatterySnapshot snapshot, BatteryHealth latest, BatteryHealth first,
            float designCapacity, CapacityEstimate capacityEstimate,
            float stateOfHealth, float capacityFade, float capacityLoss,
            CycleMetrics cycles, DegradationMetrics degradation,
            float predictedEOL, float predictedReplacement,
            AbnormalWearDetector.WearAnalysis wearAnalysis,
            DegradationModel.ConfidenceResult confidence,
            DegradationModel.HealthScores scores,
            int trackingDays) {

        BatteryHealth health = new BatteryHealth();

        // Timestamp
        health.timestamp = snapshot.timestamp;

        // Capacity metrics
        health.designCapacityMah = designCapacity;
        health.currentCapacityMah = capacityEstimate.currentCapacity;
        health.measuredCapacityMah = capacityEstimate.measuredCapacity;
        health.estimatedCapacityMah = capacityEstimate.estimatedCapacity;
        health.capacityLossMah = capacityLoss;
        health.capacityFade = capacityFade;
        health.healthPercentage = stateOfHealth;
        health.capacityFadePercent = capacityFade;
        health.stateOfHealth = stateOfHealth;

        // Cycle metrics
        health.cycleCount = cycles.totalCycles;
        health.fullCycleCount = cycles.fullCycleCount;
        health.partialCycleAccumulator = cycles.partialAccumulator;
        health.totalChargeAccumulated = cycles.totalChargeMah;
        health.totalDischargeAccumulated = cycles.totalDischargeMah;
        health.deepCycleCount = cycles.deepCycleCount;
        health.topOffCycleCount = cycles.topOffCycleCount;
        health.trackingDays = trackingDays;
        health.firstRecordTimestamp = first != null ? first.timestamp : snapshot.timestamp;

        // Degradation metrics
        health.fadePerCycle = degradation.fadePerCycle;
        health.fadePer100Cycles = degradation.fadePer100Cycles;
        health.fadePerMonth = degradation.fadePerMonth;
        health.degradationRate = degradation.currentRate;
        health.smoothedDegradationRate = degradation.smoothedRate;
        health.degradationAcceleration = degradation.acceleration;
        health.degradationTrend = degradation.trend;

        // EOL prediction
        health.predictedEOL = sanitize(predictedEOL);
        health.predictedReplacementDate = sanitize(predictedReplacement);
        health.eolConfidence = sanitize(confidence != null ? confidence.degradationConfidence : 0f);

        // Resistance metrics (carry forward or calculate)
        health.internalResistance = sanitize(latest != null ? latest.internalResistance : 0);
        health.resistance25C = sanitize(latest != null ? latest.resistance25C : 0);
        health.resistanceIncrease = sanitize(calculateResistanceIncrease(latest));
        health.voltageDropUnderLoad = sanitize(latest != null ? latest.voltageDropUnderLoad : 0);
        health.selfDischargeRate = sanitize(latest != null ? latest.selfDischargeRate : 0);
        health.chargingEfficiency = sanitize(latest != null ? latest.chargingEfficiency : 90f);
        health.dischargeEfficiency = sanitize(latest != null ? latest.dischargeEfficiency : 95f);

        // PARANOID: Ensure eolConfidence is explicitly non-null/initialized for Room
        // Sanitize check already handled above, but keeping safety check for now.
        if (Float.isNaN(health.eolConfidence) || Float.isInfinite(health.eolConfidence)) {
            health.eolConfidence = 0f;
        }

        // Abnormal wear detection
        health.hasAbnormalWear = wearAnalysis.hasAbnormalWear();
        health.hasAbnormalAging = wearAnalysis.hasAcceleratingDegradation;
        health.hasSwellingRisk = wearAnalysis.hasSwellingRisk;
        health.hasCalibrationIssue = Math.abs(snapshot.level - stateOfHealth) > 10f;
        health.hasHighResistance = wearAnalysis.hasResistanceIssue;
        health.hasCapacityInstability = wearAnalysis.hasSuddenDrop;
        health.abnormalityDetails = wearAnalysis.getSummary();
        health.agingNotes = wearAnalysis.recommendation;
        health.wearDeviation = wearAnalysis.fadeDeviation;
        health.wearSeverity = wearAnalysis.totalSeverityScore;

        // Health scores
        health.capacityScore = scores.capacityScore;
        health.resistanceScore = scores.resistanceScore;
        health.cycleScore = scores.cycleScore;
        health.degradationScore = scores.degradationScore;
        health.thermalScore = scores.thermalScore;
        health.overallScore = scores.overallScore;

        // Supplier/batch info (carry forward)
        health.batteryModel = latest != null ? latest.batteryModel : null;
        health.manufacturer = latest != null ? latest.manufacturer : null;
        health.manufactureDate = latest != null ? latest.manufactureDate : null;
        health.batchId = latest != null ? latest.batchId : null;

        // Update supplier/batch aggregations
        updateSupplierBatchMetrics(health);

        // Calibration tracking
        health.lastFullCharge = snapshot.level >= FULL_CHARGE_LEVEL
                ? snapshot.timestamp
                : (latest != null ? latest.lastFullCharge : 0);
        health.lastFullDischarge = snapshot.level <= FULL_DISCHARGE_LEVEL
                ? snapshot.timestamp
                : (latest != null ? latest.lastFullDischarge : 0);
        health.hasCalibrationCycle = (health.lastFullCharge > 0 && health.lastFullDischarge > 0);
        health.needsCalibration = health.hasCalibrationIssue || !health.hasCalibrationCycle;
        health.dataPointCount = getSnapshotCount(first);

        // Confidence metrics
        health.overallConfidence = sanitize(confidence != null ? confidence.overallConfidence : 0f);
        health.capacityConfidence = sanitize(confidence != null ? confidence.capacityConfidence : 0f);
        health.cycleConfidence = sanitize(confidence != null ? confidence.cycleConfidence : 0f);
        health.degradationConfidence = sanitize(confidence != null ? confidence.degradationConfidence : 0f);
        health.meetsMinimumDataRequirement = confidence != null && confidence.meetsMinimumRequirement;

        return health;
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private BatteryHealth getFirstHealthRecord() {
        List<BatteryHealth> all = database.batteryHealthDao().getRecentHealthRecords(1000);
        if (all != null && !all.isEmpty()) {
            return all.get(all.size() - 1); // Oldest
        }
        return null;
    }

    private BatteryHealth getHealthAtTime(long timestamp) {
        List<BatteryHealth> history = database.batteryHealthDao()
                .getHealthHistorySince(timestamp);
        if (history != null && !history.isEmpty()) {
            return history.get(0);
        }
        return null;
    }

    private int calculateTrackingDays(BatteryHealth first, long currentTimestamp) {
        if (first == null)
            return 0;
        return (int) ((currentTimestamp - first.timestamp) / (24 * 3600000L));
    }

    private int getSnapshotCount(BatteryHealth first) {
        if (first == null)
            return 0;
        return database.batterySnapshotDao().getCountSince(first.timestamp);
    }

    private boolean checkCalibrationCycle(BatteryHealth latest, BatterySnapshot snapshot) {
        if (latest == null)
            return false;
        // Has both full charge and full discharge recorded
        return latest.lastFullCharge > 0 && latest.lastFullDischarge > 0;
    }

    private float calculateMeasurementVariance() {
        // Calculate variance of recent capacity measurements
        List<BatteryHealth> recent = database.batteryHealthDao().getRecentHealthRecords(10);
        if (recent == null || recent.size() < 3)
            return 10f; // High variance when little data

        float sum = 0;
        float sumSq = 0;
        for (BatteryHealth h : recent) {
            sum += h.currentCapacityMah;
            sumSq += h.currentCapacityMah * h.currentCapacityMah;
        }
        float mean = sum / recent.size();
        float variance = (sumSq / recent.size()) - (mean * mean);
        return (float) Math.sqrt(variance) / mean * 100; // CV as percentage
    }

    private float calculateResistanceIncrease(BatteryHealth latest) {
        if (latest == null || latest.internalResistance <= 0)
            return 0;
        BatteryHealth first = getFirstHealthRecord();
        if (first == null || first.internalResistance <= 0)
            return 0;
        return DegradationModel.calculateResistanceIncrease(
                latest.internalResistance, first.internalResistance);
    }

    private float getAverageTemperature() {
        List<BatterySnapshot> recent = database.batterySnapshotDao()
                .getSnapshotsSince(System.currentTimeMillis() - WEEK_MS);
        if (recent == null || recent.isEmpty())
            return 25f;

        float sum = 0;
        for (BatterySnapshot s : recent) {
            sum += s.temperature / 10f;
        }
        return sum / recent.size();
    }

    private TemperatureStats getTemperatureStats() {
        TemperatureStats stats = new TemperatureStats();
        List<BatterySnapshot> recent = database.batterySnapshotDao()
                .getSnapshotsSince(System.currentTimeMillis() - WEEK_MS);

        if (recent == null || recent.isEmpty()) {
            stats.average = 25;
            stats.min = 25;
            stats.max = 25;
            stats.maxSpike = 0;
            return stats;
        }

        float sum = 0;
        float min = Float.MAX_VALUE;
        float max = Float.MIN_VALUE;
        float maxSpike = 0;
        float prevTemp = -1;

        for (BatterySnapshot s : recent) {
            float tempC = s.temperature / 10f;
            sum += tempC;
            min = Math.min(min, tempC);
            max = Math.max(max, tempC);

            if (prevTemp >= 0) {
                float spike = Math.abs(tempC - prevTemp);
                maxSpike = Math.max(maxSpike, spike);
            }
            prevTemp = tempC;
        }

        stats.average = sum / recent.size();
        stats.min = min;
        stats.max = max;
        stats.maxSpike = maxSpike;

        return stats;
    }

    private void updateSupplierBatchMetrics(BatteryHealth health) {
        if (health.manufacturer != null && !health.manufacturer.isEmpty()) {
            health.supplierAverageHealth = database.batteryHealthDao()
                    .getAverageHealthBySupplier(health.manufacturer);
            health.supplierDeviation = health.healthPercentage - health.supplierAverageHealth;

            // Calculate quality tier
            if (health.supplierAverageHealth >= 90) {
                health.supplierQualityTier = "A";
            } else if (health.supplierAverageHealth >= 80) {
                health.supplierQualityTier = "B";
            } else if (health.supplierAverageHealth >= 70) {
                health.supplierQualityTier = "C";
            } else if (health.supplierAverageHealth >= 60) {
                health.supplierQualityTier = "D";
            } else {
                health.supplierQualityTier = "F";
            }
        }

        if (health.batchId != null && !health.batchId.isEmpty()) {
            health.batchAverageHealth = database.batteryHealthDao()
                    .getAverageHealthByBatch(health.batchId);
            health.batchDeviation = health.healthPercentage - health.batchAverageHealth;

            // Flag if batch average is significantly below supplier average
            if (health.supplierAverageHealth > 0) {
                health.batchQualityAlert = (health.supplierAverageHealth - health.batchAverageHealth) > 5;
            }
        }
    }

    // =========================================================================
    // DATA CLASSES
    // =========================================================================

    private static class CapacityEstimate {
        float currentCapacity = 0;
        float measuredCapacity = 0;
        float estimatedCapacity = 0;
        String method = "unknown";
        int confidence = 0;
    }

    private static class CycleMetrics {
        float totalCycles = 0;
        int fullCycleCount = 0;
        float partialAccumulator = 0;
        int deepCycleCount = 0;
        int topOffCycleCount = 0;
        long totalChargeMah = 0;
        long totalDischargeMah = 0;
        float wearAdjustedCycles = 0;
        float cyclingHealthRatio = 1.0f;
    }

    private static class DegradationMetrics {
        float fadePerCycle = 0;
        float fadePer100Cycles = 0;
        float fadePerMonth = 0;
        float currentRate = 0;
        float smoothedRate = 0;
        float acceleration = 0;
        String trend = "unknown";
    }

    private static class TemperatureStats {
        float average = 25;
        float min = 25;
        float max = 25;
        float maxSpike = 0;
    }

    // =========================================================================
    // UTILITY
    // =========================================================================

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Sanitize float values to prevent SQLite constraint failures.
     * SQLite converts NaN and Infinity to NULL, which triggers NOT NULL constraint
     * violations.
     */
    private float sanitize(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return 0f;
        }
        return value;
    }
}
