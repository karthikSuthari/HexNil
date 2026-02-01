package com.example.hexnil.manager;

import android.content.Context;
import android.util.Log;

import com.example.hexnil.charging.ChargerClassifier;
import com.example.hexnil.charging.ChargingSafetyScorer;
import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.model.BatterySnapshot;
import com.example.hexnil.model.ChargingSession;

/**
 * OEM-grade charging session lifecycle management and analysis.
 * 
 * Handles:
 * - Correct session start/end detection with edge cases
 * - Multi-phase charging analysis (CC/CV phases)
 * - Charging curve capture (V/I/T vs time)
 * - Fast/normal/slow classification
 * - Charger fingerprinting (OEM vs third-party)
 * - Over-current, over-voltage, over-temperature detection
 * - Charging efficiency calculation
 * - Overnight charging detection
 * - Safety scoring and OEM compliance
 * - Battery degradation impact tracking
 * 
 * Edge Cases Handled:
 * - Brief unplugs (< 30 seconds) don't end session
 * - Power fluctuations during fast charging
 * - Overnight charging with float phase
 * - Interrupted sessions
 * - Cable/charger swaps mid-session
 */
public class ChargingAnalyzer {

    private static final String TAG = "ChargingAnalyzer";
    
    // ========================================================================
    // CONFIGURATION THRESHOLDS
    // ========================================================================
    
    // Session detection
    private static final long MIN_SESSION_DURATION_MS = 60_000;      // 1 minute minimum
    private static final long BRIEF_UNPLUG_THRESHOLD_MS = 30_000;    // 30 sec grace period
    private static final long OVERNIGHT_THRESHOLD_MS = 6 * 60 * 60 * 1000; // 6 hours
    
    // CC/CV phase detection
    private static final float CC_CV_VOLTAGE_THRESHOLD = 4200f;      // mV at CC/CV transition
    private static final float CV_CURRENT_DROP_THRESHOLD = 0.5f;     // Current drops to 50% of peak
    private static final float TRICKLE_CURRENT_THRESHOLD = 100f;     // mA for trickle phase
    
    // Fluctuation detection
    private static final float CURRENT_FLUCTUATION_THRESHOLD = 500f; // mA change
    private static final float VOLTAGE_FLUCTUATION_THRESHOLD = 80f;  // mV change
    
    // Safety thresholds
    private static final float OVER_CURRENT_THRESHOLD = 2500f;       // mA
    private static final float OVER_VOLTAGE_THRESHOLD = 4450f;       // mV
    private static final float OVER_TEMP_THRESHOLD = 44f;            // °C
    
    // Interruption detection
    private static final float INTERRUPTION_THRESHOLD_MA = 400f;     // Current drop = interruption
    
    // Full charge detection
    private static final int FULL_CHARGE_LEVEL = 100;
    private static final float FULL_CHARGE_CURRENT_MA = 150f;        // Trickle current at 100%
    
    // ========================================================================
    // STATE VARIABLES
    // ========================================================================
    
    private final HexNilDatabase database;
    private ChargingSession activeSession;
    
    // Accumulators for averaging
    private float voltageSum;
    private float currentSum;
    private float tempSum;
    private int sampleCount;
    
    // Last sample tracking
    private float lastCurrentMa;
    private float lastVoltageMv;
    private float lastTempC;
    private long lastTimestamp;
    private int lastLevel;
    
    // Peak tracking
    private float peakCurrentMa;
    private long peakCurrentTimestamp;
    
    // CC/CV phase tracking
    private boolean inCCPhase = true;
    private long ccPhaseStartTime;
    private long cvPhaseStartTime;
    private float ccPhaseCurrentSum;
    private int ccPhaseSampleCount;
    private float cvPhaseCurrentSum;
    private int cvPhaseSampleCount;
    
    // Full charge tracking
    private long fullChargeStartTime;
    private boolean wasAtFullCharge;
    
    // Interruption tracking
    private long lastInterruptionTs;
    private long lastSampleIntervalSum;
    private int sampleIntervalCount;
    private long longestInterruption;
    private long totalInterruptionTime;
    
    // Curve builders
    private StringBuilder voltageCurve;
    private StringBuilder currentCurve;
    private StringBuilder temperatureCurve;
    private StringBuilder interruptionTimestamps;
    private StringBuilder interruptionDurations;
    
