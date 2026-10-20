package com.tospery.nav

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class ParsedNavRoute(
    val path: String,
    val queryParameters: Map<String, String> = emptyMap(),
) {
    fun query(name: String): String? = queryParameters[name]
}

fun NavRoute.parse(): ParsedNavRoute {
    val raw = value.trim()
    val path = raw.substringBefore("?").trim().trimStart('/')

    require(path.isNotBlank()) {
        "NavRoute path must not be blank."
    }

    val query = raw.substringAfter("?", missingDelimiterValue = "")
    if (query.isBlank()) {
        return ParsedNavRoute(path = path)
    }

    val parameters = query
        .split("&")
        .filter { it.isNotBlank() }
        .associate { part ->
            val name = part.substringBefore("=").decodeUrl()
            val value = part.substringAfter("=", missingDelimiterValue = "").decodeUrl()
            name to value
        }

    return ParsedNavRoute(
        path = path,
        queryParameters = parameters,
    )
}

private fun String.decodeUrl(): String {
    return URLDecoder.decode(this, StandardCharsets.UTF_8.name())
}
