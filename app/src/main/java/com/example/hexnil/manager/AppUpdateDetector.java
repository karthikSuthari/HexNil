package com.example.hexnil.manager;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.model.UpdateSession;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Detects concurrent app updates that could contaminate OTA diagnostic data.
 *
 * During a post-OTA monitoring window, if an app updates, its behavioral changes
 * (battery drain, thermal profile, CPU usage) could be falsely attributed to the
 * system update. This detector periodically scans installed packages for version
 * changes and records them against the active update session so the analysis
 * pipeline can flag or exclude contaminated data.
 */
public class AppUpdateDetector {

    private static final String TAG = "AppUpdateDetector";

    private static final String PREFS_NAME = "app_update_detector_prefs";
    private static final String KEY_KNOWN_VERSIONS = "known_app_versions";
    private static final String KEY_LAST_SCAN = "last_scan_timestamp";
    private static final long SCAN_INTERVAL_MS = 6 * 60 * 60 * 1000L; // 6 hours

    private final Context context;
    private final HexNilDatabase database;

    public AppUpdateDetector(Context context, HexNilDatabase database) {
        this.context = context.getApplicationContext();
        this.database = database;
    }

    /**
     * Scans installed packages for version changes since the last scan.
     * Compares current installed package versions against previously stored
     * versions in SharedPreferences. Any package whose version has changed
     * is recorded as an AppUpdate.
     *
     * @return list of detected app updates since the last scan
     */
    public List<AppUpdate> scanForUpdates() {
        List<AppUpdate> detectedUpdates = new ArrayList<>();
        long now = System.currentTimeMillis();

        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String previousVersionsJson = prefs.getString(KEY_KNOWN_VERSIONS, null);

            // Load previously known versions
            Map<String, String> previousVersions = new HashMap<>();
            if (previousVersionsJson != null) {
                try {
                    JSONObject json = new JSONObject(previousVersionsJson);
                    java.util.Iterator<String> keys = json.keys();
                    while (keys.hasNext()) {
                        String pkg = keys.next();
                        previousVersions.put(pkg, json.getString(pkg));
                    }
                } catch (JSONException e) {
                    Log.w(TAG, "Failed to parse previous versions JSON, starting fresh", e);
                }
            }

            // Get current installed versions
            Map<String, String> currentVersions = getInstalledPackageVersions();

            // Compare and detect updates
            for (Map.Entry<String, String> entry : currentVersions.entrySet()) {
                String packageName = entry.getKey();
                String currentVersion = entry.getValue();
                String previousVersion = previousVersions.get(packageName);

                if (previousVersion != null && !previousVersion.equals(currentVersion)) {
                    AppUpdate update = new AppUpdate(
                            packageName,
                            previousVersion,
                            currentVersion,
                            now,
                            isSystemApp(packageName)
                    );
                    detectedUpdates.add(update);
                    Log.d(TAG, "Detected app update: " + update);
                }
            }

            // Save current versions for next scan
            JSONObject versionsJson = new JSONObject();
            for (Map.Entry<String, String> entry : currentVersions.entrySet()) {
                try {
                    versionsJson.put(entry.getKey(), entry.getValue());
                } catch (JSONException e) {
                    Log.w(TAG, "Failed to store version for " + entry.getKey(), e);
                }
            }

            prefs.edit()
                    .putString(KEY_KNOWN_VERSIONS, versionsJson.toString())
                    .putLong(KEY_LAST_SCAN, now)
                    .apply();

            Log.d(TAG, "Scan complete. Detected " + detectedUpdates.size()
                    + " app updates out of " + currentVersions.size() + " installed packages");

        } catch (Exception e) {
            Log.e(TAG, "Error scanning for app updates", e);
        }

