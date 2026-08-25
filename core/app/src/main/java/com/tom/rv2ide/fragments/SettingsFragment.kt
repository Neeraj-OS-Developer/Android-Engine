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
package com.tom.rv2ide.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.tom.rv2ide.R
import com.tom.rv2ide.databinding.FragmentSettingsBinding
import com.tom.rv2ide.utils.PreferencesManager
import com.tom.rv2ide.viewmodel.GitViewModel

/**
 * @author Mohammed-baqer-null @ https://github.com/Mohammed-baqer-null
 */

class SettingsFragment : Fragment() {
    
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: GitViewModel by activityViewModels()
    private lateinit var prefsManager: PreferencesManager
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        prefsManager = PreferencesManager(requireContext())
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        loadSettings()
        setupButtons()
    }
    
    private fun loadSettings() {
        binding.textUserName.text = prefsManager.getGitUserName()
        binding.textUserEmail.text = prefsManager.getGitUserEmail()
        binding.switchRememberCredentials.isChecked = prefsManager.shouldRememberCredentials()
        
        val hasCredentials = prefsManager.getUsername() != null
        binding.textCredentialsStatus.text = if (hasCredentials) "Saved" else "Not saved"
    }
    
    private fun setupButtons() {
        binding.buttonEditUserConfig.setOnClickListener {
            showEditUserConfigDialog()
        }
        
        binding.switchRememberCredentials.setOnCheckedChangeListener { _, isChecked ->
            prefsManager.setRememberCredentials(isChecked)
            if (!isChecked) {
                prefsManager.clearCredentials()
                binding.textCredentialsStatus.text = "Not saved"
            }
        }
        
        binding.buttonClearCredentials.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Clear Credentials")
                .setMessage("Are you sure you want to clear saved credentials?")
                .setPositiveButton("Clear") { _, _ ->
                    prefsManager.clearCredentials()
                    binding.textCredentialsStatus.text = "Not saved"
                    Snackbar.make(binding.root, "Credentials cleared", Snackbar.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
    
    private fun showEditUserConfigDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_user_config, null)
        val editTextName = dialogView.findViewById<TextInputEditText>(R.id.editTextUserName)
        val editTextEmail = dialogView.findViewById<TextInputEditText>(R.id.editTextUserEmail)
        
        editTextName.setText(prefsManager.getGitUserName())
        editTextEmail.setText(prefsManager.getGitUserEmail())
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Git User Config")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val name = editTextName.text.toString().trim()
                val email = editTextEmail.text.toString().trim()
                
                if (name.isNotBlank() && email.isNotBlank()) {
                    prefsManager.setGitUserName(name)
                    prefsManager.setGitUserEmail(email)
                    viewModel.setUserConfig(name, email)
                    loadSettings()
                    Snackbar.make(binding.root, "User config updated", Snackbar.LENGTH_SHORT).show()
                } else {
                    Snackbar.make(binding.root, "Name and email cannot be empty", Snackbar.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}