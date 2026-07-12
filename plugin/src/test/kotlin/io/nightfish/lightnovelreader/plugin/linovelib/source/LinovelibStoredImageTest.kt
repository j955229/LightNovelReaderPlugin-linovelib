package io.nightfish.lightnovelreader.plugin.linovelib.source

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.URI
import java.nio.file.Files

class LinovelibStoredImageTest {
    @Test
    fun `stores protected image once and reuses local file uri`() = runBlocking {
        val directory = Files.createTempDirectory("linovelib-images").toFile()
        val jpeg = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0x00)
        var downloads = 0
        val store = LinovelibImageStore(
            directory = directory,
            downloader = {
                downloads++
                DownloadedLinovelibImage(jpeg, "image/jpeg")
            },
            diagnostics = LinovelibDiagnostics { _, _, _, _ -> }
        )
        val remote = "https://img.readpai.com/cover.jpg"
        val blocks = listOf(ParsedContentBlock.Image(remote), ParsedContentBlock.Image(remote))

        val first = store.localize(blocks)
        val second = store.localize(blocks)

        val firstUri = (first.first() as ParsedContentBlock.Image).url
        assertTrue(firstUri.startsWith("file:"))
        assertEquals(firstUri, (first.last() as ParsedContentBlock.Image).url)
        assertEquals(firstUri, (second.first() as ParsedContentBlock.Image).url)
        assertArrayEquals(jpeg, File(URI(firstUri)).readBytes())
        assertEquals(1, downloads)
        directory.deleteRecursively()
        Unit
    }

    @Test
    fun `omits image when local storage fails so export does not retry remote url`() = runBlocking {
        val directory = Files.createTempDirectory("linovelib-images").toFile()
        val store = LinovelibImageStore(
            directory = directory,
            downloader = { error("blocked") },
            diagnostics = LinovelibDiagnostics { _, _, _, _ -> }
        )
        val remote = "https://img.readpai.com/illustration.webp"

        val result = store.localize(listOf(ParsedContentBlock.Image(remote)))

        assertTrue(result.isEmpty())
        directory.deleteRecursively()
        Unit
    }

    @Test
    fun `rejects html interception page returned with http 200`() = runBlocking {
        val directory = Files.createTempDirectory("linovelib-images").toFile()
        val store = LinovelibImageStore(
            directory = directory,
            downloader = {
                DownloadedLinovelibImage("<html>blocked</html>".toByteArray(), "text/html")
            },
            diagnostics = LinovelibDiagnostics { _, _, _, _ -> }
        )

        val result = store.localize(
            listOf(ParsedContentBlock.Image("https://img.readpai.com/intercept.jpg"))
        )

        assertTrue(result.isEmpty())
        assertTrue(directory.listFiles().orEmpty().isEmpty())
        directory.deleteRecursively()
        Unit
    }
}
