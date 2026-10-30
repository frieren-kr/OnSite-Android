package kr.onsite.app.core.auth

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

// DataStore: SharedPreferences를 대체하는 키-값 저장소. 파일 읽기/쓰기를 코루틴으로 해서 메인 스레드를 막지 않고,
// 값이 바뀌면 Flow로 알려 준다. 이름이 "auth"라 실제 파일은 files/datastore/auth.preferences_pb
// (백업 제외 규칙이 이 경로를 가리킨다). 한 파일에 DataStore 인스턴스는 하나만 있어야 해서 최상위 프로퍼티로 둔다.
private val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(name = "auth")

// 로그인 토큰을 Keystore 키로 암호화해 DataStore에 저장한다. 평문 토큰은 디스크에 남지 않는다.
class TokenStore(context: Context) {

    private val dataStore = context.applicationContext.authDataStore
    private val cipher = TokenCipher(KEY_ALIAS)
    private val mutex = Mutex()

    // 모든 HTTP 요청마다 디스크를 읽고 복호화하지 않도록 한 번 읽은 값을 메모리에 들고 있는다.
    @Volatile private var loaded = false
    @Volatile private var cached: String? = null

    // 토큰 "존재 여부"만 흘려보낸다(토큰 값 자체는 Flow로 퍼뜨리지 않는다).
    val hasToken: Flow<Boolean> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[TOKEN_KEY] != null }
        .distinctUntilChanged()

    suspend fun get(): String? {
        if (loaded) return cached
        return mutex.withLock {
            if (!loaded) {
                cached = readFromDisk()
                loaded = true
            }
            cached
        }
    }

    suspend fun save(token: String) {
        mutex.withLock {
            val encrypted = withContext(Dispatchers.Default) { cipher.encrypt(token) }
            dataStore.edit { it[TOKEN_KEY] = encrypted }
            cached = token
            loaded = true
        }
    }

    suspend fun clear() {
        mutex.withLock {
            cached = null
            loaded = true
            removeFromDisk()
        }
    }

    private suspend fun readFromDisk(): String? {
        val stored = try {
            dataStore.data.first()[TOKEN_KEY]
        } catch (e: IOException) {
            Log.w(TAG, "토큰 파일을 읽지 못함: ${e.javaClass.simpleName}")
            null
        } ?: return null

        return try {
            withContext(Dispatchers.Default) { cipher.decrypt(stored) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 키가 사라졌거나(앱 데이터 복원 등) 데이터가 손상된 경우. 복구할 방법이 없으니
            // 앱을 죽이지 않고 토큰을 지워 "로그아웃 상태"로 만든다. 예외 메시지 대신 종류만 남긴다.
            Log.w(TAG, "토큰 복호화 실패, 로그아웃 처리: ${e.javaClass.simpleName}")
            removeFromDisk()
            null
        }
    }

    private suspend fun removeFromDisk() {
        try {
            dataStore.edit { it.remove(TOKEN_KEY) }
        } catch (e: IOException) {
            Log.w(TAG, "토큰 파일을 지우지 못함: ${e.javaClass.simpleName}")
        }
    }

    private companion object {
        const val TAG = "TokenStore"

        // 이 앱 전용 Keystore 키 이름. 다른 용도의 키와 섞이지 않게 패키지명을 붙인다.
        const val KEY_ALIAS = "kr.onsite.app.auth_token_key"
        val TOKEN_KEY = stringPreferencesKey("auth_token")
    }
}
