# ============================================================================
# ABU Launcher — R8 / ProGuard 规则
# ----------------------------------------------------------------------------
# 基线规则来自 AGP 的 proguard-android-optimize.txt，已经包含：
#   -allowaccessmodification（允许优化时放宽访问修饰符）、
#   enum values() / valueOf(String)、View 的 setter/getter、Parcelable CREATOR、
#   @Keep 标注、@JavascriptInterface、native 方法、反射需要的注解属性 等。
# 下面只补本工程真正需要的少量规则，并为 release 崩溃栈保留可读性。
# ============================================================================

# 1) 枚举反序列化 —— 本工程多处把枚举名持久化后再用 valueOf(name) 还原：
#    DesktopPreferences / Wallpaper / LaunchAnim 的 SharedPreferences，
#    AppIconProcessor 的磁盘缓存 meta，SettingsPage 的分类名。
#    默认规则已保留 valueOf(String)，这里再显式兜底，确保枚举常量不被 R8 移除。
-keepclassmembers enum com.limi.tvdesktop.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# 2) 清单中声明的组件。AGP 会依据合并后的 AndroidManifest 自动生成 keep 规则，
#    这里显式写出来，便于排查“组件被混淆后无法启动”这类问题。
-keep class com.limi.tvdesktop.MainActivity { *; }
-keep class com.limi.tvdesktop.BootReceiver { *; }

# 3) 保留行号，方便用 mapping.txt 还原线上崩溃堆栈（体积代价很小）。
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# 4) OkHttp 在 Android 上会引用到的可选平台实现，缺失时不要当成构建错误。
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**

# 5) net.i2p Ed25519 contains an optional desktop-JDK compatibility branch. Android uses
#    EdDSAPublicKey directly and never loads the sun.security.x509 type.
-dontwarn sun.security.x509.X509Key
