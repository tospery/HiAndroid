package com.tospery.net

import com.tospery.base.result.AppResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkEnvelopeMappingTest {
    @Test
    fun requiredDataEnvelopeMapsSuccessCodeToSuccessResult() {
        val envelope = ApiEnvelope(
            code = "200",
            message = "成功",
            data = "ok",
        )

        val result = envelope.toRequiredDataNetworkResult(isSuccessCode = { it == "200" })

        assertEquals(AppResult.Success("ok"), result)
    }

    @Test
    fun requiredDataEnvelopeMapsNullDataToInvalidResponseFormat() {
        val envelope = ApiEnvelope<String>(
            code = "200",
            message = "成功",
            data = null,
        )

        val result = envelope.toRequiredDataNetworkResult(isSuccessCode = { it == "200" })

        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is InvalidDataError.InvalidResponseFormat)
    }

    @Test
    fun emptyEnvelopeMapsSuccessWithoutDataToUnitSuccess() {
        val envelope = ApiEnvelope<String>(
            code = "200",
            message = "成功",
            data = null,
        )

        val result = envelope.toEmptyNetworkResult(isSuccessCode = { it == "200" })

        assertEquals(AppResult.Success(Unit), result)
    }

    @Test
    fun envelopeMapsBusinessCodeToBusinessFailure() {
        val envelope = ApiEnvelope<String>(
            code = "40101",
            message = "登录已过期",
            data = null,
        )

        val result = envelope.toEmptyNetworkResult(isSuccessCode = { it == "200" })

        assertEquals(
            AppResult.Failure(
                ServerError.BusinessFailure(
                    code = "40101",
                    message = "登录已过期",
                    debugMessage = "登录已过期",
                ),
            ),
            result,
        )
    }

    @Test
    fun pagedEnvelopeAllowsEmptyListByDefault() {
        val envelope = PagedEnvelope<String>(
            hasNext = false,
            count = 0,
            items = emptyList(),
        )

        val result = envelope.toNetworkResult()

        assertEquals(AppResult.Success(envelope), result)
    }

    @Test
    fun pagedEnvelopeCanTreatEmptyListAsFailure() {
        val envelope = PagedEnvelope<String>(
            hasNext = false,
            count = 0,
            items = emptyList(),
        )

        val result = envelope.toNetworkResult(allowEmpty = false)

        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is InvalidDataError.EmptyList)
    }

    @Test
    fun envelopeCanUseCustomBusinessErrorMapper() {
        val envelope = ApiEnvelope<String>(
            code = "40101",
            message = "登录已过期",
            data = null,
        )

        val result = envelope.toEmptyNetworkResult(
            isSuccessCode = { it == "200" },
            businessErrorMapper = BusinessErrorMapper { _, message ->
                AuthError.SessionExpired(debugMessage = message)
            },
        )

        assertEquals(
            AppResult.Failure(
                AuthError.SessionExpired(debugMessage = "登录已过期"),
            ),
            result,
        )
    }
}
