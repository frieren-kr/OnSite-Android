package kr.onsite.app.core.auth

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kr.onsite.app.core.network.AuthApi
import kr.onsite.app.core.network.MobileApi
import kr.onsite.app.core.network.SignInRequest
import java.io.IOException

sealed interface LoginResult {
    data object Success : LoginResult
    data object InvalidCredentials : LoginResult
    data object NetworkError : LoginResult
    data object Failed : LoginResult
}

enum class SessionStatus {
    // 서버가 토큰을 인정함(200)
    Valid,

    // 토큰은 있지만 서버에 확인하지 못함(오프라인, 서버 오류). 일단 로그인 상태로 본다.
    Unverified,

    // 토큰이 없거나 서버가 거절함(401)
    LoggedOut,
}

class AuthRepository(
    private val authApi: AuthApi,
    private val mobileApi: MobileApi,
    private val tokenStore: TokenStore,
    val sessionExpired: Flow<Unit>,
) {
    // Flow: 시간이 지나며 바뀌는 값을 구독할 수 있는 스트림. 한 번 묻고 끝나는 함수와 달리
    // 로그인/로그아웃으로 값이 바뀔 때마다 구독자에게 새 값이 자동으로 전달된다.
    val isLoggedIn: Flow<Boolean> = tokenStore.hasToken

    suspend fun login(email: String, password: String): LoginResult {
        val response = try {
            authApi.signInEmail(SignInRequest(email, password))
        } catch (e: IOException) {
            return LoginResult.NetworkError
        }
        if (response.code() == 401) return LoginResult.InvalidCredentials
        if (!response.isSuccessful) return LoginResult.Failed

        // 본문의 token은 서명 없는 원본 값이고, set-auth-token 헤더는 bearer 플러그인이 서명까지 붙여 준 값이다.
        // Bearer 인증에는 헤더 값을 쓴다.
        val token = response.headers()[HEADER_AUTH_TOKEN] ?: return LoginResult.Failed
        return try {
            tokenStore.save(token)
            LoginResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LoginResult.Failed
        }
    }

    suspend fun logout() {
        try {
            authApi.signOut(JsonObject(emptyMap()))
        } catch (e: IOException) {
            // 오프라인이어도 로그아웃은 돼야 한다. 지우지 못한 서버 세션은 만료 시각이 지나면 사라진다.
        } finally {
            // 코루틴이 취소되는 중이어도 로컬 토큰 삭제는 끝까지 한다.
            withContext(NonCancellable) { tokenStore.clear() }
        }
    }

    // 앱 시작 시 자동 로그인 확인용. 프로젝트 목록 API를 "토큰이 아직 유효한지" 확인하는 데만 쓴다.
    suspend fun checkSession(): SessionStatus {
        if (tokenStore.get() == null) return SessionStatus.LoggedOut
        return try {
            val response = mobileApi.getProjects()
            when {
                response.isSuccessful -> SessionStatus.Valid
                // 토큰 삭제는 AuthInterceptor가 이미 했다.
                response.code() == 401 -> SessionStatus.LoggedOut
                else -> SessionStatus.Unverified
            }
        } catch (e: IOException) {
            SessionStatus.Unverified
        }
    }

    private companion object {
        const val HEADER_AUTH_TOKEN = "set-auth-token"
    }
}
