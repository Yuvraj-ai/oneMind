package com.onemind.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.onemind.app.ui.composer.ComposerScreen
import com.onemind.app.ui.feed.FeedScreen
import com.onemind.app.ui.feed.MemoryDetailScreen
import com.onemind.app.ui.onboarding.OnboardingScreen
import com.onemind.app.ui.settings.SettingsScreen

/**
 * Main navigation graph for oneMind.
 * Start destination is determined by whether onboarding is complete.
 */
@Composable
fun OneMindNavHost(
    navController: NavHostController,
    startDestination: String = NavRoutes.FEED,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(NavRoutes.ONBOARDING) {
            OnboardingScreen(
                onOnboardingComplete = {
                    navController.navigate(NavRoutes.FEED) {
                        popUpTo(NavRoutes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        composable(NavRoutes.FEED) {
            FeedScreen(
                onNavigateToComposer = {
                    navController.navigate(NavRoutes.COMPOSER)
                },
                onNavigateToMemory = { memoryId ->
                    navController.navigate(NavRoutes.memoryDetail(memoryId))
                },
                onNavigateToSettings = {
                    navController.navigate(NavRoutes.SETTINGS)
                }
            )
        }

        composable(NavRoutes.COMPOSER) {
            ComposerScreen(
                memoryId = null,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = NavRoutes.COMPOSER_EDIT,
            arguments = listOf(navArgument("memoryId") { type = NavType.LongType })
        ) { backStackEntry ->
            val memoryId = backStackEntry.arguments?.getLong("memoryId") ?: 0L
            ComposerScreen(
                memoryId = memoryId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = NavRoutes.MEMORY_DETAIL,
            arguments = listOf(navArgument("memoryId") { type = NavType.LongType })
        ) { backStackEntry ->
            val memoryId = backStackEntry.arguments?.getLong("memoryId") ?: 0L
            MemoryDetailScreen(
                memoryId = memoryId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEdit = { id ->
                    navController.navigate(NavRoutes.composerEdit(id))
                }
            )
        }

        composable(NavRoutes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
