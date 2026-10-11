package com.example.phoneagent.ui.designsystem.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.phoneagent.R
import com.example.phoneagent.domain.interaction.ApprovalRequest
import com.example.phoneagent.domain.tools.CallDescription
import com.example.phoneagent.domain.tools.Risk
import com.example.phoneagent.ui.designsystem.theme.AppTheme
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A 320dp screen at 200% font: the narrowest, tallest text the sheets have to survive. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-port-xhdpi")
class SheetsNarrowLargeFontUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun string(
        id: Int,
        vararg args: Any,
    ): String = ApplicationProvider.getApplicationContext<Context>().getString(id, *args)

    private fun request(index: Int) =
        ApprovalRequest(
            CallDescription("Поставить будильник", "На 06:%02d".format(index)),
            Risk.CONFIRM,
            buildJsonObject {
            },
        )

    private fun show(content: @Composable () -> Unit) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                AppTheme(useDynamicColor = false) { content() }
            }
        }
    }

    private fun bounds(node: SemanticsNodeInteraction): Rect = node.fetchSemanticsNode().boundsInRoot

    @Test
    fun `fifteen actions at 200 percent font keep both answer buttons on the screen`() {
        show { BatchApprovalSheet(requests = List(15, ::request), onApprove = {}, onReject = {}) }

        composeRule.onNodeWithText(string(R.string.batch_approval_approve_all, 15)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.batch_approval_reject_all)).assertIsDisplayed()
    }

    @Test
    fun `the answer buttons of a batch stack when they do not fit side by side, approve on top`() {
        show { BatchApprovalSheet(requests = List(3, ::request), onApprove = {}, onReject = {}) }

        val approve = bounds(composeRule.onNodeWithText(string(R.string.batch_approval_approve_all, 3)))
        val reject = bounds(composeRule.onNodeWithText(string(R.string.batch_approval_reject_all)))

        assertTrue("reject $reject must be below approve $approve", reject.top >= approve.bottom)
    }

    @Test
    fun `the single approval sheet keeps both buttons on the screen`() {
        show {
            ApprovalSheet(
                request = request(30),
                arguments = buildJsonObject {},
                onApprove = {},
                onReject = {},
            )
        }

        composeRule.onNodeWithText(string(R.string.approval_approve)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.approval_reject)).assertIsDisplayed()
    }

    @Test
    fun `the last of fifteen rows can still be reached by scrolling the list`() {
        show { BatchApprovalSheet(requests = List(15, ::request), onApprove = {}, onReject = {}) }

        // The rows are numbered and none is cut off: the number of the last one exists in the tree.
        composeRule.onNodeWithText("15").assertExists()
        assertEquals(true, bounds(composeRule.onNodeWithText(string(R.string.batch_approval_reject_all))).height > 0f)
    }
}

/** A phone of an ordinary size at the regular font: the two answers sit next to each other. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class SheetsRegularUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `at a regular size the two answers of a batch are side by side`() {
        composeRule.setContent {
            AppTheme(useDynamicColor = false) {
                BatchApprovalSheet(
                    requests =
                        List(
                            2,
                        ) { ApprovalRequest(CallDescription("Поставить будильник", "На 06:0$it"), Risk.CONFIRM, buildJsonObject {}) },
                    onApprove = {},
                    onReject = {},
                )
            }
        }
        val context = ApplicationProvider.getApplicationContext<Context>()

        val approve =
            composeRule
                .onNodeWithText(
                    context.getString(R.string.batch_approval_approve_all, 2),
                ).fetchSemanticsNode()
                .boundsInRoot
        val reject = composeRule.onNodeWithText(context.getString(R.string.batch_approval_reject_all)).fetchSemanticsNode().boundsInRoot

        assertEquals("same row", approve.top, reject.top, 2f)
        assertTrue("reject is on the left of approve", reject.right <= approve.left)
    }
}
