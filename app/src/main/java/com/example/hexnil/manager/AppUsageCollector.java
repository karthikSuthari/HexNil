package com.example.hexnil.manager;

import android.app.usage.NetworkStats;
import android.app.usage.NetworkStatsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.TrafficStats;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.text.format.DateUtils;
import android.util.Log;
import android.util.SparseArray;

import com.example.hexnil.model.AppBatteryUsage;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Comprehensive app usage collector for battery drain analysis.
 * Collects: foregroundTime, backgroundTime, network usage, CPU estimates,
 * wakelock estimates, alarm/wake counts, GPS requests, etc.
 */
public class AppUsageCollector {

    private static final String TAG = "AppUsageCollector";
    
    private final Context context;
    private final UsageStatsManager usageStatsManager;
    private final NetworkStatsManager networkStatsManager;
    private final PackageManager packageManager;
    
    // Cache for UID lookups
    private final Map<String, Integer> uidCache = new HashMap<>();
    
    // Track previous network bytes for delta calculation
    private final Map<String, Long> previousRxBytes = new HashMap<>();
    private final Map<String, Long> previousTxBytes = new HashMap<>();
    
    // Cache subscriber ID (null if unavailable, empty string means checked and failed)
    private String cachedSubscriberId = null;
    private boolean subscriberIdChecked = false;
    
    // Category averages for comparison (drain per hour)
    private static final Map<String, Float> CATEGORY_AVERAGES = new HashMap<>();
    static {
        CATEGORY_AVERAGES.put("game", 15.0f);
        CATEGORY_AVERAGES.put("social", 8.0f);
        CATEGORY_AVERAGES.put("video", 12.0f);
        CATEGORY_AVERAGES.put("music", 5.0f);
        CATEGORY_AVERAGES.put("productivity", 4.0f);
        CATEGORY_AVERAGES.put("utility", 3.0f);
        CATEGORY_AVERAGES.put("system", 2.0f);
        CATEGORY_AVERAGES.put("communication", 6.0f);
        CATEGORY_AVERAGES.put("navigation", 18.0f);
        CATEGORY_AVERAGES.put("browser", 7.0f);
        CATEGORY_AVERAGES.put("other", 5.0f);
    }

    public AppUsageCollector(Context context) {
        this.context = context.getApplicationContext();
        this.usageStatsManager = (UsageStatsManager) this.context.getSystemService(Context.USAGE_STATS_SERVICE);
        this.networkStatsManager = (NetworkStatsManager) this.context.getSystemService(Context.NETWORK_STATS_SERVICE);
        this.packageManager = this.context.getPackageManager();
    }

    /**
     * Collect comprehensive usage data for all apps with activity in the window.
     */
    public List<AppBatteryUsage> collect(long windowMs) {
        long now = System.currentTimeMillis();
        long start = now - (windowMs > 0 ? windowMs : 15 * DateUtils.MINUTE_IN_MILLIS);
        List<AppBatteryUsage> result = new ArrayList<>();
        
        if (usageStatsManager == null) {
            Log.w(TAG, "UsageStatsManager unavailable");
            return result;
        }

        List<UsageStats> stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, now);
        if (stats == null || stats.isEmpty()) {
            Log.d(TAG, "No usage stats available for period");
            return result;
        }

        for (UsageStats s : stats) {
            // Include apps with any activity
            if (s.getTotalTimeInForeground() <= 0 && s.getLastTimeUsed() < start) {
                continue;
            }
            
            String packageName = s.getPackageName();
            AppBatteryUsage usage = new AppBatteryUsage();
            usage.timestamp = now;
            usage.periodStart = start;
            usage.periodEnd = now;
            usage.packageName = packageName;
            
            // Group A: Identification & Timing
            populateIdentification(usage, packageName);
            
            // Group B: Base Consumption
            populateBaseConsumption(usage, s, windowMs);
            
            // Group C: Resource & Wakeup Metrics
            populateResourceMetrics(usage, packageName, start, now);
            
            // Group D: Background Behavior
            populateBackgroundBehavior(usage, s, packageName);
            
            // Group E: Efficiency Analysis
            populateEfficiencyAnalysis(usage);
            
            result.add(usage);
        }
        
