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
import android.content.SharedPreferences
import org.slf4j.LoggerFactory

/**
 * Configuration for memory optimization settings. Allows users to customize memory management
 * behavior.
 *
 * <p>Performance characteristics:
 * * **In-memory [Snapshot] cache** — property getters are plain field reads (no SharedPreferences
 *   lookup, no boxing).
 * * **Batched writes** — [applyLowMemorySettings], [applyHighMemorySettings] and
 *   [resetToDefaults] perform a **single** SharedPreferences transaction instead of nine.
 * * **Thread-safe setters** — `@Synchronized` read-modify-write on the snapshot prevents lost
 *   updates under concurrent access.
 * * **Guarded logging** — hot setters check `isInfoEnabled` before building the log string.
 *
 * @author Neeraj-OS-developer
 */
class MemoryOptimizationConfig private constructor(context: Context) {

  private val log = LoggerFactory.getLogger(MemoryOptimizationConfig::class.java)

  /** Application context — avoids holding an Activity reference for the singleton lifetime. */
  private val prefs: SharedPreferences =
      context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  /**
   * Immutable snapshot of every setting. All getters read from this field instead of hitting
   * SharedPreferences on every access.
   *
   * Marked `@Volatile` so readers always observe a fully-constructed snapshot after a write.
   */
  @Volatile private var cache: Snapshot = Snapshot.readFrom(prefs)

  companion object {
    @Volatile private var INSTANCE: MemoryOptimizationConfig? = null

    @JvmStatic
    fun getInstance(context: Context): MemoryOptimizationConfig {
      return INSTANCE
          ?: synchronized(this) {
            INSTANCE ?: MemoryOptimizationConfig(context).also { INSTANCE = it }
          }
    }

    private const val PREFS_NAME = "memory_optimization"

    // Keys — extracted so we never risk typos across getter/setter/editor pairs.
    private const val KEY_MEMORY_PRESSURE_THRESHOLD = "memory_pressure_threshold"
    private const val KEY_CHART_UPDATE_INTERVAL = "chart_update_interval"
    private const val KEY_MAX_CACHE_SIZE = "max_cache_size"
    private const val KEY_LARGE_FILE_THRESHOLD = "large_file_threshold"
    private const val KEY_LARGE_PROJECT_THRESHOLD = "large_project_threshold"
    private const val KEY_OPTIMIZATION_ENABLED = "optimization_enabled"
    private const val KEY_AGGRESSIVE_CLEANUP_ENABLED = "aggressive_cleanup_enabled"
    private const val KEY_CHART_OPTIMIZATION_ENABLED = "chart_optimization_enabled"
    private const val KEY_LARGE_PROJECT_OPTIMIZATION_ENABLED = "large_project_optimization_enabled"

    // Default values
    private const val DEFAULT_MEMORY_PRESSURE_THRESHOLD = 85
    private const val DEFAULT_CHART_UPDATE_INTERVAL = 2_000L
    private const val DEFAULT_CACHE_SIZE = 50
    private const val DEFAULT_LARGE_FILE_THRESHOLD = 1024L * 1024L // 1 MB
    private const val DEFAULT_LARGE_PROJECT_THRESHOLD = 1_000

    /** Pre-built default snapshot — reused by [resetToDefaults] without allocation. */
    private val DEFAULT_SNAPSHOT =
        Snapshot(
            memoryPressureThreshold = DEFAULT_MEMORY_PRESSURE_THRESHOLD,
            chartUpdateInterval = DEFAULT_CHART_UPDATE_INTERVAL,
            maxCacheSize = DEFAULT_CACHE_SIZE,
            largeFileThreshold = DEFAULT_LARGE_FILE_THRESHOLD,
            largeProjectThreshold = DEFAULT_LARGE_PROJECT_THRESHOLD,
            optimizationEnabled = true,
            aggressiveCleanupEnabled = false,
            chartOptimizationEnabled = true,
            largeProjectOptimizationEnabled = true,
        )
  }

  /** Memory pressure threshold percentage. */
  var memoryPressureThreshold: Int
    get() = cache.memoryPressureThreshold
    set(value) = setInt(KEY_MEMORY_PRESSURE_THRESHOLD, value) {
      cache = cache.copy(memoryPressureThreshold = value)
      if (log.isInfoEnabled) log.info("Memory pressure threshold set to: $value%")
    }

  /** Chart update interval in milliseconds. */
  var chartUpdateInterval: Long
    get() = cache.chartUpdateInterval
    set(value) = setLong(KEY_CHART_UPDATE_INTERVAL, value) {
      cache = cache.copy(chartUpdateInterval = value)
      if (log.isInfoEnabled) log.info("Chart update interval set to: ${value}ms")
    }

