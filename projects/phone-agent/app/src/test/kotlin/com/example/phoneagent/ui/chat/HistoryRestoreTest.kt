package com.example.phoneagent.ui.chat

import com.example.phoneagent.domain.conversation.CallId
import com.example.phoneagent.domain.conversation.HistoryItem
import com.example.phoneagent.domain.conversation.ItemId
import com.example.phoneagent.domain.tools.CallDescription
import com.example.phoneagent.domain.tools.ToolContent
import com.example.phoneagent.domain.tools.ToolFile
import com.example.phoneagent.ui.designsystem.components.FileHit
import com.example.phoneagent.ui.designsystem.components.ToolCallStatus
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** Tool cards live in the saved history as "call + result"; reopening the app must draw them again. */
class HistoryRestoreTest {
    private val args: JsonObject = buildJsonObject { put("hour", 7) }
    private val describe = { item: HistoryItem.ToolCallItem -> CallDescription("Card of ${item.name}", "args ${item.arguments}") }

    private fun call(
        id: String,
        callId: String,
        name: String = "set_alarm",
    ) = HistoryItem.ToolCallItem(ItemId(id), CallId(callId), name, args)

    private fun output(
        callId: String,
        text: String,
    ) = HistoryItem.ToolOutput(ItemId("out-$callId"), CallId(callId), text)

    private fun ChatMessage.card(): ToolCallMessage = requireNotNull(toolCall) { "not a card: $this" }

    @Test
    fun `a finished call comes back as a card between the texts around it`() {
        val messages =
            listOf(
                HistoryItem.UserMessage(ItemId("u1"), "Поставь будильник"),
                HistoryItem.AssistantMessage(ItemId("a1"), "Ставлю."),
                call("t1", "c1"),
                output("c1", "Будильник поставлен"),
                HistoryItem.AssistantMessage(ItemId("a2"), "Готово."),
            ).toChatMessages(describe)

        assertEquals(listOf("u1", "a1", "t1", "a2"), messages.map { it.id })
        assertEquals(listOf(ChatRole.USER, ChatRole.ASSISTANT, ChatRole.TOOL, ChatRole.ASSISTANT), messages.map { it.role })
    }

    @Test
    fun `the card shows the description of the tool, the saved arguments and the result`() {
        val card = listOf(call("t1", "c1"), output("c1", "Будильник поставлен")).toChatMessages(describe).single().card()

        assertEquals(ToolCallStatus.Done("Будильник поставлен"), card.status)
        assertEquals(CallDescription("Card of set_alarm", "args $args"), card.description)
        assertEquals(args, card.arguments)
        assertEquals(false, card.expanded)
    }

    @Test
    fun `a failed call comes back as a failure with its reason`() {
        val card = listOf(call("t1", "c1"), output("c1", "Failed: Приложение «Часы» не найдено")).toChatMessages(describe).single().card()

        assertEquals(ToolCallStatus.Failed("Приложение «Часы» не найдено"), card.status)
    }

    @Test
    fun `a rejected call comes back as rejected`() {
        val card = listOf(call("t1", "c1"), output("c1", "Rejected: declined by the user")).toChatMessages(describe).single().card()

        assertEquals(ToolCallStatus.Rejected, card.status)
    }

    @Test
    fun `a call without a result comes back as interrupted`() {
        val card = listOf(call("t1", "c1")).toChatMessages(describe).single().card()

        assertEquals(ToolCallStatus.Cancelled, card.status)
    }

    @Test
    fun `a text that only mentions failure is still shown as done`() {
        val card = listOf(call("t1", "c1"), output("c1", "Alarm set. Failed: none")).toChatMessages(describe).single().card()

        assertEquals(ToolCallStatus.Done("Alarm set. Failed: none"), card.status)
    }

    @Test
    fun `results are matched to their calls by call id, not by position`() {
        val messages =
            listOf(
                call("t1", "c1"),
                call("t2", "c2"),
                output("c2", "Failed: second broke"),
                output("c1", "first fine"),
            ).toChatMessages(describe)

        assertEquals(ToolCallStatus.Done("first fine"), messages[0].card().status)
        assertEquals(ToolCallStatus.Failed("second broke"), messages[1].card().status)
    }

    @Test
    fun `restored structured file content comes back as count and chips`() {
        val json =
            """{"items":[{"name":"Договор.pdf","uri":"content://media/external/file/1","size":1200,"mimeType":"application/pdf"},""" +
                """{"name":"scan.jpg","uri":"content://media/external/file/2"}]}"""

        val card =
            listOf(call("t1", "c1", "file_catalog"), output("c1", json))
                .toChatMessages(describe) { _, _ ->
                    ToolContent.FilesFound(
                        listOf(
                            ToolFile("Договор.pdf", "content://media/external/file/1", 1200L, "application/pdf"),
                            ToolFile("scan.jpg", "content://media/external/file/2", null, null),
                        ),
                    )
                }.single()
                .card()

        assertEquals(ToolCallStatus.FilesFound(2), card.status)
        assertEquals(
            listOf(
                FileHit("Договор.pdf", "content://media/external/file/1", 1200L, "application/pdf"),
                FileHit("scan.jpg", "content://media/external/file/2", null, null),
            ),
            card.fileHits,
        )
    }

    @Test
    fun `the tool result item itself is not drawn as a message`() {
        val messages = listOf(call("t1", "c1"), output("c1", "ok")).toChatMessages(describe)

        assertEquals(1, messages.size)
        assertNotNull(messages.single().toolCall)
    }

    @Test
    fun `an outside change of the history shows each card once, not the old live one and the restored one`() {
        val live =
            ChatUiState(
                messages =
                    listOf(
                        ChatMessage("1", ChatRole.USER, "Поставь будильник"),
                        ChatMessage("2", ChatRole.TOOL, "", ToolCallMessage(ToolCallStatus.Done("ok"), CallDescription("t", "s"), args)),
                    ),
            )
        val loaded = listOf(HistoryItem.UserMessage(ItemId("u1"), "Поставь будильник"), call("t1", "c1"), output("c1", "ok"))

        val messages = live.withLoadedHistory(loaded, isInitial = false, describe = describe).messages

        assertEquals(listOf("u1", "t1"), messages.map { it.id })
    }
}
