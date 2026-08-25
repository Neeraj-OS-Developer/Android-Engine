/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.tom.rv2ide.utils

import java.io.File

object GradleFileWriter {

    fun updateModuleBuildGradle(
        moduleDir: File,
        versionName: String?,
        versionCode: Int?,
        minSdk: Int?,
        targetSdk: Int?,
        compileSdk: Int?
    ): Boolean {
        val buildGradleKts = File(moduleDir, "build.gradle.kts")
        val buildGradle = File(moduleDir, "build.gradle")

        val gradleFile = when {
            buildGradleKts.exists() -> buildGradleKts
            buildGradle.exists() -> buildGradle
            else -> return false
        }

        return try {
            var content = gradleFile.readText()
            val isKotlin = gradleFile.name.endsWith(".kts")

            versionName?.let { content = updateVersionName(content, it, isKotlin) }
            versionCode?.let { content = updateVersionCode(content, it, isKotlin) }
            minSdk?.let { content = updateMinSdk(content, it, isKotlin) }
            targetSdk?.let { content = updateTargetSdk(content, it, isKotlin) }
            compileSdk?.let { content = updateCompileSdk(content, it, isKotlin) }

            gradleFile.writeText(content)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun updateVersionName(content: String, value: String, isKotlin: Boolean): String {
        var result = content
        
        val patterns = listOf(
            Regex("""versionName\s*=\s*"[^"]*"""") to """versionName = "$value"""",
            Regex("""versionName\s*=\s*'[^']*'""") to """versionName = '$value'""",
            Regex("""versionName\("[^"]*"\)""") to """versionName("$value")""",
            Regex("""versionName\s+["'][^"']+["']""") to """versionName "$value""""
        )
        
        for ((pattern, replacement) in patterns) {
            if (pattern.containsMatchIn(result)) {
                return pattern.replace(result, replacement)
            }
        }
        return result
    }

    private fun updateVersionCode(content: String, value: Int, isKotlin: Boolean): String {
        var result = content
        
        val patterns = listOf(
            Regex("""versionCode\s*=\s*\d+""") to """versionCode = $value""",
            Regex("""versionCode\(\s*\d+\s*\)""") to """versionCode($value)""",
            Regex("""versionCode\s+\d+""") to """versionCode $value"""
        )
        
        for ((pattern, replacement) in patterns) {
            if (pattern.containsMatchIn(result)) {
                result = pattern.replace(result, replacement)
                return result
            }
        }
        
        return result
    }

    private fun updateMinSdk(content: String, value: Int, isKotlin: Boolean): String {
        var result = content
        
        val patterns = listOf(
            Regex("""minSdk\s*=\s*\d+""") to """minSdk = $value""",
            Regex("""minSdkVersion\s*=\s*\d+""") to """minSdkVersion = $value""",
            Regex("""minSdk\(\s*\d+\s*\)""") to """minSdk($value)""",
            Regex("""minSdkVersion\(\s*\d+\s*\)""") to """minSdkVersion($value)""",
            Regex("""minSdkVersion\s+\d+""") to """minSdkVersion $value""",
            Regex("""minSdk\s+\d+""") to """minSdk $value"""
        )
        
        for ((pattern, replacement) in patterns) {
            if (pattern.containsMatchIn(result)) {
                return pattern.replace(result, replacement)
            }
        }
        return result
    }

    private fun updateTargetSdk(content: String, value: Int, isKotlin: Boolean): String {
        var result = content
        
        val patterns = listOf(
            Regex("""targetSdk\s*=\s*\d+""") to """targetSdk = $value""",
            Regex("""targetSdkVersion\s*=\s*\d+""") to """targetSdkVersion = $value""",
            Regex("""targetSdk\(\s*\d+\s*\)""") to """targetSdk($value)""",
            Regex("""targetSdkVersion\(\s*\d+\s*\)""") to """targetSdkVersion($value)""",
            Regex("""targetSdkVersion\s+\d+""") to """targetSdkVersion $value""",
            Regex("""targetSdk\s+\d+""") to """targetSdk $value"""
        )
        
        for ((pattern, replacement) in patterns) {
            if (pattern.containsMatchIn(result)) {
                return pattern.replace(result, replacement)
            }
        }
        return result
    }

    private fun updateCompileSdk(content: String, value: Int, isKotlin: Boolean): String {
        var result = content
        
        val patterns = listOf(
            Regex("""compileSdk\s*=\s*\d+""") to """compileSdk = $value""",
            Regex("""compileSdkVersion\s*=\s*\d+""") to """compileSdkVersion = $value""",
            Regex("""compileSdk\(\s*\d+\s*\)""") to """compileSdk($value)""",
            Regex("""compileSdkVersion\(\s*\d+\s*\)""") to """compileSdkVersion($value)""",
            Regex("""compileSdkVersion\s+\d+""") to """compileSdkVersion $value""",
            Regex("""compileSdk\s+\d+""") to """compileSdk $value"""
        )
        
        for ((pattern, replacement) in patterns) {
            if (pattern.containsMatchIn(result)) {
                return pattern.replace(result, replacement)
            }
        }
        return result
    }
}