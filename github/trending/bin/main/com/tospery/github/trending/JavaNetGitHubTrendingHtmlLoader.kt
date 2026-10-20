package com.tospery.github.trending

import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 基于 JDK HttpURLConnection 的 HTML 加载器。
 *
 * 该实现让库在不依赖 Retrofit/OkHttp 的情况下也能独立使用。
 */
class JavaNetGitHubTrendingHtmlLoader : GitHubTrendingHtmlLoader {
    override suspend fun load(url: String): String =
        withContext(Dispatchers.IO) {
            loadBlocking(url)
        }

    private fun loadBlocking(url: String): String {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = DEFAULT_TIMEOUT_MILLIS
        connection.readTimeout = DEFAULT_TIMEOUT_MILLIS
        connection.setRequestProperty("Accept", "text/html")
        connection.setRequestProperty("User-Agent", DEFAULT_USER_AGENT)

        return try {
            val statusCode = connection.responseCode
            val stream =
                if (statusCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

            val body =
                stream
                    ?.bufferedReader()
                    ?.use { reader -> reader.readText() }
                    .orEmpty()
            if (statusCode !in 200..299) {
                throw IllegalStateException(
                    "GitHub Trending 请求失败，HTTP 状态码：$statusCode。",
                )
            }
            body
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 15_000
        const val DEFAULT_USER_AGENT = "tospery-github-trending"
    }
}
