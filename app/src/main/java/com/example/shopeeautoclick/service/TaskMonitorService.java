package com.example.shopeeautoclick.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Point;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.text.TextUtils;

import com.example.shopeeautoclick.MainActivity;
import com.example.shopeeautoclick.R;
import com.example.shopeeautoclick.accessibility.AutoClickAccessibilityService;
import com.example.shopeeautoclick.data.model.LiveTask;
import com.example.shopeeautoclick.data.repository.TaskRepository;
import com.example.shopeeautoclick.utils.AppLogger;
import com.example.shopeeautoclick.utils.ScreenUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class TaskMonitorService extends Service {
    public static final String ACTION_STOP = "com.example.shopeeautoclick.action.STOP_TASK_MONITOR";
    public static final String ACTION_STATUS = "com.example.shopeeautoclick.action.TASK_STATUS";
    public static final String EXTRA_SERVICE_RUNNING = "extra_service_running";
    public static final String EXTRA_CURRENT_LINK = "extra_current_link";
    public static final String EXTRA_WAIT_SECONDS = "extra_wait_seconds";
    public static final String EXTRA_TASK_STATUS = "extra_task_status";
    public static final String EXTRA_LOG = "extra_log";

    private static final String TAG = "TaskMonitorService";
    private static final String CHANNEL_ID = "auto_click_monitor_channel";
    private static final int NOTIFICATION_ID = 2001;
    private static final long POLL_DELAY_MILLIS = 5000L;
    private static volatile boolean serviceRunning;

    private final Object lock = new Object();
    private ExecutorService executorService;
    private Future<?> loopFuture;
    private TaskRepository repository;
    private volatile boolean isRunning;

    public static boolean isServiceRunning() {
        return serviceRunning;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        repository = new TaskRepository(this);
        executorService = Executors.newSingleThreadExecutor();
        createNotificationChannel();
        AppLogger.d(TAG, "TaskMonitorService created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopMonitor();
            stopSelf();
            return START_NOT_STICKY;
        }

        startAsForeground();
        startMonitorLoop();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopMonitor();
        if (executorService != null) {
            executorService.shutdownNow();
        }
        AppLogger.d(TAG, "TaskMonitorService destroyed");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startMonitorLoop() {
        synchronized (lock) {
            if (isRunning) {
                sendStatus(true, "", -1, "running", "Task monitor is already running");
                return;
            }

            isRunning = true;
            serviceRunning = true;
            sendStatus(true, "", -1, "running", "Task monitor started");
            loopFuture = executorService.submit(this::runLoop);
        }
    }

    private void stopMonitor() {
        synchronized (lock) {
            isRunning = false;
            serviceRunning = false;
            if (loopFuture != null) {
                loopFuture.cancel(true);
                loopFuture = null;
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        sendStatus(false, "", -1, "stopped", "Task monitor stopped");
    }

    private void runLoop() {
        while (isRunning && !Thread.currentThread().isInterrupted()) {
            try {
                sendStatus(true, "", -1, "polling", "Checking pending task");
                LiveTask task = repository.getPendingTask();

                if (task == null) {
                    sendStatus(true, "", -1, "idle", "No pending task");
                    sleepInterruptibly(POLL_DELAY_MILLIS);
                    continue;
                }

                if (!task.isPending()) {
                    sendStatus(true, task.getLiveUrl(), task.getWaitTimeSeconds(), task.getStatus(),
                            "Task is not pending");
                    sleepInterruptibly(POLL_DELAY_MILLIS);
                    continue;
                }

                processTask(task);
                sleepInterruptibly(POLL_DELAY_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                AppLogger.d(TAG, "Task loop interrupted");
            } catch (Exception e) {
                AppLogger.e(TAG, "Task loop error", e);
                sendStatus(true, "", -1, "error", e.getMessage());
                sleepQuietly(POLL_DELAY_MILLIS);
            }
        }

        serviceRunning = false;
        sendStatus(false, "", -1, "stopped", "Task loop stopped");
    }

    private void processTask(LiveTask task) {
        String taskId = task.getId();
        String liveUrl = task.getLiveUrl();
        int waitSeconds = Math.max(0, task.getWaitTimeSeconds());

        try {
            sendStatus(true, liveUrl, waitSeconds, "processing", "Marking task " + taskId + " as processing");
            repository.markProcessing(taskId);

            openShopeeLive(liveUrl);
            sendStatus(true, liveUrl, waitSeconds, "waiting", "Waiting " + waitSeconds + " seconds");
            sleepInterruptibly(waitSeconds * 1000L);

            AutoClickAccessibilityService accessibilityService =
                    AutoClickAccessibilityService.getInstance();
            if (accessibilityService == null) {
                throw new IllegalStateException("Accessibility service is not enabled");
            }

            Point clickPoint = ScreenUtils.getConfiguredClickPoint(this);
            String activePackage = accessibilityService.getActivePackageName();
            AppLogger.d(TAG, "Active package before click=" + activePackage);
            sendStatus(true, liveUrl, waitSeconds, "clicking",
                    "Clicking " + activePackage + " at x=" + clickPoint.x + ", y=" + clickPoint.y);

            boolean clickCompleted = accessibilityService.dispatchClickAt(clickPoint.x, clickPoint.y);
            if (!clickCompleted) {
                throw new IllegalStateException("Click gesture was not completed");
            }

            sendStatus(true, liveUrl, waitSeconds, "clicked",
                    "Clicked " + activePackage + " at x=" + clickPoint.x + ", y=" + clickPoint.y);
            sleepInterruptibly(2000L);
            accessibilityService.goBack();
            sleepInterruptibly(500L);
            accessibilityService.returnToMainApp(this);

            boolean doneMarked = markDoneWithRetry(taskId, task.getCollectedCoin());
            if (doneMarked) {
                sendStatus(true, liveUrl, waitSeconds, "done", "Task " + taskId + " marked done");
            } else {
                sendStatus(true, liveUrl, waitSeconds, "sync_error",
                        "Clicked, but cannot mark task done");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            markErrorSafely(taskId, "Task interrupted");
        } catch (Exception e) {
            AppLogger.e(TAG, "Task processing error", e);
            markErrorSafely(taskId, e.getMessage() == null ? "Unknown task error" : e.getMessage());
            sendStatus(true, liveUrl, waitSeconds, "error", e.getMessage());
        }
    }

    private boolean markDoneWithRetry(String taskId, int collectedCoin) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                repository.markDone(taskId, collectedCoin);
                return true;
            } catch (Exception error) {
                AppLogger.e(TAG, "Cannot mark task done, attempt " + attempt, error);
                sleepQuietly(2000L);
            }
        }
        return false;
    }

    private void openShopeeLive(String liveUrl) {
        String normalizedUrl = normalizeLiveUrl(liveUrl);
        if (TextUtils.isEmpty(normalizedUrl)) {
            throw new IllegalArgumentException("Live URL is empty");
        }

        Uri uri = Uri.parse(normalizedUrl);
        String scheme = uri.getScheme();
        if (scheme == null
                || (!"http".equalsIgnoreCase(scheme)
                && !"https".equalsIgnoreCase(scheme)
                && !"shopee".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("Live URL has an invalid scheme");
        }

        Intent shopeeIntent = buildViewIntent(uri);
        shopeeIntent.setPackage("com.shopee.vn");
        try {
            startActivity(shopeeIntent);
            AppLogger.d(TAG, "Opened live URL with Shopee package");
            return;
        } catch (ActivityNotFoundException e) {
            AppLogger.d(TAG, "Shopee app did not handle URL, falling back to generic ACTION_VIEW");
        }

        Intent genericIntent = buildViewIntent(uri);
        try {
            startActivity(genericIntent);
            AppLogger.d(TAG, "Opened live URL with generic ACTION_VIEW");
        } catch (Exception e) {
            throw new IllegalStateException("Cannot open live url", e);
        }
    }

    private Intent buildViewIntent(Uri uri) {
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    private String normalizeLiveUrl(String liveUrl) {
        if (liveUrl == null) {
            return "";
        }

        String value = liveUrl.trim();
        int markdownStart = value.indexOf("](");
        if (value.startsWith("[") && markdownStart > 0 && value.endsWith(")")) {
            value = value.substring(markdownStart + 2, value.length() - 1).trim();
        }
        return value;
    }

    private void markErrorSafely(String taskId, String message) {
        if (TextUtils.isEmpty(taskId)) {
            return;
        }

        try {
            repository.markError(taskId, message == null ? "Unknown error" : message);
        } catch (Exception error) {
            AppLogger.e(TAG, "Cannot mark task as error", error);
        }
    }

    private void startAsForeground() {
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            );
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private Notification buildNotification() {
        Intent launchIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                launchIntent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        ? PendingIntent.FLAG_IMMUTABLE
                        : 0
        );

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        return builder
                .setContentTitle("Shopee Live Auto Click Assistant")
                .setContentText("Auto Click Service is running")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Auto Click Monitor",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Task monitor foreground service");

        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.createNotificationChannel(channel);
    }

    private void sendStatus(
            boolean running,
            String currentLink,
            int waitSeconds,
            String taskStatus,
            String log
    ) {
        Intent intent = new Intent(ACTION_STATUS);
        intent.setPackage(getPackageName());
        intent.putExtra(EXTRA_SERVICE_RUNNING, running);
        intent.putExtra(EXTRA_CURRENT_LINK, currentLink == null ? "" : currentLink);
        intent.putExtra(EXTRA_WAIT_SECONDS, waitSeconds);
        intent.putExtra(EXTRA_TASK_STATUS, taskStatus == null ? "" : taskStatus);
        intent.putExtra(EXTRA_LOG, log == null ? "" : log);
        sendBroadcast(intent);
    }

    private void sleepInterruptibly(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
