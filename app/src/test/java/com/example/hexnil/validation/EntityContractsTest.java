package com.example.hexnil.validation;

import static org.junit.Assert.*;

import com.example.hexnil.model.*;

import org.junit.Test;
import org.junit.Before;

/**
 * Unit tests for EntityContracts validation per HexNil-AI specification.
 * 
 * Tests cover:
 * - Required field validation
 * - Range validation
 * - Duplicate detection
 * - Session integrity
 * - AI result constraints
 */
public class EntityContractsTest {

    // ═══════════════════════════════════════════════════════════════════════════════
    // BATTERY SNAPSHOT TESTS
    // ═══════════════════════════════════════════════════════════════════════════════

    @Test
    public void testBatterySnapshot_valid() {
        BatterySnapshot snapshot = new BatterySnapshot();
        snapshot.timestamp = System.currentTimeMillis();
        snapshot.level = 50;
        snapshot.voltage = 4000;
        snapshot.temperature = 350; // 35°C in tenths
        snapshot.status = 2; // BATTERY_STATUS_DISCHARGING
        snapshot.isCharging = false;

        EntityContracts.ContractValidationResult result = EntityContracts.BatterySnapshotContract.validate(snapshot);

        assertTrue("Valid snapshot should pass", result.isValid());
        assertFalse("No warnings expected", result.hasWarnings());
    }

    @Test
    public void testBatterySnapshot_invalidLevel() {
        BatterySnapshot snapshot = new BatterySnapshot();
        snapshot.timestamp = System.currentTimeMillis();
        snapshot.level = 150; // Invalid: > 100

        EntityContracts.ContractValidationResult result = EntityContracts.BatterySnapshotContract.validate(snapshot);

        assertFalse("Invalid level should fail", result.isValid());
        assertTrue("Should have violations", result.hasViolations());
    }

    @Test
    public void testBatterySnapshot_missingTimestamp() {
        BatterySnapshot snapshot = new BatterySnapshot();
        snapshot.timestamp = 0; // Missing
        snapshot.level = 50;

        EntityContracts.ContractValidationResult result = EntityContracts.BatterySnapshotContract.validate(snapshot);

        assertFalse("Missing timestamp should fail", result.isValid());
    }

