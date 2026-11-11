package com.partha.lumen

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File

/**
 * Lumen Settings: everything about the home screen that can change without a rebuild.
 *
 * Also accepts `--es import_avatar <path>` so a photo can be set over adb.
 */
class SettingsActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var list: LinearLayout
    private lateinit var previewImage: ImageView
    private lateinit var previewInitial: TextView
    private var focusedRow = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        setContentView(buildUi())
        intent.getStringExtra("import_avatar")?.let { importPhoto(File(it)) }
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dpi(64), dpi(40), dpi(56), dpi(40))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF23242C.toInt(), 0xFF0B0B0E.toInt()))
        }

        val left = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val preview = FrameLayout(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0x40FFFFFF) }
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = true
        }
        previewImage = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        preview.addView(previewImage, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        previewInitial = label(40f, "sans-serif-light", 1f).apply { gravity = Gravity.CENTER }
        preview.addView(previewInitial, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        left.addView(preview, LinearLayout.LayoutParams(dpi(112), dpi(112)))
        left.addView(label(32f, "sans-serif-light", 1f).apply { text = "Lumen"; setPadding(0, dpi(18), 0, 0) })
        left.addView(label(14f, "sans-serif", 0.6f).apply { text = "Home screen settings" })
        root.addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.36f))

        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            setPadding(0, dpi(8), 0, dpi(8))
        }
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.64f))
        return root
    }

    /** Rebuilds the list from current prefs, keeping focus on the same row. */
    private fun render() {
        val photo = Avatar.load(this)
        previewImage.setImageBitmap(photo)
        previewInitial.text = prefs.name.trim().take(1).uppercase()
        previewInitial.visibility = if (photo == null) View.VISIBLE else View.GONE

        list.removeAllViews()
        section("Profile")
        row("Your name", prefs.name) { editName() }
        row("Greeting", greetingLabel(prefs.greeting)) { chooseGreeting() }
        row("Profile photo", if (photo == null) "None" else "Set") { photoOptions() }

        section("Clock")
        row("Day and date", onOff(prefs.showDate)) { prefs.showDate = !prefs.showDate; render() }
        row("Time format", if (prefs.clock24) "24-hour" else "12-hour") { prefs.clock24 = !prefs.clock24; render() }

        section("Look")
        row("Background colour", if (prefs.dynamicTint) "Match app" else "Dark") { prefs.dynamicTint = !prefs.dynamicTint; render() }
        row("App artwork backdrop", onOff(prefs.artwork)) { prefs.artwork = !prefs.artwork; render() }

        section("Remote")
        val remoteOn = RemoteService.isEnabled(this)
        row("Remote buttons", if (remoteOn) "On" else "Off · turn on") { openAccessibilitySettings() }
        row("Quick Settings button", buttonLabel(prefs.quickKey, "Settings ⚙")) { assignButton("quick", "Quick Settings") }
        row("Close apps button", buttonLabel(prefs.closeKey, "Live TV 📺")) { assignButton("close", "Close apps") }

        section("Apps")
        row("Close apps on home screen", onOff(prefs.closeButton)) { prefs.closeButton = !prefs.closeButton; render() }
        row("Keep running when closing", keepRunningLabel()) { chooseKeepRunning() }
        row("Hidden apps", "${prefs.hidden.size} hidden") { chooseHidden() }
        row("Reset favourites", "") { confirmResetFavourites() }

        section("System")
        row("Android TV settings", "") {
            startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        row("About Lumen", "Version ${packageManager.getPackageInfo(packageName, 0).versionName}") {}

        val rows = (0 until list.childCount).map { list.getChildAt(it) }.filter { it.isFocusable }
        rows.getOrNull(focusedRow.coerceAtMost(rows.lastIndex))?.requestFocus()
    }

    private fun section(name: String) {
        list.addView(label(13f, "sans-serif-medium", 0.5f).apply {
            text = name.uppercase()
            letterSpacing = 0.08f
            setPadding(dpi(18), dpi(18), 0, dpi(6))
        })
    }

    private fun row(title: String, value: String, onClick: () -> Unit) {
        val index = (0 until list.childCount).count { list.getChildAt(it).isFocusable }
        val row = TvRow(this, title, value)
        val styleFocus = row.onFocusChangeListener
        row.setOnFocusChangeListener { v, has ->
            styleFocus.onFocusChange(v, has)
            if (has) focusedRow = index
        }
        row.setOnClickListener { onClick() }
        list.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dpi(6)
        })
    }

    // ---- Profile -------------------------------------------------------------

    private fun editName() {
        val input = EditText(this).apply {
            setText(prefs.name)
            setSelection(text.length)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setSingleLine()
        }
        val frame = FrameLayout(this).apply { setPadding(dpi(20), dpi(8), dpi(20), 0); addView(input) }
        dialog()
            .setTitle("Your name")
            .setView(frame)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) prefs.name = name
                render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun greetingLabel(key: String) = when (key) {
        "time" -> "Good morning / evening"
        "off" -> "Off"
        else -> "Welcome"
    }

    private fun chooseGreeting() {
        val keys = arrayOf("welcome", "time", "off")
        dialog()
            .setTitle("Greeting")
            .setSingleChoiceItems(keys.map { greetingLabel(it) }.toTypedArray(), keys.indexOf(prefs.greeting)) { d, i ->
                prefs.greeting = keys[i]
                d.dismiss()
                render()
            }
            .show()
    }

    private fun photoOptions() {
        val options = mutableListOf("Choose from Downloads, Pictures or USB")
        if (Avatar.file(this).exists()) options += "Remove photo"
        dialog()
            .setTitle("Profile photo")
            .setItems(options.toTypedArray()) { _, i ->
                if (i == 0) pickPhoto() else { Avatar.remove(this); render() }
            }
            .show()
    }

    private val readImagesPermission =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE

    private fun pickPhoto() {
        if (checkSelfPermission(readImagesPermission) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(readImagesPermission), REQUEST_IMAGES)
            return
        }
        val images = findImages()
        if (images.isEmpty()) {
            toast("No photos found. Put a .jpg or .png in Downloads or Pictures, or on a USB stick.")
            return
        }
        dialog()
            .setTitle("Choose a photo")
            .setItems(images.map { "${it.name}   ·   ${it.parentFile?.name}" }.toTypedArray()) { _, i -> importPhoto(images[i]) }
            .show()
    }

    @Deprecated("Fine for a single permission request")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == REQUEST_IMAGES && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) pickPhoto()
        else if (requestCode == REQUEST_IMAGES) toast("Lumen needs access to photos to choose one.")
    }

    /** Images in the usual photo folders and on any USB drive, newest first. */
    private fun findImages(): List<File> {
        val roots = mutableListOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
        )
        File("/storage").listFiles()
            ?.filter { it.name != "emulated" && it.name != "self" && it.canRead() }
            ?.forEach { usb -> roots += listOf(usb, File(usb, "Pictures"), File(usb, "DCIM")) }

        val extensions = setOf("jpg", "jpeg", "png", "webp")
        return roots.filter { it.isDirectory }
            .flatMap { root -> root.walk().maxDepth(2).filter { it.isFile && it.extension.lowercase() in extensions }.toList() }
            .distinctBy { it.absolutePath }
            .sortedByDescending { it.lastModified() }
            .take(60)
    }

    private fun importPhoto(file: File) {
        val ok = try { Avatar.import(this, file) } catch (e: Exception) { false }
        toast(if (ok) "Profile photo updated" else "Couldn't read ${file.name}")
        render()
    }

    // ---- Remote --------------------------------------------------------------

    private fun buttonLabel(saved: Int, default: String) = if (saved < 0) default else Keys.name(saved)

    /** Accessibility services are a security setting, so the user switches this on themselves. */
    private fun openAccessibilitySettings() {
        dialog()
            .setTitle("Turn on remote buttons")
            .setMessage(
                "On the next screen, open \"Lumen remote buttons\" and switch it on.\n\n" +
                    "Lumen only watches remote button presses. It doesn't read anything on screen."
            )
            .setPositiveButton("Open accessibility") { _, _ ->
                try {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Waits for the next remote button press (caught by [RemoteService]) and saves it. */
    private fun assignButton(target: String, what: String) {
        if (!RemoteService.isEnabled(this)) {
            openAccessibilitySettings()
            return
        }
        prefs.learning = target
        val waiting = dialog()
            .setTitle(what)
            .setMessage("Press the remote button you want to use.\n\nPress Back to cancel.")
            .setNeutralButton("Use default button") { _, _ ->
                prefs.learning = ""
                if (target == "quick") prefs.quickKey = -1 else prefs.closeKey = -1
                render()
            }
            .create()
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "learning" && prefs.learning.isEmpty()) {
                waiting.dismiss()
                render()
            }
        }
        prefs.onChange(listener)
        waiting.setOnDismissListener {
            prefs.stopOnChange(listener)
            prefs.learning = ""
        }
        waiting.show()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    // ---- Apps ----------------------------------------------------------------

    /** Every TV app except Lumen, as (package, label), sorted by label. */
    private fun tvApps(): List<Pair<String, String>> {
        val pm = packageManager
        val seen = HashSet<String>()
        return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0)
            .filter { it.activityInfo.packageName != packageName && seen.add(it.activityInfo.packageName) }
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .sortedBy { it.second.lowercase() }
    }

    /** A tick-list of TV apps; [save] gets the ticked packages. */
    private fun chooseApps(title: String, selected: Set<String>, save: (Set<String>) -> Unit) {
        val apps = tvApps()
        val picked = selected.toMutableSet()
        val checked = apps.map { it.first in picked }.toBooleanArray()
        dialog()
            .setTitle(title)
            .setMultiChoiceItems(apps.map { it.second }.toTypedArray(), checked) { _, i, on ->
                if (on) picked += apps[i].first else picked -= apps[i].first
            }
            .setPositiveButton("Save") { _, _ -> save(picked); render() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun chooseHidden() = chooseApps("Hidden apps", prefs.hidden) { prefs.hidden = it }

    private fun chooseKeepRunning() = chooseApps("Keep running when closing", prefs.keepRunning) { prefs.keepRunning = it }

    private fun keepRunningLabel(): String {
        val keep = prefs.keepRunning
        val names = tvApps().filter { it.first in keep }.map { it.second }
        return when {
            names.isEmpty() -> "None"
            names.size <= 2 -> names.joinToString(", ")
            else -> "${names.size} apps"
        }
    }

    private fun confirmResetFavourites() {
        dialog()
            .setTitle("Reset favourites?")
            .setMessage("Puts the favourites row back to Netflix, YouTube, JioHotstar, Prime Video, Spotify and MX Player.")
            .setPositiveButton("Reset") { _, _ -> prefs.favourites = Prefs.DEFAULT_FAVS; toast("Favourites reset") }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---- Helpers -------------------------------------------------------------

    private fun dialog() = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)

    private fun onOff(on: Boolean) = if (on) "On" else "Off"

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private fun label(sp: Float, family: String, alpha: Float) = TextView(this).apply {
        textSize = sp
        setTextColor(Color.WHITE)
        this.alpha = alpha
        typeface = Typeface.create(family, Typeface.NORMAL)
    }

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    private fun dpi(v: Int) = dp(v.toFloat()).toInt()

    private companion object {
        const val REQUEST_IMAGES = 1
    }
}
