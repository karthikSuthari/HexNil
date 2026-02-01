package com.example.hexnil.manager;

import android.app.ActivityManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.PowerManager;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.model.BatterySnapshot;
import com.example.hexnil.model.IdleDrainSession;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OEM-Grade Idle Drain Analyzer
 * 
 * Tracks standby battery drain and Doze mode effectiveness with comprehensive
 * metrics:
 * - Doze state timing (entry/exit/duration)
 * - Deep sleep vs light sleep vs awake time breakdown
 * - Wakelock, alarm, network, and sync event tracking
 * - Background service monitoring
 * - Leakage detection and classification
 * - Benchmark comparison against industry standards
 * 
 * Groups A-F fully implemented per OEM specification.
 */
public class IdleDrainAnalyzer {

    private static final String TAG = "IdleDrainAnalyzer";

    // Benchmarks
    private static final float IDLE_DRAIN_EXCELLENT = 0.3f; // %/hr
    private static final float IDLE_DRAIN_GOOD = 0.5f; // %/hr
    private static final float IDLE_DRAIN_FAIR = 1.0f; // %/hr
    private static final float IDLE_DRAIN_POOR = 2.0f; // %/hr

    private final Context context;
    private final HexNilDatabase database;
    private IdleDrainSession activeSession;

    // State tracking for timing accumulation
    private long lastDozeStateTime;
    private boolean lastDozeState;
    private long accumulatedDeepSleepTime;
    private long accumulatedAwakeTime;
    private int dozeExitCount;
    
    // Group D tracking: Wakeups & Background Activity
    private List<String> dozeExitReasonsList = new ArrayList<>();
    private Map<String, Long> wakelockHolders = new HashMap<>();
    private List<String> alarmDetailsList = new ArrayList<>();
    private List<String> networkDetailsList = new ArrayList<>();
    private List<String> syncDetailsList = new ArrayList<>();
    private long totalWakelockAccumulated = 0;
    private int wakelockEventCount = 0;
    private int syncEventCount = 0;
    private long networkBytesAccumulated = 0;

    public IdleDrainAnalyzer(Context context, HexNilDatabase database) {
        this.context = context;
        this.database = database;
    }

    public synchronized void beginIdleIfNeeded(BatterySnapshot snapshot) {
        if (snapshot == null || snapshot.isScreenOnOrFalse() || snapshot.isCharging)
            return;
        if (activeSession != null && activeSession.endTime == 0)
            return;

        activeSession = new IdleDrainSession();
        activeSession.startLevel = snapshot.level;
        activeSession.startTime = snapshot.timestamp;
        activeSession.wasScreenOff = true;
        activeSession.wasUnplugged = !snapshot.isCharging;
        activeSession.dozeEntryTime = snapshot.isDozeMode ? snapshot.timestamp : 0;

        // Reset accumulators
        lastDozeStateTime = snapshot.timestamp;
        lastDozeState = snapshot.isDozeMode;
        accumulatedDeepSleepTime = 0;
        accumulatedAwakeTime = 0;
        dozeExitCount = 0;
        
        // Reset Group D trackers
        dozeExitReasonsList.clear();
        wakelockHolders.clear();
        alarmDetailsList.clear();
        networkDetailsList.clear();
        syncDetailsList.clear();
        totalWakelockAccumulated = 0;
        wakelockEventCount = 0;
        syncEventCount = 0;
        networkBytesAccumulated = 0;

        database.idleDrainSessionDao().insert(activeSession);
    }

    public synchronized void processSnapshot(BatterySnapshot snapshot) {
        if (snapshot == null)
            return;

        if (snapshot.isScreenOnOrFalse() || snapshot.isCharging) {
            closeIfNeeded(snapshot);
            return;
        }

        if (activeSession == null) {
            beginIdleIfNeeded(snapshot);
        }
        if (activeSession == null)
            return;

        activeSession.endLevel = snapshot.level;
        activeSession.endTime = snapshot.timestamp;
        activeSession.drainAmount = Math.max(0, activeSession.startLevel - activeSession.endLevel);
        activeSession.drainPerHour = activeSession.getDurationHours() > 0
                ? activeSession.drainAmount / activeSession.getDurationHours()
                : 0;

        // Track sleep state transitions
        trackSleepStates(snapshot);

        // Update all metrics
        updateIdleMetrics(snapshot);
        activeSession.benchmarkResult = benchmark(activeSession.drainPerHour);

        database.idleDrainSessionDao().update(activeSession);
    }

