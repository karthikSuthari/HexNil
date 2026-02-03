package com.example.hexnil.ui.chart;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.widget.TextView;

import com.example.hexnil.R;
import com.github.mikephil.charting.components.MarkerView;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.utils.MPPointF;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * OEM-Grade Data Marker View for Chart Interaction
 * 
 * DESIGN PRINCIPLES (Per OEM UI Spec):
 * - Compact dark card style
 * - Monospace font for values
 * - No animations or tooltip arrows
 * - Shows timestamp, value, and context
 * - Values from RoomDB only (no interpolation)
 * 
 * DISPLAY FORMAT:
 * ┌─────────────────────┐
 * │ 02/03 14:35         │
 * │ 85.0%               │
 * │ DISCHARGING         │
 * └─────────────────────┘
 */
public class OemDataMarkerView extends MarkerView {

    // UI Colors (matching OemChartConfig)
    private static final int BG_COLOR = Color.parseColor("#1A1D24");
    private static final int TEXT_PRIMARY = Color.parseColor("#E6E8EB");
    private static final int TEXT_SECONDARY = Color.parseColor("#9AA4B2");
    
    private final TextView tvTimestamp;
    private final TextView tvValue;
    private final TextView tvContext;
    
    private final SimpleDateFormat dateFormat;
    private long baseTimestamp = 0;
    private float timeScaleFactor = 60 * 1000; // Default: 1 unit = 1 minute
    
    private String valueUnit = "%";
    private String valueSuffix = "";
    private ContextResolver contextResolver;
    
    /**
     * Interface for resolving context string from data point
     */
    public interface ContextResolver {
        /**
         * Get context string for a data point
         * @param xValue X-axis value (typically time index)
         * @param yValue Y-axis value (the metric)
         * @return Context string (e.g., "CHARGING", "SCREEN ON", etc.)
         */
        String getContext(float xValue, float yValue);
    }
    
    public OemDataMarkerView(Context context) {
        super(context, R.layout.marker_oem_data);
        
        tvTimestamp = findViewById(R.id.marker_timestamp);
        tvValue = findViewById(R.id.marker_value);
        tvContext = findViewById(R.id.marker_context);
        
        dateFormat = new SimpleDateFormat("MM/dd HH:mm", Locale.getDefault());
        
        // Apply monospace font
        Typeface mono = Typeface.MONOSPACE;
        if (tvTimestamp != null) tvTimestamp.setTypeface(mono);
        if (tvValue != null) tvValue.setTypeface(mono, Typeface.BOLD);
        if (tvContext != null) tvContext.setTypeface(mono);
    }
    
    /**
     * Set base timestamp for X-axis time conversion
     * @param baseTimestamp Epoch milliseconds for x=0
     * @param timeScaleFactor Milliseconds per X unit (default: 60000 for minutes)
     */
    public void setTimeBase(long baseTimestamp, float timeScaleFactor) {
        this.baseTimestamp = baseTimestamp;
        this.timeScaleFactor = timeScaleFactor;
    }
    
    /**
     * Set value display format
     * @param unit Unit string (e.g., "%", "°C", "mAh")
     */
    public void setValueUnit(String unit) {
        this.valueUnit = unit != null ? unit : "";
    }
    
    /**
     * Set value suffix (for rates like "%/hr")
     */
    public void setValueSuffix(String suffix) {
        this.valueSuffix = suffix != null ? suffix : "";
    }
    
    /**
     * Set context resolver for dynamic context strings
     */
    public void setContextResolver(ContextResolver resolver) {
        this.contextResolver = resolver;
    }
    
    @Override
    public void refreshContent(Entry e, Highlight highlight) {
        if (e == null) return;
        
        // Calculate timestamp from X value
        long timestamp = baseTimestamp + (long)(e.getX() * timeScaleFactor);
        
        // Format timestamp
        if (tvTimestamp != null) {
            tvTimestamp.setText(dateFormat.format(new Date(timestamp)));
        }
        
        // Format value with unit
        if (tvValue != null) {
            String valueText = formatValue(e.getY());
            tvValue.setText(valueText);
        }
        
        // Get context from resolver or data tag
        if (tvContext != null) {
            String context = resolveContext(e);
            if (context != null && !context.isEmpty()) {
                tvContext.setText(context);
                tvContext.setVisibility(VISIBLE);
            } else {
                tvContext.setVisibility(GONE);
            }
        }
        
        super.refreshContent(e, highlight);
    }
    
    private String formatValue(float value) {
        // Determine precision based on value magnitude
        if (Math.abs(value) >= 100) {
            return String.format(Locale.US, "%.0f%s%s", value, valueUnit, valueSuffix);
        } else if (Math.abs(value) >= 10) {
            return String.format(Locale.US, "%.1f%s%s", value, valueUnit, valueSuffix);
        } else {
            return String.format(Locale.US, "%.2f%s%s", value, valueUnit, valueSuffix);
        }
    }
    
    private String resolveContext(Entry e) {
        // Try resolver first
        if (contextResolver != null) {
            return contextResolver.getContext(e.getX(), e.getY());
        }
        
        // Try data tag (Entry can store arbitrary object)
        Object data = e.getData();
        if (data instanceof String) {
            return (String) data;
        } else if (data instanceof EntryContext) {
            return ((EntryContext) data).getDisplayText();
        }
        
        return null;
    }
    
    @Override
    public MPPointF getOffset() {
        // Position marker above the touch point, centered horizontally
        return new MPPointF(-(getWidth() / 2f), -getHeight() - 10);
    }
    
    @Override
    public MPPointF getOffsetForDrawingAtPoint(float posX, float posY) {
        // Adjust offset to keep marker within chart bounds
        MPPointF offset = getOffset();
        
        // Keep marker within horizontal bounds
        float markerWidth = getWidth();
        if (posX + offset.x < 0) {
            offset.x = -posX;
        }
        
        // If near top, show marker below point instead
        if (posY + offset.y < 0) {
            offset.y = 10;
        }
        
        return offset;
    }
    
    /**
     * Data class for storing context with Entry
     */
    public static class EntryContext {
        public final boolean isCharging;
        public final boolean screenOn;
        public final String customContext;
        
        public EntryContext(boolean isCharging, boolean screenOn) {
            this.isCharging = isCharging;
            this.screenOn = screenOn;
            this.customContext = null;
        }
        
        public EntryContext(String customContext) {
            this.isCharging = false;
            this.screenOn = false;
            this.customContext = customContext;
        }
        
        public String getDisplayText() {
            if (customContext != null && !customContext.isEmpty()) {
                return customContext;
            }
            
            StringBuilder sb = new StringBuilder();
            if (isCharging) {
                sb.append("CHARGING");
            } else {
                sb.append("DISCHARGING");
            }
            
            if (screenOn) {
                sb.append(" • SCREEN ON");
            }
            
            return sb.toString();
        }
    }
}
