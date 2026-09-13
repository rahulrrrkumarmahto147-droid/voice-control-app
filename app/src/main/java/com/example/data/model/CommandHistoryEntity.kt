package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "command_history")
data class CommandHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val originalCommand: String,
    val interpretedAction: String,
    val status: CommandStatus,
    val resultMessage: String,
    val timestamp: Long = System.currentTimeMillis()
)
