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
package com.tom.rv2ide.utils;

import android.annotation.SuppressLint;
import android.content.Context;

import androidx.annotation.NonNull;

import com.blankj.utilcode.util.FileUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Map;
import java.util.UUID;

/**
 * Central environment configuration and path management for AndroidIDE.
 * <p>
 * This class is responsible for initializing and providing access to all
 * critical directories and files used by the IDE, including the home directory,
 * prefix, project storage, Android SDK, Java runtime, and tooling APIs.
 * It also sets up environment variables for child processes and manages
 * temporary file creation.
 * <p>
 * All paths are initialized once via {@link #init(Context)} and are then
 * available as public static fields. The class is designed to be used as a
 * singleton-like utility with no instantiation.
 *
 * @author Neeraj-OS-developer
 * @since 1.0
 */
@SuppressLint("SdCardPath")
public final class Environment {

    /** Name of the default folder for storing AndroidIDE projects on external storage. */
    public static final String PROJECTS_FOLDER = "AndroidProjects";

    private static final Logger LOG = LoggerFactory.getLogger(Environment.class);

    // ------------------------------------------------------------------------
    // Core directory and file references (initialized in init())
    // ------------------------------------------------------------------------

    /** Root directory of the app's internal storage (context.getFilesDir()). */
    public static File ROOT;

    /** Prefix directory for the Linux-like environment (usr). */
    public static File PREFIX;

    /** Home directory for the IDE's user. */
    public static File HOME;

    /** AndroidIDE-specific configuration directory (.androidide). */
    public static File ANDROIDIDE_HOME;

    // public static File ANDROIDIDE_PREFIX;

    /** Directory for UI-related resources. */
    public static File ANDROIDIDE_UI;

    /** Java home directory (JDK 17 or 21). */
    public static File JAVA_HOME;

    /** Android SDK root directory. */
    public static File ANDROID_HOME;

    /** Temporary directory for the environment. */
    public static File TMP_DIR;

    /** Binary directory (usr/bin). */
    public static File BIN_DIR;

    /** Library directory (usr/lib). */
    public static File LIB_DIR;

    /** Directory for ACS projects (AT_ACSHOME_PROJECTS). */
    public static File AT_ACSHOME_PROJECTS;

    /** Main projects directory (external storage). */
    public static File PROJECTS_DIR;

    /** Realm database directory. */
    public static File REALM_DB_DIR;

    /** Path to the android.jar used by Java LSP until project initialization. */
    public static File ANDROID_JAR;

    /** Path to the tooling-api-all.jar. */
    public static File TOOLING_API_JAR;

    /** Gradle init script. */
    public static File INIT_SCRIPT;

    /** Gradle user home directory (.gradle). */
    public static File GRADLE_USER_HOME;

    /** Path to the aapt2 binary. */
    public static File AAPT2;

    /** Path to the java executable. */
    public static File JAVA;

    /** Path to the bash shell. */
    public static File BASH_SHELL;

    /** Path to the login shell. */
    public static File LOGIN_SHELL;

    // ------------------------------------------------------------------------
    // Language Server Protocol (LSP) directories
    // ------------------------------------------------------------------------

    /** Root directory for LSP servers. */
    public static File SERVERS_DIR;

    /** Directory for C/C++ LSP server. */
    public static File SERVERS_C_CPP_DIR;

    /** Directory for Kotlin LSP server. */
    public static File SERVERS_KOTLIN_DIR;

    /** Configuration directory for Kotlin language server. */
    public static File SERVER_CONFIG_DIR;

    /** ACS properties file. */
    public static File ACSIDE;

