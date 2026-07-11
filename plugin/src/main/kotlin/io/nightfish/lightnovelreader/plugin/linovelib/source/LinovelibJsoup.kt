package io.nightfish.lightnovelreader.plugin.linovelib.source

import org.jsoup.Connection

internal fun Connection.acceptLinovelibContentTypes(): Connection =
    ignoreContentType(true)
