package com.example.hexnil.manager;

import android.content.Context;
import android.util.Log;

import com.example.hexnil.model.BatterySnapshot;

/**
 * Classifies the current device state for ML feature enrichment.
 *
 * Determines what the device is currently doing so battery snapshots can be
 * labeled with a device state and confidence score. The state label is used
 * downstream by the anomaly detector and training pipeline to contextualise
 * drain, thermal, and CPU readings.
 *
 * Supported states: idle, light_active, heavy_active, gaming, charging, doze, screen_off.
 */
public class DeviceStateClassifier {

    private static final String TAG = "DeviceStateClassifier";

    // ========================================================================
    // STATE CONSTANTS
    // ========================================================================

    public static final String STATE_IDLE = "idle";
    public static final String STATE_LIGHT_ACTIVE = "light_active";
    public static final String STATE_HEAVY_ACTIVE = "heavy_active";
    public static final String STATE_GAMING = "gaming";
    public static final String STATE_CHARGING = "charging";
    public static final String STATE_DOZE = "doze";
    public static final String STATE_SCREEN_OFF = "screen_off";

    // ========================================================================
    // KNOWN PACKAGE LISTS
    // ========================================================================

    private static final String[] KNOWN_GAMING_PACKAGES = {
            "com.supercell.clashofclans",
            "com.epicgames.fortnite",
            "com.activision.callofduty.shooter",
            "com.tencent.ig",
            "com.miHoYo.GenshinImpact",
            "com.garena.game.codm"
    };

    // ========================================================================
    // DEPENDENCIES
    // ========================================================================

    private final Context context;

    // ========================================================================
    // CONSTRUCTOR
    // ========================================================================

    public DeviceStateClassifier(Context context) {
        this.context = context.getApplicationContext();
    }

    // ========================================================================
    // PUBLIC API
    // ========================================================================

    /**
     * Classifies the current device state based on the provided telemetry values.
     * The classification follows a priority order: charging > doze > screen_off >
     * gaming > heavy_active > light_active > idle.
     *
     * @param cpuUsage      current CPU usage percentage (0-100)
     * @param screenOn      whether the screen is currently on
     * @param isCharging    whether the device is currently charging
     * @param foregroundApp package name of the current foreground app, or null
     * @param isDozeMode    whether the device is in Doze mode
     * @return a {@link DeviceStateResult} containing the classified state and confidence
     */
    public DeviceStateResult classifyCurrentState(int cpuUsage,
                                                   boolean screenOn,
                                                   boolean isCharging,
                                                   String foregroundApp,
                                                   boolean isDozeMode) {
        if (isCharging) {
            Log.d(TAG, "Classified state: charging (confidence 0.95)");
            return new DeviceStateResult(STATE_CHARGING, 0.95f);
        }

        if (isDozeMode) {
            Log.d(TAG, "Classified state: doze (confidence 0.9)");
            return new DeviceStateResult(STATE_DOZE, 0.9f);
        }

        if (!screenOn) {
            Log.d(TAG, "Classified state: screen_off (confidence 0.85)");
            return new DeviceStateResult(STATE_SCREEN_OFF, 0.85f);
        }

        if (matchesKnownGaming(foregroundApp)) {
            Log.d(TAG, "Classified state: gaming (confidence 0.9) app=" + foregroundApp);
            return new DeviceStateResult(STATE_GAMING, 0.9f);
        }

        if (cpuUsage > 70) {
            Log.d(TAG, "Classified state: heavy_active (confidence 0.8) cpu=" + cpuUsage);
            return new DeviceStateResult(STATE_HEAVY_ACTIVE, 0.8f);
        }

        if (cpuUsage > 20) {
            Log.d(TAG, "Classified state: light_active (confidence 0.75) cpu=" + cpuUsage);
            return new DeviceStateResult(STATE_LIGHT_ACTIVE, 0.75f);
        }

        Log.d(TAG, "Classified state: idle (confidence 0.7) cpu=" + cpuUsage);
        return new DeviceStateResult(STATE_IDLE, 0.7f);
    }

    /**
     * Classifies device state from a {@link BatterySnapshot} by extracting the
     * relevant fields and delegating to {@link #classifyCurrentState}.
     *
     * @param snapshot the battery snapshot to classify
     * @return a {@link DeviceStateResult} containing the classified state and confidence
     */
    public DeviceStateResult classifyFromSnapshot(BatterySnapshot snapshot) {
        // status == 2 is BatteryManager.BATTERY_STATUS_CHARGING
        boolean charging = snapshot.isCharging || snapshot.status == 2;

        // screenOn is three-state Integer: 1=on, 0=off, null=unavailable.
        // Treat null (unavailable) as off for classification purposes.
        boolean screenOn = snapshot.screenOn != null && snapshot.screenOn != 0;

        return classifyCurrentState(
                snapshot.cpuUsage,
                screenOn,
                charging,
                snapshot.foregroundApp,
                snapshot.isDozeMode
        );
    }

    /**
     * Classifies the device state from the snapshot and enriches it by setting
     * the {@code deviceState} and {@code stateConfidence} fields.
     *
     * @param snapshot the battery snapshot to enrich
     * @return the same snapshot instance with deviceState and stateConfidence populated
     */
    public BatterySnapshot enrichSnapshot(BatterySnapshot snapshot) {
        DeviceStateResult result = classifyFromSnapshot(snapshot);
        snapshot.deviceState = result.state;
        snapshot.stateConfidence = result.confidence;
        Log.d(TAG, "Enriched snapshot id=" + snapshot.id
                + " state=" + result.state
                + " confidence=" + result.confidence);
        return snapshot;
    }

    /**
     * Returns {@code true} if the given state represents a high-drain activity
     * that is expected to consume significant battery.
     *
     * @param state the device state string to check
     * @return true for "gaming" and "heavy_active"
     */
    public boolean isHighDrainActivity(String state) {
        return STATE_GAMING.equals(state) || STATE_HEAVY_ACTIVE.equals(state);
    }

    /**
     * Returns {@code true} if the given state represents a low-power condition
     * where minimal battery drain is expected.
     *
     * @param state the device state string to check
     * @return true for "idle", "doze", and "screen_off"
     */
    public boolean isLowPowerState(String state) {
        return STATE_IDLE.equals(state)
                || STATE_DOZE.equals(state)
                || STATE_SCREEN_OFF.equals(state);
    }

    // ========================================================================
    // INNER CLASSES
    // ========================================================================

    /**
     * Result of a device state classification, containing the determined state
     * label and the classifier's confidence in that determination.
     */
    public static class DeviceStateResult {
        /** Device state label (one of the STATE_* constants). */
        public String state;
        /** Confidence score for the classification, 0.0-1.0. */
        public float confidence;

        public DeviceStateResult(String state, float confidence) {
            this.state = state;
            this.confidence = confidence;
        }
    }

    // ========================================================================
    // PRIVATE HELPERS
    // ========================================================================

    /**
     * Returns {@code true} if the given package name matches any entry in the
     * known gaming packages list.
     *
     * @param packageName the package name to check, may be null
     * @return true if the package is a known gaming app
     */
    private boolean matchesKnownGaming(String packageName) {
        if (packageName == null) {
            return false;
        }
        for (String known : KNOWN_GAMING_PACKAGES) {
            if (known.equals(packageName)) {
                return true;
            }
        }
        return false;
    }
}
