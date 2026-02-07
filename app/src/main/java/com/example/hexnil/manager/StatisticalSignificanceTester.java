package com.example.hexnil.manager;

import android.content.Context;
import android.util.Log;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.model.ComparisonStatistics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Statistical significance testing engine for OTA pre/post comparison.
 *
 * <p>Performs Welch's t-test on paired pre-update and post-update metric
 * distributions, computes Cohen's d effect size, and persists the results
 * as {@link ComparisonStatistics} rows.  Downstream consumers such as
 * {@link com.example.hexnil.worker.PostUpdateVerificationWorker} use this
 * to determine whether observed changes after a system update are
 * statistically and practically meaningful.
 *
 * <p>Key capabilities:
 * <ul>
 *   <li>Welch's t-test (unequal variances, unequal sample sizes)</li>
 *   <li>Welch-Satterthwaite degrees of freedom approximation</li>
 *   <li>Two-tailed p-value approximation via regularized incomplete beta function</li>
 *   <li>Cohen's d effect size with pooled standard deviation</li>
 *   <li>Session-level result summarization</li>
 * </ul>
 */
public class StatisticalSignificanceTester {

    private static final String TAG = "StatSigTester";

    // =========================================================================
    // CONSTANTS
    // =========================================================================

    /** Significance threshold for two-tailed tests (alpha = 0.05). */
    public static final double P_VALUE_THRESHOLD = 0.05;

    /** Cohen's d threshold for a small effect. */
    public static final double COHEN_D_SMALL = 0.2;

    /** Cohen's d threshold for a medium effect. */
    public static final double COHEN_D_MEDIUM = 0.5;

    /** Cohen's d threshold for a large effect. */
    public static final double COHEN_D_LARGE = 0.8;

    /** Minimum sample size required for a reliable test. */
    public static final int MIN_SAMPLE_SIZE = 5;

    /** Maximum iterations for the continued-fraction beta evaluation. */
    private static final int BETA_MAX_ITERATIONS = 200;

    /** Convergence tolerance for the continued-fraction beta evaluation. */
    private static final double BETA_EPSILON = 1e-10;

    private final Context context;
    private final HexNilDatabase database;

    public StatisticalSignificanceTester(Context context, HexNilDatabase database) {
        this.context = context.getApplicationContext();
        this.database = database;
    }

    // =========================================================================
    // PUBLIC API -- WELCH'S T-TEST
    // =========================================================================

