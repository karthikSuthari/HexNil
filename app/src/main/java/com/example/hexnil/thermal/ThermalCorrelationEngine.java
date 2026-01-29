package com.example.hexnil.thermal;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.model.BatterySnapshot;
import com.example.hexnil.model.ChargingSession;
import com.example.hexnil.model.ThermalEvent;
import com.example.hexnil.model.ThermalHeatBucket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * OEM-Grade Thermal Correlation Engine
 * 
 * Correlates thermal events with:
 * - Charging behavior (current, voltage, fast charging)
 * - CPU/GPU load patterns
 * - Foreground applications
 * - Time of day patterns
 * - Battery level correlation
 * 
 * Provides root cause analysis for thermal issues.
 */
public class ThermalCorrelationEngine {

    private static final String TAG = "ThermalCorrelation";

    // Correlation thresholds
    private static final float HIGH_CPU_THRESHOLD = 60.0f;
    private static final float HIGH_GPU_THRESHOLD = 60.0f;
    private static final float HIGH_CURRENT_THRESHOLD = 2000.0f; // mA
    private static final float TEMP_CORRELATION_THRESHOLD = 40.0f; // °C

    // History tracking
    private static final int CORRELATION_HISTORY_SIZE = 100;
    private static final long CORRELATION_WINDOW_MS = 30 * 60 * 1000; // 30 minutes

    private final HexNilDatabase database;
    private final Object historyLock = new Object();
    private final LinkedList<CorrelationSample> correlationHistory = new LinkedList<>();

    // App heat tracking
    private final Object profileLock = new Object();
    private final Map<String, AppThermalProfile> appProfiles = new HashMap<>();

    // Charging correlation tracking
    private final LinkedList<ChargingThermalSample> chargingThermalHistory = new LinkedList<>();

    public ThermalCorrelationEngine(HexNilDatabase database) {
        this.database = database;
    }

    // =====================================================================
    // Data Classes
    // =====================================================================

    public static class CorrelationSample {
        public long timestamp;
        public float temperature;
        public boolean isCharging;
        public float chargeCurrent;
        public int cpuLoad;
        public int gpuLoad;
        public String foregroundApp;
        public int batteryLevel;
        public boolean screenOn;
        public String networkType;
    }

    public static class ChargingThermalSample {
        public long timestamp;
        public float temperature;
        public float current;
        public float voltage;
        public String chargingType;
        public boolean isFastCharging;
    }

    public static class AppThermalProfile {
        public String packageName;
        public int sampleCount;
        public float avgTemperature;
        public float maxTemperature;
        public float minTemperature;
        public float tempRiseAvg;
        public int highTempEventCount;
        public long totalForegroundTimeMs;
        public float thermalImpactScore; // 0-100
        public String thermalCategory; // "cool", "normal", "warm", "hot"
    }

    public static class CorrelationResult {
        public String primaryFactor; // "charging", "cpu", "gpu", "app", "ambient", "combined"
        public float confidence; // 0-1
        public Map<String, Float> factorContributions; // Factor -> contribution %

        public ChargingCorrelation chargingCorrelation;
        public CpuCorrelation cpuCorrelation;
        public GpuCorrelation gpuCorrelation;
        public AppCorrelation appCorrelation;

        public String rootCauseAnalysis;
        public List<String> recommendations;
    }

    public static class ChargingCorrelation {
        public float correlationStrength; // 0-1
        public float avgTempWhileCharging;
        public float avgTempWhileNotCharging;
        public float tempDelta;
        public float avgCurrentWhenHot;
        public float avgCurrentWhenCool;
        public boolean fastChargingCausesHeat;
        public float fastChargeHeatContribution;
        public String worstChargingType;
        public float worstChargingTypeAvgTemp;
    }

    public static class CpuCorrelation {
        public float correlationStrength;
        public float avgTempAtHighCpu;
        public float avgTempAtLowCpu;
        public float tempDelta;
        public int highCpuThermalEventCount;
        public float cpuLoadThresholdForHeat; // CPU% that causes noticeable heat
    }

    public static class GpuCorrelation {
        public float correlationStrength;
        public float avgTempAtHighGpu;
        public float avgTempAtLowGpu;
        public float tempDelta;
        public int highGpuThermalEventCount;
    }

