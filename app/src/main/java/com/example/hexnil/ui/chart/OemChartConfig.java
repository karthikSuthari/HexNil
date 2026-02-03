package com.example.hexnil.ui.chart;

import android.content.Context;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Typeface;

import androidx.core.content.ContextCompat;

import com.example.hexnil.R;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * OEM-Grade Chart Configuration Utility
 * 
 * Industrial diagnostic styling for MPAndroidChart:
 * - Dark theme colors
 * - Horizontal sliding enabled
 * - No animations (except scroll)
 * - Minimal grid clutter
 * - Monospace typography for values
 * - Status color indicators
 * 
 * DESIGN PRINCIPLES:
 * - Pinch zoom disabled (OEM feel)
 * - No decorative animations
 * - Current value always visible
 * - Time window label present
 */
public class OemChartConfig {
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // OEM DARK THEME COLORS
    // ═══════════════════════════════════════════════════════════════════════════════
    
    public static final int COLOR_BG_PRIMARY = Color.parseColor("#0F1115");
    public static final int COLOR_BG_CARD = Color.parseColor("#161A20");
    public static final int COLOR_TEXT_PRIMARY = Color.parseColor("#E6E8EB");
    public static final int COLOR_TEXT_SECONDARY = Color.parseColor("#9AA4B2");
    public static final int COLOR_TEXT_TERTIARY = Color.parseColor("#6E7681");
    public static final int COLOR_DIVIDER = Color.parseColor("#232833");
    public static final int COLOR_GRID = Color.parseColor("#21262D");
    
    // Accent color (cyan/blue)
    public static final int COLOR_ACCENT = Color.parseColor("#58A6FF");
    public static final int COLOR_ACCENT_DIM = Color.parseColor("#1F6FEB");
    
    // Status colors (muted for OEM)
    public static final int COLOR_SAFE = Color.parseColor("#238636");
    public static final int COLOR_WARNING = Color.parseColor("#D29922");
    public static final int COLOR_CRITICAL = Color.parseColor("#DA3633");
    public static final int COLOR_INFO = Color.parseColor("#58A6FF");
    
    // Data line colors
    public static final int COLOR_LINE_PRIMARY = Color.parseColor("#58A6FF");
    public static final int COLOR_LINE_SECONDARY = Color.parseColor("#8B949E");
    public static final int COLOR_LINE_TERTIARY = Color.parseColor("#7EE787");
    public static final int COLOR_LINE_CHARGING = Color.parseColor("#3FB950");
    public static final int COLOR_LINE_TEMPERATURE = Color.parseColor("#F78166");
    
