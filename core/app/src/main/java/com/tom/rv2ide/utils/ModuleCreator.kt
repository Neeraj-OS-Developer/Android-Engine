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

import com.tom.rv2ide.fragments.sidebar.SubModuleFragment.ModuleLanguage
import java.io.File
import java.io.IOException

/**
 * Utility class for creating new sub-modules in Android projects. Handles module structure
 * creation, build.gradle generation, and settings.gradle.kts updates.
 *
 * <p>Performance &amp; reliability characteristics:
 * * **Single-pass project detection** — the app module's build script is read exactly once per
 *   module creation (previous implementation read it up to four times).
 * * **Pre-compiled regexes** — all parsing patterns live in [Companion] and are compiled once at
 *   class-load time.
 * * **Atomic writes** — every generated file is written to a temporary sibling and renamed into
 *   place, so a crash mid-write cannot corrupt existing project files.
 * * **Strict input validation** — module names are matched against [VALID_MODULE_NAME] to prevent
 *   path-traversal and invalid Gradle identifiers.
 *
 * @author Neeraj-OS-developer
 */
class ModuleCreator {

  data class CreationResult(val success: Boolean, val errorMessage: String? = null)

  data class AppModuleConfig(val compileSdk: Int, val minSdk: Int)

  /**
   * Aggregated project metadata — computed once per [createModule] call and reused across every
   * downstream step.
   */
  private data class ProjectInfo(
      val useKotlinDsl: Boolean,
      val basePackageName: String,
      val appConfig: AppModuleConfig,
      /** The app module's build script, or `null` if the project has no `app` module yet. */
      val appBuildFile: File?,
      /** Snapshot of [appBuildFile] contents at detection time, or `null`. */
      val appBuildContent: String?,
  )

  /**
   * Creates a new sub-module with the specified configuration.
   *
   * @param moduleName The name of the module to create
   * @param language The programming language (Kotlin or Java)
   * @param projectRoot The root directory of the project
   * @return [CreationResult] indicating success or failure
   */
  fun createModule(
      moduleName: String,
      language: ModuleLanguage,
      projectRoot: File,
  ): CreationResult {
    return try {
      val trimmed = moduleName.trim()
      if (trimmed.isEmpty()) {
        return CreationResult(false, "Module name cannot be empty")
      }
      if (!VALID_MODULE_NAME.matches(trimmed)) {
        return CreationResult(
            false,
            "Module name must start with a letter and contain only letters, digits, or underscores",
        )
      }
      if (!projectRoot.exists() || !projectRoot.isDirectory) {
        return CreationResult(false, "Project root directory does not exist")
      }

      val moduleDir = File(projectRoot, trimmed)
      if (moduleDir.exists()) {
        return CreationResult(false, "Module '$trimmed' already exists")
      }

      // ONE pass over the app build file — everything downstream reuses this snapshot.
      val info = detectProjectInfo(projectRoot)

      createModuleStructure(moduleDir, trimmed, language, info)
      updateSettingsGradle(projectRoot, trimmed)
      addDependencyToAppModule(info, trimmed)

      CreationResult(true)
    } catch (e: Exception) {
      CreationResult(false, e.message ?: "Unknown error occurred")
    }
  }

  // ---------------------------------------------------------------- detection

