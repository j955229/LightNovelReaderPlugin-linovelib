package io.nightfish.lightnovelreader.plugin.linovelib

import android.util.Log
import io.nightfish.lightnovelreader.api.plugin.LightNovelReaderPlugin
import io.nightfish.lightnovelreader.api.plugin.Plugin

@Suppress("unused")
@Plugin(
    version = BuildConfig.VERSION_CODE,
    name = "Linovelib TW",
    versionName = BuildConfig.VERSION_NAME,
    author = "LightNovelReader contributor",
    description = "tw.linovelib.com web data source",
    updateUrl = "",
    apiVersion = 2
)
class LinovelibPlugin : LightNovelReaderPlugin {
    override fun onLoad() {
        Log.i("LinovelibPlugin", "Linovelib TW plugin loaded")
    }
}
