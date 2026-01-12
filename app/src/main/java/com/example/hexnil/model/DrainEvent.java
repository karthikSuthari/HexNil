package com.example.hexnil.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Records battery drain events for root-cause analysis
 * Tracks app-wise and system-wise battery consumption
 */
@Entity(tableName = "drain_events")
public class DrainEvent {
    
    @PrimaryKey(autoGenerate = true)
    public long id;
    
    public long startTime;
    public long endTime;
    
    // Drain metrics
    public int startLevel;               // Battery % at start
    public int endLevel;                 // Battery % at end
    public int drainAmount;              // % drained
    public float drainRatePerHour;       // %/hour
    
    // Context
    public boolean screenOn;
    public String screenState;           // "on", "off", "doze"
    public boolean isCharging;
    public boolean isIdle;
    
    // Drain classification
    public String drainType;             // "normal", "heavy", "idle", "standby"
    public String drainCategory;         // "app", "system", "network", "display", "unknown"
    public String primaryCause;          // Main drain cause
    
    // App attribution
    public String topDrainingApps;       // JSON array of top draining apps
    public String foregroundApp;         // Primary foreground app during period
    public int wakelockCount;            // Number of wakelocks held
    public String activeWakelocks;       // JSON of active wakelocks
    
    // Network drain
    public float networkDrainEstimate;   // Estimated % from network
    public String networkType;           // "5g", "4g", "wifi", "none"
    public long dataSent;                // Bytes sent
    public long dataReceived;            // Bytes received
    
    // Display drain
    public float displayDrainEstimate;   // Estimated % from display
    public int avgBrightness;            // Average brightness level
    public long screenOnTime;            // Screen on time in ms
    
    // CPU drain
    public float cpuDrainEstimate;       // Estimated % from CPU
    public int avgCpuUsage;              // Average CPU usage %
    
    // GPS drain
    public float gpsDrainEstimate;       // Estimated % from GPS
    public boolean gpsActive;
    
    // Analysis results
    public String analysisNotes;         // AI analysis notes
    public String recommendations;       // Recommendations for optimization
    
    // Anomaly flags
    public boolean isAnomalous;          // Higher than expected drain
    public float expectedDrain;          // What drain should have been
    public float anomalyScore;           // How anomalous (0-1)
    
    public DrainEvent() {
        this.startTime = System.currentTimeMillis();
    }
    
    public long getDurationMinutes() {
        if (endTime == 0) return 0;
        return (endTime - startTime) / 60000;
    }
    
    public float getDurationHours() {
        return getDurationMinutes() / 60.0f;
    }
    
    public boolean isExcessiveDrain() {
        if (screenOn) {
            return drainRatePerHour > 20; // >20%/hr with screen on is high
        } else {
            return drainRatePerHour > 3;  // >3%/hr idle is high
        }
    }
}
