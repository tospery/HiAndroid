package com.tospery.github.trending

import com.tospery.github.model.core.ProgrammingLanguage
import com.tospery.github.model.core.SpokenLanguage
import com.tospery.github.model.core.DateRange
import org.junit.Assert.assertEquals
import org.junit.Test

class GitHubTrendingUrlBuilderTest {
    private val builder = GitHubTrendingUrlBuilder()

    @Test
    fun repositoriesUrlBuildsDefaultTrendingUrl() {
        val url = builder.repositoriesUrl()

        assertEquals(
            "https://github.com/trending?since=daily",
            url,
        )
    }

    @Test
    fun repositoriesUrlBuildsUrlWithLanguageSpokenLanguageAndDateRange() {
        val url = builder.repositoriesUrl(
            programmingLanguage = ProgrammingLanguage(id = "Kotlin", name = "Kotlin"),
            spokenLanguage = SpokenLanguage(id = "zh", name = "Chinese"),
            dateRange = DateRange.MONTHLY,
        )

        assertEquals(
            "https://github.com/trending/Kotlin?since=monthly&spoken_language_code=zh",
            url,
        )
    }

    @Test
    fun repositoriesUrlOmitsAnySpokenLanguageSentinel() {
        val url =
            builder.repositoriesUrl(
                spokenLanguage = SpokenLanguage(id = "*", name = "Any"),
            )

        assertEquals(
            "https://github.com/trending?since=daily",
            url,
        )
    }

    @Test
    fun repositoriesUrlOmitsAnyProgrammingLanguageSentinel() {
        val url =
            builder.repositoriesUrl(
                programmingLanguage = ProgrammingLanguage(id = "*", name = "Any"),
            )

        assertEquals(
            "https://github.com/trending?since=daily",
            url,
        )
    }

    @Test
    fun developersUrlBuildsUrlWithLanguageAndDateRange() {
        val url = builder.developersUrl(
            programmingLanguage = ProgrammingLanguage(id = "Kotlin", name = "Kotlin"),
            dateRange = DateRange.WEEKLY,
        )

        assertEquals(
            "https://github.com/trending/developers/Kotlin?since=weekly",
            url,
        )
    }

    @Test
    fun developersUrlOmitsAnyProgrammingLanguageSentinel() {
        val url =
            builder.developersUrl(
                programmingLanguage = ProgrammingLanguage(id = "*", name = "Any"),
            )

        assertEquals(
            "https://github.com/trending/developers?since=daily",
            url,
        )
    }

    @Test
    fun developersUrlAddsSponsorableOnlyWhenRequested() {
        val sponsorableUrl = builder.developersUrl(sponsorable = true)
        val allDevelopersUrl = builder.developersUrl(sponsorable = false)

        assertEquals(
            "https://github.com/trending/developers?since=daily&sponsorable=1",
            sponsorableUrl,
        )
        assertEquals(
            "https://github.com/trending/developers?since=daily",
            allDevelopersUrl,
        )
    }

    @Test
    fun urlBuilderEncodesSpecialCharacters() {
        val url = builder.repositoriesUrl(
            programmingLanguage = ProgrammingLanguage(id = "C++", name = "C++"),
        )

        assertEquals(
            "https://github.com/trending/C%2B%2B?since=daily",
            url,
        )
    }
}
