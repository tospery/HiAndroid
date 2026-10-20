package com.tospery.github.trending

import com.tospery.github.model.core.Repo
import com.tospery.github.model.core.RepoId
import com.tospery.github.model.core.User
import com.tospery.github.model.core.UserId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsoupGitHubTrendingParserTest {
    private val parser = JsoupGitHubTrendingParser()

    @Test
    fun parseRepositoriesMapsRepositoryFields() {
        val html = """
            <html>
              <body>
                <article class="Box-row">
                  <h2>
                    <a href="/owner/repo">
                      owner / repo
                    </a>
                  </h2>
                  <p>Repository description</p>
                  <span style="background-color: #F18E33;"></span>
                  <span itemprop="programmingLanguage">Kotlin</span>
                  <a href="/owner/repo/stargazers">1,234</a>
                  <a href="/owner/repo/forks">56</a>
                  <span class="d-inline-block mr-3">
                    Built by
                    <a href="/octocat">
                      <img src="/avatars/u/1?v=4" alt="@octocat" />
                    </a>
                    <a href="/hubot">
                      <img src="https://avatars.githubusercontent.com/u/2?v=4" alt="@hubot" />
                    </a>
                  </span>
                  <span class="float-sm-right">78 stars today</span>
                </article>
              </body>
            </html>
        """.trimIndent()

        val result = parser.parseRepositories(html)
        val repoId = RepoId("trending:owner/repo")

        assertEquals(
            listOf(
                Repo(
                    id = repoId,
                    githubId = null,
                    ownerLogin = "owner",
                    name = "repo",
                    fullName = "owner/repo",
                    description = "Repository description",
                    htmlUrl = "https://github.com/owner/repo",
                    language = "Kotlin",
                    stargazersCount = 1234,
                    forksCount = 56,
                    watchersCount = null,
                    openIssuesCount = null,
                    licenseName = null,
                    isPrivate = null,
                    isFork = null,
                    updatedAt = null,
                ),
            ),
            result.repositories,
        )
        assertEquals(mapOf(repoId to 78), result.starsInPeriodByRepoId)
        assertEquals(
            listOf("octocat", "hubot"),
            result.builtByUsersByRepoId[repoId]?.map(User::login),
        )
        assertEquals(
            listOf(
                "https://github.com/avatars/u/1?v=4",
                "https://avatars.githubusercontent.com/u/2?v=4",
            ),
            result.builtByUsersByRepoId[repoId]?.map(User::avatarUrl),
        )
    }

    @Test
    fun parseRepositoriesSkipsRowsWithoutRepositoryPath() {
        val html = """
            <html>
              <body>
                <article class="Box-row">
                  <h2><a href="/only-owner">invalid</a></h2>
                </article>
              </body>
            </html>
        """.trimIndent()

        assertTrue(parser.parseRepositories(html).repositories.isEmpty())
    }

    @Test
    fun parseDevelopersMapsDeveloperFields() {
        val html = """
        <html>
          <body>
            <article class="Box-row">
              <img src="/avatars/u/1?v=4" />
              <h1>
                <a href="/octocat">Octocat</a>
              </h1>
              <article>
                <h1><a href="/octocat/hello-world">hello-world</a></h1>
                <p>Demo repository</p>
              </article>
            </article>
          </body>
        </html>
    """.trimIndent()

        val result = parser.parseDevelopers(html)
        val developerId = UserId("trending:octocat")

        assertEquals(
            listOf(
                User(
                    id = developerId,
                    githubId = null,
                    login = "octocat",
                    name = "Octocat",
                    avatarUrl = "https://github.com/avatars/u/1?v=4",
                    htmlUrl = "https://github.com/octocat",
                    bio = null,
                    company = null,
                    location = null,
                    blog = null,
                    followersCount = null,
                    followingCount = null,
                ),
            ),
            result.developers,
        )
        assertEquals(
            Repo(
                id = RepoId("trending:octocat/hello-world"),
                githubId = null,
                ownerLogin = "octocat",
                name = "hello-world",
                fullName = "octocat/hello-world",
                description = "Demo repository",
                htmlUrl = "https://github.com/octocat/hello-world",
                language = null,
                stargazersCount = null,
                forksCount = null,
                watchersCount = null,
                openIssuesCount = null,
                licenseName = null,
                isPrivate = null,
                isFork = null,
                updatedAt = null,
            ),
            result.popularRepositoryByDeveloperId[developerId],
        )
    }

    @Test
    fun parseDevelopersKeepsDeveloperWithoutPopularRepository() {
        val html = """
            <html>
              <body>
                <article class="Box-row">
                  <h1><a href="/octocat">Octocat</a></h1>
                </article>
              </body>
            </html>
        """.trimIndent()

        val result = parser.parseDevelopers(html)

        assertEquals(listOf("octocat"), result.developers.map(User::login))
        assertTrue(result.popularRepositoryByDeveloperId.isEmpty())
    }

    @Test
    fun parseDevelopersMapsLocationAndCompanyMetadata() {
        val html = """
            <html>
              <body>
                <article class="Box-row">
                  <h1><a href="/rjpower">Russell Power</a></h1>
                  <p>
                    <svg class="octicon octicon-location"></svg>
                    <span class="Truncate">
                      <span class="Truncate-text">Seattle</span>
                    </span>
                  </p>
                </article>
                <article class="Box-row">
                  <h1><a href="/alexey-milovidov">Alexey Milovidov</a></h1>
                  <p>
                    <span class="sr-only">Works for ClickHouse</span>
                    <svg class="octicon octicon-organization"></svg>
                    <span class="Truncate">
                      <span class="Truncate-text">ClickHouse</span>
                    </span>
                  </p>
                </article>
              </body>
            </html>
        """.trimIndent()

        val result = parser.parseDevelopers(html)

        assertEquals(
            "Seattle",
            result.developers.single { it.login == "rjpower" }.location,
        )
        assertEquals(
            "ClickHouse",
            result.developers.single { it.login == "alexey-milovidov" }.company,
        )
    }

    @Test
    fun parseRepositoriesAcceptsWeeklyAndMonthlyStarLabels() {
        val html = """
            <html>
              <body>
                <article class="Box-row">
                  <h2><a href="/owner/weekly">owner / weekly</a></h2>
                  <span>1.2k stars this week</span>
                </article>
                <article class="Box-row">
                  <h2><a href="/owner/monthly">owner / monthly</a></h2>
                  <span>2M stars this month</span>
                </article>
              </body>
            </html>
        """.trimIndent()

        val result = parser.parseRepositories(html)

        assertEquals(
            mapOf(
                RepoId("trending:owner/weekly") to 1_200,
                RepoId("trending:owner/monthly") to 2_000_000,
            ),
            result.starsInPeriodByRepoId,
        )
    }
}
