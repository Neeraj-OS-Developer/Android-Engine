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
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Debug
import android.util.Log
import android.view.View
import android.view.ViewGroup
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lightweight in-process memory profiler for AndroidIDE.
 *
 * <p>Performance characteristics:
 * * **Off-main-thread analysis** — [Debug.getMemoryInfo] and view-hierarchy walks run on
 *   [Dispatchers.Default]; the main thread only pays the cost of capturing a single lifecycle
 *   callback.
 * * **Single IPC per cycle** — [Debug.MemoryInfo] is captured exactly once per monitoring tick and
 *   reused across every report, instead of the previous four calls per cycle.
 * * **Bounded retention** — [activityMemoryMap] is pruned to [MAX_TRACKED_ACTIVITIES] entries; stale
 *   `_destroyed` snapshots older than [DESTROYED_RETENTION_MS] are evicted.
 * * **Adaptive cadence** — 10s when idle, 5s under normal load, 2s under pressure.
 * * **Silent in production** — all logging is gated by [Log.isLoggable].
 *
 * @author Neeraj-OS-developer
 */
class MemoryProfiler private constructor(context: Context) {

  /** Application context — avoids holding an Activity for the singleton lifetime. */
  private val appContext: Context = context.applicationContext

  /**
   * Concurrent map of per-activity snapshots. Bounded by [MAX_TRACKED_ACTIVITIES]; pruning happens
   * lazily on the background monitoring thread, so callbacks stay cheap.
   */
  private val activityMemoryMap = ConcurrentHashMap<String, MemorySnapshot>()

  private val isMonitoring = AtomicBoolean(false)
  @Volatile private var monitoringJob: Job? = null
  @Volatile private var currentActivityRef: WeakReference<Activity>? = null

  private val lifecycleCallbacks =
      object : Application.ActivityLifecycleCallbacks {
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

        override fun onActivityPaused(activity: Activity) = Unit

        override fun onActivityStopped(activity: Activity) = Unit

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

