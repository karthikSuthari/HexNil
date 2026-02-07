package com.example.hexnil.model;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Results of comparing before vs after for each pre-update issue.
 * Determines if each issue was fixed, improved, unchanged, or worsened.
 */
@Entity(tableName = "post_update_verifications", foreignKeys = {
        @ForeignKey(entity = UpdateSession.class, parentColumns = "id", childColumns = "sessionId", onDelete = ForeignKey.CASCADE),
        @ForeignKey(entity = PreUpdateIssue.class, parentColumns = "id", childColumns = "preUpdateIssueId", onDelete = ForeignKey.CASCADE)
}, indices = {
        @Index("sessionId"),
        @Index("preUpdateIssueId"),
        @Index("verificationStatus")
})
public class PostUpdateVerification {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long sessionId;
    public long preUpdateIssueId;

    // === Timing ===
    public long verifiedAt;

    // === Issue Reference ===
    public String issueUuid;
    public String issueType;
    public String issueTitle;

    // === Before State ===
    public Double preUpdateValue;
    public Integer preUpdateOccurrences;

    // === After State ===
    public Double postUpdateValue;
    public Integer postUpdateOccurrences;

    // === Change Analysis ===
    public Double absoluteChange;
    public Double percentChange;

    // === Verdict ===
    // fixed, improved, unchanged, worsened, inconclusive
    public String verificationStatus;
    public Double confidenceScore; // 0-1 confidence in this verdict

    // === Evidence ===
    public String postUpdateEvidenceJson; // JSON with post-update data samples
    public String comparisonDetails; // Detailed explanation of comparison

    // === Impact ===
    public boolean userNoticeable; // Would user notice this change?

    public PostUpdateVerification() {
    }

    /**
     * Create a verification for a pre-update issue.
     */
    public static PostUpdateVerification create(long sessionId, long preUpdateIssueId,
            String issueUuid, String issueType, String issueTitle) {
        PostUpdateVerification verification = new PostUpdateVerification();
        verification.sessionId = sessionId;
        verification.preUpdateIssueId = preUpdateIssueId;
        verification.verifiedAt = System.currentTimeMillis();
        verification.issueUuid = issueUuid;
        verification.issueType = issueType;
        verification.issueTitle = issueTitle;
        return verification;
    }

    /**
     * Set the before/after comparison data.
     */
    public void setComparison(Double preValue, Integer preOccurrences,
            Double postValue, Integer postOccurrences) {
        this.preUpdateValue = preValue;
        this.preUpdateOccurrences = preOccurrences;
        this.postUpdateValue = postValue;
        this.postUpdateOccurrences = postOccurrences;

        // Calculate changes
        if (preValue != null && postValue != null) {
            this.absoluteChange = postValue - preValue;
            if (preValue != 0) {
                this.percentChange = ((postValue - preValue) / Math.abs(preValue)) * 100;
            }
        }
    }

