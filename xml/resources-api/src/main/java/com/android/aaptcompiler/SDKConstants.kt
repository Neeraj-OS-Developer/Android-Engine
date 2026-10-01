package com.android.aaptcompiler

/**
 * Centralised Android SDK knowledge base.
 *
 * Covers every public API level from Android 1.0 (API 1) to Android 16 (API 36).
 *
 * Provides:
 *  - Raw constants (Short, for AAPT2 compatibility).
 *  - Reverse lookup (API → codename / version / letter).
 *  - Safe helpers (auto-clamp, nearest-known, validation).
 *
 * This object NEVER throws. Every lookup has a graceful fallback.
 */
internal object SDKConstants {

    // ------------------------------------------------------------------
    //  RAW API LEVEL CONSTANTS  (Android 1.0 → 16)
    // ------------------------------------------------------------------
    const val SDK_BASE                 = 1.toShort()   // Android 1.0
    const val SDK_BASE_1_1             = 2.toShort()   // Android 1.1
    const val SDK_CUPCAKE              = 3.toShort()   // Android 1.5
    const val SDK_DONUT                = 4.toShort()   // Android 1.6
    const val SDK_ECLAIR               = 5.toShort()   // Android 2.0
    const val SDK_ECLAIR_0_1           = 6.toShort()   // Android 2.0.1
    const val SDK_ECLAIR_MR1           = 7.toShort()   // Android 2.1
    const val SDK_FROYO                = 8.toShort()   // Android 2.2
    const val SDK_GINGERBREAD          = 9.toShort()   // Android 2.3
    const val SDK_GINGERBREAD_MR1      = 10.toShort()  // Android 2.3.3
    const val SDK_HONEYCOMB            = 11.toShort()  // Android 3.0
    const val SDK_HONEYCOMB_MR1        = 12.toShort()  // Android 3.1
    const val SDK_HONEYCOMB_MR2        = 13.toShort()  // Android 3.2
    const val SDK_ICE_CREAM_SANDWICH   = 14.toShort()  // Android 4.0
    const val SDK_ICE_CREAM_SANDWICH_MR1 = 15.toShort()// Android 4.0.3
    const val SDK_JELLY_BEAN           = 16.toShort()  // Android 4.1
    const val SDK_JELLY_BEAN_MR1       = 17.toShort()  // Android 4.2
    const val SDK_JELLY_BEAN_MR2       = 18.toShort()  // Android 4.3
    const val SDK_KITKAT               = 19.toShort()  // Android 4.4
    const val SDK_KITKAT_WATCH         = 20.toShort()  // Android 4.4W
    const val SDK_LOLLIPOP             = 21.toShort()  // Android 5.0
    const val SDK_LOLLIPOP_MR1         = 22.toShort()  // Android 5.1
    const val SDK_MARSHMALLOW          = 23.toShort()  // Android 6.0
    const val SDK_NOUGAT               = 24.toShort()  // Android 7.0
    const val SDK_NOUGAT_MR1           = 25.toShort()  // Android 7.1
    const val SDK_O                    = 26.toShort()  // Android 8.0 (Oreo)
    const val SDK_O_MR1                = 27.toShort()  // Android 8.1
    const val SDK_P                    = 28.toShort()  // Android 9 (Pie)
    const val SDK_Q                    = 29.toShort()  // Android 10
    const val SDK_R                    = 30.toShort()  // Android 11
    const val SDK_S                    = 31.toShort()  // Android 12
    const val SDK_S_V2                 = 32.toShort()  // Android 12L
    const val SDK_TIRAMISU             = 33.toShort()  // Android 13
    const val SDK_U                    = 34.toShort()  // Android 14 (Upside Down Cake)
    const val SDK_V                    = 35.toShort()  // Android 15 (Vanilla Ice Cream)
    const val SDK_W                    = 36.toShort()  // Android 16 (Baklava)

    // ------------------------------------------------------------------
    //  BOUNDS
    // ------------------------------------------------------------------
    val MIN_API: Short get() = SDK_BASE
    val MAX_API: Short get() = SDK_W
    val VALID_RANGE: IntRange get() = SDK_BASE.toInt()..SDK_W.toInt()

