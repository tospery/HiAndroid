package com.tospery.nav

@JvmInline
value class UrlScheme(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "UrlScheme value must not be blank." }
        require(!value.contains("://")) { "UrlScheme value must not contain ://." }
    }

    fun normalized(): String = value.lowercase()

    override fun toString(): String = value
}

@JvmInline
value class UrlHost(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "UrlHost value must not be blank." }
        require(!value.contains("/")) { "UrlHost value must not contain /." }
    }

    fun normalized(): String = value.lowercase()

    override fun toString(): String = value
}