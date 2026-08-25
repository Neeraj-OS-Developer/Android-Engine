
‎/*
‎ * This file is part of Android-Code-Studio.
‎ * Modified by Neeraj-OS-Developer.
‎ */

const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

/**
 * Recursively find all directories that contain build.gradle or build.gradle.kts
 * @param {string} dir - Directory to scan
 * @param {string[]} result - Accumulator for found module paths
 * @returns {string[]} List of module paths (relative to project root)
 */
function findAndroidModules(dir, result = []) {
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  for (const entry of entries) {
    // Skip hidden directories
    if (entry.name.startsWith('.')) continue;

    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      const hasGradle = fs.existsSync(path.join(fullPath, 'build.gradle')) ||
                        fs.existsSync(path.join(fullPath, 'build.gradle.kts'));

      if (hasGradle) {
        // Store relative path with forward slashes
        const relPath = path.relative(process.cwd(), fullPath).replace(/\\/g, '/');
        result.push(relPath);
      } else {
        // Continue scanning subdirectories
        findAndroidModules(fullPath, result);
      }
    }
  }
  return result;
}

/**
 * Get the set of modules that have been modified according to git status
 * @param {string[]} modules - List of all module paths
 * @returns {string[]} Modules with uncommitted changes
 */
function getChangedScopes(modules) {
  const changed = new Set();
  try {
    const output = execSync('git status --porcelain', { encoding: 'utf-8' });
    const files = output.split('\n')
      .map(line => line.trim().split(/\s+/).pop())
      .filter(Boolean);

    for (const file of files) {
      for (const mod of modules) {
        if (file.startsWith(mod + '/')) {
          changed.add(mod);
        }
      }
    }
  } catch (error) {
    // Silently fail – git may not be available or not a git repo
  }
  return [...changed];
}

// ----------------------------------------------------------------------
// Main configuration
// ----------------------------------------------------------------------
const allModules = findAndroidModules(process.cwd());
const changedScopes = getChangedScopes(allModules);

/** @type {import('cz-git').UserConfig} */
module.exports = {
  rules: {
    // You can add custom commitlint rules here if needed
    // Example: 'subject-case': [2, 'never', ['start-case', 'pascal-case']],
  },
  prompt: {
    messages: {
      type: 'Select the type of change you are committing:',
      scope: 'Select a scope (optional):',
      customScope: 'Enter a custom scope:',
      subject: 'Write a short, descriptive title for the change:\n',
      body: 'Provide a longer description of the change (optional). Use "|" to break lines:\n',
      breaking: 'List any breaking changes (optional). Use "|" to break lines:\n',
      footerPrefixesSelect: 'Select the issue prefix (optional):',
      customFooterPrefix: 'Enter a custom issue prefix:',
      footer: 'List related issues (optional), e.g., #31, #I3244:\n',
      confirmCommit: 'Are you sure you want to proceed with this commit?',
    },
    types: [
      { value: 'feat', name: 'feat:     A new feature' },
      { value: 'fix', name: 'fix:      A bug fix' },
      { value: 'docs', name: 'docs:     Documentation only changes' },
      { value: 'style', name: 'style:    Code style (formatting, missing semi-colons, etc.)' },
      { value: 'refactor', name: 'refactor: A code change that neither fixes a bug nor adds a feature' },
      { value: 'perf', name: 'perf:     A code change that improves performance' },
      { value: 'test', name: 'test:     Adding missing tests or correcting existing tests' },
      { value: 'build', name: 'build:    Changes that affect the build system or external dependencies' },
      { value: 'ci', name: 'ci:       Changes to CI configuration files and scripts' },
      { value: 'revert', name: 'revert:   Revert to a commit' },
      { value: 'chore', name: 'chore:    Other changes that don\'t modify src or test files' },
    ],
    allowBreakingChanges: ['feat', 'fix', 'build'],
    scopes: allModules,
    defaultScope: changedScopes,          // Automatically pre‑select changed modules
    enableMultipleScopes: true,           // Allow selecting several scopes
    scopeEnumSeparator: ',',              // Separate multiple scopes with a comma
    markBreakingChangeMode: true,         // Ask for breaking change info after type selection
  },
};
