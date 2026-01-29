package com.example.hexnil.validation;

import android.telephony.TelephonyManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.hexnil.model.NetworkBluetoothSnapshot;
import com.example.hexnil.model.NetworkCellularSnapshot;
import com.example.hexnil.model.NetworkWifiSnapshot;

/**
 * OEM-Grade Network Data Validation Rules
 * 
 * Validates raw network data for physical plausibility and data quality
 * scoring.
 * Used by collectors before database insertion.
 * 
 * VALIDATION PRINCIPLES:
 * - NEVER modify or reject raw data - only flag and score
 * - Use industry-standard ranges from 3GPP and IEEE specifications
 * - Provide actionable validation notes for debugging
 * 
 * QUALITY SCORE GUIDELINES:
 * - 90-100: High confidence, all expected fields present
 * - 70-89: Good confidence, some optional fields missing
 * - 50-69: Moderate confidence, key fields missing or borderline
 * - 30-49: Low confidence, multiple anomalies
 * - 0-29: Very low confidence, likely invalid
 */
public class NetworkValidationRules {
    private static final String TAG = "NetworkValidationRules";

    // =========================================================================
    // CELLULAR SIGNAL RANGES (3GPP specifications)
    // =========================================================================

    // RSRP (Reference Signal Received Power) - LTE
    private static final int RSRP_MIN_DBM = -140; // Very weak
    private static final int RSRP_MAX_DBM = -44; // Very strong

    // RSRQ (Reference Signal Received Quality) - LTE
    private static final int RSRQ_MIN_DB = -20; // Poor
    private static final int RSRQ_MAX_DB = -3; // Excellent

    // SINR (Signal to Interference plus Noise Ratio) - LTE/NR
    private static final int SINR_MIN_DB = -23;
    private static final int SINR_MAX_DB = 40;

    // RSSI (Received Signal Strength Indicator)
    private static final int RSSI_MIN_DBM = -113;
    private static final int RSSI_MAX_DBM = -51;

    // Generic signal dBm
    private static final int SIGNAL_MIN_DBM = -140;
    private static final int SIGNAL_MAX_DBM = -30;

    // ASU (Arbitrary Strength Unit)
    private static final int ASU_MIN = 0;
    private static final int ASU_MAX = 99;

    // PCI (Physical Cell ID) - LTE
    private static final int PCI_MIN = 0;
    private static final int PCI_MAX = 503;

    // NR PCI (5G)
    private static final int NR_PCI_MAX = 1007;

    // ARFCN/EARFCN ranges
    private static final int EARFCN_MIN = 0;
    private static final int EARFCN_MAX = 262143; // Includes NR-ARFCN

    // =========================================================================
    // WI-FI SIGNAL RANGES (IEEE 802.11 specifications)
    // =========================================================================

    private static final int WIFI_RSSI_MIN_DBM = -100;
    private static final int WIFI_RSSI_MAX_DBM = -10;

    private static final int WIFI_LINK_SPEED_MIN = 1; // Mbps
    private static final int WIFI_LINK_SPEED_MAX = 4800; // Wi-Fi 6E max

    private static final int WIFI_FREQ_2G_MIN = 2400;
    private static final int WIFI_FREQ_2G_MAX = 2500;
    private static final int WIFI_FREQ_5G_MIN = 5150;
    private static final int WIFI_FREQ_5G_MAX = 5900;
    private static final int WIFI_FREQ_6G_MIN = 5925;
    private static final int WIFI_FREQ_6G_MAX = 7125;

    // =========================================================================
    // BLUETOOTH RANGES
    // =========================================================================

    private static final int BT_RSSI_MIN_DBM = -127;
    private static final int BT_RSSI_MAX_DBM = 20;

    private static final int BT_TX_POWER_MIN = -127;
    private static final int BT_TX_POWER_MAX = 20;

    // =========================================================================
    // VALIDATION FLAGS (bitmask)
    // =========================================================================

