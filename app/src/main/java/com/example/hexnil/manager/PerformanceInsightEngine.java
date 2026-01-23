package com.example.hexnil.manager;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.model.DailyAggregate;
import com.example.hexnil.model.RegressionEvent;
import com.example.hexnil.model.MemorySnapshot;
import com.example.hexnil.model.CrashEvent;
import com.example.hexnil.model.AnrEvent;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Performance Insight Engine
 * 
 * Generates human-readable, actionable insights from performance data.
 * 
 * Core principles:
 * - NEVER invents data - all insights are DB-backed
 * - Every claim includes evidence reference (table, timestamp, count)
 * - Confidence scoring based on data quality and quantity
 * - Natural language explanations for non-technical users
 * 
 * Output types:
 * - Executive summary (1-2 sentences)
 * - Technical summary (detailed for engineers)
 * - Regression reports with evidence
 * - Health score explanations
 */
public class PerformanceInsightEngine {

    private static final String TAG = "PerformanceInsightEngine";

    private final Context context;
    private final HexNilDatabase database;

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private static final SimpleDateFormat DATETIME_FORMAT = new SimpleDateFormat("MMM d, h:mm a", Locale.US);

    public PerformanceInsightEngine(Context context, HexNilDatabase database) {
        this.context = context.getApplicationContext();
        this.database = database;
    }

    // =========================================================================
    // Executive Summary Generation
    // =========================================================================

    /**
     * Generate a one-sentence executive summary of device performance.
     * 
     * @return Human-readable summary
     */
    public String generateExecutiveSummary() {
        try {
            // Get latest aggregate
            DailyAggregate latest = database.dailyAggregateDao().getLatest();
            if (latest == null) {
                return "Performance monitoring is active. Data will be available after 24 hours of use.";
            }

            // Get active regressions
            int activeRegressions = database.regressionEventDao().getActiveCount();

            // Build summary based on health score
            StringBuilder sb = new StringBuilder();

            if (latest.healthScore >= 90) {
                sb.append("✅ Device performance is excellent");
            } else if (latest.healthScore >= 70) {
                sb.append("👍 Device performance is good");
            } else if (latest.healthScore >= 50) {
                sb.append("⚠️ Device performance needs attention");
            } else {
                sb.append("🔴 Device performance is degraded");
            }

            sb.append(" (").append(latest.healthGrade).append(")");

            if (activeRegressions > 0) {
                sb.append(". ").append(activeRegressions);
                sb.append(activeRegressions == 1 ? " issue detected" : " issues detected");
            }

            sb.append(".");

            return sb.toString();
        } catch (Exception e) {
            Log.e(TAG, "Error generating executive summary", e);
            return "Performance data is being collected.";
        }
    }

