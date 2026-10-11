package com.tom.rv2ide.app

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * CyberUI — Universal Edge-to-Edge + Immersive Fullscreen Utility.
 *
 * Supports API 1 → API 35+ across AOSP, Samsung One UI, Xiaomi
 * HyperOS/MIUI, OPPO/Realme ColorOS, Vivo Funtouch, OnePlus
 * OxygenOS, Huawei EMUI, Transsion HiOS/XOS.
 *
 * ─────────────────────────────────────────────────────────────
 *  USAGE
 * ─────────────────────────────────────────────────────────────
 *
 *  Edge-to-Edge (bars visible, transparent):
 *      CyberUI.applyEdgeToEdge(this)
 *
 *  Fullscreen Immersive (bars completely hidden):
 *      CyberUI.applyImmersiveFullscreen(this)
 */
@Suppress("DEPRECATION")
object CyberUI {

    private const val TAG = "CyberUI"

    /** Android 10/11 को immersive mode सेट करने के लिए re-apply की delay. */
    private const val IMMERSIVE_REAPPLY_DELAY_MS = 200L

    // ═══════════════════════════════════════════════════════════════
    //  PUBLIC API — EDGE-TO-EDGE
    // ═══════════════════════════════════════════════════════════════

    @JvmStatic
    fun applyEdgeToEdge(activity: Activity) {
        applyEdgeToEdge(activity, false)
    }

    @JvmStatic
    fun applyEdgeToEdge(activity: Activity, useDarkIcons: Boolean) {
        val window = activity.window ?: return

        applyModernEdgeToEdge(activity, window, useDarkIcons)
        applyLegacyEdgeToEdge(window)
        applyOemHacks(window)
        applyInsetsListener(activity)
    }

    // ═══════════════════════════════════════════════════════════════
    //  PUBLIC API — IMMERSIVE FULLSCREEN (bars hidden)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Status bar + Navigation bar दोनों को पूरी तरह छिपा देता है।
     * Immersive Sticky mode — user edge swipe करे तो bars थोड़ी देर
     * दिखेंगे, फिर अपने आप छिप जाएँगे।
     */
    @JvmStatic
    fun applyImmersiveFullscreen(activity: Activity) {
        val window = activity.window ?: return
        val decor = window.decorView

        // ── 1. Content को bars के पीछे extend करो ────────────────
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // ── 2. Bars को 100% transparent रखो ─────────────────────
        window.clearFlags(
            WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.navigationBarDividerColor = Color.TRANSPARENT
        }

        // ── 3. Contrast enforcement बंद करो (Android 10+) ────────
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }

        // ── 4. Cutout को cover करो ───────────────────────────────
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val lp = window.attributes
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else {
                lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            window.attributes = lp
        }

        // ── 5. Modern API (30+) से bars छिपाओ ────────────────────
        val controller: WindowInsetsControllerCompat? =
            WindowCompat.getInsetsController(window, decor)

