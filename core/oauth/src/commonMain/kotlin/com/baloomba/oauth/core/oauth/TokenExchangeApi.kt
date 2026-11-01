package com.baloomba.oauth.core.oauth

import com.baloomba.oauth.core.backendcontract.BackendErrorCode
import com.baloomba.oauth.core.backendcontract.ExchangeRequest
import com.baloomba.oauth.core.backendcontract.ExchangeResponse

/** Port: exchanges an authorization code for tokens via the backend. Transport failures throw. */
public interface TokenExchangeApi {
    public suspend fun exchange(request: ExchangeRequest): ExchangeResult
}

public sealed interface ExchangeResult {
    public data class Ok(val response: ExchangeResponse) : ExchangeResult
    public data class Rejected(val code: BackendErrorCode, val message: String) : ExchangeResult
}
