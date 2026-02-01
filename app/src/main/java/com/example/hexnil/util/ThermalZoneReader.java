package com.example.hexnil.util;

import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class to read thermal zone information from Android system files.
 * 
 * Attempts to read from /sys/class/thermal/ directories when accessible.
 * On most devices without root, this will only be partially available or not at all.
 * The battery temperature from BatteryManager is always available as a fallback.
 * 
 * Thermal zones typically include:
 * - battery: Battery temperature sensor
 * - cpu: CPU core temperatures
 * - gpu: GPU temperature
 * - skin: Device surface temperature
 * - pa-therm: Power amplifier temperature
 * - msm-therm: Main SoC temperature
 * - quiet-therm: Quiet mode thermal zone
 */
public class ThermalZoneReader {

    private static final String TAG = "ThermalZoneReader";
    private static final String THERMAL_PATH = "/sys/class/thermal/";

    // Known thermal zone type patterns
    private static final String[] CPU_PATTERNS = {"cpu", "little", "big", "core", "cluster", "tsens_tz_sensor"};
    private static final String[] GPU_PATTERNS = {"gpu", "adreno"};
    private static final String[] BATTERY_PATTERNS = {"battery", "batt"};
    private static final String[] SKIN_PATTERNS = {"skin", "surface", "back-therm", "front-therm", "quiet-therm"};
    private static final String[] SOC_PATTERNS = {"soc", "msm", "sdm", "mtk"};

    /**
     * Result containing all readable thermal zone temperatures.
     */
    public static class ThermalZoneData {
        public float cpuTemp = 0;          // °C, 0 if unavailable
        public float gpuTemp = 0;          // °C, 0 if unavailable
        public float batteryTemp = 0;      // °C, 0 if unavailable (use BatteryManager instead)
        public float skinTemp = 0;         // °C, 0 if unavailable
        public float socTemp = 0;          // °C, 0 if unavailable
        public float ambientEstimate = 0;  // °C, estimated
        public Map<String, Float> allZones = new HashMap<>();
        public boolean hasExtendedData = false;
        
        /**
         * Convert to JSON string for storage.
         */
        public String toJson() {
            try {
                JSONObject json = new JSONObject();
                if (batteryTemp > 0) json.put("battery", round(batteryTemp, 1));
                if (cpuTemp > 0) json.put("cpu", round(cpuTemp, 1));
                if (gpuTemp > 0) json.put("gpu", round(gpuTemp, 1));
                if (skinTemp > 0) json.put("skin", round(skinTemp, 1));
                if (socTemp > 0) json.put("soc", round(socTemp, 1));
                if (ambientEstimate > 0) json.put("ambient", round(ambientEstimate, 1));
                
                // Add any additional zones
                for (Map.Entry<String, Float> entry : allZones.entrySet()) {
                    if (!json.has(entry.getKey()) && entry.getValue() > 0) {
                        json.put(entry.getKey(), round(entry.getValue(), 1));
                    }
                }
                
                return json.toString();
            } catch (Exception e) {
                return "{}";
            }
        }
        
        private float round(float value, int decimals) {
            float factor = (float) Math.pow(10, decimals);
            return Math.round(value * factor) / factor;
        }
    }

