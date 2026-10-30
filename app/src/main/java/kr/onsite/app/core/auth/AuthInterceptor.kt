package kr.onsite.app.core.auth

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

// Interceptor: OkHttp가 보내는 모든 요청/응답이 거쳐 가는 "검문소".
// API 함수마다 토큰 헤더를 붙이고 401을 검사하는 코드를 반복하지 않고, 여기 한 곳에서 처리한다.
class AuthInterceptor(private val tokenStore: TokenStore) : Interceptor {

    // 세션 만료(401) 알림. 화면 쪽(AppNavHost)이 구독하다가 로그인 화면으로 보낸다.
    // 값을 저장해 두는 상태가 아니라 "지금 듣고 있는 쪽"에게 한 번 전달되는 이벤트라 SharedFlow를 쓴다.
    private val _sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val sessionExpired: SharedFlow<Unit> = _sessionExpired.asSharedFlow()

    override fun intercept(chain: Interceptor.Chain): Response {
        // OkHttp는 자체 백그라운드 스레드에서 이 함수를 부르므로 runBlocking으로 기다려도 화면이 멈추지 않는다.
        // 첫 호출 이후로는 TokenStore의 메모리 캐시라 바로 반환된다.
        val token = runBlocking { tokenStore.get() }
        val request = if (token != null) {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }

        val response = chain.proceed(request)

        // 토큰을 붙여 보냈는데 401 → 서버에서 세션이 만료·삭제됨(비밀번호 변경, 만료 등).
        // 토큰 없이 받은 401(예: 로그인 비밀번호 틀림)은 세션 만료가 아니므로 무시한다.
        if (response.code == 401 && token != null) {
            runBlocking { tokenStore.clear() }
            _sessionExpired.tryEmit(Unit)
        }
        return response
    }
}
