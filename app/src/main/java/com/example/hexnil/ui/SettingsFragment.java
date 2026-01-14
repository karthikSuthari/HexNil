package com.example.hexnil.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.hexnil.R;
import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.service.BatteryMonitorService;
import com.example.hexnil.service.RemoteMonitorServer;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Settings fragment with service controls and app configuration.
 */
public class SettingsFragment extends Fragment {

    private HexNilDatabase database;
    private ExecutorService executor;
    private Handler handler;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_settings, container, false);
        
        database = HexNilDatabase.getInstance(requireContext());
        executor = Executors.newSingleThreadExecutor();
        handler = new Handler(Looper.getMainLooper());
        
        setupViews(root);
        updateStats(root);
        
        return root;
    }
    
    private void setupViews(View root) {
        // Service Controls
        SwitchMaterial switchMonitor = root.findViewById(R.id.switch_monitor);
        SwitchMaterial switchRemote = root.findViewById(R.id.switch_remote);
        MaterialButton btnBatteryOptimize = root.findViewById(R.id.btn_battery_optimize);
        MaterialButton btnClearData = root.findViewById(R.id.btn_clear_data);
        MaterialButton btnExportData = root.findViewById(R.id.btn_export_data);
        
        if (switchMonitor != null) {
            switchMonitor.setChecked(true); // Service should be running
            switchMonitor.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    BatteryMonitorService.start(requireContext());
                    Toast.makeText(requireContext(), "Monitoring started", Toast.LENGTH_SHORT).show();
                } else {
                    BatteryMonitorService.stop(requireContext());
                    Toast.makeText(requireContext(), "Monitoring stopped", Toast.LENGTH_SHORT).show();
                }
            });
        }
        
        if (switchRemote != null) {
            switchRemote.setChecked(RemoteMonitorServer.isRunning());
            switchRemote.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    RemoteMonitorServer.start(requireContext());
                    handler.postDelayed(() -> {
                        String url = RemoteMonitorServer.getServerUrl();
                        if (url != null) {
                            Toast.makeText(requireContext(), "Remote server: " + url, Toast.LENGTH_LONG).show();
                        }
                    }, 1000);
                } else {
                    RemoteMonitorServer.stop(requireContext());
                    Toast.makeText(requireContext(), "Remote server stopped", Toast.LENGTH_SHORT).show();
                }
            });
        }
        
        if (btnBatteryOptimize != null) {
            btnBatteryOptimize.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + requireContext().getPackageName()));
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(requireContext(), "Cannot open battery settings", Toast.LENGTH_SHORT).show();
                }
            });
        }
        
        if (btnClearData != null) {
            btnClearData.setOnClickListener(v -> showClearDataDialog());
        }
        
        if (btnExportData != null) {
            btnExportData.setOnClickListener(v -> {
                if (RemoteMonitorServer.isRunning()) {
                    String url = RemoteMonitorServer.getServerUrl() + "/export";
                    Toast.makeText(requireContext(), "Open " + url + " on your laptop", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(requireContext(), "Enable remote server first", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
    
    private void updateStats(View root) {
        TextView tvDbStats = root.findViewById(R.id.tv_db_stats);
        if (tvDbStats == null) return;
        
        executor.execute(() -> {
            try {
                int snapshots = database.batterySnapshotDao().getCount();
                int sessions = database.chargingSessionDao().getCount();
                int thermal = database.thermalEventDao().getCount();
                int drain = database.drainEventDao().getCount();
                int idle = database.idleDrainSessionDao().getCount();
                int safety = database.safetyEventDao().getCount();
                int health = database.batteryHealthDao().getCount();
                
                String stats = String.format(
                    "📊 Database Statistics\n\n" +
                    "Snapshots: %,d\n" +
                    "Charging Sessions: %,d\n" +
                    "Thermal Events: %,d\n" +
                    "Drain Events: %,d\n" +
                    "Idle Sessions: %,d\n" +
                    "Safety Events: %,d\n" +
                    "Health Records: %,d",
                    snapshots, sessions, thermal, drain, idle, safety, health
                );
                
                handler.post(() -> tvDbStats.setText(stats));
            } catch (Exception e) {
                handler.post(() -> tvDbStats.setText("Error loading stats"));
            }
        });
    }
    
    private void showClearDataDialog() {
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle("Clear All Data")
            .setMessage("This will permanently delete all battery monitoring data. This action cannot be undone.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Clear", (dialog, which) -> {
                executor.execute(() -> {
                    try {
                        // Delete old data (keep last 24 hours)
                        long cutoff = System.currentTimeMillis() - (24 * 60 * 60 * 1000);
                        database.batterySnapshotDao().deleteOlderThan(cutoff);
                        
                        handler.post(() -> {
                            Toast.makeText(requireContext(), "Old data cleared", Toast.LENGTH_SHORT).show();
                            updateStats(getView());
                        });
                    } catch (Exception e) {
                        handler.post(() -> 
                            Toast.makeText(requireContext(), "Error clearing data", Toast.LENGTH_SHORT).show());
                    }
                });
            })
            .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (executor != null) {
            executor.shutdown();
        }
    }
}
