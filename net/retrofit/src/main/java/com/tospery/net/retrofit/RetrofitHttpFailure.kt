package com.tospery.net.retrofit

import com.squareup.moshi.JsonReader
import com.tospery.net.ServerError
import okio.Buffer
import retrofit2.HttpException
import retrofit2.Response

/** 将 Retrofit HTTP 失败映射为保留服务端展示文案的统一错误。 */
fun HttpException.toHttpFailure(): ServerError.HttpFailure =
    ServerError.HttpFailure(
        statusCode = code(),
        message = response()?.apiErrorMessage(),
        debugMessage = message,
        cause = this,
    )

/**
 * 将直接返回 [Response] 的接口失败映射为统一错误。
 *
 * 这类接口不会抛出 [HttpException]，因此也必须在此读取错误 body 的 `message`。
 */
fun Response<*>.toHttpFailure(): ServerError.HttpFailure =
    ServerError.HttpFailure(
        statusCode = code(),
        message = apiErrorMessage(),
        debugMessage = message(),
    )

private fun Response<*>.apiErrorMessage(): String? =
    runCatching {
        errorBody()
            ?.string()
            ?.let(::parseApiErrorMessage)
    }.getOrNull()

internal fun parseApiErrorMessage(errorBody: String): String? =
    runCatching {
        JsonReader.of(Buffer().writeUtf8(errorBody)).use { reader ->
            if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) {
                return@use null
            }

            var message: String? = null
            reader.beginObject()
            while (reader.hasNext()) {
                if (reader.nextName() == API_ERROR_MESSAGE_FIELD &&
                    reader.peek() == JsonReader.Token.STRING
                ) {
                    message = reader.nextString().trim().takeIf(String::isNotEmpty)
                } else {
                    reader.skipValue()
                }
            }
            reader.endObject()
            message
        }
    }.getOrNull()

private const val API_ERROR_MESSAGE_FIELD = "message"
