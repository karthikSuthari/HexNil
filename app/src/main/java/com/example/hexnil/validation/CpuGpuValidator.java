package com.example.hexnil.validation;

import com.example.hexnil.model.CpuGpuLoadSample;

import java.util.ArrayList;
import java.util.List;

/**
 * OEM-Grade CPU/GPU Sample Validator
 * 
 * Zero-tolerance validation for CPU/GPU load samples before database insertion.
 * 
 * Validation Rules:
 * 1. Active foreground app must have CPU > 0 if system CPU is busy
 * 2. GPU drops from high to zero instantly are flagged
 * 3. Duplicate timestamps are rejected
 * 4. Frozen values (identical across N samples) are rejected
 * 5. CPU/GPU values > 100 are normalized with warning
 * 
 * All violations are logged with explicit notes for auditability.
 */
public class CpuGpuValidator {

    private static final String TAG = "CpuGpuValidator";

    /** Maximum consecutive frozen samples before rejection */
    private static final int MAX_FROZEN_SAMPLES = 5;

    // =========================================================================
    // STATE
    // =========================================================================

    private CpuGpuLoadSample previousSample;
    private int consecutiveFrozenCount = 0;

    // =========================================================================
    // RESULT CLASS
    // =========================================================================

    /**
     * Validation result with detailed breakdown.
     */
    public static class ValidationResult {
        /** Whether sample passed validation */
        public boolean isValid = true;

        /** Status: "valid", "warning", "invalid" */
        public String status = "valid";

        /** Combined notes about violations/warnings */
        public String notes = "";

        /** List of individual violations */
        public List<String> violations = new ArrayList<>();

        /** Whether sample was modified (e.g., normalized) */
        public boolean wasModified = false;

        public ValidationResult() {
        }

        public void addViolation(String violation) {
            violations.add(violation);
        }

        public void buildNotes() {
            notes = String.join("; ", violations);
        }

        public void markAsWarning() {
            if (isValid) {
                status = "warning";
            }
        }

        public void markAsInvalid(String reason) {
            isValid = false;
            status = "invalid";
            addViolation(reason);
        }
    }

    // =========================================================================
    // VALIDATION
    // =========================================================================

    /**
     * Validate a CPU/GPU sample before insertion.
     * 
     * This method may modify the sample (e.g., normalize values > 100)
     * and returns a result indicating validity and any issues.
     * 
     * @param sample The sample to validate (may be modified)
     * @return ValidationResult with status and notes
     */
    public ValidationResult validate(CpuGpuLoadSample sample) {
        ValidationResult result = new ValidationResult();

        if (sample == null) {
            result.markAsInvalid("Null sample");
            return result;
        }

        // Rule 1: Active foreground app must have detectable CPU
        validateActiveForegroundCpu(sample, result);

        // Rule 2: GPU consistency check
        validateGpuConsistency(sample, result);

        // Rule 3: Duplicate timestamp detection
        validateTimestamp(sample, result);

        // Rule 4: Frozen values detection
        validateNotFrozen(sample, result);

        // Rule 5: Range normalization
        normalizeRanges(sample, result);

        // Rule 6: Stress category validation
        validateStressCategory(sample, result);

        // Build final notes
        result.buildNotes();

        // Apply result to sample
        sample.validationStatus = result.status;
        sample.validationNotes = result.notes;

        // Update tracking state
        previousSample = sample;

        return result;
    }

    /**
     * Reset validation state (call on session reset).
     */
    public void reset() {
        previousSample = null;
        consecutiveFrozenCount = 0;
    }

    // =========================================================================
    // VALIDATION RULES
    // =========================================================================

    /**
     * Rule 1: Active foreground apps should have measurable CPU if system is busy.
     */
    private void validateActiveForegroundCpu(CpuGpuLoadSample sample, ValidationResult result) {
        if (!sample.isActive)
            return;
        if (!"foreground".equals(sample.sourceType))
            return;

        // If app is marked as active foreground but has 0% CPU while system is busy
        if (sample.appCpuPercent == 0 && sample.systemCpuPercent > 10) {
            result.addViolation("Active foreground app reports 0% CPU while system is at " +
                    sample.systemCpuPercent + "%");
            result.markAsWarning();
        }
    }

    /**
     * Rule 2: GPU shouldn't drop from high to zero instantly.
     */
    private void validateGpuConsistency(CpuGpuLoadSample sample, ValidationResult result) {
        if (!sample.isGpuAvailable)
            return;
        if (sample.gpuPercent < 0)
            return;
        if (previousSample == null)
            return;

        // GPU dropping from >30% to 0% in one sample is suspicious
        if (sample.gpuPercent == 0 && previousSample.gpuPercent > 30) {
            result.addViolation("GPU dropped from " + previousSample.gpuPercent +
                    "% to 0% (instant drop suspicious)");
            result.markAsWarning();
        }
    }

