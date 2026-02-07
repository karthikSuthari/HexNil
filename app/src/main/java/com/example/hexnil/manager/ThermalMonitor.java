package com.example.hexnil.manager;

import android.content.Context;
import android.net.TrafficStats;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.model.BatterySnapshot;
import com.example.hexnil.model.ThermalEvent;
import com.example.hexnil.model.ThermalHeatBucket;
import com.example.hexnil.thermal.ThermalCorrelationEngine;
import com.example.hexnil.thermal.ThermalIntelligence;
import com.example.hexnil.util.ActivityDetector;
import com.example.hexnil.util.ThermalZoneReader;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OEM-Grade Thermal Monitor with comprehensive heat intelligence.
 * 
 * Monitors snapshot data for thermal anomalies and logs ThermalEvent records.
 * Integrates with ThermalIntelligence for spike/sustained heat detection
 * and ThermalCorrelationEngine for root cause analysis.
 * 
 * Populates ALL columns in thermal_heat_buckets and thermal_events tables:
 * - Activity correlations (gaming, camera, navigation)
 * - Thermal zones (CPU, GPU, skin, ambient)
 * - Network activity tracking
 * - App temperature contribution analysis
 * - Temperature sample history
 */
public class ThermalMonitor {

    private static final String TAG = "ThermalMonitor";
    
    // Use OEM-safe thresholds from ThermalIntelligence
    private static final float CRITICAL_TEMP_C = ThermalIntelligence.TEMP_CRITICAL;  // 46°C
    private static final float WARNING_TEMP_C = ThermalIntelligence.TEMP_WARNING;    // 42°C
    private static final float ELEVATED_TEMP_C = ThermalIntelligence.TEMP_ELEVATED;  // 40°C
    
    // Network activity threshold (bytes per second)
    private static final long HEAVY_NETWORK_THRESHOLD = 500_000; // 500 KB/s

    private final Context context;
    private final HexNilDatabase database;
    private final ThermalIntelligence thermalIntelligence;
    private final ThermalCorrelationEngine correlationEngine;
    private final ActivityDetector activityDetector;
    
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
    
    // NEW: Activity tracking for bucket
    private int gamingSamples = 0;
    private int cameraSamples = 0;
    private int navigationSamples = 0;
    private Map<String, Integer> networkTypeCounts = new HashMap<>();
    private Map<String, Float> appTempSums = new HashMap<>();
    private Map<String, Integer> appSampleCounts = new HashMap<>();
    
    // NEW: Network activity tracking
    private long lastNetworkBytesRx = 0;
    private long lastNetworkBytesTx = 0;
    private long lastNetworkCheckTime = 0;
    
    // NEW: Temperature samples for event
    private List<TempSample> eventTempSamples = new ArrayList<>();

    // ML Infrastructure
    private final DeviceProfileManager deviceProfileManager;

    private static class TempSample {
        long timestamp;
        float temp;
        TempSample(long ts, float t) { timestamp = ts; temp = t; }
    }

