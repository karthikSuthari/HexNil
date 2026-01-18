# HexNil-AI: Complete Technical Documentation

> **Version**: 1.0.0  
> **Last Updated**: 2026-01-17  
> **Classification**: OEM-Grade Battery Intelligence System

---

## 1. HexNil Overview

### Purpose
HexNil is an **OEM-grade Android battery intelligence and diagnostics system** designed to provide comprehensive battery health monitoring, anomaly detection, charging analysis, thermal management, and AI-powered root cause analysis.

### Target Users
1. **OEM Engineers**: For pre-production battery validation, post-update regression detection, and fleet quality monitoring
2. **Quality Assurance Teams**: For certification compliance evidence and safety validation
3. **End Users**: For battery health visibility and optimization recommendations

### Why HexNil Exists
Standard battery monitoring tools provide only surface-level metrics (percentage, temperature). HexNil provides:
- **Deep diagnostics**: SOC drift detection, charging curve analysis, degradation rate tracking
- **AI-powered root cause analysis**: Not just "battery is draining," but *why* it's draining
- **OEM compliance support**: Structured evidence for IEC 62133, UN 38.3, and UL2054 standards
- **Anti-hallucination architecture**: AI inferences are bounded by deterministic calculations with mandatory confidence scoring

---

## 2. System Architecture

### 2.1 Android Application Layer
```
com.example.hexnil/
├── HexNilApplication.java          # Application lifecycle, database init
├── MainActivity.java               # Entry point, navigation
├── ai/                             # AI Intelligence modules
│   ├── AIBatteryIntelligence.java  # Master AI engine (anti-hallucination)
│   ├── AIReadinessValidator.java   # Data sufficiency gating
│   ├── DeterministicAnalyzer.java  # Exact calculations (no AI)
│   ├── AIOutputStructure.java      # Structured output formatting
│   └── AIAnalysisConfig.java       # Configuration thresholds
├── manager/                        # Business logic managers
│   ├── HexNilAI.java               # Orchestrates AI analysis
│   ├── BatteryDataCollector.java   # Raw data collection
│   ├── BatteryHealthManager.java   # Health calculation engine
│   ├── ChargingAnalyzer.java       # Charging session analysis
│   ├── ThermalMonitor.java         # Thermal event detection
│   ├── DrainAnalyzer.java          # Drain rate tracking
│   ├── IdleDrainAnalyzer.java      # Idle power consumption
│   ├── SafetyMonitor.java          # Safety threshold enforcement
│   ├── CalibrationManager.java     # SOC accuracy tracking
│   ├── ProtectionManager.java      # Abuse detection
│   ├── AnomalyDetector.java        # Pattern anomaly detection
│   ├── UpdateComparisonManager.java# Pre/post update analysis
│   ├── MetricsRollupManager.java   # Time-windowed aggregations
│   ├── TrendAnalyzer.java          # Degradation trend detection
│   ├── IssueClassifier.java        # Issue categorization
│   ├── RiskEvaluator.java          # Risk scoring
│   ├── RecommendationEngine.java   # Action recommendations
│   └── [9 more managers]
├── database/                       # Room persistence layer
├── service/                        # Background services
│   ├── BatteryMonitorService.java  # Foreground monitoring service
│   └── RemoteMonitorServer.java    # HTTP API for external access
├── ui/                             # Fragment-based UI
├── validation/                     # Data integrity validation
├── intelligence/                   # App Intelligence (isolated DB)
└── model/                          # Room entities
```

### 2.2 Foreground Service (`BatteryMonitorService`)
**Purpose**: Continuous battery monitoring with adaptive sampling rates.

**Sampling Intervals**:
| Context | Interval | Trigger |
|---------|----------|---------|
| `thermal_event` | 5 seconds | Temperature ≥40°C |
| `critical_battery` | 10 seconds | Battery ≤10% |
| `fast_charging` | 15 seconds | Fast charge detected |
| `charging` | 30 seconds | Normal charging |
| `low_battery` | 30 seconds | Battery ≤20% |
| `idle` | 5 minutes | Doze mode active |
| `screen_off` | 2 minutes | Screen off, not idle |
| `discharging` | 1 minute | Normal discharge |

**AI Analysis Trigger**: Every 30 minutes, plus 5 minutes after service start.

### 2.3 Room Database Design
**Database Name**: `hexnil_battery_db`  
**Current Version**: 14 (with migrations 1→14)

### 2.4 Backend Pipeline
**Remote Monitor Server**: Embedded HTTP server (port 8080) providing:
- `/api/snapshot/latest` - Latest battery state
- `/api/health` - Battery health metrics
- `/api/ai` - AI analysis results
- `/api/export` - Full database export as JSON

**External Backend** (optional): FastAPI server for MySQL sync and cross-device fleet analysis.

### 2.5 AI Intelligence Layer
**Core Architecture**: Anti-hallucination design with strict separation:
- **Deterministic Layer**: Exact calculations (temperatures, voltages, drain rates)
- **AI Inference Layer**: Pattern interpretation with mandatory confidence scores
- **Gating Layer**: AI blocked if data requirements not met

---

## 3. Data Collection Layer

### 3.1 Battery Snapshots (`BatteryDataCollector`)
**Table**: `battery_snapshots`  
**Frequency**: Adaptive (5 seconds to 5 minutes)

**Collected Fields**:
| Field | Source | Unit |
|-------|--------|------|
| `level` | BatteryManager.EXTRA_LEVEL | % |
| `temperature` | BatteryManager.EXTRA_TEMPERATURE | tenths of °C |
| `voltage` | BatteryManager.EXTRA_VOLTAGE | mV |
| `current` | BatteryManager (API 21+) | µA |
| `chargeCounter` | BatteryManager (API 21+) | µAh |
| `status` | BatteryManager.EXTRA_STATUS | enum |
| `plugged` | BatteryManager.EXTRA_PLUGGED | enum |
| `health` | BatteryManager.EXTRA_HEALTH | enum |
| `screenOn` | PowerManager.isInteractive() | boolean |
| `isCharging` | Derived from status | boolean |
| `isFastCharging` | Derived from current threshold | boolean |
| `chargingType` | USB/AC/Wireless detection | string |
| `qualityScore` | Validation of data consistency | 0-100 |

**Quality Validation**: Each snapshot receives a `qualityScore` based on:
- Timestamp monotonicity
- Level delta reasonableness
- Temperature range validity
- Voltage range validity

### 3.2 App Battery Usage (`AppUsageCollector`)
**Table**: `app_battery_usage`  
**Frequency**: Every 5 minutes (piggybacks on snapshot collection)

**Collected Fields**:
- `packageName`, `appName`, `isSystemApp`
- `foregroundTime`, `backgroundTime` (milliseconds)
- `batteryPercentUsed`, `drainPerHour`
- `wakelockCount`, `wakelockTime`
- `networkBytes`, `alarmCount`
- `efficiencyRating`: "efficient", "normal", "inefficient", "problematic"

**Permission Required**: `PACKAGE_USAGE_STATS` (special permission, must grant in Settings)

### 3.3 Charging Sessions (`ChargingAnalyzer`)
**Table**: `charging_sessions`  
**Lifecycle**: Created on plug-in, finalized on unplug

**Tracked Metrics**:
| Metric | Description |
|--------|-------------|
| `startLevel`, `endLevel` | SOC at session boundaries |
| `startTime`, `endTime` | Timestamps |
| `chargingDuration` | Total charging time (ms) |
| `isFastCharging` | Peak current > 2500mA |
| `maxCurrent`, `avgCurrent` | Current statistics (mA) |
| `maxTemperature`, `avgTemperature` | Thermal statistics (°C) |
| `chargingSpeed` | %/hour rate |
| `chargingEfficiency` | Input vs stored energy ratio |
| `energyDelivered` | mAh delivered |
| `voltageCurveJson`, `currentCurveJson` | Time-series curves |
| `curveStabilityScore` | Charging curve smoothness |
| `chargerType` | USB, AC, Wireless |

