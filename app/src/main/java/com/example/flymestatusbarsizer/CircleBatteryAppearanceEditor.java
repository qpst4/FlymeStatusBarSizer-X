package com.example.flymestatusbarsizer;

import android.app.AlertDialog;
import android.graphics.Color;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

final class CircleBatteryAppearanceEditor {
    private final MainActivity activity;

    CircleBatteryAppearanceEditor(MainActivity activity) {
        this.activity = activity;
    }

    void addTo(LinearLayout root) {
        addTransparency(root);
        activity.addDivider(root);
        addColor(root, "正常 · 浅色背景", SettingsStore.KEY_CAMERA_CIRCLE_BATTERY_NORMAL_LIGHT_COLOR,
                SettingsStore.DEFAULT_CAMERA_CIRCLE_BATTERY_NORMAL_LIGHT_COLOR);
        addColor(root, "正常 · 深色背景", SettingsStore.KEY_CAMERA_CIRCLE_BATTERY_NORMAL_DARK_COLOR,
                SettingsStore.DEFAULT_CAMERA_CIRCLE_BATTERY_NORMAL_DARK_COLOR);
        addColor(root, "充电", SettingsStore.KEY_CAMERA_CIRCLE_BATTERY_CHARGING_COLOR,
                SettingsStore.DEFAULT_CAMERA_CIRCLE_BATTERY_CHARGING_COLOR);
        addColor(root, "省电模式", SettingsStore.KEY_CAMERA_CIRCLE_BATTERY_POWER_SAVE_COLOR,
                SettingsStore.DEFAULT_CAMERA_CIRCLE_BATTERY_POWER_SAVE_COLOR);
        addColor(root, "低电量（低于 10%）", SettingsStore.KEY_CAMERA_CIRCLE_BATTERY_LOW_COLOR,
                SettingsStore.DEFAULT_CAMERA_CIRCLE_BATTERY_LOW_COLOR);
    }

