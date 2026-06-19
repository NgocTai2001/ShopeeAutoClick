package com.example.shopeeautoclick.utils;

import android.util.Log;

public final class AppLogger {
    private AppLogger() {
    }

    public static void d(String tag, String message) {
        Log.d(tag, message);
    }

    public static void e(String tag, String message, Throwable throwable) {
        if (throwable == null) {
            Log.e(tag, message);
        } else {
            Log.e(tag, message, throwable);
        }
    }
}
