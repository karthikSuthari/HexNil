package com.example.hexnil.manager;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import com.example.hexnil.model.NetworkBluetoothSnapshot;
import java.util.concurrent.atomic.AtomicLong;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Set;
import java.lang.reflect.Method;

/**
 * OEM-Grade Bluetooth Data Collector
 * 
 * Collects raw Bluetooth adapter and profile data for stability tracking.
 * Device addresses are hashed for privacy protection.
 * 
 * API LEVEL REQUIREMENTS:
 * - BluetoothManager: API 18+
 * - Bluetooth LE: API 21+
 * - BluetoothCodecConfig: API 26+
 * 
 * PERMISSION REQUIREMENTS:
 * - BLUETOOTH for basic info (API < 31)
 * - BLUETOOTH_CONNECT (API 31+) for connected devices
 * 
 * DATA INTEGRITY PRINCIPLES:
 * - Hash device addresses before storage (SHA-256)
 * - Never fabricate values - use null for unavailable data
 * - Profile-specific data only available when profile connected
 */
public class BluetoothCollector {
    private static final String TAG = "BluetoothCollector";

    private final Context context;
    private final BluetoothManager bluetoothManager;
    private final BluetoothAdapter bluetoothAdapter;
    private final AtomicLong sequenceCounter = new AtomicLong(0);
    private long sessionId = 0;
    private NetworkBluetoothSnapshot lastSnapshot = null;
    private int reconnectCount = 0;

    // Profile proxies (obtained asynchronously)
    private BluetoothA2dp a2dpProxy = null;
    private BluetoothHeadset headsetProxy = null;
    private long connectionStartTime = 0;

    public BluetoothCollector(Context context) {
        this.context = context.getApplicationContext();
        this.bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        this.bluetoothAdapter = bluetoothManager != null ? bluetoothManager.getAdapter() : null;
        this.sessionId = System.currentTimeMillis();

        // Request profile proxies
        initProfileProxies();
    }

    /**
     * Initialize Bluetooth profile proxies.
     */
    private void initProfileProxies() {
        if (bluetoothAdapter == null)
            return;

        try {
            bluetoothAdapter.getProfileProxy(context, new BluetoothProfile.ServiceListener() {
                @Override
                public void onServiceConnected(int profile, BluetoothProfile proxy) {
                    if (profile == BluetoothProfile.A2DP) {
                        a2dpProxy = (BluetoothA2dp) proxy;
                    }
                }

                @Override
                public void onServiceDisconnected(int profile) {
                    if (profile == BluetoothProfile.A2DP) {
                        a2dpProxy = null;
                    }
                }
            }, BluetoothProfile.A2DP);

            bluetoothAdapter.getProfileProxy(context, new BluetoothProfile.ServiceListener() {
                @Override
                public void onServiceConnected(int profile, BluetoothProfile proxy) {
                    if (profile == BluetoothProfile.HEADSET) {
                        headsetProxy = (BluetoothHeadset) proxy;
                    }
                }

                @Override
                public void onServiceDisconnected(int profile) {
                    if (profile == BluetoothProfile.HEADSET) {
                        headsetProxy = null;
                    }
                }
            }, BluetoothProfile.HEADSET);
        } catch (Exception e) {
            Log.w(TAG, "Failed to init profile proxies", e);
        }
    }

