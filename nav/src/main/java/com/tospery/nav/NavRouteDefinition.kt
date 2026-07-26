package com.tospery.nav

@JvmInline
value class NavRouteId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "NavRouteId value must not be blank." }
    }

    override fun toString(): String = value
}

data class NavRouteDefinition(
    val id: NavRouteId,
    val path: String = id.value,
    val queryDiscriminator: NavRouteQueryDiscriminator? = null,
    val requiresAuth: Boolean = false,
    val presentation: NavPresentation = NavPresentation.SCREEN,
    val defaultForwardMode: ForwardMode = presentation.defaultForwardMode,
) {
    init {
        require(path.isNotBlank()) { "NavRouteDefinition path must not be blank." }
        if (presentation != NavPresentation.SCREEN) {
            require(defaultForwardMode == ForwardMode.PRESENT) {
                "Dialog and sheet routes must use ForwardMode.PRESENT."
            }
        }
    }
}

class NavRouteTable(
    definitions: Collection<NavRouteDefinition>,
) {
    private val registeredRoutes: List<RegisteredNavRoute>

    init {
        val compiledRoutes =
            definitions.map { definition ->
                RegisteredNavRoute(
                    definition = definition,
                    pattern = compileNavRoutePattern(definition.path),
                )
            }

        validateNoAmbiguousPatterns(compiledRoutes)

        registeredRoutes =
            compiledRoutes.sortedWith(
                compareByDescending<RegisteredNavRoute> {
                    it.pattern.literalSegmentCount
                }.thenBy {
                    it.pattern.hasCatchAllParameter
                }.thenByDescending {
                    it.pattern.segments.size
                },
            )
    }

    fun match(path: String): NavRouteMatch? {
        return match(ParsedNavRoute(path = path))
    }

    fun match(route: ParsedNavRoute): NavRouteMatch? {
        val pathSegments = parseRoutePathSegments(route.path) ?: return null

        return registeredRoutes.firstNotNullOfOrNull { registeredRoute ->
            val definition = registeredRoute.definition

            if (!definition.acceptsQueryParameters(route.queryParameters)) {
                null
            } else {
                registeredRoute.pattern
                    .match(pathSegments)
                    ?.let { pathParameters ->
                        NavRouteMatch(
                            definition = definition,
                            pathParameters = pathParameters,
                        )
                    }
            }
        }
    }

    fun findByPath(path: String): NavRouteDefinition? {
        return match(path)?.definition
    }

    fun contains(path: String): Boolean {
        return match(path) != null
    }
}

private fun NavRouteDefinition.acceptsQueryParameters(
    queryParameters: Map<String, String>,
): Boolean {
    return queryDiscriminator?.matches(queryParameters) ?: true
}

private data class RegisteredNavRoute(
    val definition: NavRouteDefinition,
    val pattern: CompiledNavRoutePattern,
)

private data class CompiledNavRoutePattern(
    val segments: List<NavRoutePatternSegment>,
) {
    val hasCatchAllParameter: Boolean =
        segments.lastOrNull() is NavRoutePatternSegment.CatchAllParameter

    val literalSegmentCount: Int =
        segments.count { segment ->
            segment is NavRoutePatternSegment.Literal
        }

    fun match(pathSegments: List<String>): Map<String, String>? {
        if (
            (!hasCatchAllParameter && segments.size != pathSegments.size) ||
            (hasCatchAllParameter && pathSegments.size < segments.size)
        ) {
            return null
        }

        val pathParameters = linkedMapOf<String, String>()

        segments.forEachIndexed { index, patternSegment ->
            when (patternSegment) {
                is NavRoutePatternSegment.Literal -> {
                    if (patternSegment.value != pathSegments[index]) {
                        return null
                    }
                }

                is NavRoutePatternSegment.Parameter -> {
                    pathParameters[patternSegment.name] = pathSegments[index]
                }

                is NavRoutePatternSegment.CatchAllParameter -> {
                    pathParameters[patternSegment.name] =
                        pathSegments
                            .subList(index, pathSegments.size)
                            .joinToString("/")
                }
            }
        }

        return pathParameters.toMap()
    }

    fun overlaps(other: CompiledNavRoutePattern): Boolean {
        val candidateSegmentCount =
            when {
                hasCatchAllParameter && other.hasCatchAllParameter ->
                    maxOf(segments.size, other.segments.size)

                hasCatchAllParameter -> other.segments.size
                other.hasCatchAllParameter -> segments.size
                segments.size == other.segments.size -> segments.size
                else -> return false
            }
        if (
            candidateSegmentCount < segments.size ||
            candidateSegmentCount < other.segments.size
        ) {
            return false
        }

        return (0 until candidateSegmentCount).all { index ->
            val left = segmentAt(index)
            val right = other.segmentAt(index)
            left !is NavRoutePatternSegment.Literal ||
                right !is NavRoutePatternSegment.Literal ||
                left.value == right.value
        }
    }

    private fun segmentAt(index: Int): NavRoutePatternSegment {
        return if (index < segments.lastIndex || !hasCatchAllParameter) {
            segments[index]
        } else {
            requireNotNull(segments.lastOrNull())
        }
    }
}