    public static class AppCorrelation {
        public float correlationStrength;
        public String hottestApp;
        public float hottestAppAvgTemp;
        public String coolestApp;
        public float coolestAppAvgTemp;
        public List<AppThermalProfile> topHotApps;
        public Map<String, Float> appTempContribution;
    }

    public static class HeatBucketAnalysis {
        public Map<String, BucketStats> bucketStats;
        public String dominantBucket;
        public float dominantBucketPercent;
        public long totalTimeTracked;
        public float overallAvgTemp;
        public float timeInDangerZone; // % time >45°C
        public float timeInWarningZone; // % time 40-45°C
        public List<HourlyPattern> hourlyPatterns;
    }

    public static class BucketStats {
        public String bucket;
        public long totalDurationMs;
        public float percentOfTime;
        public int sampleCount;
        public float avgTemp;
        public float maxTemp;
        public int chargingSampleCount;
        public float chargingPercent;
        public String topApp;
    }

    public static class HourlyPattern {
        public int hour; // 0-23
        public float avgTemperature;
        public String dominantBucket;
        public int eventCount;
        public boolean isHotHour;
    }

    public static class ThermalRootCause {
        public String primaryCause;
        public float confidence;
        public List<ContributingFactor> contributingFactors;
        public String analysis;
        public List<String> recommendations;
        public float degradationRisk; // 0-1
    }

    public static class ContributingFactor {
        public String factor;
        public float contribution; // 0-100%
        public String evidence;
    }

    // =====================================================================
    // Main Correlation Methods
    // =====================================================================

    /**
     * Record a sample for correlation analysis.
     */
    public void recordSample(BatterySnapshot snapshot) {
        CorrelationSample sample = new CorrelationSample();
        sample.timestamp = snapshot.timestamp > 0 ? snapshot.timestamp : System.currentTimeMillis();
        sample.temperature = snapshot.getTemperatureCelsius();
        sample.isCharging = snapshot.isCharging;
        sample.chargeCurrent = Math.abs(snapshot.chargeRate);
        sample.cpuLoad = (snapshot.cpuLoadTotal != null && snapshot.cpuLoadTotal > 0) ? snapshot.cpuLoadTotal
                : snapshot.cpuUsage;
        sample.gpuLoad = snapshot.gpuLoad != null ? snapshot.gpuLoad : 0;
        sample.foregroundApp = snapshot.topAppPackage != null ? snapshot.topAppPackage : snapshot.foregroundApp;
        sample.batteryLevel = snapshot.level;
        sample.screenOn = snapshot.isScreenOnOrFalse();
        sample.networkType = snapshot.networkState;

        addCorrelationSample(sample);
        updateAppProfile(sample);

        if (snapshot.isCharging) {
            recordChargingThermalSample(snapshot);
        }
    }

    /**
     * Perform comprehensive correlation analysis.
     */
    public CorrelationResult analyzeCorrelations() {
        CorrelationResult result = new CorrelationResult();
        result.factorContributions = new HashMap<>();
        result.recommendations = new ArrayList<>();

        synchronized (historyLock) {
            // Analyze each factor
            result.chargingCorrelation = analyzeChargingCorrelation();
            result.cpuCorrelation = analyzeCpuCorrelation();
            result.gpuCorrelation = analyzeGpuCorrelation();
            result.appCorrelation = analyzeAppCorrelation();

            // Calculate contributions
            float totalStrength = result.chargingCorrelation.correlationStrength +
                    result.cpuCorrelation.correlationStrength +
                    result.gpuCorrelation.correlationStrength +
                    result.appCorrelation.correlationStrength;

            if (totalStrength > 0) {
                result.factorContributions.put("charging",
                        (result.chargingCorrelation.correlationStrength / totalStrength) * 100);
                result.factorContributions.put("cpu",
                        (result.cpuCorrelation.correlationStrength / totalStrength) * 100);
                result.factorContributions.put("gpu",
                        (result.gpuCorrelation.correlationStrength / totalStrength) * 100);
                result.factorContributions.put("app",
                        (result.appCorrelation.correlationStrength / totalStrength) * 100);
            }
        }

        // Determine primary factor
        float maxStrength = 0;
        String primaryFactor = "ambient";

        if (result.chargingCorrelation.correlationStrength > maxStrength) {
            maxStrength = result.chargingCorrelation.correlationStrength;
            primaryFactor = "charging";
        }
        if (result.cpuCorrelation.correlationStrength > maxStrength) {
            maxStrength = result.cpuCorrelation.correlationStrength;
            primaryFactor = "cpu";
        }
        if (result.gpuCorrelation.correlationStrength > maxStrength) {
            maxStrength = result.gpuCorrelation.correlationStrength;
            primaryFactor = "gpu";
        }
        if (result.appCorrelation.correlationStrength > maxStrength) {
            maxStrength = result.appCorrelation.correlationStrength;
            primaryFactor = "app";
        }

        // Check for combined factors
        int significantFactors = 0;
        if (result.chargingCorrelation.correlationStrength > 0.3f)
            significantFactors++;
        if (result.cpuCorrelation.correlationStrength > 0.3f)
            significantFactors++;
        if (result.gpuCorrelation.correlationStrength > 0.3f)
            significantFactors++;

        if (significantFactors >= 2) {
            primaryFactor = "combined";
        }

        result.primaryFactor = primaryFactor;
        result.confidence = maxStrength;

        // Generate analysis and recommendations
        result.rootCauseAnalysis = generateRootCauseAnalysis(result);
        result.recommendations = generateRecommendations(result);

        return result;
    }

