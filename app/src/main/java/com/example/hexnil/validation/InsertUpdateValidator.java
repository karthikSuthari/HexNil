package com.example.hexnil.validation;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.hexnil.model.AIAnalysisResult;
import com.example.hexnil.model.BatteryHealth;
import com.example.hexnil.model.BatterySnapshot;
import com.example.hexnil.model.ChargingSession;
import com.example.hexnil.model.DrainEvent;
import com.example.hexnil.model.ThermalEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Insert/Update Validator - Pre-Insert Validation Rules
 * 
 * VALIDATES ALL DATA BEFORE DATABASE INSERTION
 * 
 * Core Responsibilities:
 * 1. Validate against entity contracts
 * 2. Check for duplicates
 * 3. Verify referential integrity
 * 4. Apply auto-corrections when safe
 * 5. Reject invalid data with clear reasons
 * 
 * RULE: No garbage in. Period.
 * 
 * OEM Integration: All insertions pass through this validator.
 */
public class InsertUpdateValidator {
    
    private static final String TAG = "InsertUpdateValidator";
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // VALIDATION RESULT
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Result of insert/update validation.
     */
    public static class ValidationResult<T> {
        private final boolean accepted;
        private final T originalEntity;
        private final T correctedEntity;
        private final List<String> rejectionReasons;
        private final List<String> appliedCorrections;
        private final ValidationAction recommendedAction;
        
        public ValidationResult(boolean accepted, T originalEntity, T correctedEntity,
                                 List<String> rejectionReasons, List<String> appliedCorrections,
                                 ValidationAction recommendedAction) {
            this.accepted = accepted;
            this.originalEntity = originalEntity;
            this.correctedEntity = correctedEntity;
            this.rejectionReasons = rejectionReasons != null ? rejectionReasons : new ArrayList<>();
            this.appliedCorrections = appliedCorrections != null ? appliedCorrections : new ArrayList<>();
            this.recommendedAction = recommendedAction;
        }
        
        public boolean isAccepted() { return accepted; }
        public T getOriginalEntity() { return originalEntity; }
        public T getCorrectedEntity() { return correctedEntity; }
        public T getEntityToInsert() { return correctedEntity != null ? correctedEntity : originalEntity; }
        public List<String> getRejectionReasons() { return rejectionReasons; }
        public List<String> getAppliedCorrections() { return appliedCorrections; }
        public ValidationAction getRecommendedAction() { return recommendedAction; }
        
        public boolean hasCorrections() { return !appliedCorrections.isEmpty(); }
        
        // Static factory methods
        public static <T> ValidationResult<T> accept(T entity) {
            return new ValidationResult<>(true, entity, null, null, null, ValidationAction.INSERT);
        }
        
        public static <T> ValidationResult<T> acceptWithCorrections(T original, T corrected, 
                                                                      List<String> corrections) {
            return new ValidationResult<>(true, original, corrected, null, corrections, 
                    ValidationAction.INSERT_CORRECTED);
        }
        
        public static <T> ValidationResult<T> reject(T entity, List<String> reasons) {
            return new ValidationResult<>(false, entity, null, reasons, null, ValidationAction.REJECT);
        }
        
        public static <T> ValidationResult<T> skip(T entity, String reason) {
            List<String> reasons = new ArrayList<>();
            reasons.add(reason);
            return new ValidationResult<>(false, entity, null, reasons, null, ValidationAction.SKIP);
        }
    }
    
