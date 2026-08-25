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

package com.tom.rv2ide.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tom.rv2ide.databinding.ItemFileBinding
import com.tom.rv2ide.models.FileItem
import com.tom.rv2ide.R

/**
 * @author Mohammed-baqer-null @ https://github.com/Mohammed-baqer-null
 */

class FileAdapter(
    private var fileList: List<FileItem>,
    private val onItemClick: (FileItem) -> Unit,
    private val onItemLongClick: (FileItem) -> Unit,
    private val iconProvider: ((String, Boolean) -> Int)? = null
) : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {

    inner class FileViewHolder(private val binding: ItemFileBinding) : 
        RecyclerView.ViewHolder(binding.root) {
        
        fun bind(fileItem: FileItem) {
            binding.tvName.text = fileItem.name
            
            // Use icon provider if available
            iconProvider?.let { provider ->
                binding.ivIcon.setImageResource(
                    provider(fileItem.name, fileItem.isDirectory)
                )
            }
            
            binding.root.setOnClickListener {
                onItemClick(fileItem)
            }
            
            binding.root.setOnLongClickListener {
                onItemLongClick(fileItem)
                true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val binding = ItemFileBinding.inflate(
            LayoutInflater.from(parent.context), 
            parent, 
            false
        )
        return FileViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(fileList[position])
    }

    override fun getItemCount(): Int = fileList.size

    fun updateData(newList: List<FileItem>) {
        fileList = newList
        notifyDataSetChanged()
    }
}