### 3.4 Thermal Monitoring (`ThermalMonitor`)
**Tables**: `thermal_events`, `thermal_heat_buckets`

**Thermal Event Detection**:
- **Threshold Crossing**: Records event when temp exceeds 38°C, 42°C, or 45°C
- **Duration Tracking**: How long device stayed in each thermal zone
- **Peak Detection**: Maximum temperature reached

**Heat Buckets** (aggregated by hour):
| Bucket | Temperature Range |
|--------|-------------------|
| `cool` | < 30°C |
| `normal` | 30-38°C |
| `warm` | 38-42°C |
| `hot` | 42-45°C |
| `critical` | > 45°C |

### 3.5 Drain Tracking (`DrainAnalyzer`, `IdleDrainAnalyzer`)
**Table**: `drain_events`, `idle_drain_sessions`

**Drain Event Fields**:
- `startLevel`, `endLevel`, `drainAmount`
- `startTime`, `endTime`, `duration`
- `drainRatePerHour` (calculated: `drainAmount / (duration / 3600000)`)
- `screenOn` (boolean)
- `drainCategory`: "system", "app", "unknown"
- `wakelockCount`, `networkDrainEstimate`

**Idle Drain Session**: Special tracking for screen-off, not-charging periods
- `drainPerHour` during true idle
- `wakelockInterruptions` count

### 3.6 Safety Monitoring (`SafetyMonitor`)
**Table**: `safety_events`

**Safety Thresholds**:
| Event Type | Trigger | Severity |
|------------|---------|----------|
| `over_temp` | ≥45°C | warning/critical |
| `over_voltage` | ≥4500mV | warning |
| `over_current` | ≥2500mA (charging) | warning |
| `over_discharge` | Level <15% | warning |

**Each Event Records**:
- Trigger value and threshold
- Charging state at time of event
- Severity classification

### 3.7 Calibration Tracking (`CalibrationManager`)
**Table**: `calibration_data`

**SOC Drift Detection**:
- `reportedLevel`: What Android reports
- `estimatedActualLevel`: Calculated from `chargeCounter / designCapacity`
- `levelDrift`: Difference between reported and estimated
- `driftDirection`: "under_reporting", "over_reporting", "stable"
- `socAccuracy`: 0-100% accuracy score
- `needsCalibration`: True if drift > 10%
- `calibrationRecommendation`: User guidance

**Sudden Drop Detection**: Tracks when level drops >5% in <15 minutes

---

## 4. Room Database Design

### 4.1 All Tables (23 Tables)

#### Primary Database (`hexnil_battery_db`)
| Table | Entity | Purpose | Retention |
|-------|--------|---------|-----------|
| `battery_snapshots` | BatterySnapshot | Raw sensor data | 30 days |
| `battery_health` | BatteryHealth | Health metrics history | 365 days |
| `charging_sessions` | ChargingSession | Charge event analysis | 90 days |
| `thermal_events` | ThermalEvent | Thermal incidents | 90 days |
| `thermal_heat_buckets` | ThermalHeatBucket | Hourly thermal distribution | 30 days |
| `drain_events` | DrainEvent | Discharge tracking | 30 days |
| `idle_drain_sessions` | IdleDrainSession | Idle power consumption | 30 days |
| `app_battery_usage` | AppBatteryUsage | Per-app consumption | 14 days |
| `app_drain_ranks` | AppDrainRank | App ranking by drain | 7 days |
| `safety_events` | SafetyEvent | Safety threshold violations | 365 days |
| `calibration_data` | CalibrationData | SOC accuracy tracking | 90 days |
| `ai_analysis_results` | AIAnalysisResult | AI insights storage | 30 days |
| `anomaly_records` | AnomalyRecord | Detected anomalies | 90 days |
| `compliance_reports` | ComplianceReport | Certification evidence | 365 days |
| `compliance_evidence` | ComplianceEvidence | Supporting test data | 365 days |
| `stress_test_results` | StressTestResult | Load test results | 180 days |
| `update_comparisons` | UpdateComparison | Pre/post update deltas | 180 days |
| `device_info` | DeviceInfo | Device identification | Forever |

#### App Intelligence Database (`hexnil_app_intelligence.db`) - Isolated
| Table | Purpose |
|-------|---------|
| `ai_app_registry` | App package tracking |
| `ai_app_usage_sessions` | Detailed usage sessions |
| `ai_app_battery_attribution` | Per-app battery attribution |
| `ai_app_thermal_impact` | App thermal contribution |
| `ai_app_anomalies` | App-specific anomaly detection |

### 4.2 Why Each Table is Persisted

| Table | Justification |
|-------|---------------|
| `battery_snapshots` | Foundation for all analysis; enables trend detection and historical comparison |
| `battery_health` | Critical for EOL prediction and degradation tracking; insurance for warranty claims |
| `charging_sessions` | Enables charging behavior analysis; detects charger quality issues |
| `thermal_events` | Safety-critical; evidence for thermal throttling investigations |
| `safety_events` | Compliance requirement; audit trail for safety incidents |
| `ai_analysis_results` | Enables longitudinal tracking of AI insights; supports recommendation evolution |
| `anomaly_records` | Pattern persistence; detects recurring issues; supports OEM fleet analysis |
| `compliance_reports` | Certification evidence; legal and regulatory requirement |

### 4.3 Retention and Integrity Rules

**Retention Manager** (`RetentionManager.java`):
- Runs daily cleanup job
- Respects entity-specific retention policies
- Never deletes data during active analysis

**Integrity Rules**:
- All entities have auto-generated primary keys
- Foreign keys enforced where applicable (device_id references)
- Timestamps use milliseconds since epoch (UTC)
- JSON fields validated for well-formedness

---

## 5. AI & Intelligence Modules (DETAILED)

### 5.1 Trend Analysis (`TrendAnalyzer`)

**Algorithm**:
```java
// Compare 7-day average to 24-hour average
healthTrend = healthDay - healthWeek;
drainTrend = screenOnDay - screenOnWeek;
temperatureTrend = tempDay - tempWeek;

isDegrading = healthTrend < -1.5f || drainTrend > 2.0f || temperatureTrend > 1.5f;
```

**Output Labels**:
- `degrading`: Battery is getting worse
- `improving`: Battery performance improving
- `stable`: No significant change

**NOT Stored**: Trend analysis results are computed on-demand from stored data. Justification: Easy to recompute; storing would create stale data.

### 5.2 Issue Classification (`IssueClassifier`)

**Classification Logic**:
```java
boolean highDrain = avgScreenOnDrain > 18 || avgScreenOffDrain > 3.5f;
boolean thermalRisk = maxPeakTemp > 43;
boolean safetyRisk = criticalSafety > 0;
boolean healthRisk = abnormalWear > 0;
```

**Severity Derivation**:
| Condition | Severity |
|-----------|----------|
| safetyRisk | critical |
| thermalRisk | high |
| healthRisk | high |
| highDrain only | medium |
| None | low |

**Risk Score Formula**:
```java
score = 0;
if (safetyRisk) score += 40;
if (thermalRisk) score += 30;
if (healthRisk) score += 20;
if (highDrain) score += 20;
score += min(20, max(0, (avgScreenOnDrain - 15)));
score += min(20, max(0, (maxPeakTemp - 40)));
return min(100, score);
```

