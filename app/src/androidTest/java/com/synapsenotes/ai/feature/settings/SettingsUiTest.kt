package com.synapsenotes.ai.feature.settings

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.synapsenotes.ai.ui.theme.LlmNotesTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testCloudBackupCard_DisconnectedState_ShowsGoogleSignIn() {
        var clicked = false
        composeTestRule.setContent {
            LlmNotesTheme {
                CloudBackupCard(
                    isConnected = false,
                    userEmail = null,
                    onConnect = { clicked = true }
                )
            }
        }

        // Verify "Sign in with Google" button is displayed
        composeTestRule.onNodeWithText("Sign in with Google")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()

        assert(clicked) { "Connect button should trigger onClick" }

        // Verify "Google Drive" title is displayed
        composeTestRule.onNodeWithText("Google Drive").assertIsDisplayed()
        
        // Verify status text
        composeTestRule.onNodeWithText("Last synced: Never").assertIsDisplayed()
    }

    @Test
    fun testCloudBackupCard_ConnectedState_ShowsSyncNow() {
        var clicked = false
        composeTestRule.setContent {
            LlmNotesTheme {
                CloudBackupCard(
                    isConnected = true,
                    userEmail = "test@example.com",
                    isSyncing = false,
                    onConnect = { clicked = true }
                )
            }
        }

        // Verify "Sync Now" button is displayed
        composeTestRule.onNodeWithText("Sync Now")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()

        assert(clicked) { "Sync button should trigger onClick" }

        // Verify email is displayed
        composeTestRule.onNodeWithText("Connected as test@example.com").assertIsDisplayed()
    }

    @Test
    fun testCloudBackupCard_SyncingState_ButtonDisabled() {
        composeTestRule.setContent {
            LlmNotesTheme {
                CloudBackupCard(
                    isConnected = true,
                    userEmail = "test@example.com",
                    isSyncing = true,
                    onConnect = {}
                )
            }
        }

        // Button should be displayed but maybe disabled or replaced by progress indicator
        // In the code: enabled = !isSyncing
        // And content changes to CircularProgressIndicator
        
        // We can check if "Sync Now" is NOT displayed (replaced by progress) or if the button container is disabled.
        // Actually, looking at the code:
        /*
            if (isSyncing) {
                CircularProgressIndicator(...)
            } else {
                Text("Sync Now")
            }
        */
        // So "Sync Now" text should NOT be present.
        composeTestRule.onNodeWithText("Sync Now").assertDoesNotExist()
    }
    
    @Test
    fun testAboutPrivacyButton() {
        var clicked = false
        composeTestRule.setContent {
            LlmNotesTheme {
                AboutPrivacyButton(onClick = { clicked = true })
            }
        }
        
        composeTestRule.onNodeWithText("About & Privacy")
            .assertIsDisplayed()
            .performClick()
            
        assert(clicked)
    }
}
