package com.partha.lumen

import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import java.util.Collections

/**
 * Lumen: a minimal home screen in the spirit of the Apple TV home.
 *
 * Layout, top to bottom: a "top shelf" showing the focused app's name over a
 * blurred, colour-tinted backdrop of its banner; a row of favourites; then a
 * grid of every other app. Hold OK (or press Menu) on a tile for options.
 */
class MainActivity : Activity() {

    private class App(val pkg: String, val component: ComponentName, val label: String, val info: ResolveInfo)

    private lateinit var prefs: SharedPreferences
    private lateinit var backdrop: ImageView
    private lateinit var title: TextView
    private lateinit var scroll: ScrollView
    private lateinit var favRow: LinearLayout
    private lateinit var grid: GridLayout
    private lateinit var allLabel: TextView

    private val scrim = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0, 0, Color.BLACK))
    private var tintColor = Color.BLACK
    private var tintAnimator: ValueAnimator? = null
    private val bannerCache = HashMap<String, Drawable?>()
    private val colorCache = HashMap<String, Int>()
    private var shelfPkg: String? = null
    private var lastFocused: String? = null
    private var resumed = false

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            bannerCache.clear()
            colorCache.clear()
            reload()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("lumen", MODE_PRIVATE)
        window.setBackgroundDrawable(ColorDrawable(Color.BLACK))
        setContentView(buildUi())
        applyScrim()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(packageReceiver, filter, RECEIVER_EXPORTED)
        else registerReceiver(packageReceiver, filter)
        reload()
    }

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
    }

    override fun onPause() {
        resumed = false
        super.onPause()
    }

    // Home pressed while already home: jump back to the first favourite.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (resumed) goToTop()
    }

    @Deprecated("Home screen has nowhere to go back to")
    override fun onBackPressed() = goToTop()

    // ---- UI ----------------------------------------------------------------

    private fun buildUi(): View {
        val root = FrameLayout(this)

        backdrop = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 0f
            if (Build.VERSION.SDK_INT >= 31) {
                setRenderEffect(RenderEffect.createBlurEffect(dp(40f), dp(40f), Shader.TileMode.CLAMP))
            }
        }
        root.addView(backdrop, fill())
        root.addView(View(this).apply { background = scrim }, fill())

        scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            isFillViewport = true
            clipChildren = false
            clipToPadding = false
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(48), 0, dpi(48), dpi(40))
            clipChildren = false
            clipToPadding = false
        }
        scroll.addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(scroll, fill())

        val shelf = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM
            setPadding(dpi(4), 0, 0, dpi(14))
        }
        title = text(44f, "sans-serif-light", 1f).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
        shelf.addView(title)
        content.addView(shelf, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpi(270)))

        val favScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
            setPadding(0, dpi(14), 0, dpi(14))
        }
        favRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
            setPadding(dpi(4), 0, dpi(4), 0)
        }
        favScroll.addView(favRow)
        content.addView(favScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        allLabel = text(14f, "sans-serif-medium", 0.55f).apply {
            text = "All apps"
            setPadding(dpi(4), dpi(16), 0, dpi(6))
        }
        content.addView(allLabel)
        grid = GridLayout(this).apply {
            columnCount = 6
            clipChildren = false
            clipToPadding = false
            setPadding(dpi(4), dpi(8), 0, 0)
        }
        content.addView(grid)

        val clock = TextClock(this).apply {
            format12Hour = "h:mm"
            format24Hour = "HH:mm"
            textSize = 20f
            setTextColor(Color.WHITE)
            alpha = 0.85f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setShadowLayer(dp(6f), 0f, dp(1f), 0x99000000.toInt())
        }
        root.addView(clock, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END
        ).apply { setMargins(0, dpi(24), dpi(40), 0) })

        return root
    }

    private fun reload() {
        val pm = packageManager
        val hidden = hiddenSet()
        val seen = HashSet<String>()
        val all = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0
        ).mapNotNull { ri ->
            val pkg = ri.activityInfo.packageName
            if (pkg == packageName || !seen.add(pkg)) null
            else App(pkg, ComponentName(pkg, ri.activityInfo.name), ri.loadLabel(pm).toString(), ri)
        }
        val byPkg = all.associateBy { it.pkg }
        val favPkgs = favourites().filter { it in byPkg && it !in hidden }
        val rest = all.filter { it.pkg !in favPkgs && it.pkg !in hidden }.sortedBy { it.label.lowercase() }

        favRow.removeAllViews()
        grid.removeAllViews()
        favPkgs.forEach { favRow.addView(tile(byPkg.getValue(it), fav = true)) }
        rest.forEach { grid.addView(tile(it, fav = false)) }
        allLabel.visibility = if (rest.isEmpty()) View.GONE else View.VISIBLE

        val target = lastFocused?.let { findTile(it) } ?: favRow.getChildAt(0) ?: grid.getChildAt(0)
        target?.post { target.requestFocus() }
    }

    private fun tile(app: App, fav: Boolean): View {
        val w = if (fav) 160 else 126
        val h = if (fav) 90 else 71
        val radius = dp(if (fav) 12f else 9f)

        val card = FrameLayout(this).apply {
            tag = app.pkg
            isFocusable = true
            isClickable = true
            isLongClickable = true
            background = GradientDrawable().apply { cornerRadius = radius; setColor(0xFF1E1F24.toInt()) }
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = true
        }

        val banner = bannerOf(app)
        if (banner != null) {
            card.addView(ImageView(this).apply {
                setImageDrawable(banner)
                scaleType = ImageView.ScaleType.CENTER_CROP
            }, fill())
        } else {
            // No TV banner: icon plus name, centred.
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
            val size = dpi(if (fav) 40 else 30)
            col.addView(ImageView(this).apply { setImageDrawable(app.info.loadIcon(packageManager)) }, LinearLayout.LayoutParams(size, size))
            col.addView(text(if (fav) 12f else 10f, "sans-serif-medium", 0.9f).apply {
                text = app.label
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER
                setPadding(dpi(6), dpi(4), dpi(6), 0)
            })
            card.addView(col, fill())
        }

        val ring = View(this).apply {
            background = GradientDrawable().apply { cornerRadius = radius; setStroke(dpi(2), 0xE6FFFFFF.toInt()) }
            alpha = 0f
        }
        card.addView(ring, fill())

        card.layoutParams = if (fav) {
            LinearLayout.LayoutParams(dpi(w), dpi(h)).apply { marginEnd = dpi(18) }
        } else {
            GridLayout.LayoutParams().apply { width = dpi(w); height = dpi(h); setMargins(0, 0, dpi(16), dpi(16)) }
        }

        card.setOnFocusChangeListener { v, has ->
            val scale = if (!has) 1f else if (fav) 1.12f else 1.14f
            v.animate().scaleX(scale).scaleY(scale).translationZ(if (has) dp(14f) else 0f)
                .setDuration(160).setInterpolator(DecelerateInterpolator()).start()
            ring.animate().alpha(if (has) 1f else 0f).setDuration(160).start()
            if (has) {
                lastFocused = app.pkg
                showShelf(app)
                if (fav) scroll.smoothScrollTo(0, 0)
            }
        }
        card.setOnClickListener { launch(app) }
        card.setOnLongClickListener { options(app, fav); true }
        card.setOnKeyListener { _, code, ev ->
            if (code == KeyEvent.KEYCODE_MENU && ev.action == KeyEvent.ACTION_UP) { options(app, fav); true } else false
        }
        return card
    }

    // ---- Top shelf -----------------------------------------------------------

    private fun showShelf(app: App) {
        if (shelfPkg == app.pkg) return
        shelfPkg = app.pkg
        title.text = app.label

        val art = bannerOf(app) ?: app.info.loadIcon(packageManager)
        val copy = art.constantState?.newDrawable(resources)?.mutate() ?: art
        backdrop.animate().cancel()
        backdrop.animate().alpha(0f).setDuration(120).withEndAction {
            backdrop.setImageDrawable(copy)
            backdrop.animate().alpha(0.55f).setDuration(320).start()
        }.start()
        tintTo(accentOf(app, art))
    }

    private fun tintTo(target: Int) {
        tintAnimator?.cancel()
        tintAnimator = ValueAnimator.ofArgb(tintColor, target).apply {
            duration = 450
            addUpdateListener { tintColor = it.animatedValue as Int; applyScrim() }
            start()
        }
    }

    private fun applyScrim() {
        scrim.colors = intArrayOf(
            withAlpha(tintColor, 0x70),
            withAlpha(blend(tintColor, Color.BLACK, 0.6f), 0xD0),
            0xF5000000.toInt(),
        )
    }

    /** A rich, darkened version of the art's dominant colour, favouring vivid pixels. */
    private fun accentOf(app: App, art: Drawable): Int = colorCache.getOrPut(app.pkg) {
        val w = 24
        val h = 14
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val copy = art.constantState?.newDrawable(resources)?.mutate() ?: art
        copy.setBounds(0, 0, w, h)
        copy.draw(Canvas(bmp))
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        bmp.recycle()

        var r = 0L; var g = 0L; var b = 0L; var n = 0L
        val hsv = FloatArray(3)
        for (p in px) {
            if (Color.alpha(p) < 128) continue
            Color.colorToHSV(p, hsv)
            val weight = 1 + (hsv[1] * hsv[2] * 8).toInt()
            r += Color.red(p) * weight; g += Color.green(p) * weight; b += Color.blue(p) * weight; n += weight
        }
        if (n == 0L) return@getOrPut 0xFF303036.toInt()
        Color.RGBToHSV((r / n).toInt(), (g / n).toInt(), (b / n).toInt(), hsv)
        hsv[1] = (hsv[1] * 1.2f).coerceAtMost(0.85f)
        hsv[2] = hsv[2].coerceIn(0.25f, 0.5f)
        Color.HSVToColor(hsv)
    }

    // ---- Actions -------------------------------------------------------------

    private fun launch(app: App) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
            .setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Can't open ${app.label}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun options(app: App, fav: Boolean) {
        val actions = ArrayList<Pair<String, () -> Unit>>()
        if (fav) {
            actions += "Move left" to { moveFav(app.pkg, -1) }
            actions += "Move right" to { moveFav(app.pkg, +1) }
            actions += "Remove from Favourites" to { saveFavs(visibleFavs().apply { remove(app.pkg) }); reload() }
        } else {
            actions += "Add to Favourites" to { saveFavs(visibleFavs().apply { add(app.pkg) }); reload() }
        }
        actions += "Hide ${app.label}" to {
            saveHidden(hiddenSet().apply { add(app.pkg) })
            lastFocused = null
            reload()
        }
        actions += "App info" to {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.pkg, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        val hidden = hiddenSet()
        if (hidden.isNotEmpty()) actions += "Show hidden apps (${hidden.size})" to { saveHidden(emptySet()); reload() }

        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(app.label)
            .setItems(actions.map { it.first }.toTypedArray()) { _, i -> actions[i].second() }
            .show()
    }

    private fun moveFav(pkg: String, delta: Int) {
        val list = visibleFavs()
        val from = list.indexOf(pkg)
        val to = from + delta
        if (from < 0 || to !in list.indices) return
        Collections.swap(list, from, to)
        saveFavs(list)
        reload()
    }

    private fun goToTop() {
        scroll.smoothScrollTo(0, 0)
        (favRow.getChildAt(0) ?: grid.getChildAt(0))?.requestFocus()
    }

    // ---- Storage ---------------------------------------------------------------

    private val defaultFavs = listOf(
        "com.netflix.ninja",
        "com.google.android.youtube.tv",
        "in.startv.hotstar",
        "com.amazon.amazonvideo.livingroom",
        "com.spotify.tv.android",
        "com.mxtech.videoplayer.television",
    )
    private val defaultHidden = setOf("me.efesser.flauncher", "com.vt.tv.mobiledeviceconnectiontips")

    private fun favourites(): List<String> =
        prefs.getString("favs", null)?.split(',')?.filter { it.isNotEmpty() } ?: defaultFavs

    private fun saveFavs(list: List<String>) = prefs.edit().putString("favs", list.joinToString(",")).apply()

    private fun hiddenSet(): MutableSet<String> = HashSet(prefs.getStringSet("hidden", defaultHidden)!!)

    private fun saveHidden(set: Set<String>) = prefs.edit().putStringSet("hidden", set).apply()

    private fun visibleFavs(): MutableList<String> =
        (0 until favRow.childCount).map { favRow.getChildAt(it).tag as String }.toMutableList()

    // ---- Helpers -----------------------------------------------------------------

    private fun bannerOf(app: App): Drawable? = bannerCache.getOrPut(app.pkg) {
        try { app.info.activityInfo.loadBanner(packageManager) } catch (e: Exception) { null }
    }

    private fun findTile(pkg: String): View? =
        favRow.findViewWithTag(pkg) ?: grid.findViewWithTag(pkg)

    private fun text(sp: Float, family: String, alpha: Float) = TextView(this).apply {
        textSize = sp
        setTextColor(Color.WHITE)
        this.alpha = alpha
        typeface = Typeface.create(family, Typeface.NORMAL)
        setShadowLayer(dp(6f), 0f, dp(1f), 0x99000000.toInt())
    }

    private fun fill() = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    private fun dpi(v: Int) = dp(v.toFloat()).toInt()

    private fun withAlpha(c: Int, a: Int) = (c and 0x00FFFFFF) or (a shl 24)

    private fun blend(a: Int, b: Int, t: Float) = Color.rgb(
        (Color.red(a) * (1 - t) + Color.red(b) * t).toInt(),
        (Color.green(a) * (1 - t) + Color.green(b) * t).toInt(),
        (Color.blue(a) * (1 - t) + Color.blue(b) * t).toInt(),
    )
}
