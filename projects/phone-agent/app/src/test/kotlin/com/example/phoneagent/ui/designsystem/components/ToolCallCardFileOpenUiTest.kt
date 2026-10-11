package com.example.phoneagent.ui.designsystem.components

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.phoneagent.R
import com.example.phoneagent.domain.tools.CallDescription
import com.example.phoneagent.ui.designsystem.theme.AppTheme
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class ToolCallCardFileOpenUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val file = FileHit("Договор.pdf", "content://media/external/file/1", 1200L, "application/pdf")

    @Test
    fun `opening a file chip requires confirmation first`() {
        val opened = mutableListOf<FileHit>()
        composeRule.setContent {
            AppTheme(useDynamicColor = false) {
                ToolCallCard(
                    state = ToolCallStatus.FilesFound(1),
                    description = CallDescription("Поиск файлов", "Результаты"),
                    arguments = buildJsonObject {},
                    expanded = true,
                    onExpandToggle = {},
                    fileHits = listOf(file),
                    onOpenFile = opened::add,
                )
            }
        }

        composeRule.onNodeWithText(file.name).performClick()

        composeRule.onNodeWithText(string(R.string.file_open_confirm_title)).assertIsDisplayed()
        assertTrue(opened.isEmpty())

        composeRule.onNodeWithText(string(R.string.file_open_action)).performClick()

        assertEquals(listOf(file), opened)
    }

    private fun string(id: Int): String = ApplicationProvider.getApplicationContext<Context>().getString(id)
}
