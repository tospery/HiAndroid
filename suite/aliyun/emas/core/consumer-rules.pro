# Alibaba Cloud EMAS Mobile Monitoring reflection entry points.
-keep class com.aliyun.emas.apm.** { *; }
-keep class com.alibaba.sdk.android.networkmonitor.** { *; }

# EMAS models retain optional AutoValue annotations that are not needed at runtime.
-dontwarn com.google.auto.value.AutoValue
-dontwarn com.google.auto.value.AutoValue$Builder

# Network Monitor supports multiple OkHttp generations and keeps optional references to
# internal classes that do not exist in the selected supported OkHttp 5.3.2 runtime.
-dontwarn okhttp3.internal.Version
-dontwarn okhttp3.internal.http.HttpEngine
-dontwarn okhttp3.internal.http.RouteException
