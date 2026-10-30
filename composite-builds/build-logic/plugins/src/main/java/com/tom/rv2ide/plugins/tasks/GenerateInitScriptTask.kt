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

package com.tom.rv2ide.plugins.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

/**
 * Generates the Gradle init script for AndroidIDE.
 *
 * The generated init script loads the `rv2ide-gradle-plugin` JAR from the local filesystem
 * (bundled alongside this init script in the APK assets under `data/common/`) instead of
 * downloading it from Maven Central. This makes AndroidIDE fully usable in offline mode.
 */
abstract class GenerateInitScriptTask : DefaultTask() {

  @get:Input abstract val downloadVersion: Property<String>

  @get:Input abstract val mavenGroupId: Property<String>

  @get:OutputDirectory abstract val outputDir: DirectoryProperty

  companion object {
    private const val PLUGIN_JAR_NAME = "rv2ide-gradle-plugin-1.0.0.jar"
    private const val INIT_SCRIPT_NAME = "androidide.init.gradle"
  }

  @TaskAction
  fun generate() {
    val outFile =
        this.outputDir.file("data/common/$INIT_SCRIPT_NAME").also {
          it.get().asFile.parentFile.mkdirs()
        }

    outFile.get().asFile.bufferedWriter().use { writer ->
      writer.write(buildInitScript())
    }

    logger.info("Generated AndroidIDE init script at: ${outFile.get().asFile.absolutePath}")
  }

  private fun buildInitScript(): String {
    return """
      def androidideInitScript = gradle.startParameter.allInitScripts
          .find { it.name == '$INIT_SCRIPT_NAME' }

      if (androidideInitScript == null) {
          throw new GradleException(
              "AndroidIDE: Cannot locate '$INIT_SCRIPT_NAME' among Gradle init scripts."
          )
      }

      def pluginJar = new File(androidideInitScript.parentFile, '$PLUGIN_JAR_NAME')

      if (!pluginJar.exists() || !pluginJar.isFile()) {
          throw new GradleException(
              "AndroidIDE: Gradle plugin JAR not found at ${'$'}{pluginJar.absolutePath}."
          )
      }

      logger.lifecycle("AndroidIDE: Loading Gradle plugin from ${'$'}{pluginJar.absolutePath}")

      initscript {
          dependencies {
              classpath files(pluginJar)
          }
      }

      apply plugin: com.tom.rv2ide.gradle.AndroidIDEInitScriptPlugin
    """.trimIndent()
  }
}
