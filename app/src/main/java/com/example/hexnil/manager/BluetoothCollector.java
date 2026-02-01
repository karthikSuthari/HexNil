package com.example.hexnil.manager;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import com.example.hexnil.model.NetworkBluetoothSnapshot;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

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
 * - BLUETOOTH_SCAN (API 31+) for device scanning
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
    private final PowerManager powerManager;
    private final AtomicLong sequenceCounter = new AtomicLong(0);
    private long sessionId = 0;
    private NetworkBluetoothSnapshot lastSnapshot = null;
    private int reconnectCount = 0;

    // Profile proxies (obtained asynchronously)
    private BluetoothA2dp a2dpProxy = null;
    private BluetoothHeadset headsetProxy = null;
    private long connectionStartTime = 0;

    // RSSI cache from scan results (device address hash -> RSSI)
    private final Map<String, Integer> rssiCache = new ConcurrentHashMap<>();
    private final Map<String, Long> rssiCacheTimestamp = new ConcurrentHashMap<>();
    private static final long RSSI_CACHE_VALIDITY_MS = 60000; // 1 minute

    // Connection state tracking
    private volatile String lastConnectedDeviceHash = null;
    private volatile boolean wasConnected = false;

    // Broadcast receiver for scan results (to capture RSSI)
    private BroadcastReceiver scanReceiver = null;
    private boolean receiverRegistered = false;

    public BluetoothCollector(Context context) {
        this.context = context.getApplicationContext();
        this.bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        this.bluetoothAdapter = bluetoothManager != null ? bluetoothManager.getAdapter() : null;
        this.powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        this.sessionId = System.currentTimeMillis();

        // Request profile proxies
        initProfileProxies();

        // Register scan receiver for RSSI caching
        registerScanReceiver();
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
     * Register broadcast receiver to capture RSSI from scan results.
     */
    @SuppressLint("MissingPermission")
    private void registerScanReceiver() {
        if (bluetoothAdapter == null)
            return;

        try {
            scanReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                        short rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);

                        if (device != null && rssi != Short.MIN_VALUE) {
                            String addressHash = hashSha256(device.getAddress());
                            if (addressHash != null) {
                                rssiCache.put(addressHash, (int) rssi);
                                rssiCacheTimestamp.put(addressHash, System.currentTimeMillis());
                            }
                        }
                    }
                }
            };

            IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(scanReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                context.registerReceiver(scanReceiver, filter);
            }
            receiverRegistered = true;
        } catch (Exception e) {
            Log.w(TAG, "Failed to register scan receiver", e);
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

        // Set dynamic snapshot context
        snapshot.snapshotContext = determineSnapshotContext(snapshotContext);

        // Collect adapter state (no permission required pre-S)
        collectAdapterState(snapshot);

        // Check permission for connected device info
        if (!hasBluetoothPermission()) {
            Log.w(TAG, "Bluetooth permission denied - limited data only");
            snapshot.validationStatus = "warning";
            snapshot.validationNotes = "BLUETOOTH_CONNECT denied";
            snapshot.qualityScore = 30;
            snapshot.confidence = 0.3f;
            lastSnapshot = snapshot;
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

        // Get RSSI from cache if available
        collectCachedRssi(snapshot);

        // Save reconnect count from tracked state
        snapshot.reconnectCount = reconnectCount;

        // Track connection changes
        trackConnectionChanges(snapshot);

        // Detect signal data changes
        detectSignalDataChanged(snapshot);

        // Calculate confidence score based on data completeness
        snapshot.confidence = calculateConfidence(snapshot);

        // Full validation
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
                    // Avoid double-counting if same device on both profiles
                    for (BluetoothDevice device : headsetDevices) {
                        boolean alreadyCounted = false;
                        if (a2dpProxy != null) {
                            List<BluetoothDevice> a2dpDevices = a2dpProxy.getConnectedDevices();
                            if (a2dpDevices != null && a2dpDevices.contains(device)) {
                                alreadyCounted = true;
                            }
                        }
                        if (!alreadyCounted) {
                            connectedCount++;
                        }
                    }
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
            snapshot.dataSource = "PROFILE_PROXY";

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

                    // LDAC quality via getCodecSpecific1
                    try {
                        java.lang.reflect.Method getCodecSpecific1 = codecConfig.getClass()
                                .getMethod("getCodecSpecific1");
                        Object codecSpecific1 = getCodecSpecific1.invoke(codecConfig);
                        if (codecSpecific1 instanceof Long) {
                            long specific1 = (Long) codecSpecific1;
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
            snapshot.dataSource = "PROFILE_PROXY";

            // Hash device address
            String address = device.getAddress();
            if (address != null) {
                snapshot.remoteAddressHash = hashSha256(address);
            }

            snapshot.remoteDeviceName = device.getName();
            snapshot.remoteDeviceType = device.getType();

            if (device.getBluetoothClass() != null) {
                snapshot.remoteDeviceClass = device.getBluetoothClass().getDeviceClass();
            }

            // Connection duration
            if (connectionStartTime > 0) {
                snapshot.connectionDurationMs = System.currentTimeMillis() - connectionStartTime;
            }

        } catch (SecurityException e) {
            Log.w(TAG, "Headset data access denied", e);
        } catch (Exception e) {
            Log.w(TAG, "Failed to get headset data", e);
        }
    }

    /**
     * Get RSSI from cache if available and valid.
     */
    private void collectCachedRssi(NetworkBluetoothSnapshot snapshot) {
        if (snapshot.remoteAddressHash == null)
            return;

        String hash = snapshot.remoteAddressHash;
        Long timestamp = rssiCacheTimestamp.get(hash);

        if (timestamp != null && System.currentTimeMillis() - timestamp < RSSI_CACHE_VALIDITY_MS) {
            Integer cachedRssi = rssiCache.get(hash);
            if (cachedRssi != null) {
                snapshot.rssi = cachedRssi;
            }
        }
    }

    /**
     * Collect LE Audio / BLE specific data.
     */
    @SuppressLint("MissingPermission")
    private void collectLeAudioData(NetworkBluetoothSnapshot snapshot) {
        // LE Audio support requires API 33+
        if (Build.VERSION.SDK_INT < 33) {
            return;
        }

        try {
            // For connected LE devices, mark profile type
            if (snapshot.remoteDeviceType != null &&
                    (snapshot.remoteDeviceType == BluetoothDevice.DEVICE_TYPE_LE ||
                            snapshot.remoteDeviceType == BluetoothDevice.DEVICE_TYPE_DUAL)) {

                if (snapshot.profileType == null && snapshot.connectionState != null &&
                        snapshot.connectionState == BluetoothProfile.STATE_CONNECTED) {
                    snapshot.profileType = "LE_AUDIO";
                    snapshot.profileTypeConstant = 22; // BluetoothProfile.LE_AUDIO
                }

                // Note: leConnectionInterval and leLatency require active GATT connection
                // with onConnectionUpdatedCallback - left null per design decision
            }

        } catch (Exception e) {
            Log.d(TAG, "LE Audio data collection not available: " + e.getMessage());
        }
    }

    /**
     * Track connection state changes for reconnect counting.
     */
    private void trackConnectionChanges(NetworkBluetoothSnapshot snapshot) {
        boolean isNowConnected = snapshot.connectionState != null &&
                snapshot.connectionState == BluetoothProfile.STATE_CONNECTED;
        String currentDeviceHash = snapshot.remoteAddressHash;

        if (isNowConnected && !wasConnected) {
            // Just connected
            connectionStartTime = System.currentTimeMillis();
        } else if (!isNowConnected && wasConnected) {
            // Just disconnected
            reconnectCount++;
        } else if (isNowConnected && currentDeviceHash != null &&
                lastConnectedDeviceHash != null &&
                !currentDeviceHash.equals(lastConnectedDeviceHash)) {
            // Connected to different device
            connectionStartTime = System.currentTimeMillis();
        }

        wasConnected = isNowConnected;
        lastConnectedDeviceHash = currentDeviceHash;
    }

    /**
     * Detect if signal data has changed since last snapshot.
     */
    private void detectSignalDataChanged(NetworkBluetoothSnapshot snapshot) {
        if (lastSnapshot == null) {
            return; // No previous snapshot to compare
        }

        boolean changed = false;

        // Compare key signal fields
        if (!equals(snapshot.rssi, lastSnapshot.rssi))
            changed = true;
        if (!equals(snapshot.adapterState, lastSnapshot.adapterState))
            changed = true;
        if (!equals(snapshot.connectionState, lastSnapshot.connectionState))
            changed = true;
        if (!equals(snapshot.connectedDeviceCount, lastSnapshot.connectedDeviceCount))
            changed = true;
        if (!equals(snapshot.isPlaying, lastSnapshot.isPlaying))
            changed = true;

        // Note: NetworkBluetoothSnapshot doesn't have signalDataChanged field
        // We encode this in validation notes instead
        if (!changed) {
            snapshot.validationNotes = appendNote(snapshot.validationNotes, "Signal unchanged");
        }
    }

    /**
     * Null-safe equals check.
     */
    private boolean equals(Object a, Object b) {
        if (a == null && b == null)
            return true;
        if (a == null || b == null)
            return false;
        return a.equals(b);
    }

    /**
     * Calculate confidence score based on data completeness (0.0-1.0).
     */
    private float calculateConfidence(NetworkBluetoothSnapshot snapshot) {
        int dataPoints = 0;
        int totalPossible = 12;

        if (snapshot.adapterState != null)
            dataPoints++;
        if (snapshot.isEnabled != null)
            dataPoints++;
        if (snapshot.scanMode != null)
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
        if (snapshot.remoteDeviceName != null)
            dataPoints++;
        if (snapshot.rssi != null)
            dataPoints++;
        if (snapshot.codecType != null)
            dataPoints++;
        if (snapshot.connectionDurationMs != null)
            dataPoints++;

        return (float) dataPoints / totalPossible;
    }

    /**
     * Full validation of snapshot.
     */
    private void validateSnapshot(NetworkBluetoothSnapshot snapshot) {
        int qualityScore = 50; // Base score
        int validationFlags = 0;
        StringBuilder notes = new StringBuilder();

        // Bit 0: adapter_state_valid
        if (snapshot.adapterState != null && snapshot.adapterState >= 10 && snapshot.adapterState <= 13) {
            validationFlags |= 1;
            qualityScore += 10;
        }

        // Bit 1: connection_state_valid
        if (snapshot.connectionState != null && snapshot.connectionState >= 0 && snapshot.connectionState <= 3) {
            validationFlags |= 2;
            qualityScore += 10;
        }

        // Bit 2: rssi_in_range
        if (snapshot.rssi != null && snapshot.rssi >= -100 && snapshot.rssi <= 0) {
            validationFlags |= 4;
            qualityScore += 10;
        } else if (snapshot.rssi != null) {
            notes.append("RSSI out of range; ");
        }

        // Bit 3: codec_valid (only for A2DP)
        if ("A2DP".equals(snapshot.profileType)) {
            if (snapshot.codecType != null && !snapshot.codecType.startsWith("UNKNOWN")) {
                validationFlags |= 8;
                qualityScore += 10;
            } else {
                notes.append("Codec info unavailable; ");
            }
        }

        // Bit 4: timestamp_valid
        if (snapshot.timestamp > 0 && snapshot.elapsedRealtimeMs > 0) {
            validationFlags |= 16;
            qualityScore += 5;
        }

        // Device counts available
        if (snapshot.pairedDeviceCount != null)
            qualityScore += 3;
        if (snapshot.connectedDeviceCount != null)
            qualityScore += 2;

        // Remote device info
        if (snapshot.remoteAddressHash != null)
            qualityScore += 5;
        if (snapshot.remoteDeviceName != null)
            qualityScore += 5;

        // Cap score at 100
        snapshot.qualityScore = Math.min(100, qualityScore);
        snapshot.validationFlags = validationFlags;

        // Set validation status
        if (snapshot.qualityScore >= 85) {
            snapshot.validationStatus = "valid";
        } else if (snapshot.qualityScore >= 50) {
            snapshot.validationStatus = "warning";
        } else {
            snapshot.validationStatus = "invalid";
        }

        // Append notes
        if (notes.length() > 0) {
            snapshot.validationNotes = appendNote(snapshot.validationNotes, notes.toString().trim());
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
        if (input == null)
            return null;
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
        wasConnected = true;
    }

    /**
     * Record a disconnection event.
     */
    public void onDeviceDisconnected() {
        reconnectCount++;
        connectionStartTime = 0;
        wasConnected = false;
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
        wasConnected = false;
        lastConnectedDeviceHash = null;
        rssiCache.clear();
        rssiCacheTimestamp.clear();
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
        // Unregister scan receiver
        if (receiverRegistered && scanReceiver != null) {
            try {
                context.unregisterReceiver(scanReceiver);
            } catch (Exception e) {
                Log.w(TAG, "Failed to unregister scan receiver", e);
            }
            receiverRegistered = false;
        }

        // Close profile proxies
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
