package eu.kanade.tachiyomi.data.comments

import android.annotation.SuppressLint
import android.app.Application
import android.provider.Settings
import eu.kanade.tachiyomi.data.comments.CommentsApi.Result
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.injectLazy
import java.security.MessageDigest

/**
 * Anonymous device identity (NCM-003): `sha256(ANDROID_ID)` → bearer
 * token from `POST /v1/device/register`. Token + display name persist
 * in plain preferences — the token grants posting under an anonymous
 * handle, nothing more. ANDROID_ID survives reinstall (same signing
 * key), so suspensions re-attach; a factory reset or key rotation is
 * the only identity reset.
 */
class DeviceIdentityProvider(
    private val app: Application,
) {

    private val api: CommentsApi by injectLazy()
    private val preferenceStore: PreferenceStore by injectLazy()

    private val deviceHashPref by lazy { preferenceStore.getString("comments_device_hash", "") }
    private val tokenPref by lazy { preferenceStore.getString("comments_token", "") }
    private val namePref by lazy { preferenceStore.getString("comments_display_name", "") }

    private val mutex = Mutex()

    @SuppressLint("HardwareIds")
    fun deviceHash(): String {
        val cached = deviceHashPref.get()
        if (cached.isNotEmpty()) return cached
        val androidId = Settings.Secure.getString(
            app.contentResolver,
            Settings.Secure.ANDROID_ID,
        ).orEmpty()
        return sha256Hex(androidId).also { deviceHashPref.set(it) }
    }

    fun token(): String? = tokenPref.get().takeIf { it.isNotEmpty() }
    fun displayName(): String? = namePref.get().takeIf { it.isNotEmpty() }
    fun setDisplayName(name: String) = namePref.set(name)

    /**
     * Token if present, else register (device hash → token) once.
     * null when the server is unreachable — callers degrade.
     */
    suspend fun ensureToken(): String? = mutex.withLock {
        token() ?: run {
            val res = api.register(deviceHash())
            if (res is Result.Ok) {
                tokenPref.set(res.value.token)
                res.value.token
            } else {
                null
            }
        }
    }

    /** Force re-register (e.g. token rejected with 401). */
    suspend fun refreshToken(): String? = mutex.withLock {
        tokenPref.set("")
        val res = api.register(deviceHash())
        if (res is Result.Ok) {
            tokenPref.set(res.value.token)
            res.value.token
        } else {
            null
        }
    }

    companion object {
        fun sha256Hex(input: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(input.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}
