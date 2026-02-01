package com.example.hexnil.charging;

import com.example.hexnil.model.BatterySnapshot;
import com.example.hexnil.model.ChargingSession;

/**
 * OEM-grade charger classification and fingerprinting utility.
 * 
 * Provides:
 * - Charging speed classification (trickle, slow, normal, fast, super_fast, ultra_fast)
 * - OEM vs third-party charger detection
 * - Charger quality assessment
 * - USB-PD/QC protocol detection
 * - Charger fingerprinting for pattern analysis
 * 
 * Classification Thresholds (based on industry standards):
 * - Trickle:    < 500mA  (USB 2.0 limit)
 * - Slow:       500-1000mA (USB 3.0 BC1.2)
 * - Normal:     1000-1500mA (Standard 5V/1.5A)
 * - Fast:       1500-2500mA (QC2.0, USB-PD 15W)
 * - Super Fast: 2500-4000mA (QC3.0, USB-PD 25W)
 * - Ultra Fast: > 4000mA (QC4+, USB-PD 45W+, proprietary)
 */
public class ChargerClassifier {

    // ============================================================================
    // CURRENT THRESHOLDS (mA)
    // ============================================================================
    
    public static final float CURRENT_TRICKLE_MAX = 500f;
    public static final float CURRENT_SLOW_MAX = 1000f;
    public static final float CURRENT_NORMAL_MAX = 1500f;
    public static final float CURRENT_FAST_MAX = 2500f;
    public static final float CURRENT_SUPER_FAST_MAX = 4000f;
    // Above 4000mA = ultra_fast
    
    // ============================================================================
    // VOLTAGE THRESHOLDS (mV)
    // ============================================================================
    
    public static final float VOLTAGE_5V_MIN = 4800f;
    public static final float VOLTAGE_5V_MAX = 5200f;
    public static final float VOLTAGE_9V_MIN = 8500f;
    public static final float VOLTAGE_9V_MAX = 9500f;
    public static final float VOLTAGE_12V_MIN = 11500f;
    public static final float VOLTAGE_12V_MAX = 12500f;
    public static final float VOLTAGE_15V_MIN = 14500f;
    public static final float VOLTAGE_15V_MAX = 15500f;
    public static final float VOLTAGE_20V_MIN = 19500f;
    public static final float VOLTAGE_20V_MAX = 20500f;
    
    // ============================================================================
    // PLUG TYPE CONSTANTS
    // ============================================================================
    
    public static final int PLUG_NONE = 0;
    public static final int PLUG_AC = 1;
    public static final int PLUG_USB = 2;
    public static final int PLUG_WIRELESS = 4;
    public static final int PLUG_USB_PD = 5;
    
    // ============================================================================
    // CHARGING SPEED CLASSIFICATION
    // ============================================================================
    
    /**
     * Charging speed tier enumeration.
     */
    public enum ChargingSpeed {
        TRICKLE("trickle", 0),
        SLOW("slow", 1),
        NORMAL("normal", 2),
        FAST("fast", 3),
        SUPER_FAST("super_fast", 4),
        ULTRA_FAST("ultra_fast", 5);
        
        public final String code;
        public final int tier;
        
        ChargingSpeed(String code, int tier) {
            this.code = code;
            this.tier = tier;
        }
    }
    
    /**
     * Charger quality tier based on stability and safety.
     */
    public enum ChargerQuality {
        OEM("oem", 100),                    // Original manufacturer charger
        CERTIFIED("certified", 85),          // Third-party certified (UL, CE, MFi)
        COMPATIBLE("compatible", 70),        // Works but not certified
        QUESTIONABLE("questionable", 50),    // Stability issues
        POOR("poor", 25),                    // Significant issues
        DANGEROUS("dangerous", 0);           // Safety hazard
        
        public final String code;
        public final int score;
        
        ChargerQuality(String code, int score) {
            this.code = code;
            this.score = score;
        }
    }
    