    /**
     * Collect a fresh Bluetooth snapshot with the given context.
     * 
     * @param snapshotContext Context for this capture (e.g., "screen_on", "idle")
     * @return NetworkBluetoothSnapshot with raw values, null if Bluetooth
     *         unavailable
     */
    @Nullable
    @SuppressLint("MissingPermission")
    public NetworkBluetoothSnapshot collectSnapshot(@NonNull String snapshotContext) {
        if (bluetoothAdapter == null) {
            Log.w(TAG, "BluetoothAdapter unavailable");
            return null;
        }

        NetworkBluetoothSnapshot snapshot = new NetworkBluetoothSnapshot(snapshotContext);
        snapshot.sequenceNumber = sequenceCounter.incrementAndGet();
        snapshot.sessionId = sessionId;
        snapshot.dataSource = "ADAPTER";
        snapshot.snapshotTrigger = "PERIODIC";

        // Collect adapter state (no permission required pre-S)
        collectAdapterState(snapshot);

        // Check permission for connected device info
        if (!hasBluetoothPermission()) {
            Log.w(TAG, "Bluetooth permission denied - limited data only");
            snapshot.validationStatus = "warning";
            snapshot.validationNotes = "BLUETOOTH_CONNECT denied";
            snapshot.qualityScore = 30;
            snapshot.confidence = 0.3f;
            return snapshot;
        }

        // Collect paired and connected device counts
        collectDeviceCounts(snapshot);

        // Collect A2DP profile data
        collectA2dpData(snapshot);

        // Collect HFP profile data
        collectHeadsetData(snapshot);

        // Collect BLE/LE Audio data if available
        collectLeAudioData(snapshot);

        // Save reconnect count from tracked state
        snapshot.reconnectCount = reconnectCount;

        // Calculate confidence score based on data completeness
        snapshot.confidence = calculateConfidence(snapshot);

        // Set validation status and quality score
        snapshot.validationStatus = "unchecked";
        snapshot.qualityScore = calculateQualityScore(snapshot);

        lastSnapshot = snapshot;
        return snapshot;
    }

    /**
     * Calculate confidence score based on data completeness (0.0-1.0).
     */
    private float calculateConfidence(NetworkBluetoothSnapshot snapshot) {
        int dataPoints = 0;
        int totalPossible = 10;

        if (snapshot.adapterState != null)
            dataPoints++;
        if (snapshot.isEnabled != null)
            dataPoints++;
        if (snapshot.pairedDeviceCount != null)
            dataPoints++;
        if (snapshot.connectedDeviceCount != null)
            dataPoints++;
        if (snapshot.profileType != null)
            dataPoints++;
        if (snapshot.connectionState != null)
            dataPoints++;
        if (snapshot.remoteAddressHash != null)
            dataPoints++;
        if (snapshot.rssi != null)
            dataPoints++;
        if (snapshot.codecType != null)
            dataPoints++;
        if (snapshot.remoteDeviceName != null)
            dataPoints++;

        return (float) dataPoints / totalPossible;
    }

    /**
     * Calculate quality score based on data validity (0-100).
     */
    private int calculateQualityScore(NetworkBluetoothSnapshot snapshot) {
        int score = 50; // Base score

        // Adapter state available
        if (snapshot.adapterState != null)
            score += 10;

        // Device counts available
        if (snapshot.pairedDeviceCount != null)
            score += 5;
        if (snapshot.connectedDeviceCount != null)
            score += 5;

        // Profile data available
        if (snapshot.profileType != null)
            score += 10;
        if (snapshot.connectionState != null)
            score += 5;

        // Remote device info
        if (snapshot.remoteAddressHash != null)
            score += 5;
        if (snapshot.rssi != null)
            score += 5;

        // Audio codec info (premium data)
        if (snapshot.codecType != null)
            score += 5;

        return Math.min(100, score);
    }

    /**
     * Collect adapter state.
     */
    @SuppressLint("MissingPermission")
    private void collectAdapterState(NetworkBluetoothSnapshot snapshot) {
        try {
            int state = bluetoothAdapter.getState();
            snapshot.adapterState = state;
            snapshot.adapterStateName = NetworkBluetoothSnapshot.getAdapterStateName(state);
            snapshot.isEnabled = bluetoothAdapter.isEnabled();

            if (hasBluetoothPermission()) {
                snapshot.scanMode = bluetoothAdapter.getScanMode();
                snapshot.isDiscovering = bluetoothAdapter.isDiscovering();
            }

        } catch (SecurityException e) {
            Log.w(TAG, "Adapter state access denied", e);
        } catch (Exception e) {
            Log.w(TAG, "Failed to get adapter state", e);
        }
    }

    /**
     * Collect paired and connected device counts.
     */
    @SuppressLint("MissingPermission")
    private void collectDeviceCounts(NetworkBluetoothSnapshot snapshot) {
        try {
            // Paired devices
            Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();
            if (bondedDevices != null) {
                snapshot.pairedDeviceCount = bondedDevices.size();
            }

            // Connected devices count (from profiles)
            int connectedCount = 0;

            if (a2dpProxy != null) {
                List<BluetoothDevice> a2dpDevices = a2dpProxy.getConnectedDevices();
                if (a2dpDevices != null) {
                    connectedCount += a2dpDevices.size();
                }
            }

            if (headsetProxy != null) {
                List<BluetoothDevice> headsetDevices = headsetProxy.getConnectedDevices();
                if (headsetDevices != null) {
                    connectedCount += headsetDevices.size();
                }
            }

            snapshot.connectedDeviceCount = connectedCount;

        } catch (SecurityException e) {
            Log.w(TAG, "Device count access denied", e);
        } catch (Exception e) {
            Log.w(TAG, "Failed to get device counts", e);
        }
    }

