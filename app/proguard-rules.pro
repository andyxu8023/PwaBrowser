# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
-keepclassmembers class fqcn.of.javascript.interface.for.webview {
   public *;
}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ============== 自动填充相关混淆规则 ==============

# 保持自动填充服务相关类
-keep class * extends android.service.autofill.AutofillService {
    *;
}

# 保持自动填充管理器相关
-keep class android.view.autofill.** { *; }
-keep class android.service.autofill.** { *; }
-keep class androidx.autofill.** { *; }

# 保持 WebView 自动填充功能
-keepattributes *Annotation*
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# 保持 JavaScript 接口（关键：保持我们的JavaScript接口不被混淆）
-keep class ddd.pwa.browser.** {
    *;
}
-keepclassmembers class ddd.pwa.browser.** {
    *;
}

# 保持 WebViewClient 和 WebChromeClient 子类
-keep class * extends android.webkit.WebViewClient {
    public <init>(...);
    public *;
}
-keep class * extends android.webkit.WebChromeClient {
    public <init>(...);
    public *;
}

# 保持 WebView 相关的方法
-keepclassmembers class * extends android.webkit.WebView {
    public *;
}

# ============== Kotlin 相关 ==============

# 保持 Kotlin 协程和反射功能
-keep class kotlin.coroutines.** { *; }
-keep class kotlinx.coroutines.** { *; }

# 保持数据类和伴生对象
-keepclassmembers class ** {
    ** companion;
}

-keepclasseswithmembers class ** {
    public <init>(...);
}

# ============== 应用特定类 ==============

# 保持所有 Activity
-keep public class * extends androidx.appcompat.app.AppCompatActivity

# 保持 WebViewActivity 中的所有方法（特别重要）
-keep class ddd.pwa.browser.WebViewActivity {
    *;
}

-keep class ddd.pwa.browser.WVViewClient {
    *;
}

-keep class ddd.pwa.browser.WVChromeClient {
    *;
}

# 保持 LAUNCH_MODE 枚举类
-keep class ddd.pwa.browser.LAUNCH_MODE {
    *;
}

# 保持 JavaScript 接口中的回调方法
-keepclassmembers class ** {
    @android.webkit.JavascriptInterface <methods>;
}

# ============== 反射相关 ==============

# 保持序列化和反序列化
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# 保持注解
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault

# ============== 资源相关 ==============

# 保持资源
-keepclassmembers class **.R$* {
    public static <fields>;
}

# 保持原生方法
-keepclasseswithmembers class * {
    native <methods>;
}

# ============== 通用优化规则 ==============

# 移除日志输出（发布版本）
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# 保持 Parcelable 实现
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# ============== 调试信息保留 ==============

# 保留本地变量表名称
-keepattributes LocalVariableTable, LocalVariableTypeTable

# 保留泛型信息
-keepattributes Signature, InnerClasses, EnclosingMethod

# 保留异常信息
-keepattributes Exceptions

# ============== WebView 资源拦截相关 ==============

# 保持 ServiceWorker 相关类
-keep class * extends android.webkit.ServiceWorkerClient {
    public *;
}

-keep class * extends android.webkit.ServiceWorkerController {
    public *;
}

# 保持 WebResourceResponse 相关
-keep class * extends android.webkit.WebResourceResponse {
    public *;
}