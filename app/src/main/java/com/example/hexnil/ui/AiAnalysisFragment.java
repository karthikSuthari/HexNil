package com.example.hexnil.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.hexnil.R;
import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.model.AIAnalysisResult;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AiAnalysisFragment extends Fragment {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_ai_analysis, container, false);
        TextView tvExecutiveSummary = root.findViewById(R.id.tv_executive_summary);
        TextView tvRiskScore = root.findViewById(R.id.tv_risk_score);
        TextView tvSeverity = root.findViewById(R.id.tv_severity);
        TextView tvRootCause = root.findViewById(R.id.tv_root_cause);
        TextView tvRecommendations = root.findViewById(R.id.tv_recommendations);
        TextView tvTechnicalDetails = root.findViewById(R.id.tv_technical_details);

        executor.execute(() -> {
            HexNilDatabase db = HexNilDatabase.getInstance(requireContext());
            AIAnalysisResult result = db.miscDao().getLatestAIAnalysis();
            requireActivity().runOnUiThread(() -> {
                if (result == null) {
                    tvExecutiveSummary.setText("No analysis data available");
                    tvRiskScore.setText("--");
                    tvSeverity.setText("--");
                    tvRootCause.setText("No issues detected");
                    tvRecommendations.setText("No recommendations at this time");
                    tvTechnicalDetails.setText("Analysis period: --\nData points analyzed: --\nConfidence: --");
                    return;
                }
                
                // Executive Summary
                if (result.executiveSummary != null && !result.executiveSummary.isEmpty()) {
                    tvExecutiveSummary.setText(result.executiveSummary);
                } else {
                    tvExecutiveSummary.setText("Battery system operating within normal parameters.");
                }
                
                // Risk Score
                tvRiskScore.setText(String.valueOf((int)result.riskScore));
                
                // Severity
                tvSeverity.setText(result.severity != null ? result.severity.toUpperCase() : "LOW");
                
                // Root Cause
                if (result.rootCause != null && !result.rootCause.isEmpty()) {
                    tvRootCause.setText(result.rootCause);
                } else {
                    tvRootCause.setText("Primary issue: " + (result.primaryIssue != null ? result.primaryIssue : "None"));
                }
                
                // Recommendations
                if (result.primaryRecommendation != null && !result.primaryRecommendation.isEmpty()) {
                    tvRecommendations.setText(result.primaryRecommendation);
                } else {
                    tvRecommendations.setText("Continue monitoring. System operating within expected parameters.");
                }
                
                // Technical Details
                StringBuilder techDetails = new StringBuilder();
                techDetails.append("Analysis period: ").append(result.analysisPeriod != null ? result.analysisPeriod : "Last 24 hours").append("\n");
                techDetails.append("Data points analyzed: ").append(result.dataPointsAnalyzed > 0 ? result.dataPointsAnalyzed : "--").append("\n");
                techDetails.append("Confidence: ").append(result.issueConfidence > 0 ? String.format("%.0f%%", result.issueConfidence * 100) : "--");
                tvTechnicalDetails.setText(techDetails.toString());
            });
        });

        return root;
    }
}
