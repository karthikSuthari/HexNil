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
        ComplianceEvidence.class
    },
    version = 11,
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
    
    public static HexNilDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (HexNilDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            HexNilDatabase.class,
                            DATABASE_NAME
                    )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
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
}
