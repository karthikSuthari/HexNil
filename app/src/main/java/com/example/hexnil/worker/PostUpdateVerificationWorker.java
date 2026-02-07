package com.example.hexnil.worker;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.hexnil.service.UpdateImpactObserver;

/**
 * WorkManager worker for post-update verification.
 *
 * Scheduled by UpdateImpactObserver after a system update is detected.
 * Runs after a configurable observation period (default 48 hours) to allow
 * sufficient post-update data collection before comparing against the baseline.
 *
 * Android 12+ compliant: uses WorkManager (not deprecated AlarmManager patterns).
 */
public class PostUpdateVerificationWorker extends Worker {

    private static final String TAG = "PostUpdateVerifWorker";

    public PostUpdateVerificationWorker(@NonNull Context context,
            @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        String sessionUuid = getInputData().getString("session_uuid");
        if (sessionUuid == null || sessionUuid.isEmpty()) {
            Log.e(TAG, "No session_uuid provided, cannot verify");
            return Result.failure();
        }

        Log.i(TAG, "Starting post-update verification for session: " + sessionUuid);

        try {
            UpdateImpactObserver observer = UpdateImpactObserver.getInstance(getApplicationContext());
            observer.runPostUpdateVerification(sessionUuid);

            Log.i(TAG, "Post-update verification dispatched for: " + sessionUuid);
            return Result.success();

        } catch (Exception e) {
            Log.e(TAG, "Error in post-update verification worker", e);
            // Retry on failure — WorkManager will back off automatically
            return Result.retry();
        }
    }
}
