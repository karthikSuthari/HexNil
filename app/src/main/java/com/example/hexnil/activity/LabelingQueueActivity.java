package com.example.hexnil.activity;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.manager.LabelingService;
import com.example.hexnil.model.AnomalyRecord;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Diagnostic Activity for ML engineers to review and label anomaly detections.
 *
 * Presents a queue of unlabeled anomaly records and allows engineers to classify
 * each as true_positive, false_positive, or inconclusive. Labels feed the ground
 * truth pipeline used to measure and improve model precision/recall.
 *
 * This is an internal engineering tool, not a consumer-facing screen.
 */
public class LabelingQueueActivity extends AppCompatActivity {

    private static final String TAG = "LabelingQueue";

    // OEM color palette
    private static final int COLOR_BG_PRIMARY = Color.parseColor("#0F1115");
    private static final int COLOR_BG_CARD = Color.parseColor("#1C2128");
    private static final int COLOR_ACCENT = Color.parseColor("#58A6FF");
    private static final int COLOR_TEXT_PRIMARY = Color.parseColor("#FFFFFF");
    private static final int COLOR_TEXT_SECONDARY = Color.parseColor("#8B949E");
    private static final int COLOR_STATUS_SAFE = Color.parseColor("#238636");
    private static final int COLOR_STATUS_WARNING = Color.parseColor("#D29922");
    private static final int COLOR_STATUS_CRITICAL = Color.parseColor("#DA3633");

    private static final int QUEUE_BATCH_SIZE = 50;
    private static final String LABELER_USER_ID = "oem_engineer";
    private static final String LABELER_ROLE = "oem_engineer";
    private static final float DEFAULT_CONFIDENCE = 0.9f;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final SimpleDateFormat timestampFormat =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

    private HexNilDatabase database;
    private LabelingService labelingService;

    // UI references
    private LinearLayout rootLayout;
    private LinearLayout queueContainer;
    private TextView progressText;
    private TextView summaryLabeledCount;
    private TextView summaryUnlabeledCount;
    private TextView summaryTpCount;
    private TextView summaryFpCount;
    private TextView summaryInconclusiveCount;
    private TextView summaryPercentage;
    private ProgressBar summaryProgressBar;
    private TextView emptyStateText;
    private ProgressBar loadingIndicator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        database = HexNilDatabase.getInstance(this);
        labelingService = new LabelingService(this, database);

        rootLayout = buildRootLayout();
        setContentView(rootLayout);

        applyWindowInsets(rootLayout);
        loadQueueData();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    // =========================================================================
    // LAYOUT CONSTRUCTION
    // =========================================================================

