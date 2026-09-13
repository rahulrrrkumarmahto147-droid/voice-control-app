package com.example.action

data class ParsedCommand(
    val rawText: String,
    val actions: List<ActionType>,
    val requiresConfirmation: Boolean = false,
    val confirmationPrompt: String? = null
)
