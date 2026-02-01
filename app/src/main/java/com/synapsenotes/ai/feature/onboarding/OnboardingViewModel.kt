package com.synapsenotes.ai.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.synapsenotes.ai.core.preferences.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val googleSignInClient: GoogleSignInClient,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun completeOnboarding() {
        appPreferences.onboardingCompleted = true
    }

    fun getSignInIntent(): Intent {
        return googleSignInClient.signInIntent
    }

    fun onGoogleSignInSuccess(account: GoogleSignInAccount) {
        viewModelScope.launch {
            appPreferences.isDriveConnected = true
            appPreferences.driveEmail = account.email
            
            _uiState.value = _uiState.value.copy(
                isGoogleDriveConnected = true,
                userEmail = account.email
            )
        }
    }

    fun handleSignInResult(intent: Intent?) {
        if (intent == null) {
             android.util.Log.e("OnboardingVM", "Sign in result intent is null")
             // Could update UI state to show error
             return
        }
        try {
            val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(intent)
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            if (account != null) {
                onGoogleSignInSuccess(account)
            }
        } catch (e: com.google.android.gms.common.api.ApiException) {
            android.util.Log.e("OnboardingVM", "Sign in failed code: ${e.statusCode}", e)
        }
    }
}

data class OnboardingUiState(
    val isGoogleDriveConnected: Boolean = false,
    val userEmail: String? = null
)
