package com.tom.rv2ide.tooling.impl.sync

import java.io.File
import java.io.Serializable
import org.slf4j.LoggerFactory

data class DependencyDeclaration(
    val configuration: String,
    val declaration: String,
    val isCatalogReference: Boolean,
    val catalogLibrary: CatalogLibrary? = null,
) : Serializable {
  private val serialVersionUID = 1L

  val resolvedCoordinates: String?
    get() =
        catalogLibrary?.let { "${it.group}:${it.artifact}:${it.version}" }
            ?: if (!isCatalogReference) declaration else null
}

class BuildScriptParser {

  companion object {
    private val log = LoggerFactory.getLogger(BuildScriptParser::class.java)

    // Common dependency configurations
    private val DEPENDENCY_CONFIGURATIONS =
        setOf(
            "implementation",
            "api",
            "compileOnly",
            "runtimeOnly",
            "testImplementation",
            "testCompileOnly",
            "testRuntimeOnly",
            "androidTestImplementation",
            "androidTestCompileOnly",
            "androidTestRuntimeOnly",
            "debugImplementation",
            "releaseImplementation",
            "kapt",
            "ksp",
            "annotationProcessor",
        )
  }

  fun parseDependencies(
      buildFile: File,
      catalogInfo: VersionCatalogInfo?,
  ): List<DependencyDeclaration> {
    if (!buildFile.exists()) {
      log.warn("Build file not found: ${buildFile.absolutePath}")
      return emptyList()
    }

    val isKotlinDsl = buildFile.name.endsWith(".kts")
    val content = buildFile.readText()

    log.debug(
        "Parsing build file: ${buildFile.absolutePath} (Kotlin DSL: $isKotlinDsl, size: ${content.length} chars)"
    )
    log.debug(
        "Catalog info available: ${catalogInfo != null}, libraries: ${catalogInfo?.libraries?.size ?: 0}"
    )

    return if (isKotlinDsl) {
      parseKotlinDsl(content, catalogInfo)
    } else {
      parseGroovyDsl(content, catalogInfo)
    }
  }

  private fun parseKotlinDsl(
      content: String,
      catalogInfo: VersionCatalogInfo?,
  ): List<DependencyDeclaration> {
    val dependencies = mutableListOf<DependencyDeclaration>()
    var inDependenciesBlock = false
    var blockDepth = 0
    var dependenciesBlockFound = false

    content.lines().forEachIndexed { lineNum, line ->
      val trimmed = line.trim()

      // Track dependencies block - handle both "dependencies {" and "dependencies {"
      if (trimmed.startsWith("dependencies")) {
        if (trimmed.contains("{")) {
          inDependenciesBlock = true
          blockDepth = 1
          dependenciesBlockFound = true
          log.debug("Found dependencies block at line ${lineNum + 1}: $trimmed")
        } else if (trimmed == "dependencies") {
          // Multi-line format, next line should have {
          log.debug(
              "Found 'dependencies' keyword at line ${lineNum + 1}, waiting for opening brace"
          )
        }
      }

      // Check for opening brace on next line after "dependencies"
      if (!inDependenciesBlock && trimmed == "{") {
        val prevLine = if (lineNum > 0) content.lines()[lineNum - 1].trim() else ""
        if (prevLine == "dependencies") {
          inDependenciesBlock = true
          blockDepth = 1
          dependenciesBlockFound = true
          log.debug("Found dependencies block at line ${lineNum + 1} (multi-line)")
        }
      }

      if (inDependenciesBlock) {
        // Track braces
        val openBraces = trimmed.count { it == '{' }
        val closeBraces = trimmed.count { it == '}' }
        blockDepth += openBraces - closeBraces

        log.trace("Line ${lineNum + 1} in dependencies block: depth=$blockDepth, line='$trimmed'")

        // Exit dependencies block
        if (blockDepth <= 0) {
          log.debug("Exiting dependencies block at line ${lineNum + 1}, depth: $blockDepth")
          inDependenciesBlock = false
          return@forEachIndexed
        }

        // Parse dependency line (skip lines that are just braces or empty)
        if (trimmed != "{" && trimmed != "}" && trimmed.isNotEmpty()) {
          val dependency = parseKotlinDependencyLine(trimmed, catalogInfo, lineNum + 1)
          if (dependency != null) {
            dependencies.add(dependency)
            log.debug("Found dependency at line ${lineNum + 1}: ${dependency.declaration}")
          } else {
            log.trace("Line ${lineNum + 1} did not match any dependency pattern: '$trimmed'")
          }
        }
      }
    }

    if (dependencies.isEmpty()) {
      if (dependenciesBlockFound) {
        log.warn("Dependencies block was detected but no dependencies were parsed.")
        log.warn("File content (first 100 lines):\n${content.lines().take(100).joinToString("\n")}")
      } else {
        log.warn("No dependencies block found in file. Searching for 'dependencies' keyword...")
        val hasDependenciesKeyword = content.contains("dependencies", ignoreCase = true)
        log.warn("File contains 'dependencies' keyword: $hasDependenciesKeyword")
        if (hasDependenciesKeyword) {
          log.warn("File content:\n$content")
        }
      }
    }

    log.info("Parsed ${dependencies.size} dependencies from Kotlin DSL")
    return dependencies
  }