    /**
     * Collect A2DP profile data.
     */
    @SuppressLint("MissingPermission")
    private void collectA2dpData(NetworkBluetoothSnapshot snapshot) {
        if (a2dpProxy == null)
            return;

        try {
            List<BluetoothDevice> connectedDevices = a2dpProxy.getConnectedDevices();
            if (connectedDevices == null || connectedDevices.isEmpty())
                return;

            BluetoothDevice device = connectedDevices.get(0);
            snapshot.profileType = "A2DP";
            snapshot.profileTypeConstant = BluetoothProfile.A2DP;
            snapshot.connectionState = a2dpProxy.getConnectionState(device);
            snapshot.connectionStateName = NetworkBluetoothSnapshot.getConnectionStateName(snapshot.connectionState);

            // Hash device address
            String address = device.getAddress();
            if (address != null) {
                snapshot.remoteAddressHash = hashSha256(address);
            }

            // Device name
            snapshot.remoteDeviceName = device.getName();

            // Device type
            snapshot.remoteDeviceType = device.getType();

            // Device class
            if (device.getBluetoothClass() != null) {
                snapshot.remoteDeviceClass = device.getBluetoothClass().getDeviceClass();
            }

            // Collect RSSI for connected device
            collectDeviceRssi(device, snapshot);

            // Codec info (API 26+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                collectA2dpCodecInfo(device, snapshot);
            }

            // Playing state
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                snapshot.isPlaying = a2dpProxy.isA2dpPlaying(device);
            }

