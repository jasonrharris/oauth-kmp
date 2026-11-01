package com.baloomba.oauth.core.oauth

import com.baloomba.oauth.core.backendcontract.ExchangeRequest
import com.baloomba.oauth.core.backendcontract.TokenSet
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

const val RFC_VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
const val RFC_CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"

class FakeSecureRandom(private val queue: MutableList<String> = mutableListOf()) : SecureRandom {
    val stringCalls = mutableListOf<Pair<Int, String>>()
    private var counter = 0

    override fun bytes(n: Int): ByteArray = ByteArray(n) { it.toByte() }

    override fun string(length: Int, alphabet: String): String {
        stringCalls += length to alphabet
        return if (queue.isNotEmpty()) queue.removeAt(0) else "rnd${counter++}"
    }
}

class FakeSha256 : Sha256 {
    val inputs = mutableListOf<ByteArray>()
    override fun hash(input: ByteArray): ByteArray {
        inputs += input
        return if (input.decodeToString() == RFC_VERIFIER) {
            Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).decode(RFC_CHALLENGE)
        } else {
            ByteArray(32) { 0xFF.toByte() }
        }
    }
}

class FakeClock(var now: Instant = Instant.fromEpochSeconds(1_000_000)) : Clock {
    override fun now(): Instant = now
    fun advance(by: Duration) {
        now += by
    }
}

class FakePendingAuthStore : PendingAuthStore {
    val sessions = mutableMapOf<String, PendingAuthorization>()
    override suspend fun save(session: PendingAuthorization) {
        sessions[session.state] = session
    }
    override suspend fun take(state: String): PendingAuthorization? = sessions.remove(state)
}

class FakeTokenStore : TokenStore {
    val saved = mutableMapOf<ProviderId, TokenSet>()
    override suspend fun save(provider: ProviderId, tokens: TokenSet) {
        saved[provider] = tokens
    }
    override suspend fun load(provider: ProviderId): TokenSet? = saved[provider]
    override suspend fun clear(provider: ProviderId) {
        saved.remove(provider)
    }
}

class FakeLauncher(var result: LaunchResult = LaunchResult.Cancelled) : AuthRedirectLauncher {
    val launched = mutableListOf<Pair<String, String>>()
    var onLaunch: (String) -> LaunchResult = { result }
    override suspend fun launch(authorizationUrl: String, redirectUri: String): LaunchResult {
        launched += authorizationUrl to redirectUri
        return onLaunch(authorizationUrl)
    }
}

class FakeExchangeApi(var behaviour: suspend (ExchangeRequest) -> ExchangeResult) : TokenExchangeApi {
    val requests = mutableListOf<ExchangeRequest>()
    override suspend fun exchange(request: ExchangeRequest): ExchangeResult {
        requests += request
        return behaviour(request)
    }
}
