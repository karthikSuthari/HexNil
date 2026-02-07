package com.example.hexnil.manager;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.model.DeviceProfile;
import com.example.hexnil.model.HumanLabel;
import com.example.hexnil.model.MLPrediction;
import com.example.hexnil.model.NegativeExample;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Backend communication service for the HexNil battery diagnostic app.
 *
 * Handles syncing training data, labels, and ML model updates with a
 * configurable backend server. Supports incremental sync so only new
 * records since the last successful sync are transmitted.
 *
 * The backend URL defaults to a placeholder. Call {@link #setBackendUrl(String)}
 * to point at a real deployment before attempting any sync operations.
 */
public class BackendSyncService {

    private static final String TAG = "BackendSyncService";

    // =========================================================================
    // CONSTANTS
    // =========================================================================

    private static final String PREFS_NAME = "backend_sync_prefs";
    private static final String KEY_LAST_SYNC = "last_sync_timestamp";
    private static final String KEY_LAST_ANOMALY_ID = "last_synced_anomaly_id";
    private static final String KEY_LAST_LABEL_ID = "last_synced_label_id";
    private static final String KEY_LAST_NEG_EXAMPLE_ID = "last_synced_neg_example_id";
    private static final String KEY_LAST_PREDICTION_ID = "last_synced_prediction_id";
    private static final String KEY_BACKEND_URL = "backend_url";
    private static final String DEFAULT_BACKEND_URL = "https://hexnil-backend.internal.example.com/api/v1";
    private static final int SYNC_TIMEOUT_MS = 30000;
    private static final int MAX_BATCH_SIZE = 500;

    // =========================================================================
    // FIELDS
    // =========================================================================

    private final Context context;
    private final HexNilDatabase database;

    // =========================================================================
    // CONSTRUCTOR
    // =========================================================================

    public BackendSyncService(Context context, HexNilDatabase database) {
        this.context = context.getApplicationContext();
        this.database = database;
    }

    // =========================================================================
    // BACKEND URL MANAGEMENT
    // =========================================================================

    /**
     * Store a custom backend URL in SharedPreferences.
     *
     * @param url the fully-qualified base URL of the backend API
     */
    public void setBackendUrl(String url) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_BACKEND_URL, url).apply();
        Log.i(TAG, "Backend URL updated: " + url);
    }

    /**
     * Read the backend URL from SharedPreferences, falling back to the default
     * placeholder if no custom URL has been configured.
     *
     * @return the backend base URL
     */
    public String getBackendUrl() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_BACKEND_URL, DEFAULT_BACKEND_URL);
    }

    /**
     * Returns {@code true} if a custom backend URL has been set
     * (i.e. the URL is not the default placeholder).
     */
    public boolean isBackendConfigured() {
        String url = getBackendUrl();
        return url != null && !url.equals(DEFAULT_BACKEND_URL) && !url.isEmpty();
    }

    // =========================================================================
    // MASTER SYNC
    // =========================================================================

    /**
     * Master sync method. Runs all incremental sync operations in sequence and
     * updates the last sync timestamp on completion.
     *
     * @return a {@link SyncResult} summarising what was synced and any errors
     */
    public SyncResult syncAll() {
        Log.i(TAG, "Starting full sync to backend: " + getBackendUrl());

        SyncResult result = new SyncResult();
        result.timestamp = System.currentTimeMillis();
        List<String> errors = new ArrayList<>();

        try {
            result.labelsSynced = syncLabels();
        } catch (Exception e) {
            Log.e(TAG, "Error syncing labels", e);
            errors.add("Labels sync failed: " + e.getMessage());
        }

        try {
            result.negativeExamplesSynced = syncNegativeExamples();
        } catch (Exception e) {
            Log.e(TAG, "Error syncing negative examples", e);
            errors.add("Negative examples sync failed: " + e.getMessage());
        }

        try {
            result.predictionsSynced = syncPredictions();
        } catch (Exception e) {
            Log.e(TAG, "Error syncing predictions", e);
            errors.add("Predictions sync failed: " + e.getMessage());
        }

        try {
            result.profilesSynced = syncDeviceProfiles();
        } catch (Exception e) {
            Log.e(TAG, "Error syncing device profiles", e);
            errors.add("Device profiles sync failed: " + e.getMessage());
        }

        result.errors = errors;
        result.success = errors.isEmpty();

        // Update last sync timestamp
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putLong(KEY_LAST_SYNC, result.timestamp).apply();

        Log.i(TAG, "Sync completed. Success=" + result.success
                + " labels=" + result.labelsSynced
                + " negExamples=" + result.negativeExamplesSynced
                + " predictions=" + result.predictionsSynced
                + " profiles=" + result.profilesSynced
                + " errors=" + errors.size());

        return result;
    }

    // =========================================================================
    // INCREMENTAL SYNC: LABELS
    // =========================================================================

    /**
     * Incrementally sync human labels that have been created since the last
     * successful sync. Records are batched into payloads of up to
     * {@link #MAX_BATCH_SIZE} and POSTed to the backend.
     *
     * @return the number of label records successfully synced
     */
    public int syncLabels() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long lastId = prefs.getLong(KEY_LAST_LABEL_ID, 0);

        List<HumanLabel> newRecords = database.humanLabelDao().getNewRecords(lastId);
        if (newRecords == null || newRecords.isEmpty()) {
            Log.d(TAG, "No new labels to sync");
            return 0;
        }

        Log.d(TAG, "Syncing " + newRecords.size() + " new labels since id " + lastId);
        int totalSynced = 0;

        List<List<HumanLabel>> batches = partitionList(newRecords, MAX_BATCH_SIZE);
        for (List<HumanLabel> batch : batches) {
            JSONArray payload = buildJsonBatch(batch, "human_label");
            SyncResponse response = sendToBackend("/labels", payload);
            if (response.success) {
                long maxId = 0;
                for (HumanLabel label : batch) {
                    if (label.id > maxId) {
                        maxId = label.id;
                    }
                }
                prefs.edit().putLong(KEY_LAST_LABEL_ID, maxId).apply();
                totalSynced += batch.size();
                Log.d(TAG, "Label batch synced: " + batch.size() + " records, maxId=" + maxId);
            } else {
                Log.w(TAG, "Label batch sync failed: " + response.errorMessage);
                break;
            }
        }

        return totalSynced;
    }

    // =========================================================================
    // INCREMENTAL SYNC: NEGATIVE EXAMPLES
    // =========================================================================

    /**
     * Incrementally sync negative examples created since the last sync.
     *
     * @return the number of negative example records successfully synced
     */
    public int syncNegativeExamples() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long lastId = prefs.getLong(KEY_LAST_NEG_EXAMPLE_ID, 0);

        List<NegativeExample> newRecords = database.negativeExampleDao().getNewRecords(lastId);
        if (newRecords == null || newRecords.isEmpty()) {
            Log.d(TAG, "No new negative examples to sync");
            return 0;
        }

        Log.d(TAG, "Syncing " + newRecords.size() + " new negative examples since id " + lastId);
        int totalSynced = 0;

        List<List<NegativeExample>> batches = partitionList(newRecords, MAX_BATCH_SIZE);
        for (List<NegativeExample> batch : batches) {
            JSONArray payload = buildJsonBatch(batch, "negative_example");
            SyncResponse response = sendToBackend("/negative-examples", payload);
            if (response.success) {
                long maxId = 0;
                for (NegativeExample example : batch) {
                    if (example.id > maxId) {
                        maxId = example.id;
                    }
                }
                prefs.edit().putLong(KEY_LAST_NEG_EXAMPLE_ID, maxId).apply();
                totalSynced += batch.size();
                Log.d(TAG, "Negative example batch synced: " + batch.size() + " records, maxId=" + maxId);
            } else {
                Log.w(TAG, "Negative example batch sync failed: " + response.errorMessage);
                break;
            }
        }

        return totalSynced;
    }

    // =========================================================================
    // INCREMENTAL SYNC: PREDICTIONS
    // =========================================================================

    /**
     * Incrementally sync ML predictions created since the last sync.
     *
     * @return the number of prediction records successfully synced
     */
    public int syncPredictions() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long lastId = prefs.getLong(KEY_LAST_PREDICTION_ID, 0);

        List<MLPrediction> newRecords = database.mlPredictionDao().getNewRecords(lastId);
        if (newRecords == null || newRecords.isEmpty()) {
            Log.d(TAG, "No new predictions to sync");
            return 0;
        }

        Log.d(TAG, "Syncing " + newRecords.size() + " new predictions since id " + lastId);
        int totalSynced = 0;

        List<List<MLPrediction>> batches = partitionList(newRecords, MAX_BATCH_SIZE);
        for (List<MLPrediction> batch : batches) {
            JSONArray payload = buildJsonBatch(batch, "ml_prediction");
            SyncResponse response = sendToBackend("/predictions", payload);
            if (response.success) {
                long maxId = 0;
                for (MLPrediction prediction : batch) {
                    if (prediction.id > maxId) {
                        maxId = prediction.id;
                    }
                }
                prefs.edit().putLong(KEY_LAST_PREDICTION_ID, maxId).apply();
                totalSynced += batch.size();
                Log.d(TAG, "Prediction batch synced: " + batch.size() + " records, maxId=" + maxId);
            } else {
                Log.w(TAG, "Prediction batch sync failed: " + response.errorMessage);
                break;
            }
        }

        return totalSynced;
    }

    // =========================================================================
    // FULL SYNC: DEVICE PROFILES
    // =========================================================================

    /**
     * Sync all device profiles to the backend. Because the device profiles table
     * is small, a full sync is performed rather than an incremental one.
     *
     * @return the number of device profile records successfully synced
     */
    public int syncDeviceProfiles() {
        List<DeviceProfile> profiles = database.deviceProfileDao().getAll();
        if (profiles == null || profiles.isEmpty()) {
            Log.d(TAG, "No device profiles to sync");
            return 0;
        }

        Log.d(TAG, "Syncing " + profiles.size() + " device profiles (full sync)");

        JSONArray payload = buildJsonBatch(profiles, "device_profile");
        SyncResponse response = sendToBackend("/device-profiles", payload);
        if (response.success) {
            Log.d(TAG, "Device profiles synced: " + profiles.size() + " records");
            return profiles.size();
        } else {
            Log.w(TAG, "Device profile sync failed: " + response.errorMessage);
            return 0;
        }
    }

    // =========================================================================
    // HTTP: SEND TO BACKEND (POST)
    // =========================================================================

    /**
     * POST a JSON array payload to the given backend endpoint.
     *
     * @param endpoint the relative path appended to the backend base URL
     * @param payload  the JSON array to send as the request body
     * @return a {@link SyncResponse} describing the outcome
     */
    public SyncResponse sendToBackend(String endpoint, JSONArray payload) {
        HttpURLConnection connection = null;
        try {
            String baseUrl = getBackendUrl();
            URL url = new URL(baseUrl + endpoint);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(SYNC_TIMEOUT_MS);
            connection.setReadTimeout(SYNC_TIMEOUT_MS);
            connection.setDoOutput(true);

            // Write request body
            String body = payload.toString();
            try (OutputStream os = connection.getOutputStream();
                 OutputStreamWriter writer = new OutputStreamWriter(os, "UTF-8")) {
                writer.write(body);
                writer.flush();
            }

            // Read response
            int statusCode = connection.getResponseCode();
            String responseBody = readResponseBody(connection, statusCode);

            SyncResponse response = new SyncResponse();
            response.statusCode = statusCode;
            response.responseBody = responseBody;
            response.success = statusCode >= 200 && statusCode < 300;
            if (!response.success) {
                response.errorMessage = "HTTP " + statusCode + ": " + responseBody;
            }

            Log.d(TAG, "POST " + endpoint + " -> " + statusCode
                    + " (" + body.length() + " bytes sent)");

            return response;

        } catch (Exception e) {
            Log.e(TAG, "Failed to POST " + endpoint, e);
            SyncResponse errorResponse = new SyncResponse();
            errorResponse.statusCode = -1;
            errorResponse.responseBody = "";
            errorResponse.success = false;
            errorResponse.errorMessage = e.getClass().getSimpleName() + ": " + e.getMessage();
            return errorResponse;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    // =========================================================================
    // HTTP: DOWNLOAD MODEL UPDATE
    // =========================================================================

    /**
     * Check for and optionally download an ML model update from the backend.
     * Issues a GET to the model-update endpoint, checks if a newer version is
     * available, and downloads the model file into the app's internal files
     * directory.
     *
     * @param modelName the name (identifier) of the model to check
     * @return a {@link ModelUpdateResult} describing the outcome
     */
    public ModelUpdateResult downloadModelUpdate(String modelName) {
        HttpURLConnection connection = null;
        ModelUpdateResult result = new ModelUpdateResult();
        result.modelName = modelName;

        try {
            // Step 1: Check if a newer version is available
            String baseUrl = getBackendUrl();
            URL checkUrl = new URL(baseUrl + "/models/" + modelName + "/latest");
            connection = (HttpURLConnection) checkUrl.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(SYNC_TIMEOUT_MS);
            connection.setReadTimeout(SYNC_TIMEOUT_MS);

            int statusCode = connection.getResponseCode();
            if (statusCode != 200) {
                result.updateAvailable = false;
                result.success = false;
                result.errorMessage = "Model check returned HTTP " + statusCode;
                return result;
            }

            String responseBody = readResponseBody(connection, statusCode);
            JSONObject metadata = new JSONObject(responseBody);
            connection.disconnect();
            connection = null;

            String newVersion = metadata.optString("version", "");
            String downloadUrl = metadata.optString("downloadUrl", "");
            long modelSize = metadata.optLong("sizeBytes", 0);

            if (newVersion.isEmpty() || downloadUrl.isEmpty()) {
                result.updateAvailable = false;
                result.success = true;
                result.newVersion = newVersion;
                Log.d(TAG, "No update available for model: " + modelName);
                return result;
            }

            result.updateAvailable = true;
            result.newVersion = newVersion;

            // Step 2: Download the model file
            URL modelUrl = new URL(downloadUrl);
            connection = (HttpURLConnection) modelUrl.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(SYNC_TIMEOUT_MS);
            connection.setReadTimeout(SYNC_TIMEOUT_MS);

            int downloadStatus = connection.getResponseCode();
            if (downloadStatus != 200) {
                result.success = false;
                result.errorMessage = "Model download returned HTTP " + downloadStatus;
                return result;
            }

            // Save to internal files directory
            File modelsDir = new File(context.getFilesDir(), "ml_models");
            if (!modelsDir.exists()) {
                modelsDir.mkdirs();
            }

            String fileName = modelName + "_" + newVersion + ".tflite";
            File modelFile = new File(modelsDir, fileName);

            try (InputStream is = connection.getInputStream();
                 FileOutputStream fos = new FileOutputStream(modelFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalBytesRead = 0;
                while ((bytesRead = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;
                }
                fos.flush();

                Log.i(TAG, "Model downloaded: " + modelName + " v" + newVersion
                        + " (" + totalBytesRead + " bytes) -> " + modelFile.getAbsolutePath());
            }

            result.downloadedFilePath = modelFile.getAbsolutePath();
            result.success = true;

            return result;

        } catch (Exception e) {
            Log.e(TAG, "Failed to download model update: " + modelName, e);
            result.success = false;
            result.errorMessage = e.getClass().getSimpleName() + ": " + e.getMessage();
            return result;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    // =========================================================================
    // SYNC STATUS & TIMESTAMPS
    // =========================================================================

    /**
     * Read the timestamp of the last successful sync from SharedPreferences.
     *
     * @return epoch millis of last sync, or 0 if never synced
     */
    public long getLastSyncTimestamp() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getLong(KEY_LAST_SYNC, 0);
    }

    /**
     * Build a comprehensive sync status summary including pending record counts
     * and backend reachability.
     *
     * @return a {@link SyncStatus} snapshot
     */
    public SyncStatus getSyncStatus() {
        SyncStatus status = new SyncStatus();
        status.lastSyncTimestamp = getLastSyncTimestamp();

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // Count pending labels
        long lastLabelId = prefs.getLong(KEY_LAST_LABEL_ID, 0);
        List<HumanLabel> pendingLabelList = database.humanLabelDao().getNewRecords(lastLabelId);
        status.pendingLabels = (pendingLabelList != null) ? pendingLabelList.size() : 0;

        // Count pending negative examples
        long lastNegId = prefs.getLong(KEY_LAST_NEG_EXAMPLE_ID, 0);
        List<NegativeExample> pendingNegList = database.negativeExampleDao().getNewRecords(lastNegId);
        status.pendingExamples = (pendingNegList != null) ? pendingNegList.size() : 0;

        // Count pending predictions
        long lastPredId = prefs.getLong(KEY_LAST_PREDICTION_ID, 0);
        List<MLPrediction> pendingPredList = database.mlPredictionDao().getNewRecords(lastPredId);
        status.pendingPredictions = (pendingPredList != null) ? pendingPredList.size() : 0;

        // Check backend reachability
        status.isBackendReachable = checkBackendReachable();

        return status;
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    /**
     * Convert a list of records to a JSONArray based on the record type.
     *
     * @param records the list of entity objects to serialise
     * @param type    one of "human_label", "negative_example", "ml_prediction",
     *                "device_profile"
     * @return a JSONArray containing one JSONObject per record
     */
    private JSONArray buildJsonBatch(List<?> records, String type) {
        JSONArray array = new JSONArray();

        for (Object record : records) {
            try {
                JSONObject obj = new JSONObject();

                switch (type) {
                    case "human_label": {
                        HumanLabel label = (HumanLabel) record;
                        obj.put("id", label.id);
                        obj.put("anomalyRecordId", label.anomalyRecordId);
                        obj.put("safetyEventId", label.safetyEventId);
                        obj.put("regressionEventId", label.regressionEventId);
                        obj.put("thermalEventId", label.thermalEventId);
                        obj.put("updateSessionId", label.updateSessionId);
                        obj.put("labelerId", label.labelerId);
                        obj.put("labelerRole", label.labelerRole);
                        obj.put("groundTruthVerdict", label.groundTruthVerdict);
                        obj.put("severityCorrected", label.severityCorrected);
                        obj.put("rootCauseCorrected", label.rootCauseCorrected);
                        obj.put("rmaTicketId", label.rmaTicketId);
                        obj.put("labTestId", label.labTestId);
                        obj.put("userReportId", label.userReportId);
                        obj.put("labeledAt", label.labeledAt);
                        obj.put("labelConfidence", label.labelConfidence);
                        obj.put("notes", label.notes);
                        break;
                    }

                    case "negative_example": {
                        NegativeExample example = (NegativeExample) record;
                        obj.put("id", example.id);
                        obj.put("conditionType", example.conditionType);
                        obj.put("deviceFingerprint", example.deviceFingerprint);
                        obj.put("timestamp", example.timestamp);
                        obj.put("durationMs", example.durationMs);
                        obj.put("metricsJson", example.metricsJson);
                        obj.put("expectedBehavior", example.expectedBehavior);
                        obj.put("normalCauseRootCause", example.normalCauseRootCause);
                        obj.put("detectedActivity", example.detectedActivity);
                        obj.put("foregroundApp", example.foregroundApp);
                        obj.put("foregroundAppName", example.foregroundAppName);
                        obj.put("batteryLevel", example.batteryLevel);
                        obj.put("temperature", example.temperature);
                        obj.put("cpuUsage", example.cpuUsage);
                        obj.put("drainRate", example.drainRate);
                        obj.put("labeledAsNormal", example.labeledAsNormal);
                        obj.put("labeledBy", example.labeledBy);
                        obj.put("usedInTraining", example.usedInTraining);
                        obj.put("trainingSetVersion", example.trainingSetVersion);
                        break;
                    }

                    case "ml_prediction": {
                        MLPrediction prediction = (MLPrediction) record;
                        obj.put("id", prediction.id);
                        obj.put("modelId", prediction.modelId);
                        obj.put("predictionTimestamp", prediction.predictionTimestamp);
                        obj.put("inputFeaturesJson", prediction.inputFeaturesJson);
                        obj.put("predictedClass", prediction.predictedClass);
                        obj.put("predictionConfidence", prediction.predictionConfidence);
                        obj.put("predictedSeverity", prediction.predictedSeverity);
                        obj.put("groundTruthLabel", prediction.groundTruthLabel);
                        obj.put("wasCorrect", prediction.wasCorrect);
                        obj.put("deviceFingerprint", prediction.deviceFingerprint);
                        obj.put("relatedRecordId", prediction.relatedRecordId);
                        obj.put("relatedRecordTable", prediction.relatedRecordTable);
                        obj.put("inferenceTimeMs", prediction.inferenceTimeMs);
                        obj.put("modelVersion", prediction.modelVersion);
                        break;
                    }

                    case "device_profile": {
                        DeviceProfile profile = (DeviceProfile) record;
                        obj.put("id", profile.id);
                        obj.put("deviceFingerprint", profile.deviceFingerprint);
                        obj.put("manufacturer", profile.manufacturer);
                        obj.put("model", profile.model);
                        obj.put("marketName", profile.marketName);
                        obj.put("soc", profile.soc);
                        obj.put("socBinning", profile.socBinning);
                        obj.put("ramGB", profile.ramGB);
                        obj.put("storageGB", profile.storageGB);
                        obj.put("batteryDesignCapacityMah", profile.batteryDesignCapacityMah);
                        obj.put("screenSizeInches", profile.screenSizeInches);
                        obj.put("screenResolution", profile.screenResolution);
                        obj.put("deviceCount", profile.deviceCount);
                        obj.put("firstSeen", profile.firstSeen);
                        obj.put("lastUpdated", profile.lastUpdated);
                        obj.put("idleDrainMean", profile.idleDrainMean);
                        obj.put("idleDrainStdDev", profile.idleDrainStdDev);
                        obj.put("activeDrainMean", profile.activeDrainMean);
                        obj.put("activeDrainStdDev", profile.activeDrainStdDev);
                        obj.put("thermalEnvelopeCpu", profile.thermalEnvelopeCpu);
                        obj.put("thermalEnvelopeGpu", profile.thermalEnvelopeGpu);
                        obj.put("thermalEnvelopeBattery", profile.thermalEnvelopeBattery);
                        obj.put("avgColdLaunchMs", profile.avgColdLaunchMs);
                        obj.put("avgJankPercent", profile.avgJankPercent);
                        obj.put("avgFps", profile.avgFps);
                        obj.put("crashesPerDayMean", profile.crashesPerDayMean);
                        obj.put("anrsPerDayMean", profile.anrsPerDayMean);
                        break;
                    }

                    default:
                        Log.w(TAG, "Unknown record type for JSON batch: " + type);
                        break;
                }

                array.put(obj);

            } catch (JSONException e) {
                Log.e(TAG, "Failed to serialise " + type + " record to JSON", e);
            }
        }

        return array;
    }

    /**
     * Quick health check on the backend. Issues a HEAD request to the base URL
     * and returns {@code true} if the server responds with a status below 500.
     */
    private boolean checkBackendReachable() {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(getBackendUrl());
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setInstanceFollowRedirects(true);

            int statusCode = connection.getResponseCode();
            boolean reachable = statusCode < 500;

            Log.d(TAG, "Backend reachability check: HTTP " + statusCode
                    + " -> " + (reachable ? "reachable" : "unreachable"));

            return reachable;

        } catch (Exception e) {
            Log.d(TAG, "Backend unreachable: " + e.getMessage());
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Read the response body from an {@link HttpURLConnection}. Uses the error
     * stream for non-2xx status codes.
     */
    private String readResponseBody(HttpURLConnection connection, int statusCode) {
        StringBuilder sb = new StringBuilder();
        InputStream stream = null;
        try {
            if (statusCode >= 200 && statusCode < 300) {
                stream = connection.getInputStream();
            } else {
                stream = connection.getErrorStream();
            }

            if (stream == null) {
                return "";
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error reading response body", e);
        }

        return sb.toString();
    }

    /**
     * Partition a list into sublists of at most {@code batchSize} elements.
     */
    private <T> List<List<T>> partitionList(List<T> list, int batchSize) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            int end = Math.min(list.size(), i + batchSize);
            partitions.add(new ArrayList<>(list.subList(i, end)));
        }
        return partitions;
    }

    // =========================================================================
    // INNER CLASSES
    // =========================================================================

    /**
     * Result of a full {@link #syncAll()} operation.
     */
    public static class SyncResult {
        public boolean success;
        public int labelsSynced;
        public int negativeExamplesSynced;
        public int predictionsSynced;
        public int profilesSynced;
        public List<String> errors;
        public long timestamp;

        public SyncResult() {
            this.success = false;
            this.labelsSynced = 0;
            this.negativeExamplesSynced = 0;
            this.predictionsSynced = 0;
            this.profilesSynced = 0;
            this.errors = new ArrayList<>();
            this.timestamp = 0;
        }

        /**
         * Total number of records synced across all categories.
         */
        public int getTotalSynced() {
            return labelsSynced + negativeExamplesSynced + predictionsSynced + profilesSynced;
        }

        @Override
        public String toString() {
            return "SyncResult{" +
                    "success=" + success +
                    ", labels=" + labelsSynced +
                    ", negExamples=" + negativeExamplesSynced +
                    ", predictions=" + predictionsSynced +
                    ", profiles=" + profilesSynced +
                    ", errors=" + errors.size() +
                    ", timestamp=" + timestamp +
                    '}';
        }
    }

    /**
     * Result of a single HTTP request to the backend.
     */
    public static class SyncResponse {
        public int statusCode;
        public String responseBody;
        public boolean success;
        public String errorMessage;

        public SyncResponse() {
            this.statusCode = -1;
            this.responseBody = "";
            this.success = false;
            this.errorMessage = null;
        }

        @Override
        public String toString() {
            return "SyncResponse{" +
                    "statusCode=" + statusCode +
                    ", success=" + success +
                    ", errorMessage='" + errorMessage + '\'' +
                    '}';
        }
    }

    /**
     * Result of a model-update check and optional download.
     */
    public static class ModelUpdateResult {
        public boolean updateAvailable;
        public String modelName;
        public String newVersion;
        public String downloadedFilePath;
        public boolean success;
        public String errorMessage;

        public ModelUpdateResult() {
            this.updateAvailable = false;
            this.modelName = "";
            this.newVersion = "";
            this.downloadedFilePath = null;
            this.success = false;
            this.errorMessage = null;
        }

        @Override
        public String toString() {
            return "ModelUpdateResult{" +
                    "model='" + modelName + '\'' +
                    ", updateAvailable=" + updateAvailable +
                    ", newVersion='" + newVersion + '\'' +
                    ", downloaded=" + (downloadedFilePath != null) +
                    ", success=" + success +
                    '}';
        }
    }

    /**
     * Snapshot of the current sync state including pending record counts.
     */
    public static class SyncStatus {
        public long lastSyncTimestamp;
        public int pendingLabels;
        public int pendingExamples;
        public int pendingPredictions;
        public boolean isBackendReachable;

        public SyncStatus() {
            this.lastSyncTimestamp = 0;
            this.pendingLabels = 0;
            this.pendingExamples = 0;
            this.pendingPredictions = 0;
            this.isBackendReachable = false;
        }

        /**
         * Total number of records waiting to be synced.
         */
        public int getTotalPending() {
            return pendingLabels + pendingExamples + pendingPredictions;
        }

        /**
         * Returns {@code true} if there are no records waiting to be synced.
         */
        public boolean isFullySynced() {
            return pendingLabels == 0 && pendingExamples == 0 && pendingPredictions == 0;
        }

        @Override
        public String toString() {
            return "SyncStatus{" +
                    "lastSync=" + lastSyncTimestamp +
                    ", pendingLabels=" + pendingLabels +
                    ", pendingExamples=" + pendingExamples +
                    ", pendingPredictions=" + pendingPredictions +
                    ", reachable=" + isBackendReachable +
                    '}';
        }
    }
}
