/*
 *  This file is part of LogWire.
 *
 *  LogWire is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  LogWire is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with LogWire.  If not, see <https://www.gnu.org/licenses/>.
 */
 
package io.github.mohammedbaqernull.logger.logwire

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri

/**
 * LogWire automatic initializer using ContentProvider.
 * 
 * This ContentProvider automatically initializes LogWire when the application starts,
 * without requiring manual initialization code in the Application class or activities.
 * 
 * @author Mohammed-baqer-null @ https://github.com/Mohammed-baqer-null
 */
class LogWireInitializer : ContentProvider() {
    
    /**
     * Initializes LogWire when the ContentProvider is created.
     * 
     * This method is called automatically by the Android system during app startup.
     * It initializes LogWire for all apps EXCEPT the logger app itself to prevent
     * self-logging loops.
     * 
     * @return true to indicate successful initialization
     */
    override fun onCreate(): Boolean {
        context?.let { ctx ->
            // Don't initialize LogWire in the main logger app itself
            // to prevent the logger from logging its own logs
            if (ctx.packageName != io.github.mohammedbaqernull.logger.configurations.PACKAGE_NAME) {
                LogWire.initialize(ctx)
            }
        }
        return true
    }
    
    // ContentProvider interface methods - not used, return null/0
    
    override fun query(
        uri: Uri, 
        projection: Array<out String>?, 
        selection: String?, 
        selectionArgs: Array<out String>?, 
        sortOrder: String?
    ): Cursor? = null
    
    override fun getType(uri: Uri): String? = null
    
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    
    override fun delete(
        uri: Uri, 
        selection: String?, 
        selectionArgs: Array<out String>?
    ): Int = 0
    
    override fun update(
        uri: Uri, 
        values: ContentValues?, 
        selection: String?, 
        selectionArgs: Array<out String>?
    ): Int = 0
}
