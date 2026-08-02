# U-APM 使用反射和 Native 崩溃采集，必须保留以下实现。
-keep class com.umeng.** { *; }
-keep class com.uc.** { *; }
-keep class com.efs.** { *; }

-keepclassmembers class * {
    public <init>(org.json.JSONObject);
}

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
