package kr.onsite.app.core.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

// Retrofit은 이 인터페이스를 보고 실제 HTTP 호출 코드를 만들어 준다.
// Response<Unit>: 본문은 읽지 않고 상태 코드와 헤더만 쓴다(로그인 응답 본문의 사용자 정보는 지금 필요 없다).
interface AuthApi {

    @POST("api/auth/sign-in/email")
    suspend fun signInEmail(@Body body: SignInRequest): Response<Unit>

    // 서버 세션을 삭제한다. 본문은 빈 JSON {}.
    @POST("api/auth/sign-out")
    suspend fun signOut(@Body body: JsonObject): Response<Unit>
}

@Serializable
data class SignInRequest(val email: String, val password: String) {
    // data class의 기본 toString은 모든 필드를 출력한다. 실수로 로그에 찍혀도 비밀번호가 새지 않게 가린다.
    override fun toString(): String = "SignInRequest(***)"
}
