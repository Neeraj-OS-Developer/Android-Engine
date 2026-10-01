/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.tom.rv2ide.utils

import android.content.Context
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * Ultra-optimized memory management system for AndroidIDE.
 *
 * <p>Performance characteristics:
 * * **Zero-allocation hot path** when the heap snapshot is unchanged — cached [MemoryInfo].
 * * **Adaptive polling** — 8s when idle, 4s under medium load, 2s under high/critical.
 * * **Lock-free listener iteration** via [CopyOnWriteArraySet].
 * * **Pre-bucketed cleanup tasks** — no per-cycle `filter {}` allocations.
 * * **Integer math** for pressure calculation — no `Float` boxing.
 * * **Level-transition notifications** — listeners only fire on state change.
 *
 * @author Neeraj-OS-developer
 */
class MemoryManager private constructor(context: Context?) {

  private val log = LoggerFactory.getLogger(MemoryManager::class.java)

  /** Kept only for API compatibility — manager does not currently use the context. */
  @Suppress("unused")
  private val contextRef: WeakReference<Context>? = context?.let { WeakReference(it) }

  /** Lock-free iteration; writes (add/remove) are rare. */
  private val memoryPressureListeners = CopyOnWriteArraySet<MemoryPressureListener>()

  /** O(1) register/unregister by task name. */
  private val cleanupTasks = ConcurrentHashMap<String, CleanupTask>()

  /**
   * Priority-sorted task list — rebuilt **only** on register/unregister, never on the monitoring
   * cycle. Sorted ascending so we can iterate in reverse to hit CRITICAL first.
   */
  @Volatile private var tasksByPriority: List<CleanupTask> = emptyList()

  private val isMonitoring = AtomicBoolean(false)
  @Volatile private var monitoringJob: Job? = null

  /** Avoid re-notifying the same pressure level repeatedly. */
  @Volatile private var lastNotifiedLevel: MemoryPressureLevel? = null

  /** Cached snapshot — re-used when heap numbers are unchanged. */
  @Volatile private var cachedMemoryInfo: MemoryInfo? = null

  /** Tracks how many consecutive LOW checks we've seen, for idle back-off. */
  private var consecutiveLowChecks = 0

  companion object {
    @Volatile private var INSTANCE: MemoryManager? = null

    fun getInstance(context: Context? = null): MemoryManager {
      return INSTANCE
          ?: synchronized(this) { INSTANCE ?: MemoryManager(context).also { INSTANCE = it } }
    }

    const val CRITICAL_MEMORY_THRESHOLD = 90 // Percentage
    const val HIGH_MEMORY_THRESHOLD = 80 // Percentage
    const val MEDIUM_MEMORY_THRESHOLD = 70 // Percentage

    /**
     * Legacy constant — retained for API compatibility. The manager now uses adaptive intervals:
     * see [INTERVAL_IDLE], [INTERVAL_MEDIUM], [INTERVAL_HIGH].
     */
    @Deprecated("Adaptive intervals are used instead", ReplaceWith(""))
    const val MEMORY_CHECK_INTERVAL = 5_000L

    // Adaptive intervals (ms).
    private const val INTERVAL_IDLE = 8_000L // LOW after back-off streak
    private const val INTERVAL_MEDIUM = 4_000L // MEDIUM, or LOW warming up
    private const val INTERVAL_HIGH = 2_000L // HIGH / CRITICAL

    /** After this many consecutive LOW checks, drop to [INTERVAL_IDLE]. */
    private const val IDLE_BACKOFF_STREAK = 3
  }

  /** Start monitoring memory usage and perform automatic cleanup. */
  @Synchronized
  fun startMonitoring() {
    if (isMonitoring.get()) {
      if (log.isDebugEnabled) log.debug("Memory monitoring is already active")
      return
    }

    isMonitoring.set(true)
    lastNotifiedLevel = null
    consecutiveLowChecks = 0
    if (log.isInfoEnabled) log.info("Starting memory monitoring")

    // Fresh scope each start — old one may have been cancelled by stopMonitoring().
    monitoringJob =
        CoroutineScope(Dispatchers.Default + SupervisorJob()).launch {
          while (isActive && isMonitoring.get()) {
            val nextDelay =
                try {
                  intervalFor(checkMemoryPressure())
                } catch (t: Throwable) {
                  log.error("Memory monitor cycle failed", t)
                  INTERVAL_HIGH // aggressive retry on error
                }
            delay(nextDelay)
          }
        }
  }

