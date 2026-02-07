package com.example.hexnil.manager;

import android.content.Context;
import android.util.Log;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.model.UpdateSession;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles post-OTA warm-up period exclusion for battery diagnostics.
 *
 * After an OTA update installs, the first 48 hours of data should be excluded
 * from comparisons because the device is performing background optimization
 * (dexopt, re-indexing, cache rebuilding). For major system OTAs, the warm-up
 * period is extended to 72 hours to account for heavier optimization workloads.
 *
 * This class provides methods to:
 * - Check if the device is currently in a post-OTA warmup period
 * - Query remaining warmup time and progress
 * - Determine whether individual data points should be excluded
 * - Mark sessions as warmup-excluded after the period elapses
 */
public class WarmupPeriodExcluder {

    private static final String TAG = "WarmupExcluder";

    /** Standard warm-up period: 48 hours for security patches, firmware, and unknown updates */
    static final long WARMUP_PERIOD_MS = 48L * 60 * 60 * 1000;

    /** Extended warm-up period: 72 hours for major system OTA updates */
    static final long EXTENDED_WARMUP_MS = 72L * 60 * 60 * 1000;

    /** Maximum number of recent sessions to query when searching for active warmup periods */
    private static final int RECENT_SESSION_QUERY_LIMIT = 20;

    private final Context context;
    private final HexNilDatabase database;

    public WarmupPeriodExcluder(Context context, HexNilDatabase database) {
        this.context = context;
        this.database = database;
    }

