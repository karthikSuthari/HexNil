package com.example.hexnil.manager;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.JsonWriter;
import android.util.Log;

import com.example.hexnil.model.BatterySnapshot;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;

/**
 * Lightweight snapshot collector that maps BatteryManager readings into our BatterySnapshot entity.
 * Keeps defaults defensive to avoid crashes on missing sensors.
 */
public class BatteryDataCollector {

    private static final String TAG = "BatteryDataCollector";

    private static long lastCpuIdle = -1;
    private static long lastCpuTotal = -1;

    private final Context context;
    private final BatteryManager batteryManager;

    public BatteryDataCollector(Context context) {
        this.context = context.getApplicationContext();
        this.batteryManager = (BatteryManager) this.context.getSystemService(Context.BATTERY_SERVICE);
    }

    public BatterySnapshot collectSnapshot() {
        BatterySnapshot snapshot = new BatterySnapshot();

        if (batteryManager == null) {
            return snapshot;
        }

        snapshot.level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        snapshot.chargeCounter = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
        snapshot.currentNow = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
        snapshot.currentAverage = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE);
        snapshot.energyCounter = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER);

        Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery != null) {
            snapshot.voltage = battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
            snapshot.temperature = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
            snapshot.health = battery.getIntExtra(BatteryManager.EXTRA_HEALTH, 0);
            snapshot.status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, 0);
            snapshot.plugged = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        }

        snapshot.isCharging = snapshot.status == BatteryManager.BATTERY_STATUS_CHARGING
            || snapshot.status == BatteryManager.BATTERY_STATUS_FULL;
        snapshot.chargingType = resolveChargingType(snapshot.plugged);
        snapshot.isFastCharging = snapshot.isCharging && snapshot.currentNow > 1500000; // heuristics

        // Enhanced diagnostics
        snapshot.cpuLoadTotal = readCpuLoadPercent();
        snapshot.topAppPackage = readTopAppPackage();
        snapshot.gpuLoad = readGpuLoadPercent();
        snapshot.networkState = readNetworkState();
        readChargerFingerprint(snapshot);
        snapshot.voltageSamplesJson = collectVoltageSamples();
        snapshot.currentSamplesJson = collectCurrentSamples();

        // Basic estimations; leave zeros if unavailable
        snapshot.designCapacityMah = 0;
        snapshot.capacityMah = snapshot.chargeCounter > 0 ? snapshot.chargeCounter / 1000f : 0;
        snapshot.healthPercentage = snapshot.capacityMah > 0 && snapshot.designCapacityMah > 0
                ? (snapshot.capacityMah / snapshot.designCapacityMah) * 100f
                : 0;

        snapshot.chargeRate = snapshot.currentNow < 0 ? 0 : snapshot.currentNow / 1000f;
        snapshot.dischargeRate = snapshot.currentNow > 0 ? 0 : Math.abs(snapshot.currentNow) / 1000f;

        // System context; best-effort reads
        snapshot.screenOn = isScreenOn();
        snapshot.screenBrightness = getScreenBrightness();

        return snapshot;
    }

    private boolean isScreenOn() {
        try {
            return Settings.System.getInt(context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE) == Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL;
        } catch (Exception ignored) {
            return true;
        }
    }

    private int getScreenBrightness() {
        try {
            return Settings.System.getInt(context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private int readCpuLoadPercent() {
        // Parse /proc/stat for aggregate CPU ticks and compute delta-based utilization.
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/stat"))) {
            String line = br.readLine();
            if (line == null || !line.startsWith("cpu")) return -1;

            String[] parts = line.trim().split("\\s+");
            if (parts.length < 8) return -1;

            long user = Long.parseLong(parts[1]);
            long nice = Long.parseLong(parts[2]);
            long system = Long.parseLong(parts[3]);
            long idle = Long.parseLong(parts[4]);
            long iowait = Long.parseLong(parts[5]);
            long irq = Long.parseLong(parts[6]);
            long softirq = Long.parseLong(parts[7]);
            long steal = parts.length > 8 ? Long.parseLong(parts[8]) : 0;

            long idleAll = idle + iowait;
            long total = user + nice + system + idle + iowait + irq + softirq + steal;

            int result = -1;
            if (lastCpuTotal >= 0 && lastCpuIdle >= 0) {
                long diffTotal = total - lastCpuTotal;
                long diffIdle = idleAll - lastCpuIdle;
                if (diffTotal > 0) {
                    long busy = diffTotal - diffIdle;
                    long percent = busy * 100 / diffTotal;
                    result = (int) Math.max(0, Math.min(100, percent));
                }
            }

            lastCpuTotal = total;
            lastCpuIdle = idleAll;
            return result;
        } catch (Exception e) {
            return -1;
        }
    }

    private String readTopAppPackage() {
        try {
            UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            long now = System.currentTimeMillis();
            List<UsageStats> stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 5 * 60_000, now);
            UsageStats top = null;
            if (stats != null) {
                for (UsageStats s : stats) {
                    if (top == null || s.getLastTimeUsed() > top.getLastTimeUsed()) {
                        top = s;
                    }
                }
            }
            return top != null ? top.getPackageName() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private int readGpuLoadPercent() {
        // Most devices lack public GPU load; return -1 when unavailable.
        return -1;
    }

    private String readNetworkState() {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network active = cm.getActiveNetwork();
            if (active == null) return "none";
            NetworkCapabilities caps = cm.getNetworkCapabilities(active);
            if (caps == null) return "none";
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "wifi";
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return "mobile";
            return "none";
        } catch (Exception e) {
            return "none";
        }
    }

    private void readChargerFingerprint(BatterySnapshot snapshot) {
        try {
            UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
            if (usbManager == null) return;
            for (Map.Entry<String, UsbDevice> entry : usbManager.getDeviceList().entrySet()) {
                UsbDevice device = entry.getValue();
                snapshot.chargerVendorId = device.getVendorId();
                snapshot.chargerProductId = device.getProductId();
                break;
            }
        } catch (Exception ignored) {
            // best-effort
        }
    }

    private String collectVoltageSamples() {
        // Use voltage from battery intent since BATTERY_PROPERTY_VOLTAGE may not be available
        return collectSamples(() -> {
            Intent batteryIntent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            return batteryIntent != null ? batteryIntent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) : 0;
        });
    }

    private String collectCurrentSamples() {
        return collectSamples(() -> batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW));
    }

    private String collectSamples(IntSupplier supplier) {
        try {
            StringWriter sw = new StringWriter();
            JsonWriter jw = new JsonWriter(sw);
            jw.beginArray();
            long startElapsed = SystemClock.elapsedRealtime();
            long startWall = System.currentTimeMillis();
            int count = 0;
            while (SystemClock.elapsedRealtime() - startElapsed < 100) {
                jw.value(supplier.getAsInt());
                count++;
                SystemClock.sleep(5);
            }
            jw.endArray();
            jw.close();
            long endWall = System.currentTimeMillis();
            Log.d(TAG, "Sampled " + count + " points in " + (endWall - startWall) + "ms");
            return sw.toString();
        } catch (Exception e) {
            return "[]";
        }
    }

    private interface IntSupplier {
        int getAsInt();
    }

    private String resolveChargingType(int plugged) {
        switch (plugged) {
            case BatteryManager.BATTERY_PLUGGED_USB:
                return "usb";
            case BatteryManager.BATTERY_PLUGGED_AC:
                return "ac";
            case BatteryManager.BATTERY_PLUGGED_WIRELESS:
                return "wireless";
            default:
                return "unknown";
        }
    }
}
