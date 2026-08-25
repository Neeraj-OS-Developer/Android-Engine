package com.tom.rv2ide.tooling.impl.sync

import com.moandjiezana.toml.Toml
import java.io.File
import java.io.Serializable
import org.slf4j.LoggerFactory

data class VersionCatalogInfo(
    val catalogName: String = "libs",
    val libraries: Map<String, CatalogLibrary>,
    val versions: Map<String, String>,
    val plugins: Map<String, CatalogPlugin>,
) : Serializable {
  private val serialVersionUID = 1L
}

data class CatalogLibrary(
    val name: String,
    val group: String,
    val artifact: String,
    val version: String,
    val versionRef: String?,
    val catalogReference: String,
) : Serializable {
  private val serialVersionUID = 1L
}

data class CatalogPlugin(
    val name: String,
    val id: String,
    val version: String?,
    val versionRef: String?,
    val catalogReference: String,
) : Serializable {
  private val serialVersionUID = 1L
}

class VersionCatalogParser {

  companion object {
    private val log = LoggerFactory.getLogger(VersionCatalogParser::class.java)
  }

  /**
   * Parses the version catalog (libs.versions.toml) from the project directory. The catalog is
   * typically located at gradle/libs.versions.toml
   */
  fun parseVersionCatalog(projectDir: File, catalogName: String = "libs"): VersionCatalogInfo? {
    val catalogFile = File(projectDir, "gradle/libs.versions.toml")

    System.err.println("Looking for version catalog at: ${catalogFile.absolutePath}")
    System.err.println("Catalog file exists: ${catalogFile.exists()}")

    if (!catalogFile.exists()) {
      System.err.println("Version catalog not found at: ${catalogFile.absolutePath}")
      return null
    }

    return try {
      System.err.println("Attempting to parse TOML catalog...")
      val result = parseTomlCatalog(catalogFile, catalogName)
      System.err.println("Successfully parsed catalog with ${result.libraries.size} libraries")
      result
    } catch (e: Exception) {
      System.err.println("Failed to parse version catalog: ${catalogFile.absolutePath}")
      e.printStackTrace(System.err)
      null
    }
  }

  private fun parseTomlCatalog(file: File, catalogName: String): VersionCatalogInfo {
    // Read the file content and parse manually instead of using Toml library
    val content = file.readText()

    // Parse [versions] section
    val versions = parseVersionsSection(content)

    // Parse [libraries] section
    val libraries = parseLibrariesSection(content, versions, catalogName)

    // Parse [plugins] section
    val plugins = parsePluginsSection(content, versions, catalogName)

    System.err.println(
        "Parsed version catalog: ${libraries.size} libraries, ${plugins.size} plugins"
    )

    return VersionCatalogInfo(
        catalogName = catalogName,
        libraries = libraries,
        versions = versions,
        plugins = plugins,
    )
  }

  private fun parseVersionsSection(content: String): Map<String, String> {
    val versions = mutableMapOf<String, String>()
    var inVersionsSection = false

    content.lines().forEach { line ->
      val trimmed = line.trim()

      // Check for section headers
      if (trimmed.startsWith("[")) {
        inVersionsSection = trimmed == "[versions]"
        return@forEach
      }

      // Skip comments and empty lines
      if (trimmed.isEmpty() || trimmed.startsWith("#")) {
        return@forEach
      }

      if (inVersionsSection) {
        val parts = trimmed.split("=", limit = 2)
        if (parts.size == 2) {
          val key = parts[0].trim()
          val value = parts[1].trim().removeSurrounding("\"")
          versions[key] = value
        }
      }
    }

    return versions
  }

  private fun parseLibrariesSection(
      content: String,
      versions: Map<String, String>,
      catalogName: String,
  ): Map<String, CatalogLibrary> {
    val libraries = mutableMapOf<String, CatalogLibrary>()
    var inLibrariesSection = false

    content.lines().forEach { line ->
      val trimmed = line.trim()

      // Check for section headers
      if (trimmed.startsWith("[")) {
        inLibrariesSection = trimmed == "[libraries]"
        return@forEach
      }

      // Skip comments and empty lines
      if (trimmed.isEmpty() || trimmed.startsWith("#")) {
        return@forEach
      }

      if (inLibrariesSection) {
        parseLibraryLine(trimmed, versions, catalogName)?.let { library ->
          libraries[library.name] = library
        }
      }
    }

    return libraries
  }

