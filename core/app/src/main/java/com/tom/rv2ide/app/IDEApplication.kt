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

package com.tom.rv2ide.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.StrictMode
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Observer
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.Operation
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.blankj.utilcode.util.ThrowableUtils.getFullStackTrace
import com.google.android.material.color.DynamicColors
import com.termux.app.TermuxApplication
import com.termux.shared.reflection.ReflectionUtils
import com.tom.rv2ide.BuildConfig
import com.tom.rv2ide.activities.CrashHandlerActivity
import com.tom.rv2ide.activities.editor.IDELogcatReader
import com.tom.rv2ide.buildinfo.BuildInfo
import com.tom.rv2ide.editor.schemes.IDEColorSchemeProvider
import com.tom.rv2ide.eventbus.events.preferences.PreferenceChangeEvent
import com.tom.rv2ide.events.AppEventsIndex
import com.tom.rv2ide.events.EditorEventsIndex
import com.tom.rv2ide.events.LspApiEventsIndex
import com.tom.rv2ide.events.LspJavaEventsIndex
import com.tom.rv2ide.preferences.internal.DevOpsPreferences
import com.tom.rv2ide.preferences.internal.GeneralPreferences
import com.tom.rv2ide.preferences.internal.StatPreferences
import com.tom.rv2ide.resources.localization.LocaleProvider
import com.tom.rv2ide.stats.AndroidIDEStats
import com.tom.rv2ide.stats.StatUploadWorker
import com.tom.rv2ide.syntax.colorschemes.SchemeAndroidIDE
import com.tom.rv2ide.treesitter.TreeSitter
import com.tom.rv2ide.ui.themes.IDETheme
import com.tom.rv2ide.ui.themes.IThemeManager
import com.tom.rv2ide.utils.ChartMemoryCleanupTask
import com.tom.rv2ide.utils.Environment
import com.tom.rv2ide.utils.MemoryManager
import com.tom.rv2ide.utils.MemoryProfiler
import com.tom.rv2ide.utils.RecyclableObjectPool
import com.tom.rv2ide.utils.VMUtils
import com.tom.rv2ide.utils.flashError
import io.github.mohammedbaqernull.seasonal.SeasonalEffects
import io.github.miyazkaori.silentinstaller.SilentInstaller
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.lang.Thread.UncaughtExceptionHandler
import java.time.Duration
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.slf4j.LoggerFactory

class IDEApplication : TermuxApplication() {

  private var uncaughtExceptionHandler: UncaughtExceptionHandler? = null
  private var ideLogcatReader: IDELogcatReader? = null
  private var memoryManager: MemoryManager? = null
  private var chartCleanupTask: ChartMemoryCleanupTask? = null
  private var memoryProfiler: MemoryProfiler? = null

  /** Guard so asset extraction / native init never runs twice. */
  private val assetsExtracted = AtomicBoolean(false)

  init {
    RecyclableObjectPool.DEBUG = BuildConfig.DEBUG
  }

  override fun attachBaseContext(base: Context) {
    super.attachBaseContext(base)

    // Silent installer must be initialised before any Application-level logic.
    runCatching { SilentInstaller.init(this) }
        .onFailure { log.error("Failed to init SilentInstaller", it) }

    // Native TreeSitter must be loaded after base context is attached.
    if (!VMUtils.isJvm()) {
      runCatching { TreeSitter.loadLibrary() }
          .onFailure { log.error("Failed to load TreeSitter library", it) }
    }
  }

  @OptIn(DelicateCoroutinesApi::class)
  override fun onCreate() {
    instance = this

    // Preserve the platform default handler so we can chain to it on crash.
    uncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, th -> handleCrash(thread, th) }

    super.onCreate()

    // --- Seasonal overlay (cheap, avoid work when disabled) ---
    if (GeneralPreferences.snowfallOverlay && isSnowfallSeasonActive()) {
      runCatching {
            SeasonalEffects.init(this)
            SeasonalEffects.enableChristmas()
            SeasonalEffects.setSnowflakeCount(20)
          }
          .onFailure { log.error("Failed to initialise seasonal effects", it) }
    }

    // --- Debug-only diagnostics ---
    if (BuildConfig.DEBUG) {
      runCatching {
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder(StrictMode.getVmPolicy())
                    .penaltyLog()
                    .detectAll()
                    .build()
            )
          }
          .onFailure { log.warn("Failed to install StrictMode VmPolicy", it) }