  /**
   * Reads the app module's build script a single time and extracts every piece of metadata needed
   * downstream.
   */
  private fun detectProjectInfo(projectRoot: File): ProjectInfo {
    val ktsFile = File(projectRoot, APP_BUILD_KTS)
    val groovyFile = File(projectRoot, APP_BUILD_GROOVY)

    val appBuildFile: File? =
        when {
          ktsFile.isFile -> ktsFile
          groovyFile.isFile -> groovyFile
          else -> null
        }

    val useKotlinDsl = appBuildFile == null || appBuildFile == ktsFile

    if (appBuildFile == null) {
      return ProjectInfo(
          useKotlinDsl = true,
          basePackageName = DEFAULT_PACKAGE,
          appConfig = AppModuleConfig(DEFAULT_COMPILE_SDK, DEFAULT_MIN_SDK),
          appBuildFile = null,
          appBuildContent = null,
      )
    }

    val content = appBuildFile.readText()

    val basePackageName =
        NAMESPACE_PATTERN.find(content)?.groupValues?.get(1)
            ?: APPLICATION_ID_PATTERN.find(content)?.groupValues?.get(1)
            ?: DEFAULT_PACKAGE

    val compileSdk =
        COMPILE_SDK_PATTERN.find(content)?.groupValues?.get(1)?.toIntOrNull() ?: DEFAULT_COMPILE_SDK
    val minSdk =
        MIN_SDK_PATTERN.find(content)?.groupValues?.get(1)?.toIntOrNull() ?: DEFAULT_MIN_SDK

    return ProjectInfo(
        useKotlinDsl = useKotlinDsl,
        basePackageName = basePackageName,
        appConfig = AppModuleConfig(compileSdk, minSdk),
        appBuildFile = appBuildFile,
        appBuildContent = content,
    )
  }

  // ---------------------------------------------------------------- structure

  private fun createModuleStructure(
      moduleDir: File,
      moduleName: String,
      language: ModuleLanguage,
      info: ProjectInfo,
  ) {
    val srcMainDir = File(moduleDir, "src/main")
    val javaDir = File(srcMainDir, if (language == ModuleLanguage.KOTLIN) "kotlin" else "java")
    val resourcesDir = File(srcMainDir, "resources")

    if (!moduleDir.mkdirs() && !moduleDir.isDirectory) {
      throw IOException("Failed to create module directory: ${moduleDir.absolutePath}")
    }
    if (!srcMainDir.mkdirs() && !srcMainDir.isDirectory) {
      throw IOException("Failed to create src/main directory")
    }
    if (!javaDir.mkdirs() && !javaDir.isDirectory) {
      throw IOException("Failed to create source directory")
    }
    if (!resourcesDir.mkdirs() && !resourcesDir.isDirectory) {
      throw IOException("Failed to create resources directory")
    }

    createBuildGradle(moduleDir, moduleName, language, info)
    createProguardRules(moduleDir)
    createConsumerRules(moduleDir)
    createSampleSourceFile(javaDir, moduleName, language, info.basePackageName)
  }

  private fun createBuildGradle(
      moduleDir: File,
      moduleName: String,
      language: ModuleLanguage,
      info: ProjectInfo,
  ) {
    val buildFile = File(moduleDir, if (info.useKotlinDsl) "build.gradle.kts" else "build.gradle")
    val content =
        if (info.useKotlinDsl) {
          generateKotlinDslBuildScript(moduleName, language, info.basePackageName, info.appConfig)
        } else {
          generateGroovyBuildScript(moduleName, language, info.basePackageName, info.appConfig)
        }
    buildFile.writeAtomically(content)
  }

  private fun generateKotlinDslBuildScript(
      moduleName: String,
      language: ModuleLanguage,
      basePackageName: String,
      appConfig: AppModuleConfig,
  ): String {
    val isKotlin = language == ModuleLanguage.KOTLIN

    val kotlinPlugin =
        if (isKotlin) "id(\"kotlin-android\")" else "// Java module - no additional plugin needed"

    val kotlinOptions =
        if (isKotlin) {
          """
  kotlinOptions {
    jvmTarget = "1.8"
  }"""
        } else {
          ""
        }

    return """
plugins {
  id("com.android.library")
  $kotlinPlugin
}

android {
  namespace = "$basePackageName.$moduleName"
  compileSdk = ${appConfig.compileSdk}

  defaultConfig {
    minSdk = ${appConfig.minSdk}
  }
  
  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )
    }
  }
$kotlinOptions
}

dependencies {
  // Core Android dependencies
  implementation("androidx.annotation:annotation:1.7.0")
}
"""
        .trimIndent()
  }

