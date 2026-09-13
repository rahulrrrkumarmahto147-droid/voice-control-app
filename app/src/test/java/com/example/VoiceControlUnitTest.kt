package com.example

import com.example.action.ActionType
import com.example.action.ScrollDirection
import com.example.action.SwipeDirection
import com.example.parser.RuleBasedCommandParser
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VoiceControlUnitTest {

    private lateinit var parser: RuleBasedCommandParser

    @Before
    fun setup() {
        parser = RuleBasedCommandParser()
    }

    @Test
    fun testOpenAppCommand() = runTest {
        val result = parser.parse("Open YouTube")
        assertEquals(1, result.actions.size)
        assertTrue(result.actions[0] is ActionType.OpenApp)
        assertEquals("youtube", (result.actions[0] as ActionType.OpenApp).appName.lowercase())
        assertFalse(result.requiresConfirmation)
    }

    @Test
    fun testGoBackTwice() = runTest {
        val result = parser.parse("Go back twice")
        assertEquals(1, result.actions.size)
        assertTrue(result.actions[0] is ActionType.Back)
        assertEquals(2, (result.actions[0] as ActionType.Back).times)
    }

    @Test
    fun testGoHome() = runTest {
        val result = parser.parse("Go home")
        assertEquals(1, result.actions.size)
        assertTrue(result.actions[0] is ActionType.Home)
    }

    @Test
    fun testScrollDown() = runTest {
        val result = parser.parse("Scroll down")
        assertEquals(1, result.actions.size)
        assertTrue(result.actions[0] is ActionType.Scroll)
        assertEquals(ScrollDirection.DOWN, (result.actions[0] as ActionType.Scroll).direction)
    }

    @Test
    fun testSwipeLeft() = runTest {
        val result = parser.parse("Swipe left")
        assertEquals(1, result.actions.size)
        assertTrue(result.actions[0] is ActionType.Swipe)
        assertEquals(SwipeDirection.LEFT, (result.actions[0] as ActionType.Swipe).direction)
    }

    @Test
    fun testTapButton() = runTest {
        val result = parser.parse("Tap the search button")
        assertEquals(1, result.actions.size)
        assertTrue(result.actions[0] is ActionType.Tap)
        assertEquals("search", (result.actions[0] as ActionType.Tap).target.lowercase())
    }

    @Test
    fun testTypeText() = runTest {
        val result = parser.parse("Type technology news")
        assertEquals(1, result.actions.size)
        assertTrue(result.actions[0] is ActionType.TypeText)
        assertEquals("technology news", (result.actions[0] as ActionType.TypeText).text)
    }

    @Test
    fun testTakeScreenshot() = runTest {
        val result = parser.parse("Take a screenshot")
        assertEquals(1, result.actions.size)
        assertTrue(result.actions[0] is ActionType.TakeScreenshot)
    }

    @Test
    fun testReadScreen() = runTest {
        val result = parser.parse("Read what's on the screen")
        assertEquals(1, result.actions.size)
        assertTrue(result.actions[0] is ActionType.ReadScreen)
    }

    @Test
    fun testCompoundOpenAndSearch() = runTest {
        val result = parser.parse("Open Chrome and search for latest AI news")
        // Expected sequence: OpenApp, Wait, Tap("Search"), Wait, TypeText("latest AI news"), PressEnter
        assertTrue(result.actions.size >= 4)
        assertTrue(result.actions[0] is ActionType.OpenApp)
        assertEquals("chrome", (result.actions[0] as ActionType.OpenApp).appName.lowercase())
        assertTrue(result.actions.any { it is ActionType.TypeText && it.text == "latest AI news" })
        assertTrue(result.actions.any { it is ActionType.PressEnter })
    }

    @Test
    fun testCompoundOpenSearchAndTapFirstResult() = runTest {
        val result = parser.parse("Open Instagram, search for Cristiano Ronaldo, and open the first result")
        assertTrue(result.actions.size >= 6)
        assertTrue(result.actions[0] is ActionType.OpenApp)
        assertEquals("instagram", (result.actions[0] as ActionType.OpenApp).appName.lowercase())
        assertTrue(result.actions.any { it is ActionType.TypeText && it.text == "Cristiano Ronaldo" })
        assertTrue(result.actions.any { it is ActionType.Tap && it.target.contains("first result", ignoreCase = true) })
    }

    @Test
    fun testDestructiveConfirmation() = runTest {
        val result = parser.parse("Send this message to John")
        assertTrue(result.requiresConfirmation)
        assertTrue(result.confirmationPrompt!!.contains("send a message", ignoreCase = true))
    }

    @Test
    fun testDeleteConfirmation() = runTest {
        val result = parser.parse("Delete all downloads")
        assertTrue(result.requiresConfirmation)
        assertTrue(result.confirmationPrompt!!.contains("delete content", ignoreCase = true))
    }
}
