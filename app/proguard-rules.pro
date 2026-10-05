# LSPosed reads this class name from META-INF/xposed/java_init.list.
# The compileOnly libxposed API does not contribute its consumer rules to R8.
-keep,allowoptimization class com.example.flymestatusbarsizer.FlymeStatusBarSizer {
    public <init>();
    public void onPackageLoaded(io.github.libxposed.api.XposedModuleInterface$PackageLoadedParam);
}
