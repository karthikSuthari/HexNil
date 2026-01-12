package com.example.hexnil;

import androidx.annotation.NonNull;

/**
 * Simple navigation contract so fragments can ask the activity to open modules.
 */
public interface ModuleNavigator {
    void openModule(@NonNull String tag);
}
