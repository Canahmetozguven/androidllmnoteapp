package com.example.llmnotes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.llmnotes.feature.chat.ChatScreen
import com.example.llmnotes.feature.files.FilesScreen
import com.example.llmnotes.feature.notes.NoteDetailScreen
import com.example.llmnotes.feature.notes.NoteListScreen
import com.example.llmnotes.feature.onboarding.OnboardingScreen
import com.example.llmnotes.feature.settings.SettingsScreen
import com.example.llmnotes.ui.components.AppBottomNavBar
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
                    // Triggers auto-loading of models via init block
                    val mainViewModel: MainViewModel = hiltViewModel() 
                    val startDestination = if (mainViewModel.isOnboardingCompleted) "notes" else "onboarding"
                    MainScreen(navController = navController, startDestination = startDestination)
                }
            }
        }
    }
}

// Routes that should show the bottom navigation bar
private val bottomNavRoutes = setOf("notes", "chat", "files")

@Composable
private fun MainScreen(
    navController: NavHostController,
    startDestination: String
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    
    // Determine if bottom nav should be visible
    val showBottomNav = currentRoute in bottomNavRoutes

    Scaffold(
        bottomBar = {
            if (showBottomNav) {
                AppBottomNavBar(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            // Pop up to the start destination to avoid building up a large back stack
                            popUpTo("notes") {
                                saveState = true
                            }
                            // Avoid multiple copies of the same destination
                            launchSingleTop = true
                            // Restore state when re-selecting a previously selected item
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = if (showBottomNav) Modifier.padding(innerPadding) else Modifier
        ) {
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
                        navController.navigate("chat") {
                            popUpTo("notes") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
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
            composable("files") {
                FilesScreen()
            }
        }
    }
}