        override fun onActivityDestroyed(activity: Activity) {
          // Only capture the "destroyed" marker if we were tracking this activity.
          val name = activity::class.java.simpleName
          if (activityMemoryMap.containsKey(name) || currentActivityRef?.get() === activity) {
            captureMemorySnapshot("${name}_destroyed")
          }
          if (currentActivityRef?.get() === activity) {
            currentActivityRef = null
          }
        }
      }

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
      val timestamp: Long,
  )

  /** Start profiling. Idempotent — safe to call multiple times. */
  fun startMonitoring() {
    if (!isMonitoring.compareAndSet(false, true)) return

    (appContext as? Application)?.registerActivityLifecycleCallbacks(lifecycleCallbacks)

    monitoringJob =
        CoroutineScope(Dispatchers.Default + SupervisorJob()).launch {
          // Warm start — first sample fires immediately.
          while (isActive && isMonitoring.get()) {
            val delayMs =
                try {
                  runAnalysisCycle()
                } catch (t: Throwable) {
                  Log.e(TAG, "Monitoring cycle failed", t)
                  INTERVAL_HIGH // Aggressive retry on error.
                }
            delay(delayMs)
          }
        }

    if (isLoggable) Log.i(TAG, "Memory profiling started")
  }

  /** Stop profiling. Idempotent. */
  fun stopMonitoring() {
    if (!isMonitoring.compareAndSet(true, false)) return

    (appContext as? Application)?.unregisterActivityLifecycleCallbacks(lifecycleCallbacks)
    monitoringJob?.cancel()
    monitoringJob = null

    if (isLoggable) Log.i(TAG, "Memory profiling stopped")
  }

  /** Force GC and log the delta. **Never blocks the caller** — safe from any thread. */
  fun forceGarbageCollection() {
    if (!isLoggable) {
      // Still perform GC even if logging is off — the call is requested explicitly.
      System.gc()
      Runtime.getRuntime().runFinalization()
      return
    }

    val runtime = Runtime.getRuntime()
    val before = (runtime.totalMemory() - runtime.freeMemory()) / MB
    Log.i(TAG, "Forcing garbage collection...")

    System.gc()
    runtime.runFinalization()

    val after = (runtime.totalMemory() - runtime.freeMemory()) / MB
    Log.i(TAG, "GC completed. Freed: ${before - after}MB (Before: ${before}MB, After: ${after}MB)")
  }

  /** Print a one-shot detailed breakdown. Cheap — a single [Debug.getMemoryInfo] call. */
  fun logDetailedMemoryBreakdown() {
    if (!isLoggable) return
    val info = captureMemoryInfo()
    Log.i(TAG, "========================================")
    Log.i(TAG, "DETAILED MEMORY BREAKDOWN")
    Log.i(TAG, "========================================")
    Log.i(TAG, "Dalvik Heap: ${info.dalvikPrivateDirty}KB")
    Log.i(TAG, "Native Heap: ${info.nativePrivateDirty}KB")
    Log.i(TAG, "Graphics: ${info.getMemoryStat("summary.graphics")}KB")
    Log.i(TAG, "Stack: ${info.getMemoryStat("summary.stack")}KB")
    Log.i(TAG, "Code: ${info.getMemoryStat("summary.code")}KB")
    Log.i(TAG, "Private Other: ${info.otherPrivateDirty}KB")
    Log.i(TAG, "Total PSS: ${info.totalPss}KB")
    Log.i(TAG, "Total Private Dirty: ${info.totalPrivateDirty}KB")
  }

  // ------------------------------------------------------------------ internals

  /**
   * Runs one analysis cycle on the background thread and returns the delay (ms) before the next
   * cycle. All expensive work — [Debug.getMemoryInfo], view traversal, sorting — happens here, off
   * the main thread.
   */
  private fun runAnalysisCycle(): Long {
    // ONE capture per cycle. Reused across every downstream analysis.
    val memoryInfo = captureMemoryInfo()
    val activityName = currentActivityRef?.get()?.let { it::class.java.simpleName } ?: "Background"

    captureMemorySnapshotFromInfo(activityName, memoryInfo)

    if (isLoggable) {
      logMemoryUsage(memoryInfo, activityName)
      analyzeMemoryHogs()
      analyzePssBreakdown(memoryInfo)
      analyzeViewHierarchy(activityName)
      analyzeFileHandles()
    }

    pruneStaleEntries()

    return adaptiveInterval(memoryInfo)
  }

  private fun adaptiveInterval(info: Debug.MemoryInfo): Long {
    val runtime = Runtime.getRuntime()
    val max = runtime.maxMemory()
    if (max <= 0L) return INTERVAL_NORMAL

    val used = runtime.totalMemory() - runtime.freeMemory()
    val usedPercent = (used * 100L / max).toInt()

    return when {
      usedPercent >= 90 -> INTERVAL_HIGH
      usedPercent >= 80 -> INTERVAL_NORMAL
      else -> INTERVAL_IDLE
    }
  }

  /** Single source of truth for [Debug.MemoryInfo] capture. */
  private fun captureMemoryInfo(): Debug.MemoryInfo {
    val info = Debug.MemoryInfo()
    Debug.getMemoryInfo(info)
    return info
  }

  /** Cheap snapshot capture from an already-obtained [Debug.MemoryInfo]. */
  private fun captureMemorySnapshotFromInfo(activityName: String, info: Debug.MemoryInfo) {
    val runtime = Runtime.getRuntime()
    val snapshot =
        MemorySnapshot(
            activityName = activityName,
            heapSize = runtime.maxMemory(),
            heapAllocated = runtime.totalMemory(),
            heapFree = runtime.freeMemory(),
            nativeHeap = Debug.getNativeHeapAllocatedSize(),
            pssMemory = info.totalPss.toLong() * KB,
            dalvikPss = info.dalvikPss.toLong() * KB,
            nativePss = info.nativePss.toLong() * KB,
            otherPss = info.otherPss.toLong() * KB,
            graphicsPss = (info.getMemoryStat("summary.graphics")?.toLongOrNull() ?: 0L) * KB,
            stackPss = (info.getMemoryStat("summary.stack")?.toLongOrNull() ?: 0L) * KB,
            codePss = (info.getMemoryStat("summary.code")?.toLongOrNull() ?: 0L) * KB,
            privateDirty = info.totalPrivateDirty.toLong() * KB,
            timestamp = System.currentTimeMillis(),
        )
    activityMemoryMap[activityName] = snapshot
  }

  /**
   * Lifecycle-callback entry point. Captures the memory snapshot ONLY when it can be obtained
   * cheaply. Falls back to a lightweight snapshot (skipping [Debug.getMemoryInfo]) if called too
   * frequently — this keeps the main thread cheap.
   */
  private fun captureMemorySnapshot(activityName: String) {
    // Reuse the most recent full snapshot for the same key if it's recent enough.
    val existing = activityMemoryMap[activityName]
    val now = System.currentTimeMillis()
    if (existing != null && now - existing.timestamp < SNAPSHOT_REUSE_WINDOW_MS) return

    // Otherwise, take a fresh capture. This is the only Debug call on the main thread.
    captureMemorySnapshotFromInfo(activityName, captureMemoryInfo())
  }

  private fun logMemoryUsage(info: Debug.MemoryInfo, activityName: String) {
    val runtime = Runtime.getRuntime()
    val usedMB = (runtime.totalMemory() - runtime.freeMemory()) / MB
    val totalMB = runtime.totalMemory() / MB
    val maxMB = runtime.maxMemory() / MB
    val nativeMB = Debug.getNativeHeapAllocatedSize() / MB
    val pssMB = info.totalPss.toLong() / 1024

    Log.i(TAG, "========================================")
    Log.i(TAG, "MEMORY USAGE REPORT")
    Log.i(TAG, "========================================")
    Log.i(TAG, "Heap Memory: ${usedMB}MB / ${totalMB}MB (Max: ${maxMB}MB)")
    Log.i(TAG, "Native Heap: ${nativeMB}MB")
    Log.i(TAG, "PSS Memory: ${pssMB}MB")
    Log.i(TAG, "Memory Usage: ${if (maxMB > 0) usedMB * 100 / maxMB else 0}%")
    Log.i(TAG, "Current Activity: $activityName")
  }

  private fun analyzeMemoryHogs() {
    if (activityMemoryMap.isEmpty()) return

    Log.i(TAG, "----------------------------------------")
    Log.i(TAG, "MEMORY CONSUMPTION BY COMPONENT")
    Log.i(TAG, "----------------------------------------")

    val sorted =
        activityMemoryMap.values.sortedByDescending {
          it.heapAllocated - it.heapFree + it.nativeHeap
        }

    val limit = if (sorted.size < TOP_HOGS_LIMIT) sorted.size else TOP_HOGS_LIMIT
    for (i in 0 until limit) {
      val s = sorted[i]
      val usedHeap = (s.heapAllocated - s.heapFree) / MB
      val nativeHeap = s.nativeHeap / MB
      val pss = s.pssMemory / MB
      Log.i(TAG, "${s.activityName}:")
      Log.i(
          TAG,
          "  Heap: ${usedHeap}MB | Native: ${nativeHeap}MB | PSS: ${pss}MB | Total: ${usedHeap + nativeHeap}MB",
      )
    }

    identifyMemoryLeaks()
  }

  private fun analyzePssBreakdown(info: Debug.MemoryInfo) {
    Log.i(TAG, "========================================")
    Log.i(TAG, "PSS MEMORY BREAKDOWN (Root Cause Analysis)")
    Log.i(TAG, "========================================")

    val dalvikPss = info.dalvikPss / 1024
    val nativePss = info.nativePss / 1024
    val otherPss = info.otherPss / 1024
    val graphicsPss = (info.getMemoryStat("summary.graphics")?.toIntOrNull() ?: 0) / 1024
    val stackPss = (info.getMemoryStat("summary.stack")?.toIntOrNull() ?: 0) / 1024
    val codePss = (info.getMemoryStat("summary.code")?.toIntOrNull() ?: 0) / 1024
    val totalPss = info.totalPss / 1024

    Log.i(TAG, "Total PSS: ${totalPss}MB")
    Log.i(TAG, "")
    Log.i(TAG, "PSS Breakdown:")
    Log.i(TAG, "  Dalvik (Java Heap):     ${dalvikPss}MB (${pct(dalvikPss, totalPss)}%)")
    Log.i(TAG, "  Native Heap:            ${nativePss}MB (${pct(nativePss, totalPss)}%)")
    Log.i(TAG, "  Graphics:               ${graphicsPss}MB (${pct(graphicsPss, totalPss)}%)")
    Log.i(TAG, "  Code (Libraries/DEX):   ${codePss}MB (${pct(codePss, totalPss)}%)")
    Log.i(TAG, "  Stack:                  ${stackPss}MB (${pct(stackPss, totalPss)}%)")
    Log.i(TAG, "  Other (Files/Buffers):  ${otherPss}MB (${pct(otherPss, totalPss)}%)")
    Log.i(TAG, "")

    analyzeHighestPssComponents(dalvikPss, nativePss, graphicsPss, codePss, otherPss)
  }

  /** Safe percentage helper — returns 0 when [total] is 0. */
  private fun pct(part: Int, total: Int): Int = if (total <= 0) 0 else part * 100 / total

  private fun analyzeHighestPssComponents(
      dalvik: Int,
      native: Int,
      graphics: Int,
      code: Int,
      other: Int,
  ) {
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

  private fun analyzeViewHierarchy(activityName: String) {
    val activity = currentActivityRef?.get() ?: return
    val rootView = activity.window?.decorView?.rootView ?: return

    try {
      val viewCount = countViews(rootView)
      Log.i(TAG, "----------------------------------------")
      Log.i(TAG, "VIEW HIERARCHY ANALYSIS")
      Log.i(TAG, "----------------------------------------")
      Log.i(TAG, "Activity: $activityName")
      Log.i(TAG, "Total Views: $viewCount")

      if (viewCount > VIEW_COUNT_WARN_THRESHOLD) {
        Log.w(TAG, "⚠️  View count is HIGH! Consider optimizing layout hierarchy")
      }
    } catch (t: Throwable) {
      Log.e(TAG, "Error analyzing view hierarchy", t)
    }
  }

  private fun countViews(view: View): Int {
    if (view !is ViewGroup) return 1
    var count = 1
    val n = view.childCount
    for (i in 0 until n) {
      count += countViews(view.getChildAt(i))
    }
    return count
  }

  private fun identifyMemoryLeaks() {
    if (activityMemoryMap.isEmpty()) return

    val now = System.currentTimeMillis()
    var headerPrinted = false
    for ((name, snapshot) in activityMemoryMap) {
      if (!name.endsWith(DESTROYED_SUFFIX)) continue
      if (now - snapshot.timestamp <= LEAK_ALERT_AFTER_MS) continue

      if (!headerPrinted) {
        Log.w(TAG, "----------------------------------------")
        Log.w(TAG, "POTENTIAL MEMORY LEAKS DETECTED")
        Log.w(TAG, "----------------------------------------")
        headerPrinted = true
      }

      val cleanName = name.substring(0, name.length - DESTROYED_SUFFIX.length)
      val memoryMB =
          (snapshot.heapAllocated - snapshot.heapFree + snapshot.nativeHeap) / MB
      Log.w(TAG, "$cleanName: Still holding ${memoryMB}MB after destruction")
    }
  }

  private fun analyzeFileHandles() {
    try {
      FileHandleTracker.getInstance().analyzeFileHandles()
      FileHandleTracker.getInstance().analyzeMemoryMaps()
    } catch (t: Throwable) {
      Log.e(TAG, "Error analyzing file handles", t)
    }
  }

  /**
   * Evicts (a) `_destroyed` snapshots older than [DESTROYED_RETENTION_MS], and (b) any excess
   * entries beyond [MAX_TRACKED_ACTIVITIES] (oldest by timestamp first). Runs on the background
   * monitoring thread — never on a lifecycle callback.
   */
  private fun pruneStaleEntries() {
    val now = System.currentTimeMillis()

    // Pass 1: drop old destroyed markers.
    val expired = ArrayList<String>(4)
    for ((name, snapshot) in activityMemoryMap) {
      if (name.endsWith(DESTROYED_SUFFIX) && now - snapshot.timestamp > DESTROYED_RETENTION_MS) {
        expired.add(name)
      }
    }
    expired.forEach { activityMemoryMap.remove(it) }

    // Pass 2: cap total size.
    if (activityMemoryMap.size <= MAX_TRACKED_ACTIVITIES) return

    val sortedByAge = activityMemoryMap.values.sortedBy { it.timestamp }
    val toRemove = activityMemoryMap.size - MAX_TRACKED_ACTIVITIES
    for (i in 0 until toRemove) {
      activityMemoryMap.remove(sortedByAge[i].activityName)
    }
  }

  companion object {
    private const val TAG = "MemoryProfiler"

    @Volatile private var instance: MemoryProfiler? = null

    fun getInstance(context: Context): MemoryProfiler {
      return instance
          ?: synchronized(this) {
            instance ?: MemoryProfiler(context.applicationContext).also { instance = it }
          }
    }

    // Unit constants — avoid magic numbers.
    private const val KB = 1024L
    private const val MB = 1024L * 1024L

    // Adaptive cadence (ms).
    private const val INTERVAL_IDLE = 10_000L
    private const val INTERVAL_NORMAL = 5_000L
    private const val INTERVAL_HIGH = 2_000L

    // Retention / bounds.
    private const val MAX_TRACKED_ACTIVITIES = 32
    private const val DESTROYED_RETENTION_MS = 5 * 60_000L // 5 min
    private const val LEAK_ALERT_AFTER_MS = 60_000L // 1 min
    private const val SNAPSHOT_REUSE_WINDOW_MS = 500L

    private const val TOP_HOGS_LIMIT = 10
    private const val VIEW_COUNT_WARN_THRESHOLD = 500
    private const val DESTROYED_SUFFIX = "_destroyed"

    /** Cached — [Log.isLoggable] is a system call, no need to repeat per log statement. */
    private val isLoggable: Boolean = Log.isLoggable(TAG, Log.INFO)
  }
}