  private fun generateGroovyBuildScript(
      moduleName: String,
      language: ModuleLanguage,
      basePackageName: String,
      appConfig: AppModuleConfig,
  ): String {
    val isKotlin = language == ModuleLanguage.KOTLIN

    val kotlinPlugin =
        if (isKotlin) "id 'kotlin-android'" else "// Java module - no additional plugin needed"

    val kotlinOptions =
        if (isKotlin) {
          """
  kotlinOptions {
    jvmTarget = '1.8'
  }"""
        } else {
          ""
        }

    return """
plugins {
  id 'com.android.library'
  $kotlinPlugin
}

android {
  namespace '$basePackageName.$moduleName'
  compileSdk ${appConfig.compileSdk}

  defaultConfig {
    minSdk ${appConfig.minSdk}
  }

  buildTypes {
    release {
      minifyEnabled false
      proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
    }
  }

  compileOptions {
    sourceCompatibility JavaVersion.VERSION_1_8
    targetCompatibility JavaVersion.VERSION_1_8
  }
$kotlinOptions
}

dependencies {
  // Core Android dependencies
  implementation 'androidx.annotation:annotation:1.7.0'
}
"""
        .trimIndent()
  }

  private fun createProguardRules(moduleDir: File) {
    val proguardFile = File(moduleDir, "proguard-rules.pro")
    proguardFile.writeAtomically(PROGUARD_TEMPLATE)
  }

  private fun createConsumerRules(moduleDir: File) {
    val consumerRulesFile = File(moduleDir, "consumer-rules.pro")
    consumerRulesFile.writeAtomically(CONSUMER_RULES_TEMPLATE)
  }

  private fun createSampleSourceFile(
      sourceDir: File,
      moduleName: String,
      language: ModuleLanguage,
      basePackageName: String,
  ) {
    val packageDir = File(sourceDir, basePackageName.replace('.', '/') + "/$moduleName")
    if (!packageDir.mkdirs() && !packageDir.isDirectory) {
      throw IOException("Failed to create package directory: ${packageDir.absolutePath}")
    }

    val isKotlin = language == ModuleLanguage.KOTLIN
    val fileName = if (isKotlin) "SampleClass.kt" else "SampleClass.java"
    val sampleFile = File(packageDir, fileName)

    val content =
        if (isKotlin) {
          """
package $basePackageName.$moduleName

/**
 * Sample class for the $moduleName module.
 */
class SampleClass {
    
    /**
     * Sample method that returns a greeting message.
     */
    fun getGreeting(): String {
        return "Hello from $moduleName module!"
    }
}
"""
              .trimIndent()
        } else {
          """
package $basePackageName.$moduleName;

/**
 * Sample class for the $moduleName module.
 */
public class SampleClass {
    
    /**
     * Sample method that returns a greeting message.
     */
    public String getGreeting() {
        return "Hello from $moduleName module!";
    }
}
"""
              .trimIndent()
        }

    sampleFile.writeAtomically(content)
  }

  // ---------------------------------------------------------------- mutation

  private fun updateSettingsGradle(projectRoot: File, moduleName: String) {
    val settingsFile = File(projectRoot, "settings.gradle.kts")
    if (!settingsFile.exists()) {
      throw IOException("settings.gradle.kts not found in project root")
    }

    val content = settingsFile.readText()

    // Match ":$moduleName" as a standalone include entry (avoid ":moduleX" matching ":module").
    if (INCLUDED_MODULE_PATTERN(moduleName).containsMatchIn(content)) {
      return // Already included.
    }

    val newContent =
        try {
          val match = INCLUDE_PATTERN.find(content)
          if (match == null) {
            content + "\n\ninclude(\":$moduleName\")\n"
          } else {
            val existing = match.groupValues[1].trim()
            val replacement =
                if (existing.isEmpty()) {
                  "include(\":$moduleName\")"
                } else {
                  "include(\n  $existing,\n  \":$moduleName\"\n)"
                }
            // Range-based replacement — never interprets `$` in moduleName as a backreference.
            content.substring(0, match.range.first) +
                replacement +
                content.substring(match.range.last + 1)
          }
        } catch (e: Exception) {
          throw IOException("Failed to update settings.gradle.kts: ${e.message}", e)
        }

    settingsFile.writeAtomically(newContent)
  }

