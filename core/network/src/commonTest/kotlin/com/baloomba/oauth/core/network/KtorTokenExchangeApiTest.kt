package com.baloomba.oauth.core.network

import com.baloomba.oauth.core.backendcontract.BackendErrorCode
import com.baloomba.oauth.core.backendcontract.ExchangeRequest
import com.baloomba.oauth.core.backendcontract.ExchangeResponse
import com.baloomba.oauth.core.backendcontract.TokenSet
import com.baloomba.oauth.core.oauth.ExchangeResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class KtorTokenExchangeApiTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val request = ExchangeRequest("spotify", "code-1", "verifier-1", "app://cb")

    private fun api(baseUrl: String = "https://api.example.com", handler: MockRequestHandler): KtorTokenExchangeApi =
        KtorTokenExchangeApi(baseUrl, HttpClient(MockEngine(handler)))

    @Test
    fun postsJsonToApiExchangeUnderBaseUrl() = runTest {
        var method: HttpMethod? = null
        var url: String? = null
        var contentType: String? = null
        var body: String? = null
        val api = api("https://api.example.com/") { req ->
            method = req.method
            url = req.url.toString()
            contentType = req.body.contentType?.toString()
            body = req.body.toByteArray().decodeToString()
            respond(OK_BODY, HttpStatusCode.OK, jsonHeaders)
        }

        api.exchange(request)

        assertEquals(HttpMethod.Post, method)
        assertEquals("https://api.example.com/api/exchange", url)
        assertEquals("application/json", contentType)
        assertEquals(
            """{"provider":"spotify","code":"code-1","codeVerifier":"verifier-1","redirectUri":"app://cb"}""",
            body,
        )
    }

    @Test
    fun successMapsToOk() = runTest {
        val api = api { respond(OK_BODY, HttpStatusCode.OK, jsonHeaders) }

        val result = api.exchange(request)

        assertEquals(
            ExchangeResult.Ok(ExchangeResponse("spotify", TokenSet("at", "rt", 3600, "Bearer", "a b"))),
            result,
        )
    }

    @Test
    fun badRequestMapsToRejectedInvalidRequest() = runTest {
        val api = api {
            respond("""{"code":"invalid_request","message":"missing code"}""", HttpStatusCode.BadRequest, jsonHeaders)
        }

        assertEquals(
            ExchangeResult.Rejected(BackendErrorCode.InvalidRequest, "missing code"),
            api.exchange(request),
        )
    }

    @Test
    fun unsupportedProviderMapsToRejected() = runTest {
        val api = api {
            respond("""{"code":"unsupported_provider","message":"nope"}""", HttpStatusCode.BadRequest, jsonHeaders)
        }

        assertEquals(
            ExchangeResult.Rejected(BackendErrorCode.UnsupportedProvider, "nope"),
            api.exchange(request),
        )
    }

    @Test
    fun badGatewayMapsToProviderExchangeFailed() = runTest {
        val api = api {
            respond(
                """{"code":"provider_exchange_failed","message":"invalid_grant"}""",
                HttpStatusCode.BadGateway,
                jsonHeaders,
            )
        }

        assertEquals(
            ExchangeResult.Rejected(BackendErrorCode.ProviderExchangeFailed, "invalid_grant"),
            api.exchange(request),
        )
    }

    @Test
    fun unrecognisedErrorCodeMapsToUnknown() = runTest {
        val api = api { respond("""{"code":"brand_new","message":"m"}""", HttpStatusCode.BadRequest, jsonHeaders) }

        assertEquals(ExchangeResult.Rejected(BackendErrorCode.Unknown, "m"), api.exchange(request))
    }

    @Test
    fun nonJsonErrorBodyMapsToUnknownWithStatusMessage() = runTest {
        val api = api { respond("<html>oops</html>", HttpStatusCode.InternalServerError) }

        val result = assertIs<ExchangeResult.Rejected>(api.exchange(request))
        assertEquals(BackendErrorCode.Unknown, result.code)
        assertEquals("HTTP 500", result.message)
    }

    @Test
    fun transportFailurePropagatesAsException() = runTest {
        val api = api { throw IllegalStateException("offline") }

        assertFailsWith<IllegalStateException> { api.exchange(request) }
    }

    @Test
    fun malformedSuccessBodyMapsToRejectedUnknown() = runTest {
        val api = api { respond("""{"nope":1}""", HttpStatusCode.OK, jsonHeaders) }

        assertEquals(BackendErrorCode.Unknown, assertIs<ExchangeResult.Rejected>(api.exchange(request)).code)
    }

    @Test
    fun emptyAccessTokenMapsToRejectedUnknown() = runTest {
        val api = api {
            respond("""{"provider":"spotify","tokens":{"accessToken":""}}""", HttpStatusCode.OK, jsonHeaders)
        }

        assertEquals(BackendErrorCode.Unknown, assertIs<ExchangeResult.Rejected>(api.exchange(request)).code)
    }

    @Test
    fun httpBaseUrlIsRejectedUnlessInsecureAllowed() {
        assertFailsWith<IllegalArgumentException> {
            KtorTokenExchangeApi("http://api.example.com", HttpClient(MockEngine { respond("") }))
        }
        KtorTokenExchangeApi(
            "http://localhost:8080",
            HttpClient(MockEngine { respond("") }),
            allowInsecure = true,
        )
    }

    @Test
    fun secretsAreRedactedInToString() {
        assertEquals(false, request.toString().contains("code-1"))
        assertEquals(false, request.toString().contains("verifier-1"))
    }

    private companion object {
        const val OK_BODY =
            """{"provider":"spotify","tokens":{"accessToken":"at","refreshToken":"rt","expiresIn":3600,"tokenType":"Bearer","scope":"a b"}}"""
    }
}
