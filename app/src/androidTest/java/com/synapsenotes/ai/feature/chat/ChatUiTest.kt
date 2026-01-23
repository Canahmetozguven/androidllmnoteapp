package com.synapsenotes.ai.feature.chat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.synapsenotes.ai.ui.theme.LlmNotesTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testChatEmptyStateDisplaysCorrectly() {
        composeTestRule.setContent {
            LlmNotesTheme {
                ChatEmptyState()
            }
        }

        // Verify key text elements are displayed
        composeTestRule.onNodeWithText("Chat with your notes").assertIsDisplayed()
        composeTestRule.onNodeWithText("Try asking:").assertIsDisplayed()
        composeTestRule.onNodeWithText("Summarize my recent notes").assertIsDisplayed()
    }

    @Test
    fun testChatInputBarDisplaysPlaceholder() {
        composeTestRule.setContent {
            LlmNotesTheme {
                ChatInputBar(
                    inputText = "",
                    onInputChange = {},
                    onSend = {},
                    isLoading = false
                )
            }
        }

        // Verify placeholder text
        composeTestRule.onNodeWithText("Ask about your notes...").assertIsDisplayed()
        // Verify disclaimer
        composeTestRule.onNodeWithText("AI can make mistakes. Check important info.").assertIsDisplayed()
    }
}
