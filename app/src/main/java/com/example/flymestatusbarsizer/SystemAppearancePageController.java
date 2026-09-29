package com.example.flymestatusbarsizer;

import android.widget.LinearLayout;

final class SystemAppearancePageController {
    private SystemAppearancePageController() {
    }

    static void bind(MainActivity activity, LinearLayout root) {
        LinearLayout organizer = new LinearLayout(activity);
        organizer.setOrientation(LinearLayout.VERTICAL);
        activity.addActionButtonRow(organizer, "AI 整理桌面",
                "按应用用途生成文件夹，支持预览和撤销。保留底栏、小组件及特殊快捷方式。",
                "打开", activity::showLauncherOrganizerPage);
        root.addView(activity.buildSectionCard("桌面整理", "", organizer), PageViewUtils.matchWrap());
        root.addView(activity.createSystemAppearanceSettingsCard(), PageViewUtils.matchWrap());
    }
}
