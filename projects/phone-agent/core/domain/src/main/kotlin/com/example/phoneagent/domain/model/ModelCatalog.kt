package com.example.phoneagent.domain.model

public sealed interface ModelCatalogResult {
    public data class Available(
        public val models: List<String>,
    ) : ModelCatalogResult

    public data object Unavailable : ModelCatalogResult
}

public interface ModelCatalog {
    public suspend fun load(): ModelCatalogResult
}
