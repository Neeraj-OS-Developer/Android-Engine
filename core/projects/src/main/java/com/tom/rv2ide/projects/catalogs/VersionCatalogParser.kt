package com.tom.rv2ide.projects.catalogs

import java.io.File

data class CatalogLibrary(
    val alias: String,
    val group: String,
    val name: String,
    val version: String?,
)

data class VersionCatalog(
    val file: File,
    val versions: Map<String, String>,
    val libraries: Map<String, CatalogLibrary>,
) {

  fun resolveLibrary(alias: String): String? {
    val library = libraries[alias] ?: return null
    val version = library.version ?: return "${library.group}:${library.name}"
    return "${library.group}:${library.name}:$version"
  }
}

class VersionCatalogParser {

  fun parse(catalogFile: File): VersionCatalog? {
    if (!catalogFile.exists() || !catalogFile.name.endsWith(".toml")) {
      return null
    }

    return try {
      val content = catalogFile.readText()
      parseToml(content, catalogFile)
    } catch (e: Exception) {
      null
    }
  }

  private fun parseToml(content: String, file: File): VersionCatalog {
    val versions = mutableMapOf<String, String>()
    val libraries = mutableMapOf<String, CatalogLibrary>()

    var currentSection = ""

    content.lines().forEach { line ->
      val trimmed = line.trim()

      if (trimmed.startsWith("#") || trimmed.isEmpty()) {
        return@forEach
      }

      if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
        currentSection = trimmed.substring(1, trimmed.length - 1)
        return@forEach
      }

      if (currentSection == "versions") {
        parseVersionEntry(trimmed, versions)
      }

      if (currentSection == "libraries") {
        parseLibraryEntry(trimmed, libraries, versions)
      }
    }

    return VersionCatalog(file, versions, libraries)
  }

  private fun parseVersionEntry(line: String, versions: MutableMap<String, String>) {
    val parts = line.split("=", limit = 2)
    if (parts.size != 2) return

    val key = parts[0].trim()
    val value = parts[1].trim().removeSurrounding("\"")
    versions[key] = value
  }

  private fun parseLibraryEntry(
      line: String,
      libraries: MutableMap<String, CatalogLibrary>,
      versions: Map<String, String>,
  ) {
    val parts = line.split("=", limit = 2)
    if (parts.size != 2) return

    val alias = parts[0].trim()
    val definition = parts[1].trim()

    val library =
        when {
          definition.startsWith("{") -> parseStructuredLibrary(definition, versions, alias)
          definition.startsWith("\"") -> parseStringLibrary(definition, alias)
          else -> null
        }

    library?.let { libraries[alias] = it }
  }

  private fun parseStructuredLibrary(
      definition: String,
      versions: Map<String, String>,
      alias: String,
  ): CatalogLibrary? {
    val content = definition.removeSurrounding("{", "}").trim()
    val properties = mutableMapOf<String, String>()

    var currentKey = ""
    var currentValue = StringBuilder()
    var inQuotes = false
    var expectValue = false

    content.forEach { char ->
      when {
        char == '"' -> inQuotes = !inQuotes
        char == '=' && !inQuotes -> expectValue = true
        char == ',' && !inQuotes -> {
          if (currentKey.isNotEmpty()) {
            properties[currentKey.trim()] = currentValue.toString().trim().removeSurrounding("\"")
            currentKey = ""
            currentValue = StringBuilder()
            expectValue = false
          }
        }
        expectValue -> currentValue.append(char)
        !inQuotes && char.isWhitespace() -> {}
        else -> {
          if (!expectValue) currentKey += char else currentValue.append(char)
        }
      }
    }

    if (currentKey.isNotEmpty()) {
      properties[currentKey.trim()] = currentValue.toString().trim().removeSurrounding("\"")
    }

    val group: String
    val name: String

    if (properties.containsKey("module")) {
      val moduleParts = properties["module"]!!.split(":")
      if (moduleParts.size < 2) return null
      group = moduleParts[0]
      name = moduleParts[1]
    } else {
      group = properties["group"] ?: return null
      name = properties["name"] ?: return null
    }

    val version =
        when {
          properties.containsKey("version.ref") -> versions[properties["version.ref"]]
          properties.containsKey("version") -> properties["version"]
          else -> null
        }

    return CatalogLibrary(alias, group, name, version)
  }

  private fun parseStringLibrary(definition: String, alias: String): CatalogLibrary? {
    val coords = definition.removeSurrounding("\"").split(":")
    if (coords.size < 2) return null

    return CatalogLibrary(
        alias = alias,
        group = coords[0],
        name = coords[1],
        version = coords.getOrNull(2),
    )
  }
}
