package com.tospery.suite.logging

import android.util.Log
import timber.log.Timber

object TimberInitializer {
    fun plantDebugTree() {
        Timber.plant(Timber.DebugTree())
    }

    /**
     * Release 版本仍保留可运维的 INFO/WARN/ERROR，同时从输出端再次屏蔽调试日志。
     *
     * AppLogger 的最低级别负责避免构建低级别日志内容；这里是防止其他 Timber
     * 直接调用绕过日志抽象的第二道保护。
     */
    fun plantReleaseTree() {
        Timber.plant(ReleaseTree())
    }

    fun plant(tree: Timber.Tree) {
        Timber.plant(tree)
    }

    fun uprootAll() {
        Timber.uprootAll()
    }
}

private class ReleaseTree : Timber.DebugTree() {
    override fun isLoggable(
        tag: String?,
        priority: Int,
    ): Boolean = priority >= Log.INFO
}
