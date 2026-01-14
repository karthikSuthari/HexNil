package com.example.hexnil.manager;

import android.content.Context;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.model.BatterySnapshot;
import com.example.hexnil.model.ThermalEvent;
import com.example.hexnil.model.ThermalHeatBucket;
import com.example.hexnil.thermal.ThermalCorrelationEngine;
import com.example.hexnil.thermal.ThermalIntelligence;

/**
 * OEM-Grade Thermal Monitor with comprehensive heat intelligence.
 * 
 * Monitors snapshot data for thermal anomalies and logs ThermalEvent records.
 * Integrates with ThermalIntelligence for spike/sustained heat detection
 * and ThermalCorrelationEngine for root cause analysis.
 */
public class ThermalMonitor {

    private static final String TAG = "ThermalMonitor";
    
    // Use OEM-safe thresholds from ThermalIntelligence
    private static final float CRITICAL_TEMP_C = ThermalIntelligence.TEMP_CRITICAL;  // 46°C
    private static final float WARNING_TEMP_C = ThermalIntelligence.TEMP_WARNING;    // 42°C
    private static final float ELEVATED_TEMP_C = ThermalIntelligence.TEMP_ELEVATED;  // 40°C

    private final HexNilDatabase database;
    private final ThermalIntelligence thermalIntelligence;
    private final ThermalCorrelationEngine correlationEngine;
    
    private ThermalEvent activeEvent;
    private ThermalHeatBucket activeBucket;
    
    // Tracking for enhanced bucket stats
    private int chargingSamples = 0;
    private int notChargingSamples = 0;
    private float chargingTempSum = 0;
    private float notChargingTempSum = 0;
    private int highCpuSamples = 0;
    private int highGpuSamples = 0;
    private float highCpuTempSum = 0;
    private float highGpuTempSum = 0;
    private int screenOnSamples = 0;
    private int startBatteryLevel = -1;

    public ThermalMonitor(Context context, HexNilDatabase database) {
        this.database = database;
        this.thermalIntelligence = new ThermalIntelligence();
        this.correlationEngine = new ThermalCorrelationEngine(database);
    }

    /**
     * Analyze a battery snapshot for thermal anomalies.
     */
    public void analyzeSnapshot(BatterySnapshot snapshot) {
        float tempC = snapshot.getTemperatureCelsius();
        
        // Run thermal intelligence analysis
        ThermalIntelligence.ThermalAnalysis analysis = thermalIntelligence.analyze(snapshot);
        
        // Record for correlation analysis
        correlationEngine.recordSample(snapshot);
        
        // Determine severity using enhanced thresholds
        String severity = getSeverity(tempC);

        if ("normal".equals(severity)) {
            closeActiveEvent(snapshot.timestamp, analysis);
            updateBucket(snapshot, tempC, analysis);
            return;
        }

        if (activeEvent == null) {
            startNewEvent(snapshot, tempC, severity, analysis);
            return;
        }

        updateActiveEvent(snapshot, tempC, severity, analysis);
        updateBucket(snapshot, tempC, analysis);
    }
    
    private String getSeverity(float tempC) {
        if (tempC >= ThermalIntelligence.TEMP_DANGER) return "danger";
        if (tempC >= CRITICAL_TEMP_C) return "critical";
        if (tempC >= WARNING_TEMP_C) return "warning";
        if (tempC >= ELEVATED_TEMP_C) return "elevated";
        return "normal";
    }
    
