import sqlite3

conn = sqlite3.connect('sync_data/hexnil_battery_db')
cur = conn.cursor()

print("=" * 70)
print("SAFETY_EVENTS FIELD ANALYSIS")
print("=" * 70)

cur.execute('SELECT COUNT(*) FROM safety_events')
total = cur.fetchone()[0]
print(f"Total records: {total}\n")

# Check each field
fields_to_check = [
    # Group A
    ('deviceModel', "deviceModel IS NOT NULL AND deviceModel != ''"),
    ('firmwareVersion', "firmwareVersion IS NOT NULL AND firmwareVersion != ''"),
    ('batteryPartNumber', "batteryPartNumber IS NOT NULL AND batteryPartNumber != ''"),
    # Group B
    ('eventType', "eventType IS NOT NULL AND eventType != ''"),
    ('severity', "severity IS NOT NULL AND severity != ''"),
    ('wasProtectionTriggered', "wasProtectionTriggered = 1"),
    # Group C
    ('triggerValue', "triggerValue != 0"),
    ('thresholdValue', "thresholdValue != 0"),
    ('measurementUnit', "measurementUnit IS NOT NULL AND measurementUnit != ''"),
    # Group D
    ('activity', "activity IS NOT NULL AND activity != ''"),
    ('chargingType', "chargingType IS NOT NULL AND chargingType != ''"),
    # Group E
    ('ambientTemperature', "ambientTemperature != 0"),
    ('wasThermalShutdown', "wasThermalShutdown = 1"),
    # Group F
    ('expectedVoltage', "expectedVoltage != 0"),
    ('lowestVoltage', "lowestVoltage != 0"),
    # Group G
    ('abuseType', "abuseType IS NOT NULL AND abuseType != ''"),
    ('abuseCount', "abuseCount > 0"),
    ('wasEmergencyShutdown', "wasEmergencyShutdown = 1"),
    # Group H
    ('voltageAnomaly', "voltageAnomaly != 0"),
    ('capacityAnomaly', "capacityAnomaly != 0"),
    ('resistanceAnomaly', "resistanceAnomaly != 0"),
    # Group I
    ('protectionAction', "protectionAction IS NOT NULL AND protectionAction != ''"),
    ('resolutionTime', "resolutionTime > 0"),
    ('wasResolved', "wasResolved = 1"),
    # Group J
    ('exceedsRegulatory', "exceedsRegulatory = 1"),
    ('requiresReport', "requiresReport = 1"),
]

print("Field Population:")
for field, condition in fields_to_check:
    cur.execute(f"SELECT COUNT(*) FROM safety_events WHERE {condition}")
    count = cur.fetchone()[0]
    pct = 100 * count / total if total > 0 else 0
    status = "✓" if pct > 0 else "✗"
    print(f"  {status} {field}: {count}/{total} ({pct:.1f}%)")

# Event type distribution
print("\nEvent Types:")
cur.execute("SELECT eventType, COUNT(*) FROM safety_events GROUP BY eventType ORDER BY COUNT(*) DESC")
for row in cur.fetchall():
    print(f"  {row[0]}: {row[1]}")

# Severity distribution
print("\nSeverity Levels:")
cur.execute("SELECT severity, COUNT(*) FROM safety_events GROUP BY severity ORDER BY COUNT(*) DESC")
for row in cur.fetchall():
    print(f"  {row[0]}: {row[1]}")

print("\n" + "=" * 70)
print("ANOMALY_RECORDS FIELD ANALYSIS")
print("=" * 70)

cur.execute('SELECT COUNT(*) FROM anomaly_records')
total = cur.fetchone()[0]
print(f"Total records: {total}\n")

anomaly_fields = [
    # Group A
    ('title', "title IS NOT NULL AND title != ''"),
    ('sourceApp', "sourceApp IS NOT NULL AND sourceApp != ''"),
    ('sourceAppName', "sourceAppName IS NOT NULL AND sourceAppName != ''"),
    # Group B
    ('anomalyType', "anomalyType IS NOT NULL AND anomalyType != ''"),
    ('confidenceScore', "confidenceScore > 0"),
    # Group C
    ('description', "description IS NOT NULL AND description != ''"),
    ('technicalDetails', "technicalDetails IS NOT NULL AND technicalDetails != ''"),
    ('rootCause', "rootCause IS NOT NULL AND rootCause != ''"),
    ('impact', "impact IS NOT NULL AND impact != ''"),
    ('recommendation', "recommendation IS NOT NULL AND recommendation != ''"),
    # Group D
    ('metricName', "metricName IS NOT NULL AND metricName != ''"),
    ('metricUnit', "metricUnit IS NOT NULL AND metricUnit != ''"),
    ('actualValue', "actualValue != 0"),
    ('expectedValue', "expectedValue != 0"),
    ('deviationPercent', "deviationPercent != 0"),
    # Group E
    ('deviceState', "deviceState IS NOT NULL AND deviceState != ''"),
    ('periodStart', "periodStart > 0"),
    ('periodEnd', "periodEnd > 0"),
    ('dataPointsAnalyzed', "dataPointsAnalyzed > 0"),
    # Group F
    ('sourceComponent', "sourceComponent IS NOT NULL AND sourceComponent != ''"),
    ('relatedAnomalyId', "relatedAnomalyId > 0"),
    # Group G
    ('acknowledged', "acknowledged = 1"),
    ('resolved', "resolved = 1"),
    ('resolution', "resolution IS NOT NULL AND resolution != ''"),
    ('occurrenceCount', "occurrenceCount > 1"),
    ('isRecurring', "isRecurring = 1"),
]

print("Field Population:")
for field, condition in anomaly_fields:
    cur.execute(f"SELECT COUNT(*) FROM anomaly_records WHERE {condition}")
    count = cur.fetchone()[0]
    pct = 100 * count / total if total > 0 else 0
    status = "✓" if pct > 0 else "✗"
    print(f"  {status} {field}: {count}/{total} ({pct:.1f}%)")

# Anomaly type distribution
print("\nAnomaly Types:")
cur.execute("SELECT anomalyType, COUNT(*) FROM anomaly_records GROUP BY anomalyType ORDER BY COUNT(*) DESC")
for row in cur.fetchall():
    print(f"  {row[0]}: {row[1]}")

# Severity distribution  
print("\nSeverity Levels:")
cur.execute("SELECT severity, COUNT(*) FROM anomaly_records GROUP BY severity ORDER BY COUNT(*) DESC")
for row in cur.fetchall():
    print(f"  {row[0]}: {row[1]}")

conn.close()
