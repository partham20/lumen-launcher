package com.partha.lumen

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

fun Context.dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

fun Context.dpi(v: Int) = dp(v.toFloat()).toInt()

fun Context.label(sp: Float, family: String, alpha: Float) = TextView(this).apply {
    textSize = sp
    setTextColor(Color.WHITE)
    this.alpha = alpha
    typeface = Typeface.create(family, Typeface.NORMAL)
}

/**
 * A settings row in the tvOS style: translucent when idle, white with dark text
 * when focused. [value] is the right-hand text, updated through [TvRow.setValue].
 */
class TvRow(context: Context, title: String, value: String, titleSp: Float = 17f) : LinearLayout(context) {
    private val titleView = context.label(titleSp, "sans-serif", 1f).apply { text = title }
    private val valueView = context.label(titleSp - 2f, "sans-serif", 1f).apply {
        text = value
        gravity = Gravity.END
        setTextColor(IDLE_VALUE)
    }
    private val idle = GradientDrawable().apply { cornerRadius = context.dp(10f); setColor(0x14FFFFFF) }
    private val active = GradientDrawable().apply { cornerRadius = context.dp(10f); setColor(0xF2FFFFFF.toInt()) }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isFocusable = true
        isClickable = true
        background = idle
        setPadding(context.dpi(18), context.dpi(13), context.dpi(18), context.dpi(13))
        addView(titleView, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(valueView)
        setOnFocusChangeListener { v, has ->
            v.background = if (has) active else idle
            titleView.setTextColor(if (has) Color.BLACK else Color.WHITE)
            valueView.setTextColor(if (has) 0xFF555560.toInt() else IDLE_VALUE)
            v.animate().scaleX(if (has) 1.02f else 1f).scaleY(if (has) 1.02f else 1f).setDuration(120).start()
        }
    }

    fun setValue(text: String) {
        valueView.text = text
    }

    private companion object {
        const val IDLE_VALUE = 0x99FFFFFF.toInt()
    }
}
