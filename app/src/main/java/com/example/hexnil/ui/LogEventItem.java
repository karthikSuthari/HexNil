package com.example.hexnil.ui;

/**
 * Unified log event item for display in LogsFragment.
 * Represents anomalies, safety events, and regression events in a common format.
 */
public class LogEventItem {
    
    public static final int SEVERITY_SAFE = 0;
    public static final int SEVERITY_WARNING = 1;
    public static final int SEVERITY_CRITICAL = 2;
    public static final int SEVERITY_INFO = 3;
    
    public long id;
    public long timestamp;
    public String eventType;      // e.g., "THERMAL_ALERT", "OVER_TEMP", "LAUNCH_TIME"
    public String title;          // Human-readable title
    public String description;    // Brief description
    public String details;        // Monospace detailed information
    public int severity;          // SEVERITY_* constant
    public String category;       // "ANOMALY", "SAFETY", "REGRESSION"
    
    public LogEventItem() {}
    
    public LogEventItem(long id, long timestamp, String eventType, String title, 
                        String description, String details, int severity, String category) {
        this.id = id;
        this.timestamp = timestamp;
        this.eventType = eventType;
        this.title = title;
        this.description = description;
        this.details = details;
        this.severity = severity;
        this.category = category;
    }
}