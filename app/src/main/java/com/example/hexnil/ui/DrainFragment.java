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
import com.example.hexnil.model.DrainEvent;
import com.example.hexnil.model.IdleDrainSession;
import com.example.hexnil.ui.chart.OemChartConfig;
import com.example.hexnil.ui.chart.OemDataMarkerView;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * OEM-Grade Drain Analysis Module with sliding graphs
 * 
 * Displays:
 * - Screen ON vs Screen OFF drain rate trend
 * - Idle drain sessions bar chart
 * - Drain metrics and attribution
 */
public class DrainFragment extends Fragment {

    private HexNilDatabase database;
    private ExecutorService executor;
    private Handler handler;
    
    // UI Elements
    private TextView tvScreenOnDrain;
    private TextView tvIdleDrain;
    private TextView tvDrainEvents;
    private TextView tvAvgDrainRate;
    private TextView tvPrimaryCause;
    private TextView tvDrainAssessment;
    
    // Charts
    private LineChart chartDrainRate;
    private BarChart chartIdleDrain;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_drain, container, false);
        
        database = HexNilDatabase.getInstance(requireContext());
        executor = Executors.newSingleThreadExecutor();
        handler = new Handler(Looper.getMainLooper());
        
        initViews(root);
        setupCharts();
        loadData();
        
        return root;
    }
    
    private void initViews(View root) {
        tvScreenOnDrain = root.findViewById(R.id.tv_screen_on_drain);
        tvIdleDrain = root.findViewById(R.id.tv_idle_drain);
        tvDrainEvents = root.findViewById(R.id.tv_drain_events);
        tvAvgDrainRate = root.findViewById(R.id.tv_avg_drain_rate);
        tvPrimaryCause = root.findViewById(R.id.tv_primary_cause);
        tvDrainAssessment = root.findViewById(R.id.tv_drain_assessment);
        
        chartDrainRate = root.findViewById(R.id.chart_drain_rate);
        chartIdleDrain = root.findViewById(R.id.chart_idle_drain);
    }
    
    private void setupCharts() {
        // Drain Rate Line Chart
        if (chartDrainRate != null) {
            OemChartConfig.configureLineChart(chartDrainRate, requireContext());
            chartDrainRate.getAxisLeft().setAxisMinimum(0f);
            chartDrainRate.getAxisLeft().setValueFormatter(new OemChartConfig.DrainRateAxisFormatter());
            OemChartConfig.configureTimeSeriesChart(chartDrainRate, 48);
            
            // Add threshold line for idle drain
            OemChartConfig.addWarningThreshold(chartDrainRate.getAxisLeft(), 2f, "IDLE LIMIT");
        }
        
        // Idle Drain Bar Chart
        if (chartIdleDrain != null) {
            OemChartConfig.configureBarChart(chartIdleDrain);
            chartIdleDrain.getAxisLeft().setAxisMinimum(0f);
            chartIdleDrain.getAxisLeft().setAxisMaximum(5f);
            chartIdleDrain.getAxisLeft().setValueFormatter(new OemChartConfig.DrainRateAxisFormatter());
        }
    }
    
    private void loadData() {
        executor.execute(() -> {
            try {
                // Get drain events (last 24 hours)
                long oneDayAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000);
                List<DrainEvent> drainEvents = database.drainEventDao().getEventsSince(oneDayAgo);
                
                // Get average drain rates
                Float avgScreenOnDrain = database.drainEventDao().getAverageScreenOnDrain(oneDayAgo);
                Float avgScreenOffDrain = database.drainEventDao().getAverageScreenOffDrain(oneDayAgo);
                
                // Get idle drain sessions (last 7 days)
                long sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000);
                List<IdleDrainSession> idleSessions = database.idleDrainSessionDao().getSessionsSince(sevenDaysAgo);
                
                // Get event count
                int eventCount = database.drainEventDao().getCount();
                
                handler.post(() -> {
                    updateUI(drainEvents, avgScreenOnDrain, avgScreenOffDrain, eventCount);
                    updateDrainRateChart(drainEvents);
                    updateIdleDrainChart(idleSessions);
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
    
    private void updateUI(List<DrainEvent> events, Float avgScreenOnDrain, Float avgScreenOffDrain, int eventCount) {
        // Screen ON drain
        if (avgScreenOnDrain != null && avgScreenOnDrain > 0) {
            tvScreenOnDrain.setText(String.format(Locale.US, "%.1f %%/hr", avgScreenOnDrain));
            int color = OemChartConfig.getDrainRateColor(avgScreenOnDrain, true);
            tvScreenOnDrain.setTextColor(color);
        } else {
            tvScreenOnDrain.setText("-- %/hr");
        }
        
        // Idle drain
        if (avgScreenOffDrain != null && avgScreenOffDrain > 0) {
            tvIdleDrain.setText(String.format(Locale.US, "%.1f %%/hr", avgScreenOffDrain));
            int color = OemChartConfig.getDrainRateColor(avgScreenOffDrain, false);
            tvIdleDrain.setTextColor(color);
        } else {
            tvIdleDrain.setText("-- %/hr");
        }
        
        // Event count
        tvDrainEvents.setText(String.valueOf(eventCount));
        
        // Average drain rate
        if (avgScreenOnDrain != null && avgScreenOffDrain != null) {
            float avg = (avgScreenOnDrain + avgScreenOffDrain) / 2f;
            tvAvgDrainRate.setText(String.format(Locale.US, "%.1f %%/hr", avg));
        }
        
        // Primary cause (most common drain category)
        String primaryCause = getPrimaryCause(events);
        tvPrimaryCause.setText(primaryCause);
        
        // Assessment
        String assessment = generateDrainAssessment(avgScreenOnDrain, avgScreenOffDrain, events);
        tvDrainAssessment.setText(assessment);
    }
    
    private void updateDrainRateChart(List<DrainEvent> events) {
        if (chartDrainRate == null || events == null || events.isEmpty()) return;
        
        List<Entry> screenOnEntries = new ArrayList<>();
        List<Entry> screenOffEntries = new ArrayList<>();
        
        long baseTime = events.get(events.size() - 1).startTime;
        
        // Configure data marker for drain rate inspection
        OemChartConfig.configureDataMarkerWithContext(
            chartDrainRate,
            requireContext(),
            baseTime,
            60f * 60f * 1000f,  // 1 hour per X unit
            "%",
            "/hr",
            (xValue, yValue) -> {
                // Context based on drain rate
                if (yValue > 2.0f) return "HIGH DRAIN";
                else if (yValue > 1.0f) return "ELEVATED";
                else return "NORMAL";
            }
        );
        
        // Process events (oldest first)
        for (int i = events.size() - 1; i >= 0; i--) {
            DrainEvent e = events.get(i);
            float x = (e.startTime - baseTime) / (60f * 60f * 1000f); // Hours
            
            if (e.screenOn) {
                screenOnEntries.add(new Entry(x, e.drainRatePerHour));
            } else {
                screenOffEntries.add(new Entry(x, e.drainRatePerHour));
            }
        }
        
        List<ILineDataSet> dataSets = new ArrayList<>();
        
        if (!screenOnEntries.isEmpty()) {
            LineDataSet screenOnSet = OemChartConfig.createPrimaryDataSet(screenOnEntries, "Screen ON");
            dataSets.add(screenOnSet);
        }
        
        if (!screenOffEntries.isEmpty()) {
            LineDataSet screenOffSet = OemChartConfig.createSecondaryDataSet(screenOffEntries, "Screen OFF");
            dataSets.add(screenOffSet);
        }
        
        if (!dataSets.isEmpty()) {
            LineData data = new LineData(dataSets);
            chartDrainRate.setData(data);
            chartDrainRate.getXAxis().setValueFormatter(new OemChartConfig.TimeAxisFormatter(baseTime));
            OemChartConfig.moveToLatest(chartDrainRate, Math.max(screenOnEntries.size(), screenOffEntries.size()));
            chartDrainRate.invalidate();
        }
    }
    
    private void updateIdleDrainChart(List<IdleDrainSession> sessions) {
        if (chartIdleDrain == null || sessions == null || sessions.isEmpty()) return;
        
        List<BarEntry> entries = new ArrayList<>();
        
        // Take last 10 sessions
        int count = Math.min(sessions.size(), 10);
        for (int i = 0; i < count; i++) {
            IdleDrainSession s = sessions.get(sessions.size() - count + i);
            entries.add(new BarEntry(i, s.drainPerHour));
        }
        
        BarDataSet dataSet = OemChartConfig.createBarDataSet(entries, "Idle %/hr");
        BarData data = new BarData(dataSet);
        data.setBarWidth(0.7f);
        
        chartIdleDrain.setData(data);
        chartIdleDrain.invalidate();
    }
    
    private String getPrimaryCause(List<DrainEvent> events) {
        if (events == null || events.isEmpty()) return "--";
        
        Map<String, Integer> causes = new HashMap<>();
        for (DrainEvent e : events) {
            if (e.drainCategory != null) {
                causes.merge(e.drainCategory, 1, Integer::sum);
            }
        }
        
        return causes.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("Display");
    }
    
    private String generateDrainAssessment(Float screenOnDrain, Float screenOffDrain, List<DrainEvent> events) {
        StringBuilder sb = new StringBuilder();
        
        if (screenOnDrain == null && screenOffDrain == null) {
            return "Insufficient drain data. Continue monitoring.";
        }
        
        // Screen ON assessment
        if (screenOnDrain != null) {
            if (screenOnDrain <= 15) {
                sb.append("Screen-ON drain: EXCELLENT. ");
            } else if (screenOnDrain <= 20) {
                sb.append("Screen-ON drain: GOOD. ");
            } else if (screenOnDrain <= 30) {
                sb.append("Screen-ON drain: MODERATE. ");
            } else {
                sb.append("Screen-ON drain: HIGH - investigate active apps. ");
            }
        }
        
        // Idle assessment  
        if (screenOffDrain != null) {
            if (screenOffDrain <= 0.5) {
                sb.append("Idle drain: EXCELLENT. ");
            } else if (screenOffDrain <= 1.0) {
                sb.append("Idle drain: GOOD. ");
            } else if (screenOffDrain <= 2.0) {
                sb.append("Idle drain: ELEVATED - check background activity. ");
            } else {
                sb.append("Idle drain: CRITICAL - wakelocks likely active. ");
            }
        }
        
        // Anomaly check
        if (events != null) {
            long anomalies = events.stream().filter(e -> e.isAnomalous).count();
            if (anomalies > 0) {
                sb.append(String.format(Locale.US, "%d anomalous events detected.", anomalies));
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
    }
}
