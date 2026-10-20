# core:oauth
The heart of the library. Provider config, PKCE, authorization URL building, callback parsing, the `OAuthClient` orchestrator, and the **ports** (interfaces) it needs:
`SecureRandom`, `Sha256`, `PendingAuthStore`, `TokenStore`, `AuthRedirectLauncher`, `TokenExchangeApi`.
Depends on: `core:backend-contract` only. No platform code.
