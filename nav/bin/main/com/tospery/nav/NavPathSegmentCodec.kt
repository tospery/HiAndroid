package com.tospery.nav

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * Encodes one decoded URL path segment using UTF-8 percent encoding.
 *
 * This function deliberately operates on a single segment. Callers must not pass path separators;
 * complete route paths should be assembled from individually encoded segments.
 */
fun encodeNavPathSegment(value: String): String {
    require(isValidDecodedNavPathSegment(value)) {
        "Nav path segment contains an unsafe value."
    }

    val bytes = requireNotNull(value.encodeUtf8StrictOrNull()) {
        "Nav path segment is not valid Unicode."
    }

    return buildString(bytes.size) {
        bytes.forEach { byte ->
            val unsignedByte = byte.toInt() and 0xFF
            if (unsignedByte.isUnreservedPathByte()) {
                append(unsignedByte.toChar())
            } else {
                append('%')
                append(HexDigits[unsignedByte ushr 4])
                append(HexDigits[unsignedByte and 0x0F])
            }
        }
    }
}

/** Decodes one URL path segment without applying form-url-encoded `+` semantics. */
internal fun decodeNavPathSegmentOrNull(value: String): String? {
    if (value.isEmpty()) {
        return null
    }

    val decodedBytes = ByteArrayOutputStream(value.length)
    var index = 0

    while (index < value.length) {
        if (value[index] == '%') {
            if (index + PercentEncodedByteLength > value.length) {
                return null
            }

            val high = value[index + 1].hexDigitOrNull() ?: return null
            val low = value[index + 2].hexDigitOrNull() ?: return null
            decodedBytes.write((high shl 4) or low)
            index += PercentEncodedByteLength
        } else {
            val nextPercentIndex =
                value.indexOf('%', startIndex = index)
                    .takeUnless { it == -1 }
                    ?: value.length
            val rawBytes =
                value
                    .substring(index, nextPercentIndex)
                    .encodeUtf8StrictOrNull()
                    ?: return null

            decodedBytes.write(rawBytes)
            index = nextPercentIndex
        }
    }

    val decodedValue = decodedBytes.toByteArray().decodeUtf8StrictOrNull() ?: return null
    return decodedValue.takeIf(::isValidDecodedNavPathSegment)
}

internal fun isValidDecodedNavPathSegment(value: String): Boolean {
    return value.isNotEmpty() &&
        value != "." &&
        value != ".." &&
        '/' !in value &&
        '\\' !in value &&
        value.none(Char::isISOControl)
}

private fun String.encodeUtf8StrictOrNull(): ByteArray? {
    return runCatching {
        val buffer =
            StandardCharsets.UTF_8
                .newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(CharBuffer.wrap(this))

        ByteArray(buffer.remaining()).also(buffer::get)
    }.getOrNull()
}

private fun ByteArray.decodeUtf8StrictOrNull(): String? {
    return runCatching {
        StandardCharsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(this))
            .toString()
    }.getOrNull()
}

private fun Int.isUnreservedPathByte(): Boolean {
    return this in 'A'.code..'Z'.code ||
        this in 'a'.code..'z'.code ||
        this in '0'.code..'9'.code ||
        this == '-'.code ||
        this == '.'.code ||
        this == '_'.code ||
        this == '~'.code
}

private fun Char.hexDigitOrNull(): Int? {
    return when (this) {
        in '0'..'9' -> this - '0'
        in 'A'..'F' -> this - 'A' + 10
        in 'a'..'f' -> this - 'a' + 10
        else -> null
    }
}

private const val PercentEncodedByteLength = 3
private const val HexDigits = "0123456789ABCDEF"
