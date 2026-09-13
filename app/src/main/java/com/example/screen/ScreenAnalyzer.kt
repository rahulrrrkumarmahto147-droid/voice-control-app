package com.example.screen

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

object ScreenAnalyzer {
    private const val TAG = "ScreenAnalyzer"

    /**
     * Traverses the accessibility node tree and builds a flat list of visible elements.
     */
    fun analyzeTree(root: AccessibilityNodeInfo?): List<ScreenNodeInfo> {
        if (root == null) return emptyList()
        val result = mutableListOf<ScreenNodeInfo>()
        traverse(root, result)
        return result
    }

    private fun traverse(node: AccessibilityNodeInfo?, list: MutableList<ScreenNodeInfo>) {
        if (node == null) return

        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        // Only include nodes with non-zero bounds that are visible or have content
        val hasContent = !node.text.isNullOrBlank() || !node.contentDescription.isNullOrBlank()
        val isInteractive = node.isClickable || node.isEditable || node.isScrollable

        if ((hasContent || isInteractive) && bounds.width() > 0 && bounds.height() > 0) {
            list.add(
                ScreenNodeInfo(
                    text = node.text?.toString()?.trim(),
                    contentDescription = node.contentDescription?.toString()?.trim(),
                    viewId = node.viewIdResourceName,
                    className = node.className?.toString(),
                    isClickable = node.isClickable,
                    isEditable = node.isEditable,
                    isScrollable = node.isScrollable,
                    isVisibleToUser = node.isVisibleToUser,
                    bounds = bounds,
                    node = node
                )
            )
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                traverse(child, list)
            }
        }
    }

    /**
     * Finds the best matching node for a query string.
     * Supports matching by exact text, case-insensitive text, content description,
     * view ID, or interactive containers containing the query.
     */
    fun findBestMatch(
        root: AccessibilityNodeInfo?,
        query: String,
        requireClickable: Boolean = false,
        requireEditable: Boolean = false
    ): ScreenNodeInfo? {
        if (root == null || query.isBlank()) return null

        val nodes = analyzeTree(root)
        val cleanQuery = query.trim().lowercase()

        // Scoring algorithm
        var bestNode: ScreenNodeInfo? = null
        var highestScore = -1

        for (item in nodes) {
            if (requireEditable && !item.isEditable) continue

            val text = item.text?.lowercase() ?: ""
            val desc = item.contentDescription?.lowercase() ?: ""
            val viewId = item.viewId?.lowercase() ?: ""

            var score = 0

            when {
                // Exact text match
                text == cleanQuery -> score += 100
                // Exact description match
                desc == cleanQuery -> score += 95
                // Text starts with query
                text.startsWith(cleanQuery) -> score += 80
                // Description starts with query
                desc.startsWith(cleanQuery) -> score += 75
                // Text contains query as full word or substring
                text.contains(cleanQuery) -> score += 60
                // Description contains query
                desc.contains(cleanQuery) -> score += 55
                // View ID contains query
                viewId.contains(cleanQuery) -> score += 40
            }

            if (score > 0) {
                // Bonus points for clickable elements
                if (item.isClickable) score += 20
                if (item.isEditable && requireEditable) score += 20
                if (item.isVisibleToUser) score += 10

                // If user specifically asked for clickable or it matches query well
                if (!requireClickable || item.isClickable || findClickableParent(item.node) != null) {
                    if (score > highestScore) {
                        highestScore = score
                        bestNode = item
                    }
                }
            }
        }

        Log.d(TAG, "Search for '$query' found best match: ${bestNode?.displayText} with score $highestScore")
        return bestNode
    }

    /**
     * Traverses upwards to find the closest clickable ancestor node.
     */
    fun findClickableParent(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var current = node?.parent
        while (current != null) {
            if (current.isClickable) {
                return current
            }
            current = current.parent
        }
        return null
    }

    /**
     * Extracts all readable visible text on the screen for the Read Screen command.
     */
    fun extractReadableText(root: AccessibilityNodeInfo?): String {
        val nodes = analyzeTree(root)
        val extracted = mutableListOf<String>()
        val seen = mutableSetOf<String>()

        for (item in nodes) {
            val label = item.displayText
            if (label.isNotBlank() && seen.add(label.lowercase())) {
                extracted.add(label)
            }
        }

        return if (extracted.isEmpty()) {
            "No readable text found on the screen."
        } else {
            extracted.joinToString(separator = ". ")
        }
    }
}
