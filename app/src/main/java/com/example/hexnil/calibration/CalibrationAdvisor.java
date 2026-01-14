package com.example.hexnil.calibration;

import android.content.Context;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.model.CalibrationData;

import java.util.ArrayList;
import java.util.List;

/**
 * CalibrationAdvisor - Human-Readable Calibration Recommendation Engine
 * 
 * Generates actionable, user-friendly calibration recommendations based on
 * calibration intelligence analysis. Provides step-by-step guidance for
 * improving battery percentage accuracy.
 * 
 * RECOMMENDATION CATEGORIES:
 * =========================
 * 
 * 1. IMMEDIATE ACTIONS
 *    - Critical issues requiring immediate attention
 *    - Safety-related calibration problems
 * 
 * 2. SCHEDULED MAINTENANCE
 *    - Regular calibration cycles
 *    - Preventive maintenance recommendations
 * 
 * 3. OPTIMIZATION TIPS
 *    - Best practices for maintaining accuracy
 *    - Usage pattern suggestions
 * 
 * URGENCY LEVELS:
 * ==============
 * - CRITICAL: Immediate action required (potential safety issue)
 * - HIGH: Action needed within 24-48 hours
 * - MODERATE: Action recommended within a week
 * - LOW: Optional improvement suggestion
 * - INFO: Informational only, no action needed
 * 
 * @author HexNil Battery Intelligence Team
 * @version 2.0.0
 */
public class CalibrationAdvisor {
    
    private static final String TAG = "CalibrationAdvisor";
    
    private final Context context;
    private final HexNilDatabase database;
    
    public CalibrationAdvisor(Context context, HexNilDatabase database) {
        this.context = context;
        this.database = database;
    }
    
    // ==================================================================================
    //                          MAIN RECOMMENDATION METHODS
    // ==================================================================================
    
    /**
     * Generate complete calibration advice based on analysis
     */
    public CalibrationAdvice generateAdvice(CalibrationIntelligence.CalibrationAnalysis analysis) {
        if (analysis == null || !analysis.valid) {
            return CalibrationAdvice.unavailable("Insufficient calibration data");
        }
        
        CalibrationAdvice advice = new CalibrationAdvice();
        advice.timestamp = System.currentTimeMillis();
        advice.calibrationQuality = analysis.calibrationQuality;
        advice.qualityGrade = analysis.qualityGrade;
        advice.isCalibrated = analysis.isCalibrated;
        advice.needsCalibration = analysis.needsCalibration;
        
        // Determine primary recommendation
        advice.primaryRecommendation = generatePrimaryRecommendation(analysis);
        advice.urgency = determineUrgency(analysis);
        advice.urgencyColor = getUrgencyColor(advice.urgency);
        
        // Generate detailed steps
        advice.calibrationSteps = generateCalibrationSteps(analysis);
        
        // Generate explanation
        advice.explanation = generateExplanation(analysis);
        
        // Generate additional tips
        advice.optimizationTips = generateOptimizationTips(analysis);
        
        // Generate warnings if any
        advice.warnings = generateWarnings(analysis);
        
        // Set scheduling
        advice.recommendedSchedule = generateSchedule(analysis);
        advice.nextCalibrationDate = calculateNextCalibrationDate(analysis);
        
        // Generate summary for notifications
        advice.notificationSummary = generateNotificationSummary(advice);
        advice.notificationTitle = generateNotificationTitle(advice);
        
        advice.available = true;
        return advice;
    }
    
    /**
     * Generate quick status message for UI display
     */
    public String generateQuickStatus(CalibrationIntelligence.CalibrationAnalysis analysis) {
        if (analysis == null || !analysis.valid) {
            return "Calibration status unknown";
        }
        
        if (analysis.calibrationQuality >= 90) {
            return "✅ Battery calibration is excellent";
        } else if (analysis.calibrationQuality >= 75) {
            return "✓ Battery calibration is good";
        } else if (analysis.calibrationQuality >= 50) {
            return "⚠️ Battery calibration needs attention";
        } else if (analysis.calibrationQuality >= 25) {
            return "❗ Battery calibration is poor - action recommended";
        } else {
            return "🔴 Battery calibration is critical - immediate action needed";
        }
    }
    
