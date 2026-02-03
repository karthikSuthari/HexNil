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
import com.example.hexnil.model.BatterySnapshot;
import com.example.hexnil.model.ThermalEvent;
import com.example.hexnil.ui.chart.OemChartConfig;
import com.example.hexnil.ui.chart.OemDataMarkerView;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * OEM-Grade Thermal Monitoring Module with temperature graph
 * 
 * Displays:
 * - Temperature trend over time with threshold highlighting
 * - Current and peak temperature
 * - Thermal event metrics
 */
public class ThermalFragment extends Fragment {

    private HexNilDatabase database;
    private ExecutorService executor;
    private Handler handler;
    
    // UI Elements
    private TextView tvCurrentTemp;
    private TextView tvPeakTemp;
    private TextView tvEventCount;
    private TextView tvAvgTemp;
    private TextView tvSeverity;
    private TextView tvThermalAssessment;
    
    // Chart
    private LineChart chartTemperature;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_thermal, container, false);
        
        database = HexNilDatabase.getInstance(requireContext());
        executor = Executors.newSingleThreadExecutor();
        handler = new Handler(Looper.getMainLooper());
        
        initViews(root);
        setupChart();
        loadData();
        
        return root;
    }
    
    private void initViews(View root) {
        tvCurrentTemp = root.findViewById(R.id.tv_current_temp);
        tvPeakTemp = root.findViewById(R.id.tv_peak_temp);
        tvEventCount = root.findViewById(R.id.tv_event_count);
        tvAvgTemp = root.findViewById(R.id.tv_avg_temp);
        tvSeverity = root.findViewById(R.id.tv_severity);
        tvThermalAssessment = root.findViewById(R.id.tv_thermal_assessment);
        chartTemperature = root.findViewById(R.id.chart_temperature);
    }
    
    private void setupChart() {
        if (chartTemperature != null) {
            OemChartConfig.configureLineChart(chartTemperature, requireContext());
            
            // Set temperature range
            chartTemperature.getAxisLeft().setAxisMinimum(15f);
            chartTemperature.getAxisLeft().setAxisMaximum(50f);
            chartTemperature.getAxisLeft().setValueFormatter(new OemChartConfig.TemperatureAxisFormatter());
            
            OemChartConfig.configureTimeSeriesChart(chartTemperature, 72); // ~6 hours at 5-min intervals
            
            // Add threshold lines
            OemChartConfig.addWarningThreshold(chartTemperature.getAxisLeft(), 40f, "WARNING 40°C");
            OemChartConfig.addCriticalThreshold(chartTemperature.getAxisLeft(), 45f, "CRITICAL 45°C");
        }
    }
    
    private void loadData() {
        executor.execute(() -> {
            try {
                // Get latest snapshot for current temp
                BatterySnapshot latestSnapshot = database.batterySnapshotDao().getLatest();
                
                // Get snapshots for chart (last 24 hours)
                long twentyFourHoursAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000);
                List<BatterySnapshot> snapshots = database.batterySnapshotDao().getSnapshotsSince(twentyFourHoursAgo);
                
                // Get thermal events
                int thermalEventCount = database.thermalEventDao().getCount();
                ThermalEvent latestEvent = database.thermalEventDao().getLatest();
                
                // Calculate statistics
                float peakTemp = 0;
                float sumTemp = 0;
                int count = 0;
                
                for (BatterySnapshot s : snapshots) {
                    float temp = s.temperature / 10f;
                    if (temp > peakTemp) peakTemp = temp;
                    sumTemp += temp;
                    count++;
                }
                
                float avgTemp = count > 0 ? sumTemp / count : 0;
                float currentTemp = latestSnapshot != null ? latestSnapshot.temperature / 10f : 0;
                
                final float finalPeakTemp = peakTemp;
                final float finalAvgTemp = avgTemp;
                final float finalCurrentTemp = currentTemp;
                
                handler.post(() -> {
                    updateUI(finalCurrentTemp, finalPeakTemp, finalAvgTemp, thermalEventCount, latestEvent);
                    updateTemperatureChart(snapshots);
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
    
    private void updateUI(float currentTemp, float peakTemp, float avgTemp, int eventCount, ThermalEvent latestEvent) {
        // Current temperature
        if (currentTemp > 0) {
            tvCurrentTemp.setText(String.format(Locale.US, "%.1f°C", currentTemp));
            int color = OemChartConfig.getTemperatureColor(currentTemp);
            tvCurrentTemp.setTextColor(color);
        } else {
            tvCurrentTemp.setText("--°C");
        }
        
        // Peak temperature
        if (peakTemp > 0) {
            tvPeakTemp.setText(String.format(Locale.US, "%.1f°C", peakTemp));
            int color = OemChartConfig.getTemperatureColor(peakTemp);
            tvPeakTemp.setTextColor(color);
        } else {
            tvPeakTemp.setText("--°C");
        }
        
        // Event count
        tvEventCount.setText(String.valueOf(eventCount));
        
        // Average temperature
        if (avgTemp > 0) {
            tvAvgTemp.setText(String.format(Locale.US, "%.1f°C", avgTemp));
        } else {
            tvAvgTemp.setText("--°C");
        }
        
        // Severity
        String severity = getSeverityLabel(peakTemp);
        tvSeverity.setText(severity);
        setSeverityColor(severity);
        
        // Assessment
        String assessment = generateThermalAssessment(currentTemp, peakTemp, avgTemp, eventCount);
        tvThermalAssessment.setText(assessment);
    }
    
    private void updateTemperatureChart(List<BatterySnapshot> snapshots) {
        if (chartTemperature == null || snapshots == null || snapshots.isEmpty()) return;
        
        List<Entry> entries = new ArrayList<>();
        long baseTime = snapshots.get(0).timestamp;
        
        // Configure data marker for temperature inspection
        OemChartConfig.configureDataMarkerWithContext(
            chartTemperature,
            requireContext(),
            baseTime,
            60f * 1000f,  // 1 minute per X unit
            "°C",
            "",
            (xValue, yValue) -> {
                // Provide thermal zone context
                if (yValue >= 45) return "CRITICAL ZONE";
                else if (yValue >= 40) return "WARNING ZONE";
                else if (yValue >= 35) return "ELEVATED";
                else return "NORMAL";
            }
        );
        
        for (BatterySnapshot s : snapshots) {
            float x = (s.timestamp - baseTime) / (60f * 1000f); // Minutes
            float temp = s.temperature / 10f;
            entries.add(new Entry(x, temp));
        }
        
        if (entries.isEmpty()) return;
        
        LineDataSet dataSet = OemChartConfig.createTemperatureDataSet(entries, "Temperature °C");
        LineData data = new LineData(dataSet);
        
        chartTemperature.setData(data);
        chartTemperature.getXAxis().setValueFormatter(new OemChartConfig.TimeAxisFormatter(baseTime));
        OemChartConfig.moveToLatest(chartTemperature, entries.size());
        chartTemperature.invalidate();
    }
    
    private String getSeverityLabel(float peakTemp) {
        if (peakTemp <= 35) return "NORMAL";
        if (peakTemp <= 40) return "ELEVATED";
        if (peakTemp <= 45) return "WARNING";
        return "CRITICAL";
    }
    
    private void setSeverityColor(String severity) {
        int color;
        switch (severity) {
            case "CRITICAL":
                color = ContextCompat.getColor(requireContext(), R.color.oem_status_critical);
                break;
            case "WARNING":
            case "ELEVATED":
                color = ContextCompat.getColor(requireContext(), R.color.oem_status_warning);
                break;
            default:
                color = ContextCompat.getColor(requireContext(), R.color.oem_status_safe);
        }
        tvSeverity.setTextColor(color);
    }
    
    private String generateThermalAssessment(float currentTemp, float peakTemp, float avgTemp, int eventCount) {
        StringBuilder sb = new StringBuilder();
        
        // Current status
        if (currentTemp > 0) {
            if (currentTemp <= 35) {
                sb.append("Current temperature: OPTIMAL. ");
            } else if (currentTemp <= 40) {
                sb.append("Current temperature: ELEVATED - reduce load. ");
            } else if (currentTemp <= 45) {
                sb.append("Current temperature: HIGH - thermal throttling possible. ");
            } else {
                sb.append("Current temperature: CRITICAL - battery damage risk. ");
            }
        }
        
        // Peak analysis
        if (peakTemp > 40) {
            sb.append(String.format(Locale.US, "Peak of %.1f°C detected. ", peakTemp));
        }
        
        // Average assessment
        if (avgTemp > 35) {
            sb.append("Average temperature elevated - check cooling. ");
        }
        
        // Event count
        if (eventCount > 10) {
            sb.append(String.format(Locale.US, "%d thermal events - review usage patterns.", eventCount));
        }
        
        if (sb.length() == 0) {
            sb.append("Thermal behavior within normal parameters.");
        }
        
        return sb.toString();
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (executor != null) {
            executor.shutdown();
        }
    }
}
