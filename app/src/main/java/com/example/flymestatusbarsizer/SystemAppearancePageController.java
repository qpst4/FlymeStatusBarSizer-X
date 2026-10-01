package com.example.flymestatusbarsizer;

import android.widget.LinearLayout;

final class SystemAppearancePageController {
    private SystemAppearancePageController() {
    }

    static void bind(MainActivity activity, LinearLayout root) {
        LinearLayout tint = new LinearLayout(activity);
        tint.setOrientation(LinearLayout.VERTICAL);
        String[] scenes = {"桌面", "最近任务", "下拉通知栏", "控制中心", "锁屏"};
        for (int i = 0; i < scenes.length; i++) {
            if (i > 0) activity.addDivider(tint);
            activity.addChoiceRow(tint, scenes[i], "", SettingsStore.STATUS_BAR_TINT_KEYS[i],
                    0, new int[]{0, 1, 2}, new String[]{"跟随系统", "固定黑色", "固定白色"});
        }
        root.addView(activity.buildSectionCard("状态栏图标颜色",
                "各界面独立设置，其他界面保持系统原生变色。", tint), PageViewUtils.matchWrap());
        LinearLayout organizer = new LinearLayout(activity);
        organizer.setOrientation(LinearLayout.VERTICAL);
        activity.addActionButtonRow(organizer, "AI 整理桌面",
                "按应用用途生成文件夹，支持预览和撤销。保留底栏、小组件及特殊快捷方式。",
                "打开", activity::showLauncherOrganizerPage);
        root.addView(activity.buildSectionCard("桌面整理", "", organizer), PageViewUtils.matchWrap());
        root.addView(activity.createSystemAppearanceSettingsCard(), PageViewUtils.matchWrapWithTop(activity, 8));
    }
}
