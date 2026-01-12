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
        TextView primary = root.findViewById(R.id.ai_primary_issue);
        TextView risk = root.findViewById(R.id.ai_risk);
        TextView recs = root.findViewById(R.id.ai_recommendations);

        executor.execute(() -> {
            HexNilDatabase db = HexNilDatabase.getInstance(requireContext());
            AIAnalysisResult result = db.miscDao().getLatestAIAnalysis();
            requireActivity().runOnUiThread(() -> {
                if (result == null) {
                    primary.setText("Primary issue: no data");
                    risk.setText("Risk: n/a");
                    recs.setText("Recommendations: n/a");
                    return;
                }
                primary.setText("Primary issue: " + result.primaryIssue + " (" + result.severity + ")");
                risk.setText("Risk: " + result.riskScore + " | Trend: " + result.trend);
                recs.setText("Recommendations: " + result.primaryRecommendation);
            });
        });

        return root;
    }
}
