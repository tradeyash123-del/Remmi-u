package com.example.jarvis

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.jarvis.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var securityManager: SecurityManager
    private lateinit var speechManager: SpeechManager

    private val importModelLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            Toast.makeText(this, "Importing model, please wait...", Toast.LENGTH_LONG).show()
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val customModelFile = File(filesDir, "custom_model.bin")
                    contentResolver.openInputStream(it)?.use { inputStream ->
                        FileOutputStream(customModelFile).use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Model imported! Initializing...", Toast.LENGTH_SHORT).show()
                        viewModel.reinitializeLlm(customModelFile.absolutePath)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Error importing model.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private val requestRecordAudioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                startListeningSafely()
            } else {
                Toast.makeText(this, "Microphone permission is required.", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        securityManager = SecurityManager(this)

        speechManager = SpeechManager(this,
            onSpeechResult = { text ->
                binding.etInput.setText(text)
                viewModel.onSpeechInputReceived(text)
            },
            onSpeechError = { error ->
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
            }
        )

        binding.btnSend.setOnClickListener {
            val input = binding.etInput.text.toString()
            if (input.isNotBlank()) {
                viewModel.onSpeechInputReceived(input)
                binding.etInput.text.clear()
            }
        }

        binding.btnMic.setOnClickListener {
            checkMicrophonePermissionAndListen()
        }

        binding.btnImportModel.setOnClickListener {
            importModelLauncher.launch(arrayOf("*/*")) // Allow all files, but users should pick .bin
        }

        observeViewModel()
    }

    private fun appendLog(msg: String) {
        val currentText = binding.tvStatus.text.toString()
        binding.tvStatus.text = "$currentText\n$msg"
    }

    private fun checkMicrophonePermissionAndListen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startListeningSafely()
        } else {
            requestRecordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startListeningSafely() {
        speechManager.startListening()
        appendLog("Listening...")
    }

    override fun onDestroy() {
        super.onDestroy()
        speechManager.destroy()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is MainViewModel.AgentState.Idle -> {
                        appendLog("Jarvis is ready.")
                        binding.btnSend.isEnabled = true
                    }
                    is MainViewModel.AgentState.InitializingLLM -> {
                        appendLog("Jarvis is initializing Gemma LLM...")
                        binding.btnSend.isEnabled = false
                    }
                    is MainViewModel.AgentState.Processing -> {
                        appendLog("Processing request...")
                        binding.btnSend.isEnabled = false
                    }
                    is MainViewModel.AgentState.ActionRequiresAuth -> {
                        appendLog("Action requires your approval: ${state.command.action.name}")

                        if (securityManager.isBiometricReady()) {
                            securityManager.promptBiometricAuth(
                                actionDescription = "${state.command.action.name} target: ${state.command.target}",
                                onSuccess = { viewModel.onAuthSucceeded(state.command) },
                                onFailure = { reason -> viewModel.onAuthFailed(reason) }
                            )
                        } else {
                            viewModel.onAuthFailed("Biometrics not available or not set up.")
                        }
                    }
                    is MainViewModel.AgentState.ActionExecuted -> {
                        appendLog("Jarvis: ${state.message}")
                        binding.btnSend.isEnabled = true
                        speechManager.speak(state.message)
                    }
                    is MainViewModel.AgentState.Error -> {
                        appendLog("Error: ${state.message}")
                        binding.btnSend.isEnabled = true
                        speechManager.speak(state.message)
                    }
                    is MainViewModel.AgentState.Listening -> {
                        // Unused in basic text flow
                    }
                }
            }
        }
    }
}