    // Session start timestamp for relative curve times
    private long sessionStartTimestamp;
    
    // Brief unplug detection
    private long unpluggedTimestamp;
    private boolean wasRecentlyUnplugged;
    
    // Screen tracking
    private boolean screenWasOnDuringCharge;
    
    // Flag to track if first sample has been processed
    private boolean firstSampleProcessed;
    
    // ========================================================================
    // CONSTRUCTOR
    // ========================================================================
    
    public ChargingAnalyzer(Context context, HexNilDatabase database) {
        this.database = database;
    }
    
    // ========================================================================
    // SESSION LIFECYCLE
    // ========================================================================
    
    /**
     * Start a new charging session.
     * Called when power is connected.
     */
    public synchronized void startChargingSession() {
        // Check for brief unplug scenario
        if (wasRecentlyUnplugged && activeSession != null) {
            long unpluggedDuration = System.currentTimeMillis() - unpluggedTimestamp;
            if (unpluggedDuration < BRIEF_UNPLUG_THRESHOLD_MS) {
                Log.d(TAG, "Brief unplug detected (" + unpluggedDuration + "ms), resuming session");
                wasRecentlyUnplugged = false;
                // Don't count as interruption if very brief (< 5 sec)
                if (unpluggedDuration > 5000) {
                    activeSession.interruptionCount++;
                    appendValue(interruptionTimestamps, System.currentTimeMillis());
                    appendValue(interruptionDurations, unpluggedDuration);
                    if (unpluggedDuration > longestInterruption) {
                        longestInterruption = unpluggedDuration;
                    }
                    totalInterruptionTime += unpluggedDuration;
                }
                return;
            }
        }
        
        // If we already have an active session with data, don't create a new one
        // This prevents race conditions where startChargingSession is called multiple times
        if (activeSession != null && firstSampleProcessed) {
            Log.d(TAG, "Session already active with data, skipping new session creation");
            return;
        }
        
        // End any existing incomplete session that has no data
        if (activeSession != null && !activeSession.isComplete()) {
            Log.d(TAG, "Closing orphaned session before starting new one");
            endChargingSession();
        }
        
        // Create new session
        activeSession = new ChargingSession();
        activeSession.startTime = System.currentTimeMillis();
        resetAccumulators();
        
        // Insert and get auto-generated ID
        long id = database.chargingSessionDao().insert(activeSession);
        activeSession.id = id;
        
        Log.d(TAG, "Started new charging session: " + id);
    }
    
    /**
     * End the current charging session.
     * Called when power is disconnected.
     */
    public synchronized void endChargingSession() {
        if (activeSession == null) {
            Log.d(TAG, "No active session to end");
            return;
        }
        
        // Mark unplug time for brief unplug detection
        unpluggedTimestamp = System.currentTimeMillis();
        wasRecentlyUnplugged = true;
        
        // Check if session is too short
        long duration = System.currentTimeMillis() - activeSession.startTime;
        if (duration < MIN_SESSION_DURATION_MS) {
            Log.d(TAG, "Session too short (" + duration + "ms), discarding");
            database.chargingSessionDao().delete(activeSession);
            activeSession = null;
            wasRecentlyUnplugged = false;
            return;
        }
        
        // Finalize session data
        activeSession.endTime = System.currentTimeMillis();
        activeSession.chargedAmount = Math.max(0, activeSession.endLevel - activeSession.startLevel);
        activeSession.chargingDuration = activeSession.endTime - activeSession.startTime;
        
        // Calculate final averages
        if (sampleCount > 0) {
            activeSession.avgVoltage = voltageSum / sampleCount;
            activeSession.avgCurrent = currentSum / sampleCount;
            activeSession.avgTemperature = tempSum / sampleCount;
        }
        
        // Calculate charging speed (%/hour)
        float hours = activeSession.chargingDuration / 3_600_000f;
        if (hours > 0) {
            activeSession.chargingSpeed = activeSession.chargedAmount / hours;
        }
        
        // Calculate charging efficiency
        calculateChargingEfficiency();
        
        // Calculate power
        activeSession.powerWatts = (activeSession.avgVoltage / 1000f) * (activeSession.avgCurrent / 1000f);
        
        // Finalize CC/CV phase data
        finalizeCCCVPhases();
        
        // Finalize overnight charging data
        finalizeOvernightData();
        
        // Calculate temperature rise
        activeSession.temperatureRise = activeSession.maxTemperature - activeSession.startTemperature;
        
        // Finalize interruption data
        activeSession.longestInterruption = longestInterruption;
        activeSession.totalInterruptionTime = totalInterruptionTime;
        
        // Finalize curves
        finalizeCurves();
        
        // Sample count
        activeSession.samplesCollected = sampleCount;
        activeSession.curveSampleCount = sampleCount;
        
        // Screen tracking
        activeSession.screenOnDuringCharge = screenWasOnDuringCharge;
        
        // Classify charger
        classifyCharger();
        
        // Safety scoring
        performSafetyScoring();
        
        // Determine session context
        determineSessionContext();
        
        // Save finalized session
        database.chargingSessionDao().update(activeSession);
        
        Log.d(TAG, "Ended charging session: " + activeSession.id + 
              ", charged " + activeSession.chargedAmount + "% in " + (duration / 60000) + " min");
        
        activeSession = null;
        wasRecentlyUnplugged = false;
    }
    
