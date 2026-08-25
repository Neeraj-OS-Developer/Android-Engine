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

package com.tom.rv2ide.lsp.kotlin

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.tom.rv2ide.lsp.api.describeSnippet
import com.tom.rv2ide.lsp.models.Command
import com.tom.rv2ide.lsp.models.CompletionItem
import com.tom.rv2ide.lsp.models.CompletionItemKind
import com.tom.rv2ide.lsp.models.InsertTextFormat
import com.tom.rv2ide.lsp.models.MatchLevel
import com.tom.rv2ide.lsp.models.TextEdit
import com.tom.rv2ide.models.Position
import com.tom.rv2ide.models.Range
import java.util.concurrent.Executors
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory

/*
 * @author Mohammed-baqer-null @ https://github.com/Mohammed-baqer-null
 */

class KotlinCompletionConverter {

  companion object {
    private val log = LoggerFactory.getLogger(KotlinCompletionConverter::class.java)
  }

  private val snippetTransformer = SnippetTransformer()
  private val importResolver = KotlinImportResolver()

  private val cpuDispatcher =
      Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
          .asCoroutineDispatcher()

  private val completionCache =
      java.util.concurrent.ConcurrentHashMap<String, List<CompletionItem>>()

  private fun getCacheKey(fileContent: String, position: Int): String {
    return "${fileContent.hashCode()}_$position"
  }

  private var javaCompilerBridge: KotlinJavaCompilerBridge? = null

  fun setJavaCompilerBridge(bridge: KotlinJavaCompilerBridge) {
    this.javaCompilerBridge = bridge
  }

  suspend fun convertWithClasspathEnhancement(
      itemsArray: JsonArray,
      fileContent: String,
      prefix: String,
  ): List<CompletionItem> = withContext(cpuDispatcher) {
      KslLogs.debug("Converting {} items with classpath enhancement", itemsArray.size())
  
      // Process BOTH in parallel
      val lspItemsDeferred = async { convertFast(itemsArray, fileContent, prefix) }
      
      val classpathItemsDeferred = async {
          if (prefix.length >= 2 && javaCompilerBridge != null) {
              getClasspathCompletions(prefix, fileContent)
          } else {
              emptyList()
          }
      }
      
      val lspItems = lspItemsDeferred.await()
      val classpathItems = classpathItemsDeferred.await()
  
      val allItems = (lspItems + classpathItems).distinctBy { "${it.ideLabel}:${it.detail}" }
  
      KslLogs.debug("Total items after classpath enhancement: {}", allItems.size)
      allItems
  }

  /** Get completion items from classpath (Java compiler) */
  private fun getClasspathCompletions(prefix: String, fileContent: String): List<CompletionItem> {
    val bridge = javaCompilerBridge ?: return emptyList()

    return try {
      val classes = bridge.findClassesByPrefix(prefix)

      classes.map { classInfo ->
        val needsImport =
            importResolver.needsImportForClass(
                classInfo.simpleName,
                classInfo.fullyQualifiedName,
                fileContent,
            )

        val additionalEdits =
            if (needsImport) {
              val (line, importText) =
                  importResolver.generateImportEdit(classInfo.fullyQualifiedName, fileContent)
              listOf(
                  com.tom.rv2ide.lsp.models.TextEdit(
                      range =
                          com.tom.rv2ide.models.Range(
                              start = com.tom.rv2ide.models.Position(line, 0),
                              end = com.tom.rv2ide.models.Position(line, 0),
                          ),
                      newText = importText,
                  )
              )
            } else {
              null
            }

        CompletionItem(
            ideLabel = classInfo.simpleName,
            detail = classInfo.fullyQualifiedName,
            insertText = classInfo.simpleName,
            insertTextFormat = null,
            sortText = classInfo.simpleName,
            command = null,
            completionKind = com.tom.rv2ide.lsp.models.CompletionItemKind.CLASS,
            matchLevel = com.tom.rv2ide.lsp.models.MatchLevel.CASE_SENSITIVE_PREFIX,
            additionalTextEdits = additionalEdits,
            data = null,
        )
      }
    } catch (e: Exception) {
      KslLogs.error("Failed to get classpath completions", e)
      emptyList()
    }
  }

  suspend fun convertWithCache(
      itemsArray: JsonArray,
      fileContent: String,
      position: Int,
      prefix: String = "",
  ): List<CompletionItem> {
    val cacheKey = getCacheKey(fileContent, position)

    return completionCache.getOrPut(cacheKey) { convert(itemsArray, fileContent, prefix) }
  }

