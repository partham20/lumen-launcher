package com.partha.lumen

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

/**
 * Identifies remote buttons. Most buttons have an Android key code; a few vendor
 * buttons arrive as KEYCODE_UNKNOWN, so those are identified by scan code instead.
 */
object Keys {
    private const val SCAN_BASE = 100_000

    fun id(e: KeyEvent): Int = if (e.keyCode != KeyEvent.KEYCODE_UNKNOWN) e.keyCode else SCAN_BASE + e.scanCode

    /** The remote's ⚙ Settings button, however this TV reports it. */
    val QUICK_DEFAULT = setOf(KeyEvent.KEYCODE_SETTINGS, KeyEvent.KEYCODE_NOTIFICATION)

    /** The remote's 📺 Live TV button (Linux KEY_PROGRAM, scan code 362). */
    val CLOSE_DEFAULT = setOf(KeyEvent.KEYCODE_TV, KeyEvent.KEYCODE_GUIDE, SCAN_BASE + 362)

    fun matches(e: KeyEvent, saved: Int, defaults: Set<Int>): Boolean =
        if (saved >= 0) id(e) == saved else id(e) in defaults || (SCAN_BASE + e.scanCode) in defaults

    fun name(id: Int): String =
        if (id >= SCAN_BASE) "Button ${id - SCAN_BASE}"
        else KeyEvent.keyCodeToString(id).removePrefix("KEYCODE_").replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }

    /** Buttons that can never be reassigned, so the remote always stays usable. */
    val RESERVED = setOf(
        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE,
    )
}

/**
 * "Lumen remote buttons": watches remote key presses only, never screen content.
 * Settings opens Lumen Quick Settings; Live TV goes Home and closes apps.
 */
class RemoteService : AccessibilityService() {

    private lateinit var prefs: Prefs
    private val main = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        prefs = Prefs(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onKeyEvent(event: KeyEvent): Boolean {
        // Some TVs (this Hisense included) keep only error-level logs, so log the
        // non-navigation buttons at that level to make remote mapping debuggable.
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && event.keyCode !in Keys.RESERVED) {
            Log.e(TAG, "key ${event.keyCode} ${KeyEvent.keyCodeToString(event.keyCode)} scan=${event.scanCode}")
        }

        val learning = prefs.learning
        if (learning.isNotEmpty()) return learn(event, learning)

        val action = when {
            Keys.matches(event, prefs.quickKey, Keys.QUICK_DEFAULT) -> ::openQuickSettings
            Keys.matches(event, prefs.closeKey, Keys.CLOSE_DEFAULT) -> ::goHomeAndCloseApps
            else -> return false
        }
        // Act on the first press; swallow the rest of it (repeats and release) too.
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) action()
        return true
    }

    /**
     * Assigns the next button press to [target]. Back cancels; arrows, OK and the
     * other reserved buttons pass through so the waiting dialog stays usable.
     */
    private fun learn(event: KeyEvent, target: String): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) prefs.learning = ""
            return true
        }
        if (event.keyCode in Keys.RESERVED) return false
        if (event.action != KeyEvent.ACTION_UP) return true
        val id = Keys.id(event)
        if (target == "quick") prefs.quickKey = id else prefs.closeKey = id
        prefs.learning = ""
        toast("Saved: ${Keys.name(id)}")
        return true
    }

    private fun openQuickSettings() {
        startActivity(Intent(this, QuickSettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Going Home first moves the current app to the background so it gets closed too. */
    private fun goHomeAndCloseApps() {
        performGlobalAction(GLOBAL_ACTION_HOME)
        main.postDelayed({ AppCloser.closeAll(this) }, 700)
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        private const val TAG = "LumenRemote"

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            val me = ComponentName(context, RemoteService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