        Log.d(TAG, "Collected usage data for " + result.size() + " apps");
        return result;
    }

    /**
     * Group A: Identification & Timing
     * appName, isSystemApp, isPreInstalled, appCategory
     */
    private void populateIdentification(AppBatteryUsage usage, String packageName) {
        try {
            ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
            usage.appName = packageManager.getApplicationLabel(appInfo).toString();
            usage.isSystemApp = (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            usage.isPreInstalled = (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0 ||
                                   appInfo.sourceDir.startsWith("/system");
            
            // Determine app category
            usage.appCategory = determineCategory(packageName, appInfo);
        } catch (PackageManager.NameNotFoundException e) {
            usage.appName = packageName;
            usage.isSystemApp = false;
            usage.isPreInstalled = false;
            usage.appCategory = "other";
        }
    }

    /**
     * Group B: Base Consumption
     * foregroundTime, backgroundTime, batteryPercentUsed, drainPerHour
     */
    private void populateBaseConsumption(AppBatteryUsage usage, UsageStats stats, long windowMs) {
        // Foreground time
        usage.foregroundTime = stats.getTotalTimeInForeground();
        
        // Background time (API 29+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // getTotalTimeVisible gives foreground, getLastTimeUsed - foreground time approximates background
            long totalVisible = stats.getTotalTimeVisible();
            long totalForeground = stats.getTotalTimeInForeground();
            // Estimate background as visible minus foreground (app was "active" but not in foreground)
            usage.backgroundTime = Math.max(0, totalVisible - totalForeground);
            
            // Also try to get foreground service time as additional background activity
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                long fgServiceTime = stats.getTotalTimeForegroundServiceUsed();
                // If foreground service time is higher, use that as background indicator
                if (fgServiceTime > usage.backgroundTime) {
                    usage.backgroundTime = fgServiceTime;
                }
            }
        } else {
            // Pre-API 29: estimate background based on last used vs foreground
            // If app was recently used but has little foreground time, it was likely in background
            long timeSinceLastUsed = System.currentTimeMillis() - stats.getLastTimeUsed();
            if (timeSinceLastUsed < windowMs && usage.foregroundTime < windowMs * 0.1) {
                usage.backgroundTime = windowMs - usage.foregroundTime;
            } else {
                usage.backgroundTime = 0;
            }
        }
        
        // Calculate battery consumption
        float hoursActive = (usage.foregroundTime + usage.backgroundTime) / 3600000.0f;
        
        // Estimate drain rates based on app type
        float foregroundDrainRate = 10.0f; // %/hour typical foreground
        float backgroundDrainRate = 1.5f;  // %/hour typical background
        
        float foregroundHours = usage.foregroundTime / 3600000.0f;
        float backgroundHours = usage.backgroundTime / 3600000.0f;
        
        usage.batteryPercentUsed = (foregroundHours * foregroundDrainRate) + 
                                   (backgroundHours * backgroundDrainRate);
        
        // Drain per hour of active use
        usage.drainPerHour = hoursActive > 0 ? usage.batteryPercentUsed / hoursActive : 0;
    }

    /**
     * Group C: Resource & Wakeup Metrics
     * cpuTimeMs, networkBytes, wakelockTime, partialWakelockTime, wakelockCount,
     * alarmWakeCount, networkWakeCount, wakelockDrainScore, alarmCount, gpsRequests
     */
    private void populateResourceMetrics(AppBatteryUsage usage, String packageName, long start, long end) {
        int uid = getUidForPackage(packageName);
        
        // === Network bytes ===
        usage.networkBytes = getNetworkBytesForUid(uid, packageName, start, end);
        
        // === CPU time estimate ===
        // Android doesn't expose per-app CPU time directly to third-party apps
        // We estimate based on foreground + background activity
        float foregroundCpuFactor = 0.3f; // 30% of foreground time is CPU active
        float backgroundCpuFactor = 0.1f; // 10% of background time
        usage.cpuTimeMs = (usage.foregroundTime * foregroundCpuFactor) + 
                          (usage.backgroundTime * backgroundCpuFactor);
        
        // === Wakelock estimates ===
        // Without BATTERY_STATS system permission, we estimate wakelocks from background activity
        // Heavy background apps likely hold wakelocks
        if (usage.backgroundTime > 60000) { // More than 1 minute background
            // Estimate partial wakelock time as 80% of background time
            usage.partialWakelockTime = (long)(usage.backgroundTime * 0.8);
            usage.wakelockTime = usage.partialWakelockTime; // Most wakelocks are partial
            
            // Estimate wakelock count (one per minute of background activity)
            usage.wakelockCount = Math.max(1, (int)(usage.backgroundTime / 60000));
        } else {
            usage.partialWakelockTime = 0;
            usage.wakelockTime = 0;
            usage.wakelockCount = 0;
        }
        
        // Wakelock drain score (0-100)
        // High background time + network activity = higher drain
        float wakelockMinutes = usage.wakelockTime / 60000.0f;
        float networkMB = usage.networkBytes / (1024.0f * 1024.0f);
        usage.wakelockDrainScore = Math.min(100, wakelockMinutes * 0.5f + networkMB * 2.0f);
        
        // === Alarm and wake counts ===
        // Estimate from background wakes - apps with frequent background activity likely use alarms
        if (usage.backgroundTime > 300000) { // More than 5 minutes background
            // Estimate ~1 alarm per 5 minutes of significant background activity
            usage.alarmWakeCount = Math.max(1, (int)(usage.backgroundTime / 300000));
            usage.alarmCount = usage.alarmWakeCount * 2; // Usually more scheduled than triggered
        } else {
            usage.alarmWakeCount = 0;
            usage.alarmCount = 0;
        }
        
        // Network wake count - estimate from network activity
        if (usage.networkBytes > 100 * 1024) { // More than 100KB
            usage.networkWakeCount = Math.max(1, (int)(usage.networkBytes / (500 * 1024))); // 1 wake per 500KB
        } else {
            usage.networkWakeCount = 0;
        }
        
        // === GPS requests ===
        // Estimate GPS usage for navigation/maps apps
        String category = usage.appCategory != null ? usage.appCategory : "other";
        if ("navigation".equals(category) || packageName.contains("maps") || 
            packageName.contains("gps") || packageName.contains("location")) {
            // Navigation apps with background time are actively using GPS
            usage.gpsRequests = (int)(usage.backgroundTime / 1000); // ~1 request per second
        } else {
            // Other apps occasionally request GPS
            usage.gpsRequests = usage.backgroundTime > 60000 ? 
                Math.max(1, (int)(usage.backgroundTime / 60000)) : 0;
        }
    }

    /**
     * Group D: Background Behavior
     * backgroundWakes, pushNotifications, backgroundServices, hasExcessiveBackground
     */
    private void populateBackgroundBehavior(AppBatteryUsage usage, UsageStats stats, String packageName) {
        // Background wakes - estimate from background time patterns
        if (usage.backgroundTime > 0) {
            // Assume app wakes approximately once per minute of background time
            usage.backgroundWakes = Math.max(1, (int)(usage.backgroundTime / 60000));
        } else {
            usage.backgroundWakes = 0;
        }
        
        // Push notifications - estimate from message/social apps
        String category = usage.appCategory != null ? usage.appCategory : "other";
        if ("social".equals(category) || "communication".equals(category) || 
            packageName.contains("message") || packageName.contains("chat") ||
            packageName.contains("mail") || packageName.contains("email")) {
            // Social/communication apps likely receive push notifications
            usage.pushNotifications = Math.max(1, usage.backgroundWakes / 2);
        } else {
            usage.pushNotifications = 0;
        }
        
        // Background services
        // Estimate based on app type and background activity
        if (usage.backgroundTime > 300000) { // More than 5 minutes background
            usage.backgroundServices = 1; // At least one background service
            if (usage.backgroundTime > 1800000) { // More than 30 minutes
                usage.backgroundServices = 2;
            }
        } else {
            usage.backgroundServices = 0;
        }
        
        // Excessive background flag
        // Flag if background time exceeds foreground by 3x or more than 30 minutes
        float bgFgRatio = usage.foregroundTime > 0 ? 
            (float)usage.backgroundTime / usage.foregroundTime : 
            (usage.backgroundTime > 0 ? Float.MAX_VALUE : 0);
        
        usage.hasExcessiveBackground = bgFgRatio > 3.0f || 
                                       (usage.backgroundTime > 1800000 && usage.foregroundTime < 60000);
    }

    /**
     * Group E: Efficiency Analysis
     * categoryAverage, deviationFromAverage, efficiencyRating, drainReasons, 
     * optimizationTips, needsRestriction
     */
    private void populateEfficiencyAnalysis(AppBatteryUsage usage) {
        // Category average
        String category = usage.appCategory != null ? usage.appCategory : "other";
        Float avg = CATEGORY_AVERAGES.get(category);
        usage.categoryAverage = avg != null ? avg : 5.0f;
        
        // Deviation from average
        usage.deviationFromAverage = usage.drainPerHour - usage.categoryAverage;
        
        // Efficiency rating based on drain rate relative to category
        boolean isProblematic = usage.drainPerHour > usage.categoryAverage * 2;
        boolean isInefficient = usage.drainPerHour > usage.categoryAverage * 1.3;
        boolean isNormal = usage.drainPerHour > usage.categoryAverage * 0.8;
        
        if (isProblematic) {
            usage.efficiencyRating = "problematic";
        } else if (isInefficient) {
            usage.efficiencyRating = "inefficient";
        } else if (isNormal) {
            usage.efficiencyRating = "normal";
        } else {
            usage.efficiencyRating = "efficient";
        }
        
        // Drain reasons - JSON array
        // Include both absolute thresholds AND relative efficiency issues
        JSONArray reasons = new JSONArray();
        
        // Absolute metric-based reasons
        if (usage.foregroundTime > 1800000) { // 30+ min foreground
            reasons.put("high_screen_time");
        }
        if (usage.backgroundTime > 600000) { // 10+ min background
            reasons.put("background_activity");
        }
        if (usage.wakelockTime > 300000) { // 5+ min wakelock
            reasons.put("wakelocks");
        }
        if (usage.networkBytes > 50 * 1024 * 1024) { // 50+ MB
            reasons.put("heavy_network");
        }
        if (usage.gpsRequests > 60) { // More than 1 per minute
            reasons.put("gps_usage");
        }
        if (usage.hasExcessiveBackground) {
            reasons.put("excessive_background");
        }
        
        // Relative efficiency-based reasons (when rated problematic/inefficient but no absolute triggers)
        if (reasons.length() == 0) {
            if (isProblematic) {
                reasons.put("high_drain_rate");
                reasons.put("exceeds_category_average_2x");
            } else if (isInefficient) {
                reasons.put("above_average_drain");
            } else {
                reasons.put("normal_usage");
            }
        }
        usage.drainReasons = reasons.toString();
        
        // Optimization tips - must be consistent with efficiency rating
        JSONArray tips = new JSONArray();
        
        // Tips based on specific issues
        if (usage.hasExcessiveBackground) {
            tips.put("Enable battery restriction for this app");
        }
        if (usage.backgroundTime > usage.foregroundTime * 2) {
            tips.put("Check app for unnecessary background activity");
        }
        if (usage.networkBytes > 100 * 1024 * 1024) { // 100MB+
            tips.put("App uses heavy network - check sync settings");
        }
        if ("navigation".equals(category) && usage.backgroundTime > 300000) {
            tips.put("Close navigation when not in use");
        }
        if ("game".equals(category) && (isProblematic || isInefficient)) {
            tips.put("Reduce graphics quality to save battery");
        }
        if (usage.wakelockTime > 600000) { // 10+ min wakelock
            tips.put("App may be preventing device sleep");
        }
        
        // Generic tips based on efficiency rating when no specific tips
        if (tips.length() == 0) {
            if (isProblematic) {
                tips.put("Consider restricting battery usage for this app");
                tips.put("App drains battery faster than similar apps");
            } else if (isInefficient) {
                tips.put("Monitor this app for battery impact");
                tips.put("App uses more battery than average for its category");
            } else {
                tips.put("App is running efficiently");
            }
        }
        usage.optimizationTips = tips.toString();
        
        // Needs restriction recommendation
        usage.needsRestriction = isProblematic ||
                                  usage.hasExcessiveBackground ||
                                  usage.deviationFromAverage > 10;
    }

    /**
     * Determine app category from package name and app info
     */
    private String determineCategory(String packageName, ApplicationInfo appInfo) {
        // Check Android category (API 26+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            int category = appInfo.category;
            switch (category) {
                case ApplicationInfo.CATEGORY_GAME:
                    return "game";
                case ApplicationInfo.CATEGORY_AUDIO:
                case ApplicationInfo.CATEGORY_VIDEO:
                    return "video";
                case ApplicationInfo.CATEGORY_IMAGE:
                    return "media";
                case ApplicationInfo.CATEGORY_SOCIAL:
                    return "social";
                case ApplicationInfo.CATEGORY_NEWS:
                    return "news";
                case ApplicationInfo.CATEGORY_MAPS:
                    return "navigation";
                case ApplicationInfo.CATEGORY_PRODUCTIVITY:
                    return "productivity";
            }
        }
        
        // Infer from package name
        String pkg = packageName.toLowerCase();
        if (pkg.contains("game") || pkg.contains("play") || pkg.contains("clash") || 
            pkg.contains("candy") || pkg.contains("puzzle")) {
            return "game";
        }
        if (pkg.contains("youtube") || pkg.contains("netflix") || pkg.contains("video") ||
            pkg.contains("player") || pkg.contains("tiktok") || pkg.contains("instagram")) {
            return "video";
        }
        if (pkg.contains("music") || pkg.contains("spotify") || pkg.contains("audio")) {
            return "music";
        }
        if (pkg.contains("facebook") || pkg.contains("twitter") || pkg.contains("social") ||
            pkg.contains("snapchat") || pkg.contains("linkedin")) {
            return "social";
        }
        if (pkg.contains("whatsapp") || pkg.contains("telegram") || pkg.contains("messenger") ||
            pkg.contains("sms") || pkg.contains("message") || pkg.contains("chat")) {
            return "communication";
        }
        if (pkg.contains("maps") || pkg.contains("navigation") || pkg.contains("gps") ||
            pkg.contains("waze") || pkg.contains("uber") || pkg.contains("lyft")) {
            return "navigation";
        }
        if (pkg.contains("chrome") || pkg.contains("browser") || pkg.contains("firefox") ||
            pkg.contains("opera") || pkg.contains("edge")) {
            return "browser";
        }
        if (pkg.contains("office") || pkg.contains("docs") || pkg.contains("sheets") ||
            pkg.contains("drive") || pkg.contains("dropbox") || pkg.contains("note")) {
            return "productivity";
        }
        if (pkg.contains("google") || pkg.contains("android") || pkg.contains("samsung") ||
            pkg.contains("system") || pkg.contains("provider")) {
            return "system";
        }
        
        return "other";
    }

    /**
     * Get UID for a package
     */
    private int getUidForPackage(String packageName) {
        Integer cached = uidCache.get(packageName);
        if (cached != null) return cached;
        
        try {
            ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
            int uid = appInfo.uid;
            uidCache.put(packageName, uid);
            return uid;
        } catch (PackageManager.NameNotFoundException e) {
            return -1;
        }
    }

    /**
     * Get network bytes for a UID using TrafficStats and NetworkStatsManager
     */
    private long getNetworkBytesForUid(int uid, String packageName, long start, long end) {
        if (uid < 0) return 0;
        
        long totalBytes = 0;
        
        // Method 1: TrafficStats (cumulative, need delta calculation)
        long rxBytes = TrafficStats.getUidRxBytes(uid);
        long txBytes = TrafficStats.getUidTxBytes(uid);
        
        if (rxBytes != TrafficStats.UNSUPPORTED && txBytes != TrafficStats.UNSUPPORTED) {
            Long prevRx = previousRxBytes.get(packageName);
            Long prevTx = previousTxBytes.get(packageName);
            
            if (prevRx != null && prevTx != null) {
                // Calculate delta since last check
                long deltaRx = Math.max(0, rxBytes - prevRx);
                long deltaTx = Math.max(0, txBytes - prevTx);
                totalBytes = deltaRx + deltaTx;
            } else {
                // First time - can't calculate delta, use estimate based on app type
                // Assume recent activity based on window
                totalBytes = 0;
            }
            
            // Store for next delta
            previousRxBytes.put(packageName, rxBytes);
            previousTxBytes.put(packageName, txBytes);
        }
        
        // Method 2: NetworkStatsManager for more accurate historical data (API 23+)
        if (networkStatsManager != null && totalBytes == 0) {
            try {
                // Query WiFi stats
                NetworkStats wifiStats = networkStatsManager.queryDetailsForUid(
                    ConnectivityManager.TYPE_WIFI, null, start, end, uid);
                totalBytes += sumNetworkStatsBuckets(wifiStats);
                
                // Query mobile stats (requires carrier privileges on newer Android)
                try {
                    NetworkStats mobileStats = networkStatsManager.queryDetailsForUid(
                        ConnectivityManager.TYPE_MOBILE, getSubscriberId(), start, end, uid);
                    totalBytes += sumNetworkStatsBuckets(mobileStats);
                } catch (SecurityException se) {
                    // Mobile stats may require carrier privileges
                    Log.d(TAG, "Cannot access mobile network stats: " + se.getMessage());
                }
            } catch (Exception e) {
                Log.d(TAG, "NetworkStatsManager query failed: " + e.getMessage());
            }
        }
        
        return totalBytes;
    }

    /**
     * Sum network stats buckets
     */
    private long sumNetworkStatsBuckets(NetworkStats stats) {
        if (stats == null) return 0;
        
        long total = 0;
        NetworkStats.Bucket bucket = new NetworkStats.Bucket();
        while (stats.hasNextBucket()) {
            stats.getNextBucket(bucket);
            total += bucket.getRxBytes() + bucket.getTxBytes();
        }
        stats.close();
        return total;
    }

    /**
     * Get subscriber ID for mobile network stats (requires READ_PHONE_STATE)
     * Caches the result to avoid repeated SecurityException logging
     */
    private String getSubscriberId() {
        // Return cached result if already checked
        if (subscriberIdChecked) {
            return cachedSubscriberId;
        }
        
        subscriberIdChecked = true;
        try {
            TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm != null) {
                cachedSubscriberId = tm.getSubscriberId();
                if (cachedSubscriberId != null) {
                    Log.d(TAG, "Subscriber ID obtained successfully");
                }
                return cachedSubscriberId;
            }
        } catch (SecurityException e) {
            Log.d(TAG, "Cannot get subscriber ID (carrier privileges required): " + e.getMessage());
            cachedSubscriberId = null;
        }
        return null;
    }
}
