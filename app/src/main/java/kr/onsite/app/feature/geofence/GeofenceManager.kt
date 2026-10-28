package kr.onsite.app.feature.geofence

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import kr.onsite.app.BuildConfig

// 지오펜스: "이 원 안에 들어오면 알려줘"를 Play services에 맡기는 기능.
// 앱이 직접 위치를 계속 추적하지 않아도 되므로 배터리를 아낀다.
class GeofenceManager(private val context: Context) {

    private val client = LocationServices.getGeofencingClient(context)

    // PendingIntent: "나중에 다른 프로세스(여기선 Play services)가 우리 앱 대신 실행할 Intent".
    // 앱이 꺼져 있어도 진입 이벤트가 오면 Play services가 이걸로 GeofenceBroadcastReceiver를 깨운다.
    //
    // 왜 FLAG_MUTABLE인가: Play services가 이 Intent에 "어느 지오펜스에, 어떤 전환(ENTER 등)이,
    // 어느 위치에서 일어났는지"를 extra로 채워 넣어서 보낸다. IMMUTABLE이면 채워 넣을 수 없어
    // 리시버가 빈 Intent를 받게 된다. Android 12+는 MUTABLE/IMMUTABLE 중 하나를 반드시 명시해야 한다.
    // 대상을 GeofenceBroadcastReceiver로 명시(explicit Intent)했으므로 다른 앱이 가로챌 수 없다.
    private val pendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        val mutableFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0 // Android 11 이하는 기본이 mutable이라 플래그가 필요 없다
        }
        PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag,
        )
    }

    // 권한 확인은 호출 전에 화면에서 하지만, 여기서도 한 번 더 막아 둔다.
    @SuppressLint("MissingPermission")
    fun register(onResult: (String) -> Unit) {
        if (!GeofencePermissions.hasFineLocation(context) ||
            !GeofencePermissions.hasBackgroundLocation(context)
        ) {
            onResult("실패: 위치 권한(정밀 + 백그라운드)이 없습니다")
            return
        }

        val geofence = Geofence.Builder()
            .setRequestId(TEST_REQUEST_ID)
            .setCircularRegion(TEST_LAT, TEST_LNG, TEST_RADIUS_M)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
            .build()

        val request = GeofencingRequest.Builder()
            // 등록 순간 이미 원 안에 있으면 바로 ENTER 이벤트를 보낸다.
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        // addGeofences는 결과를 Task로 비동기로 돌려준다. 성공/실패 리스너는 메인 스레드에서 불린다.
        client.addGeofences(request, pendingIntent)
            .addOnSuccessListener {
                // 좌표 로그는 디버그 빌드에서만, 이 프로토타입 한정. 실제 기능에선 지운다.
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "등록 성공: $TEST_REQUEST_ID ($TEST_LAT, $TEST_LNG) r=${TEST_RADIUS_M}m")
                } else {
                    Log.i(TAG, "등록 성공")
                }
                onResult("등록 성공: 경복궁 근정전 반경 ${TEST_RADIUS_M.toInt()}m")
            }
            .addOnFailureListener { e ->
                val message = describeError(e)
                Log.w(TAG, "등록 실패: $message")
                onResult("등록 실패: $message")
            }
    }

    fun unregister(onResult: (String) -> Unit) {
        client.removeGeofences(listOf(TEST_REQUEST_ID))
            .addOnSuccessListener {
                Log.i(TAG, "해제 성공")
                onResult("해제 성공")
            }
            .addOnFailureListener { e ->
                val message = describeError(e)
                Log.w(TAG, "해제 실패: $message")
                onResult("해제 실패: $message")
            }
    }

    // ApiException이면 숫자 코드 대신 GEOFENCE_NOT_AVAILABLE 같은 이름으로 바꿔 보여준다.
    private fun describeError(e: Exception): String =
        if (e is ApiException) {
            "${GeofenceStatusCodes.getStatusCodeString(e.statusCode)} (${e.statusCode})"
        } else {
            e.toString()
        }

    companion object {
        const val TAG = "Geofence"

        // 1주차 프로토타입용 고정 좌표: 경복궁 근정전
        private const val TEST_REQUEST_ID = "test-geunjeongjeon"
        private const val TEST_LAT = 37.5786
        private const val TEST_LNG = 126.9770
        private const val TEST_RADIUS_M = 200f
    }
}
