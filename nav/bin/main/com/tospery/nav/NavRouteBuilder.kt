package com.tospery.nav

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class NavRouteBuilder(
    private val path: String,
) {
    private val queryParameters = linkedMapOf<String, String>()

    fun query(
        name: String,
        value: String?,
    ): NavRouteBuilder = apply {
        if (value != null) {
            queryParameters[name] = value
        }
    }

    fun build(): NavRoute {
        val normalizedPath = path.trim().trimStart('/')

        require(normalizedPath.isNotBlank()) {
            "NavRoute path must not be blank."
        }

        if (queryParameters.isEmpty()) {
            return NavRoute(normalizedPath)
        }

        val query = queryParameters.entries.joinToString("&") { (name, value) ->
            "${name.encodeUrl()}=${value.encodeUrl()}"
        }

        return NavRoute("$normalizedPath?$query")
    }

    private fun String.encodeUrl(): String {
        // URLEncoder 使用表单编码并把空格写成 +，Navigation Compose 不会将其还原。
        return URLEncoder
            .encode(this, StandardCharsets.UTF_8.name())
            .replace("+", "%20")
    }
}

fun navRoute(
    path: String,
    builder: NavRouteBuilder.() -> Unit = {},
): NavRoute {
    return NavRouteBuilder(path)
        .apply(builder)
        .build()
}
