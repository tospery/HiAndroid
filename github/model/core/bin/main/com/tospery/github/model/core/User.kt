package com.tospery.github.model.core

/**
 * App 内部使用的用户稳定标识。
 */
@JvmInline
value class UserId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "UserId 不能为空。" }
    }

    override fun toString(): String = value
}

/**
 * GitHub 账号类型。
 *
 * GitHub REST API 可能继续扩展 `type` 字段，因此未知值必须保留为 [UNKNOWN]，
 * 不能默认误判为个人开发者账号。
 */
enum class UserAccountType {
    USER,
    ORGANIZATION,
    BOT,
    UNKNOWN,
}

/**
 * 当前登录用户领域模型。
 *
 * 这里只表达 App 需要长期使用的用户摘要；
 * GitHub REST API 的完整字段应放在 data 层 DTO 中。
 */
data class User(
    val id: UserId,
    val githubId: Long?,
    val login: String,
    val name: String?,
    val avatarUrl: String?,
    val htmlUrl: String?,
    val bio: String?,
    val company: String?,
    val location: String?,
    val blog: String?,
    val followersCount: Int?,
    val followingCount: Int?,
    val publicReposCount: Int? = null,
    val createdAt: String? = null,
    val email: String? = null,
    val accountType: UserAccountType = UserAccountType.USER,
)
