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

package com.tom.rv2ide.actions.sidebar

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.tom.rv2ide.R
import com.tom.rv2ide.actions.ActionData
import com.tom.rv2ide.actions.requireContext
import com.tom.rv2ide.activities.AssetStudioActivity
import com.tom.rv2ide.fragments.sidebar.AssetStudioFragment
import com.tom.rv2ide.fragments.sidebar.GitClientFragment
import kotlin.reflect.KClass

/**
 * Sidebar action for opening the Git client.
 *
 * @author Mohammed-baqer-null @ https://github.com/Mohammed-baqer-null
 */
class GitClientAction(context: Context, override val order: Int) :
    AbstractSidebarAction() {

  companion object {
    const val ID = "ide.editor.sidebar.gitclient"
  }

  override val id: String = ID
  override val fragmentClass: KClass<out Fragment> = GitClientFragment::class

  init {
    label = context.getString(R.string.sidebar_git_title)
    icon = ContextCompat.getDrawable(context, R.drawable.ic_git)
    iconRes = R.drawable.ic_git
  }
}
