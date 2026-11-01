package com.baloomba.oauth.core.oauth

import kotlin.test.Test
import kotlin.test.assertEquals

class CallbackParserTest {
    // S-CB-1
    @Test
    fun parsesSuccess() {
        assertEquals(
            CallbackResult.Success("abc", "st1"),
            CallbackParser.parse("app://cb?code=abc&state=st1"),
        )
    }

    // S-CB-2
    @Test
    fun percentDecodesValues() {
        assertEquals(
            CallbackResult.Success("a b/c+d", "s é"),
            CallbackParser.parse("https://x.test/cb?code=a%20b%2Fc%2Bd&state=s+%C3%A9"),
        )
    }

    // S-CB-3
    @Test
    fun parsesProviderError() {
        assertEquals(
            CallbackResult.ProviderError("access_denied", "User said no", "x"),
            CallbackParser.parse("app://cb?error=access_denied&error_description=User%20said%20no&state=x"),
        )
        assertEquals(
            CallbackResult.ProviderError("access_denied", null, null),
            CallbackParser.parse("app://cb?error=access_denied"),
        )
    }

    // S-CB-4
    @Test
    fun providerErrorWinsOverCode() {
        assertEquals(
            CallbackResult.ProviderError("server_error", null, "b"),
            CallbackParser.parse("app://cb?code=a&state=b&error=server_error"),
        )
    }

    // S-CB-5
    @Test
    fun malformedInputs() {
        listOf(
            "",
            "app://cb",
            "app://cb?",
            "app://cb?code=a",
            "app://cb?state=a",
            "app://cb?code=&state=b",
            "app://cb?code=a&state=",
            "not a url",
            "app://cb?code=%ZZ&state=b",
        ).forEach { assertEquals(CallbackResult.Malformed, CallbackParser.parse(it), it) }
    }

    // S-CB-6
    @Test
    fun fragmentIsIgnoredAndFlagWithoutValueTolerated() {
        assertEquals(
            CallbackResult.Success("a", "b"),
            CallbackParser.parse("app://cb?flag&code=a&state=b#frag"),
        )
    }
}
