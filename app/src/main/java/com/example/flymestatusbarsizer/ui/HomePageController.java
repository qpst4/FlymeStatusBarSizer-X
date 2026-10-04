package com.example.flymestatusbarsizer.ui;

import com.example.flymestatusbarsizer.MainActivity;
import com.example.flymestatusbarsizer.R;

import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class HomePageController {
    private HomePageController() {
    }

    public static void bind(MainActivity activity, LinearLayout root) {
        TextView label = text(activity, "FLYME · 个性化工具", 11, activity.primaryColor());
        label.setLetterSpacing(0.12f);
        root.addView(label, PageViewUtils.matchWrapWithTop(activity, 8));
        TextView title = text(activity, "FlymeBarSizer", 30, activity.textColor());
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout titleRow = new LinearLayout(activity);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(title, new LinearLayout.LayoutParams(0,
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
        titleRow.addView(donate, donateLp);
        root.addView(titleRow, PageViewUtils.matchWrapWithTop(activity, 4));
        root.addView(text(activity, "让系统界面更合心意", 14, activity.subtextColor()),
                PageViewUtils.matchWrapWithTop(activity, 4));
        LinearLayout content = activity.addFeatureSearch(root);
        content.addView(buildRestartCard(activity), PageViewUtils.matchWrapWithTop(activity, 12));

        LinearLayout personal = addGroup(activity, content, "个性化");
        addEntry(activity, personal, "图标与电池", "图标大小 · 电池样式",
                R.drawable.ic_settings_battery, MainActivity.Page.ICONS_BATTERY);
        addEntry(activity, personal, "时间与网络", "时间样式 · 实时网速",
                R.drawable.ic_settings_clock, MainActivity.Page.TIME_NETWORK);
        addEntry(activity, personal, "系统外观", "桌面文件夹 · 外观细节",
                R.drawable.ic_settings_appearance, MainActivity.Page.SYSTEM_APPEARANCE);
        addEntry(activity, personal, "系统交互", "导航手势 · 输入法工具栏",
                R.drawable.ic_settings_interaction, MainActivity.Page.SYSTEM_INTERACTION);

        LinearLayout tools = addGroup(activity, content, "工具与支持");
        addEntry(activity, tools, "高级与调试", "配置管理 · 布局微调",
                R.drawable.ic_settings_tune, MainActivity.Page.ADVANCED_DEBUG);
        addEntry(activity, tools, "关于与支持", "版本信息 · 项目与交流",
                R.drawable.ic_settings_info, MainActivity.Page.ABOUT);
    }

    private static View buildRestartCard(MainActivity activity) {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        activity.addActionButtonRow(content, "SystemUI",
                "修改状态栏、通知背景等设置后，重启系统界面。",
                "重启", activity::restartSystemUi);
        activity.addDivider(content);
        activity.addActionButtonRow(content, "系统桌面",
                "修改文件夹、后台布局或堆叠参数后，重启系统桌面。",
                "重启", activity::restartLauncher);
        activity.addDivider(content);
        activity.addActionButtonRow(content, "SystemUITools",
                "重启后小窗相关修改立即重新加载。",
                "重启", activity::restartSystemUiTools);
        activity.addDivider(content);
        activity.addActionButtonRow(content, "OneMind/PPS",
                "开关变更后重启 PPS，让进程重新加载模块。",
                "重启", activity::restartOneMindPps);
        View card = activity.buildSectionCard("应用重启", "设置修改后，在这里重启对应应用。", content);
        card.setBackground(activity.roundRect(activity.primaryContainerColor(), 24));
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