    // ========================================================================
    // SNAPSHOT PROCESSING
    // ========================================================================
    
    /**
     * Process a battery snapshot during charging.
     * Called periodically by BatteryMonitorService.
     */
    public synchronized void processChargingSnapshot(BatterySnapshot snapshot) {
        if (activeSession == null) {
            startChargingSession();
        }
        if (activeSession == null) return;
        
        // First sample initialization - use flag instead of checking startLevel==0
        // (battery could genuinely be at 0% when plugging in)
        if (!firstSampleProcessed) {
            activeSession.startLevel = snapshot.level;
            activeSession.startTemperature = snapshot.getTemperatureCelsius();
            activeSession.startTime = snapshot.timestamp;
            sessionStartTimestamp = snapshot.timestamp;
            // Handle nullable Integer -> int conversion for charger IDs
            activeSession.chargerVendorId = snapshot.chargerVendorId != null ? snapshot.chargerVendorId : 0;
            activeSession.chargerProductId = snapshot.chargerProductId != null ? snapshot.chargerProductId : 0;
            ccPhaseStartTime = snapshot.timestamp;
            lastLevel = snapshot.level;
            firstSampleProcessed = true;
            Log.d(TAG, "First sample: level=" + snapshot.level + "%, temp=" + snapshot.getTemperatureCelsius() + "°C");
        }
        
        // Update charger IDs if they weren't available at start but are now
        // This handles cases where USB enumeration takes time
        if (activeSession.chargerVendorId == 0 && snapshot.chargerVendorId != null && snapshot.chargerVendorId != 0) {
            activeSession.chargerVendorId = snapshot.chargerVendorId;
            activeSession.chargerProductId = snapshot.chargerProductId != null ? snapshot.chargerProductId : 0;
        }
        
        // Update current values
        activeSession.endLevel = snapshot.level;
        activeSession.peakLevel = Math.max(activeSession.peakLevel, snapshot.level);
        activeSession.endTemperature = snapshot.getTemperatureCelsius();
        
        // Temperature tracking
        if (activeSession.maxTemperature < snapshot.getTemperatureCelsius()) {
            activeSession.maxTemperature = snapshot.getTemperatureCelsius();
        }
        
        // Current tracking
        float absCurrent = Math.abs(snapshot.chargeRate);
        if (absCurrent > activeSession.maxCurrent) {
            activeSession.maxCurrent = absCurrent;
            peakCurrentMa = absCurrent;
            peakCurrentTimestamp = snapshot.timestamp;
        }
        if (activeSession.minCurrent == 0 || absCurrent < activeSession.minCurrent) {
            activeSession.minCurrent = absCurrent;
        }
        
        // Voltage tracking
        if (activeSession.maxVoltage < snapshot.voltage) {
            activeSession.maxVoltage = snapshot.voltage;
        }
        if (activeSession.minVoltage == 0 || snapshot.voltage < activeSession.minVoltage) {
            activeSession.minVoltage = snapshot.voltage;
        }
        
        // Charging type classification
        activeSession.chargingType = ChargerClassifier.getChargingType(absCurrent, snapshot.isFastCharging);
        activeSession.isFastCharging = snapshot.isFastCharging;
        activeSession.plugType = snapshot.plugged;
        
        // Energy accumulation
        activeSession.energyDelivered += accumulateEnergy(snapshot);
        
        // Accumulate for averages
        sampleCount++;
        voltageSum += snapshot.voltage;
        currentSum += absCurrent;
        tempSum += snapshot.getTemperatureCelsius();
        activeSession.avgVoltage = voltageSum / sampleCount;
        activeSession.avgCurrent = currentSum / sampleCount;
        activeSession.avgTemperature = tempSum / sampleCount;
        
        // Track samples collected
        activeSession.samplesCollected = sampleCount;
        
        // Calculate temperatureRise live (will be finalized at end)
        activeSession.temperatureRise = activeSession.maxTemperature - activeSession.startTemperature;
        
        // CC/CV phase detection
        detectChargingPhase(snapshot);
        
        // Full charge tracking
        trackFullCharge(snapshot);
        
        // Detect fluctuations and safety events
        detectFluctuations(snapshot);
        
        // Detect interruptions
        detectInterruptions(snapshot);
        
        // Record curve samples
        recordCurveSamples(snapshot);
        
        // Real-time safety evaluation
        evaluateRealTimeSafety(snapshot);
        
        // Charger fingerprint - format as hex string for readability
        if (snapshot.chargerVendorId != null && snapshot.chargerVendorId != 0) {
            activeSession.chargerFingerprint = String.format("%04X:%04X", 
                    snapshot.chargerVendorId, 
                    snapshot.chargerProductId != null ? snapshot.chargerProductId : 0);
        } else if (activeSession.chargerVendorId != 0) {
            // Use cached values if current snapshot doesn't have them
            activeSession.chargerFingerprint = String.format("%04X:%04X", 
                    activeSession.chargerVendorId, 
                    activeSession.chargerProductId);
        } else {
            // No charger ID available
            activeSession.chargerFingerprint = null;
        }
        
        // Screen tracking
        if (snapshot.isScreenOnOrFalse()) {
            screenWasOnDuringCharge = true;
        }
        
        // Track top app during charge
        if (snapshot.topAppPackage != null && !snapshot.topAppPackage.isEmpty()) {
            activeSession.topAppDuringCharge = snapshot.topAppPackage;
        }
        
        // Update last values
        lastCurrentMa = absCurrent;
        lastVoltageMv = snapshot.voltage;
        lastTempC = snapshot.getTemperatureCelsius();
        lastTimestamp = snapshot.timestamp;
        lastLevel = snapshot.level;
        
        // Periodic save (every 10 samples)
        if (sampleCount % 10 == 0) {
            database.chargingSessionDao().update(activeSession);
        }
    }
    