    /**
     * Analyze heat bucket distribution.
     */
    public HeatBucketAnalysis analyzeHeatBuckets(long startTime) {
        HeatBucketAnalysis analysis = new HeatBucketAnalysis();
        analysis.bucketStats = new HashMap<>();
        analysis.hourlyPatterns = new ArrayList<>();

        List<ThermalHeatBucket> buckets = database.thermalHeatBucketDao().getBucketsSince(startTime);

        if (buckets.isEmpty()) {
            return analysis;
        }

        // Initialize bucket stats
        String[] bucketNames = { "<35", "35-40", "40-45", ">45" };
        for (String name : bucketNames) {
            BucketStats stats = new BucketStats();
            stats.bucket = name;
            analysis.bucketStats.put(name, stats);
        }

        // Aggregate bucket data
        long totalDuration = 0;
        float tempSum = 0;
        int sampleSum = 0;

        // Hourly aggregation
        Map<Integer, List<ThermalHeatBucket>> hourlyBuckets = new HashMap<>();

        for (ThermalHeatBucket bucket : buckets) {
            BucketStats stats = analysis.bucketStats.get(bucket.tempBucket);
            if (stats != null) {
                stats.totalDurationMs += bucket.durationMs;
                stats.sampleCount += bucket.sampleCount;
                stats.maxTemp = Math.max(stats.maxTemp, bucket.maxTemp);
                stats.avgTemp = (stats.avgTemp * (stats.sampleCount - bucket.sampleCount) +
                        bucket.avgTemp * bucket.sampleCount) / stats.sampleCount;
                stats.chargingSampleCount += bucket.chargingCount;
                if (bucket.topForegroundApp != null) {
                    stats.topApp = bucket.topForegroundApp;
                }
            }

            totalDuration += bucket.durationMs;
            tempSum += bucket.avgTemp * bucket.sampleCount;
            sampleSum += bucket.sampleCount;

            // Hourly tracking
            int hour = bucket.hourBlock;
            if (!hourlyBuckets.containsKey(hour)) {
                hourlyBuckets.put(hour, new ArrayList<>());
            }
            hourlyBuckets.get(hour).add(bucket);
        }

        analysis.totalTimeTracked = totalDuration;
        analysis.overallAvgTemp = sampleSum > 0 ? tempSum / sampleSum : 0;

        // Calculate percentages
        long dangerTime = 0;
        long warningTime = 0;
        long maxDuration = 0;
        String dominantBucket = "<35";

        for (Map.Entry<String, BucketStats> entry : analysis.bucketStats.entrySet()) {
            BucketStats stats = entry.getValue();
            stats.percentOfTime = totalDuration > 0 ? (stats.totalDurationMs / (float) totalDuration) * 100 : 0;
            stats.chargingPercent = stats.sampleCount > 0
                    ? (stats.chargingSampleCount / (float) stats.sampleCount) * 100
                    : 0;

            if (stats.totalDurationMs > maxDuration) {
                maxDuration = stats.totalDurationMs;
                dominantBucket = entry.getKey();
            }

            if (">45".equals(entry.getKey())) {
                dangerTime = stats.totalDurationMs;
            } else if ("40-45".equals(entry.getKey())) {
                warningTime = stats.totalDurationMs;
            }
        }

        analysis.dominantBucket = dominantBucket;
        analysis.dominantBucketPercent = totalDuration > 0 ? (maxDuration / (float) totalDuration) * 100 : 0;
        analysis.timeInDangerZone = totalDuration > 0 ? (dangerTime / (float) totalDuration) * 100 : 0;
        analysis.timeInWarningZone = totalDuration > 0 ? (warningTime / (float) totalDuration) * 100 : 0;

        // Hourly patterns
        for (int hour = 0; hour < 24; hour++) {
            HourlyPattern pattern = new HourlyPattern();
            pattern.hour = hour;

            List<ThermalHeatBucket> hourBuckets = hourlyBuckets.get(hour);
            if (hourBuckets != null && !hourBuckets.isEmpty()) {
                float hourTempSum = 0;
                int hourSamples = 0;
                Map<String, Long> bucketDurations = new HashMap<>();

                for (ThermalHeatBucket b : hourBuckets) {
                    hourTempSum += b.avgTemp * b.sampleCount;
                    hourSamples += b.sampleCount;
                    bucketDurations.merge(b.tempBucket, b.durationMs, Long::sum);
                    pattern.eventCount++;
                }

                pattern.avgTemperature = hourSamples > 0 ? hourTempSum / hourSamples : 0;

                // Find dominant bucket for hour
                long maxHourDuration = 0;
                for (Map.Entry<String, Long> e : bucketDurations.entrySet()) {
                    if (e.getValue() > maxHourDuration) {
                        maxHourDuration = e.getValue();
                        pattern.dominantBucket = e.getKey();
                    }
                }

                pattern.isHotHour = pattern.avgTemperature > 40;
            } else {
                pattern.avgTemperature = 0;
                pattern.dominantBucket = "<35";
            }

            analysis.hourlyPatterns.add(pattern);
        }

        return analysis;
    }

