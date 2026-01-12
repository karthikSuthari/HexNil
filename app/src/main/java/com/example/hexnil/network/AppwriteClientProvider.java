package com.example.hexnil.network;

import android.content.Context;
import android.util.Log;

import com.example.hexnil.BuildConfig;

import java.lang.reflect.Method;

import io.appwrite.Client;

/**
 * Centralized Appwrite client provider. Uses BuildConfig values for endpoint/project/key.
 */
public final class AppwriteClientProvider {

    private static final String TAG = "AppwriteClientProvider";
    private static volatile Client client;

    private AppwriteClientProvider() {}

    public static Client get(Context context) {
        if (client == null) {
            synchronized (AppwriteClientProvider.class) {
                if (client == null) {
                    client = new Client(context);
                    try {
                        // Use reflection to avoid ambiguous method reference issue
                        Method setEndpoint = Client.class.getMethod("setEndpoint", String.class);
                        Method setProject = Client.class.getMethod("setProject", String.class);
                        setEndpoint.invoke(client, BuildConfig.APPWRITE_ENDPOINT);
                        setProject.invoke(client, BuildConfig.APPWRITE_PROJECT_ID);
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to configure Appwrite client", e);
                    }
                }
            }
        }
        return client;
    }
}
