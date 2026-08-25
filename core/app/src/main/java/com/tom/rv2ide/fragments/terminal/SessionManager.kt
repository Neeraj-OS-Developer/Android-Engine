package com.tom.rv2ide.fragments.terminal

import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.widget.PopupMenu
import androidx.fragment.app.Fragment
import com.google.android.material.tabs.TabLayout
import com.termux.app.TermuxService
import com.termux.shared.logger.Logger
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.tom.rv2ide.utils.Environment
import com.tom.rv2ide.projects.IProjectManager

class SessionManager(
    private val fragment: Fragment,
    private val terminalView: TerminalView?,
    private val sessionTabs: TabLayout?,
    private val terminalSessionClient: TerminalSessionClient,
    private val onSessionChange: (hasSession: Boolean) -> Unit
) {
    
    private val sessionsList = mutableListOf<TermuxSession>()
    var currentSession: TermuxSession? = null
        private set
    
    fun setup() {
        sessionTabs?.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                (tab.tag as? TermuxSession)?.let { switchToSession(it) }
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        
        sessionTabs?.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            for (i in 0 until (sessionTabs.tabCount)) {
                sessionTabs.getTabAt(i)?.view?.setOnLongClickListener { view ->
                    val session = sessionTabs.getTabAt(i)?.tag as? TermuxSession
                    session?.let { 
                        showSessionMenu(view, it)
                        true
                    } ?: false
                }
            }
        }
    }
    
    fun createNewSession(termuxService: TermuxService?) {
        val service = termuxService ?: return
        if (!fragment.isAdded) return
        
        val workingDir = IProjectManager.getInstance().projectDirPath ?: Environment.HOME.absolutePath
        val sessionName = "Session ${sessionsList.size + 1}"
        val shell = when {
            Environment.LOGIN_SHELL.exists() -> Environment.LOGIN_SHELL.absolutePath
            Environment.BASH_SHELL.exists() -> Environment.BASH_SHELL.absolutePath
            else -> "/system/bin/sh"
        }
        val arguments = if (shell.contains("login")) arrayOf("-l") else emptyArray()
        
        service.createTermuxSession(shell, arguments, null, workingDir, false, sessionName)?.let { session ->
            session.terminalSession.updateTerminalSessionClient(terminalSessionClient)
            sessionsList.add(session)
            addTabForSession(session)
            switchToSession(session)
            onSessionChange(true)
        }
    }
    
    fun switchToSession(session: TermuxSession) {
        if (!fragment.isAdded) return
        
        currentSession = session
        terminalView?.attachSession(session.terminalSession)
        
        for (i in 0 until (sessionTabs?.tabCount ?: 0)) {
            if (sessionTabs?.getTabAt(i)?.tag == session) {
                sessionTabs.selectTab(sessionTabs.getTabAt(i))
                break
            }
        }
        
        terminalView?.requestFocus()
    }
    
    fun closeSession(session: TermuxSession, termuxService: TermuxService?) {
        if (!fragment.isAdded) return
        
        // Send ENTER key to clean up the terminal before closing
        sendEnterKey()
        
        sessionsList.remove(session)
        removeTabForSession(session)
        
        if (currentSession == session) {
            if (sessionsList.isNotEmpty()) {
                switchToSession(sessionsList[0])
            } else {
                terminalView?.attachSession(null)
                currentSession = null
                onSessionChange(false)
            }
        }
        
        try {
            session.terminalSession.finishIfRunning()
            termuxService?.removeTermuxSession(session.terminalSession)
        } catch (e: Exception) {
            Logger.logError("SessionManager", "Error closing session: ${e.message}")
        }
    }
    
    private fun sendEnterKey() {
        val eventTime = System.currentTimeMillis()
        val keyCode = KeyEvent.KEYCODE_ENTER
        terminalView?.onKeyDown(keyCode, KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0, 0))
        terminalView?.onKeyUp(keyCode, KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0, 0))
    }
    
    fun onSessionFinished(finishedSession: TerminalSession, termuxService: TermuxService?) {
        if (!fragment.isAdded) return
        
        sessionsList.find { it.terminalSession == finishedSession }?.let { termuxSession ->
            closeSession(termuxSession, termuxService)
        }
    }
    
    fun updateTabTitle(terminalSession: TerminalSession) {
        if (!fragment.isAdded) return
        sessionsList.find { it.terminalSession == terminalSession }?.let { session ->
            for (i in 0 until (sessionTabs?.tabCount ?: 0)) {
                if (sessionTabs?.getTabAt(i)?.tag == session) {
                    sessionTabs.getTabAt(i)?.text = terminalSession.title ?: "Session ${i + 1}"
                    break
                }
            }
        }
    }
    
    private fun addTabForSession(session: TermuxSession) {
        if (!fragment.isAdded) return
        sessionTabs?.newTab()?.apply {
            tag = session
            text = session.terminalSession.title ?: "Session ${sessionsList.indexOf(session) + 1}"
            sessionTabs.addTab(this)
        }
    }
    
    private fun removeTabForSession(session: TermuxSession) {
        if (!fragment.isAdded) return
        for (i in 0 until (sessionTabs?.tabCount ?: 0)) {
            if (sessionTabs?.getTabAt(i)?.tag == session) {
                sessionTabs?.removeTabAt(i)
                break
            }
        }
    }
    
    private fun showSessionMenu(view: View, session: TermuxSession) {
        if (!fragment.isAdded) return
        val context = fragment.requireContext()
        PopupMenu(context, view).apply {
            menu.add(0, 1, 0, "Close Session")
            setOnMenuItemClickListener { item ->
                if (item.itemId == 1) {
                    closeSession(session, null)
                    true
                } else false
            }
            show()
        }
    }
    
    fun hasActiveSessions() = sessionsList.isNotEmpty()
    
    fun getSessionsList() = sessionsList.toList()
}