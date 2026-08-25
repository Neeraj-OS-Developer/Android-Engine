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
package com.tom.rv2ide.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tom.rv2ide.R
import com.tom.rv2ide.databinding.ItemFileChangeBinding
import com.tom.rv2ide.git.ChangeType
import com.tom.rv2ide.git.FileChange

/**
 * @author Mohammed-baqer-null @ https://github.com/Mohammed-baqer-null
 */

class FileChangesAdapter(
    private val onStageClick: (FileChange) -> Unit,
    private val onUnstageClick: (FileChange) -> Unit,
    private val onDiscardClick: (FileChange) -> Unit
) : ListAdapter<FileChange, FileChangesAdapter.ViewHolder>(DiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFileChangeBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class ViewHolder(
        private val binding: ItemFileChangeBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
    fun bind(fileChange: FileChange) {
        binding.textFileName.text = fileChange.path
        
        val (badgeText, badgeColor) = when {
            fileChange.isStaged && fileChange.changeType == ChangeType.MODIFIED -> "M" to R.color.change_added
            fileChange.isStaged && fileChange.changeType == ChangeType.UNTRACKED -> "A" to R.color.change_added
            fileChange.isStaged && fileChange.changeType == ChangeType.DELETED -> "D" to R.color.change_deleted
            fileChange.changeType == ChangeType.UNTRACKED -> "U" to R.color.change_untracked
            fileChange.changeType == ChangeType.MODIFIED -> "M" to R.color.change_modified
            fileChange.changeType == ChangeType.ADDED -> "A" to R.color.change_added
            fileChange.changeType == ChangeType.DELETED -> "D" to R.color.change_deleted
            fileChange.changeType == ChangeType.CONFLICTING -> "C" to R.color.change_conflicting
            else -> "?" to R.color.change_untracked
        }
        
        binding.textChangeType.text = badgeText
        binding.textChangeType.setBackgroundColor(
            ContextCompat.getColor(binding.root.context, badgeColor)
        )
        
        binding.buttonStage.visibility = if (fileChange.isStaged) View.GONE else View.VISIBLE
        binding.buttonUnstage.visibility = if (fileChange.isStaged) View.VISIBLE else View.GONE
        
        binding.buttonStage.setOnClickListener {
            onStageClick(fileChange)
        }
        
        binding.buttonUnstage.setOnClickListener {
            onUnstageClick(fileChange)
        }
        
        binding.buttonDiscard.setOnClickListener {
            onDiscardClick(fileChange)
        }
    }
    }
    
    private class DiffCallback : DiffUtil.ItemCallback<FileChange>() {
        override fun areItemsTheSame(oldItem: FileChange, newItem: FileChange): Boolean {
            return oldItem.path == newItem.path
        }
        
        override fun areContentsTheSame(oldItem: FileChange, newItem: FileChange): Boolean {
            return oldItem == newItem
        }
    }
}