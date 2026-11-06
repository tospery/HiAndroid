package com.tospery.net.sse

/**
 * 逐行提取 Server-Sent Events 中 `data:` 字段的负载。
 *
 * 面向“每个事件只有一行 data、负载为独立 JSON”的流式接口：行首空白、空行、`:` 开头的注释行，
 * 以及 `event:`、`id:`、`retry:` 等非 data 字段都会被忽略。它不按 SSE 规范拼接多行 data，
 * 需要完整事件语义的调用方应自行按空行聚合。
 */
object SseLineParser {
    /** 返回去掉 `data:` 前缀与前导空白后的负载；非 data 行或负载为空时返回 null。 */
    fun dataPayloadOrNull(line: String): String? {
        val normalizedLine = line.trimStart()
        if (normalizedLine.startsWith(COMMENT_PREFIX) || !normalizedLine.startsWith(DATA_PREFIX)) {
            return null
        }
        return normalizedLine
            .removePrefix(DATA_PREFIX)
            .trimStart()
            .takeIf(String::isNotBlank)
    }

    private const val DATA_PREFIX = "data:"
    private const val COMMENT_PREFIX = ":"
}