    @Test
    public void testBatterySnapshot_extremeTemperature() {
        BatterySnapshot snapshot = new BatterySnapshot();
        snapshot.timestamp = System.currentTimeMillis();
        snapshot.level = 50;
        snapshot.temperature = 850; // 85°C - above safe limit but not error

        EntityContracts.ContractValidationResult result = EntityContracts.BatterySnapshotContract.validate(snapshot);

        assertTrue("Extreme temp should pass with warning", result.isValid());
        assertTrue("Should have temperature warning", result.hasWarnings());
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // CHARGING SESSION TESTS
    // ═══════════════════════════════════════════════════════════════════════════════

    @Test
    public void testChargingSession_valid() {
        ChargingSession session = new ChargingSession();
        session.startTime = System.currentTimeMillis() - 3600000; // 1 hour ago
        session.endTime = System.currentTimeMillis();
        session.startLevel = 20;
        session.endLevel = 80;

        EntityContracts.ContractValidationResult result = EntityContracts.ChargingSessionContract.validate(session);

        assertTrue("Valid session should pass", result.isValid());
    }

    @Test
    public void testChargingSession_incomplete() {
        ChargingSession session = new ChargingSession();
        session.startTime = System.currentTimeMillis() - 3600000;
        session.endTime = 0; // Not completed
        session.startLevel = 20;

        EntityContracts.ContractValidationResult result = EntityContracts.ChargingSessionContract.validate(session);

        assertTrue("Incomplete session should pass with warning", result.isValid());
        assertTrue("Should warn about incomplete", result.hasWarnings());
    }

    @Test
    public void testChargingSession_invalidLevelGain() {
        ChargingSession session = new ChargingSession();
        session.startTime = System.currentTimeMillis() - 3600000;
        session.endTime = System.currentTimeMillis();
        session.startLevel = 80;
        session.endLevel = 20; // Decreased - invalid for charging

        EntityContracts.ContractValidationResult result = EntityContracts.ChargingSessionContract.validate(session);

        assertFalse("End < Start should fail", result.isValid());
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // APP BATTERY USAGE TESTS
    // ═══════════════════════════════════════════════════════════════════════════════

    @Test
    public void testAppBatteryUsage_valid() {
        AppBatteryUsage usage = new AppBatteryUsage();
        usage.timestamp = System.currentTimeMillis();
        usage.packageName = "com.example.app";
        usage.batteryPercentUsed = 5.5f;
        usage.foregroundTime = 3600000;
        usage.backgroundTime = 1800000;

        EntityContracts.ContractValidationResult result = EntityContracts.AppBatteryUsageContract.validate(usage);

        assertTrue("Valid usage should pass", result.isValid());
    }

    @Test
    public void testAppBatteryUsage_missingPackageName() {
        AppBatteryUsage usage = new AppBatteryUsage();
        usage.timestamp = System.currentTimeMillis();
        usage.packageName = null; // Missing required field
        usage.batteryPercentUsed = 5.5f;

        EntityContracts.ContractValidationResult result = EntityContracts.AppBatteryUsageContract.validate(usage);

        assertFalse("Missing packageName should fail", result.isValid());
    }

    @Test
    public void testAppBatteryUsage_negativeTime() {
        AppBatteryUsage usage = new AppBatteryUsage();
        usage.timestamp = System.currentTimeMillis();
        usage.packageName = "com.example.app";
        usage.batteryPercentUsed = 5.5f;
        usage.foregroundTime = -100; // Invalid

        EntityContracts.ContractValidationResult result = EntityContracts.AppBatteryUsageContract.validate(usage);

        assertFalse("Negative time should fail", result.isValid());
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // ANOMALY RECORD TESTS
    // ═══════════════════════════════════════════════════════════════════════════════

    @Test
    public void testAnomalyRecord_valid() {
        AnomalyRecord anomaly = new AnomalyRecord();
        anomaly.detectedAt = System.currentTimeMillis();
        anomaly.confidenceScore = 0.85f;
        anomaly.anomalyType = "drain";
        anomaly.severity = "high";
        anomaly.batteryLevel = 45;

        EntityContracts.ContractValidationResult result = EntityContracts.AnomalyRecordContract.validate(anomaly);

        assertTrue("Valid anomaly should pass", result.isValid());
    }

    @Test
    public void testAnomalyRecord_invalidConfidence() {
        AnomalyRecord anomaly = new AnomalyRecord();
        anomaly.detectedAt = System.currentTimeMillis();
        anomaly.confidenceScore = 1.5f; // > 1.0 is invalid

        EntityContracts.ContractValidationResult result = EntityContracts.AnomalyRecordContract.validate(anomaly);

        assertFalse("Confidence > 1 should fail", result.isValid());
    }

    @Test
    public void testAnomalyRecord_unrecognizedType() {
        AnomalyRecord anomaly = new AnomalyRecord();
        anomaly.detectedAt = System.currentTimeMillis();
        anomaly.confidenceScore = 0.5f;
        anomaly.anomalyType = "unknown_type"; // Not in valid list

        EntityContracts.ContractValidationResult result = EntityContracts.AnomalyRecordContract.validate(anomaly);

        assertTrue("Unrecognized type should pass with warning", result.isValid());
        assertTrue("Should warn about type", result.hasWarnings());
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // IDLE DRAIN SESSION TESTS
    // ═══════════════════════════════════════════════════════════════════════════════

    @Test
    public void testIdleDrainSession_extremeDrain() {
        IdleDrainSession session = new IdleDrainSession();
        session.startTime = System.currentTimeMillis();
        session.startLevel = 80;
        session.endLevel = 70;
        session.drainPerHour = 15.0f; // Extreme for idle

        EntityContracts.ContractValidationResult result = EntityContracts.IdleDrainSessionContract.validate(session);

        assertTrue("Should pass with warning", result.isValid());
        assertTrue("Should warn about high drain", result.hasWarnings());
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // SAFETY EVENT TESTS
    // ═══════════════════════════════════════════════════════════════════════════════

    @Test
    public void testSafetyEvent_valid() {
        SafetyEvent event = new SafetyEvent();
        event.timestamp = System.currentTimeMillis();
        event.eventType = "over_temp";
        event.severity = "critical";
        event.batteryTemperature = 48.0f;

        EntityContracts.ContractValidationResult result = EntityContracts.SafetyEventContract.validate(event);

        assertTrue("Valid safety event should pass", result.isValid());
    }

    @Test
    public void testSafetyEvent_extremeTemperature() {
        SafetyEvent event = new SafetyEvent();
        event.timestamp = System.currentTimeMillis();
        event.eventType = "over_temp";
        event.batteryTemperature = 95.0f; // Beyond 80°C

        EntityContracts.ContractValidationResult result = EntityContracts.SafetyEventContract.validate(event);

        assertTrue("Should pass with warning", result.isValid());
        assertTrue("Should warn about extreme temp", result.hasWarnings());
    }
}
