package com.example.hexnil.manager;

import android.util.Log;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.model.AnomalyRecord;
import com.example.hexnil.model.AppBatteryUsage;
import com.example.hexnil.model.BatteryHealth;
import com.example.hexnil.model.BatterySnapshot;
import com.example.hexnil.model.ThermalEvent;
import com.example.hexnil.model.ChargingSession;
import com.example.hexnil.model.DrainEvent;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * AI-powered anomaly detection engine.
 * Analyzes battery data to detect unusual patterns and stores detailed anomaly records.
 */
public class AnomalyDetector {
    
    private static final String TAG = "AnomalyDetector";
    
    // Thresholds for anomaly detection
    private static final float APP_DRAIN_THRESHOLD = 15f;        // %/hr considered high
    private static final float APP_DRAIN_CRITICAL = 25f;         // %/hr considered critical
    private static final float TEMP_WARNING = 40f;               // °C
    private static final float TEMP_CRITICAL = 45f;              // °C
    private static final float HEALTH_WARNING = 85f;             // %
    private static final float HEALTH_CRITICAL = 75f;            // %
    private static final float DEGRADATION_RATE_HIGH = 0.5f;     // %/month
    private static final float IDLE_DRAIN_HIGH = 2f;             // %/hr in idle
    private static final float CHARGING_EFFICIENCY_LOW = 75f;    // %
    private static final int WAKELOCK_COUNT_HIGH = 50;           // wakelocks in period
    private static final long BACKGROUND_TIME_HIGH = 30 * 60 * 1000; // 30 min background
    
    private final HexNilDatabase database;
    private final SimpleDateFormat dateFormat;
    
    public AnomalyDetector(HexNilDatabase database) {
        this.database = database;
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    }
    
    /**
     * Run full anomaly detection on recent data
     */
    public List<AnomalyRecord> detectAnomalies(long periodStart, long periodEnd) {
        List<AnomalyRecord> anomalies = new ArrayList<>();
        
        try {
            // Detect app drain anomalies
            anomalies.addAll(detectAppDrainAnomalies(periodStart, periodEnd));
            
            // Detect thermal anomalies
            anomalies.addAll(detectThermalAnomalies(periodStart, periodEnd));
            
            // Detect battery health anomalies
            anomalies.addAll(detectHealthAnomalies());
            
            // Detect charging anomalies
            anomalies.addAll(detectChargingAnomalies(periodStart));
            
            // Detect usage pattern anomalies
            anomalies.addAll(detectUsagePatternAnomalies(periodStart, periodEnd));
            
            // Detect idle drain anomalies
            anomalies.addAll(detectIdleDrainAnomalies(periodStart));
            
            // Store all detected anomalies
            for (AnomalyRecord anomaly : anomalies) {
                // Check for duplicates (don't store same anomaly within 1 hour)
                AnomalyRecord existing = database.anomalyRecordDao().findSimilarRecent(
                        anomaly.anomalyType, 
                        anomaly.sourceApp, 
                        System.currentTimeMillis() - 3600000);
                
                if (existing == null) {
                    long id = database.anomalyRecordDao().insert(anomaly);
                    Log.i(TAG, "Anomaly detected and stored: " + anomaly.toString() + " (id=" + id + ")");
                } else {
                    // Update occurrence count for recurring anomaly
                    existing.occurrenceCount++;
                    existing.isRecurring = true;
                    database.anomalyRecordDao().update(existing);
                    Log.i(TAG, "Recurring anomaly updated: " + existing.title + " (count=" + existing.occurrenceCount + ")");
                }
            }
            
            Log.d(TAG, "Anomaly detection complete: " + anomalies.size() + " anomalies found");
            
        } catch (Exception e) {
            Log.e(TAG, "Error during anomaly detection", e);
        }
        
        return anomalies;
    }
    
