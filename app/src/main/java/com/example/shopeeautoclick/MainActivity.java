package com.example.shopeeautoclick;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Point;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;

import com.example.shopeeautoclick.accessibility.AutoClickAccessibilityService;
import com.example.shopeeautoclick.service.TaskMonitorService;
import com.example.shopeeautoclick.utils.AppLogger;
import com.example.shopeeautoclick.utils.PermissionUtils;
import com.example.shopeeautoclick.utils.ScreenUtils;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";

    private TextView tvAccessibilityStatus;
    private TextView tvServiceStatus;
    private TextView tvCurrentLink;
    private TextView tvWaitTime;
    private TextView tvTaskStatus;
    private TextView tvLog;

    private boolean receiverRegistered;

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            applyServiceStatus(intent);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupButtons();
        updateAccessibilityStatus(true);
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerStatusReceiver();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateAccessibilityStatus(true);
    }

    @Override
    protected void onStop() {
        unregisterStatusReceiver();
        super.onStop();
    }

    private void initViews() {
        tvAccessibilityStatus = findViewById(R.id.tvAccessibilityStatus);
        tvServiceStatus = findViewById(R.id.tvServiceStatus);
        tvCurrentLink = findViewById(R.id.tvCurrentLink);
        tvWaitTime = findViewById(R.id.tvWaitTime);
        tvTaskStatus = findViewById(R.id.tvTaskStatus);
        tvLog = findViewById(R.id.tvLog);
    }

    private void setupButtons() {
        Button btnAccessibilitySettings = findViewById(R.id.btnAccessibilitySettings);
        Button btnStartService = findViewById(R.id.btnStartService);
        Button btnStopService = findViewById(R.id.btnStopService);
        Button btnTestClick = findViewById(R.id.btnTestClick);

        btnAccessibilitySettings.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });

        btnStartService.setOnClickListener(v -> startMonitorService());
        btnStopService.setOnClickListener(v -> stopMonitorService());
        btnTestClick.setOnClickListener(v -> testClick());
    }

    private void updateAccessibilityStatus(boolean autoStartService) {
        boolean enabled = PermissionUtils.isAccessibilityServiceEnabled(
                this,
                AutoClickAccessibilityService.class
        );

        tvAccessibilityStatus.setText(enabled ? "Access: on" : "Access: off");
        tvServiceStatus.setText(TaskMonitorService.isServiceRunning()
                ? "Service: on"
                : "Service: off");

        if (enabled && autoStartService && !TaskMonitorService.isServiceRunning()) {
            startMonitorService();
        }
    }

    private void startMonitorService() {
        if (!PermissionUtils.isAccessibilityServiceEnabled(this, AutoClickAccessibilityService.class)) {
            tvLog.setText("Log: Enable Accessibility first");
            return;
        }

        try {
            Intent intent = new Intent(this, TaskMonitorService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
            tvServiceStatus.setText("Service: starting");
            tvLog.setText("Log: Start requested");
        } catch (Exception e) {
            AppLogger.e(TAG, "Cannot start TaskMonitorService", e);
            tvLog.setText("Log: cannot start service: " + e.getMessage());
        }
    }

    private void stopMonitorService() {
        try {
            Intent intent = new Intent(this, TaskMonitorService.class);
            intent.setAction(TaskMonitorService.ACTION_STOP);
            startService(intent);
            tvServiceStatus.setText("Service: stopping");
            tvLog.setText("Log: Stop requested");
        } catch (Exception e) {
            AppLogger.e(TAG, "Cannot stop TaskMonitorService", e);
            tvLog.setText("Log: cannot stop service: " + e.getMessage());
        }
    }

    private void testClick() {
        AutoClickAccessibilityService service = AutoClickAccessibilityService.getInstance();
        if (service == null) {
            tvLog.setText("Log: Accessibility unavailable");
            return;
        }

        Point point = ScreenUtils.getConfiguredClickPoint(this);
        service.performClickAt(point.x, point.y);
        tvLog.setText("Log: test click requested at x=" + point.x + ", y=" + point.y);
    }

    private void registerStatusReceiver() {
        if (receiverRegistered) {
            return;
        }

        IntentFilter filter = new IntentFilter(TaskMonitorService.ACTION_STATUS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(statusReceiver, filter);
        }
        receiverRegistered = true;
    }

    private void unregisterStatusReceiver() {
        if (!receiverRegistered) {
            return;
        }

        try {
            unregisterReceiver(statusReceiver);
        } catch (IllegalArgumentException ignored) {
            AppLogger.d(TAG, "Status receiver was already unregistered");
        }
        receiverRegistered = false;
    }

    private void applyServiceStatus(Intent intent) {
        boolean running = intent.getBooleanExtra(
                TaskMonitorService.EXTRA_SERVICE_RUNNING,
                TaskMonitorService.isServiceRunning()
        );
        tvServiceStatus.setText(running ? "Service: on" : "Service: off");

        String currentLink = intent.getStringExtra(TaskMonitorService.EXTRA_CURRENT_LINK);
        if (currentLink != null) {
            tvCurrentLink.setText(currentLink.isEmpty()
                    ? "Link: -"
                    : "Link: " + currentLink);
        }

        int waitSeconds = intent.getIntExtra(TaskMonitorService.EXTRA_WAIT_SECONDS, -1);
        if (waitSeconds >= 0) {
            tvWaitTime.setText("Wait: " + waitSeconds + "s");
        }

        String taskStatus = intent.getStringExtra(TaskMonitorService.EXTRA_TASK_STATUS);
        if (taskStatus != null) {
            tvTaskStatus.setText("Task: " + taskStatus);
        }

        String log = intent.getStringExtra(TaskMonitorService.EXTRA_LOG);
        if (log != null) {
            tvLog.setText("Log: " + log);
        }
    }

}
