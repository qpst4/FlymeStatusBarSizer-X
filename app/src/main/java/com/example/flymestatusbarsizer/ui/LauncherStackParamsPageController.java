package com.example.flymestatusbarsizer.ui;

import com.example.flymestatusbarsizer.MainActivity;

import android.widget.LinearLayout;

public final class LauncherStackParamsPageController {
    private LauncherStackParamsPageController() {
    }

    public static void bind(MainActivity activity, LinearLayout root) {
        root.addView(activity.createLauncherStackParamsSettingsCard(), PageViewUtils.matchWrap());
    }
}