    private void closeIfNeeded(BatterySnapshot snapshot) {
        if (activeSession == null)
            return;

        // Finalize Doze timing
        if (activeSession.dozeEntryTime > 0 && activeSession.dozeExitTime == 0) {
            activeSession.dozeExitTime = snapshot.timestamp;
        }

        // Accumulate final state time
        long timeSinceLastState = snapshot.timestamp - lastDozeStateTime;
        if (lastDozeState) {
            accumulatedDeepSleepTime += timeSinceLastState;
        } else {
            accumulatedAwakeTime += timeSinceLastState;
        }

        activeSession.endTime = snapshot.timestamp;
        activeSession.endLevel = snapshot.level;
        activeSession.drainAmount = Math.max(0, activeSession.startLevel - activeSession.endLevel);
        activeSession.drainPerHour = activeSession.getDurationHours() > 0
                ? activeSession.drainAmount / activeSession.getDurationHours()
                : 0;

        // Final metrics update
        updateIdleMetrics(snapshot);

        database.idleDrainSessionDao().update(activeSession);
        activeSession = null;
    }

    /**
     * Track Doze state transitions to calculate deepSleepTime, lightSleepTime,
     * awakeTime.
     */
    private void trackSleepStates(BatterySnapshot snapshot) {
        boolean currentDozeState = snapshot.isDozeMode;
        long now = snapshot.timestamp;

        // Accumulate time since last state check
        long elapsed = now - lastDozeStateTime;
        if (elapsed > 0) {
            if (lastDozeState) {
                // Was in Doze (deep sleep)
                accumulatedDeepSleepTime += elapsed;
            } else {
                // Was awake/idle (not in Doze)
                accumulatedAwakeTime += elapsed;
            }
        }

        // Detect Doze exit
        if (lastDozeState && !currentDozeState) {
            dozeExitCount++;
            if (activeSession.dozeExitTime == 0) {
                activeSession.dozeExitTime = now;
            }
        }

        // Detect Doze entry
        if (!lastDozeState && currentDozeState) {
            if (activeSession.dozeEntryTime == 0) {
                activeSession.dozeEntryTime = now;
            }
        }

        // Update tracking state
        lastDozeStateTime = now;
        lastDozeState = currentDozeState;
    }

    // Instrumentation hooks for wake reasons
    public synchronized void recordAlarmWakeup() {
        recordAlarmWakeup(null, null);
    }
    
    public synchronized void recordAlarmWakeup(String appPackage, String alarmType) {
        if (activeSession != null) {
            activeSession.alarmWakeups += 1;
            activeSession.alarmsFired += 1;
            
            // Track alarm details for Group F JSON
            String detail = String.format("{\"app\":\"%s\",\"type\":\"%s\",\"time\":%d}",
                    appPackage != null ? appPackage : "unknown",
                    alarmType != null ? alarmType : "unknown",
                    System.currentTimeMillis());
            alarmDetailsList.add(detail);
            
            // Add to doze exit reasons if we were in doze
            if (lastDozeState) {
                dozeExitReasonsList.add("alarm:" + (appPackage != null ? appPackage : "unknown"));
            }
            
            database.idleDrainSessionDao().update(activeSession);
        }
    }

    public synchronized void recordNetworkWakeup() {
        recordNetworkWakeup(null, null, 0);
    }
    
    public synchronized void recordNetworkWakeup(String appPackage, String networkType, long bytes) {
        if (activeSession != null) {
            activeSession.networkWakeups += 1;
            activeSession.networkWakes += 1;
            networkBytesAccumulated += bytes;
            activeSession.networkActivity = networkBytesAccumulated;
            
            // Track network details for Group F JSON
            String detail = String.format("{\"app\":\"%s\",\"type\":\"%s\",\"bytes\":%d,\"time\":%d}",
                    appPackage != null ? appPackage : "unknown",
                    networkType != null ? networkType : "unknown",
                    bytes,
                    System.currentTimeMillis());
            networkDetailsList.add(detail);
            
            // Add to doze exit reasons if we were in doze
            if (lastDozeState) {
                dozeExitReasonsList.add("network:" + (appPackage != null ? appPackage : "unknown"));
            }
            
            database.idleDrainSessionDao().update(activeSession);
        }
    }
    
