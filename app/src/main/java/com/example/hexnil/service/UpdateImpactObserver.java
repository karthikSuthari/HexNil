package com.example.hexnil.service;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Data;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.database.AnomalyRecordDao;
import com.example.hexnil.database.BatterySnapshotDao;
import com.example.hexnil.database.CrashEventDao;
import com.example.hexnil.database.AnrEventDao;
import com.example.hexnil.database.DailyAggregateDao;
import com.example.hexnil.database.IdleDrainSessionDao;
import com.example.hexnil.database.ThermalEventDao;
import com.example.hexnil.database.UpdateSessionDao;
import com.example.hexnil.database.PreUpdateSnapshotDao;
import com.example.hexnil.database.PreUpdateIssueDao;
import com.example.hexnil.database.PostUpdateVerificationDao;
import com.example.hexnil.database.UpdateRegressionDao;
import com.example.hexnil.model.AnomalyRecord;
import com.example.hexnil.model.DailyAggregate;
import com.example.hexnil.model.UpdateSession;
import com.example.hexnil.model.PreUpdateSnapshot;
import com.example.hexnil.model.PreUpdateIssue;
import com.example.hexnil.model.PostUpdateVerification;
import com.example.hexnil.model.UpdateRegression;
import com.example.hexnil.worker.PostUpdateVerificationWorker;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Update Impact Observer - Silently tracks system updates and their impact.
 *
 * This manager class handles:
 * 1. Detection of pending updates and update completion
 * 2. Capturing pre-update baseline state (known issues, performance metrics)
 * 3. Post-update verification (comparing before/after to detect fixes,
 *    unchanged issues, regressions)
 * 4. Building update impact verdicts
 *
 * The entire process is silent - users only see results when verification is
 * complete.
 *
 * Lifecycle:
 *   pending → baseline_locked → installed_monitoring → analyzing → verified
 */
public class UpdateImpactObserver {

    private static final String TAG = "UpdateImpactObserver";
    private static final String PREFS_NAME = "update_impact_prefs";
    private static final String KEY_LAST_BUILD_FINGERPRINT = "last_build_fingerprint";
    private static final String KEY_LAST_ANDROID_VERSION = "last_android_version";
    private static final String KEY_LAST_SECURITY_PATCH = "last_security_patch";
    private static final String KEY_LAST_MODEM_VERSION = "last_modem_version";
    private static final String KEY_PENDING_SESSION_UUID = "pending_session_uuid";
    private static final String KEY_BOOT_ELAPSED_REALTIME = "boot_elapsed_realtime";

    /** Minimum hours of post-update observation before verification is allowed */
    private static final long MIN_POST_UPDATE_OBSERVATION_HOURS = 24;

    /** Delay before scheduling verification worker (hours) */
    private static final long VERIFICATION_DELAY_HOURS = 48;

    /** Minimum pre-update observation days for high-quality baseline */
    private static final int MIN_BASELINE_DAYS = 3;

    /** Lookback window for anomaly collection (milliseconds) — 14 days */
    private static final long ANOMALY_LOOKBACK_MS = 14L * 24 * 60 * 60 * 1000;

    /**
     * Thresholds for regression detection.
     * A regression is only flagged when post-update metric degrades by more than this.
     */
    private static final double REGRESSION_THRESHOLD_PERCENT = 25.0;

    private static volatile UpdateImpactObserver instance;
    private final Context context;
    private final ExecutorService executor;
    private final HexNilDatabase database;
    private final SharedPreferences prefs;

    // Core DAOs
    private final UpdateSessionDao sessionDao;
    private final PreUpdateSnapshotDao snapshotDao;
    private final PreUpdateIssueDao issueDao;
    private final PostUpdateVerificationDao verificationDao;
    private final UpdateRegressionDao regressionDao;

    // Data source DAOs
    private final AnomalyRecordDao anomalyDao;
    private final BatterySnapshotDao batterySnapshotDao;
    private final CrashEventDao crashEventDao;
    private final AnrEventDao anrEventDao;
    private final DailyAggregateDao dailyAggregateDao;
    private final ThermalEventDao thermalEventDao;
    private final IdleDrainSessionDao idleDrainDao;

    private UpdateImpactObserver(Context context) {
        this.context = context.getApplicationContext();
        this.executor = Executors.newSingleThreadExecutor();
        this.database = HexNilDatabase.getInstance(context);
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // Core DAOs
        this.sessionDao = database.updateSessionDao();
        this.snapshotDao = database.preUpdateSnapshotDao();
        this.issueDao = database.preUpdateIssueDao();
        this.verificationDao = database.postUpdateVerificationDao();
        this.regressionDao = database.updateRegressionDao();

        // Data source DAOs
        this.anomalyDao = database.anomalyRecordDao();
        this.batterySnapshotDao = database.batterySnapshotDao();
        this.crashEventDao = database.crashEventDao();
        this.anrEventDao = database.anrEventDao();
        this.dailyAggregateDao = database.dailyAggregateDao();
        this.thermalEventDao = database.thermalEventDao();
        this.idleDrainDao = database.idleDrainSessionDao();
    }

    public static UpdateImpactObserver getInstance(Context context) {
        if (instance == null) {
            synchronized (UpdateImpactObserver.class) {
                if (instance == null) {
                    instance = new UpdateImpactObserver(context);
                }
            }
        }
        return instance;
    }

    // ========================================================================
    // UPDATE DETECTION
    // ========================================================================

