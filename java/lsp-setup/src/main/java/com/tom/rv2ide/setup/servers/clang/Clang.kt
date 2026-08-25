/*
 *  This file is part of AndroidCodeStudio.
 *
 *  AndroidCodeStudio is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidCodeStudio is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidCodeStudio.  If not, see <https://www.gnu.org/licenses/>.
*/

package com.tom.rv2ide.setup.servers.clang

import android.content.Context
import com.tom.rv2ide.setup.servers.ILanguageServerInstaller
import com.tom.rv2ide.shell.executeProcessAsync
import com.tom.rv2ide.utils.Environment
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

/**
 * @author Mohammed-baqer-null @ https://github.com/Mohammed-baqer-null
 */

class Clang(private val context: Context) : ILanguageServerInstaller {
  
  private var hasOverwriteError = false
  private var hasUnmetDependencies = false
  private val errorPatterns = listOf(
    "trying to overwrite",
    "which is also in package",
    "returned an error code",
    "Unmet dependencies"
  )
  
  override fun isInstalled(): Boolean {
    val clangBinary = File(Environment.BIN_DIR, "clang")
    val clangdBinary = File(Environment.BIN_DIR, "clangd")
    return clangBinary.exists() && clangdBinary.exists()
  }
  
  override fun install(onOutput: (String) -> Unit): Boolean {
    return try {
      val env = mutableMapOf<String, String>()
      Environment.putEnvironment(env, false)
      
      // Reset error flags
      hasOverwriteError = false
      hasUnmetDependencies = false
      
      // Fix PATH issues first
      if (!fixSystemPath(env, onOutput)) {
        onOutput("\nWarning: Could not fix PATH issues, continuing anyway...")
      }
      
      onOutput("\nRunning dpkg --configure -a...")
      
      val dpkgProcess = executeProcessAsync {
        command = listOf(
          Environment.BIN_DIR.absolutePath + "/dpkg",
          "--configure",
          "-a"
        )
        environment = env
        workingDirectory = Environment.HOME
        redirectErrorStream = true
      }
      
      readProcessOutput(dpkgProcess, onOutput)
      val dpkgExitCode = dpkgProcess.waitFor()
      onOutput("\ndpkg --configure -a completed (exit code: $dpkgExitCode)")
      
      // If configure failed with overwrite error, try to fix it
      if (dpkgExitCode != 0 && hasOverwriteError) {
        onOutput("\nDetected package conflict. Attempting to resolve with --force-overwrite...")
        forceConfigurePackages(env, onOutput)
      }
      
      onOutput("\nRunning apt --fix-missing update...")
      
      val fixMissingProcess = executeProcessAsync {
        command = listOf(
          Environment.BIN_DIR.absolutePath + "/apt",
          "--fix-missing",
          "update"
        )
        environment = env
        workingDirectory = Environment.HOME
        redirectErrorStream = true
      }
      
      readProcessOutput(fixMissingProcess, onOutput)
      fixMissingProcess.waitFor()
      onOutput("\nApt --fix-missing update completed")
      
      // Try standard installation first
      onOutput("\nInstalling clang...")
      hasOverwriteError = false
      hasUnmetDependencies = false
      
      val installProcess = executeProcessAsync {
        command = listOf(
          Environment.BIN_DIR.absolutePath + "/apt",
          "install",
          "clang",
          "-y"
        )
        environment = env
        workingDirectory = Environment.HOME
        redirectErrorStream = true
      }
      
      readProcessOutput(installProcess, onOutput)
      val installExitCode = installProcess.waitFor()
      onOutput("\nClang installation command completed (exit code: $installExitCode)")
      
      // Check if installation was successful
      if (installExitCode == 0 && isInstalled()) {
        onOutput("\nClang installation successful!")
        return true
      }
      
      // If there's an overwrite error or unmet dependencies, use force-overwrite
      if (hasOverwriteError || hasUnmetDependencies) {
        onOutput("\nDetected package conflicts. Installing with --force-overwrite...")
        return installWithForceOverwrite(env, onOutput)
      }
      
      // Try fix-broken install
      onOutput("\nInstallation incomplete. Running apt --fix-broken install...")
      if (fixBrokenInstall(env, onOutput)) {
        if (isInstalled()) {
          onOutput("\nClang binaries found after fix!")
          return true
        }
      }
      
      // Final check
      onOutput("\nFinal check for clang binaries...")
      if (isInstalled()) {
        onOutput("Clang binaries found!")
        true
      } else {
        onOutput("Clang binaries not found. Installation failed.")
        onOutput("Please check the error messages above for details.")
        false
      }
    } catch (e: Exception) {
      onOutput("\nError during installation: ${e.message}")
      e.printStackTrace()
      false
    }
  }
  
