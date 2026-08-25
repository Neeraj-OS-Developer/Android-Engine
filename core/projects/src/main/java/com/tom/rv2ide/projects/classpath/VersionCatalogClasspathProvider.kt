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

package com.tom.rv2ide.projects.classpath

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory

/**
 * Resolves dependencies from Gradle version catalogs (libs.versions.toml) and provides them as
 * classpath entries.
 *
 * @author Mohammed-baqer-null @ https://github.com/Mohammed-baqer-null
 */
class VersionCatalogClasspathProvider {

  companion object {
    private val log = LoggerFactory.getLogger(VersionCatalogClasspathProvider::class.java)

    // Cache resolved dependencies per project directory
    private val catalogCache = ConcurrentHashMap<String, CatalogCacheEntry>()

    // Cache for transitive dependencies to avoid re-parsing POMs
    private val transitiveCache = ConcurrentHashMap<String, List<File>>()

    // Cache for POM files
    private val pomCache = ConcurrentHashMap<String, List<CatalogDependency>>()

    /**
     * Get classpath entries from version catalog for the given project directory.
     *
     * @param projectDir The module's project directory (not root project)
     * @param existingDeps Map of group:name -> File for already resolved dependencies to avoid
     *   conflicts
     */
    fun getClasspathFromCatalog(
        projectDir: File,
        existingDeps: Map<String, File> = emptyMap(),
    ): Set<File> {
      // Find catalog in root project directory
      val rootProjectDir = findRootProjectDir(projectDir)
      val catalogFile =
          findCatalogFile(rootProjectDir)
              ?: run {
                log.debug("No version catalog found in project: {}", rootProjectDir.absolutePath)
                return emptySet()
              }

      // Find build.gradle file in the MODULE directory (projectDir parameter)
      val buildGradleFile = findBuildGradleFile(projectDir)

      if (buildGradleFile == null) {
        log.warn("No build.gradle found for module: {}", projectDir.absolutePath)
        return emptySet()
      }

      val cacheKey = "${catalogFile.absolutePath}:${projectDir.absolutePath}"
      val cached = catalogCache[cacheKey]

      val buildGradleTimestamp = buildGradleFile.lastModified()
      val catalogTimestamp = catalogFile.lastModified()
      if (cached != null) {
        val currentUsedAliases = parseBuildGradleForCatalogDependencies(buildGradleFile)
        val allCatalogDeps = parseCatalog(catalogFile)
        val currentlyUsedDeps =
            filterUsedDependencies(allCatalogDeps, currentUsedAliases)
                .map { it.toMavenCoordinate() }
                .toSet()

        val cachedDeps = cached.resolvedJars.mapNotNull { extractDependencyFromJarPath(it) }.toSet()

        if (cachedDeps == currentlyUsedDeps) {
          log.debug(
              "Cache valid: same dependencies. Using cache for module {} ({} JARs)",
              projectDir.name,
              cached.resolvedJars.size,
          )
          return cached.resolvedJars
        } else {
          log.info(
              "Dependency list changed for module {}: was {}, now {}",
              projectDir.name,
              cachedDeps.size,
              currentlyUsedDeps.size,
          )
          catalogCache.remove(cacheKey)
          // DON'T return anything here - let it re-resolve below with the NEW dependency list
        }
      }

      log.info("Found version catalog: {}", catalogFile.absolutePath)
      log.info("Checking module build file: {}", buildGradleFile.absolutePath)

      return try {
        // Parse ALL dependencies from catalog
        val allCatalogDependencies = parseCatalog(catalogFile)
        log.info("Parsed {} dependencies from version catalog", allCatalogDependencies.size)

        // Parse build.gradle to find which catalog dependencies are actually used
        val usedAliases = parseBuildGradleForCatalogDependencies(buildGradleFile)
        log.info("Found {} catalog dependency references in build.gradle", usedAliases.size)

        if (usedAliases.isEmpty()) {
          log.info(
              "No catalog dependencies declared in build.gradle for module: {}",
              projectDir.name,
          )
          // Clear cache and return empty
          catalogCache[cacheKey] =
              CatalogCacheEntry(
                  timestamp = catalogTimestamp,
                  buildGradleTimestamp = buildGradleTimestamp,
                  resolvedJars = emptySet(),
              )
          return emptySet()
        }

        // Filter to only dependencies that are actually used
        val usedDependencies = filterUsedDependencies(allCatalogDependencies, usedAliases)
        log.info("Filtering to {} actually used dependencies", usedDependencies.size)

        // Filter out dependencies that conflict with existing ones
        val newDeps = filterNonConflictingDependencies(usedDependencies, existingDeps)
        log.info("After conflict resolution, {} new dependencies to resolve", newDeps.size)

        if (newDeps.isEmpty()) {
          log.info("No new dependencies to resolve from catalog (all already present)")
          // Still cache it as empty
          catalogCache[cacheKey] =
              CatalogCacheEntry(
                  timestamp = catalogTimestamp,
                  buildGradleTimestamp = buildGradleTimestamp,
                  resolvedJars = emptySet(),
              )
          return emptySet()
        }

        val jars = resolveDependenciesToJars(newDeps)
        log.info("Successfully resolved {} JAR files from version catalog", jars.size)

        // Cache the result with both timestamps
        catalogCache[cacheKey] =
            CatalogCacheEntry(
                timestamp = catalogTimestamp,
                buildGradleTimestamp = buildGradleTimestamp,
                resolvedJars = jars.toSet(),
            )

        jars.toSet()
      } catch (e: Exception) {
        log.error("Failed to resolve version catalog dependencies", e)
        emptySet()
      }

      log.info("Found version catalog: {}", catalogFile.absolutePath)
      log.info("Checking module build file: {}", buildGradleFile.absolutePath)

      return try {
        // Parse ALL dependencies from catalog
        val allCatalogDependencies = parseCatalog(catalogFile)
        log.info("Parsed {} dependencies from version catalog", allCatalogDependencies.size)

        // Parse build.gradle to find which catalog dependencies are actually used
        val usedAliases = parseBuildGradleForCatalogDependencies(buildGradleFile)
        log.info("Found {} catalog dependency references in build.gradle", usedAliases.size)

        if (usedAliases.isEmpty()) {
          log.info(
              "No catalog dependencies declared in build.gradle for module: {}",
              projectDir.name,
          )
          return emptySet()
        }

        // Filter to only dependencies that are actually used
        val usedDependencies = filterUsedDependencies(allCatalogDependencies, usedAliases)
        log.info("Filtering to {} actually used dependencies", usedDependencies.size)

        // Filter out dependencies that conflict with existing ones
        val newDeps = filterNonConflictingDependencies(usedDependencies, existingDeps)
        log.info("After conflict resolution, {} new dependencies to resolve", newDeps.size)

        if (newDeps.isEmpty()) {
          log.info("No new dependencies to resolve from catalog (all already present)")
          return emptySet()
        }

        val jars = resolveDependenciesToJars(newDeps)
        log.info("Successfully resolved {} JAR files from version catalog", jars.size)

        // Cache the result with both timestamps
        catalogCache[cacheKey] =
            CatalogCacheEntry(
                timestamp = catalogTimestamp,
                buildGradleTimestamp = buildGradleTimestamp,
                resolvedJars = jars.toSet(),
            )

        jars.toSet()
      } catch (e: Exception) {
        log.error("Failed to resolve version catalog dependencies", e)
        emptySet()
      }
    }

    // Helper functions - moved into companion object for access
    private fun findRootProjectDir(moduleDir: File): File {
      var current = moduleDir
      while (current.parentFile != null) {
        val settingsGradle = File(current, "settings.gradle")
        val settingsGradleKts = File(current, "settings.gradle.kts")
        if (settingsGradle.exists() || settingsGradleKts.exists()) {
          return current
        }
        current = current.parentFile
      }
      return moduleDir
    }

    private fun findBuildGradleFile(moduleDir: File): File? {
      val buildGradle = File(moduleDir, "build.gradle")
      if (buildGradle.exists()) {
        log.debug("Found build.gradle in module: {}", moduleDir.name)
        return buildGradle
      }

      val buildGradleKts = File(moduleDir, "build.gradle.kts")
      if (buildGradleKts.exists()) {
        log.debug("Found build.gradle.kts in module: {}", moduleDir.name)
        return buildGradleKts
      }

      log.warn("No build.gradle(.kts) found in module: {}", moduleDir.absolutePath)
      return null
    }

    private fun findCatalogFile(projectDir: File): File? {
      val standardLocations =
          listOf(
              File(projectDir, "gradle/libs.versions.toml"),
              File(projectDir, "gradle/catalog/libs.versions.toml"),
              File(projectDir, "buildSrc/libs.versions.toml"),
          )
      return standardLocations.firstOrNull { it.exists() }
    }

    private fun parseCatalog(catalogFile: File): List<CatalogDependency> {
      val dependencies = mutableListOf<CatalogDependency>()
      val content = catalogFile.readText()
      val versions = parseVersionsSection(content)
      log.debug("Found {} version definitions", versions.size)
      val libraries = parseLibrariesSection(content, versions)
      dependencies.addAll(libraries)
      return dependencies
    }

    private fun parseBuildGradleForCatalogDependencies(buildGradleFile: File): Set<String> {
      val aliases = mutableSetOf<String>()
      try {
        val content = buildGradleFile.readText()
        log.debug("Parsing build.gradle content (length: {} chars)", content.length)

        var inBlockComment = false

        content.lines().forEach { line ->
          val trimmed = line.trim()

          // Handle block comments
          if (trimmed.startsWith("/*")) {
            inBlockComment = true
          }
          if (inBlockComment) {
            if (trimmed.endsWith("*/") || trimmed.contains("*/")) {
              inBlockComment = false
            }
            log.trace("Skipping block comment line: {}", trimmed)
            return@forEach
          }

          // Skip single-line comments
          if (trimmed.startsWith("//")) {
            log.trace("Skipping commented line: {}", trimmed)
            return@forEach
          }

          // Remove inline comments from the line before parsing
          val lineWithoutInlineComment =
              if (line.contains("//")) {
                line.substringBefore("//")
              } else {
                line
              }

          // Find catalog references in this line
          val pattern = """libs\.([a-zA-Z0-9.\-_]+)""".toRegex()
          pattern.findAll(lineWithoutInlineComment).forEach { match ->
            val fullAlias = match.groupValues[1]
            val normalizedAlias = fullAlias.replace(".", "-")
            aliases.add(normalizedAlias)
            log.trace(
                "Found catalog dependency reference: libs.{} -> alias: {}",
                fullAlias,
                normalizedAlias,
            )
          }
        }

        log.debug("Extracted {} catalog dependency aliases from build.gradle", aliases.size)
      } catch (e: Exception) {
        log.error("Failed to parse build.gradle for catalog dependencies", e)
      }
      return aliases
    }

    private fun filterUsedDependencies(
        allDependencies: List<CatalogDependency>,
        usedAliases: Set<String>,
    ): List<CatalogDependency> {
      return allDependencies.filter { dep ->
        usedAliases.contains(dep.alias).also { used ->
          if (used) {
            log.debug("Including used dependency: {} ({})", dep.alias, dep.toMavenCoordinate())
          }
        }
      }
    }

    private fun filterNonConflictingDependencies(
        dependencies: List<CatalogDependency>,
        existingDeps: Map<String, File>,
    ): List<CatalogDependency> {
      if (existingDeps.isEmpty()) {
        log.debug("No existing dependencies to check against")
        return dependencies
      }
      return dependencies.filter { dep ->
        val key = dep.toGroupArtifactKey()
        val exists = existingDeps.containsKey(key)
        if (exists) {
          log.debug(
              "Skipping conflicting dependency: {} (already exists at: {})",
              dep.toMavenCoordinate(),
              existingDeps[key]?.name,
          )
        } else {
          log.trace("Including new dependency: {}", dep.toMavenCoordinate())
        }
        !exists
      }
    }

    private fun resolveDependenciesToJars(dependencies: List<CatalogDependency>): List<File> {
      val gradleCacheDir =
          File(
              System.getProperty("user.home") ?: "/data/data/com.tom.rv2ide/files",
              ".gradle/caches/modules-2/files-2.1",
          )
      if (!gradleCacheDir.exists()) {
        log.warn("Gradle cache directory not found: {}", gradleCacheDir.absolutePath)
        return emptyList()
      }
      val jars = mutableListOf<File>()
      val resolved = mutableSetOf<String>()
      dependencies.forEach { dep ->
        val cacheKey = dep.toMavenCoordinate()
        val cachedJars = transitiveCache[cacheKey]
        if (cachedJars != null) {
          log.trace("Cache hit for: {}", cacheKey)
          jars.addAll(cachedJars)
        } else {
          val foundJars = resolveTransitiveDependencies(dep, gradleCacheDir, resolved)
          if (foundJars.isNotEmpty()) {
            jars.addAll(foundJars)
            transitiveCache[cacheKey] = foundJars
          } else {
            log.warn("Could not find JAR for: {}", dep.toMavenCoordinate())
          }
        }
      }
      return jars
    }

    private fun resolveTransitiveDependencies(
        dep: CatalogDependency,
        cacheDir: File,
        resolved: MutableSet<String>,
    ): List<File> {
      val coordinate = dep.toMavenCoordinate()
      if (resolved.contains(coordinate)) {
        return emptyList()
      }
      resolved.add(coordinate)
      val jars = mutableListOf<File>()
      val mainJars = findJarInGradleCache(dep, cacheDir)
      jars.addAll(mainJars)
      val pomFile = findPomInGradleCache(dep, cacheDir)
      if (pomFile != null) {
        val pomCacheKey = pomFile.absolutePath
        val cachedDeps = pomCache[pomCacheKey]
        val transitiveDeps =
            if (cachedDeps != null) {
              log.trace("POM cache hit: {}", pomFile.name)
              cachedDeps
            } else {
              val deps = parsePomDependencies(pomFile)
              pomCache[pomCacheKey] = deps
              deps
            }
        transitiveDeps.forEach { transitiveDep ->
          jars.addAll(resolveTransitiveDependencies(transitiveDep, cacheDir, resolved))
        }
      }
      return jars
    }

    private fun findPomInGradleCache(dep: CatalogDependency, cacheDir: File): File? {
      val artifactDir = File(cacheDir, "${dep.group}/${dep.name}")
      if (!artifactDir.exists()) return null
      val versionDirs =
          artifactDir.listFiles { file -> file.isDirectory && file.name == dep.version }?.toList()
              ?: return null
      versionDirs.forEach { versionDir ->
        versionDir.listFiles()?.forEach { hashDir ->
          if (hashDir.isDirectory) {
            val pomFile = hashDir.listFiles { file -> file.extension == "pom" }?.firstOrNull()
            if (pomFile != null) return pomFile
          }
        }
      }
      return null
    }

    private fun parsePomDependencies(pomFile: File): List<CatalogDependency> {
      val dependencies = mutableListOf<CatalogDependency>()
      try {
        val xml = pomFile.readText()
        val dependencyPattern =
            """<dependency>.*?<groupId>(.*?)</groupId>.*?<artifactId>(.*?)</artifactId>.*?<version>(.*?)</version>.*?</dependency>"""
                .toRegex(RegexOption.DOT_MATCHES_ALL)
        dependencyPattern.findAll(xml).forEach { match ->
          val group = match.groupValues[1].trim()
          val name = match.groupValues[2].trim()
          var version = match.groupValues[3].trim()
          val scopeMatch = """<scope>(.*?)</scope>""".toRegex().find(match.value)
          if (scopeMatch != null) {
            val scope = scopeMatch.groupValues[1].trim()
            if (scope in listOf("test", "provided")) {
              return@forEach
            }
          }
          if (version.startsWith("\${")) {
            return@forEach
          }
          val syntheticAlias = "$group-$name".replace(".", "-")
          dependencies.add(CatalogDependency(syntheticAlias, group, name, version))
        }
      } catch (e: Exception) {
        log.error("Failed to parse POM: {}", pomFile.name, e)
      }
      return dependencies
    }

    private fun parseVersionsSection(content: String): Map<String, String> {
      val versions = mutableMapOf<String, String>()
      val versionsMatch =
          """\[versions\](.*?)(?=\[|$)""".toRegex(RegexOption.DOT_MATCHES_ALL).find(content)
              ?: return versions
      val versionsSection = versionsMatch.groupValues[1]
      val linePattern = """(\w+)\s*=\s*(?:"([^"]+)"|'([^']+)')""".toRegex()
      linePattern.findAll(versionsSection).forEach { match ->
        val key = match.groupValues[1]
        val value = match.groupValues[2].ifEmpty { match.groupValues[3] }
        if (value.isNotEmpty()) {
          versions[key] = value
        }
      }
      return versions
    }

    private fun parseLibrariesSection(
        content: String,
        versions: Map<String, String>,
    ): List<CatalogDependency> {
      val libraries = mutableListOf<CatalogDependency>()
      val librariesMatch =
          """\[libraries\](.*?)(?=\[|$)""".toRegex(RegexOption.DOT_MATCHES_ALL).find(content)
              ?: run {
                log.warn("Could not find [libraries] section in catalog")
                return libraries
              }
      val librariesSection = librariesMatch.groupValues[1]
      val lines = librariesSection.lines()
      var i = 0
      while (i < lines.size) {
        val line = lines[i].trim()
        if (line.isEmpty() || line.startsWith("#")) {
          i++
          continue
        }
        if (line.contains("= {")) {
          val aliasMatch = """^([\w\-]+)\s*=\s*\{""".toRegex().find(line)
          if (aliasMatch == null) {
            i++
            continue
          }
          val alias = aliasMatch.groupValues[1]
          var group: String? = null
          var name: String? = null
          var version: String? = null
          if (line.contains("}")) {
            val moduleMatch = """module\s*=\s*"([^"]+)"""".toRegex().find(line)
            if (moduleMatch != null) {
              val moduleParts = moduleMatch.groupValues[1].split(":")
              if (moduleParts.size >= 2) {
                group = moduleParts[0]
                name = moduleParts[1]
                if (moduleParts.size >= 3) {
                  version = moduleParts[2]
                }
              }
            }
            """group\s*=\s*"([^"]+)"""".toRegex().find(line)?.let { group = it.groupValues[1] }
            """name\s*=\s*"([^"]+)"""".toRegex().find(line)?.let { name = it.groupValues[1] }
            """version\s*=\s*"([^"]+)"""".toRegex().find(line)?.let { version = it.groupValues[1] }
            """version\.ref\s*=\s*"([^"]+)"""".toRegex().find(line)?.let {
              val versionKey = it.groupValues[1]
              version = versions[versionKey]
              if (version == null) {
                log.warn("Version reference '{}' not found in [versions] for {}", versionKey, alias)
              }
            }
            if (group != null && name != null && version != null) {
              libraries.add(CatalogDependency(alias, group, name, version))
              log.trace("Parsed: {} -> {}", alias, "$group:$name:$version")
            }
          } else {
            i++
            while (i < lines.size) {
              val innerLine = lines[i].trim()
              if (innerLine.contains("}")) break
              """module\s*=\s*"([^"]+)"""".toRegex().find(innerLine)?.let {
                val moduleParts = it.groupValues[1].split(":")
                if (moduleParts.size >= 2) {
                  group = moduleParts[0]
                  name = moduleParts[1]
                  if (moduleParts.size >= 3) version = moduleParts[2]
                }
              }
              """group\s*=\s*"([^"]+)"""".toRegex().find(innerLine)?.let {
                group = it.groupValues[1]
              }
              """name\s*=\s*"([^"]+)"""".toRegex().find(innerLine)?.let { name = it.groupValues[1] }
              """version\s*=\s*"([^"]+)"""".toRegex().find(innerLine)?.let {
                version = it.groupValues[1]
              }
              """version\.ref\s*=\s*"([^"]+)"""".toRegex().find(innerLine)?.let {
                val versionKey = it.groupValues[1]
                version = versions[versionKey]
              }
              i++
            }
            if (group != null && name != null && version != null) {
              libraries.add(CatalogDependency(alias, group, name, version))
              log.trace("Parsed: {} -> {}", alias, "$group:$name:$version")
            }
          }
        }
        i++
      }
      log.info("Successfully parsed {} dependencies from version catalog", libraries.size)
      return libraries
    }

    private fun extractDependencyFromJarPath(jar: File): String? {
      try {
        // Gradle cache structure:
        // ~/.gradle/caches/modules-2/files-2.1/group/name/version/hash/file.jar
        val pathParts = jar.absolutePath.split(File.separator)
        val modulesIndex = pathParts.indexOfLast { it == "files-2.1" }
        if (modulesIndex >= 0 && pathParts.size > modulesIndex + 3) {
          val group = pathParts[modulesIndex + 1]
          val name = pathParts[modulesIndex + 2]
          val version = pathParts[modulesIndex + 3]
          return "$group:$name:$version"
        }
      } catch (e: Exception) {
        log.trace("Could not extract dependency info from jar path: {}", jar.name)
      }
      return null
    }

    private fun findJarInGradleCache(dep: CatalogDependency, cacheDir: File): List<File> {
      val artifactDir = File(cacheDir, "${dep.group}/${dep.name}")
      if (!artifactDir.exists()) {
        log.trace("Artifact dir not found: {}", artifactDir.absolutePath)
        return emptyList()
      }
      val versionDirs =
          artifactDir
              .listFiles { file -> file.isDirectory && isVersionMatch(file.name, dep.version) }
              ?.toList() ?: emptyList()
      if (versionDirs.isEmpty()) {
        log.trace("No version directories found for: {}", dep.toMavenCoordinate())
        return emptyList()
      }
      val jars = mutableListOf<File>()
      versionDirs.forEach { versionDir ->
        versionDir.listFiles()?.forEach { hashDir ->
          if (hashDir.isDirectory) {
            hashDir
                .listFiles { file ->
                  file.extension == "jar" &&
                      !file.name.contains("sources") &&
                      !file.name.contains("javadoc")
                }
                ?.forEach { jar ->
                  jars.add(jar)
                  log.trace("Found JAR: {}", jar.name)
                }
            hashDir
                .listFiles { file -> file.extension == "aar" }
                ?.forEach { aar ->
                  val extractedJar = extractClassesJarFromAAR(aar)
                  if (extractedJar != null) {
                    jars.add(extractedJar)
                    log.trace("Extracted classes.jar from AAR: {}", aar.name)
                  }
                }
          }
        }
      }
      return jars
    }

    private fun isVersionMatch(dirName: String, requestedVersion: String): Boolean {
      if (dirName == requestedVersion) return true
      if (requestedVersion.contains("+")) {
        val prefix = requestedVersion.substringBefore("+")
        return dirName.startsWith(prefix)
      }
      return false
    }

    private fun extractClassesJarFromAAR(aarFile: File): File? {
      try {
        val extractDir = File(aarFile.parentFile, "${aarFile.nameWithoutExtension}-extracted")
        val classesJar = File(extractDir, "classes.jar")
        if (classesJar.exists()) {
          return classesJar
        }
        extractDir.mkdirs()
        java.util.zip.ZipFile(aarFile).use { zip ->
          val classesEntry = zip.getEntry("classes.jar")
          if (classesEntry != null) {
            zip.getInputStream(classesEntry).use { input ->
              classesJar.outputStream().use { output -> input.copyTo(output) }
            }
            log.debug("Extracted classes.jar from: {}", aarFile.name)
            return classesJar
          } else {
            log.warn("No classes.jar found in AAR: {}", aarFile.name)
          }
        }
      } catch (e: Exception) {
        log.error("Failed to extract classes.jar from AAR: {}", aarFile.name, e)
      }
      return null
    }

    /** Clear all caches (call when libs.versions.toml changes) */
    fun clearCache() {
      catalogCache.clear()
      transitiveCache.clear()
      pomCache.clear()
      log.info("Cleared all version catalog caches")
    }
  }

  data class CatalogCacheEntry(
      val timestamp: Long,
      val buildGradleTimestamp: Long,
      val resolvedJars: Set<File>,
  )

  data class CatalogDependency(
      val alias: String,
      val group: String,
      val name: String,
      val version: String,
  ) {
    fun toMavenCoordinate(): String = "$group:$name:$version"

    fun toGroupArtifactKey(): String = "$group:$name"
  }
}
