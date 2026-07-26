package com.tospery.nav

import com.tospery.base.logging.LogAttribute
import com.tospery.base.logging.debug
import java.net.URI

private val defaultSystemSchemes = setOf(
    UrlScheme("mailto"),
    UrlScheme("sms"),
    UrlScheme("smsto"),
    UrlScheme("tel"),
    UrlScheme("geo"),
    UrlScheme("market"),
)

data class UrlNavigationConfig(
    val appSchemes: Set<UrlScheme>,
    val trustedHosts: Set<UrlHost>,
    val systemSchemes: Set<UrlScheme> = defaultSystemSchemes,
    val webOpenMode: WebOpenMode = WebOpenMode.EXTERNAL_BROWSER,
    val redactSensitiveLogValues: Boolean = true,
)

class UrlNavigationClassifier(
    private val config: UrlNavigationConfig,
) {
    private val appSchemes = config.appSchemes.mapTo(mutableSetOf()) { it.normalized() }
    private val trustedHosts = config.trustedHosts.mapTo(mutableSetOf()) { it.normalized() }
    private val systemSchemes = config.systemSchemes.mapTo(mutableSetOf()) { it.normalized() }

    fun classify(uri: String): UrlNavigationTarget {
        val target = classifyTarget(uri)

        debug(
            tag = NAV_LOG_TAG,
            attributes =
                listOf(
                    LogAttribute(
                        key = "input_url",
                        value =
                            uri.toNavigationLogUrl(
                                redactSensitiveLogValues = config.redactSensitiveLogValues,
                            ),
                    ),
                    LogAttribute(
                        key = "target_type",
                        value = target.navigationLogType(),
                    ),
                    LogAttribute(
                        key = "target_url",
                        value =
                            target.toNavigationLogUrl(
                                redactSensitiveLogValues = config.redactSensitiveLogValues,
                            ),
                    ),
                ),
        ) {
            "URL 导航目标分类完成。"
        }

        return target
    }

    private fun classifyTarget(uri: String): UrlNavigationTarget {
        val parsedUri = runCatching { URI(uri.trim()) }.getOrNull()
            ?: return UrlNavigationTarget.Unknown(uri)

        val scheme = parsedUri.scheme?.lowercase()
            ?: return UrlNavigationTarget.InternalRoute(
                route = NavRoute(uri),
                origin = InternalRouteOrigin.RelativeRoute,
            )

        val host = parsedUri.host?.lowercase()
        val path = parsedUri.rawPath.orEmpty().trimStart('/')
        val query = parsedUri.rawQuery

        return when {
            scheme in appSchemes -> {
                buildInternalRoute(
                    path = host.orEmpty() + path.withLeadingSlash(),
                    rawQuery = query,
                    source = uri,
                    origin =
                        InternalRouteOrigin.AppScheme(
                            scheme = UrlScheme(scheme),
                        ),
                )
            }

            scheme in HttpSchemes && host in trustedHosts -> {
                buildInternalRoute(
                    path = path,
                    rawQuery = query,
                    source = uri,
                    origin =
                        InternalRouteOrigin.TrustedWebHost(
                            scheme = UrlScheme(scheme),
                            host = UrlHost(requireNotNull(host)),
                        ),
                )
            }

            scheme in systemSchemes -> {
                UrlNavigationTarget.SystemUri(uri)
            }

            scheme in HttpSchemes -> {
                UrlNavigationTarget.WebUrl(
                    url = uri,
                    openMode = config.webOpenMode,
                )
            }

            else -> {
                UrlNavigationTarget.ExternalApp(uri)
            }
        }
    }

    private fun buildInternalRoute(
        path: String,
        rawQuery: String?,
        source: String,
        origin: InternalRouteOrigin,
    ): UrlNavigationTarget {
        val route = runCatching {
            NavRoute(buildRoute(path = path, rawQuery = rawQuery))
        }.getOrNull()

        return if (route == null) {
            UrlNavigationTarget.Unknown(source)
        } else {
            UrlNavigationTarget.InternalRoute(
                route = route,
                origin = origin,
            )
        }
    }

    private fun buildRoute(
        path: String,
        rawQuery: String?,
    ): String {
        val normalizedPath = path.trim().trimStart('/')

        require(normalizedPath.isNotBlank()) {
            "Internal route path must not be blank."
        }

        return if (rawQuery.isNullOrBlank()) {
            normalizedPath
        } else {
            "$normalizedPath?$rawQuery"
        }
    }

    private fun String.withLeadingSlash(): String {
        return if (isBlank()) "" else "/$this"
    }

    private companion object {
        val HttpSchemes = setOf("http", "https")
    }
}
