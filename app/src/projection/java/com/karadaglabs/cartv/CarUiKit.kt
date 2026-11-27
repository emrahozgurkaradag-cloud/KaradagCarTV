package com.karadaglabs.cartv

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * Araç ekranı (Android Auto host penceresi, Android View) için ÖK tasarım
 * sistemi. Renkler telefondaki Compose temasıyla aynı (res/values/colors.xml).
 * Ölçüler head-unit çözünürlüğüne göre [scale] ile ayarlanır: kısa ekranlarda
 * (ör. 800x480) dokunma hedefi en az 64dp kalır.
 */
object CarUiKit {

    val ORANGE = Color.parseColor("#FF7A1A")
    val ORANGE_SOFT = Color.parseColor("#33FF7A1A")
    val ANTHRACITE = Color.parseColor("#1B1E23")
    val ANTHRACITE_LIGHT = Color.parseColor("#23272E")
    val SURFACE = Color.parseColor("#2A2F37")
    val SURFACE_HIGH = Color.parseColor("#353B45")
    val INK = Color.parseColor("#F4F1EC")
    val INK_MUTED = Color.parseColor("#A9AEB6")
    val OUTLINE = Color.parseColor("#444B56")
    val LIVE_RED = Color.parseColor("#E5484D")

    fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    /** Kısa (yükseklik < 420dp) araç ekranında biraz daha sıkı yerleşim. */
    fun compact(context: Context): Boolean {
        val metrics = context.resources.displayMetrics
        return metrics.heightPixels / metrics.density < 420f
    }

    fun rounded(color: Int, radiusDp: Int, context: Context, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(context, radiusDp).toFloat()
            setColor(color)
            if (stroke != null) setStroke(dp(context, 1), stroke)
        }

    fun pressable(base: Drawable, context: Context): Drawable =
        RippleDrawable(ColorStateList.valueOf(Color.parseColor("#44FF7A1A")), base, null)

    fun icon(context: Context, res: Int, tint: Int): Drawable? =
        ContextCompat.getDrawable(context, res)?.mutate()?.apply { setTint(tint) }

    fun wordmark(context: Context, sizeSp: Float): TextView = TextView(context).apply {
        text = SpannableStringBuilder("KaradagCarTV").apply {
            setSpan(ForegroundColorSpan(INK), 0, 7, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(ORANGE), 7, 12, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        typeface = Typeface.DEFAULT_BOLD
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        maxLines = 1
    }

    fun logo(context: Context, sizeDp: Int): ImageView = ImageView(context).apply {
        setImageResource(R.drawable.ic_ok_monogram)
        background = rounded(ANTHRACITE_LIGHT, sizeDp / 4, context, OUTLINE)
        scaleType = ImageView.ScaleType.FIT_CENTER
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        layoutParams = LinearLayout.LayoutParams(dp(context, sizeDp), dp(context, sizeDp))
    }

    /** Hap biçimli seçim düğmesi (kategori, filtre). */
    fun chip(context: Context, label: String, selected: Boolean, iconRes: Int?, onClick: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            maxLines = 1
            minHeight = dp(context, if (compact(context)) 52 else 60)
            setPadding(dp(context, 20), 0, dp(context, 20), 0)
            setTextColor(if (selected) ANTHRACITE else INK)
            background = pressable(
                rounded(if (selected) ORANGE else SURFACE, 30, context, if (selected) ORANGE else OUTLINE),
                context
            )
            if (iconRes != null) {
                setCompoundDrawablesRelativeWithIntrinsicBounds(
                    icon(context, iconRes, if (selected) ANTHRACITE else ORANGE), null, null, null
                )
                compoundDrawablePadding = dp(context, 8)
            }
            isSelected = selected
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(context, 10) }
        }

    /** Kanal logosu yoksa baş harf rozeti. */
    fun initialsBadge(context: Context, name: String): Drawable =
        object : Drawable() {
            private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = ORANGE
                typeface = Typeface.DEFAULT_BOLD
                textAlign = android.graphics.Paint.Align.CENTER
            }
            private val bg = rounded(SURFACE_HIGH, 14, context)
            private val text = ChannelLogos.initials(name)

            override fun draw(canvas: android.graphics.Canvas) {
                bg.bounds = bounds
                bg.draw(canvas)
                paint.textSize = bounds.height() * 0.36f
                val y = bounds.exactCenterY() - (paint.descent() + paint.ascent()) / 2
                canvas.drawText(text, bounds.exactCenterX(), y, paint)
            }

            override fun setAlpha(alpha: Int) { paint.alpha = alpha }
            override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter }
            @Deprecated("Deprecated in Java")
            override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
        }
}
