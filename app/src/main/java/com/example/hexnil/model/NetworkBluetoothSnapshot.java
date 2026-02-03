package com.example.hexnil.model;

import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * OEM-Grade Network Bluetooth Snapshot Entity
 * 
 * Raw Bluetooth protocol data capture for stability and regression tracking.
 * Each snapshot represents a unique point-in-time capture of Bluetooth state.
 * 
 * CRITICAL DESIGN PRINCIPLES:
 * 1. Every snapshot MUST have a unique auto-generated ID
 * 2. NULL means data unavailable/restricted, NEVER fabricate values
 * 3. Validation metadata enables quality filtering
 * 4. Profile-specific data only available when profile is connected
 *
 * THREE-STATE SEMANTICS:
 * - Integer/String value = MEASURED/AVAILABLE
 * - null = UNAVAILABLE/RESTRICTED/NOT_SUPPORTED
 * - Do NOT use sentinel values like -1 or "N/A"
 *
 * Data Sources:
 * - BluetoothAdapter
 * - BluetoothProfile (A2DP, HFP, HID, etc.)
 * - BluetoothDevice
 */
@Entity(tableName = "network_bluetooth_snapshots", indices = {
        @Index(value = { "timestamp" }),
        @Index(value = { "adapterState" }),
        @Index(value = { "profileType" }),
        @Index(value = { "validationStatus" })
})
public class NetworkBluetoothSnapshot {

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
    // ADAPTER STATE
    // =========================================================================

    /**
     * Bluetooth adapter state.
     * Values from BluetoothAdapter: STATE_OFF (10), STATE_TURNING_ON (11),
     * STATE_ON (12), STATE_TURNING_OFF (13)
     */
    public Integer adapterState;

    /** Human-readable adapter state name. */
    public String adapterStateName;

    /** Scan mode: NONE, CONNECTABLE, CONNECTABLE_DISCOVERABLE. */
    public Integer scanMode;

    /** Whether Bluetooth is enabled. */
    public Boolean isEnabled;

    /** Whether currently discovering devices. */
    public Boolean isDiscovering;

    // =========================================================================
    // DEVICE COUNTS
    // =========================================================================

    /** Number of bonded (paired) devices. */
    public Integer pairedDeviceCount;

    /** Number of currently connected devices. */
    public Integer connectedDeviceCount;

    // =========================================================================
    // PROFILE-SPECIFIC DATA
    // =========================================================================

    /**
     * Primary profile type for this snapshot.
     * Values: A2DP, HFP, HID, LE_AUDIO, HEADSET, PAN, MAP, PBAP, SAP, GATT
     */
    public String profileType;

    /** Profile constant from BluetoothProfile. */
    public Integer profileTypeConstant;

    /**
     * Connection state for the profile.
     * Values from BluetoothProfile: STATE_DISCONNECTED (0), STATE_CONNECTING (1),
     * STATE_CONNECTED (2), STATE_DISCONNECTING (3)
     */
    public Integer connectionState;

    /** Human-readable connection state name. */
    public String connectionStateName;

    // =========================================================================
    // A2DP-SPECIFIC (Audio)
    // =========================================================================

    /**
     * Audio codec type (A2DP only).
     * Values: SBC, AAC, aptX, aptX_HD, LDAC, LC3, OPUS, etc.
     */
    public String codecType;

    /** Codec constant from BluetoothCodecConfig. */
    public Integer codecTypeConstant;

    /** Sample rate in Hz (44100, 48000, 96000, etc.). */
    public Integer sampleRate;

    /** Bits per sample (16, 24, 32). */
    public Integer bitsPerSample;

    /** Channel mode: MONO, STEREO. */
    public String channelMode;

    /** Codec quality setting if available. */
    public Integer codecQuality;

    /** Whether currently playing audio. */
    public Boolean isPlaying;

    // =========================================================================
    // LE AUDIO-SPECIFIC
    // =========================================================================

    /** BLE connection interval in ms if LE Audio. */
    public Integer leConnectionInterval;

    /** BLE connection latency. */
    public Integer leLatency;

    // =========================================================================
    // SIGNAL & QUALITY METRICS
    // =========================================================================

    /** RSSI of connected remote device in dBm. */
    public Integer rssi;

    /** Transmit power level if available. */
    public Integer txPowerLevel;

    /** Packet error count if exposed by stack. */
    public Integer packetErrorCount;

    /** Number of reconnection attempts in session. */
    public Integer reconnectCount;