    /**
     * Generate drift explanation in plain language
     */
    public String explainDrift(CalibrationIntelligence.CalibrationAnalysis analysis) {
        if (analysis == null || !analysis.valid) {
            return "Unable to analyze battery drift.";
        }
        
        StringBuilder sb = new StringBuilder();
        
        float drift = analysis.emaDrift;
        String direction = analysis.driftDirection.toString();
        
        if (Math.abs(drift) < 3) {
            sb.append("Your battery percentage is accurate. ");
            sb.append("The displayed level closely matches the actual charge level.");
        } else if (direction.equals("UNDER_REPORTING")) {
            sb.append(String.format("Your battery is showing %.0f%% LESS charge than it actually has. ", Math.abs(drift)));
            sb.append("This means when your phone shows 30%, you actually have about ");
            sb.append(String.format("%.0f%% charge remaining. ", 30 + Math.abs(drift)));
            sb.append("Your phone may shut down unexpectedly showing 'low battery' when there's still charge left.");
        } else if (direction.equals("OVER_REPORTING")) {
            sb.append(String.format("Your battery is showing %.0f%% MORE charge than it actually has. ", Math.abs(drift)));
            sb.append("This means when your phone shows 30%, you may only have about ");
            sb.append(String.format("%.0f%% charge remaining. ", Math.max(0, 30 - Math.abs(drift))));
            sb.append("Your phone may shut down unexpectedly while still showing remaining charge.");
        }
        
        return sb.toString();
    }
    
    // ==================================================================================
    //                          RECOMMENDATION GENERATION
    // ==================================================================================
    
    private String generatePrimaryRecommendation(CalibrationIntelligence.CalibrationAnalysis analysis) {
        // Critical: Severe drift or many sudden drops
        if (analysis.driftSeverity == CalibrationIntelligence.DriftSeverity.CRITICAL) {
            return "Critical calibration required - Battery percentage is severely inaccurate";
        }
        
        if (analysis.totalSuddenDrops >= 10) {
            return "Urgent calibration needed - Frequent sudden battery drops detected";
        }
        
        // High priority
        if (analysis.driftSeverity == CalibrationIntelligence.DriftSeverity.SEVERE) {
            return "Calibration strongly recommended - Significant drift detected";
        }
        
        if (analysis.totalSuddenDrops >= 5) {
            return "Calibration recommended - Multiple sudden drops detected";
        }
        
        // Moderate priority
        if (analysis.driftSeverity == CalibrationIntelligence.DriftSeverity.SIGNIFICANT) {
            return "Consider calibrating your battery - Noticeable drift detected";
        }
        
        if (analysis.daysSinceFullCycle > 30) {
            return "Schedule a calibration cycle - It's been over a month since last full cycle";
        }
        
        // Low priority
        if (analysis.needsCalibration) {
            return "Minor calibration recommended for optimal accuracy";
        }
        
        // No calibration needed
        if (analysis.calibrationQuality >= 90) {
            return "No action needed - Your battery is well calibrated";
        }
        
        return "Battery calibration is acceptable - Monitor for changes";
    }
    
    private CalibrationUrgency determineUrgency(CalibrationIntelligence.CalibrationAnalysis analysis) {
        if (analysis.calibrationUrgency == CalibrationIntelligence.CalibrationUrgency.CRITICAL) {
            return CalibrationUrgency.CRITICAL;
        }
        if (analysis.calibrationUrgency == CalibrationIntelligence.CalibrationUrgency.HIGH) {
            return CalibrationUrgency.HIGH;
        }
        if (analysis.calibrationUrgency == CalibrationIntelligence.CalibrationUrgency.MODERATE) {
            return CalibrationUrgency.MODERATE;
        }
        if (analysis.needsCalibration) {
            return CalibrationUrgency.LOW;
        }
        return CalibrationUrgency.INFO;
    }
    