  private fun parseLibraryLine(
      line: String,
      versions: Map<String, String>,
      catalogName: String,
  ): CatalogLibrary? {
    val parts = line.split("=", limit = 2)
    if (parts.size != 2) return null

    val alias = parts[0].trim()
    val definition = parts[1].trim()

    return when {
      // String format: "group:artifact:version"
      definition.startsWith("\"") -> {
        val coords = definition.removeSurrounding("\"").split(":")
        if (coords.size < 2) return null

        CatalogLibrary(
            name = alias,
            group = coords[0],
            artifact = coords[1],
            version = coords.getOrNull(2) ?: "unknown",
            versionRef = null,
            catalogReference = "$catalogName.$alias",
        )
      }

      // Structured format: { group = "...", name = "...", version.ref = "..." }
      definition.startsWith("{") -> {
        parseStructuredLibrary(definition, versions, alias, catalogName)
      }

      else -> null
    }
  }

  private fun parseStructuredLibrary(
      definition: String,
      versions: Map<String, String>,
      alias: String,
      catalogName: String,
  ): CatalogLibrary? {
    // Remove { and } and parse the content
    val content = definition.removeSurrounding("{", "}").trim()

    var group: String? = null
    var name: String? = null
    var version: String? = null
    var versionRef: String? = null
    var module: String? = null

    // Simple state machine to parse key-value pairs
    var currentKey = StringBuilder()
    var currentValue = StringBuilder()
    var inQuotes = false
    var inKey = true

    var i = 0
    while (i < content.length) {
      val char = content[i]

      when {
        char == '"' -> {
          inQuotes = !inQuotes
        }
        char == '=' && !inQuotes -> {
          inKey = false
        }
        char == ',' && !inQuotes -> {
          // End of key-value pair
          val key = currentKey.toString().trim()
          val value = currentValue.toString().trim().removeSurrounding("\"")

          when (key) {
            "group" -> group = value
            "name" -> name = value
            "module" -> module = value
            "version" -> version = value
            "version.ref" -> versionRef = value
          }

          currentKey = StringBuilder()
          currentValue = StringBuilder()
          inKey = true
        }
        inKey -> {
          currentKey.append(char)
        }
        else -> {
          currentValue.append(char)
        }
      }

      i++
    }

    // Handle last key-value pair
    if (currentKey.isNotEmpty()) {
      val key = currentKey.toString().trim()
      val value = currentValue.toString().trim().removeSurrounding("\"")

      when (key) {
        "group" -> group = value
        "name" -> name = value
        "module" -> module = value
        "version" -> version = value
        "version.ref" -> versionRef = value
      }
    }

    // Handle module notation
    if (module != null) {
      val parts = module.split(":")
      if (parts.size >= 2) {
        group = parts[0]
        name = parts[1]
      }
    }

    if (group == null || name == null) {
      return null
    }

    // Resolve version reference
    val resolvedVersion =
        when {
          versionRef != null -> versions[versionRef] ?: versionRef
          version != null -> version
          else -> "unknown"
        }

    return CatalogLibrary(
        name = alias,
        group = group,
        artifact = name,
        version = resolvedVersion,
        versionRef = versionRef,
        catalogReference = "$catalogName.$alias",
    )
  }

  private fun parsePluginsSection(
      content: String,
      versions: Map<String, String>,
      catalogName: String,
  ): Map<String, CatalogPlugin> {
    val plugins = mutableMapOf<String, CatalogPlugin>()
    var inPluginsSection = false

    content.lines().forEach { line ->
      val trimmed = line.trim()

      // Check for section headers
      if (trimmed.startsWith("[")) {
        inPluginsSection = trimmed == "[plugins]"
        return@forEach
      }

      // Skip comments and empty lines
      if (trimmed.isEmpty() || trimmed.startsWith("#")) {
        return@forEach
      }

      if (inPluginsSection) {
        // Similar parsing logic for plugins
        // For now, just skip
      }
    }

    return plugins
  }

