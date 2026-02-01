package com.example.hexnil.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.hexnil.model.ChargingSession;

import java.util.List;

/**
 * Data Access Object for ChargingSession entity.
 * 
 * Provides comprehensive queries for:
 * - Session lifecycle management
 * - OEM reporting and compliance
 * - Charger pattern analysis
 * - Safety event tracking
 * - Efficiency trends
 * - Battery degradation impact
 */
@Dao
public interface ChargingSessionDao {
    
    // ========================================================================
    // BASIC CRUD
    // ========================================================================
    
    @Insert
    long insert(ChargingSession session);
    
    @Update
    void update(ChargingSession session);
    
    @Delete
    void delete(ChargingSession session);
    
    @Query("SELECT * FROM charging_sessions WHERE id = :id")
    ChargingSession getById(long id);
    
    @Query("SELECT * FROM charging_sessions ORDER BY startTime DESC")
    List<ChargingSession> getAll();
    
    @Query("SELECT COUNT(*) FROM charging_sessions")
    int getCount();
    
    @Query("SELECT COUNT(*) FROM charging_sessions WHERE startTime >= :startTime")
    int getCountSince(long startTime);
    
    // ========================================================================
    // SESSION RETRIEVAL
    // ========================================================================
    
    @Query("SELECT * FROM charging_sessions ORDER BY startTime DESC LIMIT 1")
    ChargingSession getLatest();
    
    @Query("SELECT * FROM charging_sessions WHERE endTime = 0 LIMIT 1")
    ChargingSession getActiveSession();
    
    @Query("SELECT * FROM charging_sessions WHERE startTime >= :startTime ORDER BY startTime DESC")
    List<ChargingSession> getSessionsSince(long startTime);
    
    @Query("SELECT * FROM charging_sessions ORDER BY startTime DESC LIMIT :limit")
    List<ChargingSession> getRecentSessions(int limit);
    
    @Query("SELECT * FROM charging_sessions WHERE endTime > 0 ORDER BY startTime DESC")
    List<ChargingSession> getCompletedSessions();
    
    @Query("SELECT * FROM charging_sessions WHERE endTime > 0 AND startTime >= :startTime ORDER BY startTime DESC")
    List<ChargingSession> getCompletedSessionsSince(long startTime);
    
    // ========================================================================
    // SAFETY QUERIES
    // ========================================================================
    
    @Query("SELECT * FROM charging_sessions WHERE safetyStatus = 'danger' OR safetyStatus = 'warning'")
    List<ChargingSession> getUnsafeSessions();
    
    @Query("SELECT * FROM charging_sessions WHERE safetyStatus = :status ORDER BY startTime DESC")
    List<ChargingSession> getSessionsBySafetyStatus(String status);
    
    @Query("SELECT * FROM charging_sessions WHERE passedOEMCompliance = 0 AND endTime > 0")
    List<ChargingSession> getNonCompliantSessions();
    
    @Query("SELECT COUNT(*) FROM charging_sessions WHERE overCurrentEvents > 0")
    int getOverCurrentSessionCount();
    
    @Query("SELECT COUNT(*) FROM charging_sessions WHERE overVoltageEvents > 0")
    int getOverVoltageSessionCount();
    
    @Query("SELECT COUNT(*) FROM charging_sessions WHERE overTempEvents > 0")
    int getOverTempSessionCount();
    
    @Query("SELECT SUM(overCurrentEvents) FROM charging_sessions WHERE startTime >= :startTime")
    int getTotalOverCurrentEvents(long startTime);
    
    @Query("SELECT SUM(overVoltageEvents) FROM charging_sessions WHERE startTime >= :startTime")
    int getTotalOverVoltageEvents(long startTime);
    
    @Query("SELECT SUM(overTempEvents) FROM charging_sessions WHERE startTime >= :startTime")
    int getTotalOverTempEvents(long startTime);
    
    @Query("SELECT AVG(safetyScore) FROM charging_sessions WHERE endTime > 0 AND startTime >= :startTime")
    float getAverageSafetyScore(long startTime);
    
    // ========================================================================
    // FAST CHARGING QUERIES
    // ========================================================================
    
    @Query("SELECT * FROM charging_sessions WHERE isFastCharging = 1 AND startTime >= :startTime ORDER BY startTime DESC")
    List<ChargingSession> getFastChargingSessions(long startTime);
    
    @Query("SELECT COUNT(*) FROM charging_sessions WHERE isFastCharging = 1 AND startTime >= :startTime")
    int getFastChargingSessionCount(long startTime);
    
    @Query("SELECT AVG(chargingSpeed) FROM charging_sessions WHERE isFastCharging = 1 AND endTime > 0 AND startTime >= :startTime")
    float getAverageFastChargingSpeed(long startTime);
    
    // ========================================================================
    // CHARGING METRICS
    // ========================================================================
    
