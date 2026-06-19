package com.example.shopeeautoclick.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.view.WindowMetrics;

import com.example.shopeeautoclick.data.remote.RetrofitClient;

public final class ScreenUtils {
    public static final int DEFAULT_CLICK_OFFSET_RIGHT = 48;
    public static final float DEFAULT_CLICK_Y_RATIO = 0.12f;

    private static final String KEY_CLICK_X = "click_x";
    private static final String KEY_CLICK_Y = "click_y";

    private ScreenUtils() {
    }

    public static Point getConfiguredClickPoint(Context context) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(RetrofitClient.PREFS_NAME, Context.MODE_PRIVATE);

        if (prefs.contains(KEY_CLICK_X) && prefs.contains(KEY_CLICK_Y)) {
            return new Point(prefs.getInt(KEY_CLICK_X, 0), prefs.getInt(KEY_CLICK_Y, 0));
        }

        Point screenSize = getScreenSize(context);
        int x = Math.max(0, screenSize.x - DEFAULT_CLICK_OFFSET_RIGHT);
        int y = Math.max(0, Math.round(screenSize.y * DEFAULT_CLICK_Y_RATIO));
        return new Point(x, y);
    }

    public static void saveClickPoint(Context context, int x, int y) {
        context.getApplicationContext()
                .getSharedPreferences(RetrofitClient.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_CLICK_X, x)
                .putInt(KEY_CLICK_Y, y)
                .apply();
    }

    public static Point getScreenSize(Context context) {
        WindowManager windowManager =
                (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowMetrics metrics = windowManager.getCurrentWindowMetrics();
            Rect bounds = metrics.getBounds();
            return new Point(bounds.width(), bounds.height());
        }

        DisplayMetrics displayMetrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(displayMetrics);
        return new Point(displayMetrics.widthPixels, displayMetrics.heightPixels);
    }
}
