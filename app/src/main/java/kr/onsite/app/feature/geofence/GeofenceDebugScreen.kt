package kr.onsite.app.feature.geofence

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect

// 1주차 지오펜스 검증용 임시 화면. 실제 화면이 생기면 지운다.
@Composable
fun GeofenceDebugScreen(manager: GeofenceManager, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    var fine by remember { mutableStateOf(GeofencePermissions.hasFineLocation(context)) }
    var background by remember { mutableStateOf(GeofencePermissions.hasBackgroundLocation(context)) }
    var notification by remember { mutableStateOf(GeofencePermissions.hasNotification(context)) }
    var result by remember { mutableStateOf("아직 등록하지 않음") }

    fun refresh() {
        fine = GeofencePermissions.hasFineLocation(context)
        background = GeofencePermissions.hasBackgroundLocation(context)
        notification = GeofencePermissions.hasNotification(context)
    }

    // 설정 화면에 갔다가 돌아오면(onResume) 권한 상태를 다시 읽는다.
    LifecycleResumeEffect(Unit) {
        refresh()
        onPauseOrDispose { }
    }

    // 권한 요청 런처: 시스템 권한 다이얼로그를 띄우고 결과를 콜백으로 받는다.
    // 결과 값 대신 refresh()로 실제 상태를 다시 읽는다(사용자가 "대략적 위치"만 고를 수도 있으므로).
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { refresh() }
    val backgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refresh() }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refresh() }

    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("지오펜스 디버그", style = MaterialTheme.typography.titleLarge)

        Text("정밀 위치: ${mark(fine)}")
        Text("백그라운드 위치: ${mark(background)}")
        Text("알림: ${mark(notification)}")

        HorizontalDivider()

        // 1단계: 전경 위치. FINE과 COARSE를 함께 요청해야 Android 12+에서 "정밀/대략" 선택지가 뜬다.
        Button(
            onClick = {
                locationLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("위치 권한 요청") }

        // 2단계: 백그라운드 위치. 반드시 1단계 허용 후에 따로 요청한다(동시 요청 시 Android 11+가 거부).
        if (GeofencePermissions.needsBackgroundPermission()) {
            Button(
                onClick = { backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) },
                enabled = fine && !background,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("백그라운드 위치 권한") }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Text(
                    "Android 11 이상은 설정 화면으로 이동합니다. 위치 권한에서 '항상 허용'을 고른 뒤 돌아오세요.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        } else {
            Text("이 기기(Android 9 이하)는 백그라운드 권한이 따로 없습니다.", style = MaterialTheme.typography.bodySmall)
        }

        if (GeofencePermissions.needsNotificationPermission()) {
            Button(
                onClick = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                enabled = !notification,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("알림 권한") }
        }

        HorizontalDivider()

        Button(
            onClick = {
                result = "등록 중…"
                manager.register { result = it }
            },
            enabled = fine && background && notification,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("지오펜스 등록") }

        OutlinedButton(
            onClick = { manager.unregister { result = it } },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("지오펜스 해제") }

        Text("결과: $result")
    }
}

private fun mark(granted: Boolean) = if (granted) "허용" else "거부"
