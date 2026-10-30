package kr.onsite.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.serialization.Serializable
import kr.onsite.app.core.auth.AuthRepository
import kr.onsite.app.feature.geofence.GeofenceDebugScreen
import kr.onsite.app.feature.geofence.GeofenceManager
import kr.onsite.app.feature.home.HomeScreen
import kr.onsite.app.feature.home.HomeViewModel
import kr.onsite.app.feature.login.LoginScreen
import kr.onsite.app.feature.login.LoginViewModel
import kr.onsite.app.feature.login.StartDestination
import kr.onsite.app.feature.login.StartScreen
import kr.onsite.app.feature.login.StartViewModel

// 화면(목적지) 이름. @Serializable 객체로 두면 문자열 경로 대신 타입으로 이동할 수 있어 오타가 컴파일 에러로 잡힌다.
@Serializable data object StartRoute
@Serializable data object LoginRoute
@Serializable data object HomeRoute
@Serializable data object GeofenceDebugRoute

// Navigation: 앱의 화면 목록과 이동 규칙을 NavHost 한 곳에 모은다. 뒤로가기 스택을 라이브러리가 관리해 줘서
// "로그인 후 뒤로가기로 로그인 화면에 돌아가지 않기" 같은 규칙을 popUpTo 옵션으로 표현할 수 있다.
@Composable
fun AppNavHost(
    authRepository: AuthRepository,
    geofenceManager: GeofenceManager,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()

    // 어느 화면에 있든 세션 만료(401) 이벤트가 오면 로그인 화면으로 보낸다.
    LaunchedEffect(navController) {
        authRepository.sessionExpired.collect { navController.navigateToLogin() }
    }

    NavHost(navController = navController, startDestination = StartRoute, modifier = modifier) {
        composable<StartRoute> {
            val viewModel: StartViewModel = viewModel(factory = StartViewModel.factory(authRepository))
            StartScreen(
                viewModel = viewModel,
                onDestination = { destination ->
                    val route = when (destination) {
                        StartDestination.Home -> HomeRoute
                        StartDestination.Login -> LoginRoute
                    }
                    // 시작 화면은 스택에서 빼서 뒤로가기로 돌아오지 않게 한다.
                    navController.navigate(route) {
                        popUpTo<StartRoute> { inclusive = true }
                    }
                },
            )
        }
        composable<LoginRoute> {
            val viewModel: LoginViewModel = viewModel(factory = LoginViewModel.factory(authRepository))
            LoginScreen(
                viewModel = viewModel,
                onLoggedIn = {
                    navController.navigate(HomeRoute) {
                        popUpTo<LoginRoute> { inclusive = true }
                    }
                },
            )
        }
        composable<HomeRoute> {
            val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(authRepository))
            HomeScreen(
                viewModel = viewModel,
                onLoggedOut = { navController.navigateToLogin() },
                onOpenGeofenceDebug = { navController.navigate(GeofenceDebugRoute) },
            )
        }
        composable<GeofenceDebugRoute> {
            GeofenceDebugScreen(manager = geofenceManager)
        }
    }
}

// 뒤로가기 스택을 전부 비우고 로그인 화면만 남긴다(로그아웃 후 뒤로가기로 홈에 돌아가지 못하게).
private fun NavController.navigateToLogin() {
    navigate(LoginRoute) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}
