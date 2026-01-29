package com.example.hexnil.manager;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityNr;
import android.telephony.CellInfo;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoNr;
import android.telephony.CellSignalStrength;
import android.telephony.CellSignalStrengthLte;
import android.telephony.CellSignalStrengthNr;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import com.example.hexnil.model.NetworkCellularSnapshot;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * OEM-Grade Cellular Data Collector
 * 
 * Collects raw cellular/modem signal data using TelephonyManager APIs.
 * All values are stored as raw measurements without transformation.
 * 
 * API LEVEL REQUIREMENTS:
 * - Basic cellular: API 17+
 * - LTE signal details: API 26+
 * - NR (5G) signal: API 29+
 * - TelephonyDisplayInfo (5G display): API 30+
 * 
 * PERMISSION REQUIREMENTS:
 * - READ_PHONE_STATE (dangerous permission) for TelephonyManager access
 * 
 * DATA INTEGRITY PRINCIPLES:
 * - Never fabricate values - use null for unavailable data
 * - Always timestamp with System.currentTimeMillis()
 * - Log all API restrictions/failures for auditability
 */
public class CellularCollector {
    private static final String TAG = "CellularCollector";

    private final Context context;
    private final TelephonyManager telephonyManager;
    private final AtomicLong sequenceCounter = new AtomicLong(0);
    private long sessionId = 0;
    private NetworkCellularSnapshot lastSnapshot = null;

    public CellularCollector(Context context) {
        this.context = context.getApplicationContext();
        this.telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        this.sessionId = System.currentTimeMillis();
    }

    /**
     * Collect a fresh cellular snapshot with the given context.
     * 
     * @param snapshotContext Context for this capture (e.g., "screen_on", "idle")
     * @return NetworkCellularSnapshot with raw values, null if telephony
     *         unavailable
     */
    @Nullable
    public NetworkCellularSnapshot collectSnapshot(@NonNull String snapshotContext) {
        if (telephonyManager == null) {
            Log.w(TAG, "TelephonyManager unavailable");
            return null;
        }

        NetworkCellularSnapshot snapshot = new NetworkCellularSnapshot(snapshotContext);
        snapshot.sequenceNumber = sequenceCounter.incrementAndGet();
        snapshot.sessionId = sessionId;
        snapshot.dataSource = "TELEPHONY_MANAGER";
        snapshot.snapshotTrigger = "PERIODIC";

        // Collect modem firmware version (no permission required)
        collectModemInfo(snapshot);

        // Check permission before accessing sensitive telephony data
        if (!hasPhoneStatePermission()) {
            Log.w(TAG, "READ_PHONE_STATE permission denied - limited data only");
            snapshot.validationStatus = "warning";
            snapshot.validationNotes = "READ_PHONE_STATE denied";
            snapshot.qualityScore = 30;
            return snapshot;
        }

        // Collect all available data
        collectNetworkType(snapshot);
        collectSignalStrength(snapshot);
        collectServiceState(snapshot);
        collectCellInfo(snapshot);
        collectCarrierInfo(snapshot);

        // Detect frozen signal (same as previous)
        detectFrozenSignal(snapshot);

        // Set initial validation (detailed validation in NetworkValidationRules)
        if (snapshot.hasSignalData()) {
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
     * Collect modem/baseband firmware version.
     * No permission required.
     */
    private void collectModemInfo(NetworkCellularSnapshot snapshot) {
        try {
            String radioVersion = Build.getRadioVersion();
            snapshot.modemFirmwareVersion = radioVersion;
            snapshot.basebandVersion = radioVersion;
        } catch (Exception e) {
            Log.w(TAG, "Failed to get radio version", e);
            snapshot.modemFirmwareVersion = null;
        }
    }

    /**
     * Collect network type (LTE, NR, etc.).
     */
    @SuppressLint("MissingPermission")
    private void collectNetworkType(NetworkCellularSnapshot snapshot) {
        try {
            int networkType = telephonyManager.getDataNetworkType();
            snapshot.networkType = networkType;
            snapshot.radioTechnology = mapNetworkTypeToName(networkType);
        } catch (SecurityException e) {
            Log.w(TAG, "getDataNetworkType() denied", e);
        } catch (Exception e) {
            Log.w(TAG, "Failed to get network type", e);
        }
    }

    /**
     * Map TelephonyManager network type constant to readable name.
     */
    private String mapNetworkTypeToName(int networkType) {
        switch (networkType) {
            case TelephonyManager.NETWORK_TYPE_LTE:
                return "LTE";
            case TelephonyManager.NETWORK_TYPE_HSPAP:
                return "HSPA+";
            case TelephonyManager.NETWORK_TYPE_HSPA:
                return "HSPA";
            case TelephonyManager.NETWORK_TYPE_UMTS:
                return "UMTS";
            case TelephonyManager.NETWORK_TYPE_EDGE:
                return "EDGE";
            case TelephonyManager.NETWORK_TYPE_GPRS:
                return "GPRS";
            case TelephonyManager.NETWORK_TYPE_GSM:
                return "GSM";
            case TelephonyManager.NETWORK_TYPE_CDMA:
                return "CDMA";
            case TelephonyManager.NETWORK_TYPE_EVDO_0:
            case TelephonyManager.NETWORK_TYPE_EVDO_A:
            case TelephonyManager.NETWORK_TYPE_EVDO_B:
                return "EVDO";
            default:
                // API 29+ for 5G
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (networkType == TelephonyManager.NETWORK_TYPE_NR) {
                        return "NR";
                    }
                }
                return "UNKNOWN_" + networkType;
        }
    }

    /**
     * Collect signal strength using SignalStrength API.
     */
    @SuppressLint("MissingPermission")
    private void collectSignalStrength(NetworkCellularSnapshot snapshot) {
        try {
            SignalStrength signalStrength = telephonyManager.getSignalStrength();
            if (signalStrength == null) {
                Log.d(TAG, "SignalStrength is null");
                return;
            }

            // Get overall signal level (0-4)
            snapshot.signalLevel = signalStrength.getLevel();

            // API 26+: Get cell signal strengths
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                List<CellSignalStrength> strengths = signalStrength.getCellSignalStrengths();

                for (CellSignalStrength strength : strengths) {
                    if (strength instanceof CellSignalStrengthLte) {
                        collectLteSignal((CellSignalStrengthLte) strength, snapshot);
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                            strength instanceof CellSignalStrengthNr) {
                        collectNrSignal((CellSignalStrengthNr) strength, snapshot);
                    }
                }
            }

            // Fallback: overall dBm
            if (snapshot.signalStrengthDbm == null) {
                int dbm = getSignalDbmLegacy(signalStrength);
                if (dbm != Integer.MAX_VALUE) {
                    snapshot.signalStrengthDbm = dbm;
                }
            }

        } catch (SecurityException e) {
            Log.w(TAG, "getSignalStrength() denied", e);
        } catch (Exception e) {
            Log.w(TAG, "Failed to get signal strength", e);
        }
    }

