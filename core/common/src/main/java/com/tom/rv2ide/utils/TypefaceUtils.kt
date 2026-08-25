package com.tom.rv2ide.utils

import android.graphics.Typeface
import com.tom.rv2ide.app.BaseApplication
import com.tom.rv2ide.managers.PreferenceManager
import java.io.File

const val SELECTED_CUSTOM_FONT = "idepref_selected_custom_font"

private val prefs by lazy {
    PreferenceManager(BaseApplication.getBaseInstance())
}

fun quicksand(): Typeface =
    Typeface.createFromAsset(BaseApplication.getBaseInstance().assets, "fonts/quicksand.ttf")

fun jetbrainsMono(): Typeface =
    Typeface.createFromAsset(BaseApplication.getBaseInstance().assets, "fonts/jetbrains-mono.ttf")

fun josefinSans(): Typeface =
    Typeface.createFromAsset(BaseApplication.getBaseInstance().assets, "fonts/josefin-sans.ttf")

fun customOrJBMono(useCustom: Boolean = true): Typeface {
    if (!useCustom) return jetbrainsMono()

    val selectedFont = selectedCustomFont
    if (selectedFont.isNullOrEmpty()) return jetbrainsMono()

    val fontDir: File = Environment.ANDROIDIDE_UI
    val fontFile = File(fontDir, selectedFont)

    if (fontFile.exists() && fontFile.isFile && fontFile.length() > 0) {
        return Typeface.createFromFile(fontFile)
    }

    return jetbrainsMono()
}

fun getAvailableCustomFonts(): List<String> {
    val fontDir: File = Environment.ANDROIDIDE_UI

    return fontDir.listFiles { file ->
        val ext = file.extension.lowercase()
        ext == "ttf" || ext == "otf"
    }
        ?.map { it.name }
        ?.sorted()
        ?: emptyList()
}

var selectedCustomFont: String?
    get() = prefs.getString(SELECTED_CUSTOM_FONT, null)
    set(value) {
        prefs.putString(SELECTED_CUSTOM_FONT, value)
    }