package com.dietoexpress.mobile

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import com.dietoexpress.mobile.data.PortalService

/**
 * One authenticated Coil loader shared by all training screens.
 *
 * The disk cache lives in app-private filesDir (not just memory and not the evictable cacheDir),
 * so technique images survive navigation, process death and device restarts. Coil keys entries by
 * the stable backend URL: exercise id + image slot.
 */
internal object TrainingImageLoader {
    @Volatile private var instance: ImageLoader? = null

    fun get(context: Context): ImageLoader {
        instance?.let { return it }
        return synchronized(this) {
            instance ?: ImageLoader.Builder(context.applicationContext)
                .okHttpClient(PortalService.authenticatedClient())
                .diskCache {
                    DiskCache.Builder()
                        .directory(java.io.File(context.applicationContext.filesDir, "exercise-image-cache"))
                        .maxSizeBytes(150L * 1024L * 1024L)
                        .build()
                }
                .build()
                .also { instance = it }
        }
    }
}
