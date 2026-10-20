package com.tospery.github.trending

import com.tospery.github.model.core.Repo
import com.tospery.github.model.core.RepoId
import com.tospery.github.model.core.User
import com.tospery.github.model.core.UserId
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * 基于 jsoup 的 GitHub Trending HTML 解析器。
 *
 * GitHub Trending 是网页而不是稳定 API，所以选择较宽松的选择器，降低页面微调导致解析失败的概率。
 */
class JsoupGitHubTrendingParser : GitHubTrendingParser {
    override fun parseRepositories(html: String): GitHubTrendingRepositories {
        val document = Jsoup.parse(html, GITHUB_BASE_URL)
        val repositories = mutableListOf<Repo>()
        val starsInPeriodByRepoId = linkedMapOf<RepoId, Int>()
        val builtByUsersByRepoId = linkedMapOf<RepoId, List<User>>()

        document.select("article.Box-row").forEach { article ->
            val repository = article.parseRepository() ?: return@forEach
            repositories += repository
            article.parseStarsInPeriod()?.let { starsInPeriod ->
                starsInPeriodByRepoId[repository.id] = starsInPeriod
            }
            val builtByUsers = article.parseBuiltByUsers()
            if (builtByUsers.isNotEmpty()) {
                builtByUsersByRepoId[repository.id] = builtByUsers
            }
        }

        return GitHubTrendingRepositories(
            repositories = repositories,
            starsInPeriodByRepoId = starsInPeriodByRepoId,
            builtByUsersByRepoId = builtByUsersByRepoId,
        )
    }

    override fun parseDevelopers(html: String): GitHubTrendingDevelopers {
        val document = Jsoup.parse(html, GITHUB_BASE_URL)
        val developers = mutableListOf<User>()
        val popularRepositoryByDeveloperId = linkedMapOf<UserId, Repo>()

        document.select("article.Box-row").forEach { article ->
            val developer = article.parseDeveloper() ?: return@forEach
            developers += developer
            article.parsePopularRepository()?.let { repository ->
                popularRepositoryByDeveloperId[developer.id] = repository
            }
        }

        return GitHubTrendingDevelopers(
            developers = developers,
            popularRepositoryByDeveloperId = popularRepositoryByDeveloperId,
        )
    }

    private fun Element.parseRepository(): Repo? {
        val repoLink = selectFirst("h2 a") ?: return null
        val path = repoLink.attr("href").trim('/')
        val parts = path.split("/")
        if (parts.size < 2) return null

        val owner = parts[0].trim()
        val name = parts[1].trim()
        if (owner.isBlank() || name.isBlank()) return null

        val description = selectFirst("p")?.text()?.takeIf { it.isNotBlank() }
        val languageElement = selectFirst("[itemprop=programmingLanguage]")
        val language = languageElement?.text()?.takeIf { it.isNotBlank() }
        val stars = select("a[href=/$owner/$name/stargazers]").firstOrNull()?.text()?.toCompactIntOrNull()
        val forks = select("a[href=/$owner/$name/forks]").firstOrNull()?.text()?.toCompactIntOrNull()

        return Repo(
            id = RepoId("trending:$owner/$name"),
            githubId = null,
            ownerLogin = owner,
            name = name,
            fullName = "$owner/$name",
            description = description,
            htmlUrl = "$GITHUB_BASE_URL/$owner/$name",
            language = language,
            stargazersCount = stars,
            forksCount = forks,
            watchersCount = null,
            openIssuesCount = null,
            licenseName = null,
            isPrivate = null,
            isFork = null,
            updatedAt = null,
        )
    }

    private fun Element.parseDeveloper(): User? {
        val userLink =
            select("h1 a, h2 a")
                .firstOrNull { link -> link.userLoginOrNull() != null }
                ?: return null
        val username = userLink.userLoginOrNull() ?: return null
        val displayName = userLink.text().trim().takeIf { it.isNotBlank() }
        val avatarUrl =
            selectFirst("img.avatar-user, img")
                ?.absUrl("src")
                ?.takeIf { it.isNotBlank() }
        val company = parseDeveloperMetadata(ORGANIZATION_ICON_CLASS)
        val location = parseDeveloperMetadata(LOCATION_ICON_CLASS)

        return User(
            id = UserId("trending:$username"),
            githubId = null,
            login = username,
            name = displayName,
            avatarUrl = avatarUrl,
            htmlUrl = "$GITHUB_BASE_URL/$username",
            bio = null,
            company = company,
            location = location,
            blog = null,
            followersCount = null,
            followingCount = null,
        )
    }

