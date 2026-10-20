package com.tospery.github.trending

/**
 * GitHub Trending HTML 加载器。
 *
 * 这里故意只抽象成 URL -> HTML，避免公开 API 绑定某个具体网络库。
 */
fun interface GitHubTrendingHtmlLoader {
    suspend fun load(url: String): String
}