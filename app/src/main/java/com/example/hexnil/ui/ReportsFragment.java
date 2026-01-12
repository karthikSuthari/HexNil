package com.example.hexnil.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.hexnil.R;
import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.manager.DashboardDataProvider;
import com.example.hexnil.manager.ComplianceMatrixBuilder;
import com.example.hexnil.model.BatteryHealth;
import com.example.hexnil.model.AppDrainRank;
import com.example.hexnil.model.ThermalHeatBucket;
import com.example.hexnil.model.ComplianceReport;
import com.example.hexnil.model.UpdateComparison;
import com.example.hexnil.model.ChargingSession;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReportsFragment extends Fragment {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_reports, container, false);
        TextView daily = root.findViewById(R.id.report_daily);
        TextView weekly = root.findViewById(R.id.report_weekly);
        TextView export = root.findViewById(R.id.report_export_status);
        TextView chartRollups = root.findViewById(R.id.chart_rollups);
        TextView chartThermal = root.findViewById(R.id.chart_thermal);
        TextView chartApps = root.findViewById(R.id.chart_apps);
        TextView chartCharging = root.findViewById(R.id.chart_charging);
        TextView chartUpdate = root.findViewById(R.id.chart_update);

        executor.execute(() -> {
            HexNilDatabase db = HexNilDatabase.getInstance(requireContext());
            DashboardDataProvider dashboards = new DashboardDataProvider(db);
            ComplianceMatrixBuilder compliance = new ComplianceMatrixBuilder(db);
            long now = System.currentTimeMillis();
            DashboardDataProvider.DashboardData data = dashboards.load(now, 7L * 24 * 60 * 60 * 1000L);
            ComplianceReport matrix = compliance.buildMatrix(now - 7L * 24 * 60 * 60 * 1000L, now);

            requireActivity().runOnUiThread(() -> {
                BatteryHealth latestHealth = data.healthHistory.isEmpty() ? null : data.healthHistory.get(data.healthHistory.size() - 1);
                BatteryHealth firstHealth = data.healthHistory.isEmpty() ? null : data.healthHistory.get(0);
                daily.setText(formatHealthTrend(firstHealth, latestHealth));
                weekly.setText(formatDrainBreakdown(data.drainRanks, latestHealth));
                export.setText("Compliance: " + matrix.complianceMatrix + " readiness=" + matrix.certificationReadiness);
                chartRollups.setText(formatAttribution(data.drainRanks));
                chartThermal.setText(formatThermals(data.heatBuckets));
                chartApps.setText(formatTopApps(data.drainRanks));
                chartCharging.setText(formatCharging(data.latestCharge));
                chartUpdate.setText(formatUpdate(data.latestUpdate));
            });
        });

        return root;
    }

    private String formatHealthTrend(BatteryHealth first, BatteryHealth latest) {
        if (latest == null) return "Health trend: no data";
        float start = first != null ? first.healthPercentage : latest.healthPercentage;
        float end = latest.healthPercentage;
        return "Health trend: " + String.format("%.1f", start) + "% → " + String.format("%.1f", end) + "%";
    }

    private String formatDrainBreakdown(java.util.List<AppDrainRank> ranks, BatteryHealth latestHealth) {
        if (ranks == null || ranks.isEmpty()) return "Drain breakdown: no data";
        float system = 0f, user = 0f;
        for (AppDrainRank r : ranks) {
            if (isSystemApp(r.packageName)) system += r.contributionPct;
            else user += r.contributionPct;
        }
        String supplier = latestHealth != null ? " | supplier avg " + String.format("%.1f", latestHealth.supplierAverageHealth) + "%" : "";
        return "Drain breakdown (system/user): " + String.format("%.1f", system) + "% / " + String.format("%.1f", user) + "%" + supplier;
    }

    private String formatAttribution(java.util.List<AppDrainRank> ranks) {
        if (ranks == null || ranks.isEmpty()) return "Attribution: no data";
        AppDrainRank top = ranks.get(0);
        return "Top contributor: " + top.packageName + " (" + String.format("%.1f", top.contributionPct) + "%)";
    }

    private String formatThermals(java.util.List<ThermalHeatBucket> buckets) {
        if (buckets == null || buckets.isEmpty()) return "Heat map: no data";
        ThermalHeatBucket hottest = buckets.get(0);
        for (ThermalHeatBucket b : buckets) {
            if (b.maxTemp > hottest.maxTemp) hottest = b;
        }
        return "Heat map: max " + hottest.maxTemp + "C at hour " + hottest.hourBlock + " (" + hottest.tempBucket + ")";
    }

    private String formatTopApps(java.util.List<AppDrainRank> ranks) {
        if (ranks == null || ranks.isEmpty()) return "Apps: no data";
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (AppDrainRank r : ranks) {
            if (shown++ >= 3) break;
            if (sb.length() > 0) sb.append(" | ");
            sb.append('#').append(r.rank).append(' ').append(r.packageName)
              .append(" (").append(String.format("%.1f", r.contributionPct)).append("%)");
        }
        return "Apps: " + sb.toString();
    }

    private String formatCharging(ChargingSession session) {
        if (session == null) return "Charging: no sessions";
        return "Charging curve pts v/c: " + countPoints(session.voltageCurveJson) + "/" + countPoints(session.currentCurveJson)
                + " maxT=" + session.maxTemperature + "C stability=" + session.curveStabilityScore;
    }

    private String formatUpdate(UpdateComparison update) {
        if (update == null) return "Update impact: no data";
        return "Update impact: " + update.overallImpact + " (drain Δ=" + String.format("%.2f", update.drainRateDelta)
                + "%/h, idle Δ=" + String.format("%.2f", update.idleDrainDelta) + ", conf="
                + String.format("%.0f", update.confidenceLevel) + "%)";
    }

    private int countPoints(String jsonArray) {
        if (jsonArray == null || jsonArray.length() < 2) return 0;
        int count = 0;
        for (int i = 0; i < jsonArray.length(); i++) {
            if (jsonArray.charAt(i) == '{') count++;
        }
        return count;
    }

    private boolean isSystemApp(String pkg) {
        if (pkg == null) return false;
        return pkg.startsWith("com.android") || pkg.startsWith("com.google.android")
                || pkg.startsWith("com.qualcomm") || pkg.startsWith("com.samsung")
                || pkg.startsWith("miui") || pkg.startsWith("com.huawei");
    }
}
