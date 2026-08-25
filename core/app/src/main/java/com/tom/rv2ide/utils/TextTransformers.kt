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

package com.tom.rv2ide.utils

import io.github.rosemoe.sora.widget.CodeEditor
import android.content.ClipboardManager
import android.content.Context

/*
 * @author Mohammed-baqer-null @ https://github.com/Mohammed-baqer-null
*/

class TextTransformers(val editor: CodeEditor) {

  // Detect comment style based on file extension
  private fun getCommentStyle(): CommentStyle {
    val fileName = getOpenedFileName() ?: ""
    
    return when {
      fileName.endsWith(".kt") || fileName.endsWith(".kts") -> CommentStyle("//", "")
      fileName.endsWith(".java") -> CommentStyle("//", "")
      fileName.endsWith(".js") || fileName.endsWith(".jsx") -> CommentStyle("//", "")
      fileName.endsWith(".ts") || fileName.endsWith(".tsx") -> CommentStyle("//", "")
      fileName.endsWith(".c") || fileName.endsWith(".cpp") || fileName.endsWith(".cc") -> CommentStyle("//", "")
      fileName.endsWith(".cs") -> CommentStyle("//", "")
      fileName.endsWith(".swift") -> CommentStyle("//", "")
      fileName.endsWith(".go") -> CommentStyle("//", "")
      fileName.endsWith(".rs") -> CommentStyle("//", "")
      fileName.endsWith(".php") -> CommentStyle("//", "")
      fileName.endsWith(".sh") || fileName.endsWith(".bash") -> CommentStyle("#", "")
      fileName.endsWith(".py") -> CommentStyle("#", "")
      fileName.endsWith(".rb") -> CommentStyle("#", "")
      fileName.endsWith(".pl") -> CommentStyle("#", "")
      fileName.endsWith(".yaml") || fileName.endsWith(".yml") -> CommentStyle("#", "")
      fileName.endsWith(".r") -> CommentStyle("#", "")
      fileName.endsWith(".lua") -> CommentStyle("--", "")
      fileName.endsWith(".sql") -> CommentStyle("--", "")
      fileName.endsWith(".hs") -> CommentStyle("--", "")
      fileName.endsWith(".html") || fileName.endsWith(".xml") -> CommentStyle("<!--", "-->")
      fileName.endsWith(".css") -> CommentStyle("/*", "*/")
      else -> CommentStyle("//", "") // Default to //
    }
  }

  private fun getOpenedFileName(): String? {
    return fileName
  }

  fun lineToUppercase() {
    if (hasSelection()) {
      transformSelection { it.uppercase() }
    } else {
      val line = getLineNumberAtCursor(editor)
      val text = getTextAtCursorLine(editor)
      setTextAtLine(editor, line, text.uppercase())
    }
  }

  fun lineToLowercase() {
    if (hasSelection()) {
      transformSelection { it.lowercase() }
    } else {
      val line = getLineNumberAtCursor(editor)
      val text = getTextAtCursorLine(editor)
      setTextAtLine(editor, line, text.lowercase())
    }
  }

  fun deleteLine() {
    if (hasSelection()) {
      deleteSelection()
    } else {
      deleteLineAtCursor()
    }
  }

  fun duplicateLine() {
    if (hasSelection()) {
      duplicateSelection()
    } else {
      duplicateCurrentLine(editor)
    }
  }

  fun copyLine() {
    if (hasSelection()) {
      copySelectionToClipboard()
    } else {
      copyLineToClipboard()
    }
  }

  fun commentLine() {
    if (hasSelection()) {
      commentSelection()
    } else {
      commentCurrentLine()
    }
  }

  // Check if there's any text selected
  private fun hasSelection(): Boolean {
    val cursor = editor.cursor
    return cursor.isSelected
  }

  // Get selected text
  private fun getSelectedText(): String {
    val cursor = editor.cursor
    val content = editor.text
    return content.subContent(
        cursor.leftLine,
        cursor.leftColumn,
        cursor.rightLine,
        cursor.rightColumn
    ).toString()
  }