    private String getUrgencyColor(CalibrationUrgency urgency) {
        switch (urgency) {
            case CRITICAL: return "#FF0000"; // Red
            case HIGH: return "#FF6600";     // Orange
            case MODERATE: return "#FFCC00"; // Yellow
            case LOW: return "#66CC00";      // Light green
            case INFO: return "#00CC66";     // Green
            default: return "#808080";       // Gray
        }
    }
    
    // ==================================================================================
    //                          STEP-BY-STEP INSTRUCTIONS
    // ==================================================================================
    
    private List<CalibrationStep> generateCalibrationSteps(CalibrationIntelligence.CalibrationAnalysis analysis) {
        List<CalibrationStep> steps = new ArrayList<>();
        
        if (!analysis.needsCalibration && analysis.calibrationQuality >= 75) {
            // No calibration needed
            CalibrationStep step = new CalibrationStep();
            step.stepNumber = 1;
            step.instruction = "No calibration needed at this time";
            step.details = "Your battery percentage is accurate. Continue normal usage.";
            step.duration = "N/A";
            step.isOptional = true;
            steps.add(step);
            return steps;
        }
        
        // Full calibration cycle steps
        
        // Step 1: Preparation
        CalibrationStep step1 = new CalibrationStep();
        step1.stepNumber = 1;
        step1.instruction = "Prepare for calibration";
        step1.details = "Find a time when you won't need your phone for several hours. " +
                       "Make sure you have your charger ready. " +
                       "Close all apps and disable battery saver mode.";
        step1.duration = "5 minutes";
        step1.isOptional = false;
        step1.icon = "⚙️";
        steps.add(step1);
        
        // Step 2: Discharge
        CalibrationStep step2 = new CalibrationStep();
        step2.stepNumber = 2;
        step2.instruction = "Discharge battery to 5% or lower";
        step2.details = "Use your phone normally until it reaches 5% or less. " +
                       "You can play videos, games, or use the camera to speed up discharge. " +
                       "Do NOT let it shut down from empty battery if possible.";
        step2.duration = "2-6 hours";
        step2.isOptional = false;
        step2.icon = "🔋";
        step2.warning = "Don't leave your phone at 0% for extended periods";
        steps.add(step2);
        
        // Step 3: Power off
        CalibrationStep step3 = new CalibrationStep();
        step3.stepNumber = 3;
        step3.instruction = "Power off your phone";
        step3.details = "Once at 5% or below, power off your phone completely. " +
                       "Wait 1-2 minutes before proceeding.";
        step3.duration = "2 minutes";
        step3.isOptional = false;
        step3.icon = "📴";
        steps.add(step3);
        
        // Step 4: Charge while off
        CalibrationStep step4 = new CalibrationStep();
        step4.stepNumber = 4;
        step4.instruction = "Charge to 100% while powered off";
        step4.details = "Connect your original charger and charge completely to 100%. " +
                       "Keep the phone OFF during the entire charging process. " +
                       "Use a wall charger, not USB from a computer.";
        step4.duration = "1-3 hours";
        step4.isOptional = false;
        step4.icon = "🔌";
        step4.warning = "Do not interrupt the charging process";
        steps.add(step4);
        
        // Step 5: Keep charging
        CalibrationStep step5 = new CalibrationStep();
        step5.stepNumber = 5;
        step5.instruction = "Continue charging for 30 more minutes";
        step5.details = "After the LED shows fully charged, keep it plugged in for 30 minutes. " +
                       "This ensures the battery is truly at 100% capacity.";
        step5.duration = "30 minutes";
        step5.isOptional = false;
        step5.icon = "⏱️";
        steps.add(step5);
        
        // Step 6: Power on
        CalibrationStep step6 = new CalibrationStep();
        step6.stepNumber = 6;
        step6.instruction = "Power on while still plugged in";
        step6.details = "Turn on your phone while still connected to the charger. " +
                       "Wait for it to fully boot up and show 100%.";
        step6.duration = "2-3 minutes";
        step6.isOptional = false;
        step6.icon = "📱";
        steps.add(step6);
        
        // Step 7: Unplug
        CalibrationStep step7 = new CalibrationStep();
        step7.stepNumber = 7;
        step7.instruction = "Unplug and use normally";
        step7.details = "Disconnect the charger and use your phone normally. " +
                       "The battery gauge should now be recalibrated. " +
                       "Monitor the percentage accuracy over the next few days.";
        step7.duration = "Ongoing";
        step7.isOptional = false;
        step7.icon = "✅";
        steps.add(step7);
        
        // Optional: Repeat
        if (analysis.calibrationQuality < 50 || analysis.totalSuddenDrops >= 5) {
            CalibrationStep step8 = new CalibrationStep();
            step8.stepNumber = 8;
            step8.instruction = "Repeat if needed";
            step8.details = "If battery percentage is still inaccurate after 1 week, " +
                           "repeat this calibration process one more time. " +
                           "If issues persist, the battery may need replacement.";
            step8.duration = "1 week observation";
            step8.isOptional = true;
            step8.icon = "🔄";
            steps.add(step8);
        }
        
        return steps;
    }
    