    @Query("SELECT AVG(chargingSpeed) FROM charging_sessions WHERE endTime > 0 AND startTime >= :startTime")
    float getAverageChargingSpeed(long startTime);
    
    @Query("SELECT AVG(maxTemperature) FROM charging_sessions WHERE endTime > 0 AND startTime >= :startTime")
    float getAverageChargingTemp(long startTime);
    
    @Query("SELECT AVG(chargingEfficiency) FROM charging_sessions WHERE endTime > 0 AND chargingEfficiency > 0 AND startTime >= :startTime")
    float getAverageChargingEfficiency(long startTime);
    
    @Query("SELECT SUM(chargedAmount) FROM charging_sessions WHERE startTime >= :startTime")
    int getTotalChargedAmount(long startTime);
    
    @Query("SELECT SUM(energyDelivered) FROM charging_sessions WHERE startTime >= :startTime")
    float getTotalEnergyDelivered(long startTime);
    
    @Query("SELECT AVG(chargingDuration) FROM charging_sessions WHERE endTime > 0 AND startTime >= :startTime")
    float getAverageChargingDuration(long startTime);
    
    // ========================================================================
    // CHARGER QUALITY QUERIES
    // ========================================================================
    
    @Query("SELECT * FROM charging_sessions WHERE chargerQuality = :quality ORDER BY startTime DESC")
    List<ChargingSession> getSessionsByChargerQuality(String quality);
    
    @Query("SELECT chargerFingerprint, COUNT(*) as count FROM charging_sessions GROUP BY chargerFingerprint ORDER BY count DESC")
    List<ChargerUsageCount> getChargerUsageCounts();
    
    @Query("SELECT AVG(safetyScore) FROM charging_sessions WHERE chargerFingerprint = :fingerprint AND endTime > 0")
    float getAverageSafetyScoreForCharger(String fingerprint);
    
    @Query("SELECT * FROM charging_sessions WHERE chargerFingerprint = :fingerprint ORDER BY startTime DESC")
    List<ChargingSession> getSessionsForCharger(String fingerprint);
    
    @Query("SELECT COUNT(DISTINCT chargerFingerprint) FROM charging_sessions")
    int getUniqueChargerCount();
    
    @Query("SELECT * FROM charging_sessions WHERE isOriginalCharger = 1 ORDER BY startTime DESC LIMIT :limit")
    List<ChargingSession> getOEMChargerSessions(int limit);
    
    @Query("SELECT * FROM charging_sessions WHERE isOriginalCharger = 0 AND endTime > 0 ORDER BY startTime DESC LIMIT :limit")
    List<ChargingSession> getThirdPartyChargerSessions(int limit);
    
    // ========================================================================
    // OVERNIGHT CHARGING QUERIES
    // ========================================================================
    
    @Query("SELECT * FROM charging_sessions WHERE isOvernightCharging = 1 ORDER BY startTime DESC")
    List<ChargingSession> getOvernightChargingSessions();
    
    @Query("SELECT COUNT(*) FROM charging_sessions WHERE isOvernightCharging = 1 AND startTime >= :startTime")
    int getOvernightChargingCount(long startTime);
    
    @Query("SELECT AVG(timeAtFullCharge) FROM charging_sessions WHERE reachedFullCharge = 1 AND endTime > 0 AND startTime >= :startTime")
    float getAverageTimeAtFullCharge(long startTime);
    
    @Query("SELECT SUM(timeAtFullCharge) FROM charging_sessions WHERE startTime >= :startTime")
    long getTotalTimeAtFullCharge(long startTime);
    
    // ========================================================================
    // CC/CV PHASE ANALYSIS
    // ========================================================================
    
    @Query("SELECT AVG(ccPhaseDuration) FROM charging_sessions WHERE ccPhaseDuration > 0 AND endTime > 0 AND startTime >= :startTime")
    float getAverageCCPhaseDuration(long startTime);
    
    @Query("SELECT AVG(cvPhaseDuration) FROM charging_sessions WHERE cvPhaseDuration > 0 AND endTime > 0 AND startTime >= :startTime")
    float getAverageCVPhaseDuration(long startTime);
    
    @Query("SELECT AVG(ccPhaseEndLevel) FROM charging_sessions WHERE ccPhaseEndLevel > 0 AND endTime > 0 AND startTime >= :startTime")
    float getAverageCCPhaseEndLevel(long startTime);
    
    // ========================================================================
    // DEGRADATION IMPACT QUERIES
    // ========================================================================
    
    @Query("SELECT SUM(degradationImpact) FROM charging_sessions WHERE startTime >= :startTime")
    float getTotalDegradationImpact(long startTime);
    
    @Query("SELECT SUM(cycleEquivalent) FROM charging_sessions WHERE startTime >= :startTime")
    float getTotalCycleEquivalent(long startTime);
    
    @Query("SELECT AVG(stressLevel) FROM charging_sessions WHERE endTime > 0 AND startTime >= :startTime")
    float getAverageStressLevel(long startTime);
    
