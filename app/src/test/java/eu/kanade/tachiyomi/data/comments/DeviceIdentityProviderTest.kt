package eu.kanade.tachiyomi.data.comments

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DeviceIdentityProviderTest {

    @Test
    fun `device hash is sha256 hex of android id`() {
        // sha256("test-android-id") — precomputed reference digest.
        val expected = "87fece06eb296cc26800e69c4e12bb990eeb819bc518fd0069414bfca71a6277"
        assertEquals(expected, DeviceIdentityProvider.sha256Hex("test-android-id"))
    }

    @Test
    fun `empty id still hashes deterministically`() {
        val a = DeviceIdentityProvider.sha256Hex("")
        assertEquals(64, a.length)
        assertEquals(a, DeviceIdentityProvider.sha256Hex(""))
    }
}
