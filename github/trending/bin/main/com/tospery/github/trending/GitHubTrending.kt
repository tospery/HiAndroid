package com.tospery.github.trending

/**
 * GitHub Trending 库入口。
 */
object GitHubTrending {
    fun create(
        htmlLoader: GitHubTrendingHtmlLoader = JavaNetGitHubTrendingHtmlLoader(),
        parser: GitHubTrendingParser = JsoupGitHubTrendingParser(),
        urlBuilder: GitHubTrendingUrlBuilder = GitHubTrendingUrlBuilder(),
    ): GitHubTrendingApi =
        DefaultGitHubTrendingApi(
            htmlLoader = htmlLoader,
            parser = parser,
            urlBuilder = urlBuilder,
        )
}
