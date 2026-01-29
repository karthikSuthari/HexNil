package com.example.hexnil.model;

import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * OEM-Grade Network Wi-Fi Snapshot Entity
 * 
 * Raw Wi-Fi diagnostics data capture for stability monitoring.
 * Each snapshot represents a unique point-in-time capture of Wi-Fi state.
 * 
 * CRITICAL DESIGN PRINCIPLES:
 * 1. Every snapshot MUST have a unique auto-generated ID
 * 2. SSID and BSSID are hashed for privacy (SHA-256)
 * 3. NULL means data unavailable/restricted, NEVER fabricate values
 * 4. Validation metadata enables quality filtering
 *
 * THREE-STATE SEMANTICS:
 * - Integer/String value = MEASURED/AVAILABLE
 * - null = UNAVAILABLE/RESTRICTED/NOT_SUPPORTED
 * - Do NOT use sentinel values like -1 or "N/A"
 *
 * Data Sources:
 * - WifiManager
 * - WifiInfo
 * - ConnectivityManager.NetworkCallback
 */
@Entity(tableName = "network_wifi_snapshots", indices = {
        @Index(value = { "timestamp" }),
        @Index(value = { "connectionState" }),
        @Index(value = { "validationStatus" }),
        @Index(value = { "wifiStandard" })
})
public class NetworkWifiSnapshot {

    @PrimaryKey(autoGenerate = true)
    public long id;

    // =========================================================================
    // TIMING & SEQUENCING
    // =========================================================================

    /** Wall-clock timestamp when this snapshot was captured (UTC millis). */
    public long timestamp;

    /** Monotonic sequence number for ordering within a session. */
    public long sequenceNumber;

    /** Elapsed realtime (SystemClock.elapsedRealtime) for drift-free ordering. */
    public long elapsedRealtimeMs;

    /** Session ID for grouping related snapshots. */
    public long sessionId;

    // =========================================================================
    // NETWORK IDENTITY (Privacy-Hashed)
    // =========================================================================

    /**
     * SHA-256 hash of SSID for privacy protection.
     * Raw SSID is never stored.
     */
    public String ssidHash;

    /**
     * SHA-256 hash of BSSID for privacy protection.
     * Raw BSSID is never stored.
     */
    public String bssidHash;

    // =========================================================================
    // CONNECTION PARAMETERS
    // =========================================================================

    /** Operating frequency in MHz (e.g., 2412, 5180, 6055). */
    public Integer frequency;

    /**
     * Frequency band derived from frequency.
     * Values: 2.4GHz, 5GHz, 6GHz
     */
    public String frequencyBand;

    /** Channel number. */
    public Integer channel;

    /** Link speed in Mbps from WifiInfo.getLinkSpeed(). */
    public Integer linkSpeedMbps;

    /** Transmit link speed in Mbps (Android 10+). */
    public Integer txLinkSpeedMbps;

    /** Receive link speed in Mbps (Android 10+). */
    public Integer rxLinkSpeedMbps;

    /** Maximum supported TX link speed in Mbps. */
    public Integer maxTxLinkSpeedMbps;

    /** Maximum supported RX link speed in Mbps. */
    public Integer maxRxLinkSpeedMbps;

    // =========================================================================
    // SIGNAL STRENGTH
    // =========================================================================

    /** RSSI in dBm. Range: -100 to 0 dBm typically. */
    public Integer rssi;

    /** Signal level (0-4 bars) as calculated by system. */
    public Integer signalLevel;

    // =========================================================================
    // WI-FI STANDARD & CAPABILITIES
    // =========================================================================

    /**
     * Wi-Fi standard from WifiInfo.getWifiStandard() (Android 11+).
     * Values: WIFI_STANDARD_LEGACY (1), 11N (4), 11AC (5), 11AX (6), 11AD (7), 11BE
     * (8)
     */
    public Integer wifiStandard;

    /** Human-readable Wi-Fi standard name. */
    public String wifiStandardName;

    /** Security type (WPA, WPA2, WPA3, OPEN, etc.). */
    public String securityType;

    // =========================================================================
    // CONNECTION STATE
    // =========================================================================

    /**
     * Connection state string.
     * Values: CONNECTED, CONNECTING, DISCONNECTED, AUTHENTICATING, etc.
     */
    public String connectionState;

    /** Network ID from WifiInfo.getNetworkId(). */
    public Integer networkId;

    /** Whether the connection is metered. */
    public Boolean isMetered;

    /** Whether this is a passpoint network. */
    public Boolean isPasspoint;

    // =========================================================================
    // STABILITY METRICS
    // =========================================================================

    /** Raw disconnect reason code if captured. */
    public Integer disconnectReason;

    /** Number of reconnects in current session. */
    public Integer reconnectCount;

    /** DHCP lease acquisition latency in milliseconds. */
    public Long dhcpLatencyMs;

    /** Time since last successful authentication in milliseconds. */
    public Long timeSinceAuthMs;

    /** Number of association attempts. */
    public Integer associationAttempts;

    /** Number of failed associations. */
    public Integer associationFailures;

    // =========================================================================
    // IP & NETWORK INFO
    // =========================================================================

    /** Whether an IPv4 address is assigned. */
    public Boolean hasIpv4;

    /** Whether an IPv6 address is assigned. */
    public Boolean hasIpv6;

