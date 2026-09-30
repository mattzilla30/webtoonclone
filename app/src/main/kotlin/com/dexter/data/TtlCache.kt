package com.dexter.data

/** A small in-memory cache whose entries expire [ttlMillis] after they were stored. */
class TtlCache<K : Any, V : Any>(
    private val ttlMillis: Long,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private class Entry<V>(val value: V, val storedAt: Long)

    private val entries = HashMap<K, Entry<V>>()

    @Synchronized
    fun get(key: K): V? {
        val entry = entries[key] ?: return null
        if (clock() - entry.storedAt >= ttlMillis) {
            entries.remove(key)
            return null
        }
        return entry.value
    }

    @Synchronized
    fun put(key: K, value: V) {
        entries[key] = Entry(value, clock())
    }

    @Synchronized
    fun remove(key: K) {
        entries.remove(key)
    }
}
