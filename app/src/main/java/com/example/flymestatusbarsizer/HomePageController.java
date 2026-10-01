package com.example.flymestatusbarsizer;

import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

final class HomePageController {
    private HomePageController() {
    }

    static void bind(MainActivity activity, LinearLayout root) {
        TextView label = text(activity, "FLYME · 个性化工具", 11, activity.primaryColor());
        label.setLetterSpacing(0.12f);
        root.addView(label, PageViewUtils.matchWrapWithTop(activity, 8));
        TextView title = text(activity, "FlymeBarSizer", 30, activity.textColor());
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        root.addView(title, PageViewUtils.matchWrapWithTop(activity, 4));
        root.addView(text(activity, "让系统界面更合心意", 14, activity.subtextColor()),
                PageViewUtils.matchWrapWithTop(activity, 4));
        root.addView(buildHeroCard(activity), PageViewUtils.matchWrapWithTop(activity, 12));

        LinearLayout personal = addGroup(activity, root, "个性化");
        addEntry(activity, personal, "图标与电池", "图标大小 · 电池样式",
                R.drawable.ic_settings_battery, MainActivity.Page.ICONS_BATTERY);
        addEntry(activity, personal, "时间与网络", "时间样式 · 实时网速",
                R.drawable.ic_settings_clock, MainActivity.Page.TIME_NETWORK);
        addEntry(activity, personal, "系统外观", "桌面文件夹 · 外观细节",
                R.drawable.ic_settings_appearance, MainActivity.Page.SYSTEM_APPEARANCE);
        addEntry(activity, personal, "系统交互", "导航手势 · 输入法工具栏",
                R.drawable.ic_settings_interaction, MainActivity.Page.SYSTEM_INTERACTION);

        LinearLayout tools = addGroup(activity, root, "工具与支持");
        addEntry(activity, tools, "高级与调试", "配置管理 · 布局微调",
                R.drawable.ic_settings_tune, MainActivity.Page.ADVANCED_DEBUG);
        addEntry(activity, tools, "关于与支持", "版本信息 · 项目与交流",
                R.drawable.ic_settings_info, MainActivity.Page.ABOUT);
    }

    private static View buildHeroCard(MainActivity activity) {
        LinearLayout card = activity.card(activity.primaryContainerColor(), 24);
        card.setPadding(activity.dp(16), activity.dp(12), activity.dp(16), activity.dp(12));
        TextView title = text(activity, "状态栏与系统界面", 18, activity.primaryDeepColor());
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        card.addView(title, PageViewUtils.matchWrap());
        card.addView(text(activity, "从图标到交互，按你的习惯调整", 13, activity.subtextColor()),
                PageViewUtils.matchWrapWithTop(activity, 6));

        LinearLayout actions = new LinearLayout(activity);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        TextView restart = activity.filledButton("重启 SystemUI", activity.primaryColor(), Color.WHITE);
        activity.setTapClickListener(restart, v -> activity.restartSystemUi());
        actions.addView(restart, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        ImageView donate = new ImageView(activity);
        donate.setImageResource(R.drawable.ic_donate_copy);
        donate.setColorFilter(activity.primaryColor());
        donate.setContentDescription("捐赠");
        donate.setPadding(activity.dp(8), activity.dp(8), activity.dp(8), activity.dp(8));
        donate.setBackground(activity.roundRect(activity.surfaceColor(), 24));
        activity.setTapClickListener(donate, v -> activity.openPage(MainActivity.Page.DONATION));
        LinearLayout.LayoutParams donateLp = new LinearLayout.LayoutParams(activity.dp(40), activity.dp(40));
        donateLp.leftMargin = activity.dp(12);
        actions.addView(donate, donateLp);
        card.addView(actions, PageViewUtils.matchWrapWithTop(activity, 10));
        return card;
    }

    private static LinearLayout addGroup(MainActivity activity, LinearLayout root, String title) {
        TextView heading = text(activity, title, 13, activity.subtextColor());
        heading.setPadding(activity.dp(4), 0, 0, 0);
        root.addView(heading, PageViewUtils.matchWrapWithTop(activity, 16));
        LinearLayout group = activity.card(activity.surfaceColor(), 24);
        group.setPadding(0, 0, 0, 0);
        group.setClipToOutline(true);
        root.addView(group, PageViewUtils.matchWrapWithTop(activity, 6));
        return group;
    }

    private static void addEntry(MainActivity activity, LinearLayout group, String title,
            String summary, int iconRes, MainActivity.Page page) {
        if (group.getChildCount() > 0) {
            View divider = new View(activity);
            divider.setBackgroundColor(activity.surfaceSoftColor());
            LinearLayout.LayoutParams line = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, activity.dp(1));
            line.leftMargin = activity.dp(72);
            line.rightMargin = activity.dp(16);
            group.addView(divider, line);
        }
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(activity.dp(64));
        row.setPadding(activity.dp(16), activity.dp(8), activity.dp(16), activity.dp(8));
        activity.setTapClickListener(row, v -> activity.openPage(page));

        ImageView icon = new ImageView(activity);
        icon.setImageResource(iconRes);
        icon.setColorFilter(activity.primaryColor());
        icon.setPadding(activity.dp(10), activity.dp(10), activity.dp(10), activity.dp(10));
        icon.setBackground(activity.roundRect(activity.surfaceSoftColor(), 16));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(icon, new LinearLayout.LayoutParams(activity.dp(40), activity.dp(40)));

        LinearLayout texts = new LinearLayout(activity);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView name = text(activity, title, 16, activity.textColor());
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        texts.addView(name, PageViewUtils.matchWrap());
        texts.addView(text(activity, summary, 12, activity.subtextColor()),
                PageViewUtils.matchWrapWithTop(activity, 4));
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textLp.leftMargin = activity.dp(16);
        textLp.rightMargin = activity.dp(8);
        row.addView(texts, textLp);

        TextView arrow = text(activity, "›", 22, activity.subtextColor());
        arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(arrow, PageViewUtils.wrapWrap());
        group.addView(row, PageViewUtils.matchWrap());
    }

    private static TextView text(MainActivity activity, String value, int size, int color) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }
}
