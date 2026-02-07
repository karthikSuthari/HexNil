package com.example.hexnil.model;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Master table tracking each system update event.
 * Captures timing, version context, detection method, and verification results.
 * 
 * Status flow: pending -> baseline_locked -> installed_monitoring -> verified
 */
@Entity(tableName = "update_sessions", indices = {
        @Index(value = "sessionUuid", unique = true),
        @Index("status"),
        @Index("detectedAt"),
        @Index("currentBuildFingerprint")
})
public class UpdateSession {

    @PrimaryKey(autoGenerate = true)
    public long id;

    // Unique identifier for this update session
    public String sessionUuid;

    // === Timing ===
    public long detectedAt; // When update was first detected
    public Long preSnapshotLockedAt; // When pre-update baseline was frozen
    public Long updateInstalledAt; // When update actually installed
    public Long verificationCompletedAt; // When post-update analysis finished

    // === Version Context ===
    public String previousBuildFingerprint;
    public String currentBuildFingerprint;
    public String previousAndroidVersion;
    public String currentAndroidVersion;
    public String previousSecurityPatch;
    public String currentSecurityPatch;
    public String previousModemVersion;
    public String currentModemVersion;

    // === Session State ===
    // pending, baseline_locked, installed_monitoring, analyzing, verified
    public String status;

    // system_ota, security_patch, firmware_only, unknown
    public String updateType;

    // === Detection Method ===
    // build_change, update_agent, manual, boot_detection
    public String detectionMethod;

    // === Pre-Update Intelligence ===
    public int knownIssuesCount;
    public Double preUpdateDataQuality; // 0-1 confidence in baseline data
    public Double preUpdateObservationHours;
    public Integer preUpdateSnapshotCount;

    // === Post-Update Results ===
    public int issuesFixed;
    public int issuesUnchanged;
    public int newRegressionsDetected;

    // === Overall Verdict ===
    public Double updateImpactScore; // -100 to +100 (negative = harmful)
    // improved, unchanged, regressed, mixed
    public String overallVerdict;

    // === Metadata ===
    public boolean userWasNotified;
    public boolean userDismissed;
    public String notes;

    // ML Infrastructure
    public String deviceFingerprint;             // Device SKU fingerprint for fleet analysis
    public Integer warmupPeriodExcluded;          // 1 if first 48hr post-update data was excluded
    public Integer statisticalSignificanceTested; // 1 if statistical tests were run
    public String concurrentAppUpdatesJson;       // JSON list of app updates during same period
    public String contaminationFlags;             // Comma-separated contamination flags
    public Long mlImpactPredictionId;             // FK to ml_predictions for predicted impact

    public UpdateSession() {
    }

    /**
     * Create a new pending update session.
     */
    public static UpdateSession createPending(String sessionUuid, String currentFingerprint,
            String currentAndroidVersion, String currentSecurityPatch,
            String currentModemVersion, String detectionMethod) {
        UpdateSession session = new UpdateSession();
        session.sessionUuid = sessionUuid;
        session.detectedAt = System.currentTimeMillis();
        session.previousBuildFingerprint = currentFingerprint;
        session.previousAndroidVersion = currentAndroidVersion;
        session.previousSecurityPatch = currentSecurityPatch;
        session.previousModemVersion = currentModemVersion;
        session.status = "pending";
        session.detectionMethod = detectionMethod;
        session.knownIssuesCount = 0;
        session.issuesFixed = 0;
        session.issuesUnchanged = 0;
        session.newRegressionsDetected = 0;
        session.userWasNotified = false;
        session.userDismissed = false;
        return session;
    }

    /**
     * Mark the baseline as locked.
     */
    public void lockBaseline(int issuesCount, double dataQuality, double observationHours, int snapshotCount) {
        this.preSnapshotLockedAt = System.currentTimeMillis();
        this.knownIssuesCount = issuesCount;
        this.preUpdateDataQuality = dataQuality;
        this.preUpdateObservationHours = observationHours;
        this.preUpdateSnapshotCount = snapshotCount;
        this.status = "baseline_locked";
    }

    /**
     * Mark the update as installed.
     */
    public void markInstalled(String newFingerprint, String newAndroidVersion,
            String newSecurityPatch, String newModemVersion) {
        this.updateInstalledAt = System.currentTimeMillis();
        this.currentBuildFingerprint = newFingerprint;
        this.currentAndroidVersion = newAndroidVersion;
        this.currentSecurityPatch = newSecurityPatch;
        this.currentModemVersion = newModemVersion;
        this.status = "installed_monitoring";

        // Determine update type
        if (newSecurityPatch != null && !newSecurityPatch.equals(this.previousSecurityPatch)) {
            if (newAndroidVersion != null && !newAndroidVersion.equals(this.previousAndroidVersion)) {
                this.updateType = "system_ota";
            } else {
                this.updateType = "security_patch";
            }
        } else if (newModemVersion != null && !newModemVersion.equals(this.previousModemVersion)) {
            this.updateType = "firmware_only";
        } else {
            this.updateType = "unknown";
        }
    }

    /**
     * Mark verification as complete with final results.
     */
    public void completeVerification(int fixed, int unchanged, int regressions,
            double impactScore, String verdict) {
        this.verificationCompletedAt = System.currentTimeMillis();
        this.issuesFixed = fixed;
        this.issuesUnchanged = unchanged;
        this.newRegressionsDetected = regressions;
        this.updateImpactScore = impactScore;
        this.overallVerdict = verdict;
        this.status = "verified";
    }
}