    /**
     * Charging protocol detection.
     */
    public enum ChargingProtocol {
        STANDARD_USB("usb_standard"),
        USB_BC12("usb_bc12"),
        USB_PD("usb_pd"),
        QUALCOMM_QC2("qc2"),
        QUALCOMM_QC3("qc3"),
        QUALCOMM_QC4("qc4"),
        SAMSUNG_AFC("samsung_afc"),
        OPPO_VOOC("oppo_vooc"),
        ONEPLUS_WARP("oneplus_warp"),
        HUAWEI_SCP("huawei_scp"),
        MEDIATEK_PE("mediatek_pe"),
        WIRELESS_QI("wireless_qi"),
        WIRELESS_PMA("wireless_pma"),
        UNKNOWN("unknown");
        
        public final String code;
        
        ChargingProtocol(String code) {
            this.code = code;
        }
    }
    
    // ============================================================================
    // CLASSIFICATION METHODS
    // ============================================================================
    
    /**
     * Classify charging speed based on current draw.
     * 
     * @param currentMa Charging current in milliamps
     * @return ChargingSpeed classification
     */
    public static ChargingSpeed classifySpeed(float currentMa) {
        float absCurrent = Math.abs(currentMa);
        
        if (absCurrent < CURRENT_TRICKLE_MAX) {
            return ChargingSpeed.TRICKLE;
        } else if (absCurrent < CURRENT_SLOW_MAX) {
            return ChargingSpeed.SLOW;
        } else if (absCurrent < CURRENT_NORMAL_MAX) {
            return ChargingSpeed.NORMAL;
        } else if (absCurrent < CURRENT_FAST_MAX) {
            return ChargingSpeed.FAST;
        } else if (absCurrent < CURRENT_SUPER_FAST_MAX) {
            return ChargingSpeed.SUPER_FAST;
        } else {
            return ChargingSpeed.ULTRA_FAST;
        }
    }
    
    /**
     * Get charging type string for database storage.
     * 
     * @param currentMa Charging current in milliamps
     * @param isFastCharging System fast charging flag
     * @return Human-readable charging type
     */
    public static String getChargingType(float currentMa, boolean isFastCharging) {
        ChargingSpeed speed = classifySpeed(currentMa);
        
        // Override with system flag if it indicates fast charging
        if (isFastCharging && speed.tier < ChargingSpeed.FAST.tier) {
            return ChargingSpeed.FAST.code;
        }
        
        return speed.code;
    }
    
    /**
     * Detect charging protocol based on voltage/current characteristics.
     * 
     * NOTE: On most Android devices, the voltage reported by BatteryManager is the
     * battery cell voltage (3.7V-4.4V), NOT the charger input voltage (5V, 9V, etc.).
     * Therefore, protocol detection relies primarily on current draw and system flags.
     * 
     * @param batteryVoltageMv Battery voltage in millivolts (typically 3700-4400mV)
     * @param currentMa Charging current in milliamps
     * @param plugType Android plug type constant
     * @return Detected charging protocol
     */
    public static ChargingProtocol detectProtocol(float batteryVoltageMv, float currentMa, int plugType) {
        // Wireless charging
        if (plugType == PLUG_WIRELESS) {
            return ChargingProtocol.WIRELESS_QI;
        }
        
        float absCurrent = Math.abs(currentMa);
        
        // Since Android typically reports battery voltage (not charger input voltage),
        // we infer protocol from charging current patterns:
        
        // Ultra high current (>3A) typically indicates fast charging protocols
        if (absCurrent > 3000) {
            // Could be VOOC (5V/6A), Super VOOC, Warp Charge, or high-power USB-PD
            // Check if current is extremely high (VOOC-style low voltage high current)
            if (absCurrent > 4500) {
                return ChargingProtocol.OPPO_VOOC; // Or OnePlus WARP - they use 5V/6A+
            }
            // Could be QC4/USB-PD at 9V or higher
            return ChargingProtocol.USB_PD;
        }
        
        // High current (2-3A) indicates fast charging
        if (absCurrent > 2000) {
            // Likely QC3.0 (adjustable 3.6-20V) or USB-PD
            return ChargingProtocol.QUALCOMM_QC3;
        }
        
        // Medium-high current (1.5-2A) could be QC2.0 or USB BC1.2
        if (absCurrent > 1500) {
            return ChargingProtocol.QUALCOMM_QC2;
        }
        
        // Standard current (1-1.5A) - USB BC1.2 DCP
        if (absCurrent > 1000) {
            return ChargingProtocol.USB_BC12;
        }
        
        // Low current (<1A) - Standard USB
        if (absCurrent > 0) {
            return ChargingProtocol.STANDARD_USB;
        }
        
        return ChargingProtocol.UNKNOWN;
    }
    