  private fun fixSystemPath(
    env: MutableMap<String, String>,
    onOutput: (String) -> Unit
  ): Boolean {
    return try {
      onOutput("Checking system PATH...")
      
      // Ensure dpkg-deb and start-stop-daemon symlinks exist
      val binDir = File(Environment.BIN_DIR.absolutePath)
      val sbinDir = File(Environment.HOME, "usr/sbin")
      
      if (!sbinDir.exists()) {
        sbinDir.mkdirs()
        onOutput("Created sbin directory")
      }
      
      val dpkgDeb = File(binDir, "dpkg-deb")
      val startStopDaemon = File(binDir, "start-stop-daemon")
      
      if (!dpkgDeb.exists()) {
        onOutput("dpkg-deb not found in PATH")
      }
      
      if (!startStopDaemon.exists()) {
        onOutput("start-stop-daemon not found in PATH")
      }
      
      true
    } catch (e: Exception) {
      onOutput("Error checking PATH: ${e.message}")
      false
    }
  }
  
  private fun forceConfigurePackages(
    env: Map<String, String>,
    onOutput: (String) -> Unit
  ): Boolean {
    return try {
      val forceDpkgProcess = executeProcessAsync {
        command = listOf(
          Environment.BIN_DIR.absolutePath + "/dpkg",
          "--force-overwrite",
          "--configure",
          "-a"
        )
        environment = env
        workingDirectory = Environment.HOME
        redirectErrorStream = true
      }
      
      readProcessOutput(forceDpkgProcess, onOutput)
      val exitCode = forceDpkgProcess.waitFor()
      onOutput("\nForce configure completed (exit code: $exitCode)")
      exitCode == 0
    } catch (e: Exception) {
      onOutput("\nError during force configure: ${e.message}")
      false
    }
  }
  
  private fun installWithForceOverwrite(
    env: Map<String, String>,
    onOutput: (String) -> Unit
  ): Boolean {
    return try {
      onOutput("\nAttempting installation with force-overwrite option...")
      
      // First, try to fix broken packages with force-overwrite
      val fixBrokenProcess = executeProcessAsync {
        command = listOf(
          Environment.BIN_DIR.absolutePath + "/apt",
          "-o",
          "Dpkg::Options::=--force-overwrite",
          "--fix-broken",
          "install",
          "-y"
        )
        environment = env
        workingDirectory = Environment.HOME
        redirectErrorStream = true
      }
      
      readProcessOutput(fixBrokenProcess, onOutput)
      val fixExitCode = fixBrokenProcess.waitFor()
      onOutput("\nFix broken installation completed (exit code: $fixExitCode)")
      
      // Check if binaries exist now
      if (isInstalled()) {
        onOutput("\nClang binaries found after fix-broken!")
        return true
      }
      
      // If not installed yet, try direct installation with force-overwrite
      onOutput("\nRetrying clang installation with force-overwrite...")
      
      val forceInstallProcess = executeProcessAsync {
        command = listOf(
          Environment.BIN_DIR.absolutePath + "/apt",
          "-o",
          "Dpkg::Options::=--force-overwrite",
          "install",
          "clang",
          "-y"
        )
        environment = env
        workingDirectory = Environment.HOME
        redirectErrorStream = true
      }
      
      readProcessOutput(forceInstallProcess, onOutput)
      val installExitCode = forceInstallProcess.waitFor()
      onOutput("\nForce installation completed (exit code: $installExitCode)")
      
      // Final check
      val installed = isInstalled()
      if (installed) {
        onOutput("\nClang installation successful!")
      } else {
        onOutput("\nClang binaries still not found.")
        
        // One last attempt with install -f
        onOutput("\nRunning final fix attempt...")
        fixBrokenInstall(env, onOutput)
        
        return isInstalled()
      }
      
      installed
    } catch (e: Exception) {
      onOutput("\nError during force installation: ${e.message}")
      e.printStackTrace()
      false
    }
  }
  
  private fun fixBrokenInstall(
    env: Map<String, String>,
    onOutput: (String) -> Unit
  ): Boolean {
    return try {
      val fixProcess = executeProcessAsync {
        command = listOf(
          Environment.BIN_DIR.absolutePath + "/apt",
          "-o",
          "Dpkg::Options::=--force-overwrite",
          "install",
          "-f",
          "-y"
        )
        environment = env
        workingDirectory = Environment.HOME
        redirectErrorStream = true
      }
      
      readProcessOutput(fixProcess, onOutput)
      val exitCode = fixProcess.waitFor()
      onOutput("\nApt install -f completed (exit code: $exitCode)")
      exitCode == 0
    } catch (e: Exception) {
      onOutput("\nError during fix-broken install: ${e.message}")
      false
    }
  }
  
  private fun readProcessOutput(process: Process, onOutput: (String) -> Unit) {
    val reader = BufferedReader(InputStreamReader(process.inputStream))
    var lineCount = 0
    reader.useLines { lines ->
      lines.forEach { line ->
        onOutput(line)
        
        // Check for error patterns
        if (line.contains("trying to overwrite", ignoreCase = true) ||
            line.contains("which is also in package", ignoreCase = true)) {
          hasOverwriteError = true
        }
        
        if (line.contains("Unmet dependencies", ignoreCase = true) ||
            line.contains("fix-broken install", ignoreCase = true)) {
          hasUnmetDependencies = true
        }
        
        lineCount++
        if (lineCount % 5 == 0) {
          Thread.sleep(10)
        }
      }
    }
  }
}