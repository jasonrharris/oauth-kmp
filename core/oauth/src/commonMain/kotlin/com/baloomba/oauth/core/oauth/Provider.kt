package com.baloomba.oauth.core.oauth

import kotlin.jvm.JvmInline

@JvmInline
public value class ProviderId private constructor(public val value: String) {
    public companion object {
        public fun of(raw: String): ProviderId {
            val normalized = raw.trim().lowercase()
            require(normalized.isNotEmpty()) { "Provider id must not be blank" }
            return ProviderId(normalized)
        }
    }
}

/** A provider is data: endpoints, client id and scopes. The flow is identical for all providers. */
public data class OAuthProviderConfig(
    val id: ProviderId,
    val displayName: String,
    val authorizationEndpoint: String,
    val clientId: String,
    val scopes: List<String>,
    val extraAuthParams: Map<String, String> = emptyMap(),
)

public interface ProviderRegistry {
    public fun all(): List<OAuthProviderConfig>
    public fun get(id: ProviderId): OAuthProviderConfig?
}

public class InMemoryProviderRegistry(configs: List<OAuthProviderConfig>) : ProviderRegistry {
    private val byId = configs.associateBy { it.id }
    private val ordered = configs.toList()

    override fun all(): List<OAuthProviderConfig> = ordered
    override fun get(id: ProviderId): OAuthProviderConfig? = byId[id]
}
