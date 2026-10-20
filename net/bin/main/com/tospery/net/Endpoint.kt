package com.tospery.net

enum class HttpMethod {
    GET,
    POST,
    PUT,
    PATCH,
    DELETE,
    HEAD,
    OPTIONS,
}

sealed interface Endpoint {
    val value: String

    data class RelativeUrl(
        override val value: String,
    ) : Endpoint {
        init {
            require(value.isNotBlank()) { "Relative URL endpoint must not be blank." }
            require(!value.startsWith("http://") && !value.startsWith("https://")) {
                "Relative URL endpoint must not be an absolute URL."
            }
        }
    }

    data class AbsoluteUrl(
        override val value: String,
    ) : Endpoint {
        init {
            require(value.startsWith("http://") || value.startsWith("https://")) {
                "Absolute URL endpoint must start with http:// or https://."
            }
        }
    }
}