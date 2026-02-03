package com.example.hexnil.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.hexnil.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Adapter for displaying log events in LogsFragment.
 * Supports expandable item details on tap.
 */
public class LogEventAdapter extends RecyclerView.Adapter<LogEventAdapter.ViewHolder> {

    private final Context context;
    private List<LogEventItem> items = new ArrayList<>();
    private final Set<Long> expandedItems = new HashSet<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.US);
    private final SimpleDateFormat dateTimeFormat = new SimpleDateFormat("MMM dd, HH:mm", Locale.US);

    public LogEventAdapter(Context context) {
        this.context = context;
    }

    public void setItems(List<LogEventItem> items) {
        this.items = items != null ? items : new ArrayList<>();
        expandedItems.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_log_event, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LogEventItem item = items.get(position);
        boolean isExpanded = expandedItems.contains(item.id);
        
        // Severity indicator color
        int severityColor;
        switch (item.severity) {
            case LogEventItem.SEVERITY_CRITICAL:
                severityColor = ContextCompat.getColor(context, R.color.oem_status_critical);
                break;
            case LogEventItem.SEVERITY_WARNING:
                severityColor = ContextCompat.getColor(context, R.color.oem_status_warning);
                break;
            case LogEventItem.SEVERITY_SAFE:
                severityColor = ContextCompat.getColor(context, R.color.oem_status_safe);
                break;
            default:
                severityColor = ContextCompat.getColor(context, R.color.oem_text_tertiary);
        }
        holder.severityIndicator.setBackgroundColor(severityColor);
        
        // Event type and title
        holder.tvEventType.setText(item.eventType);
        holder.tvTitle.setText(item.title);
        
        // Timestamp - show date if older than today
        String timestamp = formatTimestamp(item.timestamp);
        holder.tvTimestamp.setText(timestamp);
        
        // Description
        holder.tvDescription.setText(item.description != null ? item.description : "");
        
        // Details section (expanded state)
        holder.detailsSection.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
        holder.tvDetails.setText(item.details != null ? item.details : "No additional details.");
        
        // Expand indicator rotation
        holder.ivExpand.setRotation(isExpanded ? 180f : 0f);
        
        // Click to expand/collapse
        holder.itemView.setOnClickListener(v -> {
            if (expandedItems.contains(item.id)) {
                expandedItems.remove(item.id);
            } else {
                expandedItems.add(item.id);
            }
            notifyItemChanged(position);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private String formatTimestamp(long timestamp) {
        long now = System.currentTimeMillis();
        long oneDayAgo = now - (24L * 60 * 60 * 1000);
        
        if (timestamp > oneDayAgo) {
            return timeFormat.format(new Date(timestamp));
        } else {
            return dateTimeFormat.format(new Date(timestamp));
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        View severityIndicator;
        TextView tvEventType;
        TextView tvTitle;
        TextView tvTimestamp;
        TextView tvDescription;
        LinearLayout detailsSection;
        TextView tvDetails;
        ImageView ivExpand;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            severityIndicator = itemView.findViewById(R.id.severity_indicator);
            tvEventType = itemView.findViewById(R.id.tv_event_type);
            tvTitle = itemView.findViewById(R.id.tv_event_title);
            tvTimestamp = itemView.findViewById(R.id.tv_timestamp);
            tvDescription = itemView.findViewById(R.id.tv_description);
            detailsSection = itemView.findViewById(R.id.details_section);
            tvDetails = itemView.findViewById(R.id.tv_details);
            ivExpand = itemView.findViewById(R.id.iv_expand);
        }
    }
}