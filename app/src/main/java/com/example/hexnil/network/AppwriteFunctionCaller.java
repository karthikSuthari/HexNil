package com.example.hexnil.network;

import android.util.Log;

import com.example.hexnil.BuildConfig;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Minimal helper to call an Appwrite Function without exposing API keys.
 * Uses Appwrite session/JWT if provided. Caller should run on a background thread.
 */
public final class AppwriteFunctionCaller {

    private static final String TAG = "AppwriteFn";

    private AppwriteFunctionCaller() {}

    public static String callFunction(String functionId, String jwt, String payloadJson) throws Exception {
        String endpoint = BuildConfig.APPWRITE_ENDPOINT; // e.g., https://sgp.cloud.appwrite.io/v1
        if (!endpoint.endsWith("/v1")) {
            endpoint = endpoint + "/v1";
        }
        URL url = new URL(endpoint + "/functions/" + functionId + "/executions");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-Appwrite-Project", BuildConfig.APPWRITE_PROJECT_ID);
        if (jwt != null && !jwt.isEmpty()) {
            conn.setRequestProperty("X-Appwrite-JWT", jwt);
        }

        try (OutputStream os = conn.getOutputStream();
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8))) {
            writer.write(payloadJson == null ? "{}" : payloadJson);
            writer.flush();
        }

        int code = conn.getResponseCode();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream(),
                StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        if (code >= 200 && code < 300) {
            return sb.toString();
        } else {
            Log.w(TAG, "Function call failed code=" + code + " body=" + sb);
            throw new IllegalStateException("Function call failed: " + code);
        }
    }
}
