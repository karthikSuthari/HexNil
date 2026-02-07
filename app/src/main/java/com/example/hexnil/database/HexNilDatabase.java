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
import com.example.hexnil.model.RefreshRateSession;
import com.example.hexnil.model.RefreshRateAnomaly;
import com.example.hexnil.model.CpuGpuLoadSample;
import com.example.hexnil.model.MemorySnapshot;
import com.example.hexnil.model.AppLaunchMetric;
import com.example.hexnil.model.FrameMetric;
import com.example.hexnil.model.CrashEvent;
import com.example.hexnil.model.AnrEvent;
import com.example.hexnil.model.DailyAggregate;
import com.example.hexnil.model.RegressionEvent;
import com.example.hexnil.model.NetworkCellularSnapshot;
import com.example.hexnil.model.NetworkWifiSnapshot;
import com.example.hexnil.model.NetworkBluetoothSnapshot;
import com.example.hexnil.model.ModemFirmwareEvent;
import com.example.hexnil.model.UpdateSession;
import com.example.hexnil.model.PreUpdateSnapshot;
import com.example.hexnil.model.PreUpdateIssue;
import com.example.hexnil.model.PostUpdateVerification;
import com.example.hexnil.model.UpdateRegression;
import com.example.hexnil.model.DeviceProfile;
import com.example.hexnil.model.DeviceBaselineThreshold;
import com.example.hexnil.model.HumanLabel;
import com.example.hexnil.model.LabelStatistics;
import com.example.hexnil.model.NegativeExample;
import com.example.hexnil.model.MLModel;
import com.example.hexnil.model.MLPrediction;
import com.example.hexnil.model.ComparisonStatistics;

/**
 * Main Room Database for HexNil Battery Analysis
 * Stores all battery-related data for historical analysis and reporting
 */
