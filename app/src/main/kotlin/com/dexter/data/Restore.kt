package com.dexter.data

/**
 * Puts removed series back. Series added after the removal stay on top, so newly read
 * series keep their place, and the restored ones follow in their old order.
 */
fun mergeRestore(current: List<SavedSeries>, snapshot: List<SavedSeries>): List<SavedSeries> {
    val restoredIds = snapshot.mapTo(mutableSetOf()) { it.id }
    return current.filter { it.id !in restoredIds } + snapshot
}
