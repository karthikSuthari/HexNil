package com.example.hexnil.worker;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.manager.PerformanceAggregator;
import com.example.hexnil.manager.RegressionDetector;

/**
 * Performance Aggregation Worker
 * 
 * Background worker that performs daily performance aggregation and
 * regression detection. Guaranteed to run even if the app process is closed.
 */
public class PerformanceAggregationWorker extends Worker {

    private static final String TAG = "PerfAggWorker";

    public PerformanceAggregationWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.i(TAG, "Starting background performance aggregation task");

        try {
            Context context = getApplicationContext();
            HexNilDatabase database = HexNilDatabase.getInstance(context);

            // 1. Run Daily Aggregation
            PerformanceAggregator aggregator = new PerformanceAggregator(context, database);
            aggregator.runDailyAggregationSync();

            // 2. Run Regression Detection
            RegressionDetector detector = new RegressionDetector(context, database);
            detector.runDetectionSync();

            Log.i(TAG, "Background performance aggregation task completed successfully");
            return Result.success();

        } catch (Exception e) {
            Log.e(TAG, "Error in performance aggregation worker", e);
            return Result.retry();
        }
    }
}
