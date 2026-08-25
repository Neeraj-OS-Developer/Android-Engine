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

package com.tom.rv2ide.projectdata.parsers

import java.io.File

/**
 * Parses Gradle settings files (settings.gradle or settings.gradle.kts) and extracts the
 * modules defined through the include() directives. Supports Kotlin and Groovy DSLs,
 * multiline include statements, comma-separated module declarations, and nested or
 * irregular whitespace formatting.
 *
 * The parser automatically removes block and line comments before processing the
 * include() entries.
 *
 * @author
 * Mohammed-baqer-null @ https://github.com/Mohammed-baqer-null
 */
object ModuleParser {

    /**
     * Returns the number of modules defined in the settings file.
     *
     * @param path Path to settings.gradle or settings.gradle.kts.
     * @return The number of parsed modules.
     */
    fun getModulesCount(path: String): Int = getModules(path).size

    /**
     * Returns all modules declared using include() in the provided settings file.
     *
     * @param path Path to settings.gradle or settings.gradle.kts.
     * @return Array of module names without duplicates.
     */
    fun getModules(path: String): Array<String> {
        val file = File(path)
        if (!file.exists() || !file.isFile) return emptyArray()

        val raw = file.readText()

        // Remove block comments.
        val noBlock = raw.replace(
            Regex("/\\*.*?\\*/", setOf(RegexOption.DOT_MATCHES_ALL)),
            ""
        )
        
        // Remove line comments.
        val cleaned = noBlock.replace(
            Regex("//.*?$", RegexOption.MULTILINE),
            ""
        )

        // Match all include(...) blocks, with multiline support.
        val includeBlocks = Regex(
            "include\\s*\\((.*?)\\)",
            setOf(RegexOption.DOT_MATCHES_ALL)
        ).findAll(cleaned)

        val modules = mutableListOf<String>()

        for (match in includeBlocks) {
            val inside = match.groupValues[1]

            // Extract all "module" occurrences inside the parentheses.
            Regex("\"([^\"]+)\"")
                .findAll(inside)
                .forEach { modules += it.groupValues[1].trim() }
        }

        return modules.distinct().toTypedArray()
    }
}