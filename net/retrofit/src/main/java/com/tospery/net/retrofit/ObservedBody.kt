package com.tospery.net.retrofit

import com.tospery.base.logging.LogAttribute
import com.tospery.base.logging.debug
import java.io.IOException
import java.util.UUID
import okhttp3.MediaType
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource
import okio.ForwardingSink
import okio.ForwardingSource
import okio.buffer
import okio.ByteString.Companion.toByteString

/**
 * A bounded mirror of the bytes actually sent or consumed by OkHttp. The mirror never pre-reads
 * a one-shot request or a streaming response, and a logging failure cannot fail the HTTP call.
 */
internal class NetworkBodyCapture(
    private val tag: String,
    private val requestId: String,
    private val direction: String,
    private val byteLimit: Long,
) {
    private var observedBytes = 0L
    private var capturedBytes = 0L
    private var chunkIndex = 0L
    private var limitExceeded = false
    private var loggingFailed = false
    private var finished = false

    @Synchronized
    fun record(source: Buffer, offset: Long, byteCount: Long) {
        if (finished || byteCount <= 0L) return

        observedBytes = saturatingAdd(observedBytes, byteCount)
        if (loggingFailed) return

        val available = (byteLimit - capturedBytes).coerceAtLeast(0L)
        var remaining = minOf(byteCount, available)
        var position = offset

        while (remaining > 0L) {
            val size = minOf(remaining, CHUNK_BYTES)
            val copy = Buffer()
            source.copyTo(copy, position, size)
            val base64 = copy.readByteString().base64()
            val attributes = commonAttributes() + listOf(
                LogAttribute("kind", "body_chunk"),
                LogAttribute("chunk_index", chunkIndex.toString()),
                LogAttribute("chunk_bytes", size.toString()),
                LogAttribute("body_encoding", "base64"),
            )

            if (!safeDebug(attributes, base64)) {
                loggingFailed = true
                return
            }

            capturedBytes += size
            chunkIndex++
            position += size
            remaining -= size
        }

        if (byteCount > available) limitExceeded = true
    }

    @Synchronized
    fun finish(completed: Boolean, reason: String? = null) {
        if (finished) return
        finished = true
        if (loggingFailed) return

        val state = if (completed && !limitExceeded) "complete" else "incomplete"
        val cause = when {
            limitExceeded -> "capacity_limit"
            completed -> "none"
            else -> reason ?: "interrupted"
        }
        val attributes = commonAttributes() + listOf(
            LogAttribute("kind", "body_end"),
            LogAttribute("body_state", state),
            LogAttribute("body_end_reason", cause),
            LogAttribute("observed_bytes", observedBytes.toString()),
            LogAttribute("captured_bytes", capturedBytes.toString()),
            LogAttribute("chunk_count", chunkIndex.toString()),
        )
        safeDebug(attributes, "network_body_end")
    }

    private fun commonAttributes(): List<LogAttribute> = listOf(
        LogAttribute("network_request_id", requestId),
        LogAttribute("direction", direction),
    )

    private fun safeDebug(attributes: List<LogAttribute>, message: String): Boolean =
        try {
            debug(tag = tag, attributes = attributes) { message }
            true
        } catch (_: Exception) {
            false
        }

    private fun saturatingAdd(left: Long, right: Long): Long =
        if (left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right

    companion object {
        private const val CHUNK_BYTES = 32L * 1024L

        fun newRequestId(): String = UUID.randomUUID().toString()
    }
}

internal class ObservedRequestBody(
    private val delegate: RequestBody,
    private val capture: NetworkBodyCapture,
) : RequestBody() {
    override fun contentType(): MediaType? = delegate.contentType()

    override fun contentLength(): Long = delegate.contentLength()

    override fun isDuplex(): Boolean = delegate.isDuplex()

    override fun isOneShot(): Boolean = delegate.isOneShot()

    override fun writeTo(sink: BufferedSink) {
        val forwarding = object : ForwardingSink(sink) {
            override fun write(source: Buffer, byteCount: Long) {
                var remaining = byteCount
                while (remaining > 0L) {
                    val size = minOf(remaining, WRITE_CHUNK_BYTES)
                    val copy = Buffer()
                    source.copyTo(copy, 0L, size)
                    super.write(source, size)
                    capture.record(copy, 0L, size)
                    remaining -= size
                }
            }

            override fun close() {
                try {
                    super.close()
                    capture.finish(completed = true)
                } catch (exception: IOException) {
                    capture.finish(completed = false, reason = "write_error")
                    throw exception
                }
            }
        }
        val observedSink = forwarding.buffer()

        try {
            delegate.writeTo(observedSink)
            observedSink.emit()
            // A duplex body may continue writing after writeTo returns; its sink closes later.
            if (!delegate.isDuplex()) capture.finish(completed = true)
        } catch (exception: Exception) {
            capture.finish(completed = false, reason = "write_error")
            throw exception
        }
    }

    private companion object {
        const val WRITE_CHUNK_BYTES = 32L * 1024L
    }
}

internal class ObservedResponseBody(
    private val delegate: ResponseBody,
    private val capture: NetworkBodyCapture,
) : ResponseBody() {
    override fun contentType(): MediaType? = delegate.contentType()

    override fun contentLength(): Long = delegate.contentLength()

    private val observedSource: BufferedSource by lazy {
        object : ForwardingSource(delegate.source()) {
            override fun read(sink: Buffer, byteCount: Long): Long {
                return try {
                    val offset = sink.size
                    val count = super.read(sink, byteCount)
                    when {
                        count > 0L -> capture.record(sink, offset, count)
                        count == -1L -> capture.finish(completed = true)
                    }
                    count
                } catch (exception: IOException) {
                    capture.finish(completed = false, reason = "read_error")
                    throw exception
                }
            }

            override fun close() {
                capture.finish(completed = false, reason = "closed_before_eof")
                super.close()
            }
        }.buffer()
    }

    override fun source(): BufferedSource = observedSource
}
