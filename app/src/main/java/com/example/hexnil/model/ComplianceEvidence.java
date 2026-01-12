package com.example.hexnil.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Immutable evidence row tying a rule violation to a specific measurement.
 */
@Entity(tableName = "compliance_evidence")
public class ComplianceEvidence {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long timestamp;
    public String ruleId;
    public float metricValue;
    public float thresholdValue;
    public String sourceModule;   // Which module detected it
    public String severity;       // warning | critical | fail
    public String category;       // safety | thermal | charging
    public long sourceEventId;    // optional foreign id of originating event/session
    public String notes;          // small context blob

    public ComplianceEvidence() {
        this.timestamp = System.currentTimeMillis();
        this.severity = "warning";
        this.category = "";
    }
}
