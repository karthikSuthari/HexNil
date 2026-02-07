package com.example.hexnil.model;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Statistical significance test results for OTA pre/post comparisons.
 * Stores Welch's t-test, Mann-Whitney U, or chi-squared results
 * to quantify whether observed metric changes are statistically
 * and practically meaningful after an update.
 */
@Entity(tableName = "comparison_statistics", foreignKeys = {
        @ForeignKey(entity = PostUpdateVerification.class,
                parentColumns = "id",
                childColumns = "verificationId",
                onDelete = ForeignKey.CASCADE),
        @ForeignKey(entity = UpdateSession.class,
                parentColumns = "id",
                childColumns = "sessionId",
                onDelete = ForeignKey.CASCADE)
}, indices = {
        @Index("verificationId"),
        @Index("sessionId"),
        @Index("metricName")
})
public class ComparisonStatistics {

    @PrimaryKey(autoGenerate = true)
    public long id;

    /** FK to post_update_verifications */
    public long verificationId;

    /** FK to update_sessions (denormalized for convenience) */
    public long sessionId;

    /** Timestamp when this statistical test was computed */
    public long computedAt;

    /** Name of the metric being compared (e.g., "drain_rate", "avg_temperature") */
    public String metricName;

    // === Pre-Update Distribution ===

    public Double preUpdateMean;
    public Double preUpdateStdDev;
    /** Number of pre-update samples */
    public int preUpdateN;

    // === Post-Update Distribution ===

    public Double postUpdateMean;
    public Double postUpdateStdDev;
    /** Number of post-update samples */
    public int postUpdateN;

    // === Test Results ===

    public Double tStatistic;
    public Double pValue;

    /** Cohen's d effect size */
    public Double cohenD;

    /** Boolean flag: 1 if p < 0.05 */
    public int statisticallySignificant;

    /** Boolean flag: 1 if |d| > 0.5 */
    public int practicallySignificant;

    /** Type of test performed: "welch_t_test", "mann_whitney", "chi_squared" */
    public String testType;

    /** Free-form notes or warnings about the test (e.g., low sample size) */
    public String notes;

    public ComparisonStatistics() {
    }
}
