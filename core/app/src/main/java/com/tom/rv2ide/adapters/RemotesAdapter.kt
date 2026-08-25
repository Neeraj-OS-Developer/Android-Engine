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
import com.tom.rv2ide.databinding.ItemRemoteBinding
import com.tom.rv2ide.git.RemoteInfo

/**
 * @author Mohammed-baqer-null @ https://github.com/Mohammed-baqer-null
 */

class RemotesAdapter(
    private val onRemoveClick: (RemoteInfo) -> Unit
) : ListAdapter<RemoteInfo, RemotesAdapter.ViewHolder>(DiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRemoteBinding.inflate(
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
        private val binding: ItemRemoteBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(remote: RemoteInfo) {
            binding.textRemoteName.text = remote.name
            binding.textRemoteUrl.text = remote.fetchUrl
            
            binding.buttonRemove.setOnClickListener {
                onRemoveClick(remote)
            }
        }
    }
    
    private class DiffCallback : DiffUtil.ItemCallback<RemoteInfo>() {
        override fun areItemsTheSame(oldItem: RemoteInfo, newItem: RemoteInfo): Boolean {
            return oldItem.name == newItem.name
        }
        
        override fun areContentsTheSame(oldItem: RemoteInfo, newItem: RemoteInfo): Boolean {
            return oldItem == newItem
        }
    }
}
