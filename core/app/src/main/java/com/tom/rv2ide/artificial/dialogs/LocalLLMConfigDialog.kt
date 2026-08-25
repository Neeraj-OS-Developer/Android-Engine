package com.tom.rv2ide.artificial.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.textfield.TextInputEditText
import com.tom.rv2ide.R
import com.tom.rv2ide.app.BaseApplication

class LocalLLMConfigDialog(
    private val onSave: (baseUrl: String, modelName: String) -> Unit
) : BottomSheetDialogFragment() {

    private lateinit var baseUrlInput: TextInputEditText
    private lateinit var modelNameInput: TextInputEditText
    private lateinit var saveButton: Button
    private lateinit var cancelButton: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_local_llm_config, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        baseUrlInput = view.findViewById(R.id.baseUrlInput)
        modelNameInput = view.findViewById(R.id.modelNameInput)
        saveButton = view.findViewById(R.id.saveButton)
        cancelButton = view.findViewById(R.id.cancelButton)

        loadSavedConfig()

        saveButton.setOnClickListener {
            val baseUrl = baseUrlInput.text.toString().trim()
            val modelName = modelNameInput.text.toString().trim()

            if (baseUrl.isNotEmpty() && modelName.isNotEmpty()) {
                saveConfig(baseUrl, modelName)
                onSave(baseUrl, modelName)
                dismiss()
            }
        }

        cancelButton.setOnClickListener {
            dismiss()
        }
    }

    private fun loadSavedConfig() {
        val prefs = BaseApplication.getBaseInstance().prefManager
        baseUrlInput.setText(prefs.getString("local_llm_base_url", "http://localhost:1234"))
        modelNameInput.setText(prefs.getString("local_llm_model_name", "local-model"))
    }

    private fun saveConfig(baseUrl: String, modelName: String) {
        val prefs = BaseApplication.getBaseInstance().prefManager
        prefs.putString("local_llm_base_url", baseUrl)
        prefs.putString("local_llm_model_name", modelName)
    }
}