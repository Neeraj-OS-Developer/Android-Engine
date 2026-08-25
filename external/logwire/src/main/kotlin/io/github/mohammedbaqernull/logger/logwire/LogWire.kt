/*
 *  This file is part of LogWire.
 *
 *  LogWire is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  LogWire is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with LogWire.  If not, see <https://www.gnu.org/licenses/>.
 */
 
package io.github.mohammedbaqernull.logger.logwire

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import io.github.mohammedbaqernull.logger.ILogWireService
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * LogWire - A lightweight logcat reader and forwarder for Android applications.
 *
 * This class provides automatic logcat reading and forwarding to a centralized log receiver service.
 * It reads logcat output for the current process and sends parsed log entries via AIDL to the
 * LogReceiverService for display and management.
 *
 * Features:
 * - Automatic initialization via ContentProvider
 * - Process-specific logcat filtering
 * - AIDL-based inter-component communication
 * - Thread-safe log reading and forwarding
 * - Singleton pattern for app-wide usage
 *
 * @author Mohammed-baqer-null @ https://github.com/Mohammed-baqer-null
 */
class LogWire private constructor(private val context: Context) {
    
    /** AIDL service interface for sending logs to the receiver */
    private var logService: ILogWireService? = null
    
    /** Current application package name */
    private val packageName = context.packageName
    
    /** Background thread for reading logcat output */
    private var logcatThread: Thread? = null
    
    /** Flag indicating if logcat reader is actively running */
    private var isRunning = false
    
    /** Flag indicating if service connection is established */
    private var isConnected = false
    
    private val TAG = "LogWire"
    
