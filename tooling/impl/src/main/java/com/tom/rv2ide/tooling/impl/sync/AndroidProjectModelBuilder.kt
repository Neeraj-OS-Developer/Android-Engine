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
package com.tom.rv2ide.tooling.impl.sync

import com.android.builder.model.v2.models.AndroidDsl
import com.android.builder.model.v2.models.AndroidProject
import com.android.builder.model.v2.models.BasicAndroidProject
import com.android.builder.model.v2.models.ModelBuilderParameter
import com.android.builder.model.v2.models.ProjectSyncIssues
import com.android.builder.model.v2.models.VariantDependencies
import com.tom.rv2ide.tooling.api.IAndroidProject
import com.tom.rv2ide.tooling.api.messages.InitializeProjectParams
import com.tom.rv2ide.tooling.impl.internal.AndroidProjectImpl
import org.gradle.tooling.model.idea.IdeaSingleEntryLibraryDependency

/**
 * Builds model for Android application and library projects.
 *
 * @author Akash Yadav
 * @modification Mohammed-baqer-null @ https://github.com/Mohammed-baqer-null
 * ++ AGP 8.13.0: Updated to use additionalArtifactsInModel for proper dependency resolution
 */
class AndroidProjectModelBuilder(initializationParams: InitializeProjectParams) :
    AbstractModelBuilder<AndroidProjectModelBuilderParams, IAndroidProject>(initializationParams) {

  override fun build(param: AndroidProjectModelBuilderParams): IAndroidProject {
    val (controller, module, versions, syncIssueReporter) = param

    val androidParams = initializationParams.androidParams
    val projectPath = module.gradleProject.path
    val basicModel = controller.getModelAndLog(module, BasicAndroidProject::class.java)
    val androidModel = controller.getModelAndLog(module, AndroidProject::class.java)
    val androidDsl = controller.getModelAndLog(module, AndroidDsl::class.java)

    val variantNames = basicModel.variants.map { it.name }
    log("${variantNames.size} build variants found for project '$projectPath': $variantNames")

    var androidVariant = androidParams.variantSelections[projectPath]

    if (androidVariant != null && !variantNames.contains(androidVariant)) {
      log(
          "Configured variant '$androidVariant' not found for project '$projectPath'. Falling back to default variant."
      )
      androidVariant = null
    }

    val configurationVariant = androidVariant ?: variantNames.firstOrNull()
    if (configurationVariant.isNullOrBlank()) {
      throw ModelBuilderException(
          "No variant found for project '$projectPath'. providedVariant=$androidVariant"
      )
    }

    log("Selected build variant '$configurationVariant' for project '$projectPath'")

    try {
      log("Forcing dependency resolution for Android module: $projectPath")
      var downloadedCount = 0
      for (dependency in module.dependencies) {
        if (dependency is IdeaSingleEntryLibraryDependency) {
          try {
            val file = dependency.file
            if (file.exists()) {
              downloadedCount++
            }
          } catch (fileEx: Exception) {
            log("Failed to access dependency file: ${fileEx.message}")
          }
        }
      }
      log("Forced resolution of $downloadedCount dependencies for module: $projectPath")
    } catch (resEx: Exception) {
      log("Failed to pre-resolve dependencies: ${resEx.message}")
    }

    val variantDependencies =
        controller.getModelAndLog(
            module,
            VariantDependencies::class.java,
            ModelBuilderParameter::class.java,
        ) {
          it.variantName = configurationVariant
          it.additionalArtifactsInModel =
              true
          it.dontBuildRuntimeClasspath =
              false
          it.dontBuildUnitTestRuntimeClasspath = true
          it.dontBuildScreenshotTestRuntimeClasspath = true
          it.dontBuildAndroidTestRuntimeClasspath = true
          it.dontBuildTestFixtureRuntimeClasspath = true
          it.dontBuildHostTestRuntimeClasspath = emptyMap()
        }

    controller.findModel(module, ProjectSyncIssues::class.java)?.also { syncIssues ->
      syncIssueReporter.reportAll(syncIssues)
    }

    return AndroidProjectImpl(
        module.gradleProject,
        configurationVariant,
        basicModel,
        androidModel,
        variantDependencies,
        versions,
        androidDsl,
    )
  }
}