    /**
     * Detect excessive app battery drain
     */
    private List<AnomalyRecord> detectAppDrainAnomalies(long periodStart, long periodEnd) {
        List<AnomalyRecord> anomalies = new ArrayList<>();
        
        try {
            List<AppBatteryUsage> appUsages = database.appBatteryUsageDao()
                    .getAllAppHistoryInRange(periodStart, periodEnd);
            
            if (appUsages == null || appUsages.isEmpty()) return anomalies;
            
            for (AppBatteryUsage usage : appUsages) {
                if (usage.drainPerHour > APP_DRAIN_THRESHOLD) {
                    String severity = usage.drainPerHour > APP_DRAIN_CRITICAL ? "critical" : "high";
                    float expectedDrain = usage.isSystemApp ? 5f : 8f; // Expected normal drain
                    
                    String description = buildAppDrainDescription(usage);
                    
                    AnomalyRecord anomaly = AnomalyRecord.appDrainAnomaly(
                            usage.packageName,
                            usage.appName,
                            expectedDrain,
                            usage.drainPerHour,
                            description
                    );
                    
                    anomaly.periodStart = periodStart;
                    anomaly.periodEnd = periodEnd;
                    anomaly.sourceComponent = "AnomalyDetector.detectAppDrainAnomalies";
                    anomaly.technicalDetails = buildAppTechnicalDetails(usage);
                    anomaly.recommendation = buildAppRecommendation(usage);
                    anomaly.rootCause = determineAppDrainCause(usage);
                    anomaly.impact = "Battery draining " + String.format("%.1f", usage.deviationFromAverage) + 
                            "% faster than average for this app category";
                    
                    anomalies.add(anomaly);
                }
                
                // Check for excessive background activity
                if (usage.hasExcessiveBackground || usage.backgroundTime > BACKGROUND_TIME_HIGH) {
                    AnomalyRecord anomaly = AnomalyRecord.usagePatternAnomaly(
                            "Excessive Background",
                            usage.appName + " spent " + (usage.backgroundTime / 60000) + 
                                    " minutes in background consuming battery",
                            usage.getDrainScore()
                    );
                    anomaly.sourceApp = usage.packageName;
                    anomaly.sourceAppName = usage.appName;
                    anomaly.recommendation = "Restrict background activity for " + usage.appName + 
                            " in Settings > Apps > " + usage.appName + " > Battery";
                    anomalies.add(anomaly);
                }
                
                // Check for high wakelock count
                if (usage.wakelockCount > WAKELOCK_COUNT_HIGH) {
                    AnomalyRecord anomaly = AnomalyRecord.usagePatternAnomaly(
                            "High Wakelock Usage",
                            usage.appName + " acquired " + usage.wakelockCount + 
                                    " wakelocks preventing device sleep",
                            (float) usage.wakelockCount
                    );
                    anomaly.sourceApp = usage.packageName;
                    anomaly.sourceAppName = usage.appName;
                    anomaly.severity = usage.wakelockCount > 100 ? "high" : "medium";
                    anomaly.recommendation = "Consider uninstalling or restricting " + usage.appName;
                    anomalies.add(anomaly);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error detecting app drain anomalies", e);
        }
        
        return anomalies;
    }
    
    /**
     * Detect thermal anomalies
     */
    private List<AnomalyRecord> detectThermalAnomalies(long periodStart, long periodEnd) {
        List<AnomalyRecord> anomalies = new ArrayList<>();
        
        try {
            // Check recent snapshots for high temperature
            List<BatterySnapshot> snapshots = database.batterySnapshotDao()
                    .getSnapshotsSince(periodStart);
            
            if (snapshots == null || snapshots.isEmpty()) return anomalies;
            
            float maxTemp = 0;
            float sumTemp = 0;
            int highTempCount = 0;
            BatterySnapshot maxTempSnapshot = null;
            
            for (BatterySnapshot snapshot : snapshots) {
                float temp = snapshot.getTemperatureCelsius();
                sumTemp += temp;
                
                if (temp > maxTemp) {
                    maxTemp = temp;
                    maxTempSnapshot = snapshot;
                }
                
                if (temp > TEMP_WARNING) {
                    highTempCount++;
                }
            }
            
            float avgTemp = sumTemp / snapshots.size();
            
            if (maxTemp > TEMP_WARNING) {
                String description = String.format(
                        "Device reached %.1f°C (max) with average of %.1f°C. " +
                        "High temperature was recorded %d times out of %d measurements. " +
                        "Detected at: %s",
                        maxTemp, avgTemp, highTempCount, snapshots.size(),
                        maxTempSnapshot != null ? dateFormat.format(new Date(maxTempSnapshot.timestamp)) : "unknown"
                );
                
                AnomalyRecord anomaly = AnomalyRecord.thermalAnomaly(maxTemp, avgTemp, description);
                anomaly.periodStart = periodStart;
                anomaly.periodEnd = periodEnd;
                anomaly.dataPointsAnalyzed = snapshots.size();
                anomaly.sourceComponent = "AnomalyDetector.detectThermalAnomalies";
                
                if (maxTempSnapshot != null) {
                    anomaly.batteryLevel = maxTempSnapshot.level;
                    anomaly.wasCharging = maxTempSnapshot.isCharging;
                    anomaly.screenOn = maxTempSnapshot.screenOn;
                }
                
                anomaly.technicalDetails = String.format(
                        "{\"maxTemp\":%.1f,\"avgTemp\":%.1f,\"highTempCount\":%d,\"totalSamples\":%d}",
                        maxTemp, avgTemp, highTempCount, snapshots.size()
                );
                
                if (maxTemp > TEMP_CRITICAL) {
                    anomaly.recommendation = "URGENT: Stop charging immediately and let device cool down. " +
                            "Avoid using while charging. Check for hardware issues if recurring.";
                    anomaly.rootCause = "Possible causes: Fast charging in hot environment, " +
                            "heavy usage while charging, or battery/charging circuit issue.";
                } else {
                    anomaly.recommendation = "Reduce heavy usage during charging. " +
                            "Use a well-ventilated area for charging.";
                    anomaly.rootCause = "Likely caused by heavy app usage during charging.";
                }
                
                anomaly.impact = "High temperatures accelerate battery degradation and can cause safety issues.";
                
                anomalies.add(anomaly);
            }
            
            // Check thermal events
            List<ThermalEvent> thermalEvents = database.thermalEventDao().getEventsSince(periodStart);
            if (thermalEvents != null && !thermalEvents.isEmpty()) {
                for (ThermalEvent event : thermalEvents) {
                    if (event.peakTemperature > TEMP_CRITICAL) {
                        AnomalyRecord anomaly = new AnomalyRecord();
                        anomaly.anomalyType = "thermal";
                        anomaly.severity = "critical";
                        anomaly.title = "Critical Thermal Event";
                        anomaly.description = String.format(
                                "Critical thermal event: Peak %.1f°C lasting %d seconds. " +
                                "Event type: %s. Trigger: %s",
                                event.peakTemperature, event.duration / 1000,
                                event.eventType != null ? event.eventType : "unknown",
                                event.triggerActivity != null ? event.triggerActivity : "unknown"
                        );
                        anomaly.expectedValue = 40f;
                        anomaly.actualValue = event.peakTemperature;
                        anomaly.metricName = "peakTemperature";
                        anomaly.metricUnit = "°C";
                        anomaly.confidenceScore = 98f;
                        anomaly.recommendation = "Investigate thermal management. Check for blocked vents or heavy processing apps.";
                        
                        anomalies.add(anomaly);
                    }
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error detecting thermal anomalies", e);
        }
        
        return anomalies;
    }
    
    /**
     * Detect battery health anomalies
     */
    private List<AnomalyRecord> detectHealthAnomalies() {
        List<AnomalyRecord> anomalies = new ArrayList<>();
        
        try {
            BatteryHealth health = database.batteryHealthDao().getLatest();
            if (health == null) return anomalies;
            
            // Check overall health
            if (health.healthPercentage < HEALTH_WARNING) {
                String description = String.format(
                        "Battery health is at %.1f%% (design capacity: %.0f mAh, current: %.0f mAh). " +
                        "Cycle count: %.0f cycles. Degradation rate: %.2f%%/month. " +
                        "Estimated remaining life: %.0f months.",
                        health.healthPercentage,
                        health.designCapacityMah,
                        health.currentCapacityMah,
                        health.cycleCount,
                        health.fadePerMonth,
                        health.predictedEOL
                );
                
                AnomalyRecord anomaly = AnomalyRecord.healthAnomaly(
                        health.healthPercentage,
                        health.degradationRate,
                        description
                );
                
                anomaly.sourceComponent = "AnomalyDetector.detectHealthAnomalies";
                anomaly.technicalDetails = String.format(
                        "{\"health\":%.1f,\"cycles\":%.0f,\"fadePerMonth\":%.2f,\"eol\":%.0f," +
                        "\"designCap\":%.0f,\"currentCap\":%.0f,\"resistance\":%.0f}",
                        health.healthPercentage, health.cycleCount, health.fadePerMonth,
                        health.predictedEOL, health.designCapacityMah, health.currentCapacityMah,
                        health.internalResistance
                );
                
                if (health.healthPercentage < HEALTH_CRITICAL) {
                    anomaly.recommendation = "Battery replacement recommended. " +
                            "Current capacity is significantly degraded.";
                    anomaly.impact = "Significantly reduced battery life and potential unexpected shutdowns.";
                } else {
                    anomaly.recommendation = "Monitor battery closely. Consider replacement planning. " +
                            "Avoid deep discharges and extreme temperatures.";
                    anomaly.impact = "Reduced battery capacity affecting daily usage.";
                }
                
                anomaly.rootCause = determineHealthDegradationCause(health);
                
                anomalies.add(anomaly);
            }
            
            // Check for abnormal degradation rate
            if (health.fadePerMonth > DEGRADATION_RATE_HIGH) {
                AnomalyRecord anomaly = new AnomalyRecord();
                anomaly.anomalyType = "battery_health";
                anomaly.severity = "high";
                anomaly.title = "Accelerated Battery Degradation";
                anomaly.description = String.format(
                        "Battery is degrading at %.2f%%/month which is faster than normal (expected <0.3%%/month). " +
                        "At this rate, battery will reach 80%% health in approximately %.0f months.",
                        health.fadePerMonth,
                        (health.healthPercentage - 80) / health.fadePerMonth
                );
                anomaly.expectedValue = 0.3f;
                anomaly.actualValue = health.fadePerMonth;
                anomaly.deviationPercent = ((health.fadePerMonth - 0.3f) / 0.3f) * 100;
                anomaly.metricName = "fadePerMonth";
                anomaly.metricUnit = "%/month";
                anomaly.confidenceScore = 85f;
                anomaly.recommendation = "Reduce exposure to heat, avoid deep discharges, and use moderate charging.";
                anomaly.rootCause = "Fast degradation often caused by heat exposure, frequent fast charging, or deep discharge cycles.";
                
                anomalies.add(anomaly);
            }
            
            // Check for abnormal wear indicators
            if (health.hasAbnormalWear) {
                AnomalyRecord anomaly = new AnomalyRecord();
                anomaly.anomalyType = "battery_health";
                anomaly.severity = health.hasSwellingRisk ? "critical" : "high";
                anomaly.title = "Abnormal Battery Wear Detected";
                anomaly.description = health.abnormalityDetails != null ? 
                        health.abnormalityDetails : "Abnormal wear patterns detected in battery metrics.";
                anomaly.confidenceScore = 80f;
                
                if (health.hasSwellingRisk) {
                    anomaly.recommendation = "URGENT: Stop using device immediately. Battery may be swelling. " +
                            "Seek professional service.";
                    anomaly.impact = "Potential safety hazard.";
                } else {
                    anomaly.recommendation = "Schedule battery inspection. Monitor for physical changes.";
                    anomaly.impact = "Reduced reliability and potential safety concerns.";
                }
                
                anomalies.add(anomaly);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error detecting health anomalies", e);
        }
        
        return anomalies;
    }
    
    /**
     * Detect charging anomalies
     */
    private List<AnomalyRecord> detectChargingAnomalies(long since) {
        List<AnomalyRecord> anomalies = new ArrayList<>();
        
        try {
            List<ChargingSession> sessions = database.chargingSessionDao().getSessionsSince(since);
            if (sessions == null || sessions.isEmpty()) return anomalies;
            
            for (ChargingSession session : sessions) {
                // Check for low charging efficiency
                if (session.chargingEfficiency > 0 && session.chargingEfficiency < CHARGING_EFFICIENCY_LOW) {
                    AnomalyRecord anomaly = AnomalyRecord.chargingAnomaly(
                            "Low Efficiency",
                            session.chargingEfficiency,
                            String.format("Charging session had only %.1f%% efficiency. " +
                                    "Session duration: %d minutes. Energy delivered: %.0f mAh.",
                                    session.chargingEfficiency,
                                    session.chargingDuration / 60000,
                                    session.energyDelivered)
                    );
                    anomaly.recommendation = "Check charger and cable. Use original charger if possible.";
                    anomaly.rootCause = "Low efficiency may indicate faulty charger, damaged cable, or battery issues.";
                    anomalies.add(anomaly);
                }
                
                // Check for excessive temperature during charging
                if (session.maxTemperature > TEMP_WARNING) {
                    AnomalyRecord anomaly = AnomalyRecord.chargingAnomaly(
                            "High Temperature While Charging",
                            session.avgTemperature,
                            String.format("Device reached %.1f°C during charging (avg: %.1f°C). " +
                                    "Safe charging temperature is below 40°C.",
                                    session.maxTemperature, session.avgTemperature)
                    );
                    anomaly.severity = session.maxTemperature > TEMP_CRITICAL ? "critical" : "high";
                    anomaly.recommendation = "Charge in cooler environment. Avoid using device while charging.";
                    anomalies.add(anomaly);
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error detecting charging anomalies", e);
        }
        
        return anomalies;
    }
    
    /**
     * Detect unusual usage patterns
     */
    private List<AnomalyRecord> detectUsagePatternAnomalies(long periodStart, long periodEnd) {
        List<AnomalyRecord> anomalies = new ArrayList<>();
        
        try {
            // Get drain events
            List<DrainEvent> drainEvents = database.drainEventDao().getEventsSince(periodStart);
            if (drainEvents == null) return anomalies;
            
            float totalDrain = 0;
            int highDrainCount = 0;
            
            for (DrainEvent event : drainEvents) {
                totalDrain += event.drainAmount;
                if (event.drainRatePerHour > 10) {
                    highDrainCount++;
                }
            }
            
            // Check for overall high drain pattern
            if (highDrainCount > drainEvents.size() / 2 && drainEvents.size() > 5) {
                AnomalyRecord anomaly = AnomalyRecord.usagePatternAnomaly(
                        "Consistently High Drain",
                        String.format("%d out of %d drain events showed high drain rates (>10%%/hr). " +
                                "Total battery drained: %.1f%%",
                                highDrainCount, drainEvents.size(), totalDrain),
                        (float) highDrainCount / drainEvents.size() * 100
                );
                anomaly.recommendation = "Review installed apps and background activity. " +
                        "Consider factory reset if issue persists.";
                anomalies.add(anomaly);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error detecting usage pattern anomalies", e);
        }
        
        return anomalies;
    }
    
    /**
     * Detect idle drain anomalies
     */
    private List<AnomalyRecord> detectIdleDrainAnomalies(long since) {
        List<AnomalyRecord> anomalies = new ArrayList<>();
        
        try {
            Float avgIdleDrainObj = database.drainEventDao().getAverageIdleDrain(since);
            float avgIdleDrain = avgIdleDrainObj != null ? avgIdleDrainObj : 0f;
            
            if (avgIdleDrain > IDLE_DRAIN_HIGH) {
                AnomalyRecord anomaly = new AnomalyRecord();
                anomaly.anomalyType = "usage_pattern";
                anomaly.severity = avgIdleDrain > 5 ? "high" : "medium";
                anomaly.title = "High Idle Battery Drain";
                anomaly.description = String.format(
                        "Device is draining %.1f%%/hr while idle (expected <1%%/hr). " +
                        "This indicates apps or services running in background.",
                        avgIdleDrain
                );
                anomaly.expectedValue = 1f;
                anomaly.actualValue = avgIdleDrain;
                anomaly.deviationPercent = (avgIdleDrain - 1f) / 1f * 100;
                anomaly.metricName = "idleDrainPerHour";
                anomaly.metricUnit = "%/hr";
                anomaly.confidenceScore = 90f;
                anomaly.recommendation = "Check for apps with high background activity. " +
                        "Enable battery saver or restrict background apps.";
                anomaly.rootCause = "Background apps, sync services, or malware may be causing excessive idle drain.";
                
                anomalies.add(anomaly);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error detecting idle drain anomalies", e);
        }
        
        return anomalies;
    }
    
    // =========================================================================
    // HELPER METHODS
    // =========================================================================
    
    private String buildAppDrainDescription(AppBatteryUsage usage) {
        StringBuilder sb = new StringBuilder();
        sb.append(usage.appName).append(" is consuming battery at ").append(String.format("%.1f", usage.drainPerHour)).append("%/hr ");
        sb.append("(").append(usage.batteryPercentUsed).append("% total). ");
        
        if (usage.foregroundTime > 0) {
            sb.append("Foreground time: ").append(usage.foregroundTime / 60000).append(" min. ");
        }
        if (usage.backgroundTime > 0) {
            sb.append("Background time: ").append(usage.backgroundTime / 60000).append(" min. ");
        }
        if (usage.wakelockCount > 0) {
            sb.append("Wakelocks: ").append(usage.wakelockCount).append(". ");
        }
        
        sb.append("Detected at: ").append(dateFormat.format(new Date(usage.timestamp)));
        
        return sb.toString();
    }
    
    private String buildAppTechnicalDetails(AppBatteryUsage usage) {
        return String.format(
                "{\"package\":\"%s\",\"drain\":%.2f,\"foreground\":%d,\"background\":%d," +
                "\"wakelocks\":%d,\"wakelockTime\":%d,\"networkBytes\":%d,\"alarms\":%d," +
                "\"isSystem\":%b,\"efficiency\":\"%s\"}",
                usage.packageName, usage.drainPerHour, usage.foregroundTime, usage.backgroundTime,
                usage.wakelockCount, usage.wakelockTime, usage.networkBytes, usage.alarmCount,
                usage.isSystemApp, usage.efficiencyRating
        );
    }
    
    private String buildAppRecommendation(AppBatteryUsage usage) {
        if ("problematic".equals(usage.efficiencyRating)) {
            return "Consider uninstalling " + usage.appName + " or finding an alternative. " +
                    "If needed, restrict its background activity.";
        } else if (usage.hasExcessiveBackground) {
            return "Restrict background activity: Settings > Apps > " + usage.appName + 
                    " > Battery > Background restriction";
        } else if (usage.wakelockCount > WAKELOCK_COUNT_HIGH) {
            return "This app prevents device sleep. Consider restricting or replacing it.";
        }
        return "Monitor this app. If drain continues, consider restricting background activity.";
    }
    
    private String determineAppDrainCause(AppBatteryUsage usage) {
        if (usage.wakelockCount > WAKELOCK_COUNT_HIGH) {
            return "Excessive wakelocks preventing device sleep";
        } else if (usage.backgroundTime > usage.foregroundTime * 2) {
            return "High background activity relative to foreground usage";
        } else if (usage.networkBytes > 100 * 1024 * 1024) { // 100MB
            return "High network data usage";
        } else if (usage.alarmCount > 20) {
            return "Frequent alarm scheduling waking device";
        }
        return "General high battery consumption during active use";
    }
    
    private String determineHealthDegradationCause(BatteryHealth health) {
        StringBuilder causes = new StringBuilder();
        
        if (health.cycleCount > 500) {
            causes.append("High cycle count (").append(String.format("%.0f", health.cycleCount)).append(" cycles). ");
        }
        if (health.deepCycleCount > 50) {
            causes.append("Many deep discharge cycles. ");
        }
        if (health.fadePerMonth > 0.5f) {
            causes.append("Accelerated degradation rate. ");
        }
        
        if (causes.length() == 0) {
            causes.append("Normal aging and usage patterns.");
        }
        
        return causes.toString();
    }
}
