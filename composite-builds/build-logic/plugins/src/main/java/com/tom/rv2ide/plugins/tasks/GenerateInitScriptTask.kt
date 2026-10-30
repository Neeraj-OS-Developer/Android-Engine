/* author Name: Neeraj-OS-Developer 
 *
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
              "AndroidIDE Error: Cannot locate '$INIT_SCRIPT_NAME' among Gradle init scripts."
          )
      }

      def pluginJar = new File(androidideInitScript.parentFile, '$PLUGIN_JAR_NAME')
      boolean isOfflineMode = false

      // =========================================================
      // SMART FALLBACK SYSTEM (Offline -> Online -> Detailed Error)
      // =========================================================

      if (pluginJar.exists() && pluginJar.isFile()) {
          // LEVEL 1: OFFLINE MODE (JAR found in assets)
          isOfflineMode = true
          logger.lifecycle("AndroidIDE: Offline mode detected. Loading plugin from: ${'$'}{pluginJar.absolutePath}")
          
          initscript {
              dependencies {
                  classpath files(pluginJar)
              }
          }
      } else {
          // LEVEL 2: ONLINE MODE (JAR not found, attempting Maven)
          logger.warn("AndroidIDE: Offline JAR not found. Falling back to ONLINE mode (Maven Central)...")
          
          try {
              initscript {
                  repositories {
                      google()
                      mavenCentral()
                  }
                  dependencies {
                      classpath('io.github.mohammed-baqer-null:rv2ide-gradle-plugin:1.0.0') {
                          setChanging(false)
                      }
                  }
              }
              logger.lifecycle("AndroidIDE: Online mode successful. Plugin downloaded from Maven.")
          } catch (Exception e) {
              // LEVEL 3: CRITICAL ERROR (Both Offline and Online failed)
              throw new GradleException("""
                  
                  ==============================================================
                  ANDROIDIDE CRITICAL ERROR: PLUGIN LOAD FAILED
                  ==============================================================
                  
                  The AndroidIDE Gradle plugin could not be loaded via either method:
                  
                  1. OFFLINE Check: FAILED
                     Reason: File not found at ${'$'}{pluginJar.absolutePath}
                     
                  2. ONLINE Check: FAILED
                     Reason: No internet connection or Maven repository is unreachable.
                  
                  Solutions:
                  - Enable your internet connection and try building again.
                  - Reinstall the AndroidIDE APK to restore the bundled offline JAR.
                  
                  Original Error: ${'$'}{e.message}
                  ==============================================================
                  
              """.trimIndent())
          }
      }

      // =========================================================
      // Project Repositories Configuration
      // =========================================================
      settingsEvaluated { settings ->
          settings.pluginManagement {
              repositories {
                  google()
                  mavenCentral()
                  gradlePluginPortal()
              }
          }
      }

      allprojects {
          buildscript {
              repositories {
                  google()
                  mavenCentral()
              }
          }
          repositories {
              google()
              mavenCentral()
          }
      }

      // =========================================================
      // Final Apply
      // =========================================================
      apply plugin: com.tom.rv2ide.gradle.AndroidIDEInitScriptPlugin
      
      logger.lifecycle("AndroidIDE: Init script execution finished. (Offline Mode: ${'$'}{isOfflineMode})")
    """.trimIndent()
  }
}