  // Get selection range
  private fun getSelectionRange(): SelectionRange {
    val cursor = editor.cursor
    return SelectionRange(
        cursor.leftLine,
        cursor.leftColumn,
        cursor.rightLine,
        cursor.rightColumn
    )
  }

  // Transform selected text with a function
  private fun transformSelection(transform: (String) -> String) {
    val range = getSelectionRange()
    val selectedText = getSelectedText()
    val transformedText = transform(selectedText)

    editor.text.replace(
        range.startLine,
        range.startColumn,
        range.endLine,
        range.endColumn,
        transformedText
    )
  }

  // Delete selection
  private fun deleteSelection() {
    val range = getSelectionRange()
    editor.text.delete(
        range.startLine,
        range.startColumn,
        range.endLine,
        range.endColumn
    )
  }

  // Delete line at cursor
  private fun deleteLineAtCursor() {
    val content = editor.text
    val currentLine = editor.cursor.leftLine

    if (currentLine < content.lineCount - 1) {
      // Delete line including newline
      content.delete(currentLine, 0, currentLine + 1, 0)
    } else {
      // Last line - delete content only
      val lineEndColumn = content.getColumnCount(currentLine)
      content.delete(currentLine, 0, currentLine, lineEndColumn)
    }
  }

  // Duplicate selection
  private fun duplicateSelection() {
    val range = getSelectionRange()
    val selectedText = getSelectedText()

    editor.text.insert(
        range.endLine,
        range.endColumn,
        selectedText
    )
  }

  // Copy line to clipboard
  private fun copyLineToClipboard() {
    val lineText = getTextAtCursorLine(editor)
    copyToClipboard(lineText)
  }

  // Copy selection to clipboard
  private fun copySelectionToClipboard() {
    val selectedText = getSelectedText()
    copyToClipboard(selectedText)
  }

