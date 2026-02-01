package com.example.hexnil.util;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraManager;
import android.location.LocationManager;
import android.os.Build;

import java.util.HashSet;
import java.util.Set;

/**
 * Utility class to detect specific activity types from foreground apps.
 * 
 * Detects:
 * - Gaming activity (games, game-like apps)
 * - Camera activity (camera apps, video recording)
 * - Navigation activity (maps, GPS, navigation apps)
 * - Network activity intensity
 */
public class ActivityDetector {

    private static final String TAG = "ActivityDetector";

    // Known game package patterns
    private static final Set<String> GAME_PATTERNS = new HashSet<>();
    private static final Set<String> CAMERA_PATTERNS = new HashSet<>();
    private static final Set<String> NAVIGATION_PATTERNS = new HashSet<>();
    private static final Set<String> KNOWN_GAMES = new HashSet<>();

    static {
        // Game package patterns
        GAME_PATTERNS.add("game");
        GAME_PATTERNS.add("games");
        GAME_PATTERNS.add("unity");
        GAME_PATTERNS.add("unreal");
        GAME_PATTERNS.add("cocos");
        GAME_PATTERNS.add("tencent");
        GAME_PATTERNS.add("mihoyo");
        GAME_PATTERNS.add("pubg");
        GAME_PATTERNS.add("roblox");
        GAME_PATTERNS.add("supercell");
        GAME_PATTERNS.add("gameloft");
        GAME_PATTERNS.add("ea.game");
        GAME_PATTERNS.add("com.king");
        GAME_PATTERNS.add("rovio");
        GAME_PATTERNS.add("zynga");
        GAME_PATTERNS.add("kabam");
        GAME_PATTERNS.add("clash");
        GAME_PATTERNS.add("candy");
        GAME_PATTERNS.add("puzzle");
        GAME_PATTERNS.add("arcade");
        GAME_PATTERNS.add("racing");
        GAME_PATTERNS.add("shooter");
        GAME_PATTERNS.add("rpg");
        GAME_PATTERNS.add("moba");
        GAME_PATTERNS.add("minecraft");
        GAME_PATTERNS.add("fortnite");
        GAME_PATTERNS.add("callofduty");
        GAME_PATTERNS.add("asphalt");
        GAME_PATTERNS.add("genshin");
        GAME_PATTERNS.add("diablo");
        GAME_PATTERNS.add("pokemon");
        GAME_PATTERNS.add("freefire");
        GAME_PATTERNS.add("mobilelegends");
        GAME_PATTERNS.add("brawlstars");

        // Camera package patterns
        CAMERA_PATTERNS.add("camera");
        CAMERA_PATTERNS.add("cam");
        CAMERA_PATTERNS.add("gcam");
        CAMERA_PATTERNS.add("photo");
        CAMERA_PATTERNS.add("photography");
        CAMERA_PATTERNS.add("scanner");
        CAMERA_PATTERNS.add("qr");
        CAMERA_PATTERNS.add("barcode");
        CAMERA_PATTERNS.add("video.recorder");
        CAMERA_PATTERNS.add("snapchat");
        CAMERA_PATTERNS.add("instagram"); // Has camera features

        // Navigation package patterns
        NAVIGATION_PATTERNS.add("maps");
        NAVIGATION_PATTERNS.add("navigation");
        NAVIGATION_PATTERNS.add("navigator");
        NAVIGATION_PATTERNS.add("gps");
        NAVIGATION_PATTERNS.add("waze");
        NAVIGATION_PATTERNS.add("uber");
        NAVIGATION_PATTERNS.add("lyft");
        NAVIGATION_PATTERNS.add("grab");
        NAVIGATION_PATTERNS.add("bolt");
        NAVIGATION_PATTERNS.add("didi");
        NAVIGATION_PATTERNS.add("citymapper");
        NAVIGATION_PATTERNS.add("moovit");
        NAVIGATION_PATTERNS.add("tomtom");
        NAVIGATION_PATTERNS.add("sygic");
        NAVIGATION_PATTERNS.add("here.app");
        NAVIGATION_PATTERNS.add("osmand");
        NAVIGATION_PATTERNS.add("mapquest");
        NAVIGATION_PATTERNS.add("strava"); // GPS tracking
        NAVIGATION_PATTERNS.add("runkeeper");
        NAVIGATION_PATTERNS.add("nike.run");
        NAVIGATION_PATTERNS.add("delivery"); // Delivery apps use GPS

        // Specific known games (full package names)
        KNOWN_GAMES.add("com.supercell.clashofclans");
        KNOWN_GAMES.add("com.supercell.clashroyale");
        KNOWN_GAMES.add("com.supercell.brawlstars");
        KNOWN_GAMES.add("com.tencent.ig");
        KNOWN_GAMES.add("com.pubg.imobile");
        KNOWN_GAMES.add("com.pubg.krmobile");
        KNOWN_GAMES.add("com.activision.callofduty.shooter");
        KNOWN_GAMES.add("com.dts.freefireth");
        KNOWN_GAMES.add("com.garena.game.codm");
        KNOWN_GAMES.add("com.miHoYo.GenshinImpact");
        KNOWN_GAMES.add("com.roblox.client");
        KNOWN_GAMES.add("com.mojang.minecraftpe");
        KNOWN_GAMES.add("com.innersloth.spacemafia");
        KNOWN_GAMES.add("com.kiloo.subwaysurf");
        KNOWN_GAMES.add("com.imangi.templerun");
        KNOWN_GAMES.add("com.king.candycrushsaga");
        KNOWN_GAMES.add("com.halfbrick.fruitninjafree");
        KNOWN_GAMES.add("com.rovio.angrybirds");
        KNOWN_GAMES.add("com.ea.game.pvzfree_row");
        KNOWN_GAMES.add("com.mobile.legends");
    }