  /** Stop memory monitoring. */
  @Synchronized
  fun stopMonitoring() {
    if (!isMonitoring.compareAndSet(true, false)) return
    monitoringJob?.cancel()
    monitoringJob = null
    if (log.isInfoEnabled) log.info("Stopped memory monitoring")
  }

  private fun intervalFor(level: MemoryPressureLevel): Long =
      when (level) {
        MemoryPressureLevel.CRITICAL,
        MemoryPressureLevel.HIGH -> INTERVAL_HIGH
        MemoryPressureLevel.MEDIUM -> INTERVAL_MEDIUM
        MemoryPressureLevel.LOW -> {
          consecutiveLowChecks++
          if (consecutiveLowChecks >= IDLE_BACKOFF_STREAK) INTERVAL_IDLE else INTERVAL_MEDIUM
        }
      }

  /**
   * Check current memory pressure and trigger appropriate cleanup.
   *
   * @return the pressure level observed.
   */
  private fun checkMemoryPressure(): MemoryPressureLevel {
    val info = getMemoryInfo() ?: return MemoryPressureLevel.LOW
    val level = calculateMemoryPressure(info)

    when (level) {
      MemoryPressureLevel.CRITICAL -> {
        if (log.isWarnEnabled) log.warn("Critical memory pressure detected: ${info.usedPercent}%")
        performCriticalCleanup()
      }
      MemoryPressureLevel.HIGH -> {
        if (log.isWarnEnabled) log.warn("High memory pressure detected: ${info.usedPercent}%")
        performHighCleanup()
      }
      MemoryPressureLevel.MEDIUM -> {
        if (log.isInfoEnabled) log.info("Medium memory pressure detected: ${info.usedPercent}%")
        performMediumCleanup()
      }
      MemoryPressureLevel.LOW -> Unit
    }

    // Only notify listeners on level transitions to prevent spam.
    if (level != lastNotifiedLevel) {
      lastNotifiedLevel = level
      notifyMemoryPressure(level, info)
    }
    return level
  }

  /**
   * Get current memory information. Returns a cached instance when the heap numbers are unchanged,
   * eliminating per-cycle allocation on the hot path.
   */
  private fun getMemoryInfo(): MemoryInfo? {
    val runtime = Runtime.getRuntime()
    val max = runtime.maxMemory()
    if (max <= 0L) return null

    val free = runtime.freeMemory()
    val total = runtime.totalMemory()
    val used = total - free
    // Integer math avoids Float boxing and precision loss.
    val usedPercent = (used * 100L / max).toInt()

    val cached = cachedMemoryInfo
    if (cached != null &&
        cached.usedMemory == used &&
        cached.maxMemory == max &&
        cached.freeMemory == free) {
      return cached
    }

    return MemoryInfo(used, max, free, usedPercent).also { cachedMemoryInfo = it }
  }

  private fun calculateMemoryPressure(info: MemoryInfo): MemoryPressureLevel =
      when {
        info.usedPercent >= CRITICAL_MEMORY_THRESHOLD -> MemoryPressureLevel.CRITICAL
        info.usedPercent >= HIGH_MEMORY_THRESHOLD -> MemoryPressureLevel.HIGH
        info.usedPercent >= MEDIUM_MEMORY_THRESHOLD -> MemoryPressureLevel.MEDIUM
        else -> MemoryPressureLevel.LOW
      }

  /** Perform critical memory cleanup — runs all registered tasks, highest priority first. */
  private fun performCriticalCleanup() {
    val tasks = tasksByPriority
    for (i in tasks.indices.reversed()) {
      val task = tasks[i]
      try {
        task.performCriticalCleanup()
      } catch (t: Throwable) {
        log.error("Error during critical cleanup task: ${task.name}", t)
      }
    }
    // Single GC hint — repeated System.gc() with sleeps is an anti-pattern and wastes CPU.
    System.gc()
  }

