package com.tospery.net

import kotlinx.coroutines.flow.Flow

/**
 * 网络连接状态观察器。
 * 平台实现层负责把系统网络回调转换成 Flow。
 */
fun interface NetworkConnectivityObserver {
    fun observe(): Flow<NetworkConnectivity>
}