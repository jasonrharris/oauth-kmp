package com.baloomba.oauth.core.network

import com.baloomba.oauth.core.backendcontract.BackendErrorCode
import com.baloomba.oauth.core.backendcontract.ErrorResponse
import com.baloomba.oauth.core.backendcontract.ExchangeRequest
import com.baloomba.oauth.core.backendcontract.ExchangeResponse
import com.baloomba.oauth.core.oauth.ExchangeResult
import com.baloomba.oauth.core.oauth.TokenExchangeApi
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * [TokenExchangeApi] over Ktor. Takes [baseUrl] explicitly and never reads global config.
 * Transport failures (no network, timeouts) propagate as exceptions.
 */
public class KtorTokenExchangeApi(
    baseUrl: String,
    httpClient: HttpClient,
    allowInsecure: Boolean = false,
    requestTimeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) : TokenExchangeApi {
    init {
        require(allowInsecure || baseUrl.startsWith("https://", ignoreCase = true)) {
            "baseUrl must use https unless allowInsecure is set"
        }
    }

    private val endpoint = baseUrl.trimEnd('/') + "/api/exchange"
    private val json = Json { ignoreUnknownKeys = true }
    private val client = httpClient.config {
        expectSuccess = false
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) { this.requestTimeoutMillis = requestTimeoutMillis }
    }

    override suspend fun exchange(request: ExchangeRequest): ExchangeResult {
        val response = client.post(endpoint) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        return if (response.status.isSuccess()) {
            response.toOk()
        } else {
            response.toRejected()
        }
    }

    private suspend fun HttpResponse.toOk(): ExchangeResult {
        val parsed = try {
            json.decodeFromString(ExchangeResponse.serializer(), bodyAsText())
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
        return if (parsed != null && parsed.tokens.accessToken.isNotEmpty()) {
            ExchangeResult.Ok(parsed)
        } else {
            ExchangeResult.Rejected(BackendErrorCode.Unknown, "Malformed exchange response")
        }
    }

    private suspend fun HttpResponse.toRejected(): ExchangeResult.Rejected {
        val text = bodyAsText()
        val error = try {
            json.decodeFromString(ErrorResponse.serializer(), text)
        } catch (_: SerializationException) {
            null
        }
        return if (error != null) {
            ExchangeResult.Rejected(BackendErrorCode.fromWire(error.code), error.message)
        } else {
            ExchangeResult.Rejected(BackendErrorCode.Unknown, "HTTP ${status.value}")
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 30_000L
    }
}