  /** Maximum cache size for file content. */
  var maxCacheSize: Int
    get() = cache.maxCacheSize
    set(value) = setInt(KEY_MAX_CACHE_SIZE, value) {
      cache = cache.copy(maxCacheSize = value)
      if (log.isInfoEnabled) log.info("Max cache size set to: $value")
    }

  /** Large file threshold in bytes. */
  var largeFileThreshold: Long
    get() = cache.largeFileThreshold
    set(value) = setLong(KEY_LARGE_FILE_THRESHOLD, value) {
      cache = cache.copy(largeFileThreshold = value)
      if (log.isInfoEnabled) log.info("Large file threshold set to: $value bytes")
    }

  /** Large project threshold (number of files). */
  var largeProjectThreshold: Int
    get() = cache.largeProjectThreshold
    set(value) = setInt(KEY_LARGE_PROJECT_THRESHOLD, value) {
      cache = cache.copy(largeProjectThreshold = value)
      if (log.isInfoEnabled) log.info("Large project threshold set to: $value files")
    }

  /** Enable memory optimization. */
  var isOptimizationEnabled: Boolean
    get() = cache.optimizationEnabled
    set(value) = setBoolean(KEY_OPTIMIZATION_ENABLED, value) {
      cache = cache.copy(optimizationEnabled = value)
      if (log.isInfoEnabled) {
        log.info("Memory optimization ${if (value) "enabled" else "disabled"}")
      }
    }

  /** Enable aggressive cleanup. */
  var isAggressiveCleanupEnabled: Boolean
    get() = cache.aggressiveCleanupEnabled
    set(value) = setBoolean(KEY_AGGRESSIVE_CLEANUP_ENABLED, value) {
      cache = cache.copy(aggressiveCleanupEnabled = value)
      if (log.isInfoEnabled) {
        log.info("Aggressive cleanup ${if (value) "enabled" else "disabled"}")
      }
    }

  /** Enable chart memory optimization. */
  var isChartOptimizationEnabled: Boolean
    get() = cache.chartOptimizationEnabled
    set(value) = setBoolean(KEY_CHART_OPTIMIZATION_ENABLED, value) {
      cache = cache.copy(chartOptimizationEnabled = value)
      if (log.isInfoEnabled) {
        log.info("Chart optimization ${if (value) "enabled" else "disabled"}")
      }
    }

  /** Enable large project optimization. */
  var isLargeProjectOptimizationEnabled: Boolean
    get() = cache.largeProjectOptimizationEnabled
    set(value) = setBoolean(KEY_LARGE_PROJECT_OPTIMIZATION_ENABLED, value) {
      cache = cache.copy(largeProjectOptimizationEnabled = value)
      if (log.isInfoEnabled) {
        log.info("Large project optimization ${if (value) "enabled" else "disabled"}")
      }
    }

  /** Reset all settings to defaults — single transaction, single cache swap. */
  @Synchronized
  fun resetToDefaults() {
    prefs.edit().clear().apply()
    cache = DEFAULT_SNAPSHOT
    if (log.isInfoEnabled) log.info("Memory optimization settings reset to defaults")
  }

  /**
   * Get all current settings as a map. Backed by the in-memory snapshot — no SharedPreferences
   * lookups and no per-call primitive boxing on the fast path (the map itself is still allocated,
   * but this method is not a hot path).
   */
  fun getAllSettings(): Map<String, Any> {
    val s = cache
    return linkedMapOf(
        KEY_MEMORY_PRESSURE_THRESHOLD to s.memoryPressureThreshold,
        KEY_CHART_UPDATE_INTERVAL to s.chartUpdateInterval,
        KEY_MAX_CACHE_SIZE to s.maxCacheSize,
        KEY_LARGE_FILE_THRESHOLD to s.largeFileThreshold,
        KEY_LARGE_PROJECT_THRESHOLD to s.largeProjectThreshold,
        KEY_OPTIMIZATION_ENABLED to s.optimizationEnabled,
        KEY_AGGRESSIVE_CLEANUP_ENABLED to s.aggressiveCleanupEnabled,
        KEY_CHART_OPTIMIZATION_ENABLED to s.chartOptimizationEnabled,
        KEY_LARGE_PROJECT_OPTIMIZATION_ENABLED to s.largeProjectOptimizationEnabled,
    )
  }

  /**
   * Apply optimized settings for low-memory devices. **Single SharedPreferences transaction** — the
   * previous implementation fired nine separate `edit().apply()` calls.
   */
  @Synchronized
  fun applyLowMemorySettings() {
    val newSnapshot =
        DEFAULT_SNAPSHOT.copy(
            memoryPressureThreshold = 70,
            chartUpdateInterval = 5_000L,
            maxCacheSize = 25,
            largeFileThreshold = 512L * 1024L, // 512 KB
            largeProjectThreshold = 500,
            optimizationEnabled = true,
            aggressiveCleanupEnabled = true,
            chartOptimizationEnabled = true,
            largeProjectOptimizationEnabled = true,
        )
    commit(newSnapshot)
    if (log.isInfoEnabled) log.info("Applied low memory settings")
  }

