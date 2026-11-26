package com.nmamit.attendance.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nmamit.attendance.ui.auth.AuthViewModel
import com.nmamit.attendance.ui.auth.LoginScreen
import com.nmamit.attendance.ui.dashboard.DashboardScreen
import com.nmamit.attendance.ui.dashboard.DashboardViewModel
import com.nmamit.attendance.ui.theme.BgCanvas
import com.nmamit.attendance.ui.theme.Foreground

object AppRoutes {
    const val LOGIN = "login"
    const val DASHBOARD = "dashboard"
}

@Composable
fun AppNavGraph(
    navController: NavHostController = rememberNavController(),
    authViewModel: AuthViewModel,
    dashboardViewModel: DashboardViewModel
) {
    val authState by authViewModel.uiState.collectAsStateWithLifecycle()

    if (authState.isCheckingSession) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BgCanvas),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                color = Foreground,
                strokeWidth = 2.dp,
                modifier = Modifier.size(28.dp)
            )
        }
        return
    }

    val initialDestination = remember {
        if (authState.isLoggedIn) AppRoutes.DASHBOARD else AppRoutes.LOGIN
    }

    LaunchedEffect(initialDestination) {
        if (initialDestination == AppRoutes.DASHBOARD) {
            dashboardViewModel.loadAllInitialData()
        }
    }

    NavHost(
        navController = navController,
        startDestination = initialDestination
    ) {
        composable(AppRoutes.LOGIN) {
            LoginScreen(
                viewModel = authViewModel,
                onLoginSuccess = {
                    dashboardViewModel.loadAllInitialData()
                    navController.navigate(AppRoutes.DASHBOARD) {
                        popUpTo(AppRoutes.LOGIN) { inclusive = true }
                    }
                }
            )
        }

        composable(AppRoutes.DASHBOARD) {
            DashboardScreen(
                viewModel = dashboardViewModel,
                onLogout = {
                    dashboardViewModel.resetState()
                    authViewModel.logout()
                    navController.navigate(AppRoutes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
