package com.zkwokleung.backchannel

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache

class BackchannelApp : Application(), ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    /**
     * One image loader for the app, so every thumbnail fades in instead of snapping, and the
     * memory cache is sized deliberately. Thumbnails are immutable per URL, so cache headers are
     * ignored.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .crossfade(180)
            .respectCacheHeaders(false)
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.20).build() }
            .build()
}