    private void startNewEvent(BatterySnapshot snapshot, float tempC, String severity, 
                               ThermalIntelligence.ThermalAnalysis analysis) {
        activeEvent = new ThermalEvent();
        activeEvent.timestamp = snapshot.timestamp;
        activeEvent.eventType = tempC >= CRITICAL_TEMP_C ? "threshold_violation" : "spike";
        activeEvent.severity = severity;
        activeEvent.batteryTemp = tempC;
        activeEvent.cpuTemp = 0;
        activeEvent.ambientTemp = 0;
        activeEvent.peakTemperature = tempC;
        activeEvent.temperatureRise = tempC - WARNING_TEMP_C;
        activeEvent.foregroundApp = snapshot.topAppPackage != null ? snapshot.topAppPackage : snapshot.foregroundApp;
        activeEvent.cpuUsage = snapshot.cpuLoadTotal > 0 ? snapshot.cpuLoadTotal : snapshot.cpuUsage;
        activeEvent.gpuUsage = snapshot.gpuLoad;
        activeEvent.isCharging = snapshot.isCharging;
        activeEvent.screenOn = snapshot.screenOn;
        activeEvent.batteryLevel = snapshot.level;
        activeEvent.currentDraw = snapshot.dischargeRate;
        activeEvent.networkType = snapshot.networkState;
        activeEvent.triggerActivity = snapshot.screenOn ? "display_load" : "background_load";
        
        // Set spike detection fields from analysis
        activeEvent.isSpike = analysis.isSpike;
        activeEvent.spikeMagnitude = analysis.spikeMagnitude;
        activeEvent.spikeRateOfChange = analysis.rateOfChange;
        if (analysis.isSpike) {
            activeEvent.spikeStartTime = snapshot.timestamp;
            activeEvent.spikeStartTemp = tempC - analysis.spikeMagnitude;
        }
        
        // Set sustained heat fields
        activeEvent.isSustainedHeat = analysis.isSustainedHeat;
        activeEvent.sustainedState = analysis.sustainedState != null ? analysis.sustainedState.name() : "none";
        activeEvent.sustainedDuration = analysis.sustainedDuration;
        activeEvent.sustainedAvgTemp = analysis.avgTemp;
        activeEvent.sustainedThreshold = ThermalIntelligence.TEMP_WARNING;
        
        // Set correlation fields
        activeEvent.primaryCause = analysis.primaryCause;
        activeEvent.causeConfidence = analysis.causeConfidence;
        
        // Set charging context
        if (snapshot.isCharging) {
            activeEvent.chargeCurrent = Math.abs(snapshot.chargeRate);
            activeEvent.chargeVoltage = snapshot.voltage;
            activeEvent.chargingType = snapshot.chargingType;
            activeEvent.wasFastCharging = Math.abs(snapshot.chargeRate) > 2000;
            activeEvent.chargerPower = (snapshot.voltage * Math.abs(snapshot.chargeRate)) / 1000000f;
        }
        
        // Set OEM threshold fields
        ThermalIntelligence.OEMThresholdResult oemResult = thermalIntelligence.evaluateOEMThresholds(tempC);
        activeEvent.thresholdLevel = oemResult.level;
        activeEvent.nextThreshold = oemResult.nextThreshold;
        activeEvent.headroom = oemResult.headroom;
        activeEvent.passesOEMCompliance = oemResult.passesOEMCompliance;
        activeEvent.requiresAction = oemResult.requiresAction;
        activeEvent.requiresEmergencyAction = oemResult.requiresEmergencyAction;
        
        // Set thermal score and zone
        activeEvent.thermalScore = analysis.thermalScore;
        activeEvent.thermalTrend = analysis.trend != null ? analysis.trend.name() : "stable";
        activeEvent.thermalZone = analysis.zone != null ? analysis.zone.label : "<35";
        
        activeEvent.sampleCount = 1;
        
        activeEvent.id = database.thermalEventDao().insert(activeEvent);
    }
    
