package com.nmamit.attendance

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nmamit.attendance.data.local.SessionManager
import com.nmamit.attendance.data.remote.NetworkClient
import com.nmamit.attendance.data.repository.AttendanceRepository
import com.nmamit.attendance.ui.auth.AuthViewModel
import com.nmamit.attendance.ui.dashboard.DashboardViewModel
import com.nmamit.attendance.ui.navigation.AppNavGraph
import com.nmamit.attendance.ui.theme.NMAMITAttendanceTheme

class MainActivity : ComponentActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var repository: AttendanceRepository

    private val authViewModel: AuthViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AuthViewModel(repository) as T
            }
        }
    }

    private val dashboardViewModel: DashboardViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return DashboardViewModel(repository) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        // Initialize dependencies (standalone direct HTTPS portal client)
        sessionManager = SessionManager(applicationContext)
        val portalClient = NetworkClient.createPortalClient()
        repository = AttendanceRepository(
            apiService = portalClient.apiService,
            cookieJar = portalClient.cookieJar,
            sessionManager = sessionManager
        )

        setContent {
            NMAMITAttendanceTheme {
                AppNavGraph(
                    authViewModel = authViewModel,
                    dashboardViewModel = dashboardViewModel
                )
            }
        }
    }
}