    /**
     * Perform root cause analysis for a thermal event.
     */
    public ThermalRootCause analyzeRootCause(ThermalEvent event) {
        ThermalRootCause rootCause = new ThermalRootCause();
        rootCause.contributingFactors = new ArrayList<>();
        rootCause.recommendations = new ArrayList<>();

        // Analyze event context
        boolean chargingRelated = event.isCharging;
        boolean cpuRelated = event.cpuUsage > HIGH_CPU_THRESHOLD;
        boolean gpuRelated = event.gpuUsage > HIGH_GPU_THRESHOLD;
        boolean appRelated = event.foregroundApp != null && !event.foregroundApp.isEmpty();

        float totalContribution = 0;

        // Charging contribution
        if (chargingRelated) {
            ContributingFactor factor = new ContributingFactor();
            factor.factor = "charging";
            factor.contribution = calculateChargingContribution(event);
            factor.evidence = String.format("Charging with %.0f mA current", event.currentDraw);
            rootCause.contributingFactors.add(factor);
            totalContribution += factor.contribution;
        }

        // CPU contribution
        if (cpuRelated) {
            ContributingFactor factor = new ContributingFactor();
            factor.factor = "cpu_load";
            factor.contribution = calculateCpuContribution(event);
            factor.evidence = String.format("CPU at %d%%", event.cpuUsage);
            rootCause.contributingFactors.add(factor);
            totalContribution += factor.contribution;
        }

        // GPU contribution
        if (gpuRelated) {
            ContributingFactor factor = new ContributingFactor();
            factor.factor = "gpu_load";
            factor.contribution = calculateGpuContribution(event);
            factor.evidence = String.format("GPU at %d%%", event.gpuUsage);
            rootCause.contributingFactors.add(factor);
            totalContribution += factor.contribution;
        }

        // App contribution
        if (appRelated) {
            ContributingFactor factor = new ContributingFactor();
            factor.factor = "foreground_app";
            factor.contribution = calculateAppContribution(event);
            factor.evidence = String.format("App: %s", event.foregroundApp);
            rootCause.contributingFactors.add(factor);
            totalContribution += factor.contribution;
        }

        // Normalize contributions
        if (totalContribution > 0) {
            for (ContributingFactor factor : rootCause.contributingFactors) {
                factor.contribution = (factor.contribution / totalContribution) * 100;
            }
        }

        // Determine primary cause
        float maxContrib = 0;
        String primaryCause = "ambient";

        for (ContributingFactor factor : rootCause.contributingFactors) {
            if (factor.contribution > maxContrib) {
                maxContrib = factor.contribution;
                primaryCause = factor.factor;
            }
        }

        rootCause.primaryCause = primaryCause;
        rootCause.confidence = maxContrib / 100.0f;

        // Generate analysis
        rootCause.analysis = generateEventAnalysis(event, rootCause);
        rootCause.recommendations = generateEventRecommendations(event, rootCause);

        // Calculate degradation risk
        rootCause.degradationRisk = calculateDegradationRisk(event);

        return rootCause;
    }

