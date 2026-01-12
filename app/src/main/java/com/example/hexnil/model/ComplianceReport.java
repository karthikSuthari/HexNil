package com.example.hexnil.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Generates compliance reports for regulatory and certification support
 * Supports IEC, UL, and other battery safety standards
 */
@Entity(tableName = "compliance_reports")
public class ComplianceReport {
    
    @PrimaryKey(autoGenerate = true)
    public long id;
    
    public long timestamp;
    public long periodStart;
    public long periodEnd;
    
    // Report metadata
    public String reportType;            // "safety", "thermal", "charging", "comprehensive"
    public String reportVersion;         // Report format version
    public String generatedBy;           // HexNil version
    
    // Device info
    public String deviceModel;
    public String deviceSerial;
    public String firmwareVersion;
    public String batteryPartNumber;
    
    // Safety compliance
    public boolean safetyCompliant;
    public int safetyViolationCount;
    public String safetyViolations;      // JSON of violations
    public float safetyScore;            // 0-100
    
    // Temperature compliance (typically max 45°C during use, 60°C during charging)
    public float maxOperatingTemp;
    public float maxChargingTemp;
    public boolean tempCompliant;
    public int tempViolationCount;
    public String tempViolationDetails;
    
    // Voltage compliance
    public float minVoltage;
    public float maxVoltage;
    public boolean voltageCompliant;
    public int voltageViolationCount;
    public String voltageViolationDetails;
    
    // Current compliance
    public float maxChargingCurrent;
    public float maxDischargeCurrent;
    public boolean currentCompliant;
    public int currentViolationCount;
    public String currentViolationDetails;
    
    // Over-discharge protection
    public float lowestVoltage;
    public boolean overDischargeProtectionWorking;
    public int overDischargeEvents;
    
    // Overcharge protection
    public float highestVoltage;
    public boolean overchargeProtectionWorking;
    public int overchargeEvents;
    
    // Thermal protection
    public boolean thermalProtectionWorking;
    public int thermalShutdownEvents;
    public int thermalThrottleEvents;
    
    // Standard-specific compliance
    public boolean iecCompliant;         // IEC 62133
    public boolean ulCompliant;          // UL 2054
    public boolean unCompliant;          // UN 38.3
    public String standardsNotes;
    
    // Certification readiness
    public float certificationReadiness; // 0-100%
    public String certificationGaps;     // What's missing
    public String certificationRecommendations;
    
    // Evidence / audit trail
    public String evidenceSummary;       // JSON of ComplianceEvidence used for this report
    public String evidenceIds;           // JSON array of evidence ids for traceability
    public int evidenceCount;

    // Audit trail
    public String auditTrail;            // JSON of timestamped events
    public int dataPointsAnalyzed;
    public float dataQuality;            // Quality of data used
    
    // Report content
    public String executiveSummary;
    public String technicalDetails;
    public String complianceMatrix;      // JSON matrix of requirements vs status
    public String attachments;           // References to detailed logs
    
    // Sign-off
    public String reviewStatus;          // "pending", "approved", "rejected"
    public String reviewNotes;
    public long reviewTimestamp;
    
    public ComplianceReport() {
        this.timestamp = System.currentTimeMillis();
    }
    
    public boolean isFullyCompliant() {
        return safetyCompliant && tempCompliant && voltageCompliant && currentCompliant;
    }
    
    public String getComplianceGrade() {
        if (safetyScore >= 95 && isFullyCompliant()) return "A";
        if (safetyScore >= 85 && safetyViolationCount == 0) return "B";
        if (safetyScore >= 70) return "C";
        if (safetyScore >= 50) return "D";
        return "F";
    }
    
    public boolean isCertificationReady() {
        return certificationReadiness >= 95 && isFullyCompliant();
    }
}