    /**
     * Run a Welch's t-test comparing pre-update and post-update sample arrays
     * for the given metric, persist the result, and return the populated
     * {@link ComparisonStatistics} entity.
     *
     * <p>The test is two-tailed: it detects changes in either direction.
     * Both statistical significance (p-value) and practical significance
     * (Cohen's d) are evaluated.
     *
     * @param verificationId FK to post_update_verifications
     * @param sessionId      FK to update_sessions
     * @param metricName     human-readable metric identifier (e.g. "drain_rate")
     * @param preValues      raw pre-update sample values
     * @param postValues     raw post-update sample values
     * @return the persisted ComparisonStatistics, or {@code null} on error
     */
    public ComparisonStatistics runWelchTTest(long verificationId,
                                              long sessionId,
                                              String metricName,
                                              double[] preValues,
                                              double[] postValues) {
        if (preValues == null || postValues == null) {
            Log.w(TAG, "runWelchTTest: null input arrays for " + metricName);
            return null;
        }

        int n1 = preValues.length;
        int n2 = postValues.length;

        if (n1 < 2 || n2 < 2) {
            Log.w(TAG, "runWelchTTest: insufficient samples for " + metricName
                    + " (pre=" + n1 + ", post=" + n2 + ")");
            return null;
        }

        try {
            // Compute descriptive statistics
            double mean1 = computeMean(preValues);
            double mean2 = computeMean(postValues);
            double stdDev1 = computeStdDev(preValues, mean1);
            double stdDev2 = computeStdDev(postValues, mean2);

            // Variances
            double var1 = stdDev1 * stdDev1;
            double var2 = stdDev2 * stdDev2;

            // Welch's t-statistic:  t = (mean1 - mean2) / sqrt(s1^2/n1 + s2^2/n2)
            double sePart = (var1 / n1) + (var2 / n2);
            double se = Math.sqrt(sePart);

            double tStatistic;
            if (se < 1e-15) {
                // Both groups have zero variance and identical means
                tStatistic = 0.0;
            } else {
                tStatistic = (mean1 - mean2) / se;
            }

            // Welch-Satterthwaite degrees of freedom:
            // df = (s1^2/n1 + s2^2/n2)^2 / ((s1^2/n1)^2/(n1-1) + (s2^2/n2)^2/(n2-1))
            double df;
            double term1 = var1 / n1;
            double term2 = var2 / n2;
            double numerator = (term1 + term2) * (term1 + term2);
            double denominator = (term1 * term1) / (n1 - 1) + (term2 * term2) / (n2 - 1);

            if (denominator < 1e-15) {
                df = n1 + n2 - 2;
            } else {
                df = numerator / denominator;
            }

            // Two-tailed p-value from the t-distribution
            double pValue = approximatePValue(tStatistic, df);

            // Effect size (Cohen's d)
            double cohenD = computeCohenD(mean1, mean2, stdDev1, stdDev2);

            // Build and populate the entity
            ComparisonStatistics stats = new ComparisonStatistics();
            stats.verificationId = verificationId;
            stats.sessionId = sessionId;
            stats.computedAt = System.currentTimeMillis();
            stats.metricName = metricName;
            stats.preUpdateMean = mean1;
            stats.preUpdateStdDev = stdDev1;
            stats.preUpdateN = n1;
            stats.postUpdateMean = mean2;
            stats.postUpdateStdDev = stdDev2;
            stats.postUpdateN = n2;
            stats.tStatistic = tStatistic;
            stats.pValue = pValue;
            stats.cohenD = cohenD;
            stats.statisticallySignificant = (pValue < P_VALUE_THRESHOLD) ? 1 : 0;
            stats.practicallySignificant = (Math.abs(cohenD) >= COHEN_D_MEDIUM) ? 1 : 0;
            stats.testType = "welch_t_test";

            // Build notes with warnings and interpretive context
            StringBuilder notes = new StringBuilder();
            if (n1 < MIN_SAMPLE_SIZE || n2 < MIN_SAMPLE_SIZE) {
                notes.append("WARNING: Low sample size (pre=").append(n1)
                        .append(", post=").append(n2)
                        .append(", min=").append(MIN_SAMPLE_SIZE).append("). ");
            }
            notes.append("Effect size: ").append(getEffectSizeLabel(cohenD));
            notes.append(String.format(Locale.US, " (d=%.3f)", cohenD));
            notes.append(String.format(Locale.US, ", t(%.1f)=%.3f", df, tStatistic));
            notes.append(String.format(Locale.US, ", p=%.4f", pValue));
            stats.notes = notes.toString();

            // Persist
            long id = database.comparisonStatisticsDao().insert(stats);
            stats.id = id;

            Log.d(TAG, "Welch t-test for " + metricName + ": t=" +
                    String.format(Locale.US, "%.3f", tStatistic) +
                    ", p=" + String.format(Locale.US, "%.4f", pValue) +
                    ", d=" + String.format(Locale.US, "%.3f", cohenD) +
                    " [" + (stats.statisticallySignificant == 1 ? "SIG" : "n.s.") + "]");

            return stats;

        } catch (Exception e) {
            Log.e(TAG, "Error running Welch t-test for " + metricName, e);
            return null;
        }
    }

    /**
     * Convenience overload that accepts {@code List<Float>} inputs and
     * delegates to {@link #runWelchTTest(long, long, String, double[], double[])}.
     */
    public ComparisonStatistics runWelchTTest(long verificationId,
                                              long sessionId,
                                              String metricName,
                                              List<Float> preValues,
                                              List<Float> postValues) {
        double[] pre = toDoubleArray(preValues);
        double[] post = toDoubleArray(postValues);
        if (pre == null || post == null) {
            Log.w(TAG, "runWelchTTest(List): null/empty input for " + metricName);
            return null;
        }
        return runWelchTTest(verificationId, sessionId, metricName, pre, post);
    }

