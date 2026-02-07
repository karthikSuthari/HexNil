package com.example.hexnil.manager;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.model.DeviceProfile;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Locale;

/**
 * Device Profile Manager
 *
 * Handles device fingerprinting and device profile management for fleet-wide
 * hardware identification and statistical aggregation.
 *
 * The fingerprint is SHA256(manufacturer + model + soc + ramGB) and uniquely
 * identifies a device SKU. This enables per-SKU baselines for anomaly detection,
 * drain benchmarking, and thermal envelope tracking across all devices sharing
 * the same hardware configuration.
 *
 * ============================================================================
 * FINGERPRINT GENERATION
 * ============================================================================
 *
 * Sources:
 *   - Manufacturer: Build.MANUFACTURER
 *   - Model:        Build.MODEL
 *   - SoC:          Build.SOC_MODEL (API 31+) or Build.HARDWARE (fallback)
 *   - RAM:          ActivityManager.MemoryInfo.totalMem (rounded to GB)
 *
 * Hash: SHA-256 of the concatenated fields, returned as lowercase hex string.
 *
 * ============================================================================
 * FLEET STATISTICS
 * ============================================================================
 *
 * updateFleetStatistics() aggregates recent telemetry for a fingerprint:
 *   - Idle drain average from idle drain events
 *   - Active drain average from screen-on drain events
 *   - Thermal envelope (CPU, GPU, battery) from thermal events
 *   - Updates the DeviceProfile with these fleet-wide averages
 */
public class DeviceProfileManager {

    private static final String TAG = "DeviceProfileManager";

    // =========================================================================
    // CONFIGURATION
    // =========================================================================

    /** Time window for fleet statistics aggregation (7 days) */
    private static final long FLEET_STATS_WINDOW_MS = 7L * 24L * 60L * 60L * 1000L;

    // =========================================================================
    // DEPENDENCIES
    // =========================================================================

    private final Context context;
    private final HexNilDatabase database;

    /** Cached fingerprint for the current device */
    private String cachedFingerprint;

    // =========================================================================
    // CONSTRUCTOR
    // =========================================================================

    public DeviceProfileManager(Context context, HexNilDatabase database) {
        this.context = context.getApplicationContext();
        this.database = database;
    }

    // =========================================================================
    // FINGERPRINT GENERATION
    // =========================================================================

