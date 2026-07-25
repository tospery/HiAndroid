package com.tospery.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavRouteTableTest {
    @Test
    fun exactStaticRouteMatchesWithoutParameters() {
        val definition =
            NavRouteDefinition(
                id = NavRouteId("settings-appearance"),
                path = "settings/appearance",
            )
        val table = NavRouteTable(listOf(definition))

        val match = requireNotNull(table.match("settings/appearance"))

        assertEquals(definition, match.definition)
        assertEquals(emptyMap<String, String>(), match.pathParameters)
    }

    @Test
    fun overlayDiscriminatorSelectsDialogRoute() {
        val table = accountAndDialogRouteTable()

        val match =
            requireNotNull(
                table.match(
                    NavRoute("dialog?id=logout").parse(),
                ),
            )

        assertEquals(NavRouteId("dialog"), match.definition.id)
        assertEquals(emptyMap<String, String>(), match.pathParameters)
    }

    @Test
    fun dialogWithoutDiscriminatorMatchesAccountRoute() {
        val table = accountAndDialogRouteTable()

        val match =
            requireNotNull(
                table.match(NavRoute("dialog").parse()),
            )

        assertEquals(NavRouteId("account"), match.definition.id)
        assertEquals(
            mapOf("login" to "dialog"),
            match.pathParameters,
        )
    }

    @Test
    fun unrelatedDialogQueryMatchesAccountRoute() {
        val table = accountAndDialogRouteTable()

        val match =
            requireNotNull(
                table.match(
                    NavRoute("dialog?foo=bar").parse(),
                ),
            )

        assertEquals(NavRouteId("account"), match.definition.id)
        assertEquals(
            mapOf("login" to "dialog"),
            match.pathParameters,
        )
    }

    @Test
    fun incompleteOverlayQueryStillSelectsDialogRoute() {
        val table = accountAndDialogRouteTable()

        val match =
            requireNotNull(
                table.match(
                    NavRoute("dialog?title=MissingActions").parse(),
                ),
            )

        assertEquals(NavRouteId("dialog"), match.definition.id)
    }

    @Test
    fun singleParameterRouteExtractsLogin() {
        val definition =
            NavRouteDefinition(
                id = NavRouteId("account"),
                path = "{login}",
            )
        val table = NavRouteTable(listOf(definition))

        val match = requireNotNull(table.match("devxoul"))

        assertEquals(definition, match.definition)
        assertEquals(
            mapOf("login" to "devxoul"),
            match.pathParameters,
        )
    }

    @Test
    fun repositoryRouteExtractsOwnerAndRepo() {
        val definition =
            NavRouteDefinition(
                id = NavRouteId("repository"),
                path = "{owner}/{repo}",
            )
        val table = NavRouteTable(listOf(definition))

        val match =
            requireNotNull(
                table.match("devxoul/ReactorKit"),
            )

        assertEquals(definition, match.definition)
        assertEquals(
            mapOf(
                "owner" to "devxoul",
                "repo" to "ReactorKit",
            ),
            match.pathParameters,
        )
    }

    @Test
    fun catchAllParameterExtractsDecodedTrailingPathSegments() {
        val definition =
            NavRouteDefinition(
                id = NavRouteId("repository-file"),
                path = "{owner}/{repo}/blob/{ref}/{*path}",
            )
        val table = NavRouteTable(listOf(definition))

        val match =
            requireNotNull(
                table.match(
                    "devxoul/ReactorKit/blob/main/src/main%20code/App.kt",
                ),
            )

        assertEquals(definition, match.definition)
        assertEquals(
            mapOf(
                "owner" to "devxoul",
                "repo" to "ReactorKit",
                "ref" to "main",
                "path" to "src/main code/App.kt",
            ),
            match.pathParameters,
        )
    }

    @Test
    fun catchAllParameterRequiresAtLeastOneSafeTrailingSegment() {
        val table =
            NavRouteTable(
                definitions =
                    listOf(
                        NavRouteDefinition(
                            id = NavRouteId("repository-file"),
                            path = "{owner}/{repo}/blob/{ref}/{*path}",
                        ),
                    ),
            )

        assertNull(table.match("devxoul/ReactorKit/blob/main"))
        assertNull(table.match("devxoul/ReactorKit/blob/main/src/%2E%2E/App.kt"))
        assertNull(table.match("devxoul/ReactorKit/blob/main/src%2Fmain/App.kt"))
    }

    @Test
    fun exactRouteWinsBeforeOverlappingCatchAllFallback() {
        val exact =
            NavRouteDefinition(
                id = NavRouteId("repository-file-root"),
                path = "{owner}/{repo}/blob/{ref}/README.md",
            )
        val catchAll =
            NavRouteDefinition(
                id = NavRouteId("repository-file"),
                path = "{owner}/{repo}/blob/{ref}/{*path}",
            )
        val table = NavRouteTable(listOf(catchAll, exact))

        assertEquals(
            exact,
            table.match("devxoul/ReactorKit/blob/main/README.md")?.definition,
        )
    }

    @Test
    fun percentEncodedUtf8SegmentIsDecodedBeforeParameterExtraction() {
        val table = repositoryRouteTable()

        val match = requireNotNull(table.match("caf%C3%A9/project"))

        assertEquals(
            mapOf(
                "owner" to "café",
                "repo" to "project",
            ),
            match.pathParameters,
        )
    }

    @Test
    fun plusInPathSegmentRemainsPlus() {
        val table = repositoryRouteTable()

        val match = requireNotNull(table.match("owner+suffix/project"))

        assertEquals("owner+suffix", match.pathParameters["owner"])
    }

    @Test
    fun unsafeOrMalformedSegmentsDoNotMatchDynamicRoutes() {
        val table = repositoryRouteTable()

        listOf(
            "devxoul%2Fadmin/ReactorKit",
            "devxoul%5Cadmin/ReactorKit",
            "./ReactorKit",
            "../ReactorKit",
            "%2E/ReactorKit",
            "%2E%2E/ReactorKit",
            "devxoul//ReactorKit",
            "devxoul/%00ReactorKit",
            "devxoul/%2",
            "devxoul/%GG",
            "devxoul/%C3%28",
        ).forEach { path ->
            assertNull(table.match(path))
        }
    }

    @Test
    fun staticRouteWinsOverFullyDynamicRoute() {
        val repository =
            NavRouteDefinition(
                id = NavRouteId("repository"),
                path = "{owner}/{repo}",
            )
        val settingsProfile =
            NavRouteDefinition(
                id = NavRouteId("settings-profile"),
                path = "settings/profile",
            )
        val table =
            NavRouteTable(
                definitions =
                    listOf(
                        repository,
                        settingsProfile,
                    ),
            )

        val match =
            requireNotNull(
                table.match("settings/profile"),
            )

        assertEquals(settingsProfile, match.definition)
        assertEquals(emptyMap<String, String>(), match.pathParameters)
    }

    @Test
    fun routeWithMoreLiteralSegmentsWins() {
        val repository =
            NavRouteDefinition(
                id = NavRouteId("repository"),
                path = "{owner}/{repo}",
            )
        val settingsSection =
            NavRouteDefinition(
                id = NavRouteId("settings-section"),
                path = "settings/{section}",
            )
        val table =
            NavRouteTable(
                definitions =
                    listOf(
                        repository,
                        settingsSection,
                    ),
            )

        val match =
            requireNotNull(
                table.match("settings/appearance"),
            )

        assertEquals(settingsSection, match.definition)
        assertEquals(
            mapOf("section" to "appearance"),
            match.pathParameters,
        )
    }

    @Test
    fun differentSegmentCountDoesNotMatch() {
        val table =
            NavRouteTable(
                definitions =
                    listOf(
                        NavRouteDefinition(
                            id = NavRouteId("repository"),
                            path = "{owner}/{repo}",
                        ),
                    ),
            )

        assertNull(table.match("devxoul"))
        assertNull(table.match("devxoul/ReactorKit/issues"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun equivalentDynamicPatternsAreRejected() {
        NavRouteTable(
            definitions =
                listOf(
                    NavRouteDefinition(
                        id = NavRouteId("repository"),
                        path = "{owner}/{repo}",
                    ),
                    NavRouteDefinition(
                        id = NavRouteId("project"),
                        path = "{account}/{project}",
                    ),
                ),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun overlappingPatternsWithEqualSpecificityAreRejected() {
        NavRouteTable(
            definitions =
                listOf(
                    NavRouteDefinition(
                        id = NavRouteId("settings-section"),
                        path = "settings/{section}",
                    ),
                    NavRouteDefinition(
                        id = NavRouteId("profile-child"),
                        path = "{login}/profile",
                    ),
                ),
        )
    }

    @Test
    fun equalSpecificityWithoutOverlapIsAllowed() {
        val settingsSection =
            NavRouteDefinition(
                id = NavRouteId("settings-section"),
                path = "settings/{section}",
            )
        val trendingRange =
            NavRouteDefinition(
                id = NavRouteId("trending-range"),
                path = "trending/{range}",
            )
        val table =
            NavRouteTable(
                definitions =
                    listOf(
                        settingsSection,
                        trendingRange,
                    ),
            )

        assertEquals(
            settingsSection,
            table.match("settings/appearance")?.definition,
        )
        assertEquals(
            trendingRange,
            table.match("trending/daily")?.definition,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun malformedParameterSegmentIsRejected() {
        NavRouteTable(
            definitions =
                listOf(
                    NavRouteDefinition(
                        id = NavRouteId("invalid"),
                        path = "{owner",
                    ),
                ),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun catchAllParameterBeforeFinalSegmentIsRejected() {
        NavRouteTable(
            definitions =
                listOf(
                    NavRouteDefinition(
                        id = NavRouteId("invalid"),
                        path = "{owner}/{*path}/edit",
                    ),
                ),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicatedParameterNameIsRejected() {
        NavRouteTable(
            definitions =
                listOf(
                    NavRouteDefinition(
                        id = NavRouteId("invalid"),
                        path = "{owner}/{owner}",
                    ),
                ),
        )
    }

    private fun accountAndDialogRouteTable(): NavRouteTable {
        return NavRouteTable(
            definitions =
                listOf(
                    NavRouteDefinition(
                        id = NavRouteId("account"),
                        path = "{login}",
                    ),
                    navOverlayRouteDefinition(
                        presentation = NavPresentation.DIALOG,
                    ),
                ),
        )
    }

    private fun repositoryRouteTable(): NavRouteTable {
        return NavRouteTable(
            definitions =
                listOf(
                    NavRouteDefinition(
                        id = NavRouteId("repository"),
                        path = "{owner}/{repo}",
                    ),
                ),
        )
    }
}