  /** Perform high memory cleanup — HIGH and CRITICAL tasks only. */
  private fun performHighCleanup() {
    val tasks = tasksByPriority
    val minOrdinal = CleanupPriority.HIGH.ordinal
    for (i in tasks.indices.reversed()) {
      val task = tasks[i]
      if (task.priority.ordinal < minOrdinal) continue
      try {
        task.performHighCleanup()
      } catch (t: Throwable) {
        log.error("Error during high cleanup task: ${task.name}", t)
      }
    }
  }

  /** Perform medium memory cleanup — MEDIUM, HIGH and CRITICAL tasks. */
  private fun performMediumCleanup() {
    val tasks = tasksByPriority
    val minOrdinal = CleanupPriority.MEDIUM.ordinal
    for (i in tasks.indices.reversed()) {
      val task = tasks[i]
      if (task.priority.ordinal < minOrdinal) continue
      try {
        task.performMediumCleanup()
      } catch (t: Throwable) {
        log.error("Error during medium cleanup task: ${task.name}", t)
      }
    }
  }

  /** Register a cleanup task. */
  @Synchronized
  fun registerCleanupTask(task: CleanupTask) {
    cleanupTasks[task.name] = task
    rebuildTaskBuckets()
    if (log.isInfoEnabled) log.info("Registered cleanup task: ${task.name}")
  }

  /** Unregister a cleanup task. */
  @Synchronized
  fun unregisterCleanupTask(name: String) {
    if (cleanupTasks.remove(name) != null) {
      rebuildTaskBuckets()
      if (log.isInfoEnabled) log.info("Unregistered cleanup task: $name")
    }
  }

  /**
   * Rebuilds the priority-sorted task list. Called **only** on register/unregister — not on every
   * monitoring cycle — eliminating the per-cycle `filter {}` allocation that the previous
   * implementation performed.
   */
  private fun rebuildTaskBuckets() {
    // Ascending sort — we iterate in reverse so CRITICAL / HIGH tasks run first.
    tasksByPriority = cleanupTasks.values.sortedBy { it.priority.ordinal }
  }

  /** Add memory pressure listener. */
  fun addMemoryPressureListener(listener: MemoryPressureListener) {
    memoryPressureListeners.add(listener)
  }

  /** Remove memory pressure listener. */
  fun removeMemoryPressureListener(listener: MemoryPressureListener) {
    memoryPressureListeners.remove(listener)
  }

  private fun notifyMemoryPressure(level: MemoryPressureLevel, memoryInfo: MemoryInfo) {
    // CopyOnWriteArraySet iterates without locks or allocation.
    for (listener in memoryPressureListeners) {
      try {
        listener.onMemoryPressure(level, memoryInfo)
      } catch (t: Throwable) {
        log.error("Error notifying memory pressure listener", t)
      }
    }
  }

  /** Get current memory statistics. */
  fun getMemoryStatistics(): MemoryInfo? = getMemoryInfo()

  /** Force memory cleanup synchronously. */
  fun forceCleanup() {
    if (log.isInfoEnabled) log.info("Forcing memory cleanup")
    performCriticalCleanup()
  }

  /** Memory pressure levels. */
  enum class MemoryPressureLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL,
  }

  /** Cleanup priority levels. */
  enum class CleanupPriority {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL,
  }

  /** Memory information data class. */
  data class MemoryInfo(
      val usedMemory: Long,
      val maxMemory: Long,
      val freeMemory: Long,
      val usedPercent: Int,
  )

  /** Memory pressure listener interface. */
  interface MemoryPressureListener {
    fun onMemoryPressure(level: MemoryPressureLevel, memoryInfo: MemoryInfo)
  }

  /** Cleanup task interface. */
  interface CleanupTask {
    val name: String
    val priority: CleanupPriority

    fun performMediumCleanup()

    fun performHighCleanup()

    fun performCriticalCleanup()
  }
}