  private fun parseVersionsSection(toml: Toml): Map<String, String> {
    val versionsTable = toml.getTable("versions") ?: return emptyMap()

    return versionsTable.toMap().mapValues { (_, value) ->
      when (value) {
        is String -> value
        else -> value.toString()
      }
    }
  }

  private fun parseLibraryEntry(
      name: String,
      librariesTable: Toml,
      versions: Map<String, String>,
      catalogName: String,
  ): CatalogLibrary? {
    // Use getString for direct string values, getTable for table values
    val stringValue = librariesTable.getString(name)

    if (stringValue != null) {
      // String format: "group:artifact:version"
      return parseStringFormat(name, stringValue, catalogName)
    }

    // Table format: { group = "...", name = "...", version = "..." }
    val tableValue = librariesTable.getTable(name)
    if (tableValue != null) {
      return parseTableFormat(name, tableValue, versions, catalogName)
    }

    log.warn("Unknown library format for '$name'")
    return null
  }

  private fun parseStringFormat(
      name: String,
      coords: String,
      catalogName: String,
  ): CatalogLibrary? {
    val parts = coords.split(":")
    if (parts.size < 3) {
      log.warn("Invalid library coordinates for '$name': $coords")
      return null
    }

    return CatalogLibrary(
        name = name,
        group = parts[0],
        artifact = parts[1],
        version = parts[2],
        versionRef = null,
        catalogReference = "$catalogName.$name",
    )
  }

  private fun parseTableFormat(
      name: String,
      table: Toml,
      versions: Map<String, String>,
      catalogName: String,
  ): CatalogLibrary? {
    val group = table.getString("group")
    val artifact = table.getString("name")

    if (group == null || artifact == null) {
      log.warn("Library '$name' missing group or artifact name")
      return null
    }

    // Handle version reference or direct version
    val directVersion = table.getString("version")
    if (directVersion != null) {
      return CatalogLibrary(
          name = name,
          group = group,
          artifact = artifact,
          version = directVersion,
          versionRef = null,
          catalogReference = "$catalogName.$name",
      )
    }

    // Check for version.ref
    val versionTable = table.getTable("version")
    if (versionTable != null) {
      val ref = versionTable.getString("ref")
      val resolvedVersion = ref?.let { versions[it] } ?: ref ?: "unknown"

      return CatalogLibrary(
          name = name,
          group = group,
          artifact = artifact,
          version = resolvedVersion,
          versionRef = ref,
          catalogReference = "$catalogName.$name",
      )
    }

    log.warn("Library '$name' has no version information")
    return null
  }

  private fun parsePluginEntry(
      name: String,
      pluginsTable: Toml,
      versions: Map<String, String>,
      catalogName: String,
  ): CatalogPlugin? {
    // Try string format first
    val stringValue = pluginsTable.getString(name)
    if (stringValue != null) {
      val parts = stringValue.split(":")
      return CatalogPlugin(
          name = name,
          id = parts.getOrNull(0) ?: stringValue,
          version = parts.getOrNull(1),
          versionRef = null,
          catalogReference = "$catalogName.plugins.$name",
      )
    }

    // Try table format
    val tableValue = pluginsTable.getTable(name)
    if (tableValue != null) {
      val id = tableValue.getString("id") ?: return null

      // Check for direct version
      val directVersion = tableValue.getString("version")
      if (directVersion != null) {
        return CatalogPlugin(
            name = name,
            id = id,
            version = directVersion,
            versionRef = null,
            catalogReference = "$catalogName.plugins.$name",
        )
      }

      // Check for version.ref
      val versionTable = tableValue.getTable("version")
      if (versionTable != null) {
        val ref = versionTable.getString("ref")
        val resolvedVersion = ref?.let { versions[it] }

        return CatalogPlugin(
            name = name,
            id = id,
            version = resolvedVersion,
            versionRef = ref,
            catalogReference = "$catalogName.plugins.$name",
        )
      }

      // Plugin without version
      return CatalogPlugin(
          name = name,
          id = id,
          version = null,
          versionRef = null,
          catalogReference = "$catalogName.plugins.$name",
      )
    }

    return null
  }
}
