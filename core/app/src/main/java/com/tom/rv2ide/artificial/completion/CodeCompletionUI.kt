package com.tom.rv2ide.artificial.completion

import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.KeyEvent
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.event.EventReceiver
import io.github.rosemoe.sora.event.SubscriptionReceipt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CodeCompletionUI(
    private val editor: CodeEditor,
    private val completionService: AICodeCompletionService,
    private val scope: CoroutineScope
) {
    
    private var suggestionJob: Job? = null
    private var currentSuggestion: String? = null
    private var suggestionStartPosition: Int = 0
    private val debounceDelayMs = 1500L
    private var lastChangeTime = 0L
    private var isRequestingsuggestion = false
    private var isListening = false
    
    private var contentChangeReceipt: SubscriptionReceipt<ContentChangeEvent>? = null
    private var selectionChangeReceipt: SubscriptionReceipt<SelectionChangeEvent>? = null
    
    var onSuggestionChanged: ((String?) -> Unit)? = null
    
    fun initialize() {
        cleanup()
        setupEditorListeners()
    }
    
    private fun setupEditorListeners() {
        contentChangeReceipt = editor.subscribeEvent(ContentChangeEvent::class.java) { event, _ ->
            if (isListening) {
                onContentChanged(event)
            }
        }
        
        selectionChangeReceipt = editor.subscribeEvent(SelectionChangeEvent::class.java) { event, _ ->
            if (isListening) {
                onSelectionChanged(event)
            }
        }
        
        editor.setOnKeyListener { _, keyCode, event ->
            if (!isListening) return@setOnKeyListener false
            
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_TAB -> {
                        if (currentSuggestion != null && acceptSuggestion()) {
                            return@setOnKeyListener true
                        }
                    }
                    KeyEvent.KEYCODE_ESCAPE -> {
                        clearSuggestionDisplay()
                        return@setOnKeyListener true
                    }
                }
            }
            false
        }
    }
    
    fun startListening() {
        android.util.Log.d("CodeCompletionUI", "startListening() called, current state: $isListening")
        isListening = true
        android.util.Log.d("CodeCompletionUI", "isListening set to: $isListening")
    }
    
    fun stopListening() {
        android.util.Log.d("CodeCompletionUI", "stopListening() called, current state: $isListening")
        isListening = false
        suggestionJob?.cancel()
        suggestionJob = null
        clearSuggestionDisplay()
        android.util.Log.d("CodeCompletionUI", "isListening set to: $isListening, job cancelled")
    }
    
    private fun onContentChanged(event: ContentChangeEvent) {
        if (!isListening) {
            android.util.Log.d("CodeCompletionUI", "onContentChanged but NOT listening - ignoring")
            return
        }
        
        android.util.Log.d("CodeCompletionUI", "onContentChanged - listening is TRUE")
        lastChangeTime = System.currentTimeMillis()
        
        if (event.action == ContentChangeEvent.ACTION_DELETE) {
            clearSuggestionDisplay()
            return
        }
        
        suggestionJob?.cancel()
        clearSuggestionDisplay()
        
        suggestionJob = scope.launch {
            delay(debounceDelayMs)
            
            if (!isListening) {
                android.util.Log.d("CodeCompletionUI", "Job woke up but no longer listening - aborting")
                return@launch
            }
            
            if (System.currentTimeMillis() - lastChangeTime >= debounceDelayMs) {
                requestSuggestion()
            }
        }
    }
    
    private fun onSelectionChanged(event: SelectionChangeEvent) {
        if (event.cause == SelectionChangeEvent.CAUSE_TAP || 
            event.cause == SelectionChangeEvent.CAUSE_SELECTION_HANDLE) {
            clearSuggestionDisplay()
        }
    }
    
    private suspend fun requestSuggestion() {
        if (!isListening) {
            android.util.Log.d("CodeCompletionUI", "requestSuggestion - NOT listening, aborting")
            return
        }
        
        if (!completionService.isEnabled()) {
            android.util.Log.d("CodeCompletionUI", "requestSuggestion - service NOT enabled, aborting")
            return
        }
        
        if (isRequestingsuggestion) {
            android.util.Log.d("CodeCompletionUI", "requestSuggestion - already requesting, aborting")
            return
        }
        
        android.util.Log.d("CodeCompletionUI", "requestSuggestion - proceeding with request")
        isRequestingsuggestion = true
        
        withContext(Dispatchers.Main) {
            try {
                if (!isListening) {
                    android.util.Log.d("CodeCompletionUI", "requestSuggestion - lost listening state during switch")
                    isRequestingsuggestion = false
                    return@withContext
                }
                
                val cursor = editor.cursor
                
                if (cursor.isSelected) {
                    isRequestingsuggestion = false
                    return@withContext
                }
                
                val currentLine = editor.text.getLine(cursor.leftLine)
                val lineText = currentLine.toString()
                
                if (lineText.trim().isEmpty() || cursor.leftColumn < lineText.length - 1) {
                    isRequestingsuggestion = false
                    return@withContext
                }
                
                val cursorPosition = getAbsoluteCursorPosition()
                val currentText = editor.text.toString()
                
                android.util.Log.d("CodeCompletionUI", "Requesting suggestion at position: $cursorPosition")
                
                val suggestionResult = withContext(Dispatchers.IO) {
                    if (!isListening) {
                        android.util.Log.d("CodeCompletionUI", "Lost listening during IO call")
                        return@withContext null
                    }
                    completionService.getSuggestion(
                        currentText,
                        cursorPosition,
                        "kotlin"
                    )
                }
                
                android.util.Log.d("CodeCompletionUI", "Received suggestion: $suggestionResult")
                
                if (!isListening) {
                    android.util.Log.d("CodeCompletionUI", "Lost listening after receiving suggestion")
                    isRequestingsuggestion = false
                    return@withContext
                }
                
                if (suggestionResult != null && suggestionResult.text.isNotBlank()) {
                    suggestionStartPosition = cursorPosition
                    currentSuggestion = suggestionResult.text
                    onSuggestionChanged?.invoke(suggestionResult.text)
                } else {
                    android.util.Log.d("CodeCompletionUI", "Suggestion was null or blank")
                }
            } catch (e: Exception) {
                android.util.Log.e("CodeCompletionUI", "Error requesting suggestion", e)
            } finally {
                isRequestingsuggestion = false
            }
        }
    }
    
    private fun getAbsoluteCursorPosition(): Int {
        val cursor = editor.cursor
        var position = 0
        
        for (i in 0 until cursor.leftLine) {
            position += editor.text.getLine(i).length + 1
        }
        position += cursor.leftColumn
        
        return position
    }
    
    private fun clearSuggestionDisplay() {
        currentSuggestion = null
        onSuggestionChanged?.invoke(null)
    }
    
    fun acceptSuggestion(): Boolean {
        android.util.Log.d("CodeCompletionUI", "acceptSuggestion() called")
        
        val suggestion = currentSuggestion
        if (suggestion == null) {
            android.util.Log.e("CodeCompletionUI", "currentSuggestion is NULL!")
            return false
        }
        
        android.util.Log.d("CodeCompletionUI", "Suggestion to insert: '$suggestion'")
        
        try {
            val cursor = editor.cursor
            val currentLine = cursor.leftLine
            val currentColumn = cursor.leftColumn
            val content = editor.text
            
            val currentLineText = content.getLine(currentLine).toString()
            val textBeforeCursor = currentLineText.substring(0, currentColumn.coerceAtMost(currentLineText.length))
            
            var finalSuggestion = suggestion
            
            if (textBeforeCursor.isNotEmpty() && !textBeforeCursor.endsWith(" ") && !textBeforeCursor.endsWith("\t")) {
                if (!suggestion.startsWith(" ") && !suggestion.startsWith("\n")) {
                    finalSuggestion = " $suggestion"
                    android.util.Log.d("CodeCompletionUI", "Added space before suggestion: '$finalSuggestion'")
                }
            }
            
            val lines = finalSuggestion.lines()
            
            if (lines.size == 1) {
                content.insert(currentLine, currentColumn, finalSuggestion)
                val newColumn = currentColumn + finalSuggestion.length
                editor.setSelection(currentLine, newColumn)
            } else {
                val baseIndentation = textBeforeCursor.takeWhile { it.isWhitespace() }
                
                content.insert(currentLine, currentColumn, lines[0])
                
                var insertLine = currentLine + 1
                for (i in 1 until lines.size) {
                    val line = lines[i]
                    val indentedLine = if (line.isNotBlank()) {
                        baseIndentation + line.trimStart()
                    } else {
                        ""
                    }
                    
                    val lineContent = content.getLine(currentLine)
                    val lineLength = lineContent.length
                    content.insert(currentLine, lineLength, "\n" + indentedLine)
                    insertLine++
                }
                
                val lastLine = lines.last()
                val finalColumn = if (lastLine.isBlank()) {
                    baseIndentation.length
                } else {
                    baseIndentation.length + lastLine.trimStart().length
                }
                
                editor.setSelection(insertLine - 1, finalColumn)
            }
            
            android.util.Log.d("CodeCompletionUI", "Text inserted successfully!")
            
            clearSuggestionDisplay()
            editor.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            
            return true
        } catch (e: Exception) {
            android.util.Log.e("CodeCompletionUI", "ERROR inserting text!", e)
            e.printStackTrace()
            return false
        }
    }
    
    fun clearSuggestion() {
        suggestionJob?.cancel()
        suggestionJob = null
        clearSuggestionDisplay()
    }
    
    fun getCurrentSuggestion(): String? = currentSuggestion
    
    fun cleanup() {
        android.util.Log.d("CodeCompletionUI", "cleanup() - unsubscribing events")
        stopListening()
        suggestionJob?.cancel()
        suggestionJob = null
        currentSuggestion = null
        onSuggestionChanged = null
        
        contentChangeReceipt?.unsubscribe()
        selectionChangeReceipt?.unsubscribe()
        contentChangeReceipt = null
        selectionChangeReceipt = null
    }
}