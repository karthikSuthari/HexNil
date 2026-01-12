package com.example.hexnil.manager;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.model.AppDrainRank;
import com.example.hexnil.model.BatteryHealth;
import com.example.hexnil.model.ChargingSession;
import com.example.hexnil.model.ThermalHeatBucket;
import com.example.hexnil.model.UpdateComparison;

import java.util.Collections;
import java.util.List;

/**
 * Supplies persisted dashboard data (no recomputation in UI).
 */
public class DashboardDataProvider {

    public static class DashboardData {
        public List<BatteryHealth> healthHistory = Collections.emptyList();
        public List<AppDrainRank> drainRanks = Collections.emptyList();
        public List<ThermalHeatBucket> heatBuckets = Collections.emptyList();
        public ChargingSession latestCharge;
        public UpdateComparison latestUpdate;
    }

    private final HexNilDatabase db;

    public DashboardDataProvider(HexNilDatabase db) {
        this.db = db;
    }

    public DashboardData load(long now, long windowMs) {
        DashboardData data = new DashboardData();
        long start = now - windowMs;

        data.healthHistory = db.batteryHealthDao().getHealthHistorySince(start);
        data.drainRanks = db.appDrainRankDao().getByWindow("24h");
        data.heatBuckets = db.thermalHeatBucketDao().getBucketsSince(start);
        data.latestCharge = db.chargingSessionDao().getLatest();
        data.latestUpdate = db.miscDao().getLatestUpdateComparison();
        return data;
    }
}