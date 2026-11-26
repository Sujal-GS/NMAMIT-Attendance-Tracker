package com.nmamit.attendance.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nmamit.attendance.data.remote.AppUpdateChecker
import com.nmamit.attendance.data.remote.AppUpdateInfo
import com.nmamit.attendance.ui.auth.AuthViewModel
import com.nmamit.attendance.ui.auth.LoginScreen
import com.nmamit.attendance.ui.components.AppUpdateModal
import com.nmamit.attendance.ui.components.LocalHazeState
import com.nmamit.attendance.ui.dashboard.DashboardScreen
import com.nmamit.attendance.ui.dashboard.DashboardViewModel
import com.nmamit.attendance.ui.theme.BgCanvas
import com.nmamit.attendance.ui.theme.Foreground
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

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
    var updateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var dismissedUpdate by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        updateInfo = AppUpdateChecker.checkForUpdate()
    }

    val showUpdateModal = updateInfo != null && !dismissedUpdate
    BackHandler(enabled = showUpdateModal) {
        dismissedUpdate = true
    }

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

    val updateHazeState = remember { HazeState() }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (showUpdateModal) Modifier.hazeSource(updateHazeState) else Modifier)
        ) {
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

        AnimatedVisibility(
            visible = showUpdateModal,
            enter = fadeIn(tween(200)) + scaleIn(
                initialScale = 0.92f,
                animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMedium)
            ),
            exit = fadeOut(tween(150)) + scaleOut(
                targetScale = 0.94f,
                animationSpec = tween(150)
            )
        ) {
            updateInfo?.let { info ->
                CompositionLocalProvider(LocalHazeState provides updateHazeState) {
                    AppUpdateModal(
                        updateInfo = info,
                        onDismiss = { dismissedUpdate = true }
                    )
                }
            }
        }
    }
}
