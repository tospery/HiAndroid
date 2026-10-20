package com.tospery.github.model.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubModelsTest {
    @Test
    fun programmingLanguagesContainColorFromFixedData() {
        val kotlin = ProgrammingLanguage.all()
            .first { it.name == "Kotlin" }

        assertEquals("kotlin", kotlin.id)
        assertEquals("#F18E33", kotlin.color)
    }

    @Test
    fun spokenLanguagesContainChineseAndEnglish() {
        val spokenLanguages = SpokenLanguage.all()

        assertTrue(spokenLanguages.any { it.id == "zh" && it.name == "Chinese" })
        assertTrue(spokenLanguages.any { it.id == "en" && it.name == "English" })
    }

    @Test
    fun dateRangesUseGitHubTrendingQueryValues() {
        assertEquals(
            listOf("daily", "weekly", "monthly"),
            DateRange.all().map { it.value },
        )
    }
}
