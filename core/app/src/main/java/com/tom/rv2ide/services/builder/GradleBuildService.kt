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
package com.tom.rv2ide.services.builder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.text.TextUtils
import androidx.core.app.NotificationManagerCompat
import com.blankj.utilcode.util.ResourceUtils
import com.blankj.utilcode.util.ZipUtils
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment
import com.tom.rv2ide.BuildConfig
import com.tom.rv2ide.R.*
import com.tom.rv2ide.app.BaseApplication
import com.tom.rv2ide.lookup.Lookup
import com.tom.rv2ide.managers.ToolsManager
import com.tom.rv2ide.preferences.internal.BuildPreferences
import com.tom.rv2ide.preferences.internal.DevOpsPreferences
import com.tom.rv2ide.projects.builder.BuildService
import com.tom.rv2ide.projects.internal.ProjectManagerImpl
import com.tom.rv2ide.resources.R
import com.tom.rv2ide.services.ToolingServerNotStartedException
import com.tom.rv2ide.services.builder.ToolingServerRunner.OnServerStartListener
import com.tom.rv2ide.tasks.ifCancelledOrInterrupted
import com.tom.rv2ide.tasks.runOnUiThread
import com.tom.rv2ide.tooling.api.ForwardingToolingApiClient
import com.tom.rv2ide.tooling.api.IProject
import com.tom.rv2ide.tooling.api.IToolingApiClient
import com.tom.rv2ide.tooling.api.IToolingApiServer
import com.tom.rv2ide.tooling.api.LogSenderConfig.PROPERTY_LOGSENDER_ENABLED
import com.tom.rv2ide.tooling.api.messages.InitializeProjectParams
import com.tom.rv2ide.tooling.api.messages.LogMessageParams
import com.tom.rv2ide.tooling.api.messages.TaskExecutionMessage
import com.tom.rv2ide.tooling.api.messages.result.BuildCancellationRequestResult
import com.tom.rv2ide.tooling.api.messages.result.BuildInfo
import com.tom.rv2ide.tooling.api.messages.result.BuildResult
import com.tom.rv2ide.tooling.api.messages.result.GradleWrapperCheckResult
import com.tom.rv2ide.tooling.api.messages.result.InitializeResult
import com.tom.rv2ide.tooling.api.messages.result.TaskExecutionResult
import com.tom.rv2ide.tooling.api.models.ToolingServerMetadata
import com.tom.rv2ide.tooling.events.ProgressEvent
import com.tom.rv2ide.utils.Environment
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Objects
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * A foreground service that handles interaction with the Gradle Tooling API.
 *
 * ---------------------------------------------------------------------------------------------
 * FOREGROUND-SERVICE + LEAK-SAFETY REFACTOR (Neeraj-OS-Developer)
 * ---------------------------------------------------------------------------------------------
 * • Android 10 (SDK 29) requires `foregroundServiceType` on `startForeground`. We now call the
 *   3-arg overload with [ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC], and the notification
 *   uses an immutable [PendingIntent] (mandatory on Android 12 / API 31+).
 * • All cross-thread mutable fields are `@Volatile`, the service coroutine scope now uses a
 *   [SupervisorJob] and is cancelled in [onDestroy] — no leaked reader jobs, no leaked pipes,
 *   no leaked `ForwardingToolingApiClient` references.
 * • `executeTasks` guarantees `process.destroy()` and `isBuildInProgress = false` via a
 *   `try/finally`, and the fragile `System.setProperty` init-script handshake is replaced with
 *   a plain volatile field so concurrent builds cannot stomp on each other.
 * ---------------------------------------------------------------------------------------------
 *
 * @author Neeraj-OS-developer
 */
