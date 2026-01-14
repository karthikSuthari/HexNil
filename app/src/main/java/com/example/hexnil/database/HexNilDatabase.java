package com.example.hexnil.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.hexnil.model.AIAnalysisResult;
import com.example.hexnil.model.AppBatteryUsage;
import com.example.hexnil.model.BatteryHealth;
import com.example.hexnil.model.BatterySnapshot;
import com.example.hexnil.model.CalibrationData;
import com.example.hexnil.model.ChargingSession;
import com.example.hexnil.model.ComplianceEvidence;
import com.example.hexnil.model.ComplianceReport;
import com.example.hexnil.model.DeviceInfo;
import com.example.hexnil.model.DrainEvent;
import com.example.hexnil.model.AppDrainRank;
import com.example.hexnil.model.IdleDrainSession;
import com.example.hexnil.model.SafetyEvent;
import com.example.hexnil.model.StressTestResult;
import com.example.hexnil.model.ThermalEvent;
import com.example.hexnil.model.ThermalHeatBucket;
import com.example.hexnil.model.UpdateComparison;
import com.example.hexnil.model.AnomalyRecord;

/**
 * Main Room Database for HexNil Battery Analysis
 * Stores all battery-related data for historical analysis and reporting
 */
@Database(
    entities = {
        BatterySnapshot.class,
        ChargingSession.class,
        ThermalEvent.class,
        DrainEvent.class,
        AppBatteryUsage.class,
        AppDrainRank.class,
        BatteryHealth.class,
        IdleDrainSession.class,
        SafetyEvent.class,
        CalibrationData.class,
        StressTestResult.class,
        UpdateComparison.class,
        ThermalHeatBucket.class,
        AIAnalysisResult.class,
        DeviceInfo.class,
        ComplianceReport.class,
        ComplianceEvidence.class,
        AnomalyRecord.class
    },
    version = 19,
    exportSchema = true
)
public abstract class HexNilDatabase extends RoomDatabase {
    
    private static final String DATABASE_NAME = "hexnil_battery_db";
    private static volatile HexNilDatabase INSTANCE;
    
    // DAOs
    public abstract BatterySnapshotDao batterySnapshotDao();
    public abstract ChargingSessionDao chargingSessionDao();
    public abstract ThermalEventDao thermalEventDao();
    public abstract ThermalHeatBucketDao thermalHeatBucketDao();
    public abstract DrainEventDao drainEventDao();
    public abstract AppBatteryUsageDao appBatteryUsageDao();
    public abstract AppDrainRankDao appDrainRankDao();
    public abstract BatteryHealthDao batteryHealthDao();
    public abstract IdleDrainSessionDao idleDrainSessionDao();
    public abstract SafetyEventDao safetyEventDao();
    public abstract MiscDao miscDao();
    public abstract ComplianceEvidenceDao complianceEvidenceDao();
    public abstract ValidationDao validationDao();
    public abstract AnomalyRecordDao anomalyRecordDao();
    