    private void updateActiveEvent(BatterySnapshot snapshot, float tempC, String severity,
                                   ThermalIntelligence.ThermalAnalysis analysis) {
        // Update severity to highest seen
        if (getSeverityLevel(severity) > getSeverityLevel(activeEvent.severity)) {
            activeEvent.severity = severity;
        }
        
        activeEvent.peakTemperature = Math.max(activeEvent.peakTemperature, tempC);
        activeEvent.temperatureRise = activeEvent.peakTemperature - WARNING_TEMP_C;
        activeEvent.duration = snapshot.timestamp - activeEvent.timestamp;
        activeEvent.cpuUsage = Math.max(activeEvent.cpuUsage, 
            snapshot.cpuLoadTotal > 0 ? snapshot.cpuLoadTotal : snapshot.cpuUsage);
        activeEvent.gpuUsage = Math.max(activeEvent.gpuUsage, snapshot.gpuLoad);
        activeEvent.isCharging = activeEvent.isCharging || snapshot.isCharging;
        activeEvent.screenOn = activeEvent.screenOn || snapshot.screenOn;
        activeEvent.networkType = snapshot.networkState;
        activeEvent.batteryLevel = snapshot.level;
        activeEvent.currentDraw = snapshot.dischargeRate;
        
        // Update event type based on duration
        if (activeEvent.duration > 120_000) {
            activeEvent.eventType = "sustained_high";
        }
        
        // Update spike/sustained fields
        activeEvent.isSpike = activeEvent.isSpike || analysis.isSpike;
        activeEvent.spikeMagnitude = Math.max(activeEvent.spikeMagnitude, analysis.spikeMagnitude);
        activeEvent.isSustainedHeat = analysis.isSustainedHeat;
        activeEvent.sustainedState = analysis.sustainedState != null ? analysis.sustainedState.name() : activeEvent.sustainedState;
        activeEvent.sustainedDuration = analysis.sustainedDuration;
        activeEvent.sustainedAvgTemp = analysis.avgTemp;
        
        // Update correlation
        if (analysis.causeConfidence > activeEvent.causeConfidence) {
            activeEvent.primaryCause = analysis.primaryCause;
            activeEvent.causeConfidence = analysis.causeConfidence;
        }
        
        // Update OEM threshold tracking
        ThermalIntelligence.OEMThresholdResult oemResult = thermalIntelligence.evaluateOEMThresholds(tempC);
        activeEvent.passesOEMCompliance = activeEvent.passesOEMCompliance && oemResult.passesOEMCompliance;
        activeEvent.requiresAction = activeEvent.requiresAction || oemResult.requiresAction;
        activeEvent.requiresEmergencyAction = activeEvent.requiresEmergencyAction || oemResult.requiresEmergencyAction;
        
        // Update thermal score (use lowest)
        activeEvent.thermalScore = Math.min(activeEvent.thermalScore, analysis.thermalScore);
        activeEvent.thermalTrend = analysis.trend != null ? analysis.trend.name() : activeEvent.thermalTrend;
        
        activeEvent.sampleCount++;
        
        database.thermalEventDao().update(activeEvent);
    }
    
    private int getSeverityLevel(String severity) {
        if (severity == null) return 0;
        switch (severity) {
            case "emergency": return 5;
            case "danger": return 4;
            case "critical": return 3;
            case "warning": return 2;
            case "elevated": return 1;
            default: return 0;
        }
    }

    private void closeActiveEvent(long timestamp, ThermalIntelligence.ThermalAnalysis analysis) {
        if (activeEvent == null) return;
        
        activeEvent.duration = Math.max(0, timestamp - activeEvent.timestamp);
        activeEvent.cooldownTime = activeEvent.duration;
        activeEvent.resolution = "cooled";
        
        // Calculate cooldown rate
        if (activeEvent.duration > 0 && activeEvent.peakTemperature > analysis.currentTemp) {
            float tempDrop = activeEvent.peakTemperature - analysis.currentTemp;
            activeEvent.cooldownRate = (tempDrop / activeEvent.duration) * 60000;  // °C per minute
        }
        
        // Perform root cause analysis on completed event
        ThermalCorrelationEngine.ThermalRootCause rootCause = correlationEngine.analyzeRootCause(activeEvent);
        activeEvent.primaryCause = rootCause.primaryCause;
        activeEvent.causeConfidence = rootCause.confidence;
        activeEvent.degradationRisk = rootCause.degradationRisk;
        
        // Calculate contribution percentages
        for (ThermalCorrelationEngine.ContributingFactor factor : rootCause.contributingFactors) {
            switch (factor.factor) {
                case "charging":
                    activeEvent.chargingContribution = factor.contribution;
                    break;
                case "cpu_load":
                    activeEvent.cpuContribution = factor.contribution;
                    break;
                case "gpu_load":
                    activeEvent.gpuContribution = factor.contribution;
                    break;
                case "foreground_app":
                    activeEvent.appContribution = factor.contribution;
                    break;
            }
        }
        
        activeEvent.correlationNotes = rootCause.analysis;
        activeEvent.degradationNotes = String.join("; ", rootCause.recommendations);
        
        database.thermalEventDao().update(activeEvent);
        activeEvent = null;
    }