  suspend fun convert(
      itemsArray: JsonArray,
      fileContent: String,
      prefix: String,
  ): List<CompletionItem> =
      withContext(cpuDispatcher) {
        KslLogs.debug("Received {} completion items", itemsArray.size())

        // Process items in parallel
        val filteredItems =
            itemsArray
                .map { element ->
                  async {
                    try {
                      val item = element.asJsonObject
                      val converted = convertItem(item, fileContent, prefix)

                      if (
                          converted.ideLabel.isBlank() ||
                              converted.ideLabel == "K" ||
                              converted.ideLabel == "Keyword"
                      ) {
                        null
                      } else {
                        KslLogs.trace(
                            "Converted completion item: label='{}', detail='{}', kind={}",
                            converted.ideLabel,
                            converted.detail,
                            converted.completionKind,
                        )
                        converted
                      }
                    } catch (e: Exception) {
                      KslLogs.warn("Failed to convert completion item: {}", e.message)
                      null
                    }
                  }
                }
                .awaitAll()
                .filterNotNull()

        KslLogs.debug("Filtered to {} useful completion items", filteredItems.size)
        filteredItems
      }

  fun cleanup() {
    cpuDispatcher.close()
  }

  suspend fun convertFast(
      itemsArray: JsonArray,
      fileContent: String,
      prefix: String,
  ): List<CompletionItem> = withContext(cpuDispatcher) {
      KslLogs.debug("Fast converting {} items", itemsArray.size())
  
      val cpuCount = Runtime.getRuntime().availableProcessors()
      val itemsList = itemsArray.toList()
      val chunkSize = (itemsList.size / cpuCount).coerceAtLeast(10)
      
      val results = itemsList.chunked(chunkSize).map { chunk ->
          async {
              val chunkResults = mutableListOf<CompletionItem>()
              for (element in chunk) {
                  try {
                      val item = element.asJsonObject
                      val converted = convertItemFast(item, fileContent, prefix)
  
                      if (converted.ideLabel.isNotBlank() &&
                          converted.ideLabel != "K" &&
                          converted.ideLabel != "Keyword") {
                          chunkResults.add(converted)
                      }
                  } catch (e: Exception) {
                      // Skip
                  }
              }
              chunkResults
          }
      }.awaitAll().flatten()
  
      KslLogs.debug("Converted {} items", results.size)
      results
  }

  private fun convertItemFast(
      item: JsonObject,
      fileContent: String,
      prefix: String,
  ): CompletionItem {
    val label = item.get("label")?.asString ?: ""
    val detail = item.get("detail")?.asString ?: ""
    var insertText = item.get("insertText")?.asString
    val sortText = item.get("sortText")?.asString
    val kind = item.get("kind")?.asInt ?: 1
    val insertTextFormatValue = item.get("insertTextFormat")?.asInt

    val isSnippet = insertTextFormatValue == 2
    val snippetMetadata =
        if (isSnippet && insertText != null) {
          prepareSnippetMetadata(insertText, detail, label)
        } else {
          null
        }
    insertText = snippetMetadata?.text ?: insertText
    val insertFormat = if (snippetMetadata != null) InsertTextFormat.SNIPPET else null
    val command = snippetMetadata?.command

    // Rest of the code remains the same...
    val additionalTextEdits = item.getAsJsonArray("additionalTextEdits")
    val importEdit = extractImportFromAdditionalEdits(additionalTextEdits)

    val additionalEdits = mutableListOf<TextEdit>()

    if (importEdit != null) {
      val (line, importText) = parseImportStatement(importEdit, fileContent)
      additionalEdits.add(
          TextEdit(
              range = Range(start = Position(line, 0), end = Position(line, 0)),
              newText = importText,
          )
      )
    } else {
      val tempItem =
          CompletionItem(
              ideLabel = label,
              detail = detail,
              insertText = insertText,
              insertTextFormat = null,
              sortText = sortText,
              command = null,
              completionKind = mapCompletionKind(kind),
              matchLevel = MatchLevel.NO_MATCH,
              additionalTextEdits = null,
              data = null,
          )

      val fqn = importResolver.needsImport(tempItem, fileContent)
      if (fqn != null) {
        val (line, importText) = importResolver.generateImportEdit(fqn, fileContent)
        additionalEdits.add(
            TextEdit(
                range = Range(start = Position(line, 0), end = Position(line, 0)),
                newText = importText,
            )
        )
      }
    }

    return CompletionItem(
        ideLabel = label,
        detail = detail,
        insertText = insertText,
        insertTextFormat = insertFormat,
        sortText = sortText,
        command = command,
        completionKind = mapCompletionKind(kind),
        matchLevel = MatchLevel.NO_MATCH,
        additionalTextEdits = if (additionalEdits.isNotEmpty()) additionalEdits else null,
        data = null,
    ).apply {
      snippetMetadata?.let {
        snippetDescription = describeSnippet(prefix, allowCommandExecution = it.allowCommand)
      }
    }
  }

