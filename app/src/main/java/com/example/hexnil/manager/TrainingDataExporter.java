package com.example.hexnil.manager;

import android.content.Context;
import android.util.Log;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.model.AnomalyRecord;
import com.example.hexnil.model.BatterySnapshot;
import com.example.hexnil.model.DeviceBaselineThreshold;
import com.example.hexnil.model.DeviceProfile;
import com.example.hexnil.model.HumanLabel;
import com.example.hexnil.model.NegativeExample;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Exports labeled training data from the HexNil battery diagnostic database
 * for ML model training. Produces JSON Lines (JSONL) format suitable for
 * ingestion by Python ML pipelines.
 *
 * Each export method writes one JSON object per line to the output file,
 * enabling streaming reads and parallel processing downstream.
 */
public class TrainingDataExporter {

    private static final String TAG = "TrainingDataExporter";

    private static final String FILE_ANOMALIES = "labeled_anomalies.jsonl";
    private static final String FILE_NEGATIVES = "negative_examples.jsonl";
    private static final String FILE_PROFILES = "device_profiles.jsonl";
    private static final String FILE_SNAPSHOTS = "battery_timeseries.jsonl";
    private static final String FILE_MANIFEST = "manifest.json";

    private final Context context;
    private final HexNilDatabase database;

    public TrainingDataExporter(Context context, HexNilDatabase database) {
        this.context = context;
        this.database = database;
    }

    // =========================================================================
    // PUBLIC EXPORT METHODS
    // =========================================================================

    /**
     * Export all anomalies that have human labels to JSONL format.
     * Each line contains the anomaly fields merged with the label fields,
     * producing a single flat record suitable for supervised learning.
     *
     * @param outputFile destination JSONL file
     * @return count of exported records
     */
    public int exportLabeledAnomalies(File outputFile) {
        int count = 0;
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(outputFile));
            List<AnomalyRecord> allAnomalies = database.anomalyRecordDao().getAllAnomalies();

            for (AnomalyRecord anomaly : allAnomalies) {
                List<HumanLabel> labels = database.humanLabelDao().getForAnomaly(anomaly.id);
                if (labels == null || labels.isEmpty()) {
                    continue;
                }
                // Use the most recent label for this anomaly
                HumanLabel label = labels.get(0);
                JSONObject json = anomalyToJson(anomaly, label);
                writeJsonLine(writer, json);
                count++;
            }

