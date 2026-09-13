package com.example.screen

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

data class ScreenNodeInfo(
    val text: String?,
    val contentDescription: String?,
    val viewId: String?,
    val className: String?,
    val isClickable: Boolean,
    val isEditable: Boolean,
    val isScrollable: Boolean,
    val isVisibleToUser: Boolean,
    val bounds: Rect,
    val node: AccessibilityNodeInfo?
) {
    val displayText: String
        get() = text?.takeIf { it.isNotBlank() }
            ?: contentDescription?.takeIf { it.isNotBlank() }
            ?: ""

    val centerX: Float
        get() = bounds.exactCenterX()

    val centerY: Float
        get() = bounds.exactCenterY()
}