    // =========================================================================
    // DESCRIPTIVE STATISTICS
    // =========================================================================

    /**
     * Compute the arithmetic mean of the given values.
     *
     * @param values non-null array with at least one element
     * @return the mean
     */
    public double computeMean(double[] values) {
        if (values == null || values.length == 0) {
            return 0.0;
        }
        double sum = 0.0;
        for (double v : values) {
            sum += v;
        }
        return sum / values.length;
    }

    /**
     * Compute the sample standard deviation using Bessel's correction (n-1
     * denominator).
     *
     * @param values array of sample values
     * @param mean   precomputed mean of the values
     * @return the sample standard deviation, or 0 if fewer than 2 values
     */
    public double computeStdDev(double[] values, double mean) {
        if (values == null || values.length < 2) {
            return 0.0;
        }
        double sumSquaredDiff = 0.0;
        for (double v : values) {
            double diff = v - mean;
            sumSquaredDiff += diff * diff;
        }
        return Math.sqrt(sumSquaredDiff / (values.length - 1));
    }

    /**
     * Compute Cohen's d effect size using pooled standard deviation.
     *
     * <p>Formula: {@code d = (mean1 - mean2) / sqrt((s1^2 + s2^2) / 2)}
     *
     * @return Cohen's d; positive means group 1 has a higher mean
     */
    public double computeCohenD(double mean1, double mean2,
                                double stdDev1, double stdDev2) {
        double pooledVariance = (stdDev1 * stdDev1 + stdDev2 * stdDev2) / 2.0;
        double pooledStdDev = Math.sqrt(pooledVariance);
        if (pooledStdDev < 1e-15) {
            return 0.0;
        }
        return (mean1 - mean2) / pooledStdDev;
    }

    // =========================================================================
    // P-VALUE APPROXIMATION
    // =========================================================================

    /**
     * Compute the two-tailed p-value for a given t-statistic and degrees of
     * freedom using Student's t-distribution.
     *
     * <p>For large df (&gt;30) a normal approximation is used for speed and
     * numerical stability.  For smaller df the regularized incomplete beta
     * function is used.
     *
     * @param t  the t-statistic (may be negative)
     * @param df the degrees of freedom (Welch-Satterthwaite)
     * @return the two-tailed p-value in [0, 1]
     */
    public double approximatePValue(double t, double df) {
        if (Double.isNaN(t) || Double.isNaN(df) || df <= 0) {
            return 1.0;
        }

        double absT = Math.abs(t);

        if (df > 30) {
            // Normal approximation (valid for large df)
            // P(|Z| > |t|) = 2 * Phi(-|t|) where Phi is the standard normal CDF
            double pValue = 2.0 * normalCdfComplement(absT);
            return Math.max(0.0, Math.min(1.0, pValue));
        }

        // For smaller df, use the relationship between the t-distribution CDF
        // and the regularized incomplete beta function:
        //   P(T <= t) for t > 0 = 1 - 0.5 * I_x(df/2, 1/2)
        //   where x = df / (df + t^2)
        // Two-tailed p-value = 2 * P(T > |t|) = I_x(df/2, 1/2)
        double x = df / (df + absT * absT);
        double pValue = betaRegularized(x, df / 2.0, 0.5);
        return Math.max(0.0, Math.min(1.0, pValue));
    }

    // =========================================================================
    // EFFECT SIZE INTERPRETATION
    // =========================================================================

    /**
     * Return a human-readable label for the magnitude of a Cohen's d value.
     *
     * @param cohenD the effect size (sign is ignored)
     * @return one of "negligible", "small", "medium", or "large"
     */
    public String getEffectSizeLabel(double cohenD) {
        double abs = Math.abs(cohenD);
        if (abs >= COHEN_D_LARGE) {
            return "large";
        } else if (abs >= COHEN_D_MEDIUM) {
            return "medium";
        } else if (abs >= COHEN_D_SMALL) {
            return "small";
        } else {
            return "negligible";
        }
    }

