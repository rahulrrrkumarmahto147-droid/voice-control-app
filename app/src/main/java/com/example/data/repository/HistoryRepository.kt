package com.example.data.repository

import com.example.data.database.CommandHistoryDao
import com.example.data.model.CommandHistoryEntity
import com.example.data.model.CommandStatus
import kotlinx.coroutines.flow.Flow

class HistoryRepository(private val dao: CommandHistoryDao) {
    val allHistory: Flow<List<CommandHistoryEntity>> = dao.getAllHistory()
    val recentHistory: Flow<List<CommandHistoryEntity>> = dao.getRecentHistory(15)

    suspend fun logCommand(
        originalCommand: String,
        interpretedAction: String,
        status: CommandStatus,
        resultMessage: String
    ): Long {
        val entity = CommandHistoryEntity(
            originalCommand = originalCommand,
            interpretedAction = interpretedAction,
            status = status,
            resultMessage = resultMessage
        )
        return dao.insertHistory(entity)
    }

    suspend fun clearHistory() {
        dao.clearAllHistory()
    }

    suspend fun deleteHistoryItem(id: Long) {
        dao.deleteHistoryById(id)
    }
}
