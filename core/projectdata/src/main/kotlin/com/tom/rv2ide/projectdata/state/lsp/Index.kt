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

package com.tom.rv2ide.projectdata.state.lsp

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/*
 * State holder for managing lsp indexing status
 * Provides observable state via StateFlow for reactive updates across modules
 * @author Mohammed-baqer-null @ https://github.com/Mohammed-baqer-null
 */

object Index {

  /*
   * @variable Project indexing?
   * Observable via StateFlow for reactive updates across Activities/Fragments
   */
  private val _isProjectIndexing = MutableStateFlow(false)
  val isProjectIndexing: StateFlow<Boolean>
    get() = _isProjectIndexing

  /*
   * @function Update indexing state
   */
  fun setIsIndexing(isIndexing: Boolean) {
    _isProjectIndexing.value = isIndexing
  }

  /*
   * @function Check current state (optional helper for readability)
   */
  fun isIndexing(): Boolean = _isProjectIndexing.value
}
