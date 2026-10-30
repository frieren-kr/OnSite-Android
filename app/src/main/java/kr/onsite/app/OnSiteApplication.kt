package kr.onsite.app

import android.app.Application
import kr.onsite.app.feature.geofence.GeofenceNotifier

// Application은 앱 프로세스가 시작될 때 Activity보다 먼저 한 번 만들어진다.
// 알림 채널처럼 "앱 시작 시 1회" 해야 하는 초기화를 여기서 한다.
class OnSiteApplication : Application() {

    // 앱이 살아 있는 동안 하나만 존재하는 의존성 묶음. Activity가 다시 만들어져도 그대로 유지된다.
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        GeofenceNotifier.createChannel(this)
    }
}