    public enum ValidationAction {
        INSERT,           // Insert as-is
        INSERT_CORRECTED, // Insert with corrections applied
        UPDATE,           // Update existing record
        SKIP,             // Skip - duplicate or unnecessary
        REJECT            // Reject - invalid data
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // BATTERY SNAPSHOT VALIDATION
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Validate a BatterySnapshot before insertion.
     */
    public ValidationResult<BatterySnapshot> validateForInsert(
            @NonNull BatterySnapshot snapshot,
            @Nullable BatterySnapshot lastSnapshot) {
        
        List<String> rejectionReasons = new ArrayList<>();
        List<String> corrections = new ArrayList<>();
        
        // === CONTRACT VALIDATION ===
        EntityContracts.ContractValidationResult contractResult = 
                EntityContracts.BatterySnapshotContract.validate(snapshot);
        
        if (!contractResult.isValid()) {
            return ValidationResult.reject(snapshot, contractResult.getViolations());
        }
        
        // === DUPLICATE CHECK ===
        if (lastSnapshot != null) {
            // Check for exact duplicate (same timestamp)
            if (snapshot.timestamp == lastSnapshot.timestamp) {
                return ValidationResult.skip(snapshot, "Duplicate timestamp - already recorded");
            }
            
            // Check for too-frequent snapshots
            long timeDelta = snapshot.timestamp - lastSnapshot.timestamp;
            if (timeDelta < EntityContracts.BatterySnapshotContract.MIN_SNAPSHOT_INTERVAL_MS) {
                return ValidationResult.skip(snapshot, 
                        "Too soon after last snapshot (" + timeDelta + "ms)");
            }
        }
        
        // === SANITY CORRECTIONS ===
        BatterySnapshot corrected = null;
        
        // Clamp level to valid range
        if (snapshot.level < 0) {
            if (corrected == null) corrected = cloneSnapshot(snapshot);
            corrected.level = 0;
            corrections.add("Clamped negative level to 0");
        } else if (snapshot.level > 100) {
            if (corrected == null) corrected = cloneSnapshot(snapshot);
            corrected.level = 100;
            corrections.add("Clamped level > 100 to 100");
        }
        
        // Fix future timestamps
        long now = System.currentTimeMillis();
        if (snapshot.timestamp > now + EntityContracts.BatterySnapshotContract.MAX_FUTURE_TOLERANCE_MS) {
            if (corrected == null) corrected = cloneSnapshot(snapshot);
            corrected.timestamp = now;
            corrections.add("Corrected future timestamp to now");
        }
        
        // === RETURN RESULT ===
        if (!corrections.isEmpty()) {
            Log.w(TAG, "Snapshot accepted with " + corrections.size() + " corrections");
            return ValidationResult.acceptWithCorrections(snapshot, corrected, corrections);
        }
        
        // Log warnings but still accept
        if (contractResult.hasWarnings()) {
            Log.d(TAG, "Snapshot accepted with warnings: " + contractResult.getWarnings());
        }
        
        return ValidationResult.accept(snapshot);
    }
    
