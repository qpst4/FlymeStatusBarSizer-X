package com.example.flymestatusbarsizer.ui;

/** Restart entries for the packages declared in META-INF/xposed/scope.list. */
public enum RestartTarget {
    SYSTEM_UI("com.android.systemui", "SystemUI",
            "修改状态栏、通知背景等设置后，重启系统界面。"),
    SHARE_RESOLVER("com.android.intentresolver", "系统分享",
            "更新分享列表功能后重启，下次打开分享面板时加载新版模块。"),
    GALLERY("com.meizu.media.gallery", "Flyme 图库",
            "更新图库分享功能后重启，再手动打开图库。"),
    LAUNCHER("com.meizu.flyme.launcher", "系统桌面",
            "修改文件夹、后台布局或堆叠参数后，重启系统桌面。"),
    ASSISTANT("com.meizu.assistant", "Aicy 纵览",
            "更新全局负一屏功能后，重启 Aicy 纵览。"),
    SYSTEM_UI_TOOLS("com.flyme.systemuitools", "SystemUITools",
            "重启后小窗相关修改重新加载。"),
    PPS("com.meizu.pps", "OneMind/PPS",
            "开关变更后重启 PPS，让进程重新加载模块。"),
    PHONE_MANAGER("com.meizu.safe", "手机管家",
            "修改后台优化设置后重启，再打开手机管家或等待系统启动服务。"),
    CARLINK("com.upuphone.carlink", "CarLink",
            "修改应用流转和车联设置后重启，再打开 CarLink 或重新连接车机。"),
    AOSP_IME("com.android.inputmethod.latin", "AOSP 输入法",
            "更新输入法控制栏功能后重启，重新调出键盘时加载模块。"),
    GBOARD("com.google.android.inputmethod.latin", "Gboard",
            "更新输入法控制栏功能后重启，重新调出键盘时加载模块。"),
    WECHAT_IME("com.tencent.wetype", "微信输入法",
            "更新输入法控制栏功能后重启，重新调出键盘时加载模块。"),
    FLYME_IME("flyme.inputmethod", "Flyme 输入法",
            "更新输入法控制栏功能后重启，重新调出键盘时加载模块。"),
    FRAMEWORK("android", "系统框架",
            "系统框架相关功能需重启手机才能重新加载。点击后需确认，需要 Root 权限。");

    public final String packageName;
    public final String label;
    public final String summary;

    RestartTarget(String packageName, String label, String summary) {
        this.packageName = packageName;
        this.label = label;
        this.summary = summary;
    }

    public String[] restartCommands() {
        return switch (this) {
            case SYSTEM_UI -> new String[]{
                    "killall " + packageName, "pkill -f " + packageName, "am crash " + packageName};
            case SHARE_RESOLVER, GALLERY, LAUNCHER -> new String[]{
                    "am force-stop " + packageName, "pkill -f " + packageName, "killall " + packageName};
            // Keep bound services eligible for reconnection; force-stop removes their bindings.
            case ASSISTANT -> new String[]{"killall " + packageName, "pkill -f " + packageName};
            case SYSTEM_UI_TOOLS -> new String[]{
                    "pkill -f " + packageName, "killall " + packageName, "am crash " + packageName};
            case PPS -> new String[]{
                    "cmd package set-stopped-state " + packageName + " false",
                    "pkill -f " + packageName, "killall " + packageName};
            case PHONE_MANAGER, CARLINK, AOSP_IME, GBOARD, WECHAT_IME, FLYME_IME -> new String[]{
                    "pkill -f '^" + packageName.replace(".", "[.]") + "(:[^[:space:]]+)?$'",
                    "killall " + packageName};
            case FRAMEWORK -> throw new IllegalStateException("Framework requires a separate device restart");
        };
    }

    public boolean stopOnFirstSuccess() {
        return this != PPS;
    }
}
