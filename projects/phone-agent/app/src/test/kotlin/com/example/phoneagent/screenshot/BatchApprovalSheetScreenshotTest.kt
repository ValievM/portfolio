package com.example.phoneagent.screenshot

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.phoneagent.domain.interaction.ApprovalRequest
import com.example.phoneagent.domain.tools.CallDescription
import com.example.phoneagent.domain.tools.Risk
import com.example.phoneagent.ui.designsystem.components.BatchApprovalSheet
import kotlinx.serialization.json.buildJsonObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = PHONE_QUALIFIERS)
class BatchApprovalSheetScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun fiveAlarms() =
        composeRule.snapshot("batch_approval_sheet", wholeScreen = true, fillScreen = true) {
            BatchApprovalSheet(
                requests = listOf("06:00", "06:15", "06:30", "06:45", "07:00").map(::request),
                onApprove = {},
                onReject = {},
            )
        }

    private fun request(time: String) =
        ApprovalRequest(CallDescription("Поставить будильник", "На $time"), Risk.CONFIRM, buildJsonObject {})
}
