package com.example.hexnil.state;

import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Persistent cached device state for snapshot collection.
 * Updated only via events (screen, network, charging). Never polled in background.
 *
 * Three-state semantics:
 * - Boolean true/false = we know the state (from events).
 * - null = UNAVAILABLE (no event yet or Android restricted access).
 *
 * Snapshots MUST read from cache only. Do NOT call PowerManager.isInteractive(),
 * WifiManager.isWifiEnabled(), etc. at sample time.
 */
public final class DeviceStateCache {

    private static final String TAG = "DeviceStateCache";

    private final AtomicReference<Boolean> screenOn = new AtomicReference<>(null);
    private final AtomicReference<Boolean> wifiActive = new AtomicReference<>(null);
    private final AtomicReference<Boolean> mobileActive = new AtomicReference<>(null);
    private final AtomicReference<Boolean> charging = new AtomicReference<>(null);

    public DeviceStateCache() {}

    // --- Screen (ACTION_SCREEN_ON / ACTION_SCREEN_OFF) ---

    public void setScreenOn(boolean on) {
        screenOn.set(on);
        Log.d(TAG, "Screen state cached: " + (on ? "on" : "off"));
    }

    @Nullable
    public Boolean getScreenOn() {
        return screenOn.get();
    }

    /**
     * 1 = on, 0 = off, null = unavailable.
     */
    @Nullable
    public Integer getScreenOnAsTernary() {
        Boolean v = screenOn.get();
        if (v == null) return null;
        return v ? 1 : 0;
    }

    // --- Network (ConnectivityManager.NetworkCallback) ---

    public void setNetworkState(boolean hasWifi, boolean hasMobile) {
        wifiActive.set(hasWifi);
        mobileActive.set(hasMobile);
        Log.d(TAG, "Network state cached: wifi=" + hasWifi + ", mobile=" + hasMobile);
    }

    public void clearNetworkState() {
        wifiActive.set(false);
        mobileActive.set(false);
        Log.d(TAG, "Network state cached: disconnected");
    }

    /** Called when we have never received a network callback (unknown). */
    public void setNetworkUnknown() {
        wifiActive.set(null);
        mobileActive.set(null);
    }

    @Nullable
    public Boolean getWifiActive() {
        return wifiActive.get();
    }

    @Nullable
    public Boolean getMobileActive() {
        return mobileActive.get();
    }

    @Nullable
    public Integer getWifiActiveAsTernary() {
        Boolean v = wifiActive.get();
        if (v == null) return null;
        return v ? 1 : 0;
    }

    @Nullable
    public Integer getMobileActiveAsTernary() {
        Boolean v = mobileActive.get();
        if (v == null) return null;
        return v ? 1 : 0;
    }

    // --- Charging (ACTION_BATTERY_CHANGED / POWER_* ) ---

    public void setCharging(boolean charging) {
        this.charging.set(charging);
        Log.d(TAG, "Charging state cached: " + charging);
    }

    @Nullable
    public Boolean getCharging() {
        return charging.get();
    }

    @Nullable
    public Integer getChargingAsTernary() {
        Boolean v = charging.get();
        if (v == null) return null;
        return v ? 1 : 0;
    }

    // --- NetworkCallback helper ---

    /**
     * Update cache from NetworkCapabilities.
     * Call from NetworkCallback#onAvailable / onCapabilitiesChanged.
     */
    public static void updateFromCapabilities(
            DeviceStateCache cache,
            NetworkCapabilities caps) {
        if (cache == null || caps == null) {
            return;
        }
        boolean wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        boolean mobile = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
        cache.setNetworkState(wifi, mobile);
    }

    /**
     * Clear network state when we lose the default network.
     * Call from NetworkCallback#onLost.
     */
    public static void clearNetwork(DeviceStateCache cache) {
        if (cache != null) {
            cache.clearNetworkState();
        }
    }
}
