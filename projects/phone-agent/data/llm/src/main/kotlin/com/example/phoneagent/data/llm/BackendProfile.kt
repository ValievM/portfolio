package com.example.phoneagent.data.llm

public data class BackendProfile(
    public val baseUrl: String,
    public val instructions: String = SYSTEM_PROMPT,
) {
    public companion object {
        public val ChatGptSubscription: BackendProfile =
            BackendProfile(
                baseUrl = "https://chatgpt.com/backend-api/codex",
            )
    }
}
