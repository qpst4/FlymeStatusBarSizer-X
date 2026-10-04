package com.example.flymestatusbarsizer.ui;

import com.example.flymestatusbarsizer.MainActivity;

import android.widget.LinearLayout;

public final class IconsBatteryPageController {
    private IconsBatteryPageController() {
    }

    public static void bind(MainActivity activity, LinearLayout root) {
        root.addView(activity.createIconSizingCard(), PageViewUtils.matchWrap());
        root.addView(activity.createBatterySettingsCard(), PageViewUtils.matchWrapWithTop(activity, 8));
        root.addView(activity.createNotificationSettingsCard(), PageViewUtils.matchWrapWithTop(activity, 8));
        root.addView(activity.createSignalSettingsCard(), PageViewUtils.matchWrapWithTop(activity, 8));
    }
}
