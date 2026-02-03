package com.example.hexnil.ui;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.hexnil.ModuleNavigator;
import com.example.hexnil.R;
import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.model.AIAnalysisResult;
import com.example.hexnil.model.BatteryHealth;
import com.example.hexnil.model.BatterySnapshot;
import com.example.hexnil.service.RemoteMonitorServer;
import com.example.hexnil.ui.chart.OemChartConfig;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * OEM-Grade Dashboard with real-time battery monitoring display.
 * Shows live battery stats, service status, remote access, and database
 * statistics.
 */
public class DashboardFragment extends Fragment {

    private ModuleNavigator navigator;
    private HexNilDatabase database;
    private ExecutorService executor;
    private Handler handler;
    private Runnable refreshRunnable;

    // UI Elements
    private TextView tvServiceStatus;
    private TextView tvUptime;
    private TextView tvLastAiRun;
    private TextView tvBatteryLevel;
    private TextView tvChargingStatus;
    private TextView tvGraphTimeWindow;
    private ProgressBar progressBattery;
    private TextView tvTemperature;
    private TextView tvVoltage;
    private TextView tvCurrent;
    private TextView tvHealth;
    private TextView tvDataIntegrity;
    private TextView tvSnapshotCount;
    private TextView tvSessionCount;
    private TextView tvEventCount;
    private TextView tvMonitors;
    private TextView tvRemoteUrl;
    private SwitchMaterial switchRemote;
    private View statusIndicator;
    private View remoteStatusIndicator;
    
    // Chart
    private LineChart chartBatteryLevel;
    private long chartBaseTimestamp;