**Issue Categories**:
- `hardware`: Safety or thermal issues
- `firmware`: Health/calibration issues
- `app`: Drain issues only

**NOT Stored**: Classification results computed per-request. Justification: Inputs change frequently; caching would be stale.

### 5.3 Risk Evaluation (`RiskEvaluator`)

**Risk Score Calculation**:
```java
risk = 0f;
risk += clamp(screenOnDrain - 12f, 0f, 20f) * 2f;
risk += clamp(screenOffDrain - 1.5f, 0f, 10f) * 3f;
risk += clamp(idleDrain - 0.7f, 0f, 5f) * 5f;
risk += clamp(maxTemp - 43f, 0f, 8f) * 4f;
risk += clamp(maxChargeTemp - 44f, 0f, 6f) * 4f;
risk += clamp(25f - avgHealth, 0f, 25f);
risk += fastChargeAbuse * 3f;
risk += overDischargeEvents * 4f;
risk += swellingRisk ? 10f : 0f;
return clamp(risk, 0f, 100f);
```

**Severity Thresholds**:
| Risk Score | Severity |
|------------|----------|
| ≥85 | critical |
| ≥65 | high |
| ≥45 | medium |
| ≥25 | low |
| <25 | info |

### 5.4 Metrics Rollups (`MetricsRollupManager`)

**Purpose**: Aggregate raw data into time-windowed summaries for AI consumption.

**Rollup Fields** (`RollupMetrics` - NOT an entity, not stored):
- `screenOnDrain`, `screenOffDrain`, `idleDrain` (averages)
- `avgChargeMinutes`, `avgChargeSpeed`, `maxChargeTemp`
- `avgTemp`, `maxTemp`
- `avgHealth`, `capacityFade`
- `fastChargeAbuse`, `overDischargeEvents`
- `stressScore`, `swellingRisk`
- `sampleCount`

**NOT Stored**: Computed on-demand from raw events. Justification: Rollups are derived aggregates; storing would duplicate data.

### 5.5 Recommendation Engine (`RecommendationEngine`)

**Input**: `IssueClassifier.Result` + `TrendAnalyzer.Trend`

**Recommendation Generation**:
```java
if ("hardware".equals(issueCategory)) {
    recs.add("Reduce thermal load, verify charger quality");
    eng.add("Review charging current limits and thermal throttling");
    user.add("Avoid fast charging in hot environments");
} else if ("firmware".equals(issueCategory)) {
    recs.add("Run a full calibration cycle");
    eng.add("Validate fuel gauge calibration tables");
    user.add("Charge to 100% then discharge to 5%");
}

if (trend.isDegrading) {
    recs.add("Schedule follow-up analysis in 24h");
}

if (riskScore > 70) {
    user.add("Stop heavy usage until stable");
    eng.add("Collect thermal traces for lab review");
}
```

**Output Structure**: `Recommendation` object with `recommendationsJson`, `primaryRecommendation`, `userActions`, `engineerActions`

**NOT Stored**: Recommendations generated on-demand. Justification: Recommendations depend on current state; stale recommendations are harmful.

### 5.6 Confidence Scoring (`AIBatteryIntelligence`)

**Confidence Levels**:
| Level | Threshold | Description |
|-------|-----------|-------------|
| `VERY_HIGH` | ≥0.90 | Strong statistical support, multiple corroborating sources |
| `HIGH` | ≥0.80 | Good statistical support, consistent patterns |
| `MODERATE` | ≥0.70 | Reasonable support, some uncertainty |
| `LOW` | ≥0.60 | Limited support, significant uncertainty |
| `INSUFFICIENT` | <0.60 | Not enough data to make inference |

**Minimum Thresholds for AI Output**:
- `MIN_CONFIDENCE_FOR_ASSERTION`: 60%
- `MIN_CONFIDENCE_FOR_RECOMMENDATION`: 70%
- `MIN_CONFIDENCE_FOR_ROOT_CAUSE`: 75%
- `MIN_EVIDENCE_POINTS`: 3

**Confidence Calculation Example (Health Pattern)**:
```java
confidenceScore = 0.5;  // Baseline

if (voltageRangeHealthy) confidenceScore += 0.15;
if (lowVoltageObserved) confidenceScore += 0.10;

if (cycles < 100) confidenceScore += 0.15;
else if (cycles < 300) confidenceScore += 0.10;

if (evidenceCount < MIN_EVIDENCE_POINTS) {
    confidenceScore *= 0.7;  // Penalty
}
```

### 5.7 Anti-Hallucination Rules

**Core Principles**:
1. AI NEVER runs without passing `AIReadinessValidator`
2. AI NEVER invents data - only interprets existing measurements
3. All inferences have explicit confidence scores
4. Deterministic calculations delegated to `DeterministicAnalyzer`
5. Recommendations are evidence-based with cited data points

**What AI CAN Do (Interpretation/Pattern Recognition)**:
- Identify correlations between factors (temp + load → drain)
- Classify usage patterns (heavy gamer, normal user, power user)
- Detect anomalies that don't fit expected patterns
- Synthesize insights from multiple data sources
- Prioritize issues by impact
- Generate human-readable explanations

**What AI CANNOT Do (Must Stay Deterministic)**:
- Report exact battery percentages
- Report exact temperatures/voltages
- Calculate drain rates
- Determine charge cycle counts
- Make threshold comparisons
- Report timestamps or durations

**Gating Requirements** (`AIReadinessValidator`):
| Requirement | Minimum |
|-------------|---------|
| Snapshots | 50 |
| Observation hours | 4 |
| Charging sessions | 1 |
| Drain events | 3 |

---

## 6. What HexNil Deliberately Does NOT Store

### 6.1 Recommendation Engine Output
**Not Stored Because**: Recommendations are context-sensitive. A recommendation valid today may be wrong tomorrow. Storing introduces staleness risk.

### 6.2 Trend Analysis Results
**Not Stored Because**: Trivial to recompute from stored data. Storing would create data duplication and potential inconsistency.

### 6.3 Issue Classification Results
**Not Stored Because**: Classification depends on current thresholds and algorithms. Algorithm updates would invalidate stored classifications.

### 6.4 Risk Evaluation Output
**Not Stored Because**: Risk is computed from rollup metrics. Storing risk separately from rollups creates dual-source-of-truth problems.

### 6.5 Rollup Metrics
**Not Stored Because**: Aggregates are always computable from raw events. Storing aggregates while retaining raw data is redundant.

### 6.6 Dashboard/Chart Data
**Not Stored Because**: UI-facing data structures are presentation concerns. The data layer should not be coupled to presentation needs.

---

## 7. Anomaly Detection Logic (`AnomalyDetector`)

### 7.1 Detection Thresholds

| Anomaly Type | Metric | Warning | Critical |
|--------------|--------|---------|----------|
| App Drain | drainPerHour | >15%/hr | >25%/hr |
| Temperature | °C | >40°C | >45°C |
| Battery Health | % | <85% | <75% |
| Degradation Rate | %/month | >0.5 | N/A |
| Idle Drain | %/hr | >2%/hr | >5%/hr |
| Charging Efficiency | % | <75% | N/A |
| Wakelock Count | count | >50 | >100 |
| Background Time | ms | >30 min | N/A |

### 7.2 Severity Calculation

**Thermal Severity**:
```java
if (maxTemp > 45) severity = "critical";
else if (maxTemp > 40) severity = "high";
else severity = "medium";
```

**Health Severity**:
```java
if (healthPercentage < 75) severity = "critical";
else if (healthPercentage < 85) severity = "high";
else severity = "medium";

if (hasSwellingRisk) severity = "critical";
```