    /** Gateway latency (ping) in milliseconds if measured. */
    public Integer gatewayLatencyMs;

    // =========================================================================
    // CONTEXT & METADATA
    // =========================================================================

    /** Context in which this snapshot was captured. */
    public String snapshotContext;

    /** Trigger type: PERIODIC, EVENT, MANUAL. */
    public String snapshotTrigger;

    /** Data source: WIFI_MANAGER, CALLBACK, INFERRED. */
    public String dataSource;

    /** Confidence score 0.0-1.0 (1.0 = high confidence). */
    public Float confidence;

    // =========================================================================
    // VALIDATION METADATA (OEM-Grade)
    // =========================================================================

    /**
     * Validation status from NetworkValidationRules.
     * Values: "valid", "warning", "invalid", "unchecked"
     */
    public String validationStatus;

    /**
     * Quality score from validation (0-100).
     * 100 = perfect, <70 = questionable, <50 = unreliable
     */
    public int qualityScore;

    /**
     * Validation flags encoded as bitmask.
     * Bit 0: rssi_in_range
     * Bit 1: frequency_valid
     * Bit 2: link_speed_valid
     * Bit 3: no_frozen_signal
     * Bit 4: no_spike_detected
     * Bit 5: timestamp_valid
     */
    public int validationFlags;

    /** Reason for validation failure or warning. */
    public String validationNotes;

    /** Indicates if signal values changed from previous snapshot. */
    public boolean signalDataChanged;

    // =========================================================================
    // CONSTRUCTORS
    // =========================================================================

    /** Default constructor - creates snapshot with fresh timestamp. */
    public NetworkWifiSnapshot() {
        this.timestamp = System.currentTimeMillis();
        this.elapsedRealtimeMs = android.os.SystemClock.elapsedRealtime();
        this.validationStatus = "unchecked";
        this.qualityScore = -1;
        this.validationFlags = 0;
        this.signalDataChanged = true;
    }

    /** Creates a snapshot with specified context. */
    @Ignore
    public NetworkWifiSnapshot(String snapshotContext) {
        this();
        this.snapshotContext = snapshotContext;
    }

    // =========================================================================
    // UTILITY METHODS
    // =========================================================================

    /**
     * Check if currently connected to Wi-Fi.
     */
    @Ignore
    public boolean isConnected() {
        return "CONNECTED".equals(connectionState);
    }

    /**
     * Check if this is a 5GHz connection.
     */
    @Ignore
    public boolean is5GHz() {
        return frequency != null && frequency >= 5000;
    }

    /**
     * Check if this is a 6GHz (Wi-Fi 6E) connection.
     */
    @Ignore
    public boolean is6GHz() {
        return frequency != null && frequency >= 5925;
    }

    /**
     * Check if this is Wi-Fi 6 (802.11ax) or newer.
     */
    @Ignore
    public boolean isWifi6OrNewer() {
        return wifiStandard != null && wifiStandard >= 6;
    }

    /**
     * Derive frequency band from frequency in MHz.
     */
    @Ignore
    public static String getFrequencyBand(Integer frequency) {
        if (frequency == null)
            return null;
        if (frequency < 3000)
            return "2.4GHz";
        if (frequency < 5925)
            return "5GHz";
        return "6GHz";
    }

    /**
     * Checks if this snapshot passes minimum quality threshold.
     */
    @Ignore
    public boolean isUsable() {
        return !"invalid".equals(validationStatus) && qualityScore >= 50;
    }

    /**
     * Checks if this snapshot has high quality data.
     */
    @Ignore
    public boolean isHighQuality() {
        return "valid".equals(validationStatus) && qualityScore >= 85;
    }

    /**
     * Sets validation result from NetworkValidationRules.
     */
    @Ignore
    public void setValidationResult(boolean valid, int score, int flags, String notes) {
        this.validationStatus = valid ? (score >= 85 ? "valid" : "warning") : "invalid";
        this.qualityScore = score;
        this.validationFlags = flags;
        this.validationNotes = notes;
    }

    /**
     * Creates a fingerprint for duplicate/frozen signal detection.
     */
    @Ignore
    public String getSignalFingerprint() {
        return (rssi != null ? rssi : "n") + ":" +
                (frequency != null ? frequency : "n") + ":" +
                (linkSpeedMbps != null ? linkSpeedMbps : "n") + ":" +
                (bssidHash != null ? bssidHash.substring(0, 8) : "n");
    }

    /**
     * Checks if signal values match another snapshot (potential frozen data).
     */
    @Ignore
    public boolean hasSameSignalAs(NetworkWifiSnapshot other) {
        if (other == null)
            return false;
        return java.util.Objects.equals(this.rssi, other.rssi) &&
                java.util.Objects.equals(this.frequency, other.frequency) &&
                java.util.Objects.equals(this.linkSpeedMbps, other.linkSpeedMbps) &&
                java.util.Objects.equals(this.bssidHash, other.bssidHash);
    }

    @Override
    public String toString() {
        return "NetworkWifiSnapshot{" +
                "id=" + id +
                ", ts=" + timestamp +
                ", rssi=" + rssi +
                ", freq=" + frequency +
                ", speed=" + linkSpeedMbps +
                ", standard=" + wifiStandardName +
                ", state=" + connectionState +
                ", quality=" + qualityScore +
                ", valid=" + validationStatus +
                '}';
    }
}