    // ========================================================================
    // CC/CV PHASE DETECTION
    // ========================================================================
    
    /**
     * Detect and track CC (Constant Current) vs CV (Constant Voltage) phases.
     * 
     * Li-ion charging typically has:
     * 1. CC Phase: Battery charged at max current until voltage reaches ~4.2V
     * 2. CV Phase: Voltage held constant while current tapers off
     * 3. Trickle: Very low current to maintain 100%
     */
    private void detectChargingPhase(BatterySnapshot snapshot) {
        float current = Math.abs(snapshot.chargeRate);
        float voltage = snapshot.voltage;
        
        if (inCCPhase) {
            // CC phase ends when:
            // 1. Voltage reaches threshold (typically 4.2V / 4200mV)
            // 2. Current starts dropping significantly
            
            boolean voltageReached = voltage >= CC_CV_VOLTAGE_THRESHOLD;
            boolean currentDropping = peakCurrentMa > 0 && current < (peakCurrentMa * CV_CURRENT_DROP_THRESHOLD);
            
            if (voltageReached || currentDropping) {
                // Transition to CV phase
                inCCPhase = false;
                activeSession.ccPhaseDuration = snapshot.timestamp - ccPhaseStartTime;
                activeSession.ccPhaseEndLevel = snapshot.level;
                activeSession.ccToCvTransitionVoltage = voltage;
                
                if (ccPhaseSampleCount > 0) {
                    activeSession.ccPhaseAvgCurrent = ccPhaseCurrentSum / ccPhaseSampleCount;
                }
                
                cvPhaseStartTime = snapshot.timestamp;
                activeSession.currentPhase = "cv";
                
                Log.d(TAG, "Transitioned to CV phase at " + snapshot.level + "%, " + voltage + "mV, ccDuration=" + activeSession.ccPhaseDuration + "ms");
            } else {
                // Still in CC phase - update duration in real-time
                ccPhaseCurrentSum += current;
                ccPhaseSampleCount++;
                activeSession.ccPhaseDuration = snapshot.timestamp - ccPhaseStartTime;
            }
        } else {
            // In CV phase - update duration in real-time
            cvPhaseCurrentSum += current;
            cvPhaseSampleCount++;
            activeSession.cvPhaseDuration = snapshot.timestamp - cvPhaseStartTime;
            
            // Detect trickle phase (very low current at 100%)
            if (snapshot.level >= 100 && current < TRICKLE_CURRENT_THRESHOLD) {
                activeSession.currentPhase = "trickle";
            }
        }
    }
    