    public static HexNilDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (HexNilDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            HexNilDatabase.class,
                            DATABASE_NAME
                    )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19)
                    .build();
                }
            }
        }
        return INSTANCE;
    }
    
    public static void destroyInstance() {
        INSTANCE = null;
    }

    // Migration: add expanded diagnostics columns to snapshots, app usage, and idle drain tables
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            // BatterySnapshot new columns
            db.execSQL("ALTER TABLE battery_snapshots ADD COLUMN cpuLoadTotal INTEGER NOT NULL DEFAULT -1");
            db.execSQL("ALTER TABLE battery_snapshots ADD COLUMN topAppPackage TEXT");
            db.execSQL("ALTER TABLE battery_snapshots ADD COLUMN gpuLoad INTEGER NOT NULL DEFAULT -1");
            db.execSQL("ALTER TABLE battery_snapshots ADD COLUMN networkState TEXT DEFAULT 'unknown'");
            db.execSQL("ALTER TABLE battery_snapshots ADD COLUMN chargerVendorId INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE battery_snapshots ADD COLUMN chargerProductId INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE battery_snapshots ADD COLUMN voltageSamplesJson TEXT DEFAULT '[]'");
            db.execSQL("ALTER TABLE battery_snapshots ADD COLUMN currentSamplesJson TEXT DEFAULT '[]'");

            // AppBatteryUsage new column
            db.execSQL("ALTER TABLE app_battery_usage ADD COLUMN partialWakelockTime INTEGER NOT NULL DEFAULT 0");

            // IdleDrainSession new columns
            db.execSQL("ALTER TABLE idle_drain_sessions ADD COLUMN dozeEntryTime INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE idle_drain_sessions ADD COLUMN dozeExitTime INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE idle_drain_sessions ADD COLUMN alarmWakeups INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE idle_drain_sessions ADD COLUMN networkWakeups INTEGER NOT NULL DEFAULT 0");
        }
    };

    // Migration: add charging curve, interruption evidence, and stability columns
    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE charging_sessions ADD COLUMN voltageCurveJson TEXT DEFAULT '[]'");
            db.execSQL("ALTER TABLE charging_sessions ADD COLUMN currentCurveJson TEXT DEFAULT '[]'");
            db.execSQL("ALTER TABLE charging_sessions ADD COLUMN curveSampleIntervalMs INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE charging_sessions ADD COLUMN curveStabilityScore REAL NOT NULL DEFAULT 100");
            db.execSQL("ALTER TABLE charging_sessions ADD COLUMN interruptionCount INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE charging_sessions ADD COLUMN interruptionTimestamps TEXT DEFAULT '[]'");
            db.execSQL("ALTER TABLE charging_sessions ADD COLUMN interruptionDurations TEXT DEFAULT '[]'");
        }
    };

    // Migration: add thermal heat buckets table
    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `thermal_heat_buckets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `startTime` INTEGER NOT NULL, `endTime` INTEGER NOT NULL, `tempBucket` TEXT, `hourBlock` INTEGER NOT NULL, `sampleCount` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, `chargingCount` INTEGER NOT NULL, `topForegroundApp` TEXT, `maxTemp` REAL NOT NULL, `avgTemp` REAL NOT NULL)");
        }
    };

    // Migration: add app drain rank table
    static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `app_drain_ranks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `windowLabel` TEXT, `windowStart` INTEGER NOT NULL, `windowEnd` INTEGER NOT NULL, `rank` INTEGER NOT NULL, `packageName` TEXT, `drainScore` REAL NOT NULL, `contributionPct` REAL NOT NULL, `screenTimeMs` INTEGER NOT NULL, `foregroundTimeMs` INTEGER NOT NULL, `backgroundTimeMs` INTEGER NOT NULL)");
        }
    };

    // Migration: add compliance evidence table and audit columns to reports
    static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `compliance_evidence` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `ruleId` TEXT, `metricValue` REAL NOT NULL, `thresholdValue` REAL NOT NULL, `sourceModule` TEXT, `severity` TEXT, `category` TEXT, `sourceEventId` INTEGER NOT NULL DEFAULT 0, `notes` TEXT)");
            db.execSQL("ALTER TABLE compliance_reports ADD COLUMN evidenceSummary TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE compliance_reports ADD COLUMN evidenceIds TEXT DEFAULT '[]'");
            db.execSQL("ALTER TABLE compliance_reports ADD COLUMN evidenceCount INTEGER NOT NULL DEFAULT 0");
        }
    };

    // Migration: add advanced health/aging columns
    static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE battery_health ADD COLUMN estimatedCapacityMah REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE battery_health ADD COLUMN capacityFadePercent REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE battery_health ADD COLUMN fadePer100Cycles REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE battery_health ADD COLUMN fadePerMonth REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE battery_health ADD COLUMN hasAbnormalAging INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE battery_health ADD COLUMN agingNotes TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE battery_health ADD COLUMN supplierAverageHealth REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE battery_health ADD COLUMN batchAverageHealth REAL NOT NULL DEFAULT 0");
        }
    };

    // Migration: wakelock/network wake metrics and deep sleep scores
    static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE app_battery_usage ADD COLUMN alarmWakeCount INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE app_battery_usage ADD COLUMN networkWakeCount INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE app_battery_usage ADD COLUMN wakelockDrainScore REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE idle_drain_sessions ADD COLUMN deepSleepSuccessRate REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE idle_drain_sessions ADD COLUMN dozeEffectivenessScore REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE idle_drain_sessions ADD COLUMN leakageClass TEXT DEFAULT 'NORMAL'");
        }
    };

    // Migration: OTA impact analytics deltas and chart payloads
    static final Migration MIGRATION_8_9 = new Migration(8, 9) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE update_comparisons ADD COLUMN preDrainRate REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE update_comparisons ADD COLUMN postDrainRate REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE update_comparisons ADD COLUMN drainRateDelta REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE update_comparisons ADD COLUMN drainRateChange REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE update_comparisons ADD COLUMN thermalPeakDelta REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE update_comparisons ADD COLUMN thermalPeakChange REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE update_comparisons ADD COLUMN preSeriesJson TEXT DEFAULT '[]'");
            db.execSQL("ALTER TABLE update_comparisons ADD COLUMN postSeriesJson TEXT DEFAULT '[]'");
        }
    };

    // Migration: Stress testing safety flags and new scores
    static final Migration MIGRATION_9_10 = new Migration(9, 10) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE stress_test_results ADD COLUMN highTemperatureScenario INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE stress_test_results ADD COLUMN safetyTriggerCount INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE stress_test_results ADD COLUMN safetyNotes TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE stress_test_results ADD COLUMN thermalResilienceScore REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE stress_test_results ADD COLUMN chargingStabilityScore REAL NOT NULL DEFAULT 0");
        }
    };

    // Migration: Calibration intelligence fields
    static final Migration MIGRATION_10_11 = new Migration(10, 11) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE calibration_data ADD COLUMN driftExceeded INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE calibration_data ADD COLUMN socMismatch INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE calibration_data ADD COLUMN correctedSoc REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE calibration_data ADD COLUMN correctionConfidence REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE calibration_data ADD COLUMN recommendationReason TEXT DEFAULT ''");
        }
    };

    // Migration: OEM-Grade snapshot validation and metadata fields
    static final Migration MIGRATION_11_12 = new Migration(11, 12) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            // Sequence and session tracking
            db.execSQL("ALTER TABLE battery_snapshots ADD COLUMN sequenceNumber INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE battery_snapshots ADD COLUMN elapsedRealtimeMs INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE battery_snapshots ADD COLUMN sessionId INTEGER NOT NULL DEFAULT 0");
            
            // Sampling metadata
            db.execSQL("ALTER TABLE battery_snapshots ADD COLUMN samplingContext TEXT DEFAULT 'normal'");
            db.execSQL("ALTER TABLE battery_snapshots ADD COLUMN samplingIntervalMs INTEGER NOT NULL DEFAULT 60000");
            db.execSQL("ALTER TABLE battery_snapshots ADD COLUMN timeSincePreviousMs INTEGER NOT NULL DEFAULT -1");
            
            // Validation fields
            db.execSQL("ALTER TABLE battery_snapshots ADD COLUMN validationStatus TEXT DEFAULT 'unchecked'");
            db.execSQL("ALTER TABLE battery_snapshots ADD COLUMN qualityScore INTEGER NOT NULL DEFAULT -1");
            db.execSQL("ALTER TABLE battery_snapshots ADD COLUMN validationFlags INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE battery_snapshots ADD COLUMN validationNotes TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE battery_snapshots ADD COLUMN sensorDataChanged INTEGER NOT NULL DEFAULT 1");
            
            // Create indices for efficient querying
            db.execSQL("CREATE INDEX IF NOT EXISTS index_battery_snapshots_timestamp ON battery_snapshots(timestamp)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_battery_snapshots_samplingContext ON battery_snapshots(samplingContext)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_battery_snapshots_isCharging_timestamp ON battery_snapshots(isCharging, timestamp)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_battery_snapshots_validationStatus ON battery_snapshots(validationStatus)");
        }
    };

    // Migration: OEM-Grade Battery Health & Degradation Engine fields
    static final Migration MIGRATION_12_13 = new Migration(12, 13) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            // =====================================================================
            // BatteryHealth: Capacity metrics
            // =====================================================================
            db.execSQL("ALTER TABLE battery_health ADD COLUMN measuredCapacityMah REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE battery_health ADD COLUMN capacityLossMah REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE battery_health ADD COLUMN stateOfHealth REAL NOT NULL DEFAULT 0");
            
            // =====================================================================
            // BatteryHealth: Enhanced cycle counting
            // =====================================================================
            db.execSQL("ALTER TABLE battery_health ADD COLUMN fullCycleCount INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE battery_health ADD COLUMN partialCycleAccumulator REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE battery_health ADD COLUMN deepCycleCount INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE battery_health ADD COLUMN topOffCycleCount INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE battery_health ADD COLUMN trackingDays INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE battery_health ADD COLUMN firstRecordTimestamp INTEGER NOT NULL DEFAULT 0");
            
            // =====================================================================
            // BatteryHealth: Enhanced degradation tracking
            // =====================================================================
            db.execSQL("ALTER TABLE battery_health ADD COLUMN fadePerCycle REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE battery_health ADD COLUMN smoothedDegradationRate REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE battery_health ADD COLUMN degradationAcceleration REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE battery_health ADD COLUMN predictedReplacementDate REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE battery_health ADD COLUMN eolConfidence REAL NOT NULL DEFAULT 0");
            
            // =====================================================================
            // BatteryHealth: Internal resistance
            // =====================================================================
            db.execSQL("ALTER TABLE battery_health ADD COLUMN resistance25C REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE battery_health ADD COLUMN resistanceIncrease REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE battery_health ADD COLUMN dischargeEfficiency REAL NOT NULL DEFAULT 95");
            
            // =====================================================================
            // BatteryHealth: Abnormal wear detection
            // =====================================================================
            db.execSQL("ALTER TABLE battery_health ADD COLUMN hasHighResistance INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE battery_health ADD COLUMN hasCapacityInstability INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE battery_health ADD COLUMN wearDeviation REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE battery_health ADD COLUMN wearSeverity INTEGER NOT NULL DEFAULT 0");
            
            // =====================================================================
            // BatteryHealth: Enhanced health scores
            // =====================================================================
            db.execSQL("ALTER TABLE battery_health ADD COLUMN degradationScore REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE battery_health ADD COLUMN thermalScore REAL NOT NULL DEFAULT 0");
            
            // =====================================================================
            // BatteryHealth: Supplier/batch quality tracking
            // =====================================================================
            db.execSQL("ALTER TABLE battery_health ADD COLUMN supplierDeviation REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE battery_health ADD COLUMN batchDeviation REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE battery_health ADD COLUMN supplierQualityTier TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE battery_health ADD COLUMN batchQualityAlert INTEGER NOT NULL DEFAULT 0");
            
            // =====================================================================
            // BatteryHealth: Calibration & confidence
            // =====================================================================
            db.execSQL("ALTER TABLE battery_health ADD COLUMN hasCalibrationCycle INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE battery_health ADD COLUMN dataPointCount INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE battery_health ADD COLUMN overallConfidence REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE battery_health ADD COLUMN capacityConfidence REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE battery_health ADD COLUMN cycleConfidence REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE battery_health ADD COLUMN degradationConfidence REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE battery_health ADD COLUMN meetsMinimumDataRequirement INTEGER NOT NULL DEFAULT 0");
            
            // =====================================================================
            // BatteryHealth: Indices for efficient querying
            // =====================================================================
            db.execSQL("CREATE INDEX IF NOT EXISTS index_battery_health_timestamp ON battery_health(timestamp)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_battery_health_manufacturer ON battery_health(manufacturer)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_battery_health_batchId ON battery_health(batchId)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_battery_health_healthPercentage ON battery_health(healthPercentage)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_battery_health_hasAbnormalWear ON battery_health(hasAbnormalWear)");
        }
    };

    // Migration: OEM-Grade Charging Behavior & Safety Analysis fields
    static final Migration MIGRATION_13_14 = new Migration(13, 14) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            // =====================================================================
            // ChargingSession: Peak and protocol fields
            // =====================================================================
            db.execSQL("ALTER TABLE charging_sessions ADD COLUMN peakLevel INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE charging_sessions ADD COLUMN chargingProtocol TEXT DEFAULT 'unknown'");
            db.execSQL("ALTER TABLE charging_sessions ADD COLUMN temperatureRise REAL NOT NULL DEFAULT 0");
            
            // =====================================================================
            // ChargingSession: CC/CV Phase tracking
            // =====================================================================
            db.execSQL("ALTER TABLE charging_sessions ADD COLUMN ccPhaseDuration INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE charging_sessions ADD COLUMN cvPhaseDuration INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE charging_sessions ADD COLUMN ccPhaseEndLevel INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE charging_sessions ADD COLUMN ccPhaseAvgCurrent INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE charging_sessions ADD COLUMN cvPhaseAvgCurrent INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE charging_sessions ADD COLUMN ccToCvTransitionVoltage INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE charging_sessions ADD COLUMN currentPhase TEXT DEFAULT 'unknown'");
            
            // =====================================================================
            // ChargingSession: Overnight charging detection
            // =====================================================================
            db.execSQL("ALTER TABLE charging_sessions ADD COLUMN isOvernightCharging INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE charging_sessions ADD COLUMN timeAtFullCharge INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE charging_sessions ADD COLUMN percentAtFullCharge INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE charging_sessions ADD COLUMN reachedFullCharge INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE charging_sessions ADD COLUMN fullChargeHoldCount INTEGER NOT NULL DEFAULT 0");
            
            // =====================================================================
            // ChargingSession: Temperature curve and sampling
            // =====================================================================
            db.execSQL("ALTER TABLE charging_sessions ADD COLUMN temperatureCurveJson TEXT DEFAULT '[]'");
            db.execSQL("ALTER TABLE charging_sessions ADD COLUMN curveSampleCount INTEGER NOT NULL DEFAULT 0");
            
            // =====================================================================
            // ChargingSession: Enhanced interruption tracking
            // =====================================================================
            db.execSQL("ALTER TABLE charging_sessions ADD COLUMN longestInterruption INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE charging_sessions ADD COLUMN totalInterruptionTime INTEGER NOT NULL DEFAULT 0");
            
            // =====================================================================
            // ChargingSession: Safety scoring
            // =====================================================================
            db.execSQL("ALTER TABLE charging_sessions ADD COLUMN safetyScore REAL NOT NULL DEFAULT -1");
            db.execSQL("ALTER TABLE charging_sessions ADD COLUMN temperatureScore REAL NOT NULL DEFAULT -1");
            db.execSQL("ALTER TABLE charging_sessions ADD COLUMN voltageScore REAL NOT NULL DEFAULT -1");
            db.execSQL("ALTER TABLE charging_sessions ADD COLUMN currentScore REAL NOT NULL DEFAULT -1");
            db.execSQL("ALTER TABLE charging_sessions ADD COLUMN stabilityScore REAL NOT NULL DEFAULT -1");
            db.execSQL("ALTER TABLE charging_sessions ADD COLUMN passedOEMCompliance INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE charging_sessions ADD COLUMN complianceNotes TEXT DEFAULT ''");
            
            // =====================================================================
            // ChargingSession: Charger fingerprinting
            // =====================================================================
            db.execSQL("ALTER TABLE charging_sessions ADD COLUMN chargerVendorId INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE charging_sessions ADD COLUMN chargerProductId INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE charging_sessions ADD COLUMN chargerClassification TEXT DEFAULT 'unknown'");
            db.execSQL("ALTER TABLE charging_sessions ADD COLUMN chargerPowerCapacity REAL NOT NULL DEFAULT 0");
            
            // =====================================================================
            // ChargingSession: Degradation impact assessment
            // =====================================================================
            db.execSQL("ALTER TABLE charging_sessions ADD COLUMN degradationImpact REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE charging_sessions ADD COLUMN cycleEquivalent REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE charging_sessions ADD COLUMN stressLevel TEXT DEFAULT 'none'");
            db.execSQL("ALTER TABLE charging_sessions ADD COLUMN degradationRiskLevel TEXT DEFAULT 'none'");
            db.execSQL("ALTER TABLE charging_sessions ADD COLUMN degradationNotes TEXT DEFAULT ''");
            
            // =====================================================================
            // ChargingSession: Context and metadata
            // =====================================================================
            db.execSQL("ALTER TABLE charging_sessions ADD COLUMN sessionContext TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE charging_sessions ADD COLUMN screenOnDuringCharge INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE charging_sessions ADD COLUMN topAppDuringCharge TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE charging_sessions ADD COLUMN samplesCollected INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE charging_sessions ADD COLUMN powerWatts REAL NOT NULL DEFAULT 0");
            
            // =====================================================================
            // ChargingSession: Indices for efficient querying
            // =====================================================================
            db.execSQL("CREATE INDEX IF NOT EXISTS index_charging_sessions_startTime ON charging_sessions(startTime)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_charging_sessions_endTime ON charging_sessions(endTime)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_charging_sessions_chargingType ON charging_sessions(chargingType)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_charging_sessions_safetyScore ON charging_sessions(safetyScore)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_charging_sessions_isOvernightCharging ON charging_sessions(isOvernightCharging)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_charging_sessions_chargerClassification ON charging_sessions(chargerClassification)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_charging_sessions_passedOEMCompliance ON charging_sessions(passedOEMCompliance)");
        }
    };

    // Migration: OEM-Grade Thermal Monitoring & Heat Intelligence fields
    static final Migration MIGRATION_14_15 = new Migration(14, 15) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            // =====================================================================
            // ThermalEvent: Spike detection fields
            // =====================================================================
            db.execSQL("ALTER TABLE thermal_events ADD COLUMN isSpike INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE thermal_events ADD COLUMN spikeRateOfChange REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE thermal_events ADD COLUMN spikeMagnitude REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE thermal_events ADD COLUMN spikeStartTime INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE thermal_events ADD COLUMN spikeStartTemp REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE thermal_events ADD COLUMN spikeCause TEXT DEFAULT ''");
            
            // =====================================================================
            // ThermalEvent: Sustained heat detection fields
            // =====================================================================
            db.execSQL("ALTER TABLE thermal_events ADD COLUMN isSustainedHeat INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE thermal_events ADD COLUMN sustainedState TEXT DEFAULT 'none'");
            db.execSQL("ALTER TABLE thermal_events ADD COLUMN sustainedDuration INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE thermal_events ADD COLUMN sustainedAvgTemp REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE thermal_events ADD COLUMN sustainedThreshold REAL NOT NULL DEFAULT 0");
            
            // =====================================================================
            // ThermalEvent: Correlation analysis fields
            // =====================================================================
            db.execSQL("ALTER TABLE thermal_events ADD COLUMN primaryCause TEXT DEFAULT 'unknown'");
            db.execSQL("ALTER TABLE thermal_events ADD COLUMN causeConfidence REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE thermal_events ADD COLUMN chargingContribution REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE thermal_events ADD COLUMN cpuContribution REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE thermal_events ADD COLUMN gpuContribution REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE thermal_events ADD COLUMN appContribution REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE thermal_events ADD COLUMN correlationNotes TEXT DEFAULT ''");
            
            // =====================================================================
            // ThermalEvent: Charging context fields
            // =====================================================================
            db.execSQL("ALTER TABLE thermal_events ADD COLUMN chargeCurrent REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE thermal_events ADD COLUMN chargeVoltage REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE thermal_events ADD COLUMN chargingType TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE thermal_events ADD COLUMN wasFastCharging INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE thermal_events ADD COLUMN chargerPower REAL NOT NULL DEFAULT 0");
            
            // =====================================================================
            // ThermalEvent: OEM threshold tracking fields
            // =====================================================================
            db.execSQL("ALTER TABLE thermal_events ADD COLUMN thresholdLevel TEXT DEFAULT 'normal'");
            db.execSQL("ALTER TABLE thermal_events ADD COLUMN nextThreshold REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE thermal_events ADD COLUMN headroom REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE thermal_events ADD COLUMN passesOEMCompliance INTEGER NOT NULL DEFAULT 1");
            db.execSQL("ALTER TABLE thermal_events ADD COLUMN requiresAction INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE thermal_events ADD COLUMN requiresEmergencyAction INTEGER NOT NULL DEFAULT 0");
            
            // =====================================================================
            // ThermalEvent: Degradation risk fields
            // =====================================================================
            db.execSQL("ALTER TABLE thermal_events ADD COLUMN degradationRisk REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE thermal_events ADD COLUMN estimatedCycleImpact REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE thermal_events ADD COLUMN degradationNotes TEXT DEFAULT ''");
            
            // =====================================================================
            // ThermalEvent: Thermal score and trend fields
            // =====================================================================
            db.execSQL("ALTER TABLE thermal_events ADD COLUMN thermalScore INTEGER NOT NULL DEFAULT 100");
            db.execSQL("ALTER TABLE thermal_events ADD COLUMN thermalTrend TEXT DEFAULT 'stable'");
            db.execSQL("ALTER TABLE thermal_events ADD COLUMN thermalZone TEXT DEFAULT '<35'");
            
            // =====================================================================
            // ThermalEvent: Resolution and sample tracking fields
            // =====================================================================
            db.execSQL("ALTER TABLE thermal_events ADD COLUMN cooldownRate REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE thermal_events ADD COLUMN sampleCount INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE thermal_events ADD COLUMN temperatureSamplesJson TEXT DEFAULT '[]'");
            
            // =====================================================================
            // ThermalEvent: Indices for efficient querying
            // =====================================================================
            db.execSQL("CREATE INDEX IF NOT EXISTS index_thermal_events_timestamp ON thermal_events(timestamp)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_thermal_events_severity ON thermal_events(severity)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_thermal_events_eventType ON thermal_events(eventType)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_thermal_events_isCharging ON thermal_events(isCharging)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_thermal_events_foregroundApp ON thermal_events(foregroundApp)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_thermal_events_isSpike ON thermal_events(isSpike)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_thermal_events_isSustainedHeat ON thermal_events(isSustainedHeat)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_thermal_events_primaryCause ON thermal_events(primaryCause)");
            
            // =====================================================================
            // ThermalHeatBucket: Temperature statistics
            // =====================================================================
            db.execSQL("ALTER TABLE thermal_heat_buckets ADD COLUMN minTemp REAL NOT NULL DEFAULT 100");
            db.execSQL("ALTER TABLE thermal_heat_buckets ADD COLUMN tempVariance REAL NOT NULL DEFAULT 0");
            
            // =====================================================================
            // ThermalHeatBucket: Charging correlation fields
            // =====================================================================
            db.execSQL("ALTER TABLE thermal_heat_buckets ADD COLUMN chargingPercent REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE thermal_heat_buckets ADD COLUMN avgTempCharging REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE thermal_heat_buckets ADD COLUMN avgTempNotCharging REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE thermal_heat_buckets ADD COLUMN avgChargeCurrent REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE thermal_heat_buckets ADD COLUMN fastChargingCount INTEGER NOT NULL DEFAULT 0");
            
            // =====================================================================
            // ThermalHeatBucket: CPU/GPU correlation fields
            // =====================================================================
            db.execSQL("ALTER TABLE thermal_heat_buckets ADD COLUMN highCpuCount INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE thermal_heat_buckets ADD COLUMN highGpuCount INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE thermal_heat_buckets ADD COLUMN avgCpuLoad REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE thermal_heat_buckets ADD COLUMN avgGpuLoad REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE thermal_heat_buckets ADD COLUMN avgTempHighCpu REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE thermal_heat_buckets ADD COLUMN avgTempHighGpu REAL NOT NULL DEFAULT 0");
            
            // =====================================================================
            // ThermalHeatBucket: App correlation fields
            // =====================================================================
            db.execSQL("ALTER TABLE thermal_heat_buckets ADD COLUMN screenOnCount INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE thermal_heat_buckets ADD COLUMN screenOnPercent REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE thermal_heat_buckets ADD COLUMN appTempContributionJson TEXT DEFAULT '{}'");
            
            // =====================================================================
            // ThermalHeatBucket: Context fields
            // =====================================================================
            db.execSQL("ALTER TABLE thermal_heat_buckets ADD COLUMN gamingCount INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE thermal_heat_buckets ADD COLUMN cameraCount INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE thermal_heat_buckets ADD COLUMN navigationCount INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE thermal_heat_buckets ADD COLUMN networkType TEXT DEFAULT ''");
            
            // =====================================================================
            // ThermalHeatBucket: Thermal events fields
            // =====================================================================
            db.execSQL("ALTER TABLE thermal_heat_buckets ADD COLUMN spikeCount INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE thermal_heat_buckets ADD COLUMN sustainedHeatCount INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE thermal_heat_buckets ADD COLUMN warningEventCount INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE thermal_heat_buckets ADD COLUMN criticalEventCount INTEGER NOT NULL DEFAULT 0");
            
            // =====================================================================
            // ThermalHeatBucket: Battery impact fields
            // =====================================================================
            db.execSQL("ALTER TABLE thermal_heat_buckets ADD COLUMN avgBatteryLevel REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE thermal_heat_buckets ADD COLUMN batteryDrain REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE thermal_heat_buckets ADD COLUMN degradationRisk REAL NOT NULL DEFAULT 0");
            
            // =====================================================================
            // ThermalHeatBucket: OEM metrics fields
            // =====================================================================
            db.execSQL("ALTER TABLE thermal_heat_buckets ADD COLUMN thermalScore REAL NOT NULL DEFAULT 100");
            db.execSQL("ALTER TABLE thermal_heat_buckets ADD COLUMN exceedsOEMLimit INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE thermal_heat_buckets ADD COLUMN oemComplianceStatus TEXT DEFAULT 'pass'");
            
            // =====================================================================
            // ThermalHeatBucket: Indices for efficient querying
            // =====================================================================
            db.execSQL("CREATE INDEX IF NOT EXISTS index_thermal_heat_buckets_startTime ON thermal_heat_buckets(startTime)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_thermal_heat_buckets_tempBucket ON thermal_heat_buckets(tempBucket)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_thermal_heat_buckets_hourBlock ON thermal_heat_buckets(hourBlock)");
        }
    };

    // Migration: OEM-Grade Battery Calibration & SOC Accuracy Intelligence fields
    static final Migration MIGRATION_15_16 = new Migration(15, 16) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            // =====================================================================
            // CalibrationData: Advanced drift metrics
            // =====================================================================
            db.execSQL("ALTER TABLE calibration_data ADD COLUMN emaDrift REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE calibration_data ADD COLUMN driftVelocity REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE calibration_data ADD COLUMN driftTrend REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE calibration_data ADD COLUMN driftSeverity TEXT DEFAULT 'none'");
            db.execSQL("ALTER TABLE calibration_data ADD COLUMN driftVariance REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE calibration_data ADD COLUMN driftStdDev REAL NOT NULL DEFAULT 0");
            
            // =====================================================================
            // CalibrationData: Enhanced sudden drop tracking
            // =====================================================================
            db.execSQL("ALTER TABLE calibration_data ADD COLUMN dropRatePerHour REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE calibration_data ADD COLUMN dropPattern TEXT DEFAULT 'none'");
            db.execSQL("ALTER TABLE calibration_data ADD COLUMN peakDropHour INTEGER NOT NULL DEFAULT 0");
            
            // =====================================================================
            // CalibrationData: Calibration cycle tracking
            // =====================================================================
            db.execSQL("ALTER TABLE calibration_data ADD COLUMN daysSinceFullCycle INTEGER NOT NULL DEFAULT 0");
            
            // =====================================================================
            // CalibrationData: Enhanced calibration quality
            // =====================================================================
            db.execSQL("ALTER TABLE calibration_data ADD COLUMN qualityGrade TEXT DEFAULT ''");
            
            // =====================================================================
            // CalibrationData: Calibration success criteria
            // =====================================================================
            db.execSQL("ALTER TABLE calibration_data ADD COLUMN avgDriftPassing INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE calibration_data ADD COLUMN noRecentDropsPassing INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE calibration_data ADD COLUMN velocityPassing INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE calibration_data ADD COLUMN hasRecentFullCharge INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE calibration_data ADD COLUMN hasRecentDeepDischarge INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE calibration_data ADD COLUMN criteriaMet INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE calibration_data ADD COLUMN calibrationScore REAL NOT NULL DEFAULT 0");
            
            // =====================================================================
            // CalibrationData: Estimation method tracking
            // =====================================================================
            db.execSQL("ALTER TABLE calibration_data ADD COLUMN chargeCounterEstimate REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE calibration_data ADD COLUMN chargeCounterConfidence REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE calibration_data ADD COLUMN voltageEstimate REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE calibration_data ADD COLUMN voltageConfidence REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE calibration_data ADD COLUMN energyEstimate REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE calibration_data ADD COLUMN energyConfidence REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE calibration_data ADD COLUMN capacityEstimate REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE calibration_data ADD COLUMN capacityConfidence REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE calibration_data ADD COLUMN primaryMethod TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE calibration_data ADD COLUMN methodAgreement REAL NOT NULL DEFAULT 0");
            
            // =====================================================================
            // CalibrationData: Confidence intervals
            // =====================================================================
            db.execSQL("ALTER TABLE calibration_data ADD COLUMN overallConfidence REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE calibration_data ADD COLUMN confidenceGrade TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE calibration_data ADD COLUMN confidenceIntervalLow REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE calibration_data ADD COLUMN confidenceIntervalHigh REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE calibration_data ADD COLUMN intervalWidth REAL NOT NULL DEFAULT 0");
            
            // =====================================================================
            // CalibrationData: Extended voltage correlation
            // =====================================================================
            db.execSQL("ALTER TABLE calibration_data ADD COLUMN voltageAt80 REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE calibration_data ADD COLUMN voltageCorrelationScore REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE calibration_data ADD COLUMN correlationGrade TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE calibration_data ADD COLUMN curveStretched INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE calibration_data ADD COLUMN curveCompressed INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE calibration_data ADD COLUMN voltageRangeRatio REAL NOT NULL DEFAULT 0");
            
            // =====================================================================
            // CalibrationData: Enhanced recommendations
            // =====================================================================
            db.execSQL("ALTER TABLE calibration_data ADD COLUMN calibrationUrgency TEXT DEFAULT 'none'");
            db.execSQL("ALTER TABLE calibration_data ADD COLUMN nextCalibrationDate INTEGER NOT NULL DEFAULT 0");
            
            // =====================================================================
            // CalibrationData: Health impact tracking
            // =====================================================================
            db.execSQL("ALTER TABLE calibration_data ADD COLUMN healthImpactLevel TEXT DEFAULT 'minimal'");
            db.execSQL("ALTER TABLE calibration_data ADD COLUMN affectsHealthAccuracy INTEGER NOT NULL DEFAULT 0");
            
            // =====================================================================
            // CalibrationData: History analysis fields
            // =====================================================================
            db.execSQL("ALTER TABLE calibration_data ADD COLUMN sampleCount INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE calibration_data ADD COLUMN volatilityScore REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE calibration_data ADD COLUMN trendDirection TEXT DEFAULT 'stable'");
            db.execSQL("ALTER TABLE calibration_data ADD COLUMN underReportingCount INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE calibration_data ADD COLUMN overReportingCount INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE calibration_data ADD COLUMN stableCount INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE calibration_data ADD COLUMN dominantPattern TEXT DEFAULT ''");
            
            // =====================================================================
            // CalibrationData: Indices for efficient querying
            // =====================================================================
            db.execSQL("CREATE INDEX IF NOT EXISTS index_calibration_data_timestamp ON calibration_data(timestamp)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_calibration_data_needsCalibration ON calibration_data(needsCalibration)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_calibration_data_hasSuddenDrops ON calibration_data(hasSuddenDrops)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_calibration_data_calibrationUrgency ON calibration_data(calibrationUrgency)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_calibration_data_isCalibrated ON calibration_data(isCalibrated)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_calibration_data_driftDirection ON calibration_data(driftDirection)");
        }
    };
    
    // =====================================================================================
    // MIGRATION 16 -> 17: Drain Intelligence Enhancement
    // Adds comprehensive drain analysis fields for screen state analysis,
    // expected vs actual drain modeling, deep sleep effectiveness tracking,
    // component-level attribution, and per-app drain profiling
    // =====================================================================================
    static final Migration MIGRATION_16_17 = new Migration(16, 17) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            
            // =====================================================================
            // DrainEvent: Extended screen state classification
            // =====================================================================
            db.execSQL("ALTER TABLE drain_events ADD COLUMN screenStateDetailed TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE drain_events ADD COLUMN screenStateGrade TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE drain_events ADD COLUMN brightnessCategory INTEGER NOT NULL DEFAULT 0");
            
            // =====================================================================
            // DrainEvent: Expected vs actual drain model
            // =====================================================================
            db.execSQL("ALTER TABLE drain_events ADD COLUMN expectedDrainRate REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE drain_events ADD COLUMN drainDelta REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE drain_events ADD COLUMN drainEfficiency REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE drain_events ADD COLUMN drainEfficiencyGrade TEXT DEFAULT ''");
            
            // =====================================================================
            // DrainEvent: Baseline comparison
            // =====================================================================
            db.execSQL("ALTER TABLE drain_events ADD COLUMN baselineScreenOnRate REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE drain_events ADD COLUMN baselineScreenOffRate REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE drain_events ADD COLUMN baselineDozeDrainRate REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE drain_events ADD COLUMN vsBaselineDelta REAL NOT NULL DEFAULT 0");
            
            // =====================================================================
            // DrainEvent: Benchmark comparison
            // =====================================================================
            db.execSQL("ALTER TABLE drain_events ADD COLUMN benchmarkResult TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE drain_events ADD COLUMN benchmarkDelta REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE drain_events ADD COLUMN benchmarkDrainRate REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE drain_events ADD COLUMN benchmarkNotes TEXT DEFAULT ''");
            
            // =====================================================================
            // DrainEvent: Deep sleep effectiveness
            // =====================================================================
            db.execSQL("ALTER TABLE drain_events ADD COLUMN deepSleepEffectiveness REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE drain_events ADD COLUMN deepSleepGrade TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE drain_events ADD COLUMN isDeepSleepActive INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE drain_events ADD COLUMN deepSleepPercentage REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE drain_events ADD COLUMN deepSleepTimeMs INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE drain_events ADD COLUMN lightSleepTimeMs INTEGER NOT NULL DEFAULT 0");
            
            // =====================================================================
            // DrainEvent: Idle drain quality
            // =====================================================================
            db.execSQL("ALTER TABLE drain_events ADD COLUMN idleDrainQuality TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE drain_events ADD COLUMN idleBenchmarkResult TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE drain_events ADD COLUMN idleDrainScore REAL NOT NULL DEFAULT 0");
            
            // =====================================================================
            // DrainEvent: Component-level drain attribution
            // =====================================================================
            db.execSQL("ALTER TABLE drain_events ADD COLUMN displayDrainPct REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE drain_events ADD COLUMN cpuDrainPct REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE drain_events ADD COLUMN networkDrainPct REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE drain_events ADD COLUMN sensorDrainPct REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE drain_events ADD COLUMN otherDrainPct REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE drain_events ADD COLUMN primaryDrainComponent TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE drain_events ADD COLUMN componentBreakdownJson TEXT DEFAULT ''");
            
            // =====================================================================
            // DrainEvent: Per-app drain profiling
            // =====================================================================
            db.execSQL("ALTER TABLE drain_events ADD COLUMN totalAppsContributing INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE drain_events ADD COLUMN topAppDrainPct REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE drain_events ADD COLUMN topAppPackage TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE drain_events ADD COLUMN topAppName TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE drain_events ADD COLUMN topAppIsSystem INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE drain_events ADD COLUMN topAppEfficiency REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE drain_events ADD COLUMN appDrainBreakdownJson TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE drain_events ADD COLUMN worstOffendersJson TEXT DEFAULT ''");
            
            // =====================================================================
            // DrainEvent: System vs user app comparison
            // =====================================================================
            db.execSQL("ALTER TABLE drain_events ADD COLUMN systemAppDrain REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE drain_events ADD COLUMN userAppDrain REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE drain_events ADD COLUMN systemAppDrainPct REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE drain_events ADD COLUMN userAppDrainPct REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE drain_events ADD COLUMN unattributedDrain REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE drain_events ADD COLUMN unattributedDrainPct REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE drain_events ADD COLUMN systemVsUserAnalysis TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE drain_events ADD COLUMN drainDominant TEXT DEFAULT ''");
            
            // =====================================================================
            // DrainEvent: Enhanced anomaly detection
            // =====================================================================
            db.execSQL("ALTER TABLE drain_events ADD COLUMN anomalySeverity TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE drain_events ADD COLUMN consecutiveAnomalies INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE drain_events ADD COLUMN anomalyReason TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE drain_events ADD COLUMN isPatternAnomaly INTEGER NOT NULL DEFAULT 0");
            
            // =====================================================================
            // DrainEvent: Drain trend tracking
            // =====================================================================
            db.execSQL("ALTER TABLE drain_events ADD COLUMN trendDirection TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE drain_events ADD COLUMN trendChangePercent REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE drain_events ADD COLUMN rollingAvgDrain REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE drain_events ADD COLUMN peakDrainRate REAL NOT NULL DEFAULT 0");
            
            // =====================================================================
            // DrainEvent: Thermal correlation
            // =====================================================================
            db.execSQL("ALTER TABLE drain_events ADD COLUMN avgTemperature INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE drain_events ADD COLUMN maxTemperature INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE drain_events ADD COLUMN thermalDrainImpact REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE drain_events ADD COLUMN wasOverheating INTEGER NOT NULL DEFAULT 0");
            
            // =====================================================================
            // DrainEvent: Quality metrics
            // =====================================================================
            db.execSQL("ALTER TABLE drain_events ADD COLUMN overallDrainScore REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE drain_events ADD COLUMN overallGrade TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE drain_events ADD COLUMN qualitySummary TEXT DEFAULT ''");
            
            // =====================================================================
            // DrainEvent: Indices for efficient querying
            // =====================================================================
            db.execSQL("CREATE INDEX IF NOT EXISTS index_drain_events_startTime ON drain_events(startTime)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_drain_events_screenStateDetailed ON drain_events(screenStateDetailed)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_drain_events_isAnomalous ON drain_events(isAnomalous)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_drain_events_benchmarkResult ON drain_events(benchmarkResult)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_drain_events_drainDominant ON drain_events(drainDominant)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_drain_events_topAppPackage ON drain_events(topAppPackage)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_drain_events_primaryDrainComponent ON drain_events(primaryDrainComponent)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_drain_events_isIdle ON drain_events(isIdle)");
        }
    };
    
    // =====================================================================
    // MIGRATION 17→18: AI-Ready Battery Intelligence - Anti-Hallucination Fields
    // Adds comprehensive AI readiness, deterministic vs AI separation, confidence tracking
    // =====================================================================
    static final Migration MIGRATION_17_18 = new Migration(17, 18) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            // =====================================================================
            // AI Readiness - Anti-Hallucination Gate Status
            // =====================================================================
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN aiWasAllowedToRun INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN aiBlockedReason TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN aiReadinessScore REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN aiReadinessDetails TEXT DEFAULT ''");
            
            // Data requirement checks
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN snapshotsAvailable INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN snapshotsRequired INTEGER NOT NULL DEFAULT 100");
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN observationHoursAvailable INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN observationHoursRequired INTEGER NOT NULL DEFAULT 24");
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN chargingSessionsAvailable INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN drainEventsAvailable INTEGER NOT NULL DEFAULT 0");
            
            // =====================================================================
            // Data Quality Metrics
            // =====================================================================
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN dataCompletenessScore REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN dataConsistencyScore REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN dataCoverageScore REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN dataQualityGrade TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN dataGaps TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN dataQualityIssues TEXT DEFAULT ''");
            
            // =====================================================================
            // Deterministic Results - Exact values, no AI
            // =====================================================================
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN deterministicResults TEXT DEFAULT ''");
            
            // Temperature (deterministic)
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN tempMinCelsius REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN tempMaxCelsius REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN tempMeanCelsius REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN tempStdDevCelsius REAL NOT NULL DEFAULT 0");
            
            // Voltage (deterministic)
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN voltageMinMv INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN voltageMaxMv INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN voltageMeanMv INTEGER NOT NULL DEFAULT 0");
            
            // Drain (deterministic)
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN drainRateMeanPerHour REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN drainRateMaxPerHour REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN drainRateStdDev REAL NOT NULL DEFAULT 0");
            
            // Charge cycles (deterministic)
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN equivalentChargeCycles REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN fullDischargeCount INTEGER NOT NULL DEFAULT 0");
            
            // Time distribution (deterministic)
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN screenOnTimeMs INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN chargingTimeMs INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN highTempTimeMs INTEGER NOT NULL DEFAULT 0");
            
            // =====================================================================
            // AI Interpretations - Insights with confidence
            // =====================================================================
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN aiInterpretations TEXT DEFAULT ''");
            
            // Health pattern inference
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN healthPatternInference TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN healthPatternConfidence REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN healthPatternEvidence TEXT DEFAULT ''");
            
            // Usage pattern inference
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN usagePatternInference TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN usagePatternConfidence REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN usagePatternCategory TEXT DEFAULT ''");
            
            // Charging behavior inference
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN chargingBehaviorInference TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN chargingBehaviorConfidence REAL NOT NULL DEFAULT 0");
            
            // Thermal stress inference
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN thermalStressInference TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN thermalStressConfidence REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN thermalStressLevel TEXT DEFAULT ''");
            
            // Drain anomaly inference
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN drainAnomalyInference TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN drainAnomalyConfidence REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN drainAnomaliesDetected INTEGER NOT NULL DEFAULT 0");
            
            // =====================================================================
            // Root Cause Analysis - With confidence and evidence
            // =====================================================================
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN rootCauseCode TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN rootCauseConfidence REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN rootCauseConfidenceLevel TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN rootCauseEvidence TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN alternativeHypotheses TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN rootCauseCaveats TEXT DEFAULT ''");
            
            // =====================================================================
            // Confidence Metrics
            // =====================================================================
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN overallConfidenceScore REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN overallConfidenceLevel TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN evidencePointCount INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN confidenceBreakdown TEXT DEFAULT ''");
            
            // Safety check results
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN passedSafetyChecks INTEGER NOT NULL DEFAULT 1");
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN safetyViolations TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN hasCriticalSafetyIssue INTEGER NOT NULL DEFAULT 0");
            
            // =====================================================================
            // Compliance & Audit
            // =====================================================================
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN analysisVersion TEXT DEFAULT '1.0.0'");
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN configurationUsed TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN disclaimers TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN includesAIContent INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN aiContentMarkers TEXT DEFAULT ''");
            
            // =====================================================================
            // OEM Structured Output
            // =====================================================================
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN structuredReport TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN headline TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN synopsis TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN batteryHealthStatus TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN primaryConcern TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE ai_analysis_results ADD COLUMN actionUrgency TEXT DEFAULT ''");
            
            // =====================================================================
            // Indices for efficient querying
            // =====================================================================
            db.execSQL("CREATE INDEX IF NOT EXISTS index_ai_analysis_results_aiWasAllowedToRun ON ai_analysis_results(aiWasAllowedToRun)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_ai_analysis_results_overallConfidenceScore ON ai_analysis_results(overallConfidenceScore)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_ai_analysis_results_rootCauseCode ON ai_analysis_results(rootCauseCode)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_ai_analysis_results_batteryHealthStatus ON ai_analysis_results(batteryHealthStatus)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_ai_analysis_results_hasCriticalSafetyIssue ON ai_analysis_results(hasCriticalSafetyIssue)");
        }
    };
    
    // =====================================================================
    // MIGRATION 18→19: Anomaly Detection Records
    // Stores detected anomalies with detailed descriptions for AI analysis
    // =====================================================================
    static final Migration MIGRATION_18_19 = new Migration(18, 19) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS anomaly_records (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "detectedAt INTEGER NOT NULL, " +
                    "anomalyType TEXT, " +
                    "severity TEXT, " +
                    "confidenceScore REAL NOT NULL DEFAULT 0, " +
                    "sourceApp TEXT, " +
                    "sourceAppName TEXT, " +
                    "sourceComponent TEXT, " +
                    "title TEXT, " +
                    "description TEXT, " +
                    "technicalDetails TEXT, " +
                    "expectedValue REAL NOT NULL DEFAULT 0, " +
                    "actualValue REAL NOT NULL DEFAULT 0, " +
                    "deviationPercent REAL NOT NULL DEFAULT 0, " +
                    "metricName TEXT, " +
                    "metricUnit TEXT, " +
                    "batteryLevel INTEGER NOT NULL DEFAULT 0, " +
                    "wasCharging INTEGER NOT NULL DEFAULT 0, " +
                    "screenOn INTEGER NOT NULL DEFAULT 0, " +
                    "deviceState TEXT, " +
                    "periodStart INTEGER NOT NULL DEFAULT 0, " +
                    "periodEnd INTEGER NOT NULL DEFAULT 0, " +
                    "dataPointsAnalyzed INTEGER NOT NULL DEFAULT 0, " +
                    "recommendation TEXT, " +
                    "rootCause TEXT, " +
                    "impact TEXT, " +
                    "acknowledged INTEGER NOT NULL DEFAULT 0, " +
                    "resolved INTEGER NOT NULL DEFAULT 0, " +
                    "resolvedAt INTEGER NOT NULL DEFAULT 0, " +
                    "resolution TEXT, " +
                    "relatedAnomalyId INTEGER NOT NULL DEFAULT 0, " +
                    "occurrenceCount INTEGER NOT NULL DEFAULT 1, " +
                    "isRecurring INTEGER NOT NULL DEFAULT 0" +
                    ")");
            
            // Create indices for efficient querying
            db.execSQL("CREATE INDEX IF NOT EXISTS index_anomaly_records_detectedAt ON anomaly_records(detectedAt)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_anomaly_records_anomalyType ON anomaly_records(anomalyType)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_anomaly_records_severity ON anomaly_records(severity)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_anomaly_records_sourceApp ON anomaly_records(sourceApp)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_anomaly_records_resolved ON anomaly_records(resolved)");
        }
    };
}
