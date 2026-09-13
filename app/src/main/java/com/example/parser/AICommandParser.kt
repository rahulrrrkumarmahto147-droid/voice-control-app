package com.example.parser

import com.example.action.ParsedCommand

/**
 * Abstraction for converting natural language voice input into structured phone actions.
 * Allows switching between local rule-based parsing and external AI model parsing.
 */
interface AICommandParser {
    suspend fun parse(input: String): ParsedCommand
}
