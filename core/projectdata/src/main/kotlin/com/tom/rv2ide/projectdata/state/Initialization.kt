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

package com.tom.rv2ide.projectdata.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/*
 * State holder for managing project initialization status
 * Provides observable state via StateFlow for reactive updates across modules
 * @author Mohammed-baqer-null @ https://github.com/Mohammed-baqer-null
 */

object Initialization {

  /*
   * @variable Project initialized?
   * Observable via StateFlow for reactive updates across Activities/Fragments
   */
  private val _isProjectInitializing = MutableStateFlow(false)
  val isProjectInitializing: StateFlow<Boolean>
    get() = _isProjectInitializing

  /*
   * @function Update initialization state
   */
  fun setInitializing(isInitializing: Boolean) {
    _isProjectInitializing.value = isInitializing
  }

  /*
   * @function Check current state (optional helper for readability)
   */
  fun isInitializing(): Boolean = _isProjectInitializing.value
}
