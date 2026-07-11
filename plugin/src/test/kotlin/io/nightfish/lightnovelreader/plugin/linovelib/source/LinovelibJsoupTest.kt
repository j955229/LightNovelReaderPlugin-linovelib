package io.nightfish.lightnovelreader.plugin.linovelib.source

import com.sun.net.httpserver.HttpServer
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.InetSocketAddress

class LinovelibJsoupTest {
    @Test
    fun acceptsJavascriptSearchGuardResponse() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val body = "jieqiSearchJs=test-token".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/javascript")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()

        try {
            val response = Jsoup.connect("http://127.0.0.1:${server.address.port}/")
                .acceptLinovelibContentTypes()
                .execute()

            assertEquals("jieqiSearchJs=test-token", response.body())
        } finally {
            server.stop(0)
        }
    }
}