    /**
     * Validate a batch of snapshots for bulk insert.
     */
    public List<ValidationResult<BatterySnapshot>> validateBatchForInsert(
            @NonNull List<BatterySnapshot> snapshots,
            @Nullable BatterySnapshot lastExistingSnapshot) {
        
        List<ValidationResult<BatterySnapshot>> results = new ArrayList<>();
        
        // Sort by timestamp
        List<BatterySnapshot> sorted = new ArrayList<>(snapshots);
        sorted.sort((a, b) -> Long.compare(a.timestamp, b.timestamp));
        
        BatterySnapshot previous = lastExistingSnapshot;
        
        for (BatterySnapshot snapshot : sorted) {
            ValidationResult<BatterySnapshot> result = validateForInsert(snapshot, previous);
            results.add(result);
            
            if (result.isAccepted()) {
                previous = result.getEntityToInsert();
            }
        }
        
        return results;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // CHARGING SESSION VALIDATION
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Validate a ChargingSession before insertion.
     */
    public ValidationResult<ChargingSession> validateForInsert(
            @NonNull ChargingSession session,
            @Nullable List<ChargingSession> existingSessions) {
        
        List<String> rejectionReasons = new ArrayList<>();
        List<String> corrections = new ArrayList<>();
        
        // === CONTRACT VALIDATION ===
        EntityContracts.ContractValidationResult contractResult = 
                EntityContracts.ChargingSessionContract.validate(session);
        
        if (!contractResult.isValid()) {
            return ValidationResult.reject(session, contractResult.getViolations());
        }
        
        // === OVERLAP CHECK ===
        if (existingSessions != null) {
            for (ChargingSession existing : existingSessions) {
                if (sessionsOverlap(session, existing)) {
                    // Check if it's essentially the same session
                    if (Math.abs(session.startTime - existing.startTime) < 60000) {  // Within 1 min
                        return ValidationResult.skip(session, 
                                "Session overlaps with existing session ID " + existing.id);
                    }
                    
                    // Different start times but overlap - might be valid
                    corrections.add("Session overlaps with existing session - verify data");
                }
            }
        }
        
        // === LEVEL CORRECTIONS ===
        ChargingSession corrected = null;
        
        // Clamp levels
        if (session.startLevel < 0 || session.startLevel > 100) {
            if (corrected == null) corrected = cloneSession(session);
            corrected.startLevel = Math.max(0, Math.min(100, session.startLevel));
            corrections.add("Clamped startLevel to valid range");
        }
        
        if (session.endLevel > 0 && (session.endLevel < 0 || session.endLevel > 100)) {
            if (corrected == null) corrected = cloneSession(session);
            corrected.endLevel = Math.max(0, Math.min(100, session.endLevel));
            corrections.add("Clamped endLevel to valid range");
        }
        
        // === RETURN RESULT ===
        if (!corrections.isEmpty()) {
            Log.w(TAG, "Session accepted with " + corrections.size() + " corrections");
            return ValidationResult.acceptWithCorrections(session, corrected, corrections);
        }
        
        return ValidationResult.accept(session);
    }
    
    /**
     * Check if two sessions overlap in time.
     */
    private boolean sessionsOverlap(ChargingSession a, ChargingSession b) {
        long aEnd = a.endTime > 0 ? a.endTime : System.currentTimeMillis();
        long bEnd = b.endTime > 0 ? b.endTime : System.currentTimeMillis();
        
        return a.startTime < bEnd && aEnd > b.startTime;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // DRAIN EVENT VALIDATION
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Validate a DrainEvent before insertion.
     */
    public ValidationResult<DrainEvent> validateForInsert(
            @NonNull DrainEvent event,
            @Nullable List<DrainEvent> existingEvents) {
        
        // === CONTRACT VALIDATION ===
        EntityContracts.ContractValidationResult contractResult = 
                EntityContracts.DrainEventContract.validate(event);
        
        if (!contractResult.isValid()) {
            return ValidationResult.reject(event, contractResult.getViolations());
        }
        
        // === DUPLICATE CHECK ===
        if (existingEvents != null) {
            for (DrainEvent existing : existingEvents) {
                // Same time period
                if (event.startTime == existing.startTime && event.endTime == existing.endTime) {
                    return ValidationResult.skip(event, "Duplicate drain event for same time period");
                }
            }
        }
        
        // === CORRECTIONS ===
        List<String> corrections = new ArrayList<>();
        DrainEvent corrected = null;
        
        // Clamp drain rate
        if (event.drainRatePerHour < 0) {
            if (corrected == null) corrected = cloneDrainEvent(event);
            corrected.drainRatePerHour = 0;
            corrections.add("Clamped negative drain rate to 0");
        }
        
        if (!corrections.isEmpty()) {
            return ValidationResult.acceptWithCorrections(event, corrected, corrections);
        }
        
        return ValidationResult.accept(event);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // THERMAL EVENT VALIDATION
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Validate a ThermalEvent before insertion.
     */
    public ValidationResult<ThermalEvent> validateForInsert(@NonNull ThermalEvent event) {
        // === CONTRACT VALIDATION ===
        EntityContracts.ContractValidationResult contractResult = 
                EntityContracts.ThermalEventContract.validate(event);
        
        if (!contractResult.isValid()) {
            return ValidationResult.reject(event, contractResult.getViolations());
        }
        
        return ValidationResult.accept(event);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // BATTERY HEALTH VALIDATION
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Validate a BatteryHealth record before insertion.
     */
    public ValidationResult<BatteryHealth> validateForInsert(
            @NonNull BatteryHealth health,
            @Nullable BatteryHealth lastHealth) {
        
        List<String> corrections = new ArrayList<>();
        
        // === CONTRACT VALIDATION ===
        EntityContracts.ContractValidationResult contractResult = 
                EntityContracts.BatteryHealthContract.validate(health);
        
        if (!contractResult.isValid()) {
            return ValidationResult.reject(health, contractResult.getViolations());
        }
        
        // === DUPLICATE CHECK ===
        if (lastHealth != null) {
            // Same health values within short time = skip
            if (health.timestamp - lastHealth.timestamp < 60000 &&  // Within 1 minute
                Math.abs(health.healthPercentage - lastHealth.healthPercentage) < 0.1) {
                return ValidationResult.skip(health, "Duplicate health reading");
            }
        }
        
        // === SANITY CHECK: Health shouldn't increase dramatically ===
        if (lastHealth != null && health.healthPercentage > lastHealth.healthPercentage + 5) {
            // Health jumped up significantly - suspicious
            corrections.add("Health increased by " + 
                    String.format("%.1f%%", health.healthPercentage - lastHealth.healthPercentage) + 
                    " - verify accuracy");
        }
        
        if (!corrections.isEmpty()) {
            return ValidationResult.acceptWithCorrections(health, health, corrections);
        }
        
        return ValidationResult.accept(health);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // AI ANALYSIS RESULT VALIDATION
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Validate an AIAnalysisResult before insertion.
     */
    public ValidationResult<AIAnalysisResult> validateForInsert(@NonNull AIAnalysisResult result) {
        // === CONTRACT VALIDATION ===
        EntityContracts.ContractValidationResult contractResult = 
                EntityContracts.AIAnalysisResultContract.validate(result);
        
        if (!contractResult.isValid()) {
            return ValidationResult.reject(result, contractResult.getViolations());
        }
        
        List<String> corrections = new ArrayList<>();
        AIAnalysisResult corrected = null;
        
        // === CONFIDENCE CLAMPING ===
        if (result.overallConfidenceScore > 1.0f) {
            if (corrected == null) corrected = cloneAIResult(result);
            corrected.overallConfidenceScore = 1.0f;
            corrections.add("Clamped confidence score to 1.0");
        }
        
        // === ANTI-HALLUCINATION ENFORCEMENT ===
        // If AI was blocked, ensure certain fields are null/empty
        if (!result.aiWasAllowedToRun && result.rootCause != null && 
            !result.rootCause.isEmpty() && !"INSUFFICIENT_DATA".equals(result.rootCauseCode)) {
            if (corrected == null) corrected = cloneAIResult(result);
            corrected.rootCause = "Analysis blocked: " + result.aiBlockedReason;
            corrected.rootCauseCode = "BLOCKED";
            corrections.add("Cleared AI-generated fields because AI was blocked");
        }
        
        if (!corrections.isEmpty()) {
            return ValidationResult.acceptWithCorrections(result, corrected, corrections);
        }
        
        return ValidationResult.accept(result);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // UPDATE VALIDATION
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Validate an update to a ChargingSession (e.g., setting endTime).
     */
    public ValidationResult<ChargingSession> validateForUpdate(
            @NonNull ChargingSession original,
            @NonNull ChargingSession updated) {
        
        List<String> rejectionReasons = new ArrayList<>();
        List<String> corrections = new ArrayList<>();
        
        // === IMMUTABLE FIELD CHECK ===
        // Once set, startTime shouldn't change
        if (original.startTime != updated.startTime) {
            rejectionReasons.add("startTime cannot be modified after creation");
            return ValidationResult.reject(updated, rejectionReasons);
        }
        
        // ID must match
        if (original.id != updated.id) {
            rejectionReasons.add("Cannot change entity ID");
            return ValidationResult.reject(updated, rejectionReasons);
        }
        
        // === CONTRACT VALIDATION ===
        EntityContracts.ContractValidationResult contractResult = 
                EntityContracts.ChargingSessionContract.validate(updated);
        
        if (!contractResult.isValid()) {
            return ValidationResult.reject(updated, contractResult.getViolations());
        }
        
        // === PROGRESSION CHECK ===
        // If completing a session, ensure end values make sense
        if (original.endTime <= 0 && updated.endTime > 0) {
            // Completing the session
            if (updated.endTime < updated.startTime) {
                rejectionReasons.add("endTime cannot be before startTime");
                return ValidationResult.reject(updated, rejectionReasons);
            }
            
            if (updated.endLevel < updated.startLevel) {
                // This is weird for a charging session
                corrections.add("endLevel < startLevel for charging session - unusual but allowed");
            }
        }
        
        if (!corrections.isEmpty()) {
            return ValidationResult.acceptWithCorrections(original, updated, corrections);
        }
        
        return new ValidationResult<>(true, original, updated, null, null, ValidationAction.UPDATE);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // HELPER METHODS - ENTITY CLONING (Essential fields only)
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Clone essential BatterySnapshot fields for correction.
     * Note: Full entity has many fields - only core validation fields copied here.
     */
    private BatterySnapshot cloneSnapshot(BatterySnapshot original) {
        BatterySnapshot clone = new BatterySnapshot();
        // Core fields
        clone.id = original.id;
        clone.timestamp = original.timestamp;
        clone.level = original.level;
        clone.voltage = original.voltage;
        clone.temperature = original.temperature;
        clone.status = original.status;
        clone.isCharging = original.isCharging;
        clone.screenOn = original.screenOn;
        clone.health = original.health;
        clone.plugged = original.plugged;
        clone.chargeCounter = original.chargeCounter;
        clone.currentNow = original.currentNow;
        clone.energyCounter = original.energyCounter;
        // Validation metadata
        clone.sequenceNumber = original.sequenceNumber;
        clone.elapsedRealtimeMs = original.elapsedRealtimeMs;
        clone.validationStatus = original.validationStatus;
        clone.qualityScore = original.qualityScore;
        clone.validationFlags = original.validationFlags;
        clone.validationNotes = original.validationNotes;
        clone.sensorDataChanged = original.sensorDataChanged;
        clone.sessionId = original.sessionId;
        clone.samplingContext = original.samplingContext;
        clone.samplingIntervalMs = original.samplingIntervalMs;
        clone.timeSincePreviousMs = original.timeSincePreviousMs;
        return clone;
    }
    
    /**
     * Clone essential ChargingSession fields for correction.
     */
    private ChargingSession cloneSession(ChargingSession original) {
        ChargingSession clone = new ChargingSession();
        // Core fields
        clone.id = original.id;
        clone.startTime = original.startTime;
        clone.endTime = original.endTime;
        clone.startLevel = original.startLevel;
        clone.endLevel = original.endLevel;
        clone.plugType = original.plugType;
        clone.chargingType = original.chargingType;
        clone.isFastCharging = original.isFastCharging;
        clone.chargedAmount = original.chargedAmount;
        clone.avgTemperature = original.avgTemperature;
        clone.maxTemperature = original.maxTemperature;
        clone.safetyStatus = original.safetyStatus;
        clone.safetyScore = original.safetyScore;
        return clone;
    }
    
    /**
     * Clone essential DrainEvent fields for correction.
     */
    private DrainEvent cloneDrainEvent(DrainEvent original) {
        DrainEvent clone = new DrainEvent();
        // Core fields
        clone.id = original.id;
        clone.startTime = original.startTime;
        clone.endTime = original.endTime;
        clone.startLevel = original.startLevel;
        clone.endLevel = original.endLevel;
        clone.drainRatePerHour = original.drainRatePerHour;
        clone.drainType = original.drainType;
        clone.drainAmount = original.drainAmount;
        clone.screenOn = original.screenOn;
        clone.isCharging = original.isCharging;
        clone.isIdle = original.isIdle;
        clone.avgTemperature = original.avgTemperature;
        clone.isAnomalous = original.isAnomalous;
        return clone;
    }
    
    /**
     * Clone essential AIAnalysisResult fields for correction.
     */
    private AIAnalysisResult cloneAIResult(AIAnalysisResult original) {
        AIAnalysisResult clone = new AIAnalysisResult();
        // Core fields
        clone.id = original.id;
        clone.timestamp = original.timestamp;
        clone.analysisType = original.analysisType;
        clone.overallBatteryScore = original.overallBatteryScore;
        clone.healthScore = original.healthScore;
        clone.severity = original.severity;
        clone.dataPointsAnalyzed = original.dataPointsAnalyzed;
        // AI readiness fields
        clone.aiWasAllowedToRun = original.aiWasAllowedToRun;
        clone.aiBlockedReason = original.aiBlockedReason;
        clone.aiReadinessScore = original.aiReadinessScore;
        // Root cause fields
        clone.rootCause = original.rootCause;
        clone.rootCauseCode = original.rootCauseCode;
        clone.rootCauseConfidence = original.rootCauseConfidence;
        // Confidence
        clone.overallConfidenceScore = original.overallConfidenceScore;
        clone.recommendations = original.recommendations;
        return clone;
    }
}