    private fun Element.parseDeveloperMetadata(iconClass: String): String? =
        selectFirst("svg.$iconClass")
            ?.parent()
            ?.selectFirst(".Truncate-text")
            ?.text()
            ?.trim()
            ?.takeIf(String::isNotEmpty)

    private fun Element.parsePopularRepository(): Repo? {
        val repoLink =
            select("article h1 a[href], article h2 a[href]")
                .firstOrNull { link -> link.repositoryPathOrNull() != null }
                ?: return null
        val repositoryPath = repoLink.repositoryPathOrNull() ?: return null
        val repositoryArticle =
            repoLink.parents().firstOrNull { parent -> parent.tagName() == "article" }
        val description =
            repositoryArticle
                ?.selectFirst("div.f6.color-fg-muted.mt-1, p")
                ?.text()
                ?.takeIf { it.isNotBlank() }

        return Repo(
            id = RepoId("trending:${repositoryPath.owner}/${repositoryPath.name}"),
            githubId = null,
            ownerLogin = repositoryPath.owner,
            name = repositoryPath.name,
            fullName = "${repositoryPath.owner}/${repositoryPath.name}",
            description = description,
            htmlUrl = "$GITHUB_BASE_URL/${repositoryPath.owner}/${repositoryPath.name}",
            language = null,
            stargazersCount = null,
            forksCount = null,
            watchersCount = null,
            openIssuesCount = null,
            licenseName = null,
            isPrivate = null,
            isFork = null,
            updatedAt = null,
        )
    }

    private fun Element.parseStarsInPeriod(): Int? {
        return select("span")
            .asSequence()
            .map(Element::text)
            .mapNotNull { text ->
                STARS_IN_PERIOD_REGEX
                    .find(text)
                    ?.groupValues
                    ?.get(1)
                    ?.toCompactIntOrNull()
            }
            .firstOrNull()
    }

    private fun Element.parseBuiltByUsers(): List<User> {
        val builtByContainer =
            select("span")
                .firstOrNull { span ->
                    span.ownText().contains(BUILT_BY_LABEL, ignoreCase = true) &&
                        span.selectFirst("a[href] img") != null
                }
                ?: return emptyList()

        return builtByContainer
            .select("a[href]")
            .mapNotNull { link ->
                val username = link.userLoginOrNull() ?: return@mapNotNull null
                val avatarUrl =
                    link.selectFirst("img")
                        ?.absUrl("src")
                        ?.takeIf(String::isNotBlank)

                User(
                    id = UserId("trending:$username"),
                    githubId = null,
                    login = username,
                    name = null,
                    avatarUrl = avatarUrl,
                    htmlUrl =
                        link.absUrl("href").takeIf(String::isNotBlank)
                            ?: "$GITHUB_BASE_URL/$username",
                    bio = null,
                    company = null,
                    location = null,
                    blog = null,
                    followersCount = null,
                    followingCount = null,
                )
            }.distinctBy(User::login)
    }

    private fun Element.userLoginOrNull(): String? {
        val path = attr("href").substringBefore('?').trim('/')
        return path.takeIf { it.isNotBlank() && '/' !in it }
    }

    private fun Element.repositoryPathOrNull(): RepositoryPath? {
        val parts =
            attr("href")
                .substringBefore('?')
                .trim('/')
                .split('/')
        if (parts.size != 2) return null

        val owner = parts[0].trim()
        val name = parts[1].trim()
        if (owner.isBlank() || name.isBlank()) return null

        return RepositoryPath(owner = owner, name = name)
    }

    private fun String.toCompactIntOrNull(): Int? {
        val normalized = trim()
            .replace(",", "")
            .lowercase()

        return when {
            normalized.endsWith("k") -> {
                val value = normalized.removeSuffix("k").toDoubleOrNull() ?: return null
                (value * 1_000).toInt()
            }
            normalized.endsWith("m") -> {
                val value = normalized.removeSuffix("m").toDoubleOrNull() ?: return null
                (value * 1_000_000).toInt()
            }
            else -> normalized.toIntOrNull()
        }
    }

    private data class RepositoryPath(
        val owner: String,
        val name: String,
    )

    private companion object {
        const val GITHUB_BASE_URL = "https://github.com"
        const val BUILT_BY_LABEL = "Built by"
        const val LOCATION_ICON_CLASS = "octicon-location"
        const val ORGANIZATION_ICON_CLASS = "octicon-organization"
        val STARS_IN_PERIOD_REGEX =
            Regex(
                pattern = "([\\d,]+(?:\\.\\d+)?[kKmM]?)\\s+stars?\\s+(?:today|this\\s+week|this\\s+month)",
                option = RegexOption.IGNORE_CASE,
            )
    }
}
