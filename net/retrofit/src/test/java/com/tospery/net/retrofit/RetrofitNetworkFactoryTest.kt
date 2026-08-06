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
    fun factoryOkHttpClientLogsNetworkRequestWithoutQuery() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse(code = 200, body = "ok"))
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
                    .url(server.url("/repos?access_token=secret"))
                    .build(),
            ).execute().close()

            val messages = logger.entries.map { it.message }
            assertEquals(6, messages.size)
            assertEquals("[GET]${server.url("/repos")}", messages[0])
            assertEquals("[GET][请求头]\n<空>", messages[1])
            assertEquals("[GET][请求正文]\n<空>", messages[2])
            assertEquals("[GET][200]${server.url("/repos")}", messages[3])
            assertTrue(messages[4].startsWith("[GET][200][响应头]\n"))
            assertTrue(messages[4].contains("Content-Length: 2"))
            assertEquals("[GET][200][响应正文]\nok", messages[5])
            assertEquals(
                listOf(
                    LogLevel.DEBUG,
                    LogLevel.DEBUG,
                    LogLevel.DEBUG,
                    LogLevel.INFO,
                    LogLevel.DEBUG,
                    LogLevel.DEBUG,
                ),
                logger.entries.map(LogEntry::level),
            )
            assertTrue(messages.joinToString("\n").contains("secret").not())
            assertTrue(logger.entries.all { it.tag == NET_LOG_TAG })
        }
    }

    @Test
    fun factoryOkHttpClientLogsRequestAndResponseDetailsWithRedaction() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse(
                    code = 200,
                    body = """{"access_token":"server-secret","user":{"login":"tospery"}}""",
                    headers = okhttp3.Headers.headersOf("Content-Type", "application/json"),
                )
            )
            server.start()

            val logger = RecordingLogProvider()
            LogRegistry.install(logger)
            val config = RetrofitNetworkConfig(
                baseUrl = server.url("/").toString(),
                defaultHeaders = mapOf("X-Client-Id" to "higit"),
            )
            val client = RetrofitNetworkFactory.createOkHttpClient(
                config = config,
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
            assertTrue(messages[1].contains("Authorization: ***"))
            assertTrue(messages[1].contains("X-Client-Id: higit"))
            assertTrue(messages[2].contains(""""githubAccessToken":"***""""))
            assertTrue(messages[2].contains(""""client":{"platform":"android"}"""))
            assertEquals("[POST][200]${server.url("/v1/github/login")}", messages[3])
            assertTrue(messages[4].contains("Content-Type: application/json"))
            assertTrue(messages[5].contains(""""access_token":"***""""))
            assertTrue(messages[5].contains(""""user":{"login":"tospery"}"""))
            assertTrue(messages.joinToString("\n").contains("client-secret").not())
            assertTrue(messages.joinToString("\n").contains("server-secret").not())
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
    fun factoryOkHttpClientCanDisableSensitiveDataRedactionForDebugBuilds() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse(
                    code = 200,
                    body = """{"access_token":"server-secret"}""",
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
                redactSensitiveData = false,
            )

            client.newCall(
                Request.Builder()
                    .url(server.url("/v1/github/login?access_token=query-secret"))
                    .post(
                        """{"githubAccessToken":"client-secret","client":{"platform":"android"}}"""
                            .toRequestBody("application/json".toMediaType())
                    )
                    .build(),
            ).execute().close()

            val messages = logger.entries.map { it.message }
            assertEquals(
                "[POST]${server.url("/v1/github/login?access_token=query-secret")}",
                messages[0],
            )
            assertTrue(messages[2].contains(""""githubAccessToken":"client-secret""""))
            assertEquals(
                "[POST][200]${server.url("/v1/github/login?access_token=query-secret")}",
                messages[3],
            )
            assertTrue(messages[5].contains(""""access_token":"server-secret""""))
        }
    }

    @Test
    fun infoMinimumLevelDoesNotLogRequestOrResponseBodies() {
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
                        .url(server.url("/user"))
                        .build(),
                ).execute()
                .close()

            assertEquals(
                listOf("[GET][200]${server.url("/user")}"),
                logger.entries.map(LogEntry::message),
            )
            assertTrue(logger.entries.all { it.level == LogLevel.INFO })
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
    ) : LogProvider {
        val entries = mutableListOf<LogEntry>()

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
}
