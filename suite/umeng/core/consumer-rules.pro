# 友盟公共组件通过反射发现产品组件与协议实现。
-keep class com.umeng.** { *; }
-keep class org.repackage.** { *; }

-keepclassmembers class * {
    public <init>(org.json.JSONObject);
}

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