    /**
     * Rule 3: Reject duplicate timestamps.
     */
    private void validateTimestamp(CpuGpuLoadSample sample, ValidationResult result) {
        if (previousSample == null)
            return;

        if (sample.timestamp == previousSample.timestamp) {
            result.markAsInvalid("Duplicate timestamp: " + sample.timestamp);
        }

        // Also check for timestamps moving backwards
        if (sample.timestamp < previousSample.timestamp) {
            result.addViolation("Timestamp moved backwards: " + sample.timestamp +
                    " < " + previousSample.timestamp);
            result.markAsWarning();
        }
    }

    /**
     * Rule 4: Detect frozen/stuck sensors (identical values over N samples).
     */
    private void validateNotFrozen(CpuGpuLoadSample sample, ValidationResult result) {
        if (previousSample == null) {
            consecutiveFrozenCount = 0;
            return;
        }

        boolean isFrozen = isSampleFrozen(sample, previousSample);

        if (isFrozen) {
            consecutiveFrozenCount++;
            if (consecutiveFrozenCount >= MAX_FROZEN_SAMPLES) {
                result.markAsInvalid("Values frozen for " + consecutiveFrozenCount +
                        " consecutive samples (sensor stuck?)");
            } else if (consecutiveFrozenCount >= 3) {
                result.addViolation("Values unchanged for " + consecutiveFrozenCount + " samples");
                result.markAsWarning();
            }
        } else {
            consecutiveFrozenCount = 0;
        }
    }

    /**
     * Check if a sample has identical key values to its predecessor.
     */
    private boolean isSampleFrozen(CpuGpuLoadSample current, CpuGpuLoadSample previous) {
        if (previous == null)
            return false;

        return current.systemCpuPercent == previous.systemCpuPercent &&
                current.gpuPercent == previous.gpuPercent &&
                Math.abs(current.temperature - previous.temperature) < 0.1f &&
                current.batteryLevel == previous.batteryLevel &&
                current.load1m == previous.load1m;
    }

    /**
     * Rule 5: Normalize values outside valid range.
     */
    private void normalizeRanges(CpuGpuLoadSample sample, ValidationResult result) {
        // CPU normalization
        if (sample.systemCpuPercent > 100) {
            result.addViolation("System CPU normalized from " + sample.systemCpuPercent + " to 100");
            sample.systemCpuPercent = 100;
            result.wasModified = true;
            result.markAsWarning();
        }
        if (sample.systemCpuPercent < -1) {
            sample.systemCpuPercent = -1;
            result.wasModified = true;
        }

        if (sample.appCpuPercent > 100) {
            result.addViolation("App CPU normalized from " + sample.appCpuPercent + " to 100");
            sample.appCpuPercent = 100;
            result.wasModified = true;
            result.markAsWarning();
        }

        // GPU normalization
        if (sample.gpuPercent > 100) {
            result.addViolation("GPU normalized from " + sample.gpuPercent + " to 100");
            sample.gpuPercent = 100;
            result.wasModified = true;
            result.markAsWarning();
        }

        // Stress score normalization
        if (sample.stressScore < 0) {
            sample.stressScore = 0;
            result.wasModified = true;
        }
        if (sample.stressScore > 100) {
            sample.stressScore = 100;
            result.wasModified = true;
        }
    }

    /**
     * Rule 6: Validate stress category matches score.
     */
    private void validateStressCategory(CpuGpuLoadSample sample, ValidationResult result) {
        String expected = categorizeScore(sample.stressScore);

        if (sample.stressCategory == null) {
            sample.stressCategory = expected;
            result.wasModified = true;
        } else if (!expected.equals(sample.stressCategory)) {
            result.addViolation("Stress category '" + sample.stressCategory +
                    "' doesn't match score " + sample.stressScore +
                    " (expected '" + expected + "')");
            sample.stressCategory = expected;
            result.wasModified = true;
            result.markAsWarning();
        }
    }

    /**
     * Convert stress score to category.
     */
    private String categorizeScore(float score) {
        if (score <= 25)
            return "LOW";
        if (score <= 50)
            return "MEDIUM";
        if (score <= 75)
            return "HIGH";
        return "CRITICAL";
    }

    // =========================================================================
    // STATIC VALIDATION UTILITIES
    // =========================================================================

    /**
     * Quick validation check without state tracking.
     * 
     * @param sample Sample to check
     * @return true if sample passes basic validation
     */
    public static boolean quickValidate(CpuGpuLoadSample sample) {
        if (sample == null)
            return false;
        if (sample.timestamp <= 0)
            return false;
        if (sample.systemCpuPercent < -1 || sample.systemCpuPercent > 100)
            return false;
        if (sample.gpuPercent < -1 || sample.gpuPercent > 100)
            return false;
        return true;
    }

    /**
     * Check if a sample represents meaningful activity (not idle).
     * 
     * @param sample Sample to check
     * @return true if sample shows meaningful system activity
     */
    public static boolean hasMeaningfulActivity(CpuGpuLoadSample sample) {
        if (sample == null)
            return false;

        return sample.systemCpuPercent > 5 ||
                sample.gpuPercent > 5 ||
                sample.load1m > 1.0f ||
                sample.isThrottled;
    }
}
