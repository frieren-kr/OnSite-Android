# OnSite Android — 프로젝트 지침

이 파일은 Claude Code가 이 저장소에서 작업할 때 항상 따르는 규칙이다. 아래 결정은 이미 확정된 것이므로 다시 제안하지 않는다. 바꾸고 싶으면 사용자에게 트레이드오프를 먼저 설명하고 동의를 받는다.

## 1. 프로젝트 개요

- 기존 웹 서비스 OnSite(Next.js, 저장소 `frieren-kr/On-Site`)의 **참여자 기능만** 안드로이드 네이티브 앱으로 옮기는 과제. 1인 개발, 구현 마감 12/8, 데모·발표 12/15.
- 앱으로 만드는 이유는 딱 두 가지: **지오펜싱 기반 근처 도착 자동 알림**, **오프라인 보기**. 이 둘이 빠지면 과제의 존재 이유가 사라진다. 다른 기능보다 항상 우선한다.
- 주최자 편집 기능(프로젝트 생성, 장소·일정 작성, 초대)은 웹에 남긴다. 앱은 **조회 전용**이다. 앱에서 데이터를 쓰는 API는 만들지 않는다.
- 서버·DB는 기존 것을 재사용한다. 앱 전용 조회 API만 웹 저장소에 추가한다. 이 저장소에서 웹 저장소 코드를 수정하지 않는다 (서버 쪽 작업은 사용자에게 "웹 저장소에서 할 일"로 분리해 알려준다).

## 2. 기술 스택 (고정)

| 영역 | 선택 | 메모 |
|---|---|---|
| 언어·빌드 | Kotlin, Gradle Kotlin DSL, Version Catalog(`libs.versions.toml`) | 의존성은 반드시 카탈로그에 추가 |
| 최소 SDK | API 26 (Android 8.0) | |
| UI | Jetpack Compose + Material 3 | XML 레이아웃 쓰지 않음 |
| 화면 이동 | `androidx.navigation:navigation-compose` (안정 버전) | Navigation 3 같은 알파/베타 사용 금지 |
| 구조 | MVVM: Screen(Composable) → ViewModel → Repository → (Retrofit API / Room DAO) | |
| 비동기 | Coroutines + Flow | RxJava 금지 |
| DI | **라이브러리 없음.** `AppContainer` 수동 주입 | Hilt/Koin 제안 금지 — 학습 부담 대비 이득 없음 |
| 네트워크 | Retrofit + OkHttp + kotlinx.serialization | Gson/Moshi 금지 |
| 로컬 저장 | Room(오프라인 캐시), DataStore(설정·토큰) | 토큰은 Android Keystore로 암호화해 DataStore에 저장. `EncryptedSharedPreferences`는 deprecated라 쓰지 않음 |
| 지도 | 네이버 지도 Android SDK | 웹과 동일 공급자. 카카오/구글 지도 제안 금지 |
| 위치 | Google Play services `FusedLocationProviderClient` + `GeofencingClient` | 에뮬레이터는 Google Play 이미지 필수 |
| 마크다운 | Markwon | 장소 해설 렌더링 |
| 이미지 | Coil | R2 공개 URL 로딩 |

## 3. 패키지 구조 (단일 모듈 `app`)

```
kr.onsite.app
├── core
│   ├── network     Retrofit 인스턴스, AuthInterceptor(Bearer 헤더), DTO
│   ├── data        Room DB, DAO, Entity, Repository 구현
│   ├── auth        TokenStore(Keystore+DataStore), 세션 상태
│   └── ui          테마, 공용 컴포저블
├── feature
│   ├── login
│   ├── projects    프로젝트 목록
│   ├── schedule    일정·장소 목록
│   ├── place       장소 해설
│   ├── map         동선 지도
│   └── geofence    지오펜스 등록, BroadcastReceiver, 알림
├── AppContainer.kt
└── MainActivity.kt
```

기능별(feature-first)로 나눈다. 레이어별(`viewmodels/`, `repositories/`) 폴더 구조로 바꾸지 않는다.

## 4. 서버 연동 규칙

- 인증: 웹 저장소 Better Auth에 `bearer()` 플러그인을 추가한다. 로그인 응답 헤더 `set-auth-token`의 값을 토큰으로 저장하고, 이후 모든 요청에 `Authorization: Bearer <token>`을 붙인다.
- 앱 전용 API는 웹 저장소의 `/api/mobile/*` 아래에 둔다. 필요한 엔드포인트는 3개로 시작: 프로젝트 목록, 프로젝트 상세(일정·장소·동선), 장소 해설.
- **서버의 권한 검증(`ProjectMember` 확인)은 그대로 유지한다.** 앱 편의를 위해 서버 검증을 느슨하게 하자는 제안은 하지 않는다.
- 401 응답이 오면 토큰을 지우고 로그인 화면으로 보낸다.
- 개발 중 서버 주소는 `BuildConfig`로 주입한다(debug: 로컬 또는 Vercel preview, release: 운영). 코드에 URL 하드코딩 금지.

## 5. 보안 규칙

