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
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tom.rv2ide.R
import com.tom.rv2ide.databinding.ItemBranchBinding
import android.util.TypedValue

/**
 * @author Mohammed-baqer-null @ https://github.com/Mohammed-baqer-null
 */

class BranchesAdapter(
    private val onCheckoutClick: (String) -> Unit,
    private val onDeleteClick: (String) -> Unit,
    private val getCurrentBranch: () -> String
) : ListAdapter<String, BranchesAdapter.ViewHolder>(DiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBranchBinding.inflate(
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
        private val binding: ItemBranchBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(branch: String) {
            val isCurrentBranch = branch == getCurrentBranch()
            
            binding.textBranchName.text = branch
            
            if (isCurrentBranch) {
                val typedValue = TypedValue()
                binding.root.context.theme.resolveAttribute(
                    android.R.attr.colorPrimary,
                    typedValue,
                    true
                )
                binding.cardBranch.strokeColor = typedValue.data
                binding.cardBranch.strokeWidth = 4
            } else {
                binding.cardBranch.strokeWidth = 0
            }
            
            binding.buttonCheckout.setOnClickListener {
                if (!isCurrentBranch) {
                    onCheckoutClick(branch)
                }
            }
            
            binding.buttonDelete.setOnClickListener {
                onDeleteClick(branch)
            }
            
            binding.buttonCheckout.isEnabled = !isCurrentBranch
        }
    }
    
    private class DiffCallback : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }
        
        override fun areContentsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }
    }
}