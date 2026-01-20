package com.example.llmnotes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.llmnotes.feature.chat.ChatScreen
import com.example.llmnotes.feature.notes.NoteDetailScreen
import com.example.llmnotes.feature.notes.NoteListScreen
import com.example.llmnotes.feature.onboarding.OnboardingScreen
import com.example.llmnotes.feature.settings.SettingsScreen
import com.example.llmnotes.ui.theme.LlmNotesTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LlmNotesTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "onboarding") {
                        composable("onboarding") {
                            OnboardingScreen(
                                onFinish = {
                                    navController.navigate("notes") {
                                        popUpTo("onboarding") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("notes") {
                            NoteListScreen(
                                onNoteClick = { noteId ->
                                    navController.navigate("noteDetail/$noteId")
                                },
                                onCreateNote = {
                                    navController.navigate("noteDetail/new")
                                },
                                onSettingsClick = {
                                    navController.navigate("settings")
                                },
                                onChatClick = {
                                    navController.navigate("chat")
                                }
                            )
                        }
                        composable(
                            "noteDetail/{noteId}",
                            arguments = listOf(navArgument("noteId") { type = NavType.StringType })
                        ) {
                            NoteDetailScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("chat") {
                            ChatScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