    private LinearLayout buildRootLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_BG_PRIMARY);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        root.addView(buildTopBar());
        root.addView(buildSummaryCard());

        // Loading indicator
        loadingIndicator = new ProgressBar(this);
        loadingIndicator.setIndeterminate(true);
        LinearLayout.LayoutParams loadingParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        loadingParams.gravity = Gravity.CENTER;
        loadingParams.topMargin = dp(32);
        loadingIndicator.setLayoutParams(loadingParams);
        root.addView(loadingIndicator);

        // Scrollable queue area
        ScrollView scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);

        queueContainer = new LinearLayout(this);
        queueContainer.setOrientation(LinearLayout.VERTICAL);
        queueContainer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        queueContainer.setPadding(dp(16), dp(8), dp(16), dp(24));

        scrollView.addView(queueContainer);
        root.addView(scrollView);

        // Empty state text (hidden initially)
        emptyStateText = new TextView(this);
        emptyStateText.setText("No unlabeled anomalies in queue.");
        emptyStateText.setTextColor(COLOR_TEXT_SECONDARY);
        emptyStateText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        emptyStateText.setGravity(Gravity.CENTER);
        emptyStateText.setVisibility(View.GONE);
        LinearLayout.LayoutParams emptyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        emptyParams.topMargin = dp(48);
        emptyStateText.setLayoutParams(emptyParams);
        queueContainer.addView(emptyStateText);

        return root;
    }

    private LinearLayout buildTopBar() {
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackgroundColor(COLOR_BG_CARD);
        topBar.setPadding(dp(8), dp(12), dp(16), dp(12));
        topBar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // Back button
        TextView backButton = new TextView(this);
        backButton.setText("\u2190");
        backButton.setTextColor(COLOR_ACCENT);
        backButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        backButton.setPadding(dp(12), dp(8), dp(12), dp(8));
        backButton.setOnClickListener(v -> finish());
        topBar.addView(backButton);

        // Title
        TextView title = new TextView(this);
        title.setText("Labeling Queue");
        title.setTextColor(COLOR_TEXT_PRIMARY);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.leftMargin = dp(4);
        title.setLayoutParams(titleParams);
        topBar.addView(title);

        // Progress text (right side)
        progressText = new TextView(this);
        progressText.setText("--");
        progressText.setTextColor(COLOR_TEXT_SECONDARY);
        progressText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        topBar.addView(progressText);

        return topBar;
    }

    private LinearLayout buildSummaryCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(makeCardBackground());
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(dp(16), dp(12), dp(16), dp(4));
        card.setLayoutParams(cardParams);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));

        // Card title
        TextView cardTitle = new TextView(this);
        cardTitle.setText("Labeling Progress");
        cardTitle.setTextColor(COLOR_ACCENT);
        cardTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        cardTitle.setTypeface(Typeface.DEFAULT_BOLD);
        cardTitle.setAllCaps(true);
        cardTitle.setLetterSpacing(0.05f);
        card.addView(cardTitle);

        // Progress bar
        summaryProgressBar = new ProgressBar(this, null,
                android.R.attr.progressBarStyleHorizontal);
        summaryProgressBar.setMax(100);
        summaryProgressBar.setProgress(0);
        LinearLayout.LayoutParams pbParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(6));
        pbParams.topMargin = dp(10);
        pbParams.bottomMargin = dp(10);
        summaryProgressBar.setLayoutParams(pbParams);
        card.addView(summaryProgressBar);

        // Percentage text
        summaryPercentage = new TextView(this);
        summaryPercentage.setText("0%");
        summaryPercentage.setTextColor(COLOR_TEXT_PRIMARY);
        summaryPercentage.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        summaryPercentage.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(summaryPercentage);

        // Stats row
        HorizontalScrollView statsScroll = new HorizontalScrollView(this);
        statsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout.LayoutParams statsScrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        statsScrollParams.topMargin = dp(8);
        statsScroll.setLayoutParams(statsScrollParams);

        LinearLayout statsRow = new LinearLayout(this);
        statsRow.setOrientation(LinearLayout.HORIZONTAL);
        statsRow.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        summaryLabeledCount = addStatChip(statsRow, "Labeled", "0", COLOR_ACCENT);
        summaryUnlabeledCount = addStatChip(statsRow, "Unlabeled", "0", COLOR_TEXT_SECONDARY);
        summaryTpCount = addStatChip(statsRow, "TP", "0", COLOR_STATUS_SAFE);
        summaryFpCount = addStatChip(statsRow, "FP", "0", COLOR_STATUS_CRITICAL);
        summaryInconclusiveCount = addStatChip(statsRow, "Uncertain", "0", COLOR_STATUS_WARNING);

        statsScroll.addView(statsRow);
        card.addView(statsScroll);

        return card;
    }

    /**
     * Adds a labeled stat chip to a horizontal container and returns the value TextView.
     */
    private TextView addStatChip(LinearLayout container, String label, String value, int color) {
        LinearLayout chip = new LinearLayout(this);
        chip.setOrientation(LinearLayout.VERTICAL);
        chip.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        chipParams.rightMargin = dp(20);
        chip.setLayoutParams(chipParams);

        TextView valueView = new TextView(this);
        valueView.setText(value);
        valueView.setTextColor(color);
        valueView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        valueView.setTypeface(Typeface.DEFAULT_BOLD);
        chip.addView(valueView);

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(COLOR_TEXT_SECONDARY);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        chip.addView(labelView);

        container.addView(chip);
        return valueView;
    }

    // =========================================================================
    // ANOMALY CARD CONSTRUCTION
    // =========================================================================

    private View buildAnomalyCard(AnomalyRecord anomaly) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(makeCardBackground());
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(10);
        card.setLayoutParams(cardParams);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setTag(anomaly.id);

        // Row 1: Title + severity badge
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView titleView = new TextView(this);
        titleView.setText(anomaly.title != null ? anomaly.title : "Anomaly #" + anomaly.id);
        titleView.setTextColor(COLOR_TEXT_PRIMARY);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setMaxLines(2);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        titleRow.addView(titleView);

        titleRow.addView(buildSeverityBadge(anomaly.severity));
        card.addView(titleRow);

        // Row 2: Type + Source
        LinearLayout metaRow = new LinearLayout(this);
        metaRow.setOrientation(LinearLayout.HORIZONTAL);
        metaRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        metaParams.topMargin = dp(6);
        metaRow.setLayoutParams(metaParams);

        if (anomaly.anomalyType != null) {
            TextView typeLabel = makeMetaLabel("Type: " + anomaly.anomalyType);
            metaRow.addView(typeLabel);
        }

        if (anomaly.sourceAppName != null && !anomaly.sourceAppName.isEmpty()) {
            TextView srcLabel = makeMetaLabel("  |  App: " + anomaly.sourceAppName);
            metaRow.addView(srcLabel);
        } else if (anomaly.sourceApp != null && !anomaly.sourceApp.isEmpty()) {
            TextView srcLabel = makeMetaLabel("  |  Pkg: " + anomaly.sourceApp);
            metaRow.addView(srcLabel);
        }

        card.addView(metaRow);

        // Row 3: Metric values
        if (anomaly.metricName != null && !anomaly.metricName.isEmpty()) {
            LinearLayout metricRow = new LinearLayout(this);
            metricRow.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams metricParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            metricParams.topMargin = dp(6);
            metricRow.setLayoutParams(metricParams);

            String unit = anomaly.metricUnit != null ? anomaly.metricUnit : "";
            String metricText = String.format(Locale.US, "%s: %.1f%s (expected: %.1f%s)",
                    anomaly.metricName, anomaly.actualValue, unit,
                    anomaly.expectedValue, unit);
            TextView metricView = new TextView(this);
            metricView.setText(metricText);
            metricView.setTextColor(COLOR_TEXT_SECONDARY);
            metricView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            metricView.setTypeface(Typeface.MONOSPACE);
            metricRow.addView(metricView);

            card.addView(metricRow);
        }

        // Row 4: Timestamp + Confidence + Occurrences
        LinearLayout infoRow = new LinearLayout(this);
        infoRow.setOrientation(LinearLayout.HORIZONTAL);
        infoRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        infoParams.topMargin = dp(6);
        infoRow.setLayoutParams(infoParams);

        String timeStr = timestampFormat.format(new Date(anomaly.detectedAt));
        TextView timeView = makeMetaLabel(timeStr);
        timeView.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        infoRow.addView(timeView);

        TextView confView = new TextView(this);
        confView.setText(String.format(Locale.US, "Conf: %.0f%%", anomaly.confidenceScore));
        confView.setTextColor(COLOR_ACCENT);
        confView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        confView.setTypeface(Typeface.DEFAULT_BOLD);
        infoRow.addView(confView);

        if (anomaly.occurrenceCount > 1) {
            TextView occView = new TextView(this);
            occView.setText(String.format(Locale.US, "  x%d", anomaly.occurrenceCount));
            occView.setTextColor(COLOR_STATUS_WARNING);
            occView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            infoRow.addView(occView);
        }

        card.addView(infoRow);

        // Row 5: Description (if present, compact)
        if (anomaly.description != null && !anomaly.description.isEmpty()) {
            TextView descView = new TextView(this);
            descView.setText(anomaly.description);
            descView.setTextColor(COLOR_TEXT_SECONDARY);
            descView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            descView.setMaxLines(3);
            descView.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            descParams.topMargin = dp(6);
            descView.setLayoutParams(descParams);
            card.addView(descView);
        }

        // Divider before action buttons
        View divider = new View(this);
        divider.setBackgroundColor(Color.parseColor("#30FFFFFF"));
        LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        divParams.topMargin = dp(10);
        divParams.bottomMargin = dp(10);
        divider.setLayoutParams(divParams);
        card.addView(divider);

        // Row 6: Action buttons
        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.CENTER);
        buttonRow.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        buttonRow.addView(buildActionButton("True Positive", COLOR_STATUS_SAFE,
                "true_positive", anomaly.id, card));
        buttonRow.addView(buildActionButton("False Positive", COLOR_STATUS_CRITICAL,
                "false_positive", anomaly.id, card));
        buttonRow.addView(buildActionButton("Uncertain", COLOR_STATUS_WARNING,
                "inconclusive", anomaly.id, card));

        card.addView(buttonRow);

        return card;
    }

    private TextView buildSeverityBadge(String severity) {
        TextView badge = new TextView(this);
        badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setAllCaps(true);
        badge.setPadding(dp(8), dp(3), dp(8), dp(3));
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        badgeParams.leftMargin = dp(8);
        badge.setLayoutParams(badgeParams);

        int bgColor;
        if ("critical".equals(severity)) {
            bgColor = COLOR_STATUS_CRITICAL;
        } else if ("high".equals(severity)) {
            bgColor = Color.parseColor("#B8600A");
        } else if ("medium".equals(severity)) {
            bgColor = COLOR_STATUS_WARNING;
        } else {
            bgColor = COLOR_TEXT_SECONDARY;
        }

        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setCornerRadius(dp(4));
        badgeBg.setColor(bgColor);
        badge.setBackground(badgeBg);

        badge.setText(severity != null ? severity : "unknown");
        badge.setTextColor(COLOR_TEXT_PRIMARY);

        return badge;
    }

    private View buildActionButton(String text, int color, String verdict,
                                   long anomalyId, View cardView) {
        TextView button = new TextView(this);
        button.setText(text);
        button.setTextColor(color);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(12), dp(10), dp(12), dp(10));

        GradientDrawable buttonBg = new GradientDrawable();
        buttonBg.setCornerRadius(dp(6));
        buttonBg.setStroke(dp(1), color);
        buttonBg.setColor(Color.TRANSPARENT);
        button.setBackground(buttonBg);

        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        btnParams.setMargins(dp(4), 0, dp(4), 0);
        button.setLayoutParams(btnParams);

        button.setOnClickListener(v -> onLabelClicked(anomalyId, verdict, cardView));

        return button;
    }

    // =========================================================================
    // DATA LOADING
    // =========================================================================

    private void loadQueueData() {
        showLoading(true);

        executor.execute(() -> {
            try {
                // Fetch unlabeled anomaly IDs
                List<Long> unlabeledIds = labelingService.getUnlabeledAnomalyQueue(
                        QUEUE_BATCH_SIZE);

                // Fetch full AnomalyRecord objects
                List<AnomalyRecord> anomalies = new ArrayList<>();
                for (Long id : unlabeledIds) {
                    AnomalyRecord record = database.anomalyRecordDao().getById(id);
                    if (record != null) {
                        anomalies.add(record);
                    }
                }

                // Fetch progress
                LabelingService.LabelingProgress progress =
                        labelingService.getLabelingProgress();

                runOnUiThread(() -> {
                    showLoading(false);
                    updateProgressUI(progress);
                    populateQueue(anomalies);
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to load labeling queue", e);
                runOnUiThread(() -> {
                    showLoading(false);
                    Toast.makeText(LabelingQueueActivity.this,
                            "Failed to load queue: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void populateQueue(List<AnomalyRecord> anomalies) {
        // Remove all views except the empty state text
        int childCount = queueContainer.getChildCount();
        for (int i = childCount - 1; i >= 0; i--) {
            View child = queueContainer.getChildAt(i);
            if (child != emptyStateText) {
                queueContainer.removeViewAt(i);
            }
        }

        if (anomalies.isEmpty()) {
            emptyStateText.setVisibility(View.VISIBLE);
        } else {
            emptyStateText.setVisibility(View.GONE);
            for (AnomalyRecord anomaly : anomalies) {
                queueContainer.addView(buildAnomalyCard(anomaly));
            }
        }
    }

    // =========================================================================
    // LABELING ACTION
    // =========================================================================

    private void onLabelClicked(long anomalyId, String verdict, View cardView) {
        // Disable the card to prevent double-taps
        setCardEnabled(cardView, false);

        executor.execute(() -> {
            try {
                labelingService.labelAnomaly(
                        anomalyId,
                        LABELER_USER_ID,
                        LABELER_ROLE,
                        verdict,
                        DEFAULT_CONFIDENCE,
                        null
                );

                LabelingService.LabelingProgress progress =
                        labelingService.getLabelingProgress();

                runOnUiThread(() -> {
                    // Animate card removal
                    cardView.animate()
                            .alpha(0f)
                            .translationX(cardView.getWidth())
                            .setDuration(250)
                            .withEndAction(() -> {
                                queueContainer.removeView(cardView);
                                checkEmptyState();
                            })
                            .start();

                    updateProgressUI(progress);

                    String label = verdictToDisplay(verdict);
                    Toast.makeText(LabelingQueueActivity.this,
                            "Labeled #" + anomalyId + " as " + label,
                            Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to label anomaly " + anomalyId, e);
                runOnUiThread(() -> {
                    setCardEnabled(cardView, true);
                    Toast.makeText(LabelingQueueActivity.this,
                            "Labeling failed: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void setCardEnabled(View card, boolean enabled) {
        card.setAlpha(enabled ? 1.0f : 0.5f);
        if (card instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) card;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                child.setEnabled(enabled);
                if (child instanceof ViewGroup) {
                    setChildrenEnabled((ViewGroup) child, enabled);
                }
            }
        }
    }

    private void setChildrenEnabled(ViewGroup group, boolean enabled) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            child.setEnabled(enabled);
            if (child instanceof ViewGroup) {
                setChildrenEnabled((ViewGroup) child, enabled);
            }
        }
    }

    // =========================================================================
    // UI UPDATES
    // =========================================================================

    private void updateProgressUI(LabelingService.LabelingProgress progress) {
        if (progress == null) {
            return;
        }

        progressText.setText(String.format(Locale.US, "%d / %d labeled",
                progress.labeledCount, progress.totalAnomalies));

        summaryLabeledCount.setText(String.valueOf(progress.labeledCount));
        summaryUnlabeledCount.setText(String.valueOf(progress.unlabeledCount));
        summaryTpCount.setText(String.valueOf(progress.truePositiveCount));
        summaryFpCount.setText(String.valueOf(progress.falsePositiveCount));
        summaryInconclusiveCount.setText(String.valueOf(progress.inconclusiveCount));

        int pct = Math.round(progress.labelingPercentage);
        summaryPercentage.setText(String.format(Locale.US, "%d%% labeled", pct));
        summaryProgressBar.setProgress(pct);
    }

    private void checkEmptyState() {
        // Count remaining anomaly cards (exclude emptyStateText)
        int remainingCards = 0;
        for (int i = 0; i < queueContainer.getChildCount(); i++) {
            View child = queueContainer.getChildAt(i);
            if (child != emptyStateText && child.getVisibility() == View.VISIBLE) {
                remainingCards++;
            }
        }

        if (remainingCards == 0) {
            emptyStateText.setVisibility(View.VISIBLE);
            emptyStateText.setText("All anomalies in this batch have been labeled.");
        }
    }

    private void showLoading(boolean loading) {
        loadingIndicator.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    // =========================================================================
    // UTILITY METHODS
    // =========================================================================

    private void applyWindowInsets(View root) {
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets cutout = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout());

            int top = Math.max(insets.top, cutout.top);
            int bottom = Math.max(insets.bottom, cutout.bottom);
            int left = Math.max(insets.left, cutout.left);
            int right = Math.max(insets.right, cutout.right);

            v.setPadding(left, top, right, bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    private GradientDrawable makeCardBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(COLOR_BG_CARD);
        drawable.setCornerRadius(dp(10));
        return drawable;
    }

    private TextView makeMetaLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(COLOR_TEXT_SECONDARY);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        return tv;
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }

    private static String verdictToDisplay(String verdict) {
        switch (verdict) {
            case "true_positive":
                return "True Positive";
            case "false_positive":
                return "False Positive";
            case "inconclusive":
                return "Uncertain";
            default:
                return verdict;
        }
    }
}