  private fun convertItem(
      item: JsonObject,
      fileContent: String,
      prefix: String,
  ): CompletionItem {
    val label = item.get("label")?.asString ?: ""
    val detail = item.get("detail")?.asString ?: ""
    var insertText = item.get("insertText")?.asString
    val sortText = item.get("sortText")?.asString
    val kind = item.get("kind")?.asInt ?: 1
    val insertTextFormat = item.get("insertTextFormat")?.asInt

    val isSnippet = insertTextFormat == 2
    val snippetMetadata =
        if (isSnippet && insertText != null) {
          prepareSnippetMetadata(insertText, detail, label)
        } else {
          null
        }
    insertText = snippetMetadata?.text ?: insertText
    val insertFormat = if (snippetMetadata != null) InsertTextFormat.SNIPPET else null
    val command = snippetMetadata?.command

    // Extract import info from LSP server's additionalTextEdits
    val additionalTextEdits = item.getAsJsonArray("additionalTextEdits")
    val serverImportEdit = extractImportFromAdditionalEdits(additionalTextEdits)

    // Generate additional edits if needed
    val additionalEdits = mutableListOf<TextEdit>()

    if (serverImportEdit != null) {
      // Use server-provided import
      val (line, importText) = parseImportStatement(serverImportEdit, fileContent)
      additionalEdits.add(
          TextEdit(
              range = Range(start = Position(line, 0), end = Position(line, 0)),
              newText = importText,
          )
      )
      KslLogs.debug("Using server import: {}", importText.trim())
    } else {
      // Check if we need to generate import ourselves
      val tempItem =
          CompletionItem(
              ideLabel = label,
              detail = detail,
              insertText = insertText,
              insertTextFormat = null,
              sortText = sortText,
              command = null,
              completionKind = mapCompletionKind(kind),
              matchLevel = MatchLevel.NO_MATCH,
              additionalTextEdits = null,
              data = null,
          )

      val fqn = importResolver.needsImport(tempItem, fileContent)
      if (fqn != null) {
        val (line, importText) = importResolver.generateImportEdit(fqn, fileContent)
        additionalEdits.add(
            TextEdit(
                range = Range(start = Position(line, 0), end = Position(line, 0)),
                newText = importText,
            )
        )
        KslLogs.debug("Generated import: {}", importText.trim())
      }
    }

    return CompletionItem(
        ideLabel = label,
        detail = detail,
        insertText = insertText,
        insertTextFormat = insertFormat,
        sortText = sortText,
        command = command,
        completionKind = mapCompletionKind(kind),
        matchLevel = MatchLevel.NO_MATCH,
        additionalTextEdits = if (additionalEdits.isNotEmpty()) additionalEdits else null,
        data = null,
    ).apply {
      snippetMetadata?.let {
        snippetDescription = describeSnippet(prefix, allowCommandExecution = it.allowCommand)
      }
    }
  }

  /** Extracts import statement from LSP server's additionalTextEdits */
  private fun extractImportFromAdditionalEdits(edits: JsonArray?): String? {
    if (edits == null || edits.size() == 0) return null

    try {
      for (edit in edits) {
        val editObj = edit.asJsonObject
        val newText = editObj.get("newText")?.asString ?: continue

        // Check if this is an import statement
        val trimmed = newText.trim()
        if (trimmed.startsWith("import ")) {
          return trimmed
        }
      }
    } catch (e: Exception) {
      log.error("Failed to extract import from additionalTextEdits", e)
    }

    return null
  }

