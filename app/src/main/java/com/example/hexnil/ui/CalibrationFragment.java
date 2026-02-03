package com.example.hexnil.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.hexnil.R;
import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.databinding.FragmentCalibrationBinding;
import com.example.hexnil.model.CalibrationData;
import com.example.hexnil.ui.chart.OemChartConfig;
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
 * CalibrationFragment - SOC Drift Analysis Module
 * 
 * OEM-grade calibration monitoring with:
 * - Reported vs Estimated SOC comparison
 * - Drift trend visualization
 * - Calibration verdict and recommendations
 */
public class CalibrationFragment extends Fragment {

    private FragmentCalibrationBinding binding;
    private HexNilDatabase database;
    private ExecutorService executor;

    // Drift thresholds for calibration assessment
    private static final float DRIFT_THRESHOLD_NORMAL = 5.0f;
    private static final float DRIFT_THRESHOLD_WARNING = 8.0f;
    private static final float DRIFT_THRESHOLD_CRITICAL = 12.0f;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentCalibrationBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        database = HexNilDatabase.getInstance(requireContext());
        executor = Executors.newSingleThreadExecutor();
        
        setupChart();
        loadData();
    }

    private void setupChart() {
        LineChart chart = binding.chartSocDrift;
        OemChartConfig.configureLineChart(chart, requireContext());
        
        // Set Y axis for SOC percentage (0-100%)
        chart.getAxisLeft().setAxisMinimum(0f);
        chart.getAxisLeft().setAxisMaximum(100f);
        chart.getAxisLeft().setGranularity(10f);
        chart.getAxisLeft().setValueFormatter(new OemChartConfig.PercentAxisFormatter());
        
        // Enable horizontal scrolling for time-series navigation
        chart.setDragEnabled(true);
        chart.setScaleXEnabled(true);
        chart.setScaleYEnabled(false);
        chart.setVisibleXRangeMaximum(24f); // Show 24 data points at a time
    }

    private void loadData() {
        executor.execute(() -> {
            try {
                // Get calibration history for last 7 days
                long sevenDaysAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000);
                List<CalibrationData> history = database.miscDao().getCalibrationHistory(sevenDaysAgo);
                CalibrationData latest = database.miscDao().getLatestCalibration();
                int suddenDropCount = database.miscDao().getTotalSuddenDrops(sevenDaysAgo);
                
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> updateUI(history, latest, suddenDropCount));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void updateUI(List<CalibrationData> history, CalibrationData latest, int suddenDrops) {
        if (binding == null || !isAdded()) return;

        if (latest != null) {
            // Update primary metrics
            float drift = Math.abs(latest.levelDrift);
            binding.tvDrift.setText(String.format(Locale.US, "%.1f%%", drift));
            binding.tvDrift.setTextColor(getDriftColor(drift));
            
            float confidence = latest.calibrationQuality * 100;
            binding.tvConfidence.setText(String.format(Locale.US, "%.0f%%", confidence));
            binding.tvConfidence.setTextColor(getConfidenceColor(confidence));
            
            // Calibration verdict
            boolean needsCalibration = latest.needsCalibration;
            binding.tvCalibrationVerdict.setText(needsCalibration ? "YES" : "NO");
            binding.tvCalibrationVerdict.setTextColor(needsCalibration ? 
                ContextCompat.getColor(requireContext(), R.color.oem_status_critical) :
                ContextCompat.getColor(requireContext(), R.color.oem_status_safe));
            
            // Calibration details
            String details = String.format(Locale.US,
                "Drift %%:            %.2f\n" +
                "Confidence:         %.1f%%\n" +
                "Method agreement:   %.1f%%\n" +
                "Quality grade:      %s",
                latest.levelDrift,
                confidence,
                latest.methodAgreement * 100,
                latest.qualityGrade != null ? latest.qualityGrade : "--"
            );
            binding.tvCalibrationDetails.setText(details);
            
            // Key metrics
            binding.tvReportedSoc.setText(String.format(Locale.US, "%d%%", latest.reportedLevel));
            binding.tvEstimatedSoc.setText(String.format(Locale.US, "%.0f%%", latest.estimatedActualLevel));
            
            String direction = latest.driftDirection;
            binding.tvDriftDirection.setText(direction != null ? direction.toUpperCase(Locale.US) : "--");
            binding.tvSuddenDrops.setText(String.valueOf(suddenDrops));
            
            // Color code sudden drops
            if (suddenDrops > 5) {
                binding.tvSuddenDrops.setTextColor(ContextCompat.getColor(requireContext(), R.color.oem_status_critical));
            } else if (suddenDrops > 2) {
                binding.tvSuddenDrops.setTextColor(ContextCompat.getColor(requireContext(), R.color.oem_status_warning));
            } else {
                binding.tvSuddenDrops.setTextColor(ContextCompat.getColor(requireContext(), R.color.oem_text_primary));
            }
            
            // Generate assessment
            binding.tvAssessment.setText(generateAssessment(latest, drift, suddenDrops));
        } else {
            binding.tvDrift.setText("--%");
            binding.tvConfidence.setText("--%");
            binding.tvCalibrationVerdict.setText("--");
            binding.tvCalibrationDetails.setText("Insufficient calibration data.");
            binding.tvReportedSoc.setText("--%");
            binding.tvEstimatedSoc.setText("--%");
            binding.tvDriftDirection.setText("--");
            binding.tvSuddenDrops.setText("--");
            binding.tvAssessment.setText("No calibration data available. Please allow the app to collect battery data over time.");
        }
        
        // Update chart
        updateSocDriftChart(history);
    }

    private void updateSocDriftChart(List<CalibrationData> history) {
        if (history == null || history.isEmpty()) {
            binding.chartSocDrift.setData(null);
            binding.chartSocDrift.invalidate();
            return;
        }

        // Prepare entries for both reported and estimated SOC
        List<Entry> reportedEntries = new ArrayList<>();
        List<Entry> estimatedEntries = new ArrayList<>();
        
        // Process in chronological order (oldest first)
        for (int i = history.size() - 1; i >= 0; i--) {
            CalibrationData data = history.get(i);
            float index = history.size() - 1 - i;
            
            reportedEntries.add(new Entry(index, data.reportedLevel));
            estimatedEntries.add(new Entry(index, data.estimatedActualLevel));
        }

        // Create dataset for reported SOC (primary color)
        LineDataSet reportedDataSet = new LineDataSet(reportedEntries, "REPORTED SOC");
        configureReportedDataSet(reportedDataSet);
        
        // Create dataset for estimated SOC (accent color)
        LineDataSet estimatedDataSet = new LineDataSet(estimatedEntries, "ESTIMATED SOC");
        configureEstimatedDataSet(estimatedDataSet);

        LineData lineData = new LineData(reportedDataSet, estimatedDataSet);
        lineData.setDrawValues(false);
        
        binding.chartSocDrift.setData(lineData);
        
        // Move to latest data point
        if (history.size() > 24) {
            binding.chartSocDrift.moveViewToX(history.size() - 24);
        }
        
        binding.chartSocDrift.invalidate();
    }

    private void configureReportedDataSet(LineDataSet dataSet) {
        int primaryColor = ContextCompat.getColor(requireContext(), R.color.oem_chart_line_primary);
        
        dataSet.setColor(primaryColor);
        dataSet.setLineWidth(2f);
        dataSet.setDrawCircles(true);
        dataSet.setCircleColor(primaryColor);
        dataSet.setCircleRadius(3f);
        dataSet.setDrawCircleHole(false);
        dataSet.setMode(LineDataSet.Mode.LINEAR);
        dataSet.setDrawFilled(false);
        dataSet.setHighLightColor(Color.WHITE);
    }

    private void configureEstimatedDataSet(LineDataSet dataSet) {
        int accentColor = ContextCompat.getColor(requireContext(), R.color.oem_chart_accent);
        
        dataSet.setColor(accentColor);
        dataSet.setLineWidth(2f);
        dataSet.setDrawCircles(true);
        dataSet.setCircleColor(accentColor);
        dataSet.setCircleRadius(3f);
        dataSet.setDrawCircleHole(false);
        dataSet.setMode(LineDataSet.Mode.LINEAR);
        dataSet.setDrawFilled(false);
        dataSet.enableDashedLine(10f, 5f, 0f);
        dataSet.setHighLightColor(Color.WHITE);
    }

    private int getDriftColor(float drift) {
        if (drift >= DRIFT_THRESHOLD_CRITICAL) {
            return ContextCompat.getColor(requireContext(), R.color.oem_status_critical);
        } else if (drift >= DRIFT_THRESHOLD_WARNING) {
            return ContextCompat.getColor(requireContext(), R.color.oem_status_warning);
        } else if (drift >= DRIFT_THRESHOLD_NORMAL) {
            return ContextCompat.getColor(requireContext(), R.color.oem_status_warning);
        }
        return ContextCompat.getColor(requireContext(), R.color.oem_status_safe);
    }

    private int getConfidenceColor(float confidence) {
        if (confidence >= 85) {
            return ContextCompat.getColor(requireContext(), R.color.oem_status_safe);
        } else if (confidence >= 70) {
            return ContextCompat.getColor(requireContext(), R.color.oem_status_warning);
        }
        return ContextCompat.getColor(requireContext(), R.color.oem_status_critical);
    }

    private String generateAssessment(CalibrationData latest, float drift, int suddenDrops) {
        StringBuilder assessment = new StringBuilder();
        
        // Drift assessment
        if (drift >= DRIFT_THRESHOLD_CRITICAL) {
            assessment.append("CRITICAL: SOC drift exceeds 12%. Battery gauge inaccurate. ");
            assessment.append("Full discharge-charge cycle recommended. ");
        } else if (drift >= DRIFT_THRESHOLD_WARNING) {
            assessment.append("WARNING: Elevated SOC drift detected (>8%). ");
            assessment.append("Battery gauge accuracy degraded. ");
        } else if (drift >= DRIFT_THRESHOLD_NORMAL) {
            assessment.append("MARGINAL: SOC drift within acceptable limits but trending up. ");
            assessment.append("Monitor for further changes. ");
        } else {
            assessment.append("NOMINAL: SOC drift within normal operating range. ");
            assessment.append("Battery gauge calibration satisfactory. ");
        }
        
        // Sudden drop assessment
        if (suddenDrops > 5) {
            assessment.append("ATTENTION: Multiple sudden battery drops detected (").append(suddenDrops).append("). ");
            assessment.append("Potential cell degradation or calibration error. ");
        } else if (suddenDrops > 2) {
            assessment.append("NOTE: Some sudden battery drops recorded. ");
        }
        
        // Confidence assessment
        float confidence = latest.calibrationQuality * 100;
        if (confidence < 70) {
            assessment.append("Low confidence in calibration data. More readings needed.");
        }
        
        // Method agreement
        float agreement = latest.methodAgreement * 100;
        if (agreement < 80) {
            assessment.append(" SOC estimation methods showing disagreement - readings may be unreliable.");
        }
        
        return assessment.toString().trim();
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
