package org.balonmano.live.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class StreamCredentials(val server: String, val key: String)

class CredentialsStore(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "youtube-credentials.enc"))
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }
    fun load(): StreamCredentials? {
        if (!file.baseFile.exists()) return null
        val objectValue = JSONObject(file.openRead().bufferedReader().use { it.readText() })
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(objectValue.getString("iv"), Base64.NO_WRAP)))
        val clear = JSONObject(String(cipher.doFinal(Base64.decode(objectValue.getString("data"), Base64.NO_WRAP)), Charsets.UTF_8))
        return StreamCredentials(clear.getString("server"), clear.getString("key"))
    }
    fun save(credentials: StreamCredentials) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val clear = JSONObject().put("server", credentials.server).put("key", credentials.key).toString().toByteArray(Charsets.UTF_8)
        val encrypted = cipher.doFinal(clear)
        val bytes = JSONObject().put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .put("data", Base64.encodeToString(encrypted, Base64.NO_WRAP)).toString().toByteArray(Charsets.UTF_8)
        var output: java.io.FileOutputStream? = null
        try { output = file.startWrite(); output.write(bytes); file.finishWrite(output) }
        catch (e: Exception) { file.failWrite(output); throw e }
    }
    fun clear() { file.delete() }
    companion object { private const val ALIAS = "balonmano.youtube.v1" }
}
