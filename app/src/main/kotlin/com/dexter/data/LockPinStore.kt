package com.dexter.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
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
    private val failuresKey = intPreferencesKey("pin_failures")
    private val lockedUntilKey = longPreferencesKey("pin_locked_until")

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

    /**
     * Checks [pin], counting wrong tries. After [FREE_TRIES] wrong tries in a row each further one
     * locks entry for a while, doubling up to an hour, so a short PIN can't be guessed by trying them
     * all. The count is stored, so restarting the app does not reset it.
     */
    suspend fun attempt(pin: String, now: Long = System.currentTimeMillis()): PinAttempt {
        val prefs = context.lockPinDataStore.data.first()
        val lockedUntil = prefs[lockedUntilKey] ?: 0L
        if (lockedUntil > now) return PinAttempt.LockedOut(lockedUntil - now)
        if (verifyPin(pin)) {
            context.lockPinDataStore.edit {
                it.remove(failuresKey)
                it.remove(lockedUntilKey)
            }
            return PinAttempt.Passed
        }
        val failures = (prefs[failuresKey] ?: 0) + 1
        val wait = pinLockoutMs(failures)
        context.lockPinDataStore.edit {
            it[failuresKey] = failures
            if (wait > 0) it[lockedUntilKey] = now + wait
        }
        return if (wait > 0) PinAttempt.LockedOut(wait) else PinAttempt.Wrong(FREE_TRIES - failures)
    }

    /** Forgets the app PIN entirely. */
    suspend fun clearPin() {
        context.lockPinDataStore.edit { prefs ->
            prefs.remove(saltKey)
            prefs.remove(hashKey)
            prefs.remove(failuresKey)
            prefs.remove(lockedUntilKey)
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

/** What one PIN try did. */
sealed interface PinAttempt {
    data object Passed : PinAttempt

    /** Wrong, with [triesLeft] more before entry locks. */
    data class Wrong(val triesLeft: Int) : PinAttempt

    /** Entry is locked for [waitMs] more milliseconds. */
    data class LockedOut(val waitMs: Long) : PinAttempt
}

/** Wrong tries allowed before entry starts locking. */
const val FREE_TRIES = 5

/** How long entry locks after the [failures]th wrong try in a row: none at first, then 30 seconds doubling to an hour. */
fun pinLockoutMs(failures: Int): Long {
    if (failures < FREE_TRIES) return 0L
    val steps = (failures - FREE_TRIES).coerceAtMost(7)
    return (30_000L shl steps).coerceAtMost(60 * 60_000L)
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
