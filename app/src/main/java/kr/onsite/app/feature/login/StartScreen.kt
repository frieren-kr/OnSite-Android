package kr.onsite.app.feature.login

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kr.onsite.app.core.auth.AuthRepository
import kr.onsite.app.core.auth.SessionStatus

enum class StartDestination { Home, Login }

// 앱 시작 시 자동 로그인 여부를 판단한다.
class StartViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _destination = MutableStateFlow<StartDestination?>(null)
    val destination: StateFlow<StartDestination?> = _destination.asStateFlow()

    init {
        viewModelScope.launch {
            _destination.value = when (authRepository.checkSession()) {
                SessionStatus.Valid -> StartDestination.Home
                // 오프라인·서버 오류면 일단 홈으로. 나중에 Room 캐시를 붙이면 여기서 캐시를 보여 준다.
                SessionStatus.Unverified -> StartDestination.Home
                SessionStatus.LoggedOut -> StartDestination.Login
            }
        }
    }

    companion object {
        // DI 라이브러리가 없으므로, ViewModel에 생성자 인자를 넘기려면 이렇게 팩토리를 직접 만든다.
        fun factory(authRepository: AuthRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { StartViewModel(authRepository) }
        }
    }
}

@Composable
fun StartScreen(
    viewModel: StartViewModel,
    onDestination: (StartDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val destination by viewModel.destination.collectAsStateWithLifecycle()

    LaunchedEffect(destination) {
        destination?.let(onDestination)
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