    /**
     * Record a wakelock acquisition event.
     */
    public synchronized void recordWakelockEvent(String appPackage, long durationMs) {
        if (activeSession != null) {
            wakelockEventCount++;
            activeSession.wakelockEvents = wakelockEventCount;
            
            totalWakelockAccumulated += durationMs;
            activeSession.totalWakelockTime = totalWakelockAccumulated;
            
            // Track per-app wakelock time
            String pkg = appPackage != null ? appPackage : "unknown";
            wakelockHolders.merge(pkg, durationMs, Long::sum);
            
            // Add to doze exit reasons if significant wakelock
            if (lastDozeState && durationMs > 1000) {
                dozeExitReasonsList.add("wakelock:" + pkg);
            }
            
            database.idleDrainSessionDao().update(activeSession);
        }
    }
    
    /**
     * Record a sync adapter event.
     */
    public synchronized void recordSyncEvent(String appPackage, String syncType) {
        if (activeSession != null) {
            syncEventCount++;
            activeSession.syncEvents = syncEventCount;
            
            String detail = String.format("{\"app\":\"%s\",\"type\":\"%s\",\"time\":%d}",
                    appPackage != null ? appPackage : "unknown",
                    syncType != null ? syncType : "unknown",
                    System.currentTimeMillis());
            syncDetailsList.add(detail);
            
            // Add to doze exit reasons if we were in doze
            if (lastDozeState) {
                dozeExitReasonsList.add("sync:" + (appPackage != null ? appPackage : "unknown"));
            }
            
            database.idleDrainSessionDao().update(activeSession);
        }
    }

    private void updateIdleMetrics(BatterySnapshot snapshot) {
        if (activeSession == null)
            return;

        activeSession.dozeActive = snapshot.isDozeMode;

        long totalDuration = Math.max(1, snapshot.timestamp - activeSession.startTime);

        // =====================================================================
        // TIMING FIELDS (Group A extension)
        // =====================================================================
        activeSession.deepSleepTime = accumulatedDeepSleepTime;
        activeSession.awakeTime = accumulatedAwakeTime;
        activeSession.lightSleepTime = Math.max(0,
                totalDuration - accumulatedDeepSleepTime - accumulatedAwakeTime);

        // Doze exits
        activeSession.dozeExits = dozeExitCount;

        // =====================================================================
        // PERCENTAGE CALCULATIONS (Group C)
        // =====================================================================
        activeSession.deepSleepPercentage = totalDuration > 0
                ? (accumulatedDeepSleepTime * 100f) / totalDuration
                : 0f;
        activeSession.deepSleepSuccessRate = totalDuration > 0
                ? (float) accumulatedDeepSleepTime / totalDuration
                : 0f;

        // =====================================================================
        // EFFECTIVENESS SCORING (Group C)
        // =====================================================================
        // Doze effectiveness combines deep sleep percentage with low drain
        float drainPenalty = Math.min(50, activeSession.drainPerHour * 20);
        float sleepBonus = activeSession.deepSleepPercentage * 0.5f;
        activeSession.dozeEffectiveness = Math.max(0f, Math.min(100f, sleepBonus + 50 - drainPenalty));
        activeSession.dozeEffectivenessScore = activeSession.dozeEffectiveness;

        // =====================================================================
        // SESSION CLASSIFICATION (Group B)
        // =====================================================================
        if (activeSession.deepSleepPercentage > 70) {
            activeSession.sessionType = "deep_doze";
        } else if (activeSession.deepSleepPercentage > 30) {
            activeSession.sessionType = "light_doze";
        } else if (snapshot.isDozeMode) {
            activeSession.sessionType = "idle";
        } else {
            activeSession.sessionType = "standby";
        }

        // =====================================================================
        // EXPECTED DRAIN & DELTA (Group E)
        // =====================================================================
        // Expected drain: 0.3%/hr during deep doze, 0.7%/hr during idle
        float expectedRate = activeSession.deepSleepPercentage > 50 ? 0.3f : 0.7f;
        activeSession.expectedDrain = expectedRate * activeSession.getDurationHours();
        activeSession.drainDelta = activeSession.drainAmount - activeSession.expectedDrain;

        // =====================================================================
        // GROUP D: BACKGROUND & NETWORK WAKEUPS
        // =====================================================================
        activeSession.wakelockEvents = wakelockEventCount;
        activeSession.totalWakelockTime = totalWakelockAccumulated;
        activeSession.syncEvents = syncEventCount;
        activeSession.networkActivity = networkBytesAccumulated;
        
        // Count background services
        activeSession.backgroundServices = countBackgroundServices();
        
        // =====================================================================
        // GROUP F: JSON DETAILS
        // =====================================================================
        activeSession.dozeExitReasons = buildDozeExitReasonsJson();
        activeSession.topWakelockHolders = buildTopWakelockHoldersJson();
        activeSession.alarmsDetails = buildAlarmsDetailsJson();
        activeSession.networkDetails = buildNetworkDetailsJson();
        activeSession.syncDetails = buildSyncDetailsJson();
        activeSession.leakingServices = identifyLeakingServices();

        // =====================================================================
        // LEAKAGE DETECTION (Group E)
        // =====================================================================
        int totalWakeups = activeSession.alarmWakeups + activeSession.networkWakeups;
        activeSession.hasLeakage = activeSession.drainPerHour > 1.0f ||
                totalWakeups > 3 ||
                dozeExitCount > 5 ||
                wakelockEventCount > 10;

        activeSession.leakageSources = buildLeakageSources();
        activeSession.leakageClass = classifyLeakage(
                activeSession.drainPerHour,
                activeSession.deepSleepSuccessRate,
                totalWakeups + dozeExitCount);
        activeSession.recommendations = generateRecommendations();
        
        // Service leakage estimate
        activeSession.serviceLeakageEstimate = estimateServiceLeakage();
    }
    
