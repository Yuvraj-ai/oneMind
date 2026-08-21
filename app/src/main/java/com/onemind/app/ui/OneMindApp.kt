package com.onemind.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.onemind.app.ui.navigation.OneMindNavHost
import com.onemind.app.ui.theme.OneMindTheme

@Composable
fun OneMindApp() {
    OneMindTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            val navController = rememberNavController()
            OneMindNavHost(navController = navController)
        }
    }
}
