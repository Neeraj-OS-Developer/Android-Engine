package com.tom.rv2ide.editor.schemes

import android.content.Context
import androidx.annotation.WorkerThread
import com.tom.rv2ide.eventbus.events.editor.ColorSchemeInvalidatedEvent
import com.tom.rv2ide.preferences.internal.EditorPreferences
import com.tom.rv2ide.syntax.colorschemes.SchemeAndroidIDE
import com.tom.rv2ide.utils.Environment
import com.tom.rv2ide.utils.isSystemInDarkMode
import java.io.File
import java.io.FileFilter
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus
import org.slf4j.LoggerFactory

object IDEColorSchemeProvider {

  private val log = LoggerFactory.getLogger(IDEColorSchemeProvider::class.java)
  private val schemesDir = File(Environment.ANDROIDIDE_UI, "editor/schemes")

  private val schemes = ConcurrentHashMap<String, IDEColorScheme>()

  private const val SCHEME_NAME = "scheme.name"
  private const val SCHEME_VERSION = "scheme.version"
  private const val SCHEME_IS_DARK = "scheme.isDark"
  private const val SCHEME_FILE = "scheme.file"

  private var isDefaultSchemeLoaded = false
  private var isCurrentSchemeLoaded = false

  private var defaultScheme: IDEColorScheme? = null
    get() {
      return field
          ?: getColorScheme(EditorPreferences.DEFAULT_COLOR_SCHEME).also { scheme ->
            field = scheme
            isDefaultSchemeLoaded = scheme != null
          }
    }

  private var currentScheme: IDEColorScheme? = null
    get() {
      return field
          ?: getColorScheme(EditorPreferences.colorScheme).also { scheme ->
            field = scheme
            isCurrentSchemeLoaded = scheme != null
          }
    }

  @WorkerThread
  private fun getColorScheme(name: String): IDEColorScheme? {
    val scheme = schemes[name] ?: run {
      log.error("Color scheme '{}' not found in loaded schemes", name)
      return null
    }
    return loadColorScheme(scheme)
  }

  private fun loadColorScheme(scheme: IDEColorScheme): IDEColorScheme? {
    return try {
      scheme.load()
      scheme.darkVariant?.load()
      scheme
    } catch (err: Exception) {
      log.error(
          "An error occurred while loading color scheme '{}' from file '{}'",
          scheme.name,
          scheme.file.absolutePath,
          err,
      )
      null
    }
  }

  @JvmStatic
  @WorkerThread
  fun init() {
    val schemeDirs =
        schemesDir.listFiles(FileFilter { it.isDirectory && File(it, "scheme.prop").exists() })
            ?: run {
              log.error("No color schemes found in directory: {}", schemesDir.absolutePath)
              return
            }

    log.debug("Found {} scheme directories in {}", schemeDirs.size, schemesDir.absolutePath)

    for (schemeDir in schemeDirs) {
      val prop = File(schemeDir, "scheme.prop")
      val props =
          try {
            prop.reader().use { reader -> Properties().apply { load(reader) } }
          } catch (err: Exception) {
            log.error("Failed to read properties for scheme '{}' at '{}'", schemeDir.name, prop.absolutePath, err)
            continue
          }

      val name = props.getProperty(SCHEME_NAME, "Unknown")
      val version = props.getProperty(SCHEME_VERSION, "0").toInt()
      val isDark = props.getProperty(SCHEME_IS_DARK, "false").toBoolean()
      val file =
          props.getProperty(SCHEME_FILE)
              ?: run {
                log.error(
                    "Scheme '{}' does not specify 'scheme.file' in scheme.prop file at '{}'",
                    schemeDir.name,
                    prop.absolutePath
                )
                ""
              }

      if (version <= 0) {
        log.warn("Version code of color scheme '{}' must be set to >= 1", schemeDir)
      }

      if (file.isBlank()) {
        continue
      }

      val schemeFile = File(schemeDir, file)
      if (!schemeFile.exists()) {
        log.error("Scheme file '{}' does not exist for scheme '{}'", schemeFile.absolutePath, schemeDir.name)
        continue
      }

      log.debug("Registered color scheme: name='{}', key='{}', file='{}'", name, schemeDir.name, schemeFile.absolutePath)

      val scheme = IDEColorScheme(schemeFile, schemeDir.name)
      scheme.name = name
      scheme.version = version
      scheme.isDarkScheme = isDark
      schemes[schemeDir.name] = scheme
    }

    schemes.values.forEach { it.darkVariant = schemes["${it.key}-dark"] }
    
    log.info("Initialized {} color schemes", schemes.size)
  }

  @JvmStatic
  fun initIfNeeded() {
    if (this.schemes.isEmpty()) {
      init()
    }
  }

  @JvmOverloads
  fun readSchemeAsync(
      context: Context,
      coroutineScope: CoroutineScope,
      type: String? = null,
      callbackContext: CoroutineContext = Dispatchers.Main.immediate,
      callback: (SchemeAndroidIDE?) -> Unit,
  ) {

    val loadedScheme =
        if (
            isCurrentSchemeLoaded &&
                (type == null || currentScheme?.getLanguageScheme(type) != null)
        ) {
          currentScheme
        } else if (isDefaultSchemeLoaded) {
          defaultScheme
        } else {
          null
        }

    if (loadedScheme != null) {
      coroutineScope.launch(callbackContext) { callback(readScheme(context, type)) }
      return
    }

    coroutineScope.launch(Dispatchers.IO) {
      val scheme = readScheme(context, type)
      withContext(callbackContext) { callback(scheme) }
    }
  }

  @JvmOverloads
  @WorkerThread
  fun readScheme(context: Context, type: String? = null): SchemeAndroidIDE? {
    val scheme = getColorSchemeForType(type)
    if (scheme == null) {
      log.error("Failed to read color scheme for type '{}'", type)
      return null
    }

    val dark = scheme.darkVariant
    if (context.isSystemInDarkMode() && dark != null) {
      return dark
    }

    return scheme
  }

  @WorkerThread
  fun getColorSchemeForType(type: String?): IDEColorScheme? {
    if (type == null) {
      return currentScheme
    }

    val current = currentScheme
    if (current != null && current.getLanguageScheme(type) != null) {
      return current
    }

    if (current != null) {
      log.warn("Color scheme '{}' does not support '{}'. Available languages: {}", 
        current.name, type, current.languages.keys.joinToString(", "))
      log.warn("Falling back to default color scheme")
    }

    val default = defaultScheme
    if (default != null && default.getLanguageScheme(type) != null) {
      return default
    }

    if (default != null) {
      log.error("Default color scheme '{}' does not support '{}'. Available languages: {}", 
        default.name, type, default.languages.keys.joinToString(", "))
    }

    return default
  }

  fun list(): List<IDEColorScheme> {
    return this.schemes.values.filter { !it.key.endsWith("-dark") }.toList()
  }

  fun destroy() {
    this.schemes.clear()
    this.currentScheme = null
    this.isCurrentSchemeLoaded = false

    this.defaultScheme = null
    this.isDefaultSchemeLoaded = false
  }

  @WorkerThread
  fun reload() {
    destroy()
    init()
    EventBus.getDefault().post(ColorSchemeInvalidatedEvent())
  }
}