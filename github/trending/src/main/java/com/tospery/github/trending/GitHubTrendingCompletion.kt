package com.tospery.github.trending

/**
 * GitHub 相关能力对外暴露的统一结果。
 *
 * 不直接依赖 AtlasHub 的 AppResult，避免通用 GitHub 模型库绑定具体业务项目。
 */
sealed interface GitHubTrendingResult<out DATA> {
    data class Success<out DATA>(
        val data: DATA,
    ) : GitHubTrendingResult<DATA>

    data class Failure(
        val error: GitHubTrendingError,
    ) : GitHubTrendingResult<Nothing>
}

/**
 * GitHub 相关能力可暴露给调用方的错误类型。
 */
sealed interface GitHubTrendingError {
    data class RequestFailed(
        val message: String?,
        val cause: Throwable? = null,
    ) : GitHubTrendingError

    data class InvalidHtml(
        val message: String,
        val cause: Throwable? = null,
    ) : GitHubTrendingError
}