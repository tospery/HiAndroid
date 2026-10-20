package com.tospery.net

/**
 * 与平台无关的网络连接状态。
 * Android 的 ConnectivityManager 监听实现应放在 Android 实现层中。
 */
enum class NetworkConnectivity {
    NONE,
    WIFI,
    CELLULAR,
    OTHER,
}