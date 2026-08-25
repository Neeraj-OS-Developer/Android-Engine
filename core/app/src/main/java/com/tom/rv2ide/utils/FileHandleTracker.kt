/*
 * This file is part of AndroidIDE.
 *
 * AndroidIDE is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AndroidIDE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package com.tom.rv2ide.utils

import android.os.Process
import android.util.Log
import java.io.File

class FileHandleTracker private constructor() {

  data class FileHandleInfo(
    val fd: String,
    val path: String,
    val size: Long
  )

  fun scanOpenFileHandles(): List<FileHandleInfo> {
    val fileHandles = mutableListOf<FileHandleInfo>()
    val pid = Process.myPid()
    val fdDir = File("/proc/$pid/fd")

    try {
      if (!fdDir.exists() || !fdDir.isDirectory) {
        Log.w(TAG, "Cannot access /proc/$pid/fd")
        return emptyList()
      }

      fdDir.listFiles()?.forEach { fdLink ->
        try {
          val canonicalPath = fdLink.canonicalPath
          val targetFile = File(canonicalPath)
          
          val size = if (targetFile.exists() && targetFile.isFile) {
            targetFile.length()
          } else {
            0L
          }

          fileHandles.add(
            FileHandleInfo(
              fd = fdLink.name,
              path = canonicalPath,
              size = size
            )
          )
        } catch (e: Exception) {
          
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error scanning file handles", e)
    }

    return fileHandles
  }

  fun analyzeFileHandles() {
    val handles = scanOpenFileHandles()
    
    Log.i(TAG, "========================================")
    Log.i(TAG, "OPEN FILE HANDLES ANALYSIS")
    Log.i(TAG, "========================================")
    Log.i(TAG, "Total open file descriptors: ${handles.size}")
    
    val categorized = categorizeFileHandles(handles)
    
    categorized.forEach { (category, files) ->
      if (files.isNotEmpty()) {
        val totalSize = files.sumOf { it.size }
        Log.i(TAG, "")
        Log.i(TAG, "$category: ${files.size} files (${totalSize / (1024 * 1024)}MB)")
        
        files.sortedByDescending { it.size }.take(5).forEach { file ->
          Log.i(TAG, "  ${file.path} (${file.size / 1024}KB)")
        }
      }
    }
    
    identifyMemoryMappedFiles(handles)
    identifyPotentialLeaks(handles)
  }

  private fun categorizeFileHandles(handles: List<FileHandleInfo>): Map<String, List<FileHandleInfo>> {
    val categories = mutableMapOf<String, MutableList<FileHandleInfo>>()
    
    handles.forEach { handle ->
      val category = when {
        handle.path.contains("/dev/") -> "Device Files"
        handle.path.contains(".so") -> "Native Libraries (.so)"
        handle.path.contains(".dex") || handle.path.contains(".odex") -> "DEX Files"
        handle.path.contains(".apk") -> "APK Files"
        handle.path.contains(".jar") -> "JAR Files"
        handle.path.contains(".ttf") || handle.path.contains(".otf") -> "Font Files"
        handle.path.contains(".xml") -> "XML Resources"
        handle.path.contains(".png") || handle.path.contains(".jpg") || handle.path.contains(".webp") -> "Image Files"
        handle.path.contains("pipe:") || handle.path.contains("socket:") -> "Pipes/Sockets"
        handle.path.contains("/data/") && handle.path.contains(".db") -> "Database Files"
        handle.path.contains("/cache/") -> "Cache Files"
        else -> "Other Files"
      }
      
      categories.getOrPut(category) { mutableListOf() }.add(handle)
    }
    
    return categories
  }

  private fun identifyMemoryMappedFiles(handles: List<FileHandleInfo>) {
    val largeFiles = handles.filter { it.size > 10 * 1024 * 1024 }
    
    if (largeFiles.isNotEmpty()) {
      Log.w(TAG, "")
      Log.w(TAG, "⚠️  LARGE MEMORY-MAPPED FILES DETECTED:")
      largeFiles.sortedByDescending { it.size }.forEach { file ->
        Log.w(TAG, "  ${file.path}: ${file.size / (1024 * 1024)}MB")
      }
      Log.w(TAG, "  These files may be memory-mapped and consuming PSS memory")
    }
  }

  private fun identifyPotentialLeaks(handles: List<FileHandleInfo>) {
    val suspiciousPatterns = listOf(
      "/cache/" to "Unclosed cache files",
      ".tmp" to "Temporary files not cleaned",
      ".log" to "Log files keeping handles open"
    )
    
    suspiciousPatterns.forEach { (pattern, warning) ->
      val matches = handles.filter { it.path.contains(pattern) }
      if (matches.size > 10) {
        Log.w(TAG, "")
        Log.w(TAG, "⚠️  POTENTIAL LEAK: $warning")
        Log.w(TAG, "  Found ${matches.size} files matching '$pattern'")
        matches.take(3).forEach { 
          Log.w(TAG, "    ${it.path}")
        }
      }
    }
  }

  fun scanMemoryMaps(): Map<String, Long> {
    val pid = Process.myPid()
    val mapsFile = File("/proc/$pid/maps")
    val memoryRegions = mutableMapOf<String, Long>()

    try {
      if (!mapsFile.exists()) {
        Log.w(TAG, "Cannot access /proc/$pid/maps")
        return emptyMap()
      }

      mapsFile.forEachLine { line ->
        val parts = line.split(Regex("\\s+"))
        if (parts.size >= 6) {
          val addressRange = parts[0]
          val filePath = parts.drop(5).joinToString(" ")
          
          if (filePath.isNotBlank() && !filePath.startsWith("[")) {
            val addresses = addressRange.split("-")
            if (addresses.size == 2) {
              try {
                val start = addresses[0].toLong(16)
                val end = addresses[1].toLong(16)
                val size = end - start
                
                memoryRegions[filePath] = (memoryRegions[filePath] ?: 0L) + size
              } catch (e: Exception) {
                
              }
            }
          }
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error reading memory maps", e)
    }

    return memoryRegions
  }

  fun analyzeMemoryMaps() {
    val memoryMaps = scanMemoryMaps()
    
    Log.i(TAG, "========================================")
    Log.i(TAG, "MEMORY-MAPPED FILES ANALYSIS")
    Log.i(TAG, "========================================")
    
    val sortedMaps = memoryMaps.entries.sortedByDescending { it.value }.take(20)
    
    var totalMappedMB = 0L
    sortedMaps.forEach { (path, size) ->
      val sizeMB = size / (1024 * 1024)
      totalMappedMB += sizeMB
      Log.i(TAG, "$sizeMB MB - $path")
    }
    
    Log.i(TAG, "")
    Log.i(TAG, "Total memory-mapped: ${totalMappedMB}MB")
    
    if (totalMappedMB > 200) {
      Log.w(TAG, "")
      Log.w(TAG, "⚠️  EXCESSIVE MEMORY-MAPPED FILES!")
      Log.w(TAG, "  This contributes to 'Other' PSS memory")
      Log.w(TAG, "  Consider lazy loading or unloading unused resources")
    }
  }

  companion object {
    private const val TAG = "FileHandleTracker"
    
    @Volatile
    private var instance: FileHandleTracker? = null

    fun getInstance(): FileHandleTracker {
      return instance ?: synchronized(this) {
        instance ?: FileHandleTracker().also { instance = it }
      }
    }
  }
}
