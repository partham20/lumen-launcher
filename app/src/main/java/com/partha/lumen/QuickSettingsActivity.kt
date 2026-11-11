package com.partha.lumen

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock

/**
 * A panel that slides in from the right over whatever is on screen, opened by
 * the remote's Settings button. Back closes it.
 */
class QuickSettingsActivity : Activity() {

    private lateinit var panel: View
    private lateinit var audio: AudioManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audio = getSystemService(AudioManager::class.java)
        setContentView(buildUi())
        panel.translationX = dp(420f)
        panel.animate().translationX(0f).setDuration(220).setInterpolator(DecelerateInterpolator()).start()
    }

    private fun buildUi(): View {
        val root = FrameLayout(this).apply { setBackgroundColor(0x66000000) }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(28), dpi(32), dpi(28), dpi(24))
        }
        panel = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            background = GradientDrawable().apply {
                setColor(0xF21A1B20.toInt())
                cornerRadii = floatArrayOf(dp(22f), dp(22f), 0f, 0f, 0f, 0f, dp(22f), dp(22f))
            }
            addView(column)
        }
        root.addView(panel, FrameLayout.LayoutParams(dpi(380), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END))

        // Header: time and date, then who's signed in.
        column.addView(TextClock(this).apply {
            format12Hour = "h:mm"
            format24Hour = "HH:mm"
            if (Prefs(context).clock24) format12Hour = "HH:mm"
            textSize = 34f
            setTextColor(0xFFFFFFFF.toInt())
            typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
        })
        column.addView(TextClock(this).apply {
            format12Hour = "EEEE, d MMMM"
            format24Hour = "EEEE, d MMMM"
            textSize = 14f
            setTextColor(0xB3FFFFFF.toInt())
        })
        column.addView(label(13f, "sans-serif", 0.5f).apply {
            text = Prefs(context).name
            setPadding(0, dpi(4), 0, dpi(18))
        })

        val keep = AppCloser.keepLabel(this)
        val close = TvRow(this, "Close apps", if (keep.isEmpty()) "" else "Keeps $keep")
        close.setOnClickListener {
            close.setValue("Closing…")
            AppCloser.closeAll(this)
            close.postDelayed({ close.setValue("Done") }, 1500)
        }
        add(column, close)

        val volume = TvRow(this, "Volume", volumeText())
        volume.setOnKeyListener { _, code, ev ->
            if (ev.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            val direction = when (code) {
                KeyEvent.KEYCODE_DPAD_LEFT -> AudioManager.ADJUST_LOWER
                KeyEvent.KEYCODE_DPAD_RIGHT -> AudioManager.ADJUST_RAISE
                else -> return@setOnKeyListener false
            }
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
            volume.setValue(volumeText())
            true
        }
        volume.setOnClickListener {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
            volume.setValue(volumeText())
        }
        add(column, volume)

        section(column, "Settings")
        add(column, TvRow(this, "Wi-Fi & network", "").also { it.setOnClickListener { open(Settings.ACTION_WIFI_SETTINGS) } })
        add(column, TvRow(this, "Bluetooth & remotes", "").also { it.setOnClickListener { open(Settings.ACTION_BLUETOOTH_SETTINGS) } })
        add(column, TvRow(this, "Picture & display", "").also { it.setOnClickListener { open(Settings.ACTION_DISPLAY_SETTINGS) } })
        add(column, TvRow(this, "Sound", "").also { it.setOnClickListener { open(Settings.ACTION_SOUND_SETTINGS) } })
        add(column, TvRow(this, "Lumen settings", "").also {
            it.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)); finish() }
        })
        add(column, TvRow(this, "All settings", "").also { it.setOnClickListener { open(Settings.ACTION_SETTINGS) } })

        close.post { close.requestFocus() }
        return root
    }

    private fun volumeText(): String {
        if (audio.isStreamMute(AudioManager.STREAM_MUSIC)) return "Muted  ‹ ›"
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val now = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        return "${now * 100 / max}%  ‹ ›"
    }

    /** Opens a system settings screen, falling back to the main Settings app. */
    private fun open(action: String) {
        val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        finish()
    }

    private fun add(column: LinearLayout, row: View) {
        column.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dpi(6)
        })
    }

    private fun section(column: LinearLayout, name: String) {
        column.addView(label(12f, "sans-serif-medium", 0.5f).apply {
            text = name.uppercase()
            letterSpacing = 0.08f
            setPadding(dpi(6), dpi(16), 0, dpi(6))
        })
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, android.R.anim.fade_out)
    }
}
