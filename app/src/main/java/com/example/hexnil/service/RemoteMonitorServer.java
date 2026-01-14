package com.example.hexnil.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.hexnil.MainActivity;
import com.example.hexnil.R;
import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.model.AIAnalysisResult;
import com.example.hexnil.model.BatteryHealth;
import com.example.hexnil.model.BatterySnapshot;
import com.example.hexnil.model.ChargingSession;
import com.example.hexnil.model.DrainEvent;
import com.example.hexnil.model.IdleDrainSession;
import com.example.hexnil.model.SafetyEvent;
import com.example.hexnil.model.ThermalEvent;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Remote Monitor Server - OEM-Grade Remote Access
 * 
 * Provides HTTP API for remote inspection of battery data from a laptop.
 * Runs on port 8765 and exposes REST endpoints for all battery data.
 * 
 * ENDPOINTS:
 * - GET /                    - Server info and available endpoints
 * - GET /status              - Current service and battery status
 * - GET /snapshots           - Recent battery snapshots (default: last 100)
 * - GET /snapshots?limit=N   - Last N snapshots
 * - GET /charging            - Charging sessions
 * - GET /thermal             - Thermal events
 * - GET /drain               - Drain events
 * - GET /idle                - Idle drain sessions
 * - GET /safety              - Safety events
 * - GET /health              - Battery health records
 * - GET /ai                  - AI analysis results
 * - GET /live                - Live current battery state
 * - GET /export              - Full database export (JSON)
 * - GET /dashboard           - Interactive HTML dashboard
 * 
 * SECURITY:
 * - Only accessible on local WiFi network
 * - No authentication (for ease of use during testing)
 * - Production should add authentication layer
 */
public class RemoteMonitorServer extends Service {

    private static final String TAG = "RemoteMonitorServer";
    private static final String CHANNEL_ID = "hexnil_remote_server";
    private static final int NOTIFICATION_ID = 1002;
    private static final int DEFAULT_PORT = 8765;

    private ServerSocket serverSocket;
    private ExecutorService executor;
    private HexNilDatabase database;
    private Gson gson;
    private AtomicBoolean isRunning = new AtomicBoolean(false);
    private String serverIp = "unknown";
    private int serverPort = DEFAULT_PORT;

    // Static reference for status queries
    private static RemoteMonitorServer instance;
    private static long startTime;
    private static int requestCount;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        database = HexNilDatabase.getInstance(this);
        gson = new GsonBuilder()
                .setPrettyPrinting()
                .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
                .create();
        executor = Executors.newCachedThreadPool();
        createNotificationChannel();
        Log.d(TAG, "Remote Monitor Server created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Remote Monitor Server starting");
        
        startForeground(NOTIFICATION_ID, createNotification("Starting..."));
        
        if (!isRunning.get()) {
            executor.execute(this::startServer);
        }
        
