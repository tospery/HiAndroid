package com.tospery.net.retrofit

import com.squareup.moshi.JsonClass
import com.tospery.base.logging.LogEntry
import com.tospery.base.logging.LogLevel
import com.tospery.base.logging.LogProvider
import com.tospery.base.logging.LogRegistry
import com.tospery.base.logging.NoOpLogProvider
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.Assert.assertTrue
import retrofit2.http.Body
import retrofit2.http.POST
import org.junit.After
import java.io.ByteArrayOutputStream
import java.util.Base64
import okio.BufferedSink

class RetrofitNetworkFactoryTest {

    @After
    fun resetLogProvider() {
        LogRegistry.install(NoOpLogProvider)
    }

    private interface SampleService {
        @retrofit2.http.GET("repos")
        suspend fun repos(): String
    }

    private interface SampleBodyService {
        @POST("login")
        fun login(
            @Body body: SampleBody,
        ): Call<ResponseBody>
    }

    @JsonClass(generateAdapter = false)
    private data class SampleBody(
        val token: String,
    )

    @Test
    fun factoryCreatesOkHttpClientWithConfiguredTimeouts() {
        val config = RetrofitNetworkConfig(
            baseUrl = "https://api.github.com/",
            connectTimeoutMillis = 1_000L,
            readTimeoutMillis = 2_000L,
            writeTimeoutMillis = 3_000L,
        )

        val client = RetrofitNetworkFactory.createOkHttpClient(config)

        assertEquals(1_000, client.connectTimeoutMillis)
        assertEquals(2_000, client.readTimeoutMillis)
        assertEquals(3_000, client.writeTimeoutMillis)
    }

