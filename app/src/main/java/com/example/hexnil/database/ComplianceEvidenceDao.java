package com.example.hexnil.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.hexnil.model.ComplianceEvidence;

import java.util.List;

@Dao
public interface ComplianceEvidenceDao {

    @Insert
    long insert(ComplianceEvidence evidence);

    @Insert
    List<Long> insertAll(List<ComplianceEvidence> evidence);

    @Query("SELECT * FROM compliance_evidence WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp DESC")
    List<ComplianceEvidence> getBetween(long start, long end);

    @Query("SELECT * FROM compliance_evidence WHERE ruleId = :ruleId ORDER BY timestamp DESC")
    List<ComplianceEvidence> getByRule(String ruleId);

    @Query("DELETE FROM compliance_evidence WHERE timestamp < :beforeTime")
    void deleteOlderThan(long beforeTime);
}
