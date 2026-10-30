package kr.onsite.app.core.auth

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// Android Keystore: 암호화 키를 앱 파일이 아니라 시스템 보안 영역(지원 기기는 하드웨어 칩)에 보관한다.
// 앱 코드조차 키 값을 꺼낼 수 없고 "이 키로 암호화/복호화해 줘"만 요청할 수 있다.
// 그래서 DataStore 파일이 루팅·백업 등으로 유출돼도 키가 없으면 토큰을 읽을 수 없다.
internal class TokenCipher(private val keyAlias: String) {

    // 저장 형식: Base64( IV 12바이트 + 암호문 ). AES-GCM은 복호화할 때 암호화에 쓴 IV가 필요하다.
    fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        // IV는 Keystore가 매번 무작위로 만든다(같은 토큰도 매번 다른 암호문이 된다).
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    // 키가 없거나(앱 데이터 일부만 복원됨 등) 데이터가 손상되면 예외를 던진다. 처리는 호출하는 쪽(TokenStore)에서.
    fun decrypt(stored: String): String {
        val bytes = Base64.decode(stored, Base64.NO_WRAP)
        if (bytes.size <= IV_SIZE) throw GeneralSecurityException("저장된 데이터가 너무 짧음")
        val key = loadKey() ?: throw GeneralSecurityException("Keystore 키 없음")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, bytes, 0, IV_SIZE))
        val decrypted = cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE)
        return String(decrypted, Charsets.UTF_8)
    }

    private fun loadKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return keyStore.getKey(keyAlias, null) as? SecretKey
    }

    private fun getOrCreateKey(): SecretKey {
        loadKey()?.let { return it }
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val TAG_BITS = 128
    }
}