    /**
     * Finalize CC/CV phase data when session ends.
     */
    private void finalizeCCCVPhases() {
        if (!inCCPhase && cvPhaseStartTime > 0) {
            activeSession.cvPhaseDuration = activeSession.endTime - cvPhaseStartTime;
            if (cvPhaseSampleCount > 0) {
                activeSession.cvPhaseAvgCurrent = cvPhaseCurrentSum / cvPhaseSampleCount;
            }
        } else {
            // Never transitioned - entire session was CC phase
            activeSession.ccPhaseDuration = activeSession.chargingDuration;
            if (ccPhaseSampleCount > 0) {
                activeSession.ccPhaseAvgCurrent = ccPhaseCurrentSum / ccPhaseSampleCount;
            }
        }
        
        activeSession.currentPhase = "complete";
    }
    
    // ========================================================================
    // FULL CHARGE & OVERNIGHT TRACKING
    // ========================================================================
    
    /**
     * Track time spent at full charge (100%).
     */
    private void trackFullCharge(BatterySnapshot snapshot) {
        if (snapshot.level >= FULL_CHARGE_LEVEL) {
            if (!wasAtFullCharge) {
                // Just reached full charge
                fullChargeStartTime = snapshot.timestamp;
                wasAtFullCharge = true;
                activeSession.reachedFullCharge = true;
                activeSession.fullChargeHoldCount++;
            }
        } else if (wasAtFullCharge) {
            // Dropped below 100%
            long timeAtFull = snapshot.timestamp - fullChargeStartTime;
            activeSession.timeAtFullCharge += timeAtFull;
            wasAtFullCharge = false;
        }
    }
    
    /**
     * Finalize overnight and full charge tracking.
     */
    private void finalizeOvernightData() {
        // If still at full charge when session ends
        if (wasAtFullCharge && fullChargeStartTime > 0) {
            activeSession.timeAtFullCharge += activeSession.endTime - fullChargeStartTime;
        }
        
        // Calculate percentage of session at 100%
        if (activeSession.chargingDuration > 0) {
            activeSession.percentAtFullCharge = 
                (activeSession.timeAtFullCharge * 100f) / activeSession.chargingDuration;
        }
        
        // Overnight charging detection
        activeSession.isOvernightCharging = activeSession.chargingDuration >= OVERNIGHT_THRESHOLD_MS;
    }
    
    // ========================================================================
    // FLUCTUATION & SAFETY EVENT DETECTION
    // ========================================================================
    
    /**
     * Detect current/voltage fluctuations and safety threshold violations.
     */
    private void detectFluctuations(BatterySnapshot snapshot) {
        float current = Math.abs(snapshot.chargeRate);
        
        // Current fluctuation detection
        if (lastCurrentMa != 0 && Math.abs(current - lastCurrentMa) > CURRENT_FLUCTUATION_THRESHOLD) {
            activeSession.currentFluctuations++;
        }
        
        // Voltage fluctuation detection
        if (lastVoltageMv != 0 && Math.abs(snapshot.voltage - lastVoltageMv) > VOLTAGE_FLUCTUATION_THRESHOLD) {
            activeSession.voltageFluctuations++;
        }
        
        // Over-current detection
        if (current > OVER_CURRENT_THRESHOLD) {
            activeSession.overCurrentEvents++;
        }
        
        // Over-voltage detection
        if (snapshot.voltage > OVER_VOLTAGE_THRESHOLD) {
            activeSession.overVoltageEvents++;
        }
        
        // Over-temperature detection
        if (snapshot.getTemperatureCelsius() > OVER_TEMP_THRESHOLD) {
            activeSession.overTempEvents++;
        }
    }
    
