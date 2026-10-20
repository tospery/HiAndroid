package com.tospery.github.trending

import com.tospery.github.model.core.DateRange
import com.tospery.github.model.core.ProgrammingLanguage
import com.tospery.github.model.core.SpokenLanguage

/**
 * GitHub Trending API 默认实现。
 *
 * 这里负责组织 URL 构建、HTML 加载和 HTML 解析流程。
 */
class DefaultGitHubTrendingApi(
    private val htmlLoader: GitHubTrendingHtmlLoader,
    private val parser: GitHubTrendingParser,
    private val urlBuilder: GitHubTrendingUrlBuilder = GitHubTrendingUrlBuilder(),
) : GitHubTrendingApi {
    override suspend fun getRepositories(
        programmingLanguage: ProgrammingLanguage?,
        spokenLanguage: SpokenLanguage?,
        dateRange: DateRange,
    ): GitHubTrendingResult<GitHubTrendingRepositories> =
        runGitHubTrendingCatching {
            val url = urlBuilder.repositoriesUrl(
                programmingLanguage = programmingLanguage,
                spokenLanguage = spokenLanguage,
                dateRange = dateRange,
            )
            parser.parseRepositories(htmlLoader.load(url))
        }

    override suspend fun getDevelopers(
        programmingLanguage: ProgrammingLanguage?,
        dateRange: DateRange,
        sponsorable: Boolean,
    ): GitHubTrendingResult<GitHubTrendingDevelopers> =
        runGitHubTrendingCatching {
            val url = urlBuilder.developersUrl(
                programmingLanguage = programmingLanguage,
                dateRange = dateRange,
                sponsorable = sponsorable,
            )
            parser.parseDevelopers(htmlLoader.load(url))
        }

    private suspend fun <DATA> runGitHubTrendingCatching(
        block: suspend () -> DATA,
    ): GitHubTrendingResult<DATA> =
        runCatching {
            block()
        }.fold(
            onSuccess = { GitHubTrendingResult.Success(it) },
            onFailure = {
                GitHubTrendingResult.Failure(
                    GitHubTrendingError.RequestFailed(
                        message = it.message,
                        cause = it,
                    ),
                )
            },
        )
}