### 7.3 Root Cause Attribution

**App Drain Causes**:
```java
if (wakelockCount > 50) return "Excessive wakelocks preventing device sleep";
if (backgroundTime > foregroundTime * 2) return "High background activity";
if (networkBytes > 100MB) return "High network data usage";
if (alarmCount > 20) return "Frequent alarm scheduling";
return "General high consumption during active use";
```

**Health Degradation Causes**:
```java
if (cycleCount > 500) causes.add("High cycle count");
if (deepCycleCount > 50) causes.add("Many deep discharge cycles");
if (fadePerMonth > 0.5) causes.add("Accelerated degradation rate");
```

### 7.4 Anomaly Deduplication
Anomalies within 1 hour with same type and source are deduplicated. Recurring anomalies increment `occurrenceCount` and set `isRecurring = true`.

---

## 8. Reports & Dashboards

### 8.1 PDF Reports (`PDFReportBuilder`)
**Generation**: On-demand from `AIAnalysisResult`
**Contents**:
- Executive summary
- Risk score visualization
- Issue breakdown
- Recommendations
- Technical details (for OEM)

**NOT Stored**: PDF bytes are generated on request. Only metadata (generation timestamp) is logged.

### 8.2 CSV Exports (`CSVExporter`)
**Generation**: On-demand from `AIAnalysisResult`
**Format**: Comma-separated values with headers
**Contents**: All AIAnalysisResult fields in tabular format

### 8.3 OEM Dashboards (`DashboardDataProvider`, `ChartDataProvider`)
**Data Flow**:
```
Room DB → DashboardDataProvider.load() → DashboardData → UI Fragment
```

**Dashboard Data Structure**:
```java
class DashboardData {
    List<BatteryHealth> healthHistory;
    List<AppDrainRank> drainRanks;
    List<ThermalHeatBucket> heatBuckets;
    ChargingSession latestCharge;
    UpdateComparison latestUpdate;
}
```

### 8.4 What is Generated vs Stored

| Output | Generated | Stored |
|--------|-----------|--------|
| PDF Report | ✓ | ✗ |
| CSV Export | ✓ | ✗ |
| AIAnalysisResult | ✓ | ✓ (Room) |
| Dashboard Aggregates | ✓ | ✗ |
| Anomaly Records | ✓ | ✓ (Room) |
| Compliance Reports | ✓ | ✓ (Room) |

---

## 9. Update & Comparison Intelligence

### 9.1 Pre/Post Update Analysis (`UpdateComparisonManager`)

**Comparison Windows**: 7 days before vs 7 days after update

**Metrics Compared**:
| Metric | Pre-Update | Post-Update | Delta |
|--------|------------|-------------|-------|
| Screen-on drain | Avg %/hr | Avg %/hr | Δ |
| Screen-off drain | Avg %/hr | Avg %/hr | Δ |
| Idle drain | Avg %/hr | Avg %/hr | Δ |
| Avg charge time | Minutes | Minutes | Δ |
| Avg temperature | °C | °C | Δ |
| Health | % | % | Δ |

### 9.2 Verdict Scoring

**Score Calculation**:
```java
float score = 0f;
float drainDelta = post.screenOnDrain - pre.screenOnDrain;
float tempDelta = post.avgTemperature - pre.avgTemperature;
float healthDelta = post.health - pre.health;

// Negative deltas = improvement
if (drainDelta > 1f) score -= 15;
else if (drainDelta < -1f) score += 10;

if (tempDelta > 1.5f) score -= 10;
if (healthDelta < -1f) score -= 10;

score = clamp(score, -100, 100);
```

**Verdict Labels**:
| Score | Label |
|-------|-------|
| ≥30 | improvement |
| 10 to 29 | slight_improvement |
| -10 to 9 | neutral |
| -30 to -11 | slight_regression |
| <-30 | regression |

### 9.3 Confidence Calculation
```java
int preDays = countDaysWithSamples(preSeries);
int postDays = countDaysWithSamples(postSeries);
float dayRatio = min(1f, (preDays + postDays) / 14f);
return min(95f, dayRatio * 100f);
```

### 9.4 Regression Detection Criteria
A regression is flagged if:
- Screen-on drain increased by >2%/hr
- Temperature increased by >3°C
- Health decreased by >2%
- Idle drain increased by >1%/hr

---

## 10. OEM-Specific Capabilities

### 10.1 Supplier Analysis
**Purpose**: Compare battery behavior across supplier batches

**Available Data Points**:
- Degradation rate variance by batch
- Cycle-to-health correlation
- Thermal resilience comparison

### 10.2 Batch Quality Detection
**Enabled By**: `deviceId` tracking + backend aggregation

**Quality Indicators**:
- Higher-than-fleet-average degradation rate
- Frequent safety events in specific batches
- Calibration accuracy variance

### 10.3 Certification & Compliance Support

**Supported Standards**:
- IEC 62133 (Battery safety)
- UN 38.3 (Transport safety)
- UL 2054 (Household/commercial batteries)

**Evidence Collection** (`ComplianceMatrixBuilder`):
- Safety event history
- Thermal threshold compliance
- Over-discharge protection verification
- Overcharge protection verification

**Compliance Report Fields**:
- `safetyCompliant`: Boolean
- `thermalProtectionWorking`: Boolean
- `overchargeProtectionWorking`: Boolean
- `overDischargeProtectionWorking`: Boolean
- `certificationReadiness`: 0-100 score

---

## 11. Limitations & Constraints

### 11.1 Android API Limits

| API | Minimum | Limitation |
|-----|---------|------------|
| `BatteryManager.BATTERY_PROPERTY_CURRENT_NOW` | API 21 | May return 0 on some OEMs |
| `BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER` | API 21 | Not available on all devices |
| `BatteryManager.BATTERY_PROPERTY_CAPACITY` | API 21 | Reports design, not current capacity |
| `UsageStatsManager` | API 21 | Requires special permission |

### 11.2 Permission Restrictions

| Permission | Type | Required For |
|------------|------|--------------|
| `FOREGROUND_SERVICE` | Normal | Background monitoring |
| `RECEIVE_BOOT_COMPLETED` | Normal | Auto-start on boot |
| `WAKE_LOCK` | Normal | Prevent sleep during analysis |
| `PACKAGE_USAGE_STATS` | Special | App usage collection |
| `BATTERY_STATS` | Signature | Full BatteryStats (OEM only) |

### 11.3 Non-Root vs OEM Builds

| Feature | Non-Root | OEM Build |
|---------|----------|-----------|
| Basic snapshots | ✓ | ✓ |
| Temperature | ✓ | ✓ |
| Voltage | ✓ | ✓ |
| Current | Partial | ✓ |
| Full BatteryStats | ✗ | ✓ |
| Per-app attribution | Limited | ✓ |
| Wakelock details | ✗ | ✓ |

---

## 12. Data Flow (End-to-End)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           HARDWARE/SENSORS                                   │
│  BatteryManager → PowerManager → UsageStatsManager → ThermalManager         │
└────────────────────────────────┬────────────────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                        DATA COLLECTION LAYER                                 │
│  BatteryDataCollector → BatterySnapshot (validated, quality-scored)         │
└────────────────────────────────┬────────────────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          ROOM DATABASE                                       │
│  battery_snapshots, charging_sessions, thermal_events, drain_events...     │
└────────────────────────────────┬────────────────────────────────────────────┘
                                 │
            ┌────────────────────┼────────────────────┐
            ▼                    ▼                    ▼
┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
│ MANAGERS         │  │ AI LAYER         │  │ VALIDATION       │
│ HealthManager    │  │ HexNilAI         │  │ InsertValidator  │
│ ChargingAnalyzer │  │ AIBattery-       │  │ EntityContracts  │
│ ThermalMonitor   │  │ Intelligence     │  │ RetentionManager │
│ DrainAnalyzer    │  │ AnomalyDetector  │  └──────────────────┘
│ SafetyMonitor    │  │ ReadinessValid.  │
│ CalibrationMgr   │  │ Deterministic-   │
│ ProtectionMgr    │  │ Analyzer         │
└────────┬─────────┘  └────────┬─────────┘
         │                     │
         ▼                     ▼
┌──────────────────────────────────────────┐
│          AIAnalysisResult (Stored)       │
│  - Deterministic calculations            │
│  - AI interpretations (with confidence)  │
│  - Root cause analysis                   │
│  - Recommendations                       │
│  - OEM structured output                 │
└────────────────────┬─────────────────────┘
                     │
         ┌───────────┼───────────┐
         ▼           ▼           ▼
┌────────────┐ ┌──────────┐ ┌──────────────┐
│ UI         │ │ Reports  │ │ Remote API   │
│ Fragments  │ │ PDF/CSV  │ │ HTTP Server  │
│ Dashboards │ │ OEM Data │ │ MySQL Sync   │
└────────────┘ └──────────┘ └──────────────┘
```

---

## 13. What Makes HexNil Different From Normal Battery Apps

### 13.1 Technical Differentiation

| Feature | Normal Apps | HexNil |
|---------|-------------|--------|
| Data granularity | Minutes | 5 seconds (adaptive) |
| Drain attribution | Estimated | Calculated from events |
| Health estimation | Single value | Multi-factor model |
| Charging analysis | None | Curve analysis, efficiency |
| AI analysis | None | Evidence-based with confidence |
| OEM support | None | Compliance, fleet analysis |
| Data retention | Volatile | Structured persistence |

### 13.2 Architectural Differentiation

| Aspect | Normal Apps | HexNil |
|--------|-------------|--------|
| Data validation | None | Quality scores, contracts |
| AI safety | N/A | Anti-hallucination gating |
| Confidence scoring | None | Per-inference confidence |
| Audit trail | None | Full evidence citations |
| Reproducibility | None | Deterministic calculations |
| Isolation | N/A | Separate AI database |

---

## 14. Final Capability Matrix

### Data Collection
- [x] Battery level, temperature, voltage, current (adaptive sampling)
- [x] Charging session detection and analysis
- [x] Thermal event tracking with zone classification
- [x] Drain rate calculation (screen-on, screen-off, idle)
- [x] Per-app battery usage attribution
- [x] Safety event detection (over-temp, over-voltage, over-discharge)
- [x] SOC calibration accuracy tracking
- [x] Wakelock and alarm tracking (with permissions)

### Analysis & Intelligence
- [x] Battery health estimation with degradation rate
- [x] EOL prediction
- [x] Charging efficiency calculation
- [x] Charging curve analysis
- [x] Thermal stress assessment
- [x] Idle drain baseline establishment
- [x] Trend analysis (degrading/stable/improving)
- [x] Issue classification (hardware/firmware/app)
- [x] Risk scoring (0-100)
- [x] Root cause analysis with confidence scores
- [x] Recommendation generation (user + engineer actions)
- [x] Anomaly detection with deduplication

### Pre/Post Update Analysis
- [x] 7-day comparison windows
- [x] Regression detection
- [x] Verdict scoring with confidence

### OEM Support
- [x] Compliance evidence collection
- [x] Certification readiness scoring
- [x] Fleet-level aggregation (via backend)
- [x] Structured OEM report generation
- [x] Batch quality indicators

### Persistence & Export
- [x] Room database with 23+ tables
- [x] Schema migrations (v1→v14)
- [x] Retention policies per entity
- [x] JSON export via HTTP API
- [x] PDF report generation
- [x] CSV export
- [x] MySQL sync pipeline (optional)

### Safety & Validation
- [x] AI readiness gating
- [x] Anti-hallucination enforcement
- [x] Data quality validation
- [x] Entity contract enforcement
- [x] Confidence thresholds for outputs

---

## 15. Battery Domain Boundaries & Physical Reality

> **Classification**: OEM-Grade Hardware-Software Boundary Reference  
> **Audience**: Battery hardware engineers, Android framework engineers, certification auditors

This section establishes the authoritative boundary between battery hardware reality, firmware/IC behavior, Android OS representation, and HexNil's software-observable domain. All HexNil design decisions, AI confidence gating, and Room DB persistence philosophy are rooted in these physical constraints.

---

### 15.1 Battery vs Power vs Firmware — Responsibility Boundary

#### 15.1.1 Domain Definitions

**Battery Chemistry & Cell Behavior**:
- Electrochemical reactions within lithium-ion cells
- Solid Electrolyte Interphase (SEI) layer growth
- Lithium plating under fast charge stress
- Thermal runaway thresholds
- Physical swelling from gas evolution
- Intrinsic capacity fade from cycling and calendar aging

**PMIC / Fuel Gauge IC Responsibility**:
- Coulomb counting via current integration
- Open Circuit Voltage (OCV) table lookup for SOC estimation
- Full Charge Capacity (FCC) learning and tracking
- Temperature compensation of measurements
- Charging current/voltage regulation
- Safety cutoff enforcement (over-voltage, over-current, over-temperature)
- Impedance tracking (some advanced gauges only)

**Android OS / Framework Responsibility**:
- Abstraction of BatteryManager properties via HAL
- Power management policies (Doze, App Standby, Battery Saver)
- Thermal framework throttling decisions
- UsageStats aggregation of app foreground/background time
- Reporting battery status/health enum values
- Wakelock accounting and alarm scheduling

**HexNil Software Domain**:
- Collection of Android-exposed metrics at adaptive intervals
- Statistical analysis of collected time-series data
- Pattern detection across charging sessions, drain events, thermal readings
- Confidence-bounded AI inference with mandatory evidence citation
- Anomaly detection from deviation against established baselines
- Trend analysis from longitudinal data

#### 15.1.2 Responsibility Matrix

| Behavior/Metric | Battery Cell | Fuel Gauge IC | Android OS | HexNil |
|-----------------|:------------:|:-------------:|:----------:|:------:|
| Actual chemical capacity | ● | ○ | ✗ | ✗ |
| True SOC (State of Charge) | ● | ◐ | ◐ | ✗ |
| Reported SOC (`level`) | ✗ | ● | ● | ◐ |
| Current measurement | ● | ● | ◐ | ○ |
| Voltage measurement | ● | ● | ● | ○ |
| Temperature measurement | ● | ● | ● | ○ |
| FCC (Full Charge Capacity) | ● | ◐ | ○ | ○ |
| Cycle count (hardware) | ✗ | ◐ | ○ | ◐ |
| Cycle count (calculated) | ✗ | ✗ | ✗ | ● |
| Charging curve shape | ● | ◐ | ✗ | ● |
| Degradation rate | ● | ✗ | ✗ | ◐ |
| Safety cutoff enforcement | ✗ | ● | ○ | ✗ |
| Root cause attribution | ✗ | ✗ | ✗ | ◐ |

**Legend**: ● = Primary responsibility | ◐ = Partial/derived | ○ = Passthrough/read-only | ✗ = No visibility

#### 15.1.3 What HexNil Cannot Attribute Blame To

HexNil explicitly refuses to assign root cause to:
- Battery cell manufacturing defects (requires lab teardown)
- Fuel gauge IC calibration table errors (vendor proprietary)
- PMIC firmware bugs (not observable from Android APIs)
- Charger IC communication failures (below Android abstraction)
- SEI layer degradation rate (no direct measurement possible)
- Lithium plating occurrence (requires post-mortem analysis)

When anomalies are detected, HexNil attributes to the **observable domain** (firmware, app, or environmental) with explicit confidence bounds, never to cell chemistry directly.

---

### 15.2 What Software Can Measure vs What It Can Only Infer

#### 15.2.1 Direct Measurements (Ground Truth)

These values are hardware-reported through Android's BatteryManager API:

| Metric | Source | Unit | HexNil Manager | Room Table |
|--------|--------|------|----------------|------------|
| Battery Level | `EXTRA_LEVEL` | % (0-100) | `BatteryDataCollector` | `battery_snapshots.level` |
| Temperature | `EXTRA_TEMPERATURE` | tenths of °C | `BatteryDataCollector` | `battery_snapshots.temperature` |
| Voltage | `EXTRA_VOLTAGE` | mV | `BatteryDataCollector` | `battery_snapshots.voltage` |
| Current | `BATTERY_PROPERTY_CURRENT_NOW` | µA | `BatteryDataCollector` | `battery_snapshots.current` |
| Charge Counter | `BATTERY_PROPERTY_CHARGE_COUNTER` | µAh | `BatteryDataCollector` | `battery_snapshots.chargeCounter` |
| Charging Status | `EXTRA_STATUS` | enum | `BatteryDataCollector` | `battery_snapshots.status` |
| Plugged Type | `EXTRA_PLUGGED` | enum | `BatteryDataCollector` | `battery_snapshots.plugged` |
| Health Status | `EXTRA_HEALTH` | enum | `BatteryDataCollector` | `battery_snapshots.health` |
| Screen State | `PowerManager.isInteractive()` | boolean | `BatteryDataCollector` | `battery_snapshots.screenOn` |

**Measurement Caveats**:
- `BATTERY_PROPERTY_CURRENT_NOW` returns 0 on some OEM implementations
- `BATTERY_PROPERTY_CHARGE_COUNTER` is not available on all devices
- Temperature sensor placement varies by device (may not reflect cell core temperature)
- Level is already processed by fuel gauge (not raw chemistry state)

#### 15.2.2 Derived Calculations (Deterministic)

These values are computed by `DeterministicAnalyzer` using exact formulas:

| Metric | Formula | HexNil Manager | Room Table |
|--------|---------|----------------|------------|
| Drain Rate (%/hr) | `(startLevel - endLevel) / durationHours` | `DrainAnalyzer` | `drain_events.drainRatePerHour` |
| Charging Rate (%/hr) | `(endLevel - startLevel) / durationHours` | `ChargingAnalyzer` | `charging_sessions.chargingSpeed` |
| Cycle Count | `totalDischarge / 100` | `BatteryHealthManager` | `battery_health.cycleCount` |
| Capacity Fade (%) | `(design - current) / design × 100` | `BatteryHealthManager` | `battery_health.capacityFade` |
| SOC Drift | `estimatedActual - reported` | `CalibrationManager` | `calibration_data.levelDrift` |
| Charging Efficiency | `energyStored / energyInput × 100` | `ChargingAnalyzer` | `charging_sessions.chargingEfficiency` |
| Thermal Duration | `Σ(timeInZone)` per threshold | `ThermalMonitor` | `thermal_heat_buckets` |

**Derivation Caveats**:
- Drain rate accuracy depends on sampling interval
- Charging efficiency relies on current integration accuracy
- Cycle count is equivalent cycles (partial discharge accumulated)

#### 15.2.3 Statistical Inferences (AI Domain)

These values are generated by `AIBatteryIntelligence` with mandatory confidence scoring:

| Inference | Input Data | Confidence Source | HexNil Manager | Stored? |
|-----------|------------|-------------------|----------------|---------|
| Health % | Capacity fade + cycles + resistance | Multi-factor agreement | `BatteryHealthManager` | Yes: `battery_health.healthPercentage` |
| EOL Prediction | Degradation rate trend | Trend stability + data points | `BatteryHealthManager` | Yes: `battery_health.predictedEOL` |
| Degradation Trend | Health delta over time | Linear regression R² | `TrendAnalyzer` | Not stored (recomputable) |
| Usage Pattern | Drain + screen + charging behavior | Pattern consistency | `AIBatteryIntelligence` | Yes: `ai_analysis_results.usagePattern` |
| Root Cause | Anomaly correlation | Evidence count + correlation strength | `AIBatteryIntelligence` | Yes: `ai_analysis_results.rootCauseJson` |
| Swelling Risk | Capacity instability + resistance increase | Threshold deviation severity | `AbnormalWearDetector` | Yes: `battery_health.hasSwellingRisk` |
| Recommendations | Issue classification + risk score | Issue confidence × risk confidence | `RecommendationEngine` | Not stored (context-sensitive) |

**Why Some Inferences Are Not Stored**:
- `TrendAnalyzer` output: Trivially recomputable from `battery_health` history
- `RecommendationEngine` output: Context-sensitive; stale recommendations create harm
- `IssueClassifier` output: Algorithm may evolve; stored classifications become inconsistent
- `MetricsRollupManager` output: Aggregate of raw events; storing duplicates data

---

### 15.3 Fuel Gauge IC & PMIC Physical Limitations

#### 15.3.1 Full Charge Capacity (FCC) Drift

**Root Cause**: Fuel gauge IC learns FCC by integrating current during full charge cycles. Without regular 0→100% cycles, FCC estimate becomes stale.

**Observable Effect**: Reported level jumps (e.g., 30% → 20% in seconds) as gauge corrects internally.

**HexNil Response**:
- `CalibrationManager.suddenDropCount` tracks these events
- `calibration_data.hasSuddenDrops` flags unstable SOC reporting
- `battery_health.hasCalibrationIssue` triggers calibration recommendation
- AI confidence degrades when sudden drops exceed threshold

**Justification for Confidence Scoring**: Without ground-truth FCC, HexNil cannot distinguish between actual capacity loss and gauge error. Confidence reflects this ambiguity.

#### 15.3.2 Open Circuit Voltage (OCV) Table Inaccuracies

**Root Cause**: OCV tables are calibrated at factory for specific cell chemistry. Aging changes the OCV-SOC relationship.

**Observable Effect**: Level plateaus (e.g., stuck at 50% for extended time) followed by rapid changes.

**HexNil Response**:
- `BatteryDataCollector.qualityScore` penalizes non-monotonic level behavior
- `calibration_data.socAccuracy` tracks deviation from expected behavior
- AI explicitly notes OCV uncertainty in low-confidence scenarios

#### 15.3.3 Charge Counter Instability

**Root Cause**: Coulomb counting accumulates integration error over time. Temperature affects current sensor accuracy.

**Observable Effect**: `chargeCounter` value diverges from actual energy transferred.

**HexNil Response**:
- `BatteryHealthManager.estimateCapacity()` uses multiple methods, not sole reliance on charge counter
- `BatteryHealthManager.calculateMeasurementVariance()` detects high variance in estimates
- Capacity confidence (`battery_health.capacityConfidence`) is reduced when variance is high

#### 15.3.4 Temperature Sensor Placement Bias

**Root Cause**: Sensor location (near PMIC, on battery pack surface, or on PCB) affects readings. Cell core temperature can be 5-10°C higher than surface during fast charge.

**Observable Effect**: Thermal events may not trigger until after cell damage has occurred.

**HexNil Response**:
- `ThermalMonitor` uses conservative thresholds (38°C, 42°C, 45°C surface → implies higher core)
- `thermal_events.peakTemperature` stored with awareness that core temperature is unknowable
- AI never claims to know internal cell temperature; reports surface-equivalent values only

#### 15.3.5 Vendor-Dependent Behavior

**Root Cause**: Each SoC vendor (Qualcomm, MediaTek, Exynos) implements BatteryManager HAL differently. Some expose full data; others stub methods.

**Observable Effect**:
- `BATTERY_PROPERTY_CURRENT_NOW` returns 0 on some devices
- `BATTERY_PROPERTY_CHARGE_COUNTER` unavailable on many devices
- Fast-charge detection varies by charger IC

**HexNil Response**:
- `BatteryDataCollector` validates all readings before persistence
- `battery_snapshots.qualityScore` reflects data completeness
- `AIReadinessValidator` requires minimum valid data points before enabling AI
- AI explicitly notes when analysis is degraded due to missing sensor data

---

### 15.4 Battery Chemistry & Aging Reality (High-Level)

#### 15.4.1 Cycle Aging

**Physical Reality**: Each charge-discharge cycle consumes active lithium and creates SEI layer buildup. Degradation is capacity loss per cycle.

**Industry Baseline**: Quality Li-ion cells: <0.05% capacity loss per cycle for first 300 cycles.

**HexNil Metrics**:
- `battery_health.fadePerCycle`: Directly computed from fade/cycles
- `battery_health.fadePer100Cycles`: Industry-standard comparison metric
- Anomaly detection: `AnomalyDetector` flags >0.08% fade/cycle as abnormal

#### 15.4.2 Calendar Aging

**Physical Reality**: Batteries degrade even when unused. SEI layer grows passively. Higher temperature accelerates calendar aging.

**Industry Baseline**: 2-5% capacity loss per year at room temperature storage at 50% SOC.

**HexNil Metrics**:
- `battery_health.fadePerMonth`: Calendar-based degradation rate
- `battery_health.hasAbnormalAging`: Flags calendar aging faster than cycle aging would predict
- Tracking: `battery_health.trackingDays` enables calendar-normalized analysis

#### 15.4.3 High-SOC Aging (80-100%)

**Physical Reality**: Lithium-ion cells experience accelerated degradation when held at high state of charge. Electrolyte oxidation increases at high voltage.

**Industry Data**: Storing at 100% SOC vs 50% SOC can double degradation rate.

**HexNil Metrics**:
- `DeterministicAnalyzer.calculateSOCDistribution()`: Time spent in high SOC buckets
- `battery_health.topOffCycleCount`: Counts 80%→100% charge events
- Anomaly: Excessive time in 90-100% range flagged in AI analysis

**HexNil Detection**:
- `charging_sessions` track how often device reaches 100%
- AI correlates high top-off frequency with accelerated fade

#### 15.4.4 Temperature-Accelerated Degradation

**Physical Reality**: Every 10°C increase roughly doubles reaction rates. High temperature during charging is especially damaging.

**Industry Data**: Charging at 45°C vs 25°C can increase degradation 2-4x.

**HexNil Metrics**:
- `thermal_heat_buckets`: Time spent in each thermal zone per hour
- `charging_sessions.maxTemperature`: Peak temperature during charge
- `battery_health.thermalScore`: Health score component based on thermal history
- Anomaly: `thermal_events` with `severity=critical` during charging sessions

#### 15.4.5 Fast-Charge Stress

**Physical Reality**: High charging current creates concentration gradients, mechanical stress, and lithium plating risk. Damage accumulates with repeated fast-charge cycles.

**HexNil Metrics**:
- `charging_sessions.isFastCharging`: Detected via current threshold (>2500mA)
- `battery_health.fastChargeAbuse` (via rollup): Count of fast-charge at high temperature
- `RiskEvaluator`: Includes `fastChargeAbuse * 3` in risk score calculation

---

### 15.5 Environmental & User-Behavior Factors

#### 15.5.1 Ambient Temperature Influence

**Reality**: Device temperature is influenced by ambient environment, not just usage. Cold weather reduces available capacity; hot weather accelerates degradation.

**HexNil Detection**:
- `battery_snapshots.temperature` collected at each interval
- `ThermalMonitor` tracks when temperature exceeds thresholds
- AI notes: "Analysis period included X hours in elevated thermal zone"

**False Blame Avoidance**:
- HexNil does not blame the battery for high temperatures caused by environment
- Root cause attribution includes "environmental thermal stress" as distinct from "app-induced thermal"

#### 15.5.2 Charging While Gaming

**Reality**: Simultaneous high current draw (gaming) and high current input (charging) creates extreme thermal stress. This is one of the most damaging usage patterns.

**HexNil Detection**:
- `charging_sessions` with `maxTemperature > 42°C` AND high drain rate correlation
- `BatteryDataCollector` captures both `isCharging` and `screenOn`
- Anomaly: `ChargingAnalyzer.curveStabilityScore` degrades during load+charge

**User Guidance**:
- `RecommendationEngine` generates: "Avoid heavy usage while charging above 80%"

#### 15.5.3 Overnight Charging Patterns

**Reality**: Typical overnight charge reaches 100% within 2 hours, then maintains float charge for 6+ hours. Float charging at 100% SOC accelerates calendar aging.

**HexNil Detection**:
- `charging_sessions.duration` tracks time from plug-in to unplug
- Sessions with duration >6 hours and endLevel=100% flagged as overnight
- AI correlates overnight frequency with accelerated health decline

#### 15.5.4 Navigation / Hotspot / Camera Workloads

**Reality**: GPS + screen + cellular (navigation), or hotspot (LTE radio + WiFi AP), or camera (ISP + display + storage) are sustained high-power workloads.

**HexNil Detection**:
- `drain_events` with high `drainRatePerHour` (>15%/hr) during screen-on
- `app_battery_usage` tracks per-app `batteryPercentUsed` and `foregroundTime`
- `IdleDrainAnalyzer` establishes baseline; deviation indicates workload

**False Blame Avoidance**:
- HexNil does not flag expected high drain during navigation as "problematic"
- AI notes: "High drain rate consistent with navigation/hotspot workload pattern"
- Only flags if drain significantly exceeds expected range for detected workload

---

### 15.6 What HexNil Will NEVER Claim to Know (Hard Limits)

#### 15.6.1 Fundamentally Unknowable in Software

| Metric | Why Unknowable | HexNil Position |
|--------|----------------|-----------------|
| True cell internal temperature | Sensor is on surface/PCB, not inside cell | Report measured value; acknowledge core is higher |
| Actual mAh capacity | Requires controlled lab discharge | Report estimate with confidence bounds |
| Lithium plating occurrence | Requires cell teardown | Infer risk from fast-charge + low-temp correlation |
| SEI thickness | Requires electrochemical analysis | Infer degradation from capacity trend |
| Internal short risk | Requires impedance spectroscopy | Flag based on resistance increase trend |
| Manufacturing defects | Requires batch quality data | Flag deviation from fleet average (if available) |
| Exact remaining lifespan | Depends on future usage | Provide prediction range with stated assumptions |

#### 15.6.2 Why HexNil Refuses to Guess

**Anti-Hallucination Commitment**:
1. All inferences require minimum evidence (`MIN_EVIDENCE_POINTS = 3`)
2. All assertions require minimum confidence (`MIN_CONFIDENCE_FOR_ASSERTION = 60%`)
3. Root cause claims require elevated confidence (`MIN_CONFIDENCE_FOR_ROOT_CAUSE = 75%`)
4. Recommendations require moderate confidence (`MIN_CONFIDENCE_FOR_RECOMMENDATION = 70%`)

**Explicit Rejection Cases**:
- `AIReadinessValidator` blocks AI if data requirements not met
- `AIBatteryIntelligence.AIInference.meetsMinimumConfidence()` gates output
- Inferences below threshold are excluded from `AIAnalysisResult`

#### 15.6.3 How This Shapes Anti-Hallucination Rules

**`AIReadinessValidator` Gating** (from existing implementation):

| Analysis Type | Minimum Snapshots | Minimum Observation Hours | Other Requirements |
|---------------|-------------------|---------------------------|-------------------|
| Health Analysis | 50 | 4 | ≥1 charge cycle, SOC range coverage |
| Charging Analysis | 50 | 4 | ≥3 charging sessions |
| Drain Analysis | 50 | 4 | ≥3 drain events, screen state data |
| Thermal Analysis | 50 | 4 | Temperature variance present |
| Calibration Analysis | 100 | 24 | ≥1 full cycle, edge SOC readings |

**Confidence Degradation Factors**:
- Missing sensor data (current = 0, charge counter unavailable)
- High measurement variance
- Insufficient time coverage
- Lack of calibration cycles
- Recent algorithm updates invalidating historical patterns

**AI Output Structure Requirements**:
- Every `AIInference` includes `evidenceCitations` (specific data points referenced)
- Every `AIInference` includes `caveats` (acknowledged limitations)
- Every `RootCauseAnalysis` includes `deterministicData` (exact measurements supporting inference)

---

### 15.7 Architectural Consequences in HexNil

#### 15.7.1 Why Trends, Rollups, and Recommendations Are Not Stored

| Derived Output | Room Storage | Justification |
|----------------|--------------|---------------|
| `TrendAnalyzer.Trend` | Not stored | Recomputable from `battery_health` history in O(n) time |
| `MetricsRollupManager.RollupMetrics` | Not stored | Aggregate of raw events; storing creates dual source of truth |
| `RecommendationEngine.Recommendation` | Not stored | Context-sensitive; stale recommendations harmful to user |
| `IssueClassifier.Result` | Not stored | Algorithm may evolve; old classifications become inconsistent |
| `RiskEvaluator` score | Not stored | Computed from rollups; storing score without inputs is meaningless |
| Dashboard chart data | Not stored | Presentation layer concern; not core data model |

**Principle**: Store evidence, not opinions. Evidence retention enables re-analysis with improved algorithms.

#### 15.7.2 Why Room DB Stores Evidence, Not Opinions

**Evidence Tables** (Raw or lightly processed measurements):
- `battery_snapshots`: Raw sensor readings at each sample
- `charging_sessions`: Observable session boundaries and measured parameters
- `thermal_events`: Threshold crossing events with exact values
- `drain_events`: Discharge periods with measured rates
- `safety_events`: Safety threshold violations with trigger values
- `calibration_data`: SOC drift measurements

**Derived but Persisted** (With confidence, for longitudinal tracking):
- `battery_health`: Health metrics with explicit confidence scores
- `ai_analysis_results`: AI insights with evidence citations
- `anomaly_records`: Detected anomalies with severity and evidence

**Design Rationale**:
- Evidence persists across algorithm updates
- Opinions (recommendations, classifications) can be regenerated
- Auditors can verify AI conclusions against raw evidence
- Fleet analysis requires consistent evidence schema

#### 15.7.3 Why AI Outputs Are Recomputable

**Recomputation Guarantee**:
```
Given: battery_snapshots + charging_sessions + drain_events + thermal_events
Then: AIBatteryIntelligence.performAnalysis() produces equivalent output
```

**Implementation Mechanism**:
1. `DeterministicAnalyzer` produces identical results for identical inputs (pure functions)
2. `AIReadinessValidator` gates consistently based on data availability
3. `AIBatteryIntelligence` calculates confidence from data characteristics (not cached values)
4. `RecommendationEngine` generates recommendations from current state (not historical recommendations)

**OEM Audit Benefit**:
- Re-run analysis on historical data to verify conclusions
- Compare AI behavior across algorithm versions
- Reproduce any reported anomaly from stored evidence

#### 15.7.4 Reference to HexNil Components

**Room DB Implementation** (`HexNilDatabase`):
- 23 tables across primary and App Intelligence databases
- Schema version 14 with full migration path
- Retention policies enforced by `RetentionManager`
- Entity contracts validated by `InsertValidator`

**HexNilAI** (`manager/HexNilAI.java`):
- Orchestrates AI analysis pipeline
- Coordinates `AIReadinessValidator` → `DeterministicAnalyzer` → `AIBatteryIntelligence`
- Produces `AIAnalysisResult` with full evidence trail

**RecommendationEngine** (`manager/RecommendationEngine.java`):
- Input: `IssueClassifier.Result` + `TrendAnalyzer.Trend`
- Output: Context-sensitive recommendations (never persisted)
- Generates both user-facing and engineer-facing action items

**TrendAnalyzer** (`manager/TrendAnalyzer.java`):
- Compares 7-day vs 24-hour averages for health, drain, temperature
- Returns `Trend` object with direction and magnitude
- Pure function of stored `battery_health` history

---

### 15.8 Summary: Physical Reality → HexNil Design

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     BATTERY CELL (PHYSICAL REALITY)                         │
│  - Electrochemistry governs behavior                                        │
│  - Degradation determined by chemistry, temperature, usage                  │
│  - True state unknowable without lab equipment                              │
└───────────────────────────────────┬─────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                     FUEL GAUGE IC / PMIC (ESTIMATION LAYER)                 │
│  - Coulomb counting + OCV lookup = SOC estimation                           │
│  - FCC learning (degrades without full cycles)                              │
│  - Temperature compensation (sensor placement bias)                         │
│  - Vendor-specific implementation differences                               │
└───────────────────────────────────┬─────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                     ANDROID OS (ABSTRACTION LAYER)                          │
│  - BatteryManager API exposes measurements                                  │
│  - Some properties unavailable or return 0                                  │
│  - HAL implementation varies by vendor                                      │
│  - No guarantee of accuracy or consistency                                  │
└───────────────────────────────────┬─────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                     HEXNIL DATA COLLECTION                                  │
│  - BatteryDataCollector samples at adaptive intervals                       │
│  - Quality validation assigns qualityScore                                  │
│  - Missing data flagged, not invented                                       │
│  - Raw snapshots persisted to Room DB                                       │
└───────────────────────────────────┬─────────────────────────────────────────┘
                                    │
              ┌─────────────────────┼─────────────────────┐
              ▼                     ▼                     ▼
┌──────────────────────┐ ┌──────────────────────┐ ┌──────────────────────┐
│ DETERMINISTIC        │ │ MANAGERS             │ │ AI LAYER             │
│ (DeterministicAnalyzer)│ │ (Health, Charging,  │ │ (AIBatteryIntelligence│
│ - Pure math only     │ │  Thermal, Drain)     │ │  + AIReadinessValidator│
│ - Same input = same  │ │ - Derived metrics    │ │ - Confidence-bounded │
│   output             │ │ - Persisted with     │ │ - Evidence-cited     │
│ - No interpretation  │ │   confidence         │ │ - Anti-hallucination │
└──────────────────────┘ └──────────────────────┘ └──────────────────────┘
              │                     │                     │
              └─────────────────────┴─────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                     ROOM DB (EVIDENCE LAYER)                                │
│  - Stores measurements, not opinions                                        │
│  - Derived values include confidence scores                                 │
│  - Supports re-analysis with updated algorithms                             │
│  - Retention policies preserve audit trail                                  │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Core Design Principle**: HexNil acknowledges that software observes an abstraction of battery reality, not the reality itself. Every HexNil output explicitly states its confidence level and evidence basis, enabling OEM engineers, certification auditors, and end users to understand both what HexNil knows and what it cannot know.

---

*End of HexNil-AI Technical Documentation*