    // ==================================================================================
    //                          EXPLANATION GENERATION
    // ==================================================================================
    
    private String generateExplanation(CalibrationIntelligence.CalibrationAnalysis analysis) {
        StringBuilder sb = new StringBuilder();
        
        // Quality explanation
        sb.append("CALIBRATION STATUS: ").append(analysis.qualityGrade.toUpperCase()).append("\n\n");
        
        // Drift explanation
        if (Math.abs(analysis.emaDrift) >= 3) {
            sb.append("📊 DRIFT DETECTED\n");
            sb.append("Your battery is ");
            if (analysis.driftDirection == CalibrationIntelligence.DriftDirection.UNDER_REPORTING) {
                sb.append("under-reporting by ~").append(String.format("%.0f%%", Math.abs(analysis.emaDrift)));
                sb.append(" (showing less charge than actual).\n");
            } else {
                sb.append("over-reporting by ~").append(String.format("%.0f%%", Math.abs(analysis.emaDrift)));
                sb.append(" (showing more charge than actual).\n");
            }
            sb.append("\n");
        }
        
        // Sudden drops explanation
        if (analysis.totalSuddenDrops > 0) {
            sb.append("⚠️ SUDDEN DROPS DETECTED\n");
            sb.append("Your battery has experienced ").append(analysis.totalSuddenDrops);
            sb.append(" sudden percentage drop(s). This indicates calibration issues.\n");
            sb.append("\n");
        }
        
        // Cycle information
        if (analysis.daysSinceFullCycle > 14) {
            sb.append("📅 FULL CYCLE NEEDED\n");
            sb.append("It's been ").append(analysis.daysSinceFullCycle);
            sb.append(" days since your last full charge/discharge cycle. ");
            sb.append("Regular cycles help maintain calibration accuracy.\n");
            sb.append("\n");
        }
        
        // Success criteria
        if (analysis.calibrationSuccessCriteria != null) {
            sb.append("📋 CALIBRATION CRITERIA\n");
            CalibrationIntelligence.CalibrationSuccessResult criteria = analysis.calibrationSuccessCriteria;
            sb.append("• Drift threshold: ").append(criteria.avgDriftPassing ? "✓ Passing" : "✗ Not met").append("\n");
            sb.append("• No sudden drops: ").append(criteria.noRecentDropsPassing ? "✓ Passing" : "✗ Not met").append("\n");
            sb.append("• Drift stability: ").append(criteria.velocityPassing ? "✓ Passing" : "✗ Not met").append("\n");
            sb.append("• Recent full charge: ").append(criteria.hasRecentFullCharge ? "✓ Yes" : "✗ No").append("\n");
            sb.append("• Recent deep discharge: ").append(criteria.hasRecentDeepDischarge ? "✓ Yes" : "✗ No").append("\n");
        }
        
        return sb.toString();
    }
    