    private void addTransparency(LinearLayout root) {
        String key = SettingsStore.KEY_CAMERA_CIRCLE_BATTERY_TRANSPARENCY_TENTH_PERCENT;
        int defaultValue = SettingsStore.DEFAULT_CAMERA_CIRCLE_BATTERY_TRANSPARENCY_TENTH_PERCENT;
        LinearLayout header = row("电量圆弧透明度");
        activity.addHelpButton(header, "电量圆弧透明度",
                "0% 完全不透明，100% 完全透明。默认 11.8% 透明（88.2% 不透明），只调整电量圆弧；彩虹色也生效。点击数值可精确输入。背景环保持系统原样。");
        TextView value = activity.chip("", activity.surfaceSoftColor(), activity.primaryColor());
        value.setContentDescription("电量圆弧透明度，点击输入");
        header.addView(value);
        root.addView(header);
        SeekBar slider = new SeekBar(activity);
        slider.setContentDescription("电量圆弧透明度");
        activity.styleSeekBar(slider);
        slider.setMax(1000);
        slider.setProgress(Math.max(0, Math.min(1000,
                SettingsStore.readInt(activity.prefs(), key, defaultValue))));
        value.setText(percent(slider.getProgress()));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                value.setText(percent(progress));
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                activity.putIntSetting(key, bar.getProgress());
            }
        });
        root.addView(slider, new LinearLayout.LayoutParams(-1, activity.dp(40)));
        activity.setTapClickListener(value, v -> {
            EditText input = new EditText(activity);
            input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            input.setText(String.format(Locale.US, "%.1f", slider.getProgress() / 10f));
            input.selectAll();
            AlertDialog dialog = new AlertDialog.Builder(activity)
                    .setTitle("电量圆弧透明度")
                    .setMessage("输入 0～100，支持一位小数。数值越大越透明。")
                    .setView(input).setNegativeButton("取消", null)
                    .setNeutralButton("恢复默认", (d, w) -> {
                        slider.setProgress(defaultValue);
                        activity.putIntSetting(key, defaultValue);
                    })
                    .setPositiveButton("应用", null).create();
            show(dialog);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
                try {
                    float number = Float.parseFloat(input.getText().toString().trim());
                    if (!Float.isFinite(number) || number < 0 || number > 100) {
                        throw new NumberFormatException();
                    }
                    int selected = Math.round(number * 10);
                    slider.setProgress(selected);
                    activity.putIntSetting(key, selected);
                    dialog.dismiss();
                } catch (NumberFormatException e) {
                    input.setError("请输入 0～100 的数值");
                }
            });
        });
    }

    void addColor(LinearLayout root, String title, String key, int defaultColor) {
        LinearLayout row = row(title);
        TextView button = new TextView(activity);
        button.setTextSize(14);
        button.setGravity(Gravity.CENTER);
        button.setPadding(activity.dp(12), activity.dp(8), activity.dp(12), activity.dp(8));
        button.setMinHeight(activity.dp(44));
        button.setContentDescription(title + "颜色，点击修改");
        updateSwatch(button, SettingsStore.readInt(activity.prefs(), key, defaultColor));
        row.addView(button);
        activity.setTapClickListener(button, v -> showColorDialog(title, key, defaultColor, button));
        root.addView(row);
    }

    private void showColorDialog(String title, String key, int defaultColor, TextView button) {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(activity.dp(20), activity.dp(8), activity.dp(20), 0);
        TextView preview = new TextView(activity);
        preview.setGravity(Gravity.CENTER);
        content.addView(preview, new LinearLayout.LayoutParams(-1, activity.dp(44)));
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setHint("#RRGGBB，例如 #088BFF");
        input.setContentDescription("十六进制颜色");
        content.addView(input);
        ColorPaletteView palette = new ColorPaletteView(activity, color -> input.setText(hex(color)));
        content.addView(palette, 0);
        SeekBar[] channels = new SeekBar[3];
        String[] labels = {"红", "绿", "蓝"};
        for (int i = 0; i < channels.length; i++) {
            LinearLayout row = row(labels[i]);
            SeekBar slider = new SeekBar(activity);
            slider.setMax(255);
            slider.setContentDescription(labels[i] + "色分量");
            activity.styleSeekBar(slider);
            channels[i] = slider;
            row.addView(slider, new LinearLayout.LayoutParams(activity.dp(190), activity.dp(40)));
            slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                    if (fromUser) {
                        input.setText(hex(Color.rgb(channels[0].getProgress(),
                                channels[1].getProgress(), channels[2].getProgress())));
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar bar) { }
                @Override public void onStopTrackingTouch(SeekBar bar) { }
            });
            content.addView(row);
        }
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                try {
                    int color = parseColor(s.toString());
                    input.setError(null);
                    updateSwatch(preview, color);
                    palette.setCurrentColor(color);
                    channels[0].setProgress(Color.red(color));
                    channels[1].setProgress(Color.green(color));
                    channels[2].setProgress(Color.blue(color));
                } catch (IllegalArgumentException ignored) {
                    palette.setCurrentColor(null);
                }
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        input.setText(hex(SettingsStore.readInt(activity.prefs(), key, defaultColor)));
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(content);
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle(title + "颜色")
                .setMessage("输入色值或拖动红、绿、蓝滑块。透明度由电量圆弧透明度统一控制。")
                .setView(scroll).setNegativeButton("取消", null)
                .setNeutralButton("恢复默认", (d, w) -> {
                    palette.recordApplied(defaultColor);
                    activity.putIntSetting(key, defaultColor);
                    updateSwatch(button, defaultColor);
                })
                .setPositiveButton("应用", null).create();
        show(dialog);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                int color = parseColor(input.getText().toString());
                palette.recordApplied(color);
                activity.putIntSetting(key, color);
                updateSwatch(button, color);
                dialog.dismiss();
            } catch (IllegalArgumentException e) {
                input.setError("请输入六位十六进制颜色，例如 #088BFF");
            }
        });
    }

    static int parseColor(String text) {
        String hex = text.trim();
        if (hex.startsWith("#")) hex = hex.substring(1);
        if (!hex.matches("[0-9a-fA-F]{6}")) throw new IllegalArgumentException("Invalid RGB");
        return 0xFF000000 | Integer.parseInt(hex, 16);
    }

    private LinearLayout row(String title) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, activity.dp(4), 0, activity.dp(4));
        TextView label = new TextView(activity);
        label.setText(title);
        label.setTextSize(16);
        label.setTextColor(activity.textColor());
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        return row;
    }

    private void updateSwatch(TextView view, int color) {
        int opaque = color | 0xFF000000;
        view.setText(hex(opaque));
        view.setBackground(activity.outlinedRect(opaque, activity.strokeColor(), 1, 12));
        double luminance = 0.299 * Color.red(opaque) + 0.587 * Color.green(opaque)
                + 0.114 * Color.blue(opaque);
        view.setTextColor(luminance > 150 ? Color.BLACK : Color.WHITE);
    }

    private void show(AlertDialog dialog) {
        dialog.show();
        activity.styleDialog(dialog);
        activity.attachDialogButtonHaptics(dialog);
    }

    private static String hex(int color) {
        return String.format(Locale.US, "#%06X", color & 0xFFFFFF);
    }

    private static String percent(int value) {
        return String.format(Locale.US, "%.1f%%", value / 10f);
    }
}
