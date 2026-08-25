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
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tom.rv2ide.databinding.ItemCommitBinding
import com.tom.rv2ide.git.CommitInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * @author Mohammed-baqer-null @ https://github.com/Mohammed-baqer-null
 */

class CommitHistoryAdapter : ListAdapter<CommitInfo, CommitHistoryAdapter.ViewHolder>(DiffCallback()) {
    
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCommitBinding.inflate(
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
        private val binding: ItemCommitBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(commit: CommitInfo) {
            binding.textCommitMessage.text = commit.message
            binding.textCommitHash.text = commit.shortHash
            binding.textCommitAuthor.text = commit.author
            binding.textCommitDate.text = dateFormat.format(Date(commit.timestamp))
        }
    }
    
    private class DiffCallback : DiffUtil.ItemCallback<CommitInfo>() {
        override fun areItemsTheSame(oldItem: CommitInfo, newItem: CommitInfo): Boolean {
            return oldItem.hash == newItem.hash
        }
        
        override fun areContentsTheSame(oldItem: CommitInfo, newItem: CommitInfo): Boolean {
            return oldItem == newItem
        }
    }
}