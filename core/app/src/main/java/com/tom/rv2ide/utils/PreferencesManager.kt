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
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * @author Mohammed-baqer-null @ https://github.com/Mohammed-baqer-null
 */

class PreferencesManager(context: Context) {
    
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val encryptedPrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "git_credentials",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    private val normalPrefs: SharedPreferences = context.getSharedPreferences("git_config", Context.MODE_PRIVATE)
    
    // Git Config
    fun setGitUserName(name: String) {
        normalPrefs.edit().putString("git_user_name", name).apply()
    }
    
    fun getGitUserName(): String {
        return normalPrefs.getString("git_user_name", "User") ?: "User"
    }
    
    fun setGitUserEmail(email: String) {
        normalPrefs.edit().putString("git_user_email", email).apply()
    }
    
    fun getGitUserEmail(): String {
        return normalPrefs.getString("git_user_email", "user@example.com") ?: "user@example.com"
    }
    
    // Credentials
    fun setRememberCredentials(remember: Boolean) {
        normalPrefs.edit().putBoolean("remember_credentials", remember).apply()
    }
    
    fun shouldRememberCredentials(): Boolean {
        return normalPrefs.getBoolean("remember_credentials", false)
    }
    
    fun saveCredentials(username: String, password: String) {
        if (shouldRememberCredentials()) {
            encryptedPrefs.edit()
                .putString("username", username)
                .putString("password", password)
                .apply()
        }
    }
    
    fun getUsername(): String? {
        return if (shouldRememberCredentials()) {
            encryptedPrefs.getString("username", null)
        } else null
    }
    
    fun getPassword(): String? {
        return if (shouldRememberCredentials()) {
            encryptedPrefs.getString("password", null)
        } else null
    }
    
    fun clearCredentials() {
        encryptedPrefs.edit()
            .remove("username")
            .remove("password")
            .apply()
    }
}