    // ========================================================================
    // INTERRUPTION DETECTION
    // ========================================================================
    
    /**
     * Detect charging interruptions (sudden current drops).
     */
    private void detectInterruptions(BatterySnapshot snapshot) {
        float current = Math.abs(snapshot.chargeRate);
        
        if (lastCurrentMa > TRICKLE_CURRENT_THRESHOLD) {
            float drop = lastCurrentMa - current;
            if (drop > INTERRUPTION_THRESHOLD_MA) {
                activeSession.interruptionCount++;
                long now = snapshot.timestamp;
                long duration = lastInterruptionTs > 0 ? now - lastInterruptionTs : 0;
                
                appendValue(interruptionTimestamps, now);
                appendValue(interruptionDurations, duration);
                
                if (duration > longestInterruption) {
                    longestInterruption = duration;
                }
                totalInterruptionTime += duration;
                lastInterruptionTs = now;
            }
        }
        
        // Compute stability score
        float penalties = activeSession.interruptionCount * 5f + 
                         activeSession.currentFluctuations * 2f + 
                         activeSession.voltageFluctuations * 1.5f;
        activeSession.curveStabilityScore = Math.max(0f, 100f - penalties);
    }
    
    // ========================================================================
    // CURVE RECORDING
    // ========================================================================
    
    /**
     * Record voltage, current, and temperature curve samples.
     * Uses relative timestamps from session start for cleaner data.
     */
    private void recordCurveSamples(BatterySnapshot snapshot) {
        // Initialize session start timestamp if not set
        if (sessionStartTimestamp == 0) {
            sessionStartTimestamp = snapshot.timestamp;
        }
        
        // Voltage curve - format: {"t":relativeMs,"v":voltageVolts}
        appendVoltageSample(voltageCurve, snapshot.timestamp, snapshot.voltage);
        
        // Current curve - format: {"t":relativeMs,"i":currentMa}
        appendCurrentSample(currentCurve, snapshot.timestamp, Math.abs(snapshot.chargeRate));
        
        // Temperature curve - format: {"t":relativeMs,"temp":tempCelsius}
        appendTemperatureSample(temperatureCurve, snapshot.timestamp, snapshot.getTemperatureCelsius());
        
        // Sample interval tracking
        if (lastTimestamp > 0) {
            long interval = snapshot.timestamp - lastTimestamp;
            if (interval > 0) {
                lastSampleIntervalSum += interval;
                sampleIntervalCount++;
            }
        }
        
        activeSession.curveSampleIntervalMs = sampleIntervalCount > 0
                ? (int) (lastSampleIntervalSum / sampleIntervalCount)
                : 0;
    }
    
    /**
     * Finalize all curve JSON arrays.
     */
    private void finalizeCurves() {
        activeSession.voltageCurveJson = closeArray(voltageCurve);
        activeSession.currentCurveJson = closeArray(currentCurve);
        activeSession.temperatureCurveJson = closeArray(temperatureCurve);
        activeSession.interruptionTimestamps = closeArray(interruptionTimestamps);
        activeSession.interruptionDurations = closeArray(interruptionDurations);
    }
    
    // ========================================================================
    // ENERGY & EFFICIENCY CALCULATIONS
    // ========================================================================
    
    /**
     * Accumulate energy delivered during charging.
     * Returns mAh for this sample interval.
     */
    private float accumulateEnergy(BatterySnapshot snapshot) {
        if (lastTimestamp == 0) {
            lastTimestamp = snapshot.timestamp;
            return 0f;
        }
        
        long deltaMs = Math.max(0, snapshot.timestamp - lastTimestamp);
        float deltaHours = deltaMs / 3_600_000f;
        float chargeMa = Math.max(0f, Math.abs(snapshot.chargeRate));
        
        return chargeMa * deltaHours; // mAh
    }
    
    /**
     * Calculate charging efficiency.
     * 
     * Efficiency = (Battery capacity gained / Energy delivered) * 100
     * 
     * Note: This is an approximation since we estimate capacity from percentage.
     */
    private void calculateChargingEfficiency() {
        if (activeSession.energyDelivered <= 0 || activeSession.chargedAmount <= 0) {
            activeSession.chargingEfficiency = 0;
            return;
        }
        
        // Estimate battery capacity (assume ~4000mAh typical)
        // TODO: Use actual design capacity from device
        float estimatedCapacityMah = 4000f;
        float capacityGainedMah = (activeSession.chargedAmount / 100f) * estimatedCapacityMah;
        
        // Efficiency calculation
        float efficiency = (capacityGainedMah / activeSession.energyDelivered) * 100f;
        
        // Clamp to reasonable range (charging efficiency rarely exceeds 95%)
        activeSession.chargingEfficiency = Math.min(100f, Math.max(0f, efficiency));
    }
    
