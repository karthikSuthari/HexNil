package com.example.hexnil.manager;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import com.example.hexnil.model.NetworkWifiSnapshot;
import java.util.concurrent.atomic.AtomicLong;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * OEM-Grade Wi-Fi Data Collector
 * 
 * Collects raw Wi-Fi diagnostics data using WifiManager APIs.
 * SSID and BSSID are hashed for privacy protection.
 * 
 * API LEVEL REQUIREMENTS:
 * - Basic Wi-Fi: API 17+
 * - TX/RX link speed: API 29+
 * - Wi-Fi standard: API 30+
 * 
 * PERMISSION REQUIREMENTS:
 * - ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION (API 27+) for SSID/BSSID
 * - ACCESS_WIFI_STATE for basic info
 * 
 * DATA INTEGRITY PRINCIPLES:
 * - Hash SSID/BSSID before storage (SHA-256)
 * - Never fabricate values - use null for unavailable data
 * - Always timestamp with System.currentTimeMillis()
 */
public class WifiCollector {
    private static final String TAG = "WifiCollector";
    private static final String UNKNOWN_SSID = "<unknown ssid>";

    private final Context context;
    private final WifiManager wifiManager;
    private final AtomicLong sequenceCounter = new AtomicLong(0);
    private long sessionId = 0;
    private NetworkWifiSnapshot lastSnapshot = null;
    private int reconnectCount = 0;
    private String lastBssidHash = null;

    public WifiCollector(Context context) {
        this.context = context.getApplicationContext();
        this.wifiManager = (WifiManager) context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        this.sessionId = System.currentTimeMillis();
    }

    /**
     * Collect a fresh Wi-Fi snapshot with the given context.
     * 
     * @param snapshotContext Context for this capture (e.g., "screen_on", "idle")
     * @return NetworkWifiSnapshot with raw values, null if Wi-Fi unavailable
     */
    @Nullable
    public NetworkWifiSnapshot collectSnapshot(@NonNull String snapshotContext) {
        if (wifiManager == null) {
            Log.w(TAG, "WifiManager unavailable");
            return null;
        }

        NetworkWifiSnapshot snapshot = new NetworkWifiSnapshot(snapshotContext);
        snapshot.sequenceNumber = sequenceCounter.incrementAndGet();
        snapshot.sessionId = sessionId;
        snapshot.dataSource = "WIFI_MANAGER";
        snapshot.snapshotTrigger = "PERIODIC";

        // Check if Wi-Fi is enabled
        if (!wifiManager.isWifiEnabled()) {
            snapshot.connectionState = "DISABLED";
            snapshot.validationStatus = "valid";
            snapshot.qualityScore = 50;
            lastSnapshot = snapshot;
            return snapshot;
        }

        // Get connection info
        WifiInfo wifiInfo = getWifiInfo();
        if (wifiInfo == null) {
            snapshot.connectionState = "DISCONNECTED";
            snapshot.validationStatus = "valid";
            snapshot.qualityScore = 50;
            lastSnapshot = snapshot;
            return snapshot;
        }

        // Collect all available data
        collectConnectionState(wifiInfo, snapshot);
        collectSignalInfo(wifiInfo, snapshot);
        collectLinkSpeed(wifiInfo, snapshot);
        collectWifiStandard(wifiInfo, snapshot);
        collectNetworkIdentity(wifiInfo, snapshot);
        collectStabilityMetrics(wifiInfo, snapshot);

        // Detect frozen signal
        detectFrozenSignal(snapshot);

        // Set initial validation
        if (snapshot.rssi != null) {
            snapshot.validationStatus = "unchecked";
            snapshot.qualityScore = 70;
        } else {
            snapshot.validationStatus = "warning";
            snapshot.qualityScore = 40;
            snapshot.validationNotes = "No signal data available";
        }

        lastSnapshot = snapshot;
        return snapshot;
    }