  /** Parses import statement and determines insertion position */
  private fun parseImportStatement(
      importStatement: String,
      fileContent: String,
  ): Pair<Int, String> {
    val cleanImport = importStatement.trim()
    val lines = fileContent.split("\n")

    var insertLine = 0
    var lastImportIndex = -1
    var packageIndex = -1

    for ((index, line) in lines.withIndex()) {
      val trimmed = line.trim()
      when {
        trimmed.startsWith("package ") -> packageIndex = index
        trimmed.startsWith("import ") -> lastImportIndex = index
        trimmed.isNotEmpty() &&
            !trimmed.startsWith("//") &&
            !trimmed.startsWith("/*") &&
            lastImportIndex >= 0 -> break
      }
    }

    insertLine =
        when {
          lastImportIndex >= 0 -> lastImportIndex + 1
          packageIndex >= 0 -> packageIndex + 2
          else -> 0
        }

    return Pair(insertLine, "$cleanImport\n")
  }

  private fun extractParameterNamesFromDetail(detail: String, label: String): List<String> {
    var signature = detail

    if (!detail.contains("(") && label.contains("(")) {
      signature = label
    }

    return snippetTransformer.extractParameterNames(signature)
  }

  private data class SnippetMetadata(
      val text: String,
      val allowCommand: Boolean,
      val command: Command?,
  )

  private fun prepareSnippetMetadata(
      rawSnippet: String,
      detail: String,
      label: String,
  ): SnippetMetadata {
    val parameterNames = extractParameterNamesFromDetail(detail, label)
    var snippetText =
        if (parameterNames.isNotEmpty() && rawSnippet.contains("\${")) {
          snippetTransformer.transformSnippet(rawSnippet, parameterNames)
        } else {
          rawSnippet
        }

    snippetText = simplifyCallSnippet(snippetText) ?: snippetText
    snippetText = ensureTerminalTabStop(snippetText)

    val command = createSignatureCommand(snippetText)
    return SnippetMetadata(text = snippetText, allowCommand = command != null, command = command)
  }

  private fun simplifyCallSnippet(snippet: String): String? {
    val openIndex = snippet.indexOf('(')
    if (openIndex <= 0) return null
    if (snippet[openIndex - 1].isWhitespace()) return null

    val closeIndex = findMatchingParen(snippet, openIndex)
    if (closeIndex == -1) return null

    val inner = snippet.substring(openIndex + 1, closeIndex)
    val placeholderOnly =
        inner
            .replace("""\$\{(\d+)(:[^}]*)?\}""".toRegex(), "")
            .replace("""\$\d+""".toRegex(), "")
            .replace(",", "")
            .replace(" ", "")
            .replace("\t", "")
            .replace("\n", "")
            .isEmpty()

    if (!placeholderOnly) return null

    val afterParen = snippet.substring(closeIndex + 1)
    val cleanedAfterParen =
        afterParen.replaceFirst("""^\s*(\$\{0(?::[^}]*)?\}|\$0)""".toRegex(), "")

    return snippet.substring(0, openIndex + 1) + "\$0)" + cleanedAfterParen
  }

  private fun findMatchingParen(text: String, openIndex: Int): Int {
    var depth = 0
    for (i in openIndex until text.length) {
      val ch = text[i]
      if (ch == '(') {
        depth++
      } else if (ch == ')') {
        depth--
        if (depth == 0) return i
      }
    }
    return -1
  }

  private fun ensureTerminalTabStop(snippet: String): String {
    val hasTerminalTabStop = snippet.contains("\$0") || snippet.contains("\${0")
    return if (hasTerminalTabStop) snippet else snippet + "\$0"
  }

  private fun createSignatureCommand(snippetText: String): Command? {
    return if (snippetText.contains("(")) {
      Command("Trigger Parameter Hints", Command.TRIGGER_PARAMETER_HINTS)
    } else {
      null
    }
  }

  private fun mapCompletionKind(kind: Int): CompletionItemKind {
    return when (kind) {
      1 -> CompletionItemKind.NONE
      2 -> CompletionItemKind.METHOD
      3 -> CompletionItemKind.FUNCTION
      4 -> CompletionItemKind.CONSTRUCTOR
      5 -> CompletionItemKind.FIELD
      6 -> CompletionItemKind.VARIABLE
      7 -> CompletionItemKind.CLASS
      8 -> CompletionItemKind.INTERFACE
      9 -> CompletionItemKind.MODULE
      10 -> CompletionItemKind.PROPERTY
      12 -> CompletionItemKind.VALUE
      13 -> CompletionItemKind.ENUM
      14 -> CompletionItemKind.KEYWORD
      15 -> CompletionItemKind.SNIPPET
      20 -> CompletionItemKind.ENUM_MEMBER
      25 -> CompletionItemKind.TYPE_PARAMETER
      else -> CompletionItemKind.NONE
    }
  }
}
