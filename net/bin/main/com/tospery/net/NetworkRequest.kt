package com.tospery.net

typealias HttpHeaders = Map<String, String>
typealias QueryParameters = Map<String, String>

sealed interface RequestBody {
    data object Empty : RequestBody

    data class Text(
        val value: String,
        val contentType: String = "text/plain",
    ) : RequestBody

    class Bytes(
        val value: ByteArray,
        val contentType: String,
    ) : RequestBody {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Bytes) return false

            return value.contentEquals(other.value) &&
                    contentType == other.contentType
        }

        override fun hashCode(): Int {
            var result = value.contentHashCode()
            result = 31 * result + contentType.hashCode()
            return result
        }

        override fun toString(): String {
            return "Bytes(value=${value.contentToString()}, contentType=$contentType)"
        }
    }
}

data class NetworkRequest(
    val method: HttpMethod,
    val endpoint: Endpoint,
    val headers: HttpHeaders = emptyMap(),
    val queryParameters: QueryParameters = emptyMap(),
    val body: RequestBody = RequestBody.Empty,
)