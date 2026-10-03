package com.example.flymestatusbarsizer;

import android.graphics.Color;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;

/** Palette section reused by the battery state color dialogs. */
final class ColorPaletteView extends LinearLayout {
    private final MainActivity activity;
    private final ColorPaletteHistory history;
    private final IntConsumer onSelect;
    private final TextView pinCurrent;
    private final LinearLayout colors;
    private Integer currentColor;

    ColorPaletteView(MainActivity activity, IntConsumer onSelect) {
        super(activity);
        this.activity = activity;
        this.onSelect = onSelect;
        history = new ColorPaletteHistory(activity.prefs());
        setOrientation(VERTICAL);
        TextView help = label("点击色块选用，长按固定或取消固定");
        addView(help);
        pinCurrent = activity.filledButton("固定当前颜色", activity.surfaceSoftColor(),
                activity.primaryColor());
        activity.setTapClickListener(pinCurrent, v -> {
            if (currentColor != null) togglePin(currentColor);
        });
        addView(pinCurrent, new LayoutParams(-1, -2));
        colors = new LinearLayout(activity);
        colors.setOrientation(VERTICAL);
        addView(colors, new LayoutParams(-1, -2));
        refresh();
    }

    void setCurrentColor(Integer color) {
        currentColor = color;
        pinCurrent.setEnabled(color != null);
        pinCurrent.setAlpha(color == null ? 0.5f : 1f);
        pinCurrent.setText(color != null && history.isPinned(color)
                ? "取消固定当前颜色" : "固定当前颜色");
    }

    void recordApplied(int color) {
        history.recordApplied(color);
    }

    private void togglePin(int color) {
        history.togglePin(color);
        SettingsStore.notifyChanged(activity);
        refresh();
    }

    private void refresh() {
        colors.removeAllViews();
        if (!history.pinnedColors().isEmpty()) {
            addColors("已固定", history.pinnedColors(), true);
        }
        if (!history.recentColors().isEmpty()) {
            addColors("最近使用", history.recentColors(), false);
        } else if (history.pinnedColors().isEmpty()) {
            colors.addView(label("应用颜色后，会在这里保留最近 8 种颜色。"));
        }
        setCurrentColor(currentColor);
    }

    private void addColors(String title, List<Integer> palette, boolean pinned) {
        colors.addView(label(title));
        LinearLayout row = null;
        for (int i = 0; i < palette.size(); i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(activity);
                colors.addView(row, new LayoutParams(-1, -2));
            }
            int color = palette.get(i);
            String hex = String.format(Locale.US, "#%06X", color & 0xFFFFFF);
            TextView swatch = new TextView(activity);
            swatch.setText((pinned ? "★ " : "") + hex);
            swatch.setTextSize(13);
            swatch.setGravity(Gravity.CENTER);
            swatch.setMinHeight(activity.dp(44));
            swatch.setPadding(activity.dp(4), activity.dp(4), activity.dp(4), activity.dp(4));
            swatch.setBackground(activity.outlinedRect(color, activity.strokeColor(), 1, 10));
            double luminance = 0.299 * Color.red(color) + 0.587 * Color.green(color)
                    + 0.114 * Color.blue(color);
            swatch.setTextColor(luminance > 150 ? Color.BLACK : Color.WHITE);
            String action = pinned ? "取消固定" : "固定到顶部";
            swatch.setContentDescription(hex + "，点击选用，长按" + action);
            swatch.setTooltipText("长按" + action);
            activity.setTapClickListener(swatch, v -> onSelect.accept(color));
            swatch.setOnLongClickListener(v -> {
                togglePin(color);
                return true;
            });
            LayoutParams lp = new LayoutParams(0, -2, 1);
            lp.setMargins(0, activity.dp(4), activity.dp(4), activity.dp(4));
            row.addView(swatch, lp);
        }
        if (palette.size() % 2 != 0 && row != null) {
            row.addView(new android.view.View(activity), new LayoutParams(0, 1, 1));
        }
    }

    private TextView label(String text) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextSize(13);
        view.setTextColor(activity.textColor());
        view.setPadding(0, activity.dp(8), 0, activity.dp(4));
        return view;
    }
}
