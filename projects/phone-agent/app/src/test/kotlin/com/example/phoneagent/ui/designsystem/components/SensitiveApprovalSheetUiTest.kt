package com.example.phoneagent.ui.designsystem.components

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.phoneagent.domain.interaction.ApprovalRequest
import com.example.phoneagent.domain.tools.CallDescription
import com.example.phoneagent.domain.tools.Risk
import com.example.phoneagent.ui.designsystem.theme.AppTheme
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
class SensitiveApprovalSheetUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val answers = mutableListOf<String>()

    @Test
    fun `sensitive risk selects warning sheet with curated description instead of model arguments`() {
        showSensitive()

        composeRule.onNodeWithText("Проверьте и подтвердите").assertIsDisplayed()
        composeRule.onNodeWithText("Это действие нельзя отменить. Проверьте адресата и текст.").assertIsDisplayed()
        composeRule.onNodeWithText("Отправить SMS").assertIsDisplayed()
        composeRule.onNodeWithText("Адресат: +7 999 123-45-67").assertIsDisplayed()
        composeRule.onNodeWithText("Текст: Встречаемся у метро").assertIsDisplayed()
        composeRule.onNodeWithText("model-secret-phone").assertDoesNotExist()
        composeRule.onNodeWithText("Подробности").assertDoesNotExist()
        composeRule.onNodeWithText("Запомнить").assertDoesNotExist()
    }

    @Test
    fun `confirm risk keeps the existing approval sheet`() {
        composeRule.setContent {
            AppTheme(useDynamicColor = false) {
                ApprovalSheet(
                    request = request(Risk.CONFIRM),
                    arguments = buildJsonObject {},
                    onApprove = {},
                    onReject = {},
                )
            }
        }

        composeRule.onNodeWithText("Разрешить действие?").assertIsDisplayed()
        composeRule.onNodeWithText("Подробности").assertIsDisplayed()
        composeRule.onNodeWithText("Проверьте и подтвердите").assertDoesNotExist()
    }

    @Test
    fun `approve and reject buttons invoke their matching callbacks`() {
        showSensitive(
            onApprove = { answers += "approve" },
            onReject = { answers += "reject" },
        )

        composeRule.onNodeWithText("Разрешить").performClick()

        assertEquals(listOf("approve"), answers)
    }

    @Test
    fun `pressing back closes the sensitive sheet as a rejection`() {
        showSensitive(onReject = { answers += "reject" })

        Espresso.pressBack()
        composeRule.waitForIdle()

        assertEquals(listOf("reject"), answers)
    }

    @Test
    fun `sheet title is a focused heading and content follows warning recipient and body`() {
        showSensitive()

        val title = composeRule.onNodeWithText("Проверьте и подтвердите")
        title.assertIsFocused()
        assertTrue(title.fetchSemanticsNode().config.contains(SemanticsProperties.Heading))

        val titleBottom = title.fetchSemanticsNode().boundsInRoot.bottom
        val warningTop =
            composeRule
                .onNodeWithText(
                    "Это действие нельзя отменить. Проверьте адресата и текст.",
                ).fetchSemanticsNode()
                .boundsInRoot.top
        val recipientTop =
            composeRule
                .onNodeWithText("Адресат: +7 999 123-45-67")
                .fetchSemanticsNode()
                .boundsInRoot.top
        val contentTop =
            composeRule
                .onNodeWithText("Текст: Встречаемся у метро")
                .fetchSemanticsNode()
                .boundsInRoot.top
        val approveTop =
            composeRule
                .onNodeWithText("Разрешить")
                .fetchSemanticsNode()
                .boundsInRoot.top

        assertTrue("warning must follow the heading", warningTop >= titleBottom)
        assertTrue("recipient must follow the warning", recipientTop > warningTop)
        assertTrue("content must follow the recipient", contentTop > recipientTop)
        assertTrue("actions must follow the content", approveTop > contentTop)
    }

    private fun showSensitive(
        onApprove: () -> Unit = {},
        onReject: () -> Unit = {},
    ) {
        composeRule.setContent {
            AppTheme(useDynamicColor = false) {
                ApprovalSheet(
                    request = request(Risk.SENSITIVE),
                    arguments = buildJsonObject { put("phone", "model-secret-phone") },
                    onApprove = onApprove,
                    onReject = onReject,
                )
            }
        }
    }

    private fun request(risk: Risk) =
        ApprovalRequest(
            description =
                CallDescription(
                    title = "Отправить SMS",
                    summary = "Адресат: +7 999 123-45-67",
                    details = listOf("Текст: Встречаемся у метро"),
                ),
            risk = risk,
            arguments = buildJsonObject { put("phone", "model-secret-phone") },
        )
}
