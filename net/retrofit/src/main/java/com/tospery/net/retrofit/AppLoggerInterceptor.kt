package com.tospery.net.retrofit

import com.tospery.base.logging.LogTags
import com.tospery.base.logging.LogLevel
import com.tospery.base.logging.LogAttribute
import com.tospery.base.logging.debug
import com.tospery.base.logging.error
import com.tospery.base.logging.info
import com.tospery.base.logging.isLoggable
import com.tospery.base.logging.supportsSensitiveLogging
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
 * Ordinary clients receive only method, origin, status, and body length. A protected log
 * provider and explicit opt-in are both required before paths, headers, and bodies are recorded.
 */
internal class AppLoggerInterceptor(
    private val tag: String = NET_LOG_TAG,
    private val redactSensitiveData: Boolean = true,
    private val logBodies: Boolean = true,
    private val captureFullBodies: Boolean = false,
    private val logGraphQl: Boolean = false,
    private val fullBodyByteLimit: Long = DEFAULT_FULL_BODY_BYTE_LIMIT,
) : Interceptor {
    init {
        require(fullBodyByteLimit > 0L)
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val debugEnabled = runCatching { isLoggable(LogLevel.DEBUG, tag) }.getOrDefault(false)
        val rawCapture = !redactSensitiveData &&
            runCatching { supportsSensitiveLogging() }.getOrDefault(false)
        val fullCapture = rawCapture && captureFullBodies && logBodies && debugEnabled
        val url = request.url.toLogUrl(redact = !rawCapture)
        val shouldLogRequest = !request.isGitHubGraphQl() || (logGraphQl && fullCapture)
        val requestId = if (fullCapture) NetworkBodyCapture.newRequestId() else null
        val requestAttributes = requestId?.let {
            listOf(LogAttribute("network_request_id", it))
        } ?: emptyList()

        if (shouldLogRequest) {
            safeLog { info(tag = tag, attributes = requestAttributes) { "[${request.method}]$url" } }
        }
        if (shouldLogRequest && debugEnabled) {
            if (rawCapture) {
                request.headers.headersForLog(redact = false)
                    .takeIf(String::isNotBlank)?.let { headers ->
                        safeLog { debug(tag = tag, attributes = requestAttributes) { headers } }
                    }
            }
            if (logBodies && request.body != null && !fullCapture) {
                safeLog {
                    debug(tag = tag) {
                        if (rawCapture) {
                            request.body.requestBodyForLog(request.headers, redact = false)
                        } else {
                            omittedBody(runCatching { request.body?.contentLength() ?: -1L }.getOrDefault(-1L))
                        }
                    }
                }
            }
        }

        val observedRequest = if (shouldLogRequest && fullCapture && request.body != null) {
            request.newBuilder().method(
                request.method,
                ObservedRequestBody(
                    delegate = request.body!!,
                    capture = NetworkBodyCapture(
                        tag = tag,
                        requestId = requireNotNull(requestId),
                        direction = "request",
                        byteLimit = fullBodyByteLimit,
                    ),
                ),
            ).build()
        } else {
            request
        }

        return try {
            val response = chain.proceed(observedRequest)

            if (shouldLogRequest) {
                if (response.isSuccessful) {
                    safeLog {
                        info(tag = tag, attributes = requestAttributes) {
                            "[${request.method}][${response.code}]$url"
                        }
                    }
                } else {
                    safeLog {
                        error(tag = tag, attributes = requestAttributes) {
                            "[${request.method}][${response.code}]$url"
                        }
                    }
                }
            }
            if (shouldLogRequest && debugEnabled && logBodies) {
                if (fullCapture) {
                    response.headers.headersForLog(redact = false)
                        .takeIf(String::isNotBlank)?.let { headers ->
                            safeLog {
                                debug(
                                    tag = tag,
                                    attributes = requestAttributes +
                                        LogAttribute("kind", "response_headers"),
                                ) { headers }
                            }
                        }
                    return response.newBuilder().body(
                        ObservedResponseBody(
                            delegate = response.body,
                            capture = NetworkBodyCapture(
                                tag = tag,
                                requestId = requireNotNull(requestId),
                                direction = "response",
                                byteLimit = fullBodyByteLimit,
                            ),
                        ),
                    ).build()
                } else if (!fullCapture) {
                    safeLog {
                        debug(tag = tag) {
                            if (rawCapture) {
                                response.responseBodyForLog(redact = false)
                            } else {
                                omittedBody(runCatching { response.body.contentLength() }.getOrDefault(-1L))
                            }
                        }
                    }
                }
            }

            response
        } catch (throwable: IOException) {
            if (shouldLogRequest && chain.call().isCanceled()) {
                safeLog {
                    info(tag = tag, attributes = requestAttributes) {
                        "[${request.method}][已取消]$url"
                    }
                }
            } else if (shouldLogRequest) {
                safeLog {
                    error(
                        tag = tag,
                        throwable = throwable.takeIf { rawCapture },
                        attributes = requestAttributes,
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
            }
            throw throwable
        }
    }

    private fun RequestBody?.requestBodyForLog(headers: Headers, redact: Boolean): String {
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
            .redactSensitiveText(redact)
    }

    private fun Response.responseBodyForLog(redact: Boolean): String {
        if (!headers.isPlainTextBody()) return UNREADABLE_LOG_VALUE

        return runCatching {
            peekBody(MAX_BODY_LOG_BYTES).string()
        }.getOrDefault(UNREADABLE_LOG_VALUE)
            .truncateForLog()
            .redactSensitiveText(redact)
    }

    private fun HttpUrl.toLogUrl(redact: Boolean): String =
        if (redact) {
            newBuilder()
                .username("")
                .password("")
                .encodedPath("/")
                .query(null)
                .fragment(null)
                .build()
                .toString()
        } else {
            toString()
        }

    private fun omittedBody(contentLength: Long): String =
        "$OMITTED_BODY_VALUE bytes=${contentLength.takeIf { it >= 0L } ?: "unknown"}"

    private fun okhttp3.Request.isGitHubGraphQl(): Boolean {
        return url.host == GITHUB_API_HOST && url.encodedPath == GITHUB_GRAPHQL_PATH
    }

    private fun Headers.headersForLog(redact: Boolean): String {
        if (size == 0) return ""

        return buildString {
            repeat(size) { index ->
                val name = name(index)
                if (redact && name.isRuntimeAuthenticationHeader()) return@repeat
                append(name)
                append(": ")
                append(value(index).redactHeaderValue(name, redact))
                append('\n')
            }
        }.removeSuffix("\n")
    }

    private fun Headers.isPlainTextBody(): Boolean {
        val contentEncoding = this["Content-Encoding"]
        if (!contentEncoding.isNullOrBlank() && !contentEncoding.equals("identity", true)) {
            return false
        }

        val contentType = this["Content-Type"] ?: return true
        return PLAIN_TEXT_CONTENT_TYPES.any { contentType.contains(it, ignoreCase = true) }
    }

    private fun String.redactSensitiveText(redact: Boolean): String {
        if (!redact) return this

        return SENSITIVE_JSON_FIELD_REGEX.replace(this) { matchResult ->
            "${matchResult.groupValues[1]}$REDACTED_VALUE${matchResult.groupValues[3]}"
        }
    }

    private fun String.redactHeaderValue(name: String, redact: Boolean): String {
        return if (redact && name.isSensitiveHeaderName()) {
            REDACTED_VALUE
        } else {
            this
        }
    }

    private fun String.isSensitiveHeaderName(): Boolean {
        val normalized = lowercase()
        return SENSITIVE_HEADER_NAME_PARTS.any(normalized::contains)
    }

    private fun String.isRuntimeAuthenticationHeader(): Boolean =
        equals("Authorization", ignoreCase = true)

    private fun String.truncateForLog(): String {
        return if (length <= MAX_BODY_LOG_CHARS) {
            this.ifBlank { EMPTY_LOG_VALUE }
        } else {
            take(MAX_BODY_LOG_CHARS) + "\n...<已截断>"
        }
    }

    private inline fun safeLog(action: () -> Unit) {
        // Diagnostics must not change the result of a request when its sink is unavailable.
        try {
            action()
        } catch (_: Exception) {
            // The caller's protected sink accounts for its own failures.
        }
    }

    private companion object {
        const val DEFAULT_FULL_BODY_BYTE_LIMIT = 100L * 1024L * 1024L
        const val MAX_BODY_LOG_BYTES = 4_096L
        const val MAX_BODY_LOG_CHARS = 1_024
        const val EMPTY_LOG_VALUE = "<空>"
        const val UNREADABLE_LOG_VALUE = "<不可读取>"
        const val OMITTED_BODY_VALUE = "<body omitted>"
        const val REDACTED_VALUE = "***"
        const val GITHUB_API_HOST = "api.github.com"
        const val GITHUB_GRAPHQL_PATH = "/graphql"
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
