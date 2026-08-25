/*
 *  This file is part of LogWire.
 *
 *  LogWire is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  LogWire is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with LogWire.  If not, see <https://www.gnu.org/licenses/>.
*/

package io.github.mohammedbaqernull.logger.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * @author Mohammed-baqer-null @ https://github.com/Mohammed-baqer-null
*/

data class LogEntry(
    val packageName: String,
    val tag: String,
    val message: String,
    val level: LogLevel,
    val timestamp: Long
) {
    enum class LogLevel(val value: Int, val label: String, val color: Int) {
        VERBOSE(2, "V", 0xFF888888.toInt()),
        DEBUG(3, "D", 0xFF2196F3.toInt()),
        INFO(4, "I", 0xFF4CAF50.toInt()),
        WARN(5, "W", 0xFFFFC107.toInt()),
        ERROR(6, "E", 0xFFF44336.toInt()),
        ASSERT(7, "A", 0xFF9C27B0.toInt());
        
        companion object {
            fun fromInt(value: Int): LogLevel {
                return entries.find { it.value == value } ?: INFO
            }
        }
    }
    
    fun getFormattedTime(): String {
        val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}