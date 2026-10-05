package com.example.flymestatusbarsizer.feature.statusbar;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** Slot names verified against Flyme's framework resources, not JADX's android.R names. */
public final class StatusBarIconVisibility {
    public enum Icon {
        BLUETOOTH("连接与网络", "蓝牙", "同时隐藏蓝牙连接、耳机电量和文件传输标记。", "bluetooth"),
        VPN("连接与网络", "VPN", "隐藏 VPN 连接标记。", "vpn"),
        HD("连接与网络", "HD / VoLTE / VoWiFi", "这几种通话网络标记一起隐藏。", "volte_or_vowifi"),
        WIFI("连接与网络", "Wi-Fi", "隐藏主 Wi-Fi 图标，保留实际连接。", "wifi"),
        DUAL_WIFI("连接与网络", "双 Wi-Fi", "只隐藏副 Wi-Fi 图标。", "dual_wifi"),
        MOBILE("连接与网络", "移动网络信号", "同时隐藏所有 SIM 的信号、网络类型和上下行标记。HD 标记单独设置。", "mobile"),
        NO_SIM("连接与网络", "无 SIM 卡", "隐藏未插入 SIM 卡的提示图标。", "no_sims"),
        AIRPLANE("连接与网络", "飞行模式", "隐藏飞行模式图标。", "airplane"),
        HOTSPOT("连接与网络", "个人热点", "隐藏热点图标，包括锁屏和下拉面板中的标记。", "hotspot"),
        ETHERNET("连接与网络", "有线网络", "隐藏以太网连接图标。", "ethernet"),
        NFC("连接与网络", "NFC", "隐藏 NFC 开启标记。", "nfc"),
        ALARM("声音与提醒", "闹钟", "隐藏闹钟图标，闹钟照常响铃。", "alarm_clock"),
        MUTE("声音与提醒", "静音", "只隐藏静音图标，振动图标单独设置。", "mute"),
        VIBRATE("声音与提醒", "振动", "只隐藏振动模式图标。", "volume"),
        ZEN("声音与提醒", "勿扰 / 专注模式", "隐藏勿扰和专注模式图标。", "zen"),
        HEADSET("声音与提醒", "有线耳机", "隐藏带麦克风和不带麦克风的有线耳机图标。", "headset"),
        LOCATION("其他系统状态", "定位", "隐藏系统状态图标区的定位图标，权限使用提示单独显示。", "location"),
        MANAGED_PROFILE("其他系统状态", "工作资料", "隐藏工作资料等用户资料类型的图标。", "managed_profile"),
        SENSORS_OFF("其他系统状态", "传感器关闭", "隐藏传感器关闭标记。", "sensors_off"),
        CONNECTED_DISPLAY("其他系统状态", "外接显示器", "隐藏外接显示器状态图标。", "connected_display"),
        ROTATE("其他系统状态", "旋转锁定", "隐藏屏幕旋转锁定图标。部分系统界面默认已隐藏。", "rotate"),
        DATA_SAVER("其他系统状态", "流量节省", "隐藏流量节省图标。部分系统界面默认已隐藏。", "data_saver"),
        CAST("其他系统状态", "投屏图标", "隐藏传统投屏图标，投屏活动胶囊单独显示。", "cast"),
        TTY("其他系统状态", "TTY", "隐藏文字电话辅助功能的状态图标。", "tty");

        public final String group;
        public final String title;
        public final String description;
        public final String slot;
        public final String key;

        Icon(String group, String title, String description, String slot) {
            this.group = group;
            this.title = title;
            this.description = description;
            this.slot = slot;
            this.key = "status_bar_hide_" + slot;
        }
    }

    public static final List<Icon> ICONS = List.of(Icon.values());

    private StatusBarIconVisibility() {}

    public static String[] appendPreferenceKeys(String[] keys) {
        String[] result = Arrays.copyOf(keys, keys.length + ICONS.size());
        for (int i = 0; i < ICONS.size(); i++) result[keys.length + i] = ICONS.get(i).key;
        return result;
    }

    public static Set<String> readHiddenSlots(Predicate<String> selected) {
        Set<String> slots = new LinkedHashSet<>();
        for (Icon icon : ICONS) {
            if (selected.test(icon.key)) slots.add(icon.slot);
        }
        return Collections.unmodifiableSet(slots);
    }
}