    // Fill colors (semi-transparent)
    public static final int COLOR_FILL_PRIMARY = Color.parseColor("#1A58A6FF");
    public static final int COLOR_FILL_SECONDARY = Color.parseColor("#1A8B949E");
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // LINE CHART CONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Configure a LineChart with OEM-grade styling.
     * Enables horizontal scrolling, disables pinch zoom.
     */
    public static void configureLineChart(LineChart chart, Context context) {
        // Background and description
        chart.setBackgroundColor(COLOR_BG_CARD);
        chart.getDescription().setEnabled(false);
        chart.setDrawGridBackground(false);
        chart.setDrawBorders(false);
        
        // Disable animations (OEM feel)
        chart.setDragDecelerationEnabled(false);
        chart.animateX(0);
        
        // Enable horizontal drag (sliding), disable pinch zoom
        chart.setDragEnabled(true);
        chart.setScaleXEnabled(true);
        chart.setScaleYEnabled(false);
        chart.setPinchZoom(false);
        chart.setDoubleTapToZoomEnabled(false);
        
        // Touch feedback
        chart.setHighlightPerDragEnabled(true);
        chart.setHighlightPerTapEnabled(true);
        
        // Extra padding for readability
        chart.setExtraOffsets(8f, 16f, 8f, 8f);
        chart.setMinOffset(0f);
        
        // Configure X-axis (bottom)
        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(COLOR_TEXT_SECONDARY);
        xAxis.setTextSize(10f);
        xAxis.setDrawGridLines(false);
        xAxis.setDrawAxisLine(true);
        xAxis.setAxisLineColor(COLOR_DIVIDER);
        xAxis.setAxisLineWidth(1f);
        xAxis.setGranularity(1f);
        xAxis.setLabelCount(5, false);
        
        // Configure left Y-axis
        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setTextColor(COLOR_TEXT_SECONDARY);
        leftAxis.setTextSize(10f);
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(COLOR_GRID);
        leftAxis.setGridLineWidth(0.5f);
        leftAxis.setDrawAxisLine(false);
        leftAxis.setDrawZeroLine(false);
        leftAxis.setLabelCount(5, false);
        leftAxis.setSpaceTop(15f);
        leftAxis.setSpaceBottom(15f);
        
        // Disable right Y-axis
        chart.getAxisRight().setEnabled(false);
        
        // Legend configuration
        Legend legend = chart.getLegend();
        legend.setEnabled(true);
        legend.setTextColor(COLOR_TEXT_SECONDARY);
        legend.setTextSize(10f);
        legend.setForm(Legend.LegendForm.LINE);
        legend.setFormSize(12f);
        legend.setFormLineWidth(2f);
        legend.setXEntrySpace(12f);
        legend.setYEntrySpace(0f);
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.TOP);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.LEFT);
        legend.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        legend.setDrawInside(true);
        
        // No data text
        chart.setNoDataText("NO DATA AVAILABLE");
        chart.setNoDataTextColor(COLOR_TEXT_TERTIARY);
    }
    
    /**
     * Configure chart for time-series data with horizontal sliding.
     * Sets visible range for sliding effect.
     */
    public static void configureTimeSeriesChart(LineChart chart, int visibleXRange) {
        chart.setVisibleXRangeMaximum(visibleXRange);
        chart.setVisibleXRangeMinimum(Math.min(visibleXRange / 2f, 10f));
    }
    
    /**
     * Move chart view to show most recent data (right side).
     */
    public static void moveToLatest(LineChart chart, int dataCount) {
        if (dataCount > 0) {
            chart.moveViewToX(dataCount - 1);
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // DATA SET CONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Create a primary line data set with OEM styling.
     */
    public static LineDataSet createPrimaryDataSet(List<Entry> entries, String label) {
        LineDataSet dataSet = new LineDataSet(entries, label);
        
        // Line styling
        dataSet.setColor(COLOR_LINE_PRIMARY);
        dataSet.setLineWidth(2f);
        dataSet.setDrawCircles(false);
        dataSet.setDrawCircleHole(false);
        
        // Fill
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(COLOR_LINE_PRIMARY);
        dataSet.setFillAlpha(25);
        
        // Values
        dataSet.setDrawValues(false);
        
        // Highlight
        dataSet.setHighLightColor(COLOR_ACCENT);
        dataSet.setHighlightLineWidth(1f);
        dataSet.setDrawHorizontalHighlightIndicator(false);
        
        // Mode
        dataSet.setMode(LineDataSet.Mode.LINEAR);
        
        return dataSet;
    }
    
    /**
     * Create a secondary line data set for comparison/overlay.
     */
    public static LineDataSet createSecondaryDataSet(List<Entry> entries, String label) {
        LineDataSet dataSet = new LineDataSet(entries, label);
        
        dataSet.setColor(COLOR_LINE_SECONDARY);
        dataSet.setLineWidth(1.5f);
        dataSet.setDrawCircles(false);
        dataSet.setDrawFilled(false);
        dataSet.setDrawValues(false);
        dataSet.setHighLightColor(COLOR_TEXT_SECONDARY);
        dataSet.setDrawHorizontalHighlightIndicator(false);
        dataSet.setMode(LineDataSet.Mode.LINEAR);
        
        return dataSet;
    }
    
    /**
     * Create a charging data set with dashed line for charging periods.
     */
    public static LineDataSet createChargingDataSet(List<Entry> entries, String label) {
        LineDataSet dataSet = new LineDataSet(entries, label);
        
        dataSet.setColor(COLOR_LINE_CHARGING);
        dataSet.setLineWidth(2f);
        dataSet.setDrawCircles(false);
        dataSet.setDrawFilled(false);
        dataSet.setDrawValues(false);
        dataSet.enableDashedLine(10f, 5f, 0f);
        dataSet.setFormLineDashEffect(new DashPathEffect(new float[]{10f, 5f}, 0f));
        dataSet.setDrawHorizontalHighlightIndicator(false);
        dataSet.setMode(LineDataSet.Mode.LINEAR);
        
        return dataSet;
    }
    
    /**
     * Create a temperature data set with color-coded line.
     */
    public static LineDataSet createTemperatureDataSet(List<Entry> entries, String label) {
        LineDataSet dataSet = new LineDataSet(entries, label);
        
        dataSet.setColor(COLOR_LINE_TEMPERATURE);
        dataSet.setLineWidth(2f);
        dataSet.setDrawCircles(false);
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(COLOR_LINE_TEMPERATURE);
        dataSet.setFillAlpha(15);
        dataSet.setDrawValues(false);
        dataSet.setHighLightColor(COLOR_LINE_TEMPERATURE);
        dataSet.setDrawHorizontalHighlightIndicator(false);
        dataSet.setMode(LineDataSet.Mode.LINEAR);
        
        return dataSet;
    }
    
    /**
     * Create a data set styled for safe (green) status.
     */
    public static LineDataSet createSafeDataSet(List<Entry> entries, String label) {
        LineDataSet dataSet = new LineDataSet(entries, label);
        
        dataSet.setColor(COLOR_SAFE);
        dataSet.setLineWidth(2f);
        dataSet.setDrawCircles(false);
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(COLOR_SAFE);
        dataSet.setFillAlpha(20);
        dataSet.setDrawValues(false);
        dataSet.setMode(LineDataSet.Mode.LINEAR);
        
        return dataSet;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // THRESHOLD LINES
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Add a warning threshold line (horizontal).
     */
    public static void addWarningThreshold(YAxis axis, float value, String label) {
        LimitLine line = new LimitLine(value, label);
        line.setLineColor(COLOR_WARNING);
        line.setLineWidth(1f);
        line.enableDashedLine(10f, 5f, 0f);
        line.setLabelPosition(LimitLine.LimitLabelPosition.RIGHT_TOP);
        line.setTextColor(COLOR_WARNING);
        line.setTextSize(9f);
        axis.addLimitLine(line);
    }
    
    /**
     * Add a critical threshold line (horizontal).
     */
    public static void addCriticalThreshold(YAxis axis, float value, String label) {
        LimitLine line = new LimitLine(value, label);
        line.setLineColor(COLOR_CRITICAL);
        line.setLineWidth(1.5f);
        line.enableDashedLine(8f, 4f, 0f);
        line.setLabelPosition(LimitLine.LimitLabelPosition.RIGHT_TOP);
        line.setTextColor(COLOR_CRITICAL);
        line.setTextSize(9f);
        axis.addLimitLine(line);
    }
    
    /**
     * Add a safe threshold line (horizontal).
     */
    public static void addSafeThreshold(YAxis axis, float value, String label) {
        LimitLine line = new LimitLine(value, label);
        line.setLineColor(COLOR_SAFE);
        line.setLineWidth(1f);
        line.enableDashedLine(10f, 5f, 0f);
        line.setLabelPosition(LimitLine.LimitLabelPosition.RIGHT_BOTTOM);
        line.setTextColor(COLOR_SAFE);
        line.setTextSize(9f);
        axis.addLimitLine(line);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // AXIS FORMATTERS
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Time formatter for X-axis showing hours (HH:mm).
     */
    public static class TimeAxisFormatter extends ValueFormatter {
        private final SimpleDateFormat format;
        private final long baseTimestamp;
        
        public TimeAxisFormatter(long baseTimestamp) {
            this.format = new SimpleDateFormat("HH:mm", Locale.getDefault());
            this.baseTimestamp = baseTimestamp;
        }
        
        @Override
        public String getAxisLabel(float value, AxisBase axis) {
            long timestamp = baseTimestamp + (long)(value * 60 * 1000);
            return format.format(new Date(timestamp));
        }
    }
    
    /**
     * Time formatter for X-axis showing date (MM/dd HH:mm).
     */
    public static class DateTimeAxisFormatter extends ValueFormatter {
        private final SimpleDateFormat format;
        private final long baseTimestamp;
        
        public DateTimeAxisFormatter(long baseTimestamp) {
            this.format = new SimpleDateFormat("MM/dd HH:mm", Locale.getDefault());
            this.baseTimestamp = baseTimestamp;
        }
        
        @Override
        public String getAxisLabel(float value, AxisBase axis) {
            long timestamp = baseTimestamp + (long)(value * 60 * 1000);
            return format.format(new Date(timestamp));
        }
    }
    
    /**
     * Percentage formatter for Y-axis.
     */
    public static class PercentAxisFormatter extends ValueFormatter {
        @Override
        public String getAxisLabel(float value, AxisBase axis) {
            return String.format(Locale.getDefault(), "%.0f%%", value);
        }
    }
    
    /**
     * Temperature formatter for Y-axis (°C).
     */
    public static class TemperatureAxisFormatter extends ValueFormatter {
        @Override
        public String getAxisLabel(float value, AxisBase axis) {
            return String.format(Locale.getDefault(), "%.0f°C", value);
        }
    }
    
    /**
     * mAh formatter for Y-axis.
     */
    public static class CapacityAxisFormatter extends ValueFormatter {
        @Override
        public String getAxisLabel(float value, AxisBase axis) {
            return String.format(Locale.getDefault(), "%.0f", value);
        }
    }
    
    /**
     * Rate formatter for Y-axis (%/hr).
     */
    public static class DrainRateAxisFormatter extends ValueFormatter {
        @Override
        public String getAxisLabel(float value, AxisBase axis) {
            return String.format(Locale.getDefault(), "%.1f", value);
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // BAR CHART CONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Configure a BarChart with OEM-grade styling.
     */
    public static void configureBarChart(BarChart chart) {
        chart.setBackgroundColor(COLOR_BG_CARD);
        chart.getDescription().setEnabled(false);
        chart.setDrawGridBackground(false);
        chart.setDrawBorders(false);
        chart.setDrawBarShadow(false);
        chart.setFitBars(true);
        
        // Disable pinch zoom, enable drag
        chart.setDragEnabled(true);
        chart.setScaleXEnabled(true);
        chart.setScaleYEnabled(false);
        chart.setPinchZoom(false);
        chart.setDoubleTapToZoomEnabled(false);
        
        // X-axis
        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(COLOR_TEXT_SECONDARY);
        xAxis.setTextSize(10f);
        xAxis.setDrawGridLines(false);
        xAxis.setAxisLineColor(COLOR_DIVIDER);
        xAxis.setGranularity(1f);
        
        // Y-axis
        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setTextColor(COLOR_TEXT_SECONDARY);
        leftAxis.setTextSize(10f);
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(COLOR_GRID);
        leftAxis.setGridLineWidth(0.5f);
        leftAxis.setDrawAxisLine(false);
        leftAxis.setAxisMinimum(0f);
        
        chart.getAxisRight().setEnabled(false);
        
        // Legend
        Legend legend = chart.getLegend();
        legend.setEnabled(true);
        legend.setTextColor(COLOR_TEXT_SECONDARY);
        legend.setTextSize(10f);
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.TOP);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.LEFT);
        legend.setDrawInside(true);
        
        chart.setNoDataText("NO DATA AVAILABLE");
        chart.setNoDataTextColor(COLOR_TEXT_TERTIARY);
    }
    
    /**
     * Create a bar data set with OEM styling.
     */
    public static BarDataSet createBarDataSet(List<com.github.mikephil.charting.data.BarEntry> entries, String label) {
        BarDataSet dataSet = new BarDataSet(entries, label);
        dataSet.setColor(COLOR_LINE_PRIMARY);
        dataSet.setDrawValues(false);
        dataSet.setHighLightColor(COLOR_ACCENT);
        dataSet.setHighLightAlpha(100);
        return dataSet;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // UTILITY METHODS
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Get status color based on value and thresholds.
     */
    public static int getStatusColor(float value, float warningThreshold, float criticalThreshold, boolean higherIsBetter) {
        if (higherIsBetter) {
            if (value >= warningThreshold) return COLOR_SAFE;
            if (value >= criticalThreshold) return COLOR_WARNING;
            return COLOR_CRITICAL;
        } else {
            if (value <= warningThreshold) return COLOR_SAFE;
            if (value <= criticalThreshold) return COLOR_WARNING;
            return COLOR_CRITICAL;
        }
    }
    
    /**
     * Get health color based on battery health percentage.
     */
    public static int getHealthColor(float healthPercent) {
        if (healthPercent >= 80) return COLOR_SAFE;
        if (healthPercent >= 50) return COLOR_WARNING;
        return COLOR_CRITICAL;
    }
    
    /**
     * Get temperature color based on Celsius value.
     */
    public static int getTemperatureColor(float tempCelsius) {
        if (tempCelsius <= 35) return COLOR_SAFE;
        if (tempCelsius <= 42) return COLOR_WARNING;
        return COLOR_CRITICAL;
    }
    
    /**
     * Get drain rate color based on %/hr value.
     */
    public static int getDrainRateColor(float drainPerHour, boolean screenOn) {
        if (screenOn) {
            if (drainPerHour <= 15) return COLOR_SAFE;
            if (drainPerHour <= 25) return COLOR_WARNING;
            return COLOR_CRITICAL;
        } else {
            if (drainPerHour <= 1.0) return COLOR_SAFE;
            if (drainPerHour <= 2.5) return COLOR_WARNING;
            return COLOR_CRITICAL;
        }
    }
    
    /**
     * Refresh chart data and invalidate view.
     */
    public static void refreshChart(LineChart chart, LineData data) {
        chart.setData(data);
        chart.invalidate();
    }
    
    /**
     * Clear chart and show no data message.
     */
    public static void clearChart(LineChart chart) {
        chart.clear();
        chart.invalidate();
    }
}
