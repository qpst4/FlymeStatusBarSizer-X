package com.example.flymestatusbarsizer.ui;

import com.example.flymestatusbarsizer.MainActivity;

import android.widget.LinearLayout;

public final class TelephonyDebugPageController {
    private TelephonyDebugPageController() {
    }

    public static void bind(MainActivity activity, LinearLayout root) {
        root.addView(activity.createTelephonyDebugSettingsCard(), PageViewUtils.matchWrap());
    }
}
