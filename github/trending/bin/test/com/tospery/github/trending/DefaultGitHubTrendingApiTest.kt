package com.tospery.github.trending

import com.tospery.github.model.core.DateRange
import com.tospery.github.model.core.ProgrammingLanguage
import com.tospery.github.model.core.Repo
import com.tospery.github.model.core.RepoId
import com.tospery.github.model.core.SpokenLanguage
import com.tospery.github.model.core.User
import com.tospery.github.model.core.UserId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultGitHubTrendingApiTest {
    @Test
    fun getRepositoriesLoadsHtmlFromBuiltUrlAndParsesRepositories() = runTest {
        val parser = FakeParser(
            repositories = listOf(
                Repo(
                    id = RepoId("trending:owner/repo"),
                    githubId = null,
                    ownerLogin = "owner",
                    name = "repo",
                    fullName = "owner/repo",
                    description = "description",
                    htmlUrl = "https://github.com/owner/repo",
                    language = "Kotlin",
                    stargazersCount = 100,
                    forksCount = 10,
                    watchersCount = null,
                    openIssuesCount = null,
                    licenseName = null,
                    isPrivate = null,
                    isFork = null,
                    updatedAt = null,
                ),
            ),
        )
        val loadedUrls = mutableListOf<String>()
        val api = DefaultGitHubTrendingApi(
            htmlLoader = GitHubTrendingHtmlLoader { url ->
                loadedUrls += url
                "<html></html>"
            },
            parser = parser,
        )

        val result = api.getRepositories(
            programmingLanguage = ProgrammingLanguage("kotlin", "Kotlin", "#F18E33"),
            spokenLanguage = SpokenLanguage("zh", "Chinese"),
            dateRange = DateRange.WEEKLY,
        )

        assertEquals(
            listOf("https://github.com/trending/kotlin?since=weekly&spoken_language_code=zh"),
            loadedUrls,
        )
        assertTrue(result is GitHubTrendingResult.Success)
        assertEquals(
            GitHubTrendingRepositories(repositories = parser.repositories),
            (result as GitHubTrendingResult.Success).data,
        )
    }

    @Test
    fun getDevelopersLoadsHtmlFromBuiltUrlAndParsesDevelopers() = runTest {
        val parser = FakeParser(
            developers = listOf(
                User(
                    id = UserId("trending:octocat"),
                    githubId = null,
                    login = "octocat",
                    name = "Octocat",
                    avatarUrl = "https://github.com/images/error/octocat_happy.gif",
                    htmlUrl = "https://github.com/octocat",
                    bio = null,
                    company = null,
                    location = null,
                    blog = null,
                    followersCount = null,
                    followingCount = null,
                ),
            ),
        )
        val loadedUrls = mutableListOf<String>()
        val api = DefaultGitHubTrendingApi(
            htmlLoader = GitHubTrendingHtmlLoader { url ->
                loadedUrls += url
                "<html></html>"
            },
            parser = parser,
        )

        val result = api.getDevelopers(
            programmingLanguage = ProgrammingLanguage("kotlin", "Kotlin", "#F18E33"),
            dateRange = DateRange.MONTHLY,
            sponsorable = true,
        )

        assertEquals(
            listOf("https://github.com/trending/developers/kotlin?since=monthly&sponsorable=1"),
            loadedUrls,
        )
        assertTrue(result is GitHubTrendingResult.Success)
        assertEquals(
            GitHubTrendingDevelopers(developers = parser.developers),
            (result as GitHubTrendingResult.Success).data,
        )
    }

    @Test
    fun getRepositoriesMapsFailureToRequestFailed() = runTest {
        val throwable = IllegalStateException("failed")
        val api = DefaultGitHubTrendingApi(
            htmlLoader = GitHubTrendingHtmlLoader { throw throwable },
            parser = FakeParser(),
        )

        val result = api.getRepositories()

        assertTrue(result is GitHubTrendingResult.Failure)
        val error = (result as GitHubTrendingResult.Failure).error
        assertTrue(error is GitHubTrendingError.RequestFailed)
        assertSame(throwable, (error as GitHubTrendingError.RequestFailed).cause)
    }

    private class FakeParser(
        val repositories: List<Repo> = emptyList(),
        val developers: List<User> = emptyList(),
    ) : GitHubTrendingParser {
        override fun parseRepositories(html: String): GitHubTrendingRepositories {
            return GitHubTrendingRepositories(repositories = repositories)
        }

        override fun parseDevelopers(html: String): GitHubTrendingDevelopers {
            return GitHubTrendingDevelopers(developers = developers)
        }
    }
}
