package kr.onsite.app.core.network

import retrofit2.Response
import retrofit2.http.GET

// 웹 저장소의 앱 전용 조회 API(/api/mobile/*).
interface MobileApi {

    // 지금은 자동 로그인 때 토큰 유효성 확인용으로만 쓴다(상태 코드만 확인).
    // 프로젝트 목록 화면을 만들 때 응답 DTO로 바꾼다.
    @GET("api/mobile/projects")
    suspend fun getProjects(): Response<Unit>
}
