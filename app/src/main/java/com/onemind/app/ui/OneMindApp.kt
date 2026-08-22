package com.onemind.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.onemind.app.ui.navigation.NavRoutes
import com.onemind.app.ui.navigation.OneMindNavHost
import com.onemind.app.ui.theme.OneMindTheme

@Composable
fun OneMindApp(
    openMemoryId: Long? = null,
    appViewModel: AppViewModel = hiltViewModel()
) {
    OneMindTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            val isOnboardingComplete by appViewModel.isOnboardingComplete.collectAsState()

            when (isOnboardingComplete) {
                null -> {
                    // Loading state while checking DataStore
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                else -> {
                    val navController = rememberNavController()
                    val startDestination = if (isOnboardingComplete == true) {
                        NavRoutes.FEED
                    } else {
                        NavRoutes.ONBOARDING
                    }
                    OneMindNavHost(
                        navController = navController,
                        startDestination = startDestination
                    )

                    // Navigate to a specific Memory when launched from a
                    // notification tap. LaunchedEffect keyed on the id ensures
                    // it fires once per distinct navigation request.
                    LaunchedEffect(openMemoryId) {
                        if (openMemoryId != null && isOnboardingComplete == true) {
                            navController.navigate(NavRoutes.memoryDetail(openMemoryId)) {
                                launchSingleTop = true
                            }
                        }
                    }
                }
            }
        }
    }
}
