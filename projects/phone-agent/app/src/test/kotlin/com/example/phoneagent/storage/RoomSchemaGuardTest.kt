package com.example.phoneagent.storage

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class RoomSchemaGuardTest {
    @Test
    fun `current database schema is exported and every upgrade has a registered migration`() {
        val root = repositoryRoot()
        val databaseSource = readText(root.resolve(DATABASE_SOURCE))
        val packageName =
            PACKAGE.find(databaseSource)?.groupValues?.get(1)
                ?: error("Could not read AppDatabase package")
        val databaseName =
            DATABASE_CLASS.find(databaseSource)?.groupValues?.get(1)
                ?: error("Could not read AppDatabase class name")
        val version =
            DATABASE_VERSION
                .find(databaseSource)
                ?.groupValues
                ?.get(1)
                ?.toInt()
                ?: error("Could not read AppDatabase version")
        val schemaPath = root.resolve("app/schemas/$packageName.$databaseName/$version.json")

        assertTrue("Missing exported Room schema: $schemaPath", Files.isRegularFile(schemaPath))

        val schema = readText(schemaPath)
        assertTrue("Schema version does not match AppDatabase", Regex("\"version\"\\s*:\\s*$version\\b").containsMatchIn(schema))
        assertTrue("Schema does not contain conversation_history", schema.contains("\"tableName\": \"conversation_history\""))
        assertTrue(
            "conversation_history must keep position as its primary key",
            POSITION_PRIMARY_KEY.containsMatchIn(schema),
        )

        val appSources = readAppSources(root)
        val migrations = appSources.joinToString("\n")
        assertFalse("Destructive Room migration fallback is forbidden", migrations.contains("fallbackToDestructiveMigration"))

        if (version > 1) {
            assertTrue("Room migrations must be registered with addMigrations()", ADD_MIGRATIONS.containsMatchIn(migrations))
            for (fromVersion in 1 until version) {
                assertTrue(
                    "Missing Room migration $fromVersion -> ${fromVersion + 1}",
                    migration(fromVersion, fromVersion + 1).containsMatchIn(migrations),
                )
            }
        }
    }

    private fun repositoryRoot(): Path {
        var candidate = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        while (!Files.isRegularFile(candidate.resolve(DATABASE_SOURCE))) {
            candidate = candidate.parent ?: error("Could not locate repository root from ${System.getProperty("user.dir")}")
        }
        return candidate
    }

    private fun readAppSources(root: Path): List<String> =
        Files.walk(root.resolve("app/src/main/kotlin")).use { paths ->
            val sources = mutableListOf<String>()
            paths
                .filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
                .forEach { sources += readText(it) }
            sources
        }

    private fun readText(path: Path): String = String(Files.readAllBytes(path), StandardCharsets.UTF_8)

    private fun migration(
        from: Int,
        to: Int,
    ): Regex = Regex("Migration\\s*\\(\\s*$from\\s*,\\s*$to\\s*\\)")

    private companion object {
        const val DATABASE_SOURCE = "app/src/main/kotlin/com/example/phoneagent/storage/AppDatabase.kt"

        val PACKAGE = Regex("(?m)^package\\s+([\\w.]+)")
        val DATABASE_CLASS = Regex("\\babstract\\s+class\\s+(\\w+)\\s*:")
        val DATABASE_VERSION = Regex("@Database\\([\\s\\S]*?\\bversion\\s*=\\s*(\\d+)")
        val POSITION_PRIMARY_KEY =
            Regex("\"primaryKey\"\\s*:\\s*\\{[^}]*\"columnNames\"\\s*:\\s*\\[\\s*\"position\"\\s*]")
        val ADD_MIGRATIONS = Regex("\\.addMigrations\\s*\\(")
    }
}
