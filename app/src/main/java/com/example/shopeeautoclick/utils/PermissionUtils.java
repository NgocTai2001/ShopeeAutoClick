package com.example.shopeeautoclick.utils;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;

public final class PermissionUtils {
    private PermissionUtils() {
    }

    public static boolean isAccessibilityServiceEnabled(
            Context context,
            Class<? extends AccessibilityService> serviceClass
    ) {
        int accessibilityEnabled = 0;
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                    context.getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED
            );
        } catch (Settings.SettingNotFoundException ignored) {
            return false;
        }

        if (accessibilityEnabled != 1) {
            return false;
        }

        String enabledServices = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        if (enabledServices == null) {
            return false;
        }

        ComponentName expectedComponent = new ComponentName(context, serviceClass);
        TextUtils.SimpleStringSplitter splitter =
                new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabledServices);

        while (splitter.hasNext()) {
            ComponentName enabledComponent = ComponentName.unflattenFromString(splitter.next());
            if (expectedComponent.equals(enabledComponent)) {
                return true;
            }
        }
        return false;
    }
}
