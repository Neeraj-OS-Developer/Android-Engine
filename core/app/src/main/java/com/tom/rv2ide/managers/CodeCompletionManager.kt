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

package com.tom.rv2ide.managers

import android.content.Context
import androidx.lifecycle.LifecycleCoroutineScope
import com.tom.rv2ide.artificial.agents.AIAgentManager
import com.tom.rv2ide.artificial.completion.AICodeCompletionService
import com.tom.rv2ide.artificial.completion.CodeCompletionUI
import com.tom.rv2ide.artificial.completion.SuggestionView
import io.github.rosemoe.sora.widget.CodeEditor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/**
 * @author Mohammed-baqer-null @ https://github.com/Mohammed-baqer-null
 */

class CodeCompletionManager private constructor(
    context: Context,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val aiAgent: AIAgentManager
) {
    
    private val context: Context = context.applicationContext
    private var codeCompletionUI: CodeCompletionUI? = null
    private var setupJob: Job? = null
    private var currentEditor: WeakReference<CodeEditor>? = null
    private var currentSuggestionView: WeakReference<SuggestionView>? = null
    
    companion object {
        @Volatile
        private var instance: CodeCompletionManager? = null
        
        fun getInstance(
            context: Context,
            lifecycleScope: LifecycleCoroutineScope,
            aiAgent: AIAgentManager
        ): CodeCompletionManager {
            return instance ?: synchronized(this) {
                instance ?: CodeCompletionManager(context, lifecycleScope, aiAgent).also {
                    instance = it
                }
            }
        }
        
        fun clearInstance() {
            instance?.cleanup()
            instance = null
        }
    }
    
    fun setup(
        editor: CodeEditor?,
        suggestionView: SuggestionView?,
        onReady: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val prefs = context.getSharedPreferences("ai_preferences", Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("code_completion_enabled", true)
        
        if (!isEnabled) {
            onError(Exception("Code completion is disabled"))
            return
        }
        
        setupJob?.cancel()
        
        if (editor == null || suggestionView == null) {
            onError(Exception("Editor or SuggestionView is null"))
            return
        }
        
        currentEditor = WeakReference(editor)
        currentSuggestionView = WeakReference(suggestionView)
        
        setupJob = lifecycleScope.launch {
            try {
                delay(300)
                
                val agent = aiAgent.getCurrentAgent()
                if (agent == null) {
                    onError(Exception("No AI agent available"))
                    return@launch
                }
                
                if (!agent.isInitialized()) {
                    onError(Exception("AI agent not initialized"))
                    return@launch
                }
                
                codeCompletionUI?.cleanup()
                
                val codeCompletionService = AICodeCompletionService(context, agent)
                
                codeCompletionUI = CodeCompletionUI(editor, codeCompletionService, lifecycleScope)
                
                codeCompletionUI?.onSuggestionChanged = { suggestion ->
                    if (suggestion != null && suggestion.isNotBlank()) {
                        suggestionView.showSuggestion(suggestion, editor)
                    } else {
                        suggestionView.hideSuggestion()
                    }
                }
                
                suggestionView.setOnSuggestionClickListener {
                    codeCompletionUI?.acceptSuggestion()
                }
                
                codeCompletionUI?.initialize()
                codeCompletionUI?.startListening()
                
                onReady()
                
            } catch (e: Exception) {
                onError(e)
            }
        }
    }
    
    fun reattachToCurrentEditor() {
        val editor = currentEditor?.get()
        val suggestionView = currentSuggestionView?.get()
        
        if (editor != null && suggestionView != null) {
            setup(editor, suggestionView, {
            }, { e ->
            })
        }
    }
    
    fun clearSuggestion() {
        codeCompletionUI?.clearSuggestion()
        currentSuggestionView?.get()?.hideSuggestion()
    }
    
    fun cleanup() {
        setupJob?.cancel()
        codeCompletionUI?.cleanup()
        currentSuggestionView?.get()?.hideSuggestion()
        codeCompletionUI = null
        currentEditor = null
        currentSuggestionView = null
    }
}