package com.example.parser

import android.graphics.Bitmap
import android.graphics.Rect

/**
 * Interface for analyzing screen captures using visual multimodal AI models.
 * Used as a fallback when accessibility node hierarchy does not contain semantic text
 * or when identifying visual elements like "the blue circular icon".
 */
interface ScreenVision {
    suspend fun findElementByDescription(screenshot: Bitmap, visualQuery: String): Rect?
    suspend fun describeScreen(screenshot: Bitmap): String
}

/**
 * Default local implementation that gracefully defers to accessibility tree.
 */
class DefaultScreenVision : ScreenVision {
    override suspend fun findElementByDescription(screenshot: Bitmap, visualQuery: String): Rect? {
        // Architecture hook: Connected to backend or Gemini Vision API in production
        return null
    }

    override suspend fun describeScreen(screenshot: Bitmap): String {
        return "Visual vision model not configured. Utilizing accessibility screen hierarchy."
    }
}
