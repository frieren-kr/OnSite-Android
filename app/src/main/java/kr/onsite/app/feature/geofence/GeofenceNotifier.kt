package kr.onsite.app.feature.geofence

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kr.onsite.app.MainActivity

object GeofenceNotifier {
    private const val CHANNEL_ID = "nearby_arrival"
    private const val NOTIFICATION_ID = 1001

    // 알림 채널: Android 8.0+에서는 모든 알림이 채널에 속해야 표시된다.
    // 사용자는 설정에서 채널 단위로 소리·표시 여부를 끌 수 있다.
    // 같은 ID로 여러 번 만들어도 무시되므로 앱 시작마다 호출해도 안전하다.
    fun createChannel(context: Context) {
        val channel = NotificationChannelCompat.Builder(
            CHANNEL_ID,
            NotificationManagerCompat.IMPORTANCE_HIGH, // HIGH: 화면 상단에 팝업(헤드업)으로 뜬다
        )
            .setName("근처 도착 알림")
            .setDescription("일정 장소 근처에 도착하면 알려줍니다")
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    fun showNearbyArrival(context: Context) {
        // Android 13+에서 알림 권한이 없으면 notify()가 무시되거나 예외가 난다. 먼저 확인한다.
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!GeofencePermissions.needsNotificationPermission() || granted) {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, build(context))
        }
    }

    private fun build(context: Context) = NotificationCompat.Builder(context, CHANNEL_ID)
        // 프로토타입용 시스템 아이콘. 상태바 아이콘은 단색이어야 해서 런처 아이콘은 쓰지 않는다.
        .setSmallIcon(android.R.drawable.ic_dialog_map)
        .setContentTitle("근처 도착")
        .setContentText("경복궁 근정전 근처입니다")
        .setPriority(NotificationCompat.PRIORITY_HIGH) // Android 7 이하용. 8+는 채널 중요도를 따른다
        .setContentIntent(openAppIntent(context))
        .setAutoCancel(true) // 탭하면 알림이 사라진다
        .build()

    // 알림 탭 시 실행할 동작. 알림은 시스템이 나중에 대신 실행하므로 PendingIntent로 넘긴다.
    // 시스템이 내용을 바꿀 필요가 없으니 IMMUTABLE(변경 불가)로 만든다.
    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
