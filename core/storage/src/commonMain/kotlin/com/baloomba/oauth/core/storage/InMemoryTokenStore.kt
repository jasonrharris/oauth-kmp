package com.baloomba.oauth.core.storage

import com.baloomba.oauth.core.backendcontract.TokenSet
import com.baloomba.oauth.core.oauth.ProviderId
import com.baloomba.oauth.core.oauth.TokenStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Process-memory [TokenStore], keyed by [ProviderId]. Not persisted. */
public class InMemoryTokenStore : TokenStore {
    private val mutex = Mutex()
    private val tokens = mutableMapOf<ProviderId, TokenSet>()

    override suspend fun save(provider: ProviderId, tokens: TokenSet) {
        mutex.withLock { this.tokens[provider] = tokens }
    }

    override suspend fun load(provider: ProviderId): TokenSet? =
        mutex.withLock { tokens[provider] }

    override suspend fun clear(provider: ProviderId) {
        mutex.withLock { tokens.remove(provider) }
    }
}
