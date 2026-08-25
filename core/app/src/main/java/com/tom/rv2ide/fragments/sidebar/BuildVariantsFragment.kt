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

package com.tom.rv2ide.fragments.sidebar

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.DividerItemDecoration
import com.tom.rv2ide.activities.editor.ProjectHandlerActivity
import com.tom.rv2ide.adapters.BuildVariantsAdapter
import com.tom.rv2ide.databinding.FragmentBuildVariantsBinding
import com.tom.rv2ide.fragments.EmptyStateFragment
import com.tom.rv2ide.tooling.api.models.BuildVariantInfo
import com.tom.rv2ide.viewmodel.BuildVariantsViewModel
import com.tom.rv2ide.viewmodel.EditorViewModel
import com.tom.rv2ide.utils.GradleFileWriter
import com.tom.rv2ide.projects.IProjectManager
import java.io.File

/**
 * A fragment to show the list of Android modules and its build variants.
 *
 * @author Akash Yadav
 */
class BuildVariantsFragment :
    EmptyStateFragment<FragmentBuildVariantsBinding>(FragmentBuildVariantsBinding::inflate) {

  private val variantsViewModel by
      viewModels<BuildVariantsViewModel>(ownerProducer = { requireActivity() })

  private val editorViewModel by viewModels<EditorViewModel>(ownerProducer = { requireActivity() })

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    variantsViewModel._buildVariants.observe(viewLifecycleOwner) {
      populateRecyclerView()
      updateButtonStates(variantsViewModel.updatedBuildVariants)
    }

    variantsViewModel._updatedBuildVariants.observe(viewLifecycleOwner) { updatedVariants ->
      updateButtonStates(updatedVariants)
    }

    editorViewModel._isBuildInProgress.observe(viewLifecycleOwner) {
      updateButtonStates(variantsViewModel.updatedBuildVariants)
    }

    editorViewModel._isInitializing.observe(viewLifecycleOwner) {
      updateButtonStates(variantsViewModel.updatedBuildVariants)
    }

    binding.apply.setOnClickListener { 
        val activity = activity as? ProjectHandlerActivity ?: return@setOnClickListener
        val projectManager = IProjectManager.getInstance()
        val projectDir = projectManager.projectDir
        
        variantsViewModel.updatedBuildVariants.values.forEach { variantInfo ->
            val moduleDir = File(projectDir, variantInfo.projectPath.replace(":", File.separator))
            GradleFileWriter.updateModuleBuildGradle(
                moduleDir,
                variantInfo.versionName,
                variantInfo.versionCode,
                variantInfo.minSdk,
                variantInfo.targetSdk,
                variantInfo.compileSdk
            )
        }
        
        activity.initializeProject()
    }

    binding.discard.setOnClickListener {
      variantsViewModel.resetUpdatedSelections()
      populateRecyclerView()
    }

    binding.variantsList.addItemDecoration(
        DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
    )

    populateRecyclerView()
  }

  private fun updateButtonStates(updatedVariants: MutableMap<String, BuildVariantInfo>?) {
    _binding?.apply {
      val isBuilding = editorViewModel.let { it.isBuildInProgress || it.isInitializing }
      val isEnabled = updatedVariants?.isNotEmpty() == true && !isBuilding

      apply.isEnabled = isEnabled
      discard.isEnabled = isEnabled
    }
  }

  private fun populateRecyclerView() {
    _binding?.variantsList?.apply {
      this.adapter =
          BuildVariantsAdapter(variantsViewModel, variantsViewModel.buildVariants.values.toList())
      checkIsEmpty()
    }
  }
  
  override fun onDestroy() {
      super.onDestroy()
      com.tom.rv2ide.utils.EditorSidebarActions.removeFragmentFromCache("ide.editor.sidebar.buildVariants")
  }
  
  private fun checkIsEmpty() {
    emptyStateViewModel.isEmpty.value = _binding?.variantsList?.adapter?.itemCount == 0
  }
}