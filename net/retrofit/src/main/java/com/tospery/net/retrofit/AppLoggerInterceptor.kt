package com.tospery.net.retrofit

import com.tospery.base.logging.LogTags
import com.tospery.base.logging.LogLevel
import com.tospery.base.logging.debug
import com.tospery.base.logging.error
import com.tospery.base.logging.info
import com.tospery.base.logging.isLoggable
import com.tospery.base.logging.warning
import com.tospery.buildmetadata.module_net_retrofit.ModuleMetadata
import java.io.IOException
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.RequestBody
import okhttp3.Response
import okio.Buffer

internal val NET_LOG_TAG = LogTags.moduleTag(ModuleMetadata.path)

/**
 * 使用 base 日志抽象记录网络请求生命周期。
 *
 * 默认记录请求/响应的头与正文，便于开发阶段定位接口问题；敏感认证信息仍必须脱敏。
 * 已明确禁止记录的接口（例如贡献图 GraphQL）完全静默。
 */
internal class AppLoggerInterceptor(
    private val tag: String = NET_LOG_TAG,
    private val redactSensitiveData: Boolean = true,
    private val logBodies: Boolean = true,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toLogUrl()
        val shouldLogRequest = !request.isGitHubGraphQl()

        if (shouldLogRequest) {
            debug(tag = tag) { "[${request.method}]$url" }
        }
        if (shouldLogRequest && isLoggable(LogLevel.DEBUG, tag)) {
            debug(tag = tag) {
                requestLogSection(
                    method = request.method,
                    section = REQUEST_HEADERS_LOG_SECTION,
                    content = request.headers.headersForLog(),
                )
            }
            debug(tag = tag) {
                requestLogSection(
                    method = request.method,
                    section = REQUEST_BODY_LOG_SECTION,
                    content =
                        if (logBodies) {
                            request.body.requestBodyForLog(request.headers)
                        } else {
                            BODY_LOGGING_DISABLED_VALUE
                        },
                )
            }
        }

        return try {
            val response = chain.proceed(request)

            if (shouldLogRequest) {
                if (response.isSuccessful) {
                    info(tag = tag) { "[${request.method}][${response.code}]$url" }
                } else {
                    warning(tag = tag) { "[${request.method}][${response.code}]$url" }
                }
            }
            if (shouldLogRequest && isLoggable(LogLevel.DEBUG, tag)) {
                debug(tag = tag) {
                    responseLogSection(
                        method = request.method,
                        statusCode = response.code,
                        section = RESPONSE_HEADERS_LOG_SECTION,
                        content = response.headers.headersForLog(),
                    )
                }
                debug(tag = tag) {
                    responseLogSection(
                        method = request.method,
                        statusCode = response.code,
                        section = RESPONSE_BODY_LOG_SECTION,
                        content =
                            if (logBodies) {
                                response.responseBodyForLog()
                            } else {
                                BODY_LOGGING_DISABLED_VALUE
                            },
                    )
                }
            }

            response
        } catch (throwable: IOException) {
            if (shouldLogRequest) {
                error(
                    tag = tag,
                    throwable = throwable,
                ) {
                    buildString {
                        append("[")
                        append(request.method)
                        append("][异常]")
                        append(url)
                        append(" ")
                        append(throwable.javaClass.simpleName)
                    }.trimEnd()
                }
            }
            throw throwable
        }
    }

    private fun RequestBody?.requestBodyForLog(headers: Headers): String {
        if (this == null) return EMPTY_LOG_VALUE
        if (isDuplex() || isOneShot()) return UNREADABLE_LOG_VALUE
        if (!headers.isPlainTextBody()) return UNREADABLE_LOG_VALUE

        return runCatching {
            Buffer().use { buffer ->
                writeTo(buffer)
                buffer.readString(contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8)
            }
        }.getOrDefault(UNREADABLE_LOG_VALUE)
            .truncateForLog()
            .redactSensitiveText()
    }

    private fun Response.responseBodyForLog(): String {
        if (!headers.isPlainTextBody()) return UNREADABLE_LOG_VALUE

        return runCatching {
            peekBody(MAX_BODY_LOG_BYTES).string()
        }.getOrDefault(UNREADABLE_LOG_VALUE)
            .truncateForLog()
            .redactSensitiveText()
    }

    private fun HttpUrl.toLogUrl(): String {
        return if (redactSensitiveData) {
            newBuilder()
                .query(null)
                .fragment(null)
                .build()
                .toString()
        } else {
            toString()
        }
    }

    private fun okhttp3.Request.isGitHubGraphQl(): Boolean {
        return url.host == GITHUB_API_HOST && url.encodedPath == GITHUB_GRAPHQL_PATH
    }

    private fun Headers.headersForLog(): String {
        if (size == 0) return EMPTY_LOG_VALUE

        return buildString {
            repeat(size) { index ->
                if (index > 0) append('\n')
                val name = name(index)
                append(name)
                append(": ")
                append(value(index).redactHeaderValue(name))
            }
        }
    }

    private fun requestLogSection(
        method: String,
        section: String,
        content: String,
    ): String = "[$method][$section]\n$content"

    private fun responseLogSection(
        method: String,
        statusCode: Int,
        section: String,
        content: String,
    ): String = "[$method][$statusCode][$section]\n$content"

    private fun Headers.isPlainTextBody(): Boolean {
        val contentEncoding = this["Content-Encoding"]
        if (!contentEncoding.isNullOrBlank() && !contentEncoding.equals("identity", true)) {
            return false
        }

        val contentType = this["Content-Type"] ?: return true
        return PLAIN_TEXT_CONTENT_TYPES.any { contentType.contains(it, ignoreCase = true) }
    }

    private fun String.redactSensitiveText(): String {
        if (!redactSensitiveData) return this

        return SENSITIVE_JSON_FIELD_REGEX.replace(this) { matchResult ->
            "${matchResult.groupValues[1]}$REDACTED_VALUE${matchResult.groupValues[3]}"
        }
    }

    private fun String.redactHeaderValue(name: String): String {
        return if (redactSensitiveData && name.isSensitiveHeaderName()) {
            REDACTED_VALUE
        } else {
            this
        }
    }

    private fun String.isSensitiveHeaderName(): Boolean {
        val normalized = lowercase()
        return SENSITIVE_HEADER_NAME_PARTS.any(normalized::contains)
    }

    private fun String.truncateForLog(): String {
        return if (length <= MAX_BODY_LOG_CHARS) {
            this.ifBlank { EMPTY_LOG_VALUE }
        } else {
            take(MAX_BODY_LOG_CHARS) + "\n...<已截断>"
        }
    }

    private companion object {
        const val MAX_BODY_LOG_BYTES = 16_384L
        const val MAX_BODY_LOG_CHARS = 16_384
        const val EMPTY_LOG_VALUE = "<空>"
        const val UNREADABLE_LOG_VALUE = "<不可读取>"
        const val BODY_LOGGING_DISABLED_VALUE = "<已禁用>"
        const val REDACTED_VALUE = "***"
        const val GITHUB_API_HOST = "api.github.com"
        const val GITHUB_GRAPHQL_PATH = "/graphql"
        const val REQUEST_HEADERS_LOG_SECTION = "请求头"
        const val REQUEST_BODY_LOG_SECTION = "请求正文"
        const val RESPONSE_HEADERS_LOG_SECTION = "响应头"
        const val RESPONSE_BODY_LOG_SECTION = "响应正文"

        val PLAIN_TEXT_CONTENT_TYPES = setOf(
            "text/",
            "json",
            "xml",
            "html",
            "x-www-form-urlencoded",
        )

        val SENSITIVE_JSON_FIELD_REGEX = Regex(
            pattern = "(?i)(\"(?:githubAccessToken|githubOAuthCode|githubOAuthCodeVerifier|" +
                "access_token|refresh_token|id_token|token|code_verifier|client_secret|" +
                "clientSecret|password)\"\\s*:\\s*\")([^\"]*)(\")",
        )

        val SENSITIVE_HEADER_NAME_PARTS = setOf(
            "apikey",
            "api-key",
            "authorization",
            "cookie",
            "credential",
            "secret",
            "signature",
            "token",
        )
    }
}