    private static long serviceStartTime = System.currentTimeMillis();
    private static final int REFRESH_INTERVAL_MS = 3000;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof ModuleNavigator) {
            navigator = (ModuleNavigator) context;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_dashboard, container, false);

        database = HexNilDatabase.getInstance(requireContext());
        executor = Executors.newSingleThreadExecutor();
        handler = new Handler(Looper.getMainLooper());

        initViews(root);
        setupChart();
        setupNavButtons(root);
        setupRemoteSwitch();

        // Start periodic refresh
        refreshRunnable = this::refreshData;
        handler.post(refreshRunnable);

        return root;
    }

    private void initViews(View root) {
        tvServiceStatus = root.findViewById(R.id.tv_service_status);
        tvUptime = root.findViewById(R.id.tv_uptime);
        tvLastAiRun = root.findViewById(R.id.tv_last_ai_run);
        tvBatteryLevel = root.findViewById(R.id.tv_battery_level);
        tvChargingStatus = root.findViewById(R.id.tv_charging_status);
        tvGraphTimeWindow = root.findViewById(R.id.tv_graph_time_window);
        progressBattery = root.findViewById(R.id.progress_battery);
        tvTemperature = root.findViewById(R.id.tv_temperature);
        tvVoltage = root.findViewById(R.id.tv_voltage);
        tvCurrent = root.findViewById(R.id.tv_current);
        tvHealth = root.findViewById(R.id.tv_health);
        tvDataIntegrity = root.findViewById(R.id.tv_data_integrity);
        tvSnapshotCount = root.findViewById(R.id.tv_snapshot_count);
        tvSessionCount = root.findViewById(R.id.tv_session_count);
        tvEventCount = root.findViewById(R.id.tv_event_count);
        tvMonitors = root.findViewById(R.id.tv_monitors);
        tvRemoteUrl = root.findViewById(R.id.tv_remote_url);
        switchRemote = root.findViewById(R.id.switch_remote);
        statusIndicator = root.findViewById(R.id.status_indicator);
        remoteStatusIndicator = root.findViewById(R.id.remote_status_indicator);
        chartBatteryLevel = root.findViewById(R.id.chart_battery_level);

        ImageButton btnRefresh = root.findViewById(R.id.btn_refresh);
        if (btnRefresh != null) {
            btnRefresh.setOnClickListener(v -> refreshData());
        }
    }
    
    /**
     * Initialize battery level chart with OEM styling.
     */
    private void setupChart() {
        if (chartBatteryLevel == null) return;
        
        OemChartConfig.configureLineChart(chartBatteryLevel, requireContext());
        
        // Set Y-axis range for battery percentage
        chartBatteryLevel.getAxisLeft().setAxisMinimum(0f);
        chartBatteryLevel.getAxisLeft().setAxisMaximum(100f);
        chartBatteryLevel.getAxisLeft().setValueFormatter(new OemChartConfig.PercentAxisFormatter());
        
        // Configure for time series with sliding (show ~6 hours at a time)
        OemChartConfig.configureTimeSeriesChart(chartBatteryLevel, 72); // 72 points = ~6 hours at 5-min intervals
        
        // Add charging threshold line
        OemChartConfig.addSafeThreshold(chartBatteryLevel.getAxisLeft(), 20f, "LOW");
    }

    private void setupNavButtons(View root) {
        setNav(root, R.id.btn_health, "health");
        setNav(root, R.id.btn_idle, "idle");
        setNav(root, R.id.btn_calibration, "calibration");
        setNav(root, R.id.btn_ai, "ai");
        setNav(root, R.id.btn_reports, "reports");
        setNav(root, R.id.btn_compliance, "compliance");
        setNav(root, R.id.btn_device, "device");
        setNav(root, R.id.btn_settings, "settings");
    }

    private void setNav(View root, int buttonId, String tag) {
        MaterialButton btn = root.findViewById(buttonId);
        if (btn != null) {
            btn.setOnClickListener(v -> {
                if (navigator != null)
                    navigator.openModule(tag);
            });
        }
    }

    private void setupRemoteSwitch() {
        // Check current remote server state
        switchRemote.setChecked(RemoteMonitorServer.isRunning());
        updateRemoteStatus();

        switchRemote.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                RemoteMonitorServer.start(requireContext());
            } else {
                RemoteMonitorServer.stop(requireContext());
            }
            // Delay a bit to let service start/stop
            handler.postDelayed(this::updateRemoteStatus, 500);
        });
    }

    private void updateRemoteStatus() {
        if (RemoteMonitorServer.isRunning()) {
            RemoteMonitorServer.ServerInfo info = RemoteMonitorServer.getServerInfo();
            if (info != null) {
                tvRemoteUrl.setText(info.getUrl());
                remoteStatusIndicator.setBackgroundResource(R.drawable.status_indicator_green);
            }
        } else {
            tvRemoteUrl.setText("Enable to access from laptop");
            remoteStatusIndicator.setBackgroundResource(R.drawable.status_indicator_off);
        }
    }

    private void refreshData() {
        if (!isAdded())
            return;

        executor.execute(() -> {
            try {
                // Get latest snapshot
                BatterySnapshot snapshot = database.batterySnapshotDao().getLatest();
                BatteryHealth health = database.batteryHealthDao().getLatest();
                AIAnalysisResult aiResult = database.miscDao().getLatestAIAnalysis();

                // Get counts
                int snapshotCount = database.batterySnapshotDao().getCount();
                int sessionCount = database.chargingSessionDao().getCount();
                int thermalCount = database.thermalEventDao().getCount();
                int drainCount = database.drainEventDao().getCount();
                int safetyCount = database.safetyEventDao().getCount();
                int totalEvents = thermalCount + drainCount + safetyCount;
                
                // Get snapshots for chart (last 24 hours)
                long twentyFourHoursAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000);
                List<BatterySnapshot> chartSnapshots = database.batterySnapshotDao().getSnapshotsSince(twentyFourHoursAgo);
                
                // Count data gaps
                int dataGaps = countDataGaps(chartSnapshots);

                // Update UI on main thread
                handler.post(() -> {
                    updateUI(snapshot, health, aiResult, snapshotCount, sessionCount, totalEvents, dataGaps);
                    updateChart(chartSnapshots);
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // Schedule next refresh
        handler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS);
    }
    
    /**
     * Count significant data gaps (>10 min between snapshots).
     */
    private int countDataGaps(List<BatterySnapshot> snapshots) {
        int gaps = 0;
        if (snapshots == null || snapshots.size() < 2) return 0;
        
        for (int i = 1; i < snapshots.size(); i++) {
            long diff = snapshots.get(i).timestamp - snapshots.get(i-1).timestamp;
            if (diff > 10 * 60 * 1000) { // 10 minutes
                gaps++;
            }
        }
        return gaps;
    }
    
    /**
     * Update the battery level chart with snapshot data.
     */
    private void updateChart(List<BatterySnapshot> snapshots) {
        if (chartBatteryLevel == null || snapshots == null || snapshots.isEmpty()) {
            return;
        }
        
        // Set base timestamp for X-axis formatting
        chartBaseTimestamp = snapshots.get(0).timestamp;
        chartBatteryLevel.getXAxis().setValueFormatter(new OemChartConfig.TimeAxisFormatter(chartBaseTimestamp));
        
        // Create entries for battery level
        List<Entry> levelEntries = new ArrayList<>();
        List<Entry> chargingEntries = new ArrayList<>();
        
        for (int i = 0; i < snapshots.size(); i++) {
            BatterySnapshot s = snapshots.get(i);
            float x = (s.timestamp - chartBaseTimestamp) / (60f * 1000f); // Convert to minutes
            
            levelEntries.add(new Entry(x, s.level));
            
            // Create separate entries for charging periods
            if (s.isCharging) {
                chargingEntries.add(new Entry(x, s.level));
            }
        }
        
        // Create data sets
        List<ILineDataSet> dataSets = new ArrayList<>();
        
        // Primary battery level line
        LineDataSet levelDataSet = OemChartConfig.createPrimaryDataSet(levelEntries, "Battery %");
        dataSets.add(levelDataSet);
        
        // Charging overlay (dashed line)
        if (!chargingEntries.isEmpty()) {
            LineDataSet chargingDataSet = OemChartConfig.createChargingDataSet(chargingEntries, "Charging");
            dataSets.add(chargingDataSet);
        }
        
        // Apply data to chart
        LineData data = new LineData(dataSets);
        chartBatteryLevel.setData(data);
        
        // Move to show latest data
        OemChartConfig.moveToLatest(chartBatteryLevel, levelEntries.size());
        chartBatteryLevel.invalidate();
    }

    private void updateUI(BatterySnapshot snapshot, BatteryHealth health, AIAnalysisResult aiResult,
            int snapshotCount, int sessionCount, int eventCount, int dataGaps) {
        if (!isAdded())
            return;

        // Service status
        tvServiceStatus.setText("MONITORING ACTIVE");
        statusIndicator.setBackgroundResource(R.drawable.status_indicator_green);

        // Uptime
        long uptimeMs = System.currentTimeMillis() - serviceStartTime;
        tvUptime.setText("Uptime: " + formatUptime(uptimeMs));
        
        // Last AI run
        if (tvLastAiRun != null) {
            if (aiResult != null && aiResult.timestamp > 0) {
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                tvLastAiRun.setText("AI: " + sdf.format(new Date(aiResult.timestamp)));
            } else {
                tvLastAiRun.setText("AI: --");
            }
        }

        if (snapshot != null) {
            // Battery level
            tvBatteryLevel.setText(String.format(Locale.US, "%d%%", snapshot.level));
            progressBattery.setProgress(snapshot.level);

            // Charging status
            if (snapshot.isCharging) {
                String chargeType = snapshot.isFastCharging ? "FAST CHARGING" : "CHARGING";
                tvChargingStatus.setText(chargeType);
                tvChargingStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.oem_status_safe));
            } else {
                tvChargingStatus.setText("DISCHARGING");
                tvChargingStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.oem_text_secondary));
            }

            // Temperature
            float tempC = snapshot.temperature / 10.0f;
            tvTemperature.setText(String.format(Locale.US, "%.1f°C", tempC));
            if (tempC > 40) {
                tvTemperature.setTextColor(ContextCompat.getColor(requireContext(), R.color.oem_status_critical));
            } else if (tempC > 35) {
                tvTemperature.setTextColor(ContextCompat.getColor(requireContext(), R.color.oem_status_warning));
            } else {
                tvTemperature.setTextColor(ContextCompat.getColor(requireContext(), R.color.oem_text_primary));
            }

            // Voltage
            tvVoltage.setText(String.format(Locale.US, "%d mV", snapshot.voltage));

            // Current
            if (snapshot.currentNow != null) {
                int currentMa = snapshot.currentNow / 1000;
                tvCurrent.setText(String.format(Locale.US, "%d mA", Math.abs(currentMa)));
            } else {
                tvCurrent.setText("—");
            }
        }

        // Health
        if (health != null) {
            tvHealth.setText(String.format(Locale.US, "%.0f%%", health.healthPercentage));
            if (health.healthPercentage < 80) {
                tvHealth.setTextColor(ContextCompat.getColor(requireContext(), R.color.oem_status_critical));
            } else if (health.healthPercentage < 90) {
                tvHealth.setTextColor(ContextCompat.getColor(requireContext(), R.color.oem_status_warning));
            } else {
                tvHealth.setTextColor(ContextCompat.getColor(requireContext(), R.color.oem_status_safe));
            }
        }
        
        // Data Integrity (monospace technical block)
        if (tvDataIntegrity != null) {
            StringBuilder integrity = new StringBuilder();
            integrity.append(String.format(Locale.US, "Snapshots collected: %s\n", formatCount(snapshotCount)));
            integrity.append(String.format(Locale.US, "Events detected: %s\n", formatCount(eventCount)));
            integrity.append(String.format(Locale.US, "Data gaps (24h): %d\n", dataGaps));
            if (aiResult != null && aiResult.overallConfidenceScore > 0) {
                integrity.append(String.format(Locale.US, "AI confidence: %.0f%%", aiResult.overallConfidenceScore * 100));
            } else {
                integrity.append("AI confidence: --");
            }
            tvDataIntegrity.setText(integrity.toString());
        }

        // Database stats
        tvSnapshotCount.setText(formatCount(snapshotCount));
        tvSessionCount.setText(formatCount(sessionCount));
        tvEventCount.setText(formatCount(eventCount));

        // Active monitors
        tvMonitors.setText(getMonitorsStatus());

        // Update remote status
        updateRemoteStatus();
    }

    private String formatUptime(long ms) {
        long seconds = ms / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return String.format(Locale.US, "%dd %dh %dm", days, hours % 24, minutes % 60);
        } else if (hours > 0) {
            return String.format(Locale.US, "%dh %dm", hours, minutes % 60);
        } else if (minutes > 0) {
            return String.format(Locale.US, "%dm %ds", minutes, seconds % 60);
        } else {
            return String.format(Locale.US, "%ds", seconds);
        }
    }

    private String formatCount(int count) {
        if (count >= 1000000) {
            return String.format(Locale.US, "%.1fM", count / 1000000.0);
        } else if (count >= 1000) {
            return String.format(Locale.US, "%.1fK", count / 1000.0);
        } else {
            return String.valueOf(count);
        }
    }

    private String getMonitorsStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("• Battery Snapshots (adaptive intervals)\n");
        sb.append("• Charging Analysis (session tracking)\n");
        sb.append("• Thermal Monitoring (heat detection)\n");
        sb.append("• Drain Analysis (usage tracking)\n");
        sb.append("• Idle Drain Detection (doze monitoring)\n");
        sb.append("• Safety Monitoring (anomaly detection)\n");
        sb.append("• Calibration Tracking (drift analysis)\n");
        sb.append("• Health Estimation (degradation tracking)\n");
        sb.append("• AI Analysis Engine (comprehensive)");
        return sb.toString();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacks(refreshRunnable);
        if (executor != null) {
            executor.shutdown();
        }
    }
}
