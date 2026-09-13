package com.example.parser

import com.example.action.ActionType
import com.example.action.ParsedCommand
import com.example.action.ScrollDirection
import com.example.action.SwipeDirection
import java.util.Locale

class RuleBasedCommandParser : AICommandParser {

    override suspend fun parse(input: String): ParsedCommand {
        val trimmed = input.trim()
        if (trimmed.isBlank()) {
            return ParsedCommand(
                rawText = input,
                actions = emptyList(),
                requiresConfirmation = false
            )
        }

        // Check for safety / destructive confirmation
        val safetyCheck = checkDestructiveAction(trimmed)
        if (safetyCheck != null) {
            val baseActions = parseSingleOrComposite(trimmed)
            return ParsedCommand(
                rawText = trimmed,
                actions = baseActions,
                requiresConfirmation = true,
                confirmationPrompt = safetyCheck
            )
        }

        val actions = parseSingleOrComposite(trimmed)
        return ParsedCommand(
            rawText = trimmed,
            actions = actions,
            requiresConfirmation = false
        )
    }

    private fun checkDestructiveAction(text: String): String? {
        val lower = text.lowercase(Locale.ROOT)
        return when {
            lower.contains("send message") || lower.contains("send this message") || lower.contains("send text") -> {
                "You are about to send a message. Should I continue?"
            }
            lower.contains("delete") || lower.contains("erase") || lower.contains("remove all") -> {
                "You are about to delete content. Should I continue?"
            }
            lower.contains("purchase") || lower.contains("buy") || lower.contains("pay") || lower.contains("transfer money") -> {
                "You are about to make a payment or purchase. Should I continue?"
            }
            lower.contains("uninstall") -> {
                "You are about to uninstall an application. Should I continue?"
            }
            lower.contains("post") && (lower.contains("publicly") || lower.contains("story") || lower.contains("tweet")) -> {
                "You are about to post publicly. Should I continue?"
            }
            else -> null
        }
    }

    private fun parseSingleOrComposite(input: String): List<ActionType> {
        val trimmedInput = input.trimEnd('.', '!', '?')

        // Pattern 1: "open [App] and search for [Query]" or "open [App], search for [Query]"
        val openAndSearchRegex = Regex("^(?:open|launch|start|go to)\\s+([^,]+?)(?:\\s*,?\\s*(?:and\\s+then|then|and)\\s+|\\s*,\\s*)search\\s+(?:for\\s+)?(.+)$", RegexOption.IGNORE_CASE)
        val matchOpenSearch = openAndSearchRegex.find(trimmedInput)
        if (matchOpenSearch != null) {
            val app = matchOpenSearch.groupValues[1].trim()
            val query = matchOpenSearch.groupValues[2].trim()

            // Check if there's a third step like ", and open the first result" / "play the first video"
            val thirdStepMatch = Regex("^(.*?)(?:\\s*,?\\s*(?:and|then)?\\s+(?:open|play|tap|select)\\s+(?:the\\s+)?(first\\s+(?:result|video)|[a-zA-Z0-9\\s]+))$", RegexOption.IGNORE_CASE).find(query)

            return if (thirdStepMatch != null) {
                val realQuery = thirdStepMatch.groupValues[1].trim()
                val targetResult = thirdStepMatch.groupValues[2].trim()
                listOf(
                    ActionType.OpenApp(app),
                    ActionType.Wait(1200L),
                    ActionType.Tap("Search"),
                    ActionType.Wait(500L),
                    ActionType.TypeText(realQuery),
                    ActionType.PressEnter,
                    ActionType.Wait(1500L),
                    ActionType.Tap(targetResult)
                )
            } else {
                listOf(
                    ActionType.OpenApp(app),
                    ActionType.Wait(1200L),
                    ActionType.Tap("Search"),
                    ActionType.Wait(500L),
                    ActionType.TypeText(query),
                    ActionType.PressEnter
                )
            }
        }

        // Generic multi-step splitting using " and then ", " then ", " and ", or ", "
        val delimiters = listOf(
            Regex("\\s+and\\s+then\\s+", RegexOption.IGNORE_CASE),
            Regex("\\s+then\\s+", RegexOption.IGNORE_CASE),
            Regex("\\s+and\\s+", RegexOption.IGNORE_CASE),
            Regex(",\\s*")
        )
        var subClauses = listOf(trimmedInput)

        for (del in delimiters) {
            val temp = mutableListOf<String>()
            for (clause in subClauses) {
                if (clause.contains(del)) {
                    temp.addAll(clause.split(del).map { it.trim() }.filter { it.isNotBlank() })
                } else {
                    temp.add(clause)
                }
            }
            subClauses = temp
        }

        if (subClauses.size > 1) {
            val combinedActions = mutableListOf<ActionType>()
            for ((index, clause) in subClauses.withIndex()) {
                val parsed = parseSingleAction(clause)
                if (parsed != null) {
                    combinedActions.add(parsed)
                    // Insert a reasonable wait between distinct multi-step actions
                    if (index < subClauses.size - 1) {
                        combinedActions.add(ActionType.Wait(800L))
                    }
                }
            }
            if (combinedActions.isNotEmpty()) {
                return combinedActions
            }
        }

        // Single action
        val single = parseSingleAction(trimmedInput)
        return if (single != null) listOf(single) else listOf(ActionType.Tap(input.trim()))
    }

