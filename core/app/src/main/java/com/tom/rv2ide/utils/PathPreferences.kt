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
package com.tom.rv2ide.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * @author Mohammed-baqer-null @ https://github.com/Mohammed-baqer-null
 */

object PathPreferences {
    private const val PREF_NAME = "file_browser_prefs"
    private const val KEY_LAST_PATH_MAIN = "last_path_main"
    private const val KEY_LAST_PATH_DIALOG = "last_path_dialog"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun saveMainPath(context: Context, path: String) {
        getPrefs(context).edit().putString(KEY_LAST_PATH_MAIN, path).apply()
    }

    fun getMainPath(context: Context, defaultPath: String): String {
        return getPrefs(context).getString(KEY_LAST_PATH_MAIN, defaultPath) ?: defaultPath
    }

    fun saveDialogPath(context: Context, path: String) {
        getPrefs(context).edit().putString(KEY_LAST_PATH_DIALOG, path).apply()
    }

    fun getDialogPath(context: Context, defaultPath: String): String {
        return getPrefs(context).getString(KEY_LAST_PATH_DIALOG, defaultPath) ?: defaultPath
    }
}
