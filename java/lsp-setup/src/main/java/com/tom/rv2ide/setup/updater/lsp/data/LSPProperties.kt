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

package com.tom.rv2ide.setup.updater.lsp.data

import android.util.Log
import com.tom.rv2ide.shell.executeProcessAsync
import com.tom.rv2ide.utils.Environment
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.Properties
import java.util.concurrent.TimeUnit

/**
 * Utility object for loading Language Server Protocol (LSP)-related
 * configuration values from `.properties` files.
 *
 * This component wraps reading keys from a specified properties file
 * and provides strongly-typed accessors as needed by the LSP updater pipeline.
 *
 * @author Mohammed-baqer-null @ https://github.com/Mohammed-baqer-null
 */
object LSPProperties {

    val kotlinLspVersion: String
        get() {
            return try {
                
                val serverHome = Environment.SERVERS_KOTLIN_DIR
                val libDir = File(serverHome, "lib")
                if (!libDir.exists()) {
                    return "1.3.14"
                }

                val jars = libDir.listFiles { file: File -> file.name.endsWith(".jar") }
                if (jars == null || jars.isEmpty()) {
                    return "1.3.14"
                }

                val classpath = jars.joinToString(":") { it.absolutePath }
                val javaExec = Environment.JAVA?.absolutePath ?: "java"
                val javaHome = Environment.JAVA_HOME?.absolutePath ?: Environment.PREFIX.absolutePath
                val home = Environment.HOME.absolutePath
                
                val command = listOf(
                    javaExec,
                    "-classpath",
                    classpath,
                    "org.javacs.kt.MainKt",
                    "--versionNumber"
                )
                val process = executeProcessAsync {
                    this.command = command
                    redirectErrorStream = true
                    environment = mapOf(
                        "JAVA_HOME" to javaHome,
                        "HOME" to home
                    )
                }

                val output = process.inputStream.bufferedReader().use { it.readText().trim() }
                val completed = process.waitFor(5, TimeUnit.SECONDS)
                if (!completed) {
                    process.destroy()
                    return "1.3.14"
                }

                val exitValue = process.exitValue()
                if (exitValue == 0 && output.isNotEmpty()) {
                    output
                } else {
                    "1.3.14"
                }
            } catch (e: Exception) {
                "1.3.14"
            }
        }

    @Throws(IOException::class, NoSuchElementException::class)
    fun readPropertyValue(
        filePath: String,
        key: String,
        failIfMissing: Boolean = true
    ): String? {
        val props = Properties()

        try {
            FileInputStream(filePath).use { fis ->
                props.load(fis)
            }
        } catch (ex: IOException) {
            if (failIfMissing) {
                throw IOException("Failed to load properties file at: $filePath", ex)
            }
            return null
        }

        val value = props.getProperty(key)
        if (value == null && failIfMissing) {
            throw NoSuchElementException("Key '$key' not found in $filePath")
        }

        return value
    }

    @Throws(IOException::class)
    fun writePropertyValue(
        filePath: String,
        key: String,
        value: String
    ) {
        val file = File(filePath)
        val props = Properties()

        if (file.exists()) {
            try {
                FileInputStream(file).use { fis ->
                    props.load(fis)
                }
            } catch (ex: IOException) {
                throw IOException("Failed to load existing properties file at: $filePath", ex)
            }
        } else {
            file.parentFile?.mkdirs()
            file.createNewFile()
        }

        props.setProperty(key, value)

        try {
            FileOutputStream(file).use { fos ->
                props.store(fos, null)
            }
        } catch (ex: IOException) {
            throw IOException("Failed to write properties file at: $filePath", ex)
        }
    }
}