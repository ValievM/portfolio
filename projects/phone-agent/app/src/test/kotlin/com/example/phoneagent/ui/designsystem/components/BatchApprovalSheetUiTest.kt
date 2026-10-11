package com.example.phoneagent.ui.designsystem.components

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.phoneagent.R
import com.example.phoneagent.domain.interaction.ApprovalRequest
import com.example.phoneagent.domain.tools.CallDescription
import com.example.phoneagent.domain.tools.Risk
import com.example.phoneagent.ui.designsystem.theme.AppTheme
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class BatchApprovalSheetUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val answers = mutableListOf<String>()

    private fun show(times: List<String>) {
        composeRule.setContent {
            AppTheme(useDynamicColor = false) {
                BatchApprovalSheet(
                    requests = times.map(::request),
                    onApprove = { answers += "approve" },
                    onReject = { answers += "reject" },
                )
            }
        }
    }

    private fun string(
        id: Int,
        vararg args: Any,
    ): String = ApplicationProvider.getApplicationContext<Context>().getString(id, *args)

    private fun request(time: String) =
        ApprovalRequest(CallDescription("Поставить будильник", "На $time"), Risk.CONFIRM, buildJsonObject {})

    @Test
    fun `the sheet names the number of actions and lists every one of them`() {
        show(listOf("06:00", "06:15", "06:30"))

        composeRule.onNodeWithText("Разрешить 3 действия?").assertIsDisplayed()
        listOf("06:00", "06:15", "06:30").forEach { composeRule.onNodeWithText("На $it").assertIsDisplayed() }
    }

    @Test
    fun `approve all and reject all report exactly one answer`() {
        show(listOf("06:00", "06:15"))

        composeRule.onNodeWithText(string(R.string.batch_approval_approve_all, 2)).performClick()

        assertEquals(listOf("approve"), answers)
    }

    @Test
    fun `reject all reports a rejection`() {
        show(listOf("06:00", "06:15"))

        composeRule.onNodeWithText(string(R.string.batch_approval_reject_all)).performClick()

        assertEquals(listOf("reject"), answers)
    }
}
