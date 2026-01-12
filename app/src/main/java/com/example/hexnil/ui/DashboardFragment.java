package com.example.hexnil.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.hexnil.ModuleNavigator;
import com.example.hexnil.R;
import com.google.android.material.button.MaterialButton;

/**
 * Entry screen with quick links to all modules.
 */
public class DashboardFragment extends Fragment {

    private ModuleNavigator navigator;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof ModuleNavigator) {
            navigator = (ModuleNavigator) context;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_dashboard, container, false);

        TextView status = root.findViewById(R.id.dashboard_status);
        status.setText(getString(R.string.app_name) + " ready");

        setNav(root, R.id.btn_health, "health");
        setNav(root, R.id.btn_idle, "idle");
        setNav(root, R.id.btn_calibration, "calibration");
        setNav(root, R.id.btn_ai, "ai");
        setNav(root, R.id.btn_reports, "reports");
        setNav(root, R.id.btn_compliance, "compliance");
        setNav(root, R.id.btn_device, "device");
        setNav(root, R.id.btn_settings, "settings");

        return root;
    }

    private void setNav(View root, int buttonId, String tag) {
        MaterialButton btn = root.findViewById(buttonId);
        if (btn != null) {
            btn.setOnClickListener(v -> {
                if (navigator != null) navigator.openModule(tag);
            });
        }
    }
}