    public static final int FLAG_NONE = 0;
    public static final int FLAG_SIGNAL_OUT_OF_RANGE = 1;
    public static final int FLAG_UNEXPECTED_NULL = 1 << 1;
    public static final int FLAG_FROZEN_DATA = 1 << 2;
    public static final int FLAG_TIMESTAMP_ANOMALY = 1 << 3;
    public static final int FLAG_MISSING_CELL_INFO = 1 << 4;
    public static final int FLAG_ROAMING_DETECTED = 1 << 5;
    public static final int FLAG_EMERGENCY_ONLY = 1 << 6;
    public static final int FLAG_NO_SERVICE = 1 << 7;
    public static final int FLAG_MISSING_WIFI_IDENTITY = 1 << 8;
    public static final int FLAG_WEAK_SIGNAL = 1 << 9;
    public static final int FLAG_BLUETOOTH_DISABLED = 1 << 10;
    public static final int FLAG_NO_AUDIO_CODEC = 1 << 11;

    // =========================================================================
    // CELLULAR VALIDATION
    // =========================================================================

    /**
     * Validate a cellular snapshot and update its validation fields.
     */
    public void validateCellular(@NonNull NetworkCellularSnapshot snapshot) {
        int flags = FLAG_NONE;
        int score = 100;
        StringBuilder notes = new StringBuilder();

        // Check RSRP range
        if (snapshot.rsrp != null) {
            if (snapshot.rsrp < RSRP_MIN_DBM || snapshot.rsrp > RSRP_MAX_DBM) {
                flags |= FLAG_SIGNAL_OUT_OF_RANGE;
                notes.append("RSRP out of range: ").append(snapshot.rsrp).append("; ");
                score -= 15;
            } else if (snapshot.rsrp < -120) {
                flags |= FLAG_WEAK_SIGNAL;
                notes.append("Very weak RSRP; ");
                score -= 5;
            }
        } else if (isLteOrNr(snapshot.radioTechnology)) {
            // RSRP expected for LTE/NR but missing
            flags |= FLAG_UNEXPECTED_NULL;
            notes.append("RSRP unavailable; ");
            score -= 10;
        }

        // Check RSRQ range
        if (snapshot.rsrq != null) {
            if (snapshot.rsrq < RSRQ_MIN_DB || snapshot.rsrq > RSRQ_MAX_DB) {
                flags |= FLAG_SIGNAL_OUT_OF_RANGE;
                notes.append("RSRQ out of range: ").append(snapshot.rsrq).append("; ");
                score -= 10;
            }
        }

        // Check SINR range
        if (snapshot.sinr != null) {
            if (snapshot.sinr < SINR_MIN_DB || snapshot.sinr > SINR_MAX_DB) {
                flags |= FLAG_SIGNAL_OUT_OF_RANGE;
                notes.append("SINR out of range: ").append(snapshot.sinr).append("; ");
                score -= 10;
            }
        }

        // Check overall signal dBm
        if (snapshot.signalStrengthDbm != null) {
            if (snapshot.signalStrengthDbm < SIGNAL_MIN_DBM ||
                    snapshot.signalStrengthDbm > SIGNAL_MAX_DBM) {
                flags |= FLAG_SIGNAL_OUT_OF_RANGE;
                notes.append("Signal dBm out of range; ");
                score -= 15;
            }
        }

        // Check PCI range
        if (snapshot.pci != null) {
            int maxPci = "NR".equals(snapshot.radioTechnology) ? NR_PCI_MAX : PCI_MAX;
            if (snapshot.pci < PCI_MIN || snapshot.pci > maxPci) {
                flags |= FLAG_SIGNAL_OUT_OF_RANGE;
                notes.append("PCI out of range; ");
                score -= 5;
            }
        }

        // Check for no service
        if (snapshot.dataRegistrationState != null &&
                snapshot.dataRegistrationState == 1) { // OUT_OF_SERVICE
            flags |= FLAG_NO_SERVICE;
            notes.append("No data service; ");
            score -= 20;
        }

        // Check for emergency only
        if (snapshot.emergencyOnly != null && snapshot.emergencyOnly) {
            flags |= FLAG_EMERGENCY_ONLY;
            notes.append("Emergency calls only; ");
            score -= 15;
        }

        // Check roaming
        if (snapshot.isRoaming != null && snapshot.isRoaming) {
            flags |= FLAG_ROAMING_DETECTED;
            notes.append("Roaming; ");
            // No score penalty - just flag
        }

        // Check for missing cell info
        if (snapshot.cellId == null && snapshot.pci == null &&
                snapshot.hasSignalData()) {
            flags |= FLAG_MISSING_CELL_INFO;
            notes.append("Cell identity unavailable; ");
            score -= 5;
        }

        // Check frozen data
        if (!snapshot.signalDataChanged) {
            flags |= FLAG_FROZEN_DATA;
            notes.append("Signal unchanged since last sample; ");
            score -= 5;
        }

        // Determine validation status
        String status;
        if (score >= 70) {
            status = "valid";
        } else if (score >= 50) {
            status = "warning";
        } else {
            status = "invalid";
        }

        // Update snapshot
        snapshot.validationFlags = flags;
        snapshot.qualityScore = Math.max(0, score);
        snapshot.validationStatus = status;
        if (notes.length() > 0) {
            snapshot.validationNotes = notes.toString().trim();
        }
    }

