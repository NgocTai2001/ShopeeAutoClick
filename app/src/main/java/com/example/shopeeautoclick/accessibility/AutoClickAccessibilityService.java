package com.example.shopeeautoclick.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.Intent;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.example.shopeeautoclick.utils.AppLogger;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class AutoClickAccessibilityService extends AccessibilityService {
    private static final String TAG = "AutoClickAccessibility";
    private static final long CLICK_CALLBACK_TIMEOUT_SECONDS = 4L;
    private static final long CLICK_MARKER_DURATION_MILLIS = 500L;
    private static volatile AutoClickAccessibilityService instance;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private WindowManager windowManager;

    public static AutoClickAccessibilityService getInstance() {
        return instance;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        AppLogger.d(TAG, "Accessibility service connected");
    }

    @Override
    public boolean onUnbind(Intent intent) {
        instance = null;
        AppLogger.d(TAG, "Accessibility service unbound");
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        instance = null;
        AppLogger.d(TAG, "Accessibility service destroyed");
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Events are not needed for fixed-position test clicks.
    }

    @Override
    public void onInterrupt() {
        AppLogger.d(TAG, "Accessibility service interrupted");
    }

    public void performClickAt(int x, int y) {
        dispatchClickAt(x, y);
    }

    public boolean dispatchClickAt(int x, int y) {
        if (instance == null) {
            AppLogger.e(TAG, "Cannot click because accessibility service is not enabled", null);
            return false;
        }

        showClickMarker(x, y);

        Path clickPath = new Path();
        clickPath.moveTo(x, y);

        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(clickPath, 0, 120);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(stroke)
                .build();
        CountDownLatch callbackLatch = new CountDownLatch(1);
        AtomicBoolean completed = new AtomicBoolean(false);

        boolean dispatched = dispatchGesture(
                gesture,
                new GestureResultCallback() {
                    @Override
                    public void onCompleted(GestureDescription gestureDescription) {
                        super.onCompleted(gestureDescription);
                        completed.set(true);
                        AppLogger.d(TAG, "Click completed at x=" + x + ", y=" + y);
                        callbackLatch.countDown();
                    }

                    @Override
                    public void onCancelled(GestureDescription gestureDescription) {
                        super.onCancelled(gestureDescription);
                        AppLogger.e(TAG, "Click cancelled at x=" + x + ", y=" + y, null);
                        callbackLatch.countDown();
                    }
                },
                null
        );

        if (!dispatched) {
            AppLogger.e(TAG, "dispatchGesture returned false", null);
            return false;
        }

        try {
            boolean callbackArrived = callbackLatch.await(
                    CLICK_CALLBACK_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
            );
            if (!callbackArrived) {
                AppLogger.e(TAG, "Click callback timed out at x=" + x + ", y=" + y, null);
                return false;
            }
            return completed.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            AppLogger.e(TAG, "Click wait interrupted", e);
            return false;
        }
    }

    private void showClickMarker(int x, int y) {
        mainHandler.post(() -> {
            if (windowManager == null) {
                windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            }
            if (windowManager == null) {
                return;
            }

            int size = dpToPx(56);
            View marker = new View(this);
            marker.setAlpha(0.85f);
            marker.setClickable(false);
            marker.setFocusable(false);

            GradientDrawable background = new GradientDrawable();
            background.setShape(GradientDrawable.OVAL);
            background.setColor(0x33EE4D2D);
            background.setStroke(dpToPx(3), 0xFFEE4D2D);
            marker.setBackground(background);

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    size,
                    size,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
            );
            params.gravity = Gravity.TOP | Gravity.START;
            params.x = x - (size / 2);
            params.y = y - (size / 2);

            try {
                windowManager.addView(marker, params);
                mainHandler.postDelayed(
                        () -> removeClickMarker(marker),
                        CLICK_MARKER_DURATION_MILLIS
                );
            } catch (Exception e) {
                AppLogger.e(TAG, "Cannot show click marker", e);
            }
        });
    }

    private void removeClickMarker(View marker) {
        try {
            if (windowManager != null && marker.getWindowToken() != null) {
                windowManager.removeView(marker);
            }
        } catch (Exception e) {
            AppLogger.e(TAG, "Cannot remove click marker", e);
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    public String getActivePackageName() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            return "";
        }

        try {
            return root.getPackageName() == null ? "" : root.getPackageName().toString();
        } finally {
            root.recycle();
        }
    }

    public void goBack() {
        if (instance == null) {
            AppLogger.e(TAG, "Cannot go back because accessibility service is not enabled", null);
            return;
        }

        boolean success = performGlobalAction(GLOBAL_ACTION_BACK);
        AppLogger.d(TAG, "Global back action success=" + success);
    }

    public void returnToMainApp(Context context) {
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (intent == null) {
            AppLogger.e(TAG, "Cannot create launch intent for main app", null);
            return;
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(intent);
        AppLogger.d(TAG, "Returned to main app");
    }
}
