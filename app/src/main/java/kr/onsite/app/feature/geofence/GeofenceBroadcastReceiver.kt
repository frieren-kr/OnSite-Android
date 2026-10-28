package kr.onsite.app.feature.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import kr.onsite.app.BuildConfig

// BroadcastReceiver: 시스템이나 다른 앱이 보낸 "사건"을 받는 진입점.
// 화면(Activity)이 없어도, 앱 프로세스가 죽어 있어도 시스템이 앱을 깨워 onReceive를 호출한다.
// 그래서 백그라운드 지오펜스 이벤트를 받는 데 쓴다. onReceive는 몇 초 안에 끝나야 한다.
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Play services가 PendingIntent에 채워 넣은 extra를 해석한다.
        val event = GeofencingEvent.fromIntent(intent)
        if (event == null) {
            Log.w(TAG, "지오펜스 이벤트가 아닌 Intent 수신")
            return
        }

        if (event.hasError()) {
            val name = GeofenceStatusCodes.getStatusCodeString(event.errorCode)
            Log.e(TAG, "지오펜스 에러 이벤트: $name (${event.errorCode})")
            return
        }

        if (event.geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
            // 위치 로그는 디버그 빌드에서만, 이 프로토타입 한정. 실제 기능에선 지운다.
            if (BuildConfig.DEBUG) {
                val ids = event.triggeringGeofences?.map { it.requestId }
                val loc = event.triggeringLocation
                Log.d(TAG, "ENTER: $ids at (${loc?.latitude}, ${loc?.longitude})")
            } else {
                Log.i(TAG, "ENTER")
            }
            GeofenceNotifier.showNearbyArrival(context)
        } else {
            Log.i(TAG, "처리하지 않는 전환: ${event.geofenceTransition}")
        }
    }

    private companion object {
        const val TAG = GeofenceManager.TAG
    }
}
