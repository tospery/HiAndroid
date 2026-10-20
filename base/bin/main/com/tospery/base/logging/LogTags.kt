package com.tospery.base.logging

/**
 * 应用内统一日志 Tag。
 *
 * 每个模块使用稳定的 `mylog-模块名` Tag，方便按 Gradle 模块过滤 Logcat。
 * 具体实现模块可以通过 child() 派生更细粒度的 Tag。
 */
object LogTags {
    private const val ROOT = "mylog"

    /**
     * 根据 Gradle 模块路径生成稳定 Tag。
     *
     * 模块内应传入由 Gradle 生成的 `ModuleMetadata.path`，避免在 Kotlin 源码中
     * 重复维护模块路径或 Tag 字面量。
     */
    fun moduleTag(modulePath: String): String {
        val normalized =
            modulePath
                .trim()
                .split(':')
                .filter(String::isNotBlank)
                .joinToString(separator = "-")
        require(normalized.isNotBlank()) { "modulePath 不能为空。" }
        return "$ROOT-$normalized"
    }

    fun child(
        parent: String,
        segment: String,
    ): String {
        require(parent.isNotBlank()) { "parent 不能为空。" }
        require(segment.isNotBlank()) { "segment 不能为空。" }
        return "$parent-$segment"
    }
}