    /**
     * Get WifiInfo safely.
     */
    @SuppressLint("MissingPermission")
    @Nullable
    private WifiInfo getWifiInfo() {
        try {
            return wifiManager.getConnectionInfo();
        } catch (SecurityException e) {
            Log.w(TAG, "getConnectionInfo() denied", e);
            return null;
        } catch (Exception e) {
            Log.w(TAG, "Failed to get WifiInfo", e);
            return null;
        }
    }

    /**
     * Collect connection state.
     */
    private void collectConnectionState(WifiInfo wifiInfo, NetworkWifiSnapshot snapshot) {
        try {
            int networkId = wifiInfo.getNetworkId();
            if (networkId == -1) {
                snapshot.connectionState = "DISCONNECTED";
            } else {
                snapshot.connectionState = "CONNECTED";
                snapshot.networkId = networkId;
            }

            // Hidden network
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // No direct API for hidden, but we can check if SSID is hidden
            }

        } catch (Exception e) {
            Log.w(TAG, "Failed to get connection state", e);
            snapshot.connectionState = "UNKNOWN";
        }
    }

    /**
     * Collect signal strength info.
     */
    private void collectSignalInfo(WifiInfo wifiInfo, NetworkWifiSnapshot snapshot) {
        try {
            int rssi = wifiInfo.getRssi();
            // Check for invalid RSSI (typically -127 or similar when disconnected)
            if (rssi >= -100 && rssi <= 0) {
                snapshot.rssi = rssi;
            }

            // Signal level (0-4)
            snapshot.signalLevel = WifiManager.calculateSignalLevel(rssi, 5);

        } catch (Exception e) {
            Log.w(TAG, "Failed to get signal info", e);
        }
    }

    /**
     * Collect link speed information.
     */
    private void collectLinkSpeed(WifiInfo wifiInfo, NetworkWifiSnapshot snapshot) {
        try {
            // Basic link speed (available on all versions)
            int linkSpeed = wifiInfo.getLinkSpeed();
            if (linkSpeed > 0) {
                snapshot.linkSpeedMbps = linkSpeed;
            }

            // TX/RX link speed (API 29+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                int txSpeed = wifiInfo.getTxLinkSpeedMbps();
                if (txSpeed > 0) {
                    snapshot.txLinkSpeedMbps = txSpeed;
                }

                int rxSpeed = wifiInfo.getRxLinkSpeedMbps();
                if (rxSpeed > 0) {
                    snapshot.rxLinkSpeedMbps = rxSpeed;
                }

                // Max speeds (API 30+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    int maxTx = wifiInfo.getMaxSupportedTxLinkSpeedMbps();
                    if (maxTx > 0) {
                        snapshot.maxTxLinkSpeedMbps = maxTx;
                    }

                    int maxRx = wifiInfo.getMaxSupportedRxLinkSpeedMbps();
                    if (maxRx > 0) {
                        snapshot.maxRxLinkSpeedMbps = maxRx;
                    }
                }
            }

        } catch (Exception e) {
            Log.w(TAG, "Failed to get link speed", e);
        }
    }

    /**
     * Collect Wi-Fi standard (API 30+).
     */
    private void collectWifiStandard(WifiInfo wifiInfo, NetworkWifiSnapshot snapshot) {
        try {
            // Frequency
            int frequency = wifiInfo.getFrequency();
            if (frequency > 0) {
                snapshot.frequency = frequency;
                snapshot.frequencyBand = NetworkWifiSnapshot.getFrequencyBand(frequency);
                snapshot.channel = frequencyToChannel(frequency);
            }

            // Wi-Fi standard (API 30+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                int standard = wifiInfo.getWifiStandard();
                snapshot.wifiStandard = standard;
                snapshot.wifiStandardName = getWifiStandardName(standard);
            }

        } catch (Exception e) {
            Log.w(TAG, "Failed to get Wi-Fi standard", e);
        }
    }

    /**
     * Collect network identity (SSID/BSSID hashed for privacy).
     */
    private void collectNetworkIdentity(WifiInfo wifiInfo, NetworkWifiSnapshot snapshot) {
        try {
            // SSID - requires location permission on Android 8+
            String ssid = wifiInfo.getSSID();
            if (ssid != null && !ssid.equals(UNKNOWN_SSID) && !ssid.equals("\"" + UNKNOWN_SSID + "\"")) {
                // Remove quotes if present
                ssid = ssid.replace("\"", "");
                snapshot.ssidHash = hashSha256(ssid);
            } else if (hasLocationPermission()) {
                // Permission granted but still unknown - might be hidden network
                snapshot.validationNotes = appendNote(snapshot.validationNotes, "SSID hidden or unavailable");
            } else {
                snapshot.validationNotes = appendNote(snapshot.validationNotes,
                        "Location permission required for SSID");
            }

            // BSSID
            String bssid = wifiInfo.getBSSID();
            if (bssid != null && !bssid.equals("02:00:00:00:00:00")) {
                snapshot.bssidHash = hashSha256(bssid);
            }

        } catch (Exception e) {
            Log.w(TAG, "Failed to get network identity", e);
        }
    }

    /**
     * Collect stability metrics.
     */
    private void collectStabilityMetrics(WifiInfo wifiInfo, NetworkWifiSnapshot snapshot) {
        try {
            // Track reconnects
            String currentBssidHash = snapshot.bssidHash;
            if (lastBssidHash != null && currentBssidHash != null &&
                    !lastBssidHash.equals(currentBssidHash)) {
                reconnectCount++;
            }
            lastBssidHash = currentBssidHash;
            snapshot.reconnectCount = reconnectCount;

            // IP addresses
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Check for IP (indirect method)
                int ipAddress = wifiInfo.getIpAddress();
                snapshot.hasIpv4 = (ipAddress != 0);
            }

        } catch (Exception e) {
            Log.w(TAG, "Failed to collect stability metrics", e);
        }
    }

    /**
     * Detect frozen signal (identical to previous snapshot).
     */
    private void detectFrozenSignal(NetworkWifiSnapshot snapshot) {
        if (lastSnapshot != null) {
            snapshot.signalDataChanged = !snapshot.hasSameSignalAs(lastSnapshot);
        }
    }

    /**
     * Hash a string using SHA-256.
     */
    private String hashSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1)
                    hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "SHA-256 not available", e);
            return null;
        }
    }

    /**
     * Convert frequency to channel number.
     */
    private int frequencyToChannel(int frequency) {
        if (frequency >= 2412 && frequency <= 2484) {
            // 2.4 GHz band
            if (frequency == 2484)
                return 14;
            return (frequency - 2412) / 5 + 1;
        } else if (frequency >= 5170 && frequency <= 5825) {
            // 5 GHz band
            return (frequency - 5170) / 5 + 34;
        } else if (frequency >= 5925 && frequency <= 7125) {
            // 6 GHz band
            return (frequency - 5950) / 5 + 1;
        }
        return -1;
    }

    /**
     * Get Wi-Fi standard name from constant.
     */
    private String getWifiStandardName(int standard) {
        switch (standard) {
            case 1:
                return "802.11a/b/g";
            case 4:
                return "802.11n";
            case 5:
                return "802.11ac";
            case 6:
                return "802.11ax";
            case 7:
                return "802.11ad";
            case 8:
                return "802.11be";
            default:
                return "UNKNOWN";
        }
    }

    /**
     * Append a note to existing notes.
     */
    private String appendNote(String existing, String note) {
        if (existing == null || existing.isEmpty()) {
            return note;
        }
        return existing + "; " + note;
    }

    /**
     * Check if location permission is granted.
     */
    private boolean hasLocationPermission() {
        return ActivityCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(context,
                        Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Reset session - call when starting new monitoring session.
     */
    public void resetSession() {
        sessionId = System.currentTimeMillis();
        sequenceCounter.set(0);
        lastSnapshot = null;
        reconnectCount = 0;
        lastBssidHash = null;
    }

    /**
     * Get current session ID.
     */
    public long getSessionId() {
        return sessionId;
    }

    /**
     * Record a disconnect event with reason code.
     */
    public void recordDisconnect(int reasonCode) {
        // Will be captured in next snapshot
        reconnectCount++;
    }
}
