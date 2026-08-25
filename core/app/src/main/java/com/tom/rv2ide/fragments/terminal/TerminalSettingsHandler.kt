package com.tom.rv2ide.fragments.terminal

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.view.View
import android.widget.TextView
import com.google.android.material.slider.Slider
import com.google.android.material.materialswitch.MaterialSwitch
import com.termux.view.TerminalView

class TerminalSettingsHandler(
    private val context: Context,
    private val prefs: SharedPreferences,
    private val terminalView: TerminalView?,
    private val onExtraKeysVisibilityChanged: (Boolean) -> Unit
) {
    
    companion object {
        const val PREF_TEXT_SIZE = "terminal_text_size"
        const val PREF_EXTRA_KEYS_VISIBLE = "extra_keys_visible"
        const val DEFAULT_TEXT_SIZE = 14
        private const val TAG = "TerminalSettings"
    }
    
    fun setupSettings(rootView: View, savedTextSize: Int) {
        setupTextSizeSlider(rootView, savedTextSize)
        setupExtraKeysSwitch(rootView)
    }
    
    private fun setupTextSizeSlider(rootView: View, savedTextSize: Int) {
        val textSizeSlider = rootView.findViewById<Slider>(com.tom.rv2ide.R.id.text_size_slider)
        val textSizeValue = rootView.findViewById<TextView>(com.tom.rv2ide.R.id.text_size_value)
        
        textSizeSlider?.apply {
            value = savedTextSize.toFloat()
            textSizeValue?.text = savedTextSize.toString()
            
            addOnChangeListener { _, value, fromUser ->
                val newSize = value.toInt()
                textSizeValue?.text = newSize.toString()
                if (fromUser) {
                    terminalView?.setTextSize(newSize)
                    prefs.edit().putInt(PREF_TEXT_SIZE, newSize).apply()
                }
            }
        }
    }
    
    private fun setupExtraKeysSwitch(rootView: View) {
        val extraKeysSwitch = rootView.findViewById<MaterialSwitch>(com.tom.rv2ide.R.id.hide_extra_keys)
        val isExtraKeysVisible = prefs.getBoolean(PREF_EXTRA_KEYS_VISIBLE, true)
        
        Log.d(TAG, "setupExtraKeysSwitch - isExtraKeysVisible from prefs: $isExtraKeysVisible")
        
        extraKeysSwitch?.apply {
            // Remove the listener first to prevent triggering during setup
            setOnCheckedChangeListener(null)
            
            // Switch label says "Hide extra keys toolbar"
            // When checked = hide (keys NOT visible)
            // When unchecked = show (keys visible)
            val shouldBeChecked = !isExtraKeysVisible
            Log.d(TAG, "Setting switch checked state to: $shouldBeChecked")
            isChecked = shouldBeChecked
            
            // Now set the listener
            setOnCheckedChangeListener { _, isChecked ->
                Log.d(TAG, "Switch changed - isChecked: $isChecked")
                // isChecked = true means "hide" = keys should NOT be visible
                val shouldShowKeys = !isChecked
                Log.d(TAG, "shouldShowKeys: $shouldShowKeys, calling callback")
                prefs.edit().putBoolean(PREF_EXTRA_KEYS_VISIBLE, shouldShowKeys).apply()
                onExtraKeysVisibilityChanged(shouldShowKeys)
            }
        }
        
        if (extraKeysSwitch == null) {
            Log.e(TAG, "extraKeysSwitch is NULL!")
        }
    }
    
    fun getTextSize(): Int = prefs.getInt(PREF_TEXT_SIZE, DEFAULT_TEXT_SIZE)
    
    fun getExtraKeysVisibility(): Boolean {
        val visible = prefs.getBoolean(PREF_EXTRA_KEYS_VISIBLE, true)
        Log.d(TAG, "getExtraKeysVisibility returning: $visible")
        return visible
    }
}