@Database(entities = {
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
                AnomalyRecord.class,
                RefreshRateSession.class,
                RefreshRateAnomaly.class,
                CpuGpuLoadSample.class,
                MemorySnapshot.class,
                AppLaunchMetric.class,
                FrameMetric.class,
                CrashEvent.class,
                AnrEvent.class,
                DailyAggregate.class,
                RegressionEvent.class,
                NetworkCellularSnapshot.class,
                NetworkWifiSnapshot.class,
                NetworkBluetoothSnapshot.class,
                ModemFirmwareEvent.class,
                UpdateSession.class,
                PreUpdateSnapshot.class,
                PreUpdateIssue.class,
                PostUpdateVerification.class,
                UpdateRegression.class,
                DeviceProfile.class,
                DeviceBaselineThreshold.class,
                HumanLabel.class,
                LabelStatistics.class,
                NegativeExample.class,
                MLModel.class,
                MLPrediction.class,
                ComparisonStatistics.class
}, version = 29, exportSchema = true)
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

        public abstract RefreshRateSessionDao refreshRateSessionDao();

        public abstract RefreshRateAnomalyDao refreshRateAnomalyDao();

        public abstract CpuGpuLoadSampleDao cpuGpuLoadSampleDao();

        public abstract MemorySnapshotDao memorySnapshotDao();

        public abstract AppLaunchMetricDao appLaunchMetricDao();

        public abstract FrameMetricDao frameMetricDao();

        public abstract CrashEventDao crashEventDao();

        public abstract AnrEventDao anrEventDao();

        public abstract DailyAggregateDao dailyAggregateDao();

        public abstract RegressionEventDao regressionEventDao();

        public abstract NetworkCellularSnapshotDao networkCellularSnapshotDao();

        public abstract NetworkWifiSnapshotDao networkWifiSnapshotDao();

        public abstract NetworkBluetoothSnapshotDao networkBluetoothSnapshotDao();

        public abstract ModemFirmwareEventDao modemFirmwareEventDao();

        public abstract UpdateSessionDao updateSessionDao();

        public abstract PreUpdateSnapshotDao preUpdateSnapshotDao();

        public abstract PreUpdateIssueDao preUpdateIssueDao();

        public abstract PostUpdateVerificationDao postUpdateVerificationDao();

        public abstract UpdateRegressionDao updateRegressionDao();

        // ML Infrastructure DAOs
        public abstract DeviceProfileDao deviceProfileDao();

        public abstract DeviceBaselineThresholdDao deviceBaselineThresholdDao();

        public abstract HumanLabelDao humanLabelDao();

        public abstract LabelStatisticsDao labelStatisticsDao();

        public abstract NegativeExampleDao negativeExampleDao();

        public abstract MLModelDao mlModelDao();

        public abstract MLPredictionDao mlPredictionDao();

        public abstract ComparisonStatisticsDao comparisonStatisticsDao();

        public static HexNilDatabase getInstance(Context context) {
                if (INSTANCE == null) {
                        synchronized (HexNilDatabase.class) {
                                if (INSTANCE == null) {
                                        INSTANCE = Room.databaseBuilder(
                                                        context.getApplicationContext(),
                                                        HexNilDatabase.class,
                                                        DATABASE_NAME)
                                                        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
                                                                        MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
                                                                        MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10,
                                                                        MIGRATION_10_11, MIGRATION_11_12,
                                                                        MIGRATION_12_13, MIGRATION_13_14,
                                                                        MIGRATION_14_15, MIGRATION_15_16,
                                                                        MIGRATION_16_17, MIGRATION_17_18,
                                                                        MIGRATION_18_19, MIGRATION_19_20,
                                                                        MIGRATION_20_21,
                                                                        MIGRATION_21_22,
                                                                        MIGRATION_22_23,
                                                                        MIGRATION_23_24,
                                                                        MIGRATION_24_25,
                                                                        MIGRATION_25_26,
                                                                        MIGRATION_26_27,
                                                                        MIGRATION_27_28,
                                                                        MIGRATION_28_29)
                                                        .build();
                                }
                        }
                }
                return INSTANCE;
        }

        public static void destroyInstance() {
                INSTANCE = null;
        }

        // Migration: add expanded diagnostics columns to snapshots, app usage, and idle
        // drain tables
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
        // Adds comprehensive AI readiness, deterministic vs AI separation, confidence
        // tracking
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
                        db.execSQL("CREATE TABLE IF NOT EXISTS anomaly_records ("
                                        + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                                        + "detectedAt INTEGER NOT NULL, " + "anomalyType TEXT, " + "severity TEXT, "
                                        + "confidenceScore REAL NOT NULL DEFAULT 0, " + "sourceApp TEXT, "
                                        + "sourceAppName TEXT, " + "sourceComponent TEXT, " + "title TEXT, "
                                        + "description TEXT, " + "technicalDetails TEXT, "
                                        + "expectedValue REAL NOT NULL DEFAULT 0, "
                                        + "actualValue REAL NOT NULL DEFAULT 0, "
                                        + "deviationPercent REAL NOT NULL DEFAULT 0, " + "metricName TEXT, "
                                        + "metricUnit TEXT, " + "batteryLevel INTEGER NOT NULL DEFAULT 0, "
                                        + "wasCharging INTEGER NOT NULL DEFAULT 0, "
                                        + "screenOn INTEGER NOT NULL DEFAULT 0, " + "deviceState TEXT, "
                                        + "periodStart INTEGER NOT NULL DEFAULT 0, "
                                        + "periodEnd INTEGER NOT NULL DEFAULT 0, "
                                        + "dataPointsAnalyzed INTEGER NOT NULL DEFAULT 0, " + "recommendation TEXT, "
                                        + "rootCause TEXT, " + "impact TEXT, "
                                        + "acknowledged INTEGER NOT NULL DEFAULT 0, "
                                        + "resolved INTEGER NOT NULL DEFAULT 0, "
                                        + "resolvedAt INTEGER NOT NULL DEFAULT 0, " + "resolution TEXT, "
                                        + "relatedAnomalyId INTEGER NOT NULL DEFAULT 0, "
                                        + "occurrenceCount INTEGER NOT NULL DEFAULT 1, "
                                        + "isRecurring INTEGER NOT NULL DEFAULT 0" + ")");

                        // Create indices for efficient querying
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_anomaly_records_detectedAt ON anomaly_records(detectedAt)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_anomaly_records_anomalyType ON anomaly_records(anomalyType)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_anomaly_records_severity ON anomaly_records(severity)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_anomaly_records_sourceApp ON anomaly_records(sourceApp)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_anomaly_records_resolved ON anomaly_records(resolved)");
                }
        };

        // =====================================================================
        // Adds refresh_rate_sessions and refresh_rate_anomalies tables for
        // OEM-grade display refresh rate monitoring and anomaly detection.
        // Sessions store aggregated metrics only (no raw continuous samples).
        // Anomalies use deterministic cause classification (no AI guessing).
        // =====================================================================
        static final Migration MIGRATION_19_20 = new Migration(19, 20) {
                @Override
                public void migrate(SupportSQLiteDatabase db) {
                        // =====================================================================
                        // Create refresh_rate_sessions table
                        // Stores ONE row per foreground app/home session with aggregated metrics
                        // =====================================================================
                        db.execSQL("CREATE TABLE IF NOT EXISTS refresh_rate_sessions ("
                                        + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " + "sessionId TEXT, "
                                        + "sessionType TEXT, " + "packageName TEXT, " + "appName TEXT, "
                                        + "startTime INTEGER NOT NULL DEFAULT 0, "
                                        + "endTime INTEGER NOT NULL DEFAULT 0, "
                                        + "durationMs INTEGER NOT NULL DEFAULT 0, "
                                        + "startRefreshRateHz REAL NOT NULL DEFAULT 0, "
                                        + "endRefreshRateHz REAL NOT NULL DEFAULT 0, "
                                        + "avgRefreshRateHz REAL NOT NULL DEFAULT 0, "
                                        + "minRefreshRateHz REAL NOT NULL DEFAULT 0, "
                                        + "maxRefreshRateHz REAL NOT NULL DEFAULT 0, "
                                        + "timeAtHighHzMs INTEGER NOT NULL DEFAULT 0, "
                                        + "timeAtLowHzMs INTEGER NOT NULL DEFAULT 0, "
                                        + "isAdaptiveDisplay INTEGER NOT NULL DEFAULT 0, "
                                        + "avgBrightness REAL NOT NULL DEFAULT 0, "
                                        + "avgCpuUsage REAL NOT NULL DEFAULT 0, "
                                        + "avgGpuUsage REAL NOT NULL DEFAULT 0, "
                                        + "avgBatteryCurrent REAL NOT NULL DEFAULT 0, "
                                        + "avgTemperature REAL NOT NULL DEFAULT 0, "
                                        + "estimatedBatteryCost REAL NOT NULL DEFAULT 0, "
                                        + "anomalyCount INTEGER NOT NULL DEFAULT 0, "
                                        + "hasStrongAnomaly INTEGER NOT NULL DEFAULT 0" + ")");

                        // Indices for refresh_rate_sessions
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_refresh_rate_sessions_startTime ON refresh_rate_sessions(startTime)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_refresh_rate_sessions_packageName ON refresh_rate_sessions(packageName)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_refresh_rate_sessions_sessionType ON refresh_rate_sessions(sessionType)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_refresh_rate_sessions_hasStrongAnomaly ON refresh_rate_sessions(hasStrongAnomaly)");

                        // =====================================================================
                        // Create refresh_rate_anomalies table
                        // Stores anomalies with deterministic cause classification
                        // =====================================================================
                        db.execSQL("CREATE TABLE IF NOT EXISTS refresh_rate_anomalies ("
                                        + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                                        + "linkedSessionId INTEGER NOT NULL DEFAULT 0, " + "packageName TEXT, "
                                        + "sessionType TEXT, " + "detectedAt INTEGER NOT NULL DEFAULT 0, "
                                        + "anomalyType TEXT, " + "severity TEXT, "
                                        + "confidenceScore REAL NOT NULL DEFAULT 0, " + "causeCategory TEXT, "
                                        + "causeExplanation TEXT, " + "evidenceMetricsJson TEXT, "
                                        + "refreshRateHz REAL NOT NULL DEFAULT 0, "
                                        + "cpuUsage REAL NOT NULL DEFAULT 0, " + "gpuUsage REAL NOT NULL DEFAULT 0, "
                                        + "temperature REAL NOT NULL DEFAULT 0, "
                                        + "brightness REAL NOT NULL DEFAULT 0, "
                                        + "batteryCurrent REAL NOT NULL DEFAULT 0, "
                                        + "wasCharging INTEGER NOT NULL DEFAULT 0, "
                                        + "screenOn INTEGER NOT NULL DEFAULT 1" + ")");

                        // Indices for refresh_rate_anomalies
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_refresh_rate_anomalies_linkedSessionId ON refresh_rate_anomalies(linkedSessionId)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_refresh_rate_anomalies_detectedAt ON refresh_rate_anomalies(detectedAt)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_refresh_rate_anomalies_anomalyType ON refresh_rate_anomalies(anomalyType)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_refresh_rate_anomalies_severity ON refresh_rate_anomalies(severity)");

                        // =====================================================================
                        // Add refresh rate columns to update_comparisons table
                        // For OTA regression detection of display refresh rate changes
                        // =====================================================================
                        db.execSQL("ALTER TABLE update_comparisons ADD COLUMN preAvgRefreshRate REAL NOT NULL DEFAULT 0");
                        db.execSQL("ALTER TABLE update_comparisons ADD COLUMN preHighRefreshTimeMs INTEGER NOT NULL DEFAULT 0");
                        db.execSQL("ALTER TABLE update_comparisons ADD COLUMN preRefreshBatteryImpact REAL NOT NULL DEFAULT 0");
                        db.execSQL("ALTER TABLE update_comparisons ADD COLUMN postAvgRefreshRate REAL NOT NULL DEFAULT 0");
                        db.execSQL("ALTER TABLE update_comparisons ADD COLUMN postHighRefreshTimeMs INTEGER NOT NULL DEFAULT 0");
                        db.execSQL("ALTER TABLE update_comparisons ADD COLUMN postRefreshBatteryImpact REAL NOT NULL DEFAULT 0");
                        db.execSQL("ALTER TABLE update_comparisons ADD COLUMN refreshRateDelta REAL NOT NULL DEFAULT 0");
                        db.execSQL("ALTER TABLE update_comparisons ADD COLUMN highRefreshTimeDelta INTEGER NOT NULL DEFAULT 0");
                        db.execSQL("ALTER TABLE update_comparisons ADD COLUMN refreshBatteryImpactDelta REAL NOT NULL DEFAULT 0");
                        db.execSQL("ALTER TABLE update_comparisons ADD COLUMN refreshThermalImpactDelta REAL NOT NULL DEFAULT 0");
                        db.execSQL("ALTER TABLE update_comparisons ADD COLUMN refreshRateChange REAL NOT NULL DEFAULT 0");
                        db.execSQL("ALTER TABLE update_comparisons ADD COLUMN highRefreshTimeChange REAL NOT NULL DEFAULT 0");
                        db.execSQL("ALTER TABLE update_comparisons ADD COLUMN hasRefreshRateRegression INTEGER NOT NULL DEFAULT 0");
                }
        };

        // Migration: Add appName to refresh_rate_anomalies
        static final Migration MIGRATION_20_21 = new Migration(20, 21) {
                @Override
                public void migrate(SupportSQLiteDatabase db) {
                        db.execSQL("ALTER TABLE refresh_rate_anomalies ADD COLUMN appName TEXT DEFAULT ''");
                }
        };

        // Migration: Add CPU/GPU load samples table for continuous stress monitoring
        static final Migration MIGRATION_21_22 = new Migration(21, 22) {
                @Override
                public void migrate(SupportSQLiteDatabase db) {
                        // Create CPU/GPU load samples table
                        db.execSQL("CREATE TABLE IF NOT EXISTS `cpu_gpu_load_samples` (" +
                                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                        "`timestamp` INTEGER NOT NULL, " +
                                        "`appName` TEXT, " +
                                        "`packageName` TEXT, " +
                                        "`pid` INTEGER NOT NULL DEFAULT 0, " +
                                        "`uid` INTEGER NOT NULL DEFAULT 0, " +
                                        "`isSystemApp` INTEGER NOT NULL DEFAULT 0, " +
                                        "`systemCpuPercent` INTEGER NOT NULL DEFAULT -1, " +
                                        "`appCpuPercent` INTEGER NOT NULL DEFAULT -1, " +
                                        "`gpuPercent` INTEGER NOT NULL DEFAULT -1, " +
                                        "`isGpuAvailable` INTEGER NOT NULL DEFAULT 1, " +
                                        "`load1m` REAL NOT NULL DEFAULT 0, " +
                                        "`load5m` REAL NOT NULL DEFAULT 0, " +
                                        "`load15m` REAL NOT NULL DEFAULT 0, " +
                                        "`runnableProcesses` INTEGER NOT NULL DEFAULT 0, " +
                                        "`temperature` REAL NOT NULL DEFAULT 0, " +
                                        "`batteryLevel` INTEGER NOT NULL DEFAULT 0, " +
                                        "`drainRatePerHour` REAL NOT NULL DEFAULT 0, " +
                                        "`isThrottled` INTEGER NOT NULL DEFAULT 0, " +
                                        "`throttleLevel` INTEGER NOT NULL DEFAULT 0, " +
                                        "`stressScore` REAL NOT NULL DEFAULT 0, " +
                                        "`stressCategory` TEXT DEFAULT 'LOW', " +
                                        "`stressExplanation` TEXT, " +
                                        "`sourceType` TEXT DEFAULT 'system', " +
                                        "`validationStatus` TEXT DEFAULT 'unchecked', " +
                                        "`validationNotes` TEXT, " +
                                        "`isActive` INTEGER NOT NULL DEFAULT 1)");

                        // Create indices for efficient querying
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_cpu_gpu_load_samples_timestamp ON cpu_gpu_load_samples(timestamp)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_cpu_gpu_load_samples_packageName ON cpu_gpu_load_samples(packageName)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_cpu_gpu_load_samples_stressCategory ON cpu_gpu_load_samples(stressCategory)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_cpu_gpu_load_samples_sourceType_timestamp ON cpu_gpu_load_samples(sourceType, timestamp)");
                }
        };

        // Migration: Add thermalStatus to cpu_gpu_load_samples
        static final Migration MIGRATION_22_23 = new Migration(22, 23) {
                @Override
                public void migrate(SupportSQLiteDatabase db) {
                        db.execSQL("ALTER TABLE cpu_gpu_load_samples ADD COLUMN thermalStatus INTEGER NOT NULL DEFAULT 0");
                }
        };

        // Migration: Add inference fields to cpu_gpu_load_samples
        static final Migration MIGRATION_23_24 = new Migration(23, 24) {
                @Override
                public void migrate(SupportSQLiteDatabase db) {
                        db.execSQL("ALTER TABLE cpu_gpu_load_samples ADD COLUMN inferredLoad TEXT DEFAULT 'LOW'");
                        db.execSQL("ALTER TABLE cpu_gpu_load_samples ADD COLUMN confidenceLevel TEXT DEFAULT 'LOW'");
                        db.execSQL("ALTER TABLE cpu_gpu_load_samples ADD COLUMN detectionMethod TEXT DEFAULT 'Direct hardware counters'");
                        db.execSQL("ALTER TABLE cpu_gpu_load_samples ADD COLUMN observedSignals TEXT DEFAULT ''");
                }
        };

        // =====================================================================================
        // MIGRATION 24 -> 25: Performance Monitoring & Regression Detection System
        // Adds 7 new tables for comprehensive performance tracking:
        // - memory_snapshots: RAM usage tracking
        // - app_launch_metrics: App launch performance
        // - frame_metrics: Frame drop/jank detection
        // - crash_events: App crash records
        // - anr_events: ANR event records
        // - daily_aggregates: Aggregated daily summaries
        // - regression_events: Detected performance regressions
        // =====================================================================================
        static final Migration MIGRATION_24_25 = new Migration(24, 25) {
                @Override
                public void migrate(SupportSQLiteDatabase db) {
                        // =====================================================================
                        // memory_snapshots: RAM usage tracking
                        // =====================================================================
                        db.execSQL("CREATE TABLE IF NOT EXISTS `memory_snapshots` (" +
                                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                        "`timestamp` INTEGER NOT NULL, " +
                                        "`totalMemoryBytes` INTEGER NOT NULL DEFAULT 0, " +
                                        "`availableMemoryBytes` INTEGER NOT NULL DEFAULT 0, " +
                                        "`usedMemoryBytes` INTEGER NOT NULL DEFAULT 0, " +
                                        "`usedMemoryPercent` REAL NOT NULL DEFAULT 0, " +
                                        "`isLowMemory` INTEGER NOT NULL DEFAULT 0, " +
                                        "`threshold` INTEGER NOT NULL DEFAULT 0, " +
                                        "`runningAppCount` INTEGER NOT NULL DEFAULT 0, " +
                                        "`topMemoryApp` TEXT, " +
                                        "`topMemoryAppBytes` INTEGER NOT NULL DEFAULT 0, " +
                                        "`batteryLevel` INTEGER NOT NULL DEFAULT 0, " +
                                        "`temperature` REAL NOT NULL DEFAULT 0, " +
                                        "`isCharging` INTEGER NOT NULL DEFAULT 0, " +
                                        "`isScreenOn` INTEGER NOT NULL DEFAULT 0)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_snapshots_timestamp ON memory_snapshots(timestamp)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_snapshots_isLowMemory_timestamp ON memory_snapshots(isLowMemory, timestamp)");

                        // =====================================================================
                        // app_launch_metrics: App launch performance
                        // =====================================================================
                        db.execSQL("CREATE TABLE IF NOT EXISTS `app_launch_metrics` (" +
                                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                        "`timestamp` INTEGER NOT NULL, " +
                                        "`packageName` TEXT, " +
                                        "`appLabel` TEXT, " +
                                        "`launchType` TEXT, " +
                                        "`launchDurationMs` INTEGER NOT NULL DEFAULT 0, " +
                                        "`ttidMs` INTEGER NOT NULL DEFAULT 0, " +
                                        "`ttfdMs` INTEGER NOT NULL DEFAULT 0, " +
                                        "`beforeLevel` INTEGER NOT NULL DEFAULT 0, " +
                                        "`afterLevel` INTEGER NOT NULL DEFAULT 0, " +
                                        "`beforeTemp` REAL NOT NULL DEFAULT 0, " +
                                        "`afterTemp` REAL NOT NULL DEFAULT 0, " +
                                        "`isFirstLaunchToday` INTEGER NOT NULL DEFAULT 0, " +
                                        "`sessionContext` TEXT, " +
                                        "`appVersionCode` INTEGER NOT NULL DEFAULT 0, " +
                                        "`appVersionName` TEXT, " +
                                        "`memoryUsagePercent` REAL NOT NULL DEFAULT 0, " +
                                        "`isCharging` INTEGER NOT NULL DEFAULT 0, " +
                                        "`thermalHeadroom` REAL NOT NULL DEFAULT 0)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_app_launch_metrics_timestamp ON app_launch_metrics(timestamp)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_app_launch_metrics_packageName_timestamp ON app_launch_metrics(packageName, timestamp)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_app_launch_metrics_launchType_timestamp ON app_launch_metrics(launchType, timestamp)");

                        // =====================================================================
                        // frame_metrics: Frame drop/jank detection
                        // =====================================================================
                        db.execSQL("CREATE TABLE IF NOT EXISTS `frame_metrics` (" +
                                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                        "`sessionStart` INTEGER NOT NULL DEFAULT 0, " +
                                        "`sessionEnd` INTEGER NOT NULL DEFAULT 0, " +
                                        "`packageName` TEXT, " +
                                        "`totalFrames` INTEGER NOT NULL DEFAULT 0, " +
                                        "`slowFrames` INTEGER NOT NULL DEFAULT 0, " +
                                        "`frozenFrames` INTEGER NOT NULL DEFAULT 0, " +
                                        "`jankPercentage` REAL NOT NULL DEFAULT 0, " +
                                        "`avgFrameDurationMs` REAL NOT NULL DEFAULT 0, " +
                                        "`maxFrameDurationMs` REAL NOT NULL DEFAULT 0, " +
                                        "`p50FrameDurationMs` REAL NOT NULL DEFAULT 0, " +
                                        "`p95FrameDurationMs` REAL NOT NULL DEFAULT 0, " +
                                        "`p99FrameDurationMs` REAL NOT NULL DEFAULT 0, " +
                                        "`droppedFrameCount` INTEGER NOT NULL DEFAULT 0, " +
                                        "`fps` REAL NOT NULL DEFAULT 0, " +
                                        "`sessionDurationMs` INTEGER NOT NULL DEFAULT 0, " +
                                        "`activityName` TEXT, " +
                                        "`isHardwareAccelerated` INTEGER NOT NULL DEFAULT 1, " +
                                        "`temperature` REAL NOT NULL DEFAULT 0, " +
                                        "`memoryUsagePercent` REAL NOT NULL DEFAULT 0, " +
                                        "`wasThermalThrottling` INTEGER NOT NULL DEFAULT 0)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_frame_metrics_sessionStart ON frame_metrics(sessionStart)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_frame_metrics_packageName_sessionStart ON frame_metrics(packageName, sessionStart)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_frame_metrics_jankPercentage ON frame_metrics(jankPercentage)");

                        // =====================================================================
                        // crash_events: App crash records
                        // =====================================================================
                        db.execSQL("CREATE TABLE IF NOT EXISTS `crash_events` (" +
                                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                        "`timestamp` INTEGER NOT NULL, " +
                                        "`packageName` TEXT, " +
                                        "`appLabel` TEXT, " +
                                        "`crashType` TEXT, " +
                                        "`exceptionType` TEXT, " +
                                        "`exceptionMessage` TEXT, " +
                                        "`stackTraceHash` TEXT, " +
                                        "`pid` INTEGER NOT NULL DEFAULT 0, " +
                                        "`uid` INTEGER NOT NULL DEFAULT 0, " +
                                        "`processName` TEXT, " +
                                        "`importance` INTEGER NOT NULL DEFAULT 0, " +
                                        "`reason` TEXT, " +
                                        "`reasonCode` INTEGER NOT NULL DEFAULT 0, " +
                                        "`description` TEXT, " +
                                        "`pss` INTEGER NOT NULL DEFAULT 0, " +
                                        "`rss` INTEGER NOT NULL DEFAULT 0, " +
                                        "`appVersionName` TEXT, " +
                                        "`appVersionCode` INTEGER NOT NULL DEFAULT 0, " +
                                        "`batteryLevel` INTEGER NOT NULL DEFAULT 0, " +
                                        "`temperature` REAL NOT NULL DEFAULT 0, " +
                                        "`wasLowMemory` INTEGER NOT NULL DEFAULT 0, " +
                                        "`isNewCrashType` INTEGER NOT NULL DEFAULT 0, " +
                                        "`subReason` INTEGER NOT NULL DEFAULT 0, " +
                                        "`status` INTEGER NOT NULL DEFAULT 0)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_crash_events_timestamp ON crash_events(timestamp)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_crash_events_packageName_timestamp ON crash_events(packageName, timestamp)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_crash_events_crashType ON crash_events(crashType)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_crash_events_stackTraceHash ON crash_events(stackTraceHash)");

                        // =====================================================================
                        // anr_events: ANR event records
                        // =====================================================================
                        db.execSQL("CREATE TABLE IF NOT EXISTS `anr_events` (" +
                                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                        "`timestamp` INTEGER NOT NULL, " +
                                        "`packageName` TEXT, " +
                                        "`appLabel` TEXT, " +
                                        "`anrType` TEXT, " +
                                        "`durationMs` INTEGER NOT NULL DEFAULT 0, " +
                                        "`traceHash` TEXT, " +
                                        "`mainThreadState` TEXT, " +
                                        "`blockingComponent` TEXT, " +
                                        "`pid` INTEGER NOT NULL DEFAULT 0, " +
                                        "`description` TEXT, " +
                                        "`appVersionName` TEXT, " +
                                        "`appVersionCode` INTEGER NOT NULL DEFAULT 0, " +
                                        "`wasKilled` INTEGER NOT NULL DEFAULT 0, " +
                                        "`userWaited` INTEGER NOT NULL DEFAULT 0, " +
                                        "`batteryLevel` INTEGER NOT NULL DEFAULT 0, " +
                                        "`temperature` REAL NOT NULL DEFAULT 0, " +
                                        "`memoryUsagePercent` REAL NOT NULL DEFAULT 0, " +
                                        "`cpuLoad` REAL NOT NULL DEFAULT -1, " +
                                        "`isNewAnrType` INTEGER NOT NULL DEFAULT 0, " +
                                        "`systemVersion` TEXT, " +
                                        "`foregroundActivity` TEXT)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_anr_events_timestamp ON anr_events(timestamp)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_anr_events_packageName_timestamp ON anr_events(packageName, timestamp)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_anr_events_anrType ON anr_events(anrType)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_anr_events_traceHash ON anr_events(traceHash)");

                        // =====================================================================
                        // daily_aggregates: Aggregated daily summaries
                        // =====================================================================
                        db.execSQL("CREATE TABLE IF NOT EXISTS `daily_aggregates` (" +
                                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                        "`date` TEXT, " +
                                        "`packageName` TEXT, " +
                                        "`appLabel` TEXT, " +
                                        "`avgMemoryUsedPercent` REAL NOT NULL DEFAULT 0, " +
                                        "`maxMemoryUsedPercent` REAL NOT NULL DEFAULT 0, " +
                                        "`minMemoryUsedPercent` REAL NOT NULL DEFAULT 0, " +
                                        "`lowMemoryEventCount` INTEGER NOT NULL DEFAULT 0, " +
                                        "`memorySampleCount` INTEGER NOT NULL DEFAULT 0, " +
                                        "`launchCount` INTEGER NOT NULL DEFAULT 0, " +
                                        "`coldLaunchCount` INTEGER NOT NULL DEFAULT 0, " +
                                        "`avgColdLaunchMs` REAL NOT NULL DEFAULT 0, " +
                                        "`avgWarmLaunchMs` REAL NOT NULL DEFAULT 0, " +
                                        "`avgHotLaunchMs` REAL NOT NULL DEFAULT 0, " +
                                        "`maxLaunchMs` REAL NOT NULL DEFAULT 0, " +
                                        "`slowLaunchCount` INTEGER NOT NULL DEFAULT 0, " +
                                        "`totalFrames` INTEGER NOT NULL DEFAULT 0, " +
                                        "`totalSlowFrames` INTEGER NOT NULL DEFAULT 0, " +
                                        "`totalFrozenFrames` INTEGER NOT NULL DEFAULT 0, " +
                                        "`avgJankPercent` REAL NOT NULL DEFAULT 0, " +
                                        "`maxJankPercent` REAL NOT NULL DEFAULT 0, " +
                                        "`avgFps` REAL NOT NULL DEFAULT 0, " +
                                        "`frameSessionCount` INTEGER NOT NULL DEFAULT 0, " +
                                        "`crashCount` INTEGER NOT NULL DEFAULT 0, " +
                                        "`uniqueCrashTypes` INTEGER NOT NULL DEFAULT 0, " +
                                        "`anrCount` INTEGER NOT NULL DEFAULT 0, " +
                                        "`uniqueAnrTypes` INTEGER NOT NULL DEFAULT 0, " +
                                        "`hasNewCrashType` INTEGER NOT NULL DEFAULT 0, " +
                                        "`hasNewAnrType` INTEGER NOT NULL DEFAULT 0, " +
                                        "`avgTemp` REAL NOT NULL DEFAULT 0, " +
                                        "`maxTemp` REAL NOT NULL DEFAULT 0, " +
                                        "`minTemp` REAL NOT NULL DEFAULT 0, " +
                                        "`avgThermalHeadroom` REAL NOT NULL DEFAULT 0, " +
                                        "`minThermalHeadroom` REAL NOT NULL DEFAULT 0, " +
                                        "`thermalThrottleEvents` INTEGER NOT NULL DEFAULT 0, " +
                                        "`thermalSampleCount` INTEGER NOT NULL DEFAULT 0, " +
                                        "`totalBatteryDrain` REAL NOT NULL DEFAULT 0, " +
                                        "`avgDrainRatePerHour` REAL NOT NULL DEFAULT 0, " +
                                        "`screenOnMinutes` INTEGER NOT NULL DEFAULT 0, " +
                                        "`baseline7DayLaunchMs` REAL NOT NULL DEFAULT 0, " +
                                        "`baseline7DayJankPercent` REAL NOT NULL DEFAULT 0, " +
                                        "`baseline7DayCrashCount` REAL NOT NULL DEFAULT 0, " +
                                        "`baseline7DayTemp` REAL NOT NULL DEFAULT 0, " +
                                        "`baseline30DayLaunchMs` REAL NOT NULL DEFAULT 0, " +
                                        "`baseline30DayJankPercent` REAL NOT NULL DEFAULT 0, " +
                                        "`baseline30DayCrashCount` REAL NOT NULL DEFAULT 0, " +
                                        "`baseline30DayTemp` REAL NOT NULL DEFAULT 0, " +
                                        "`healthScore` REAL NOT NULL DEFAULT 0, " +
                                        "`healthGrade` TEXT, " +
                                        "`createdAt` INTEGER NOT NULL DEFAULT 0, " +
                                        "`updatedAt` INTEGER NOT NULL DEFAULT 0, " +
                                        "`appVersion` TEXT, " +
                                        "`systemVersion` TEXT)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_daily_aggregates_date ON daily_aggregates(date)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_daily_aggregates_packageName_date ON daily_aggregates(packageName, date)");
                        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_daily_aggregates_date_packageName ON daily_aggregates(date, packageName)");

                        // =====================================================================
                        // regression_events: Detected performance regressions
                        // =====================================================================
                        db.execSQL("CREATE TABLE IF NOT EXISTS `regression_events` (" +
                                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                        "`detectedAt` INTEGER NOT NULL DEFAULT 0, " +
                                        "`regressionStartTime` INTEGER NOT NULL DEFAULT 0, " +
                                        "`packageName` TEXT, " +
                                        "`appLabel` TEXT, " +
                                        "`regressionType` TEXT, " +
                                        "`baselineValue` REAL NOT NULL DEFAULT 0, " +
                                        "`currentValue` REAL NOT NULL DEFAULT 0, " +
                                        "`deltaPercent` REAL NOT NULL DEFAULT 0, " +
                                        "`threshold` REAL NOT NULL DEFAULT 0, " +
                                        "`unit` TEXT, " +
                                        "`triggerEvent` TEXT, " +
                                        "`previousVersion` TEXT, " +
                                        "`currentVersion` TEXT, " +
                                        "`previousSystemVersion` TEXT, " +
                                        "`systemVersion` TEXT, " +
                                        "`persistenceDays` INTEGER NOT NULL DEFAULT 0, " +
                                        "`evidenceTableName` TEXT, " +
                                        "`evidenceQuery` TEXT, " +
                                        "`evidenceCount` INTEGER NOT NULL DEFAULT 0, " +
                                        "`evidenceStartTime` INTEGER NOT NULL DEFAULT 0, " +
                                        "`evidenceEndTime` INTEGER NOT NULL DEFAULT 0, " +
                                        "`evidenceDataJson` TEXT, " +
                                        "`confidenceScore` REAL NOT NULL DEFAULT 0, " +
                                        "`severity` TEXT, " +
                                        "`explanation` TEXT, " +
                                        "`rootCause` TEXT, " +
                                        "`trend` TEXT, " +
                                        "`userRecommendation` TEXT, " +
                                        "`engineerRecommendation` TEXT, " +
                                        "`isResolved` INTEGER NOT NULL DEFAULT 0, " +
                                        "`resolvedAt` INTEGER NOT NULL DEFAULT 0, " +
                                        "`resolutionMethod` TEXT, " +
                                        "`resolvedValue` REAL NOT NULL DEFAULT 0, " +
                                        "`userNotified` INTEGER NOT NULL DEFAULT 0, " +
                                        "`userDismissed` INTEGER NOT NULL DEFAULT 0)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_regression_events_detectedAt ON regression_events(detectedAt)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_regression_events_packageName_detectedAt ON regression_events(packageName, detectedAt)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_regression_events_regressionType ON regression_events(regressionType)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_regression_events_isResolved ON regression_events(isResolved)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_regression_events_severity ON regression_events(severity)");
                }
        };

        // =====================================================================================
        // MIGRATION 25 -> 26: Three-state data model (nullable metrics, snapshot
        // context)
        // - Make three-state columns nullable: screenOn, wifi*, mobile*, bluetooth*,
        // gps*,
        // currentNow, currentAverage, energyCounter, chargeCounter, cpuLoadTotal,
        // gpuLoad,
        // screenBrightness, signalStrength, chargerVendorId, chargerProductId.
        // - Add snapshotTrigger, dataSource, confidence for explainable data.
        // =====================================================================================
        static final Migration MIGRATION_25_26 = new Migration(25, 26) {
                @Override
                public void migrate(SupportSQLiteDatabase db) {
                        db.execSQL("CREATE TABLE IF NOT EXISTS battery_snapshots_new (" +
                                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                        "timestamp INTEGER NOT NULL, sequenceNumber INTEGER NOT NULL, " +
                                        "elapsedRealtimeMs INTEGER NOT NULL, level INTEGER NOT NULL, " +
                                        "status INTEGER NOT NULL, health INTEGER NOT NULL, plugged INTEGER NOT NULL, " +
                                        "voltage INTEGER NOT NULL, temperature INTEGER NOT NULL, " +
                                        "chargeCounter INTEGER, currentNow INTEGER, currentAverage INTEGER, energyCounter INTEGER, "
                                        +
                                        "capacityMah REAL NOT NULL, designCapacityMah REAL NOT NULL, healthPercentage REAL NOT NULL, "
                                        +
                                        "chargeRate REAL NOT NULL, dischargeRate REAL NOT NULL, " +
                                        "screenOn INTEGER, screenBrightness INTEGER, " +
                                        "wifiEnabled INTEGER, mobileDataEnabled INTEGER, bluetoothEnabled INTEGER, gpsEnabled INTEGER, "
                                        +
                                        "signalStrength INTEGER, " +
                                        "isCharging INTEGER NOT NULL, isFastCharging INTEGER NOT NULL, chargingType TEXT, "
                                        +
                                        "isDozeMode INTEGER NOT NULL, cpuUsage INTEGER NOT NULL, memoryUsage INTEGER NOT NULL, "
                                        +
                                        "cpuLoadTotal INTEGER, topAppPackage TEXT, gpuLoad INTEGER, networkState TEXT, "
                                        +
                                        "chargerVendorId INTEGER, chargerProductId INTEGER, " +
                                        "voltageSamplesJson TEXT, currentSamplesJson TEXT, foregroundApp TEXT, runningApps INTEGER NOT NULL, "
                                        +
                                        "samplingContext TEXT, samplingIntervalMs INTEGER NOT NULL, timeSincePreviousMs INTEGER NOT NULL, "
                                        +
                                        "validationStatus TEXT, qualityScore INTEGER NOT NULL, validationFlags INTEGER NOT NULL, "
                                        +
                                        "validationNotes TEXT, sensorDataChanged INTEGER NOT NULL, sessionId INTEGER NOT NULL, "
                                        +
                                        "snapshotTrigger TEXT, dataSource TEXT, confidence REAL)");
                        db.execSQL("INSERT INTO battery_snapshots_new (" +
                                        "id, timestamp, sequenceNumber, elapsedRealtimeMs, level, status, health, plugged, "
                                        +
                                        "voltage, temperature, chargeCounter, currentNow, currentAverage, energyCounter, "
                                        +
                                        "capacityMah, designCapacityMah, healthPercentage, chargeRate, dischargeRate, "
                                        +
                                        "screenOn, screenBrightness, wifiEnabled, mobileDataEnabled, bluetoothEnabled, gpsEnabled, "
                                        +
                                        "signalStrength, isCharging, isFastCharging, chargingType, isDozeMode, cpuUsage, memoryUsage, "
                                        +
                                        "cpuLoadTotal, topAppPackage, gpuLoad, networkState, chargerVendorId, chargerProductId, "
                                        +
                                        "voltageSamplesJson, currentSamplesJson, foregroundApp, runningApps, " +
                                        "samplingContext, samplingIntervalMs, timeSincePreviousMs, " +
                                        "validationStatus, qualityScore, validationFlags, validationNotes, sensorDataChanged, sessionId, "
                                        +
                                        "snapshotTrigger, dataSource, confidence) " +
                                        "SELECT " +
                                        "id, timestamp, sequenceNumber, elapsedRealtimeMs, level, status, health, plugged, "
                                        +
                                        "voltage, temperature, chargeCounter, currentNow, currentAverage, energyCounter, "
                                        +
                                        "capacityMah, designCapacityMah, healthPercentage, chargeRate, dischargeRate, "
                                        +
                                        "screenOn, screenBrightness, wifiEnabled, mobileDataEnabled, bluetoothEnabled, gpsEnabled, "
                                        +
                                        "signalStrength, isCharging, isFastCharging, chargingType, isDozeMode, cpuUsage, memoryUsage, "
                                        +
                                        "cpuLoadTotal, topAppPackage, gpuLoad, networkState, chargerVendorId, chargerProductId, "
                                        +
                                        "voltageSamplesJson, currentSamplesJson, foregroundApp, runningApps, " +
                                        "samplingContext, samplingIntervalMs, timeSincePreviousMs, " +
                                        "validationStatus, qualityScore, validationFlags, validationNotes, sensorDataChanged, sessionId, "
                                        +
                                        "NULL, NULL, NULL FROM battery_snapshots");
                        db.execSQL("DROP TABLE battery_snapshots");
                        db.execSQL("ALTER TABLE battery_snapshots_new RENAME TO battery_snapshots");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_battery_snapshots_timestamp ON battery_snapshots(timestamp)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_battery_snapshots_samplingContext ON battery_snapshots(samplingContext)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_battery_snapshots_isCharging_timestamp ON battery_snapshots(isCharging, timestamp)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_battery_snapshots_validationStatus ON battery_snapshots(validationStatus)");
                }
        };

        // =====================================================================================
        // MIGRATION 26 -> 27: Network & Connectivity Stack
        // - Add network_cellular_snapshots table for 4G/5G signal metrics
        // - Add network_wifi_snapshots table for Wi-Fi stability monitoring
        // - Add network_bluetooth_snapshots table for BT protocol tracking
        // - Add modem_firmware_events table for baseband version tracking
        // =====================================================================================
        static final Migration MIGRATION_26_27 = new Migration(26, 27) {
                @Override
                public void migrate(SupportSQLiteDatabase db) {
                        // =====================================================================
                        // network_cellular_snapshots: Raw cellular/modem signal data
                        // =====================================================================
                        db.execSQL("CREATE TABLE IF NOT EXISTS `network_cellular_snapshots` (" +
                                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                        "`timestamp` INTEGER NOT NULL, " +
                                        "`sequenceNumber` INTEGER NOT NULL DEFAULT 0, " +
                                        "`elapsedRealtimeMs` INTEGER NOT NULL DEFAULT 0, " +
                                        "`sessionId` INTEGER NOT NULL DEFAULT 0, " +
                                        "`radioTechnology` TEXT, " +
                                        "`networkType` INTEGER, " +
                                        "`signalStrengthDbm` INTEGER, " +
                                        "`rsrp` INTEGER, " +
                                        "`rsrq` INTEGER, " +
                                        "`sinr` INTEGER, " +
                                        "`rssi` INTEGER, " +
                                        "`signalLevel` INTEGER, " +
                                        "`asu` INTEGER, " +
                                        "`band` INTEGER, " +
                                        "`arfcn` INTEGER, " +
                                        "`pci` INTEGER, " +
                                        "`cellId` INTEGER, " +
                                        "`tac` INTEGER, " +
                                        "`carrierId` INTEGER, " +
                                        "`mcc` TEXT, " +
                                        "`mnc` TEXT, " +
                                        "`networkOperatorName` TEXT, " +
                                        "`plmn` TEXT, " +
                                        "`dataRegistrationState` INTEGER, " +
                                        "`voiceRegistrationState` INTEGER, " +
                                        "`isRoaming` INTEGER, " +
                                        "`roamingType` TEXT, " +
                                        "`emergencyOnly` INTEGER, " +
                                        "`dataState` INTEGER, " +
                                        "`dataActivity` INTEGER, " +
                                        "`modemFirmwareVersion` TEXT, " +
                                        "`basebandVersion` TEXT, " +
                                        "`snapshotContext` TEXT, " +
                                        "`snapshotTrigger` TEXT, " +
                                        "`dataSource` TEXT, " +
                                        "`confidence` REAL, " +
                                        "`validationStatus` TEXT, " +
                                        "`qualityScore` INTEGER NOT NULL DEFAULT -1, " +
                                        "`validationFlags` INTEGER NOT NULL DEFAULT 0, " +
                                        "`validationNotes` TEXT, " +
                                        "`signalDataChanged` INTEGER NOT NULL DEFAULT 1)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_network_cellular_snapshots_timestamp ON network_cellular_snapshots(timestamp)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_network_cellular_snapshots_radioTechnology ON network_cellular_snapshots(radioTechnology)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_network_cellular_snapshots_validationStatus ON network_cellular_snapshots(validationStatus)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_network_cellular_snapshots_snapshotContext ON network_cellular_snapshots(snapshotContext)");

                        // =====================================================================
                        // network_wifi_snapshots: Raw Wi-Fi diagnostics
                        // =====================================================================
                        db.execSQL("CREATE TABLE IF NOT EXISTS `network_wifi_snapshots` (" +
                                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                        "`timestamp` INTEGER NOT NULL, " +
                                        "`sequenceNumber` INTEGER NOT NULL DEFAULT 0, " +
                                        "`elapsedRealtimeMs` INTEGER NOT NULL DEFAULT 0, " +
                                        "`sessionId` INTEGER NOT NULL DEFAULT 0, " +
                                        "`ssidHash` TEXT, " +
                                        "`bssidHash` TEXT, " +
                                        "`frequency` INTEGER, " +
                                        "`frequencyBand` TEXT, " +
                                        "`channel` INTEGER, " +
                                        "`linkSpeedMbps` INTEGER, " +
                                        "`txLinkSpeedMbps` INTEGER, " +
                                        "`rxLinkSpeedMbps` INTEGER, " +
                                        "`maxTxLinkSpeedMbps` INTEGER, " +
                                        "`maxRxLinkSpeedMbps` INTEGER, " +
                                        "`rssi` INTEGER, " +
                                        "`signalLevel` INTEGER, " +
                                        "`wifiStandard` INTEGER, " +
                                        "`wifiStandardName` TEXT, " +
                                        "`securityType` TEXT, " +
                                        "`connectionState` TEXT, " +
                                        "`networkId` INTEGER, " +
                                        "`isMetered` INTEGER, " +
                                        "`isPasspoint` INTEGER, " +
                                        "`disconnectReason` INTEGER, " +
                                        "`reconnectCount` INTEGER, " +
                                        "`dhcpLatencyMs` INTEGER, " +
                                        "`timeSinceAuthMs` INTEGER, " +
                                        "`associationAttempts` INTEGER, " +
                                        "`associationFailures` INTEGER, " +
                                        "`hasIpv4` INTEGER, " +
                                        "`hasIpv6` INTEGER, " +
                                        "`gatewayLatencyMs` INTEGER, " +
                                        "`snapshotContext` TEXT, " +
                                        "`snapshotTrigger` TEXT, " +
                                        "`dataSource` TEXT, " +
                                        "`confidence` REAL, " +
                                        "`validationStatus` TEXT, " +
                                        "`qualityScore` INTEGER NOT NULL DEFAULT -1, " +
                                        "`validationFlags` INTEGER NOT NULL DEFAULT 0, " +
                                        "`validationNotes` TEXT, " +
                                        "`signalDataChanged` INTEGER NOT NULL DEFAULT 1)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_network_wifi_snapshots_timestamp ON network_wifi_snapshots(timestamp)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_network_wifi_snapshots_connectionState ON network_wifi_snapshots(connectionState)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_network_wifi_snapshots_validationStatus ON network_wifi_snapshots(validationStatus)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_network_wifi_snapshots_wifiStandard ON network_wifi_snapshots(wifiStandard)");

                        // =====================================================================
                        // network_bluetooth_snapshots: Bluetooth protocol stability tracking
                        // =====================================================================
                        db.execSQL("CREATE TABLE IF NOT EXISTS `network_bluetooth_snapshots` (" +
                                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                        "`timestamp` INTEGER NOT NULL, " +
                                        "`sequenceNumber` INTEGER NOT NULL DEFAULT 0, " +
                                        "`elapsedRealtimeMs` INTEGER NOT NULL DEFAULT 0, " +
                                        "`sessionId` INTEGER NOT NULL DEFAULT 0, " +
                                        "`adapterState` INTEGER, " +
                                        "`adapterStateName` TEXT, " +
                                        "`scanMode` INTEGER, " +
                                        "`isEnabled` INTEGER, " +
                                        "`isDiscovering` INTEGER, " +
                                        "`pairedDeviceCount` INTEGER, " +
                                        "`connectedDeviceCount` INTEGER, " +
                                        "`profileType` TEXT, " +
                                        "`profileTypeConstant` INTEGER, " +
                                        "`connectionState` INTEGER, " +
                                        "`connectionStateName` TEXT, " +
                                        "`codecType` TEXT, " +
                                        "`codecTypeConstant` INTEGER, " +
                                        "`sampleRate` INTEGER, " +
                                        "`bitsPerSample` INTEGER, " +
                                        "`channelMode` TEXT, " +
                                        "`codecQuality` INTEGER, " +
                                        "`isPlaying` INTEGER, " +
                                        "`leConnectionInterval` INTEGER, " +
                                        "`leLatency` INTEGER, " +
                                        "`rssi` INTEGER, " +
                                        "`txPowerLevel` INTEGER, " +
                                        "`packetErrorCount` INTEGER, " +
                                        "`reconnectCount` INTEGER, " +
                                        "`connectionDurationMs` INTEGER, " +
                                        "`remoteAddressHash` TEXT, " +
                                        "`remoteDeviceType` INTEGER, " +
                                        "`remoteDeviceClass` INTEGER, " +
                                        "`remoteDeviceName` TEXT, " +
                                        "`snapshotContext` TEXT, " +
                                        "`snapshotTrigger` TEXT, " +
                                        "`dataSource` TEXT, " +
                                        "`confidence` REAL, " +
                                        "`validationStatus` TEXT, " +
                                        "`qualityScore` INTEGER NOT NULL DEFAULT -1, " +
                                        "`validationFlags` INTEGER NOT NULL DEFAULT 0, " +
                                        "`validationNotes` TEXT)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_network_bluetooth_snapshots_timestamp ON network_bluetooth_snapshots(timestamp)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_network_bluetooth_snapshots_adapterState ON network_bluetooth_snapshots(adapterState)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_network_bluetooth_snapshots_profileType ON network_bluetooth_snapshots(profileType)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_network_bluetooth_snapshots_validationStatus ON network_bluetooth_snapshots(validationStatus)");

                        // =====================================================================
                        // modem_firmware_events: Baseband/radio firmware version tracking
                        // =====================================================================
                        db.execSQL("CREATE TABLE IF NOT EXISTS `modem_firmware_events` (" +
                                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                        "`timestamp` INTEGER NOT NULL, " +
                                        "`elapsedRealtimeMs` INTEGER NOT NULL DEFAULT 0, " +
                                        "`previousVersion` TEXT, " +
                                        "`currentVersion` TEXT, " +
                                        "`previousBasebandVersion` TEXT, " +
                                        "`currentBasebandVersion` TEXT, " +
                                        "`eventType` TEXT, " +
                                        "`detectionSource` TEXT, " +
                                        "`androidVersion` TEXT, " +
                                        "`androidSdkLevel` INTEGER, " +
                                        "`securityPatch` TEXT, " +
                                        "`buildFingerprint` TEXT, " +
                                        "`buildNumber` TEXT, " +
                                        "`manufacturer` TEXT, " +
                                        "`model` TEXT, " +
                                        "`product` TEXT, " +
                                        "`bootloaderVersion` TEXT, " +
                                        "`hardware` TEXT, " +
                                        "`board` TEXT, " +
                                        "`isVersionChange` INTEGER, " +
                                        "`isOtaUpdate` INTEGER, " +
                                        "`timeSinceLastEventMs` INTEGER, " +
                                        "`sessionId` INTEGER NOT NULL DEFAULT 0, " +
                                        "`notes` TEXT)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_modem_firmware_events_timestamp ON modem_firmware_events(timestamp)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_modem_firmware_events_eventType ON modem_firmware_events(eventType)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_modem_firmware_events_currentVersion ON modem_firmware_events(currentVersion)");
                }
        };

        // Migration 27->28: Add Update Impact Observer tables
        // These tables enable tracking and verification of system update impacts
        static final Migration MIGRATION_27_28 = new Migration(27, 28) {
                @Override
                public void migrate(SupportSQLiteDatabase db) {
                        // =====================================================================
                        // update_sessions: Master table tracking each system update event
                        // =====================================================================
                        db.execSQL("CREATE TABLE IF NOT EXISTS `update_sessions` (" +
                                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                        "`sessionUuid` TEXT, " +
                                        "`detectedAt` INTEGER NOT NULL, " +
                                        "`preSnapshotLockedAt` INTEGER, " +
                                        "`updateInstalledAt` INTEGER, " +
                                        "`verificationCompletedAt` INTEGER, " +
                                        "`previousBuildFingerprint` TEXT, " +
                                        "`currentBuildFingerprint` TEXT, " +
                                        "`previousAndroidVersion` TEXT, " +
                                        "`currentAndroidVersion` TEXT, " +
                                        "`previousSecurityPatch` TEXT, " +
                                        "`currentSecurityPatch` TEXT, " +
                                        "`previousModemVersion` TEXT, " +
                                        "`currentModemVersion` TEXT, " +
                                        "`status` TEXT, " +
                                        "`updateType` TEXT, " +
                                        "`detectionMethod` TEXT, " +
                                        "`knownIssuesCount` INTEGER NOT NULL DEFAULT 0, " +
                                        "`preUpdateDataQuality` REAL, " +
                                        "`preUpdateObservationHours` REAL, " +
                                        "`preUpdateSnapshotCount` INTEGER, " +
                                        "`issuesFixed` INTEGER NOT NULL DEFAULT 0, " +
                                        "`issuesUnchanged` INTEGER NOT NULL DEFAULT 0, " +
                                        "`newRegressionsDetected` INTEGER NOT NULL DEFAULT 0, " +
                                        "`updateImpactScore` REAL, " +
                                        "`overallVerdict` TEXT, " +
                                        "`userWasNotified` INTEGER NOT NULL DEFAULT 0, " +
                                        "`userDismissed` INTEGER NOT NULL DEFAULT 0, " +
                                        "`notes` TEXT)");
                        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_update_sessions_sessionUuid ON update_sessions(sessionUuid)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_update_sessions_status ON update_sessions(status)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_update_sessions_detectedAt ON update_sessions(detectedAt)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_update_sessions_currentBuildFingerprint ON update_sessions(currentBuildFingerprint)");

                        // =====================================================================
                        // pre_update_snapshots: Frozen baseline state before update
                        // =====================================================================
                        db.execSQL("CREATE TABLE IF NOT EXISTS `pre_update_snapshots` (" +
                                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                        "`sessionId` INTEGER NOT NULL, " +
                                        "`capturedAt` INTEGER NOT NULL, " +
                                        "`buildFingerprint` TEXT, " +
                                        "`androidVersion` TEXT, " +
                                        "`securityPatch` TEXT, " +
                                        "`modemVersion` TEXT, " +
                                        "`avgDrainRatePerHour` REAL, " +
                                        "`idleDrainRatePerHour` REAL, " +
                                        "`batteryHealthPercent` REAL, " +
                                        "`cycleCount` INTEGER, " +
                                        "`avgChargingTemp` REAL, " +
                                        "`avgColdLaunchMs` REAL, " +
                                        "`avgJankPercent` REAL, " +
                                        "`avgFps` REAL, " +
                                        "`avgMemoryUsedPercent` REAL, " +
                                        "`crashesPerDay` REAL, " +
                                        "`anrsPerDay` REAL, " +
                                        "`thermalEventsPerDay` REAL, " +
                                        "`avgOperatingTemp` REAL, " +
                                        "`maxTempRecorded` REAL, " +
                                        "`timeAbove40C` INTEGER, " +
                                        "`timeAbove45C` INTEGER, " +
                                        "`knownIssuesJson` TEXT, " +
                                        "`anomalyCountTotal` INTEGER, " +
                                        "`highSeverityAnomalies` INTEGER, " +
                                        "`dataQualityScore` REAL, " +
                                        "`observationDays` INTEGER, " +
                                        "`snapshotCount` INTEGER, " +
                                        "FOREIGN KEY(`sessionId`) REFERENCES `update_sessions`(`id`) ON DELETE CASCADE)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_pre_update_snapshots_sessionId ON pre_update_snapshots(sessionId)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_pre_update_snapshots_capturedAt ON pre_update_snapshots(capturedAt)");

                        // =====================================================================
                        // pre_update_issues: Known bugs/anomalies detected before update
                        // =====================================================================
                        db.execSQL("CREATE TABLE IF NOT EXISTS `pre_update_issues` (" +
                                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                        "`sessionId` INTEGER NOT NULL, " +
                                        "`issueUuid` TEXT, " +
                                        "`detectedAt` INTEGER NOT NULL, " +
                                        "`issueType` TEXT, " +
                                        "`component` TEXT, " +
                                        "`severity` TEXT, " +
                                        "`issueTitle` TEXT, " +
                                        "`issueDescription` TEXT, " +
                                        "`technicalDetails` TEXT, " +
                                        "`metricName` TEXT, " +
                                        "`baselineValue` REAL, " +
                                        "`actualValue` REAL, " +
                                        "`deviationPercent` REAL, " +
                                        "`unit` TEXT, " +
                                        "`occurrenceCount` INTEGER, " +
                                        "`firstObserved` INTEGER, " +
                                        "`lastObserved` INTEGER, " +
                                        "`impactScore` REAL, " +
                                        "`affectsUserExperience` INTEGER NOT NULL DEFAULT 1, " +
                                        "`evidenceTableName` TEXT, " +
                                        "`evidenceQuery` TEXT, " +
                                        "`evidenceCount` INTEGER, " +
                                        "`sourcePackageName` TEXT, " +
                                        "`sourceAppLabel` TEXT, " +
                                        "FOREIGN KEY(`sessionId`) REFERENCES `update_sessions`(`id`) ON DELETE CASCADE)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_pre_update_issues_sessionId ON pre_update_issues(sessionId)");
                        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_pre_update_issues_issueUuid ON pre_update_issues(issueUuid)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_pre_update_issues_issueType ON pre_update_issues(issueType)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_pre_update_issues_severity ON pre_update_issues(severity)");

                        // =====================================================================
                        // post_update_verifications: Comparison results for each issue
                        // =====================================================================
                        db.execSQL("CREATE TABLE IF NOT EXISTS `post_update_verifications` (" +
                                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                        "`sessionId` INTEGER NOT NULL, " +
                                        "`preUpdateIssueId` INTEGER NOT NULL, " +
                                        "`verifiedAt` INTEGER NOT NULL, " +
                                        "`issueUuid` TEXT, " +
                                        "`issueType` TEXT, " +
                                        "`issueTitle` TEXT, " +
                                        "`preUpdateValue` REAL, " +
                                        "`preUpdateOccurrences` INTEGER, " +
                                        "`postUpdateValue` REAL, " +
                                        "`postUpdateOccurrences` INTEGER, " +
                                        "`absoluteChange` REAL, " +
                                        "`percentChange` REAL, " +
                                        "`verificationStatus` TEXT, " +
                                        "`confidenceScore` REAL, " +
                                        "`postUpdateEvidenceJson` TEXT, " +
                                        "`comparisonDetails` TEXT, " +
                                        "`userNoticeable` INTEGER NOT NULL DEFAULT 0, " +
                                        "FOREIGN KEY(`sessionId`) REFERENCES `update_sessions`(`id`) ON DELETE CASCADE, "
                                        +
                                        "FOREIGN KEY(`preUpdateIssueId`) REFERENCES `pre_update_issues`(`id`) ON DELETE CASCADE)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_post_update_verifications_sessionId ON post_update_verifications(sessionId)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_post_update_verifications_preUpdateIssueId ON post_update_verifications(preUpdateIssueId)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_post_update_verifications_verificationStatus ON post_update_verifications(verificationStatus)");

                        // =====================================================================
                        // update_regressions: New problems introduced by update
                        // =====================================================================
                        db.execSQL("CREATE TABLE IF NOT EXISTS `update_regressions` (" +
                                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                        "`sessionId` INTEGER NOT NULL, " +
                                        "`regressionUuid` TEXT, " +
                                        "`detectedAt` INTEGER NOT NULL, " +
                                        "`regressionType` TEXT, " +
                                        "`component` TEXT, " +
                                        "`severity` TEXT, " +
                                        "`title` TEXT, " +
                                        "`description` TEXT, " +
                                        "`technicalDetails` TEXT, " +
                                        "`metricName` TEXT, " +
                                        "`preUpdateBaseline` REAL, " +
                                        "`postUpdateValue` REAL, " +
                                        "`deltaPercent` REAL, " +
                                        "`unit` TEXT, " +
                                        "`firstOccurrence` INTEGER, " +
                                        "`occurrenceCount` INTEGER, " +
                                        "`evidenceJson` TEXT, " +
                                        "`impactScore` REAL, " +
                                        "`userNoticeable` INTEGER NOT NULL DEFAULT 0, " +
                                        "`acknowledged` INTEGER NOT NULL DEFAULT 0, " +
                                        "`userReported` INTEGER NOT NULL DEFAULT 0, " +
                                        "FOREIGN KEY(`sessionId`) REFERENCES `update_sessions`(`id`) ON DELETE CASCADE)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_update_regressions_sessionId ON update_regressions(sessionId)");
                        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_update_regressions_regressionUuid ON update_regressions(regressionUuid)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_update_regressions_regressionType ON update_regressions(regressionType)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_update_regressions_severity ON update_regressions(severity)");
                }
        };

        // =================================================================
        // MIGRATION 28 → 29: ML Infrastructure Tables + Existing Table Enhancements
        // =================================================================
        static final Migration MIGRATION_28_29 = new Migration(28, 29) {
                @Override
                public void migrate(SupportSQLiteDatabase db) {

                        // =====================================================================
                        // device_profiles: Per-device-model hardware profiles
                        // =====================================================================
                        db.execSQL("CREATE TABLE IF NOT EXISTS `device_profiles` (" +
                                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                        "`deviceFingerprint` TEXT NOT NULL, " +
                                        "`manufacturer` TEXT, " +
                                        "`model` TEXT, " +
                                        "`marketName` TEXT, " +
                                        "`soc` TEXT, " +
                                        "`socBinning` TEXT, " +
                                        "`ramGB` INTEGER, " +
                                        "`storageGB` INTEGER, " +
                                        "`batteryDesignCapacityMah` INTEGER, " +
                                        "`screenSizeInches` REAL, " +
                                        "`screenResolution` TEXT, " +
                                        "`deviceCount` INTEGER NOT NULL, " +
                                        "`firstSeen` INTEGER NOT NULL, " +
                                        "`lastUpdated` INTEGER NOT NULL, " +
                                        "`idleDrainMean` REAL, " +
                                        "`idleDrainStdDev` REAL, " +
                                        "`activeDrainMean` REAL, " +
                                        "`activeDrainStdDev` REAL, " +
                                        "`thermalEnvelopeCpu` REAL, " +
                                        "`thermalEnvelopeGpu` REAL, " +
                                        "`thermalEnvelopeBattery` REAL, " +
                                        "`avgColdLaunchMs` REAL, " +
                                        "`avgJankPercent` REAL, " +
                                        "`avgFps` REAL, " +
                                        "`crashesPerDayMean` REAL, " +
                                        "`anrsPerDayMean` REAL)");
                        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_device_profiles_deviceFingerprint ON device_profiles(deviceFingerprint)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_device_profiles_manufacturer ON device_profiles(manufacturer)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_device_profiles_model ON device_profiles(model)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_device_profiles_soc ON device_profiles(soc)");

                        // =====================================================================
                        // device_baseline_thresholds: Per-device statistical baselines
                        // =====================================================================
                        db.execSQL("CREATE TABLE IF NOT EXISTS `device_baseline_thresholds` (" +
                                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                        "`deviceFingerprint` TEXT NOT NULL, " +
                                        "`metricName` TEXT NOT NULL, " +
                                        "`p50` REAL, " +
                                        "`p95` REAL, " +
                                        "`p99` REAL, " +
                                        "`mean` REAL, " +
                                        "`stdDev` REAL, " +
                                        "`sampleSize` INTEGER NOT NULL, " +
                                        "`confidenceLevel` REAL NOT NULL, " +
                                        "`lastUpdated` INTEGER NOT NULL)");
                        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_device_baseline_thresholds_deviceFingerprint_metricName ON device_baseline_thresholds(deviceFingerprint, metricName)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_device_baseline_thresholds_deviceFingerprint ON device_baseline_thresholds(deviceFingerprint)");

                        // =====================================================================
                        // human_labels: Ground truth labels from engineers
                        // =====================================================================
                        db.execSQL("CREATE TABLE IF NOT EXISTS `human_labels` (" +
                                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                        "`anomalyRecordId` INTEGER, " +
                                        "`safetyEventId` INTEGER, " +
                                        "`regressionEventId` INTEGER, " +
                                        "`thermalEventId` INTEGER, " +
                                        "`updateSessionId` INTEGER, " +
                                        "`labelerId` TEXT NOT NULL, " +
                                        "`labelerRole` TEXT, " +
                                        "`groundTruthVerdict` TEXT NOT NULL, " +
                                        "`severityCorrected` TEXT, " +
                                        "`rootCauseCorrected` TEXT, " +
                                        "`rmaTicketId` TEXT, " +
                                        "`labTestId` TEXT, " +
                                        "`userReportId` TEXT, " +
                                        "`labeledAt` INTEGER NOT NULL, " +
                                        "`labelConfidence` REAL NOT NULL, " +
                                        "`notes` TEXT)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_human_labels_anomalyRecordId ON human_labels(anomalyRecordId)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_human_labels_safetyEventId ON human_labels(safetyEventId)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_human_labels_thermalEventId ON human_labels(thermalEventId)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_human_labels_updateSessionId ON human_labels(updateSessionId)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_human_labels_groundTruthVerdict ON human_labels(groundTruthVerdict)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_human_labels_labelerId ON human_labels(labelerId)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_human_labels_labeledAt ON human_labels(labeledAt)");

                        // =====================================================================
                        // label_statistics: Confusion matrix tracking per anomaly type
                        // =====================================================================
                        db.execSQL("CREATE TABLE IF NOT EXISTS `label_statistics` (" +
                                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                        "`computedAt` INTEGER NOT NULL, " +
                                        "`periodStart` INTEGER NOT NULL, " +
                                        "`periodEnd` INTEGER NOT NULL, " +
                                        "`anomalyType` TEXT, " +
                                        "`truePositives` INTEGER NOT NULL, " +
                                        "`falsePositives` INTEGER NOT NULL, " +
                                        "`trueNegatives` INTEGER NOT NULL, " +
                                        "`falseNegatives` INTEGER NOT NULL, " +
                                        "`precision` REAL, " +
                                        "`recall` REAL, " +
                                        "`f1Score` REAL, " +
                                        "`accuracy` REAL, " +
                                        "`totalLabeled` INTEGER NOT NULL, " +
                                        "`analysisEngineVersion` TEXT, " +
                                        "`thresholdSetVersion` TEXT, " +
                                        "`modelVersion` TEXT)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_label_statistics_anomalyType ON label_statistics(anomalyType)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_label_statistics_computedAt ON label_statistics(computedAt)");

                        // =====================================================================
                        // negative_examples: Normal high-stress scenarios for ML training
                        // =====================================================================
                        db.execSQL("CREATE TABLE IF NOT EXISTS `negative_examples` (" +
                                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                        "`conditionType` TEXT DEFAULT '' NOT NULL, " +
                                        "`deviceFingerprint` TEXT, " +
                                        "`timestamp` INTEGER NOT NULL, " +
                                        "`durationMs` INTEGER NOT NULL, " +
                                        "`metricsJson` TEXT, " +
                                        "`expectedBehavior` TEXT DEFAULT '' NOT NULL, " +
                                        "`normalCauseRootCause` TEXT, " +
                                        "`detectedActivity` TEXT, " +
                                        "`foregroundApp` TEXT, " +
                                        "`foregroundAppName` TEXT, " +
                                        "`batteryLevel` INTEGER NOT NULL, " +
                                        "`temperature` REAL NOT NULL, " +
                                        "`cpuUsage` INTEGER NOT NULL, " +
                                        "`drainRate` REAL NOT NULL, " +
                                        "`labeledAsNormal` INTEGER NOT NULL DEFAULT 1, " +
                                        "`labeledBy` TEXT, " +
                                        "`usedInTraining` INTEGER NOT NULL DEFAULT 0, " +
                                        "`trainingSetVersion` TEXT)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_negative_examples_conditionType ON negative_examples(conditionType)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_negative_examples_deviceFingerprint ON negative_examples(deviceFingerprint)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_negative_examples_timestamp ON negative_examples(timestamp)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_negative_examples_foregroundApp ON negative_examples(foregroundApp)");

                        // =====================================================================
                        // ml_models: ML model lifecycle registry
                        // =====================================================================
                        db.execSQL("CREATE TABLE IF NOT EXISTS `ml_models` (" +
                                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                        "`modelName` TEXT NOT NULL, " +
                                        "`modelVersion` TEXT NOT NULL, " +
                                        "`modelType` TEXT, " +
                                        "`taskType` TEXT, " +
                                        "`trainedOn` INTEGER NOT NULL, " +
                                        "`trainingSampleCount` INTEGER NOT NULL, " +
                                        "`validationAccuracy` REAL, " +
                                        "`validationPrecision` REAL, " +
                                        "`validationRecall` REAL, " +
                                        "`validationF1` REAL, " +
                                        "`validationAucRoc` REAL, " +
                                        "`deviceModelsSupported` TEXT, " +
                                        "`featureListJson` TEXT, " +
                                        "`hyperparametersJson` TEXT, " +
                                        "`deployedAt` INTEGER, " +
                                        "`retiredAt` INTEGER, " +
                                        "`isActive` INTEGER NOT NULL, " +
                                        "`modelFilePath` TEXT, " +
                                        "`modelSizeBytes` INTEGER NOT NULL, " +
                                        "`notes` TEXT)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_ml_models_modelName ON ml_models(modelName)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_ml_models_taskType ON ml_models(taskType)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_ml_models_isActive ON ml_models(isActive)");
                        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_ml_models_modelName_modelVersion ON ml_models(modelName, modelVersion)");

                        // =====================================================================
                        // ml_predictions: ML prediction audit trail
                        // =====================================================================
                        db.execSQL("CREATE TABLE IF NOT EXISTS `ml_predictions` (" +
                                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                        "`modelId` INTEGER NOT NULL, " +
                                        "`predictionTimestamp` INTEGER NOT NULL, " +
                                        "`inputFeaturesJson` TEXT, " +
                                        "`predictedClass` TEXT, " +
                                        "`predictionConfidence` REAL NOT NULL, " +
                                        "`predictedSeverity` TEXT, " +
                                        "`groundTruthLabel` TEXT, " +
                                        "`wasCorrect` INTEGER, " +
                                        "`deviceFingerprint` TEXT, " +
                                        "`relatedRecordId` INTEGER NOT NULL, " +
                                        "`relatedRecordTable` TEXT, " +
                                        "`inferenceTimeMs` INTEGER NOT NULL, " +
                                        "`modelVersion` TEXT)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_ml_predictions_modelId ON ml_predictions(modelId)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_ml_predictions_predictionTimestamp ON ml_predictions(predictionTimestamp)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_ml_predictions_deviceFingerprint ON ml_predictions(deviceFingerprint)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_ml_predictions_relatedRecordTable ON ml_predictions(relatedRecordTable)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_ml_predictions_wasCorrect ON ml_predictions(wasCorrect)");

                        // =====================================================================
                        // comparison_statistics: OTA statistical significance tests
                        // =====================================================================
                        db.execSQL("CREATE TABLE IF NOT EXISTS `comparison_statistics` (" +
                                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                        "`verificationId` INTEGER NOT NULL, " +
                                        "`sessionId` INTEGER NOT NULL, " +
                                        "`computedAt` INTEGER NOT NULL, " +
                                        "`metricName` TEXT, " +
                                        "`preUpdateMean` REAL, " +
                                        "`preUpdateStdDev` REAL, " +
                                        "`preUpdateN` INTEGER NOT NULL, " +
                                        "`postUpdateMean` REAL, " +
                                        "`postUpdateStdDev` REAL, " +
                                        "`postUpdateN` INTEGER NOT NULL, " +
                                        "`tStatistic` REAL, " +
                                        "`pValue` REAL, " +
                                        "`cohenD` REAL, " +
                                        "`statisticallySignificant` INTEGER NOT NULL, " +
                                        "`practicallySignificant` INTEGER NOT NULL, " +
                                        "`testType` TEXT, " +
                                        "`notes` TEXT, " +
                                        "FOREIGN KEY(`verificationId`) REFERENCES `post_update_verifications`(`id`) ON DELETE CASCADE, " +
                                        "FOREIGN KEY(`sessionId`) REFERENCES `update_sessions`(`id`) ON DELETE CASCADE)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_comparison_statistics_verificationId ON comparison_statistics(verificationId)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_comparison_statistics_sessionId ON comparison_statistics(sessionId)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_comparison_statistics_metricName ON comparison_statistics(metricName)");

                        // =====================================================================
                        // ALTER existing tables: Add ML fields
                        // =====================================================================

                        // anomaly_records: add device fingerprint and ML tracking
                        db.execSQL("ALTER TABLE anomaly_records ADD COLUMN `deviceFingerprint` TEXT");
                        db.execSQL("ALTER TABLE anomaly_records ADD COLUMN `mlPredictionId` INTEGER");
                        db.execSQL("ALTER TABLE anomaly_records ADD COLUMN `detectionMethod` TEXT DEFAULT 'heuristic'");
                        db.execSQL("ALTER TABLE anomaly_records ADD COLUMN `modelVersion` TEXT");

                        // safety_events: add device fingerprint and ML tracking
                        db.execSQL("ALTER TABLE safety_events ADD COLUMN `deviceFingerprint` TEXT");
                        db.execSQL("ALTER TABLE safety_events ADD COLUMN `mlPredictionId` INTEGER");
                        db.execSQL("ALTER TABLE safety_events ADD COLUMN `detectionMethod` TEXT DEFAULT 'heuristic'");

                        // thermal_events: add device fingerprint and ML tracking
                        db.execSQL("ALTER TABLE thermal_events ADD COLUMN `deviceFingerprint` TEXT");
                        db.execSQL("ALTER TABLE thermal_events ADD COLUMN `isNormalForActivity` INTEGER");
                        db.execSQL("ALTER TABLE thermal_events ADD COLUMN `mlPredictionId` INTEGER");

                        // update_sessions: add fingerprint, warm-up exclusion, and contamination tracking
                        db.execSQL("ALTER TABLE update_sessions ADD COLUMN `deviceFingerprint` TEXT");
                        db.execSQL("ALTER TABLE update_sessions ADD COLUMN `warmupPeriodExcluded` INTEGER");
                        db.execSQL("ALTER TABLE update_sessions ADD COLUMN `statisticalSignificanceTested` INTEGER");
                        db.execSQL("ALTER TABLE update_sessions ADD COLUMN `concurrentAppUpdatesJson` TEXT");
                        db.execSQL("ALTER TABLE update_sessions ADD COLUMN `contaminationFlags` TEXT");
                        db.execSQL("ALTER TABLE update_sessions ADD COLUMN `mlImpactPredictionId` INTEGER");

                        // battery_snapshots: add device state and confidence
                        db.execSQL("ALTER TABLE battery_snapshots ADD COLUMN `deviceState` TEXT");
                        db.execSQL("ALTER TABLE battery_snapshots ADD COLUMN `stateConfidence` REAL");
                }
        };
}