    // =========================================================================
    // SIGNIFICANCE QUERIES
    // =========================================================================

    /**
     * Determine whether a test result is statistically significant <b>and</b>
     * backed by sufficient sample sizes.
     *
     * @return {@code true} if pValue &lt; {@link #P_VALUE_THRESHOLD} and both
     *         sample sizes meet {@link #MIN_SAMPLE_SIZE}
     */
    public boolean isStatisticallySignificant(ComparisonStatistics stats) {
        if (stats == null || stats.pValue == null) {
            return false;
        }
        return stats.pValue < P_VALUE_THRESHOLD
                && stats.preUpdateN >= MIN_SAMPLE_SIZE
                && stats.postUpdateN >= MIN_SAMPLE_SIZE;
    }

    /**
     * Determine whether a test result is practically significant based on
     * Cohen's d.
     *
     * @return {@code true} if |cohenD| &ge; {@link #COHEN_D_MEDIUM}
     */
    public boolean isPracticallySignificant(ComparisonStatistics stats) {
        if (stats == null || stats.cohenD == null) {
            return false;
        }
        return Math.abs(stats.cohenD) >= COHEN_D_MEDIUM;
    }

    // =========================================================================
    // SESSION SUMMARY
    // =========================================================================

    /**
     * Retrieve all {@link ComparisonStatistics} for a session and produce a
     * human-readable summary.
     *
     * @param sessionId FK to update_sessions
     * @return a {@link TestSummary} object, never null
     */
    public TestSummary summarizeResults(long sessionId) {
        TestSummary summary = new TestSummary();
        try {
            List<ComparisonStatistics> allStats =
                    database.comparisonStatisticsDao().getBySessionId(sessionId);

            if (allStats == null || allStats.isEmpty()) {
                Log.d(TAG, "No comparison statistics found for session " + sessionId);
                return summary;
            }

            summary.totalTests = allStats.size();

            for (ComparisonStatistics stat : allStats) {
                if (stat.statisticallySignificant == 1) {
                    summary.significantCount++;
                }
                if (stat.practicallySignificant == 1) {
                    summary.practicallySignificantCount++;
                }

                // Build human-readable metric summary line
                String line = formatMetricSummary(stat);
                summary.metricsSummary.add(line);
            }

            Log.d(TAG, "Session " + sessionId + " summary: "
                    + summary.totalTests + " tests, "
                    + summary.significantCount + " statistically significant, "
                    + summary.practicallySignificantCount + " practically significant");

        } catch (Exception e) {
            Log.e(TAG, "Error summarizing results for session " + sessionId, e);
        }
        return summary;
    }

    // =========================================================================
    // INNER CLASS -- TEST SUMMARY
    // =========================================================================

    /**
     * Aggregated summary of all statistical tests for an update session.
     */
    public static class TestSummary {
        /** Total number of metrics tested. */
        public int totalTests;

        /** Number of metrics reaching statistical significance (p &lt; 0.05). */
        public int significantCount;

        /** Number of metrics reaching practical significance (|d| &ge; 0.5). */
        public int practicallySignificantCount;

        /** Human-readable one-line summary for each metric. */
        public List<String> metricsSummary = new ArrayList<>();
    }

    // =========================================================================
    // PRIVATE HELPERS -- REGULARIZED INCOMPLETE BETA FUNCTION
    // =========================================================================

