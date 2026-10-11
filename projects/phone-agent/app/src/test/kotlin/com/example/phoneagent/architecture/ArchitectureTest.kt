package com.example.phoneagent.architecture

import com.lemonappdev.konsist.api.Konsist
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ArchitectureTest {
    private val productionFiles =
        Konsist
            .scopeFromProject()
            .files
            .filter { "/src/main/" in it.path }

    @Test
    fun `production code does not use blocking or unstructured concurrency`() {
        val violations =
            productionFiles.filter { file ->
                file.imports.any {
                    it.name == "kotlinx.coroutines.GlobalScope" ||
                        it.name == "kotlinx.coroutines.runBlocking" ||
                        it.name == "java.lang.Thread.sleep"
                }
            }

        assertTrue("Forbidden concurrency in: ${violations.map { it.path }}", violations.isEmpty())
    }

    @Test
    fun `production code uses AppLogger instead of Android Log`() {
        // AndroidLogcatLogger is the one sanctioned AppLogger implementation that is allowed to
        // touch android.util.Log; everything else must go through AppLogger.
        val violations =
            productionFiles
                .filter { file -> file.imports.any { it.name == "android.util.Log" } }
                .filterNot { it.path.endsWith("/logging/AndroidLogcatLogger.kt") }

        assertTrue("Direct Android Log in: ${violations.map { it.path }}", violations.isEmpty())
    }

    @Test
    fun `core domain does not import platform network adapters or dependency injection libraries`() {
        val forbiddenPrefixes = listOf("android.", "androidx.", "okhttp3.", "dagger.", "com.example.phoneagent.data.")
        val coreViolations =
            productionFiles
                .filter { "/core/domain/" in it.path }
                .filter { file -> file.imports.any { import -> forbiddenPrefixes.any(import.name::startsWith) } }
        val llmViolations =
            productionFiles
                .filter { "/data/llm/" in it.path }
                .filter { file ->
                    file.imports.any { import ->
                        listOf("android.", "androidx.", "dagger.", "com.example.phoneagent.data.auth.")
                            .any(import.name::startsWith)
                    }
                }

        assertTrue("Forbidden core imports in: ${coreViolations.map { it.path }}", coreViolations.isEmpty())
        assertTrue("Forbidden LLM adapter imports in: ${llmViolations.map { it.path }}", llmViolations.isEmpty())
    }

    @Test
    fun `ui does not import adapter implementations`() {
        val violations =
            productionFiles
                .filter { "/app/" in it.path && "/ui/" in it.path }
                .filter { file ->
                    file.imports.any {
                        it.name.startsWith("com.example.phoneagent.data.") ||
                            it.name.startsWith("com.example.phoneagent.tools.")
                    }
                }

        assertTrue("UI imports adapters in: ${violations.map { it.path }}", violations.isEmpty())
    }

    @Test
    fun `design system does not import screens`() {
        val violations =
            productionFiles
                .filter { "/app/" in it.path && "/ui/designsystem/" in it.path }
                .filter { file -> file.imports.any { ".ui.chat." in it.name || ".ui.navigation." in it.name } }

        assertTrue("Design system imports screens in: ${violations.map { it.path }}", violations.isEmpty())
    }

    @Test
    fun `model output can never load images because no image loader is used`() {
        val violations =
            productionFiles.filter { file ->
                file.imports.any {
                    it.name.startsWith("coil") ||
                        (it.name.contains("ImageTransformer") && !it.name.endsWith("NoOpImageTransformerImpl"))
                }
            }

        assertTrue("Image loading imports in: ${violations.map { it.path }}", violations.isEmpty())
    }

    @Test
    fun `only AssistantMarkdown talks to the markdown library`() {
        val violations =
            productionFiles
                .filter { file -> file.imports.any { it.name.startsWith("com.mikepenz.") || it.name.startsWith("dev.snipme.") } }
                .filter { !it.path.endsWith("/ui/designsystem/components/AssistantMarkdown.kt") }

        assertTrue("Markdown library used outside AssistantMarkdown: ${violations.map { it.path }}", violations.isEmpty())
    }

    @Test
    fun `log messages are constants and data goes through fields`() {
        // The message is what a reader trusts to be free of user data; anything dynamic belongs in
        // `fields`, where it is redacted and visible in review (docs/log-audit.md).
        val interpolated = Regex("""\.(?:debug|info|warn|error)\(\s*[^,()]+,\s*"[^"\n]*\$""")
        val violations = productionFiles.filter { interpolated.containsMatchIn(it.text) }

        assertTrue("Interpolated log messages in: ${violations.map { it.path }}", violations.isEmpty())
    }

    @Test
    fun `ui color literals live only in theme and Text has no literals`() {
        val colorViolations =
            productionFiles
                .filter { "/app/" in it.path && "/ui/" in it.path && "/designsystem/theme/" !in it.path }
                .filter { "Color(0x" in it.text }
        val textViolations =
            productionFiles
                .filter { "/app/" in it.path && "/ui/" in it.path }
                .filter { Regex("Text\\s*\\(\\s*\"").containsMatchIn(it.text) }

        assertTrue("Color literals outside theme: ${colorViolations.map { it.path }}", colorViolations.isEmpty())
        assertTrue("Text literals in UI: ${textViolations.map { it.path }}", textViolations.isEmpty())
    }

    @Test
    fun `view models do not retain Android framework types`() {
        val forbiddenImports = listOf("android.app.Activity", "android.content.Context", "android.view.View")
        val violations =
            productionFiles
                .filter { it.name.endsWith("ViewModel.kt") }
                .filter { file -> file.imports.any { it.name in forbiddenImports } }

        assertTrue("Android types in ViewModels: ${violations.map { it.path }}", violations.isEmpty())
    }

    @Test
    fun `tool packages do not import sibling tools`() {
        val toolsRoot = "/tools/src/main/kotlin/com/example/phoneagent/tools/"
        val violations =
            productionFiles
                .filter { toolsRoot in it.path }
                .filter { file ->
                    val ownPackage = file.path.substringAfter(toolsRoot).substringBefore('/')
                    file.imports.any { import ->
                        val importedPackage =
                            import.name
                                .removePrefix("com.example.phoneagent.tools.")
                                .substringBefore('.')
                        // The generated resource class of the :tools module is shared by all tools, not a sibling tool.
                        import.name != "com.example.phoneagent.tools.R" &&
                            import.name.startsWith("com.example.phoneagent.tools.") &&
                            importedPackage != ownPackage
                    }
                }

        assertTrue("Cross-tool imports in: ${violations.map { it.path }}", violations.isEmpty())
    }

    @Test
    fun `tool implementations live in tools packages and use Tool suffix`() {
        val implementationPattern = Regex("class\\s+(\\w+)\\s*[^\\n]*:\\s*[^\\n]*\\bTool\\b")
        val violations =
            productionFiles.flatMap { file ->
                implementationPattern.findAll(file.text).mapNotNull { match ->
                    val className = match.groupValues[1]
                    if ("/tools/src/main/" !in file.path || !className.endsWith("Tool")) file.path else null
                }
            }

        assertTrue("Invalid Tool implementations in: $violations", violations.isEmpty())
    }

    @Test
    fun `gradle modules depend only in the allowed direction`() {
        val root =
            generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
                .first { File(it, "settings.gradle.kts").isFile }

        fun projectDependencies(module: String): List<String> =
            Regex("""project\("([^"]+)"\)""")
                .findAll(File(root, "$module/build.gradle.kts").readText())
                .map { it.groupValues[1] }
                .distinct()
                .toList()

        val coreDependencies = projectDependencies("core/domain")
        assertTrue("core:domain must not depend on other project modules, found: $coreDependencies", coreDependencies.isEmpty())

        // Adapters never see each other and never see the composition root (AGENTS.md).
        for (module in listOf("data/llm", "data/auth", "tools")) {
            val dependencies = projectDependencies(module)
            val forbidden = dependencies.filter { it != ":core:domain" }
            assertTrue("$module may depend only on :core:domain, found: $dependencies", forbidden.isEmpty())
        }
    }

    @Test
    fun `production dispatchers come only from DispatcherProvider`() {
        // The one sanctioned place defining real dispatchers; everyone else injects the port.
        val allowed =
            setOf(
                "/core/domain/src/main/kotlin/com/example/phoneagent/domain/platform/PlatformPorts.kt",
            )
        // Existing direct usages, kept passing on purpose. Baseline — убрать позже, когда
        // соответствующие классы переведут на DispatcherProvider:
        val baseline =
            setOf(
                "/app/src/main/kotlin/com/example/phoneagent/di/AuthBindingsModule.kt", // Dispatchers.Main.immediate для scope приложения
            )
        val violations =
            productionFiles
                .filter { file -> file.imports.any { it.name == "kotlinx.coroutines.Dispatchers" } }
                .filterNot { file ->
                    (allowed + baseline).any { suffix -> file.path.endsWith(suffix) }
                }

        assertTrue(
            "Direct Dispatchers usage outside DispatcherProvider (new code must inject the port): ${violations.map { it.path }}",
            violations.isEmpty(),
        )
    }

    @Test
    fun `cancellation is rethrown, never swallowed`() {
        // Every `catch (… : CancellationException)` block must contain a `throw`. The type is
        // matched exactly, so `TimeoutCancellationException` (the tool timeout signal, converted
        // to a Failed outcome by design in ToolPipeline) is not affected.
        val catchPattern = Regex("""catch\s*\((?:_+\s*:\s*|\w+\s*:\s*)CancellationException\s*\)""")
        val violations =
            productionFiles.flatMap { file ->
                catchPattern.findAll(file.text).mapNotNull { match ->
                    val block = blockAfter(file.text, match.range.last) ?: return@mapNotNull file.path
                    if ("throw" in block) null else file.path
                }
            }

        assertTrue("Swallowed CancellationException in: ${violations.distinct()}", violations.isEmpty())
    }

    /** Text of the balanced `{ … }` block starting at the first `{` after [fromIndex], or null. */
    private fun blockAfter(
        text: String,
        fromIndex: Int,
    ): String? {
        val start = text.indexOf('{', fromIndex)
        val end = if (start < 0) -1 else findMatchingBrace(text, start)
        return if (end > start) text.substring(start + 1, end) else null
    }

    /** Index of the `}` matching the `{` at [start], or -1 when the block never closes. */
    private fun findMatchingBrace(
        text: String,
        start: Int,
    ): Int {
        var depth = 0
        for (index in start until text.length) {
            when (text[index]) {
                '{' -> depth++
                '}' -> depth--
            }
            if (depth == 0) return index
        }
        return -1
    }
}