    // ==================================================================================
    //                          OPTIMIZATION TIPS
    // ==================================================================================
    
    private List<String> generateOptimizationTips(CalibrationIntelligence.CalibrationAnalysis analysis) {
        List<String> tips = new ArrayList<>();
        
        // Always include basic tips
        tips.add("💡 Avoid letting your battery drain to 0% regularly - this can damage the battery and cause calibration drift.");
        
        tips.add("🔌 Use your original charger or a quality certified charger for best results.");
        
        // Conditional tips based on analysis
        if (analysis.daysSinceFullCycle > 30) {
            tips.add("📅 Perform a full charge cycle (5% → 100%) at least once per month to maintain calibration.");
        }
        
        if (analysis.driftDirection == CalibrationIntelligence.DriftDirection.UNDER_REPORTING) {
            tips.add("🔋 Your battery shows less charge than actual. Trust your battery a bit more when it shows 'low'.");
        } else if (analysis.driftDirection == CalibrationIntelligence.DriftDirection.OVER_REPORTING) {
            tips.add("⚠️ Your battery shows more charge than actual. Consider charging earlier than usual to avoid unexpected shutdowns.");
        }
        
        if (analysis.totalSuddenDrops > 0) {
            tips.add("📉 Sudden drops often occur at specific battery levels. Try not to let your battery sit at these levels for long.");
        }
        
        if (analysis.calibrationQuality < 50) {
            tips.add("🔧 Consider a factory reset if calibration issues persist after multiple calibration cycles.");
            tips.add("🔄 As a last resort, some manufacturers offer battery recalibration in service mode.");
        }
        
        tips.add("🌡️ Avoid charging in extreme temperatures (below 0°C or above 45°C) as this affects calibration.");
        
        tips.add("📊 Open HexNil weekly to monitor your battery calibration status.");
        
        return tips;
    }
    
    // ==================================================================================
    //                          WARNING GENERATION
    // ==================================================================================
    
    private List<String> generateWarnings(CalibrationIntelligence.CalibrationAnalysis analysis) {
        List<String> warnings = new ArrayList<>();
        
        // Critical drift warning
        if (analysis.driftSeverity == CalibrationIntelligence.DriftSeverity.CRITICAL) {
            warnings.add("🔴 CRITICAL: Battery percentage is severely inaccurate (>20% drift). " +
                        "Your phone may shut down unexpectedly. Consider battery replacement if calibration doesn't help.");
        }
        
        // Severe drift warning
        if (analysis.driftSeverity == CalibrationIntelligence.DriftSeverity.SEVERE) {
            warnings.add("🟠 WARNING: Significant battery drift detected. " +
                        "Don't rely on displayed percentage for important situations.");
        }
        
        // Sudden drop warning
        if (analysis.totalSuddenDrops >= 5) {
            warnings.add("⚠️ ALERT: Multiple sudden battery drops detected. " +
                        "This may indicate battery degradation or a faulty battery gauge.");
        }
        
        // Over-reporting warning (safety critical)
        if (analysis.driftDirection == CalibrationIntelligence.DriftDirection.OVER_REPORTING 
            && Math.abs(analysis.emaDrift) > 10) {
            warnings.add("⚠️ SAFETY: Battery shows more charge than actual. " +
                        "Your phone may shut down even when showing significant remaining charge.");
        }
        
        // Old calibration warning
        if (analysis.daysSinceFullCycle > 60) {
            warnings.add("📅 NOTICE: No full charge cycle in over 2 months. " +
                        "Calibration accuracy may have degraded significantly.");
        }
        
        // Health impact warning
        if (analysis.healthImpactScore > 30) {
            warnings.add("📊 INFO: Calibration issues are affecting battery health readings. " +
                        "Health percentage may not be accurate until calibrated.");
        }
        
        return warnings;
    }
    
