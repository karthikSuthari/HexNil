package com.example.hexnil.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Per-device-model profile storing hardware specifications and fleet-wide statistics.
 * Links devices to their hardware fingerprint and tracks population metrics.
 *
 * The deviceFingerprint is SHA256(manufacturer + model + soc + ramGB) and uniquely
 * identifies a device SKU across the fleet.
 */
@Entity(tableName = "device_profiles", indices = {
        @Index(value = "deviceFingerprint", unique = true),
        @Index("manufacturer"),
        @Index("model"),
        @Index("soc")
})
public class DeviceProfile {

    @PrimaryKey(autoGenerate = true)
    public long id;

    /** SHA256(manufacturer + model + soc + ramGB) — unique device SKU identifier */
    @NonNull
    public String deviceFingerprint;

    // === Hardware Identification ===
    public String manufacturer;
    public String model;
    public String marketName;           // e.g., "Galaxy S24 Ultra"
    public String soc;                  // e.g., "Snapdragon 8 Gen 3"
    public String socBinning;           // "high", "mid", "low"
    public Integer ramGB;
    public Integer storageGB;
    public Integer batteryDesignCapacityMah;
    public Float screenSizeInches;
    public String screenResolution;

    // === Fleet Statistics ===
    public int deviceCount;             // How many devices of this SKU in fleet
    public long firstSeen;
    public long lastUpdated;

    // === Aggregated Baseline Metrics (fleet averages) ===
    public Float idleDrainMean;         // %/hr fleet average
    public Float idleDrainStdDev;
    public Float activeDrainMean;       // %/hr fleet average during active use
    public Float activeDrainStdDev;
    public Float thermalEnvelopeCpu;    // Normal max CPU temp for this SKU
    public Float thermalEnvelopeGpu;    // Normal max GPU temp for this SKU
    public Float thermalEnvelopeBattery;// Normal max battery temp for this SKU
    public Float avgColdLaunchMs;
    public Float avgJankPercent;
    public Float avgFps;
    public Float crashesPerDayMean;
    public Float anrsPerDayMean;

    public DeviceProfile() {
        this.deviceFingerprint = "";
        this.deviceCount = 1;
        this.firstSeen = System.currentTimeMillis();
        this.lastUpdated = System.currentTimeMillis();
    }

    /**
     * Create a new device profile from hardware identifiers.
     */
    public static DeviceProfile create(String fingerprint, String manufacturer, String model,
            String soc, Integer ramGB, Integer batteryCapacity) {
        DeviceProfile profile = new DeviceProfile();
        profile.deviceFingerprint = fingerprint;
        profile.manufacturer = manufacturer;
        profile.model = model;
        profile.soc = soc;
        profile.ramGB = ramGB;
        profile.batteryDesignCapacityMah = batteryCapacity;
        return profile;
    }

    /**
     * Returns true if this profile has enough fleet data for reliable baselines.
     */
    public boolean hasReliableBaselines() {
        return deviceCount >= 10 && idleDrainMean != null && activeDrainMean != null;
    }

    @Override
    public String toString() {
        return "DeviceProfile{" +
                "fingerprint=" + deviceFingerprint +
                ", manufacturer=" + manufacturer +
                ", model=" + model +
                ", soc=" + soc +
                ", devices=" + deviceCount +
                ", battery=" + batteryDesignCapacityMah + "mAh" +
                '}';
    }
}
