package com.tom.rv2ide.activities

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.tom.rv2ide.databinding.ActivityM3iconsBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.zip.ZipInputStream
import com.tom.rv2ide.experimental.assetstudio.m3icons.*
import com.tom.rv2ide.viewmodel.MainViewModel
import com.tom.rv2ide.viewmodel.MainViewModel.Companion.SCREEN_MAIN
import com.tom.rv2ide.app.BaseApplication
import com.tom.rv2ide.app.EdgeToEdgeIDEActivity
import com.tom.rv2ide.handlers.ConfigHandlerRegistry
import com.tom.rv2ide.managers.PreferenceManager

class ActivityM3Icons : EdgeToEdgeIDEActivity() {

    private val viewModel by viewModels<MainViewModel>()
    private var _binding: ActivityM3iconsBinding? = null
    private lateinit var adapter: IconAdapter
    private var allIcons: List<Icon> = emptyList()
    private var isDarkTheme: Boolean = false

    private val binding: ActivityM3iconsBinding
        get() = checkNotNull(_binding)

    private val prefManager: PreferenceManager
        get() = BaseApplication.getBaseInstance().prefManager
          
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.setScreen(SCREEN_MAIN)
        isDarkTheme =
            resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
    
        ConfigHandlerRegistry.registerHandlers(this, prefManager, lifecycleScope, isDarkTheme)
    
        ConfigHandlerRegistry.getHandlers().forEach { handler ->
          handler.initialize()
        }
            
        setupRecyclerView()
        setupSearch()
        loadIcons()
    }
    
    private fun setupRecyclerView() {
        adapter = IconAdapter(emptyList())
        binding.recyclerView.layoutManager = GridLayoutManager(this, 3)
        binding.recyclerView.adapter = adapter
    }
    
    private fun setupSearch() {
        binding.searchEditText.addTextChangedListener { text ->
            filterIcons(text.toString())
        }
    }
    
    private fun filterIcons(query: String) {
        val filteredIcons = if (query.isEmpty()) {
            allIcons
        } else {
            allIcons.filter { icon ->
                icon.name.contains(query, ignoreCase = true)
            }
        }
        adapter.updateIcons(filteredIcons)
    }
    
    private fun loadIcons() {
        CoroutineScope(Dispatchers.Main).launch {
            val icons = withContext(Dispatchers.IO) {
                try {
                    val iconList = mutableListOf<Icon>()
                    val zipInputStream = ZipInputStream(assets.open("icons.zip"))
                    
                    var entry = zipInputStream.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory && entry.name.endsWith(".xml")) {
                            val fileName = entry.name.substringAfterLast("/")
                            val cleanName = fileName.removeSuffix(".xml")
                                .removePrefix("baseline_")
                                .removeSuffix("_24")
                                .replace("_", " ")
                            iconList.add(
                                Icon(
                                    name = cleanName,
                                    url = entry.name
                                )
                            )
                        }
                        zipInputStream.closeEntry()
                        entry = zipInputStream.nextEntry
                    }
                    zipInputStream.close()
                    iconList
                } catch (e: Exception) {
                    e.printStackTrace()
                    emptyList<Icon>()
                }
            }
            allIcons = icons
            adapter.updateIcons(icons)
        }
    }

    override fun bindLayout(): View {
        _binding = ActivityM3iconsBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onDestroy() {
        super.onDestroy()
        ConfigHandlerRegistry.getHandlers().forEach { it.cleanup() }
        _binding = null
    }
}