    // ==================================================================================
    //                          SCHEDULING
    // ==================================================================================
    
    private String generateSchedule(CalibrationIntelligence.CalibrationAnalysis analysis) {
        if (analysis.calibrationUrgency == CalibrationIntelligence.CalibrationUrgency.CRITICAL) {
            return "Immediately";
        }
        if (analysis.calibrationUrgency == CalibrationIntelligence.CalibrationUrgency.HIGH) {
            return "Within 24-48 hours";
        }
        if (analysis.calibrationUrgency == CalibrationIntelligence.CalibrationUrgency.MODERATE) {
            return "Within 1 week";
        }
        if (analysis.needsCalibration) {
            return "Within 2 weeks";
        }
        if (analysis.daysSinceFullCycle > 30) {
            return "Within 1 month";
        }
        return "No immediate calibration needed - check monthly";
    }
    
    private long calculateNextCalibrationDate(CalibrationIntelligence.CalibrationAnalysis analysis) {
        long now = System.currentTimeMillis();
        long day = 24 * 60 * 60 * 1000L;
        
        if (analysis.calibrationUrgency == CalibrationIntelligence.CalibrationUrgency.CRITICAL) {
            return now; // Immediately
        }
        if (analysis.calibrationUrgency == CalibrationIntelligence.CalibrationUrgency.HIGH) {
            return now + (2 * day); // 2 days
        }
        if (analysis.calibrationUrgency == CalibrationIntelligence.CalibrationUrgency.MODERATE) {
            return now + (7 * day); // 1 week
        }
        if (analysis.needsCalibration) {
            return now + (14 * day); // 2 weeks
        }
        return now + (30 * day); // 1 month
    }
    
    // ==================================================================================
    //                          NOTIFICATION CONTENT
    // ==================================================================================
    
    private String generateNotificationTitle(CalibrationAdvice advice) {
        switch (advice.urgency) {
            case CRITICAL:
                return "🔴 Critical: Battery Calibration Required";
            case HIGH:
                return "🟠 Battery Calibration Recommended";
            case MODERATE:
                return "🟡 Battery Calibration Suggested";
            case LOW:
                return "💡 Battery Calibration Tip";
            default:
                return "✅ Battery Calibration Status";
        }
    }
    
    private String generateNotificationSummary(CalibrationAdvice advice) {
        if (advice.urgency == CalibrationUrgency.CRITICAL) {
            return "Your battery percentage is severely inaccurate. Tap to see calibration steps.";
        }
        if (advice.urgency == CalibrationUrgency.HIGH) {
            return "Battery drift detected. Calibration recommended to prevent unexpected shutdowns.";
        }
        if (advice.urgency == CalibrationUrgency.MODERATE) {
            return "Minor calibration issues detected. Tap for details.";
        }
        if (advice.needsCalibration) {
            return "Optional calibration available to improve battery accuracy.";
        }
        return "Battery calibration is " + advice.qualityGrade.toLowerCase() + ".";
    }
    
    // ==================================================================================
    //                          DATA CLASSES
    // ==================================================================================
    
    public enum CalibrationUrgency {
        CRITICAL,  // Immediate action required
        HIGH,      // Action needed within 24-48 hours
        MODERATE,  // Action recommended within a week
        LOW,       // Optional improvement
        INFO       // No action needed
    }
    
    public static class CalibrationAdvice {
        public long timestamp;
        public boolean available;
        public String unavailableReason;
        
        // Status
        public float calibrationQuality;
        public String qualityGrade;
        public boolean isCalibrated;
        public boolean needsCalibration;
        
