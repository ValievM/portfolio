package com.example.phoneagent.domain.tools

/** Built once at composition root from the DI multibinding `Set<Tool>`; the agent loop never sees a concrete [Tool] class. */
public class ToolRegistry(
    tools: Set<Tool>,
) {
    private val byName: Map<String, Tool>

    init {
        val duplicates =
            tools
                .groupBy { it.spec.name }
                .filterValues { it.size > 1 }
                .keys
        require(duplicates.isEmpty()) { "duplicate tool names: $duplicates" }
        byName = tools.associateBy { it.spec.name }
    }

    public val specs: List<ToolSpec> = tools.map { it.spec }

    public fun find(name: String): Tool? = byName[name]
}