    @Query("SELECT * FROM charging_sessions WHERE degradationRiskLevel = 'high' ORDER BY startTime DESC")
    List<ChargingSession> getHighDegradationRiskSessions();
    
    // ========================================================================
    // CHARGING TYPE DISTRIBUTION
    // ========================================================================
    
    @Query("SELECT chargingType, COUNT(*) as count FROM charging_sessions WHERE endTime > 0 GROUP BY chargingType")
    List<ChargingTypeCount> getChargingTypeDistribution();
    
    @Query("SELECT chargingProtocol, COUNT(*) as count FROM charging_sessions WHERE endTime > 0 AND chargingProtocol IS NOT NULL GROUP BY chargingProtocol")
    List<ProtocolCount> getChargingProtocolDistribution();
    
    // ========================================================================
    // STABILITY ANALYSIS
    // ========================================================================
    
    @Query("SELECT AVG(curveStabilityScore) FROM charging_sessions WHERE endTime > 0 AND startTime >= :startTime")
    float getAverageStabilityScore(long startTime);
    
    @Query("SELECT SUM(interruptionCount) FROM charging_sessions WHERE startTime >= :startTime")
    int getTotalInterruptionCount(long startTime);
    
    @Query("SELECT AVG(currentFluctuations) FROM charging_sessions WHERE endTime > 0 AND startTime >= :startTime")
    float getAverageCurrentFluctuations(long startTime);
    
    @Query("SELECT AVG(voltageFluctuations) FROM charging_sessions WHERE endTime > 0 AND startTime >= :startTime")
    float getAverageVoltageFluctuations(long startTime);
    
    // ========================================================================
    // OEM REPORTING QUERIES
    // ========================================================================
    
    /**
     * Get charging sessions summary for OEM reporting.
     */
    @Query("SELECT COUNT(*) as totalSessions, " +
           "SUM(CASE WHEN passedOEMCompliance = 1 THEN 1 ELSE 0 END) as compliantSessions, " +
           "SUM(CASE WHEN safetyStatus = 'danger' THEN 1 ELSE 0 END) as dangerSessions, " +
           "AVG(safetyScore) as avgSafetyScore, " +
           "AVG(chargingEfficiency) as avgEfficiency, " +
           "SUM(overCurrentEvents + overVoltageEvents + overTempEvents) as totalSafetyEvents " +
           "FROM charging_sessions WHERE endTime > 0 AND startTime >= :startTime")
    ChargingSummary getChargingSummary(long startTime);
    
    /**
     * Get charger quality distribution for OEM reporting.
     */
    @Query("SELECT chargerQuality, COUNT(*) as count, AVG(safetyScore) as avgSafetyScore " +
           "FROM charging_sessions WHERE endTime > 0 GROUP BY chargerQuality")
    List<ChargerQualityStats> getChargerQualityStats();
    
    /**
     * Get temperature distribution during charging.
     */
    @Query("SELECT " +
           "SUM(CASE WHEN maxTemperature < 35 THEN 1 ELSE 0 END) as optimalCount, " +
           "SUM(CASE WHEN maxTemperature >= 35 AND maxTemperature < 40 THEN 1 ELSE 0 END) as acceptableCount, " +
           "SUM(CASE WHEN maxTemperature >= 40 AND maxTemperature < 45 THEN 1 ELSE 0 END) as warningCount, " +
           "SUM(CASE WHEN maxTemperature >= 45 THEN 1 ELSE 0 END) as criticalCount " +
           "FROM charging_sessions WHERE endTime > 0 AND startTime >= :startTime")
    ThermalDistribution getChargingThermalDistribution(long startTime);
    
    // ========================================================================
    // CLEANUP
    // ========================================================================
    
    @Query("DELETE FROM charging_sessions WHERE startTime < :beforeTime")
    void deleteOlderThan(long beforeTime);
    
    @Query("DELETE FROM charging_sessions WHERE endTime = 0 AND startTime < :beforeTime")
    void deleteOrphanedSessions(long beforeTime);
    
    // ========================================================================
    // RESULT CLASSES
    // ========================================================================
    
    class ChargerUsageCount {
        public String chargerFingerprint;
        public int count;
    }
    
    class ChargingTypeCount {
        public String chargingType;
        public int count;
    }
    
    class ProtocolCount {
        public String chargingProtocol;
        public int count;
    }
    
    class ChargingSummary {
        public int totalSessions;
        public int compliantSessions;
        public int dangerSessions;
        public float avgSafetyScore;
        public float avgEfficiency;
        public int totalSafetyEvents;
    }
    
    class ChargerQualityStats {
        public String chargerQuality;
        public int count;
        public float avgSafetyScore;
    }
    
    class ThermalDistribution {
        public int optimalCount;      // < 35°C
        public int acceptableCount;   // 35-40°C
        public int warningCount;      // 40-45°C
        public int criticalCount;     // >= 45°C
    }
}