    /**
     * Generate a detailed technical summary for engineers.
     * 
     * @return Multi-line technical summary with metrics
     */
    public String generateTechnicalSummary() {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("=== PERFORMANCE ANALYSIS REPORT ===\n");
            sb.append("Generated: ").append(DATETIME_FORMAT.format(new Date())).append("\n");
            sb.append("Device: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n");
            sb.append("Android: ").append(Build.VERSION.RELEASE).append("\n\n");

            // Get latest aggregate
            DailyAggregate latest = database.dailyAggregateDao().getLatest();
            if (latest == null) {
                sb.append("Insufficient data for analysis. Minimum 24 hours required.\n");
                return sb.toString();
            }

            // Health Score Section
            sb.append("--- DEVICE HEALTH ---\n");
            sb.append(String.format(Locale.US, "Health Score: %.1f/100 (%s)\n",
                    latest.healthScore, latest.healthGrade));
            sb.append("Date: ").append(latest.date).append("\n\n");

            // Memory Section
            sb.append("--- MEMORY ---\n");
            sb.append(String.format(Locale.US, "Avg Usage: %.1f%%\n", latest.avgMemoryUsedPercent));
            sb.append(String.format(Locale.US, "Max Usage: %.1f%%\n", latest.maxMemoryUsedPercent));
            sb.append(String.format(Locale.US, "Low Memory Events: %d\n", latest.lowMemoryEventCount));
            sb.append(String.format(Locale.US, "Samples: %d\n\n", latest.memorySampleCount));

            // Launch Performance Section
            sb.append("--- APP LAUNCHES ---\n");
            sb.append(String.format(Locale.US, "Total Launches: %d\n", latest.launchCount));
            sb.append(String.format(Locale.US, "Cold Launches: %d\n", latest.coldLaunchCount));
            sb.append(String.format(Locale.US, "Avg Cold Launch: %.0fms\n", latest.avgColdLaunchMs));
            sb.append(String.format(Locale.US, "Avg Warm Launch: %.0fms\n", latest.avgWarmLaunchMs));
            sb.append(String.format(Locale.US, "Slow Launches: %d\n\n", latest.slowLaunchCount));

            // Frame Metrics Section
            sb.append("--- FRAME RENDERING ---\n");
            sb.append(String.format(Locale.US, "Total Frames: %d\n", latest.totalFrames));
            sb.append(String.format(Locale.US, "Slow Frames: %d\n", latest.totalSlowFrames));
            sb.append(String.format(Locale.US, "Frozen Frames: %d\n", latest.totalFrozenFrames));
            sb.append(String.format(Locale.US, "Avg Jank: %.1f%%\n", latest.avgJankPercent));
            sb.append(String.format(Locale.US, "Avg FPS: %.1f\n\n", latest.avgFps));

            // Stability Section
            sb.append("--- STABILITY ---\n");
            sb.append(String.format(Locale.US, "Crashes: %d (%d unique types)\n",
                    latest.crashCount, latest.uniqueCrashTypes));
            sb.append(String.format(Locale.US, "ANRs: %d (%d unique types)\n",
                    latest.anrCount, latest.uniqueAnrTypes));
            if (latest.hasNewCrashType) {
                sb.append("⚠️ New crash type detected\n");
            }
            if (latest.hasNewAnrType) {
                sb.append("⚠️ New ANR type detected\n");
            }
            sb.append("\n");

            // Baselines Section
            sb.append("--- BASELINES ---\n");
            sb.append(String.format(Locale.US, "7-day Avg Launch: %.0fms\n", latest.baseline7DayLaunchMs));
            sb.append(String.format(Locale.US, "7-day Avg Jank: %.1f%%\n", latest.baseline7DayJankPercent));
            sb.append(String.format(Locale.US, "30-day Avg Launch: %.0fms\n", latest.baseline30DayLaunchMs));
            sb.append(String.format(Locale.US, "30-day Avg Jank: %.1f%%\n\n", latest.baseline30DayJankPercent));

            // Active Regressions
            List<RegressionEvent> regressions = database.regressionEventDao().getActiveRegressions();
            if (!regressions.isEmpty()) {
                sb.append("--- ACTIVE REGRESSIONS ---\n");
                for (RegressionEvent reg : regressions) {
                    sb.append(String.format(Locale.US, "[%s] %s: %s\n",
                            reg.severity != null ? reg.severity.toUpperCase() : "UNKNOWN",
                            reg.regressionType,
                            reg.explanation != null ? reg.explanation : "No details"));
                }
            }

            return sb.toString();
        } catch (Exception e) {
            Log.e(TAG, "Error generating technical summary", e);
            return "Error generating report: " + e.getMessage();
        }
    }

    // =========================================================================
    // Regression Insights
    // =========================================================================

    /**
     * Generate a detailed insight report for a specific regression.
     * 
     * @param regression The regression event
     * @return Detailed insight with evidence
     */
    public RegressionInsight generateRegressionInsight(RegressionEvent regression) {
        RegressionInsight insight = new RegressionInsight();
        insight.regressionId = regression.id;
        insight.type = regression.regressionType;
        insight.severity = regression.severity;
        insight.detectedAt = regression.detectedAt;

        // Build human-readable summary
        insight.summary = buildRegressionSummary(regression);

        // Build technical explanation
        insight.technicalDetails = buildTechnicalDetails(regression);

        // Build user recommendation
        insight.userRecommendation = buildUserRecommendation(regression);

        // Calculate confidence
        insight.confidenceScore = calculateConfidence(regression);
        insight.confidenceExplanation = explainConfidence(regression, insight.confidenceScore);

        // Evidence reference
        insight.evidenceTable = regression.evidenceTableName;
        insight.evidenceCount = regression.evidenceCount;
        insight.evidenceTimeRange = formatTimeRange(
                regression.evidenceStartTime, regression.evidenceEndTime);

        return insight;
    }

    /**
     * Build a human-readable summary of the regression.
     */
    private String buildRegressionSummary(RegressionEvent reg) {
        String appName = reg.appLabel != null ? reg.appLabel : "Your device";

        switch (reg.regressionType) {
            case "launch_time":
                return String.format(Locale.US,
                        "%s is starting %.0f%% slower than usual (%.0fms vs %.0fms baseline).",
                        appName, reg.deltaPercent, reg.currentValue, reg.baselineValue);

            case "jank":
                return String.format(Locale.US,
                        "%s is experiencing more frame drops (%.1f%% jank vs %.1f%% baseline).",
                        appName, reg.currentValue, reg.baselineValue);

            case "memory":
                return String.format(Locale.US,
                        "Memory pressure has increased. %.0f low-memory events vs %.1f average.",
                        reg.currentValue, reg.baselineValue);

            case "crash":
                if (reg.baselineValue == 0) {
                    return "A new crash type was detected after an update.";
                }
                return String.format(Locale.US,
                        "%s is crashing more frequently (%.0f%% increase).",
                        appName, reg.deltaPercent);

            case "anr":
                return String.format(Locale.US,
                        "%s is not responding (ANR) - this started after a recent update.",
                        appName);

            case "thermal":
                return String.format(Locale.US,
                        "Device temperature has increased by %.1f°C. Average is now %.1f°C.",
                        reg.currentValue - reg.baselineValue, reg.currentValue);

            default:
                return reg.explanation != null ? reg.explanation : "Performance degradation detected.";
        }
    }

    /**
     * Build technical details for engineers.
     */
    private String buildTechnicalDetails(RegressionEvent reg) {
        StringBuilder sb = new StringBuilder();

        sb.append("Type: ").append(reg.regressionType).append("\n");
        sb.append("Baseline: ").append(String.format("%.2f %s", reg.baselineValue, reg.unit)).append("\n");
        sb.append("Current: ").append(String.format("%.2f %s", reg.currentValue, reg.unit)).append("\n");
        sb.append("Delta: ").append(String.format("%.1f%%", reg.deltaPercent)).append("\n");
        sb.append("Threshold: ").append(String.format("%.1f%%", reg.threshold)).append("\n");

        if (reg.triggerEvent != null) {
            sb.append("Trigger: ").append(reg.triggerEvent).append("\n");
        }

        if (reg.previousVersion != null && reg.currentVersion != null) {
            sb.append("Version: ").append(reg.previousVersion);
            sb.append(" → ").append(reg.currentVersion).append("\n");
        }

        sb.append("Evidence: ").append(reg.evidenceCount);
        sb.append(" records from ").append(reg.evidenceTableName);

        return sb.toString();
    }

    /**
     * Build a user-friendly recommendation.
     */
    private String buildUserRecommendation(RegressionEvent reg) {
        String trigger = reg.triggerEvent != null ? reg.triggerEvent : "unknown";

        switch (reg.regressionType) {
            case "launch_time":
                if ("app_update".equals(trigger)) {
                    return "The recent app update may have caused this. Check if a newer version is available or consider reverting.";
                }
                return "Try clearing the app's cache or restarting your device.";

            case "jank":
                return "Close background apps to free up resources, or reduce animations in Settings.";

            case "memory":
                return "Close unused apps. Consider uninstalling apps you don't use regularly.";

            case "crash":
                if ("app_update".equals(trigger)) {
                    return "This crash started after a recent update. Report this to the app developer.";
                }
                return "Try reinstalling the app or clearing its data.";

            case "anr":
                return "The app is freezing. Force close it and try again. If it persists, reinstall.";

            case "thermal":
                return "Your device is running hot. Reduce usage intensity or let it cool down.";

            default:
                return "Monitor the issue. If it persists, consider restarting your device.";
        }
    }

    /**
     * Calculate confidence score based on evidence quality.
     */
    private float calculateConfidence(RegressionEvent reg) {
        float confidence = 50.0f; // Base confidence

        // More evidence = higher confidence
        if (reg.evidenceCount >= 10) {
            confidence += 20;
        } else if (reg.evidenceCount >= 5) {
            confidence += 10;
        }

        // Longer persistence = higher confidence
        if (reg.persistenceDays >= 3) {
            confidence += 15;
        } else if (reg.persistenceDays >= 1) {
            confidence += 5;
        }

        // Higher delta = higher confidence
        if (reg.deltaPercent >= 50) {
            confidence += 15;
        } else if (reg.deltaPercent >= 30) {
            confidence += 10;
        }

        // Known trigger = higher confidence
        if ("app_update".equals(reg.triggerEvent) || "system_update".equals(reg.triggerEvent)) {
            confidence += 10;
        }

        return Math.min(99, confidence);
    }

    /**
     * Explain confidence score to user.
     */
    private String explainConfidence(RegressionEvent reg, float score) {
        if (score >= 90) {
            return "Very high confidence based on strong evidence";
        } else if (score >= 70) {
            return "High confidence based on consistent patterns";
        } else if (score >= 50) {
            return "Moderate confidence - more data needed";
        } else {
            return "Low confidence - pattern may be temporary";
        }
    }

    /**
     * Format time range for display.
     */
    private String formatTimeRange(long start, long end) {
        return DATETIME_FORMAT.format(new Date(start)) + " to " + DATETIME_FORMAT.format(new Date(end));
    }

    // =========================================================================
    // Health Score Explanation
    // =========================================================================

    /**
     * Explain why the health score is what it is.
     * 
     * @param aggregate The daily aggregate
     * @return Human-readable explanation
     */
    public String explainHealthScore(DailyAggregate aggregate) {
        if (aggregate == null)
            return "No data available.";

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.US, "Health Score: %.0f/100 (%s)\n\n",
                aggregate.healthScore, aggregate.healthGrade));

        List<String> positives = new ArrayList<>();
        List<String> negatives = new ArrayList<>();

        // Analyze each component

        // Memory
        if (aggregate.avgMemoryUsedPercent < 70) {
            positives.add("Memory usage is healthy (< 70%)");
        } else if (aggregate.avgMemoryUsedPercent > 85) {
            negatives.add(String.format(Locale.US,
                    "High memory usage (%.0f%%)", aggregate.avgMemoryUsedPercent));
        }

        if (aggregate.lowMemoryEventCount == 0) {
            positives.add("No low-memory events");
        } else if (aggregate.lowMemoryEventCount > 5) {
            negatives.add(String.format("%d low-memory events", aggregate.lowMemoryEventCount));
        }

        // Stability
        if (aggregate.crashCount == 0) {
            positives.add("No crashes");
        } else {
            negatives.add(String.format("%d crash(es)", aggregate.crashCount));
        }

        if (aggregate.anrCount == 0) {
            positives.add("No ANRs");
        } else {
            negatives.add(String.format("%d ANR(s)", aggregate.anrCount));
        }

        // Performance
        if (aggregate.avgJankPercent < 5) {
            positives.add("Smooth frame rendering (< 5% jank)");
        } else if (aggregate.avgJankPercent > 15) {
            negatives.add(String.format(Locale.US,
                    "High jank rate (%.1f%%)", aggregate.avgJankPercent));
        }

        if (aggregate.avgColdLaunchMs > 0 && aggregate.avgColdLaunchMs < 3000) {
            positives.add("Fast app launches (< 3s)");
        } else if (aggregate.avgColdLaunchMs > 5000) {
            negatives.add(String.format(Locale.US,
                    "Slow app launches (%.1fs)", aggregate.avgColdLaunchMs / 1000));
        }

        // Build explanation
        if (!positives.isEmpty()) {
            sb.append("✅ Positives:\n");
            for (String p : positives) {
                sb.append("  • ").append(p).append("\n");
            }
        }

        if (!negatives.isEmpty()) {
            sb.append("\n⚠️ Issues:\n");
            for (String n : negatives) {
                sb.append("  • ").append(n).append("\n");
            }
        }

        return sb.toString();
    }

    // =========================================================================
    // Data Structures
    // =========================================================================

    /**
     * Structured insight about a regression.
     */
    public static class RegressionInsight {
        public long regressionId;
        public String type;
        public String severity;
        public long detectedAt;

        public String summary;
        public String technicalDetails;
        public String userRecommendation;

        public float confidenceScore;
        public String confidenceExplanation;

        public String evidenceTable;
        public int evidenceCount;
        public String evidenceTimeRange;

        @Override
        public String toString() {
            return String.format(Locale.US, "[%s] %s\n%s\nConfidence: %.0f%% (%s)\nEvidence: %d from %s",
                    severity, type, summary, confidenceScore, confidenceExplanation,
                    evidenceCount, evidenceTable);
        }
    }
}
