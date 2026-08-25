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

import com.tom.rv2ide.R

/**
 * @author Mohammed-baqer-null @ https://github.com/Mohammed-baqer-null
 */

object FileIconManager {
    
    private val iconMap = mapOf(
        // Documents
        "properties" to R.drawable.ic_file_properties,
        "prop" to R.drawable.ic_file_properties,
        
        // Code files
        "xml" to R.drawable.ic_file_xml,
        "json" to R.drawable.ic_file_json,
        "kt" to R.drawable.ic_file_kotlin,
        "kts" to R.drawable.ic_file_kts,
        "java" to R.drawable.ic_file_java,
        "py" to R.drawable.ic_file_python,
        "cpp" to R.drawable.ic_file_cpp,
        "c" to R.drawable.ic_file_c,
        "gradle" to R.drawable.ic_file_gradle,
        "md" to R.drawable.ic_file_markdown,
        "sql" to R.drawable.ic_file_svg,
        "sh" to R.drawable.ic_file_svg,
        
        // Images
        "jpg" to R.drawable.ic_file_image,
        "jpeg" to R.drawable.ic_file_image,
        "png" to R.drawable.ic_file_image,
        "gif" to R.drawable.ic_file_image,
        "bmp" to R.drawable.ic_file_image,
        "webp" to R.drawable.ic_file_image,
        "svg" to R.drawable.ic_file_svg,
        "ico" to R.drawable.ic_file_image,
        
        // Videos
        "mp4" to R.drawable.ic_file_video,
        "mkv" to R.drawable.ic_file_video,
        "avi" to R.drawable.ic_file_video,
        "mov" to R.drawable.ic_file_video,
        "wmv" to R.drawable.ic_file_video,
        "flv" to R.drawable.ic_file_video,
        "webm" to R.drawable.ic_file_video,
        "m4v" to R.drawable.ic_file_video,
        
        // Audio
        "mp3" to R.drawable.ic_file_audio,
        "wav" to R.drawable.ic_file_audio,
        "m4a" to R.drawable.ic_file_audio,
        "flac" to R.drawable.ic_file_audio,
        "aac" to R.drawable.ic_file_audio,
        "ogg" to R.drawable.ic_file_audio,
        "wma" to R.drawable.ic_file_audio,
        
        // Archives
        "zip" to R.drawable.ic_file_archive,
        "rar" to R.drawable.ic_file_archive,
        "7z" to R.drawable.ic_file_archive,
        "tar" to R.drawable.ic_file_archive,
        "gz" to R.drawable.ic_file_archive,
        "bz2" to R.drawable.ic_file_archive,
        
        // Android specific
        "apk" to R.drawable.ic_file_apk,
        "aab" to R.drawable.ic_file_apk,
        "dex" to R.drawable.ic_file_dex,

    )
    
    /**
     * Get the icon resource for a file based on its extension
     * @param fileName The name of the file
     * @param isDirectory Whether the item is a directory
     * @return The drawable resource ID for the icon
     */
    fun getIconForFile(fileName: String, isDirectory: Boolean): Int {
        if (isDirectory) {
            return R.drawable.ic_folder
        }
        
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return iconMap[extension] ?: R.drawable.ic_file_default
    }
    
    /**
     * Check if a custom icon exists for the given extension
     * @param extension The file extension (without the dot)
     * @return true if a custom icon exists, false otherwise
     */
    fun hasCustomIcon(extension: String): Boolean {
        return iconMap.containsKey(extension.lowercase())
    }
    
    /**
     * Get all supported extensions
     * @return Set of all extensions that have custom icons
     */
    fun getSupportedExtensions(): Set<String> {
        return iconMap.keys
    }
}
