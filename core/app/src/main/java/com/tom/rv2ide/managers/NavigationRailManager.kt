package com.tom.rv2ide.managers

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.DecelerateInterpolator
import androidx.core.view.isVisible
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigationrail.NavigationRailView

class NavigationRailManager(
    private val navigationRail: NavigationRailView,
    private val overlayView: View,
    private val fabToggle: FloatingActionButton
) {

    /**
     * Observer for rail state changes. All callbacks fire on the main thread.
     * `onSlide` is called every animation frame with a value in [0f, 1f]:
     *   0f = fully collapsed, 1f = fully expanded.
     */
    interface RailStateListener {
        fun onExpanded() {}
        fun onCollapsed() {}
        fun onSlide(progress: Float) {}
    }

    var stateListener: RailStateListener? = null

    // region Internal state -------------------------------------------------
    private var isExpanded = false
    private var isAnimating = false
    private var isLayoutReady = false

    private var railWidth: Float = 0f
    private var currentProgress: Float = 0f

    private var slideAnimator: ValueAnimator? = null
    private var overlayAnimator: ValueAnimator? = null
    private var fabAnimator: ValueAnimator? = null
    // endregion

    companion object {
        private const val ANIM_DURATION_MS = 280L
        private const val FAB_ROTATION_OPEN = 90f
        private const val FAB_ROTATION_CLOSED = 0f
    }

    init {
        // Establish initial visual state BEFORE any layout pass so the rail
        // never flashes on-screen and the overlay doesn't appear solid.
        overlayView.alpha = 0f
        overlayView.isVisible = false
        navigationRail.isVisible = false

        setupToggleButton()
        setupOverlayClick()
        observeLayout()
    }

    // region Layout observation --------------------------------------------
    private fun observeLayout() {
        navigationRail.viewTreeObserver.addOnGlobalLayoutListener(
            object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    val width = navigationRail.width.toFloat()
                    if (width > 0f) {
                        railWidth = width
                        isLayoutReady = true
                        applyProgress(if (isExpanded) 1f else 0f)
                        navigationRail.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    }
                }
            }
        )
    }
    // endregion

    // region Input wiring --------------------------------------------------
    private fun setupToggleButton() {
        fabToggle.setOnClickListener { toggle() }
    }

    private fun setupOverlayClick() {
        overlayView.setOnClickListener {
            if (isExpanded) collapse()
        }
    }
    // endregion

    // region Public API ----------------------------------------------------
    fun toggle() {
        if (isAnimating) return
        if (isExpanded) collapse() else expand()
    }

    fun expand() {
        if (!isLayoutReady || isExpanded || isAnimating) return
        animateTo(targetProgress = 1f, expandedAfter = true)
    }

    fun collapse() {
        if (!isLayoutReady || !isExpanded || isAnimating) return
        animateTo(targetProgress = 0f, expandedAfter = false)
    }

    /** Snap to expanded without animation. Safe to call before layout. */
    fun expandImmediate() {
        isExpanded = true
        if (!isLayoutReady) return
        cancelAnimations()
        applyProgress(1f)
    }

    /** Snap to collapsed without animation. Safe to call before layout. */
    fun collapseImmediate() {
        isExpanded = false
        if (!isLayoutReady) return
        cancelAnimations()
        applyProgress(0f)
    }

    fun isRailExpanded(): Boolean = isExpanded
    // endregion

    // region Animation -----------------------------------------------------
    private fun animateTo(targetProgress: Float, expandedAfter: Boolean) {
        isAnimating = true
        cancelAnimatorsOnly()

        // Ensure both views are visible while the transition plays.
        if (targetProgress > 0f) navigationRail.isVisible = true
        overlayView.isVisible = true

        val startProgress = currentProgress

        slideAnimator = ValueAnimator.ofFloat(startProgress, targetProgress).apply {
            duration = ANIM_DURATION_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val p = anim.animatedValue as Float
                applyProgress(p)
                stateListener?.onSlide(p)
            }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false

                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (cancelled) return
                    isAnimating = false
                    applyProgress(targetProgress)
                    isExpanded = expandedAfter
                    if (targetProgress == 0f) {
                        navigationRail.isVisible = false
                        overlayView.isVisible = false
                    }
                    if (expandedAfter) {
                        stateListener?.onExpanded()
                    } else {
                        stateListener?.onCollapsed()
                    }
                }
            })
        }

        overlayAnimator = ValueAnimator.ofFloat(overlayView.alpha, targetProgress).apply {
            duration = ANIM_DURATION_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { overlayView.alpha = it.animatedValue as Float }
        }

        fabAnimator = ValueAnimator.ofFloat(
            fabToggle.rotation,
            if (targetProgress > 0f) FAB_ROTATION_OPEN else FAB_ROTATION_CLOSED
        ).apply {
            duration = ANIM_DURATION_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { fabToggle.rotation = it.animatedValue as Float }
        }

        slideAnimator?.start()
        overlayAnimator?.start()
        fabAnimator?.start()
    }

    /**
     * Single source of truth. Given a progress in [0f, 1f], derives and applies
     * the rail translation, overlay alpha and FAB rotation consistently.
     */
    private fun applyProgress(progress: Float) {
        currentProgress = progress.coerceIn(0f, 1f)

        navigationRail.translationX = -railWidth * (1f - currentProgress)
        overlayView.alpha = currentProgress
        fabToggle.rotation =
            FAB_ROTATION_CLOSED + (FAB_ROTATION_OPEN - FAB_ROTATION_CLOSED) * currentProgress
    }

    private fun cancelAnimatorsOnly() {
        slideAnimator?.cancel(); slideAnimator = null
        overlayAnimator?.cancel(); overlayAnimator = null
        fabAnimator?.cancel(); fabAnimator = null
    }

    private fun cancelAnimations() {
        cancelAnimatorsOnly()
        isAnimating = false
    }
    // endregion

    // region Lifecycle -----------------------------------------------------
    /** Call from `Activity.onSaveInstanceState` / `Fragment.onSaveInstanceState`. */
    fun saveState(): Boolean = isExpanded

    /** Call from `onCreate`/`onViewStateRestored` with the previously saved value. */
    fun restoreState(expanded: Boolean) {
        if (expanded) expandImmediate() else collapseImmediate()
    }

    fun cleanup() {
        cancelAnimations()
        stateListener = null
    }
    // endregion
}
