package com.example.hexnil.model;

/**
 * Aggregated metrics for a time window, used by AI Advisor and reports.
 */
public class RollupMetrics {
    public long periodStart;
    public long periodEnd;
    public String label; // e.g., "24h", "7d"

    // Drain
    public float screenOnDrain;
    public float screenOffDrain;
    public float idleDrain;
    public float wakelockDrain;
    public float networkDrain;
    public float systemDrain;
    public float userDrain;

    // Charging
    public float avgChargeMinutes;
    public float avgChargeSpeed;
    public float maxChargeTemp;

    // Thermal
    public float avgTemp;
    public float maxTemp;

    // Health
    public float avgHealth;
    public float capacityFade;

    // Protection
    public int overDischargeEvents;
    public int fastChargeAbuse;
    public boolean swellingRisk;

    // Stress tests
    public float stressScore;

    // Severity
    public float riskScore;
    public String severity; // info/low/medium/high/critical
    public String[] topIssues;
    public String attribution; // app/firmware/user/environment summary

    public int sampleCount;
}
