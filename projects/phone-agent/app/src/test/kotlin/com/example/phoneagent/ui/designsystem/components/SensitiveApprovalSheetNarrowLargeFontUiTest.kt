package com.example.phoneagent.ui.designsystem.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.phoneagent.domain.interaction.ApprovalRequest
import com.example.phoneagent.domain.tools.CallDescription
import com.example.phoneagent.domain.tools.Risk
import com.example.phoneagent.ui.designsystem.theme.AppTheme
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-port-xhdpi")
class SensitiveApprovalSheetNarrowLargeFontUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val recipient =
        "Адресат: Иван Иванович Петров, +7 999 123 45 67 890 123 456 789 012 345; " +
            "служебный номер, дополнительное пояснение на русском языке"

    private val content =
        "Текст: Здравствуйте, коллеги! Подтверждаю встречу в четверг в 18:30 у главного входа. " +
            "Если время изменится, пожалуйста, позвоните заранее. " +
            "Это длинное сообщение проверяет перенос кириллического текста на несколько строк."

    @Test
    fun `long description scrolls completely while both decision buttons remain fixed and visible`() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                AppTheme(useDynamicColor = false) {
                    ApprovalSheet(
                        request =
                            ApprovalRequest(
                                CallDescription("Отправить SMS", recipient, listOf(content, content)),
                                Risk.SENSITIVE,
                                buildJsonObject { put("body", "untrusted model text") },
                            ),
                        arguments = buildJsonObject { put("body", "untrusted model text") },
                        onApprove = {},
                        onReject = {},
                    )
                }
            }
        }

        val approve = composeRule.onNodeWithText("Разрешить")
        val reject = composeRule.onNodeWithText("Отклонить")
        val approveBefore = bounds(approve)
        val rejectBefore = bounds(reject)
        approve.assertIsDisplayed()
        reject.assertIsDisplayed()
        composeRule.onNodeWithText(recipient).assertExists().performScrollTo()
        composeRule.onAllNodesWithText(content)[1].assertExists().performScrollTo()
        composeRule.waitForIdle()

        assertEquals(approveBefore, bounds(approve))
        assertEquals(rejectBefore, bounds(reject))
        approve.assertIsDisplayed()
        reject.assertIsDisplayed()
        composeRule.onNodeWithText("untrusted model text").assertDoesNotExist()
    }

    private fun bounds(node: SemanticsNodeInteraction): Rect = node.fetchSemanticsNode().boundsInRoot
}
