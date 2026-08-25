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

import org.slf4j.LoggerFactory

/**
 * Transforms snippet placeholders with actual parameter names and parameter position comments
 * Example: "Toast.makeText(${1:p0}, ${2:p1}, ${3:p2})" -> "Toast.makeText(context = @param 1
 * message = @param 2, length = @param 3 )"
 *
 * @author Mohammed-baqer-null @ https://github.com/Mohammed-baqer-null
 */
class SnippetTransformer {

  companion object {
    private val log = LoggerFactory.getLogger(SnippetTransformer::class.java)

    // Regex to match snippet placeholders like ${1:p0}, ${2:p1}, etc.
    private val SNIPPET_PLACEHOLDER_REGEX = """\$\{(\d+):([^}]+)\}""".toRegex()
  }

  fun transformSnippet(insertText: String, parameterNames: List<String>?): String {
    return SNIPPET_PLACEHOLDER_REGEX.replace(insertText) { matchResult ->
      val tabstop = matchResult.groupValues[1]
      val placeholder = matchResult.groupValues[2]

      val replacement =
          when {
            isBlockPlaceholder(placeholder) -> "\n"
            parameterNames.isNullOrEmpty() -> placeholder
            else -> {
              val index = tabstop.toIntOrNull()?.minus(1) ?: -1
              if (index in parameterNames.indices) parameterNames[index] else placeholder
            }
          }

      "\${$tabstop:${escapePlaceholder(replacement)}}"
    }
  }

  private fun escapePlaceholder(text: String): String {
    return text.replace("\\", "\\\\").replace("$", "\\$").replace("}", "\\}")
  }

  private fun isBlockPlaceholder(placeholder: String): Boolean {
    return placeholder.matches(Regex("(block|lambda|action|init|body|builder)"))
  }

  /**
   * Extracts parameter names from a method signature Example: "makeText(context: Context, message:
   * CharSequence, length: Int)" -> ["context", "message", "length"]
   */
  fun extractParameterNames(signature: String): List<String> {
    val paramNames = mutableListOf<String>()

    // Extract content within parentheses
    val paramsMatch = """\(([^)]*)\)""".toRegex().find(signature)
    val paramsContent = paramsMatch?.groupValues?.get(1) ?: return emptyList()

    if (paramsContent.isBlank()) return emptyList()

    // Split by comma and extract parameter names
    paramsContent.split(",").forEach { param ->
      val trimmed = param.trim()
      // Parameter format: "name: Type" or "name: Type = default"
      val paramName = trimmed.substringBefore(":").trim()
      if (paramName.isNotEmpty()) {
        paramNames.add(paramName)
      }
    }

    return paramNames
  }
}