    /**
     * Compute the regularized incomplete beta function I_x(a, b) using the
     * Lentz continued-fraction algorithm.
     *
     * <p>The regularized incomplete beta function is defined as:
     * <pre>  I_x(a,b) = B(x; a,b) / B(a,b)</pre>
     * where B(x; a,b) is the incomplete beta function and B(a,b) is the
     * complete beta function.
     *
     * <p>For numerical stability, when x &gt; (a+1)/(a+b+2) the identity
     * I_x(a,b) = 1 - I_{1-x}(b,a) is applied first.
     *
     * @param x value in [0, 1]
     * @param a first shape parameter (&gt;0)
     * @param b second shape parameter (&gt;0)
     * @return the regularized incomplete beta I_x(a,b) in [0, 1]
     */
    private double betaRegularized(double x, double a, double b) {
        if (x <= 0.0) return 0.0;
        if (x >= 1.0) return 1.0;

        // Use the symmetry identity for numerical stability
        if (x > (a + 1.0) / (a + b + 2.0)) {
            return 1.0 - betaRegularized(1.0 - x, b, a);
        }

        // Compute the log of the prefactor:
        //   x^a * (1-x)^b / (a * B(a,b))
        double logPrefactor = a * Math.log(x) + b * Math.log(1.0 - x)
                - Math.log(a) - logBeta(a, b);
        double prefactor = Math.exp(logPrefactor);

        // Evaluate the continued fraction using Lentz's algorithm
        double cf = continuedFractionBeta(x, a, b);

        return Math.max(0.0, Math.min(1.0, prefactor * cf));
    }

    /**
     * Evaluate the continued fraction representation of the incomplete beta
     * function using a modified Lentz algorithm.
     *
     * <p>The continued fraction is:
     * <pre>
     *   1 / (1 + d1/(1 + d2/(1 + ...)))
     * </pre>
     * where the coefficients d_m for m = 1, 2, 3, ... are:
     * <pre>
     *   d_{2k+1} = -(a+k)(a+b+k) / ((a+2k)(a+2k+1)) * x
     *   d_{2k}   =  k(b-k) / ((a+2k-1)(a+2k)) * x
     * </pre>
     */
    private double continuedFractionBeta(double x, double a, double b) {
        double tiny = 1e-30;

        double f = 1.0;
        double c = 1.0;
        double d = 1.0;

        for (int m = 1; m <= BETA_MAX_ITERATIONS; m++) {
            // Compute the m-th coefficient
            double coeff;
            if (m % 2 == 0) {
                // Even coefficient: d_{2k} where k = m/2
                int k = m / 2;
                coeff = k * (b - k) * x / ((a + 2.0 * k - 1.0) * (a + 2.0 * k));
            } else {
                // Odd coefficient: d_{2k+1} where k = (m-1)/2
                int k = (m - 1) / 2;
                coeff = -((a + k) * (a + b + k) * x) / ((a + 2.0 * k) * (a + 2.0 * k + 1.0));
            }

            // Lentz update
            d = 1.0 + coeff * d;
            if (Math.abs(d) < tiny) d = tiny;
            d = 1.0 / d;

            c = 1.0 + coeff / c;
            if (Math.abs(c) < tiny) c = tiny;

            double delta = c * d;
            f *= delta;

            // Convergence check
            if (Math.abs(delta - 1.0) < BETA_EPSILON) {
                return f;
            }
        }

        Log.w(TAG, "betaCF: did not converge after " + BETA_MAX_ITERATIONS
                + " iterations (a=" + a + ", b=" + b + ", x=" + x + ")");
        return f;
    }

    /**
     * Compute the natural logarithm of the beta function B(a, b) using the
     * log-gamma function.
     *
     * <p>{@code ln B(a,b) = ln Gamma(a) + ln Gamma(b) - ln Gamma(a+b)}
     */
    private double logBeta(double a, double b) {
        return logGamma(a) + logGamma(b) - logGamma(a + b);
    }

    /**
     * Compute the natural logarithm of the gamma function using Stirling's
     * approximation with Lanczos coefficients for good accuracy across the
     * full range of positive inputs.
     *
     * <p>Uses the Lanczos approximation with g=7 and n=9 coefficients,
     * which gives about 15 digits of accuracy.
     */
    private double logGamma(double x) {
        if (x <= 0) {
            return 0.0;
        }

        // Lanczos coefficients for g=7, n=9
        double[] coeff = {
                0.99999999999980993,
                676.5203681218851,
                -1259.1392167224028,
                771.32342877765313,
                -176.61502916214059,
                12.507343278686905,
                -0.13857109526572012,
                9.9843695780195716e-6,
                1.5056327351493116e-7
        };

        if (x < 0.5) {
            // Reflection formula: Gamma(x) * Gamma(1-x) = pi / sin(pi*x)
            return Math.log(Math.PI / Math.sin(Math.PI * x)) - logGamma(1.0 - x);
        }

        x -= 1.0;
        double ag = coeff[0];
        double t = x + 7.5; // x + g + 0.5

        for (int i = 1; i < coeff.length; i++) {
            ag += coeff[i] / (x + i);
        }

        return 0.5 * Math.log(2.0 * Math.PI) + (x + 0.5) * Math.log(t) - t + Math.log(ag);
    }

