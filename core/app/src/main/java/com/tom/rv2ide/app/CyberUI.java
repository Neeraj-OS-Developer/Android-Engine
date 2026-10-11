package com.ns.dev.cyberhostide;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

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
 *      CyberUI.applyEdgeToEdge(this);
 *
 *  Fullscreen Immersive (bars completely hidden):
 *      CyberUI.applyImmersiveFullscreen(this);
 *
 *    MANDATORY for Android 10/11 stability — also override:
 *
 *      @Override
 *      public void onWindowFocusChanged(boolean hasFocus) {
 *          super.onWindowFocusChanged(hasFocus);
 *          if (hasFocus) CyberUI.applyImmersiveFullscreen(this);
 *      }
 */
public final class CyberUI {

    private static final String TAG = "CyberUI";

    /** Android 10/11 को immersive mode सेट करने के लिए re-apply की delay. */
    private static final long IMMERSIVE_REAPPLY_DELAY_MS = 200L;

    private CyberUI() { /* no instances */ }

    // ═══════════════════════════════════════════════════════════════
    //  PUBLIC API — EDGE-TO-EDGE
    // ═══════════════════════════════════════════════════════════════

    public static void applyEdgeToEdge(@NonNull Activity activity) {
        applyEdgeToEdge(activity, false);
    }

    public static void applyEdgeToEdge(@NonNull Activity activity, boolean useDarkIcons) {
        Window window = activity.getWindow();
        if (window == null) return;

        applyModernEdgeToEdge(activity, window, useDarkIcons);
        applyLegacyEdgeToEdge(window);
        applyOemHacks(window);
        applyInsetsListener(activity);
    }

    // ═══════════════════════════════════════════════════════════════
    //  PUBLIC API — IMMERSIVE FULLSCREEN (bars hidden)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Status bar + Navigation bar दोनों को पूरी तरह छिपा देता है।
     * Immersive Sticky mode — user edge swipe करे तो bars थोड़ी देर
     * दिखेंगे, फिर अपने आप छिप जाएँगे।
     *
     * Android 10/11 पर timing quirk के कारण इसे onWindowFocusChanged
     * से भी call करना अनिवार्य है।
     */
    public static void applyImmersiveFullscreen(@NonNull Activity activity) {
        Window window = activity.getWindow();
        if (window == null) return;

        final View decor = window.getDecorView();

        // ── 1. Content को bars के पीछे extend करो ────────────────
        WindowCompat.setDecorFitsSystemWindows(window, false);

        // ── 2. Bars को 100% transparent रखो ─────────────────────
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.setNavigationBarDividerColor(Color.TRANSPARENT);
        }

