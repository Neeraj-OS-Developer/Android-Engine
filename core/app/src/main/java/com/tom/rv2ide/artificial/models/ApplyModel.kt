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

package com.tom.rv2ide.artificial.models

import io.github.rosemoe.sora.widget.CodeEditor
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/**
 * Experimental apply model
 * @author Mohammed-baqer-null @ https://github.com/Mohammed-baqer-null
*/

data class CodeReplacement(
    val startLine: Int,
    val startColumn: Int,
    val endLine: Int,
    val endColumn: Int,
    val oldText: String,
    val newText: String
)

class ApplyModel(private val editor: CodeEditor, private val context: Context) {
    
    private val replacementHistory = mutableListOf<CodeReplacement>()
    
    /**
     * Replace a specific code snippet with new code
     */
    fun replaceSnippet(
        startLine: Int,
        startColumn: Int,
        endLine: Int,
        endColumn: Int,
        newCode: String
    ): Boolean {
        try {
            val content = editor.text
            
            // Validate line and column ranges
            if (startLine < 0 || endLine >= content.lineCount) {
                return false
            }
            
            // Get the old text for history
            val oldText = getTextInRange(startLine, startColumn, endLine, endColumn)
            
            // Calculate absolute positions
            val startPos = content.getCharIndex(startLine, startColumn)
            val endPos = content.getCharIndex(endLine, endColumn)
            
            // Perform replacement
            content.replace(startPos, endPos, newCode)
            
            // Save to history
            replacementHistory.add(
                CodeReplacement(
                    startLine, startColumn, endLine, endColumn,
                    oldText, newCode
                )
            )
            
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
    
    /**
     * Replace text at current cursor position
     */
    fun replaceAtCursor(oldText: String, newText: String): Boolean {
        try {
            val cursor = editor.cursor
            val currentLine = cursor.leftLine
            val currentColumn = cursor.leftColumn
            
            val content = editor.text
            val lineText = content.getLine(currentLine).toString()
            
            // Find the old text in current line
            val index = lineText.indexOf(oldText)
            if (index == -1) return false
            
            val startPos = content.getCharIndex(currentLine, index)
            val endPos = startPos + oldText.length
            
            content.replace(startPos, endPos, newText)
            
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
    
    /**
     * Replace all occurrences of a pattern in the entire editor
     */
    fun replaceAll(oldPattern: String, newText: String, useRegex: Boolean = false): Int {
        try {
            val content = editor.text
            val fullText = content.toString()
            var replacementCount = 0
            
            if (useRegex) {
                val regex = Regex(oldPattern)
                val matches = regex.findAll(fullText).toList()
                replacementCount = matches.size
                
                // Replace from end to start to maintain positions
                matches.reversed().forEach { match ->
                    content.replace(match.range.first, match.range.last + 1, newText)
                }
            } else {
                var startIndex = 0
                while (true) {
                    val index = fullText.indexOf(oldPattern, startIndex)
                    if (index == -1) break
                    
                    content.replace(index, index + oldPattern.length, newText)
                    replacementCount++
                    startIndex = index + newText.length
                }
            }
            
            return replacementCount
        } catch (e: Exception) {
            e.printStackTrace()
            return 0
        }
    }
    
    /**
     * Replace in selected text only
     */
    fun replaceInSelection(oldText: String, newText: String): Boolean {
        try {
            if (!editor.cursor.isSelected) return false
            
            val cursor = editor.cursor
            val leftLine = cursor.leftLine
            val leftColumn = cursor.leftColumn
            val rightLine = cursor.rightLine
            val rightColumn = cursor.rightColumn
            
            val selectedText = getTextInRange(leftLine, leftColumn, rightLine, rightColumn)
            val replacedText = selectedText.replace(oldText, newText)
            
            return replaceSnippet(leftLine, leftColumn, rightLine, rightColumn, replacedText)
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
    
    /**
     * Smart replacement - tries to match indentation and formatting
     */
    fun smartReplace(
        startLine: Int,
        startColumn: Int,
        endLine: Int,
        endColumn: Int,
        newCode: String,
        preserveIndentation: Boolean = true
    ): Boolean {
        try {
            var processedCode = newCode
            
            if (preserveIndentation) {
                // Get current line indentation
                val content = editor.text
                val lineText = content.getLine(startLine).toString()
                val indentation = lineText.takeWhile { it.isWhitespace() }
                
                // Apply indentation to new code
                processedCode = newCode.lines().joinToString("\n") { line ->
                    if (line.isNotBlank()) indentation + line.trimStart() else line
                }
            }
            
            return replaceSnippet(startLine, startColumn, endLine, endColumn, processedCode)
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
    
    /**
     * Replace by line numbers (entire lines)
     */
    fun replaceLines(startLine: Int, endLine: Int, newLines: String): Boolean {
        try {
            val content = editor.text
            
            if (startLine < 0 || endLine >= content.lineCount) {
                return false
            }
            
            val startPos = content.getCharIndex(startLine, 0)
            val endPos = if (endLine + 1 < content.lineCount) {
                content.getCharIndex(endLine + 1, 0)
            } else {
                content.length
            }
            
            content.replace(startPos, endPos, newLines + "\n")
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
    
    /**
     * Find and replace with confirmation
     */
    fun findAndReplaceWithCallback(
        searchPattern: String,
        newText: String,
        onFound: (line: Int, column: Int, matchText: String) -> Boolean
    ): Int {
        try {
            val content = editor.text
            var replacementCount = 0
            
            for (line in 0 until content.lineCount) {
                val lineText = content.getLine(line).toString()
                var columnIndex = 0
                
                while (true) {
                    val index = lineText.indexOf(searchPattern, columnIndex)
                    if (index == -1) break
                    
                    // Ask for confirmation via callback
                    if (onFound(line, index, searchPattern)) {
                        val startPos = content.getCharIndex(line, index)
                        val endPos = startPos + searchPattern.length
                        content.replace(startPos, endPos, newText)
                        replacementCount++
                    }
                    
                    columnIndex = index + searchPattern.length
                }
            }
            
            return replacementCount
        } catch (e: Exception) {
            e.printStackTrace()
            return 0
        }
    }
    
    /**
     * Get text in a specific range
     */
    private fun getTextInRange(
        startLine: Int,
        startColumn: Int,
        endLine: Int,
        endColumn: Int
    ): String {
        val content = editor.text
        val startPos = content.getCharIndex(startLine, startColumn)
        val endPos = content.getCharIndex(endLine, endColumn)
        return content.subSequence(startPos, endPos).toString()
    }
    
    /**
     * Copy replaced text to clipboard
     */
    fun copyReplacementToClipboard(replacement: CodeReplacement) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Replaced Code", replacement.newText)
        clipboard.setPrimaryClip(clip)
    }
    
    /**
     * Get replacement history
     */
    fun getHistory(): List<CodeReplacement> = replacementHistory.toList()
    
    /**
     * Clear replacement history
     */
    fun clearHistory() {
        replacementHistory.clear()
    }
    
    /**
     * Get last replacement
     */
    fun getLastReplacement(): CodeReplacement? = replacementHistory.lastOrNull()
}
