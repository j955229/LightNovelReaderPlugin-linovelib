package io.nightfish.lightnovelreader.plugin.linovelib

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import io.nightfish.lightnovelreader.plugin.linovelib.source.LinovelibImageProxy
import io.nightfish.lightnovelreader.plugin.linovelib.source.LinovelibUrls
import java.io.File
import java.io.FileNotFoundException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

class LinovelibImageProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = "image/*"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Linovelib images are read-only")
        val originalUrl = runCatching { LinovelibImageProxy.decodeUriString(uri.toString()) }
            .getOrElse { throw FileNotFoundException("Invalid Linovelib image URI") }
        if (!LinovelibImageProxy.isAllowed(originalUrl)) {
            throw FileNotFoundException("Unsupported Linovelib image host")
        }

        val cacheDirectory = context?.cacheDir?.resolve("linovelib_images")
            ?: throw FileNotFoundException("Image cache is unavailable")
        cacheDirectory.mkdirs()
        val imageFile = cacheDirectory.resolve(cacheKey(originalUrl))
        val lock = downloadLocks.computeIfAbsent(imageFile.name) { Any() }
        try {
            synchronized(lock) {
                if (!imageFile.exists() || imageFile.length() == 0L) {
                    downloadImage(originalUrl, imageFile)
                    pruneCache(cacheDirectory)
                }
            }
        } finally {
            downloadLocks.remove(imageFile.name, lock)
        }
        return ParcelFileDescriptor.open(imageFile, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private fun downloadImage(originalUrl: String, target: File) {
        val temporary = File.createTempFile("download-", ".tmp", target.parentFile)
        val connection = URL(originalUrl).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 20_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("User-Agent", IMAGE_USER_AGENT)
            connection.setRequestProperty("Referer", "${LinovelibUrls.CONTENT_HOST}/")
            connection.setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
            val status = connection.responseCode
            if (status !in 200..299) throw FileNotFoundException("Image server returned HTTP $status")
            connection.inputStream.buffered().use { input ->
                temporary.outputStream().buffered().use(input::copyTo)
            }
            if (temporary.length() == 0L) throw FileNotFoundException("Image response was empty")
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
        } catch (throwable: Throwable) {
            temporary.delete()
            throw FileNotFoundException(throwable.message ?: "Failed to download image").apply {
                initCause(throwable)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun pruneCache(directory: File) {
        directory.listFiles()
            ?.filter(File::isFile)
            ?.sortedByDescending(File::lastModified)
            ?.drop(MAX_CACHE_FILES)
            ?.forEach(File::delete)
    }

    private fun cacheKey(url: String): String = MessageDigest.getInstance("SHA-256")
        .digest(url.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    private companion object {
        const val IMAGE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Mobile Safari/537.36"
        const val MAX_CACHE_FILES = 256
        val downloadLocks = ConcurrentHashMap<String, Any>()
    }
}
