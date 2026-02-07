package com.example.hexnil.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.example.hexnil.service.UpdateImpactObserver;

/**
 * Receiver for MY_PACKAGE_REPLACED to detect seamless A/B OTA updates.
 *
 * Some OTA updates (especially Google Play System Updates and A/B partition
 * updates) do not require a full reboot. This receiver catches those cases
 * by detecting that our APK was replaced — which happens when the system
 * partition changes during an OTA.
 *
 * Also listens for BOOT_COMPLETED as a secondary trigger for update detection.
 */
public class UpdateReceiver extends BroadcastReceiver {

    private static final String TAG = "UpdateReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;

        String action = intent.getAction();

        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            Log.i(TAG, "Package replaced — checking for system update");
            try {
                UpdateImpactObserver.getInstance(context).checkForUpdateProactively();
            } catch (Exception e) {
                Log.e(TAG, "Error in MY_PACKAGE_REPLACED handler", e);
            }
        }
    }
}