    private void updateBucket(BatterySnapshot snapshot, float tempC, ThermalIntelligence.ThermalAnalysis analysis) {
        String bucket = tempBucket(tempC);
        int hourBlock = (int) ((snapshot.timestamp / 1000 / 60 / 60) % 24);

        long now = snapshot.timestamp;
        if (activeBucket == null || !bucket.equals(activeBucket.tempBucket) || activeBucket.hourBlock != hourBlock) {
            persistBucket();
            startNewBucket(now, bucket, hourBlock, tempC, snapshot.level);
        }

        if (activeBucket != null) {
            activeBucket.sampleCount += 1;
            activeBucket.avgTemp = ((activeBucket.avgTemp * (activeBucket.sampleCount - 1)) + tempC) / activeBucket.sampleCount;
            activeBucket.maxTemp = Math.max(activeBucket.maxTemp, tempC);
            activeBucket.minTemp = Math.min(activeBucket.minTemp, tempC);
            
            // Charging correlation
            activeBucket.chargingCount += snapshot.isCharging ? 1 : 0;
            if (snapshot.isCharging) {
                chargingSamples++;
                chargingTempSum += tempC;
                if (Math.abs(snapshot.chargeRate) > 2000) {
                    activeBucket.fastChargingCount++;
                }
                activeBucket.avgChargeCurrent = (activeBucket.avgChargeCurrent * (chargingSamples - 1) + 
                    Math.abs(snapshot.chargeRate)) / chargingSamples;
            } else {
                notChargingSamples++;
                notChargingTempSum += tempC;
            }
            activeBucket.chargingPercent = activeBucket.sampleCount > 0 ? 
                (activeBucket.chargingCount / (float) activeBucket.sampleCount) * 100 : 0;
            activeBucket.avgTempCharging = chargingSamples > 0 ? chargingTempSum / chargingSamples : 0;
            activeBucket.avgTempNotCharging = notChargingSamples > 0 ? notChargingTempSum / notChargingSamples : 0;
            
            // CPU/GPU correlation
            int cpuLoad = snapshot.cpuLoadTotal > 0 ? snapshot.cpuLoadTotal : snapshot.cpuUsage;
            if (cpuLoad >= 60) {
                activeBucket.highCpuCount++;
                highCpuSamples++;
                highCpuTempSum += tempC;
            }
            if (snapshot.gpuLoad >= 60) {
                activeBucket.highGpuCount++;
                highGpuSamples++;
                highGpuTempSum += tempC;
            }
            activeBucket.avgCpuLoad = (activeBucket.avgCpuLoad * (activeBucket.sampleCount - 1) + cpuLoad) / activeBucket.sampleCount;
            activeBucket.avgGpuLoad = (activeBucket.avgGpuLoad * (activeBucket.sampleCount - 1) + snapshot.gpuLoad) / activeBucket.sampleCount;
            activeBucket.avgTempHighCpu = highCpuSamples > 0 ? highCpuTempSum / highCpuSamples : 0;
            activeBucket.avgTempHighGpu = highGpuSamples > 0 ? highGpuTempSum / highGpuSamples : 0;
            
            // Screen and app tracking
            if (snapshot.screenOn) {
                screenOnSamples++;
            }
            activeBucket.screenOnCount = screenOnSamples;
            activeBucket.screenOnPercent = activeBucket.sampleCount > 0 ?
                (screenOnSamples / (float) activeBucket.sampleCount) * 100 : 0;
            
            if (snapshot.topAppPackage != null) {
                activeBucket.topForegroundApp = snapshot.topAppPackage;
            }
            
            // Event tracking
            if (analysis.isSpike) {
                activeBucket.spikeCount++;
            }
            if (analysis.isSustainedHeat) {
                activeBucket.sustainedHeatCount++;
            }
            if ("warning".equals(analysis.state.name().toLowerCase())) {
                activeBucket.warningEventCount++;
            }
            if (analysis.state.ordinal() >= ThermalIntelligence.ThermalState.CRITICAL.ordinal()) {
                activeBucket.criticalEventCount++;
            }
            
            // Battery tracking
            activeBucket.avgBatteryLevel = (activeBucket.avgBatteryLevel * (activeBucket.sampleCount - 1) + 
                snapshot.level) / activeBucket.sampleCount;
            if (startBatteryLevel >= 0) {
                activeBucket.batteryDrain = startBatteryLevel - snapshot.level;
            }
            
            // OEM compliance
            activeBucket.exceedsOEMLimit = activeBucket.exceedsOEMLimit || 
                tempC >= ThermalIntelligence.TEMP_CRITICAL;
            if (activeBucket.exceedsOEMLimit) {
                activeBucket.oemComplianceStatus = "fail";
            } else if (tempC >= ThermalIntelligence.TEMP_WARNING) {
                activeBucket.oemComplianceStatus = "warning";
            }
            
            // Thermal score
            activeBucket.thermalScore = (activeBucket.thermalScore * (activeBucket.sampleCount - 1) + 
                analysis.thermalScore) / activeBucket.sampleCount;
            
            // Duration tracking
            if (activeBucket.sampleCount > 1) {
                long lastTs = activeBucket.endTime == 0 ? activeBucket.startTime : activeBucket.endTime;
                activeBucket.durationMs += Math.max(0, now - lastTs);
            }
            activeBucket.endTime = now;
        }
    }
    
