package com.example.hexnil.activity;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
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
import com.example.hexnil.manager.DataQualityValidator;
import com.example.hexnil.manager.DeviceProfileManager;
import com.example.hexnil.manager.LabelingService;
import com.example.hexnil.manager.MLModelManager;
import com.example.hexnil.manager.TrainingDataExporter;
import com.example.hexnil.model.MLModel;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Diagnostic Activity for ML engineers to view ML model performance metrics,
 * data quality scores, and training readiness.
 *
 * Surfaces:
 *   - Data quality validation score (0-100) with per-check breakdown
 *   - Training data statistics (labeled anomalies, negative examples, profiles, snapshots)
 *   - Deployed model performance cards (accuracy, precision, recall, F1)
 *   - Labeling progress with TP/FP/uncertain breakdown
 *   - Device identification (fingerprint, model, OS version)
 *   - One-tap full training data export
 *
 * This is an internal engineering tool, not a consumer-facing screen.
 */
public class MLPerformanceDashboardActivity extends AppCompatActivity {

    private static final String TAG = "MLPerfDashboard";

    // =========================================================================
    // OEM COLOR PALETTE
    // =========================================================================

    private static final int COLOR_BG_PRIMARY = Color.parseColor("#0F1115");
    private static final int COLOR_BG_SECONDARY = Color.parseColor("#161A20");
    private static final int COLOR_BG_CARD = Color.parseColor("#1C2128");
    private static final int COLOR_ACCENT = Color.parseColor("#58A6FF");
    private static final int COLOR_ACCENT_VARIANT = Color.parseColor("#79C0FF");
    private static final int COLOR_TEXT_PRIMARY = Color.parseColor("#FFFFFF");
    private static final int COLOR_TEXT_SECONDARY = Color.parseColor("#8B949E");
    private static final int COLOR_TEXT_TERTIARY = Color.parseColor("#6E7681");
    private static final int COLOR_STATUS_SAFE = Color.parseColor("#238636");
    private static final int COLOR_STATUS_WARNING = Color.parseColor("#D29922");
    private static final int COLOR_STATUS_CRITICAL = Color.parseColor("#DA3633");

    // =========================================================================
    // DEPENDENCIES
    // =========================================================================

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final SimpleDateFormat timestampFormat =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

    private HexNilDatabase database;
    private DataQualityValidator dataQualityValidator;
    private MLModelManager mlModelManager;
    private LabelingService labelingService;
    private DeviceProfileManager deviceProfileManager;
    private TrainingDataExporter trainingDataExporter;

    // =========================================================================
    // UI REFERENCES
    // =========================================================================

    private LinearLayout rootLayout;
    private LinearLayout contentContainer;
    private ProgressBar loadingIndicator;

    // =========================================================================
    // LIFECYCLE
    // =========================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        database = HexNilDatabase.getInstance(this);
        dataQualityValidator = new DataQualityValidator(this, database);
        mlModelManager = new MLModelManager(this, database);
        labelingService = new LabelingService(this, database);
        deviceProfileManager = new DeviceProfileManager(this, database);
        trainingDataExporter = new TrainingDataExporter(this, database);

        rootLayout = buildRootLayout();
        setContentView(rootLayout);

        applyWindowInsets(rootLayout);
        loadDashboardData();
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

        // Loading indicator
        loadingIndicator = new ProgressBar(this);
        loadingIndicator.setIndeterminate(true);
        LinearLayout.LayoutParams loadingParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        loadingParams.gravity = Gravity.CENTER;
        loadingParams.topMargin = dp(48);
        loadingIndicator.setLayoutParams(loadingParams);
        root.addView(loadingIndicator);

