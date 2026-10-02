/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.xiaomi.settings.R

class PillNavBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private data class NavItem(val id: Int, val iconRes: Int, val label: String)

    private val items = mutableListOf<NavItem>()
    private val itemViews = mutableMapOf<Int, FrameLayout>()
    private val iconViews = mutableMapOf<Int, ImageView>()
    private val labelViews = mutableMapOf<Int, TextView>()
    private val weightAnimators = mutableMapOf<Int, ValueAnimator?>()

    private var selectedId = -1

    var onItemSelectedListener: ((Int) -> Unit)? = null

    private val idleIconColor: Int by lazy {
        ContextCompat.getColor(context, R.color.nav_icon_idle)
    }
    private val activeIconColor: Int by lazy {
        ContextCompat.getColor(context, R.color.nav_item_selected_on)
    }
    private val rippleColor: Int by lazy {
        ContextCompat.getColor(context, R.color.nav_item_ripple)
    }
    private val itemRadiusPx: Int by lazy {
        resources.getDimensionPixelSize(R.dimen.nav_item_radius)
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val padH = resources.getDimensionPixelSize(R.dimen.nav_pill_padding_h)
        val padV = resources.getDimensionPixelSize(R.dimen.nav_pill_padding_v)
        setPadding(padH, padV, padH, padV)
        background = ContextCompat.getDrawable(context, R.drawable.bg_nav_pill)
        outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        elevation = resources.getDimensionPixelSize(R.dimen.nav_pill_elevation).toFloat()
        clipToPadding = false
    }

    fun addItem(id: Int, iconRes: Int, labelRes: Int) {
        val label = context.getString(labelRes)
        items.add(NavItem(id, iconRes, label))

        val itemHeight = resources.getDimensionPixelSize(R.dimen.nav_item_height)
        val gap = resources.getDimensionPixelSize(R.dimen.nav_item_gap)
        val iconSize = resources.getDimensionPixelSize(R.dimen.nav_icon_size)
        val contentPadH = resources.getDimensionPixelSize(R.dimen.nav_content_padding_h)
        val labelGap = resources.getDimensionPixelSize(R.dimen.nav_label_margin_start)
        val labelSizePx = resources.getDimensionPixelSize(R.dimen.nav_label_text_size)

        val item = FrameLayout(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(gap, 0, gap, 0)
            }
            minimumHeight = itemHeight
            isClickable = true
            isFocusable = true
            foreground = makeRippleMask()
            setOnClickListener { select(id, fromUser = true) }
        }

        val content = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(contentPadH, 0, contentPadH, 0)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        }

        val icon = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
            setImageResource(iconRes)
            imageTintList = android.content.res.ColorStateList.valueOf(idleIconColor)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        content.addView(icon)

        val tv = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_VERTICAL
                marginStart = labelGap
            }
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_PX, labelSizePx.toFloat())
            maxLines = 1
            includeFontPadding = false
            visibility = GONE
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        content.addView(tv)

        item.addView(content)
        addView(item)

        itemViews[id] = item
        iconViews[id] = icon
        labelViews[id] = tv
    }

    fun setSelectedItem(id: Int) {
        select(id, fromUser = false)
    }

    fun getSelectedItem(): Int = selectedId

    fun select(id: Int, fromUser: Boolean) {
        if (id == selectedId) return
        selectedId = id

        animateWeights(id)
        refreshItemStates()

        if (fromUser) onItemSelectedListener?.invoke(id)
    }

    private fun animateWeights(selected: Int) {
        val childCount = items.size
        if (childCount == 0) return

        val selectedWeight = 1.7f
        val restWeight = ((childCount * 1f) - selectedWeight) / (childCount - 1).coerceAtLeast(1)

        items.forEach { item ->
            val view = itemViews[item.id] ?: return@forEach
            val lp = view.layoutParams as? LayoutParams ?: return@forEach
            val from = lp.weight
            val to = if (item.id == selected) selectedWeight else restWeight

            weightAnimators[item.id]?.cancel()
            if (from == to) return@forEach

            val animator = ValueAnimator.ofFloat(from, to).apply {
                duration = ANIM_DURATION_MS
                interpolator = android.view.animation.DecelerateInterpolator(1.4f)
                addUpdateListener { anim ->
                    val value = anim.animatedValue as Float
                    val p = view.layoutParams as LayoutParams
                    p.weight = value
                    view.layoutParams = p
                }
                start()
            }
            weightAnimators[item.id] = animator
        }
    }

    private fun refreshItemStates() {
        items.forEach { item ->
            val selected = item.id == selectedId
            val itemView = itemViews[item.id] ?: return@forEach
            val icon = iconViews[item.id] ?: return@forEach
            val label = labelViews[item.id] ?: return@forEach

            if (selected) {
                itemView.background = ContextCompat.getDrawable(
                    context, R.drawable.bg_nav_item_selected)
                icon.imageTintList = android.content.res.ColorStateList.valueOf(activeIconColor)
                label.setTextColor(activeIconColor)
                label.visibility = VISIBLE
            } else {
                itemView.background = null
                icon.imageTintList = android.content.res.ColorStateList.valueOf(idleIconColor)
                label.visibility = GONE
            }
        }
    }

    private fun makeRippleMask(): RippleDrawable {
        val mask = GradientDrawable().apply {
            cornerRadius = itemRadiusPx.toFloat()
            setColor(Color.WHITE)
        }
        return RippleDrawable(
            android.content.res.ColorStateList.valueOf(rippleColor),
            null,
            mask
        )
    }

    private companion object {
        const val ANIM_DURATION_MS = 200L
    }
}
