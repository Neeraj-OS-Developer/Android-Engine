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

package com.tom.rv2ide.projectdata.logs

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/*
 * State holder for managing command output logs
 * Emits log lines as they arrive and auto-clears after consumption
 * Useful for streaming command execution output (gradle, shell, etc.)
 * @author Mohammed-baqer-null @ https://github.com/Mohammed-baqer-null
 */

object LogStream {

  /*
   * SharedFlow for emitting log lines
   * Each collector receives the message once, then it's cleared
   * replay = 0 means no buffering (immediate consumption)
   */
  private val _outputFlow = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 64)
  val outputFlow: SharedFlow<String> = _outputFlow.asSharedFlow()

  /*
   * Emit a single log line
   * @param line The output line to emit
   */
  suspend fun emitLine(line: String) {
    _outputFlow.emit(line)
  }

  /*
   * Emit a single log line (non-suspend variant)
   * @param line The output line to emit
   */
  fun emitLineBlocking(line: String) {
    _outputFlow.tryEmit(line)
  }

  /*
   * Emit multiple lines at once
   * @param lines List of output lines
   */
  suspend fun emitLines(lines: List<String>) {
    lines.forEach { _outputFlow.emit(it) }
  }
}
