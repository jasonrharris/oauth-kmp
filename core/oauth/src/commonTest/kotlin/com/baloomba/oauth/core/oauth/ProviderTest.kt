package com.baloomba.oauth.core.oauth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ProviderTest {
    // S-PROV-1
    @Test
    fun providerIdIsNormalized() {
        assertEquals("spotify", ProviderId.of("  SpotIfy ").value)
        assertEquals(ProviderId.of("a"), ProviderId.of("A"))
    }

    // S-PROV-2
    @Test
    fun blankProviderIdIsRejected() {
        assertFailsWith<IllegalArgumentException> { ProviderId.of("   ") }
    }

    // S-PROV-3
    @Test
    fun inMemoryRegistryLooksUpById() {
        val spotify = testConfig()
        val registry = InMemoryProviderRegistry(listOf(spotify))
        assertEquals(listOf(spotify), registry.all())
        assertEquals(spotify, registry.get(ProviderId.of("spotify")))
        assertNull(registry.get(ProviderId.of("other")))
    }
}

fun testConfig(
    id: String = "spotify",
    extra: Map<String, String> = emptyMap(),
    endpoint: String = "https://accounts.example.com/authorize",
    scopes: List<String> = listOf("user-read-email", "playlist-read"),
) = OAuthProviderConfig(ProviderId.of(id), "Spotify", endpoint, "client-123", scopes, extra)
