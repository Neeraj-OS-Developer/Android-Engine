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
 * @author
 * Mohammed-baqer-null @ https://github.com/Mohammed-baqer-null
 */

package com.tom.rv2ide.projectdata.parsers

import java.io.File

/**
 * Provides directory discovery utilities for project modules. This parser inspects
 * the folder structure of each module identified by IModuleParser and returns the
 * resource directory associated with each module. It supports standard Android and
 * JVM directory conventions.
 *
 * The parser looks for directories such as:
 *
 * - moduleName/src/main/res
 * - moduleName/src/main/resources
 *
 * If no valid resource directory is found for a module, that module is omitted
 * from the result map.
 *
 * This parser assumes the projectRoot path denotes the directory containing
 * settings.gradle or settings.gradle.kts.
 */
object ResourceDirectoryParser {

    /**
     * Returns a mapping of module names to their corresponding resource directories.
     * Only modules that contain a valid resources directory are included in the result.
     *
     * @param projectRoot Path to the root directory of the project that contains
     *        settings.gradle or settings.gradle.kts.
     * @return A map where keys are module names and values are File instances pointing
     *         to the resource directory of each module.
     */
    fun getResDir(projectRoot: String): Map<String, File> {
        val rootDir = File(projectRoot)
        if (!rootDir.exists() || !rootDir.isDirectory) return emptyMap()

        val settingsFile = listOf(
            File(rootDir, "settings.gradle.kts"),
            File(rootDir, "settings.gradle")
        ).firstOrNull { it.exists() && it.isFile } ?: return emptyMap()

        val modules = ModuleParser.getModules(settingsFile.absolutePath)
        val result = mutableMapOf<String, File>()

        for (module in modules) {
            val moduleDir = File(rootDir, module)
            if (!moduleDir.exists() || !moduleDir.isDirectory) continue

            val androidRes = File(moduleDir, "src/main/res")
            val jvmRes = File(moduleDir, "src/main/resources")

            when {
                androidRes.exists() && androidRes.isDirectory -> {
                    result[module] = androidRes
                }
                jvmRes.exists() && jvmRes.isDirectory -> {
                    result[module] = jvmRes
                }
            }
        }

        return result
    }
}
