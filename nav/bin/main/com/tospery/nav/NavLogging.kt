package com.tospery.nav

import com.tospery.base.logging.LogAttribute
import com.tospery.base.logging.LogTags
import com.tospery.base.logging.info
import com.tospery.buildmetadata.module_nav.ModuleMetadata

internal val NAV_LOG_TAG: String =
    LogTags.child(
        parent = LogTags.moduleTag(ModuleMetadata.path),
        segment = "navigation",
    )

/**
 * 输出即将交给平台导航实现的 route URL。
 *
 * 查询参数默认仅保留名称，避免 OAuth code、token、任意 Web URL 或弹层文案进入日志。
 * 调用方可以为已确认安全的参数开放 value，或仅在允许输出敏感信息的调试构建中关闭脱敏。
 */
fun logRouteNavigation(
    routeUrl: String,
    source: String? = null,
    visibleQueryParameters: Set<String> = emptySet(),
    redactSensitiveLogValues: Boolean = true,
) {
    val attributes =
        buildList {
            add(
                LogAttribute(
                    key = "route_url",
                    value =
                        routeUrl.toNavigationLogUrl(
                            redactSensitiveLogValues = redactSensitiveLogValues,
                            visibleQueryParameters = visibleQueryParameters,
                        ),
                ),
            )
            source?.takeIf(String::isNotBlank)?.let { value ->
                add(LogAttribute(key = "source", value = value))
            }
        }

    info(
        tag = NAV_LOG_TAG,
        attributes = attributes,
    ) {
        "使用路由 URL 执行页面导航。"
    }
}

/**
 * 生成适合当前构建环境的日志 URL。
 *
 * 默认隐藏 fragment 与未显式开放的 query value；调用方仅可在明确允许输出敏感调试信息的
 * 构建环境中关闭脱敏。
 */
fun String.toNavigationLogUrl(
    redactSensitiveLogValues: Boolean = true,
    visibleQueryParameters: Set<String> = emptySet(),
): String =
    if (redactSensitiveLogValues) {
        redactNavigationUrl(visibleQueryParameters)
    } else {
        trim()
    }

/**
 * 生成脱敏后的 URL。fragment 与未显式开放的 query value 会被隐藏。
 */
fun String.redactNavigationUrl(
    visibleQueryParameters: Set<String> = emptySet(),
): String {
    val trimmed = trim()
    val fragmentIndex = trimmed.indexOf('#')
    val withoutFragment =
        if (fragmentIndex >= 0) trimmed.substring(0, fragmentIndex) else trimmed
    val fragmentSuffix = if (fragmentIndex >= 0) "#<redacted>" else ""
    val queryIndex = withoutFragment.indexOf('?')

    if (queryIndex < 0) return withoutFragment + fragmentSuffix

    val path = withoutFragment.substring(0, queryIndex)
    val rawQuery = withoutFragment.substring(queryIndex + 1)
    if (rawQuery.isEmpty()) return "$path?$fragmentSuffix"

    val safeQuery =
        rawQuery
            .split('&')
            .joinToString(separator = "&") { parameter ->
                parameter.redactQueryValue(visibleQueryParameters)
            }

    return "$path?$safeQuery$fragmentSuffix"
}

fun UrlNavigationTarget.toNavigationLogUrl(
    redactSensitiveLogValues: Boolean = true,
): String =
    when (this) {
        is UrlNavigationTarget.InternalRoute ->
            route.value.toNavigationLogUrl(redactSensitiveLogValues)

        is UrlNavigationTarget.ExternalApp ->
            uri.toOpaqueNavigationLogUrl(redactSensitiveLogValues)

        is UrlNavigationTarget.SystemUri ->
            uri.toOpaqueNavigationLogUrl(redactSensitiveLogValues)

        is UrlNavigationTarget.WebUrl ->
            url.toNavigationLogUrl(redactSensitiveLogValues)

        is UrlNavigationTarget.Unknown ->
            uri.toNavigationLogUrl(redactSensitiveLogValues)
    }

internal fun UrlNavigationTarget.navigationLogType(): String =
    javaClass.simpleName

internal fun NavAction.navigationLogType(): String =
    javaClass.simpleName

private fun String.redactQueryValue(
    visibleQueryParameters: Set<String>,
): String {
    val separatorIndex = indexOf('=')
    if (separatorIndex < 0) return this

    val key = substring(0, separatorIndex)
    return if (key in visibleQueryParameters) {
        this
    } else {
        "$key=<redacted>"
    }
}

private fun String.redactOpaqueNavigationUri(): String {
    val scheme = substringBefore(':').takeIf(String::isNotBlank)
    return if (scheme == null || ':' !in this) {
        redactNavigationUrl()
    } else {
        "$scheme:<redacted>"
    }
}

private fun String.toOpaqueNavigationLogUrl(
    redactSensitiveLogValues: Boolean,
): String =
    if (redactSensitiveLogValues) {
        redactOpaqueNavigationUri()
    } else {
        trim()
    }
