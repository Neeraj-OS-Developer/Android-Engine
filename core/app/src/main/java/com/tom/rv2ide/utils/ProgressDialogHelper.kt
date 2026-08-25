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

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tom.rv2ide.R

/**
 * @author Mohammed-baqer-null @ https://github.com/Mohammed-baqer-null
 */

class ProgressDialogHelper(private val context: Context) {
    
    private var dialog: Dialog? = null
    
    fun show(message: String = "Please wait...") {
        dismiss()
        
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_progress, null)
        val textMessage = dialogView.findViewById<TextView>(R.id.textProgressMessage)
        textMessage.text = message
        
        dialog = MaterialAlertDialogBuilder(context)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        
        dialog?.show()
    }
    
    fun updateMessage(message: String) {
        dialog?.findViewById<TextView>(R.id.textProgressMessage)?.text = message
    }
    
    fun dismiss() {
        dialog?.dismiss()
        dialog = null
    }
    
    fun isShowing(): Boolean = dialog?.isShowing == true
}