            // Connection duration
            if (connectionStartTime > 0) {
                snapshot.connectionDurationMs = System.currentTimeMillis() - connectionStartTime;
            }

        } catch (SecurityException e) {
            Log.w(TAG, "A2DP data access denied", e);
        } catch (Exception e) {
            Log.w(TAG, "Failed to get A2DP data", e);
        }
    }

    /**
     * Collect A2DP codec configuration (API 26+).
     */
    @SuppressLint("NewApi")
    private void collectA2dpCodecInfo(BluetoothDevice device, NetworkBluetoothSnapshot snapshot) {
        try {
            // BluetoothCodecStatus and BluetoothCodecConfig are available via reflection
            // as the API is @SystemApi on some versions

            java.lang.reflect.Method getCodecStatus = a2dpProxy.getClass()
                    .getMethod("getCodecStatus", BluetoothDevice.class);
            Object codecStatus = getCodecStatus.invoke(a2dpProxy, device);

            if (codecStatus != null) {
                java.lang.reflect.Method getCodecConfig = codecStatus.getClass()
                        .getMethod("getCodecConfig");
                Object codecConfig = getCodecConfig.invoke(codecStatus);

                if (codecConfig != null) {
                    java.lang.reflect.Method getCodecType = codecConfig.getClass()
                            .getMethod("getCodecType");
                    Object codecType = getCodecType.invoke(codecConfig);
                    if (codecType instanceof Integer) {
                        snapshot.codecTypeConstant = (Integer) codecType;
                        snapshot.codecType = mapCodecTypeToName((Integer) codecType);
                    }

                    java.lang.reflect.Method getSampleRate = codecConfig.getClass()
                            .getMethod("getSampleRate");
                    Object sampleRate = getSampleRate.invoke(codecConfig);
                    if (sampleRate instanceof Integer) {
                        snapshot.sampleRate = mapSampleRateToHz((Integer) sampleRate);
                    }

                    java.lang.reflect.Method getBitsPerSample = codecConfig.getClass()
                            .getMethod("getBitsPerSample");
                    Object bitsPerSample = getBitsPerSample.invoke(codecConfig);
                    if (bitsPerSample instanceof Integer) {
                        snapshot.bitsPerSample = mapBitsPerSample((Integer) bitsPerSample);
                    }

                    java.lang.reflect.Method getChannelMode = codecConfig.getClass()
                            .getMethod("getChannelMode");
                    Object channelMode = getChannelMode.invoke(codecConfig);
                    if (channelMode instanceof Integer) {
                        snapshot.channelMode = mapChannelMode((Integer) channelMode);
                    }

                    // Try to get codec quality/priority via getCodecSpecific1
                    // LDAC uses this for quality setting: 0=Best Effort, 1=Optimized, 2=Hi-Res
                    try {
                        java.lang.reflect.Method getCodecSpecific1 = codecConfig.getClass()
                                .getMethod("getCodecSpecific1");
                        Object codecSpecific1 = getCodecSpecific1.invoke(codecConfig);
                        if (codecSpecific1 instanceof Long) {
                            long specific1 = (Long) codecSpecific1;
                            // Extract quality bits (varies by codec)
                            // For LDAC: bits 0-2 represent quality
                            // For aptX HD: usually 0
                            if (specific1 > 0 && specific1 < 100) {
                                snapshot.codecQuality = (int) specific1;
                            }
                        }
                    } catch (NoSuchMethodException nsme) {
                        // getCodecSpecific1 not available
                    }
                }
            }
        } catch (Exception e) {
            // Expected on devices where codec API is restricted
            Log.d(TAG, "Codec info not available: " + e.getMessage());
        }
    }

    /**
     * Collect HFP/Headset profile data.
     */
    @SuppressLint("MissingPermission")
    private void collectHeadsetData(NetworkBluetoothSnapshot snapshot) {
        if (headsetProxy == null)
            return;
        // If A2DP data already collected, skip HFP to avoid overwriting
        if (snapshot.profileType != null)
            return;

        try {
            List<BluetoothDevice> connectedDevices = headsetProxy.getConnectedDevices();
            if (connectedDevices == null || connectedDevices.isEmpty())
                return;

            BluetoothDevice device = connectedDevices.get(0);
            snapshot.profileType = "HFP";
            snapshot.profileTypeConstant = BluetoothProfile.HEADSET;
            snapshot.connectionState = headsetProxy.getConnectionState(device);
            snapshot.connectionStateName = NetworkBluetoothSnapshot.getConnectionStateName(snapshot.connectionState);

            // Hash device address
            String address = device.getAddress();
            if (address != null) {
                snapshot.remoteAddressHash = hashSha256(address);
            }

            snapshot.remoteDeviceName = device.getName();
            snapshot.remoteDeviceType = device.getType();

            // Collect RSSI for connected device
            collectDeviceRssi(device, snapshot);

        } catch (SecurityException e) {
            Log.w(TAG, "Headset data access denied", e);
        } catch (Exception e) {
            Log.w(TAG, "Failed to get headset data", e);
        }
    }

    /**
     * Collect RSSI (signal strength) for a connected Bluetooth device.
     * Uses reflection as readRemoteRssi is primarily for BLE devices.
     */
    @SuppressLint("MissingPermission")
    private void collectDeviceRssi(BluetoothDevice device, NetworkBluetoothSnapshot snapshot) {
        if (device == null)
            return;

        try {
            // For classic Bluetooth, RSSI is not directly available after connection
            // We can try to get it via reflection on BluetoothSocket or during scanning
            // For LE devices, we need an active GATT connection

            // Try to get TX power level via reflection (if available)
            try {
                Method getTxPowerLevel = device.getClass().getMethod("getTxPowerLevel");
                Object result = getTxPowerLevel.invoke(device);
                if (result instanceof Integer) {
                    snapshot.txPowerLevel = (Integer) result;
                }
            } catch (NoSuchMethodException e) {
                // Method not available on this device/API level
            }

            // Note: For accurate RSSI, we would need:
            // 1. BLE: Active GATT connection with readRemoteRssi callback
            // 2. Classic: Inquiry scan results (only during discovery)
            // Setting to null to indicate unavailable (per three-state semantics)
            // Future enhancement: Register BLE callback for continuous RSSI monitoring

        } catch (Exception e) {
            Log.d(TAG, "RSSI collection not available: " + e.getMessage());
        }
    }

    /**
     * Collect LE Audio / BLE specific data.
     * Includes connection parameters like interval and latency.
     */
    @SuppressLint("MissingPermission")
    private void collectLeAudioData(NetworkBluetoothSnapshot snapshot) {
        // LE Audio support requires API 33+
        if (Build.VERSION.SDK_INT < 33) {
            return;
        }

        try {
            // Check for LE Audio profile proxy
            // Note: BluetoothLeAudio is @SystemApi on many devices
            // Connection interval and latency are only available via GATT callbacks
            // during an active BLE connection

            // For connected LE devices, try to get connection parameters
            if (snapshot.remoteDeviceType != null &&
                    (snapshot.remoteDeviceType == BluetoothDevice.DEVICE_TYPE_LE ||
                            snapshot.remoteDeviceType == BluetoothDevice.DEVICE_TYPE_DUAL)) {

                // Mark profile type as LE_AUDIO if not already set and device is LE
                if (snapshot.profileType == null && snapshot.connectionState != null &&
                        snapshot.connectionState == BluetoothProfile.STATE_CONNECTED) {
                    snapshot.profileType = "LE_AUDIO";
                    snapshot.profileTypeConstant = 22; // BluetoothProfile.LE_AUDIO
                }

                // Note: Actual connection interval/latency requires:
                // 1. Establishing a GATT connection
                // 2. Receiving onConnectionUpdatedCallback with interval, latency, timeout
                // This would require significant refactoring to maintain GATT connections
                // For now, these remain null indicating "not available"
            }

        } catch (Exception e) {
            Log.d(TAG, "LE Audio data collection not available: " + e.getMessage());
        }
    }

    /**
     * Map codec type constant to name.
     */
    private String mapCodecTypeToName(int codecType) {
        switch (codecType) {
            case 0:
                return "SBC";
            case 1:
                return "AAC";
            case 2:
                return "aptX";
            case 3:
                return "aptX_HD";
            case 4:
                return "LDAC";
            case 5:
                return "LC3";
            case 6:
                return "OPUS";
            default:
                return "UNKNOWN_" + codecType;
        }
    }

    /**
     * Map sample rate constant to Hz.
     */
    private int mapSampleRateToHz(int sampleRate) {
        switch (sampleRate) {
            case 0x01:
                return 44100;
            case 0x02:
                return 48000;
            case 0x04:
                return 88200;
            case 0x08:
                return 96000;
            case 0x10:
                return 176400;
            case 0x20:
                return 192000;
            default:
                return sampleRate;
        }
    }

    /**
     * Map bits per sample constant.
     */
    private int mapBitsPerSample(int bits) {
        switch (bits) {
            case 0x01:
                return 16;
            case 0x02:
                return 24;
            case 0x04:
                return 32;
            default:
                return bits;
        }
    }

    /**
     * Map channel mode constant.
     */
    private String mapChannelMode(int mode) {
        switch (mode) {
            case 0x01:
                return "MONO";
            case 0x02:
                return "STEREO";
            default:
                return "UNKNOWN";
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
     * Check if Bluetooth permission is granted.
     */
    private boolean hasBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ActivityCompat.checkSelfPermission(context,
                    Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ActivityCompat.checkSelfPermission(context,
                    Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED;
        }
    }

    /**
     * Record a connection event.
     */
    public void onDeviceConnected() {
        connectionStartTime = System.currentTimeMillis();
    }

    /**
     * Record a disconnection event.
     */
    public void onDeviceDisconnected() {
        reconnectCount++;
        connectionStartTime = 0;
    }

    /**
     * Reset session - call when starting new monitoring session.
     */
    public void resetSession() {
        sessionId = System.currentTimeMillis();
        sequenceCounter.set(0);
        lastSnapshot = null;
        reconnectCount = 0;
        connectionStartTime = 0;
    }

    /**
     * Get current session ID.
     */
    public long getSessionId() {
        return sessionId;
    }

    /**
     * Cleanup - call when collector is no longer needed.
     */
    public void cleanup() {
        if (bluetoothAdapter != null) {
            if (a2dpProxy != null) {
                bluetoothAdapter.closeProfileProxy(BluetoothProfile.A2DP, a2dpProxy);
                a2dpProxy = null;
            }
            if (headsetProxy != null) {
                bluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, headsetProxy);
                headsetProxy = null;
            }
        }
    }
}
