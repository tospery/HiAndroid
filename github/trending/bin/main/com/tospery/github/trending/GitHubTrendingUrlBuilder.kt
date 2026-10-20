package com.tospery.github.trending

import com.tospery.github.model.core.ProgrammingLanguage
import com.tospery.github.model.core.SpokenLanguage
import com.tospery.github.model.core.DateRange
import java.net.URLEncoder

/**
 * GitHub Trending URL 构建器。
 *
 * 只负责把业务参数转换成 GitHub Trending 页面地址，不负责网络请求和 HTML 解析。
 */
class GitHubTrendingUrlBuilder(
    private val baseUrl: String = DEFAULT_BASE_URL,
) {
    fun repositoriesUrl(
        programmingLanguage: ProgrammingLanguage? = null,
        spokenLanguage: SpokenLanguage? = null,
        dateRange: DateRange = DateRange.DAILY,
    ): String = buildUrl(
        path =
            listOfNotNull(
                "trending",
                programmingLanguage?.id.toGitHubTrendingFilterIdOrNull(),
            ),
        query = buildMap {
            put("since", dateRange.value)
            spokenLanguage?.id
                .toGitHubTrendingFilterIdOrNull()
                ?.let { put("spoken_language_code", it) }
        },
    )

    fun developersUrl(
        programmingLanguage: ProgrammingLanguage? = null,
        dateRange: DateRange = DateRange.DAILY,
        sponsorable: Boolean = false,
    ): String = buildUrl(
        path =
            listOfNotNull(
                "trending",
                "developers",
                programmingLanguage?.id.toGitHubTrendingFilterIdOrNull(),
            ),
        query = buildMap {
            put("since", dateRange.value)
            if (sponsorable) {
                put("sponsorable", "1")
            }
        },
    )

    private fun buildUrl(
        path: List<String>,
        query: Map<String, String>,
    ): String {
        val normalizedBaseUrl = baseUrl.trimEnd('/')
        val encodedPath = path.joinToString("/") { it.encodeUrlPart() }
        val queryString = query.entries.joinToString("&") { (key, value) ->
            "${key.encodeUrlPart()}=${value.encodeUrlPart()}"
        }

        return buildString {
            append(normalizedBaseUrl)
            append("/")
            append(encodedPath)
            if (queryString.isNotBlank()) {
                append("?")
                append(queryString)
            }
        }
    }

    private fun String.encodeUrlPart(): String =
        URLEncoder.encode(this, Charsets.UTF_8.name()).replace("+", "%20")

    private fun String?.toGitHubTrendingFilterIdOrNull(): String? =
        this?.takeUnless { it == ANY_LANGUAGE_ID }

    private companion object {
        const val ANY_LANGUAGE_ID = "*"
        const val DEFAULT_BASE_URL = "https://github.com"
    }
}
