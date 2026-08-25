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

package io.github.mohammedbaqernull.logger.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import io.github.mohammedbaqernull.logger.ILogWireService
import io.github.mohammedbaqernull.logger.model.LogEntry
import java.util.concurrent.CopyOnWriteArrayList

/*
 * @author Mohammed-baqer-null @ https://github.com/Mohammed-baqer-null
*/

class LogReceiverService : Service() {
    
    private val registeredApps = CopyOnWriteArrayList<String>()
    private val listeners = CopyOnWriteArrayList<LogListener>()
    
    interface LogListener {
        fun onLogReceived(log: LogEntry)
    }
    
    private val binder = object : ILogWireService.Stub() {
        override fun sendLog(
            packageName: String,
            tag: String,
            message: String,
            level: Int,
            timestamp: Long
        ) {
            val logEntry = LogEntry(
                packageName = packageName,
                tag = tag,
                message = message,
                level = LogEntry.LogLevel.fromInt(level),
                timestamp = timestamp
            )
            
            listeners.forEach { it.onLogReceived(logEntry) }
        }
        
        override fun registerApp(packageName: String) {
            if (!registeredApps.contains(packageName)) {
                registeredApps.add(packageName)
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
    }
    
    override fun onBind(intent: Intent?): IBinder = binder
    
    fun addListener(listener: LogListener) {
        listeners.add(listener)
    }
    
    fun removeListener(listener: LogListener) {
        listeners.remove(listener)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        listeners.clear()
    }
    
    companion object {
        private var instance: LogReceiverService? = null
        fun getInstance(): LogReceiverService? = instance
    }
}