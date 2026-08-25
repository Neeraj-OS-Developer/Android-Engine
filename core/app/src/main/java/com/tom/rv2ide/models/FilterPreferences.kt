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
package com.tom.rv2ide.models

/**
 * @author Mohammed-baqer-null @ https://github.com/Mohammed-baqer-null
 */

enum class FileFilter {
    ALL, FOLDERS_ONLY, FILES_ONLY
}

enum class ExtensionFilter {
    ALL, XML, TXT, PDF, IMAGE, VIDEO, AUDIO, APK, ZIP, CUSTOM
}

data class FilterPreferences(
    var fileFilter: FileFilter = FileFilter.ALL,
    var activeExtensions: MutableSet<ExtensionFilter> = mutableSetOf(ExtensionFilter.ALL),
    var customExtension: String? = null,
    var searchQuery: String = ""
)