            Log.i(TAG, "Exported " + count + " labeled anomalies to " + outputFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Failed to export labeled anomalies", e);
        } finally {
            closeQuietly(writer);
        }
        return count;
    }

    /**
     * Export negative examples (known-normal high-stress scenarios) to JSONL.
     * These records teach the model what normal behaviour looks like under load.
     *
     * @param outputFile destination JSONL file
     * @return count of exported records
     */
    public int exportNegativeExamples(File outputFile) {
        int count = 0;
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(outputFile));
            List<NegativeExample> examples = database.negativeExampleDao().getAll();

            for (NegativeExample example : examples) {
                JSONObject json = negativeExampleToJson(example);
                writeJsonLine(writer, json);
                count++;
            }

            Log.i(TAG, "Exported " + count + " negative examples to " + outputFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Failed to export negative examples", e);
        } finally {
            closeQuietly(writer);
        }
        return count;
    }

    /**
     * Export device profiles and their associated baseline thresholds to JSONL.
     * Each record contains the full hardware profile plus a nested array of
     * baseline thresholds for that device SKU.
     *
     * @param outputFile destination JSONL file
     * @return count of exported records
     */
    public int exportDeviceProfiles(File outputFile) {
        int count = 0;
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(outputFile));
            List<DeviceProfile> profiles = database.deviceProfileDao().getAll();

            for (DeviceProfile profile : profiles) {
                JSONObject json = new JSONObject();
                json.put("id", profile.id);
                json.put("deviceFingerprint", profile.deviceFingerprint);
                json.put("manufacturer", profile.manufacturer);
                json.put("model", profile.model);
                json.put("marketName", profile.marketName);
                json.put("soc", profile.soc);
                json.put("socBinning", profile.socBinning);
                json.put("ramGB", profile.ramGB != null ? profile.ramGB : JSONObject.NULL);
                json.put("storageGB", profile.storageGB != null ? profile.storageGB : JSONObject.NULL);
                json.put("batteryDesignCapacityMah", profile.batteryDesignCapacityMah != null ? profile.batteryDesignCapacityMah : JSONObject.NULL);
                json.put("screenSizeInches", profile.screenSizeInches != null ? profile.screenSizeInches : JSONObject.NULL);
                json.put("screenResolution", profile.screenResolution);
                json.put("deviceCount", profile.deviceCount);
                json.put("firstSeen", profile.firstSeen);
                json.put("lastUpdated", profile.lastUpdated);
                json.put("idleDrainMean", profile.idleDrainMean != null ? profile.idleDrainMean : JSONObject.NULL);
                json.put("idleDrainStdDev", profile.idleDrainStdDev != null ? profile.idleDrainStdDev : JSONObject.NULL);
                json.put("activeDrainMean", profile.activeDrainMean != null ? profile.activeDrainMean : JSONObject.NULL);
                json.put("activeDrainStdDev", profile.activeDrainStdDev != null ? profile.activeDrainStdDev : JSONObject.NULL);
                json.put("thermalEnvelopeCpu", profile.thermalEnvelopeCpu != null ? profile.thermalEnvelopeCpu : JSONObject.NULL);
                json.put("thermalEnvelopeGpu", profile.thermalEnvelopeGpu != null ? profile.thermalEnvelopeGpu : JSONObject.NULL);
                json.put("thermalEnvelopeBattery", profile.thermalEnvelopeBattery != null ? profile.thermalEnvelopeBattery : JSONObject.NULL);
                json.put("avgColdLaunchMs", profile.avgColdLaunchMs != null ? profile.avgColdLaunchMs : JSONObject.NULL);
                json.put("avgJankPercent", profile.avgJankPercent != null ? profile.avgJankPercent : JSONObject.NULL);
                json.put("avgFps", profile.avgFps != null ? profile.avgFps : JSONObject.NULL);
                json.put("crashesPerDayMean", profile.crashesPerDayMean != null ? profile.crashesPerDayMean : JSONObject.NULL);
                json.put("anrsPerDayMean", profile.anrsPerDayMean != null ? profile.anrsPerDayMean : JSONObject.NULL);

                // Fetch and nest baseline thresholds for this device
                List<DeviceBaselineThreshold> baselines =
                        database.deviceBaselineThresholdDao().getAllForDevice(profile.deviceFingerprint);
                JSONArray baselinesArray = new JSONArray();
                if (baselines != null) {
                    for (DeviceBaselineThreshold baseline : baselines) {
                        JSONObject bObj = new JSONObject();
                        bObj.put("metricName", baseline.metricName);
                        bObj.put("p50", baseline.p50 != null ? baseline.p50 : JSONObject.NULL);
                        bObj.put("p95", baseline.p95 != null ? baseline.p95 : JSONObject.NULL);
                        bObj.put("p99", baseline.p99 != null ? baseline.p99 : JSONObject.NULL);
                        bObj.put("mean", baseline.mean != null ? baseline.mean : JSONObject.NULL);
                        bObj.put("stdDev", baseline.stdDev != null ? baseline.stdDev : JSONObject.NULL);
                        bObj.put("sampleSize", baseline.sampleSize);
                        bObj.put("confidenceLevel", baseline.confidenceLevel);
                        bObj.put("lastUpdated", baseline.lastUpdated);
                        baselinesArray.put(bObj);
                    }
                }
                json.put("baselines", baselinesArray);

                writeJsonLine(writer, json);
                count++;
            }

            Log.i(TAG, "Exported " + count + " device profiles to " + outputFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Failed to export device profiles", e);
        } finally {
            closeQuietly(writer);
        }
        return count;
    }

    /**
     * Export battery snapshots as time series data to JSONL.
     * Includes key telemetry fields for temporal pattern analysis.
     *
     * @param outputFile destination JSONL file
     * @param since      epoch millis; only snapshots after this timestamp are included
     * @return count of exported records
     */
    public int exportBatteryTimeSeries(File outputFile, long since) {
        int count = 0;
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(outputFile));
            List<BatterySnapshot> snapshots = database.batterySnapshotDao().getSnapshotsSince(since);

            for (BatterySnapshot snap : snapshots) {
                JSONObject json = new JSONObject();
                json.put("id", snap.id);
                json.put("timestamp", snap.timestamp);
                json.put("level", snap.level);
                json.put("status", snap.status);
                json.put("health", snap.health);
                json.put("plugged", snap.plugged);
                json.put("voltage", snap.voltage);
                json.put("temperature", snap.temperature);
                json.put("temperatureCelsius", snap.getTemperatureCelsius());
                json.put("currentNow", snap.currentNow != null ? snap.currentNow : JSONObject.NULL);
                json.put("currentAverage", snap.currentAverage != null ? snap.currentAverage : JSONObject.NULL);
                json.put("capacityMah", snap.capacityMah);
                json.put("designCapacityMah", snap.designCapacityMah);
                json.put("healthPercentage", snap.healthPercentage);
                json.put("chargeRate", snap.chargeRate);
                json.put("dischargeRate", snap.dischargeRate);
                json.put("isCharging", snap.isCharging);
                json.put("isFastCharging", snap.isFastCharging);
                json.put("isDozeMode", snap.isDozeMode);
                json.put("cpuUsage", snap.cpuUsage);
                json.put("memoryUsage", snap.memoryUsage);
                json.put("deviceState", snap.deviceState);
                json.put("stateConfidence", snap.stateConfidence != null ? snap.stateConfidence : JSONObject.NULL);
                json.put("screenOn", snap.screenOn != null ? snap.screenOn : JSONObject.NULL);
                json.put("samplingContext", snap.samplingContext);
                json.put("validationStatus", snap.validationStatus);
                json.put("qualityScore", snap.qualityScore);

                writeJsonLine(writer, json);
                count++;
            }

            Log.i(TAG, "Exported " + count + " battery snapshots to " + outputFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Failed to export battery time series", e);
        } finally {
            closeQuietly(writer);
        }
        return count;
    }

    /**
     * Master export method. Calls all individual export methods,
     * writes files into the given directory, and produces a manifest.json
     * containing metadata about the export.
     *
     * @param outputDir directory to write all export files into
     * @return ExportResult with counts and status
     */
    public ExportResult exportFullTrainingDataset(File outputDir) {
        ExportResult result = new ExportResult();
        result.exportTimestamp = System.currentTimeMillis();
        result.outputDirectory = outputDir.getAbsolutePath();
        List<String> errors = new ArrayList<>();

        try {
            // Create output directory if needed
            if (!outputDir.exists()) {
                boolean created = outputDir.mkdirs();
                if (!created) {
                    errors.add("Failed to create output directory: " + outputDir.getAbsolutePath());
                    result.success = false;
                    result.errors = errors;
                    return result;
                }
            }

            // Export labeled anomalies
            try {
                result.anomalyCount = exportLabeledAnomalies(new File(outputDir, FILE_ANOMALIES));
            } catch (Exception e) {
                errors.add("Anomaly export failed: " + e.getMessage());
                Log.e(TAG, "Anomaly export failed", e);
            }

            // Export negative examples
            try {
                result.negativeExampleCount = exportNegativeExamples(new File(outputDir, FILE_NEGATIVES));
            } catch (Exception e) {
                errors.add("Negative example export failed: " + e.getMessage());
                Log.e(TAG, "Negative example export failed", e);
            }

            // Export device profiles
            try {
                result.profileCount = exportDeviceProfiles(new File(outputDir, FILE_PROFILES));
            } catch (Exception e) {
                errors.add("Device profile export failed: " + e.getMessage());
                Log.e(TAG, "Device profile export failed", e);
            }

            // Export battery time series (all available data)
            try {
                result.snapshotCount = exportBatteryTimeSeries(new File(outputDir, FILE_SNAPSHOTS), 0L);
            } catch (Exception e) {
                errors.add("Battery time series export failed: " + e.getMessage());
                Log.e(TAG, "Battery time series export failed", e);
            }

            result.totalRecords = result.anomalyCount + result.negativeExampleCount
                    + result.profileCount + result.snapshotCount;

            // Write manifest.json
            try {
                writeManifest(outputDir, result);
            } catch (Exception e) {
                errors.add("Manifest write failed: " + e.getMessage());
                Log.e(TAG, "Manifest write failed", e);
            }

            result.success = errors.isEmpty();
            result.errors = errors;

            Log.i(TAG, "Full training dataset export complete: " + result.totalRecords
                    + " total records, success=" + result.success);

        } catch (Exception e) {
            errors.add("Unexpected error during full export: " + e.getMessage());
            result.success = false;
            result.errors = errors;
            Log.e(TAG, "Full training dataset export failed", e);
        }

        return result;
    }

    /**
     * Post-export validation. Checks each JSONL file in the export directory
     * for existence, non-emptiness, and JSON parsability.
     *
     * @param exportDir directory containing exported files
     * @return ValidationResult with validation outcome
     */
    public ValidationResult validateExportedData(File exportDir) {
        ValidationResult result = new ValidationResult();
        List<String> errors = new ArrayList<>();
        int fileCount = 0;
        int totalRecords = 0;

        String[] expectedFiles = { FILE_ANOMALIES, FILE_NEGATIVES, FILE_PROFILES, FILE_SNAPSHOTS };

        for (String fileName : expectedFiles) {
            File file = new File(exportDir, fileName);

            if (!file.exists()) {
                errors.add("Missing file: " + fileName);
                continue;
            }

            if (file.length() == 0) {
                errors.add("Empty file: " + fileName);
                continue;
            }

            fileCount++;

            // Count lines and validate first/last line JSON parsability
            BufferedReader reader = null;
            try {
                reader = new BufferedReader(new FileReader(file));
                String firstLine = null;
                String lastLine = null;
                String line;
                int lineCount = 0;

                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) {
                        continue;
                    }
                    if (firstLine == null) {
                        firstLine = line;
                    }
                    lastLine = line;
                    lineCount++;
                }

                totalRecords += lineCount;

                // Verify JSON parsability of first line
                if (firstLine != null) {
                    try {
                        new JSONObject(firstLine);
                    } catch (Exception e) {
                        errors.add(fileName + ": first line is not valid JSON: " + e.getMessage());
                    }
                }

                // Verify JSON parsability of last line
                if (lastLine != null && !lastLine.equals(firstLine)) {
                    try {
                        new JSONObject(lastLine);
                    } catch (Exception e) {
                        errors.add(fileName + ": last line is not valid JSON: " + e.getMessage());
                    }
                }

                Log.d(TAG, "Validated " + fileName + ": " + lineCount + " records");

            } catch (IOException e) {
                errors.add("Error reading " + fileName + ": " + e.getMessage());
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }

        result.isValid = errors.isEmpty();
        result.fileCount = fileCount;
        result.totalRecords = totalRecords;
        result.errors = errors;

        Log.i(TAG, "Validation complete: valid=" + result.isValid + ", files=" + result.fileCount
                + ", records=" + result.totalRecords + ", errors=" + errors.size());

        return result;
    }

    // =========================================================================
    // INNER CLASSES
    // =========================================================================

    /**
     * Result of a full training dataset export operation.
     */
    public static class ExportResult {
        public int totalRecords;
        public int anomalyCount;
        public int negativeExampleCount;
        public int profileCount;
        public int snapshotCount;
        public long exportTimestamp;
        public String outputDirectory;
        public boolean success;
        public List<String> errors;

        public ExportResult() {
            this.errors = new ArrayList<>();
        }

        @Override
        public String toString() {
            return "ExportResult{" +
                    "totalRecords=" + totalRecords +
                    ", anomalies=" + anomalyCount +
                    ", negatives=" + negativeExampleCount +
                    ", profiles=" + profileCount +
                    ", snapshots=" + snapshotCount +
                    ", success=" + success +
                    ", errors=" + errors.size() +
                    '}';
        }
    }

    /**
     * Result of a post-export validation check.
     */
    public static class ValidationResult {
        public boolean isValid;
        public int fileCount;
        public int totalRecords;
        public List<String> errors;

        public ValidationResult() {
            this.errors = new ArrayList<>();
        }

        @Override
        public String toString() {
            return "ValidationResult{" +
                    "isValid=" + isValid +
                    ", fileCount=" + fileCount +
                    ", totalRecords=" + totalRecords +
                    ", errors=" + errors.size() +
                    '}';
        }
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    /**
     * Write a JSONObject as a single line followed by a newline character.
     */
    private void writeJsonLine(BufferedWriter writer, JSONObject obj) throws IOException {
        writer.write(obj.toString());
        writer.newLine();
    }

    /**
     * Convert an AnomalyRecord and its associated HumanLabel into a single
     * flat JSONObject containing all fields needed for supervised ML training.
     */
    private JSONObject anomalyToJson(AnomalyRecord record, HumanLabel label) {
        JSONObject json = new JSONObject();
        try {
            // Anomaly fields
            json.put("anomaly_id", record.id);
            json.put("detectedAt", record.detectedAt);
            json.put("anomalyType", record.anomalyType);
            json.put("severity", record.severity);
            json.put("confidenceScore", record.confidenceScore);
            json.put("sourceApp", record.sourceApp);
            json.put("sourceAppName", record.sourceAppName);
            json.put("sourceComponent", record.sourceComponent);
            json.put("title", record.title);
            json.put("description", record.description);
            json.put("technicalDetails", record.technicalDetails);
            json.put("expectedValue", record.expectedValue);
            json.put("actualValue", record.actualValue);
            json.put("deviationPercent", record.deviationPercent);
            json.put("metricName", record.metricName);
            json.put("metricUnit", record.metricUnit);
            json.put("batteryLevel", record.batteryLevel);
            json.put("wasCharging", record.wasCharging);
            json.put("screenOn", record.screenOn);
            json.put("deviceState", record.deviceState);
            json.put("periodStart", record.periodStart);
            json.put("periodEnd", record.periodEnd);
            json.put("dataPointsAnalyzed", record.dataPointsAnalyzed);
            json.put("recommendation", record.recommendation);
            json.put("rootCause", record.rootCause);
            json.put("impact", record.impact);
            json.put("acknowledged", record.acknowledged);
            json.put("resolved", record.resolved);
            json.put("resolvedAt", record.resolvedAt);
            json.put("resolution", record.resolution);
            json.put("relatedAnomalyId", record.relatedAnomalyId);
            json.put("occurrenceCount", record.occurrenceCount);
            json.put("isRecurring", record.isRecurring);

            // Device context
            json.put("deviceFingerprint", record.deviceFingerprint);
            json.put("detectionMethod", record.detectionMethod);
            json.put("modelVersion", record.modelVersion);
            json.put("mlPredictionId", record.mlPredictionId != null ? record.mlPredictionId : JSONObject.NULL);

            // Label fields
            json.put("label_id", label.id);
            json.put("groundTruthVerdict", label.groundTruthVerdict);
            json.put("severityCorrected", label.severityCorrected);
            json.put("rootCauseCorrected", label.rootCauseCorrected);
            json.put("labelerRole", label.labelerRole);
            json.put("labelerId", label.labelerId);
            json.put("labelConfidence", label.labelConfidence);
            json.put("labeledAt", label.labeledAt);
            json.put("rmaTicketId", label.rmaTicketId);
            json.put("labTestId", label.labTestId);
            json.put("userReportId", label.userReportId);
            json.put("labelNotes", label.notes);
        } catch (Exception e) {
            Log.e(TAG, "Error converting anomaly " + record.id + " to JSON", e);
        }
        return json;
    }

    /**
     * Convert a NegativeExample into a JSONObject for export.
     */
    private JSONObject negativeExampleToJson(NegativeExample example) {
        JSONObject json = new JSONObject();
        try {
            json.put("id", example.id);
            json.put("conditionType", example.conditionType);
            json.put("deviceFingerprint", example.deviceFingerprint);
            json.put("timestamp", example.timestamp);
            json.put("durationMs", example.durationMs);
            json.put("metricsJson", example.metricsJson);
            json.put("expectedBehavior", example.expectedBehavior);
            json.put("normalCauseRootCause", example.normalCauseRootCause);
            json.put("detectedActivity", example.detectedActivity);
            json.put("foregroundApp", example.foregroundApp);
            json.put("foregroundAppName", example.foregroundAppName);
            json.put("batteryLevel", example.batteryLevel);
            json.put("temperature", example.temperature);
            json.put("cpuUsage", example.cpuUsage);
            json.put("drainRate", example.drainRate);
            json.put("labeledAsNormal", example.labeledAsNormal);
            json.put("labeledBy", example.labeledBy);
            json.put("usedInTraining", example.usedInTraining);
            json.put("trainingSetVersion", example.trainingSetVersion);
        } catch (Exception e) {
            Log.e(TAG, "Error converting negative example " + example.id + " to JSON", e);
        }
        return json;
    }

    /**
     * Write the manifest.json file containing export metadata.
     */
    private void writeManifest(File outputDir, ExportResult result) throws Exception {
        JSONObject manifest = new JSONObject();
        manifest.put("exportTimestamp", result.exportTimestamp);
        manifest.put("exportTimestampIso", android.text.format.DateFormat.format(
                "yyyy-MM-dd'T'HH:mm:ss", result.exportTimestamp).toString());
        manifest.put("version", "1.0.0");
        manifest.put("format", "jsonl");

        JSONObject files = new JSONObject();
        files.put("labeled_anomalies", FILE_ANOMALIES);
        files.put("negative_examples", FILE_NEGATIVES);
        files.put("device_profiles", FILE_PROFILES);
        files.put("battery_timeseries", FILE_SNAPSHOTS);
        manifest.put("files", files);

        JSONObject counts = new JSONObject();
        counts.put("labeled_anomalies", result.anomalyCount);
        counts.put("negative_examples", result.negativeExampleCount);
        counts.put("device_profiles", result.profileCount);
        counts.put("battery_snapshots", result.snapshotCount);
        counts.put("total", result.totalRecords);
        manifest.put("recordCounts", counts);

        manifest.put("appPackage", context.getPackageName());

        try {
            String versionName = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName;
            manifest.put("appVersion", versionName);
        } catch (Exception e) {
            manifest.put("appVersion", "unknown");
        }

        manifest.put("androidVersion", android.os.Build.VERSION.RELEASE);
        manifest.put("sdkLevel", android.os.Build.VERSION.SDK_INT);
        manifest.put("device", android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL);

        File manifestFile = new File(outputDir, FILE_MANIFEST);
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(manifestFile));
            writer.write(manifest.toString(2));
        } finally {
            closeQuietly(writer);
        }

        Log.i(TAG, "Manifest written to " + manifestFile.getAbsolutePath());
    }

    /**
     * Silently close a writer, swallowing any IOException.
     */
    private void closeQuietly(BufferedWriter writer) {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException ignored) {
            }
        }
    }
}
