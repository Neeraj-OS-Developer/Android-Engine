package com.tom.rv2ide.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.tom.rv2ide.R

class TextAdpater(private val items: List<MenuItem>, private val onItemClick: (MenuItem) -> Unit) :
        RecyclerView.Adapter<TextAdpater.MenuViewHolder>() {

  inner class MenuViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    val icon: ImageView = view.findViewById(R.id.menuIcon)
    val title: TextView = view.findViewById(R.id.menuTitle)
    val divider: View = view.findViewById(R.id.divider)

    init {
      view.setOnClickListener {
        val position = adapterPosition
        if (position != RecyclerView.NO_POSITION) {
          onItemClick(items[position])
        }
      }
    }
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MenuViewHolder {
    val view = LayoutInflater.from(parent.context).inflate(R.layout.item_menu, parent, false)
    return MenuViewHolder(view)
  }

  override fun onBindViewHolder(holder: MenuViewHolder, position: Int) {
    val item = items[position]
    holder.title.text = item.title
    holder.icon.setImageResource(item.iconRes)

    // Apply theme tint to icon
    val iconColor =
            MaterialColors.getColor(holder.icon, com.google.android.material.R.attr.colorOnSurface)
    holder.icon.setColorFilter(iconColor)

    // Hide divider for last item
    if (position == items.size - 1) {
      holder.divider.visibility = View.GONE
    } else {
      holder.divider.visibility = View.VISIBLE
    }
  }

  override fun getItemCount(): Int = items.size
}

data class MenuItem(val title: String, val iconRes: Int)
