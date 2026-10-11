package com.example.phoneagent.domain.tools

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolRegistryTest {
    @Test
    fun `finds a registered tool by name`() {
        val tool = fakeTool("get_local_time")
        val registry = ToolRegistry(setOf(tool))

        assertEquals(tool, registry.find("get_local_time"))
    }

    @Test
    fun `an unregistered name is not found`() {
        val registry = ToolRegistry(setOf(fakeTool("get_local_time")))

        assertNull(registry.find("set_alarm"))
    }

    @Test
    fun `exposes the spec of every registered tool`() {
        val registry = ToolRegistry(setOf(fakeTool("a"), fakeTool("b")))

        assertEquals(setOf("a", "b"), registry.specs.map { it.name }.toSet())
    }

    @Test
    fun `duplicate tool names are rejected at construction`() {
        assertThrows(IllegalArgumentException::class.java) {
            ToolRegistry(setOf(fakeTool("set_alarm"), fakeTool("set_alarm")))
        }
    }

    @Test
    fun `every registered tool has a unique name, a schema, a risk, and a non-empty description`() {
        val registry =
            ToolRegistry(
                setOf(fakeTool("get_local_time"), fakeTool("set_alarm"), fakeTool("find_files")),
            )

        registry.specs.forEach { spec ->
            assertTrue("schema must be an object with a type", spec.inputSchema.containsKey("type"))
            assertTrue("description must not be blank", spec.description.isNotBlank())
        }
        assertEquals(
            registry.specs.size,
            registry.specs
                .map { it.name }
                .distinct()
                .size,
        )
    }

    private fun fakeTool(
        name: String,
        risk: Risk = Risk.SAFE,
    ): Tool =
        object : Tool {
            override val spec =
                ToolSpec(
                    name = name,
                    description = "does something",
                    inputSchema = schema(),
                    risk = risk,
                )

            override fun describe(args: JsonObject): CallDescription = CallDescription(title = name, summary = "summary")

            override suspend fun execute(
                args: JsonObject,
                context: ToolContext,
            ): ToolResult = ToolResult.Success("ok")
        }

    private fun schema(): JsonObject = buildJsonObject { put("type", "object") }
}
