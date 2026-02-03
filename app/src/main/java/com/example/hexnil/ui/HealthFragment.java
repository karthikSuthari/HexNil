package com.example.hexnil.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.hexnil.R;
import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.databinding.FragmentHealthBinding;
import com.example.hexnil.model.BatteryHealth;
import com.example.hexnil.ui.chart.OemChartConfig;
import com.example.hexnil.ui.chart.OemDataMarkerView;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * OEM-Grade Health Module with sliding graphs
 * 
 * Displays:
 * - Health % trend over time
 * - Capacity trend over time  
 * - Cycle count accumulation
 * - Degradation predictions
 */
public class HealthFragment extends Fragment {
    
    private FragmentHealthBinding binding;
    private HexNilDatabase database;
    private ExecutorService executor;
    private Handler handler;
    
    // Charts
    private LineChart chartHealthTrend;
    private LineChart chartCapacityTrend;
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentHealthBinding.inflate(inflater, container, false);
        
        database = HexNilDatabase.getInstance(requireContext());
        executor = Executors.newSingleThreadExecutor();
        handler = new Handler(Looper.getMainLooper());
        
        setupCharts();
        loadData();
        
        return binding.getRoot();
    }
    
    private void setupCharts() {
        // Health Trend Chart
        chartHealthTrend = binding.chartHealthTrend;
        if (chartHealthTrend != null) {
            OemChartConfig.configureLineChart(chartHealthTrend, requireContext());
            chartHealthTrend.getAxisLeft().setAxisMinimum(70f);
            chartHealthTrend.getAxisLeft().setAxisMaximum(100f);
            chartHealthTrend.getAxisLeft().setValueFormatter(new OemChartConfig.PercentAxisFormatter());
            OemChartConfig.configureTimeSeriesChart(chartHealthTrend, 30);
            
            // Add threshold lines
            OemChartConfig.addWarningThreshold(chartHealthTrend.getAxisLeft(), 80f, "WARNING");
            OemChartConfig.addCriticalThreshold(chartHealthTrend.getAxisLeft(), 70f, "CRITICAL");
        }
        
        // Capacity Trend Chart
        chartCapacityTrend = binding.chartCapacityTrend;
        if (chartCapacityTrend != null) {
            OemChartConfig.configureLineChart(chartCapacityTrend, requireContext());
            chartCapacityTrend.getAxisLeft().setValueFormatter(new OemChartConfig.CapacityAxisFormatter());
            OemChartConfig.configureTimeSeriesChart(chartCapacityTrend, 30);
        }
    }
    
    private void loadData() {
        executor.execute(() -> {
            try {
                // Get health history
                List<BatteryHealth> healthHistory = database.batteryHealthDao().getAll();
                BatteryHealth latest = database.batteryHealthDao().getLatest();
                BatteryHealth first = database.batteryHealthDao().getFirst();
                
                handler.post(() -> {
                    updateUI(latest, first, healthHistory);
                    updateHealthChart(healthHistory);
                    updateCapacityChart(healthHistory);
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
    
    private void updateUI(BatteryHealth latest, BatteryHealth first, List<BatteryHealth> history) {
        if (latest == null) {
            binding.tvHealthPercentage.setText("--%");
            binding.tvCycleCount.setText("--");
            binding.tvHealthVerdict.setText("Insufficient data for health assessment.");
            return;
        }
        
        // Health percentage with color
        binding.tvHealthPercentage.setText(String.format(Locale.US, "%.0f%%", latest.healthPercentage));
        int healthColor = OemChartConfig.getHealthColor(latest.healthPercentage);
        binding.tvHealthPercentage.setTextColor(healthColor);
        
        // Cycle count
        binding.tvCycleCount.setText(String.format(Locale.US, "%.0f", latest.cycleCount));
        
        // Design capacity
        if (latest.designCapacityMah > 0) {
            binding.tvDesignCapacity.setText(String.format(Locale.US, "%.0f mAh", latest.designCapacityMah));
        }
        
        // Current capacity
        if (latest.currentCapacityMah > 0) {
            binding.tvCurrentCapacity.setText(String.format(Locale.US, "%.0f mAh", latest.currentCapacityMah));
        }
        
        // Capacity fade
        binding.tvCapacityFade.setText(String.format(Locale.US, "%.1f%%", latest.capacityFade));
        
        // Degradation rate
        binding.tvDegradationRate.setText(String.format(Locale.US, "%.2f%%/month", latest.degradationRate));
        
        // Health trend label
        TextView trendLabel = binding.tvHealthTrendLabel;
        if (trendLabel != null && history != null && history.size() >= 2) {
            float recentHealth = history.get(0).healthPercentage;
            float olderHealth = history.get(Math.min(history.size() - 1, 10)).healthPercentage;
            float diff = recentHealth - olderHealth;
            
            if (Math.abs(diff) < 0.5f) {
                trendLabel.setText("STABLE");
                trendLabel.setTextColor(ContextCompat.getColor(requireContext(), R.color.oem_status_safe));
            } else if (diff < 0) {
                trendLabel.setText("DEGRADING");
                trendLabel.setTextColor(ContextCompat.getColor(requireContext(), R.color.oem_status_warning));
            } else {
                trendLabel.setText("IMPROVING");
                trendLabel.setTextColor(ContextCompat.getColor(requireContext(), R.color.oem_status_safe));
            }
        }
        
        // Verdict
        String verdict = generateHealthVerdict(latest, first, history);
        binding.tvHealthVerdict.setText(verdict);
    }
    
    private void updateHealthChart(List<BatteryHealth> history) {
        if (chartHealthTrend == null || history == null || history.isEmpty()) return;
        
        List<Entry> entries = new ArrayList<>();
        long baseTime = history.get(history.size() - 1).timestamp;
        
        // Configure data marker
        // Time scale: 1 unit = 1 day (24*60*60*1000 ms)
        OemChartConfig.configureDataMarker(
            chartHealthTrend,
            requireContext(),
            baseTime,
            24f * 60f * 60f * 1000f,  // 1 day per X unit
            "%"
        );
        
        // Reverse to chronological order
        for (int i = history.size() - 1; i >= 0; i--) {
            BatteryHealth h = history.get(i);
            float x = (h.timestamp - baseTime) / (24f * 60f * 60f * 1000f); // Days
            entries.add(new Entry(x, h.healthPercentage));
        }
        
        if (entries.isEmpty()) return;
        
        LineDataSet dataSet = OemChartConfig.createSafeDataSet(entries, "Health %");
        LineData data = new LineData(dataSet);
        chartHealthTrend.setData(data);
        
        chartHealthTrend.getXAxis().setValueFormatter(new OemChartConfig.DateTimeAxisFormatter(baseTime));
        OemChartConfig.moveToLatest(chartHealthTrend, entries.size());
        chartHealthTrend.invalidate();
    }
    
    private void updateCapacityChart(List<BatteryHealth> history) {
        if (chartCapacityTrend == null || history == null || history.isEmpty()) return;
        
        List<Entry> entries = new ArrayList<>();
        long baseTime = history.get(history.size() - 1).timestamp;
        
        float minCap = Float.MAX_VALUE;
        float maxCap = 0;
        
        for (int i = history.size() - 1; i >= 0; i--) {
            BatteryHealth h = history.get(i);
            if (h.currentCapacityMah > 0) {
                float x = (h.timestamp - baseTime) / (24f * 60f * 60f * 1000f); // Days
                entries.add(new Entry(x, h.currentCapacityMah));
                minCap = Math.min(minCap, h.currentCapacityMah);
                maxCap = Math.max(maxCap, h.currentCapacityMah);
            }
        }
        
        if (entries.isEmpty()) return;
        
        // Configure data marker
        OemChartConfig.configureDataMarker(
            chartCapacityTrend,
            requireContext(),
            baseTime,
            24f * 60f * 60f * 1000f,
            " mAh"
        );
        
        // Set Y-axis range based on data
        chartCapacityTrend.getAxisLeft().setAxisMinimum(Math.max(0, minCap - 200));
        chartCapacityTrend.getAxisLeft().setAxisMaximum(maxCap + 100);
        
        LineDataSet dataSet = OemChartConfig.createPrimaryDataSet(entries, "Capacity (mAh)");
        LineData data = new LineData(dataSet);
        chartCapacityTrend.setData(data);
        
        chartCapacityTrend.getXAxis().setValueFormatter(new OemChartConfig.DateTimeAxisFormatter(baseTime));
        OemChartConfig.moveToLatest(chartCapacityTrend, entries.size());
        chartCapacityTrend.invalidate();
    }
    
    private String generateHealthVerdict(BatteryHealth latest, BatteryHealth first, List<BatteryHealth> history) {
        StringBuilder sb = new StringBuilder();
        
        if (latest.healthPercentage >= 90) {
            sb.append("Health status: EXCELLENT. ");
        } else if (latest.healthPercentage >= 80) {
            sb.append("Health status: GOOD. ");
        } else if (latest.healthPercentage >= 70) {
            sb.append("Health status: FAIR. Battery showing significant wear. ");
        } else {
            sb.append("Health status: POOR. Battery replacement recommended. ");
        }
        
        if (latest.degradationRate > 0) {
            if (latest.degradationRate <= 0.5) {
                sb.append("Degradation rate is within normal range. ");
            } else if (latest.degradationRate <= 1.0) {
                sb.append("Degradation rate is elevated. ");
            } else {
                sb.append("Degradation rate is accelerated - investigate usage patterns. ");
            }
        }
        
        if (latest.cycleCount > 0) {
            if (latest.cycleCount < 300) {
                sb.append("Cycle count is low.");
            } else if (latest.cycleCount < 500) {
                sb.append("Cycle count is moderate.");
            } else {
                sb.append("Cycle count is high - expect reduced capacity.");
            }
        }
        
        return sb.toString();
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (executor != null) {
            executor.shutdown();
        }
        binding = null;
    }
}
