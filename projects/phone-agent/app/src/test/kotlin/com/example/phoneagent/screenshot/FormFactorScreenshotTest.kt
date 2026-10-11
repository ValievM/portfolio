package com.example.phoneagent.screenshot

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.phoneagent.domain.conversation.ItemId
import com.example.phoneagent.domain.conversation.TurnState
import com.example.phoneagent.domain.interaction.ApprovalRequest
import com.example.phoneagent.domain.tools.CallDescription
import com.example.phoneagent.domain.tools.Risk
import com.example.phoneagent.ui.chat.ChatMessage
import com.example.phoneagent.ui.chat.ChatRole
import com.example.phoneagent.ui.chat.ChatScreen
import com.example.phoneagent.ui.chat.ChatUiState
import com.example.phoneagent.ui.chat.ToolCallMessage
import com.example.phoneagent.ui.designsystem.components.BatchApprovalSheet
import com.example.phoneagent.ui.designsystem.components.ToolCallStatus
import com.example.phoneagent.ui.login.LoginFormState
import com.example.phoneagent.ui.login.LoginScreen
import com.example.phoneagent.ui.login.LoginUiState
import kotlinx.serialization.json.buildJsonObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private val conversation =
    ChatUiState(
        messages =
            listOf(
                ChatMessage("1", ChatRole.USER, "Поставь три будильника: 6:00, 6:15 и 6:30"),
                ChatMessage("2", ChatRole.ASSISTANT, "Ставлю три будильника."),
                ChatMessage("3", ChatRole.TOOL, "", ToolCallMessage(ToolCallStatus.AwaitingApproval, alarmDescription, alarmArguments)),
                ChatMessage(
                    "4",
                    ChatRole.TOOL,
                    "",
                    ToolCallMessage(ToolCallStatus.Done("Запрос на будильник отправлен"), alarmDescription, alarmArguments),
                ),
            ),
        turn = TurnState.Running(ItemId("question-1")),
    )

private fun batch(count: Int) =
    List(count) {
        ApprovalRequest(CallDescription("Поставить будильник", "На 06:%02d".format(it * 15 % 60)), Risk.CONFIRM, buildJsonObject {})
    }

/** The same three screens on every screen size; each subclass fixes the size and the file name prefix. */
abstract class FormFactorScreenshots(
    private val prefix: String,
) {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun chat() =
        composeRule.snapshot("${prefix}_chat", listOf(Variant.Light, Variant.Dark), fillScreen = true) {
            ChatScreen(state = conversation, onEvent = {}, onOpenSettings = {})
        }

    @Test
    fun chatEmpty() =
        composeRule.snapshot("${prefix}_chat_empty", listOf(Variant.Light), fillScreen = true) {
            ChatScreen(state = ChatUiState(), onEvent = {}, onOpenSettings = {})
        }

    @Test
    fun login() =
        composeRule.snapshot("${prefix}_login", listOf(Variant.Light), fillScreen = true) {
            LoginScreen(state = LoginUiState(), form = LoginFormState(), onEvent = {})
        }
}

/** A 320dp-wide phone: the narrowest screen the app promises to work on. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-port-xhdpi")
class NarrowPhoneScreenshotTest : FormFactorScreenshots("narrow") {
    @Test
    fun batchSheetLargeFont() =
        composeRule.snapshot("narrow_batch_sheet", listOf(Variant.LargeFont), wholeScreen = true) {
            BatchApprovalSheet(requests = batch(15), onApprove = {}, onReject = {})
        }
}

/** A tablet in portrait: the chat keeps its reading width instead of stretching. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w840dp-h1180dp-port-mdpi")
class TabletScreenshotTest : FormFactorScreenshots("tablet") {
    @Test
    fun batchSheet() =
        composeRule.snapshot("tablet_batch_sheet", listOf(Variant.Light), wholeScreen = true) {
            BatchApprovalSheet(requests = batch(5), onApprove = {}, onReject = {})
        }
}

/** A phone turned sideways: little height, wide screen. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w800dp-h360dp-land-xhdpi")
class LandscapePhoneScreenshotTest : FormFactorScreenshots("landscape")
