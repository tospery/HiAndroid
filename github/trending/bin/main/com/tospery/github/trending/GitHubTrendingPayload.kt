package com.tospery.github.trending

import com.tospery.github.model.core.Repo
import com.tospery.github.model.core.RepoId
import com.tospery.github.model.core.User
import com.tospery.github.model.core.UserId

/**
 * GitHub Trending 仓库页面的完整解析结果。
 *
 * repositories 继续复用 :github:model:core 的通用模型；
 * 周期内新增 star 和 Built by 用户属于本次 Trending 查询的上下文数据，
 * 因此单独按 RepoId 保存，不写入稳定的 Repo 模型。
 */
data class GitHubTrendingRepositories(
    val repositories: List<Repo>,
    val starsInPeriodByRepoId: Map<RepoId, Int> = emptyMap(),
    val builtByUsersByRepoId: Map<RepoId, List<User>> = emptyMap(),
)

/**
 * GitHub Trending 开发者页面的完整解析结果。
 *
 * developers 继续复用通用 User 模型；Popular repo 是当前榜单中的关联信息，
 * 不写入 User，避免让 Trending 特有字段污染稳定的 GitHub 用户模型。
 */
data class GitHubTrendingDevelopers(
    val developers: List<User>,
    val popularRepositoryByDeveloperId: Map<UserId, Repo> = emptyMap(),
)