        // Primary recommendation
        public String primaryRecommendation;
        public CalibrationUrgency urgency;
        public String urgencyColor;
        
        // Detailed guidance
        public List<CalibrationStep> calibrationSteps;
        public String explanation;
        public List<String> optimizationTips;
        public List<String> warnings;
        
        // Scheduling
        public String recommendedSchedule;
        public long nextCalibrationDate;
        
        // Notification content
        public String notificationTitle;
        public String notificationSummary;
        
        public static CalibrationAdvice unavailable(String reason) {
            CalibrationAdvice advice = new CalibrationAdvice();
            advice.available = false;
            advice.unavailableReason = reason;
            return advice;
        }
    }
    
    public static class CalibrationStep {
        public int stepNumber;
        public String instruction;
        public String details;
        public String duration;
        public boolean isOptional;
        public String icon;
        public String warning;
        
        public String getFormattedStep() {
            StringBuilder sb = new StringBuilder();
            if (icon != null) sb.append(icon).append(" ");
            sb.append("Step ").append(stepNumber).append(": ").append(instruction);
            if (isOptional) sb.append(" (Optional)");
            return sb.toString();
        }
    }
    
    /**
     * Generate a complete calibration report as formatted text
     */
    public String generateFullReport(CalibrationIntelligence.CalibrationAnalysis analysis) {
        CalibrationAdvice advice = generateAdvice(analysis);
        
        if (!advice.available) {
            return "Unable to generate calibration report: " + advice.unavailableReason;
        }
        
        StringBuilder report = new StringBuilder();
        
        report.append("═══════════════════════════════════════════\n");
        report.append("        BATTERY CALIBRATION REPORT         \n");
        report.append("═══════════════════════════════════════════\n\n");
        
        // Status Section
        report.append("📊 STATUS: ").append(advice.qualityGrade.toUpperCase()).append("\n");
        report.append("Quality Score: ").append(String.format("%.0f", advice.calibrationQuality)).append("/100\n");
        report.append("Calibrated: ").append(advice.isCalibrated ? "Yes ✓" : "No ✗").append("\n\n");
        
        // Recommendation
        report.append("📋 RECOMMENDATION\n");
        report.append("─────────────────\n");
        report.append(advice.primaryRecommendation).append("\n");
        report.append("Urgency: ").append(advice.urgency.toString()).append("\n");
        report.append("Schedule: ").append(advice.recommendedSchedule).append("\n\n");
        
        // Warnings
        if (advice.warnings != null && !advice.warnings.isEmpty()) {
            report.append("⚠️ WARNINGS\n");
            report.append("───────────\n");
            for (String warning : advice.warnings) {
                report.append(warning).append("\n\n");
            }
        }
        
        // Explanation
        report.append("📖 EXPLANATION\n");
        report.append("──────────────\n");
        report.append(advice.explanation).append("\n");
        
        // Calibration Steps
        if (advice.calibrationSteps != null && !advice.calibrationSteps.isEmpty()) {
            report.append("🔧 CALIBRATION STEPS\n");
            report.append("────────────────────\n");
            for (CalibrationStep step : advice.calibrationSteps) {
                report.append("\n").append(step.getFormattedStep()).append("\n");
                report.append("   ").append(step.details).append("\n");
                report.append("   Duration: ").append(step.duration).append("\n");
                if (step.warning != null) {
                    report.append("   ⚠️ ").append(step.warning).append("\n");
                }
            }
            report.append("\n");
        }
        
        // Tips
        if (advice.optimizationTips != null && !advice.optimizationTips.isEmpty()) {
            report.append("💡 OPTIMIZATION TIPS\n");
            report.append("────────────────────\n");
            for (String tip : advice.optimizationTips) {
                report.append("• ").append(tip).append("\n\n");
            }
        }
        
        report.append("═══════════════════════════════════════════\n");
        report.append("Generated by HexNil Battery Intelligence\n");
        
        return report.toString();
    }
}