    /**
     * Compute the complement of the standard normal CDF for a positive value:
     * P(Z &gt; z) = 1 - Phi(z).
     *
     * <p>Uses the Abramowitz and Stegun rational approximation (formula 26.2.17)
     * which is accurate to about 7.5 x 10^-8.
     *
     * @param z positive z-score
     * @return the upper tail probability
     */
    private double normalCdfComplement(double z) {
        if (z < 0) {
            return 1.0 - normalCdfComplement(-z);
        }
        if (z > 8.0) {
            // Far tail -- effectively zero
            return 0.0;
        }

        // Abramowitz and Stegun constants for the rational approximation
        double p = 0.2316419;
        double b1 = 0.319381530;
        double b2 = -0.356563782;
        double b3 = 1.781477937;
        double b4 = -1.821255978;
        double b5 = 1.330274429;

        double t = 1.0 / (1.0 + p * z);
        double t2 = t * t;
        double t3 = t2 * t;
        double t4 = t3 * t;
        double t5 = t4 * t;

        double phi = Math.exp(-0.5 * z * z) / Math.sqrt(2.0 * Math.PI);
        double result = phi * (b1 * t + b2 * t2 + b3 * t3 + b4 * t4 + b5 * t5);

        return Math.max(0.0, Math.min(1.0, result));
    }

    // =========================================================================
    // PRIVATE HELPERS -- DATA CONVERSION & FORMATTING
    // =========================================================================

    /**
     * Convert a {@code List<Float>} to a {@code double[]}, returning
     * {@code null} if the list is null or empty.
     */
    private double[] toDoubleArray(List<Float> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        double[] result = new double[list.size()];
        for (int i = 0; i < list.size(); i++) {
            Float val = list.get(i);
            result[i] = (val != null) ? val.doubleValue() : 0.0;
        }
        return result;
    }

    /**
     * Format a single {@link ComparisonStatistics} row into a concise
     * human-readable summary line suitable for UI display or reports.
     */
    private String formatMetricSummary(ComparisonStatistics stat) {
        StringBuilder sb = new StringBuilder();
        sb.append(stat.metricName != null ? stat.metricName : "unknown");
        sb.append(": ");

        // Direction of change
        if (stat.preUpdateMean != null && stat.postUpdateMean != null) {
            double delta = stat.postUpdateMean - stat.preUpdateMean;
            if (stat.preUpdateMean != 0.0) {
                double pctChange = (delta / Math.abs(stat.preUpdateMean)) * 100.0;
                sb.append(String.format(Locale.US, "%+.1f%% change", pctChange));
            } else {
                sb.append(String.format(Locale.US, "%+.2f change", delta));
            }
        }

        // Significance
        if (stat.statisticallySignificant == 1 && stat.practicallySignificant == 1) {
            sb.append(" [SIGNIFICANT, ");
            sb.append(getEffectSizeLabel(stat.cohenD != null ? stat.cohenD : 0.0));
            sb.append(" effect]");
        } else if (stat.statisticallySignificant == 1) {
            sb.append(" [statistically significant, ");
            sb.append(getEffectSizeLabel(stat.cohenD != null ? stat.cohenD : 0.0));
            sb.append(" effect]");
        } else {
            sb.append(" [not significant]");
        }

        // P-value and sample sizes
        sb.append(String.format(Locale.US, " (p=%.4f, n=%d/%d)",
                stat.pValue != null ? stat.pValue : 1.0,
                stat.preUpdateN,
                stat.postUpdateN));

        return sb.toString();
    }
}
