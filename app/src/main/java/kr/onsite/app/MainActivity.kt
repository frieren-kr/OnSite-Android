package kr.onsite.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import kr.onsite.app.feature.geofence.GeofenceDebugScreen
import kr.onsite.app.feature.geofence.GeofenceManager
import kr.onsite.app.ui.theme.OnSiteTheme

class MainActivity : ComponentActivity() {

    // applicationContext를 넘겨 Activity가 다시 만들어져도(화면 회전 등) 누수가 없게 한다.
    private val geofenceManager by lazy { GeofenceManager(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OnSiteTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    GeofenceDebugScreen(
                        manager = geofenceManager,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }
}