  private fun parseKotlinDependencyLine(
      line: String,
      catalogInfo: VersionCatalogInfo?,
      lineNumber: Int = 0,
  ): DependencyDeclaration? {
    // Skip comments and empty lines
    if (line.startsWith("//") || line.isEmpty() || line.trim().isEmpty()) {
      return null
    }

    // Pattern 1: Catalog reference - implementation(libs.material) or
    // implementation(libs.androidx.core.ktx)
    // More flexible pattern that handles libs.xxx and libs.xxx.yyy.zzz
    val catalogPattern = """(\w+)\s*\(\s*(libs\.[\w.]+)\s*\)""".toRegex()
    val catalogMatch = catalogPattern.find(line)

    if (catalogMatch != null) {
      val configuration = catalogMatch.groupValues[1]
      if (configuration !in DEPENDENCY_CONFIGURATIONS) {
        log.trace("Skipping non-dependency configuration: $configuration at line $lineNumber")
        return null
      }

      val reference =
          catalogMatch.groupValues[2] // e.g., "libs.material" or "libs.androidx.core.ktx"
      val libraryName = extractLibraryName(reference)

      log.debug("Extracted library name from '$reference': '$libraryName' (line $lineNumber)")
      log.debug(
          "Available catalog libraries: ${catalogInfo?.libraries?.keys?.joinToString(", ") ?: "none"}"
      )

      // Try multiple name formats to find the library
      var catalogLibrary = catalogInfo?.libraries?.get(libraryName)

      // If not found, try with original dots (some catalogs use dots in aliases)
      if (catalogLibrary == null && reference.startsWith("libs.")) {
        val withDots = reference.substring(5) // Remove "libs."
        catalogLibrary = catalogInfo?.libraries?.get(withDots)
        if (catalogLibrary != null) {
          log.debug("Found library using dot notation: $withDots")
        }
      }

      // If still not found, try searching by partial match
      if (catalogLibrary == null && catalogInfo != null) {
        catalogLibrary =
            catalogInfo.libraries.values.firstOrNull { lib ->
              lib.name.replace("-", ".") == libraryName.replace("-", ".") ||
                  lib.name == libraryName ||
                  lib.name.replace(".", "-") == libraryName
            }
        if (catalogLibrary != null) {
          log.debug("Found library using fuzzy match: ${catalogLibrary.name}")
        }
      }

      if (catalogLibrary != null) {
        log.debug(
            "Found catalog reference: $reference -> ${catalogLibrary.group}:${catalogLibrary.artifact}:${catalogLibrary.version}"
        )
      } else {
        log.warn(
            "Catalog reference '$reference' (library name: '$libraryName') not found in catalog at line $lineNumber"
        )
        // Still return it as a catalog reference even if not found in catalog
      }

      return DependencyDeclaration(
          configuration = configuration,
          declaration = reference,
          isCatalogReference = true,
          catalogLibrary = catalogLibrary,
      )
    }

    // Pattern 2: Direct string - implementation("group:artifact:version")
    val directPattern = """([\w]+)\("([^"]+)"\)""".toRegex()
    val directMatch = directPattern.find(line)

    if (directMatch != null) {
      val configuration = directMatch.groupValues[1]
      if (configuration !in DEPENDENCY_CONFIGURATIONS) {
        return null
      }

      val coordinates = directMatch.groupValues[2]

      return DependencyDeclaration(
          configuration = configuration,
          declaration = coordinates,
          isCatalogReference = false,
          catalogLibrary = null,
      )
    }

    // Pattern 3: Map notation - implementation(group = "...", name = "...", version = "...")
    if (line.matches("""[\w]+\(.*group\s*=.*""".toRegex())) {
      val configuration = line.substringBefore("(").trim()
      if (configuration !in DEPENDENCY_CONFIGURATIONS) {
        return null
      }

      val coordinates = parseMapNotation(line)
      if (coordinates != null) {
        return DependencyDeclaration(
            configuration = configuration,
            declaration = coordinates,
            isCatalogReference = false,
            catalogLibrary = null,
        )
      }
    }

    return null
  }

