package com.nmamit.attendance.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nmamit.attendance.data.model.Resource
import com.nmamit.attendance.data.model.StudentInfo
import com.nmamit.attendance.data.repository.AttendanceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val regno: String = "",
    val passwd: String = "",
    val captchaInput: String = "",
    val captchaText: String = "",
    val captchaToken: String = "",
    val univcode: String = "049",
    val isLoading: Boolean = false,
    val isCaptchaLoading: Boolean = false,
    val isCheckingSession: Boolean = true,
    val errorMessage: String? = null,
    val isLoggedIn: Boolean = false,
    val isDemo: Boolean = false,
    val studentInfo: StudentInfo? = null
)

class AuthViewModel(
    private val repository: AttendanceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val hasSession = repository.hasSavedSession()
            _uiState.value = _uiState.value.copy(
                isLoggedIn = hasSession,
                isCheckingSession = false
            )
            if (!hasSession) {
                fetchCaptcha()
            }
            repository.sessionIdFlow.collect { token ->
                _uiState.value = _uiState.value.copy(
                    isLoggedIn = !token.isNullOrBlank()
                )
            }
        }
        viewModelScope.launch {
            repository.studentInfoFlow.collect { info ->
                _uiState.value = _uiState.value.copy(studentInfo = info)
            }
        }
        viewModelScope.launch {
            repository.isDemoFlow.collect { demo ->
                _uiState.value = _uiState.value.copy(isDemo = demo)
            }
        }
    }

    fun onRegnoChanged(v: String) {
        _uiState.value = _uiState.value.copy(regno = v.take(30), errorMessage = null)
    }

    fun onPasswordChanged(v: String) {
        _uiState.value = _uiState.value.copy(passwd = v.take(128), errorMessage = null)
    }

    fun onCaptchaChanged(v: String) {
        _uiState.value = _uiState.value.copy(captchaInput = v.take(10), errorMessage = null)
    }

    fun fetchCaptcha() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCaptchaLoading = true)
            when (val result = repository.fetchCaptcha(_uiState.value.univcode)) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value.copy(
                        captchaText = result.data.captcha,
                        captchaInput = result.data.captcha, // Matches web app autofill
                        captchaToken = result.data.captchaToken,
                        isCaptchaLoading = false
                    )
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isCaptchaLoading = false,
                        errorMessage = result.message
                    )
                }
                Resource.Loading -> {}
            }
        }
    }

    fun login() {
        val state = _uiState.value
        val cleanRegno = state.regno.trim()
        if (cleanRegno.length < 3) {
            _uiState.value = state.copy(errorMessage = "Please enter your registered mobile number or USN.")
            return
        }
        if (state.passwd.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Please enter your portal password.")
            return
        }

        val captchaVal = state.captchaInput.ifBlank { state.captchaText }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            when (
                val result = repository.login(
                    regno = cleanRegno,
                    passwd = state.passwd,
                    captcha = captchaVal,
                    captchaToken = state.captchaToken,
                    univcode = state.univcode,
                    isDemo = false
                )
            ) {
                is Resource.Success -> {
                    // Scrub plaintext password from in-memory UI state immediately after login
                    _uiState.value = _uiState.value.copy(
                        passwd = "",
                        isLoading = false,
                        isLoggedIn = true,
                        isDemo = false,
                        studentInfo = result.data.studentInfo
                    )
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                    fetchCaptcha()
                }
                Resource.Loading -> {}
            }
        }
    }

    fun loginAsDemo() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            when (
                val result = repository.login(
                    regno = "NNM24CS1234",
                    passwd = "",
                    captcha = "123456",
                    captchaToken = "",
                    univcode = "049",
                    isDemo = true
                )
            ) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value.copy(
                        passwd = "",
                        isLoading = false,
                        isLoggedIn = true,
                        isDemo = true,
                        studentInfo = result.data.studentInfo
                    )
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }
                Resource.Loading -> {}
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            _uiState.value = AuthUiState(isCheckingSession = false)
            fetchCaptcha()
        }
    }
}
