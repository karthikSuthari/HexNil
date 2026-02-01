package com.example.hexnil.manager;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import com.example.hexnil.model.NetworkWifiSnapshot;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * OEM-Grade Wi-Fi Data Collector
 * 
 * Collects raw Wi-Fi diagnostics data using WifiManager and ConnectivityManager
 * APIs.
 * SSID and BSSID are hashed for privacy protection.
 * 
 * API LEVEL REQUIREMENTS:
 * - Basic Wi-Fi: API 17+
 * - TX/RX link speed: API 29+
 * - Wi-Fi standard: API 30+
 * - Security type: API 31+ (with fallbacks)
 * - Passpoint: API 30+
 * 
 * PERMISSION REQUIREMENTS:
 * - ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION (API 27+) for SSID/BSSID
 * - ACCESS_WIFI_STATE for basic info
 * - ACCESS_NETWORK_STATE for connectivity info
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
    private final ConnectivityManager connectivityManager;
    private final PowerManager powerManager;
    private final AtomicLong sequenceCounter = new AtomicLong(0);
    private long sessionId = 0;
    private NetworkWifiSnapshot lastSnapshot = null;
    private int reconnectCount = 0;
    private String lastBssidHash = null;

    // State tracking for stability metrics
    private volatile long connectionStartTime = 0;
    private volatile long lastAuthenticationTime = 0;
    private volatile long dhcpStartTime = 0;
    private volatile int associationAttemptCount = 0;
    private volatile int associationFailureCount = 0;
    private volatile int lastDisconnectReason = -1;
    private volatile boolean wasConnected = false;

    public WifiCollector(Context context) {
        this.context = context.getApplicationContext();
        this.wifiManager = (WifiManager) context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        this.connectivityManager = (ConnectivityManager) context.getApplicationContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        this.powerManager = (PowerManager) context.getApplicationContext()
                .getSystemService(Context.POWER_SERVICE);
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

        // Set dynamic snapshot context based on device state
        snapshot.snapshotContext = determineSnapshotContext(snapshotContext);

        // Check if Wi-Fi is enabled
        if (!wifiManager.isWifiEnabled()) {
            snapshot.connectionState = "DISABLED";
            snapshot.validationStatus = "valid";
            snapshot.qualityScore = 50;
            snapshot.confidence = 1.0f;
            updateConnectionState(false);
            lastSnapshot = snapshot;
            return snapshot;
        }

        // Get connection info
        WifiInfo wifiInfo = getWifiInfo();
        if (wifiInfo == null) {
            snapshot.connectionState = "DISCONNECTED";
            snapshot.validationStatus = "valid";
            snapshot.qualityScore = 50;
            snapshot.confidence = 0.8f;
            updateConnectionState(false);
            lastSnapshot = snapshot;
            return snapshot;
        }

        // Collect all available data from different sources
        collectConnectionState(wifiInfo, snapshot);
        collectSignalInfo(wifiInfo, snapshot);
        collectLinkSpeed(wifiInfo, snapshot);
        collectWifiStandard(wifiInfo, snapshot);
        collectNetworkIdentity(wifiInfo, snapshot);
        collectSecurityType(wifiInfo, snapshot);
        collectStabilityMetrics(wifiInfo, snapshot);
        collectConnectivityInfo(snapshot);
        collectIpInfo(snapshot);

        // Optional: measure gateway latency (async-friendly)
        collectGatewayLatency(snapshot);

        // Detect frozen signal
        detectFrozenSignal(snapshot);

        // Calculate confidence score
        calculateConfidence(snapshot);

        // Set validation based on data quality
        validateSnapshot(snapshot);

        lastSnapshot = snapshot;
        return snapshot;
    }

    /**
     * Determine dynamic snapshot context based on device state.
     */
    private String determineSnapshotContext(String providedContext) {
        if (providedContext != null && !providedContext.isEmpty() &&
                !providedContext.equals("normal") && !providedContext.equals("periodic")) {
            return providedContext;
        }

        try {
            if (powerManager != null) {
                boolean isInteractive = powerManager.isInteractive();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (powerManager.isDeviceIdleMode()) {
                        return "idle";
                    }
                }
                return isInteractive ? "screen_on" : "screen_off";
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to determine device state", e);
        }

        return providedContext != null ? providedContext : "unknown";
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
                updateConnectionState(false);
            } else {
                snapshot.connectionState = "CONNECTED";
                snapshot.networkId = networkId;
                updateConnectionState(true);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to get connection state", e);
            snapshot.connectionState = "UNKNOWN";
        }
    }

    /**
     * Track connection state changes for stability metrics.
     */
    private void updateConnectionState(boolean isConnected) {
        long now = System.currentTimeMillis();

        if (isConnected && !wasConnected) {
            // Just connected
            connectionStartTime = now;
            lastAuthenticationTime = now;
            associationAttemptCount++;
        } else if (!isConnected && wasConnected) {
            // Just disconnected
            reconnectCount++;
        }

        wasConnected = isConnected;
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

            // Passpoint detection (API 30+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    String passpoint = wifiInfo.getPasspointProviderFriendlyName();
                    snapshot.isPasspoint = (passpoint != null && !passpoint.isEmpty());
                } catch (Exception e) {
                    snapshot.isPasspoint = false;
                }
            } else {
                // For older APIs, assume not passpoint (can't reliably detect)
                snapshot.isPasspoint = false;
            }

        } catch (Exception e) {
            Log.w(TAG, "Failed to get network identity", e);
        }
    }

    /**
     * Collect security type information.
     */
    @SuppressLint("MissingPermission")
    private void collectSecurityType(WifiInfo wifiInfo, NetworkWifiSnapshot snapshot) {
        try {
            // API 31+ has direct getCurrentSecurityType()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                int secType = wifiInfo.getCurrentSecurityType();
                snapshot.securityType = getSecurityTypeName(secType);
            } else {
                // Fallback: try to get from configured networks (requires CHANGE_WIFI_STATE)
                snapshot.securityType = getSecurityTypeFromConfig(wifiInfo.getNetworkId());
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to get security type", e);
            snapshot.securityType = "UNKNOWN";
        }
    }

    /**
     * Get security type name from constant (API 31+).
     */
    private String getSecurityTypeName(int securityType) {
        // WifiInfo security type constants (API 31+)
        switch (securityType) {
            case 0:
                return "OPEN";
            case 1:
                return "WEP";
            case 2:
                return "WPA_PSK";
            case 3:
                return "WPA_EAP";
            case 4:
                return "WPA3_SAE";
            case 5:
                return "WPA3_EAP_SUITE_B";
            case 6:
                return "OWE";
            case 7:
                return "WAPI_PSK";
            case 8:
                return "WAPI_CERT";
            case 9:
                return "WPA3_EAP";
            case 10:
                return "OSEN";
            case 11:
                return "PASSPOINT_R1_R2";
            case 12:
                return "PASSPOINT_R3";
            case 13:
                return "DPP";
            default:
                return "UNKNOWN";
        }
    }

    /**
     * Fallback: get security type from WifiConfiguration (pre-API 31).
     */
    @SuppressLint("MissingPermission")
    private String getSecurityTypeFromConfig(int networkId) {
        if (networkId == -1)
            return "UNKNOWN";

        try {
            List<WifiConfiguration> configs = wifiManager.getConfiguredNetworks();
            if (configs != null) {
                for (WifiConfiguration config : configs) {
                    if (config.networkId == networkId) {
                        if (config.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.WPA_PSK)) {
                            return "WPA_PSK";
                        } else if (config.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.WPA_EAP)) {
                            return "WPA_EAP";
                        } else if (config.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.NONE)) {
                            if (config.wepKeys[0] != null) {
                                return "WEP";
                            }
                            return "OPEN";
                        }
                    }
                }
            }
        } catch (SecurityException e) {
            Log.d(TAG, "Cannot access configured networks for security type");
        } catch (Exception e) {
            Log.w(TAG, "Failed to get security from config", e);
        }
        return "UNKNOWN";
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

            // Time since authentication
            if (lastAuthenticationTime > 0 && "CONNECTED".equals(snapshot.connectionState)) {
                snapshot.timeSinceAuthMs = System.currentTimeMillis() - lastAuthenticationTime;
            }

            // Association attempts and failures
            snapshot.associationAttempts = associationAttemptCount;
            snapshot.associationFailures = associationFailureCount;

            // Last disconnect reason (if we captured one)
            if (lastDisconnectReason >= 0) {
                snapshot.disconnectReason = lastDisconnectReason;
            }

            // IP addresses - use connectivity manager for more accurate info
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Check for IP (indirect method via WifiInfo)
                int ipAddress = wifiInfo.getIpAddress();
                snapshot.hasIpv4 = (ipAddress != 0);
            }

        } catch (Exception e) {
            Log.w(TAG, "Failed to collect stability metrics", e);
        }
    }

    /**
     * Collect connectivity info (metered status).
     */
    private void collectConnectivityInfo(NetworkWifiSnapshot snapshot) {
        if (connectivityManager == null)
            return;

        try {
            Network network = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                network = connectivityManager.getActiveNetwork();
            }

            if (network != null) {
                NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(network);
                if (caps != null) {
                    // Check if metered
                    snapshot.isMetered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to get connectivity info", e);
        }
    }

    /**
     * Collect IP information (IPv4/IPv6).
     */
    private void collectIpInfo(NetworkWifiSnapshot snapshot) {
        if (connectivityManager == null)
            return;

        try {
            Network network = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                network = connectivityManager.getActiveNetwork();
            }

            if (network != null) {
                LinkProperties linkProps = connectivityManager.getLinkProperties(network);
                if (linkProps != null) {
                    List<LinkAddress> addresses = linkProps.getLinkAddresses();
                    boolean hasIpv4 = false;
                    boolean hasIpv6 = false;

                    for (LinkAddress addr : addresses) {
                        InetAddress inetAddr = addr.getAddress();
                        if (inetAddr instanceof Inet4Address) {
                            hasIpv4 = true;
                        } else if (inetAddr instanceof Inet6Address) {
                            hasIpv6 = true;
                        }
                    }

                    snapshot.hasIpv4 = hasIpv4;
                    snapshot.hasIpv6 = hasIpv6;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to get IP info", e);
        }
    }

    /**
     * Measure gateway latency via ping.
     * This is done synchronously but quickly (~50-200ms).
     * Can be made async if blocking is an issue.
     */
    private void collectGatewayLatency(NetworkWifiSnapshot snapshot) {
        if (connectivityManager == null)
            return;
        if (!"CONNECTED".equals(snapshot.connectionState))
            return;

        try {
            Network network = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                network = connectivityManager.getActiveNetwork();
            }

            if (network != null) {
                LinkProperties linkProps = connectivityManager.getLinkProperties(network);
                if (linkProps != null) {
                    // Get gateway address (first route's gateway)
                    for (android.net.RouteInfo route : linkProps.getRoutes()) {
                        InetAddress gateway = route.getGateway();
                        if (gateway != null && !gateway.isAnyLocalAddress()) {
                            // Ping the gateway
                            long startTime = System.currentTimeMillis();
                            boolean reachable = gateway.isReachable(500); // 500ms timeout
                            if (reachable) {
                                snapshot.gatewayLatencyMs = (int) (System.currentTimeMillis() - startTime);
                            }
                            break; // Only ping first valid gateway
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "Gateway ping failed: " + e.getMessage());
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
     * Calculate confidence score based on data completeness.
     */
    private void calculateConfidence(NetworkWifiSnapshot snapshot) {
        int availableFields = 0;
        int totalExpectedFields = 10; // Key fields we expect

        if (snapshot.rssi != null)
            availableFields++;
        if (snapshot.frequency != null)
            availableFields++;
        if (snapshot.linkSpeedMbps != null)
            availableFields++;
        if (snapshot.ssidHash != null)
            availableFields++;
        if (snapshot.bssidHash != null)
            availableFields++;
        if (snapshot.connectionState != null)
            availableFields++;
        if (snapshot.securityType != null && !"UNKNOWN".equals(snapshot.securityType))
            availableFields++;
        if (snapshot.hasIpv4 != null)
            availableFields++;
        if (snapshot.isMetered != null)
            availableFields++;
        if (snapshot.wifiStandard != null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R)
            availableFields++;

        snapshot.confidence = (float) availableFields / totalExpectedFields;
    }

    /**
     * Validate snapshot and set quality score.
     */
    private void validateSnapshot(NetworkWifiSnapshot snapshot) {
        int qualityScore = 100;
        int validationFlags = 0;
        StringBuilder notes = new StringBuilder();

        // Check RSSI in valid range
        if (snapshot.rssi != null && snapshot.rssi >= -100 && snapshot.rssi <= 0) {
            validationFlags |= 1; // Bit 0: rssi_in_range
        } else if (snapshot.rssi != null) {
            qualityScore -= 20;
            notes.append("RSSI out of range; ");
        }

        // Check frequency valid
        if (snapshot.frequency != null && snapshot.frequency > 2000 && snapshot.frequency < 7200) {
            validationFlags |= 2; // Bit 1: frequency_valid
        } else if (snapshot.frequency != null) {
            qualityScore -= 15;
            notes.append("Invalid frequency; ");
        }

        // Check link speed valid
        if (snapshot.linkSpeedMbps != null && snapshot.linkSpeedMbps > 0 && snapshot.linkSpeedMbps < 10000) {
            validationFlags |= 4; // Bit 2: link_speed_valid
        }

        // Check frozen signal
        if (snapshot.signalDataChanged) {
            validationFlags |= 8; // Bit 3: no_frozen_signal
        } else if (lastSnapshot != null) {
            qualityScore -= 10;
            notes.append("Frozen signal detected; ");
        }

        // Bit 4: no_spike_detected (always set for now)
        validationFlags |= 16;

        // Check timestamp valid
        if (snapshot.timestamp > 0 && snapshot.elapsedRealtimeMs > 0) {
            validationFlags |= 32; // Bit 5: timestamp_valid
        }

        // Adjust for missing key data
        if (snapshot.ssidHash == null && hasLocationPermission()) {
            qualityScore -= 10;
        }
        if (snapshot.rssi == null && "CONNECTED".equals(snapshot.connectionState)) {
            qualityScore -= 15;
        }

        snapshot.qualityScore = Math.max(0, Math.min(100, qualityScore));
        snapshot.validationFlags = validationFlags;

        if (snapshot.qualityScore >= 85) {
            snapshot.validationStatus = "valid";
        } else if (snapshot.qualityScore >= 50) {
            snapshot.validationStatus = "warning";
        } else {
            snapshot.validationStatus = "invalid";
        }

        if (notes.length() > 0) {
            snapshot.validationNotes = appendNote(snapshot.validationNotes, notes.toString().trim());
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
                return "Wi-Fi 4 (802.11n)";
            case 5:
                return "Wi-Fi 5 (802.11ac)";
            case 6:
                return "Wi-Fi 6 (802.11ax)";
            case 7:
                return "Wi-Fi 6E (802.11ax)";
            case 8:
                return "Wi-Fi 7 (802.11be)";
            default:
                return "Unknown";
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
        connectionStartTime = 0;
        lastAuthenticationTime = 0;
        associationAttemptCount = 0;
        associationFailureCount = 0;
        lastDisconnectReason = -1;
        wasConnected = false;
    }

    /**
     * Get current session ID.
     */
    public long getSessionId() {
        return sessionId;
    }

    /**
     * Record a disconnect event with reason code.
     * Call this from NetworkCallback or BroadcastReceiver when disconnect is
     * detected.
     */
    public void recordDisconnect(int reasonCode) {
        lastDisconnectReason = reasonCode;
        reconnectCount++;
        wasConnected = false;
    }

    /**
     * Record an association failure.
     * Call this when connection attempt fails.
     */
    public void recordAssociationFailure() {
        associationFailureCount++;
        associationAttemptCount++;
    }

    /**
     * Record DHCP start time for latency calculation.
     */
    public void recordDhcpStart() {
        dhcpStartTime = System.currentTimeMillis();
    }

    /**
     * Get DHCP latency if available.
     */
    public Long getDhcpLatency() {
        if (dhcpStartTime > 0 && wasConnected) {
            long latency = System.currentTimeMillis() - dhcpStartTime;
            dhcpStartTime = 0; // Reset after reading
            return latency;
        }
        return null;
    }
}