  private fun parseGroovyDsl(
      content: String,
      catalogInfo: VersionCatalogInfo?,
  ): List<DependencyDeclaration> {
    val dependencies = mutableListOf<DependencyDeclaration>()
    var inDependenciesBlock = false

    content.lines().forEach { line ->
      val trimmed = line.trim()

      // Track dependencies block
      if (trimmed.startsWith("dependencies")) {
        inDependenciesBlock = true
        return@forEach
      }

      if (inDependenciesBlock && trimmed == "}") {
        inDependenciesBlock = false
        return@forEach
      }

      if (inDependenciesBlock) {
        val dependency = parseGroovyDependencyLine(trimmed, catalogInfo)
        if (dependency != null) {
          dependencies.add(dependency)
        }
      }
    }

    log.info("Parsed ${dependencies.size} dependencies from Groovy DSL")
    return dependencies
  }

  private fun parseGroovyDependencyLine(
      line: String,
      catalogInfo: VersionCatalogInfo?,
  ): DependencyDeclaration? {
    // Skip comments and empty lines
    if (line.startsWith("//") || line.isEmpty()) {
      return null
    }

    // Pattern 1: Catalog reference - implementation libs.material
    val catalogPattern = """([\w]+)\s+([\w]+\.[\w.]+)""".toRegex()
    val catalogMatch = catalogPattern.find(line)

    if (catalogMatch != null) {
      val configuration = catalogMatch.groupValues[1]
      if (configuration !in DEPENDENCY_CONFIGURATIONS) {
        return null
      }

      val reference = catalogMatch.groupValues[2]
      val libraryName = extractLibraryName(reference)
      val catalogLibrary = catalogInfo?.libraries?.get(libraryName)

      return DependencyDeclaration(
          configuration = configuration,
          declaration = reference,
          isCatalogReference = true,
          catalogLibrary = catalogLibrary,
      )
    }

    // Pattern 2: String notation - implementation 'group:artifact:version'
    val stringPattern = """([\w]+)\s+['"]([^'"]+)['"]""".toRegex()
    val stringMatch = stringPattern.find(line)

    if (stringMatch != null) {
      val configuration = stringMatch.groupValues[1]
      if (configuration !in DEPENDENCY_CONFIGURATIONS) {
        return null
      }

      val coordinates = stringMatch.groupValues[2]

      return DependencyDeclaration(
          configuration = configuration,
          declaration = coordinates,
          isCatalogReference = false,
          catalogLibrary = null,
      )
    }

    return null
  }

  private fun extractLibraryName(reference: String): String {
    // Extract library name from reference like "libs.material" or "libs.androidx.core.ktx"
    // Returns "material" or "androidx-core-ktx"
    // Remove "libs." prefix if present
    val withoutLibs =
        if (reference.startsWith("libs.")) {
          reference.substring(5) // Remove "libs."
        } else {
          reference
        }

    // Convert dots to hyphens to match TOML format
    // e.g., "androidx.core.ktx" -> "androidx-core-ktx"
    return withoutLibs.replace(".", "-")
  }

  private fun parseMapNotation(line: String): String? {
    val groupPattern = """group\s*=\s*["']([^"']+)["']""".toRegex()
    val namePattern = """name\s*=\s*["']([^"']+)["']""".toRegex()
    val versionPattern = """version\s*=\s*["']([^"']+)["']""".toRegex()

    val group = groupPattern.find(line)?.groupValues?.get(1)
    val name = namePattern.find(line)?.groupValues?.get(1)
    val version = versionPattern.find(line)?.groupValues?.get(1)

    return if (group != null && name != null && version != null) {
      "$group:$name:$version"
    } else {
      null
    }
  }
}
