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

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var securityManager: SecurityManager
    private lateinit var speechManager: SpeechManager

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

        observeViewModel()
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
        binding.tvStatus.text = "Listening..."
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
                        binding.tvStatus.text = "Jarvis is ready."
                        binding.btnSend.isEnabled = true
                    }
                    is MainViewModel.AgentState.InitializingLLM -> {
                        binding.tvStatus.text = "Jarvis is initializing Gemma LLM..."
                        binding.btnSend.isEnabled = false
                    }
                    is MainViewModel.AgentState.Processing -> {
                        binding.tvStatus.text = "Processing request..."
                        binding.btnSend.isEnabled = false
                    }
                    is MainViewModel.AgentState.ActionRequiresAuth -> {
                        binding.tvStatus.text = "Action requires your approval: ${state.command.action.name}"

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
                        binding.tvStatus.text = state.message
                        binding.btnSend.isEnabled = true
                        speechManager.speak("Executing action")
                        Toast.makeText(this@MainActivity, state.message, Toast.LENGTH_SHORT).show()
                    }
                    is MainViewModel.AgentState.Error -> {
                        binding.tvStatus.text = state.message
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