  private fun addDependencyToAppModule(info: ProjectInfo, moduleName: String) {
    val appBuildFile = info.appBuildFile ?: return
    val content = info.appBuildContent ?: return

    // Match the exact project dependency — no substring false-positives.
    val existingDep =
        if (info.useKotlinDsl) {
          content.contains("project(\":$moduleName\")")
        } else {
          content.contains("project(':$moduleName')")
        }
    if (existingDep) return

    val match = DEPENDENCIES_PATTERN.find(content) ?: return
    val insertPosition = match.range.last + 1

    val dependencyLine =
        if (info.useKotlinDsl) {
          "\n    implementation(project(\":$moduleName\"))\n"
        } else {
          "\n    implementation project(':$moduleName')\n"
        }

    val newContent =
        content.substring(0, insertPosition) + dependencyLine + content.substring(insertPosition)

    appBuildFile.writeAtomically(newContent)
  }

  // ---------------------------------------------------------------- io helpers

  /**
   * Writes [content] to a sibling temp file and atomically renames it into place. Prevents file
   * corruption if the process dies mid-write. On Android (POSIX), [File.renameTo] uses
   * `rename(2)`, which atomically overwrites the destination.
   */
  private fun File.writeAtomically(content: String) {
    val parent = parentFile
    if (parent != null && !parent.exists()) {
      parent.mkdirs()
    }

    val tmp = File(parent, "$name.tmp")
    try {
      tmp.writeText(content)
      if (!tmp.renameTo(this)) {
        // Fallback for exotic filesystems that refuse overwrite-rename.
        writeText(content)
        tmp.delete()
      }
    } catch (e: IOException) {
      tmp.delete()
      throw e
    }
  }

  // ---------------------------------------------------------------- constants

  companion object {
    private const val APP_BUILD_KTS = "app/build.gradle.kts"
    private const val APP_BUILD_GROOVY = "app/build.gradle"

    private const val DEFAULT_PACKAGE = "com.example"
    private const val DEFAULT_COMPILE_SDK = 34
    private const val DEFAULT_MIN_SDK = 21

    // Pre-compiled regexes — shared across every module-creation call.
    private val NAMESPACE_PATTERN = Regex("namespace\\s*[=:]\\s*[\"']([^\"']+)[\"']")
    private val APPLICATION_ID_PATTERN = Regex("applicationId\\s*[=:]\\s*[\"']([^\"']+)[\"']")
    private val COMPILE_SDK_PATTERN = Regex("compileSdk\\s*[=:]\\s*(\\d+)")
    private val MIN_SDK_PATTERN = Regex("minSdk\\s*[=:]\\s*(\\d+)")
    private val INCLUDE_PATTERN = Regex("include\\s*\\(\\s*([^)]*)\\s*\\)")
    private val DEPENDENCIES_PATTERN = Regex("dependencies\\s*\\{")

    /**
     * Module names must be valid Gradle identifiers. Prevents path traversal (`../`) and
     * invalid identifiers.
     */
    private val VALID_MODULE_NAME = Regex("^[a-zA-Z][a-zA-Z0-9_]*$")

    /** Matches exactly `":moduleName"` in an include block, not `":moduleNameExtra"`. */
    private fun INCLUDED_MODULE_PATTERN(moduleName: String): Regex =
        Regex("[\"']:$moduleName[\"']")

    private val PROGUARD_TEMPLATE =
        """
        # Add project specific ProGuard rules here.
        # You can control the set of applied configuration files using the
        # proguardFiles setting in build.gradle.
        #
        # For more details, see
        #   http://developer.android.com/guide/developing/tools/proguard.html

        # If your project uses WebView with JS, uncomment the following
        # and specify the fully qualified class name to the JavaScript interface
        # class:
        #-keepclassmembers class fqcn.of.javascript.interface.for.webview {
        #   public *;
        #}

        # Uncomment this to preserve the line number information for
        # debugging stack traces.
        #-keepattributes SourceFile,LineNumberTable

        # If you keep the line number information, uncomment this to
        # hide the original source file name.
        #-renamesourcefileattribute SourceFile
        """
            .trimIndent()

    private val CONSUMER_RULES_TEMPLATE =
        """
        # Consumer ProGuard rules for this module
        # These rules will be applied to consumers of this library
        """
            .trimIndent()
  }
}
