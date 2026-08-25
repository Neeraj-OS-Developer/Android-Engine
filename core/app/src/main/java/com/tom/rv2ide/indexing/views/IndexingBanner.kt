/*
 *  This file is part of AndroidCodeStudio.
 *
 *  AndroidCodeStudio is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidCodeStudio is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidCodeStudio.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.tom.rv2ide.indexing.views

import android.animation.ObjectAnimator
import android.app.Activity
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.tom.rv2ide.databinding.LayoutIndexingBannerBinding

/*
 * Banner view to show indexing progress and custom notifications
 * @author Mohammed-baqer-null @ https://github.com/Mohammed-baqer-null
 */
class IndexingBanner private constructor(
  private val activity: Activity,
  private val initialTitle: String?,
  private val initialMessage: String?,
  @DrawableRes private val iconResId: Int?
) {

  private var binding: LayoutIndexingBannerBinding? = null
  private var isShowing = false

  // Backward compatibility: Constructor for existing usage
  constructor(activity: Activity) : this(activity, null, null, null)

  /**
   * Show the indexing banner with slide-down animation
   * @param autoHideAfterMillis Optional duration in milliseconds after which banner will auto-hide. Pass null to disable auto-hide.
   */
  fun show(autoHideAfterMillis: Long? = null) {
    if (isShowing) return

    val rootView = activity.findViewById<ViewGroup>(android.R.id.content)

    // Inflate layout with data binding
    binding = LayoutIndexingBannerBinding.inflate(LayoutInflater.from(activity)).apply {
      title = initialTitle ?: "Indexing project..."
      message = initialMessage ?: "Preparing..."
      
      // Set icon manually
      this@IndexingBanner.iconResId?.let {
        iconImageView.setImageResource(it)
        iconImageView.visibility = android.view.View.VISIBLE
      } ?: run {
        iconImageView.visibility = android.view.View.GONE
      }
    }

    // Add to root view with proper layout params
    val layoutParams = ViewGroup.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.WRAP_CONTENT
    )
    rootView.addView(binding?.root, 0, layoutParams)
    
    // Get status bar height and apply padding after view is added
    binding?.bannerContainer?.post {
      ViewCompat.getRootWindowInsets(rootView)?.let { insets ->
        val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
        binding?.bannerContainer?.setPadding(
          binding?.bannerContainer?.paddingLeft ?: 0,
          (binding?.bannerContainer?.paddingTop ?: 0) + statusBarHeight,
          binding?.bannerContainer?.paddingRight ?: 0,
          binding?.bannerContainer?.paddingBottom ?: 0
        )
      }
    }

    // Dynamic Island style animation - elastic drop down
    binding?.bannerContainer?.apply {
      // Start from collapsed (scaled down)
      scaleY = 0f
      scaleX = 0.95f
      alpha = 0f
      translationY = -100f
      pivotY = 0f
      pivotX = width / 2f
      
      post {
        // Animate scale Y (drop down effect)
        ObjectAnimator.ofFloat(this, "scaleY", 0f, 1.1f, 0.95f, 1f).apply {
          duration = 300
          interpolator = android.view.animation.OvershootInterpolator(1.2f)
          start()
        }
        
        // Animate scale X (slight width bounce)
        ObjectAnimator.ofFloat(this, "scaleX", 0.95f, 1.02f, 1f).apply {
          duration = 200
          interpolator = android.view.animation.DecelerateInterpolator()
          start()
        }
        
        // Animate translation (smooth drop)
        ObjectAnimator.ofFloat(this, "translationY", -50f, 10f, 0f).apply {
          duration = 250
          interpolator = android.view.animation.OvershootInterpolator(0.8f)
          start()
        }
        
        // Animate alpha (fade in)
        ObjectAnimator.ofFloat(this, "alpha", 0f, 1f).apply {
          duration = 200
          interpolator = android.view.animation.DecelerateInterpolator()
          start()
        }
      }
    }

    isShowing = true
    
    // Auto-hide if duration specified
    autoHideAfterMillis?.let { duration ->
      binding?.root?.postDelayed({
        hide()
      }, duration)
    }
  }

  /**
   * Update the subtitle text (progress message)
   */
  fun updateMessage(message: String) {
    binding?.message = message
  }

  /**
   * Update the title text
   */
  fun updateTitle(title: String) {
    binding?.title = title
  }

  /**
   * Update the icon
   */
  fun updateIcon(@DrawableRes iconResId: Int?) {
    binding?.iconImageView?.let { imageView ->
      if (iconResId != null && iconResId != 0) {
        imageView.setImageResource(iconResId)
        imageView.visibility = android.view.View.VISIBLE
      } else {
        imageView.visibility = android.view.View.GONE
      }
    }
  }

  /**
   * Hide the banner with slide-up animation
   */
  fun hide() {
    if (!isShowing || binding == null) return

    val banner = binding?.bannerContainer ?: return
    val rootView = activity.findViewById<ViewGroup>(android.R.id.content)

    banner.pivotY = 0f
    banner.pivotX = banner.width / 2f
    
    // Animate scale Y (collapse up)
    ObjectAnimator.ofFloat(banner, "scaleY", 1f, 1.05f, 0f).apply {
      duration = 200
      interpolator = android.view.animation.AnticipateInterpolator(1.5f)
      start()
    }
    
    // Animate scale X (slight squeeze)
    ObjectAnimator.ofFloat(banner, "scaleX", 1f, 0.98f, 0.95f).apply {
      duration = 200
      interpolator = android.view.animation.AccelerateInterpolator()
      start()
    }
    
    // Animate translation (pull up)
    ObjectAnimator.ofFloat(banner, "translationY", 0f, -20f, -60f).apply {
      duration = 200
      interpolator = android.view.animation.AccelerateInterpolator(1.2f)
      start()
    }
    
    // Animate alpha (fade out)
    ObjectAnimator.ofFloat(banner, "alpha", 1f, 0.7f, 0f).apply {
      duration = 200
      interpolator = android.view.animation.AccelerateInterpolator()
      start()
      
      // Remove from view hierarchy after animation
      banner.postDelayed({
        rootView.removeView(binding?.root)
        binding = null
        isShowing = false
      }, 500)
    }
  }

  /**
   * Check if banner is currently showing
   */
  fun isShowing(): Boolean = isShowing

  /**
   * Builder class for creating custom banners
   */
  class Builder {
    private var activity: Activity? = null
    private var title: String? = null
    private var message: String? = null
    @DrawableRes private var iconResId: Int? = null
    private var autoHideAfterMillis: Long? = null

    /**
     * Set the activity (will be retrieved automatically if called from Activity)
     */
    fun activity(activity: Activity) = apply {
      this.activity = activity
    }

    /**
     * Set the activity from a Fragment
     */
    fun fragment(fragment: Fragment) = apply {
      this.activity = fragment.requireActivity()
    }

    /**
     * Set the banner title
     */
    fun title(title: String) = apply {
      this.title = title
    }

    /**
     * Set the banner message
     */
    fun message(message: String) = apply {
      this.message = message
    }

    /**
     * Set the banner icon (optional)
     */
    fun icon(@DrawableRes iconResId: Int?) = apply {
      this.iconResId = iconResId
    }

    /**
     * Set auto-hide duration in milliseconds (optional)
     * @param millis Duration after which banner will automatically hide. Pass null to disable.
     */
    fun autoHide(millis: Long?) = apply {
      this.autoHideAfterMillis = millis
    }

    /**
     * Build and return the IndexingBanner instance
     */
    fun build(): IndexingBanner {
      requireNotNull(activity) { "Activity must be set using activity() or fragment()" }
      return IndexingBanner(activity!!, title, message, iconResId)
    }

    /**
     * Build and show the banner immediately
     */
    fun show(): IndexingBanner {
      return build().apply { show(autoHideAfterMillis) }
    }
  }

  companion object {
    /**
     * Create a new builder for custom banners
     */
    @JvmStatic
    fun builder() = Builder()

    /**
     * Quick show method with all parameters
     * @param autoHideAfterMillis Optional duration in milliseconds after which banner will auto-hide
     */
    @JvmStatic
    fun show(
      activity: Activity,
      title: String,
      message: String,
      @DrawableRes iconResId: Int? = null,
      autoHideAfterMillis: Long? = null
    ): IndexingBanner {
      return builder()
        .activity(activity)
        .title(title)
        .message(message)
        .icon(iconResId)
        .autoHide(autoHideAfterMillis)
        .show()
    }

    /**
     * Quick show method from Fragment
     * @param autoHideAfterMillis Optional duration in milliseconds after which banner will auto-hide
     */
    @JvmStatic
    fun show(
      fragment: Fragment,
      title: String,
      message: String,
      @DrawableRes iconResId: Int? = null,
      autoHideAfterMillis: Long? = null
    ): IndexingBanner {
      return builder()
        .fragment(fragment)
        .title(title)
        .message(message)
        .icon(iconResId)
        .autoHide(autoHideAfterMillis)
        .show()
    }
  }
}