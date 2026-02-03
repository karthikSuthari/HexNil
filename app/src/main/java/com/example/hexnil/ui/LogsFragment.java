package com.example.hexnil.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.hexnil.R;
import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.databinding.FragmentLogsBinding;
import com.example.hexnil.model.AnomalyRecord;
import com.example.hexnil.model.RegressionEvent;
import com.example.hexnil.model.SafetyEvent;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * LogsFragment - Event Log Viewer Module
 * 
 * OEM-grade event logging with:
 * - Tabbed categories (All, Anomalies, Safety, Regressions)
 * - Expandable event details
 * - Severity-coded indicators
 */
public class LogsFragment extends Fragment {

    private FragmentLogsBinding binding;
    private HexNilDatabase database;
    private ExecutorService executor;
    private LogEventAdapter adapter;
    
    // Cached data
    private List<AnomalyRecord> anomalies = new ArrayList<>();
    private List<SafetyEvent> safetyEvents = new ArrayList<>();
    private List<RegressionEvent> regressions = new ArrayList<>();
    
    private static final int TAB_ALL = 0;
    private static final int TAB_ANOMALIES = 1;
    private static final int TAB_SAFETY = 2;
    private static final int TAB_REGRESSIONS = 3;
    
    private int currentTab = TAB_ALL;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentLogsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        database = HexNilDatabase.getInstance(requireContext());
        executor = Executors.newSingleThreadExecutor();
        