    // ============================================================================
    // CHARGER FINGERPRINTING
    // ============================================================================
    
    /**
     * Result of charger fingerprinting.
     */
    public static class ChargerFingerprint {
        public String fingerprintId;
        public String vendorId;
        public String productId;
        public ChargingSpeed speedTier;
        public ChargingProtocol protocol;
        public ChargerQuality quality;
        public boolean isOEM;
        public boolean isCertified;
        public float powerCapabilityWatts;
        public String classification;
        public String notes;
        
        @Override
        public String toString() {
            return fingerprintId + "|" + speedTier.code + "|" + quality.code;
        }
    }
    
    /**
     * Generate charger fingerprint from snapshot data.
     * 
     * @param snapshot Battery snapshot with charger info
     * @return ChargerFingerprint with classification data
     */
    public static ChargerFingerprint generateFingerprint(BatterySnapshot snapshot) {
        ChargerFingerprint fp = new ChargerFingerprint();
        
        // Basic ID from USB enumeration
        fp.vendorId = String.format("%04X", snapshot.chargerVendorId);
        fp.productId = String.format("%04X", snapshot.chargerProductId);
        fp.fingerprintId = fp.vendorId + ":" + fp.productId;
        
        // Speed classification
        fp.speedTier = classifySpeed(snapshot.chargeRate);
        
        // Protocol detection (note: we use battery voltage here, not charger input voltage)
        fp.protocol = detectProtocol(snapshot.voltage, snapshot.chargeRate, snapshot.plugged);
        
        // Power capability estimate
        float volts = snapshot.voltage / 1000f;
        float amps = Math.abs(snapshot.chargeRate) / 1000f;
        fp.powerCapabilityWatts = volts * amps;
        
        // OEM detection heuristics
        fp.isOEM = detectOEMCharger(snapshot);
        fp.isCertified = snapshot.isFastCharging || fp.speedTier.tier >= ChargingSpeed.FAST.tier;
        
        // Quality assessment
        fp.quality = assessChargerQuality(snapshot, fp);
        
        // Classification string
        fp.classification = buildClassificationString(fp);
        
        return fp;
    }
    
    /**
     * Detect if charger is likely OEM (original equipment manufacturer).
     * 
     * Uses heuristics based on:
     * - Vendor ID matching known OEM ranges
     * - Fast charging capability
     * - Charging stability
     */
    private static boolean detectOEMCharger(BatterySnapshot snapshot) {
        // Fast charging flag strongly indicates OEM charger
        if (snapshot.isFastCharging) {
            return true;
        }
        
        // Known OEM vendor IDs (partial list)
        int vendorId = snapshot.chargerVendorId;
        if (vendorId == 0x04E8 || // Samsung
            vendorId == 0x2717 || // Xiaomi
            vendorId == 0x22D9 || // OPPO/OnePlus
            vendorId == 0x12D1 || // Huawei
            vendorId == 0x18D1 || // Google
            vendorId == 0x05AC) { // Apple
            return true;
        }
        
        return false;
    }
    
    /**
     * Assess charger quality based on charging characteristics.
     */
    private static ChargerQuality assessChargerQuality(BatterySnapshot snapshot, ChargerFingerprint fp) {
        // Start with baseline
        int score = 70;
        
        // OEM bonus
        if (fp.isOEM) {
            score += 30;
        }
        
        // Fast charging indicates quality
        if (snapshot.isFastCharging) {
            score += 15;
        }
        
        // Temperature check (high temp during charging indicates poor charger)
        if (snapshot.getTemperatureCelsius() > 43) {
            score -= 20;
        } else if (snapshot.getTemperatureCelsius() > 40) {
            score -= 10;
        }
        
        // Very low current could indicate cheap/damaged charger
        if (Math.abs(snapshot.chargeRate) < 300 && snapshot.isCharging) {
            score -= 25;
        }
        
        // Unknown vendor with no fast charging
        if (snapshot.chargerVendorId == 0 && !snapshot.isFastCharging) {
            score -= 15;
        }
        
        // Map score to quality tier
        if (score >= 95) return ChargerQuality.OEM;
        if (score >= 80) return ChargerQuality.CERTIFIED;
        if (score >= 65) return ChargerQuality.COMPATIBLE;
        if (score >= 45) return ChargerQuality.QUESTIONABLE;
        if (score >= 20) return ChargerQuality.POOR;
        return ChargerQuality.DANGEROUS;
    }
    
