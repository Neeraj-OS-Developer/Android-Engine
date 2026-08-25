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
package com.tom.rv2ide.setup.updater

import com.tom.rv2ide.setup.R
import com.tom.rv2ide.resources.R.string
import android.content.Context
import android.view.View
import android.view.LayoutInflater
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * @author Mohammed-baqer-null @ https://github.com/Mohammed-baqer-null
 */

class LspUpdateDialog(private val ctx: Context) {
    
    private var titleText: String = "Dialog title"
    private var descriptionText: String = "Dialog description"
    private var primaryBtnText: String? = null
    private var middleBtnText: String? = null
    private var secondaryBtnText: String? = null
    
    private var primaryBtnAction: ((LspUpdateDialog) -> Unit)? = null
    private var middleBtnAction: ((LspUpdateDialog) -> Unit)? = null
    private var secondaryBtnAction: ((LspUpdateDialog) -> Unit)? = null
    
    private var showProgress: Boolean = false
    private var operateAction: (suspend (ProgressUpdater) -> Unit)? = null
    
    private var dialog: AlertDialog? = null
    private var progressIndicator: LinearProgressIndicator? = null
    private var titleView: TextView? = null
    private var descriptionView: TextView? = null
    private var primaryBtn: TextView? = null
    private var middleBtn: TextView? = null
    private var secondaryBtn: TextView? = null
    
    fun setTitle(text: String): LspUpdateDialog {
        titleText = text
        titleView?.text = text
        return this
    }
    
    fun setDescription(text: String): LspUpdateDialog {
        descriptionText = text
        descriptionView?.text = text
        return this
    }
        
    fun setPrimaryButton(text: String?, action: ((LspUpdateDialog) -> Unit)?): LspUpdateDialog {
        primaryBtnText = text
        primaryBtnAction = action
        return this
    }
    
    fun setMiddleButton(text: String?, action: ((LspUpdateDialog) -> Unit)?): LspUpdateDialog {
        middleBtnText = text
        middleBtnAction = action
        return this
    }
    
    fun setSecondaryButton(text: String?, action: ((LspUpdateDialog) -> Unit)?): LspUpdateDialog {
        secondaryBtnText = text
        secondaryBtnAction = action
        return this
    }
    
    fun withProgress(): LspUpdateDialog {
        showProgress = true
        progressIndicator?.visibility = View.VISIBLE
        return this
    }
    
    fun operate(action: suspend (ProgressUpdater) -> Unit) {
        showProgress = true
        progressIndicator?.visibility = View.VISIBLE
        progressIndicator?.isIndeterminate = false
        progressIndicator?.progress = 0
        
        primaryBtn?.visibility = View.GONE
        middleBtn?.visibility = View.GONE
        secondaryBtn?.visibility = View.GONE
        
        dialog?.setCancelable(false)
        
        val progressUpdater = ProgressUpdater(progressIndicator, titleView, descriptionView)
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                withContext(Dispatchers.IO) {
                    action.invoke(progressUpdater)
                }
                dialog?.dismiss()
            } catch (e: Exception) {
                dialog?.dismiss()
            }
        }
    }
    
    fun reveal() {
        val view = LayoutInflater.from(ctx).inflate(R.layout.layout_lsp_update_dialog, null)
        
        titleView = view.findViewById(R.id.title)
        descriptionView = view.findViewById(R.id.description)
        primaryBtn = view.findViewById(R.id.button_primary)
        middleBtn = view.findViewById(R.id.button_middle)
        secondaryBtn = view.findViewById(R.id.button_secondary)
        progressIndicator = view.findViewById(R.id.progress_indicator)
        
        titleView?.text = titleText
        descriptionView?.text = descriptionText
        
        if (showProgress) {
            progressIndicator?.visibility = View.VISIBLE
            progressIndicator?.isIndeterminate = true
        } else {
            progressIndicator?.visibility = View.GONE
        }
        
        if (primaryBtnText != null) {
            primaryBtn?.text = primaryBtnText
            primaryBtn?.visibility = View.VISIBLE
        } else {
            primaryBtn?.visibility = View.GONE
        }
        
        if (middleBtnText != null) {
            middleBtn?.text = middleBtnText
            middleBtn?.visibility = View.VISIBLE
        } else {
            middleBtn?.visibility = View.GONE
        }
        
        if (secondaryBtnText != null) {
            secondaryBtn?.text = secondaryBtnText
            secondaryBtn?.visibility = View.VISIBLE
        } else {
            secondaryBtn?.visibility = View.GONE
        }
        
        primaryBtn?.setOnClickListener {
            primaryBtnAction?.invoke(this)
        }
        
        middleBtn?.setOnClickListener {
            middleBtnAction?.invoke(this)
        }
        
        secondaryBtn?.setOnClickListener {
            secondaryBtnAction?.invoke(this)
        }
        
        dialog = MaterialAlertDialogBuilder(ctx)
            .setView(view)
            .setCancelable(true)
            .create()
        
        dialog?.show()
        
        if (operateAction != null) {
            operate(operateAction!!)
            operateAction = null
        }
    }
    
    fun dismiss() {
        dialog?.dismiss()
    }
    
    inner class ProgressUpdater(
        private val indicator: LinearProgressIndicator?,
        private val titleView: TextView?,
        private val descriptionView: TextView?
    ) {
        suspend fun updateProgress(progress: Int) {
            withContext(Dispatchers.Main) {
                indicator?.progress = progress
            }
        }
        
        suspend fun updateTitle(text: String) {
            withContext(Dispatchers.Main) {
                titleView?.text = text
            }
        }
        
        suspend fun updateDescription(text: String) {
            withContext(Dispatchers.Main) {
                descriptionView?.text = text
            }
        }
        
        suspend fun setIndeterminate(indeterminate: Boolean) {
            withContext(Dispatchers.Main) {
                indicator?.isIndeterminate = indeterminate
            }
        }
    }
}