    /**
     * Read all available thermal zone data.
     * 
     * @param batteryTempFromManager Battery temperature from BatteryManager (always available)
     * @return ThermalZoneData with all readable temperatures
     */
    public static ThermalZoneData readThermalZones(float batteryTempFromManager) {
        ThermalZoneData data = new ThermalZoneData();
        data.batteryTemp = batteryTempFromManager;
        
        try {
            File thermalDir = new File(THERMAL_PATH);
            if (!thermalDir.exists() || !thermalDir.canRead()) {
                // Cannot access thermal directory - estimate ambient
                data.ambientEstimate = estimateAmbient(batteryTempFromManager);
                return data;
            }
            
            File[] zones = thermalDir.listFiles((dir, name) -> name.startsWith("thermal_zone"));
            if (zones == null || zones.length == 0) {
                data.ambientEstimate = estimateAmbient(batteryTempFromManager);
                return data;
            }
            
            float maxCpuTemp = 0;
            float maxGpuTemp = 0;
            float maxSkinTemp = 0;
            float maxSocTemp = 0;
            
            for (File zone : zones) {
                try {
                    String type = readFile(new File(zone, "type"));
                    String tempStr = readFile(new File(zone, "temp"));
                    
                    if (type == null || tempStr == null) continue;
                    
                    type = type.trim().toLowerCase();
                    float temp = parseTemperature(tempStr);
                    
                    if (temp <= 0 || temp > 150) continue; // Invalid reading
                    
                    data.allZones.put(type, temp);
                    data.hasExtendedData = true;
                    
                    // Categorize by type
                    if (matchesAny(type, CPU_PATTERNS)) {
                        maxCpuTemp = Math.max(maxCpuTemp, temp);
                    } else if (matchesAny(type, GPU_PATTERNS)) {
                        maxGpuTemp = Math.max(maxGpuTemp, temp);
                    } else if (matchesAny(type, BATTERY_PATTERNS)) {
                        // Use system battery temp if higher than BatteryManager
                        data.batteryTemp = Math.max(data.batteryTemp, temp);
                    } else if (matchesAny(type, SKIN_PATTERNS)) {
                        maxSkinTemp = Math.max(maxSkinTemp, temp);
                    } else if (matchesAny(type, SOC_PATTERNS)) {
                        maxSocTemp = Math.max(maxSocTemp, temp);
                    }
                } catch (Exception e) {
                    // Skip unreadable zone
                }
            }
            
            data.cpuTemp = maxCpuTemp;
            data.gpuTemp = maxGpuTemp;
            data.skinTemp = maxSkinTemp;
            data.socTemp = maxSocTemp;
            
            // Estimate ambient temperature
            if (data.skinTemp > 0) {
                // If we have skin temperature, ambient is typically lower
                data.ambientEstimate = estimateAmbientFromSkin(data.skinTemp);
            } else {
                data.ambientEstimate = estimateAmbient(data.batteryTemp);
            }
            
        } catch (Exception e) {
            Log.d(TAG, "Error reading thermal zones", e);
            data.ambientEstimate = estimateAmbient(batteryTempFromManager);
        }
        
        return data;
    }

    /**
     * Parse temperature from thermal zone file.
     * Different devices report in different formats:
     * - millidegrees (e.g., 32000 = 32°C)
     * - decidegrees (e.g., 320 = 32°C)
     * - degrees (e.g., 32 = 32°C)
     */
    private static float parseTemperature(String tempStr) {
        try {
            long temp = Long.parseLong(tempStr.trim());
            
            if (temp > 1000) {
                // Millidegrees
                return temp / 1000f;
            } else if (temp > 100) {
                // Decidegrees
                return temp / 10f;
            } else {
                // Already in degrees
                return temp;
            }
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Read a file and return its first line.
     */
    private static String readFile(File file) {
        if (!file.exists() || !file.canRead()) {
            return null;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            return reader.readLine();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Check if type matches any of the patterns.
     */
    private static boolean matchesAny(String type, String[] patterns) {
        for (String pattern : patterns) {
            if (type.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Estimate ambient temperature from battery temperature.
     * 
     * Battery temperature is typically 3-8°C higher than ambient:
     * - At idle: ~3-5°C above ambient
     * - Under light load: ~5-7°C above ambient
     * - Under heavy load: ~8-15°C above ambient
     * 
     * We use a conservative estimate assuming light activity.
     */
    private static float estimateAmbient(float batteryTemp) {
        if (batteryTemp <= 0) return 25; // Default room temperature
        
        float offset;
        if (batteryTemp < 30) {
            offset = 3; // Cool device, minimal offset
        } else if (batteryTemp < 35) {
            offset = 4; // Normal operation
        } else if (batteryTemp < 40) {
            offset = 5; // Light load
        } else if (batteryTemp < 45) {
            offset = 7; // Moderate load
        } else {
            offset = 10; // Heavy load
        }
        
        float ambient = batteryTemp - offset;
        // Clamp to reasonable indoor range
        return Math.max(15, Math.min(45, ambient));
    }

    /**
     * Estimate ambient from skin temperature.
     * Skin temperature is closer to ambient than battery.
     */
    private static float estimateAmbientFromSkin(float skinTemp) {
        if (skinTemp <= 0) return 25;
        
        float offset;
        if (skinTemp < 30) {
            offset = 2;
        } else if (skinTemp < 35) {
            offset = 3;
        } else {
            offset = 4;
        }
        
        float ambient = skinTemp - offset;
        return Math.max(15, Math.min(45, ambient));
    }
}
