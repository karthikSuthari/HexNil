package com.example.hexnil.manager;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.model.BatteryHealth;
import com.example.hexnil.model.BatterySnapshot;
import com.example.hexnil.model.ComplianceReport;
import com.example.hexnil.model.DrainEvent;

import java.util.List;

/**
 * Tracks protection/abuse indicators: fast-charge abuse, over-discharge frequency, swelling risk.
 */
public class ProtectionManager {

    private static final long WINDOW_MS = 7L * 24 * 60 * 60 * 1000;
    private static final float FAST_CHARGE_CURRENT_MA = 2500f;
    private static final float FAST_CHARGE_TEMP_C = 44f;

    private final HexNilDatabase database;
    private int fastChargeAbuse;
    private int overDischargeEvents;
    private boolean swellingRisk;
    private long lastOverDischargeTs;
    private long lastReportTs;

    public ProtectionManager(HexNilDatabase database) {
        this.database = database;
    }

    public void processSnapshot(BatterySnapshot snapshot) {
        if (snapshot == null) return;

        if (snapshot.isFastCharging && (snapshot.chargeRate > FAST_CHARGE_CURRENT_MA || snapshot.getTemperatureCelsius() > FAST_CHARGE_TEMP_C)) {
            fastChargeAbuse += 1;
        }

        if (!snapshot.isCharging && snapshot.level <= 10 && (snapshot.timestamp - lastOverDischargeTs) > 30 * 60 * 1000) {
            overDischargeEvents += 1;
            lastOverDischargeTs = snapshot.timestamp;
        }

        BatteryHealth latestHealth = database.batteryHealthDao().getLatest();
        swellingRisk = latestHealth != null && (latestHealth.hasSwellingRisk || latestHealth.capacityFade > 20);

        boolean shouldReport = fastChargeAbuse >= 3 || overDischargeEvents >= 2 || swellingRisk;
        if (shouldReport && (snapshot.timestamp - lastReportTs) > 6 * 60 * 60 * 1000) {
            persistProtectionReport(snapshot.timestamp);
            lastReportTs = snapshot.timestamp;
            fastChargeAbuse = 0;
            overDischargeEvents = 0;
        }
    }

    public ComplianceReport assessWindow() {
        long now = System.currentTimeMillis();
        return buildReport(now - WINDOW_MS, now);
    }

    private void persistProtectionReport(long timestamp) {
        ComplianceReport report = buildReport(timestamp - WINDOW_MS, timestamp);
        database.miscDao().insertComplianceReport(report);
    }

    private ComplianceReport buildReport(long start, long end) {
        List<DrainEvent> drains = database.drainEventDao().getEventsSince(start);
        int overDischargeCount = 0;
        for (DrainEvent d : drains) {
            if (d.endTime >= start && d.endTime <= end && d.endLevel <= 10 && !d.isCharging) {
                overDischargeCount++;
            }
        }

        List<com.example.hexnil.model.ChargingSession> sessions = database.chargingSessionDao().getSessionsSince(start);
        int fastAbuseCount = 0;
        for (com.example.hexnil.model.ChargingSession cs : sessions) {
            if (cs.startTime < start || cs.startTime > end) continue;
            if (cs.isFastCharging && (cs.maxCurrent > FAST_CHARGE_CURRENT_MA || cs.maxTemperature > FAST_CHARGE_TEMP_C)) {
                fastAbuseCount++;
            }
        }

        BatteryHealth latestHealth = database.batteryHealthDao().getLatest();
        boolean swelling = latestHealth != null && (latestHealth.hasSwellingRisk || latestHealth.capacityFade > 20);

        ComplianceReport report = new ComplianceReport();
        report.timestamp = end;
        report.periodStart = start;
        report.periodEnd = end;
        report.reportType = "protection";
        report.safetyViolationCount = fastAbuseCount + overDischargeCount + (swelling ? 1 : 0);
        report.safetyCompliant = report.safetyViolationCount == 0;
        report.safetyViolations = "fast_charge_abuse=" + fastAbuseCount + ",over_discharge=" + overDischargeCount + ",swelling=" + swelling;
        report.safetyScore = Math.max(0f, 100f - report.safetyViolationCount * 10f);
        report.overDischargeEvents = overDischargeCount;
        report.overDischargeProtectionWorking = overDischargeCount == 0;
        report.overchargeProtectionWorking = true;
        report.overchargeEvents = fastAbuseCount;
        report.thermalProtectionWorking = !swelling;
        report.thermalThrottleEvents = fastAbuseCount;
        report.certificationReadiness = Math.max(0f, 100f - report.safetyViolationCount * 8f);
        report.reviewStatus = "pending";
        report.reviewNotes = report.safetyCompliant ? "All protections within limits" : "Investigate abuse indicators";
        return report;
    }
}
