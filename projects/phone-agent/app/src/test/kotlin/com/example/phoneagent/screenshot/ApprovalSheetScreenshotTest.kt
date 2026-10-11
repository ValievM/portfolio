package com.example.phoneagent.screenshot

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.phoneagent.domain.interaction.ApprovalRequest
import com.example.phoneagent.domain.tools.CallDescription
import com.example.phoneagent.domain.tools.Risk
import com.example.phoneagent.ui.designsystem.components.ApprovalSheet
import kotlinx.serialization.json.buildJsonObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = PHONE_QUALIFIERS)
class ApprovalSheetScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun collapsed() =
        composeRule.snapshot("approval_sheet", wholeScreen = true, fillScreen = true) {
            ApprovalSheet(
                request = alarmApproval,
                arguments = alarmArguments,
                onApprove = {},
                onReject = {},
            )
        }

    @Test
    fun detailsExpanded() =
        composeRule.snapshot(
            "approval_sheet_details",
            listOf(Variant.Light, Variant.LargeFont),
            wholeScreen = true,
            fillScreen = true,
        ) {
            ApprovalSheet(
                request = alarmApproval,
                arguments = alarmArguments,
                onApprove = {},
                onReject = {},
                initialDetailsExpanded = true,
            )
        }

    @Test
    fun sensitiveRiskInLightDarkAndLargeFont() =
        composeRule.snapshot(
            "sensitive_approval_sheet",
            listOf(Variant.Light, Variant.Dark, Variant.LargeFont),
            wholeScreen = true,
            fillScreen = true,
        ) {
            ApprovalSheet(
                request =
                    ApprovalRequest(
                        CallDescription(
                            "Отправить SMS",
                            "Адресат: Иван Петров, +7 999 123-45-67",
                            listOf("Текст: Встречаемся у главного входа в четверг в 18:30."),
                        ),
                        Risk.SENSITIVE,
                        buildJsonObject {},
                    ),
                arguments = buildJsonObject {},
                onApprove = {},
                onReject = {},
            )
        }
}