- 토큰을 로그에 찍지 않는다. `Log.d`에 헤더·토큰·사용자 정보가 들어가면 안 된다.
- `AndroidManifest`에 `usesCleartextTraffic="true"` 넣지 않는다. 로컬 서버 테스트가 필요하면 debug 전용 `network_security_config`로 `10.0.2.2`만 허용한다.
- 네이버 지도 Client ID 등 키는 `local.properties`에 두고 Gradle로 읽어 `manifestPlaceholders`/`BuildConfig`에 넣는다. `local.properties`는 커밋하지 않는다(기본 `.gitignore`에 포함됨).
- 서명 키(`*.jks`)는 저장소 밖에 둔다.
- 테스트는 dev DB의 test-로 시작하는 계정(test-a@onsite.dev, test-b@onsite.dev)으로만 한다. 실제 사용자 계정으로 로그인·비밀번호 변경·세션 삭제를 하지 않는다. test 계정이 없으면 멈추고 사용자에게 알린다.
- 테스트 중에 코드를 고쳐야 하면 고치기 전에 무엇을 왜 고칠지 먼저 보고한다.

## 6. 위치·지오펜싱 규칙

- 권한은 두 단계로 요청한다: 먼저 `ACCESS_FINE_LOCATION`, 사용자가 동의한 뒤에 별도 화면에서 이유를 설명하고 `ACCESS_BACKGROUND_LOCATION`. Android 11+는 두 번째를 한 번에 요청하면 시스템이 거부하므로 순서를 바꾸지 않는다.
- Android 13+는 `POST_NOTIFICATIONS` 런타임 권한도 필요하다.
- 지오펜스는 "오늘 일정의 장소"만 등록한다(최대 100개 제한이 있지만 실제로는 당일 장소 10~20개). 날짜가 바뀌면 재등록.
- 알림 탭 → 해당 장소 해설 화면으로 딥링크.
- 지오펜스 동작 검증은 실기기 확보 전까지는 에뮬레이터(Google Play 이미지, API 34+)로 검증하고, 실기기 확보 후 재검증한다. 실기기는 5주차 현장 테스트 전 필수.

## 7. 오프라인 규칙

- 프로젝트 상세를 열 때 Room에 저장하고, 화면은 항상 Room을 읽는다(Single Source of Truth). 네트워크 성공 시 Room을 갱신한다.
- 네트워크 실패 시 캐시가 있으면 캐시를 보여주고 "마지막 동기화 시각"을 표시한다. 캐시도 없으면 에러 화면.
- 해설 사진은 Coil 디스크 캐시에 맡긴다. 별도 이미지 다운로드 기능은 만들지 않는다.

## 8. 하지 않기로 한 것 (재제안 금지)

- 주최자 편집 기능 (프로젝트/장소/일정 생성·수정·초대)
- 앱 내 사진 촬영·업로드
- 멀티 모듈, Hilt/Koin, Navigation 3, Compose Multiplatform, iOS
- 푸시 알림 서버(FCM) — 근처 알림은 로컬 알림으로 충분
- 소셜 로그인 — 이메일·비밀번호만
- 국가유산청 API 연동, 360 파노라마

## 9. 작업 방식

- 한 번에 한 기능. 요청한 범위를 넘어서 "겸사겸사" 리팩터링하지 않는다.
- 새 개념·라이브러리를 쓸 때는 **왜 쓰는지**를 코드 위 주석이나 설명으로 짚는다. 사용자는 Kotlin·Compose·Android가 처음이다. 복붙용 코드 덩어리만 주지 않는다.
- 코드 작성 후 반드시 `./gradlew assembleDebug`(Windows: `gradlew.bat assembleDebug`)로 빌드가 통과하는지 확인한다.
- `git commit`, `git push`는 하지 않는다. 커밋 단위와 메시지만 제안하면 사용자가 직접 한다.
- 커밋 메시지는 한글, 한 줄, "무엇을 했는지" 기준. 예: `지오펜스 등록과 알림 BroadcastReceiver 추가`
- 매주 일요일 `git tag week-N`으로 주차 경계를 남긴다(주차 보고서용). 보고서 작성 자체는 사용자가 한다 — 제안하지 않는다.

## 10. 로드맵 (10/27 시작, 6주)

| 주 | 기간 | 목표 | 완료 기준 |
|---|---|---|---|
| 1 | 10/27~11/2 | 환경 구축, Compose Hello World, 지오펜스 1개 알림, (웹) bearer 플러그인 + 프로젝트 목록 API | 에뮬레이터에서 좌표 이동 시 근처 도착 알림 1회 |
| 2 | ~11/9 | 로그인·토큰 저장·자동 로그인, Retrofit 프로젝트 목록 화면 | 앱 재시작 후 로그인 유지 |
| 3 | ~11/16 | 일정·장소 화면, 백그라운드 위치 권한 2단계 흐름, Room 스키마 | 권한 흐름 정상, 목록 캐시 저장 |
| 4 | ~11/23 | 오프라인 보기 완성, 네이버 지도 + 동선 | 비행기 모드에서 일정 열림 |
| 5 | ~11/30 | 장소 해설(마크다운·사진), 당일 지오펜스 자동 등록 → 알림 → 해설 딥링크 | 현장 테스트 1회 |
| 6 | ~12/8 | 권한 거부·통신 끊김 예외 처리, 버그 수정 | 구현 마감 |
| — | ~12/15 | 시연 영상, 발표 자료 | 데모 |

현재 주차에 맞는 작업만 진행한다. 다음 주차 기능을 미리 당겨 하자고 제안하지 않는다.