    /**
     * Count currently running background services.
     */
    private int countBackgroundServices() {
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                List<ActivityManager.RunningServiceInfo> services = am.getRunningServices(100);
                return services != null ? services.size() : 0;
            }
        } catch (Exception e) {
            // Permission denied or unavailable
        }
        return 0;
    }
    
    /**
     * Build JSON array of Doze exit reasons.
     */
    private String buildDozeExitReasonsJson() {
        if (dozeExitReasonsList.isEmpty()) return "[]";
        try {
            JSONArray arr = new JSONArray();
            for (String reason : dozeExitReasonsList) {
                JSONObject obj = new JSONObject();
                String[] parts = reason.split(":", 2);
                obj.put("reason", parts[0]);
                obj.put("source", parts.length > 1 ? parts[1] : "unknown");
                arr.put(obj);
            }
            return arr.toString();
        } catch (Exception e) {
            return "[]";
        }
    }
    
    /**
     * Build JSON of top wakelock holders sorted by duration.
     */
    private String buildTopWakelockHoldersJson() {
        if (wakelockHolders.isEmpty()) return "[]";
        try {
            JSONArray arr = new JSONArray();
            // Sort by duration descending
            wakelockHolders.entrySet().stream()
                    .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                    .limit(10)
                    .forEach(entry -> {
                        try {
                            JSONObject obj = new JSONObject();
                            obj.put("app", entry.getKey());
                            obj.put("durationMs", entry.getValue());
                            obj.put("durationMin", entry.getValue() / 60000.0);
                            arr.put(obj);
                        } catch (Exception ignored) {}
                    });
            return arr.toString();
        } catch (Exception e) {
            return "[]";
        }
    }
    
    /**
     * Build JSON array of alarm details.
     */
    private String buildAlarmsDetailsJson() {
        if (alarmDetailsList.isEmpty()) return "[]";
        return "[" + String.join(",", alarmDetailsList) + "]";
    }
    
    /**
     * Build JSON array of network wake details.
     */
    private String buildNetworkDetailsJson() {
        if (networkDetailsList.isEmpty()) return "[]";
        return "[" + String.join(",", networkDetailsList) + "]";
    }
    
    /**
     * Build JSON array of sync event details.
     */
    private String buildSyncDetailsJson() {
        if (syncDetailsList.isEmpty()) return "[]";
        return "[" + String.join(",", syncDetailsList) + "]";
    }
    
    /**
     * Identify services causing power leakage.
     */
    private String identifyLeakingServices() {
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return "";
            
            List<ActivityManager.RunningServiceInfo> services = am.getRunningServices(50);
            if (services == null || services.isEmpty()) return "";
            
            StringBuilder leaking = new StringBuilder();
            long now = android.os.SystemClock.elapsedRealtime();
            
            for (ActivityManager.RunningServiceInfo service : services) {
                // Services running for a long time during idle are potential leakers
                long runningTime = now - service.activeSince;
                if (runningTime > 30 * 60 * 1000) { // Running > 30 min
                    if (leaking.length() > 0) leaking.append(", ");
                    leaking.append(service.service.getPackageName());
                }
            }
            return leaking.toString();
        } catch (Exception e) {
            return "";
        }
    }
    
    /**
     * Estimate drain caused by background services.
     */
    private float estimateServiceLeakage() {
        // Rough estimate: each long-running service contributes ~0.1%/hr
        int leakingCount = 0;
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                List<ActivityManager.RunningServiceInfo> services = am.getRunningServices(50);
                if (services != null) {
                    long now = android.os.SystemClock.elapsedRealtime();
                    for (ActivityManager.RunningServiceInfo service : services) {
                        if (now - service.activeSince > 30 * 60 * 1000) {
                            leakingCount++;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        
        return leakingCount * 0.1f * activeSession.getDurationHours();
    }

    private String buildLeakageSources() {
        if (!activeSession.hasLeakage)
            return "";

        StringBuilder sources = new StringBuilder();
        if (activeSession.alarmWakeups > 0) {
            sources.append("alarms(").append(activeSession.alarmWakeups).append(") ");
        }
        if (activeSession.networkWakeups > 0) {
            sources.append("network(").append(activeSession.networkWakeups).append(") ");
        }
        if (dozeExitCount > 3) {
            sources.append("frequent_doze_exits(").append(dozeExitCount).append(") ");
        }
        if (activeSession.drainPerHour > 1.5f) {
            sources.append("high_drain ");
        }
        return sources.toString().trim();
    }

    private String generateRecommendations() {
        if (!activeSession.hasLeakage)
            return "";

        StringBuilder recs = new StringBuilder();
        if (activeSession.alarmWakeups > 3) {
            recs.append("Reduce alarm frequency. ");
        }
        if (activeSession.networkWakeups > 3) {
            recs.append("Restrict background network access. ");
        }
        if (dozeExitCount > 5) {
            recs.append("Check apps preventing Doze mode. ");
        }
        if (activeSession.deepSleepPercentage < 50) {
            recs.append("Enable adaptive battery. ");
        }
        if (activeSession.drainPerHour > 2.0f) {
            recs.append("Identify power-hungry background apps. ");
        }
        return recs.toString().trim();
    }

    private String classifyLeakage(float drainPerHour, float deepSleepRate, int wakes) {
        if (drainPerHour > 2.0f || wakes > 6 || deepSleepRate < 0.3f)
            return "LEAK";
        if (drainPerHour > 1.0f || wakes > 3 || deepSleepRate < 0.5f)
            return "WARNING";
        return "NORMAL";
    }

    private String benchmark(float drainPerHour) {
        if (drainPerHour < IDLE_DRAIN_EXCELLENT)
            return "excellent";
        if (drainPerHour < IDLE_DRAIN_GOOD)
            return "good";
        if (drainPerHour < IDLE_DRAIN_FAIR)
            return "fair";
        if (drainPerHour < IDLE_DRAIN_POOR)
            return "poor";
        return "failing";
    }

    /**
     * Get the active idle session (for testing/debugging).
     */
    public IdleDrainSession getActiveSession() {
        return activeSession;
    }

    /**
     * Check if currently tracking an idle session.
     */
    public boolean hasActiveSession() {
        return activeSession != null && activeSession.endTime == 0;
    }
}