    // =====================================================================
    // Factor Correlation Methods
    // =====================================================================

    private ChargingCorrelation analyzeChargingCorrelation() {
        ChargingCorrelation correlation = new ChargingCorrelation();

        float chargingTempSum = 0;
        int chargingSamples = 0;
        float notChargingTempSum = 0;
        int notChargingSamples = 0;
        float hotChargingCurrentSum = 0;
        int hotChargingSamples = 0;
        float coolChargingCurrentSum = 0;
        int coolChargingSamples = 0;

        Map<String, Float> chargingTypeTempSum = new HashMap<>();
        Map<String, Integer> chargingTypeCount = new HashMap<>();

        for (CorrelationSample sample : correlationHistory) {
            if (sample.isCharging) {
                chargingTempSum += sample.temperature;
                chargingSamples++;

                if (sample.temperature >= TEMP_CORRELATION_THRESHOLD) {
                    hotChargingCurrentSum += sample.chargeCurrent;
                    hotChargingSamples++;
                } else {
                    coolChargingCurrentSum += sample.chargeCurrent;
                    coolChargingSamples++;
                }
            } else {
                notChargingTempSum += sample.temperature;
                notChargingSamples++;
            }
        }

        // Calculate averages
        correlation.avgTempWhileCharging = chargingSamples > 0 ? chargingTempSum / chargingSamples : 0;
        correlation.avgTempWhileNotCharging = notChargingSamples > 0 ? notChargingTempSum / notChargingSamples : 0;
        correlation.tempDelta = correlation.avgTempWhileCharging - correlation.avgTempWhileNotCharging;

        correlation.avgCurrentWhenHot = hotChargingSamples > 0 ? hotChargingCurrentSum / hotChargingSamples : 0;
        correlation.avgCurrentWhenCool = coolChargingSamples > 0 ? coolChargingCurrentSum / coolChargingSamples : 0;

        // Calculate correlation strength
        if (chargingSamples > 0 && notChargingSamples > 0) {
            float tempDiffNormalized = Math.min(1.0f, Math.abs(correlation.tempDelta) / 10.0f);
            float chargingRatio = (float) chargingSamples / (chargingSamples + notChargingSamples);
            correlation.correlationStrength = tempDiffNormalized * (0.5f + chargingRatio * 0.5f);
        }

        // Fast charging analysis
        for (ChargingThermalSample cts : chargingThermalHistory) {
            if (cts.isFastCharging && cts.temperature >= TEMP_CORRELATION_THRESHOLD) {
                correlation.fastChargingCausesHeat = true;
                break;
            }
        }

        return correlation;
    }

    private CpuCorrelation analyzeCpuCorrelation() {
        CpuCorrelation correlation = new CpuCorrelation();

        float highCpuTempSum = 0;
        int highCpuSamples = 0;
        float lowCpuTempSum = 0;
        int lowCpuSamples = 0;

        for (CorrelationSample sample : correlationHistory) {
            if (sample.cpuLoad >= HIGH_CPU_THRESHOLD) {
                highCpuTempSum += sample.temperature;
                highCpuSamples++;
                if (sample.temperature >= TEMP_CORRELATION_THRESHOLD) {
                    correlation.highCpuThermalEventCount++;
                }
            } else {
                lowCpuTempSum += sample.temperature;
                lowCpuSamples++;
            }
        }

        correlation.avgTempAtHighCpu = highCpuSamples > 0 ? highCpuTempSum / highCpuSamples : 0;
        correlation.avgTempAtLowCpu = lowCpuSamples > 0 ? lowCpuTempSum / lowCpuSamples : 0;
        correlation.tempDelta = correlation.avgTempAtHighCpu - correlation.avgTempAtLowCpu;

        // Calculate correlation strength
        if (highCpuSamples > 0 && lowCpuSamples > 0) {
            float tempDiffNormalized = Math.min(1.0f, Math.abs(correlation.tempDelta) / 10.0f);
            correlation.correlationStrength = tempDiffNormalized;
        }

        // Find CPU threshold for heat
        correlation.cpuLoadThresholdForHeat = HIGH_CPU_THRESHOLD;

        return correlation;
    }

