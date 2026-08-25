/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.tom.rv2ide.inflater.internal.adapters

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import com.tom.rv2ide.annotations.inflater.ViewAdapter
import com.tom.rv2ide.annotations.uidesigner.IncludeInDesigner
import com.tom.rv2ide.annotations.uidesigner.IncludeInDesigner.Group.LAYOUTS
import com.tom.rv2ide.inflater.AttributeHandlerScope
import com.tom.rv2ide.inflater.IView
import com.tom.rv2ide.inflater.internal.LayoutFile
import com.tom.rv2ide.inflater.models.UiWidget
import com.tom.rv2ide.resources.R.drawable
import com.tom.rv2ide.resources.R.string

/**
 * Attribute adapter for [ConstraintLayout].
 *
 * @author Mohammed-baqer-null @ https://github.com/Mohammed-baqer-null
 */
@ViewAdapter(ConstraintLayout::class)
@IncludeInDesigner(group = LAYOUTS)
open class ConstraintLayoutAdapter<T : ConstraintLayout> : ViewGroupAdapter<T>() {

  override fun createAttrHandlers(create: (String, AttributeHandlerScope<T>.() -> Unit) -> Unit) {
    super.createAttrHandlers(create)

    // Add background attribute handler
    create("background") {
      val background = parseBackground(value, view.context)
      if (background != null) {
        view.background = background
      }
    }
  }

  override fun createUiWidgets(): List<UiWidget> {
    val constraintLayout =
        ConstraintLayoutWidget(
            title = string.widget_constraint_layout,
            icon = drawable.ic_widget_linear_layout_vert,
        )
    return listOf(constraintLayout)
  }

  /**
   * Parses background attribute value and returns appropriate Drawable. Supports:
   * - Color values: #RRGGBB, #AARRGGBB, color names
   * - Drawable resources: @drawable/name
   * - Color resources: @color/name
   */
  protected open fun parseBackground(value: String, context: Context): Drawable? {
    return try {
      when {
        // Handle color values (hex colors)
        value.startsWith("#") -> {
          val color = Color.parseColor(value)
          ColorDrawable(color)
        }

        // Handle drawable resources (@drawable/name)
        value.startsWith("@drawable/") -> {
          val resourceName = value.substring(10) // Remove "@drawable/"
          val resourceId =
              context.resources.getIdentifier(resourceName, "drawable", context.packageName)
          if (resourceId != 0) {
            ContextCompat.getDrawable(context, resourceId)
          } else {
            null
          }
        }

        // Handle color resources (@color/name)
        value.startsWith("@color/") -> {
          val resourceName = value.substring(7) // Remove "@color/"
          val resourceId =
              context.resources.getIdentifier(resourceName, "color", context.packageName)
          if (resourceId != 0) {
            val color = ContextCompat.getColor(context, resourceId)
            ColorDrawable(color)
          } else {
            null
          }
        }

        // Handle Android system resources (@android:drawable/name, @android:color/name)
        value.startsWith("@android:drawable/") -> {
          val resourceName = value.substring(18) // Remove "@android:drawable/"
          val resourceId = android.R.drawable::class.java.getField(resourceName).getInt(null)
          ContextCompat.getDrawable(context, resourceId)
        }

        value.startsWith("@android:color/") -> {
          val resourceName = value.substring(15) // Remove "@android:color/"
          val resourceId = android.R.color::class.java.getField(resourceName).getInt(null)
          val color = ContextCompat.getColor(context, resourceId)
          ColorDrawable(color)
        }

        // Handle named colors (red, blue, green, etc.)
        else -> {
          try {
            val color = Color.parseColor(value)
            ColorDrawable(color)
          } catch (e: IllegalArgumentException) {
            // If it's not a valid color name, return null
            null
          }
        }
      }
    } catch (e: Exception) {
      // Log the error if needed, but don't crash
      // Log.w("ConstraintLayoutAdapter", "Failed to parse background: $value", e)
      null
    }
  }

  private class ConstraintLayoutWidget(
      @StringRes title: Int,
      @DrawableRes icon: Int,
  ) : UiWidget(ConstraintLayout::class.java, title, icon) {

    override fun createView(context: Context, parent: ViewGroup, layoutFile: LayoutFile): IView {
      return super.createView(context, parent, layoutFile)
    }
  }
}
