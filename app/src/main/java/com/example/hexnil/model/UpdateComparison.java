package com.example.hexnil.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Compares battery performance before and after OTA updates
 * Validates that updates don't degrade battery life
 */
@Entity(tableName = "update_comparisons")
public class UpdateComparison {
    
    @PrimaryKey(autoGenerate = true)
    public long id;
    
    public long timestamp;
    
    // Update info
    public String previousVersion;       // OS/firmware version before
    public String currentVersion;        // OS/firmware version after
    public long updateTimestamp;         // When update was applied
    public String updateType;            // "security", "feature", "major", "minor"
    
    // Pre-update metrics (7-day average before update)
    public float preDrainRate;           // Aggregate drain %/hr across all usage
    public float preAvgDrainScreenOn;    // %/hr with screen on
    public float preAvgDrainScreenOff;   // %/hr with screen off
    public float preAvgChargingTime;     // Minutes for full charge
    public float preAvgTemperature;      // Average temp during use
    public float preMaxTemperature;      // Max temp observed
    public float preIdleDrain;           // %/hr idle
    public float preBatteryHealth;       // Health % before update
    
    // Post-update metrics (7-day average after update)
    public float postDrainRate;          // Aggregate drain %/hr across all usage
    public float postAvgDrainScreenOn;
    public float postAvgDrainScreenOff;
    public float postAvgChargingTime;
    public float postAvgTemperature;
    public float postMaxTemperature;
    public float postIdleDrain;
    public float postBatteryHealth;
    
    // Delta calculations
    public float drainRateDelta;         // + means worse after update
    public float screenOnDrainDelta;     // + means worse after update
    public float screenOffDrainDelta;
    public float chargingTimeDelta;
    public float temperatureDelta;       // Avg temperature delta
    public float thermalPeakDelta;       // Max temperature delta
    public float idleDrainDelta;
    
    // Percentage change
    public float drainRateChange;        // % change (+ is worse)
    public float screenOnDrainChange;    // % change (+ is worse)
    public float screenOffDrainChange;
    public float chargingTimeChange;
    public float temperatureChange;
    public float thermalPeakChange;
    public float idleDrainChange;
    
    // Impact assessment
    public String overallImpact;         // "improved", "neutral", "degraded"
    public float impactScore;            // -100 to +100 (negative = better)
    public String impactDetails;         // Detailed analysis
    
    // Specific issues
    public boolean hasDrainRegression;   // Significant drain increase
    public boolean hasChargingRegression; // Charging got worse
    public boolean hasThermalRegression; // Thermal issues increased
    public boolean hasIdleRegression;    // Idle drain increased
    
    // Recommendations
    public String recommendation;        // "approve", "investigate", "rollback"
    public String analysisNotes;         // Detailed notes
    public float confidenceLevel;        // How confident in analysis (0-100)

    // Chart overlay payloads for UI (JSON arrays of daily points)
    public String preSeriesJson;         // e.g., [{ts:..., drainRate:..., chargingMinutes:..., thermalPeak:..., idleDrain:..., samples:...}]
    public String postSeriesJson;
    
    public UpdateComparison() {
        this.timestamp = System.currentTimeMillis();
    }
    
    public void calculateDeltas() {
        drainRateDelta = postDrainRate - preDrainRate;
        screenOnDrainDelta = postAvgDrainScreenOn - preAvgDrainScreenOn;
        screenOffDrainDelta = postAvgDrainScreenOff - preAvgDrainScreenOff;
        chargingTimeDelta = postAvgChargingTime - preAvgChargingTime;
        temperatureDelta = postAvgTemperature - preAvgTemperature;
        thermalPeakDelta = postMaxTemperature - preMaxTemperature;
        idleDrainDelta = postIdleDrain - preIdleDrain;
        
        if (preDrainRate > 0) {
            drainRateChange = (drainRateDelta / preDrainRate) * 100;
        }
        if (preAvgDrainScreenOn > 0) {
            screenOnDrainChange = (screenOnDrainDelta / preAvgDrainScreenOn) * 100;
        }
        if (preAvgDrainScreenOff > 0) {
            screenOffDrainChange = (screenOffDrainDelta / preAvgDrainScreenOff) * 100;
        }
        if (preAvgChargingTime > 0) {
            chargingTimeChange = (chargingTimeDelta / preAvgChargingTime) * 100;
        }
        if (preAvgTemperature > 0) {
            temperatureChange = (temperatureDelta / preAvgTemperature) * 100;
        }
        if (preMaxTemperature > 0) {
            thermalPeakChange = (thermalPeakDelta / preMaxTemperature) * 100;
        }
        if (preIdleDrain > 0) {
            idleDrainChange = (idleDrainDelta / preIdleDrain) * 100;
        }
        
        // Check for regressions (>10% worse overall, more lenient for temperature)
        hasDrainRegression = drainRateChange > 10 || screenOnDrainChange > 10 || screenOffDrainChange > 10;
        hasChargingRegression = chargingTimeChange > 10;
        hasThermalRegression = temperatureChange > 5 || thermalPeakChange > 5;
        hasIdleRegression = idleDrainChange > 15;
    }
    
    public boolean hasAnyRegression() {
        return hasDrainRegression || hasChargingRegression || 
               hasThermalRegression || hasIdleRegression;
    }
}