        return START_STICKY;
    }

    private void startServer() {
        try {
            serverIp = getDeviceIpAddress();
            serverSocket = new ServerSocket(DEFAULT_PORT);
            serverPort = serverSocket.getLocalPort();
            isRunning.set(true);
            startTime = System.currentTimeMillis();
            requestCount = 0;
            
            Log.i(TAG, "Server started at http://" + serverIp + ":" + serverPort);
            updateNotification("Running at " + serverIp + ":" + serverPort);
            
            while (isRunning.get() && !serverSocket.isClosed()) {
                try {
                    Socket client = serverSocket.accept();
                    executor.execute(() -> handleRequest(client));
                } catch (IOException e) {
                    if (isRunning.get()) {
                        Log.e(TAG, "Error accepting connection", e);
                    }
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to start server", e);
            updateNotification("Failed to start: " + e.getMessage());
        }
    }

    private void handleRequest(Socket client) {
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
            
            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.isEmpty()) {
                client.close();
                return;
            }
            
            Log.d(TAG, "Request: " + requestLine);
            requestCount++;
            
            // Parse request
            String[] parts = requestLine.split(" ");
            String method = parts[0];
            String path = parts.length > 1 ? parts[1] : "/";
            
            // Skip headers
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                // Read headers but ignore them for now
            }
            
            // Route request
            String response;
            String contentType = "application/json";
            
            if (path.equals("/") || path.equals("/index.html")) {
                contentType = "text/html";
                response = generateIndexHtml();
            } else if (path.equals("/dashboard")) {
                contentType = "text/html";
                response = generateDashboardHtml();
            } else if (path.equals("/status")) {
                response = handleStatus();
            } else if (path.startsWith("/snapshots")) {
                int limit = parseLimit(path, 100);
                response = handleSnapshots(limit);
            } else if (path.equals("/charging")) {
                response = handleChargingSessions();
            } else if (path.equals("/thermal")) {
                response = handleThermalEvents();
            } else if (path.equals("/drain")) {
                response = handleDrainEvents();
            } else if (path.equals("/idle")) {
                response = handleIdleSessions();
            } else if (path.equals("/safety")) {
                response = handleSafetyEvents();
            } else if (path.equals("/health")) {
                response = handleBatteryHealth();
            } else if (path.equals("/ai")) {
                response = handleAiAnalysis();
            } else if (path.equals("/live")) {
                response = handleLiveStatus();
            } else if (path.equals("/export")) {
                response = handleFullExport();
            } else if (path.equals("/api/endpoints")) {
                response = handleEndpointList();
            } else {
                response = "{\"error\": \"Not found\", \"path\": \"" + path + "\"}";
            }
            
            sendResponse(client, 200, contentType, response);
            
        } catch (Exception e) {
            Log.e(TAG, "Error handling request", e);
            try {
                sendResponse(client, 500, "application/json", 
                        "{\"error\": \"" + e.getMessage() + "\"}");
            } catch (IOException ignored) {}
        } finally {
            try {
                client.close();
            } catch (IOException ignored) {}
        }
    }

    private void sendResponse(Socket client, int code, String contentType, String body) throws IOException {
        OutputStream out = client.getOutputStream();
        String status = code == 200 ? "OK" : "Error";
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        
        String headers = "HTTP/1.1 " + code + " " + status + "\r\n" +
                "Content-Type: " + contentType + "; charset=utf-8\r\n" +
                "Content-Length: " + bodyBytes.length + "\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n" +
                "\r\n";
        
        out.write(headers.getBytes(StandardCharsets.UTF_8));
        out.write(bodyBytes);
        out.flush();
    }

    private int parseLimit(String path, int defaultLimit) {
        try {
            if (path.contains("limit=")) {
                String[] parts = path.split("limit=");
                if (parts.length > 1) {
                    String numStr = parts[1].split("&")[0];
                    return Integer.parseInt(numStr);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse limit", e);
        }
        return defaultLimit;
    }

    // =========================================================================
    // API HANDLERS
    // =========================================================================

    private String handleStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("server", "HexNil Remote Monitor");
        status.put("version", "1.0");
        status.put("uptime_ms", System.currentTimeMillis() - startTime);
        status.put("request_count", requestCount);
        status.put("ip", serverIp);
        status.put("port", serverPort);
        status.put("timestamp", new Date().toString());
        
        // Database stats
        try {
            status.put("snapshot_count", database.batterySnapshotDao().getCount());
            status.put("charging_session_count", database.chargingSessionDao().getCount());
            status.put("thermal_event_count", database.thermalEventDao().getCount());
            status.put("drain_event_count", database.drainEventDao().getCount());
        } catch (Exception e) {
            status.put("db_error", e.getMessage());
        }
        
        return gson.toJson(status);
    }

    private String handleSnapshots(int limit) {
        try {
            List<BatterySnapshot> snapshots = database.batterySnapshotDao().getRecentSnapshots(limit);
            Map<String, Object> result = new HashMap<>();
            result.put("count", snapshots.size());
            result.put("limit", limit);
            result.put("snapshots", snapshots);
            return gson.toJson(result);
        } catch (Exception e) {
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    private String handleChargingSessions() {
        try {
            List<ChargingSession> sessions = database.chargingSessionDao().getAll();
            Map<String, Object> result = new HashMap<>();
            result.put("count", sessions.size());
            result.put("sessions", sessions);
            return gson.toJson(result);
        } catch (Exception e) {
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    private String handleThermalEvents() {
        try {
            List<ThermalEvent> events = database.thermalEventDao().getAll();
            Map<String, Object> result = new HashMap<>();
            result.put("count", events.size());
            result.put("events", events);
            return gson.toJson(result);
        } catch (Exception e) {
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    private String handleDrainEvents() {
        try {
            List<DrainEvent> events = database.drainEventDao().getAll();
            Map<String, Object> result = new HashMap<>();
            result.put("count", events.size());
            result.put("events", events);
            return gson.toJson(result);
        } catch (Exception e) {
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    private String handleIdleSessions() {
        try {
            List<IdleDrainSession> sessions = database.idleDrainSessionDao().getAll();
            Map<String, Object> result = new HashMap<>();
            result.put("count", sessions.size());
            result.put("sessions", sessions);
            return gson.toJson(result);
        } catch (Exception e) {
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    private String handleSafetyEvents() {
        try {
            List<SafetyEvent> events = database.safetyEventDao().getAll();
            Map<String, Object> result = new HashMap<>();
            result.put("count", events.size());
            result.put("events", events);
            return gson.toJson(result);
        } catch (Exception e) {
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    private String handleBatteryHealth() {
        try {
            List<BatteryHealth> records = database.batteryHealthDao().getAll();
            Map<String, Object> result = new HashMap<>();
            result.put("count", records.size());
            result.put("latest", database.batteryHealthDao().getLatest());
            result.put("records", records);
            return gson.toJson(result);
        } catch (Exception e) {
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    private String handleAiAnalysis() {
        try {
            List<AIAnalysisResult> results = database.miscDao().getAllAIAnalysisResults();
            AIAnalysisResult latest = database.miscDao().getLatestAIAnalysis();
            Map<String, Object> result = new HashMap<>();
            result.put("count", results != null ? results.size() : 0);
            result.put("latest", latest);
            result.put("results", results);
            return gson.toJson(result);
        } catch (Exception e) {
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    private String handleLiveStatus() {
        try {
            BatterySnapshot latest = database.batterySnapshotDao().getLatest();
            BatteryHealth health = database.batteryHealthDao().getLatest();
            
            Map<String, Object> live = new HashMap<>();
            live.put("timestamp", new Date().toString());
            
            if (latest != null) {
                live.put("level", latest.level);
                live.put("temperature", latest.temperature / 10.0);
                live.put("voltage", latest.voltage);
                live.put("current", latest.currentNow);
                live.put("isCharging", latest.isCharging);
                live.put("plugType", getPlugTypeName(latest.plugged));
                live.put("status", latest.status);
                live.put("snapshot_age_ms", System.currentTimeMillis() - latest.timestamp);
            }
            
            if (health != null) {
                live.put("health_percentage", health.healthPercentage);
                live.put("cycle_count", health.cycleCount);
            }
            
            return gson.toJson(live);
        } catch (Exception e) {
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    private String handleFullExport() {
        try {
            Map<String, Object> export = new HashMap<>();
            export.put("export_time", new Date().toString());
            export.put("device", Build.MODEL);
            export.put("android_version", Build.VERSION.RELEASE);
            
            // Export all tables
            export.put("snapshots", database.batterySnapshotDao().getRecentSnapshots(1000));
            export.put("charging_sessions", database.chargingSessionDao().getAll());
            export.put("thermal_events", database.thermalEventDao().getAll());
            export.put("drain_events", database.drainEventDao().getAll());
            export.put("idle_sessions", database.idleDrainSessionDao().getAll());
            export.put("safety_events", database.safetyEventDao().getAll());
            export.put("health_records", database.batteryHealthDao().getAll());
            export.put("ai_analysis", database.miscDao().getAllAIAnalysisResults());
            
            return gson.toJson(export);
        } catch (Exception e) {
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    private String handleEndpointList() {
        String[] endpoints = {
            "GET / - Server info and links",
            "GET /status - Server status and database stats",
            "GET /snapshots - Recent battery snapshots (add ?limit=N)",
            "GET /charging - All charging sessions",
            "GET /thermal - Thermal events",
            "GET /drain - Drain events",
            "GET /idle - Idle drain sessions",
            "GET /safety - Safety events",
            "GET /health - Battery health records",
            "GET /ai - AI analysis results",
            "GET /live - Current live battery state",
            "GET /export - Full database export",
            "GET /dashboard - Interactive HTML dashboard"
        };
        Map<String, Object> result = new HashMap<>();
        result.put("endpoints", endpoints);
        return gson.toJson(result);
    }

    // =========================================================================
    // HTML GENERATORS
    // =========================================================================

    private String generateIndexHtml() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <meta charset='UTF-8'>\n" +
                "    <meta name='viewport' content='width=device-width, initial-scale=1.0'>\n" +
                "    <title>HexNil Remote Monitor</title>\n" +
                "    <style>\n" +
                "        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; max-width: 800px; margin: 0 auto; padding: 20px; background: #1a1a2e; color: #eee; }\n" +
                "        h1 { color: #00d9ff; border-bottom: 2px solid #00d9ff; padding-bottom: 10px; }\n" +
                "        a { color: #00d9ff; text-decoration: none; }\n" +
                "        a:hover { text-decoration: underline; }\n" +
                "        .endpoint { background: #16213e; padding: 15px; margin: 10px 0; border-radius: 8px; border-left: 4px solid #00d9ff; }\n" +
                "        .endpoint h3 { margin: 0 0 5px 0; color: #00d9ff; }\n" +
                "        .endpoint p { margin: 0; color: #aaa; }\n" +
                "        .status { background: #0f3460; padding: 15px; border-radius: 8px; margin-bottom: 20px; }\n" +
                "        .status span { color: #00ff88; font-weight: bold; }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <h1>🔋 HexNil Remote Monitor</h1>\n" +
                "    <div class='status'>\n" +
                "        <p>Server: <span>Running</span> | IP: <span>" + serverIp + ":" + serverPort + "</span></p>\n" +
                "        <p>Time: " + sdf.format(new Date()) + "</p>\n" +
                "    </div>\n" +
                "    <h2>🎯 Quick Access</h2>\n" +
                "    <div class='endpoint'><h3><a href='/dashboard'>📊 Live Dashboard</a></h3><p>Interactive real-time battery monitoring dashboard</p></div>\n" +
                "    <div class='endpoint'><h3><a href='/live'>⚡ Live Status</a></h3><p>Current battery state (JSON)</p></div>\n" +
                "    <div class='endpoint'><h3><a href='/status'>📈 Server Status</a></h3><p>Server info and database statistics</p></div>\n" +
                "    <h2>📁 Data Endpoints</h2>\n" +
                "    <div class='endpoint'><h3><a href='/snapshots'>📸 Snapshots</a></h3><p>Battery snapshots (?limit=N for custom count)</p></div>\n" +
                "    <div class='endpoint'><h3><a href='/charging'>🔌 Charging</a></h3><p>Charging session history</p></div>\n" +
                "    <div class='endpoint'><h3><a href='/thermal'>🌡️ Thermal</a></h3><p>Thermal events and heat analysis</p></div>\n" +
                "    <div class='endpoint'><h3><a href='/drain'>📉 Drain</a></h3><p>Drain events and analysis</p></div>\n" +
                "    <div class='endpoint'><h3><a href='/idle'>😴 Idle</a></h3><p>Idle drain sessions</p></div>\n" +
                "    <div class='endpoint'><h3><a href='/safety'>⚠️ Safety</a></h3><p>Safety events and alerts</p></div>\n" +
                "    <div class='endpoint'><h3><a href='/health'>❤️ Health</a></h3><p>Battery health records</p></div>\n" +
                "    <div class='endpoint'><h3><a href='/ai'>🤖 AI Analysis</a></h3><p>AI analysis results</p></div>\n" +
                "    <div class='endpoint'><h3><a href='/export'>💾 Full Export</a></h3><p>Complete database export (JSON)</p></div>\n" +
                "</body>\n" +
                "</html>";
    }

    private String generateDashboardHtml() {
        return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <meta charset='UTF-8'>\n" +
                "    <meta name='viewport' content='width=device-width, initial-scale=1.0'>\n" +
                "    <title>HexNil Live Dashboard</title>\n" +
                "    <style>\n" +
                "        * { box-sizing: border-box; margin: 0; padding: 0; }\n" +
                "        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #0d1117; color: #c9d1d9; min-height: 100vh; }\n" +
                "        .container { max-width: 1400px; margin: 0 auto; padding: 20px; }\n" +
                "        header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 30px; border-bottom: 1px solid #30363d; padding-bottom: 20px; }\n" +
                "        h1 { color: #58a6ff; font-size: 24px; }\n" +
                "        .status-indicator { display: flex; align-items: center; gap: 8px; }\n" +
                "        .status-dot { width: 12px; height: 12px; border-radius: 50%; background: #3fb950; animation: pulse 2s infinite; }\n" +
                "        @keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.5; } }\n" +
                "        .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 20px; }\n" +
                "        .card { background: #161b22; border: 1px solid #30363d; border-radius: 12px; padding: 20px; }\n" +
                "        .card h2 { color: #58a6ff; font-size: 14px; text-transform: uppercase; letter-spacing: 1px; margin-bottom: 15px; }\n" +
                "        .big-number { font-size: 48px; font-weight: bold; color: #fff; }\n" +
                "        .unit { font-size: 18px; color: #8b949e; }\n" +
                "        .stat-row { display: flex; justify-content: space-between; padding: 10px 0; border-bottom: 1px solid #21262d; }\n" +
                "        .stat-row:last-child { border-bottom: none; }\n" +
                "        .stat-label { color: #8b949e; }\n" +
                "        .stat-value { color: #fff; font-weight: 500; }\n" +
                "        .charging { color: #3fb950; }\n" +
                "        .discharging { color: #f0883e; }\n" +
                "        .warning { color: #d29922; }\n" +
                "        .danger { color: #f85149; }\n" +
                "        .log-area { background: #0d1117; border: 1px solid #30363d; border-radius: 8px; padding: 15px; max-height: 300px; overflow-y: auto; font-family: monospace; font-size: 12px; }\n" +
                "        .log-entry { padding: 5px 0; border-bottom: 1px solid #21262d; }\n" +
                "        .refresh-btn { background: #238636; color: #fff; border: none; padding: 10px 20px; border-radius: 6px; cursor: pointer; font-weight: 500; }\n" +
                "        .refresh-btn:hover { background: #2ea043; }\n" +
                "        .auto-refresh { display: flex; align-items: center; gap: 10px; }\n" +
                "        #lastUpdate { color: #8b949e; font-size: 12px; }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class='container'>\n" +
                "        <header>\n" +
                "            <div>\n" +
                "                <h1>🔋 HexNil Live Dashboard</h1>\n" +
                "                <span id='lastUpdate'>Loading...</span>\n" +
                "            </div>\n" +
                "            <div class='auto-refresh'>\n" +
                "                <div class='status-indicator'>\n" +
                "                    <div class='status-dot'></div>\n" +
                "                    <span>Live</span>\n" +
                "                </div>\n" +
                "                <button class='refresh-btn' onclick='refreshData()'>⟳ Refresh</button>\n" +
                "            </div>\n" +
                "        </header>\n" +
                "        <div class='grid'>\n" +
                "            <div class='card'>\n" +
                "                <h2>⚡ Battery Level</h2>\n" +
                "                <div class='big-number'><span id='level'>--</span><span class='unit'>%</span></div>\n" +
                "                <div id='chargingStatus' class='stat-value'>--</div>\n" +
                "            </div>\n" +
                "            <div class='card'>\n" +
                "                <h2>🌡️ Temperature</h2>\n" +
                "                <div class='big-number'><span id='temp'>--</span><span class='unit'>°C</span></div>\n" +
                "            </div>\n" +
                "            <div class='card'>\n" +
                "                <h2>⚡ Voltage</h2>\n" +
                "                <div class='big-number'><span id='voltage'>--</span><span class='unit'>mV</span></div>\n" +
                "            </div>\n" +
                "            <div class='card'>\n" +
                "                <h2>📊 Current</h2>\n" +
                "                <div class='big-number'><span id='current'>--</span><span class='unit'>mA</span></div>\n" +
                "            </div>\n" +
                "            <div class='card'>\n" +
                "                <h2>❤️ Health</h2>\n" +
                "                <div class='big-number'><span id='health'>--</span><span class='unit'>%</span></div>\n" +
                "                <div class='stat-row'><span class='stat-label'>Cycles</span><span id='cycles' class='stat-value'>--</span></div>\n" +
                "            </div>\n" +
                "            <div class='card'>\n" +
                "                <h2>📈 Database Stats</h2>\n" +
                "                <div class='stat-row'><span class='stat-label'>Snapshots</span><span id='snapCount' class='stat-value'>--</span></div>\n" +
                "                <div class='stat-row'><span class='stat-label'>Charging Sessions</span><span id='chargeCount' class='stat-value'>--</span></div>\n" +
                "                <div class='stat-row'><span class='stat-label'>Thermal Events</span><span id='thermalCount' class='stat-value'>--</span></div>\n" +
                "                <div class='stat-row'><span class='stat-label'>Requests Served</span><span id='reqCount' class='stat-value'>--</span></div>\n" +
                "            </div>\n" +
                "        </div>\n" +
                "        <div class='card' style='margin-top: 20px;'>\n" +
                "            <h2>📋 Recent Snapshots</h2>\n" +
                "            <div id='logArea' class='log-area'>Loading...</div>\n" +
                "        </div>\n" +
                "    </div>\n" +
                "    <script>\n" +
                "        async function refreshData() {\n" +
                "            try {\n" +
                "                const [live, status, snaps] = await Promise.all([\n" +
                "                    fetch('/live').then(r => r.json()),\n" +
                "                    fetch('/status').then(r => r.json()),\n" +
                "                    fetch('/snapshots?limit=10').then(r => r.json())\n" +
                "                ]);\n" +
                "                \n" +
                "                document.getElementById('level').textContent = live.level ?? '--';\n" +
                "                document.getElementById('temp').textContent = live.temperature?.toFixed(1) ?? '--';\n" +
                "                document.getElementById('voltage').textContent = live.voltage ?? '--';\n" +
                "                document.getElementById('current').textContent = live.current ?? '--';\n" +
                "                document.getElementById('health').textContent = live.health_percentage?.toFixed(0) ?? '--';\n" +
                "                document.getElementById('cycles').textContent = live.cycle_count?.toFixed(0) ?? '--';\n" +
                "                \n" +
                "                const chargingEl = document.getElementById('chargingStatus');\n" +
                "                if (live.isCharging) {\n" +
                "                    chargingEl.textContent = '🔌 Charging (' + (live.plugType || 'AC') + ')';\n" +
                "                    chargingEl.className = 'stat-value charging';\n" +
                "                } else {\n" +
                "                    chargingEl.textContent = '📉 Discharging';\n" +
                "                    chargingEl.className = 'stat-value discharging';\n" +
                "                }\n" +
                "                \n" +
                "                document.getElementById('snapCount').textContent = status.snapshot_count ?? '--';\n" +
                "                document.getElementById('chargeCount').textContent = status.charging_session_count ?? '--';\n" +
                "                document.getElementById('thermalCount').textContent = status.thermal_event_count ?? '--';\n" +
                "                document.getElementById('reqCount').textContent = status.request_count ?? '--';\n" +
                "                \n" +
                "                if (snaps.snapshots) {\n" +
                "                    const logHtml = snaps.snapshots.map(s => {\n" +
                "                        const date = new Date(s.timestamp).toLocaleTimeString();\n" +
                "                        return `<div class='log-entry'>[${date}] Level: ${s.level}% | Temp: ${(s.temperature/10).toFixed(1)}°C | V: ${s.voltage}mV | ${s.isCharging ? '⚡' : '📉'}</div>`;\n" +
                "                    }).join('');\n" +
                "                    document.getElementById('logArea').innerHTML = logHtml || 'No data';\n" +
                "                }\n" +
                "                \n" +
                "                document.getElementById('lastUpdate').textContent = 'Updated: ' + new Date().toLocaleTimeString();\n" +
                "            } catch (e) {\n" +
                "                console.error('Refresh failed:', e);\n" +
                "            }\n" +
                "        }\n" +
                "        \n" +
                "        refreshData();\n" +
                "        setInterval(refreshData, 5000);\n" +
                "    </script>\n" +
                "</body>\n" +
                "</html>";
    }

    // =========================================================================
    // UTILITY METHODS
    // =========================================================================

    /**
     * Convert plugged int value to readable name.
     * Values from BatteryManager.EXTRA_PLUGGED
     */
    private String getPlugTypeName(int plugged) {
        switch (plugged) {
            case 0: return "Battery";
            case 1: return "AC";
            case 2: return "USB";
            case 4: return "Wireless";
            case 8: return "Dock";
            default: return "Unknown";
        }
    }

    private String getDeviceIpAddress() {
        try {
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                int ip = wifiInfo.getIpAddress();
                if (ip != 0) {
                    return String.format(Locale.US, "%d.%d.%d.%d",
                            (ip & 0xff), (ip >> 8 & 0xff), (ip >> 16 & 0xff), (ip >> 24 & 0xff));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to get WiFi IP", e);
        }
        return "localhost";
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Remote Monitor Server",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("HexNil remote monitoring server");
            channel.setShowBadge(false);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification(String status) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("HexNil Remote Server")
                .setContentText(status)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setSilent(true)
                .build();
    }

    private void updateNotification(String status) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, createNotification(status));
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Remote Monitor Server stopping");
        isRunning.set(false);
        
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error closing server socket", e);
        }
        
        if (executor != null) {
            executor.shutdownNow();
        }
        
        instance = null;
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // =========================================================================
    // STATIC HELPERS
    // =========================================================================

    public static void start(Context context) {
        Intent intent = new Intent(context, RemoteMonitorServer.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, RemoteMonitorServer.class));
    }

    public static boolean isRunning() {
        return instance != null && instance.isRunning.get();
    }

    public static String getServerUrl() {
        if (instance != null && instance.isRunning.get()) {
            return "http://" + instance.serverIp + ":" + instance.serverPort;
        }
        return null;
    }

    public static ServerInfo getServerInfo() {
        if (instance != null && instance.isRunning.get()) {
            return new ServerInfo(instance.serverIp, instance.serverPort, 
                    System.currentTimeMillis() - startTime, requestCount);
        }
        return null;
    }

    public static class ServerInfo {
        public final String ip;
        public final int port;
        public final long uptimeMs;
        public final int requestCount;

        public ServerInfo(String ip, int port, long uptimeMs, int requestCount) {
            this.ip = ip;
            this.port = port;
            this.uptimeMs = uptimeMs;
            this.requestCount = requestCount;
        }

        public String getUrl() {
            return "http://" + ip + ":" + port;
        }
    }
}
