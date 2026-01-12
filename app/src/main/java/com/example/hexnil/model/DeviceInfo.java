package com.example.hexnil.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Stores device information for OEM tracking and fleet analysis
 */
@Entity(tableName = "device_info")
public class DeviceInfo {
    
    @PrimaryKey(autoGenerate = true)
    public long id;
    
    public long timestamp;
    public long firstRecorded;
    
    // Device identification
    public String deviceId;              // Unique device identifier
    public String manufacturer;          // Device manufacturer
    public String model;                 // Device model
    public String product;               // Product name
    public String brand;                 // Brand name
    public String device;                // Device code name
    public String board;                 // Board name
    public String hardware;              // Hardware name
    
    // Software info
    public String androidVersion;        // Android version
    public int sdkVersion;               // SDK/API level
    public String buildNumber;           // Build number
    public String securityPatch;         // Security patch level
    public String kernelVersion;         // Kernel version
    public String firmwareVersion;       // OEM firmware version
    
    // Battery info
    public String batteryTechnology;     // Li-ion, Li-Po, etc.
    public float designCapacityMah;      // Design capacity
    public String batteryPartNumber;     // Part number if available
    public String batteryManufacturer;   // Battery manufacturer
    public String batteryBatchId;        // Batch ID for tracking
    
    // Hardware specs
    public String processor;             // CPU model
    public int cpuCores;                 // Number of CPU cores
    public long ramSize;                 // RAM in bytes
    public long storageSize;             // Storage in bytes
    public int screenWidth;              // Screen width pixels
    public int screenHeight;             // Screen height pixels
    public float screenDensity;          // Screen density
    
    // OEM metadata
    public String oemSku;                // OEM SKU
    public String regionCode;            // Region/locale
    public String carrierCode;           // Carrier if applicable
    public String productionDate;        // Manufacturing date if available
    public String warrantyStatus;        // Warranty info
    
    // HexNil tracking
    public long hexnilInstallDate;       // When HexNil was installed
    public String hexnilVersion;         // HexNil version
    public int totalSnapshots;           // Total battery snapshots
    public int totalAnalyses;            // Total AI analyses run
    
    public DeviceInfo() {
        this.timestamp = System.currentTimeMillis();
        this.firstRecorded = System.currentTimeMillis();
    }
    
    public String getDisplayName() {
        return manufacturer + " " + model;
    }
    
    public String getFullIdentifier() {
        return manufacturer + "_" + model + "_" + deviceId;
    }
}