    // ------------------------------------------------------------------
    //  KNOWLEDGE TABLES  (API → metadata)
    // ------------------------------------------------------------------
    private val CODENAME_MAP: Map<Int, String> = mapOf(
        1  to "Base",
        2  to "Base 1.1",
        3  to "Cupcake",
        4  to "Donut",
        5  to "Eclair",
        6  to "Eclair 0.1",
        7  to "Eclair MR1",
        8  to "Froyo",
        9  to "Gingerbread",
        10 to "Gingerbread MR1",
        11 to "Honeycomb",
        12 to "Honeycomb MR1",
        13 to "Honeycomb MR2",
        14 to "Ice Cream Sandwich",
        15 to "Ice Cream Sandwich MR1",
        16 to "Jelly Bean",
        17 to "Jelly Bean MR1",
        18 to "Jelly Bean MR2",
        19 to "KitKat",
        20 to "KitKat Watch",
        21 to "Lollipop",
        22 to "Lollipop MR1",
        23 to "Marshmallow",
        24 to "Nougat",
        25 to "Nougat MR1",
        26 to "Oreo",
        27 to "Oreo MR1",
        28 to "Pie",
        29 to "Android 10 (Q)",
        30 to "Android 11 (R)",
        31 to "Android 12 (S)",
        32 to "Android 12L (S_V2)",
        33 to "Tiramisu",
        34 to "Upside Down Cake",
        35 to "Vanilla Ice Cream",
        36 to "Baklava"
    )

    private val VERSION_MAP: Map<Int, String> = mapOf(
        1  to "1.0",      2  to "1.1",      3  to "1.5",
        4  to "1.6",      5  to "2.0",      6  to "2.0.1",
        7  to "2.1",      8  to "2.2",      9  to "2.3",
        10 to "2.3.3",    11 to "3.0",      12 to "3.1",
        13 to "3.2",      14 to "4.0",      15 to "4.0.3",
        16 to "4.1",      17 to "4.2",      18 to "4.3",
        19 to "4.4",      20 to "4.4W",     21 to "5.0",
        22 to "5.1",      23 to "6.0",      24 to "7.0",
        25 to "7.1",      26 to "8.0",      27 to "8.1",
        28 to "9",        29 to "10",       30 to "11",
        31 to "12",       32 to "12L",      33 to "13",
        34 to "14",       35 to "15",       36 to "16"
    )

    // ------------------------------------------------------------------
    //  VALIDATION
    // ------------------------------------------------------------------
    fun isValidApi(api: Int): Boolean = api in VALID_RANGE

    /** Auto-fix: clamp any input into the valid range. Never throws. */
    fun coerce(api: Int): Int = api.coerceIn(VALID_RANGE.first, VALID_RANGE.last)

    /** Nearest known API level to the given value (for fuzzy input). */
    fun nearest(api: Int): Int {
        if (isValidApi(api)) return api
        if (api < VALID_RANGE.first) return VALID_RANGE.first
        return VALID_RANGE.last
    }

    // ------------------------------------------------------------------
    //  LOOKUPS  (never throw, always return a readable string)
    // ------------------------------------------------------------------
    fun codenameOf(api: Int): String =
        CODENAME_MAP[api] ?: "Unknown codename (API $api)"

    fun versionOf(api: Int): String =
        VERSION_MAP[api]?.let { "Android $it" } ?: "Unknown Android (API $api)"

    /** Single-line human readable summary, e.g. "Android 14 (Upside Down Cake) — API 34". */
    fun describe(api: Int): String {
        val safe = coerce(api)
        val corrected = if (safe != api) " [coerced from $api]" else ""
        return "${versionOf(safe)} (${codenameOf(safe)}) — API $safe$corrected"
    }

    // ------------------------------------------------------------------
    //  COMPARISON HELPERS  (semantic sugar over `>=`)
    // ------------------------------------------------------------------
    fun isAtLeast(current: Int, required: Int): Boolean = current >= required
    fun isBelow(current: Int, required: Int): Boolean = current < required
    fun isBetween(current: Int, min: Int, max: Int): Boolean = current in min..max
    fun isExactly(current: Int, target: Int): Boolean = current == target

    /** True if the given API is inside AAPT's historic "interesting" set. */
    fun isLandmark(api: Int): Boolean = api in LANDMARKS

    private val LANDMARKS: Set<Int> = setOf(
        SDK_DONUT.toInt(), SDK_FROYO.toInt(), SDK_HONEYCOMB_MR2.toInt(),
        SDK_LOLLIPOP.toInt(), SDK_MARSHMALLOW.toInt(), SDK_O.toInt(),
        SDK_U.toInt(), SDK_V.toInt(), SDK_W.toInt()
    )

    // ------------------------------------------------------------------
    //  DEBUG / DIAGNOSTICS
    // ------------------------------------------------------------------
    /** Full dump of the known API table (useful for logs / tests). */
    fun dumpAll(): List<String> =
        VALID_RANGE.map { describe(it) }
}
