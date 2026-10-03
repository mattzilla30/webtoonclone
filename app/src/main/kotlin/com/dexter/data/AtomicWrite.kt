package com.dexter.data

import java.io.File
import java.io.OutputStream

/**
 * Writes this file through a sibling `.part` file and renames it into place, so an interrupted
 * copy never leaves a truncated file that later reads would treat as complete.
 */
fun File.writeAtomically(write: (OutputStream) -> Unit) {
    val partial = File(parentFile, "$name.part")
    try {
        partial.outputStream().use(write)
        delete()
        if (!partial.renameTo(this)) throw java.io.IOException("Could not save $name")
    } finally {
        partial.delete()
    }
}