    /**
     * Build human-readable classification string.
     */
    private static String buildClassificationString(ChargerFingerprint fp) {
        StringBuilder sb = new StringBuilder();
        sb.append(fp.speedTier.code.toUpperCase());
        sb.append(" | ");
        sb.append(fp.protocol.code.toUpperCase());
        sb.append(" | ");
        sb.append(String.format("%.1fW", fp.powerCapabilityWatts));
        sb.append(" | ");
        sb.append(fp.quality.code.toUpperCase());
        return sb.toString();
    }
    
    // ============================================================================
    // SESSION-LEVEL ANALYSIS
    // ============================================================================
    
    /**
     * Determine overall charger quality for a completed session.
     * 
     * @param session Completed charging session
     * @return ChargerQuality assessment
     */
    public static ChargerQuality assessSessionChargerQuality(ChargingSession session) {
        int score = 70;
        
        // Fast charging bonus
        if (session.isFastCharging) {
            score += 15;
        }
        
        // Temperature penalties
        if (session.maxTemperature > 46) {
            score -= 30; // Critical
        } else if (session.maxTemperature > 43) {
            score -= 15; // Warning
        } else if (session.maxTemperature > 40) {
            score -= 5;  // Elevated
        }
        
        // Stability penalties
        score -= session.interruptionCount * 5;
        score -= session.currentFluctuations * 3;
        score -= session.voltageFluctuations * 2;
        
        // Safety event penalties
        score -= session.overCurrentEvents * 10;
        score -= session.overVoltageEvents * 15;
        score -= session.overTempEvents * 10;
        
        // Efficiency bonus (if calculated)
        if (session.chargingEfficiency > 0) {
            if (session.chargingEfficiency > 90) {
                score += 10;
            } else if (session.chargingEfficiency < 70) {
                score -= 15;
            }
        }
        
        // Map to tier
        if (score >= 95) return ChargerQuality.OEM;
        if (score >= 80) return ChargerQuality.CERTIFIED;
        if (score >= 65) return ChargerQuality.COMPATIBLE;
        if (score >= 45) return ChargerQuality.QUESTIONABLE;
        if (score >= 20) return ChargerQuality.POOR;
        return ChargerQuality.DANGEROUS;
    }
    
    /**
     * Classify plug type from Android BatteryManager constant.
     * 
     * @param pluggedType Android plugged constant
     * @return Human-readable plug type
     */
    public static String getPlugTypeString(int pluggedType) {
        switch (pluggedType) {
            case PLUG_AC: return "AC";
            case PLUG_USB: return "USB";
            case PLUG_WIRELESS: return "Wireless";
            case PLUG_USB_PD: return "USB-PD";
            default: return "Unknown";
        }
    }
    
    /**
     * Estimate time to full charge based on current charging characteristics.
     * 
     * @param currentLevel Current battery percentage
     * @param chargingSpeedPerHour Charging speed in %/hour
     * @return Estimated minutes to 100%
     */
    public static int estimateTimeToFull(int currentLevel, float chargingSpeedPerHour) {
        if (chargingSpeedPerHour <= 0 || currentLevel >= 100) {
            return 0;
        }
        
        int remaining = 100 - currentLevel;
        float hours = remaining / chargingSpeedPerHour;
        
        // Account for charging slowdown above 80%
        if (currentLevel < 80) {
            float belowEighty = Math.min(80 - currentLevel, remaining);
            float aboveEighty = remaining - belowEighty;
            
            // Charging above 80% is typically 40% slower
            hours = (belowEighty / chargingSpeedPerHour) + 
                    (aboveEighty / (chargingSpeedPerHour * 0.6f));
        }
        
        return (int) (hours * 60);
    }
}
