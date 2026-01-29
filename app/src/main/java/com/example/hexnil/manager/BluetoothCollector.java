package com.example.hexnil.manager;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
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
            return snapshot;
        }

        // Collect paired and connected device counts
        collectDeviceCounts(snapshot);

        // Collect A2DP profile data
        collectA2dpData(snapshot);

        // Collect HFP profile data
        collectHeadsetData(snapshot);

        // Set validation status
        snapshot.validationStatus = "unchecked";
        snapshot.qualityScore = 70;

        lastSnapshot = snapshot;
        return snapshot;
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

        } catch (SecurityException e) {
            Log.w(TAG, "Headset data access denied", e);
        } catch (Exception e) {
            Log.w(TAG, "Failed to get headset data", e);
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