        return detectedUpdates;
    }

    /**
     * Returns a map of all installed package names to their version names.
     *
     * @return map of packageName -> versionName
     */
    public Map<String, String> getInstalledPackageVersions() {
        Map<String, String> versions = new HashMap<>();
        try {
            PackageManager pm = context.getPackageManager();
            List<PackageInfo> packages = pm.getInstalledPackages(0);
            for (PackageInfo info : packages) {
                String versionName = info.versionName;
                if (versionName == null) {
                    versionName = String.valueOf(info.versionCode);
                }
                versions.put(info.packageName, versionName);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting installed package versions", e);
        }
        return versions;
    }

    /**
     * Serializes the detected app updates to JSON and stores them in the
     * update session's concurrentAppUpdatesJson field.
     *
     * @param sessionId the update session ID
     * @param updates   list of detected app updates to record
     */
    public void recordAppUpdatesForSession(long sessionId, List<AppUpdate> updates) {
        if (updates == null || updates.isEmpty()) {
            Log.d(TAG, "No app updates to record for session " + sessionId);
            return;
        }

        try {
            UpdateSession session = database.updateSessionDao().getById(sessionId);
            if (session == null) {
                Log.w(TAG, "Session not found for ID " + sessionId);
                return;
            }

            // Parse existing concurrent updates if any
            JSONArray existingArray;
            if (session.concurrentAppUpdatesJson != null
                    && !session.concurrentAppUpdatesJson.isEmpty()) {
                try {
                    existingArray = new JSONArray(session.concurrentAppUpdatesJson);
                } catch (JSONException e) {
                    Log.w(TAG, "Failed to parse existing concurrentAppUpdatesJson, creating new", e);
                    existingArray = new JSONArray();
                }
            } else {
                existingArray = new JSONArray();
            }

            // Append new updates
            for (AppUpdate update : updates) {
                JSONObject obj = new JSONObject();
                obj.put("packageName", update.packageName);
                obj.put("previousVersion", update.previousVersion);
                obj.put("newVersion", update.newVersion);
                obj.put("detectedAt", update.detectedAt);
                obj.put("isSystemApp", update.isSystemApp);
                existingArray.put(obj);
            }

            session.concurrentAppUpdatesJson = existingArray.toString();
            database.updateSessionDao().update(session);

            Log.d(TAG, "Recorded " + updates.size() + " app updates for session " + sessionId
                    + " (total: " + existingArray.length() + ")");

        } catch (JSONException e) {
            Log.e(TAG, "JSON error recording app updates for session " + sessionId, e);
        } catch (Exception e) {
            Log.e(TAG, "Error recording app updates for session " + sessionId, e);
        }
    }

    /**
     * Checks if any significant app updates happened during the monitoring window
     * that could contaminate OTA diagnostic data. System-level apps and apps known
     * to affect battery metrics are flagged as contamination sources.
     *
     * @param sessionId the update session ID to check
     * @return list of contamination warning strings
     */
    public List<String> checkForContamination(long sessionId) {
        List<String> warnings = new ArrayList<>();

        try {
            UpdateSession session = database.updateSessionDao().getById(sessionId);
            if (session == null) {
                Log.w(TAG, "Session not found for contamination check: " + sessionId);
                return warnings;
            }

            if (session.concurrentAppUpdatesJson == null
                    || session.concurrentAppUpdatesJson.isEmpty()) {
                Log.d(TAG, "No concurrent app updates for session " + sessionId);
                return warnings;
            }

            JSONArray updatesArray;
            try {
                updatesArray = new JSONArray(session.concurrentAppUpdatesJson);
            } catch (JSONException e) {
                Log.w(TAG, "Failed to parse concurrentAppUpdatesJson for session " + sessionId, e);
                return warnings;
            }

            Set<String> systemApps = getSystemApps();
            List<String> contaminationFlags = new ArrayList<>();

            for (int i = 0; i < updatesArray.length(); i++) {
                JSONObject updateObj = updatesArray.getJSONObject(i);
                String packageName = updateObj.optString("packageName", "");
                boolean isSysApp = updateObj.optBoolean("isSystemApp", false);
                String prevVersion = updateObj.optString("previousVersion", "unknown");
                String newVersion = updateObj.optString("newVersion", "unknown");

                if (systemApps.contains(packageName)) {
                    String warning = "CRITICAL: System app updated during monitoring: "
                            + packageName + " (" + prevVersion + " -> " + newVersion + ")";
                    warnings.add(warning);
                    contaminationFlags.add("system_app_update:" + packageName);
                    Log.w(TAG, warning);
                } else if (isSysApp || isSystemApp(packageName)) {
                    String warning = "WARNING: System-pattern app updated during monitoring: "
                            + packageName + " (" + prevVersion + " -> " + newVersion + ")";
                    warnings.add(warning);
                    contaminationFlags.add("system_pattern_app_update:" + packageName);
                    Log.w(TAG, warning);
                }
            }

            // Update contamination flags on the session
            if (!contaminationFlags.isEmpty()) {
                String existingFlags = session.contaminationFlags;
                StringBuilder flagBuilder = new StringBuilder();
                if (existingFlags != null && !existingFlags.isEmpty()) {
                    flagBuilder.append(existingFlags).append(",");
                }
                for (int i = 0; i < contaminationFlags.size(); i++) {
                    if (i > 0) {
                        flagBuilder.append(",");
                    }
                    flagBuilder.append(contaminationFlags.get(i));
                }
                session.contaminationFlags = flagBuilder.toString();
                database.updateSessionDao().update(session);

                Log.d(TAG, "Updated contamination flags for session " + sessionId
                        + ": " + session.contaminationFlags);
            }

        } catch (JSONException e) {
            Log.e(TAG, "JSON error checking contamination for session " + sessionId, e);
        } catch (Exception e) {
            Log.e(TAG, "Error checking contamination for session " + sessionId, e);
        }

        return warnings;
    }

    /**
     * Returns the set of known system-level package names that significantly
     * affect battery behavior. Updates to these packages during an OTA monitoring
     * window should be treated as contamination sources.
     *
     * @return set of system-level package names
     */
    public Set<String> getSystemApps() {
        Set<String> systemApps = new HashSet<>();
        systemApps.add("com.google.android.gms");
        systemApps.add("com.android.systemui");
        systemApps.add("com.android.launcher3");
        systemApps.add("com.google.android.apps.nexuslauncher");
        systemApps.add("com.android.vending");
        systemApps.add("com.google.android.gsf");
        return systemApps;
    }

    /**
     * Checks if a given package name corresponds to a known system app or
     * matches system app naming patterns (com.android.* or com.google.android.*).
     *
     * @param packageName the package name to check
     * @return true if the package is a system app or matches system patterns
     */
    public boolean isSystemApp(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return false;
        }

        // Check known system apps first
        if (getSystemApps().contains(packageName)) {
            return true;
        }

        // Check common system app patterns
        if (packageName.startsWith("com.android.")) {
            return true;
        }
        if (packageName.startsWith("com.google.android.")) {
            return true;
        }

        return false;
    }

    /**
     * Returns true if enough time has elapsed since the last scan to warrant
     * a new scan (controlled by SCAN_INTERVAL_MS).
     *
     * @return true if a new scan should be performed
     */
    public boolean shouldScan() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long lastScan = prefs.getLong(KEY_LAST_SCAN, 0);
        long elapsed = System.currentTimeMillis() - lastScan;
        return elapsed >= SCAN_INTERVAL_MS;
    }

    /**
     * Parses the concurrentAppUpdatesJson field from the given update session
     * and returns the list of app updates that were recorded.
     *
     * @param sessionId the update session ID
     * @return list of AppUpdate objects parsed from the session, or empty list
     */
    public List<AppUpdate> getConcurrentUpdates(long sessionId) {
        List<AppUpdate> updates = new ArrayList<>();

        try {
            UpdateSession session = database.updateSessionDao().getById(sessionId);
            if (session == null) {
                Log.w(TAG, "Session not found for getConcurrentUpdates: " + sessionId);
                return updates;
            }

            if (session.concurrentAppUpdatesJson == null
                    || session.concurrentAppUpdatesJson.isEmpty()) {
                return updates;
            }

            JSONArray updatesArray = new JSONArray(session.concurrentAppUpdatesJson);
            for (int i = 0; i < updatesArray.length(); i++) {
                JSONObject obj = updatesArray.getJSONObject(i);
                AppUpdate update = new AppUpdate(
                        obj.optString("packageName", ""),
                        obj.optString("previousVersion", ""),
                        obj.optString("newVersion", ""),
                        obj.optLong("detectedAt", 0),
                        obj.optBoolean("isSystemApp", false)
                );
                updates.add(update);
            }

        } catch (JSONException e) {
            Log.e(TAG, "JSON error parsing concurrent updates for session " + sessionId, e);
        } catch (Exception e) {
            Log.e(TAG, "Error getting concurrent updates for session " + sessionId, e);
        }

        return updates;
    }

    /**
     * Represents a detected app version change.
     */
    public static class AppUpdate {

        public final String packageName;
        public final String previousVersion;
        public final String newVersion;
        public final long detectedAt;
        public final boolean isSystemApp;

        public AppUpdate(String packageName, String previousVersion, String newVersion,
                long detectedAt, boolean isSystemApp) {
            this.packageName = packageName;
            this.previousVersion = previousVersion;
            this.newVersion = newVersion;
            this.detectedAt = detectedAt;
            this.isSystemApp = isSystemApp;
        }

        @Override
        public String toString() {
            return "AppUpdate{" +
                    "packageName='" + packageName + '\'' +
                    ", previousVersion='" + previousVersion + '\'' +
                    ", newVersion='" + newVersion + '\'' +
                    ", detectedAt=" + detectedAt +
                    ", isSystemApp=" + isSystemApp +
                    '}';
        }
    }
}
