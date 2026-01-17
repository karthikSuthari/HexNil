package com.example.hexnil.intelligence;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.hexnil.intelligence.database.AppIntelligenceDatabase;

import java.io.File;

/**
 * Controller for the App Intelligence module.
 * Provides methods to enable, disable, and purge the module safely.
 * 
 * ISOLATION GUARANTEE:
 * - This module operates on a completely separate database file
 * - Enabling/disabling has NO impact on the core HexNil database
 * - Purging only removes hexnil_app_intelligence.db
 */
public class AppIntelligenceController {

    private static final String TAG = "AppIntelligence";
    private static final String PREFS_NAME = "hexnil_prefs";
    private static final String PREF_MODULE_ENABLED = "app_intelligence_enabled";
    private static final String PREF_FIRST_ENABLED_AT = "app_intelligence_first_enabled";

    // =========================================================================
    // MODULE CONTROL
    // =========================================================================

    /**
     * Enable the App Intelligence module.
     * The database will be created on first access.
     * 
     * @param context Application context
     */
    public static void enable(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(PREF_MODULE_ENABLED, true);

        // Track first enable time
        if (!prefs.contains(PREF_FIRST_ENABLED_AT)) {
            editor.putLong(PREF_FIRST_ENABLED_AT, System.currentTimeMillis());
        }

        editor.apply();
        Log.i(TAG, "App Intelligence module enabled");
    }

    /**
     * Disable the App Intelligence module.
     * Closes the database but preserves data for future re-enable.
     * 
     * @param context Application context
     */
    public static void disable(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(PREF_MODULE_ENABLED, false).apply();

        // Close the database instance
        AppIntelligenceDatabase.destroyInstance();

        Log.i(TAG, "App Intelligence module disabled (data preserved)");
    }

    /**
     * Check if the module is currently enabled.
     * 
     * @param context Application context
     * @return true if enabled
     */
    public static boolean isEnabled(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(PREF_MODULE_ENABLED, true); // Enabled by default
    }

    /**
     * Check if the database is currently available and open.
     * 
     * @return true if database is available
     */
    public static boolean isAvailable() {
        return AppIntelligenceDatabase.isAvailable();
    }

    // =========================================================================
    // PURGE / CLEANUP
    // =========================================================================

    /**
     * Completely remove the module and ALL associated data.
     * This operation is SAFE - it does NOT affect the core HexNil database.
     * 
     * @param context Application context
     * @return true if successfully purged
     */
    public static boolean purge(Context context) {
        // First disable and close the database
        disable(context);

        // Delete all database files
        boolean success = deleteDatabaseFiles(context);

        // Clear preferences
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .remove(PREF_MODULE_ENABLED)
                .remove(PREF_FIRST_ENABLED_AT)
                .apply();

        if (success) {
            Log.i(TAG, "App Intelligence module purged completely");
        } else {
            Log.w(TAG, "App Intelligence module purge completed with some issues");
        }

        return success;
    }

    /**
     * Delete all database files associated with this module.
     * 
     * @param context Application context
     * @return true if all files deleted successfully
     */
    private static boolean deleteDatabaseFiles(Context context) {
        String dbName = AppIntelligenceDatabase.getDatabaseName();
        File dbFile = context.getDatabasePath(dbName);
        File walFile = new File(dbFile.getPath() + "-wal");
        File shmFile = new File(dbFile.getPath() + "-shm");
        File journalFile = new File(dbFile.getPath() + "-journal");

        boolean success = true;

        if (dbFile.exists()) {
            success &= dbFile.delete();
            Log.d(TAG, "Deleted: " + dbFile.getName() + " = " + success);
        }
        if (walFile.exists()) {
            success &= walFile.delete();
            Log.d(TAG, "Deleted: " + walFile.getName());
        }
        if (shmFile.exists()) {
            success &= shmFile.delete();
            Log.d(TAG, "Deleted: " + shmFile.getName());
        }
        if (journalFile.exists()) {
            success &= journalFile.delete();
            Log.d(TAG, "Deleted: " + journalFile.getName());
        }

        return success;
    }

    // =========================================================================
    // STATUS / DIAGNOSTICS
    // =========================================================================

    /**
     * Get the database file size in bytes.
     * 
     * @param context Application context
     * @return Size in bytes, or -1 if not exists
     */
    public static long getDatabaseSizeBytes(Context context) {
        String dbName = AppIntelligenceDatabase.getDatabaseName();
        File dbFile = context.getDatabasePath(dbName);

        if (dbFile.exists()) {
            long size = dbFile.length();

            // Include WAL and SHM files
            File walFile = new File(dbFile.getPath() + "-wal");
            File shmFile = new File(dbFile.getPath() + "-shm");

            if (walFile.exists())
                size += walFile.length();
            if (shmFile.exists())
                size += shmFile.length();

            return size;
        }
        return -1;
    }

    /**
     * Check if the database file exists.
     * 
     * @param context Application context
     * @return true if database file exists
     */
    public static boolean databaseExists(Context context) {
        String dbName = AppIntelligenceDatabase.getDatabaseName();
        File dbFile = context.getDatabasePath(dbName);
        return dbFile.exists();
    }

    /**
     * Get when the module was first enabled.
     * 
     * @param context Application context
     * @return Timestamp when first enabled, or -1 if never
     */
    public static long getFirstEnabledAt(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getLong(PREF_FIRST_ENABLED_AT, -1);
    }

    /**
     * Get a summary of the module status.
     * 
     * @param context Application context
     * @return Status summary string
     */
    public static String getStatusSummary(Context context) {
        boolean enabled = isEnabled(context);
        boolean exists = databaseExists(context);
        long size = getDatabaseSizeBytes(context);
        long firstEnabled = getFirstEnabledAt(context);

        StringBuilder sb = new StringBuilder();
        sb.append("App Intelligence Module Status:\n");
        sb.append("  Enabled: ").append(enabled).append("\n");
        sb.append("  Database exists: ").append(exists).append("\n");

        if (size >= 0) {
            sb.append("  Database size: ").append(formatBytes(size)).append("\n");
        }

        if (firstEnabled > 0) {
            long daysSinceEnabled = (System.currentTimeMillis() - firstEnabled) / (24 * 60 * 60 * 1000);
            sb.append("  Active for: ").append(daysSinceEnabled).append(" days\n");
        }

        return sb.toString();
    }

    /**
     * Format bytes into human-readable format.
     */
    private static String formatBytes(long bytes) {
        if (bytes < 1024)
            return bytes + " B";
        if (bytes < 1024 * 1024)
            return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024)
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
