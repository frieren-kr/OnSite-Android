package kr.onsite.app.feature.login

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.onsite.app.core.auth.AuthRepository
import kr.onsite.app.core.auth.LoginResult

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isLoggedIn: Boolean = false,
) {
    val canSubmit: Boolean get() = email.isNotBlank() && password.isNotEmpty() && !isLoading
}

class LoginViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    // 이메일에는 공백·줄바꿈이 들어갈 수 없다. TextField의 singleLine은 Enter 키만 막고 붙여넣기한 줄바꿈은 막지 못해서,
    // 클립보드에 딸려 온 여러 줄 텍스트가 화면에 안 보인 채 서버로 가는 일이 생겼다. 입력 시점에 아예 걸러낸다.
    fun onEmailChange(email: String) =
        _uiState.update { it.copy(email = email.filterNot(Char::isWhitespace), errorMessage = null) }

    fun onPasswordChange(password: String) = _uiState.update { it.copy(password = password, errorMessage = null) }

    fun login() {
        val state = _uiState.value
        if (!state.canSubmit) return
        // 형식이 틀린 이메일은 서버가 400으로 거절하는데, 그러면 "잠시 후 다시 시도"라는 엉뚱한 안내가 나간다.
        // 보내기 전에 안드로이드 기본 제공 이메일 패턴으로 먼저 걸러서 정확한 이유를 보여준다.
        if (!Patterns.EMAIL_ADDRESS.matcher(state.email).matches()) {
            _uiState.update { it.copy(errorMessage = "이메일 형식이 올바르지 않아요") }
            return
        }
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            val result = authRepository.login(state.email.trim(), state.password)
            _uiState.update {
                when (result) {
                    LoginResult.Success -> it.copy(isLoading = false, password = "", isLoggedIn = true)
                    LoginResult.InvalidCredentials ->
                        it.copy(isLoading = false, errorMessage = "이메일 또는 비밀번호가 맞지 않아요")
                    LoginResult.NetworkError ->
                        it.copy(isLoading = false, errorMessage = "서버에 연결할 수 없어요")
                    LoginResult.Failed ->
                        it.copy(isLoading = false, errorMessage = "로그인하지 못했어요. 잠시 후 다시 시도해 주세요")
                }
            }
        }
    }

    companion object {
        fun factory(authRepository: AuthRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { LoginViewModel(authRepository) }
        }
    }
}