    private fun parseSingleAction(text: String): ActionType? {
        val clean = text.trim()

        // 1. Navigation Actions
        if (clean.matches(Regex("^(go\\s+)?back(?:\\s+twice|\\s+2\\s+times)$", RegexOption.IGNORE_CASE))) {
            return ActionType.Back(times = 2)
        }
        val backTimesMatch = Regex("^(?:go\\s+)?back(?:\\s+(\\d+)\\s+times)$", RegexOption.IGNORE_CASE).find(clean)
        if (backTimesMatch != null) {
            val count = backTimesMatch.groupValues[1].toIntOrNull() ?: 1
            return ActionType.Back(times = count)
        }
        if (clean.matches(Regex("^(?:go\\s+)?back$", RegexOption.IGNORE_CASE))) {
            return ActionType.Back(times = 1)
        }
        if (clean.matches(Regex("^(?:go\\s+)?home$", RegexOption.IGNORE_CASE))) {
            return ActionType.Home
        }
        if (clean.matches(Regex("^(?:show\\s+)?recents?|(?:recent\\s+apps)|overview$", RegexOption.IGNORE_CASE))) {
            return ActionType.Recents
        }

        // 2. Open / Launch App
        val openAppMatch = Regex("^(?:open|launch|start|go to)\\s+(?:the\\s+)?(.+?)(?:\\s+app)?$", RegexOption.IGNORE_CASE).find(clean)
        if (openAppMatch != null) {
            val appName = openAppMatch.groupValues[1].trim()
            if (appName.isNotBlank()) {
                return ActionType.OpenApp(appName)
            }
        }

        // 3. Scroll & Swipe
        if (clean.matches(Regex("^(?:scroll|move)\\s+down$", RegexOption.IGNORE_CASE))) {
            return ActionType.Scroll(ScrollDirection.DOWN)
        }
        if (clean.matches(Regex("^(?:scroll|move)\\s+up$", RegexOption.IGNORE_CASE))) {
            return ActionType.Scroll(ScrollDirection.UP)
        }
        if (clean.matches(Regex("^swipe\\s+up$", RegexOption.IGNORE_CASE))) {
            return ActionType.Swipe(SwipeDirection.UP)
        }
        if (clean.matches(Regex("^swipe\\s+down$", RegexOption.IGNORE_CASE))) {
            return ActionType.Swipe(SwipeDirection.DOWN)
        }
        if (clean.matches(Regex("^swipe\\s+left$", RegexOption.IGNORE_CASE))) {
            return ActionType.Swipe(SwipeDirection.LEFT)
        }
        if (clean.matches(Regex("^swipe\\s+right$", RegexOption.IGNORE_CASE))) {
            return ActionType.Swipe(SwipeDirection.RIGHT)
        }

        // 4. Screenshots & Screen Reading
        if (clean.matches(Regex("^(?:take\\s+(?:a\\s+)?screenshot|capture\\s+screen|screenshot)$", RegexOption.IGNORE_CASE))) {
            return ActionType.TakeScreenshot
        }
        if (clean.matches(Regex("^(?:read\\s+(?:what(?:'s|\\s+is)\\s+on\\s+(?:the\\s+)?screen|screen|visible\\s+text))$", RegexOption.IGNORE_CASE))) {
            return ActionType.ReadScreen
        }

        // 5. Volume Controls
        if (clean.matches(Regex("^(?:turn\\s+)?volume\\s+up|louder|increase\\s+volume$", RegexOption.IGNORE_CASE))) {
            return ActionType.VolumeUp
        }
        if (clean.matches(Regex("^(?:turn\\s+)?volume\\s+down|quieter|decrease\\s+volume$", RegexOption.IGNORE_CASE))) {
            return ActionType.VolumeDown
        }

        // 6. Enter / Search Key
        if (clean.matches(Regex("^(?:press\\s+)?enter|press\\s+search|submit$", RegexOption.IGNORE_CASE))) {
            return ActionType.PressEnter
        }

        // 7. Type Text: "type [text]", "write [text]", "enter [text]"
        val typeMatch = Regex("^(?:type|write|enter|input)\\s+(?:text\\s+)?(.+)$", RegexOption.IGNORE_CASE).find(clean)
        if (typeMatch != null) {
            val textToType = typeMatch.groupValues[1].trim('"', '\'', ' ')
            return ActionType.TypeText(textToType)
        }

        // 8. Search query alone: "search for [query]"
        val searchForMatch = Regex("^search\\s+for\\s+(.+)$", RegexOption.IGNORE_CASE).find(clean)
        if (searchForMatch != null) {
            val query = searchForMatch.groupValues[1].trim('"', '\'', ' ')
            return ActionType.TypeText(query)
        }

        // 9. Tap / Click / Press / Select UI Element
        val tapMatch = Regex("^(?:tap|click|press|select|choose)\\s+(?:the\\s+)?(.+?)(?:\\s+button|\\s+icon|\\s+tab)?$", RegexOption.IGNORE_CASE).find(clean)
        if (tapMatch != null) {
            val target = tapMatch.groupValues[1].trim('"', '\'', ' ')
            return ActionType.Tap(target)
        }

        // 10. Long press
        val longPressMatch = Regex("^long\\s+press\\s+(?:the\\s+)?(.+?)(?:\\s+button)?$", RegexOption.IGNORE_CASE).find(clean)
        if (longPressMatch != null) {
            val target = longPressMatch.groupValues[1].trim('"', '\'', ' ')
            return ActionType.LongPress(target)
        }

        return null
    }
}
