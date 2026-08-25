package com.tom.rv2ide.fragments

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Bundle
import android.os.IBinder
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.navigationrail.NavigationRailView
import com.google.android.material.tabs.TabLayout
import com.tom.rv2ide.R
import com.tom.rv2ide.fragments.terminal.*
import com.termux.app.TermuxService
import com.termux.shared.logger.Logger
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient

class TerminalFragment : Fragment() {
    
    private var terminalView: TerminalView? = null
    private var termuxService: TermuxService? = null
    private var navigationRail: NavigationRailView? = null
    private var mainContent: LinearLayout? = null
    private var menuButton: ImageView? = null
    private var terminalContent: View? = null
    private var settingsContent: View? = null
    private var emptyStateContent: View? = null
    private var sessionTabs: TabLayout? = null
    
    private var serviceIsBound = false
    private lateinit var prefs: SharedPreferences
    
    // Handlers
    private lateinit var sessionManager: SessionManager
    private lateinit var extraKeysHandler: ExtraKeysHandler
    private lateinit var navigationHandler: NavigationHandler
    private lateinit var settingsHandler: TerminalSettingsHandler
    
    private companion object {
        const val PREF_NAME = "TerminalPreferences"
    }
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName?, service: IBinder?) {
            termuxService = (service as TermuxService.LocalBinder).service
            serviceIsBound = true
        }
        
        override fun onServiceDisconnected(componentName: ComponentName?) {
            termuxService = null
            serviceIsBound = false
        }
    }
    
    private val terminalViewClient = object : TerminalViewClient {
        override fun onScale(scale: Float) = scale.coerceIn(0.5f, 2.0f)
        override fun onSingleTapUp(e: MotionEvent?) {
            if (sessionManager.currentSession != null) {
                (requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showSoftInput(terminalView, InputMethodManager.SHOW_IMPLICIT)
            }
        }
        override fun shouldBackButtonBeMappedToEscape() = false
        override fun shouldEnforceCharBasedInput() = true
        override fun shouldUseCtrlSpaceWorkaround() = false
        override fun isTerminalViewSelected() = true
        override fun copyModeChanged(copyMode: Boolean) {}
        override fun onKeyDown(keyCode: Int, e: KeyEvent?, session: TerminalSession?) = false
        override fun onKeyUp(keyCode: Int, e: KeyEvent?) = false
        override fun onLongPress(event: MotionEvent?) = true
        override fun readControlKey() = extraKeysHandler.isCtrlPressed()
        override fun readAltKey() = extraKeysHandler.isAltPressed()
        override fun readShiftKey() = false
        override fun readFnKey() = false
        override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession?) = false
        override fun onEmulatorSet() {
            terminalView?.setTerminalCursorBlinkerRate(1000)
            terminalView?.setTerminalCursorBlinkerState(true, true)
        }
        override fun logError(tag: String?, message: String?) = Logger.logError(tag, message)
        override fun logWarn(tag: String?, message: String?) = Logger.logWarn(tag, message)
        override fun logInfo(tag: String?, message: String?) = Logger.logInfo(tag, message)
        override fun logDebug(tag: String?, message: String?) = Logger.logDebug(tag, message)
        override fun logVerbose(tag: String?, message: String?) = Logger.logVerbose(tag, message)
        override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) = 
            Logger.logStackTraceWithMessage(tag, message, e)
        override fun logStackTrace(tag: String?, e: Exception?) = Logger.logStackTrace(tag, e)
    }
    
    private val terminalSessionClient = object : TerminalSessionClient {
        override fun onTextChanged(changedSession: TerminalSession) {
            terminalView?.onScreenUpdated()
        }
        
        override fun onTitleChanged(changedSession: TerminalSession) {
            sessionManager.updateTabTitle(changedSession)
        }
        
        override fun onSessionFinished(finishedSession: TerminalSession) {
            sessionManager.onSessionFinished(finishedSession, termuxService)
        }
        
        override fun onCopyTextToClipboard(session: TerminalSession, text: String) {}
        override fun onPasteTextFromClipboard(session: TerminalSession?) {}
        override fun onBell(session: TerminalSession) {}
        override fun onColorsChanged(session: TerminalSession) {}
        override fun onTerminalCursorStateChange(state: Boolean) {}
        override fun setTerminalShellPid(session: TerminalSession, pid: Int) {}
        override fun getTerminalCursorStyle() = TerminalEmulator.DEFAULT_TERMINAL_CURSOR_STYLE
        override fun logError(tag: String?, message: String?) = Logger.logError(tag, message)
        override fun logWarn(tag: String?, message: String?) = Logger.logWarn(tag, message)
        override fun logInfo(tag: String?, message: String?) = Logger.logInfo(tag, message)
        override fun logDebug(tag: String?, message: String?) = Logger.logDebug(tag, message)
        override fun logVerbose(tag: String?, message: String?) = Logger.logVerbose(tag, message)
        override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) = 
            Logger.logStackTraceWithMessage(tag, message, e)
        override fun logStackTrace(tag: String?, e: Exception?) = Logger.logStackTrace(tag, e)
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_terminal, container, false)
        
        prefs = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        
        // Initialize views
        navigationRail = rootView.findViewById(R.id.navigation_rail)
        mainContent = rootView.findViewById(R.id.main_content)
        menuButton = rootView.findViewById(R.id.menu_button)
        terminalContent = rootView.findViewById(R.id.terminal_content)
        settingsContent = rootView.findViewById(R.id.settings_content)
        emptyStateContent = rootView.findViewById(R.id.empty_state_content)
        terminalView = rootView.findViewById(R.id.terminal_view)
        sessionTabs = rootView.findViewById(R.id.session_tabs)
        
        // Initialize handlers
        sessionManager = SessionManager(
            fragment = this,
            terminalView = terminalView,
            sessionTabs = sessionTabs,
            terminalSessionClient = terminalSessionClient,
            onSessionChange = { hasSession ->
                if (hasSession) showTerminal() else showEmptyState()
            }
        )
        
        extraKeysHandler = ExtraKeysHandler(rootView, terminalView)
        
        navigationHandler = NavigationHandler(
            context = requireContext(),
            navigationRail = navigationRail,
            mainContent = mainContent,
            menuButton = menuButton,
            onMenuItemSelected = { itemId -> handleNavigationItemSelected(itemId) }
        )
        
        settingsHandler = TerminalSettingsHandler(
            context = requireContext(),
            prefs = prefs,
            terminalView = terminalView,
            onExtraKeysVisibilityChanged = { visible ->
                extraKeysHandler.setVisibility(visible)
            }
        )
        
        // Setup terminal view
        val savedTextSize = settingsHandler.getTextSize()
        terminalView?.apply {
            setTerminalViewClient(terminalViewClient)
            setTextSize(savedTextSize)
            setTypeface(Typeface.MONOSPACE)
            isFocusable = true
            isFocusableInTouchMode = true
        }
        
        // Setup all handlers
        extraKeysHandler.setup()
        sessionManager.setup()
        navigationHandler.setup()
        settingsHandler.setupSettings(rootView, savedTextSize)
        
        // Set initial extra keys visibility
        extraKeysHandler.setVisibility(settingsHandler.getExtraKeysVisibility())
        
        // Sync current session between handlers
        extraKeysHandler.currentSession = sessionManager.currentSession
        
        rootView.findViewById<MaterialButton>(R.id.create_terminal_button)?.setOnClickListener {
            bindTermuxServiceAndCreateSession()
        }
        
        return rootView
    }
    
    private fun handleNavigationItemSelected(itemId: Int): Boolean {
        return when (itemId) {
            R.id.nav_toggle_rail -> {
                if (terminalContent?.visibility == View.VISIBLE) {
                    navigationHandler.hideNavigationRail()
                }
                false
            }
            R.id.nav_terminal -> {
                if (sessionManager.hasActiveSessions()) showTerminal() else showEmptyState()
                true
            }
            R.id.nav_new_session -> {
                createNewSession()
                navigationRail?.selectedItemId = R.id.nav_terminal
                false
            }
            R.id.nav_settings -> {
                showSettings()
                true
            }
            else -> false
        }
    }
    
    private fun showEmptyState() {
        if (!isAdded) return
        emptyStateContent?.visibility = View.VISIBLE
        terminalContent?.visibility = View.GONE
        settingsContent?.visibility = View.GONE
    }
    
    private fun showTerminal() {
        if (!isAdded) return
        terminalContent?.visibility = View.VISIBLE
        emptyStateContent?.visibility = View.GONE
        settingsContent?.visibility = View.GONE
    }
    
    private fun showSettings() {
        if (!isAdded) return
        terminalContent?.visibility = View.GONE
        emptyStateContent?.visibility = View.GONE
        settingsContent?.visibility = View.VISIBLE
    }
    
    override fun onResume() {
        super.onResume()
        terminalView?.setTerminalCursorBlinkerState(true, true)
    }
    
    override fun onPause() {
        super.onPause()
        terminalView?.setTerminalCursorBlinkerState(false, false)
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        if (serviceIsBound) {
            try {
                context?.unbindService(serviceConnection)
                serviceIsBound = false
            } catch (e: Exception) {
                Logger.logError("TerminalFragment", "Error unbinding service: ${e.message}")
            }
        }
        terminalView = null
        navigationRail = null
        mainContent = null
        menuButton = null
        terminalContent = null
        settingsContent = null
        emptyStateContent = null
        sessionTabs = null
    }
    
    private fun bindTermuxServiceAndCreateSession() {
        if (!serviceIsBound) {
            val intent = Intent(requireContext(), TermuxService::class.java)
            requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            view?.postDelayed({ createNewSession() }, 100)
        } else {
            createNewSession()
        }
    }
    
    private fun createNewSession() {
        sessionManager.createNewSession(termuxService)
        // Sync current session to extra keys handler
        extraKeysHandler.currentSession = sessionManager.currentSession
    }
}