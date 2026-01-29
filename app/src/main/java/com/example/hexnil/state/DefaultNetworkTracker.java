package com.example.hexnil.state;

import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

/**
 * Registers a default network callback and updates DeviceStateCache.
 * Event-driven only; no polling.
 */
public final class DefaultNetworkTracker {

    private static final String TAG = "DefaultNetworkTracker";

    private final ConnectivityManager connectivityManager;
    private final DeviceStateCache cache;
    private ConnectivityManager.NetworkCallback callback;
    private boolean registered;

    public DefaultNetworkTracker(@NonNull ConnectivityManager cm, @NonNull DeviceStateCache cache) {
        this.connectivityManager = cm;
        this.cache = cache;
    }

    public void register() {
        if (registered) {
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.w(TAG, "DefaultNetworkCallback requires API 24+; network state will stay unknown");
            cache.setNetworkUnknown();
            return;
        }
        try {
            callback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(@NonNull Network network) {
                    try {
                        NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(network);
                        DeviceStateCache.updateFromCapabilities(cache, caps);
                    } catch (Exception e) {
                        Log.w(TAG, "Failed to get capabilities for default network", e);
                    }
                }

                @Override
                public void onCapabilitiesChanged(@NonNull Network network,
                                                  @NonNull NetworkCapabilities caps) {
                    DeviceStateCache.updateFromCapabilities(cache, caps);
                }

                @Override
                public void onLost(@NonNull Network network) {
                    DeviceStateCache.clearNetwork(cache);
                }
            };
            connectivityManager.registerDefaultNetworkCallback(callback);
            registered = true;
            Log.i(TAG, "Default network callback registered");
        } catch (Exception e) {
            Log.e(TAG, "Failed to register default network callback", e);
            cache.setNetworkUnknown();
        }
    }

    public void unregister() {
        if (!registered || callback == null) {
            return;
        }
        try {
            connectivityManager.unregisterNetworkCallback(callback);
            registered = false;
            callback = null;
            Log.i(TAG, "Default network callback unregistered");
        } catch (Exception e) {
            Log.w(TAG, "Error unregistering network callback", e);
        }
    }
}