    private GpuCorrelation analyzeGpuCorrelation() {
        GpuCorrelation correlation = new GpuCorrelation();

        float highGpuTempSum = 0;
        int highGpuSamples = 0;
        float lowGpuTempSum = 0;
        int lowGpuSamples = 0;

        for (CorrelationSample sample : correlationHistory) {
            if (sample.gpuLoad >= HIGH_GPU_THRESHOLD) {
                highGpuTempSum += sample.temperature;
                highGpuSamples++;
                if (sample.temperature >= TEMP_CORRELATION_THRESHOLD) {
                    correlation.highGpuThermalEventCount++;
                }
            } else {
                lowGpuTempSum += sample.temperature;
                lowGpuSamples++;
            }
        }

        correlation.avgTempAtHighGpu = highGpuSamples > 0 ? highGpuTempSum / highGpuSamples : 0;
        correlation.avgTempAtLowGpu = lowGpuSamples > 0 ? lowGpuTempSum / lowGpuSamples : 0;
        correlation.tempDelta = correlation.avgTempAtHighGpu - correlation.avgTempAtLowGpu;

        // Calculate correlation strength
        if (highGpuSamples > 0 && lowGpuSamples > 0) {
            float tempDiffNormalized = Math.min(1.0f, Math.abs(correlation.tempDelta) / 10.0f);
            correlation.correlationStrength = tempDiffNormalized;
        }

        return correlation;
    }

    private AppCorrelation analyzeAppCorrelation() {
        AppCorrelation correlation = new AppCorrelation();
        correlation.topHotApps = new ArrayList<>();
        correlation.appTempContribution = new HashMap<>();

        if (appProfiles.isEmpty()) {
            return correlation;
        }

        // Find hottest and coolest apps
        float maxTemp = Float.MIN_VALUE;
        float minTemp = Float.MAX_VALUE;
        String hottestApp = null;
        String coolestApp = null;

        for (AppThermalProfile profile : appProfiles.values()) {
            if (profile.sampleCount < 3)
                continue; // Need minimum samples

            if (profile.avgTemperature > maxTemp) {
                maxTemp = profile.avgTemperature;
                hottestApp = profile.packageName;
            }
            if (profile.avgTemperature < minTemp) {
                minTemp = profile.avgTemperature;
                coolestApp = profile.packageName;
            }

            correlation.appTempContribution.put(profile.packageName, profile.thermalImpactScore);
        }

        correlation.hottestApp = hottestApp;
        correlation.hottestAppAvgTemp = maxTemp;
        correlation.coolestApp = coolestApp;
        correlation.coolestAppAvgTemp = minTemp;

        // Top hot apps
        List<AppThermalProfile> sortedProfiles = new ArrayList<>(appProfiles.values());
        sortedProfiles.sort((a, b) -> Float.compare(b.avgTemperature, a.avgTemperature));

        for (int i = 0; i < Math.min(5, sortedProfiles.size()); i++) {
            if (sortedProfiles.get(i).sampleCount >= 3) {
                correlation.topHotApps.add(sortedProfiles.get(i));
            }
        }

        // Calculate correlation strength
        if (maxTemp > minTemp && maxTemp > 0) {
            correlation.correlationStrength = Math.min(1.0f, (maxTemp - minTemp) / 15.0f);
        }

        return correlation;
    }

    // =====================================================================
    // Helper Methods
    // =====================================================================

