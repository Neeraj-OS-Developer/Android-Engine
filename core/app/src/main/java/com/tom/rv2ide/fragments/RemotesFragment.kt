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
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.tom.rv2ide.R
import com.tom.rv2ide.adapters.RemotesAdapter
import com.tom.rv2ide.databinding.FragmentRemotesBinding
import com.tom.rv2ide.viewmodel.GitViewModel
import com.tom.rv2ide.utils.*

/**
 * @author Mohammed-baqer-null @ https://github.com/Mohammed-baqer-null
 */

class RemotesFragment : Fragment() {
    
    private var _binding: FragmentRemotesBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: GitViewModel by activityViewModels()
    private lateinit var adapter: RemotesAdapter

    private lateinit var progressDialog: ProgressDialogHelper
    private lateinit var prefsManager: PreferencesManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRemotesBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        progressDialog = ProgressDialogHelper(requireContext())
        prefsManager = PreferencesManager(requireContext())
        
        setupRecyclerView()
        setupObservers()
        setupButtons()
        
        viewModel.refreshRemotes()
    }
    
    private fun setupRecyclerView() {
        adapter = RemotesAdapter(
            onRemoveClick = { remote ->
                showRemoveRemoteConfirmation(remote.name)
            }
        )
        
        binding.recyclerViewRemotes.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewRemotes.adapter = adapter
    }
    
    private fun setupObservers() {
        viewModel.remotes.observe(viewLifecycleOwner) { remotes ->
            adapter.submitList(remotes)
            binding.emptyStateText.visibility = if (remotes.isEmpty()) View.VISIBLE else View.GONE
            binding.recyclerViewRemotes.visibility = if (remotes.isEmpty()) View.GONE else View.VISIBLE
        }
        
        viewModel.operationResult.observe(viewLifecycleOwner) { result ->
            if (!result.success) {
                showErrorDialog("Operation Failed", result.message)
            } else {
                Snackbar.make(binding.root, result.message, Snackbar.LENGTH_SHORT).show()
            }
        }
        
        viewModel.pushPullResult.observe(viewLifecycleOwner) { result ->
            if (!result.success) {
                showErrorDialog("${result.operation.capitalize()} Failed", result.message)
            } else {
                Snackbar.make(binding.root, result.message, Snackbar.LENGTH_LONG).show()
            }
        }
        
        viewModel.progressMessage.observe(viewLifecycleOwner) { message ->
            if (message != null) {
                progressDialog.show(message)
            } else {
                progressDialog.dismiss()
            }
        }
    }

    private fun setupButtons() {
        binding.fabAddRemote.setOnClickListener {
            showAddRemoteDialog()
        }
        
        binding.buttonRefresh.setOnClickListener {
            viewModel.refreshRemotes()
        }
        
        binding.buttonPush.setOnClickListener {
            showPushDialog()
        }
        
        binding.buttonPull.setOnClickListener {
            showPullDialog()
        }
        
        binding.buttonFetch.setOnClickListener {
            showFetchDialog()
        }
    }
    
    private fun showErrorDialog(title: String, message: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun showAddRemoteDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_remote, null)
        val editTextName = dialogView.findViewById<TextInputEditText>(R.id.editTextRemoteName)
        val editTextUrl = dialogView.findViewById<TextInputEditText>(R.id.editTextRemoteUrl)
        
        if (adapter.currentList.isEmpty()) {
            editTextName.setText("origin")
        }
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add Remote")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val name = editTextName.text.toString().trim()
                val url = editTextUrl.text.toString().trim()
                
                if (name.isNotBlank() && url.isNotBlank()) {
                    viewModel.addRemote(name, url)
                } else {
                    showErrorDialog("Invalid Input", "Name and URL cannot be empty")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showPushDialog() {
        val remotesList = adapter.currentList
        if (remotesList.isEmpty()) {
            showErrorDialog("No Remotes", "No remotes configured. Add a remote first.")
            return
        }
        
        val dialogView = layoutInflater.inflate(R.layout.dialog_push_pull, null)
        val editTextUsername = dialogView.findViewById<TextInputEditText>(R.id.editTextUsername)
        val editTextPassword = dialogView.findViewById<TextInputEditText>(R.id.editTextPassword)
        
        // Load saved credentials
        prefsManager.getUsername()?.let { editTextUsername.setText(it) }
        prefsManager.getPassword()?.let { editTextPassword.setText(it) }
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Push to Remote")
            .setMessage("Push your commits to ${remotesList[0].name}")
            .setView(dialogView)
            .setPositiveButton("Push") { _, _ ->
                val username = editTextUsername.text.toString().takeIf { it.isNotBlank() }
                val password = editTextPassword.text.toString().takeIf { it.isNotBlank() }
                
                // Save credentials if remember is enabled and credentials are provided
                if (username != null && password != null && prefsManager.shouldRememberCredentials()) {
                    prefsManager.saveCredentials(username, password)
                }
                
                viewModel.push(
                    remoteName = remotesList[0].name,
                    username = username,
                    password = password
                )
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Without credentials") { _, _ ->
                viewModel.push(remoteName = remotesList[0].name)
            }
            .show()
    }
    
    private fun showPullDialog() {
        val remotesList = adapter.currentList
        if (remotesList.isEmpty()) {
            showErrorDialog("No Remotes", "No remotes configured. Add a remote first.")
            return
        }
        
        val dialogView = layoutInflater.inflate(R.layout.dialog_push_pull, null)
        val editTextUsername = dialogView.findViewById<TextInputEditText>(R.id.editTextUsername)
        val editTextPassword = dialogView.findViewById<TextInputEditText>(R.id.editTextPassword)
        
        prefsManager.getUsername()?.let { editTextUsername.setText(it) }
        prefsManager.getPassword()?.let { editTextPassword.setText(it) }
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Pull from Remote")
            .setMessage("Pull changes from ${remotesList[0].name}")
            .setView(dialogView)
            .setPositiveButton("Pull") { _, _ ->
                val username = editTextUsername.text.toString().takeIf { it.isNotBlank() }
                val password = editTextPassword.text.toString().takeIf { it.isNotBlank() }
                
                // Save credentials if remember is enabled and credentials are provided
                if (username != null && password != null && prefsManager.shouldRememberCredentials()) {
                    prefsManager.saveCredentials(username, password)
                }
                
                viewModel.pull(
                    remoteName = remotesList[0].name,
                    username = username,
                    password = password
                )
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Without credentials") { _, _ ->
                viewModel.pull(remoteName = remotesList[0].name)
            }
            .show()
    }
    
    private fun showFetchDialog() {
        val remotesList = adapter.currentList
        if (remotesList.isEmpty()) {
            showErrorDialog("No Remotes", "No remotes configured. Add a remote first.")
            return
        }
        
        val dialogView = layoutInflater.inflate(R.layout.dialog_push_pull, null)
        val editTextUsername = dialogView.findViewById<TextInputEditText>(R.id.editTextUsername)
        val editTextPassword = dialogView.findViewById<TextInputEditText>(R.id.editTextPassword)
        
        // Load saved credentials
        prefsManager.getUsername()?.let { editTextUsername.setText(it) }
        prefsManager.getPassword()?.let { editTextPassword.setText(it) }
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Fetch from Remote")
            .setMessage("Fetch changes from ${remotesList[0].name}")
            .setView(dialogView)
            .setPositiveButton("Fetch") { _, _ ->
                val username = editTextUsername.text.toString().takeIf { it.isNotBlank() }
                val password = editTextPassword.text.toString().takeIf { it.isNotBlank() }
                
                // Save credentials if remember is enabled and credentials are provided
                if (username != null && password != null && prefsManager.shouldRememberCredentials()) {
                    prefsManager.saveCredentials(username, password)
                }
                
                viewModel.fetch(
                    remoteName = remotesList[0].name,
                    username = username,
                    password = password
                )
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Without credentials") { _, _ ->
                viewModel.fetch(remoteName = remotesList[0].name)
            }
            .show()
    }

    private fun showRemoveRemoteConfirmation(remoteName: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Remove Remote")
            .setMessage("Are you sure you want to remove remote '$remoteName'?")
            .setPositiveButton("Remove") { _, _ ->
                viewModel.removeRemote(remoteName)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        progressDialog.dismiss()
        _binding = null
    }
}