    public ThermalMonitor(Context context, HexNilDatabase database) {
        this.context = context;
        this.database = database;
        this.thermalIntelligence = new ThermalIntelligence();
        this.correlationEngine = new ThermalCorrelationEngine(database);
        this.activityDetector = new ActivityDetector(context);
        this.deviceProfileManager = new DeviceProfileManager(context, database);
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
        
        // Read thermal zones for CPU, GPU, skin, ambient temperatures
        ThermalZoneReader.ThermalZoneData thermalZones = ThermalZoneReader.readThermalZones(tempC);
        activeEvent.cpuTemp = thermalZones.cpuTemp;
        activeEvent.skinTemp = thermalZones.skinTemp;
        activeEvent.ambientTemp = thermalZones.ambientEstimate;
        activeEvent.thermalZones = thermalZones.toJson();
        
        activeEvent.peakTemperature = tempC;
        activeEvent.temperatureRise = tempC - WARNING_TEMP_C;
        
        // Get foreground app
        String foregroundApp = snapshot.topAppPackage != null ? snapshot.topAppPackage : snapshot.foregroundApp;
        activeEvent.foregroundApp = foregroundApp;
        
        // Detect activity type from foreground app
        ActivityDetector.ActivityResult activity = activityDetector.detectActivity(foregroundApp);
        activeEvent.isGaming = activity.isGaming;
        activeEvent.isCamera = activity.isCamera;
        activeEvent.isNavigation = activity.isNavigation;
        
        int cpu = (snapshot.cpuLoadTotal != null && snapshot.cpuLoadTotal > 0) ? snapshot.cpuLoadTotal : snapshot.cpuUsage;
        int gpu = snapshot.gpuLoad != null ? snapshot.gpuLoad : 0;
        activeEvent.cpuUsage = cpu;
        activeEvent.gpuUsage = gpu;
        activeEvent.isCharging = snapshot.isCharging;
        activeEvent.screenOn = snapshot.isScreenOnOrFalse();
        activeEvent.batteryLevel = snapshot.level;
        activeEvent.currentDraw = snapshot.dischargeRate;
        activeEvent.networkType = snapshot.networkState;

        // Detect heavy network activity
        activeEvent.heavyNetworkActivity = isHeavyNetworkActivity();

        // ML Infrastructure: populate device fingerprint
        try {
            activeEvent.deviceFingerprint = deviceProfileManager.getCurrentFingerprint();
        } catch (Exception ignored) { }

        // Set trigger activity based on detected type
        if (activity.isGaming) {
            activeEvent.triggerActivity = "gaming";
        } else if (activity.isCamera) {
            activeEvent.triggerActivity = "camera";
        } else if (activity.isNavigation) {
            activeEvent.triggerActivity = "navigation";
        } else if (snapshot.isScreenOnOrFalse()) {
            activeEvent.triggerActivity = "display_load";
        } else {
            activeEvent.triggerActivity = "background_load";
        }
        
        // Set spike detection fields from analysis
        activeEvent.isSpike = analysis.isSpike;
        activeEvent.spikeMagnitude = analysis.spikeMagnitude;
        activeEvent.spikeRateOfChange = analysis.rateOfChange;
        if (analysis.isSpike) {
            activeEvent.spikeStartTime = snapshot.timestamp;
            activeEvent.spikeStartTemp = tempC - analysis.spikeMagnitude;
            activeEvent.spikeCause = activity.activityType;
        }
        
        // Initialize temperature samples tracking for this event
        eventTempSamples.clear();
        eventTempSamples.add(new TempSample(snapshot.timestamp, tempC));
        
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
        int cpu = (snapshot.cpuLoadTotal != null && snapshot.cpuLoadTotal > 0) ? snapshot.cpuLoadTotal : snapshot.cpuUsage;
        int gpu = snapshot.gpuLoad != null ? snapshot.gpuLoad : 0;
        activeEvent.cpuUsage = Math.max(activeEvent.cpuUsage, cpu);
        activeEvent.gpuUsage = Math.max(activeEvent.gpuUsage, gpu);
        activeEvent.isCharging = activeEvent.isCharging || snapshot.isCharging;
        activeEvent.screenOn = activeEvent.screenOn || snapshot.isScreenOnOrFalse();
        activeEvent.networkType = snapshot.networkState;
        activeEvent.batteryLevel = snapshot.level;
        activeEvent.currentDraw = snapshot.dischargeRate;
        
        // Update activity detection (sticky - once detected, stays true for the event)
        String foregroundApp = snapshot.topAppPackage != null ? snapshot.topAppPackage : snapshot.foregroundApp;
        ActivityDetector.ActivityResult activity = activityDetector.detectActivity(foregroundApp);
        activeEvent.isGaming = activeEvent.isGaming || activity.isGaming;
        activeEvent.isCamera = activeEvent.isCamera || activity.isCamera;
        activeEvent.isNavigation = activeEvent.isNavigation || activity.isNavigation;
        activeEvent.heavyNetworkActivity = activeEvent.heavyNetworkActivity || isHeavyNetworkActivity();
        
        // Update thermal zones with latest readings
        ThermalZoneReader.ThermalZoneData thermalZones = ThermalZoneReader.readThermalZones(tempC);
        if (thermalZones.cpuTemp > activeEvent.cpuTemp) {
            activeEvent.cpuTemp = thermalZones.cpuTemp;
        }
        if (thermalZones.skinTemp > activeEvent.skinTemp) {
            activeEvent.skinTemp = thermalZones.skinTemp;
        }
        activeEvent.thermalZones = thermalZones.toJson();
        
        // Add temperature sample
        eventTempSamples.add(new TempSample(snapshot.timestamp, tempC));
        activeEvent.temperatureSamplesJson = buildTemperatureSamplesJson();
        
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
        
        // Finalize temperature samples JSON
        activeEvent.temperatureSamplesJson = buildTemperatureSamplesJson();
        eventTempSamples.clear();
        
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
        
        // Calculate estimated cycle impact based on temperature and duration
        // High temperatures cause accelerated battery degradation
        // Formula: impact = baseFactor * tempMultiplier * durationHours
        activeEvent.estimatedCycleImpact = calculateEstimatedCycleImpact(
            activeEvent.peakTemperature, 
            activeEvent.duration, 
            activeEvent.isSustainedHeat
        );
        
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
            
            int cpuLoad = (snapshot.cpuLoadTotal != null && snapshot.cpuLoadTotal > 0) ? snapshot.cpuLoadTotal : snapshot.cpuUsage;
            int gpuLoad = snapshot.gpuLoad != null ? snapshot.gpuLoad : 0;
            if (cpuLoad >= 60) {
                activeBucket.highCpuCount++;
                highCpuSamples++;
                highCpuTempSum += tempC;
            }
            if (gpuLoad >= 60) {
                activeBucket.highGpuCount++;
                highGpuSamples++;
                highGpuTempSum += tempC;
            }
            activeBucket.avgCpuLoad = (activeBucket.avgCpuLoad * (activeBucket.sampleCount - 1) + cpuLoad) / activeBucket.sampleCount;
            activeBucket.avgGpuLoad = (activeBucket.avgGpuLoad * (activeBucket.sampleCount - 1) + gpuLoad) / activeBucket.sampleCount;
            activeBucket.avgTempHighCpu = highCpuSamples > 0 ? highCpuTempSum / highCpuSamples : 0;
            activeBucket.avgTempHighGpu = highGpuSamples > 0 ? highGpuTempSum / highGpuSamples : 0;
            if (snapshot.isScreenOnOrFalse()) {
                screenOnSamples++;
            }
            activeBucket.screenOnCount = screenOnSamples;
            activeBucket.screenOnPercent = activeBucket.sampleCount > 0 ?
                (screenOnSamples / (float) activeBucket.sampleCount) * 100 : 0;
            
            if (snapshot.topAppPackage != null) {
                activeBucket.topForegroundApp = snapshot.topAppPackage;
                
                // Track app temperature contribution
                String app = snapshot.topAppPackage;
                appTempSums.put(app, appTempSums.getOrDefault(app, 0f) + tempC);
                appSampleCounts.put(app, appSampleCounts.getOrDefault(app, 0) + 1);
            }
            
            // Activity tracking (gaming, camera, navigation)
            String foregroundApp = snapshot.topAppPackage != null ? snapshot.topAppPackage : snapshot.foregroundApp;
            ActivityDetector.ActivityResult activity = activityDetector.detectActivity(foregroundApp);
            if (activity.isGaming) {
                gamingSamples++;
            }
            if (activity.isCamera) {
                cameraSamples++;
            }
            if (activity.isNavigation) {
                navigationSamples++;
            }
            activeBucket.gamingCount = gamingSamples;
            activeBucket.cameraCount = cameraSamples;
            activeBucket.navigationCount = navigationSamples;
            
            // Network type tracking
            String networkType = snapshot.networkState != null ? snapshot.networkState : "unknown";
            networkTypeCounts.put(networkType, networkTypeCounts.getOrDefault(networkType, 0) + 1);
            activeBucket.networkType = getDominantNetworkType();
            
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
        
        // Reset new tracking variables
        gamingSamples = 0;
        cameraSamples = 0;
        navigationSamples = 0;
        networkTypeCounts.clear();
        appTempSums.clear();
        appSampleCounts.clear();
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
        
        // Generate app temperature contribution JSON
        activeBucket.appTempContributionJson = buildAppTempContributionJson();
        
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
            activeEvent.temperatureSamplesJson = buildTemperatureSamplesJson();
            database.thermalEventDao().update(activeEvent);
            activeEvent = null;
        }
        eventTempSamples.clear();
    }
    
    // =========================================================================
    // Helper Methods for Data Population
    // =========================================================================
    
    /**
     * Check if there is heavy network activity.
     * Uses TrafficStats to measure network throughput.
     */
    private boolean isHeavyNetworkActivity() {
        try {
            long currentRx = TrafficStats.getTotalRxBytes();
            long currentTx = TrafficStats.getTotalTxBytes();
            long currentTime = System.currentTimeMillis();
            
            if (lastNetworkCheckTime == 0) {
                lastNetworkBytesRx = currentRx;
                lastNetworkBytesTx = currentTx;
                lastNetworkCheckTime = currentTime;
                return false;
            }
            
            long timeDelta = currentTime - lastNetworkCheckTime;
            if (timeDelta <= 0) return false;
            
            long bytesRxDelta = currentRx - lastNetworkBytesRx;
            long bytesTxDelta = currentTx - lastNetworkBytesTx;
            long totalBytesDelta = bytesRxDelta + bytesTxDelta;
            
            // Update tracking
            lastNetworkBytesRx = currentRx;
            lastNetworkBytesTx = currentTx;
            lastNetworkCheckTime = currentTime;
            
            // Calculate bytes per second
            float bytesPerSecond = (totalBytesDelta * 1000f) / timeDelta;
            
            return bytesPerSecond > HEAVY_NETWORK_THRESHOLD;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Get the dominant (most frequent) network type from tracking.
     */
    private String getDominantNetworkType() {
        if (networkTypeCounts.isEmpty()) {
            return "unknown";
        }
        
        String dominant = "unknown";
        int maxCount = 0;
        
        for (Map.Entry<String, Integer> entry : networkTypeCounts.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                dominant = entry.getKey();
            }
        }
        
        return dominant;
    }
    
    /**
     * Build JSON string for app temperature contribution.
     * Format: {"com.app.name": avgTemp, "com.other.app": avgTemp}
     */
    private String buildAppTempContributionJson() {
        try {
            JSONObject json = new JSONObject();
            
            for (Map.Entry<String, Float> entry : appTempSums.entrySet()) {
                String app = entry.getKey();
                Float tempSum = entry.getValue();
                Integer count = appSampleCounts.get(app);
                
                if (count != null && count > 0) {
                    float avgTemp = tempSum / count;
                    // Round to 1 decimal place
                    avgTemp = Math.round(avgTemp * 10) / 10f;
                    json.put(app, avgTemp);
                }
            }
            
            return json.toString();
        } catch (Exception e) {
            return "{}";
        }
    }
    
    /**
     * Build JSON string for temperature samples during an event.
     * Format: [{"t": relativeMs, "temp": tempC}, ...]
     */
    private String buildTemperatureSamplesJson() {
        try {
            if (eventTempSamples.isEmpty()) {
                return "[]";
            }
            
            JSONArray array = new JSONArray();
            long startTime = eventTempSamples.get(0).timestamp;
            
            for (TempSample sample : eventTempSamples) {
                JSONObject obj = new JSONObject();
                obj.put("t", sample.timestamp - startTime); // Relative time in ms
                obj.put("temp", Math.round(sample.temp * 10) / 10f); // Round to 1 decimal
                array.put(obj);
            }
            
            return array.toString();
        } catch (Exception e) {
            return "[]";
        }
    }
    
    /**
     * Calculate estimated battery cycle impact from a thermal event.
     * 
     * Based on research showing that high temperatures accelerate battery degradation:
     * - At 25°C: baseline aging
     * - At 35°C: ~2x aging rate
     * - At 45°C: ~4x aging rate
     * - At 55°C: ~8x aging rate
     * 
     * @param peakTemp Peak temperature during the event
     * @param durationMs Duration of the event in milliseconds
     * @param isSustained Whether this was a sustained heat event
     * @return Estimated cycle impact (0-1, where 1 = one full cycle of degradation)
     */
    private float calculateEstimatedCycleImpact(float peakTemp, long durationMs, boolean isSustained) {
        if (durationMs <= 0 || peakTemp < 35) {
            return 0;
        }
        
        // Convert duration to hours
        float hours = durationMs / 3600000f;
        
        // Base degradation rate per hour at elevated temperature
        // A typical battery has ~500 full cycles lifetime
        // Normal usage at 25°C: ~0.0002 cycles/hour (500 hours active = 0.1 cycle)
        float baseDegradationPerHour = 0.0002f;
        
        // Temperature multiplier using exponential model
        // Doubles approximately every 10°C above 25°C
        float tempFactor;
        if (peakTemp < 35) {
            tempFactor = 1.0f;
        } else if (peakTemp < 40) {
            tempFactor = 2.0f;
        } else if (peakTemp < 45) {
            tempFactor = 4.0f;
        } else if (peakTemp < 50) {
            tempFactor = 8.0f;
        } else {
            tempFactor = 16.0f; // Severe degradation above 50°C
        }
        
        // Sustained heat causes more damage than spikes
        float sustainedMultiplier = isSustained ? 1.5f : 1.0f;
        
        // Calculate impact
        float impact = baseDegradationPerHour * tempFactor * sustainedMultiplier * hours;
        
        // Cap at reasonable maximum (0.1 cycle per event is severe)
        return Math.min(0.1f, impact);
    }
}