    private void startNewBucket(long timestamp, String bucket, int hourBlock, float tempC, int batteryLevel) {
        activeBucket = new ThermalHeatBucket();
        activeBucket.startTime = timestamp;
        activeBucket.tempBucket = bucket;
        activeBucket.hourBlock = hourBlock;
        activeBucket.maxTemp = tempC;
        activeBucket.avgTemp = tempC;
        activeBucket.minTemp = tempC;
        activeBucket.sampleCount = 0;
        activeBucket.durationMs = 0;
        activeBucket.thermalScore = 100;
        activeBucket.oemComplianceStatus = "pass";
        
        // Reset tracking variables
        chargingSamples = 0;
        notChargingSamples = 0;
        chargingTempSum = 0;
        notChargingTempSum = 0;
        highCpuSamples = 0;
        highGpuSamples = 0;
        highCpuTempSum = 0;
        highGpuTempSum = 0;
        screenOnSamples = 0;
        startBatteryLevel = batteryLevel;
    }

    private void persistBucket() {
        if (activeBucket == null) return;
        
        // Calculate variance before persisting
        // Note: This is a simplified variance calculation
        float variance = (activeBucket.maxTemp - activeBucket.minTemp) / 2;
        activeBucket.tempVariance = variance;
        
        // Calculate degradation risk based on bucket
        if (">45".equals(activeBucket.tempBucket)) {
            activeBucket.degradationRisk = 0.7f + (activeBucket.durationMs / 600000f) * 0.3f;
        } else if ("40-45".equals(activeBucket.tempBucket)) {
            activeBucket.degradationRisk = 0.2f + (activeBucket.durationMs / 1200000f) * 0.3f;
        } else {
            activeBucket.degradationRisk = 0;
        }
        activeBucket.degradationRisk = Math.min(1.0f, activeBucket.degradationRisk);
        
        database.thermalHeatBucketDao().insert(activeBucket);
        activeBucket = null;
    }

    private String tempBucket(float tempC) {
        if (tempC < 35f) return "<35";
        if (tempC < 40f) return "35-40";
        if (tempC < 45f) return "40-45";
        return ">45";
    }
    
    /**
     * Get the thermal intelligence engine for external analysis.
     */
    public ThermalIntelligence getThermalIntelligence() {
        return thermalIntelligence;
    }
    
    /**
     * Get the correlation engine for external analysis.
     */
    public ThermalCorrelationEngine getCorrelationEngine() {
        return correlationEngine;
    }
    
    /**
     * Force persist the current bucket (e.g., on service stop).
     */
    public void flush() {
        persistBucket();
        if (activeEvent != null) {
            activeEvent.resolution = "service_stopped";
            database.thermalEventDao().update(activeEvent);
            activeEvent = null;
        }
    }
}
