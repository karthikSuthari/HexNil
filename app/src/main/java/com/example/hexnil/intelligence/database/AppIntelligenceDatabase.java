package com.example.hexnil.intelligence.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.example.hexnil.intelligence.database.dao.AppAnomalyDao;
import com.example.hexnil.intelligence.database.dao.AppBatteryAttributionDao;
import com.example.hexnil.intelligence.database.dao.AppRegistryDao;
import com.example.hexnil.intelligence.database.dao.AppThermalImpactDao;
import com.example.hexnil.intelligence.database.dao.AppUsageSessionDao;
import com.example.hexnil.intelligence.database.entities.AppAnomaly;
import com.example.hexnil.intelligence.database.entities.AppBatteryAttribution;
import com.example.hexnil.intelligence.database.entities.AppRegistry;
import com.example.hexnil.intelligence.database.entities.AppThermalImpact;
import com.example.hexnil.intelligence.database.entities.AppUsageSession;

/**
 * Isolated App Intelligence Database for per-app battery usage tracking.
 * 
 * CRITICAL: This database is COMPLETELY ISOLATED from the core HexNil database.
 * - Separate database file: hexnil_app_intelligence.db
 * - No foreign keys to core tables
 * - Independent versioning
 * - Can be enabled/disabled at runtime
 * - Safe to delete without affecting core data
 * 
 * This module stores:
 * - Per-app battery attribution
 * - Usage context (foreground/background, network, charging state)
 * - Thermal impact per app
 * - App-attributed anomalies (independent from core anomaly_records)
 */
@Database(entities = {
        AppRegistry.class,
        AppUsageSession.class,
        AppBatteryAttribution.class,
        AppThermalImpact.class,
        AppAnomaly.class
}, version = 1, exportSchema = true)
public abstract class AppIntelligenceDatabase extends RoomDatabase {

    /**
     * Separate database file - guaranteed isolation from core HexNil DB.
     */
    private static final String DATABASE_NAME = "hexnil_app_intelligence.db";

    private static final String PREFS_NAME = "hexnil_prefs";
    private static final String PREF_MODULE_ENABLED = "app_intelligence_enabled";

    private static volatile AppIntelligenceDatabase INSTANCE;

    // =========================================================================
    // DAOs
    // =========================================================================

    public abstract AppRegistryDao appRegistryDao();

    public abstract AppUsageSessionDao appUsageSessionDao();

    public abstract AppBatteryAttributionDao appBatteryAttributionDao();

    public abstract AppThermalImpactDao appThermalImpactDao();

    public abstract AppAnomalyDao appAnomalyDao();

    // =========================================================================
    // SINGLETON ACCESS
    // =========================================================================

    /**
     * Get the database instance.
     * Returns null if module is disabled.
     * 
     * @param context Application context
     * @return Database instance or null if disabled
     */
    public static synchronized AppIntelligenceDatabase getInstance(Context context) {
        if (!isModuleEnabled(context)) {
            return null;
        }

        if (INSTANCE == null) {
            INSTANCE = Room.databaseBuilder(
                    context.getApplicationContext(),
                    AppIntelligenceDatabase.class,
                    DATABASE_NAME)
                    .fallbackToDestructiveMigration() // Safe for v1
                    .build();
        }
        return INSTANCE;
    }

    /**
     * Get the database instance, throwing if disabled.
     * Use when module must be available.
     * 
     * @param context Application context
     * @return Database instance
     * @throws IllegalStateException if module is disabled
     */
    public static synchronized AppIntelligenceDatabase getInstanceOrThrow(Context context) {
        AppIntelligenceDatabase db = getInstance(context);
        if (db == null) {
            throw new IllegalStateException("App Intelligence module is disabled");
        }
        return db;
    }

    /**
     * Check if the module is enabled via SharedPreferences.
     */
    public static boolean isModuleEnabled(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(PREF_MODULE_ENABLED, true); // Enabled by default
    }

    /**
     * Check if the database instance is available.
     */
    public static boolean isAvailable() {
        return INSTANCE != null && INSTANCE.isOpen();
    }

    /**
     * Safely close and destroy the database instance.
     * Called when module is disabled.
     */
    public static synchronized void destroyInstance() {
        if (INSTANCE != null) {
            if (INSTANCE.isOpen()) {
                INSTANCE.close();
            }
            INSTANCE = null;
        }
    }

    /**
     * Get the database file name.
     */
    public static String getDatabaseName() {
        return DATABASE_NAME;
    }

    // =========================================================================
    // CONVENIENCE METHODS
    // =========================================================================

    /**
     * Run a transaction safely, handling null DB case.
     * 
     * @param context Application context
     * @param action  Action to run in transaction
     * @return true if action was executed, false if module disabled
     */
    public static boolean runIfEnabled(Context context, Runnable action) {
        AppIntelligenceDatabase db = getInstance(context);
        if (db != null) {
            db.runInTransaction(action);
            return true;
        }
        return false;
    }
}