    /**
     * Called on boot to check if an update just occurred.
     * Compares stored fingerprint with current system fingerprint.
     */
    public void checkForUpdateOnBoot() {
        executor.execute(() -> {
            try {
                String currentFingerprint = Build.FINGERPRINT;
                String storedFingerprint = prefs.getString(KEY_LAST_BUILD_FINGERPRINT, null);

                if (storedFingerprint == null) {
                    // First run — just store current state
                    saveCurrentSystemState();
                    Log.i(TAG, "First boot - initialized fingerprint tracking");
                    return;
                }

                if (!currentFingerprint.equals(storedFingerprint)) {
                    Log.i(TAG, "System update detected!");
                    Log.i(TAG, "Previous: " + storedFingerprint);
                    Log.i(TAG, "Current: " + currentFingerprint);

                    // Clean up stale sessions from prior updates that were never verified
                    cleanupStaleInstalledSessions();

                    handleUpdateDetected(storedFingerprint, currentFingerprint, "boot_fingerprint_change");
                } else {
                    Log.d(TAG, "No update detected - fingerprint unchanged");

                    // Check if there's a monitoring session ready for verification
                    checkPendingVerification();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error checking for update on boot", e);
            }
        });
    }

    /**
     * Called proactively (e.g., from MY_PACKAGE_REPLACED or periodic checks)
     * to detect fingerprint changes without waiting for BOOT_COMPLETED.
     */
    public void checkForUpdateProactively() {
        executor.execute(() -> {
            try {
                String currentFingerprint = Build.FINGERPRINT;
                String storedFingerprint = prefs.getString(KEY_LAST_BUILD_FINGERPRINT, null);

                if (storedFingerprint != null && !currentFingerprint.equals(storedFingerprint)) {
                    Log.i(TAG, "Proactive update detection - fingerprint changed");
                    cleanupStaleInstalledSessions();
                    handleUpdateDetected(storedFingerprint, currentFingerprint, "proactive_detection");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in proactive update check", e);
            }
        });
    }

    /**
     * Handles the detection of a system update.
     */
    private void handleUpdateDetected(String previousFingerprint, String currentFingerprint,
            String detectionMethod) {
        try {
            // Avoid duplicate sessions for the same update transition
            UpdateSession existingSession = sessionDao.getByPreviousFingerprint(previousFingerprint);
            if (existingSession != null &&
                    currentFingerprint.equals(existingSession.currentBuildFingerprint) &&
                    "installed_monitoring".equals(existingSession.status)) {
                Log.i(TAG, "Update session already exists for this transition, skipping");
                return;
            }

            // Check if we have a pending session with baseline locked
            String pendingSessionUuid = prefs.getString(KEY_PENDING_SESSION_UUID, null);
            UpdateSession session = null;

            if (pendingSessionUuid != null) {
                session = sessionDao.getByUuid(pendingSessionUuid);
                if (session != null && "baseline_locked".equals(session.status)) {
                    session.markInstalled(currentFingerprint, getCurrentAndroidVersion(),
                            getCurrentSecurityPatch(), getModemVersion());
                    session.detectionMethod = detectionMethod;
                    sessionDao.update(session);
                    Log.i(TAG, "Session transitioned: baseline_locked → installed_monitoring");
                } else {
                    session = null; // Invalid state, create fresh
                }
            }

            if (session == null) {
                // No pending session — create without pre-update baseline
                String sessionUuid = UUID.randomUUID().toString();
                session = UpdateSession.createPending(sessionUuid, previousFingerprint,
                        getStoredAndroidVersion(), getStoredSecurityPatch(),
                        getStoredModemVersion(), detectionMethod);
                session.markInstalled(currentFingerprint, getCurrentAndroidVersion(),
                        getCurrentSecurityPatch(), getModemVersion());

                long sessionId = sessionDao.insert(session);
                session.id = sessionId;
                Log.i(TAG, "Created new update session without baseline (id=" + sessionId + ")");
            }

            // Persist current state
            saveCurrentSystemState();
            prefs.edit()
                    .remove(KEY_PENDING_SESSION_UUID)
                    .putLong(KEY_BOOT_ELAPSED_REALTIME, SystemClock.elapsedRealtime())
                    .apply();

            // Schedule post-update verification
            schedulePostUpdateVerification(session);

        } catch (Exception e) {
            Log.e(TAG, "Error handling update detection", e);
        }
    }

    // ========================================================================
    // PRE-UPDATE BASELINE LOCK
    // ========================================================================

    /**
     * Refreshes the pre-update baseline if appropriate.
     * Called automatically on every app start via HexNilApplication.
     *
     * Rules:
     * - Skip if there's already an active session (pending / baseline_locked / installed_monitoring)
     * - Skip if a baseline was locked within the last 24 hours (avoid churn)
     * - Otherwise, lock a fresh baseline so one always exists before the next OTA
     */
    public void refreshBaselineIfNeeded() {
        executor.execute(() -> {
            try {
                // Don't overwrite an active session
                UpdateSession activeSession = sessionDao.getActiveSession();
                if (activeSession != null) {
                    Log.d(TAG, "Active session exists (status=" + activeSession.status
                            + "), skipping baseline refresh");
                    return;
                }

                // Check if we locked a baseline recently (within 24h)
                long lastLockTime = prefs.getLong("last_baseline_lock_time", 0);
                long hoursSinceLastLock = (System.currentTimeMillis() - lastLockTime) / (3600L * 1000);
                if (hoursSinceLastLock < 24) {
                    Log.d(TAG, "Baseline locked " + hoursSinceLastLock + "h ago, skipping");
                    return;
                }

                // Check that we have enough data for a meaningful baseline
                int snapshotCount = getSnapshotCount();
                if (snapshotCount < 10) {
                    Log.d(TAG, "Insufficient data for baseline (snapshots=" + snapshotCount + ")");
                    return;
                }

                Log.i(TAG, "Refreshing pre-update baseline automatically");
                lockPreUpdateBaselineInternal();

            } catch (Exception e) {
                Log.e(TAG, "Error in refreshBaselineIfNeeded", e);
            }
        });
    }

    /**
     * Locks the current state as the pre-update baseline.
     * Call this when an update is pending, or automatically via refreshBaselineIfNeeded().
     */
    public void lockPreUpdateBaseline() {
        executor.execute(() -> lockPreUpdateBaselineInternal());
    }

    /**
     * Internal implementation of baseline locking (runs on executor thread).
     * All database writes are wrapped in a transaction for atomicity.
     */
    private void lockPreUpdateBaselineInternal() {
        try {
            // Avoid duplicate baselines
            UpdateSession activeSession = sessionDao.getActiveSession();
            if (activeSession != null) {
                Log.w(TAG, "Active session already exists (status=" + activeSession.status
                        + "), skipping baseline lock");
                return;
            }

            // Create new session
            String sessionUuid = UUID.randomUUID().toString();
            UpdateSession session = UpdateSession.createPending(
                    sessionUuid,
                    Build.FINGERPRINT,
                    getCurrentAndroidVersion(),
                    getCurrentSecurityPatch(),
                    getModemVersion(),
                    "auto_baseline_lock");

            // Capture data outside the transaction (reads are safe)
            PreUpdateSnapshot snapshot = captureCurrentSnapshot(0); // temp sessionId
            List<PreUpdateIssue> knownIssues = captureKnownIssues(0); // temp sessionId
            double dataQuality = calculateDataQuality();
            double observationHours = calculateObservationHours();
            int snapshotCount = getSnapshotCount();

            // Atomic write: all-or-nothing
            database.runInTransaction(() -> {
                long sessionId = sessionDao.insert(session);
                session.id = sessionId;

                // Fix sessionId on captured data
                snapshot.sessionId = sessionId;
                snapshotDao.insert(snapshot);

                if (!knownIssues.isEmpty()) {
                    for (PreUpdateIssue issue : knownIssues) {
                        issue.sessionId = sessionId;
                    }
                    issueDao.insertAll(knownIssues);
                }

                session.lockBaseline(knownIssues.size(), dataQuality, observationHours, snapshotCount);
                sessionDao.update(session);
            });

            // Store pending session UUID and timestamp
            prefs.edit()
                    .putString(KEY_PENDING_SESSION_UUID, session.sessionUuid)
                    .putLong("last_baseline_lock_time", System.currentTimeMillis())
                    .apply();

            Log.i(TAG, "Pre-update baseline locked: session=" + session.sessionUuid
                    + " issues=" + knownIssues.size()
                    + " quality=" + String.format("%.2f", dataQuality)
                    + " hours=" + String.format("%.1f", observationHours)
                    + " snapshots=" + snapshotCount);

        } catch (Exception e) {
            Log.e(TAG, "Error locking pre-update baseline", e);
        }
    }

    // ========================================================================
    // DATA CAPTURE — REAL IMPLEMENTATIONS
    // ========================================================================

    /**
     * Captures current system metrics as a pre-update snapshot.
     * Aggregates from existing tables — no hardcoded values.
     */
    private PreUpdateSnapshot captureCurrentSnapshot(long sessionId) {
        PreUpdateSnapshot snapshot = PreUpdateSnapshot.create(
                sessionId, Build.FINGERPRINT, getCurrentAndroidVersion(),
                getCurrentSecurityPatch(), getModemVersion());

        long now = System.currentTimeMillis();
        long sevenDaysAgo = now - (7L * 24 * 60 * 60 * 1000);

        // Aggregate from DailyAggregates
        try {
            List<DailyAggregate> recentAggs = dailyAggregateDao.getRecentDeviceLevel(7);
            if (!recentAggs.isEmpty()) {
                double totalDrain = 0, totalTemp = 0, maxTemp = 0;
                double totalJank = 0, totalLaunch = 0, totalFps = 0, totalMem = 0;
                double totalCrashes = 0, totalAnrs = 0, totalThermalEvents = 0;
                int countWithDrain = 0, countWithPerf = 0;

                for (DailyAggregate agg : recentAggs) {
                    if (agg.avgDrainRatePerHour > 0) {
                        totalDrain += agg.avgDrainRatePerHour;
                        countWithDrain++;
                    }
                    totalTemp += agg.avgTemp;
                    if (agg.maxTemp > maxTemp) maxTemp = agg.maxTemp;

                    if (agg.avgColdLaunchMs > 0) {
                        totalLaunch += agg.avgColdLaunchMs;
                        totalJank += agg.avgJankPercent;
                        totalFps += agg.avgFps;
                        totalMem += agg.avgMemoryUsedPercent;
                        countWithPerf++;
                    }
                    totalCrashes += agg.crashCount;
                    totalAnrs += agg.anrCount;
                    totalThermalEvents += agg.thermalThrottleEvents;
                }

                int days = recentAggs.size();

                snapshot.setBatteryBaseline(
                        countWithDrain > 0 ? totalDrain / countWithDrain : null,
                        getAverageIdleDrain(sevenDaysAgo),
                        null, null, null);

                if (countWithPerf > 0) {
                    snapshot.setPerformanceBaseline(
                            totalLaunch / countWithPerf,
                            totalJank / countWithPerf,
                            totalFps / countWithPerf,
                            totalMem / countWithPerf);
                }

                snapshot.setStabilityBaseline(
                        totalCrashes / days,
                        totalAnrs / days,
                        totalThermalEvents / days);

                snapshot.setThermalBaseline(totalTemp / days, maxTemp, null, null);
                snapshot.setDataQuality(calculateDataQuality(), days, getSnapshotCount());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error capturing snapshot metrics", e);
        }

        // Known issues count
        try {
            List<AnomalyRecord> unresolved = anomalyDao.getUnresolvedAnomalies();
            List<AnomalyRecord> highPriority = anomalyDao.getUnresolvedHighPriority();
            snapshot.setIssuesSummary(null, unresolved.size(), highPriority.size());
        } catch (Exception e) {
            Log.e(TAG, "Error capturing anomaly summary", e);
        }

        return snapshot;
    }

    /**
     * Converts current unresolved anomalies into PreUpdateIssue records.
     * Derived strictly from anomaly_records — no fabrication.
     */
    private List<PreUpdateIssue> captureKnownIssues(long sessionId) {
        List<PreUpdateIssue> issues = new ArrayList<>();

        try {
            long cutoff = System.currentTimeMillis() - ANOMALY_LOOKBACK_MS;
            List<AnomalyRecord> anomalies = anomalyDao.getAnomaliesSince(cutoff);

            for (AnomalyRecord anomaly : anomalies) {
                if (anomaly.resolved && !anomaly.isRecurring) continue;

                String issueUuid = UUID.randomUUID().toString();
                PreUpdateIssue issue = PreUpdateIssue.fromAnomaly(
                        sessionId,
                        issueUuid,
                        mapAnomalyTypeToIssueType(anomaly.anomalyType),
                        anomaly.sourceComponent != null ? anomaly.sourceComponent : "system",
                        anomaly.severity != null ? anomaly.severity : "medium",
                        anomaly.title != null ? anomaly.title : "Unknown anomaly",
                        anomaly.description);

                issue.setMetrics(
                        anomaly.metricName,
                        (double) anomaly.expectedValue,
                        (double) anomaly.actualValue,
                        anomaly.metricUnit);

                issue.setFrequency(
                        anomaly.occurrenceCount,
                        anomaly.periodStart,
                        anomaly.periodEnd > 0 ? anomaly.periodEnd : anomaly.detectedAt);

                issue.setEvidence(
                        "anomaly_records",
                        "SELECT * FROM anomaly_records WHERE id = " + anomaly.id,
                        1);

                if (anomaly.sourceApp != null) {
                    issue.setSourceApp(anomaly.sourceApp, anomaly.sourceAppName);
                }

                issue.impactScore = calculateIssueImpact(anomaly);
                issues.add(issue);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error capturing known issues", e);
        }

        Log.i(TAG, "Captured " + issues.size() + " known issues for baseline");
        return issues;
    }

    /**
     * Calculates data quality score (0.0 to 1.0) based on actual observation coverage.
     */
    private double calculateDataQuality() {
        try {
            int snapshotCount = getSnapshotCount();
            int aggregateDays = dailyAggregateDao.getDistinctDateCount();

            double snapshotCoverage = Math.min(1.0, snapshotCount / 500.0);
            double dayCoverage = Math.min(1.0, aggregateDays / 7.0);
            double quality = (snapshotCoverage * 0.5) + (dayCoverage * 0.5);
            return Math.max(0.0, Math.min(1.0, quality));
        } catch (Exception e) {
            Log.e(TAG, "Error calculating data quality", e);
            return 0.0;
        }
    }

    /**
     * Calculates observation hours from battery snapshot timestamps.
     * Uses MIN(timestamp) query for accuracy instead of loading objects.
     */
    private double calculateObservationHours() {
        try {
            long sevenDaysAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000);
            Long oldest = batterySnapshotDao.getOldestTimestampSince(sevenDaysAgo);
            if (oldest != null) {
                return (System.currentTimeMillis() - oldest) / (3600.0 * 1000);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error calculating observation hours", e);
        }
        return 0.0;
    }

    /**
     * Gets the count of battery snapshots in recent 7-day window.
     * Uses COUNT(*) query to avoid loading full objects into memory.
     */
    private int getSnapshotCount() {
        try {
            long sevenDaysAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000);
            return batterySnapshotDao.getCountSince(sevenDaysAgo);
        } catch (Exception e) {
            Log.e(TAG, "Error getting snapshot count", e);
            return 0;
        }
    }

    private Double getAverageIdleDrain(long since) {
        try {
            float avg = idleDrainDao.getAverageIdleDrain(since);
            return avg > 0 ? (double) avg : null;
        } catch (Exception e) {
            return null;
        }
    }

    // ========================================================================
    // POST-UPDATE VERIFICATION
    // ========================================================================

    /**
     * Schedules post-update verification using WorkManager.
     */
    private void schedulePostUpdateVerification(UpdateSession session) {
        try {
            Data inputData = new Data.Builder()
                    .putString("session_uuid", session.sessionUuid)
                    .build();

            OneTimeWorkRequest verificationWork = new OneTimeWorkRequest.Builder(
                    PostUpdateVerificationWorker.class)
                    .setInitialDelay(VERIFICATION_DELAY_HOURS, TimeUnit.HOURS)
                    .setInputData(inputData)
                    .addTag("update_verification_" + session.sessionUuid)
                    .build();

            WorkManager.getInstance(context).enqueue(verificationWork);

            Log.i(TAG, "Verification scheduled: session=" + session.sessionUuid
                    + " delay=" + VERIFICATION_DELAY_HOURS + "h");
        } catch (Exception e) {
            Log.e(TAG, "Error scheduling post-update verification", e);
        }
    }

    /**
     * Runs post-update verification comparing pre and post states.
     * Core analysis: determines what an update fixed, broke, or left unchanged.
     * Async variant — dispatches to executor.
     */
    public void runPostUpdateVerification(String sessionUuid) {
        executor.execute(() -> runPostUpdateVerificationInternal(sessionUuid));
    }

    /**
     * Synchronous variant for PostUpdateVerificationWorker.
     * Returns true if verification ran to completion, false if it was
     * skipped or rescheduled (e.g., insufficient observation time).
     */
    public boolean runPostUpdateVerificationSync(String sessionUuid) {
        return runPostUpdateVerificationInternal(sessionUuid);
    }

    /**
     * Internal verification logic. Runs on the calling thread.
     * Returns true if verification completed, false if skipped/rescheduled.
     */
    private boolean runPostUpdateVerificationInternal(String sessionUuid) {
        try {
            UpdateSession session = sessionDao.getByUuid(sessionUuid);
            if (session == null) {
                Log.w(TAG, "Session not found: " + sessionUuid);
                return false;
            }
            if ("verified".equals(session.status)) {
                Log.w(TAG, "Session already verified: " + sessionUuid);
                return true; // Already done
            }

            // Enforce minimum observation window
            if (session.updateInstalledAt != null) {
                long hoursSince = (System.currentTimeMillis() - session.updateInstalledAt)
                        / (3600L * 1000);
                if (hoursSince < MIN_POST_UPDATE_OBSERVATION_HOURS) {
                    Log.w(TAG, "Insufficient observation time: " + hoursSince
                            + "h < " + MIN_POST_UPDATE_OBSERVATION_HOURS + "h. Rescheduling.");
                    schedulePostUpdateVerification(session);
                    return false;
                }
            }

            session.status = "analyzing";
            sessionDao.update(session);

            long postUpdateStart = session.updateInstalledAt != null
                    ? session.updateInstalledAt : session.detectedAt;

            // Step 1: Verify each pre-update issue
            int fixed = 0, improved = 0, unchanged = 0, worsened = 0, inconclusive = 0;
            List<PreUpdateIssue> preIssues = issueDao.getBySessionId(session.id);
            List<PostUpdateVerification> verifications = new ArrayList<>();

            for (PreUpdateIssue issue : preIssues) {
                PostUpdateVerification v = verifyIssue(session, issue, postUpdateStart);
                verifications.add(v);

                switch (v.verificationStatus != null ? v.verificationStatus : "") {
                    case "fixed": fixed++; break;
                    case "improved": improved++; break;
                    case "unchanged": unchanged++; break;
                    case "worsened": worsened++; break;
                    default: inconclusive++; break;
                }
            }

            if (!verifications.isEmpty()) {
                verificationDao.insertAll(verifications);
            }

            // Step 2: Detect new regressions
            List<UpdateRegression> regressions = detectNewRegressions(session, postUpdateStart);
            if (!regressions.isEmpty()) {
                regressionDao.insertAll(regressions);
            }

            // Step 3: Complete session
            session.issuesFixed = fixed + improved;
            session.issuesUnchanged = unchanged + inconclusive;
            session.newRegressionsDetected = regressions.size();

            determineVerdict(session);
            session.verificationCompletedAt = System.currentTimeMillis();
            session.status = "verified";
            sessionDao.update(session);

            Log.i(TAG, "Verification complete: " + sessionUuid
                    + " verdict=" + session.overallVerdict
                    + " fixed=" + fixed + " improved=" + improved
                    + " unchanged=" + unchanged + " worsened=" + worsened
                    + " regressions=" + regressions.size()
                    + " impact=" + String.format("%.1f", session.updateImpactScore));

            return true;

        } catch (Exception e) {
            Log.e(TAG, "Error running verification", e);
            return false;
        }
    }

    /**
     * Verifies a single pre-update issue against current post-update state.
     * Uses time-bounded queries to ensure only post-update data is compared.
     * Normalizes counts to per-day rates for fair pre/post comparison.
     */
    private PostUpdateVerification verifyIssue(UpdateSession session, PreUpdateIssue issue,
            long postUpdateStart) {
        PostUpdateVerification v = PostUpdateVerification.create(
                session.id, issue.id, issue.issueUuid, issue.issueType, issue.issueTitle);

        try {
            long now = System.currentTimeMillis();
            long postDurationMs = Math.max(now - postUpdateStart, 1);
            long postDays = Math.max(1, postDurationMs / (24L * 60 * 60 * 1000));

            // Pre-update observation period for rate normalization
            long preObservationMs = ANOMALY_LOOKBACK_MS; // 14 days
            long preDays = Math.max(1, preObservationMs / (24L * 60 * 60 * 1000));

            Double preValue = issue.actualValue;
            Integer preOccurrences = issue.occurrenceCount;
            Double postValue = null;
            Integer postOccurrences = null;
            boolean higherIsBetter = false;

            // Format postUpdateStart as date string for DailyAggregate queries
            String postStartDate = new SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    .format(new Date(postUpdateStart));

            switch (issue.issueType != null ? issue.issueType : "") {
                case "crash":
                    postOccurrences = crashEventDao.getCountInRange(postUpdateStart, now);
                    preOccurrences = preOccurrences != null ? preOccurrences : 0;
                    // Normalize to per-day rate for fair comparison
                    double preCrashRate = (double) preOccurrences / preDays;
                    double postCrashRate = (double) postOccurrences / postDays;
                    postValue = postCrashRate;
                    preValue = preCrashRate;
                    break;

                case "anr":
                    postOccurrences = anrEventDao.getCountInRange(postUpdateStart, now);
                    preOccurrences = preOccurrences != null ? preOccurrences : 0;
                    // Normalize to per-day rate for fair comparison
                    double preAnrRate = (double) preOccurrences / preDays;
                    double postAnrRate = (double) postOccurrences / postDays;
                    postValue = postAnrRate;
                    preValue = preAnrRate;
                    break;

                case "battery_drain":
                    // C1 FIX: Use time-bounded query instead of getRecentDeviceLevel(7)
                    List<DailyAggregate> postAggs = dailyAggregateDao.getDeviceLevelSince(postStartDate);
                    if (!postAggs.isEmpty()) {
                        double total = 0;
                        int count = 0;
                        for (DailyAggregate agg : postAggs) {
                            if (agg.avgDrainRatePerHour > 0) {
                                total += agg.avgDrainRatePerHour;
                                count++;
                            }
                        }
                        if (count > 0) postValue = total / count;
                    }
                    postOccurrences = postAggs.size();
                    break;

                case "thermal":
                    // C12 FIX: Use COUNT query instead of loading full objects
                    int postThermalCount = thermalEventDao.getEventCount(postUpdateStart);
                    postOccurrences = postThermalCount;
                    // Normalize to per-day rate
                    double preThermalRate = preValue != null ? preValue / preDays : 0;
                    double postThermalRate = (double) postThermalCount / postDays;
                    postValue = postThermalRate;
                    preValue = preThermalRate;
                    break;

                case "performance":
                    // C1 FIX: Use time-bounded query instead of getRecentDeviceLevel(7)
                    List<DailyAggregate> perfAggs = dailyAggregateDao.getDeviceLevelSince(postStartDate);
                    if (!perfAggs.isEmpty() && "avgColdLaunchMs".equals(issue.metricName)) {
                        double total = 0;
                        int count = 0;
                        for (DailyAggregate agg : perfAggs) {
                            if (agg.avgColdLaunchMs > 0) {
                                total += agg.avgColdLaunchMs;
                                count++;
                            }
                        }
                        if (count > 0) postValue = total / count;
                    }
                    postOccurrences = perfAggs.size();
                    break;

                default:
                    List<AnomalyRecord> postAnomalies = anomalyDao.getAnomaliesSince(postUpdateStart);
                    postOccurrences = 0;
                    for (AnomalyRecord a : postAnomalies) {
                        if (issue.issueType != null && issue.issueType.equals(
                                mapAnomalyTypeToIssueType(a.anomalyType))) {
                            postOccurrences++;
                        }
                    }
                    postValue = (double) postOccurrences;
                    preValue = preValue != null ? preValue : (double) (preOccurrences != null ? preOccurrences : 0);
                    break;
            }

            v.setComparison(preValue, preOccurrences, postValue, postOccurrences);

            if ("performance".equals(issue.issueType) &&
                    ("avgFps".equals(issue.metricName) || "batteryHealthPercent".equals(issue.metricName))) {
                higherIsBetter = true;
            }
            v.determineVerdict(higherIsBetter);

            // Adjust confidence by sample count
            if (postOccurrences != null && postOccurrences < 3) {
                v.confidenceScore = Math.min(v.confidenceScore != null ? v.confidenceScore : 0.5, 0.5);
            }

            v.setEvidence(null, "pre=" + preValue + " post=" + postValue
                    + " samples=" + postOccurrences + " postDays=" + postDays);

        } catch (Exception e) {
            Log.e(TAG, "Error verifying issue: " + issue.issueUuid, e);
            v.verificationStatus = "inconclusive";
            v.confidenceScore = 0.1;
        }

        return v;
    }

    /**
     * Detects new regressions — problems that appear post-update
     * but did not exist in the pre-update baseline.
     * Uses time-bounded queries and pre-update existence checks.
     */
    private List<UpdateRegression> detectNewRegressions(UpdateSession session, long postUpdateStart) {
        List<UpdateRegression> regressions = new ArrayList<>();
        long now = System.currentTimeMillis();

        try {
            PreUpdateSnapshot baseline = snapshotDao.getBySessionId(session.id);

            // Format postUpdateStart as date string for DailyAggregate queries
            String postStartDate = new SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    .format(new Date(postUpdateStart));

            // New crash types — C8 FIX: check pre-update existence, not total count
            List<com.example.hexnil.model.CrashEvent> newCrashes =
                    crashEventDao.getNewCrashTypes(postUpdateStart);
            for (com.example.hexnil.model.CrashEvent crash : newCrashes) {
                if (crash.stackTraceHash != null &&
                        crashEventDao.getCountByHashBefore(crash.stackTraceHash, postUpdateStart) == 0) {
                    UpdateRegression reg = UpdateRegression.create(
                            session.id, UUID.randomUUID().toString(),
                            "new_crash",
                            crash.packageName != null ? crash.packageName : "system",
                            "high",
                            "New crash: " + (crash.exceptionType != null ? crash.exceptionType : "unknown"),
                            "New crash type appeared after system update.");
                    reg.setOccurrences(crash.timestamp, 1);
                    reg.calculateImpact();
                    regressions.add(reg);
                }
            }

            // New ANR types — C8 FIX: check pre-update existence, not total count
            List<com.example.hexnil.model.AnrEvent> newAnrs =
                    anrEventDao.getNewAnrTypes(postUpdateStart);
            for (com.example.hexnil.model.AnrEvent anr : newAnrs) {
                if (anr.traceHash != null &&
                        anrEventDao.getCountByHashBefore(anr.traceHash, postUpdateStart) == 0) {
                    UpdateRegression reg = UpdateRegression.create(
                            session.id, UUID.randomUUID().toString(),
                            "new_anr",
                            anr.packageName != null ? anr.packageName : "system",
                            "high",
                            "New ANR: " + (anr.anrType != null ? anr.anrType : "unknown"),
                            "New ANR type appeared after system update.");
                    reg.setOccurrences(anr.timestamp, 1);
                    reg.calculateImpact();
                    regressions.add(reg);
                }
            }

            // Battery drain regression — C1 FIX: use time-bounded query
            if (baseline != null && baseline.avgDrainRatePerHour != null) {
                List<DailyAggregate> postAggs = dailyAggregateDao.getDeviceLevelSince(postStartDate);
                double totalDrain = 0;
                int drainCount = 0;
                for (DailyAggregate agg : postAggs) {
                    if (agg.avgDrainRatePerHour > 0) {
                        totalDrain += agg.avgDrainRatePerHour;
                        drainCount++;
                    }
                }
                if (drainCount >= 3) {
                    double postDrain = totalDrain / drainCount;
                    double preDrain = baseline.avgDrainRatePerHour;
                    if (preDrain > 0) {
                        double changePct = ((postDrain - preDrain) / preDrain) * 100;
                        if (changePct > REGRESSION_THRESHOLD_PERCENT) {
                            UpdateRegression reg = UpdateRegression.create(
                                    session.id, UUID.randomUUID().toString(),
                                    "battery_regression", "battery",
                                    changePct > 50 ? "critical" : "high",
                                    "Drain increased " + String.format("%.0f%%", changePct),
                                    "Avg drain " + String.format("%.2f", preDrain)
                                            + " → " + String.format("%.2f", postDrain) + " %/hr");
                            reg.setMetrics("avgDrainRatePerHour", preDrain, postDrain, "%/hr");
                            reg.calculateImpact();
                            regressions.add(reg);
                        }
                    }
                }
            }

            // Thermal regression — C12 FIX: use COUNT query instead of loading full objects
            if (baseline != null && baseline.thermalEventsPerDay != null) {
                long daysObserved = Math.max(1, (now - postUpdateStart) / (24L * 60 * 60 * 1000));
                int postThermalCount = thermalEventDao.getEventCount(postUpdateStart);
                double postPerDay = (double) postThermalCount / daysObserved;
                double prePerDay = baseline.thermalEventsPerDay;

                if (prePerDay > 0 && daysObserved >= 2) {
                    double changePct = ((postPerDay - prePerDay) / prePerDay) * 100;
                    if (changePct > REGRESSION_THRESHOLD_PERCENT) {
                        UpdateRegression reg = UpdateRegression.create(
                                session.id, UUID.randomUUID().toString(),
                                "thermal_regression", "thermal_manager", "high",
                                "Thermal events +" + String.format("%.0f%%", changePct),
                                String.format("%.1f → %.1f events/day", prePerDay, postPerDay));
                        reg.setMetrics("thermalEventsPerDay", prePerDay, postPerDay, "events/day");
                        reg.calculateImpact();
                        regressions.add(reg);
                    }
                } else if (prePerDay == 0 && postThermalCount > 3) {
                    UpdateRegression reg = UpdateRegression.create(
                            session.id, UUID.randomUUID().toString(),
                            "thermal_regression", "thermal_manager", "medium",
                            "New thermal events after update",
                            postThermalCount + " thermal events where none existed before.");
                    reg.setMetrics("thermalEventsPerDay", 0.0, postPerDay, "events/day");
                    reg.calculateImpact();
                    regressions.add(reg);
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error detecting regressions", e);
        }

        Log.i(TAG, "Detected " + regressions.size() + " regressions");
        return regressions;
    }

    // ========================================================================
    // VERDICT DETERMINATION
    // ========================================================================

    /**
     * Determines the overall verdict. Impact score is clamped to [-100, +100].
     */
    private void determineVerdict(UpdateSession session) {
        int fixed = session.issuesFixed;
        int unchanged = session.issuesUnchanged;
        int regressions = session.newRegressionsDetected;
        int total = fixed + unchanged + regressions;

        if (total == 0) {
            session.overallVerdict = "unchanged";
            session.updateImpactScore = 0.0;
            return;
        }

        // Weighted impact scoring
        double rawScore = (fixed * 2.0 + regressions * -3.0);
        double normalizer = Math.max(total, 1);
        double impactScore = (rawScore / normalizer) * 50.0;
        session.updateImpactScore = Math.max(-100.0, Math.min(100.0, impactScore));

        if (regressions == 0 && fixed > 0) {
            session.overallVerdict = "improved";
        } else if (regressions > 0 && fixed == 0) {
            session.overallVerdict = "regressed";
        } else if (regressions > fixed) {
            session.overallVerdict = "regressed";
        } else if (fixed > regressions) {
            session.overallVerdict = "mixed";
        } else {
            session.overallVerdict = "mixed";
        }
    }

    // ========================================================================
    // CLEANUP & RECOVERY
    // ========================================================================

    private void cleanupStaleInstalledSessions() {
        try {
            UpdateSession stale = sessionDao.getMonitoringSession();
            if (stale != null) {
                stale.status = "verified";
                stale.overallVerdict = "inconclusive";
                stale.updateImpactScore = 0.0;
                stale.verificationCompletedAt = System.currentTimeMillis();
                stale.notes = "Auto-closed: superseded by subsequent update";
                sessionDao.update(stale);
                Log.i(TAG, "Closed stale session: " + stale.sessionUuid);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error cleaning stale sessions", e);
        }
    }

    private void checkPendingVerification() {
        try {
            UpdateSession monitoring = sessionDao.getMonitoringSession();
            if (monitoring != null && monitoring.updateInstalledAt != null) {
                long hoursSince = (System.currentTimeMillis() - monitoring.updateInstalledAt)
                        / (3600L * 1000);
                if (hoursSince >= MIN_POST_UPDATE_OBSERVATION_HOURS) {
                    Log.i(TAG, "Pending session ready for verification");
                    schedulePostUpdateVerification(monitoring);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking pending verification", e);
        }
    }

    public void cleanupOldPendingSessions() {
        executor.execute(() -> {
            try {
                long cutoff = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000);
                sessionDao.deleteOldPendingSessions(cutoff);
            } catch (Exception e) {
                Log.e(TAG, "Error cleaning old sessions", e);
            }
        });
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    private void saveCurrentSystemState() {
        prefs.edit()
                .putString(KEY_LAST_BUILD_FINGERPRINT, Build.FINGERPRINT)
                .putString(KEY_LAST_ANDROID_VERSION, getCurrentAndroidVersion())
                .putString(KEY_LAST_SECURITY_PATCH, getCurrentSecurityPatch())
                .putString(KEY_LAST_MODEM_VERSION, getModemVersion())
                .apply();
    }

    private String getCurrentAndroidVersion() {
        return Build.VERSION.RELEASE;
    }

    private String getCurrentSecurityPatch() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Build.VERSION.SECURITY_PATCH;
        }
        return "unknown";
    }

    private String getModemVersion() {
        try {
            return Build.getRadioVersion();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String getStoredAndroidVersion() {
        return prefs.getString(KEY_LAST_ANDROID_VERSION, "unknown");
    }

    private String getStoredSecurityPatch() {
        return prefs.getString(KEY_LAST_SECURITY_PATCH, "unknown");
    }

    private String getStoredModemVersion() {
        return prefs.getString(KEY_LAST_MODEM_VERSION, "unknown");
    }

    private static String mapAnomalyTypeToIssueType(String anomalyType) {
        if (anomalyType == null) return "unknown";
        switch (anomalyType) {
            case "app_drain": return "battery_drain";
            case "thermal": return "thermal";
            case "battery_health": return "battery_drain";
            case "charging": return "battery_drain";
            case "system": return "regression";
            case "usage_pattern": return "performance";
            default: return anomalyType;
        }
    }

    private static double calculateIssueImpact(AnomalyRecord anomaly) {
        double base = 50.0;
        if ("critical".equals(anomaly.severity)) base = 90;
        else if ("high".equals(anomaly.severity)) base = 75;
        else if ("medium".equals(anomaly.severity)) base = 50;
        else if ("low".equals(anomaly.severity)) base = 25;

        base = base * (anomaly.confidenceScore / 100.0);
        if (anomaly.occurrenceCount > 5) base = Math.min(100, base + 10);

        return Math.max(0, Math.min(100, base));
    }

    public static String determineUpdateType(String previousFingerprint, String currentFingerprint,
            String previousAndroid, String currentAndroid) {
        if (previousAndroid != null && !previousAndroid.equals(currentAndroid)) {
            return "major_os_update";
        }
        return "security_patch";
    }

    // ========================================================================
    // DIAGNOSTIC — for verifying system readiness via logcat
    // ========================================================================

    /**
     * Logs the full state of the update impact observer for diagnostic purposes.
     * Call from adb shell:
     *   adb shell am broadcast -a com.example.hexnil.DIAGNOSE_UPDATE_OBSERVER
     * Or programmatically to verify the system is ready before an OTA.
     */
    public void logDiagnostics() {
        executor.execute(() -> {
            try {
                Log.i(TAG, "═══════════════════════════════════════════════════════");
                Log.i(TAG, "  UPDATE IMPACT OBSERVER — DIAGNOSTIC REPORT");
                Log.i(TAG, "═══════════════════════════════════════════════════════");

                // Current fingerprint
                String storedFp = prefs.getString(KEY_LAST_BUILD_FINGERPRINT, "NOT SET");
                String currentFp = Build.FINGERPRINT;
                Log.i(TAG, "Current fingerprint : " + currentFp);
                Log.i(TAG, "Stored fingerprint  : " + storedFp);
                Log.i(TAG, "Match               : " + currentFp.equals(storedFp));

                // Active session
                UpdateSession active = sessionDao.getActiveSession();
                if (active != null) {
                    Log.i(TAG, "Active session      : " + active.sessionUuid);
                    Log.i(TAG, "  Status            : " + active.status);
                    Log.i(TAG, "  Previous FP       : " + active.previousBuildFingerprint);
                    Log.i(TAG, "  Created at        : " + new java.util.Date(active.detectedAt));
                } else {
                    Log.i(TAG, "Active session      : NONE");
                }

                // Baseline-locked session
                String pendingUuid = prefs.getString(KEY_PENDING_SESSION_UUID, null);
                if (pendingUuid != null) {
                    UpdateSession pending = sessionDao.getByUuid(pendingUuid);
                    if (pending != null) {
                        Log.i(TAG, "Baseline session    : " + pending.sessionUuid);
                        Log.i(TAG, "  Status            : " + pending.status);
                        Log.i(TAG, "  Baseline quality  : " + String.format("%.2f", pending.preUpdateDataQuality));
                        Log.i(TAG, "  Known issues      : " + pending.knownIssuesCount);
                        Log.i(TAG, "  Locked at         : " + new java.util.Date(pending.preSnapshotLockedAt));

                        // Pre-update snapshot
                        PreUpdateSnapshot snap = snapshotDao.getBySessionId(pending.id);
                        if (snap != null) {
                            Log.i(TAG, "  PRE-UPDATE SNAPSHOT:");
                            Log.i(TAG, "    Avg drain       : " + snap.avgDrainRatePerHour);
                            Log.i(TAG, "    Avg temp        : " + snap.avgOperatingTemp);
                            Log.i(TAG, "    Crashes/day     : " + snap.crashesPerDay);
                            Log.i(TAG, "    ANRs/day        : " + snap.anrsPerDay);
                            Log.i(TAG, "    Avg launch ms   : " + snap.avgColdLaunchMs);
                        } else {
                            Log.w(TAG, "  PRE-UPDATE SNAPSHOT: MISSING");
                        }

                        // Pre-update issues
                        List<PreUpdateIssue> issues = issueDao.getBySessionId(pending.id);
                        Log.i(TAG, "  PRE-UPDATE ISSUES : " + issues.size());
                        for (PreUpdateIssue issue : issues) {
                            Log.i(TAG, "    [" + issue.issueType + "] " + issue.issueTitle
                                    + " (severity=" + issue.severity + ")");
                        }
                    }
                } else {
                    Log.w(TAG, "Pending baseline    : NONE — baseline not yet locked!");
                }

                // Last baseline lock time
                long lastLock = prefs.getLong("last_baseline_lock_time", 0);
                if (lastLock > 0) {
                    long hoursAgo = (System.currentTimeMillis() - lastLock) / (3600L * 1000);
                    Log.i(TAG, "Last baseline lock  : " + hoursAgo + "h ago (" + new java.util.Date(lastLock) + ")");
                }

                // Data coverage
                int snapshotCount = getSnapshotCount();
                double quality = calculateDataQuality();
                Log.i(TAG, "Data quality        : " + String.format("%.2f", quality));
                Log.i(TAG, "Battery snapshots   : " + snapshotCount + " (last 7 days)");

                // Completed sessions
                List<UpdateSession> completed = sessionDao.getVerifiedSessions(5);
                Log.i(TAG, "Verified sessions   : " + (completed != null ? completed.size() : 0));
                if (completed != null) {
                    for (UpdateSession s : completed) {
                        Log.i(TAG, "  [" + s.overallVerdict + "] impact=" + s.updateImpactScore
                                + " fixed=" + s.issuesFixed + " regressions=" + s.newRegressionsDetected);
                    }
                }

                Log.i(TAG, "═══════════════════════════════════════════════════════");
                Log.i(TAG, "READY FOR UPDATE: " + (pendingUuid != null ? "YES ✓" : "NO ✗ (run app once to lock baseline)"));
                Log.i(TAG, "═══════════════════════════════════════════════════════");

            } catch (Exception e) {
                Log.e(TAG, "Error running diagnostics", e);
            }
        });
    }
}