    // =========================================================================
    // WI-FI VALIDATION
    // =========================================================================

    /**
     * Validate a Wi-Fi snapshot and update its validation fields.
     */
    public void validateWifi(@NonNull NetworkWifiSnapshot snapshot) {
        int flags = FLAG_NONE;
        int score = 100;
        StringBuilder notes = new StringBuilder();

        // Check RSSI range
        if (snapshot.rssi != null) {
            if (snapshot.rssi < WIFI_RSSI_MIN_DBM || snapshot.rssi > WIFI_RSSI_MAX_DBM) {
                flags |= FLAG_SIGNAL_OUT_OF_RANGE;
                notes.append("RSSI out of range: ").append(snapshot.rssi).append("; ");
                score -= 20;
            } else if (snapshot.rssi < -80) {
                flags |= FLAG_WEAK_SIGNAL;
                notes.append("Weak signal; ");
                score -= 5;
            }
        } else if ("CONNECTED".equals(snapshot.connectionState)) {
            flags |= FLAG_UNEXPECTED_NULL;
            notes.append("RSSI unavailable while connected; ");
            score -= 15;
        }

        // Check link speed range
        if (snapshot.linkSpeedMbps != null) {
            if (snapshot.linkSpeedMbps < WIFI_LINK_SPEED_MIN ||
                    snapshot.linkSpeedMbps > WIFI_LINK_SPEED_MAX) {
                flags |= FLAG_SIGNAL_OUT_OF_RANGE;
                notes.append("Link speed out of range; ");
                score -= 10;
            }
        }

        // Check frequency range
        if (snapshot.frequency != null) {
            boolean validFreq = (snapshot.frequency >= WIFI_FREQ_2G_MIN && snapshot.frequency <= WIFI_FREQ_2G_MAX) ||
                    (snapshot.frequency >= WIFI_FREQ_5G_MIN && snapshot.frequency <= WIFI_FREQ_5G_MAX) ||
                    (snapshot.frequency >= WIFI_FREQ_6G_MIN && snapshot.frequency <= WIFI_FREQ_6G_MAX);
            if (!validFreq) {
                flags |= FLAG_SIGNAL_OUT_OF_RANGE;
                notes.append("Frequency out of known bands; ");
                score -= 10;
            }
        }

        // Check for missing network identity
        if ("CONNECTED".equals(snapshot.connectionState)) {
            if (snapshot.ssidHash == null && snapshot.bssidHash == null) {
                flags |= FLAG_MISSING_WIFI_IDENTITY;
                notes.append("No SSID/BSSID (location permission?); ");
                score -= 10;
            }
        }

        // Check frozen data
        if (!snapshot.signalDataChanged) {
            flags |= FLAG_FROZEN_DATA;
            notes.append("Signal unchanged; ");
            score -= 5;
        }

        // Determine validation status
        String status;
        if (score >= 70) {
            status = "valid";
        } else if (score >= 50) {
            status = "warning";
        } else {
            status = "invalid";
        }

        // Update snapshot
        snapshot.validationFlags = flags;
        snapshot.qualityScore = Math.max(0, score);
        snapshot.validationStatus = status;
        if (notes.length() > 0) {
            snapshot.validationNotes = notes.toString().trim();
        }
    }

    // =========================================================================
    // BLUETOOTH VALIDATION
    // =========================================================================

