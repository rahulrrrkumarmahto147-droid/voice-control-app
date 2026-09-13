package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.util.DisplayMetrics
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.action.ScrollDirection
import com.example.action.SwipeDirection
import com.example.screen.ScreenAnalyzer
import com.example.screen.ScreenNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class VoiceControlAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        currentInstance = this
        _isServiceActive.value = true
        Log.i(TAG, "VoiceControlAccessibilityService connected successfully.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString()
        if (!pkg.isNullOrBlank()) {
            _currentForegroundPackage.value = pkg
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "VoiceControlAccessibilityService interrupted.")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (currentInstance == this) {
            currentInstance = null
            _isServiceActive.value = false
        }
        Log.i(TAG, "VoiceControlAccessibilityService destroyed.")
    }

    /**
     * Finds an element matching [target] and clicks it.
     * Uses semantic node interaction first; falls back to gesture tap.
     */
    fun findAndClick(target: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val match = ScreenAnalyzer.findBestMatch(root, target, requireClickable = false)

        if (match != null) {
            // Try direct click on node
            if (match.node?.isClickable == true && match.node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                Log.d(TAG, "Clicked node directly: ${match.displayText}")
                return true
            }

            // Try clickable parent
            val clickableParent = ScreenAnalyzer.findClickableParent(match.node)
            if (clickableParent != null && clickableParent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                Log.d(TAG, "Clicked parent node for: ${match.displayText}")
                return true
            }

            // Fallback to coordinate-based gesture tap at the center of the element
            val bounds = match.bounds
            if (bounds.width() > 0 && bounds.height() > 0) {
                Log.d(TAG, "Falling back to gesture tap at (${bounds.exactCenterX()}, ${bounds.exactCenterY()})")
                return dispatchTap(bounds.exactCenterX(), bounds.exactCenterY())
            }
        }

        Log.w(TAG, "Could not find or click target: $target")
        return false
    }

    /**
     * Finds an element matching [target] and performs a long click.
     */
    fun findAndLongClick(target: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val match = ScreenAnalyzer.findBestMatch(root, target)

        if (match != null) {
            if (match.node?.isLongClickable == true && match.node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)) {
                return true
            }

            val parent = match.node?.parent
            if (parent?.isLongClickable == true && parent.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)) {
                return true
            }

            return dispatchLongPress(match.centerX, match.centerY)
        }
        return false
    }

    /**
     * Types [text] into an editable field.
     * If [targetField] is specified, finds matching field first; otherwise uses the currently
     * focused or first available editable field on the screen.
     */
    fun setEditText(targetField: String?, text: String): Boolean {
        val root = rootInActiveWindow ?: return false

        val targetNode: AccessibilityNodeInfo? = if (!targetField.isNullOrBlank()) {
            ScreenAnalyzer.findBestMatch(root, targetField, requireEditable = true)?.node
        } else {
            // Check for focused node first
            val focused = findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focused != null && focused.isEditable) {
                focused
            } else {
                // Find first editable node in tree
                ScreenAnalyzer.analyzeTree(root).firstOrNull { it.isEditable }?.node
            }
        }

        if (targetNode != null) {
            // Focus on element if needed
            targetNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)

            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            val success = targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            Log.d(TAG, "Set text '$text' result: $success")
            return success
        }

        Log.w(TAG, "No editable field found to type text: $text")
        return false
    }

    /**
     * Performs a scroll action in the given direction.
     */
    fun performScroll(direction: ScrollDirection): Boolean {
        val root = rootInActiveWindow
        val scrollableNode = if (root != null) {
            ScreenAnalyzer.analyzeTree(root).firstOrNull { it.isScrollable }?.node
        } else null

        val action = when (direction) {
            ScrollDirection.DOWN -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            ScrollDirection.UP -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }

        if (scrollableNode != null && scrollableNode.performAction(action)) {
            Log.d(TAG, "Scrolled ${direction.name} via node action.")
            return true
        }

        // Gesture scroll fallback
        val swipeDir = when (direction) {
            ScrollDirection.DOWN -> SwipeDirection.UP // To scroll down, content moves up
            ScrollDirection.UP -> SwipeDirection.DOWN
        }
        return performSwipe(swipeDir)
    }

    /**
     * Performs a directional swipe gesture across the screen.
     */
    fun performSwipe(direction: SwipeDirection): Boolean {
        val metrics: DisplayMetrics = resources.displayMetrics
        val width = metrics.widthPixels.toFloat()
        val height = metrics.heightPixels.toFloat()

        val startX: Float
        val startY: Float
        val endX: Float
        val endY: Float

        when (direction) {
            SwipeDirection.UP -> {
                startX = width * 0.5f
                startY = height * 0.75f
                endX = width * 0.5f
                endY = height * 0.25f
            }
            SwipeDirection.DOWN -> {
                startX = width * 0.5f
                startY = height * 0.25f
                endX = width * 0.5f
                endY = height * 0.75f
            }
            SwipeDirection.LEFT -> {
                startX = width * 0.8f
                startY = height * 0.5f
                endX = width * 0.2f
                endY = height * 0.5f
            }
            SwipeDirection.RIGHT -> {
                startX = width * 0.2f
                startY = height * 0.5f
                endX = width * 0.8f
                endY = height * 0.5f
            }
        }

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 350))
            .build()

        return dispatchGesture(gesture, null, null)
    }

    /**
     * Dispatches a tap gesture at the specified screen coordinates.
     */
    fun dispatchTap(x: Float, y: Float): Boolean {
        val path = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 80)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }

    /**
     * Dispatches a long press gesture at screen coordinates.
     */
    fun dispatchLongPress(x: Float, y: Float): Boolean {
        val path = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 1000)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }

    /**
     * Global Back navigation.
     */
    fun performBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)

    /**
     * Global Home navigation.
     */
    fun performHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)

    /**
     * Global Recents / Overview navigation.
     */
    fun performRecents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)

    /**
     * Global Screenshot capture (API 28+).
     */
    fun performScreenshot(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
        } else {
            Log.w(TAG, "Screenshots via accessibility require Android 9.0 (API 28) or higher.")
            false
        }
    }

    /**
     * Extracts and returns readable visible screen text.
     */
    fun readScreen(): String {
        val root = rootInActiveWindow
        return ScreenAnalyzer.extractReadableText(root)
    }

    companion object {
        private const val TAG = "VoiceControlService"

        @Volatile
        var currentInstance: VoiceControlAccessibilityService? = null
            private set

        private val _isServiceActive = MutableStateFlow(false)
        val isServiceActive: StateFlow<Boolean> = _isServiceActive.asStateFlow()

        private val _currentForegroundPackage = MutableStateFlow<String?>(null)
        val currentForegroundPackage: StateFlow<String?> = _currentForegroundPackage.asStateFlow()

        /**
         * Checks if the Accessibility Service is enabled in system settings.
         */
        fun isAccessibilitySettingsEnabled(context: Context): Boolean {
            val expectedServiceName = "${context.packageName}/${VoiceControlAccessibilityService::class.java.name}"
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServices)
            while (colonSplitter.hasNext()) {
                val componentName = colonSplitter.next()
                if (componentName.equals(expectedServiceName, ignoreCase = true)) {
                    return true
                }
            }
            return false
        }

        /**
         * Creates an Intent to launch the Accessibility settings screen.
         */
        fun createAccessibilitySettingsIntent(): Intent {
            return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }
}