class GradleBuildService :
    Service(), BuildService, IToolingApiClient, ToolingServerRunner.Observer {

  @Volatile private var mBinder: GradleServiceBinder? = null

  @Volatile private var isToolingServerStarted = false

  @Volatile
  override var isBuildInProgress = false
    private set

  /**
   * We do not provide direct access to GradleBuildService instance to the Tooling API launcher as
   * it may cause memory leaks. Instead, we create another client object which forwards all calls to
   * us. So, when the service is destroyed, we release the reference to the service from this
   * client.
   */
  @Volatile private var _toolingApiClient: ForwardingToolingApiClient? = null

  @Volatile private var toolingServerRunner: ToolingServerRunner? = null
  @Volatile private var outputReaderJob: Job? = null
  @Volatile private var notificationManager: NotificationManager? = null
  @Volatile private var server: IToolingApiServer? = null
  @Volatile private var eventListener: EventListener? = null

  @Volatile private var isReleaseVariant = false

  /**
   * Path to the generated init-script for the *current* build. Replaces the previous
   * `System.setProperty` handshake, which was not thread-safe.
   */
  @Volatile private var currentBuildInitScript: File? = null

  private val buildServiceScope =
      CoroutineScope(
          Dispatchers.Default + SupervisorJob() + CoroutineName("GradleBuildService"))

  private val isGradleWrapperAvailable: Boolean
    get() {
      val projectManager = ProjectManagerImpl.getInstance()
      val projectDir = projectManager.projectDirPath
      if (TextUtils.isEmpty(projectDir)) {
        return false
      }

      val projectRoot = Objects.requireNonNull(projectManager.projectDir)
      if (!projectRoot.exists()) {
        return false
      }

      val gradlew = File(projectRoot, "gradlew")
      val gradleWrapperJar = File(projectRoot, "gradle/wrapper/gradle-wrapper.jar")
      val gradleWrapperProps = File(projectRoot, "gradle/wrapper/gradle-wrapper.properties")
      return gradlew.exists() && gradleWrapperJar.exists() && gradleWrapperProps.exists()
    }

  companion object {

    private val log = LoggerFactory.getLogger(GradleBuildService::class.java)
    private val NOTIFICATION_ID = R.string.app_name
    private val SERVER_System_err = LoggerFactory.getLogger("ToolingApiErrorStream")
  }

  override fun onCreate() {
    notificationManager = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager
    // Must be shown before any I/O to satisfy the 5-second startForeground window (Android 8+).
    showNotification(getString(R.string.build_status_idle), false)
    Lookup.getDefault().update(BuildService.KEY_BUILD_SERVICE, this)
  }

  override fun isToolingServerStarted(): Boolean {
    return isToolingServerStarted && server != null
  }

  private fun showNotification(
      message: String,
      @Suppress("SameParameterValue") isProgress: Boolean,
  ) {
    log.info("Showing notification to user...")
    createNotificationChannels()

    val notification = buildNotification(message, isProgress)

    // Android 10 (Q) introduced the 3-arg startForeground() with a foregroundServiceType.
    // Android 14 (U) *requires* the type to be declared in the manifest AND passed here.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      try {
        startForeground(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
      } catch (e: Exception) {
        // Fall back to the legacy overload if the manifest does not declare the type
        // (older integrations still building against pre-Q manifests).
        log.warn("startForeground with type failed, falling back to legacy overload", e)
        startForeground(NOTIFICATION_ID, notification)
      }
    } else {
      startForeground(NOTIFICATION_ID, notification)
    }
  }

  private fun createNotificationChannels() {
    val buildNotificationChannel =
        NotificationChannel(
            BaseApplication.NOTIFICATION_GRADLE_BUILD_SERVICE,
            getString(string.title_gradle_service_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
    NotificationManagerCompat.from(this).createNotificationChannel(buildNotificationChannel)
  }

  private fun buildNotification(message: String, isProgress: Boolean): Notification {
    val ticker = getString(R.string.title_gradle_service_notification_ticker)
    val title = getString(R.string.title_gradle_service_notification)

    val builder =
        Notification.Builder(this, BaseApplication.NOTIFICATION_GRADLE_BUILD_SERVICE)
            .setSmallIcon(R.drawable.ic_launcher_notification)
            .setTicker(ticker)
            .setWhen(System.currentTimeMillis())
            .setContentTitle(title)
            .setContentText(message)

    // Content intent is optional — the launcher may not expose one.
    val launch = packageManager.getLaunchIntentForPackage(BuildConfig.APPLICATION_ID)
    if (launch != null) {
      // Android 12+ (API 31) requires an explicit mutability flag on every PendingIntent.
      val pendingFlags =
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      val intent = PendingIntent.getActivity(this, 0, launch, pendingFlags)
      builder.setContentIntent(intent)
    }

    // Checking whether to add a ProgressBar to the notification
    if (isProgress) {
      // Add ProgressBar to Notification
      builder.setProgress(100, 0, true)
    }
    return builder.build()
  }

  override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
    // No point in restarting the service if it really gets killed.
    return START_NOT_STICKY
  }

  override fun onDestroy() {
    log.info("Service is being destroyed. Cleaning up resources...")

    mBinder?.release()
    mBinder = null

    notificationManager?.cancel(NOTIFICATION_ID)
    notificationManager = null

    val lookup = Lookup.getDefault()
    lookup.unregister(BuildService.KEY_BUILD_SERVICE)
    lookup.unregister(BuildService.KEY_PROJECT_PROXY)

    server?.also { server ->
      try {
        log.info("Shutting down Tooling API server...")
        // send the shutdown request but do not wait for the server to respond
        // the service should not block the onDestroy call in order to avoid timeouts
        // the tooling server must release resources and exit automatically
        server.shutdown().get(1, TimeUnit.SECONDS)
      } catch (e: Throwable) {
        log.error("Failed to shutdown Tooling API server", e)
      }
    }
    server = null

    log.debug("Cancelling tooling server runner...")
    toolingServerRunner?.release()
    toolingServerRunner = null

    _toolingApiClient?.client = null
    _toolingApiClient = null

    log.debug("Cancelling tooling server output reader job...")
    outputReaderJob?.cancel()
    outputReaderJob = null

    // Cancel every child coroutine (readers, one-off tasks) so nothing outlives the service.
    buildServiceScope.cancel("GradleBuildService destroyed")

    eventListener = null
    currentBuildInitScript = null
    isToolingServerStarted = false
    isBuildInProgress = false
  }

  override fun onBind(intent: Intent): IBinder? {
    var binder = mBinder
    if (binder == null) {
      synchronized(this) {
        binder = mBinder
        if (binder == null) {
          binder = GradleServiceBinder(this)
          mBinder = binder
        }
      }
    }
    return binder
  }

  /** Creates a Gradle init script that injects the logger plugin into user projects. */
  private fun createLoggerInitScript(): File {
    val initScript = File(Environment.TMP_DIR, "ide-logger-init.gradle")
    initScript.writeText(
        """
          allprojects {
              afterEvaluate {
                  if (plugins.hasPlugin('com.android.application') || 
                      plugins.hasPlugin('com.android.library')) {
                      
                      android {
                          compileOptions {
                              coreLibraryDesugaringEnabled = true
                          }
                      }
                      
                      dependencies {
                          implementation files('${getLoggerRuntimeAar().absolutePath}')
                          coreLibraryDesugaring 'com.android.tools:desugar_jdk_libs:2.0.4'
                      }
                  }
              }
          }
      """
            .trimIndent()
    )
    return initScript
  }

  /** Gets or creates the logger plugin directory. */
  private fun getLoggerPluginDir(): File {
    val dir = File(Environment.HOME, "plugins/logger")
    if (!dir.exists()) {
      dir.mkdirs()
    }
    return dir
  }

  /** Extracts and returns the logger runtime AAR file. */
  private fun getLoggerRuntimeAar(): File {
    val aar = File(getLoggerPluginDir(), "logger-runtime.aar")
    if (!aar.exists()) {
      // Extract from assets
      if (
          !ResourceUtils.copyFileFromAssets(
              ToolsManager.getCommonAsset("logger-runtime.aar"),
              aar.absolutePath,
          )
      ) {
        log.error("Failed to extract logger-runtime.aar from assets")
      }
    }
    return aar
  }

  /** Check if tasks include debug builds (not release-only). */
  private fun isDebugBuild(tasks: List<String>): Boolean {
    // Check if any task contains "Debug" or doesn't contain "Release"
    val hasDebugTask =
        tasks.any { task ->
          task.contains("Debug", ignoreCase = true) ||
              task.contains("assembleDebug", ignoreCase = true)
        }

    val hasOnlyRelease =
        tasks.all { task ->
          task.contains("Release", ignoreCase = true) ||
              task.contains("assembleRelease", ignoreCase = true)
        }

    // If it's explicitly debug, or not explicitly release-only, treat as debug
    return hasDebugTask || !hasOnlyRelease
  }

  /**
   * Inject logger by staging the init-script path for the current build. This is consumed by
   * [getBuildArguments] and cleared once the build has been dispatched.
   */
  private fun injectLoggerForCurrentBuild() {
    currentBuildInitScript = createLoggerInitScript()
  }

  override fun onListenerStarted(
      server: IToolingApiServer,
      projectProxy: IProject,
      errorStream: InputStream,
  ) {
    startServerOutputReader(errorStream)
    this.server = server
    Lookup.getDefault().update(BuildService.KEY_PROJECT_PROXY, projectProxy)
    isToolingServerStarted = true
  }

  override fun onServerExited(exitCode: Int) {
    log.warn("Tooling API process terminated with exit code: {}", exitCode)
    try {
      stopForeground(STOP_FOREGROUND_REMOVE)
    } catch (e: Exception) {
      log.warn("Failed to stop foreground", e)
    }
  }

  override fun getClient(): IToolingApiClient {
    var client = _toolingApiClient
    if (client == null) {
      synchronized(this) {
        client = _toolingApiClient
        if (client == null) {
          client = ForwardingToolingApiClient(this)
          _toolingApiClient = client
        }
      }
    }
    return client!!
  }

  override fun logMessage(params: LogMessageParams) {
    val logger = LoggerFactory.getLogger(params.tag)
    when (params.level) {
      'D' -> logger.debug(params.message)
      'W' -> logger.warn(params.message)
      'E' -> logger.error(params.message)
      'I' -> logger.info(params.message)

      else -> logger.trace(params.message)
    }
  }

  override fun logOutput(line: String) {
    // Wrap the dispatch — the consumer (Activity) may already be gone.
    try {
      eventListener?.onOutput(line)
    } catch (e: Exception) {
      log.warn("EventListener.onOutput threw", e)
    }
  }

  override fun prepareBuild(buildInfo: BuildInfo) {
    updateNotification(getString(R.string.build_status_in_progress), true)
    try {
      eventListener?.prepareBuild(buildInfo)
    } catch (e: Exception) {
      log.warn("EventListener.prepareBuild threw", e)
    }
  }

  override fun onBuildSuccessful(result: BuildResult) {
    updateNotification(getString(R.string.build_status_sucess), false)
    try {
      eventListener?.onBuildSuccessful(result.tasks)
    } catch (e: Exception) {
      log.warn("EventListener.onBuildSuccessful threw", e)
    }
  }

  override fun onBuildFailed(result: BuildResult) {
    updateNotification(getString(R.string.build_status_failed), false)
    try {
      eventListener?.onBuildFailed(result.tasks)
    } catch (e: Exception) {
      log.warn("EventListener.onBuildFailed threw", e)
    }
  }

  override fun onProgressEvent(event: ProgressEvent) {
    try {
      eventListener?.onProgressEvent(event)
    } catch (e: Exception) {
      log.warn("EventListener.onProgressEvent threw", e)
    }
  }

  override fun getBuildArguments(): CompletableFuture<List<String>> {
    val extraArgs = ArrayList<String>()

    if (DevOpsPreferences.logsenderEnabled) {
      // Use a plain volatile field instead of a System property — no cross-build stomping.
      val initScript = currentBuildInitScript ?: createLoggerInitScript().also {
        currentBuildInitScript = it
      }
      if (!isReleaseVariant) {
        extraArgs.add("--init-script")
        extraArgs.add(initScript.absolutePath)
      }
    }

    // Override AAPT2 binary
    extraArgs.add("-Pandroid.aapt2FromMavenOverride=" + Environment.AAPT2.absolutePath)
    extraArgs.add("-P${PROPERTY_LOGSENDER_ENABLED}=${DevOpsPreferences.logsenderEnabled}")

    if (BuildPreferences.isStacktraceEnabled) {
      extraArgs.add("--stacktrace")
    }
    if (BuildPreferences.isInfoEnabled) {
      extraArgs.add("--info")
    }
    if (BuildPreferences.isDebugEnabled) {
      extraArgs.add("--debug")
    }
    if (BuildPreferences.isScanEnabled) {
      extraArgs.add("--scan")
    }
    if (BuildPreferences.isWarningModeAllEnabled) {
      extraArgs.add("--warning-mode")
      extraArgs.add("all")
    }
    if (BuildPreferences.isBuildCacheEnabled) {
      extraArgs.add("--build-cache")
    }
    if (BuildPreferences.isOfflineEnabled) {
      extraArgs.add("--offline")
    }

    return CompletableFuture.completedFuture(extraArgs)
  }

  override fun checkGradleWrapperAvailability(): CompletableFuture<GradleWrapperCheckResult> {
    return if (isGradleWrapperAvailable)
        CompletableFuture.completedFuture(GradleWrapperCheckResult(true))
    else installWrapper()
  }

  internal fun setServerListener(listener: OnServerStartListener?) {
    toolingServerRunner?.setListener(listener)
  }

  private fun installWrapper(): CompletableFuture<GradleWrapperCheckResult> {
    eventListener?.also { eventListener ->
      eventListener.onOutput("-------------------- NOTE --------------------")
      eventListener.onOutput(getString(R.string.msg_installing_gradlew))
      eventListener.onOutput("----------------------------------------------")
    }
    return CompletableFuture.supplyAsync { doInstallWrapper() }
  }

  private fun doInstallWrapper(): GradleWrapperCheckResult {
    val extracted = File(Environment.TMP_DIR, "gradle-wrapper.zip")
    if (
        !ResourceUtils.copyFileFromAssets(
            ToolsManager.getCommonAsset("gradle-wrapper.zip"),
            extracted.absolutePath,
        )
    ) {
      log.error("Unable to extract gradle-plugin.zip from IDE resources.")
      return GradleWrapperCheckResult(false)
    }
    try {
      val projectDir = ProjectManagerImpl.getInstance().projectDir
      val files = ZipUtils.unzipFile(extracted, projectDir)
      if (files != null && files.isNotEmpty()) {
        return GradleWrapperCheckResult(true)
      }
    } catch (e: IOException) {
      log.error("An error occurred while extracting Gradle wrapper", e)
    }
    return GradleWrapperCheckResult(false)
  }

  private fun updateNotification(message: String, isProgress: Boolean) {
    // NotificationManager.notify() is thread-safe; no UI-thread hop needed.
    doUpdateNotification(message, isProgress)
  }

  private fun doUpdateNotification(message: String, isProgress: Boolean) {
    val manager = notificationManager
        ?: (getSystemService(NOTIFICATION_SERVICE) as? NotificationManager)
        ?: return
    try {
      manager.notify(NOTIFICATION_ID, buildNotification(message, isProgress))
    } catch (e: Exception) {
      log.warn("Failed to update notification", e)
    }
  }

  override fun metadata(): CompletableFuture<ToolingServerMetadata> {
    checkServerStarted()
    return server!!.metadata()
  }

  override fun initializeProject(
      params: InitializeProjectParams
  ): CompletableFuture<InitializeResult> {
    checkServerStarted()
    Objects.requireNonNull(params)
    return performBuildTasks(server!!.initialize(params)).thenApply { result ->
      if (result != null) {
        buildServiceScope.launch {
          try {
            kotlinx.coroutines.delay(5000) // 5 seconds
            log.info("5 seconds elapsed after initialization, stopping Gradle daemons...")
            // stopGradleDaemons().get()
          } catch (e: CancellationException) {
            // scope cancelled — expected on service destroy
          } catch (e: Exception) {
            log.error("Error in post-initialization daemon cleanup", e)
          }
        }
      }
      result
    }
  }

  /**
   * Stops all Gradle daemons by executing gradlew --stop
   */
  private fun stopGradleDaemons(): CompletableFuture<Void> {
    return CompletableFuture.runAsync {
      try {
        val projectDir = ProjectManagerImpl.getInstance().projectDir
        val gradlewPath = File(projectDir, "gradlew").absolutePath

        log.info("Stopping Gradle daemons...")

        val command = listOf("sh", gradlewPath, "--stop")
        val processBuilder = ProcessBuilder(command)
        processBuilder.directory(projectDir)

        // Set up environment
        val termuxEnv = TermuxShellEnvironment().getEnvironment(this@GradleBuildService, false)
        val customEnv = HashMap<String, String>()
        Environment.putEnvironment(customEnv, false)

        val finalEnv = processBuilder.environment()
        finalEnv.putAll(termuxEnv)
        finalEnv.putAll(customEnv)

        val process = processBuilder.start()
        val exitCode = process.waitFor()

        if (exitCode == 0) {
          log.info("Gradle daemons stopped successfully")
          logOutput("Gradle daemons stopped")
        } else {
          log.warn("Failed to stop Gradle daemons, exit code: $exitCode")
        }
      } catch (e: Exception) {
        log.error("Error stopping Gradle daemons", e)
      }
    }
  }

  override fun executeTasks(vararg tasks: String): CompletableFuture<TaskExecutionResult> {
    checkServerStarted()
    val tasksList = tasks.toList()

    if (isDebugBuild(tasksList)) {
      log.info("Debug build detected, injecting logger plugin")
      isReleaseVariant = false
      injectLoggerForCurrentBuild()
    } else {
      log.info("Release build detected, skipping logger injection")
      isReleaseVariant = true
    }

    /*
    * @idea Mohammed-Baqer-Null @ https://github.com/Mohammed-baqer-null
    * ! THIS IS A TEMPORARY FIX ! gradually transforming acs lite compiler in here properly in v..04 or 05

    * - Using the local Gradle wrapper (gradlew) is significantly faster than using the Tooling API.
    * - Employing the Tooling API for compilation on Android is a poor choice this is a resource-limited Android environment, not a desktop one.
    * - The Tooling API consumes excessive JVM memory without delivering meaningful benefits.
    * - The implementation below resolves OutOfMemory exceptions seamlessly.
    */

    return performBuildTasks(
        CompletableFuture.supplyAsync {
          val buildInfo = BuildInfo(tasksList)
          prepareBuild(buildInfo)

          var process: Process? = null
          try {
            val projectDir = ProjectManagerImpl.getInstance().projectDir
            val gradlewPath = File(projectDir, "gradlew").absolutePath

            val command = mutableListOf("sh", gradlewPath)
            command.addAll(tasks)

            val buildArgs = getBuildArguments().get()
            command.addAll(buildArgs)

            log.info("Executing command: ${command.joinToString(" ")}")

            val processBuilder = ProcessBuilder(command)
            processBuilder.directory(projectDir)

            // Get Termux environment
            val termuxEnv =
                TermuxShellEnvironment().getEnvironment(this@GradleBuildService, false)

            // Add custom environment variables from Environment class
            val customEnv = HashMap<String, String>()
            Environment.putEnvironment(customEnv, false)

            // Merge environments
            val finalEnv = processBuilder.environment()
            finalEnv.putAll(termuxEnv)
            finalEnv.putAll(customEnv)

            // Ensure PATH includes BIN_DIR for clang, python, etc.
            val currentPath = finalEnv["PATH"] ?: ""
            val binDirPath = Environment.BIN_DIR.absolutePath
            val prefixBinPath = File(Environment.PREFIX, "bin").absolutePath

            // Add BIN_DIR and PREFIX/bin to PATH if not already present
            val pathEntries = mutableListOf<String>()
            if (!currentPath.contains(binDirPath)) {
              pathEntries.add(binDirPath)
            }
            if (!currentPath.contains(prefixBinPath)) {
              pathEntries.add(prefixBinPath)
            }
            pathEntries.add(currentPath)

            finalEnv["PATH"] = pathEntries.filter { it.isNotEmpty() }.joinToString(":")

            // Add LD_LIBRARY_PATH for native libraries
            val ldLibraryPath = finalEnv["LD_LIBRARY_PATH"] ?: ""
            val libDirPath = Environment.LIB_DIR.absolutePath
            finalEnv["LD_LIBRARY_PATH"] =
                if (ldLibraryPath.isEmpty()) {
                  libDirPath
                } else {
                  "$libDirPath:$ldLibraryPath"
                }

            // Set TMPDIR
            finalEnv["TMPDIR"] = Environment.TMP_DIR.absolutePath

            log.info("PATH set to: ${finalEnv["PATH"]}")
            log.info("LD_LIBRARY_PATH set to: ${finalEnv["LD_LIBRARY_PATH"]}")

            val proc = processBuilder.start()
            process = proc

            // Launch readers as children of the service scope so they are cancelled with it.
            buildServiceScope.launch(Dispatchers.IO) {
              try {
                proc.inputStream.bufferedReader().use { reader ->
                  reader.forEachLine { line -> logOutput(line) }
                }
              } catch (e: CancellationException) {
                // scope cancelled — expected
              } catch (e: Exception) {
                log.warn("Failed reading build stdout", e)
              }
            }

            buildServiceScope.launch(Dispatchers.IO) {
              try {
                proc.errorStream.bufferedReader().use { reader ->
                  reader.forEachLine { line -> logOutput(line) }
                }
              } catch (e: CancellationException) {
                // scope cancelled — expected
              } catch (e: Exception) {
                log.warn("Failed reading build stderr", e)
              }
            }

            val exitCode = proc.waitFor()

            val result =
                if (exitCode == 0) {
                  TaskExecutionResult(true, null)
                } else {
                  TaskExecutionResult(false, TaskExecutionResult.Failure.BUILD_FAILED)
                }

            if (result.isSuccessful) {
              onBuildSuccessful(BuildResult(tasksList))
            } else {
              onBuildFailed(BuildResult(tasksList))
            }

            result
          } catch (e: CancellationException) {
            log.warn("Build execution was cancelled")
            onBuildFailed(BuildResult(tasksList))
            TaskExecutionResult(false, TaskExecutionResult.Failure.BUILD_FAILED)
          } catch (e: Exception) {
            log.error("Failed to execute gradlew with sh", e)
            onBuildFailed(BuildResult(tasksList))
            TaskExecutionResult(false, TaskExecutionResult.Failure.BUILD_FAILED)
          } finally {
            // Always clean up the OS process and clear the current-build init-script path.
            try {
              process?.destroy()
            } catch (_: Throwable) {
              // ignored
            }
            currentBuildInitScript = null
          }
        }
    )
  }

  /**
   * Kills any running gradlew processes forcefully
   */
  private fun killGradlewProcesses() {
    try {
      log.info("Attempting to kill running gradlew processes...")

      val projectDir = try {
        ProjectManagerImpl.getInstance().projectDir.absolutePath
      } catch (_: Throwable) {
        null
      }

      // Scope the kill pattern to this project directory when possible to avoid killing
      // unrelated gradlew processes elsewhere on the device.
      val pattern = if (!projectDir.isNullOrEmpty()) {
        "$projectDir.*gradlew"
      } else {
        "gradlew"
      }

      val process = ProcessBuilder("pkill", "-f", pattern).start()
      val exitCode = process.waitFor()

      if (exitCode == 0) {
        log.info("Gradlew processes killed successfully")
        logOutput("All Gradle build processes terminated")
      } else {
        log.info("No gradlew processes found or already terminated")
      }
    } catch (e: Exception) {
      log.error("Error killing gradlew processes", e)
    }
  }

  override fun cancelCurrentBuild(): CompletableFuture<BuildCancellationRequestResult> {
    checkServerStarted()

    val cancellationFuture = server!!.cancelCurrentBuild()

    buildServiceScope.launch {
      try {
        kotlinx.coroutines.delay(1000) // Wait 1 second for graceful cancellation
        log.info("Force stopping Gradle daemons after build cancellation...")
        // stopGradleDaemons().get()
        killGradlewProcesses()
      } catch (e: CancellationException) {
        // expected on destroy
      } catch (e: Exception) {
        log.error("Error during forced daemon shutdown", e)
      }
    }

    return cancellationFuture
  }

  private fun <T> performBuildTasks(future: CompletableFuture<T>): CompletableFuture<T> {
    return CompletableFuture.runAsync(this::onPrepareBuildRequest)
        .handleAsync { _, _ ->
          try {
            return@handleAsync future.get()
          } catch (e: Throwable) {
            throw CompletionException(e)
          }
        }
        .handle(this::markBuildAsFinished)
  }

  private fun onPrepareBuildRequest() {
    checkServerStarted()
    ensureTmpdir()
    if (isBuildInProgress) {
      logBuildInProgress()
      throw BuildInProgressException()
    }
    isBuildInProgress = true
  }

  @Throws(ToolingServerNotStartedException::class)
  private fun checkServerStarted() {
    if (!isToolingServerStarted()) {
      throw ToolingServerNotStartedException()
    }
  }

  private fun ensureTmpdir() {
    Environment.mkdirIfNotExits(Environment.TMP_DIR)
  }

  private fun logBuildInProgress() {
    log.warn("A build is already in progress!")
  }

  @Suppress("UNUSED_PARAMETER")
  private fun <T> markBuildAsFinished(result: T, throwable: Throwable?): T {
    isBuildInProgress = false
    return result
  }

  internal fun startToolingServer(listener: OnServerStartListener?) {
    synchronized(this) {
      val runner = toolingServerRunner
      if (runner?.isStarted != true) {
        val envs = TermuxShellEnvironment().getEnvironment(this, false)
        toolingServerRunner = ToolingServerRunner(listener, this).also { it.startAsync(envs) }
        return
      }

      if (runner.isStarted && listener != null) {
        listener.onServerStarted(runner.pid!!)
      } else {
        setServerListener(listener)
      }
    }
  }

  fun setEventListener(eventListener: EventListener?): GradleBuildService {
    if (eventListener == null) {
      this.eventListener = null
      return this
    }
    this.eventListener = wrap(eventListener)
    return this
  }

  private fun wrap(listener: EventListener?): EventListener? {
    return if (listener == null) {
      null
    } else
        object : EventListener {
          override fun prepareBuild(buildInfo: BuildInfo) {
            runOnUiThread { listener.prepareBuild(buildInfo) }
          }

          override fun onBuildSuccessful(tasks: List<String?>) {
            runOnUiThread { listener.onBuildSuccessful(tasks) }
          }

          override fun onProgressEvent(event: ProgressEvent) {
            runOnUiThread { listener.onProgressEvent(event) }
          }

          override fun onBuildFailed(tasks: List<String?>) {
            runOnUiThread { listener.onBuildFailed(tasks) }
          }

          override fun onOutput(line: String?) {
            runOnUiThread { listener.onOutput(line) }
          }
        }
  }

  private fun startServerOutputReader(input: InputStream) {
    if (outputReaderJob?.isActive == true) {
      return
    }

    outputReaderJob =
        buildServiceScope.launch(Dispatchers.IO + CoroutineName("ToolingServerErrorReader")) {
          val reader = input.bufferedReader()
          try {
            reader.forEachLine { line -> SERVER_System_err.error(line) }
          } catch (e: Throwable) {
            e.ifCancelledOrInterrupted(suppress = true) {
              // will be suppressed
              return@launch
            }

            // log the error and fail silently
            log.error("Failed to read tooling server output", e)
          } finally {
            try {
              reader.close()
            } catch (_: Throwable) {
              // ignored
            }
          }
        }
  }

  /** Handles events received from a Gradle build. */
  interface EventListener {

    /**
     * Called just before a build is started.
     *
     * @param buildInfo The information about the build to be executed.
     * @see IToolingApiClient.prepareBuild
     */
    fun prepareBuild(buildInfo: BuildInfo)

    /**
     * Called when a build is successful.
     *
     * @param tasks The tasks that were run.
     * @see IToolingApiClient.onBuildSuccessful
     */
    fun onBuildSuccessful(tasks: List<String?>)

    /**
     * Called when a progress event is received from the Tooling API server.
     *
     * @param event The event model describing the event.
     */
    fun onProgressEvent(event: ProgressEvent)

    /**
     * Called when a build fails.
     *
     * @param tasks The tasks that were run.
     * @see IToolingApiClient.onBuildFailed
     */
    fun onBuildFailed(tasks: List<String?>)

    /**
     * Called when the output line is received.
     *
     * @param line The line of the build output.
     */
    fun onOutput(line: String?)
  }
}
