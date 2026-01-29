package com.example.hexnil.model;

import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * OEM-Grade Network Cellular Snapshot Entity
 * 
 * Raw cellular/modem data capture for diagnostics and validation.
 * Each snapshot represents a unique point-in-time capture of cellular state.
 * 
 * CRITICAL DESIGN PRINCIPLES:
 * 1. Every snapshot MUST have a unique auto-generated ID
 * 2. Every snapshot MUST have a fresh timestamp (never reuse)
 * 3. Snapshots are IMMUTABLE after creation
 * 4. Validation metadata enables quality filtering
 * 5. NULL means data unavailable/restricted, NEVER fabricate values
 *
 * THREE-STATE SEMANTICS:
 * - Integer/String value = MEASURED/AVAILABLE
 * - null = UNAVAILABLE/RESTRICTED/NOT_SUPPORTED
 * - Do NOT use sentinel values like -1 or "N/A"
 *
 * Data Sources:
 * - TelephonyManager
 * - SignalStrength
 * - ServiceState
 * - Build.getRadioVersion()
 */
@Entity(tableName = "network_cellular_snapshots", indices = {
        @Index(value = { "timestamp" }),
        @Index(value = { "radioTechnology" }),
        @Index(value = { "validationStatus" }),
        @Index(value = { "snapshotContext" })
})
public class NetworkCellularSnapshot {

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
    // RADIO TECHNOLOGY
    // =========================================================================

    /**
     * Radio Access Technology type.
     * Values: LTE, NR, NR_NSA, NR_SA, UMTS, HSPA, EDGE, GSM, UNKNOWN
     */
    public String radioTechnology;

    /**
     * Network type constant from TelephonyManager.getDataNetworkType().
     * Raw integer value for precise mapping.
     */
    public Integer networkType;

    // =========================================================================
    // SIGNAL STRENGTH METRICS (all dBm/dB, null if unavailable)
    // =========================================================================

    /** Overall signal strength in dBm. */
    public Integer signalStrengthDbm;

    /** LTE/NR Reference Signal Received Power. Range: -140 to -44 dBm. */
    public Integer rsrp;

    /** LTE/NR Reference Signal Received Quality. Range: -34 to +3 dB. */
    public Integer rsrq;

    /** LTE SINR or NR SS-SINR. Range: -23 to +40 dB. */
    public Integer sinr;

    /** LTE/NR RSSI (if available separately). */
    public Integer rssi;

    /** Signal level (0-4 bars) as reported by system. */
    public Integer signalLevel;

    /** ASU (Arbitrary Strength Unit) for legacy compatibility. */
    public Integer asu;

    // =========================================================================
    // CELL IDENTITY INFORMATION
    // =========================================================================

    /** Frequency band number (e.g., Band 1, 3, 7, 41, n78). */
    public Integer band;

    /** E-ARFCN (LTE) or NR-ARFCN (5G) - channel number. */
    public Integer arfcn;

    /** Physical Cell ID. */
    public Integer pci;

    /** Cell ID (CID for LTE, NCI for NR). */
    public Long cellId;

    /** Tracking Area Code (TAC). */
    public Integer tac;

    // =========================================================================
    // CARRIER & NETWORK IDENTITY
    // =========================================================================

    /** Carrier ID from TelephonyManager.getSimCarrierId(). */
    public Integer carrierId;

    /** Mobile Country Code. */
    public String mcc;

    /** Mobile Network Code. */
    public String mnc;

    /** Network operator name (friendly name). */
    public String networkOperatorName;

    /** PLMN string (MCC+MNC). */
    public String plmn;

    // =========================================================================
    // REGISTRATION STATE
    // =========================================================================

    /** Data registration state (ServiceState constant). */
    public Integer dataRegistrationState;

    /** Voice registration state (ServiceState constant). */
    public Integer voiceRegistrationState;

    /**
     * Roaming status - explicit Boolean, NEVER 0/1 ambiguity.
     * true = roaming, false = not roaming, null = unknown
     */
    public Boolean isRoaming;

    /** Network roaming type (domestic/international). */
    public String roamingType;