    private void addCorrelationSample(CorrelationSample sample) {
        synchronized (historyLock) {
            correlationHistory.addLast(sample);
            while (correlationHistory.size() > CORRELATION_HISTORY_SIZE) {
                correlationHistory.removeFirst();
            }

            // Trim old samples
            long cutoff = sample.timestamp - CORRELATION_WINDOW_MS;
            while (!correlationHistory.isEmpty() && correlationHistory.getFirst().timestamp < cutoff) {
                correlationHistory.removeFirst();
            }
        }
    }

    private void recordChargingThermalSample(BatterySnapshot snapshot) {
        ChargingThermalSample sample = new ChargingThermalSample();
        sample.timestamp = snapshot.timestamp > 0 ? snapshot.timestamp : System.currentTimeMillis();
        sample.temperature = snapshot.getTemperatureCelsius();
        sample.current = Math.abs(snapshot.chargeRate);
        sample.voltage = snapshot.voltage;
        sample.chargingType = snapshot.chargingType;
        sample.isFastCharging = sample.current > HIGH_CURRENT_THRESHOLD;

        synchronized (historyLock) {
            chargingThermalHistory.addLast(sample);
            while (chargingThermalHistory.size() > 50) {
                chargingThermalHistory.removeFirst();
            }
        }
    }

    private void updateAppProfile(CorrelationSample sample) {
        if (sample.foregroundApp == null || sample.foregroundApp.isEmpty())
            return;

        synchronized (profileLock) {
            AppThermalProfile profile = appProfiles.get(sample.foregroundApp);
            if (profile == null) {
                profile = new AppThermalProfile();
                profile.packageName = sample.foregroundApp;
                profile.minTemperature = Float.MAX_VALUE;
                appProfiles.put(sample.foregroundApp, profile);
            }

            // Update profile
            profile.avgTemperature = (profile.avgTemperature * profile.sampleCount + sample.temperature) /
                    (profile.sampleCount + 1);
            profile.sampleCount++;
            profile.maxTemperature = Math.max(profile.maxTemperature, sample.temperature);
            profile.minTemperature = Math.min(profile.minTemperature, sample.temperature);

            if (sample.temperature >= TEMP_CORRELATION_THRESHOLD) {
                profile.highTempEventCount++;
            }

            // Calculate thermal impact score
            float tempScore = Math.min(100, (profile.avgTemperature - 30) * 5);
            float eventScore = Math.min(100, profile.highTempEventCount * 10);
            profile.thermalImpactScore = tempScore * 0.6f + eventScore * 0.4f;

            // Categorize
            if (profile.avgTemperature < 35) {
                profile.thermalCategory = "cool";
            } else if (profile.avgTemperature < 40) {
                profile.thermalCategory = "normal";
            } else if (profile.avgTemperature < 45) {
                profile.thermalCategory = "warm";
            } else {
                profile.thermalCategory = "hot";
            }
        }
    }

    private float calculateChargingContribution(ThermalEvent event) {
        if (!event.isCharging)
            return 0;

        float baseContrib = 30;
        if (event.currentDraw > 2500)
            baseContrib += 20;
        if (event.currentDraw > 3500)
            baseContrib += 15;

        return Math.min(60, baseContrib);
    }

    private float calculateCpuContribution(ThermalEvent event) {
        if (event.cpuUsage < 50)
            return 0;

        float baseContrib = (event.cpuUsage - 50) * 0.8f;
        return Math.min(50, baseContrib);
    }

    private float calculateGpuContribution(ThermalEvent event) {
        if (event.gpuUsage < 50)
            return 0;

        float baseContrib = (event.gpuUsage - 50) * 0.7f;
        return Math.min(45, baseContrib);
    }

    private float calculateAppContribution(ThermalEvent event) {
        AppThermalProfile profile = appProfiles.get(event.foregroundApp);
        if (profile == null)
            return 10;

        return Math.min(40, profile.thermalImpactScore * 0.4f);
    }

    private float calculateDegradationRisk(ThermalEvent event) {
        float risk = 0;

        // Temperature contribution
        if (event.peakTemperature >= 50) {
            risk = 0.9f;
        } else if (event.peakTemperature >= 46) {
            risk = 0.6f + (event.peakTemperature - 46) * 0.075f;
        } else if (event.peakTemperature >= 42) {
            risk = 0.2f + (event.peakTemperature - 42) * 0.1f;
        } else {
            risk = Math.max(0, (event.peakTemperature - 35) * 0.03f);
        }

        // Duration contribution
        if (event.duration > 600000) { // >10 minutes
            risk = Math.min(1.0f, risk + 0.2f);
        } else if (event.duration > 300000) { // >5 minutes
            risk = Math.min(1.0f, risk + 0.1f);
        }

        return risk;
    }

