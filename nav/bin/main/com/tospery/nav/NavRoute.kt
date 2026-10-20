package com.tospery.nav

@JvmInline
value class NavRoute(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "NavRoute value must not be blank." }
    }

    override fun toString(): String = value
}