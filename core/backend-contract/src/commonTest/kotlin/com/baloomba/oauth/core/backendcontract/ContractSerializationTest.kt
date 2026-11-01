package com.baloomba.oauth.core.backendcontract

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ContractSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun exchangeRequestOmitsNullRedirectUri() {
        val encoded = json.encodeToString(ExchangeRequest.serializer(), ExchangeRequest("spotify", "c", "v"))
        assertFalse(encoded.contains("redirectUri"))
        assertEquals("""{"provider":"spotify","code":"c","codeVerifier":"v"}""", encoded)
    }

    @Test
    fun exchangeRequestIncludesRedirectUriWhenSet() {
        val encoded = json.encodeToString(
            ExchangeRequest.serializer(),
            ExchangeRequest("spotify", "c", "v", redirectUri = "app://cb"),
        )
        assertEquals(
            """{"provider":"spotify","code":"c","codeVerifier":"v","redirectUri":"app://cb"}""",
            encoded,
        )
    }

    @Test
    fun exchangeResponseDecodesFullTokenSet() {
        val body = """{"provider":"spotify","tokens":{"accessToken":"a","refreshToken":"r","expiresIn":3600,"tokenType":"Bearer","scope":"x y"}}"""
        val response = json.decodeFromString(ExchangeResponse.serializer(), body)
        assertEquals(ExchangeResponse("spotify", TokenSet("a", "r", 3600, "Bearer", "x y")), response)
    }

    @Test
    fun tokenSetDecodesWithOnlyAccessToken() {
        val tokens = json.decodeFromString(TokenSet.serializer(), """{"accessToken":"a"}""")
        assertEquals(TokenSet("a"), tokens)
    }

    @Test
    fun errorResponseDecodes() {
        val error = json.decodeFromString(ErrorResponse.serializer(), """{"code":"invalid_request","message":"bad"}""")
        assertEquals(ErrorResponse("invalid_request", "bad"), error)
    }

    @Test
    fun errorCodeFromWireMapsKnownCodes() {
        assertEquals(BackendErrorCode.InvalidRequest, BackendErrorCode.fromWire("invalid_request"))
        assertEquals(BackendErrorCode.UnsupportedProvider, BackendErrorCode.fromWire("unsupported_provider"))
        assertEquals(BackendErrorCode.ProviderExchangeFailed, BackendErrorCode.fromWire("provider_exchange_failed"))
    }

    @Test
    fun errorCodeFromWireMapsUnrecognisedToUnknown() {
        assertEquals(BackendErrorCode.Unknown, BackendErrorCode.fromWire("something_new"))
        assertEquals(BackendErrorCode.Unknown, BackendErrorCode.fromWire(""))
    }
}
