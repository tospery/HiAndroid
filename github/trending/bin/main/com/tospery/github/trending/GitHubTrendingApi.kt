package com.tospery.github.trending

import com.tospery.github.model.core.DateRange
import com.tospery.github.model.core.ProgrammingLanguage
import com.tospery.github.model.core.SpokenLanguage

/**
 * GitHub Trending 能力入口。
 *
 * 该接口属于 Trending 抓取库；GitHub 通用模型和语言字典放在 :github:model:core。
 */
interface GitHubTrendingApi {
    suspend fun getRepositories(
        programmingLanguage: ProgrammingLanguage? = null,
        spokenLanguage: SpokenLanguage? = null,
        dateRange: DateRange = DateRange.DAILY,
    ): GitHubTrendingResult<GitHubTrendingRepositories>

    suspend fun getDevelopers(
        programmingLanguage: ProgrammingLanguage? = null,
        dateRange: DateRange = DateRange.DAILY,
        sponsorable: Boolean = false,
    ): GitHubTrendingResult<GitHubTrendingDevelopers>
}
