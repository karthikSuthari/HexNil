package com.example.hexnil.manager;

import android.content.Context;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.model.BatterySnapshot;
import com.example.hexnil.model.IdleDrainSession;

/**
 * OEM-Grade Idle Drain Analyzer
 * 
 * Tracks standby battery drain and Doze mode effectiveness with comprehensive
 * metrics:
 * - Doze state timing (entry/exit/duration)
 * - Deep sleep vs light sleep vs awake time breakdown
 * - Leakage detection and classification
 * - Benchmark comparison against industry standards
 * 
 * FIXED: Previously didn't populate deepSleepTime, lightSleepTime, awakeTime.
 * Now properly calculates all timing-based metrics.
 */
public class IdleDrainAnalyzer {

    private static final String TAG = "IdleDrainAnalyzer";

    // Benchmarks
    private static final float IDLE_DRAIN_EXCELLENT = 0.3f; // %/hr
    private static final float IDLE_DRAIN_GOOD = 0.5f; // %/hr
    private static final float IDLE_DRAIN_FAIR = 1.0f; // %/hr
    private static final float IDLE_DRAIN_POOR = 2.0f; // %/hr

    private final HexNilDatabase database;
    private IdleDrainSession activeSession;

    // State tracking for timing accumulation
    private long lastDozeStateTime;
    private boolean lastDozeState;
    private long accumulatedDeepSleepTime;
    private long accumulatedAwakeTime;
    private int dozeExitCount;

    public IdleDrainAnalyzer(Context context, HexNilDatabase database) {
        this.database = database;
    }

    public synchronized void beginIdleIfNeeded(BatterySnapshot snapshot) {
        if (snapshot == null || snapshot.screenOn || snapshot.isCharging)
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

        database.idleDrainSessionDao().insert(activeSession);
    }

    public synchronized void processSnapshot(BatterySnapshot snapshot) {
        if (snapshot == null)
            return;

        if (snapshot.screenOn || snapshot.isCharging) {
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
        if (activeSession != null) {
            activeSession.alarmWakeups += 1;
            activeSession.alarmsFired += 1;
            database.idleDrainSessionDao().update(activeSession);
        }
    }

    public synchronized void recordNetworkWakeup() {
        if (activeSession != null) {
            activeSession.networkWakeups += 1;
            activeSession.networkWakes += 1;
            database.idleDrainSessionDao().update(activeSession);
        }
    }

    private void updateIdleMetrics(BatterySnapshot snapshot) {
        if (activeSession == null)
            return;

        activeSession.dozeActive = snapshot.isDozeMode;

        long totalDuration = Math.max(1, snapshot.timestamp - activeSession.startTime);

        // =====================================================================
        // TIMING FIELDS (CRITICAL FIX)
        // =====================================================================
        activeSession.deepSleepTime = accumulatedDeepSleepTime;
        activeSession.awakeTime = accumulatedAwakeTime;
        activeSession.lightSleepTime = Math.max(0,
                totalDuration - accumulatedDeepSleepTime - accumulatedAwakeTime);

        // Doze exits
        activeSession.dozeExits = dozeExitCount;

        // =====================================================================
        // PERCENTAGE CALCULATIONS
        // =====================================================================
        activeSession.deepSleepPercentage = totalDuration > 0
                ? (accumulatedDeepSleepTime * 100f) / totalDuration
                : 0f;
        activeSession.deepSleepSuccessRate = totalDuration > 0
                ? (float) accumulatedDeepSleepTime / totalDuration
                : 0f;

        // =====================================================================
        // EFFECTIVENESS SCORING
        // =====================================================================
        // Doze effectiveness combines deep sleep percentage with low drain
        float drainPenalty = Math.min(50, activeSession.drainPerHour * 20);
        float sleepBonus = activeSession.deepSleepPercentage * 0.5f;
        activeSession.dozeEffectiveness = Math.max(0f, Math.min(100f, sleepBonus + 50 - drainPenalty));
        activeSession.dozeEffectivenessScore = activeSession.dozeEffectiveness;

        // =====================================================================
        // SESSION CLASSIFICATION
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
        // EXPECTED DRAIN & DELTA
        // =====================================================================
        // Expected drain: 0.3%/hr during deep doze, 0.7%/hr during idle
        float expectedRate = activeSession.deepSleepPercentage > 50 ? 0.3f : 0.7f;
        activeSession.expectedDrain = expectedRate * activeSession.getDurationHours();
        activeSession.drainDelta = activeSession.drainAmount - activeSession.expectedDrain;

        // =====================================================================
        // LEAKAGE DETECTION
        // =====================================================================
        int totalWakeups = activeSession.alarmWakeups + activeSession.networkWakeups;
        activeSession.hasLeakage = activeSession.drainPerHour > 1.0f ||
                totalWakeups > 3 ||
                dozeExitCount > 5;

        activeSession.leakageSources = buildLeakageSources();
        activeSession.leakageClass = classifyLeakage(
                activeSession.drainPerHour,
                activeSession.deepSleepSuccessRate,
                totalWakeups + dozeExitCount);
        activeSession.recommendations = generateRecommendations();
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
