package com.partha.lumen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/** Every user-editable home screen setting, stored in one SharedPreferences file. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("lumen", Context.MODE_PRIVATE)

    var name: String
        get() = sp.getString("name", DEFAULT_NAME)!!
        set(v) = sp.edit().putString("name", v).apply()

    /** "welcome", "time" (good morning/afternoon/evening) or "off". */
    var greeting: String
        get() = sp.getString("greeting", "welcome")!!
        set(v) = sp.edit().putString("greeting", v).apply()

    var showDate: Boolean
        get() = sp.getBoolean("showDate", true)
        set(v) = sp.edit().putBoolean("showDate", v).apply()

    var clock24: Boolean
        get() = sp.getBoolean("clock24", false)
        set(v) = sp.edit().putBoolean("clock24", v).apply()

    /** Tint the background with the focused app's colour, or keep it dark. */
    var dynamicTint: Boolean
        get() = sp.getBoolean("dynamicTint", true)
        set(v) = sp.edit().putBoolean("dynamicTint", v).apply()

    /** Blurred app artwork behind the top shelf. */
    var artwork: Boolean
        get() = sp.getBoolean("artwork", true)
        set(v) = sp.edit().putBoolean("artwork", v).apply()

    /** Show the "Close apps" button in the top bar. */
    var closeButton: Boolean
        get() = sp.getBoolean("closeButton", true)
        set(v) = sp.edit().putBoolean("closeButton", v).apply()

    /** Apps that "Close apps" leaves running. */
    var keepRunning: Set<String>
        get() = HashSet(sp.getStringSet("keepRunning", DEFAULT_KEEP_RUNNING)!!)
        set(v) = sp.edit().putStringSet("keepRunning", v).apply()

    var favourites: List<String>
        get() = sp.getString("favs", null)?.split(',')?.filter { it.isNotEmpty() } ?: DEFAULT_FAVS
        set(v) = sp.edit().putString("favs", v.joinToString(",")).putInt("listVersion", listVersion + 1).apply()

    var hidden: Set<String>
        get() = HashSet(sp.getStringSet("hidden", DEFAULT_HIDDEN)!!)
        set(v) = sp.edit().putStringSet("hidden", v).putInt("listVersion", listVersion + 1).apply()

    /** Bumped whenever favourites or hidden apps change, so the home screen knows to rebuild. */
    val listVersion: Int
        get() = sp.getInt("listVersion", 0)

    companion object {
        const val DEFAULT_NAME = "Parthasarathy M"
        val DEFAULT_FAVS = listOf(
            "com.netflix.ninja",
            "com.google.android.youtube.tv",
            "in.startv.hotstar",
            "com.amazon.amazonvideo.livingroom",
            "com.spotify.tv.android",
            "com.mxtech.videoplayer.television",
        )
        val DEFAULT_HIDDEN = setOf("me.efesser.flauncher", "com.vt.tv.mobiledeviceconnectiontips")
        val DEFAULT_KEEP_RUNNING = setOf("com.spotify.tv.android")
    }
}

/** The profile photo, kept as a 256px square PNG in the app's private storage. */
object Avatar {
    private const val SIZE = 256

    fun file(context: Context) = File(context.filesDir, "avatar.png")

    fun load(context: Context): Bitmap? = file(context).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }

    fun remove(context: Context) {
        file(context).delete()
    }

    /** Decodes [source], centre-crops it square and saves it. Returns false if it isn't a readable image. */
    fun import(context: Context, source: File): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= SIZE) sample *= 2
        val full = BitmapFactory.decodeFile(source.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return false

        val side = minOf(full.width, full.height)
        val square = Bitmap.createBitmap(full, (full.width - side) / 2, (full.height - side) / 2, side, side)
        val scaled = Bitmap.createScaledBitmap(square, SIZE, SIZE, true)
        file(context).outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return true
    }
}
