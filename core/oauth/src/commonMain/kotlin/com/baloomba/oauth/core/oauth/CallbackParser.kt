package com.baloomba.oauth.core.oauth

public sealed interface CallbackResult {
    public data class Success(val code: String, val state: String) : CallbackResult
    public data class ProviderError(val error: String, val description: String?, val state: String?) : CallbackResult
    public data object Malformed : CallbackResult
}

public object CallbackParser {
    public fun parse(url: String): CallbackResult {
        val query = url.substringBefore('#').substringAfter('?', missingDelimiterValue = "")
        if (query.isEmpty()) return CallbackResult.Malformed

        val params = mutableMapOf<String, String>()
        for (pair in query.split('&')) {
            if (pair.isEmpty()) continue
            val key = UrlEncoding.decode(pair.substringBefore('=')) ?: return CallbackResult.Malformed
            val value = UrlEncoding.decode(pair.substringAfter('=', "")) ?: return CallbackResult.Malformed
            params.putIfAbsent(key, value)
        }

        val error = params["error"]
        if (!error.isNullOrEmpty()) return CallbackResult.ProviderError(error, params["error_description"], params["state"])

        val code = params["code"]
        val state = params["state"]
        return if (!code.isNullOrEmpty() && !state.isNullOrEmpty()) {
            CallbackResult.Success(code, state)
        } else {
            CallbackResult.Malformed
        }
    }
}