    private String generateRootCauseAnalysis(CorrelationResult result) {
        StringBuilder analysis = new StringBuilder();

        analysis.append("Primary thermal factor: ").append(result.primaryFactor);
        analysis.append(" (").append(String.format("%.0f%%", result.confidence * 100)).append(" confidence). ");

        if ("charging".equals(result.primaryFactor) || "combined".equals(result.primaryFactor)) {
            if (result.chargingCorrelation.tempDelta > 3) {
                analysis.append("Charging increases temperature by ~")
                        .append(String.format("%.1f°C", result.chargingCorrelation.tempDelta)).append(". ");
            }
        }

        if ("cpu".equals(result.primaryFactor) || "combined".equals(result.primaryFactor)) {
            if (result.cpuCorrelation.tempDelta > 2) {
                analysis.append("High CPU load correlates with +")
                        .append(String.format("%.1f°C", result.cpuCorrelation.tempDelta)).append(". ");
            }
        }

        if ("app".equals(result.primaryFactor)) {
            if (result.appCorrelation.hottestApp != null) {
                analysis.append("App '").append(result.appCorrelation.hottestApp)
                        .append("' causes most heat. ");
            }
        }

        return analysis.toString();
    }

    private List<String> generateRecommendations(CorrelationResult result) {
        List<String> recommendations = new ArrayList<>();

        if (result.chargingCorrelation.correlationStrength > 0.5f) {
            recommendations.add("Reduce charging rate when temperature exceeds 40°C");
            if (result.chargingCorrelation.fastChargingCausesHeat) {
                recommendations.add("Disable fast charging in warm environments");
            }
        }

        if (result.cpuCorrelation.correlationStrength > 0.5f) {
            recommendations.add("Limit CPU-intensive tasks when battery is warm");
            recommendations.add("Enable CPU throttling at lower temperatures");
        }

        if (result.gpuCorrelation.correlationStrength > 0.5f) {
            recommendations.add("Reduce GPU load during thermal events");
            recommendations.add("Lower graphics settings in games when warm");
        }

        if (result.appCorrelation.correlationStrength > 0.5f &&
                result.appCorrelation.hottestApp != null) {
            recommendations.add("Monitor or restrict app: " + result.appCorrelation.hottestApp);
        }

        return recommendations;
    }

    private String generateEventAnalysis(ThermalEvent event, ThermalRootCause rootCause) {
        StringBuilder analysis = new StringBuilder();

        analysis.append("Thermal event at ").append(String.format("%.1f°C", event.peakTemperature));
        analysis.append(" for ").append(event.duration / 1000).append(" seconds. ");
        analysis.append("Primary cause: ").append(rootCause.primaryCause).append(". ");

        if (!rootCause.contributingFactors.isEmpty()) {
            analysis.append("Contributing factors: ");
            for (ContributingFactor factor : rootCause.contributingFactors) {
                analysis.append(factor.factor).append(" (")
                        .append(String.format("%.0f%%", factor.contribution)).append("), ");
            }
        }

        return analysis.toString();
    }

    private List<String> generateEventRecommendations(ThermalEvent event, ThermalRootCause rootCause) {
        List<String> recommendations = new ArrayList<>();

        if (event.peakTemperature >= 46) {
            recommendations.add("URGENT: Take immediate action to cool device");
        }

        if (event.isCharging && event.peakTemperature >= 42) {
            recommendations.add("Reduce or stop charging");
        }

        if (event.cpuUsage > 70) {
            recommendations.add("Close CPU-intensive applications");
        }

        if (event.screenOn && event.peakTemperature >= 44) {
            recommendations.add("Turn off screen to reduce heat");
        }

        return recommendations;
    }

    /**
     * Get app thermal profiles.
     */
    public Map<String, AppThermalProfile> getAppProfiles() {
        return new HashMap<>(appProfiles);
    }

    /**
     * Clear correlation history.
     */
    public void clearHistory() {
        correlationHistory.clear();
        chargingThermalHistory.clear();
        appProfiles.clear();
    }
}
