package com.example.phoneagent.data.auth

public data class AuthConfig(
    public val issuer: String,
    public val authorizationEndpoint: String,
    public val tokenEndpoint: String,
    public val clientId: String,
    public val redirectUri: String,
    public val scope: String,
    public val authorizationParameters: Map<String, String>,
) {
    public val deviceCodeEndpoint: String
        get() = "${issuer.trimEnd('/')}/deviceauth/usercode"

    public val deviceTokenEndpoint: String
        get() = "${issuer.trimEnd('/')}/deviceauth/token"

    public val deviceVerificationUrl: String
        get() = "${issuer.trimEnd('/')}/device"

    public val deviceCallbackUrl: String
        get() = "${issuer.trimEnd('/')}/deviceauth/callback"

    public companion object {
        public val ChatGptSubscription: AuthConfig =
            AuthConfig(
                issuer = "https://auth.openai.com",
                authorizationEndpoint = "https://auth.openai.com/oauth/authorize",
                tokenEndpoint = "https://auth.openai.com/oauth/token",
                clientId = "app_EMoamEEZ73f0CkXaXp7hrann",
                redirectUri = "http://localhost:1455/auth/callback",
                scope = "openid profile email offline_access api.connectors.read api.connectors.invoke",
                authorizationParameters =
                    mapOf(
                        "response_type" to "code",
                        "code_challenge_method" to "S256",
                        "id_token_add_organizations" to "true",
                        "codex_cli_simplified_flow" to "true",
                        "originator" to "codex_cli_rs",
                    ),
            )
    }
}