    // ========================================================================
    // SAFETY & CLASSIFICATION
    // ========================================================================
    
    /**
     * Evaluate real-time safety during charging.
     */
    private void evaluateRealTimeSafety(BatterySnapshot snapshot) {
        ChargingSafetyScorer.RealTimeSafetyResult safety = 
            ChargingSafetyScorer.evaluateRealTime(snapshot);
        
        activeSession.safetyStatus = safety.getStatusCode();
        
        if (!safety.severity.code.equals("excellent") && !safety.severity.code.equals("good")) {
            activeSession.safetyNotes = safety.recommendation;
        }
    }
    
    /**
     * Perform comprehensive safety scoring at session end.
     */
    private void performSafetyScoring() {
        ChargingSafetyScorer.SessionSafetyResult result = 
            ChargingSafetyScorer.evaluateSession(activeSession);
        
        activeSession.safetyScore = result.overallScore;
        activeSession.temperatureScore = result.temperatureScore;
        activeSession.voltageScore = result.voltageScore;
        activeSession.currentScore = result.currentScore;
        activeSession.stabilityScore = result.stabilityScore;
        activeSession.safetyStatus = result.getSafetyStatus();
        activeSession.passedOEMCompliance = result.passedOEMCompliance;
        activeSession.complianceNotes = result.complianceNotes;
        
        if (result.recommendations != null && result.recommendations.length > 0) {
            activeSession.safetyNotes = String.join("; ", result.recommendations);
        }
        
        // Degradation impact
        activeSession.degradationImpact = result.batteryDegradationPercent;
        
        // Calculate degradation impact with estimated capacity
        ChargingSafetyScorer.DegradationImpact degradation = 
            ChargingSafetyScorer.calculateDegradationImpact(activeSession, 4000f);
        
        activeSession.cycleEquivalent = degradation.cycleEquivalent;
        activeSession.stressLevel = degradation.cumulativeStress;
        activeSession.degradationRiskLevel = degradation.riskLevel;
        activeSession.degradationNotes = degradation.mitigationAdvice;
    }
    
    /**
     * Classify the charger used in this session.
     */
    private void classifyCharger() {
        ChargerClassifier.ChargerQuality quality = 
            ChargerClassifier.assessSessionChargerQuality(activeSession);
        
        activeSession.chargerQuality = quality.code;
        activeSession.isOriginalCharger = quality == ChargerClassifier.ChargerQuality.OEM;
        
        // Protocol detection
        ChargerClassifier.ChargingProtocol protocol = ChargerClassifier.detectProtocol(
            activeSession.avgVoltage, activeSession.avgCurrent, activeSession.plugType);
        activeSession.chargingProtocol = protocol.code;
        
        // Power capacity
        activeSession.chargerPowerCapacity = activeSession.powerWatts;
        
        // Classification string
        activeSession.chargerClassification = activeSession.chargingType.toUpperCase() + " | " +
            protocol.code.toUpperCase() + " | " +
            String.format("%.1fW", activeSession.powerWatts) + " | " +
            quality.code.toUpperCase();
    }
    
    /**
     * Determine session context (manual, scheduled, overnight, top_up).
     */
    private void determineSessionContext() {
        if (activeSession.isOvernightCharging) {
            activeSession.sessionContext = "overnight";
        } else if (activeSession.chargedAmount < 20 && activeSession.chargingDuration < 30 * 60 * 1000) {
            activeSession.sessionContext = "top_up";
        } else {
            activeSession.sessionContext = "manual";
        }
    }
    
    // ========================================================================
    // ACCUMULATOR RESET
    // ========================================================================
    