    /**
     * Check if the device is currently in a post-OTA warm-up period.
     * Queries recent update sessions with status "installed_monitoring" or "analyzing"
     * and checks if any session's updateInstalledAt is within WARMUP_PERIOD_MS of now.
     *
     * @return true if device is currently in a warmup period
     */
    public boolean isInWarmupPeriod() {
        try {
            List<UpdateSession> sessions = findActiveMonitoringSessions();
            if (sessions.isEmpty()) {
                return false;
            }

            long now = System.currentTimeMillis();
            for (UpdateSession session : sessions) {
                if (session.updateInstalledAt != null) {
                    long elapsed = now - session.updateInstalledAt;
                    if (elapsed >= 0 && elapsed < WARMUP_PERIOD_MS) {
                        return true;
                    }
                }
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Error checking warmup period", e);
            return false;
        }
    }

    /**
     * Check if a specific update session is in its warm-up period.
     * Uses EXTENDED_WARMUP_MS (72hr) for "system_ota" type, WARMUP_PERIOD_MS (48hr) for others.
     *
     * @param sessionId the update session id to check
     * @return true if the session is currently in its warmup period
     */
    public boolean isInWarmupPeriod(long sessionId) {
        try {
            UpdateSession session = database.updateSessionDao().getById(sessionId);
            if (session == null) {
                Log.w(TAG, "Session not found: " + sessionId);
                return false;
            }

            if (session.updateInstalledAt == null) {
                return false;
            }

            long now = System.currentTimeMillis();
            long warmupDuration = getWarmupDurationMs(session);
            long elapsed = now - session.updateInstalledAt;
            return elapsed >= 0 && elapsed < warmupDuration;
        } catch (Exception e) {
            Log.e(TAG, "Error checking warmup period for session " + sessionId, e);
            return false;
        }
    }

    /**
     * Returns the number of milliseconds remaining in the current warmup period,
     * or 0 if the device is not currently in a warmup period.
     *
     * Uses the most recent installation session to determine the remaining time.
     *
     * @return milliseconds remaining in warmup, or 0 if not in warmup
     */
    public long getWarmupRemainingMs() {
        try {
            UpdateSession session = findMostRecentInstallation();
            if (session == null || session.updateInstalledAt == null) {
                return 0L;
            }

            long now = System.currentTimeMillis();
            long warmupDuration = getWarmupDurationMs(session);
            long warmupEnd = session.updateInstalledAt + warmupDuration;
            long remaining = warmupEnd - now;

            return Math.max(0L, remaining);
        } catch (Exception e) {
            Log.e(TAG, "Error calculating warmup remaining", e);
            return 0L;
        }
    }

    /**
     * Returns the progress through the current warmup period as a value from 0.0 to 1.0.
     * Returns 1.0 if the device is not in a warmup period (warmup is complete or no active warmup).
     *
     * @return progress through warmup period (0.0 = just started, 1.0 = complete or not in warmup)
     */
    public float getWarmupProgress() {
        try {
            UpdateSession session = findMostRecentInstallation();
            if (session == null || session.updateInstalledAt == null) {
                return 1.0f;
            }

            long now = System.currentTimeMillis();
            long warmupDuration = getWarmupDurationMs(session);
            long elapsed = now - session.updateInstalledAt;

            if (elapsed < 0) {
                return 0.0f;
            }

            if (elapsed >= warmupDuration) {
                return 1.0f;
            }

            return (float) elapsed / (float) warmupDuration;
        } catch (Exception e) {
            Log.e(TAG, "Error calculating warmup progress", e);
            return 1.0f;
        }
    }

    /**
     * Determines whether a data point at the given timestamp should be excluded
     * from pre/post OTA comparisons because it falls within any active warmup period.
     *
     * Checks all recent sessions (up to {@link #RECENT_SESSION_QUERY_LIMIT}) to see
     * if the timestamp falls between any session's updateInstalledAt and
     * updateInstalledAt + warmupDuration.
     *
     * @param timestamp the timestamp of the data point to evaluate
     * @return true if the data point falls within any warmup period and should be excluded
     */
    public boolean shouldExcludeDataPoint(long timestamp) {
        try {
            List<UpdateSession> sessions = database.updateSessionDao()
                    .getRecent(RECENT_SESSION_QUERY_LIMIT);
            if (sessions == null || sessions.isEmpty()) {
                return false;
            }

            for (UpdateSession session : sessions) {
                if (session.updateInstalledAt == null) {
                    continue;
                }

                long warmupDuration = getWarmupDurationMs(session);
                long warmupStart = session.updateInstalledAt;
                long warmupEnd = warmupStart + warmupDuration;

                if (timestamp >= warmupStart && timestamp < warmupEnd) {
                    Log.d(TAG, "Data point at " + timestamp
                            + " excluded: falls within warmup for session " + session.id
                            + " (type=" + session.updateType + ")");
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Error checking data point exclusion for timestamp " + timestamp, e);
            return false;
        }
    }

    /**
     * Marks an update session's warmup period data as excluded by setting
     * warmupPeriodExcluded = 1 on the session and persisting the change.
     *
     * This should be called after the warmup period elapses to record that
     * the first 48/72 hours of post-update data was properly excluded from analysis.
     *
     * @param sessionId the update session id to mark as warmup-excluded
     */
    public void markWarmupExcluded(long sessionId) {
        try {
            UpdateSession session = database.updateSessionDao().getById(sessionId);
            if (session == null) {
                Log.w(TAG, "Cannot mark warmup excluded: session not found: " + sessionId);
                return;
            }

            session.warmupPeriodExcluded = 1;
            database.updateSessionDao().update(session);
            Log.i(TAG, "Marked warmup excluded for session " + sessionId
                    + " (type=" + session.updateType + ")");
        } catch (Exception e) {
            Log.e(TAG, "Error marking warmup excluded for session " + sessionId, e);
        }
    }

    /**
     * Returns all update sessions that are currently within their warmup period.
     *
     * Scans recent sessions and returns those whose updateInstalledAt timestamp
     * is within the appropriate warmup duration (48hr or 72hr depending on type).
     *
     * @return list of UpdateSession objects currently in warmup, never null
     */
    public List<UpdateSession> getActiveWarmupSessions() {
        List<UpdateSession> result = new ArrayList<>();
        try {
            List<UpdateSession> sessions = database.updateSessionDao()
                    .getRecent(RECENT_SESSION_QUERY_LIMIT);
            if (sessions == null || sessions.isEmpty()) {
                return result;
            }

            long now = System.currentTimeMillis();
            for (UpdateSession session : sessions) {
                if (session.updateInstalledAt == null) {
                    continue;
                }

                long warmupDuration = getWarmupDurationMs(session);
                long elapsed = now - session.updateInstalledAt;

                if (elapsed >= 0 && elapsed < warmupDuration) {
                    result.add(session);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting active warmup sessions", e);
        }
        return result;
    }

    /**
     * Determines the warmup duration for a given update session based on its update type.
     * System OTAs (full OS version upgrades) receive a 72-hour warmup window because
     * they trigger significantly more background optimization. All other update types
     * (security patches, firmware updates, unknown) use the standard 48-hour window.
     *
     * @param session the update session to evaluate
     * @return warmup duration in milliseconds (EXTENDED_WARMUP_MS or WARMUP_PERIOD_MS)
     */
    public long getWarmupDurationMs(UpdateSession session) {
        if (session == null) {
            return WARMUP_PERIOD_MS;
        }
        if ("system_ota".equals(session.updateType)) {
            return EXTENDED_WARMUP_MS;
        }
        return WARMUP_PERIOD_MS;
    }

    /**
     * Finds the most recent update session with status "installed_monitoring",
     * falling back to scanning recent sessions for any with status "installed_monitoring"
     * or "analyzing" if the direct query returns nothing.
     *
     * Results are ordered by updateInstalledAt descending so the most recent
     * installation is returned.
     *
     * @return the most recent installed/monitoring session, or null if none found
     */
    private UpdateSession findMostRecentInstallation() {
        try {
            // Direct query for the most recent monitoring session
            UpdateSession session = database.updateSessionDao().getMonitoringSession();
            if (session != null) {
                return session;
            }

            // Fallback: scan recent sessions for any that may still be in warmup
            List<UpdateSession> recent = database.updateSessionDao()
                    .getRecent(RECENT_SESSION_QUERY_LIMIT);
            if (recent == null || recent.isEmpty()) {
                return null;
            }

            UpdateSession mostRecent = null;
            for (UpdateSession candidate : recent) {
                if (candidate.updateInstalledAt == null) {
                    continue;
                }
                if ("installed_monitoring".equals(candidate.status)
                        || "analyzing".equals(candidate.status)) {
                    if (mostRecent == null
                            || candidate.updateInstalledAt > mostRecent.updateInstalledAt) {
                        mostRecent = candidate;
                    }
                }
            }
            return mostRecent;
        } catch (Exception e) {
            Log.e(TAG, "Error finding most recent installation", e);
            return null;
        }
    }

    /**
     * Finds all active monitoring sessions with status "installed_monitoring" or "analyzing".
     * These represent update sessions that are still being tracked post-installation.
     *
     * @return list of active monitoring sessions, never null
     */
    private List<UpdateSession> findActiveMonitoringSessions() {
        List<UpdateSession> result = new ArrayList<>();
        try {
            List<UpdateSession> recent = database.updateSessionDao()
                    .getRecent(RECENT_SESSION_QUERY_LIMIT);
            if (recent == null || recent.isEmpty()) {
                return result;
            }
            for (UpdateSession session : recent) {
                if ("installed_monitoring".equals(session.status)
                        || "analyzing".equals(session.status)) {
                    result.add(session);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error finding active monitoring sessions", e);
        }
        return result;
    }
}