      if (DevOpsPreferences.dumpLogs) {
        startLogcatReader()
      }
      // Memory profiler is opt-in; enabling it in production causes jank.
      // if (DevOpsPreferences.enableMemoryProfiler) initializeMemoryProfiler()
    }

    // --- EventBus ---
    EventBus.builder()
        .addIndex(AppEventsIndex())
        .addIndex(EditorEventsIndex())
        .addIndex(LspApiEventsIndex())
        .addIndex(LspJavaEventsIndex())
        .installDefaultEventBus(true)

    EventBus.getDefault().register(this)

    // --- UI / theme ---
    AppCompatDelegate.setDefaultNightMode(GeneralPreferences.uiMode)

    if (IThemeManager.getInstance().getCurrentTheme() == IDETheme.MATERIAL_YOU) {
      DynamicColors.applyToActivitiesIfAvailable(this)
    }

    EditorColorScheme.setDefault(SchemeAndroidIDE.newInstance(null))

    ReflectionUtils.bypassHiddenAPIReflectionRestrictions()
    GlobalScope.launch(Dispatchers.Default) { IDEColorSchemeProvider.init() }

    // --- One-time asset extraction (non-blocking) ---
    GlobalScope.launch(Dispatchers.IO) { extractBundledAssetsOnce() }

    // --- Optional subsystems (disabled by default) ---
    // initializeMemoryManagement()

    // DISABLED: Plugin system completely disabled to prevent Tooling API issues
    // initializePluginSystem()
  }

  /**
   * Extract bundled assets only once per process.
   * Both targets are skipped when a non-empty file already exists, which avoids
   * redundant disk writes on every cold start.
   */
  private fun extractBundledAssetsOnce() {
    if (!assetsExtracted.compareAndSet(false, true)) return

    extractAssetIfMissing(
        assetPath = "fonts/jetbrains-mono.ttf",
        target = File(File(Environment.HOME, ".androidide/ui"), "jetbrains-mono.ttf"),
    )

    extractAssetIfMissing(
        assetPath = "logger-runtime.aar",
        target = File(File(Environment.HOME, "plugins/logger"), "logger-runtime.aar"),
    )
  }

  private fun extractAssetIfMissing(assetPath: String, target: File) {
    try {
      if (target.exists() && target.length() > 0L) {
        log.debug("Asset already extracted, skipping: {}", target.absolutePath)
        return
      }

      target.parentFile?.let { if (!it.exists() && !it.mkdirs()) {
        log.warn("Failed to create directory: {}", it.absolutePath)
      } }

      assets.open(assetPath).use { input ->
        BufferedInputStream(input).use { bufferedIn ->
          FileOutputStream(target).use { fileOut ->
            BufferedOutputStream(fileOut).use { bufferedOut ->
              bufferedIn.copyTo(bufferedOut, DEFAULT_BUFFER_SIZE)
            }
          }
        }
      }

      log.info("Extracted {} -> {}", assetPath, target.absolutePath)
    } catch (e: Exception) {
      log.error("Failed to extract asset: {}", assetPath, e)
    }
  }

  /**
   * Snowfall is active until (and including) January 5, 2026.
   * Uses a single `LocalDate.now()` read to avoid repeated syscalls.
   */
  private fun isSnowfallSeasonActive(): Boolean {
    val today = LocalDate.now()
    val endDate = LocalDate.of(2026, 1, 5)
    return !today.isAfter(endDate)
  }

  fun showChangelog() {
    val version =
        BuildInfo.VERSION_NAME_SIMPLE.let { if (it.startsWith("v")) it else "v$it" }

    val intent =
        Intent(Intent.ACTION_VIEW).apply {
          data =
              Uri.parse(
                  "https://github.com/Neeraj-OS-Developer/android-code-studio/releases/tag/$version"
              )
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    try {
      startActivity(intent)
    } catch (th: Throwable) {
      log.error("Unable to start activity to show changelog", th)
      flashError("Unable to start activity")
    }
  }

  fun reportStatsIfNecessary() {
    if (!StatPreferences.statOptIn) {
      log.info("Stat collection is disabled.")
      return
    }

    val constraints =
        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    val request =
        PeriodicWorkRequestBuilder<StatUploadWorker>(Duration.ofHours(24))
            .setInputData(AndroidIDEStats.statData.toInputData())
            .setConstraints(constraints)
            .addTag(StatUploadWorker.WORKER_WORK_NAME)
            .build()

    val workManager = WorkManager.getInstance(this)

    log.info("reportStatsIfNecessary: Enqueuing StatUploadWorker...")
    val operation =
        workManager.enqueueUniquePeriodicWork(
            StatUploadWorker.WORKER_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )

    operation.state.observeForever(
        object : Observer<Operation.State> {
          override fun onChanged(value: Operation.State) {
            operation.state.removeObserver(this)
            log.debug("reportStatsIfNecessary: WorkManager enqueue result: {}", value)
          }
        }
    )
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun onPrefChanged(event: PreferenceChangeEvent) {
    when (event.key) {
      GeneralPreferences.UI_MODE -> {
        if (GeneralPreferences.uiMode != AppCompatDelegate.getDefaultNightMode()) {
          AppCompatDelegate.setDefaultNightMode(GeneralPreferences.uiMode)
        }
      }
      GeneralPreferences.SELECTED_LOCALE -> {
        // Use empty locale list if the locale has been reset to 'System Default'.
        val selectedLocale = GeneralPreferences.selectedLocale
        val localeListCompat =
            selectedLocale
                ?.let { LocaleListCompat.create(LocaleProvider.getLocale(it)) }
                ?: LocaleListCompat.getEmptyLocaleList()

        AppCompatDelegate.setApplicationLocales(localeListCompat)
      }
      StatPreferences.KEY_STAT_OPT_IN -> {
        val enabled = event.value as? Boolean ?: StatPreferences.statOptIn
        if (enabled) {
          reportStatsIfNecessary()
        } else {
          cancelStatUploadWorker()
        }
      }
      GeneralPreferences.SNOWFALL_OVERLAY -> {
        val enabled = event.value as? Boolean ?: false
        if (enabled && isSnowfallSeasonActive()) {
          runCatching {
                SeasonalEffects.init(this)
                SeasonalEffects.enableChristmas()
                SeasonalEffects.setSnowflakeCount(20)
              }
              .onFailure { log.error("Failed to enable seasonal effects", it) }
        }
      }
    }
  }

  private fun handleCrash(thread: Thread, th: Throwable) {
    try {
      val intent =
          Intent()
              .setAction(CrashHandlerActivity.REPORT_ACTION)
              .putExtra(CrashHandlerActivity.TRACE_KEY, getFullStackTrace(th))
              .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

      startActivity(intent)

      // Chain to the previously installed handler (e.g. Termux / Firebase).
      uncaughtExceptionHandler?.uncaughtException(thread, th)

      exitProcess(1)
    } catch (error: Throwable) {
      Log.e(TAG, "Unable to show crash handler activity", error)
      // Fall back to the original handler rather than swallowing the crash.
      try {
        uncaughtExceptionHandler?.uncaughtException(thread, th)
      } catch (_: Throwable) {
        // Last resort — kill the process.
      }
      exitProcess(1)
    }
  }

  private fun cancelStatUploadWorker() {
    log.info("Opted-out of stat collection. Cancelling StatUploadWorker if enqueued...")
    val operation =
        WorkManager.getInstance(this).cancelUniqueWork(StatUploadWorker.WORKER_WORK_NAME)

    operation.state.observeForever(
        object : Observer<Operation.State> {
          override fun onChanged(value: Operation.State) {
            operation.state.removeObserver(this)
            log.info("StatUploadWorker: Cancellation result state: {}", value)
          }
        }
    )
  }

  private fun startLogcatReader() {
    if (ideLogcatReader != null) return // already started

    log.info("Starting logcat reader...")
    ideLogcatReader = IDELogcatReader().also { it.start() }
  }

  private fun stopLogcatReader() {
    if (ideLogcatReader == null) return

    log.info("Stopping logcat reader...")
    ideLogcatReader?.stop()
    ideLogcatReader = null
  }

  /** Initialize memory management system (opt-in). */
  private fun initializeMemoryManagement() {
    try {
      memoryManager = MemoryManager.getInstance(this)
      chartCleanupTask = ChartMemoryCleanupTask()

      // Register chart cleanup task
      chartCleanupTask?.let { memoryManager?.registerCleanupTask(it) }

      // Start memory monitoring with reduced frequency to avoid Tooling API conflicts
      memoryManager?.startMonitoring()

      Log.i(TAG, "Memory management system initialized with Tooling API compatibility")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to initialize memory management", e)
    }
  }

  /** Get the chart cleanup task for registering charts. */
  fun getChartCleanupTask(): ChartMemoryCleanupTask? = chartCleanupTask

  /** Get the memory manager instance. */
  fun getMemoryManager(): MemoryManager? = memoryManager

  private fun initializeMemoryProfiler() {
    try {
      memoryProfiler = MemoryProfiler.getInstance(this)
      memoryProfiler?.startMonitoring()
      Log.i(TAG, "Memory profiler initialized and started")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to initialize memory profiler", e)
    }
  }

  fun getMemoryProfiler(): MemoryProfiler? = memoryProfiler

  companion object {

    private const val TAG = "IDEApplication"
    private const val DEFAULT_BUFFER_SIZE = 8 * 1024

    private val log = LoggerFactory.getLogger(IDEApplication::class.java)

    @JvmStatic lateinit var instance: IDEApplication
      private set
  }
}
