package com.dexter.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.security.SecureRandom

private val Context.lockPinDataStore by preferencesDataStore(name = "lock_pin")

/** Which secret the app lock asks for. Kept in the data layer so [Settings] can name it without importing UI. */
@Serializable
enum class LockMode {
    /** The platform prompt: a fingerprint, a face, or the phone's PIN, pattern, or password. */
    BiometricOrDeviceCredential,
    /** A short PIN the app stores itself. */
    AppPin,
}

/**
 * The app's own lock PIN, for [LockMode.AppPin].
 *
 * The PIN is never stored. A random 16-byte salt plus the SHA-256 hash of salt+PIN live in the
 * app's private DataStore, which only this app's sandbox can read. That stops a casual look at the
 * file from giving away the PIN, but it is not hardware-backed: a rooted phone can still read the
 * hash and brute-force a short PIN offline. For the stronger guarantee, the
 * [LockMode.BiometricOrDeviceCredential] path keeps the secret in the phone's own credential store
 * and never in the app at all.
 */
class LockPinStore(private val context: Context) {
    private val saltKey = stringPreferencesKey("pin_salt")
    private val hashKey = stringPreferencesKey("pin_hash")

    /** Whether an app PIN is set. */
    suspend fun hasPin(): Boolean {
        val prefs = context.lockPinDataStore.data.first()
        return prefs[saltKey] != null && prefs[hashKey] != null
    }

    /** Stores a new PIN, replacing any old one. The caller validates the length and digits. */
    suspend fun setPin(pin: String) {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        context.lockPinDataStore.edit { prefs ->
            prefs[saltKey] = salt.toHex()
            prefs[hashKey] = hashPin(pin, salt).toHex()
        }
    }

    /** Whether [pin] matches the stored PIN. False when none is set or the stored values are damaged. */
    suspend fun verifyPin(pin: String): Boolean {
        val prefs = context.lockPinDataStore.data.first()
        val salt = prefs[saltKey]?.fromHex() ?: return false
        val expected = prefs[hashKey]?.fromHex() ?: return false
        // Constant-time comparison, so timing does not leak how much of the hash matched.
        return MessageDigest.isEqual(hashPin(pin, salt), expected)
    }

    /** Forgets the app PIN entirely. */
    suspend fun clearPin() {
        context.lockPinDataStore.edit { prefs ->
            prefs.remove(saltKey)
            prefs.remove(hashKey)
        }
    }

    private fun hashPin(pin: String, salt: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        return digest.digest(pin.toByteArray(Charsets.UTF_8))
    }

    private companion object {
        const val SALT_BYTES = 16
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

private fun String.fromHex(): ByteArray? {
    if (length % 2 != 0) return null
    return try {
        ByteArray(length / 2) { i -> substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    } catch (_: NumberFormatException) {
        null
    }
}