        setupTabs();
        setupRecyclerView();
        loadData();
    }

    private void setupTabs() {
        TabLayout tabLayout = binding.tabLayout;
        
        tabLayout.addTab(tabLayout.newTab().setText("ALL"));
        tabLayout.addTab(tabLayout.newTab().setText("ANOMALIES"));
        tabLayout.addTab(tabLayout.newTab().setText("SAFETY"));
        tabLayout.addTab(tabLayout.newTab().setText("REGRESSIONS"));
        
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentTab = tab.getPosition();
                updateDisplayedEvents();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void setupRecyclerView() {
        adapter = new LogEventAdapter(requireContext());
        binding.recyclerLogs.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerLogs.setAdapter(adapter);
    }

    private void loadData() {
        executor.execute(() -> {
            try {
                // Load recent events from each category
                anomalies = database.anomalyRecordDao().getRecentAnomalies(100);
                safetyEvents = database.safetyEventDao().getRecentEvents(100);
                regressions = database.regressionEventDao().getRecent(100);
                
                if (getActivity() != null) {
                    getActivity().runOnUiThread(this::updateUI);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void updateUI() {
        if (binding == null || !isAdded()) return;

        // Update summary counts
        binding.tvAnomalyCount.setText(String.valueOf(anomalies.size()));
        binding.tvSafetyCount.setText(String.valueOf(safetyEvents.size()));
        binding.tvRegressionCount.setText(String.valueOf(regressions.size()));
        
        // Color code based on counts
        if (safetyEvents.size() > 0) {
            binding.tvSafetyCount.setTextColor(ContextCompat.getColor(requireContext(), R.color.oem_status_critical));
        } else {
            binding.tvSafetyCount.setTextColor(ContextCompat.getColor(requireContext(), R.color.oem_status_safe));
        }
        
        updateDisplayedEvents();
    }

    private void updateDisplayedEvents() {
        if (binding == null || !isAdded()) return;

        List<LogEventItem> items = new ArrayList<>();
        
        switch (currentTab) {
            case TAB_ALL:
                items.addAll(convertAnomalies(anomalies));
                items.addAll(convertSafetyEvents(safetyEvents));
                items.addAll(convertRegressions(regressions));
                // Sort by timestamp descending
                items.sort((a, b) -> Long.compare(b.timestamp, a.timestamp));
                break;
            case TAB_ANOMALIES:
                items.addAll(convertAnomalies(anomalies));
                break;
            case TAB_SAFETY:
                items.addAll(convertSafetyEvents(safetyEvents));
                break;
            case TAB_REGRESSIONS:
                items.addAll(convertRegressions(regressions));
                break;
        }
        
        adapter.setItems(items);
        
        // Show/hide empty state
        if (items.isEmpty()) {
            binding.recyclerLogs.setVisibility(View.GONE);
            binding.emptyState.setVisibility(View.VISIBLE);
        } else {
            binding.recyclerLogs.setVisibility(View.VISIBLE);
            binding.emptyState.setVisibility(View.GONE);
        }
    }

    private List<LogEventItem> convertAnomalies(List<AnomalyRecord> records) {
        List<LogEventItem> items = new ArrayList<>();
        for (AnomalyRecord record : records) {
            LogEventItem item = new LogEventItem();
            item.id = record.id;
            item.timestamp = record.detectedAt;
            item.eventType = record.anomalyType != null ? record.anomalyType.toUpperCase(Locale.US) : "ANOMALY";
            item.title = record.title != null ? record.title : "Anomaly Detected";
            item.description = record.description;
            item.severity = mapSeverity(record.severity);
            item.category = "ANOMALY";
            
            // Build details
            StringBuilder details = new StringBuilder();
            if (record.metricName != null) {
                details.append(String.format(Locale.US, "Metric:      %s\n", record.metricName));
            }
            if (record.expectedValue != 0 || record.actualValue != 0) {
                details.append(String.format(Locale.US, "Expected:    %.2f %s\n", record.expectedValue, 
                    record.metricUnit != null ? record.metricUnit : ""));
                details.append(String.format(Locale.US, "Actual:      %.2f %s\n", record.actualValue,
                    record.metricUnit != null ? record.metricUnit : ""));
                details.append(String.format(Locale.US, "Deviation:   %.1f%%\n", record.deviationPercent));
            }
            if (record.sourceAppName != null) {
                details.append(String.format("App:         %s\n", record.sourceAppName));
            }
            if (record.rootCause != null) {
                details.append(String.format("Root cause:  %s\n", record.rootCause));
            }
            if (record.recommendation != null) {
                details.append(String.format("Action:      %s", record.recommendation));
            }
            item.details = details.toString().trim();
            
            items.add(item);
        }
        return items;
    }

    private List<LogEventItem> convertSafetyEvents(List<SafetyEvent> events) {
        List<LogEventItem> items = new ArrayList<>();
        for (SafetyEvent event : events) {
            LogEventItem item = new LogEventItem();
            item.id = event.id;
            item.timestamp = event.timestamp;
            item.eventType = event.eventType != null ? event.eventType.toUpperCase(Locale.US) : "SAFETY";
            item.title = getSafetyEventTitle(event.eventType);
            item.description = getSafetyEventDescription(event);
            item.severity = mapSafetySeverity(event.severity);
            item.category = "SAFETY";
            
            // Build details
            StringBuilder details = new StringBuilder();
            details.append(String.format(Locale.US, "Trigger:     %.2f %s\n", event.triggerValue, 
                event.measurementUnit != null ? event.measurementUnit : ""));
            details.append(String.format(Locale.US, "Threshold:   %.2f %s\n", event.thresholdValue,
                event.measurementUnit != null ? event.measurementUnit : ""));
            details.append(String.format(Locale.US, "Battery:     %d%%\n", event.batteryLevel));
            details.append(String.format("Charging:    %s\n", event.wasCharging ? "YES" : "NO"));
            if (event.wasProtectionTriggered) {
                details.append(String.format("Protection:  %s\n", event.protectionAction != null ? event.protectionAction : "TRIGGERED"));
            }
            if (event.exceedsRegulatory) {
                details.append(String.format("Regulatory:  EXCEEDS %s", 
                    event.regulatoryStandard != null ? event.regulatoryStandard : "LIMIT"));
            }
            item.details = details.toString().trim();
            
            items.add(item);
        }
        return items;
    }

    private List<LogEventItem> convertRegressions(List<RegressionEvent> events) {
        List<LogEventItem> items = new ArrayList<>();
        for (RegressionEvent event : events) {
            LogEventItem item = new LogEventItem();
            item.id = event.id;
            item.timestamp = event.detectedAt;
            item.eventType = event.regressionType != null ? event.regressionType.toUpperCase(Locale.US) : "REGRESSION";
            item.title = getRegressionTitle(event);
            item.description = event.appLabel != null ? 
                "Regression detected in " + event.appLabel : "System regression detected";
            item.severity = mapRegressionSeverity(event.severity);
            item.category = "REGRESSION";
            
            // Build details
            StringBuilder details = new StringBuilder();
            if (event.appLabel != null) {
                details.append(String.format("App:         %s\n", event.appLabel));
            }
            details.append(String.format(Locale.US, "Baseline:    %.2f\n", event.baselineValue));
            details.append(String.format(Locale.US, "Current:     %.2f\n", event.currentValue));
            details.append(String.format(Locale.US, "Change:      %.1f%%\n", event.deltaPercent));
            details.append(String.format("Status:      %s", event.isResolved ? "RESOLVED" : "ACTIVE"));
            item.details = details.toString().trim();
            
            items.add(item);
        }
        return items;
    }

    private int mapSeverity(String severity) {
        if (severity == null) return LogEventItem.SEVERITY_INFO;
        switch (severity.toLowerCase(Locale.US)) {
            case "critical": return LogEventItem.SEVERITY_CRITICAL;
            case "high": return LogEventItem.SEVERITY_WARNING;
            case "medium": return LogEventItem.SEVERITY_WARNING;
            case "low": return LogEventItem.SEVERITY_SAFE;
            default: return LogEventItem.SEVERITY_INFO;
        }
    }

    private int mapSafetySeverity(String severity) {
        if (severity == null) return LogEventItem.SEVERITY_WARNING;
        switch (severity.toLowerCase(Locale.US)) {
            case "emergency": return LogEventItem.SEVERITY_CRITICAL;
            case "critical": return LogEventItem.SEVERITY_CRITICAL;
            case "warning": return LogEventItem.SEVERITY_WARNING;
            default: return LogEventItem.SEVERITY_WARNING;
        }
    }

    private int mapRegressionSeverity(String severity) {
        if (severity == null) return LogEventItem.SEVERITY_WARNING;
        switch (severity.toLowerCase(Locale.US)) {
            case "critical":
            case "high":
                return LogEventItem.SEVERITY_CRITICAL;
            case "medium":
                return LogEventItem.SEVERITY_WARNING;
            case "low":
                return LogEventItem.SEVERITY_SAFE;
            default:
                return LogEventItem.SEVERITY_WARNING;
        }
    }

    private String getSafetyEventTitle(String eventType) {
        if (eventType == null) return "Safety Event";
        switch (eventType.toLowerCase(Locale.US)) {
            case "over_temp": return "Over Temperature";
            case "over_voltage": return "Over Voltage";
            case "over_current": return "Over Current";
            case "over_discharge": return "Over Discharge";
            case "swelling_risk": return "Battery Analysis";
            case "abuse": return "Usage Alert";
            default: return "Safety Event";
        }
    }

    private String getSafetyEventDescription(SafetyEvent event) {
        if (event.eventType == null) return "Safety event recorded.";
        switch (event.eventType.toLowerCase(Locale.US)) {
            case "over_temp":
                return String.format(Locale.US, "Battery temperature reached %.1f°C", event.batteryTemperature);
            case "over_voltage":
                return String.format(Locale.US, "Voltage exceeded threshold: %.2fV", event.voltage);
            case "over_current":
                return String.format(Locale.US, "Current exceeded threshold: %.0fmA", event.current);
            case "over_discharge":
                return String.format(Locale.US, "Voltage dropped to %.2fV", event.lowestVoltage);
            case "swelling_risk":
                // Use measured, evidence-based language - never alarming without proof
                if (event.swellingRiskScore >= 0.75f) {
                    return String.format(Locale.US, 
                        "Multiple anomaly indicators detected (confidence: %.0f%%). Schedule inspection.",
                        event.swellingRiskScore * 100);
                } else if (event.swellingRiskScore >= 0.4f) {
                    return String.format(Locale.US,
                        "Battery metrics inconclusive (confidence: %.0f%%). Continue monitoring.",
                        event.swellingRiskScore * 100);
                } else {
                    return "Battery health within normal operating range. No abnormal indicators.";
                }
            case "abuse":
                return event.abuseType != null ? "Abuse detected: " + event.abuseType : "Battery abuse detected";
            default:
                return "Safety event recorded.";
        }
    }

    private String getRegressionTitle(RegressionEvent event) {
        if (event.regressionType == null) return "Performance Regression";
        switch (event.regressionType.toLowerCase(Locale.US)) {
            case "launch_time": return "Launch Time Regression";
            case "jank": return "Frame Jank Regression";
            case "memory": return "Memory Regression";
            case "crash": return "Crash Regression";
            case "anr": return "ANR Regression";
            case "thermal": return "Thermal Regression";
            default: return "Performance Regression";
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
        binding = null;
    }
}