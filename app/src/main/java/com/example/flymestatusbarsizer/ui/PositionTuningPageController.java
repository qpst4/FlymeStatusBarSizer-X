package com.example.flymestatusbarsizer.ui;

import com.example.flymestatusbarsizer.MainActivity;

import android.widget.LinearLayout;

public final class PositionTuningPageController {
    private PositionTuningPageController() {
    }

    public static void bind(MainActivity activity, LinearLayout root) {
        root.addView(activity.createPositionTuningSettingsCard(), PageViewUtils.matchWrap());
    }
}
