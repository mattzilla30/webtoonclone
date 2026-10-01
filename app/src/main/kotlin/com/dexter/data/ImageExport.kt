package com.dexter.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import coil3.SingletonImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.URI

/** The image type of [bytes], read from its first bytes, as a MIME type and a file extension. */
fun imageTypeOf(bytes: ByteArray): Pair<String, String> = when {
    bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() -> "image/png" to "png"
    bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "image/jpeg" to "jpg"
    bytes.size >= 12 && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> "image/webp" to "webp"
    bytes.size >= 4 && String(bytes, 0, 4, Charsets.US_ASCII) == "GIF8" -> "image/gif" to "gif"
    else -> "image/jpeg" to "jpg"
}

/** A file name with only letters, digits, dashes, and underscores, so every app and file system accepts it. */
fun safeFileName(name: String): String = name.replace(Regex("[^A-Za-z0-9_-]+"), "_").trim('_').take(80).ifEmpty { "image" }

/** Saves and shares images the app has shown: covers and pages. */
class ImageExport(private val context: Context, private val client: OkHttpClient) {
    /**
     * The bytes of the image at [url]. A saved page is read from its file. Anything else comes from the
     * image cache when it is there under [cacheKey], and from the network when it is not.
     */
    suspend fun bytes(url: String, cacheKey: String = url): ByteArray = withContext(Dispatchers.IO) {
        if (url.startsWith("file:")) return@withContext File(URI(url)).readBytes()
        val cache = SingletonImageLoader.get(context).diskCache
        if (cache != null) {
            for (key in listOf(cacheKey, url).distinct()) {
                cache.openSnapshot(key)?.use { snapshot -> return@withContext cache.fileSystem.read(snapshot.data) { readByteArray() } }
            }
        }
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Image failed: ${response.code}")
            response.body.bytes()
        }
    }

    /** Saves [bytes] to Pictures/Dexter as [name], and returns where it went. */
    suspend fun saveToGallery(bytes: ByteArray, name: String): Uri = withContext(Dispatchers.IO) {
        val (mime, extension) = imageTypeOf(bytes)
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "${safeFileName(name)}.$extension")
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Dexter")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
            ?: throw IOException("Could not save the image")
        resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: throw IOException("Could not save the image")
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        uri
    }

    /** Writes [bytes] to a private file other apps can read through a share, and returns its address and type. */
    suspend fun shareable(bytes: ByteArray, name: String): Pair<Uri, String> = withContext(Dispatchers.IO) {
        val (mime, extension) = imageTypeOf(bytes)
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        // Only the newest shared image is kept.
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "${safeFileName(name)}.$extension").apply { writeBytes(bytes) }
        FileProvider.getUriForFile(context, "${context.packageName}.files", file) to mime
    }
}
