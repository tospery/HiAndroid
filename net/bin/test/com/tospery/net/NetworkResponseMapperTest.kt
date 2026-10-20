package com.tospery.net

import com.tospery.base.result.AppResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkResponseMapperTest {
    @Test
    fun mapperCanMapRawValueToSuccessResult() {
        val mapper = NetworkResponseMapper<String, Int> { raw ->
            AppResult.Success(raw.length)
        }

        val result = mapper.map("hello")

        assertEquals(AppResult.Success(5), result)
    }

    @Test
    fun mapperCanMapRawValueToFailureResult() {
        val mapper = NetworkResponseMapper<String, Int> {
            AppResult.Failure(InvalidDataError.InvalidResponseFormat())
        }

        val result = mapper.map("invalid")

        assertTrue(result is AppResult.Failure)
    }
}