    /**
     * Initializes all environment paths and directories.
     * <p>
     * This method must be called once during application startup, before any
     * other component accesses the static fields. It creates necessary
     * directories, sets executable permissions, and configures system properties.
     *
     * @param context The application context, used to obtain the internal files directory.
     */
    public static void init(Context context) {
        ROOT = context.getFilesDir();
        PREFIX = mkdirIfNotExits(new File(ROOT, "usr"));
        HOME = mkdirIfNotExits(new File(ROOT, "home"));
        ANDROIDIDE_HOME = mkdirIfNotExits(new File(HOME, ".androidide"));
        // ANDROIDIDE_PREFIX = mkdirIfNotExits(new File(HOME, PREFIX.getName()));
        TMP_DIR = mkdirIfNotExits(new File(PREFIX, "tmp"));
        BIN_DIR = mkdirIfNotExits(new File(PREFIX, "bin"));
        LIB_DIR = mkdirIfNotExits(new File(PREFIX, "lib"));
        PROJECTS_DIR = mkdirIfNotExits(new File(FileUtil.getExternalStorageDir(), PROJECTS_FOLDER));
        AT_ACSHOME_PROJECTS = mkdirIfNotExits(new File(HOME, "ACSProjects"));
        ANDROID_JAR = mkdirIfNotExits(new File(ANDROIDIDE_HOME, "android.jar"));
        TOOLING_API_JAR = new File(mkdirIfNotExits(new File(ANDROIDIDE_HOME, "tooling-api")),
                "tooling-api-all.jar");
        AAPT2 = new File(ANDROIDIDE_HOME, "aapt2");
        ANDROIDIDE_UI = mkdirIfNotExits(new File(ANDROIDIDE_HOME, "ui"));
        REALM_DB_DIR = mkdirIfNotExits(new File(ROOT, "realm-dbs"));

        INIT_SCRIPT = new File(mkdirIfNotExits(new File(ANDROIDIDE_HOME, "init")), "init.gradle");
        GRADLE_USER_HOME = new File(HOME, ".gradle");

        ANDROID_HOME = new File(HOME, "android-sdk");

        // Prefer Java 17, fallback to Java 21 if 17 is not present.
        File java17Home = new File(PREFIX, "lib/jvm/java-17-openjdk");
        File java21Home = new File(PREFIX, "lib/jvm/java-21-openjdk");

        JAVA_HOME = java17Home.exists() ? java17Home : java21Home;

        JAVA = new File(JAVA_HOME, "bin/java");
        BASH_SHELL = new File(BIN_DIR, "bash");
        LOGIN_SHELL = new File(BIN_DIR, "login");

        // Server locations
        SERVERS_DIR = mkdirIfNotExits(new File(HOME, "acs/servers"));
        SERVERS_C_CPP_DIR = mkdirIfNotExits(new File(HOME, "acs/servers/c_cpp/server"));
        SERVERS_KOTLIN_DIR = mkdirIfNotExits(new File(HOME, "acs/servers/kotlin/server"));
        SERVER_CONFIG_DIR = mkdirIfNotExits(new File(HOME, ".config/kotlin-language-server"));

        // ACS
        ACSIDE = createFileIfNotExists(new File(PREFIX, "share/acside.properties"));

        setExecutable(JAVA);
        setExecutable(BASH_SHELL);

        System.setProperty("user.home", HOME.getAbsolutePath());
    }

    /**
     * Creates a directory if it does not already exist.
     *
     * @param in The file/directory to check and create.
     * @return The same file object passed in.
     */
    public static File mkdirIfNotExits(File in) {
        if (in != null && !in.exists()) {
            FileUtils.createOrExistsDir(in);
        }

        return in;
    }

    /**
     * Creates a file if it does not already exist.
     *
     * @param in The file to check and create.
     * @return The same file object passed in.
     */
    public static File createFileIfNotExists(File in) {
        if (in != null && !in.exists()) {
            FileUtils.createOrExistsFile(in);
        }
        return in;
    }

    /**
     * Sets the executable permission on the given file.
     *
     * @param file The file to mark as executable.
     */
    public static void setExecutable(@NonNull final File file) {
        if (!file.setExecutable(true)) {
            LOG.error("Unable to set executable permissions to file: {}", file);
        }
    }

    /**
     * Updates the project directory to the given file path.
     *
     * @param file The new project directory.
     */
    public static void setProjectDir(@NonNull File file) {
        PROJECTS_DIR = new File(file.getAbsolutePath());
    }

    /**
     * Populates the given map with environment variables for child processes.
     *
     * @param env        The map to populate.
     * @param forFailsafe If true, skips adding user-specific environment variables.
     */
    public static void putEnvironment(Map<String, String> env, boolean forFailsafe) {

        env.put("HOME", HOME.getAbsolutePath());
        env.put("ANDROID_HOME", ANDROID_HOME.getAbsolutePath());
        env.put("ANDROID_SDK_ROOT", ANDROID_HOME.getAbsolutePath());
        env.put("ANDROID_USER_HOME", HOME.getAbsolutePath() + "/.android");
        env.put("JAVA_HOME", JAVA_HOME.getAbsolutePath());
        env.put("GRADLE_USER_HOME", GRADLE_USER_HOME.getAbsolutePath());
        env.put("SYSROOT", PREFIX.getAbsolutePath());
        env.put("PROJECTS", PROJECTS_DIR.getAbsolutePath());
        env.put("AT_ACSHOME_PROJECTS", AT_ACSHOME_PROJECTS.getAbsolutePath());

        env.put("LD_LIBRARY_PATH", LIB_DIR.getAbsolutePath() + ":" +
                new File(JAVA_HOME, "lib").getAbsolutePath());
        env.put("TMPDIR", TMP_DIR.getAbsolutePath());

        // add user envs for non-failsafe sessions
        if (!forFailsafe) {
            // No mirror select
            env.put("TERMUX_PKG_NO_MIRROR_SELECT", "true");
        }
    }

    /**
     * Returns the cache directory for a given project.
     *
     * @param projectDir The root directory of the project.
     * @return The cache directory (projectDir/.acside).
     */
    public static File getProjectCacheDir(File projectDir) {
        return new File(projectDir, ".acside");
    }

    /**
     * Creates a unique temporary file that does not yet exist on disk.
     *
     * @return A new {@link File} object representing a non-existent temp file.
     */
    @NonNull
    public static File createTempFile() {
        var file = newTempFile();
        while (file.exists()) {
            file = newTempFile();
        }

        return file;
    }

    /**
     * Generates a random temporary file path (without checking existence).
     *
     * @return A new {@link File} object with a random name inside {@link #TMP_DIR}.
     */
    @NonNull
    private static File newTempFile() {
        return new File(TMP_DIR, "temp_" + UUID.randomUUID().toString().replace('-', 'X'));
    }
}