    /** Emergency calls only. */
    public Boolean emergencyOnly;

    // =========================================================================
    // DATA CONNECTION STATE
    // =========================================================================

    /** Data connection state (DATA_CONNECTED, DATA_DISCONNECTED, etc.). */
    public Integer dataState;

    /** Data activity (DATA_ACTIVITY_IN, OUT, INOUT, DORMANT). */
    public Integer dataActivity;

    // =========================================================================
    // MODEM & FIRMWARE
    // =========================================================================

    /** Modem firmware version from Build.getRadioVersion(). */
    public String modemFirmwareVersion;

    /** Baseband version. */
    public String basebandVersion;

    // =========================================================================
    // CONTEXT & METADATA
    // =========================================================================

    /**
     * Context in which this snapshot was captured.
     * Values: screen_on, screen_off, idle, call, data_transfer, handover
     */
    public String snapshotContext;

    /** Trigger type: PERIODIC, EVENT, MANUAL. */
    public String snapshotTrigger;

    /** Data source: TELEPHONY_MANAGER, CALLBACK, INFERRED. */
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
     * Bit 0: rsrp_in_range
     * Bit 1: rsrq_in_range
     * Bit 2: sinr_in_range
     * Bit 3: no_frozen_signal
     * Bit 4: no_spike_detected
     * Bit 5: timestamp_valid
     * Bit 6: cell_id_valid
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
    public NetworkCellularSnapshot() {
        this.timestamp = System.currentTimeMillis();
        this.elapsedRealtimeMs = android.os.SystemClock.elapsedRealtime();
        this.validationStatus = "unchecked";
        this.qualityScore = -1;
        this.validationFlags = 0;
        this.signalDataChanged = true;
    }

    /** Creates a snapshot with specified context. */
    @Ignore
    public NetworkCellularSnapshot(String snapshotContext) {
        this();
        this.snapshotContext = snapshotContext;
    }

    // =========================================================================
    // UTILITY METHODS
    // =========================================================================

    /**
     * Check if this snapshot has usable signal data.
     * At minimum, we need either RSRP or signalStrengthDbm.
     */
    @Ignore
    public boolean hasSignalData() {
        return rsrp != null || signalStrengthDbm != null;
    }

    /**
     * Check if this is a 5G (NR) connection.
     */
    @Ignore
    public boolean is5G() {
        return radioTechnology != null &&
                (radioTechnology.startsWith("NR") || radioTechnology.equals("5G"));
    }

    /**
     * Check if this is a 4G (LTE) connection.
     */
    @Ignore
    public boolean isLTE() {
        return "LTE".equals(radioTechnology);
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
        return (rsrp != null ? rsrp : "n") + ":" +
                (rsrq != null ? rsrq : "n") + ":" +
                (sinr != null ? sinr : "n") + ":" +
                (pci != null ? pci : "n") + ":" +
                (radioTechnology != null ? radioTechnology : "n");
    }

    /**
     * Checks if signal values match another snapshot (potential frozen data).
     */
    @Ignore
    public boolean hasSameSignalAs(NetworkCellularSnapshot other) {
        if (other == null)
            return false;
        return java.util.Objects.equals(this.rsrp, other.rsrp) &&
                java.util.Objects.equals(this.rsrq, other.rsrq) &&
                java.util.Objects.equals(this.sinr, other.sinr) &&
                java.util.Objects.equals(this.signalStrengthDbm, other.signalStrengthDbm) &&
                java.util.Objects.equals(this.pci, other.pci);
    }

    @Override
    public String toString() {
        return "NetworkCellularSnapshot{" +
                "id=" + id +
                ", ts=" + timestamp +
                ", radio=" + radioTechnology +
                ", rsrp=" + rsrp +
                ", rsrq=" + rsrq +
                ", sinr=" + sinr +
                ", pci=" + pci +
                ", band=" + band +
                ", carrier=" + networkOperatorName +
                ", roaming=" + isRoaming +
                ", quality=" + qualityScore +
                ", valid=" + validationStatus +
                '}';
    }
}