  /**
   * Apply optimized settings for high-memory devices. **Single SharedPreferences transaction**.
   */
  @Synchronized
  fun applyHighMemorySettings() {
    val newSnapshot =
        DEFAULT_SNAPSHOT.copy(
            memoryPressureThreshold = 90,
            chartUpdateInterval = 1_000L,
            maxCacheSize = 100,
            largeFileThreshold = 2L * 1024L * 1024L, // 2 MB
            largeProjectThreshold = 2_000,
            optimizationEnabled = true,
            aggressiveCleanupEnabled = false,
            chartOptimizationEnabled = false,
            largeProjectOptimizationEnabled = false,
        )
    commit(newSnapshot)
    if (log.isInfoEnabled) log.info("Applied high memory settings")
  }

  // ---------------------------------------------------------------- internals

  /** Writes every field of [snapshot] in a single SharedPreferences transaction. */
  private fun commit(snapshot: Snapshot) {
    prefs
        .edit()
        .putInt(KEY_MEMORY_PRESSURE_THRESHOLD, snapshot.memoryPressureThreshold)
        .putLong(KEY_CHART_UPDATE_INTERVAL, snapshot.chartUpdateInterval)
        .putInt(KEY_MAX_CACHE_SIZE, snapshot.maxCacheSize)
        .putLong(KEY_LARGE_FILE_THRESHOLD, snapshot.largeFileThreshold)
        .putInt(KEY_LARGE_PROJECT_THRESHOLD, snapshot.largeProjectThreshold)
        .putBoolean(KEY_OPTIMIZATION_ENABLED, snapshot.optimizationEnabled)
        .putBoolean(KEY_AGGRESSIVE_CLEANUP_ENABLED, snapshot.aggressiveCleanupEnabled)
        .putBoolean(KEY_CHART_OPTIMIZATION_ENABLED, snapshot.chartOptimizationEnabled)
        .putBoolean(KEY_LARGE_PROJECT_OPTIMIZATION_ENABLED, snapshot.largeProjectOptimizationEnabled)
        .apply()
    cache = snapshot
  }

  @Synchronized
  private inline fun setInt(key: String, value: Int, onApplied: () -> Unit) {
    prefs.edit().putInt(key, value).apply()
    onApplied()
  }

  @Synchronized
  private inline fun setLong(key: String, value: Long, onApplied: () -> Unit) {
    prefs.edit().putLong(key, value).apply()
    onApplied()
  }

  @Synchronized
  private inline fun setBoolean(key: String, value: Boolean, onApplied: () -> Unit) {
    prefs.edit().putBoolean(key, value).apply()
    onApplied()
  }

  /**
   * Immutable holder for all settings. Single reference swap on write ensures atomic multi-field
   * reads for any caller that captures the snapshot through a getter.
   */
  private data class Snapshot(
      val memoryPressureThreshold: Int,
      val chartUpdateInterval: Long,
      val maxCacheSize: Int,
      val largeFileThreshold: Long,
      val largeProjectThreshold: Int,
      val optimizationEnabled: Boolean,
      val aggressiveCleanupEnabled: Boolean,
      val chartOptimizationEnabled: Boolean,
      val largeProjectOptimizationEnabled: Boolean,
  ) {
    companion object {
      fun readFrom(prefs: SharedPreferences): Snapshot =
          Snapshot(
              memoryPressureThreshold =
                  prefs.getInt(
                      KEY_MEMORY_PRESSURE_THRESHOLD,
                      DEFAULT_MEMORY_PRESSURE_THRESHOLD,
                  ),
              chartUpdateInterval =
                  prefs.getLong(KEY_CHART_UPDATE_INTERVAL, DEFAULT_CHART_UPDATE_INTERVAL),
              maxCacheSize = prefs.getInt(KEY_MAX_CACHE_SIZE, DEFAULT_CACHE_SIZE),
              largeFileThreshold =
                  prefs.getLong(KEY_LARGE_FILE_THRESHOLD, DEFAULT_LARGE_FILE_THRESHOLD),
              largeProjectThreshold =
                  prefs.getInt(KEY_LARGE_PROJECT_THRESHOLD, DEFAULT_LARGE_PROJECT_THRESHOLD),
              optimizationEnabled = prefs.getBoolean(KEY_OPTIMIZATION_ENABLED, true),
              aggressiveCleanupEnabled = prefs.getBoolean(KEY_AGGRESSIVE_CLEANUP_ENABLED, false),
              chartOptimizationEnabled = prefs.getBoolean(KEY_CHART_OPTIMIZATION_ENABLED, true),
              largeProjectOptimizationEnabled =
                  prefs.getBoolean(KEY_LARGE_PROJECT_OPTIMIZATION_ENABLED, true),
          )
    }
  }
}
