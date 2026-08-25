package com.tom.rv2ide.adapters

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.tom.rv2ide.R
import com.tom.rv2ide.activities.ModificationData
import java.io.File

class FileChangeAdapter(
    private val modifications: List<ModificationData>,
    private val onItemClick: (Int) -> Unit
) : RecyclerView.Adapter<FileChangeAdapter.ViewHolder>() {

    private var selectedPosition = 0

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: CardView = view.findViewById(R.id.fileChangeCard)
        val fileIcon: ImageView = view.findViewById(R.id.fileIcon)
        val fileName: TextView = view.findViewById(R.id.fileName)
        val fileStatus: TextView = view.findViewById(R.id.fileStatus)
        val filePath: TextView = view.findViewById(R.id.filePath)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ai_file_change, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val modification = modifications[position]
        val file = File(modification.filePath)

        holder.fileName.text = file.name
        holder.filePath.text = modification.filePath

        if (modification.isNewFile) {
            holder.fileStatus.text = "🆕 New File"
            holder.fileStatus.setTextColor(
                ContextCompat.getColor(holder.itemView.context, android.R.color.holo_green_dark)
            )
            holder.fileIcon.setImageResource(android.R.drawable.ic_menu_add)
        } else {
            holder.fileStatus.text = "✏️ Modified"
            holder.fileStatus.setTextColor(
                ContextCompat.getColor(holder.itemView.context, android.R.color.holo_orange_dark)
            )
            holder.fileIcon.setImageResource(android.R.drawable.ic_menu_edit)
        }

        if (position == selectedPosition) {
            holder.card.setCardBackgroundColor(
                ContextCompat.getColor(holder.itemView.context, android.R.color.white)
            )
            holder.fileName.setTypeface(null, Typeface.BOLD)
        } else {
            holder.card.setCardBackgroundColor(
                ContextCompat.getColor(holder.itemView.context, android.R.color.white)
            )
            holder.fileName.setTypeface(null, Typeface.NORMAL)
        }

        holder.card.setOnClickListener {
            val previousPosition = selectedPosition
            selectedPosition = holder.adapterPosition
            notifyItemChanged(previousPosition)
            notifyItemChanged(selectedPosition)
            onItemClick(position)
        }
    }

    override fun getItemCount() = modifications.size

    fun setSelected(position: Int) {
        val previousPosition = selectedPosition
        selectedPosition = position
        notifyItemChanged(previousPosition)
        notifyItemChanged(selectedPosition)
    }
}