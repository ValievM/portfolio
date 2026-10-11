package com.example.phoneagent.screenshot

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.phoneagent.ui.designsystem.components.ToolCallCard
import com.example.phoneagent.ui.designsystem.components.ToolCallStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = PHONE_QUALIFIERS)
class ToolCallCardScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun card(
        name: String,
        status: ToolCallStatus,
        expanded: Boolean = false,
        variants: List<Variant> = listOf(Variant.Light, Variant.Dark),
    ) = composeRule.snapshot("tool_call_card_$name", variants) {
        ToolCallCard(
            state = status,
            description = alarmDescription,
            arguments = alarmArguments,
            expanded = expanded,
            onExpandToggle = {},
            fileHits = fileHits,
        )
    }

    @Test
    fun awaitingApproval() = card("awaiting", ToolCallStatus.AwaitingApproval)

    @Test
    fun running() = card("running", ToolCallStatus.Running)

    @Test
    fun done() = card("done", ToolCallStatus.Done("Будильник поставлен на 07:30"))

    @Test
    fun filesFound() = card("files_found", ToolCallStatus.FilesFound(fileHits.size), expanded = true)

    @Test
    fun failed() = card("failed", ToolCallStatus.Failed("Приложение «Часы» не найдено"))

    @Test
    fun rejected() = card("rejected", ToolCallStatus.Rejected)

    @Test
    fun cancelled() = card("cancelled", ToolCallStatus.Cancelled)

    @Test
    fun expandedLargeFont() =
        card(
            "expanded",
            ToolCallStatus.Done("Будильник поставлен на 07:30"),
            expanded = true,
            variants = listOf(Variant.Light, Variant.Dark, Variant.LargeFont),
        )
}