    /**
     * Validate a Bluetooth snapshot and update its validation fields.
     */
    public void validateBluetooth(@NonNull NetworkBluetoothSnapshot snapshot) {
        int flags = FLAG_NONE;
        int score = 100;
        StringBuilder notes = new StringBuilder();

        // Check if disabled
        if (snapshot.isEnabled != null && !snapshot.isEnabled) {
            flags |= FLAG_BLUETOOTH_DISABLED;
            notes.append("Bluetooth disabled; ");
            score = 50; // Limited data expected
            snapshot.validationFlags = flags;
            snapshot.qualityScore = score;
            snapshot.validationStatus = "valid";
            snapshot.validationNotes = notes.toString().trim();
            return;
        }

        // Check RSSI range
        if (snapshot.rssi != null) {
            if (snapshot.rssi < BT_RSSI_MIN_DBM || snapshot.rssi > BT_RSSI_MAX_DBM) {
                flags |= FLAG_SIGNAL_OUT_OF_RANGE;
                notes.append("BT RSSI out of range; ");
                score -= 15;
            }
        }

        // Check TX power range
        if (snapshot.txPowerLevel != null) {
            if (snapshot.txPowerLevel < BT_TX_POWER_MIN ||
                    snapshot.txPowerLevel > BT_TX_POWER_MAX) {
                flags |= FLAG_SIGNAL_OUT_OF_RANGE;
                notes.append("TX power out of range; ");
                score -= 10;
            }
        }

        // Check for A2DP without codec info
        if ("A2DP".equals(snapshot.profileType) &&
                snapshot.connectionState != null &&
                snapshot.connectionState == 2) { // CONNECTED
            if (snapshot.codecType == null) {
                flags |= FLAG_NO_AUDIO_CODEC;
                notes.append("No codec info for A2DP; ");
                score -= 5;
            }
        }

        // Determine validation status
        String status;
        if (score >= 70) {
            status = "valid";
        } else if (score >= 50) {
            status = "warning";
        } else {
            status = "invalid";
        }

        // Update snapshot
        snapshot.validationFlags = flags;
        snapshot.qualityScore = Math.max(0, score);
        snapshot.validationStatus = status;
        if (notes.length() > 0) {
            snapshot.validationNotes = notes.toString().trim();
        }
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    /**
     * Check if radio technology is LTE or NR.
     */
    private boolean isLteOrNr(@Nullable String radioTechnology) {
        return "LTE".equals(radioTechnology) || "NR".equals(radioTechnology);
    }

    /**
     * Get human-readable description of validation flags.
     */
    public static String describFlags(int flags) {
        if (flags == FLAG_NONE)
            return "NONE";

        StringBuilder sb = new StringBuilder();
        if ((flags & FLAG_SIGNAL_OUT_OF_RANGE) != 0)
            sb.append("SIGNAL_OUT_OF_RANGE ");
        if ((flags & FLAG_UNEXPECTED_NULL) != 0)
            sb.append("UNEXPECTED_NULL ");
        if ((flags & FLAG_FROZEN_DATA) != 0)
            sb.append("FROZEN_DATA ");
        if ((flags & FLAG_TIMESTAMP_ANOMALY) != 0)
            sb.append("TIMESTAMP_ANOMALY ");
        if ((flags & FLAG_MISSING_CELL_INFO) != 0)
            sb.append("MISSING_CELL_INFO ");
        if ((flags & FLAG_ROAMING_DETECTED) != 0)
            sb.append("ROAMING ");
        if ((flags & FLAG_EMERGENCY_ONLY) != 0)
            sb.append("EMERGENCY_ONLY ");
        if ((flags & FLAG_NO_SERVICE) != 0)
            sb.append("NO_SERVICE ");
        if ((flags & FLAG_MISSING_WIFI_IDENTITY) != 0)
            sb.append("MISSING_WIFI_IDENTITY ");
        if ((flags & FLAG_WEAK_SIGNAL) != 0)
            sb.append("WEAK_SIGNAL ");
        if ((flags & FLAG_BLUETOOTH_DISABLED) != 0)
            sb.append("BT_DISABLED ");
        if ((flags & FLAG_NO_AUDIO_CODEC) != 0)
            sb.append("NO_AUDIO_CODEC ");
        return sb.toString().trim();
    }
}