        if (controller != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // ── 6. Legacy flags — Android 10/11 के लिए CRITICAL ──────
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            var flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                flags = flags or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            }
            decor.systemUiVisibility = flags
        }

        // ── 7. Content padding शून्य करो (bars छिपे हैं) ─────────
        val content = activity.findViewById<View>(android.R.id.content)
        content?.setPadding(0, 0, 0, 0)

        // ── 8. Android 10/11 पर deferred re-apply ────────────────
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S
        ) {
            scheduleDeferredImmersiveReapply(activity)
        }
    }

    /**
     * Immersive mode से बाहर निकलो — bars वापस दिखाओ।
     */
    @JvmStatic
    fun exitImmersiveFullscreen(activity: Activity) {
        val window = activity.window ?: return
        val decor = window.decorView

        val controller: WindowInsetsControllerCompat? =
            WindowCompat.getInsetsController(window, decor)
        controller?.show(WindowInsetsCompat.Type.systemBars())

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            decor.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  ANDROID 10/11 DEFERRED RE-APPLY (जादुई fix)
    // ═══════════════════════════════════════════════════════════════

    private fun scheduleDeferredImmersiveReapply(activity: Activity) {
        Handler(Looper.getMainLooper()).postDelayed({
            if (activity.isFinishing ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 &&
                    activity.isDestroyed)
            ) {
                return@postDelayed
            }

            val window = activity.window ?: return@postDelayed
            val decor = window.decorView

            val controller: WindowInsetsControllerCompat? =
                WindowCompat.getInsetsController(window, decor)
            if (controller != null) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }

            var flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                flags = flags or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            }
            decor.systemUiVisibility = flags
        }, IMMERSIVE_REAPPLY_DELAY_MS)
    }

    // ═══════════════════════════════════════════════════════════════
    //  MODERN EDGE-TO-EDGE PATH (API 21+ → 35+)
    // ═══════════════════════════════════════════════════════════════

    @Suppress("UNUSED_PARAMETER")
    private fun applyModernEdgeToEdge(
        activity: Activity,
        window: Window,
        useDarkIcons: Boolean
    ) {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        window.clearFlags(
            WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.navigationBarDividerColor = Color.TRANSPARENT
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val lp = window.attributes
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else {
                lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            window.attributes = lp
        }

        val controller: WindowInsetsControllerCompat? =
            WindowCompat.getInsetsController(window, window.decorView)

        if (controller != null) {
            controller.isAppearanceLightStatusBars = useDarkIcons
            controller.isAppearanceLightNavigationBars = useDarkIcons
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  LEGACY EDGE-TO-EDGE PATH (API 1 – 20)
    // ═══════════════════════════════════════════════════════════════

    private fun applyLegacyEdgeToEdge(window: Window) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            return
        }

        val decor = window.decorView
        var flags = 0

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            flags = flags or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            flags = flags or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
        decor.systemUiVisibility = flags
    }

    // ═══════════════════════════════════════════════════════════════
    //  OEM-SPECIFIC HACKS
    // ═══════════════════════════════════════════════════════════════

    private fun applyOemHacks(window: Window) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return

        val manufacturer = Build.MANUFACTURER?.lowercase() ?: ""
        val brand = Build.BRAND?.lowercase() ?: ""

        if (manufacturer.contains("xiaomi") || brand.contains("xiaomi") ||
            brand.contains("redmi") || brand.contains("poco")
        ) {
            disableMiuiStatusBarShadow(window)
        }

        if (manufacturer.contains("huawei") || brand.contains("huawei") ||
            brand.contains("honor")
        ) {
            disableEmuiStatusBarBackground(window)
        }

        if (manufacturer.contains("oppo") || manufacturer.contains("oneplus") ||
            manufacturer.contains("realme")
        ) {
            disableColorOsScrim(window)
        }
    }

    private fun disableMiuiStatusBarShadow(window: Window) {
        try {
            val lpClass = Class.forName("android.view.MiuiWindowManager\$LayoutParams")
            val flagField = lpClass.getField("EXTRA_FLAG_STATUS_BAR_SHADOW")
            val flag = flagField.getInt(null)

            val setExtraFlags = Window::class.java.getMethod(
                "setExtraFlags",
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!
            )
            setExtraFlags.invoke(window, 0, flag)
        } catch (t: Throwable) {
            Log.d(TAG, "MIUI hack skipped: ${t.message}")
        }
    }

    private fun disableEmuiStatusBarBackground(window: Window) {
        try {
            val setStatusBarColor = Window::class.java.getMethod(
                "setStatusBarColor",
                Int::class.javaPrimitiveType!!
            )
            setStatusBarColor.invoke(window, Color.TRANSPARENT)
        } catch (t: Throwable) {
            Log.d(TAG, "EMUI hack skipped: ${t.message}")
        }
    }

    private fun disableColorOsScrim(window: Window) {
        try {
            val m = Window::class.java.getMethod(
                "setNavigationBarContrastEnforced",
                Boolean::class.javaPrimitiveType!!
            )
            m.invoke(window, false)
        } catch (ignored: Throwable) {
            // no-op
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  INSETS LISTENER (safe area padding)
    // ═══════════════════════════════════════════════════════════════

    private fun applyInsetsListener(activity: Activity) {
        val content = activity.findViewById<View>(android.R.id.content) ?: return

        ViewCompat.setOnApplyWindowInsetsListener(content) { v, windowInsets ->
            val systemBars: Insets =
                windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val cutout: Insets =
                windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout())

            val left = maxOf(systemBars.left, cutout.left)
            val top = maxOf(systemBars.top, cutout.top)
            val right = maxOf(systemBars.right, cutout.right)
            val bottom = maxOf(systemBars.bottom, cutout.bottom)

            v.setPadding(left, top, right, bottom)
            WindowInsetsCompat.CONSUMED
        }

        ViewCompat.requestApplyInsets(content)
    }
}
