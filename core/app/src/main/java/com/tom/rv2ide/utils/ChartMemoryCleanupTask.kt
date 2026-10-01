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

import android.os.Handler
import android.os.Looper
import android.view.View
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory

/**
 * Cleanup task specifically for chart memory optimization. Reduces memory usage by optimizing chart
 * data and rendering.
 *
 * <p>Thread-safety: safe to call from any thread. All chart mutations are marshalled to the main
 * thread because MPAndroidChart is not thread-safe.
 *
 * @author Neeraj-OS-developer
 */
class ChartMemoryCleanupTask : MemoryManager.CleanupTask {

  private val log = LoggerFactory.getLogger(ChartMemoryCleanupTask::class.java)

  /**
   * Thread-safe set of weak references. Dead entries are pruned lazily during iteration to prevent
   * the set from growing unbounded.
   */
  private val chartReferences: MutableSet<WeakReference<LineChart>> = ConcurrentHashMap.newKeySet()

  private val mainHandler: Handler = Handler(Looper.getMainLooper())

  override val name: String = "ChartMemoryCleanup"

  override val priority: MemoryManager.CleanupPriority = MemoryManager.CleanupPriority.HIGH

  /** Register a chart for memory cleanup. Safe to call multiple times (no-op duplicates). */
  fun registerChart(chart: LineChart) {
    chartReferences.add(WeakReference(chart))
    if (log.isDebugEnabled) log.debug("Registered chart for memory cleanup")
  }

  /** Unregister a chart from memory cleanup. Uses reference equality to avoid stale removals. */
  fun unregisterChart(chart: LineChart) {
    chartReferences.removeAll { it.get() === chart }
    if (log.isDebugEnabled) log.debug("Unregistered chart from memory cleanup")
  }

  override fun performMediumCleanup() {
    if (log.isDebugEnabled) log.debug("Performing medium chart cleanup")
    forEachLiveChart { chart -> optimizeChartData(chart, reduceDataPoints = true) }
  }

  override fun performHighCleanup() {
    if (log.isDebugEnabled) log.debug("Performing high chart cleanup")
    forEachLiveChart { chart ->
      optimizeChartData(chart, reduceDataPoints = true, clearHistory = true)
    }
  }

  override fun performCriticalCleanup() {
    if (log.isDebugEnabled) log.debug("Performing critical chart cleanup")

    // Compute memory state ONCE — Runtime queries are relatively expensive.
    val critical = isMemoryCritical()

    forEachLiveChart { chart ->
      if (critical) {
        disableChart(chart)
      } else {
        optimizeChartData(
            chart,
            reduceDataPoints = true,
            clearHistory = true,
            minimizeRendering = true,
        )
      }
    }
  }

  /**
   * Iterates over live charts and prunes dead weak references in-place. Prevents the internal set
   * from accumulating stale entries across the app lifetime.
   */
  private fun forEachLiveChart(action: (LineChart) -> Unit) {
    val iterator = chartReferences.iterator()
    while (iterator.hasNext()) {
      val chart = iterator.next().get()
      if (chart == null) {
        iterator.remove()
      } else {
        action(chart)
      }
    }
  }

  /**
   * MPAndroidChart must only be touched on the main thread. If we're already on main, execute
   * synchronously to avoid extra latency; otherwise post to the main looper.
   */
  private fun runOnUi(block: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
      block()
    } else {
      mainHandler.post(block)
    }
  }

  /** Optimize chart data to reduce memory usage. */
  private fun optimizeChartData(
      chart: LineChart,
      reduceDataPoints: Boolean = false,
      clearHistory: Boolean = false,
      minimizeRendering: Boolean = false,
  ) {
    runOnUi {
      try {
        val data = chart.data as? LineData ?: return@runOnUi
        if (data.dataSets.isEmpty()) return@runOnUi

        data.dataSets.forEach { dataSet ->
          if (dataSet !is LineDataSet) return@forEach

          val entries = dataSet.values ?: return@forEach
          val size = entries.size
          if (size == 0) return@forEach

          // Decide the replacement list — null means "no change needed".
          val newValues: List<Entry>? =
              when {
                // Aggressive: keep only the newest N points.
                clearHistory && size > HISTORY_KEEP -> {
                  ArrayList(entries.subList(size - HISTORY_KEEP, size))
                }

                // Medium: stride-based downsampling. Always keeps the most recent sample.
                reduceDataPoints && size > MIN_REDUCE_THRESHOLD -> {
                  val step = (size / TARGET_POINTS).coerceAtLeast(2)
                  val sampled = ArrayList<Entry>(size / step + 2)
                  var i = 0
                  while (i < size) {
                    sampled.add(entries[i])
                    i += step
                  }
                  // Ensure latest point is preserved (important for live charts).
                  if (sampled.isEmpty() || sampled[sampled.size - 1] !== entries[size - 1]) {
                    sampled.add(entries[size - 1])
                  }
                  sampled
                }

                else -> null
              }

          if (newValues != null) {
            // Single atomic replace — MPAndroidChart's setValues() internally clears + repopulates.
            dataSet.values = newValues
          }
        }

        if (minimizeRendering) {
          chart.setDrawGridBackground(false)
          chart.setDrawBorders(false)
          chart.description?.isEnabled = false
          chart.legend?.isEnabled = false

          data.dataSets.forEach { ds ->
            if (ds is LineDataSet) {
              ds.setDrawCircles(false)
              ds.setDrawCircleHole(false)
              ds.setDrawValues(false)
              ds.setDrawIcons(false)
              ds.lineWidth = 1f
            }
          }
        }

        chart.notifyDataSetChanged()
        chart.invalidate()
      } catch (e: Exception) {
        // Never let cleanup crash the app — swallow and log.
        log.error("Error optimizing chart data", e)
      }
    }
  }

  /** Disable chart to save memory. */
  private fun disableChart(chart: LineChart) {
    runOnUi {
      try {
        chart.clear()
        chart.data = LineData()
        chart.visibility = View.GONE
        if (log.isWarnEnabled) log.warn("Disabled chart due to critical memory pressure")
      } catch (e: Exception) {
        log.error("Error disabling chart", e)
      }
    }
  }

  /** Check if memory is critically low. Returns false if maxMemory is unknown/invalid. */
  private fun isMemoryCritical(): Boolean {
    val runtime = Runtime.getRuntime()
    val max = runtime.maxMemory()
    if (max <= 0L) return false

    val used = runtime.totalMemory() - runtime.freeMemory()
    // Integer math — avoids float allocation and precision loss.
    return used * 100L / max >= CRITICAL_THRESHOLD_PERCENT
  }

  /**
   * Manual cleanup hook — retained for API compatibility. Dead references are also pruned
   * automatically during iteration via [forEachLiveChart].
   */
  fun cleanupWeakReferences() {
    chartReferences.removeAll { it.get() == null }
  }

  private companion object {
    /** Number of most-recent entries to retain in HIGH cleanup. */
    private const val HISTORY_KEEP = 5

    /** Don't bother downsampling below this size — overhead not worth it. */
    private const val MIN_REDUCE_THRESHOLD = 10

    /** Target point count after MEDIUM downsampling. */
    private const val TARGET_POINTS = 50

    /** Used-heap percentage above which memory is treated as critical. */
    private const val CRITICAL_THRESHOLD_PERCENT = 95L
  }
}