    /**
     * Collect LTE-specific signal metrics.
     */
    private void collectLteSignal(CellSignalStrengthLte lte, NetworkCellularSnapshot snapshot) {
        try {
            // RSRP: Reference Signal Received Power
            int rsrp = lte.getRsrp();
            if (rsrp != CellInfo.UNAVAILABLE) {
                snapshot.rsrp = rsrp;
            }

            // RSRQ: Reference Signal Received Quality
            int rsrq = lte.getRsrq();
            if (rsrq != CellInfo.UNAVAILABLE) {
                snapshot.rsrq = rsrq;
            }

            // RSSI: Received Signal Strength Indicator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                int rssi = lte.getRssi();
                if (rssi != CellInfo.UNAVAILABLE) {
                    snapshot.rssi = rssi;
                }
            }

            // SINR (if available via reflection or derived)
            // Note: getSinr() was added in API 26 but may return UNAVAILABLE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    // Use reflection as method availability varies by OEM
                    java.lang.reflect.Method method = lte.getClass().getMethod("getRssnr");
                    Object result = method.invoke(lte);
                    if (result instanceof Integer && ((Integer) result) != CellInfo.UNAVAILABLE) {
                        snapshot.sinr = (Integer) result;
                    }
                } catch (Exception ignored) {
                    // SINR not available on this device
                }
            }

            // ASU
            snapshot.asu = lte.getAsuLevel();

            // Overall dBm
            int dbm = lte.getDbm();
            if (dbm != CellInfo.UNAVAILABLE) {
                snapshot.signalStrengthDbm = dbm;
            }

            // Update radio technology if not set
            if (snapshot.radioTechnology == null) {
                snapshot.radioTechnology = "LTE";
            }

        } catch (Exception e) {
            Log.w(TAG, "Failed to collect LTE signal", e);
        }
    }

    /**
     * Collect 5G NR-specific signal metrics (API 29+).
     */
    @SuppressLint("NewApi")
    private void collectNrSignal(CellSignalStrengthNr nr, NetworkCellularSnapshot snapshot) {
        try {
            // SS-RSRP (Secondary Synchronization Reference Signal Received Power)
            int ssRsrp = nr.getSsRsrp();
            if (ssRsrp != CellInfo.UNAVAILABLE) {
                snapshot.rsrp = ssRsrp;
            }

            // SS-RSRQ
            int ssRsrq = nr.getSsRsrq();
            if (ssRsrq != CellInfo.UNAVAILABLE) {
                snapshot.rsrq = ssRsrq;
            }

            // SS-SINR
            int ssSinr = nr.getSsSinr();
            if (ssSinr != CellInfo.UNAVAILABLE) {
                snapshot.sinr = ssSinr;
            }

            // CSI-RSRP (Channel State Information)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                int csiRsrp = nr.getCsiRsrp();
                // Store CSI-RSRP only if SS-RSRP is unavailable
                if (snapshot.rsrp == null && csiRsrp != CellInfo.UNAVAILABLE) {
                    snapshot.rsrp = csiRsrp;
                }
            }

            // Overall dBm
            int dbm = nr.getDbm();
            if (dbm != CellInfo.UNAVAILABLE) {
                snapshot.signalStrengthDbm = dbm;
            }

            // ASU
            snapshot.asu = nr.getAsuLevel();

            // Mark as 5G NR
            if (snapshot.radioTechnology == null || snapshot.radioTechnology.equals("LTE")) {
                // Could be NSA (LTE anchor) or SA
                snapshot.radioTechnology = "NR";
            }

        } catch (Exception e) {
            Log.w(TAG, "Failed to collect NR signal", e);
        }
    }

    /**
     * Legacy fallback for overall signal dBm.
     */
    private int getSignalDbmLegacy(SignalStrength signalStrength) {
        try {
            // Try API 23+ method
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Use reflection to get dbm if available
                java.lang.reflect.Method method = signalStrength.getClass().getMethod("getDbm");
                Object result = method.invoke(signalStrength);
                if (result instanceof Integer) {
                    return (Integer) result;
                }
            }
        } catch (Exception ignored) {
        }
        return Integer.MAX_VALUE;
    }

    /**
     * Collect service state (registration, roaming).
     */
    @SuppressLint("MissingPermission")
    private void collectServiceState(NetworkCellularSnapshot snapshot) {
        try {
            ServiceState serviceState = telephonyManager.getServiceState();
            if (serviceState == null) {
                Log.d(TAG, "ServiceState is null");
                return;
            }

            // Registration states - getState() returns combined voice/data state
            int state = serviceState.getState();
            snapshot.voiceRegistrationState = state;
            snapshot.dataRegistrationState = state;

            // Roaming - explicit boolean
            snapshot.isRoaming = serviceState.getRoaming();

            // Emergency only - use reflection for broader compatibility
            try {
                java.lang.reflect.Method method = serviceState.getClass().getMethod("isEmergencyOnly");
                Object result = method.invoke(serviceState);
                if (result instanceof Boolean) {
                    snapshot.emergencyOnly = (Boolean) result;
                }
            } catch (Exception e) {
                Log.d(TAG, "isEmergencyOnly() not available");
            }

            // Operator name
            snapshot.networkOperatorName = serviceState.getOperatorAlphaLong();

        } catch (SecurityException e) {
            Log.w(TAG, "getServiceState() denied", e);
        } catch (Exception e) {
            Log.w(TAG, "Failed to get service state", e);
        }
    }

    /**
     * Collect cell identity info (PCI, band, ARFCN, TAC, etc.).
     */
    @SuppressLint("MissingPermission")
    private void collectCellInfo(NetworkCellularSnapshot snapshot) {
        try {
            List<CellInfo> cellInfoList = telephonyManager.getAllCellInfo();
            if (cellInfoList == null || cellInfoList.isEmpty()) {
                Log.d(TAG, "No cell info available");
                return;
            }

            // Find the registered (serving) cell
            for (CellInfo cellInfo : cellInfoList) {
                if (!cellInfo.isRegistered())
                    continue;

                if (cellInfo instanceof CellInfoLte) {
                    collectLteCellIdentity((CellInfoLte) cellInfo, snapshot);
                    break;
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                        cellInfo instanceof CellInfoNr) {
                    collectNrCellIdentity((CellInfoNr) cellInfo, snapshot);
                    break;
                }
            }

        } catch (SecurityException e) {
            Log.w(TAG, "getAllCellInfo() denied", e);
        } catch (Exception e) {
            Log.w(TAG, "Failed to get cell info", e);
        }
    }

    /**
     * Collect LTE cell identity.
     */
    @SuppressLint("NewApi")
    private void collectLteCellIdentity(CellInfoLte cellInfo, NetworkCellularSnapshot snapshot) {
        try {
            CellIdentityLte identity = cellInfo.getCellIdentity();

            // PCI (Physical Cell ID)
            int pci = identity.getPci();
            if (pci != CellInfo.UNAVAILABLE) {
                snapshot.pci = pci;
            }

            // TAC (Tracking Area Code)
            int tac = identity.getTac();
            if (tac != CellInfo.UNAVAILABLE) {
                snapshot.tac = tac;
            }

            // Cell ID
            int ci = identity.getCi();
            if (ci != CellInfo.UNAVAILABLE) {
                snapshot.cellId = (long) ci;
            }

            // EARFCN (band)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                int earfcn = identity.getEarfcn();
                if (earfcn != CellInfo.UNAVAILABLE) {
                    snapshot.arfcn = earfcn;
                }
            }

            // Band (API 28+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                int bandwidth = identity.getBandwidth();
                // Note: getBands() is API 30+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    int[] bands = identity.getBands();
                    if (bands != null && bands.length > 0) {
                        snapshot.band = bands[0];
                    }
                }
            }

            // MCC/MNC
            String mccStr = identity.getMccString();
            String mncStr = identity.getMncString();
            if (mccStr != null)
                snapshot.mcc = mccStr;
            if (mncStr != null)
                snapshot.mnc = mncStr;
            if (mccStr != null && mncStr != null) {
                snapshot.plmn = mccStr + mncStr;
            }

        } catch (Exception e) {
            Log.w(TAG, "Failed to collect LTE cell identity", e);
        }
    }

    /**
     * Collect 5G NR cell identity (API 29+).
     */
    @SuppressLint("NewApi")
    private void collectNrCellIdentity(CellInfoNr cellInfo, NetworkCellularSnapshot snapshot) {
        try {
            CellIdentityNr identity = (CellIdentityNr) cellInfo.getCellIdentity();

            // PCI
            int pci = identity.getPci();
            if (pci != CellInfo.UNAVAILABLE) {
                snapshot.pci = pci;
            }

            // TAC
            int tac = identity.getTac();
            if (tac != CellInfo.UNAVAILABLE) {
                snapshot.tac = tac;
            }

            // NCI (NR Cell ID)
            long nci = identity.getNci();
            if (nci != CellInfo.UNAVAILABLE_LONG) {
                snapshot.cellId = nci;
            }

            // NR-ARFCN
            int nrarfcn = identity.getNrarfcn();
            if (nrarfcn != CellInfo.UNAVAILABLE) {
                snapshot.arfcn = nrarfcn;
            }

            // Band (API 30+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                int[] bands = identity.getBands();
                if (bands != null && bands.length > 0) {
                    snapshot.band = bands[0];
                }
            }

            // MCC/MNC
            String mccStr = identity.getMccString();
            String mncStr = identity.getMncString();
            if (mccStr != null)
                snapshot.mcc = mccStr;
            if (mncStr != null)
                snapshot.mnc = mncStr;
            if (mccStr != null && mncStr != null) {
                snapshot.plmn = mccStr + mncStr;
            }

        } catch (Exception e) {
            Log.w(TAG, "Failed to collect NR cell identity", e);
        }
    }

    /**
     * Collect carrier info.
     */
    @SuppressLint("MissingPermission")
    private void collectCarrierInfo(NetworkCellularSnapshot snapshot) {
        try {
            // Carrier ID (API 28+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                int carrierId = telephonyManager.getSimCarrierId();
                if (carrierId != TelephonyManager.UNKNOWN_CARRIER_ID) {
                    snapshot.carrierId = carrierId;
                }
            }

            // Data state
            snapshot.dataState = telephonyManager.getDataState();

            // Data activity
            snapshot.dataActivity = telephonyManager.getDataActivity();

            // Network operator (MCC+MNC)
            String networkOperator = telephonyManager.getNetworkOperator();
            if (networkOperator != null && networkOperator.length() >= 5) {
                if (snapshot.mcc == null) {
                    snapshot.mcc = networkOperator.substring(0, 3);
                }
                if (snapshot.mnc == null) {
                    snapshot.mnc = networkOperator.substring(3);
                }
            }

            // Operator name
            if (snapshot.networkOperatorName == null) {
                snapshot.networkOperatorName = telephonyManager.getNetworkOperatorName();
            }

        } catch (SecurityException e) {
            Log.w(TAG, "Carrier info access denied", e);
        } catch (Exception e) {
            Log.w(TAG, "Failed to get carrier info", e);
        }
    }

    /**
     * Detect frozen signal (identical to previous snapshot).
     */
    private void detectFrozenSignal(NetworkCellularSnapshot snapshot) {
        if (lastSnapshot != null) {
            snapshot.signalDataChanged = !snapshot.hasSameSignalAs(lastSnapshot);
        }
    }

    /**
     * Check if READ_PHONE_STATE permission is granted.
     */
    private boolean hasPhoneStatePermission() {
        return ActivityCompat.checkSelfPermission(context,
                Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Reset session - call when starting new monitoring session.
     */
    public void resetSession() {
        sessionId = System.currentTimeMillis();
        sequenceCounter.set(0);
        lastSnapshot = null;
    }

    /**
     * Get current session ID.
     */
    public long getSessionId() {
        return sessionId;
    }
}
