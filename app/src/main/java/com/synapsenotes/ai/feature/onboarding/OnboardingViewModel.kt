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
            // TODO: Initialize Drive Service with this account
            _uiState.value = _uiState.value.copy(
                isGoogleDriveConnected = true,
                userEmail = account.email
            )
        }
    }
}

data class OnboardingUiState(
    val isGoogleDriveConnected: Boolean = false,
    val userEmail: String? = null
)