        // Scrollable content area
        ScrollView scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);

        contentContainer = new LinearLayout(this);
        contentContainer.setOrientation(LinearLayout.VERTICAL);
        contentContainer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        contentContainer.setPadding(dp(16), dp(8), dp(16), dp(32));
        contentContainer.setVisibility(View.GONE);

        scrollView.addView(contentContainer);
        root.addView(scrollView);

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
        title.setText("ML Dashboard");
        title.setTextColor(COLOR_TEXT_PRIMARY);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.leftMargin = dp(4);
        title.setLayoutParams(titleParams);
        topBar.addView(title);

        return topBar;
    }

    // =========================================================================
    // DATA LOADING
    // =========================================================================

    private void loadDashboardData() {
        showLoading(true);

        executor.execute(() -> {
            try {
                // Section 1: Data quality
                DataQualityValidator.DataQualityReport qualityReport =
                        dataQualityValidator.validateDataReadiness();

                // Section 2: Training data stats
                int labeledAnomalyCount = database.humanLabelDao().getTotalCount();
                int negativeExampleCount = database.negativeExampleDao().getTotalCount();
                int deviceProfileCount = database.deviceProfileDao().getCount();
                int batterySnapshotCount = database.batterySnapshotDao().getTotalCount();

                // Section 3: Deployed models
                List<MLModel> deployedModels = database.mlModelDao().getAllActiveModels();

                // Section 4: Labeling progress
                LabelingService.LabelingProgress labelingProgress =
                        labelingService.getLabelingProgress();

                // Section 5: Device info
                String fingerprint = deviceProfileManager.getCurrentFingerprint();

                runOnUiThread(() -> {
                    showLoading(false);
                    contentContainer.removeAllViews();

                    buildDataQualitySection(qualityReport);
                    buildTrainingDataSection(labeledAnomalyCount, negativeExampleCount,
                            deviceProfileCount, batterySnapshotCount);
                    buildModelPerformanceSection(deployedModels);
                    buildLabelingProgressSection(labelingProgress);
                    buildDeviceInfoSection(fingerprint);
                    buildExportButton();

                    contentContainer.setVisibility(View.VISIBLE);
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to load dashboard data", e);
                runOnUiThread(() -> {
                    showLoading(false);
                    Toast.makeText(MLPerformanceDashboardActivity.this,
                            "Failed to load dashboard: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // =========================================================================
    // SECTION 1: DATA QUALITY SCORE
    // =========================================================================

    private void buildDataQualitySection(DataQualityValidator.DataQualityReport report) {
        contentContainer.addView(buildSectionHeader("DATA QUALITY SCORE"));

        // Score display card
        LinearLayout scoreCard = createCard();

        // Score ring + value
        LinearLayout scoreRow = new LinearLayout(this);
        scoreRow.setOrientation(LinearLayout.HORIZONTAL);
        scoreRow.setGravity(Gravity.CENTER_VERTICAL);
        scoreRow.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // Circular score indicator
        View scoreRing = buildScoreRing(report.overallScore);
        LinearLayout.LayoutParams ringParams = new LinearLayout.LayoutParams(dp(96), dp(96));
        scoreRing.setLayoutParams(ringParams);
        scoreRow.addView(scoreRing);

        // Score details (right side)
        LinearLayout scoreDetails = new LinearLayout(this);
        scoreDetails.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        detailParams.leftMargin = dp(16);
        scoreDetails.setLayoutParams(detailParams);

        TextView readyLabel = new TextView(this);
        readyLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        readyLabel.setTypeface(Typeface.DEFAULT_BOLD);
        if (report.isTrainingReady) {
            readyLabel.setText("Training Ready");
            readyLabel.setTextColor(COLOR_STATUS_SAFE);
        } else {
            readyLabel.setText("Not Ready");
            readyLabel.setTextColor(COLOR_STATUS_CRITICAL);
        }
        scoreDetails.addView(readyLabel);

        TextView checksLabel = new TextView(this);
        checksLabel.setText(report.getPassedCount() + " / " + report.getTotalChecks() + " checks passed");
        checksLabel.setTextColor(COLOR_TEXT_SECONDARY);
        checksLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        LinearLayout.LayoutParams checksParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        checksParams.topMargin = dp(4);
        checksLabel.setLayoutParams(checksParams);
        scoreDetails.addView(checksLabel);

        if (!report.blockers.isEmpty()) {
            TextView blockerLabel = new TextView(this);
            blockerLabel.setText(report.blockers.size() + " blocker(s)");
            blockerLabel.setTextColor(COLOR_STATUS_CRITICAL);
            blockerLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            LinearLayout.LayoutParams blockerParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            blockerParams.topMargin = dp(2);
            blockerLabel.setLayoutParams(blockerParams);
            scoreDetails.addView(blockerLabel);
        }

        if (!report.warnings.isEmpty()) {
            TextView warningLabel = new TextView(this);
            warningLabel.setText(report.warnings.size() + " warning(s)");
            warningLabel.setTextColor(COLOR_STATUS_WARNING);
            warningLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            LinearLayout.LayoutParams warningParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            warningParams.topMargin = dp(2);
            warningLabel.setLayoutParams(warningParams);
            scoreDetails.addView(warningLabel);
        }

        scoreRow.addView(scoreDetails);
        scoreCard.addView(scoreRow);
        contentContainer.addView(scoreCard);

        // Individual check cards
        for (DataQualityValidator.QualityCheck check : report.checks) {
            contentContainer.addView(buildQualityCheckCard(check));
        }
    }

    /**
     * Builds a custom View that renders a circular arc score indicator.
     * The arc is colored based on the score threshold (green >= 80, yellow >= 60, red < 60).
     */
    private View buildScoreRing(int score) {
        int scoreColor;
        if (score >= 80) {
            scoreColor = COLOR_STATUS_SAFE;
        } else if (score >= 60) {
            scoreColor = COLOR_STATUS_WARNING;
        } else {
            scoreColor = COLOR_STATUS_CRITICAL;
        }

        final int finalScoreColor = scoreColor;
        final int finalScore = score;

        return new View(this) {
            private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final RectF arcRect = new RectF();

            {
                bgPaint.setStyle(Paint.Style.STROKE);
                bgPaint.setStrokeWidth(dp(6));
                bgPaint.setColor(Color.parseColor("#30FFFFFF"));
                bgPaint.setStrokeCap(Paint.Cap.ROUND);

                arcPaint.setStyle(Paint.Style.STROKE);
                arcPaint.setStrokeWidth(dp(6));
                arcPaint.setColor(finalScoreColor);
                arcPaint.setStrokeCap(Paint.Cap.ROUND);

                textPaint.setColor(COLOR_TEXT_PRIMARY);
                textPaint.setTextSize(sp(24));
                textPaint.setTypeface(Typeface.DEFAULT_BOLD);
                textPaint.setTextAlign(Paint.Align.CENTER);
            }

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                float cx = getWidth() / 2f;
                float cy = getHeight() / 2f;
                float radius = Math.min(cx, cy) - dp(6);

                arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius);

                // Background ring
                canvas.drawArc(arcRect, -90, 360, false, bgPaint);

                // Score arc
                float sweep = (finalScore / 100f) * 360f;
                canvas.drawArc(arcRect, -90, sweep, false, arcPaint);

                // Score text
                float textY = cy - (textPaint.descent() + textPaint.ascent()) / 2f;
                canvas.drawText(String.valueOf(finalScore), cx, textY, textPaint);
            }
        };
    }

    private View buildQualityCheckCard(DataQualityValidator.QualityCheck check) {
        LinearLayout card = createCard();

        // Header row: name + pass/fail badge
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView nameView = new TextView(this);
        nameView.setText(formatCheckName(check.name));
        nameView.setTextColor(COLOR_TEXT_PRIMARY);
        nameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        nameView.setTypeface(Typeface.DEFAULT_BOLD);
        nameView.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        headerRow.addView(nameView);

        TextView badge = new TextView(this);
        badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setAllCaps(true);
        badge.setPadding(dp(8), dp(3), dp(8), dp(3));

        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setCornerRadius(dp(4));

        if (check.passed) {
            badge.setText("PASS");
            badge.setTextColor(COLOR_TEXT_PRIMARY);
            badgeBg.setColor(COLOR_STATUS_SAFE);
        } else {
            badge.setText("FAIL");
            badge.setTextColor(COLOR_TEXT_PRIMARY);
            badgeBg.setColor("blocker".equals(check.severity)
                    ? COLOR_STATUS_CRITICAL : COLOR_STATUS_WARNING);
        }
        badge.setBackground(badgeBg);
        headerRow.addView(badge);
        card.addView(headerRow);

        // Severity label
        if (!check.passed) {
            TextView severityView = new TextView(this);
            severityView.setText("Severity: " + check.severity);
            severityView.setTextColor("blocker".equals(check.severity)
                    ? COLOR_STATUS_CRITICAL : COLOR_STATUS_WARNING);
            severityView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            LinearLayout.LayoutParams sevParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            sevParams.topMargin = dp(4);
            severityView.setLayoutParams(sevParams);
            card.addView(severityView);
        }

        // Details message
        TextView detailsView = new TextView(this);
        detailsView.setText(check.message);
        detailsView.setTextColor(COLOR_TEXT_SECONDARY);
        detailsView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        detailParams.topMargin = dp(6);
        detailsView.setLayoutParams(detailParams);
        card.addView(detailsView);

        // Value vs threshold
        TextView valueView = new TextView(this);
        valueView.setText("Value: " + formatDouble(check.actualValue)
                + "  |  Threshold: " + formatDouble(check.threshold));
        valueView.setTextColor(COLOR_TEXT_TERTIARY);
        valueView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        valueView.setTypeface(Typeface.MONOSPACE);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        valueParams.topMargin = dp(4);
        valueView.setLayoutParams(valueParams);
        card.addView(valueView);

        return card;
    }

    // =========================================================================
    // SECTION 2: TRAINING DATA STATS
    // =========================================================================

    private void buildTrainingDataSection(int labeledAnomalies, int negativeExamples,
                                          int deviceProfiles, int batterySnapshots) {
        contentContainer.addView(buildSectionHeader("TRAINING DATA STATS"));

        LinearLayout card = createCard();

        addStatRow(card, "Labeled Anomalies", String.valueOf(labeledAnomalies), COLOR_ACCENT);
        addStatRow(card, "Negative Examples", String.valueOf(negativeExamples), COLOR_ACCENT_VARIANT);
        addStatRow(card, "Device Profiles", String.valueOf(deviceProfiles), COLOR_TEXT_PRIMARY);
        addStatRow(card, "Battery Snapshots", String.valueOf(batterySnapshots), COLOR_TEXT_PRIMARY);

        contentContainer.addView(card);
    }

    private void addStatRow(LinearLayout container, String label, String value, int valueColor) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = dp(6);
        row.setLayoutParams(rowParams);

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(COLOR_TEXT_SECONDARY);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        labelView.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(labelView);

        TextView valueView = new TextView(this);
        valueView.setText(value);
        valueView.setTextColor(valueColor);
        valueView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        valueView.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(valueView);

        container.addView(row);
    }

    // =========================================================================
    // SECTION 3: MODEL PERFORMANCE
    // =========================================================================

    private void buildModelPerformanceSection(List<MLModel> deployedModels) {
        contentContainer.addView(buildSectionHeader("MODEL PERFORMANCE"));

        if (deployedModels == null || deployedModels.isEmpty()) {
            LinearLayout emptyCard = createCard();
            TextView emptyText = new TextView(this);
            emptyText.setText("No deployed models.");
            emptyText.setTextColor(COLOR_TEXT_SECONDARY);
            emptyText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            emptyText.setGravity(Gravity.CENTER);
            emptyCard.addView(emptyText);
            contentContainer.addView(emptyCard);
            return;
        }

        for (MLModel model : deployedModels) {
            contentContainer.addView(buildModelCard(model));
        }
    }

    private View buildModelCard(MLModel model) {
        LinearLayout card = createCard();

        // Row 1: Name + status badge
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView nameView = new TextView(this);
        nameView.setText(model.modelName);
        nameView.setTextColor(COLOR_TEXT_PRIMARY);
        nameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        nameView.setTypeface(Typeface.DEFAULT_BOLD);
        nameView.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        titleRow.addView(nameView);

        // Status badge
        TextView statusBadge = new TextView(this);
        String statusText = model.isActive == 1 ? "ACTIVE" : "INACTIVE";
        statusBadge.setText(statusText);
        statusBadge.setTextColor(COLOR_TEXT_PRIMARY);
        statusBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        statusBadge.setTypeface(Typeface.DEFAULT_BOLD);
        statusBadge.setPadding(dp(8), dp(3), dp(8), dp(3));

        GradientDrawable statusBg = new GradientDrawable();
        statusBg.setCornerRadius(dp(4));
        statusBg.setColor(model.isActive == 1 ? COLOR_STATUS_SAFE : COLOR_TEXT_TERTIARY);
        statusBadge.setBackground(statusBg);
        titleRow.addView(statusBadge);

        card.addView(titleRow);

        // Row 2: Version + type
        TextView metaView = new TextView(this);
        String meta = "v" + model.modelVersion;
        if (model.modelType != null) {
            meta += "  |  " + model.modelType;
        }
        if (model.taskType != null) {
            meta += "  |  " + model.taskType;
        }
        metaView.setText(meta);
        metaView.setTextColor(COLOR_TEXT_SECONDARY);
        metaView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        metaParams.topMargin = dp(4);
        metaView.setLayoutParams(metaParams);
        card.addView(metaView);

        // Divider
        card.addView(buildDivider());

        // Row 3: Validation metrics (from training time)
        if (model.hasValidationMetrics()) {
            LinearLayout metricsRow = new LinearLayout(this);
            metricsRow.setOrientation(LinearLayout.HORIZONTAL);
            metricsRow.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams metricsParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            metricsParams.topMargin = dp(4);
            metricsRow.setLayoutParams(metricsParams);

            if (model.validationAccuracy != null) {
                metricsRow.addView(buildMetricChip("Accuracy",
                        formatPercent(model.validationAccuracy), getMetricColor(model.validationAccuracy)));
            }
            if (model.validationPrecision != null) {
                metricsRow.addView(buildMetricChip("Precision",
                        formatPercent(model.validationPrecision), getMetricColor(model.validationPrecision)));
            }
            if (model.validationRecall != null) {
                metricsRow.addView(buildMetricChip("Recall",
                        formatPercent(model.validationRecall), getMetricColor(model.validationRecall)));
            }
            if (model.validationF1 != null) {
                metricsRow.addView(buildMetricChip("F1",
                        formatPercent(model.validationF1), getMetricColor(model.validationF1)));
            }

            card.addView(metricsRow);
        }

        // Row 4: Runtime performance (from predictions)
        MLModelManager.ModelPerformance performance =
                loadModelPerformanceSync(model.id);
        if (performance != null && performance.totalPredictions > 0) {
            LinearLayout runtimeRow = new LinearLayout(this);
            runtimeRow.setOrientation(LinearLayout.HORIZONTAL);
            runtimeRow.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams runtimeParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            runtimeParams.topMargin = dp(6);
            runtimeRow.setLayoutParams(runtimeParams);

            runtimeRow.addView(buildMetricChip("Predictions",
                    String.valueOf(performance.totalPredictions), COLOR_ACCENT));
            runtimeRow.addView(buildMetricChip("Labeled",
                    String.valueOf(performance.labeledCount), COLOR_ACCENT_VARIANT));

            if (performance.labeledCount > 0) {
                float runtimeAcc = performance.accuracyPercent / 100f;
                runtimeRow.addView(buildMetricChip("Runtime Acc",
                        formatPercent(runtimeAcc), getMetricColor(runtimeAcc)));
            }

            card.addView(runtimeRow);

            if (performance.calibrationGap != null) {
                TextView calView = new TextView(this);
                calView.setText("Calibration gap: " + String.format(Locale.US, "%.3f", performance.calibrationGap));
                calView.setTextColor(performance.calibrationGap < 0.1f
                        ? COLOR_STATUS_SAFE : COLOR_STATUS_WARNING);
                calView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                calView.setTypeface(Typeface.MONOSPACE);
                LinearLayout.LayoutParams calParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                calParams.topMargin = dp(4);
                calView.setLayoutParams(calParams);
                card.addView(calView);
            }
        } else {
            TextView noPredView = new TextView(this);
            noPredView.setText("No predictions recorded yet.");
            noPredView.setTextColor(COLOR_TEXT_TERTIARY);
            noPredView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            LinearLayout.LayoutParams noPredParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            noPredParams.topMargin = dp(4);
            noPredView.setLayoutParams(noPredParams);
            card.addView(noPredView);
        }

        // Row 5: Deployment timestamp + training samples
        TextView timestampView = new TextView(this);
        StringBuilder footerText = new StringBuilder();
        if (model.deployedAt != null) {
            footerText.append("Deployed: ").append(timestampFormat.format(new Date(model.deployedAt)));
        }
        if (model.trainingSampleCount > 0) {
            if (footerText.length() > 0) footerText.append("  |  ");
            footerText.append("Trained on: ").append(formatCount(model.trainingSampleCount)).append(" samples");
        }
        timestampView.setText(footerText.toString());
        timestampView.setTextColor(COLOR_TEXT_TERTIARY);
        timestampView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        LinearLayout.LayoutParams tsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tsParams.topMargin = dp(6);
        timestampView.setLayoutParams(tsParams);
        card.addView(timestampView);

        return card;
    }

    /**
     * Loads model performance synchronously. This is called from the background
     * thread during initial data load, so it is safe.
     */
    private MLModelManager.ModelPerformance loadModelPerformanceSync(long modelId) {
        try {
            return mlModelManager.getModelPerformance(modelId);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load performance for model " + modelId, e);
            return null;
        }
    }

    private View buildMetricChip(String label, String value, int valueColor) {
        LinearLayout chip = new LinearLayout(this);
        chip.setOrientation(LinearLayout.VERTICAL);
        chip.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        chipParams.setMargins(dp(2), 0, dp(2), 0);
        chip.setLayoutParams(chipParams);

        TextView valueView = new TextView(this);
        valueView.setText(value);
        valueView.setTextColor(valueColor);
        valueView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        valueView.setTypeface(Typeface.DEFAULT_BOLD);
        valueView.setGravity(Gravity.CENTER);
        chip.addView(valueView);

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(COLOR_TEXT_TERTIARY);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        labelView.setGravity(Gravity.CENTER);
        chip.addView(labelView);

        return chip;
    }

    // =========================================================================
    // SECTION 4: LABELING PROGRESS
    // =========================================================================

    private void buildLabelingProgressSection(LabelingService.LabelingProgress progress) {
        contentContainer.addView(buildSectionHeader("LABELING PROGRESS"));

        LinearLayout card = createCard();

        if (progress == null || progress.totalAnomalies == 0) {
            TextView emptyText = new TextView(this);
            emptyText.setText("No anomalies to label.");
            emptyText.setTextColor(COLOR_TEXT_SECONDARY);
            emptyText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            emptyText.setGravity(Gravity.CENTER);
            card.addView(emptyText);
            contentContainer.addView(card);
            return;
        }

        // Percentage header
        int pct = Math.round(progress.labelingPercentage);
        TextView pctView = new TextView(this);
        pctView.setText(pct + "% labeled (" + progress.labeledCount
                + " / " + progress.totalAnomalies + ")");
        pctView.setTextColor(COLOR_TEXT_PRIMARY);
        pctView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        pctView.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(pctView);

        // Progress bar
        ProgressBar progressBar = new ProgressBar(this, null,
                android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(pct);
        LinearLayout.LayoutParams pbParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(8));
        pbParams.topMargin = dp(10);
        pbParams.bottomMargin = dp(10);
        progressBar.setLayoutParams(pbParams);
        card.addView(progressBar);

        // TP / FP / Uncertain breakdown
        LinearLayout breakdownRow = new LinearLayout(this);
        breakdownRow.setOrientation(LinearLayout.HORIZONTAL);
        breakdownRow.setGravity(Gravity.CENTER);
        breakdownRow.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        breakdownRow.addView(buildBreakdownChip("TP",
                String.valueOf(progress.truePositiveCount), COLOR_STATUS_SAFE));
        breakdownRow.addView(buildBreakdownChip("FP",
                String.valueOf(progress.falsePositiveCount), COLOR_STATUS_CRITICAL));
        breakdownRow.addView(buildBreakdownChip("TN",
                String.valueOf(progress.trueNegativeCount), COLOR_ACCENT));
        breakdownRow.addView(buildBreakdownChip("FN",
                String.valueOf(progress.falseNegativeCount), COLOR_STATUS_WARNING));
        breakdownRow.addView(buildBreakdownChip("Uncertain",
                String.valueOf(progress.inconclusiveCount), COLOR_TEXT_SECONDARY));
        breakdownRow.addView(buildBreakdownChip("Unlabeled",
                String.valueOf(progress.unlabeledCount), COLOR_TEXT_TERTIARY));

        card.addView(breakdownRow);
        contentContainer.addView(card);
    }

    private View buildBreakdownChip(String label, String value, int valueColor) {
        LinearLayout chip = new LinearLayout(this);
        chip.setOrientation(LinearLayout.VERTICAL);
        chip.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        chip.setLayoutParams(chipParams);

        TextView valueView = new TextView(this);
        valueView.setText(value);
        valueView.setTextColor(valueColor);
        valueView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        valueView.setTypeface(Typeface.DEFAULT_BOLD);
        valueView.setGravity(Gravity.CENTER);
        chip.addView(valueView);

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(COLOR_TEXT_SECONDARY);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        labelView.setGravity(Gravity.CENTER);
        chip.addView(labelView);

        return chip;
    }

    // =========================================================================
    // SECTION 5: DEVICE INFO
    // =========================================================================

    private void buildDeviceInfoSection(String fingerprint) {
        contentContainer.addView(buildSectionHeader("DEVICE INFO"));

        LinearLayout card = createCard();

        addStatRow(card, "Fingerprint",
                fingerprint != null && fingerprint.length() > 16
                        ? fingerprint.substring(0, 16) + "..."
                        : (fingerprint != null ? fingerprint : "N/A"),
                COLOR_ACCENT);
        addStatRow(card, "Manufacturer", Build.MANUFACTURER, COLOR_TEXT_PRIMARY);
        addStatRow(card, "Model", Build.MODEL, COLOR_TEXT_PRIMARY);
        addStatRow(card, "Android Version", Build.VERSION.RELEASE, COLOR_TEXT_PRIMARY);
        addStatRow(card, "SDK Level", String.valueOf(Build.VERSION.SDK_INT), COLOR_TEXT_PRIMARY);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            addStatRow(card, "SoC", Build.SOC_MODEL, COLOR_TEXT_PRIMARY);
        } else {
            addStatRow(card, "Hardware", Build.HARDWARE, COLOR_TEXT_PRIMARY);
        }

        // Full fingerprint (tappable to copy)
        if (fingerprint != null) {
            TextView fullFp = new TextView(this);
            fullFp.setText(fingerprint);
            fullFp.setTextColor(COLOR_TEXT_TERTIARY);
            fullFp.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            fullFp.setTypeface(Typeface.MONOSPACE);
            fullFp.setPadding(0, dp(8), 0, 0);
            fullFp.setOnClickListener(v -> {
                android.content.ClipboardManager clipboard =
                        (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(
                            android.content.ClipData.newPlainText("fingerprint", fingerprint));
                    Toast.makeText(this, "Fingerprint copied", Toast.LENGTH_SHORT).show();
                }
            });
            card.addView(fullFp);
        }

        contentContainer.addView(card);
    }

    // =========================================================================
    // EXPORT BUTTON
    // =========================================================================

    private void buildExportButton() {
        TextView exportButton = new TextView(this);
        exportButton.setText("Export Training Data");
        exportButton.setTextColor(COLOR_BG_PRIMARY);
        exportButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        exportButton.setTypeface(Typeface.DEFAULT_BOLD);
        exportButton.setGravity(Gravity.CENTER);
        exportButton.setPadding(dp(16), dp(14), dp(16), dp(14));

        GradientDrawable buttonBg = new GradientDrawable();
        buttonBg.setCornerRadius(dp(10));
        buttonBg.setColor(COLOR_ACCENT);
        exportButton.setBackground(buttonBg);

        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonParams.topMargin = dp(20);
        buttonParams.bottomMargin = dp(16);
        exportButton.setLayoutParams(buttonParams);

        exportButton.setOnClickListener(v -> onExportClicked(exportButton));

        contentContainer.addView(exportButton);
    }

    private void onExportClicked(TextView button) {
        button.setEnabled(false);
        button.setAlpha(0.5f);
        button.setText("Exporting...");

        executor.execute(() -> {
            try {
                File exportDir = new File(getExternalFilesDir(null),
                        "ml_training_export_" + System.currentTimeMillis());

                TrainingDataExporter.ExportResult result =
                        trainingDataExporter.exportFullTrainingDataset(exportDir);

                runOnUiThread(() -> {
                    button.setEnabled(true);
                    button.setAlpha(1.0f);
                    button.setText("Export Training Data");

                    if (result.success) {
                        Toast.makeText(MLPerformanceDashboardActivity.this,
                                "Exported " + result.totalRecords + " records to:\n"
                                        + result.outputDirectory,
                                Toast.LENGTH_LONG).show();
                    } else {
                        String errorMsg = result.errors != null && !result.errors.isEmpty()
                                ? result.errors.get(0) : "Unknown error";
                        Toast.makeText(MLPerformanceDashboardActivity.this,
                                "Export failed: " + errorMsg,
                                Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Export failed", e);
                runOnUiThread(() -> {
                    button.setEnabled(true);
                    button.setAlpha(1.0f);
                    button.setText("Export Training Data");
                    Toast.makeText(MLPerformanceDashboardActivity.this,
                            "Export failed: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // =========================================================================
    // SHARED UI BUILDERS
    // =========================================================================

    private TextView buildSectionHeader(String title) {
        TextView header = new TextView(this);
        header.setText(title);
        header.setTextColor(COLOR_ACCENT);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setAllCaps(true);
        header.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        headerParams.topMargin = dp(20);
        headerParams.bottomMargin = dp(6);
        header.setLayoutParams(headerParams);
        return header;
    }

    private LinearLayout createCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(makeCardBackground());
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(8);
        card.setLayoutParams(cardParams);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        return card;
    }

    private View buildDivider() {
        View divider = new View(this);
        divider.setBackgroundColor(Color.parseColor("#20FFFFFF"));
        LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        divParams.topMargin = dp(8);
        divParams.bottomMargin = dp(4);
        divider.setLayoutParams(divParams);
        return divider;
    }

    // =========================================================================
    // UTILITY METHODS
    // =========================================================================

    private void showLoading(boolean loading) {
        loadingIndicator.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

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

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }

    private float sp(int value) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, value,
                getResources().getDisplayMetrics());
    }

    /**
     * Returns a color indicating metric quality: green for >= 0.8, yellow for >= 0.6, red below.
     */
    private int getMetricColor(float value) {
        if (value >= 0.8f) return COLOR_STATUS_SAFE;
        if (value >= 0.6f) return COLOR_STATUS_WARNING;
        return COLOR_STATUS_CRITICAL;
    }

    /**
     * Formats a 0-1 float as a percentage string, e.g. 0.872 -> "87.2%".
     */
    private String formatPercent(float value) {
        return String.format(Locale.US, "%.1f%%", value * 100f);
    }

    /**
     * Formats a double value, showing integers without decimals.
     */
    private String formatDouble(double value) {
        if (value == (long) value && Math.abs(value) < 1_000_000) {
            return String.format(Locale.US, "%d", (long) value);
        }
        return String.format(Locale.US, "%.2f", value);
    }

    /**
     * Formats a count with k/M suffix for large numbers.
     */
    private String formatCount(int count) {
        if (count >= 1_000_000) {
            return String.format(Locale.US, "%.1fM", count / 1_000_000.0);
        }
        if (count >= 1_000) {
            return String.format(Locale.US, "%.1fk", count / 1_000.0);
        }
        return String.valueOf(count);
    }

    /**
     * Converts a snake_case check name to Title Case for display.
     */
    private String formatCheckName(String name) {
        if (name == null || name.isEmpty()) return "Unknown";
        String[] parts = name.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) sb.append(part.substring(1));
        }
        return sb.toString();
    }
}