    /**
     * Generate a SHA-256 fingerprint for the current device hardware.
     *
     * The fingerprint uniquely identifies a device SKU by hashing:
     * manufacturer + model + SoC identifier + RAM (GB).
     *
     * @return lowercase hex string of the SHA-256 hash
     */
    public String generateDeviceFingerprint() {
        String manufacturer = Build.MANUFACTURER != null ? Build.MANUFACTURER : "unknown";
        String model = Build.MODEL != null ? Build.MODEL : "unknown";
        String soc = getSocIdentifier();
        int ramGB = getRamGB();

        String input = manufacturer + model + soc + ramGB;

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                String hex = String.format(Locale.US, "%02x", b & 0xFF);
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "SHA-256 not available, falling back to hashCode", e);
            return String.format(Locale.US, "%08x", input.hashCode());
        }
    }

    /**
     * Get the cached fingerprint for the current device.
     * Generates once on first call and caches for subsequent calls.
     *
     * @return device fingerprint hex string
     */
    public String getCurrentFingerprint() {
        if (cachedFingerprint == null) {
            cachedFingerprint = generateDeviceFingerprint();
        }
        return cachedFingerprint;
    }

    // =========================================================================
    // PROFILE MANAGEMENT
    // =========================================================================

    /**
     * Get or create a DeviceProfile for the current device.
     *
     * If a profile already exists for this device's fingerprint, increments the
     * deviceCount and updates lastUpdated. Otherwise creates a new profile with
     * all hardware fields populated.
     *
     * @return the existing or newly created DeviceProfile
     */
    public DeviceProfile getOrCreateProfile() {
        String fingerprint = getCurrentFingerprint();

        try {
            DeviceProfile existing = database.deviceProfileDao().getByFingerprint(fingerprint);

            if (existing != null) {
                existing.deviceCount++;
                existing.lastUpdated = System.currentTimeMillis();
                database.deviceProfileDao().update(existing);
                Log.d(TAG, "Updated existing profile: " + existing);
                return existing;
            }

            // Create new profile with full hardware identification
            String manufacturer = Build.MANUFACTURER != null ? Build.MANUFACTURER : "unknown";
            String model = Build.MODEL != null ? Build.MODEL : "unknown";
            String soc = getSocIdentifier();
            int ramGB = getRamGB();
            int batteryCapacity = getBatteryDesignCapacity(context);

            DeviceProfile profile = DeviceProfile.create(
                    fingerprint, manufacturer, model, soc, ramGB, batteryCapacity);

            // Populate additional hardware fields
            profile.storageGB = getStorageGB();
            profile.screenSizeInches = getScreenSizeInches();
            profile.screenResolution = getScreenResolution();
            profile.marketName = getSystemProperty("ro.product.marketname",
                    getSystemProperty("ro.config.marketing_name", model));

            long id = database.deviceProfileDao().insert(profile);
            profile.id = id;

            Log.d(TAG, "Created new profile: " + profile);
            return profile;

        } catch (Exception e) {
            Log.e(TAG, "Error in getOrCreateProfile", e);
            // Return a minimal profile so callers don't get null
            String manufacturer = Build.MANUFACTURER != null ? Build.MANUFACTURER : "unknown";
            String model = Build.MODEL != null ? Build.MODEL : "unknown";
            return DeviceProfile.create(fingerprint, manufacturer, model,
                    getSocIdentifier(), getRamGB(), 0);
        }
    }

    /**
     * Look up a DeviceProfile by its fingerprint.
     *
     * @param fingerprint the SHA-256 device fingerprint
     * @return the DeviceProfile, or null if not found
     */
    public DeviceProfile getProfileForDevice(String fingerprint) {
        try {
            return database.deviceProfileDao().getByFingerprint(fingerprint);
        } catch (Exception e) {
            Log.e(TAG, "Error looking up profile for fingerprint: " + fingerprint, e);
            return null;
        }
    }

    /**
     * Get all DeviceProfiles in the database, ordered by device count descending.
     *
     * @return list of all profiles
     */
    public List<DeviceProfile> getAllProfiles() {
        try {
            return database.deviceProfileDao().getAll();
        } catch (Exception e) {
            Log.e(TAG, "Error fetching all profiles", e);
            return java.util.Collections.emptyList();
        }
    }

    // =========================================================================
    // FLEET STATISTICS
    // =========================================================================

    /**
     * Update fleet-wide statistics for a device fingerprint.
     *
     * Queries recent battery snapshots, thermal events, and drain events to
     * compute fleet averages for idle drain, active drain, and thermal envelopes.
     * Updates the DeviceProfile with these aggregated metrics.
     *
     * @param fingerprint the device fingerprint to update statistics for
     */
    public void updateFleetStatistics(String fingerprint) {
        try {
            DeviceProfile profile = database.deviceProfileDao().getByFingerprint(fingerprint);
            if (profile == null) {
                Log.w(TAG, "No profile found for fingerprint: " + fingerprint);
                return;
            }

            long cutoff = System.currentTimeMillis() - FLEET_STATS_WINDOW_MS;

            // =====================================================================
            // Idle drain statistics
            // =====================================================================
            Float idleDrain = database.drainEventDao().getAverageIdleDrain(cutoff);
            if (idleDrain != null && idleDrain > 0) {
                profile.idleDrainMean = idleDrain;

                // Compute standard deviation from individual idle events
                List<com.example.hexnil.model.DrainEvent> idleEvents =
                        database.drainEventDao().getIdleDrainEvents(cutoff);
                if (idleEvents != null && idleEvents.size() >= 2) {
                    float sum = 0f;
                    float sumSq = 0f;
                    int count = 0;
                    for (com.example.hexnil.model.DrainEvent event : idleEvents) {
                        float rate = event.drainRatePerHour;
                        sum += rate;
                        sumSq += rate * rate;
                        count++;
                    }
                    if (count > 1) {
                        float mean = sum / count;
                        float variance = (sumSq / count) - (mean * mean);
                        profile.idleDrainStdDev = (float) Math.sqrt(Math.max(0, variance));
                    }
                }
            }

            // =====================================================================
            // Active drain statistics (screen-on)
            // =====================================================================
            Float activeDrain = database.drainEventDao().getAverageScreenOnDrain(cutoff);
            if (activeDrain != null && activeDrain > 0) {
                profile.activeDrainMean = activeDrain;

                // Compute standard deviation from screen-on drain events
                List<com.example.hexnil.model.DrainEvent> activeEvents =
                        database.drainEventDao().getScreenOnDrainEvents(cutoff);
                if (activeEvents != null && activeEvents.size() >= 2) {
                    float sum = 0f;
                    float sumSq = 0f;
                    int count = 0;
                    for (com.example.hexnil.model.DrainEvent event : activeEvents) {
                        float rate = event.drainRatePerHour;
                        sum += rate;
                        sumSq += rate * rate;
                        count++;
                    }
                    if (count > 1) {
                        float mean = sum / count;
                        float variance = (sumSq / count) - (mean * mean);
                        profile.activeDrainStdDev = (float) Math.sqrt(Math.max(0, variance));
                    }
                }
            }

            // =====================================================================
            // Thermal envelope statistics
            // =====================================================================
            List<com.example.hexnil.model.ThermalEvent> thermalEvents =
                    database.thermalEventDao().getEventsSince(cutoff);
            if (thermalEvents != null && !thermalEvents.isEmpty()) {
                float maxCpuTemp = 0f;
                float maxGpuTemp = 0f;
                float maxBatteryTemp = 0f;

                for (com.example.hexnil.model.ThermalEvent event : thermalEvents) {
                    // peakTemperature represents the max temperature observed in the event
                    float peakTemp = event.peakTemperature;

                    // Use CPU contribution to gauge CPU thermal load
                    if (event.cpuContribution > 0.3f && peakTemp > maxCpuTemp) {
                        maxCpuTemp = peakTemp;
                    }

                    // Use GPU contribution to gauge GPU thermal load
                    if (event.gpuContribution > 0.3f && peakTemp > maxGpuTemp) {
                        maxGpuTemp = peakTemp;
                    }

                    // Battery temperature from the event
                    if (event.batteryTemp > maxBatteryTemp) {
                        maxBatteryTemp = event.batteryTemp;
                    }
                }

                // Use the 95th percentile approach: take the peak observed as the envelope
                // for the fleet. If no specific contribution data, use peak temperature.
                if (maxCpuTemp > 0) {
                    profile.thermalEnvelopeCpu = maxCpuTemp;
                } else {
                    // Fallback: use average peak temperature as CPU envelope estimate
                    float avgPeak = database.thermalEventDao().getAveragePeakTemperature(cutoff);
                    if (avgPeak > 0) {
                        profile.thermalEnvelopeCpu = avgPeak;
                    }
                }

                if (maxGpuTemp > 0) {
                    profile.thermalEnvelopeGpu = maxGpuTemp;
                }

                if (maxBatteryTemp > 0) {
                    profile.thermalEnvelopeBattery = maxBatteryTemp;
                }
            }

            // =====================================================================
            // Battery snapshot averages
            // =====================================================================
            List<com.example.hexnil.model.BatterySnapshot> recentSnapshots =
                    database.batterySnapshotDao().getSnapshotsSince(cutoff);
            if (recentSnapshots != null && !recentSnapshots.isEmpty()) {
                float tempSum = 0f;
                int tempCount = 0;
                for (com.example.hexnil.model.BatterySnapshot snapshot : recentSnapshots) {
                    if (snapshot.temperature > 0) {
                        tempSum += snapshot.temperature / 10f;
                        tempCount++;
                    }
                }
                // Battery thermal envelope from snapshots if thermal events didn't set it
                if (profile.thermalEnvelopeBattery == null || profile.thermalEnvelopeBattery == 0f) {
                    if (tempCount > 0) {
                        float maxSnapshotTemp = database.batterySnapshotDao()
                                .getMaxTemperature(cutoff) / 10f;
                        if (maxSnapshotTemp > 0) {
                            profile.thermalEnvelopeBattery = maxSnapshotTemp;
                        }
                    }
                }
            }

            // =====================================================================
            // Persist updated profile
            // =====================================================================
            profile.lastUpdated = System.currentTimeMillis();
            database.deviceProfileDao().update(profile);

            Log.d(TAG, "Fleet statistics updated for " + fingerprint
                    + ": idleDrain=" + profile.idleDrainMean
                    + ", activeDrain=" + profile.activeDrainMean
                    + ", thermalCpu=" + profile.thermalEnvelopeCpu
                    + ", thermalBattery=" + profile.thermalEnvelopeBattery);

        } catch (Exception e) {
            Log.e(TAG, "Error updating fleet statistics for fingerprint: " + fingerprint, e);
        }
    }

    // =========================================================================
    // HARDWARE DETECTION UTILITIES
    // =========================================================================

    /**
     * Get the SoC identifier for the current device.
     * Uses Build.SOC_MODEL on API 31+ with fallback to Build.HARDWARE.
     *
     * @return SoC identifier string
     */
    private String getSocIdentifier() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // API 31+ (Android 12)
            String socModel = Build.SOC_MODEL;
            if (socModel != null && !socModel.isEmpty() && !"unknown".equalsIgnoreCase(socModel)) {
                return socModel;
            }
        }
        // Fallback for older API levels or if SOC_MODEL is unavailable
        String hardware = Build.HARDWARE;
        return (hardware != null && !hardware.isEmpty()) ? hardware : "unknown";
    }

    /**
     * Get total RAM in GB, rounded to the nearest whole number.
     *
     * @return RAM in gigabytes
     */
    private int getRamGB() {
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(memInfo);
                long totalBytes = memInfo.totalMem;
                // Round to nearest GB
                return (int) Math.round(totalBytes / (1024.0 * 1024.0 * 1024.0));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting RAM size", e);
        }
        return 0;
    }

    /**
     * Get the screen diagonal size in inches using display metrics.
     *
     * @return screen size in inches, or 0 if unavailable
     */
    public float getScreenSizeInches() {
        try {
            WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            if (wm != null && wm.getDefaultDisplay() != null) {
                DisplayMetrics metrics = new DisplayMetrics();
                wm.getDefaultDisplay().getRealMetrics(metrics);

                float widthInches = metrics.widthPixels / metrics.xdpi;
                float heightInches = metrics.heightPixels / metrics.ydpi;
                double diagonal = Math.sqrt(
                        (widthInches * widthInches) + (heightInches * heightInches));

                // Round to one decimal place
                return (float) (Math.round(diagonal * 10.0) / 10.0);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting screen size", e);
        }
        return 0f;
    }

    /**
     * Get the screen resolution as a "WxH" string.
     *
     * @return screen resolution string, or null if unavailable
     */
    private String getScreenResolution() {
        try {
            WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            if (wm != null && wm.getDefaultDisplay() != null) {
                DisplayMetrics metrics = new DisplayMetrics();
                wm.getDefaultDisplay().getRealMetrics(metrics);
                return metrics.widthPixels + "x" + metrics.heightPixels;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting screen resolution", e);
        }
        return null;
    }

    /**
     * Get total internal storage in GB.
     *
     * @return storage capacity in gigabytes
     */
    public int getStorageGB() {
        try {
            StatFs statFs = new StatFs(Environment.getDataDirectory().getAbsolutePath());
            long totalBytes = statFs.getTotalBytes();
            // Round to nearest standard storage size
            int rawGB = (int) Math.round(totalBytes / (1024.0 * 1024.0 * 1024.0));

            // Snap to common OEM storage tiers: 32, 64, 128, 256, 512, 1024
            if (rawGB <= 48) return 32;
            if (rawGB <= 96) return 64;
            if (rawGB <= 192) return 128;
            if (rawGB <= 384) return 256;
            if (rawGB <= 768) return 512;
            return 1024;
        } catch (Exception e) {
            Log.e(TAG, "Error getting storage size", e);
            return 0;
        }
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    /**
     * Read a system property using reflection on android.os.SystemProperties.
     * This is the same pattern used by BatteryHealthManager for accessing
     * hidden system properties not available through public APIs.
     *
     * @param key          the system property key
     * @param defaultValue fallback value if the property is not found
     * @return the property value, or defaultValue if not available
     */
    private String getSystemProperty(String key, String defaultValue) {
        try {
            Class<?> systemProperties = Class.forName("android.os.SystemProperties");
            Method get = systemProperties.getMethod("get", String.class, String.class);
            String result = (String) get.invoke(null, key, defaultValue);
            return (result != null && !result.isEmpty()) ? result : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Get the battery design capacity in mAh using PowerProfile reflection.
     * This is the standard OEM approach for reading the design capacity
     * from the device's power_profile.xml configuration.
     *
     * @param context application context
     * @return battery design capacity in mAh, or 0 if unavailable
     */
    private int getBatteryDesignCapacity(Context context) {
        try {
            Class<?> powerProfileClass = Class.forName("com.android.internal.os.PowerProfile");
            Object powerProfile = powerProfileClass
                    .getConstructor(Context.class)
                    .newInstance(context);
            Method getAveragePower = powerProfileClass
                    .getMethod("getAveragePower", String.class);
            double capacity = (double) getAveragePower.invoke(powerProfile, "battery.capacity");
            if (capacity > 0) {
                return (int) capacity;
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not read battery capacity from PowerProfile", e);
        }
        return 0;
    }
}