  // Copy text to clipboard - FIXED VERSION
  private fun copyToClipboard(text: String) {
    try {
      val clipboard = editor.context.getSystemService(Context.CLIPBOARD_SERVICE) 
          as ClipboardManager
      val clip = android.content.ClipData.newPlainText("editor_text", text)
      clipboard.setPrimaryClip(clip)
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  // Comment current line
  private fun commentCurrentLine() {
    val commentStyle = getCommentStyle()
    val line = getLineNumberAtCursor(editor)
    val lineText = getTextAtCursorLine(editor)
    val trimmed = lineText.trimStart()
    val leadingSpaces = lineText.takeWhile { it.isWhitespace() }

    if (commentStyle.end.isEmpty()) {
      // Single line comment (// or # or --)
      if (trimmed.startsWith(commentStyle.start)) {
        // Uncomment
        val commentWithSpace = "${commentStyle.start} "
        val uncommented = if (trimmed.startsWith(commentWithSpace)) {
          leadingSpaces + trimmed.removePrefix(commentWithSpace)
        } else {
          leadingSpaces + trimmed.removePrefix(commentStyle.start)
        }
        setTextAtLine(editor, line, uncommented)
      } else {
        // Comment
        setTextAtLine(editor, line, "$leadingSpaces${commentStyle.start} $trimmed")
      }
    } else {
      // Block comment (<!-- --> or /* */)
      if (trimmed.startsWith(commentStyle.start) && trimmed.endsWith(commentStyle.end)) {
        // Uncomment
        val uncommented = trimmed
            .removePrefix(commentStyle.start)
            .removeSuffix(commentStyle.end)
            .trim()
        setTextAtLine(editor, line, "$leadingSpaces$uncommented")
      } else {
        // Comment
        setTextAtLine(editor, line, "$leadingSpaces${commentStyle.start} $trimmed ${commentStyle.end}")
      }
    }
  }

  // Comment selection
  private fun commentSelection() {
    val commentStyle = getCommentStyle()
    val range = getSelectionRange()
    val content = editor.text

    for (i in range.startLine..range.endLine) {
      val lineText = content.getLineString(i)
      val trimmed = lineText.trimStart()
      val leadingSpaces = lineText.takeWhile { it.isWhitespace() }

      val newText = if (commentStyle.end.isEmpty()) {
        // Single line comment
        if (trimmed.startsWith(commentStyle.start)) {
          // Uncomment
          val commentWithSpace = "${commentStyle.start} "
          if (trimmed.startsWith(commentWithSpace)) {
            leadingSpaces + trimmed.removePrefix(commentWithSpace)
          } else {
            leadingSpaces + trimmed.removePrefix(commentStyle.start)
          }
        } else {
          // Comment
          "$leadingSpaces${commentStyle.start} $trimmed"
        }
      } else {
        // Block comment
        if (trimmed.startsWith(commentStyle.start) && trimmed.endsWith(commentStyle.end)) {
          // Uncomment
          val uncommented = trimmed
              .removePrefix(commentStyle.start)
              .removeSuffix(commentStyle.end)
              .trim()
          "$leadingSpaces$uncommented"
        } else {
          // Comment
          "$leadingSpaces${commentStyle.start} $trimmed ${commentStyle.end}"
        }
      }

      val lineEndColumn = content.getColumnCount(i)
      content.replace(i, 0, i, lineEndColumn, newText)
    }
  }

  // Move line up
  fun moveLineUp() {
    val currentLine = getLineNumberAtCursor(editor)
    if (currentLine > 0) {
      val content = editor.text
      val currentLineText = content.getLineString(currentLine)
      val previousLineText = content.getLineString(currentLine - 1)

      // Replace previous line with current
      val prevLineEnd = content.getColumnCount(currentLine - 1)
      content.replace(currentLine - 1, 0, currentLine - 1, prevLineEnd, currentLineText)

      // Replace current line with previous
      val currLineEnd = content.getColumnCount(currentLine)
      content.replace(currentLine, 0, currentLine, currLineEnd, previousLineText)

      // Move cursor up
      editor.setSelection(currentLine - 1, 0)
    }
  }

  // Move line down
  fun moveLineDown() {
    val currentLine = getLineNumberAtCursor(editor)
    val content = editor.text

    if (currentLine < content.lineCount - 1) {
      val currentLineText = content.getLineString(currentLine)
      val nextLineText = content.getLineString(currentLine + 1)

      // Replace current line with next
      val currLineEnd = content.getColumnCount(currentLine)
      content.replace(currentLine, 0, currentLine, currLineEnd, nextLineText)

      // Replace next line with current
      val nextLineEnd = content.getColumnCount(currentLine + 1)
      content.replace(currentLine + 1, 0, currentLine + 1, nextLineEnd, currentLineText)

      // Move cursor down
      editor.setSelection(currentLine + 1, 0)
    }
  }

  // Insert line above
  fun insertLineAbove() {
    val currentLine = getLineNumberAtCursor(editor)
    editor.text.insert(currentLine, 0, "\n")
    editor.setSelection(currentLine, 0)
  }

  // Insert line below
  fun insertLineBelow() {
    val currentLine = getLineNumberAtCursor(editor)
    val content = editor.text
    val lineEndColumn = content.getColumnCount(currentLine)
    content.insert(currentLine, lineEndColumn, "\n")
    editor.setSelection(currentLine + 1, 0)
  }

  // Select entire line
  fun selectLine() {
    val currentLine = getLineNumberAtCursor(editor)
    val content = editor.text
    val lineEndColumn = content.getColumnCount(currentLine)
    editor.setSelectionRegion(currentLine, 0, currentLine, lineEndColumn)
  }

  // Trim trailing whitespace
  fun trimTrailingWhitespace() {
    if (hasSelection()) {
      transformSelection { it.trimEnd() }
    } else {
      val line = getLineNumberAtCursor(editor)
      val lineText = getTextAtCursorLine(editor)
      setTextAtLine(editor, line, lineText.trimEnd())
    }
  }

  // Indent line
  fun indentLine() {
    if (hasSelection()) {
      indentSelection()
    } else {
      val line = getLineNumberAtCursor(editor)
      val lineText = getTextAtCursorLine(editor)
      setTextAtLine(editor, line, "    $lineText")
    }
  }

  // Unindent line
  fun unindentLine() {
    if (hasSelection()) {
      unindentSelection()
    } else {
      val line = getLineNumberAtCursor(editor)
      val lineText = getTextAtCursorLine(editor)
      if (lineText.startsWith("    ")) {
        setTextAtLine(editor, line, lineText.substring(4))
      } else if (lineText.startsWith("\t")) {
        setTextAtLine(editor, line, lineText.substring(1))
      }
    }
  }

  // Indent selection
  private fun indentSelection() {
    val range = getSelectionRange()
    val content = editor.text

    for (i in range.startLine..range.endLine) {
      val lineText = content.getLineString(i)
      val lineEndColumn = content.getColumnCount(i)
      content.replace(i, 0, i, lineEndColumn, "    $lineText")
    }
  }

  // Unindent selection
  private fun unindentSelection() {
    val range = getSelectionRange()
    val content = editor.text

    for (i in range.startLine..range.endLine) {
      val lineText = content.getLineString(i)
      val newText = when {
        lineText.startsWith("    ") -> lineText.substring(4)
        lineText.startsWith("\t") -> lineText.substring(1)
        else -> lineText
      }
      val lineEndColumn = content.getColumnCount(i)
      content.replace(i, 0, i, lineEndColumn, newText)
    }
  }

  // Join lines
  fun joinLines() {
    val currentLine = getLineNumberAtCursor(editor)
    val content = editor.text

    if (currentLine < content.lineCount - 1) {
      val currentLineText = content.getLineString(currentLine)
      val nextLineText = content.getLineString(currentLine + 1)

      // Replace current line with joined text
      val lineEndColumn = content.getColumnCount(currentLine)
      content.replace(currentLine, 0, currentLine, lineEndColumn, currentLineText.trimEnd())

      // Delete the newline and next line
      content.delete(currentLine, currentLineText.trimEnd().length, currentLine + 1, 0)

      // Add space and next line content
      content.insert(currentLine, currentLineText.trimEnd().length, " ${nextLineText.trimStart()}")
    }
  }

  // Get line count
  fun getLineCount(): Int {
    return editor.text.lineCount
  }

  // Get current column
  fun getCurrentColumn(): Int {
    return editor.cursor.leftColumn
  }

  // Go to line
  fun goToLine(lineNumber: Int) {
    if (lineNumber >= 0 && lineNumber < editor.text.lineCount) {
      editor.setSelection(lineNumber, 0)
    }
  }

  // Find and replace in current line
  fun findAndReplaceInLine(find: String, replace: String) {
    val line = getLineNumberAtCursor(editor)
    val lineText = getTextAtCursorLine(editor)
    val newText = lineText.replace(find, replace)
    setTextAtLine(editor, line, newText)
  }

  // Clear editor
  fun clearAll() {
    editor.setText("")
  }

  // Get all text
  fun getAllText(): String {
    return editor.text.toString()
  }

  // Set cursor position
  fun setCursorPosition(line: Int, column: Int) {
    editor.setSelection(line, column)
  }

  // Setter for file name (call this when you know the file name)
  var fileName: String? = null
    private set

  fun setFileName(name: String?) {
    fileName = name
  }

  private fun getTextAtCursorLine(editor: CodeEditor): String {
    val cursor = editor.cursor
    val currentLine = cursor.leftLine
    val content = editor.text

    return content.getLineString(currentLine)
  }

  private fun getLineNumberAtCursor(editor: CodeEditor): Int {
    return editor.cursor.leftLine
  }

  private fun setTextAtLine(editor: CodeEditor, lineNumber: Int, newText: String) {
    val content = editor.text
    val lineStartColumn = 0
    val lineEndColumn = content.getColumnCount(lineNumber)
    content.replace(lineNumber, lineStartColumn, lineNumber, lineEndColumn, newText)
  }

  private fun duplicateCurrentLine(editor: CodeEditor) {
    val cursor = editor.cursor
    val currentLine = cursor.leftLine
    val content = editor.text
    val lineText = content.getLineString(currentLine)

    val lineEndColumn = content.getColumnCount(currentLine)
    content.insert(currentLine, lineEndColumn, "\n$lineText")
  }

  // Data class for comment style
  data class CommentStyle(val start: String, val end: String)

  // Data class for selection range
  data class SelectionRange(
      val startLine: Int,
      val startColumn: Int,
      val endLine: Int,
      val endColumn: Int
  )
}