    /** Duration of current connection in milliseconds. */
    public Long connectionDurationMs;

    // =========================================================================
    // REMOTE DEVICE INFO (Hashed for Privacy)
    // =========================================================================

    /** SHA-256 hash of remote device address. */
    public String remoteAddressHash;

    /** Remote device type: CLASSIC, LE, DUAL, UNKNOWN. */
    public Integer remoteDeviceType;

    /** Remote device class (major/minor). */
    public Integer remoteDeviceClass;

    /** Remote device name (may be null if not cached). */
    public String remoteDeviceName;

    // =========================================================================
    // CONTEXT & METADATA
    // =========================================================================

    /** Context in which this snapshot was captured. */
    public String snapshotContext;

    /** Trigger type: PERIODIC, EVENT, MANUAL. */
    public String snapshotTrigger;

    /** Data source: ADAPTER, PROFILE_PROXY, CALLBACK. */
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
     */
    public int qualityScore;

    /**
     * Validation flags encoded as bitmask.
     * Bit 0: adapter_state_valid
     * Bit 1: connection_state_valid
     * Bit 2: rssi_in_range
     * Bit 3: codec_valid
     * Bit 4: timestamp_valid
     */
    public int validationFlags;

    /** Reason for validation failure or warning. */
    public String validationNotes;

    // =========================================================================
    // CONSTRUCTORS
    // =========================================================================

    /** Default constructor - creates snapshot with fresh timestamp. */
    public NetworkBluetoothSnapshot() {
        this.timestamp = System.currentTimeMillis();
        this.elapsedRealtimeMs = android.os.SystemClock.elapsedRealtime();
        this.validationStatus = "unchecked";
        this.qualityScore = -1;
        this.validationFlags = 0;
        // Default values for fields that must not be NULL
        // rssi stays null (three-state: unavailable when no device connected)
        // txPowerLevel stays null (three-state: unavailable on most devices)
        this.isPlaying = false; // Safe default - not playing audio
    }

    /** Creates a snapshot with specified context. */
    @Ignore
    public NetworkBluetoothSnapshot(String snapshotContext) {
        this();
        this.snapshotContext = snapshotContext;
    }

    // =========================================================================
    // UTILITY METHODS
    // =========================================================================

    /**
     * Check if Bluetooth adapter is on.
     */
    @Ignore
    public boolean isAdapterOn() {
        return adapterState != null && adapterState == 12; // STATE_ON
    }

    /**
     * Check if currently connected to a profile.
     */
    @Ignore
    public boolean isConnected() {
        return connectionState != null && connectionState == 2; // STATE_CONNECTED
    }

    /**
     * Check if this is an A2DP audio profile snapshot.
     */
    @Ignore
    public boolean isA2DP() {
        return "A2DP".equals(profileType);
    }

    /**
     * Check if using high-quality audio codec.
     */
    @Ignore
    public boolean isHighQualityCodec() {
        return codecType != null &&
                (codecType.contains("aptX") ||
                        codecType.equals("LDAC") ||
                        codecType.equals("LC3"));
    }

    /**
     * Get adapter state name from constant.
     */
    @Ignore
    public static String getAdapterStateName(Integer state) {
        if (state == null)
            return null;
        switch (state) {
            case 10:
                return "OFF";
            case 11:
                return "TURNING_ON";
            case 12:
                return "ON";
            case 13:
                return "TURNING_OFF";
            default:
                return "UNKNOWN";
        }
    }

    /**
     * Get connection state name from constant.
     */
    @Ignore
    public static String getConnectionStateName(Integer state) {
        if (state == null)
            return null;
        switch (state) {
            case 0:
                return "DISCONNECTED";
            case 1:
                return "CONNECTING";
            case 2:
                return "CONNECTED";
            case 3:
                return "DISCONNECTING";
            default:
                return "UNKNOWN";
        }
    }

    /**
     * Checks if this snapshot passes minimum quality threshold.
     */
    @Ignore
    public boolean isUsable() {
        return !"invalid".equals(validationStatus) && qualityScore >= 50;
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

    @Override
    public String toString() {
        return "NetworkBluetoothSnapshot{" +
                "id=" + id +
                ", ts=" + timestamp +
                ", adapter=" + adapterStateName +
                ", profile=" + profileType +
                ", state=" + connectionStateName +
                ", codec=" + codecType +
                ", rssi=" + rssi +
                ", devices=" + connectedDeviceCount +
                ", quality=" + qualityScore +
                '}';
    }
}