    /**
     * Determine the verdict based on the comparison, using metric-specific
     * thresholds to avoid false positives.
     *
     * @param higherIsBetter true if higher values are better (e.g., FPS, battery health)
     */
    public void determineVerdict(boolean higherIsBetter) {
        if (preUpdateValue == null || postUpdateValue == null) {
            // Fall back to occurrence-only check if we have occurrences
            if (preUpdateOccurrences != null && postUpdateOccurrences != null) {
                determineVerdictByOccurrences();
                return;
            }
            this.verificationStatus = "inconclusive";
            this.confidenceScore = 0.3;
            return;
        }

        // For occurrence-based issues (crashes, ANRs, etc.)
        if (preUpdateOccurrences != null && postUpdateOccurrences != null) {
            if (preUpdateOccurrences > 0 && postUpdateOccurrences == 0) {
                this.verificationStatus = "fixed";
                this.confidenceScore = 0.9;
                this.userNoticeable = true;
                return;
            }
            if (postUpdateOccurrences > preUpdateOccurrences * 2) {
                this.verificationStatus = "worsened";
                this.confidenceScore = 0.85;
                this.userNoticeable = true;
                return;
            }
        }

        double changePercent = percentChange != null ? percentChange : 0;

        // Use metric-specific thresholds to reduce false positives.
        // Battery drain and thermal metrics have wider natural variance.
        double threshold = getThresholdForIssueType();

        if (higherIsBetter) {
            if (changePercent >= threshold) {
                this.verificationStatus = "improved";
                this.confidenceScore = calculateConfidence(changePercent, threshold);
            } else if (changePercent <= -threshold) {
                this.verificationStatus = "worsened";
                this.confidenceScore = calculateConfidence(changePercent, threshold);
            } else {
                this.verificationStatus = "unchanged";
                this.confidenceScore = 0.7;
            }
        } else {
            // Lower is better (e.g., drain rate, jank, temperature)
            if (changePercent <= -threshold) {
                this.verificationStatus = "improved";
                this.confidenceScore = calculateConfidence(changePercent, threshold);
            } else if (changePercent >= threshold) {
                this.verificationStatus = "worsened";
                this.confidenceScore = calculateConfidence(changePercent, threshold);
            } else {
                this.verificationStatus = "unchanged";
                this.confidenceScore = 0.7;
            }
        }

        // User noticeable requires exceeding 1.5x the threshold
        this.userNoticeable = Math.abs(changePercent) >= (threshold * 1.5);
    }

    /**
     * Returns metric-specific threshold percentages to avoid false positives.
     * Battery and thermal metrics have wider natural variance than crash counts.
     */
    private double getThresholdForIssueType() {
        if (issueType == null) return 25.0;

        switch (issueType) {
            case "crash":
            case "anr":
                return 15.0; // Crashes are discrete events — smaller swings matter
            case "battery_drain":
            case "idle_drain":
                return 30.0; // Battery drain varies with usage patterns
            case "thermal":
                return 25.0; // Thermal readings have moderate variance
            case "performance":
            case "jank":
                return 20.0; // Performance metrics are moderately stable
            default:
                return 25.0; // Conservative default
        }
    }

    /**
     * Calculate confidence based on how far the change exceeds the threshold.
     * Larger deviations yield higher confidence.
     */
    private double calculateConfidence(double changePercent, double threshold) {
        double ratio = Math.abs(changePercent) / threshold;
        // Clamp between 0.6 and 0.95
        return Math.min(0.95, Math.max(0.6, 0.6 + (ratio - 1.0) * 0.15));
    }

    /**
     * Determine verdict when only occurrence data is available (no value metrics).
     */
    private void determineVerdictByOccurrences() {
        if (preUpdateOccurrences > 0 && postUpdateOccurrences == 0) {
            this.verificationStatus = "fixed";
            this.confidenceScore = 0.9;
            this.userNoticeable = true;
        } else if (postUpdateOccurrences < preUpdateOccurrences) {
            this.verificationStatus = "improved";
            this.confidenceScore = 0.75;
            this.userNoticeable = (preUpdateOccurrences - postUpdateOccurrences) >= 3;
        } else if (postUpdateOccurrences > preUpdateOccurrences * 2) {
            this.verificationStatus = "worsened";
            this.confidenceScore = 0.85;
            this.userNoticeable = true;
        } else if (postUpdateOccurrences > preUpdateOccurrences) {
            this.verificationStatus = "worsened";
            this.confidenceScore = 0.65;
            this.userNoticeable = (postUpdateOccurrences - preUpdateOccurrences) >= 3;
        } else {
            this.verificationStatus = "unchanged";
            this.confidenceScore = 0.7;
            this.userNoticeable = false;
        }
    }

    /**
     * Set evidence and explanation.
     */
    public void setEvidence(String evidenceJson, String details) {
        this.postUpdateEvidenceJson = evidenceJson;
        this.comparisonDetails = details;
    }
}
