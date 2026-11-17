# ---- 自身代码 ----
# Gson 反射反序列化、JavascriptInterface、Compose 生成代码都可能按名访问，
# 整体保留最稳；R8 仍能裁掉未引用的第三方库（图标库是主要收益）。
-keep class dev.aigw.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# ---- WebView 桥 ----
# @JavascriptInterface 标注的方法必须保留原名，否则 H5 调不到
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ---- NanoHTTPD ----
-keep class fi.iki.elonen.** { *; }
-dontwarn fi.iki.elonen.**

# ---- Gson ----
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ---- Kotlin ----
-keepclassmembers class kotlin.Metadata { public <methods>; }
-dontwarn kotlin.**