    private final Context context;
    private final PackageManager packageManager;
    private CameraManager cameraManager;
    private LocationManager locationManager;

    public ActivityDetector(Context context) {
        this.context = context;
        this.packageManager = context.getPackageManager();
        try {
            this.cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            this.locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        } catch (Exception e) {
            // Services may not be available
        }
    }

    /**
     * Check if the given package is a game.
     */
    public boolean isGamingApp(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return false;
        }

        String pkg = packageName.toLowerCase();

        // Check known games list first
        if (KNOWN_GAMES.contains(packageName)) {
            return true;
        }

        // Check pattern matches
        for (String pattern : GAME_PATTERNS) {
            if (pkg.contains(pattern)) {
                return true;
            }
        }

        // Check Android app category (API 26+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
                if (appInfo.category == ApplicationInfo.CATEGORY_GAME) {
                    return true;
                }
            } catch (PackageManager.NameNotFoundException e) {
                // Package not found
            }
        }

        return false;
    }

    /**
     * Check if the given package is a camera app.
     */
    public boolean isCameraApp(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return false;
        }

        String pkg = packageName.toLowerCase();

        for (String pattern : CAMERA_PATTERNS) {
            if (pkg.contains(pattern)) {
                return true;
            }
        }

        // Check Android app category (API 26+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
                if (appInfo.category == ApplicationInfo.CATEGORY_IMAGE) {
                    return true;
                }
            } catch (PackageManager.NameNotFoundException e) {
                // Package not found
            }
        }

        return false;
    }

    /**
     * Check if the given package is a navigation/GPS app.
     */
    public boolean isNavigationApp(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return false;
        }

        String pkg = packageName.toLowerCase();

        for (String pattern : NAVIGATION_PATTERNS) {
            if (pkg.contains(pattern)) {
                return true;
            }
        }

        // Check Android app category (API 26+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
                if (appInfo.category == ApplicationInfo.CATEGORY_MAPS) {
                    return true;
                }
            } catch (PackageManager.NameNotFoundException e) {
                // Package not found
            }
        }

        return false;
    }

    /**
     * Get the activity type for the given package.
     * 
     * @return "gaming", "camera", "navigation", or "general"
     */
    public String getActivityType(String packageName) {
        if (isGamingApp(packageName)) {
            return "gaming";
        }
        if (isCameraApp(packageName)) {
            return "camera";
        }
        if (isNavigationApp(packageName)) {
            return "navigation";
        }
        return "general";
    }

    /**
     * Detection result containing all activity flags.
     */
    public static class ActivityResult {
        public final boolean isGaming;
        public final boolean isCamera;
        public final boolean isNavigation;
        public final String activityType;

        public ActivityResult(boolean gaming, boolean camera, boolean navigation, String type) {
            this.isGaming = gaming;
            this.isCamera = camera;
            this.isNavigation = navigation;
            this.activityType = type;
        }
    }

    /**
     * Get comprehensive activity detection result for a package.
     */
    public ActivityResult detectActivity(String packageName) {
        boolean gaming = isGamingApp(packageName);
        boolean camera = isCameraApp(packageName);
        boolean navigation = isNavigationApp(packageName);
        
        String type = "general";
        if (gaming) type = "gaming";
        else if (camera) type = "camera";
        else if (navigation) type = "navigation";
        
        return new ActivityResult(gaming, camera, navigation, type);
    }

    /**
     * Check if GPS is currently active.
     */
    public boolean isGpsActive() {
        if (locationManager == null) {
            return false;
        }
        try {
            return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch (Exception e) {
            return false;
        }
    }
}
