package com.example.action

sealed interface ActionType {
    val description: String

    data class OpenApp(val appName: String) : ActionType {
        override val description: String = "Open app: $appName"
    }

    data class Back(val times: Int = 1) : ActionType {
        override val description: String = if (times > 1) "Go back $times times" else "Go back"
    }

    data object Home : ActionType {
        override val description: String = "Go home"
    }

    data object Recents : ActionType {
        override val description: String = "Show recent apps"
    }

    data class Tap(val target: String) : ActionType {
        override val description: String = "Tap \"$target\""
    }

    data class TapCoordinates(val x: Float, val y: Float) : ActionType {
        override val description: String = "Tap at ($x, $y)"
    }

    data class LongPress(val target: String) : ActionType {
        override val description: String = "Long press \"$target\""
    }

    data class Swipe(val direction: SwipeDirection) : ActionType {
        override val description: String = "Swipe ${direction.name.lowercase()}"
    }

    data class Scroll(val direction: ScrollDirection) : ActionType {
        override val description: String = "Scroll ${direction.name.lowercase()}"
    }

    data class TypeText(val text: String, val targetField: String? = null) : ActionType {
        override val description: String = if (targetField != null) {
            "Type \"$text\" into $targetField"
        } else {
            "Type \"$text\""
        }
    }

    data object PressEnter : ActionType {
        override val description: String = "Press Enter / Search"
    }

    data object TakeScreenshot : ActionType {
        override val description: String = "Take screenshot"
    }

    data object ReadScreen : ActionType {
        override val description: String = "Read visible screen content"
    }

    data class Wait(val durationMs: Long = 1000L) : ActionType {
        override val description: String = "Wait ${durationMs}ms"
    }

    data class Speak(val message: String) : ActionType {
        override val description: String = "Say \"$message\""
    }

    data object VolumeUp : ActionType {
        override val description: String = "Increase volume"
    }

    data object VolumeDown : ActionType {
        override val description: String = "Decrease volume"
    }

    data class FindText(val text: String) : ActionType {
        override val description: String = "Find text: $text"
    }

    data class FindDescription(val contentDescription: String) : ActionType {
        override val description: String = "Find description: $contentDescription"
    }

    data class FindId(val viewId: String) : ActionType {
        override val description: String = "Find view ID: $viewId"
    }
}
