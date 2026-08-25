package com.tom.rv2ide.fragments.terminal

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.drawable.AnimatedVectorDrawable
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import com.google.android.material.navigationrail.NavigationRailView
import com.termux.shared.logger.Logger

class NavigationHandler(
    private val context: Context,
    private val navigationRail: NavigationRailView?,
    private val mainContent: LinearLayout?,
    private val menuButton: ImageView?,
    private val onMenuItemSelected: (Int) -> Boolean
) {
    
    companion object {
        const val NAV_RAIL_WIDTH = 80
        const val ANIMATION_DURATION = 250L
    }
    
    var isNavRailVisible = false
        private set
    
    private var isAnimating = false
    
    fun setup() {
        navigationRail?.setOnItemSelectedListener { item ->
            onMenuItemSelected(item.itemId)
        }
        
        menuButton?.apply {
            alpha = 1f
            visibility = View.VISIBLE
            setOnClickListener { showNavigationRail() }
        }
    }
    
    fun showNavigationRail() {
        if (isAnimating || isNavRailVisible) return
        isAnimating = true
        isNavRailVisible = true
        
        val navRailWidth = (NAV_RAIL_WIDTH * context.resources.displayMetrics.density).toInt()
        animateMenuIcon(isMenuToArrow = true)
    
        menuButton?.animate()
            ?.alpha(0f)
            ?.scaleX(0.8f)
            ?.scaleY(0.8f)
            ?.setDuration(ANIMATION_DURATION)
            ?.setInterpolator(DecelerateInterpolator())
            ?.withEndAction {
                menuButton.visibility = View.GONE
                menuButton.scaleX = 1f
                menuButton.scaleY = 1f
            }
            ?.start()
        
        navigationRail?.apply {
            visibility = View.VISIBLE
            translationX = -navRailWidth.toFloat()
            alpha = 0f
            animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(ANIMATION_DURATION)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
        
        ValueAnimator.ofInt(0, navRailWidth).apply {
            duration = ANIMATION_DURATION
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val value = animator.animatedValue as Int
                (mainContent?.layoutParams as? ViewGroup.MarginLayoutParams)?.apply {
                    marginStart = value
                    mainContent.layoutParams = this
                }
            }
            start()
        }.also {
            it.doOnEnd { isAnimating = false }
        }
    }
    
    fun hideNavigationRail() {
        if (isAnimating || !isNavRailVisible) return
        isAnimating = true
        isNavRailVisible = false
        
        val navRailWidth = (NAV_RAIL_WIDTH * context.resources.displayMetrics.density).toInt()
        
        navigationRail?.animate()
            ?.translationX(-navRailWidth.toFloat())
            ?.alpha(0f)
            ?.setDuration(ANIMATION_DURATION)
            ?.setInterpolator(DecelerateInterpolator())
            ?.withEndAction {
                navigationRail.visibility = View.GONE
                navigationRail.alpha = 1f
            }
            ?.start()
        
        ValueAnimator.ofInt(navRailWidth, 0).apply {
            duration = ANIMATION_DURATION
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val value = animator.animatedValue as Int
                (mainContent?.layoutParams as? ViewGroup.MarginLayoutParams)?.apply {
                    marginStart = value
                    mainContent.layoutParams = this
                }
            }
            start()
        }.also {
            it.doOnEnd {
                isAnimating = false
                menuButton?.apply {
                    visibility = View.VISIBLE
                    alpha = 0f
                    scaleX = 0.8f
                    scaleY = 0.8f
                    animateMenuIcon(isMenuToArrow = false)
            
                    animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(ANIMATION_DURATION)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }
            }
        }
    }
    
    private fun animateMenuIcon(isMenuToArrow: Boolean) {
        try {
            val drawableRes = if (isMenuToArrow) {
                com.tom.rv2ide.R.drawable.avd_menu_to_arrow
            } else {
                com.tom.rv2ide.R.drawable.avd_arrow_to_menu
            }
            
            val animatedDrawable = AnimatedVectorDrawableCompat.create(context, drawableRes)
            if (animatedDrawable != null) {
                menuButton?.setImageDrawable(animatedDrawable)
                animatedDrawable.start()
            } else {
                val drawable = context.getDrawable(drawableRes)
                if (drawable is AnimatedVectorDrawable) {
                    menuButton?.setImageDrawable(drawable)
                    drawable.start()
                }
            }
        } catch (e: Exception) {
            Logger.logError("NavigationHandler", "Error animating menu icon: ${e.message}")
        }
    }
}

private fun ValueAnimator.doOnEnd(action: () -> Unit) {
    addListener(object : android.animation.Animator.AnimatorListener {
        override fun onAnimationStart(animation: android.animation.Animator) {}
        override fun onAnimationEnd(animation: android.animation.Animator) { action() }
        override fun onAnimationCancel(animation: android.animation.Animator) {}
        override fun onAnimationRepeat(animation: android.animation.Animator) {}
    })
}