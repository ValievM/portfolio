package com.example.phoneagent.storage

import com.example.phoneagent.domain.conversation.CallId
import com.example.phoneagent.domain.conversation.HistoryItem
import com.example.phoneagent.domain.conversation.ItemId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoomConversationRepositoryTest {
    @Test
    fun `load returns mixed history in stored order`() =
        runTest {
            val items =
                listOf(
                    HistoryItem.UserMessage(itemId = ItemId("user-1"), text = "Hello"),
                    HistoryItem.ToolCallItem(ItemId("item-1"), CallId("call-1"), "search_files", buildJsonObject {}),
                    HistoryItem.ToolOutput(ItemId("output-1"), CallId("call-1"), "Found it"),
                    HistoryItem.AssistantMessage(itemId = ItemId("assistant-1"), text = "Done"),
                    HistoryItem.Opaque(ItemId("opaque-1"), "provider", buildJsonObject { }),
                )
            val storedRows = items.mapIndexed { position, item -> HistoryItemEntityMapper.toEntity(item, position) }
            val fakeDao = FakeConversationDao(storedRows)
            val repository = RoomConversationRepository(fakeDao)

            assertEquals(items, repository.load())
        }

    @Test
    fun `replace overwrites previous rows instead of appending`() =
        runTest {
            val repository = RoomConversationRepository(FakeConversationDao(emptyList()))
            val previous = listOf(HistoryItem.UserMessage(itemId = ItemId("old"), text = "Old"))
            val replacement = listOf(HistoryItem.AssistantMessage(itemId = ItemId("new"), text = "New"))

            repository.replace(previous)
            repository.replace(replacement)

            assertEquals(replacement, repository.load())
        }

    @Test
    fun `replacing with empty history clears all rows`() =
        runTest {
            val repository =
                RoomConversationRepository(
                    FakeConversationDao(
                        listOf(HistoryItemEntityMapper.toEntity(HistoryItem.UserMessage(ItemId("old"), "Old"), position = 0)),
                    ),
                )

            repository.replace(emptyList())

            assertEquals(emptyList<HistoryItem>(), repository.load())
        }

    private class FakeConversationDao(
        initialRows: List<ConversationEntity>,
    ) : ConversationDao {
        private val rows = MutableStateFlow(initialRows)

        override suspend fun loadAll(): List<ConversationEntity> = rows.value.sortedBy(ConversationEntity::position)

        override fun observeAll(): Flow<List<ConversationEntity>> = rows.map { it.sortedBy(ConversationEntity::position) }

        override suspend fun insertAll(items: List<ConversationEntity>) {
            rows.value = rows.value + items
        }

        override suspend fun deleteAll() {
            rows.value = emptyList()
        }
    }
}
