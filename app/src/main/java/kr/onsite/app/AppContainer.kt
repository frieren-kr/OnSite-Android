package kr.onsite.app

import android.content.Context
import kotlinx.serialization.json.Json
import kr.onsite.app.core.auth.AuthInterceptor
import kr.onsite.app.core.auth.AuthRepository
import kr.onsite.app.core.auth.TokenStore
import kr.onsite.app.core.network.AuthApi
import kr.onsite.app.core.network.MobileApi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create

// 앱 전체에서 하나씩만 있어야 하는 객체(저장소, 네트워크 클라이언트, Repository)를 만들고 연결하는 곳.
// DI 라이브러리 대신 여기서 직접 생성자에 넘겨 준다. OnSiteApplication이 한 번 만든다.
class AppContainer(context: Context) {

    private val tokenStore = TokenStore(context)
    private val authInterceptor = AuthInterceptor(tokenStore)

    // 서버가 필드를 추가해도 앱이 깨지지 않도록 모르는 필드는 무시한다.
    private val json = Json { ignoreUnknownKeys = true }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .apply {
            // 요청/응답 로그는 debug 빌드에서만. AuthInterceptor 뒤에 둬야 실제로 나가는 헤더가 찍힌다.
            if (BuildConfig.DEBUG) addInterceptor(loggingInterceptor())
        }
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    val authRepository = AuthRepository(
        authApi = retrofit.create<AuthApi>(),
        mobileApi = retrofit.create<MobileApi>(),
        tokenStore = tokenStore,
        sessionExpired = authInterceptor.sessionExpired,
    )

    private fun loggingInterceptor() = HttpLoggingInterceptor().apply {
        // HEADERS까지만: BODY로 하면 로그인 요청 본문의 비밀번호와 응답 본문의 token이 그대로 찍힌다.
        level = HttpLoggingInterceptor.Level.HEADERS
        // 헤더 중 토큰이 들어가는 것들은 값을 ██로 가린다. (Set-Cookie에도 같은 세션 토큰이 들어 있다)
        redactHeader("Authorization")
        redactHeader("set-auth-token")
        redactHeader("Set-Cookie")
        redactHeader("Cookie")
    }
}
