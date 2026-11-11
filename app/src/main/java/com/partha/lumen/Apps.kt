package com.partha.lumen

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast

/** Shared "Close apps" logic, used by the home screen button, Quick Settings and the remote button. */
object AppCloser {

    /** Package names of every app with a TV launcher entry, except Lumen. */
    fun tvPackages(context: Context): List<String> {
        val seen = LinkedHashSet<String>()
        context.packageManager
            .queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0)
            .forEach { seen += it.activityInfo.packageName }
        seen -= context.packageName
        return seen.toList()
    }

    /** Labels of the keep-running apps, e.g. "Spotify". */
    fun keepLabel(context: Context): String {
        val pm = context.packageManager
        return Prefs(context).keepRunning.mapNotNull { pkg ->
            try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { null }
        }.sorted().joinToString(", ")
    }

    /**
     * Stops the background processes of every TV app except the keep-running list,
     * then toasts how much memory was freed. Apps in the foreground are not affected,
     * so callers that want everything closed should go Home first.
     */
    fun closeAll(context: Context) {
        val app = context.applicationContext
        val am = app.getSystemService(ActivityManager::class.java)
        val before = availableBytes(am)
        val keep = Prefs(app).keepRunning
        tvPackages(app).filter { it !in keep }.forEach { am.killBackgroundProcesses(it) }

        Handler(Looper.getMainLooper()).postDelayed({
            val freedMb = ((availableBytes(am) - before) / (1024 * 1024)).coerceAtLeast(0)
            val kept = keepLabel(app).let { if (it.isEmpty()) "" else " · kept $it" }
            Toast.makeText(app, "Apps closed · $freedMb MB freed$kept", Toast.LENGTH_LONG).show()
        }, 1500)
    }

    private fun availableBytes(am: ActivityManager) =
        ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.availMem
}
