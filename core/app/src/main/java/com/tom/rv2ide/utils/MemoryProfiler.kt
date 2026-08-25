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

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

class MemoryProfiler private constructor(private val context: Context) {

  private val activityMemoryMap = ConcurrentHashMap<String, MemorySnapshot>()
  private val handler = Handler(Looper.getMainLooper())
  private var isMonitoring = false
  private val monitoringInterval = 5000L
  private var currentActivityRef: WeakReference<Activity>? = null

  data class MemorySnapshot(
    val activityName: String,
    val heapSize: Long,
    val heapAllocated: Long,
    val heapFree: Long,
    val nativeHeap: Long,
    val pssMemory: Long,
    val dalvikPss: Long,
    val nativePss: Long,
    val otherPss: Long,
    val graphicsPss: Long,
    val stackPss: Long,
    val codePss: Long,
    val privateDirty: Long,
    val timestamp: Long
  )

  private val activityLifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
      currentActivityRef = WeakReference(activity)
      captureMemorySnapshot(activity::class.java.simpleName)
    }

    override fun onActivityStarted(activity: Activity) {
      currentActivityRef = WeakReference(activity)
    }

    override fun onActivityResumed(activity: Activity) {
      currentActivityRef = WeakReference(activity)
      captureMemorySnapshot(activity::class.java.simpleName)
    }

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {}

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {
      captureMemorySnapshot(activity::class.java.simpleName + "_destroyed")
      if (currentActivityRef?.get() == activity) {
        currentActivityRef = null
      }
    }
  }

  fun startMonitoring() {
    if (isMonitoring) return
    isMonitoring = true

    (context.applicationContext as? Application)?.registerActivityLifecycleCallbacks(
      activityLifecycleCallbacks
    )

    handler.post(monitoringRunnable)
    Log.i(TAG, "Memory profiling started")
  }

  fun stopMonitoring() {
    if (!isMonitoring) return
    isMonitoring = false

    (context.applicationContext as? Application)?.unregisterActivityLifecycleCallbacks(
      activityLifecycleCallbacks
    )

    handler.removeCallbacks(monitoringRunnable)
    Log.i(TAG, "Memory profiling stopped")
  }

  private val monitoringRunnable = object : Runnable {
    override fun run() {
      if (!isMonitoring) return
  
      val currentActivity = currentActivityRef?.get()
      val activityName = currentActivity?.let { it::class.java.simpleName } ?: "Background"
      
      captureMemorySnapshot(activityName)
      logMemoryUsage()
      analyzeMemoryHogs()
      analyzePssBreakdown()
      analyzeViewHierarchy()
      analyzeFileHandles()
  
      handler.postDelayed(this, monitoringInterval)
    }
  }

  private fun analyzeFileHandles() {
    try {
      FileHandleTracker.getInstance().analyzeFileHandles()
      FileHandleTracker.getInstance().analyzeMemoryMaps()
    } catch (e: Exception) {
      Log.e(TAG, "Error analyzing file handles", e)
    }
  }
  
  private fun captureMemorySnapshot(activityName: String) {
    val runtime = Runtime.getRuntime()
    val memoryInfo = Debug.MemoryInfo()
    Debug.getMemoryInfo(memoryInfo)

    val snapshot = MemorySnapshot(
      activityName = activityName,
      heapSize = runtime.maxMemory(),
      heapAllocated = runtime.totalMemory(),
      heapFree = runtime.freeMemory(),
      nativeHeap = Debug.getNativeHeapAllocatedSize(),
      pssMemory = memoryInfo.totalPss.toLong() * 1024,
      dalvikPss = memoryInfo.dalvikPss.toLong() * 1024,
      nativePss = memoryInfo.nativePss.toLong() * 1024,
      otherPss = memoryInfo.otherPss.toLong() * 1024,
      graphicsPss = (memoryInfo.getMemoryStat("summary.graphics")?.toLongOrNull() ?: 0L) * 1024,
      stackPss = (memoryInfo.getMemoryStat("summary.stack")?.toLongOrNull() ?: 0L) * 1024,
      codePss = (memoryInfo.getMemoryStat("summary.code")?.toLongOrNull() ?: 0L) * 1024,
      privateDirty = memoryInfo.totalPrivateDirty.toLong() * 1024,
      timestamp = System.currentTimeMillis()
    )

    activityMemoryMap[activityName] = snapshot
  }

  private fun logMemoryUsage() {
    val runtime = Runtime.getRuntime()
    val usedMemoryMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
    val totalMemoryMB = runtime.totalMemory() / (1024 * 1024)
    val maxMemoryMB = runtime.maxMemory() / (1024 * 1024)
    val nativeHeapMB = Debug.getNativeHeapAllocatedSize() / (1024 * 1024)

    val memoryInfo = Debug.MemoryInfo()
    Debug.getMemoryInfo(memoryInfo)
    val pssMB = memoryInfo.totalPss / 1024

    Log.i(TAG, "========================================")
    Log.i(TAG, "MEMORY USAGE REPORT")
    Log.i(TAG, "========================================")
    Log.i(TAG, "Heap Memory: ${usedMemoryMB}MB / ${totalMemoryMB}MB (Max: ${maxMemoryMB}MB)")
    Log.i(TAG, "Native Heap: ${nativeHeapMB}MB")
    Log.i(TAG, "PSS Memory: ${pssMB}MB")
    Log.i(TAG, "Memory Usage: ${(usedMemoryMB * 100 / maxMemoryMB)}%")
    
    val currentActivity = currentActivityRef?.get()
    if (currentActivity != null) {
      Log.i(TAG, "Current Activity: ${currentActivity::class.java.simpleName}")
    }
  }

  private fun analyzeMemoryHogs() {
    if (activityMemoryMap.isEmpty()) return

    Log.i(TAG, "----------------------------------------")
    Log.i(TAG, "MEMORY CONSUMPTION BY COMPONENT")
    Log.i(TAG, "----------------------------------------")

    val sortedByMemory = activityMemoryMap.entries.sortedByDescending { 
      it.value.heapAllocated - it.value.heapFree + it.value.nativeHeap 
    }

    sortedByMemory.take(10).forEach { (name, snapshot) ->
      val usedHeap = (snapshot.heapAllocated - snapshot.heapFree) / (1024 * 1024)
      val nativeHeap = snapshot.nativeHeap / (1024 * 1024)
      val pss = snapshot.pssMemory / (1024 * 1024)
      val total = usedHeap + nativeHeap

      Log.i(TAG, "$name:")
      Log.i(TAG, "  Heap: ${usedHeap}MB | Native: ${nativeHeap}MB | PSS: ${pss}MB | Total: ${total}MB")
    }

    identifyMemoryLeaks()
  }

  private fun analyzePssBreakdown() {
    val memoryInfo = Debug.MemoryInfo()
    Debug.getMemoryInfo(memoryInfo)

    Log.i(TAG, "========================================")
    Log.i(TAG, "PSS MEMORY BREAKDOWN (Root Cause Analysis)")
    Log.i(TAG, "========================================")
    
    val dalvikPss = memoryInfo.dalvikPss / 1024
    val nativePss = memoryInfo.nativePss / 1024
    val otherPss = memoryInfo.otherPss / 1024
    val graphicsPss = (memoryInfo.getMemoryStat("summary.graphics")?.toIntOrNull() ?: 0) / 1024
    val stackPss = (memoryInfo.getMemoryStat("summary.stack")?.toIntOrNull() ?: 0) / 1024
    val codePss = (memoryInfo.getMemoryStat("summary.code")?.toIntOrNull() ?: 0) / 1024
    val totalPss = memoryInfo.totalPss / 1024
    
    Log.i(TAG, "Total PSS: ${totalPss}MB")
    Log.i(TAG, "")
    Log.i(TAG, "PSS Breakdown:")
    Log.i(TAG, "  Dalvik (Java Heap):     ${dalvikPss}MB (${(dalvikPss * 100 / totalPss)}%)")
    Log.i(TAG, "  Native Heap:            ${nativePss}MB (${(nativePss * 100 / totalPss)}%)")
    Log.i(TAG, "  Graphics:               ${graphicsPss}MB (${(graphicsPss * 100 / totalPss)}%)")
    Log.i(TAG, "  Code (Libraries/DEX):   ${codePss}MB (${(codePss * 100 / totalPss)}%)")
    Log.i(TAG, "  Stack:                  ${stackPss}MB (${(stackPss * 100 / totalPss)}%)")
    Log.i(TAG, "  Other (Files/Buffers):  ${otherPss}MB (${(otherPss * 100 / totalPss)}%)")
    Log.i(TAG, "")
    
    analyzeHighestPssComponents(dalvikPss, nativePss, graphicsPss, codePss, otherPss)
  }

  private fun analyzeHighestPssComponents(dalvik: Int, native: Int, graphics: Int, code: Int, other: Int) {
    Log.i(TAG, "HIGH PSS CULPRITS:")
    
    if (graphics > 100) {
      Log.w(TAG, "⚠️  GRAPHICS: ${graphics}MB is HIGH!")
      Log.w(TAG, "   Causes: Large bitmaps, textures, UI views, hardware buffers")
      Log.w(TAG, "   Fix: Optimize images, reduce view complexity, recycle bitmaps")
    }
    
    if (code > 100) {
      Log.w(TAG, "⚠️  CODE: ${code}MB is HIGH!")
      Log.w(TAG, "   Causes: Many shared libraries (.so files), DEX code, resources")
      Log.w(TAG, "   Fix: Reduce dependencies, use ProGuard/R8, lazy load libraries")
    }
    
    if (native > 100) {
      Log.w(TAG, "⚠️  NATIVE: ${native}MB is HIGH!")
      Log.w(TAG, "   Causes: Native code allocations, JNI objects, file descriptors")
      Log.w(TAG, "   Fix: Profile native code, check for leaks, optimize buffers")
    }
    
    if (other > 100) {
      Log.w(TAG, "⚠️  OTHER: ${other}MB is HIGH!")
      Log.w(TAG, "   Causes: Memory-mapped files, cursors, file caches, system buffers")
      Log.w(TAG, "   Fix: Close cursors/streams, clear caches, check file handles")
    }
    
    if (dalvik > 150) {
      Log.w(TAG, "⚠️  DALVIK: ${dalvik}MB is HIGH!")
      Log.w(TAG, "   Causes: Large objects, memory leaks, cached data")
      Log.w(TAG, "   Fix: Use memory profiler, fix leaks, optimize data structures")
    }
  }

  private fun analyzeViewHierarchy() {
    val currentActivity = currentActivityRef?.get() ?: return
    
    try {
      val rootView = currentActivity.window?.decorView?.rootView
      if (rootView != null) {
        val viewCount = countViews(rootView)
        val bitmapMemory = estimateBitmapMemory(rootView)
        
        Log.i(TAG, "----------------------------------------")
        Log.i(TAG, "VIEW HIERARCHY ANALYSIS")
        Log.i(TAG, "----------------------------------------")
        Log.i(TAG, "Total Views: $viewCount")
        Log.i(TAG, "Estimated Bitmap Memory: ${bitmapMemory / (1024 * 1024)}MB")
        
        if (viewCount > 500) {
          Log.w(TAG, "⚠️  View count is HIGH! Consider optimizing layout hierarchy")
        }
        
        if (bitmapMemory > 50 * 1024 * 1024) {
          Log.w(TAG, "⚠️  Bitmap memory is HIGH! Optimize images and use memory caching")
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error analyzing view hierarchy", e)
    }
  }

  private fun countViews(view: View): Int {
    var count = 1
    if (view is ViewGroup) {
      for (i in 0 until view.childCount) {
        count += countViews(view.getChildAt(i))
      }
    }
    return count
  }

  private fun estimateBitmapMemory(view: View): Long {
    return 0L
  }

  private fun identifyMemoryLeaks() {
    val currentTime = System.currentTimeMillis()
    val staleActivities = activityMemoryMap.filter { (name, snapshot) ->
      name.endsWith("_destroyed") && (currentTime - snapshot.timestamp) > 60000
    }

    if (staleActivities.isNotEmpty()) {
      Log.w(TAG, "----------------------------------------")
      Log.w(TAG, "POTENTIAL MEMORY LEAKS DETECTED")
      Log.w(TAG, "----------------------------------------")
      staleActivities.forEach { (name, snapshot) ->
        val cleanName = name.replace("_destroyed", "")
        val memoryMB = (snapshot.heapAllocated - snapshot.heapFree + snapshot.nativeHeap) / (1024 * 1024)
        Log.w(TAG, "$cleanName: Still holding ${memoryMB}MB after destruction")
      }
    }
  }

  fun logDetailedMemoryBreakdown() {
    val memoryInfo = Debug.MemoryInfo()
    Debug.getMemoryInfo(memoryInfo)

    Log.i(TAG, "========================================")
    Log.i(TAG, "DETAILED MEMORY BREAKDOWN")
    Log.i(TAG, "========================================")
    Log.i(TAG, "Dalvik Heap: ${memoryInfo.dalvikPrivateDirty}KB")
    Log.i(TAG, "Native Heap: ${memoryInfo.nativePrivateDirty}KB")
    Log.i(TAG, "Graphics: ${memoryInfo.getMemoryStat("summary.graphics")}KB")
    Log.i(TAG, "Stack: ${memoryInfo.getMemoryStat("summary.stack")}KB")
    Log.i(TAG, "Code: ${memoryInfo.getMemoryStat("summary.code")}KB")
    Log.i(TAG, "Private Other: ${memoryInfo.otherPrivateDirty}KB")
    Log.i(TAG, "Total PSS: ${memoryInfo.totalPss}KB")
    Log.i(TAG, "Total Private Dirty: ${memoryInfo.totalPrivateDirty}KB")
  }

  fun forceGarbageCollection() {
    Log.i(TAG, "Forcing garbage collection...")
    val beforeMB = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024)
    
    System.gc()
    System.runFinalization()
    
    Thread.sleep(100)
    
    val afterMB = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024)
    val freedMB = beforeMB - afterMB
    
    Log.i(TAG, "GC completed. Freed: ${freedMB}MB (Before: ${beforeMB}MB, After: ${afterMB}MB)")
  }

  companion object {
    private const val TAG = "MemoryProfiler"
    
    @Volatile
    private var instance: MemoryProfiler? = null

    fun getInstance(context: Context): MemoryProfiler {
      return instance ?: synchronized(this) {
        instance ?: MemoryProfiler(context.applicationContext).also { instance = it }
      }
    }
  }
}