    /**
     * Service connection callback handler.
     * Manages the lifecycle of the AIDL service connection.
     */
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "✅ Service connected!")
            logService = ILogWireService.Stub.asInterface(service)
            isConnected = true
            
            try {
                logService?.registerApp(packageName)
                Log.d(TAG, "✅ Registered app: $packageName")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to register app", e)
            }
            
            startLogcatReader()
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "❌ Service disconnected")
            stopLogcatReader()
            logService = null
            isConnected = false
        }
    }
    
    /**
     * Starts the LogWire service by binding to the LogReceiverService.
     * 
     * This method:
     * 1. Verifies the logger package is installed
     * 2. Creates an explicit intent to the LogReceiverService
     * 3. Binds to the service with BIND_AUTO_CREATE flag
     * 
     * If binding fails, appropriate error logs are generated.
     */
    fun start() {
        Log.d(TAG, "🚀 Starting LogWire for package: $packageName")
        
        // Check if logger app is installed
        val loggerPackage = io.github.mohammedbaqernull.logger.configurations.PACKAGE_NAME
        try {
            context.packageManager.getPackageInfo(loggerPackage, 0)
            Log.d(TAG, "✅ Logger app is installed")
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "❌ Logger app NOT installed: $loggerPackage")
            return
        }
        
        val intent = Intent().apply {
            component = ComponentName(
                loggerPackage,
                "io.github.mohammedbaqernull.logger.service.LogReceiverService"
            )
        }
        
        try {
            val bound = context.bindService(
                intent, 
                serviceConnection, 
                Context.BIND_AUTO_CREATE
            )
            Log.d(TAG, "Bind service result: $bound")
            
            if (!bound) {
                Log.e(TAG, "❌ Failed to bind - service not available")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception binding service", e)
            e.printStackTrace()
        }
    }
    
    /**
     * Starts a background thread to read logcat output.
     * 
     * The logcat reader:
     * - Filters logs by current process PID
     * - Uses brief format for parsing
     * - Retrieves last 100 log lines plus streaming output
     * - Parses and forwards each log line to the service
     * - Runs until explicitly stopped or service disconnects
     * 
     * Thread safety: Only one logcat reader thread runs at a time.
     */
    private fun startLogcatReader() {
        if (isRunning) {
            Log.d(TAG, "⚠️ Logcat reader already running")
            return
        }
        
        Log.d(TAG, "📖 Starting logcat reader...")
        isRunning = true
        
        logcatThread = Thread {
            try {
                // Get current process PID for filtering
                val pid = android.os.Process.myPid()
                Log.d(TAG, "Starting logcat for PID: $pid")
                
                // Start reading logcat with:
                // -v brief: Use brief output format
                // --pid=$pid: Filter by process ID
                // -T 100: Show last 100 lines then continue streaming
                val process = Runtime.getRuntime().exec(
                    arrayOf("logcat", "-v", "brief", "--pid=$pid", "-T", "100")
                )
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                
                Log.d(TAG, "✅ Logcat reader started, reading logs...")
                
                var lineCount = 0
                var parsedCount = 0
                var line: String?
                
                while (isRunning && isConnected) {
                    line = reader.readLine()
                    if (line == null) {
                        Log.d(TAG, "❌ Logcat stream ended")
                        break
                    }
                    
                    lineCount++
                    
                    if (parseAndSendLog(line)) {
                        parsedCount++
                    }
                    
                    // Progress logging every 50 lines
                    if (lineCount % 50 == 0) {
                        Log.d(TAG, "📊 Read: $lineCount lines, Sent: $parsedCount logs")
                    }
                }
                
                reader.close()
                process.destroy()
                Log.d(TAG, "Logcat reader stopped. Total lines: $lineCount, Parsed: $parsedCount")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Logcat reader error", e)
                e.printStackTrace()
            }
        }
        logcatThread?.start()
    }
    
    /**
     * Parses a logcat line and sends it to the LogReceiverService.
     * 
     * Expected format: "LEVEL/TAG(PID): message"
     * Example: "D/MainActivity(12345): Hello World"
     * 
     * @param line Raw logcat output line
     * @return true if the line was successfully parsed and sent, false otherwise
     * 
     * The parser:
     * - Extracts log level (V/D/I/W/E/A)
     * - Extracts tag name
     * - Extracts message content
     * - Converts log level to integer value
     * - Sends via AIDL to the service
     * - Filters out LogWire's own debug logs to prevent loops
     */
    private fun parseAndSendLog(line: String): Boolean {
        if (!isConnected) return false
        
        try {
            if (line.isBlank()) return false
            
            // Brief format regex: "LEVEL/TAG(PID): message"
            val regex = """^([VDIWEA])/([^(]+)\(\s*\d+\)\s*:\s+(.+)$""".toRegex()
            val match = regex.find(line.trim())
            
            if (match == null) {
                return false
            }
            
            val groups = match.groupValues
            if (groups.size < 4) return false
            
            val levelChar = groups[1]
            val tag = groups[2].trim()
            val message = groups[3]
            
            if (tag.isEmpty() || message.isEmpty()) return false
            
            // Prevent infinite loop by not sending our own debug logs
            if (tag == TAG) return false
            
            // Convert log level character to Android log level integer
            val level = when (levelChar) {
                "V" -> 2  // VERBOSE
                "D" -> 3  // DEBUG
                "I" -> 4  // INFO
                "W" -> 5  // WARN
                "E" -> 6  // ERROR
                "A" -> 7  // ASSERT
                else -> 4 // Default to INFO
            }
            
            logService?.sendLog(packageName, tag, message, level, System.currentTimeMillis())
            
            // Sample debug logging (10% of messages)
            if (Math.random() < 0.1) {
                Log.d(TAG, "✅ Sent: [$levelChar/$tag] ${message.take(50)}${if(message.length > 50) "..." else ""}")
            }
            
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error parsing/sending log: $line", e)
            return false
        }
    }
    
    /**
     * Stops the logcat reader thread.
     * 
     * Sets the running flag to false and interrupts the thread.
     * The thread will terminate gracefully on its next iteration.
     */
    private fun stopLogcatReader() {
        Log.d(TAG, "🛑 Stopping logcat reader...")
        isRunning = false
        logcatThread?.interrupt()
        logcatThread = null
    }
    
    /**
     * Stops LogWire and unbinds from the service.
     * 
     * This method:
     * 1. Stops the logcat reader thread
     * 2. Unbinds from the LogReceiverService
     * 3. Cleans up connection state
     * 
     * Safe to call multiple times - checks connection state before unbinding.
     */
    fun stop() {
        try {
            stopLogcatReader()
            if (isConnected) {
                context.unbindService(serviceConnection)
                isConnected = false
                Log.d(TAG, "Unbound from service")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unbinding", e)
        }
    }
    
    companion object {
        /** Singleton instance of LogWire */
        @Volatile
        private var instance: LogWire? = null
        
        /**
         * Initializes the LogWire singleton instance.
         * 
         * This method is thread-safe using double-checked locking.
         * Should be called once during application startup, typically
         * via LogWireInitializer ContentProvider.
         * 
         * @param context Application context for service binding
         */
        fun initialize(context: Context) {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) {
                        instance = LogWire(context.applicationContext)
                        instance?.start()
                    }
                }
            }
        }
    }
}