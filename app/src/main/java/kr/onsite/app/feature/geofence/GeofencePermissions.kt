package kr.onsite.app.feature.geofence

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

// 런타임 권한: Android 6.0+에서 위치·알림 같은 민감한 권한은 Manifest 선언만으로는 부족하고,
// 실행 중에 사용자에게 직접 허락을 받아야 한다. 사용자는 언제든 설정에서 철회할 수 있으므로
// 기능을 쓰기 직전마다 현재 상태를 다시 확인한다.
object GeofencePermissions {

    fun hasFineLocation(context: Context) = isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION)

    // 백그라운드 위치 권한은 Android 10(Q)에서 생겼다. 그 이전에는 FINE만 있으면 백그라운드도 허용된다.
    fun needsBackgroundPermission() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    fun hasBackgroundLocation(context: Context) =
        if (needsBackgroundPermission()) {
            isGranted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            hasFineLocation(context)
        }

    // 알림 권한은 Android 13(TIRAMISU)에서 생겼다. 그 이전에는 기본 허용.
    fun needsNotificationPermission() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun hasNotification(context: Context) =
        if (needsNotificationPermission()) {
            isGranted(context, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true
        }

    private fun isGranted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