    /**
     * Reset all accumulators for a new session.
     */
    private void resetAccumulators() {
        voltageSum = 0f;
        currentSum = 0f;
        tempSum = 0f;
        sampleCount = 0;
        lastCurrentMa = 0f;
        lastVoltageMv = 0f;
        lastTempC = 0f;
        lastTimestamp = 0L;
        lastLevel = 0;
        peakCurrentMa = 0f;
        peakCurrentTimestamp = 0L;
        
        // CC/CV tracking
        inCCPhase = true;
        ccPhaseStartTime = 0L;
        cvPhaseStartTime = 0L;
        ccPhaseCurrentSum = 0f;
        ccPhaseSampleCount = 0;
        cvPhaseCurrentSum = 0f;
        cvPhaseSampleCount = 0;
        
        // Full charge tracking
        fullChargeStartTime = 0L;
        wasAtFullCharge = false;
        
        // Interruption tracking
        lastInterruptionTs = 0L;
        lastSampleIntervalSum = 0L;
        sampleIntervalCount = 0;
        longestInterruption = 0L;
        totalInterruptionTime = 0L;
        
        // Curve builders
        voltageCurve = new StringBuilder("[");
        currentCurve = new StringBuilder("[");
        temperatureCurve = new StringBuilder("[");
        interruptionTimestamps = new StringBuilder("[");
        interruptionDurations = new StringBuilder("[");
        
        // Screen tracking
        screenWasOnDuringCharge = false;
        
        // Session start time tracking
        sessionStartTimestamp = 0L;
        
        // First sample flag
        firstSampleProcessed = false;
    }
    
    // ========================================================================
    // JSON HELPERS
    // ========================================================================
    
    /**
     * Append a voltage sample to the curve.
     * Format: {"t":relativeMs,"v":voltageVolts}
     */
    private void appendVoltageSample(StringBuilder sb, long timestamp, float voltageMv) {
        if (sb == null) return;
        if (sb.length() > 1) sb.append(',');
        long relativeTime = sessionStartTimestamp > 0 ? timestamp - sessionStartTimestamp : 0;
        float voltageVolts = voltageMv / 1000f; // Convert mV to V
        sb.append("{\"t\":").append(relativeTime).append(",\"v\":").append(String.format("%.3f", voltageVolts)).append('}');
    }
    
    /**
     * Append a current sample to the curve.
     * Format: {"t":relativeMs,"i":currentMa}
     */
    private void appendCurrentSample(StringBuilder sb, long timestamp, float currentMa) {
        if (sb == null) return;
        if (sb.length() > 1) sb.append(',');
        long relativeTime = sessionStartTimestamp > 0 ? timestamp - sessionStartTimestamp : 0;
        sb.append("{\"t\":").append(relativeTime).append(",\"i\":").append((int) currentMa).append('}');
    }
    
    /**
     * Append a temperature sample to the curve.
     * Format: {"t":relativeMs,"temp":tempCelsius}
     */
    private void appendTemperatureSample(StringBuilder sb, long timestamp, float tempCelsius) {
        if (sb == null) return;
        if (sb.length() > 1) sb.append(',');
        long relativeTime = sessionStartTimestamp > 0 ? timestamp - sessionStartTimestamp : 0;
        sb.append("{\"t\":").append(relativeTime).append(",\"temp\":").append(String.format("%.1f", tempCelsius)).append('}');
    }
    
    /**
     * Legacy helper for generic pairs (used for voltage in existing code).
     */
    private void appendPair(StringBuilder sb, long t, float v) {
        if (sb == null) return;
        if (sb.length() > 1) sb.append(',');
        sb.append("{\"t\":").append(t).append(",\"v\":").append(v).append('}');
    }
    
    private void appendValue(StringBuilder sb, long v) {
        if (sb == null) return;
        if (sb.length() > 1) sb.append(',');
        sb.append(v);
    }
    
    private String closeArray(StringBuilder sb) {
        if (sb == null) return "[]";
        sb.append(']');
        return sb.toString();
    }
    
    // ========================================================================
    // PUBLIC GETTERS
    // ========================================================================
    
    /**
     * Get the active charging session (if any).
     */
    public ChargingSession getActiveSession() {
        return activeSession;
    }
    
    /**
     * Check if currently tracking a charging session.
     */
    public boolean hasActiveSession() {
        return activeSession != null && !activeSession.isComplete();
    }
    
    /**
     * Get estimated time to full charge in minutes.
     */
    public int getEstimatedTimeToFull() {
        if (activeSession == null) return 0;
        return ChargerClassifier.estimateTimeToFull(
            activeSession.endLevel, activeSession.chargingSpeed);
    }
}