    @Test
    fun factoryOkHttpClientAddsConfiguredDefaultHeaders() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse(code = 200, body = "ok"))
            server.start()

            val config = RetrofitNetworkConfig(
                baseUrl = server.url("/").toString(),
                defaultHeaders = mapOf(
                    "Accept" to "application/vnd.github+json",
                    "X-GitHub-Api-Version" to "2026-03-10",
                ),
            )
            val client = RetrofitNetworkFactory.createOkHttpClient(config)

            client.newCall(
                Request.Builder()
                    .url(server.url("/repos"))
                    .build(),
            ).execute().close()

            val request = server.takeRequest()
            assertEquals("application/vnd.github+json", request.headers["Accept"])
            assertEquals("2026-03-10", request.headers["X-GitHub-Api-Version"])
        }
    }

    @Test
    fun factoryOkHttpClientDoesNotOverrideExplicitHeader() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse(code = 200, body = "ok"))
            server.start()

            val config = RetrofitNetworkConfig(
                baseUrl = server.url("/").toString(),
                defaultHeaders = mapOf("Accept" to "application/vnd.github+json"),
            )
            val client = RetrofitNetworkFactory.createOkHttpClient(config)

            client.newCall(
                Request.Builder()
                    .url(server.url("/repos"))
                    .header("Accept", "application/vnd.github.raw+json")
                    .build(),
            ).execute().close()

            assertEquals("application/vnd.github.raw+json", server.takeRequest().headers["Accept"])
        }
    }

    @Test
    fun factoryOkHttpClientLogsDebugBodiesWithoutSectionLabels() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse(code = 200, body = "ok"))
            server.start()

            val logger = RecordingLogProvider(sensitive = true)
            LogRegistry.install(logger)
            val config = RetrofitNetworkConfig(
                baseUrl = server.url("/").toString(),
            )
            val client = RetrofitNetworkFactory.createOkHttpClient(
                config = config,
                redactSensitiveData = false,
            )

            client.newCall(
                Request.Builder()
                    .url(server.url("/repos?access_token=secret"))
                    .build(),
            ).execute().close()

            val messages = logger.entries.map { it.message }
            assertEquals(3, messages.size)
            assertEquals("[GET]${server.url("/repos?access_token=secret")}", messages[0])
            assertEquals("[GET][200]${server.url("/repos?access_token=secret")}", messages[1])
            assertEquals("ok", messages[2])
            assertEquals(
                listOf(
                    LogLevel.INFO,
                    LogLevel.INFO,
                    LogLevel.DEBUG,
                ),
                logger.entries.map(LogEntry::level),
            )
            assertTrue(logger.entries.all { it.tag == NET_LOG_TAG })
        }
    }

    @Test
    fun factoryOkHttpClientLogsRawDebugRequestAndResponseDetails() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse(
                    code = 200,
                    body = """{"access_token":"server-secret","user":{"login":"tospery"}}""",
                    headers = okhttp3.Headers.headersOf("Content-Type", "application/json"),
                )
            )
            server.start()

            val logger = RecordingLogProvider(sensitive = true)
            LogRegistry.install(logger)
            val config = RetrofitNetworkConfig(
                baseUrl = server.url("/").toString(),
                defaultHeaders = mapOf("X-Client-Id" to "atlashub"),
            )
            val client = RetrofitNetworkFactory.createOkHttpClient(
                config = config,
                redactSensitiveData = false,
            )

            client.newCall(
                Request.Builder()
                    .url(server.url("/v1/github/login"))
                    .header("Authorization", "Bearer client-secret")
                    .post(
                        """{"githubAccessToken":"client-secret","client":{"platform":"android"}}"""
                            .toRequestBody("application/json".toMediaType())
                    )
                    .build(),
            ).execute().close()

            val messages = logger.entries.map { it.message }
            assertEquals("[POST]${server.url("/v1/github/login")}", messages[0])
            assertTrue(messages[1].contains("X-Client-Id: atlashub"))
            assertTrue(messages[1].contains("Authorization: Bearer client-secret"))
            assertEquals(
                """{"githubAccessToken":"client-secret","client":{"platform":"android"}}""",
                messages[2],
            )
            assertEquals("[POST][200]${server.url("/v1/github/login")}", messages[3])
            assertEquals(
                """{"access_token":"server-secret","user":{"login":"tospery"}}""",
                messages[4],
            )
        }
    }

    @Test
    fun defaultDebugLoggingKeepsCredentialsOutOfAnUnprotectedProvider() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse(code = 200, body = "{\"token\":\"response-secret\"}"))
            server.start()

            val logger = RecordingLogProvider()
            LogRegistry.install(logger)
            val client = RetrofitNetworkFactory.createOkHttpClient(
                config = RetrofitNetworkConfig(baseUrl = server.url("/").toString()),
            )

            client.newCall(
                Request.Builder()
                    .url(server.url("/login?token=query-secret"))
                    .header("Authorization", "Bearer header-secret")
                    .post("{\"token\":\"body-secret\"}".toRequestBody("application/json".toMediaType()))
                    .build(),
            ).execute().close()

            val logged = logger.entries.joinToString("\n") { it.message }
            assertTrue(logged.contains("query-secret").not())
            assertTrue(logged.contains("header-secret").not())
            assertTrue(logged.contains("body-secret").not())
            assertTrue(logged.contains("response-secret").not())
        }
    }

    @Test
    fun factoryOkHttpClientDoesNotLogGitHubGraphQlRequest() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse(
                    code = 200,
                    body = """{"data":{"user":{"contributionDays":[{"date":"2026-08-06"}]}}}""",
                    headers = okhttp3.Headers.headersOf("Content-Type", "application/json"),
                ),
            )
            server.start()

            val logger = RecordingLogProvider()
            LogRegistry.install(logger)
            val url = "http://api.github.com:${server.port}/graphql"
            val client = RetrofitNetworkFactory.createOkHttpClient(
                config = RetrofitNetworkConfig(baseUrl = server.url("/").toString()),
            ).newBuilder()
                .dns(
                    object : Dns {
                        override fun lookup(hostname: String): List<InetAddress> {
                            return if (hostname == "api.github.com") {
                                listOf(InetAddress.getByName("127.0.0.1"))
                            } else {
                                Dns.SYSTEM.lookup(hostname)
                            }
                        }
                    },
                )
                .build()

            client.newCall(
                Request.Builder()
                    .url(url)
                    .post(
                        """{"query":"query { contributionDays { date } }"}"""
                            .toRequestBody("application/json".toMediaType()),
                    )
                    .build(),
            ).execute().close()

            assertTrue(logger.entries.isEmpty())
        }
    }

    @Test
    fun protectedFullCaptureIncludesGraphQlCredentialsAndCompleteBodies() {
        MockWebServer().use { server ->
            val requestText = "request-secret".repeat(200)
            val responseText = "response-secret".repeat(400)
            server.enqueue(MockResponse(code = 200, body = responseText))
            server.start()

            val logger = RecordingLogProvider(sensitive = true)
            LogRegistry.install(logger)
            val url = "http://api.github.com:${server.port}/graphql"
            val client = RetrofitNetworkFactory.createOkHttpClient(
                config = RetrofitNetworkConfig(baseUrl = server.url("/").toString()),
                redactSensitiveData = false,
                captureFullBodies = true,
                logGraphQl = true,
            ).newBuilder().dns(
                object : Dns {
                    override fun lookup(hostname: String): List<InetAddress> =
                        if (hostname == "api.github.com") {
                            listOf(InetAddress.getByName("127.0.0.1"))
                        } else {
                            Dns.SYSTEM.lookup(hostname)
                        }
                },
            ).build()

            val body = client.newCall(
                Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer token-secret")
                    .post(requestText.toRequestBody("application/json".toMediaType()))
                    .build(),
            ).execute().use { it.body.string() }

            assertEquals(responseText, body)
            assertEquals(requestText, server.takeRequest().body?.utf8())
            assertTrue(logger.entries.any { it.message.contains("Authorization: Bearer token-secret") })
            assertEquals(requestText, logger.bodyText("request"))
            assertEquals(responseText, logger.bodyText("response"))
            assertTrue(logger.entries.any { it.attribute("body_state") == "complete" })
        }
    }

    @Test
    fun fullCaptureOptInIsIgnoredForUnprotectedProvider() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse(code = 200, body = "secret"))
            server.start()

            val logger = RecordingLogProvider()
            LogRegistry.install(logger)
            val url = "http://api.github.com:${server.port}/graphql"
            val client = RetrofitNetworkFactory.createOkHttpClient(
                config = RetrofitNetworkConfig(baseUrl = server.url("/").toString()),
                redactSensitiveData = false,
                captureFullBodies = true,
                logGraphQl = true,
            ).newBuilder().dns(
                object : Dns {
                    override fun lookup(hostname: String): List<InetAddress> =
                        if (hostname == "api.github.com") {
                            listOf(InetAddress.getByName("127.0.0.1"))
                        } else {
                            Dns.SYSTEM.lookup(hostname)
                        }
                },
            ).build()

            client.newCall(Request.Builder().url(url).build()).execute().close()

            assertTrue(logger.entries.isEmpty())
        }
    }

    @Test
    fun fullCaptureMirrorsConsumedBodyWithoutPreReadingAndMarksCapacity() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse(code = 200, body = "abcdefghij"))
            server.start()

            val logger = RecordingLogProvider(sensitive = true)
            LogRegistry.install(logger)
            val client = RetrofitNetworkFactory.createOkHttpClient(
                config = RetrofitNetworkConfig(baseUrl = server.url("/").toString()),
                redactSensitiveData = false,
                captureFullBodies = true,
                fullBodyByteLimit = 4,
            )

            client.newCall(Request.Builder().url(server.url("/body")).build()).execute().use {
                assertEquals("", logger.bodyText("response"))
                assertEquals("abcdefghij", it.body.string())
            }

            assertEquals("abcd", logger.bodyText("response"))
            val end = logger.entries.single { it.attribute("direction") == "response" &&
                it.attribute("kind") == "body_end" }
            assertEquals("incomplete", end.attribute("body_state"))
            assertEquals("capacity_limit", end.attribute("body_end_reason"))
            assertEquals("10", end.attribute("observed_bytes"))
            assertEquals("4", end.attribute("captured_bytes"))
        }
    }

    @Test
    fun fullCaptureMarksUnconsumedResponseAsInterrupted() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse(code = 200, body = "not-read"))
            server.start()

            val logger = RecordingLogProvider(sensitive = true)
            LogRegistry.install(logger)
            val client = RetrofitNetworkFactory.createOkHttpClient(
                config = RetrofitNetworkConfig(baseUrl = server.url("/").toString()),
                redactSensitiveData = false,
                captureFullBodies = true,
            )

            client.newCall(Request.Builder().url(server.url("/stream")).build()).execute().close()

            assertEquals("", logger.bodyText("response"))
            val end = logger.entries.single { it.attribute("direction") == "response" &&
                it.attribute("kind") == "body_end" }
            assertEquals("incomplete", end.attribute("body_state"))
            assertEquals("closed_before_eof", end.attribute("body_end_reason"))
        }
    }

    @Test
    fun fullCaptureObservesOneShotRequestOnlyWhenItIsSent() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse(code = 200, body = "ok"))
            server.start()

            val logger = RecordingLogProvider(sensitive = true)
            LogRegistry.install(logger)
            val client = RetrofitNetworkFactory.createOkHttpClient(
                config = RetrofitNetworkConfig(baseUrl = server.url("/").toString()),
                redactSensitiveData = false,
                captureFullBodies = true,
            )
            var writeCount = 0
            val body = object : RequestBody() {
                override fun contentType() = "application/octet-stream".toMediaType()

                override fun isOneShot() = true

                override fun writeTo(sink: BufferedSink) {
                    writeCount++
                    sink.writeUtf8("one-shot-secret")
                }
            }

            client.newCall(Request.Builder().url(server.url("/upload")).post(body).build())
                .execute().close()

            assertEquals(1, writeCount)
            assertEquals("one-shot-secret", server.takeRequest().body?.utf8())
            assertEquals("one-shot-secret", logger.bodyText("request"))
        }
    }

    @Test
    fun factoryOkHttpClientTruncatesDebugBodiesAt1024Characters() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse(
                    code = 200,
                    body = "x".repeat(1_025),
                    headers = okhttp3.Headers.headersOf("Content-Type", "application/json"),
                )
            )
            server.start()

            val logger = RecordingLogProvider()
            LogRegistry.install(logger)
            val config = RetrofitNetworkConfig(
                baseUrl = server.url("/").toString(),
            )
            val client = RetrofitNetworkFactory.createOkHttpClient(
                config = config,
            )

            client.newCall(
                Request.Builder()
                    .url(server.url("/v1/github/login"))
                    .post(
                        "y".repeat(1_025)
                            .toRequestBody("application/json".toMediaType())
                    )
                    .build(),
            ).execute().close()

            val messages = logger.entries.map { it.message }
            assertEquals("y".repeat(1_024) + "\n...<已截断>", messages[1])
            assertEquals("x".repeat(1_024) + "\n...<已截断>", messages[3])
        }
    }

    @Test
    fun infoMinimumLevelLogsOnlyReleaseRequestAndResponseLines() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse(
                    code = 200,
                    body = """{"user":{"email":"private@example.com"}}""",
                    headers = okhttp3.Headers.headersOf("Content-Type", "application/json"),
                ),
            )
            server.start()

            val logger = RecordingLogProvider(minimumLevel = LogLevel.INFO)
            LogRegistry.install(logger)
            val client =
                RetrofitNetworkFactory.createOkHttpClient(
                    config = RetrofitNetworkConfig(baseUrl = server.url("/").toString()),
                )

            client
                .newCall(
                    Request
                        .Builder()
                        .url(server.url("/user?access_token=query-secret"))
                        .build(),
                ).execute()
                .close()

            assertEquals(
                listOf(
                    "[GET]${server.url("/user")}",
                    "[GET][200]${server.url("/user")}",
                ),
                logger.entries.map(LogEntry::message),
            )
            assertTrue(logger.entries.all { it.level == LogLevel.INFO })
        }
    }

    @Test
    fun infoMinimumLevelLogsNon2xxResponseAtErrorLevel() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse(code = 500, body = "server failure"))
            server.start()

            val logger = RecordingLogProvider(minimumLevel = LogLevel.INFO)
            LogRegistry.install(logger)
            val client =
                RetrofitNetworkFactory.createOkHttpClient(
                    config = RetrofitNetworkConfig(baseUrl = server.url("/").toString()),
                )

            client
                .newCall(
                    Request
                        .Builder()
                        .url(server.url("/user?access_token=query-secret"))
                        .build(),
                ).execute()
                .close()

            assertEquals(
                listOf(
                    "[GET]${server.url("/user")}",
                    "[GET][500]${server.url("/user")}",
                ),
                logger.entries.map(LogEntry::message),
            )
            assertEquals(
                listOf(LogLevel.INFO, LogLevel.ERROR),
                logger.entries.map(LogEntry::level),
            )
        }
    }

    @Test
    fun factoryCreatesRetrofitWithConfiguredBaseUrl() {
        val config = RetrofitNetworkConfig(
            baseUrl = "https://api.github.com/",
        )

        val retrofit = RetrofitNetworkFactory.createRetrofit(config)

        assertEquals("https://api.github.com/", retrofit.baseUrl().toString())
    }

    @Test
    fun factoryCreatesRetrofitService() {
        val config = RetrofitNetworkConfig(
            baseUrl = "https://api.github.com/",
        )
        val retrofit = RetrofitNetworkFactory.createRetrofit(config)

        val service = retrofit.create(SampleService::class.java)

        assertTrue(service::class.java.name.isNotBlank())
    }

    @Test
    fun factoryCreatesBodyConverterForKotlinDataClass() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse(code = 200, body = "ok"))
            server.start()

            val config = RetrofitNetworkConfig(
                baseUrl = server.url("/").toString(),
            )
            val retrofit = RetrofitNetworkFactory.createRetrofit(config)
            val service = retrofit.create(SampleBodyService::class.java)

            service.login(SampleBody(token = "secret")).execute().body()?.close()

            assertEquals("""{"token":"secret"}""", server.takeRequest().body?.utf8())
        }
    }

    private class RecordingLogProvider(
        private val minimumLevel: LogLevel = LogLevel.VERBOSE,
        private val sensitive: Boolean = false,
    ) : LogProvider {
        val entries = mutableListOf<LogEntry>()

        override val supportsSensitiveData: Boolean get() = sensitive

        override fun isLoggable(
            level: LogLevel,
            tag: String?,
        ): Boolean = level.ordinal >= minimumLevel.ordinal

        override fun log(entry: LogEntry) {
            entries += entry
        }
    }

    private fun LogEntry.messageWithAttributes(): String {
        return message + attributes.joinToString(
            prefix = " {",
            postfix = "}",
        ) { "${it.key}=${it.value}" }
    }

    private fun LogEntry.attribute(key: String): String? =
        attributes.firstOrNull { it.key == key }?.value

    private fun RecordingLogProvider.bodyText(direction: String): String {
        val output = ByteArrayOutputStream()
        entries.filter {
            it.attribute("direction") == direction && it.attribute("kind") == "body_chunk"
        }.sortedBy { it.attribute("chunk_index")?.toLong() }.forEach {
            output.write(Base64.getDecoder().decode(it.message))
        }
        return output.toString(Charsets.UTF_8)
    }
}
