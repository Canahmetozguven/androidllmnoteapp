package com.synapsenotes.ai.feature.notes

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.synapsenotes.ai.ui.theme.LlmNotesTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoteDetailUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testFormattingToolbar_DoneButtonTriggerCallback() {
        var onDoneCalled = false
        
        composeTestRule.setContent {
            LlmNotesTheme {
                FormattingToolbar(
                    onDone = { onDoneCalled = true }
                )
            }
        }

        // Verify "Done" text is displayed
        val doneButton = composeTestRule.onNodeWithText("Done")
        
        // Click it
        doneButton.performClick()

        // Verify callback was triggered
        assertTrue("onDone callback should be called when Done is clicked", onDoneCalled)
    }
}