private sealed interface NavRoutePatternSegment {
    data class Literal(
        val value: String,
    ) : NavRoutePatternSegment

    data class Parameter(
        val name: String,
    ) : NavRoutePatternSegment

    /**
     * Captures one or more decoded path segments. Catch-all parameters are only valid as the
     * final pattern segment, so matching cannot change the hierarchy of an earlier segment.
     */
    data class CatchAllParameter(
        val name: String,
    ) : NavRoutePatternSegment
}

private fun compileNavRoutePattern(path: String): CompiledNavRoutePattern {
    val pathSegments =
        requireNotNull(splitRoutePathSegments(path)) {
            "Nav route pattern must contain non-empty path segments."
        }
    val patternSegments =
        pathSegments.map { segment ->
            val parameterMatch = RouteParameterPattern.matchEntire(segment)
            val catchAllParameterMatch = CatchAllRouteParameterPattern.matchEntire(segment)

            when {
                parameterMatch != null -> {
                    NavRoutePatternSegment.Parameter(
                        name = parameterMatch.groupValues[1],
                    )
                }

                catchAllParameterMatch != null -> {
                    NavRoutePatternSegment.CatchAllParameter(
                        name = catchAllParameterMatch.groupValues[1],
                    )
                }

                '{' in segment || '}' in segment -> {
                    throw IllegalArgumentException(
                        "Malformed nav route parameter segment: $segment",
                    )
                }

                else -> {
                    require(isValidDecodedNavPathSegment(segment)) {
                        "Nav route pattern contains an unsafe literal segment: $segment"
                    }
                    NavRoutePatternSegment.Literal(segment)
                }
            }
        }
    val catchAllParameterIndex =
        patternSegments.indexOfFirst { segment ->
            segment is NavRoutePatternSegment.CatchAllParameter
        }
    require(
        catchAllParameterIndex == -1 ||
            catchAllParameterIndex == patternSegments.lastIndex,
    ) {
        "Nav route catch-all parameter must be the final segment: $path"
    }
    val parameterNames =
        patternSegments.mapNotNull { segment ->
            when (segment) {
                is NavRoutePatternSegment.Parameter -> segment.name
                is NavRoutePatternSegment.CatchAllParameter -> segment.name
                is NavRoutePatternSegment.Literal -> null
            }
        }

    require(parameterNames.distinct().size == parameterNames.size) {
        "Nav route pattern contains duplicated parameter names: $path"
    }

    return CompiledNavRoutePattern(patternSegments)
}

private fun validateNoAmbiguousPatterns(routes: List<RegisteredNavRoute>) {
    routes.forEachIndexed { leftIndex, left ->
        routes
            .drop(leftIndex + 1)
            .forEach { right ->
                val hasEqualSpecificity =
                    left.pattern.literalSegmentCount ==
                        right.pattern.literalSegmentCount &&
                        left.pattern.hasCatchAllParameter ==
                        right.pattern.hasCatchAllParameter
                val overlaps = left.pattern.overlaps(right.pattern)

                require(!hasEqualSpecificity || !overlaps) {
                    "NavRouteTable contains ambiguous route patterns: " +
                        "${left.definition.path} and ${right.definition.path}."
                }
            }
    }
}

private fun parseRoutePathSegments(path: String): List<String>? {
    val segments = splitRoutePathSegments(path) ?: return null
    return buildList(segments.size) {
        segments.forEach { segment ->
            add(decodeNavPathSegmentOrNull(segment) ?: return null)
        }
    }
}

private fun splitRoutePathSegments(path: String): List<String>? {
    val normalizedPath = path.trim().trim('/')
    if (normalizedPath.isBlank()) {
        return null
    }

    return normalizedPath
        .split('/')
        .takeIf { segments ->
            segments.none(String::isBlank)
        }
}

private val RouteParameterPattern =
    Regex("""^\{([A-Za-z][A-Za-z0-9_]*)\}$""")

private val CatchAllRouteParameterPattern =
    Regex("""^\{\*([A-Za-z][A-Za-z0-9_]*)\}$""")

private val NavPresentation.defaultForwardMode: ForwardMode
    get() =
        when (this) {
            NavPresentation.SCREEN -> ForwardMode.PUSH
            NavPresentation.DIALOG,
            NavPresentation.SHEET,
            -> ForwardMode.PRESENT
        }
