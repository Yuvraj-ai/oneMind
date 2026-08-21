package com.onemind.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onemind.app.data.ai.OnboardingPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * App-level ViewModel that determines the initial navigation state.
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    onboardingPreferences: OnboardingPreferences
) : ViewModel() {

    /**
     * null = still loading, true = onboarding done, false = needs onboarding
     */
    val isOnboardingComplete: StateFlow<Boolean?> = onboardingPreferences.isOnboardingComplete
        .map<Boolean, Boolean?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