        // ── 3. Contrast enforcement बंद करो (Android 10+) ────────
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }

        // ── 4. Cutout को cover करो ───────────────────────────────
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = window.getAttributes();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                lp.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            } else {
                lp.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            }
            window.setAttributes(lp);
        }

        // ── 5. Modern API (30+) से bars छिपाओ ────────────────────
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, decor);

        if (controller != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            controller.hide(WindowInsetsCompat.Type.systemBars());
            controller.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }

        // ── 6. Legacy flags — Android 10/11 के लिए CRITICAL ──────
        //     WindowInsetsControllerCompat अकेले Android 10/11 पर
        //     reliable नहीं है। इन flags के बिना bars पहली touch पर
        //     वापस आ जाते हैं और lock हो जाते हैं।
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                flags |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            }
            decor.setSystemUiVisibility(flags);
        }

        // ── 7. Content padding शून्य करो (bars छिपे हैं) ─────────
        View content = activity.findViewById(android.R.id.content);
        if (content != null) {
            content.setPadding(0, 0, 0, 0);
        }

        // ── 8. Android 10/11 पर deferred re-apply ────────────────
        //     System bars first frame पर re-appear हो सकते हैं।
        //     200ms बाद एक और बार apply करने से यह lock हो जाता है।
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            scheduleDeferredImmersiveReapply(activity);
        }
    }

    /**
     * Immersive mode से बाहर निकलो — bars वापस दिखाओ।
     */
    public static void exitImmersiveFullscreen(@NonNull Activity activity) {
        Window window = activity.getWindow();
        if (window == null) return;

        View decor = window.getDecorView();

        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, decor);
        if (controller != null) {
            controller.show(WindowInsetsCompat.Type.systemBars());
        }

        // Legacy flags clear करो
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  ANDROID 10/11 DEFERRED RE-APPLY (जादुई fix)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Android 10/11 पर system bars first draw के बाद एक बार
     * re-appear होते हैं। 200ms बाद दोबारा apply करने से वे
     * properly lock हो जाते हैं और user touch पर भी नहीं आते।
     */
    private static void scheduleDeferredImmersiveReapply(@NonNull final Activity activity) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (activity.isFinishing()
                    || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1
                        && activity.isDestroyed())) {
                return;
            }

            Window window = activity.getWindow();
            if (window == null) return;
            View decor = window.getDecorView();

            WindowInsetsControllerCompat controller =
                    WindowCompat.getInsetsController(window, decor);
            if (controller != null) {
                controller.hide(WindowInsetsCompat.Type.systemBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }

            // Legacy flags दोबारा set करो — Android 10 की lock fix
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                flags |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            }
            decor.setSystemUiVisibility(flags);
        }, IMMERSIVE_REAPPLY_DELAY_MS);
    }

    // ═══════════════════════════════════════════════════════════════
    //  MODERN EDGE-TO-EDGE PATH (API 21+ → 35+)
    // ═══════════════════════════════════════════════════════════════

    private static void applyModernEdgeToEdge(@NonNull Activity activity,
                                              @NonNull Window window,
                                              boolean useDarkIcons) {
        WindowCompat.setDecorFitsSystemWindows(window, false);

        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.setNavigationBarDividerColor(Color.TRANSPARENT);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = window.getAttributes();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                lp.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            } else {
                lp.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            }
            window.setAttributes(lp);
        }

        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, window.getDecorView());
        if (controller != null) {
            controller.setAppearanceLightStatusBars(useDarkIcons);
            controller.setAppearanceLightNavigationBars(useDarkIcons);
            controller.show(WindowInsetsCompat.Type.systemBars());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  LEGACY EDGE-TO-EDGE PATH (API 1 – 20)
    // ═══════════════════════════════════════════════════════════════

    @SuppressWarnings("deprecation")
    private static void applyLegacyEdgeToEdge(@NonNull Window window) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            return;
        }

        View decor = window.getDecorView();
        int flags = 0;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            flags |= View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            flags |= View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        }
        decor.setSystemUiVisibility(flags);
    }

    // ═══════════════════════════════════════════════════════════════
    //  OEM-SPECIFIC HACKS
    // ═══════════════════════════════════════════════════════════════

    private static void applyOemHacks(@NonNull Window window) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return;

        String manufacturer = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.toLowerCase();
        String brand = Build.BRAND == null ? "" : Build.BRAND.toLowerCase();

        if (manufacturer.contains("xiaomi") || brand.contains("xiaomi")
                || brand.contains("redmi") || brand.contains("poco")) {
            disableMiuiStatusBarShadow(window);
        }
        if (manufacturer.contains("huawei") || brand.contains("huawei")
                || brand.contains("honor")) {
            disableEmuiStatusBarBackground(window);
        }
        if (manufacturer.contains("oppo") || manufacturer.contains("oneplus")
                || manufacturer.contains("realme")) {
            disableColorOsScrim(window);
        }
    }

    private static void disableMiuiStatusBarShadow(@NonNull Window window) {
        try {
            Class<?> lpClass = Class.forName("android.view.MiuiWindowManager$LayoutParams");
            Field flagField = lpClass.getField("EXTRA_FLAG_STATUS_BAR_SHADOW");
            int flag = flagField.getInt(null);
            Method setExtraFlags = Window.class.getMethod(
                    "setExtraFlags", int.class, int.class);
            setExtraFlags.invoke(window, 0, flag);
        } catch (Throwable t) {
            Log.d(TAG, "MIUI hack skipped: " + t.getMessage());
        }
    }

    private static void disableEmuiStatusBarBackground(@NonNull Window window) {
        try {
            Method setStatusBarColor = Window.class.getMethod(
                    "setStatusBarColor", int.class);
            setStatusBarColor.invoke(window, Color.TRANSPARENT);
        } catch (Throwable t) {
            Log.d(TAG, "EMUI hack skipped: " + t.getMessage());
        }
    }

    private static void disableColorOsScrim(@NonNull Window window) {
        try {
            Method m = Window.class.getMethod(
                    "setNavigationBarContrastEnforced", boolean.class);
            m.invoke(window, false);
        } catch (Throwable ignored) { /* no-op */ }
    }

    // ═══════════════════════════════════════════════════════════════
    //  INSETS LISTENER (safe area padding)
    // ═══════════════════════════════════════════════════════════════

    private static void applyInsetsListener(@NonNull Activity activity) {
        final View content = activity.findViewById(android.R.id.content);
        if (content == null) return;

        ViewCompat.setOnApplyWindowInsetsListener(content, (v, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars());
            Insets cutout = windowInsets.getInsets(
                    WindowInsetsCompat.Type.displayCutout());

            int left   = Math.max(systemBars.left,   cutout.left);
            int top    = Math.max(systemBars.top,    cutout.top);
            int right  = Math.max(systemBars.right,  cutout.right);
            int bottom = Math.max(systemBars.bottom, cutout.bottom);

            v.setPadding(left, top, right, bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        ViewCompat.requestApplyInsets(content);
    }
}
