package com.tospery.github.model.core

/**
 * App 内部使用的仓库稳定标识。
 *
 * GitHub API 仓库使用 github:数字 id；
 * Trending HTML 等没有官方 id 的来源使用 trending:owner/name。
 */
@JvmInline
value class RepoId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "RepoId 不能为空。" }
    }

    override fun toString(): String = value
}

/**
 * GitHub 仓库领域模型。
 *
 * 这里只表达 App 业务真正需要的稳定概念；
 * GitHub REST API 的完整字段应放在 data 层 DTO 中。
 */
data class Repo(
    val id: RepoId,
    val githubId: Long?,
    val ownerLogin: String,
    val name: String,
    val fullName: String,
    val description: String?,
    val htmlUrl: String?,
    val language: String?,
    val stargazersCount: Int?,
    val forksCount: Int?,
    val watchersCount: Int?,
    val openIssuesCount: Int?,
    val licenseName: String?,
    val isPrivate: Boolean?,
    val isFork: Boolean?,
    val updatedAt: String?,
    val sizeInKilobytes: Long? = null,
    val defaultBranch: String? = null,
    val subscribersCount: Int? = null,
    val licenseSpdxId: String? = null,
)
