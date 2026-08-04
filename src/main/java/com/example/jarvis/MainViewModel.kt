package com.example.jarvis

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The 'Brain' of the Agent.
 * Orchestrates: STT -> LLM -> Tool Parsing -> (View handles Security) -> Execution
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"
    }

    private val llmManager = LlmManager(application)
    private val toolManager = ToolManager()

    // State management for UI observation
    sealed class AgentState {
        object Idle : AgentState()
        object InitializingLLM : AgentState()
        object Listening : AgentState()
        object Processing : AgentState()
        data class ActionRequiresAuth(val command: ToolManager.AgentCommand) : AgentState()
        data class ActionExecuted(val message: String) : AgentState()
        data class Error(val message: String) : AgentState()
    }

    private val _uiState = MutableStateFlow<AgentState>(AgentState.Idle)
    val uiState: StateFlow<AgentState> = _uiState.asStateFlow()

    init {
        // Initialize the heavy LLM on startup
        viewModelScope.launch {
            _uiState.value = AgentState.InitializingLLM
            val success = llmManager.initializeLlm()
            if (success) {
                _uiState.value = AgentState.Idle
            } else {
                _uiState.value = AgentState.Error("Failed to load local LLM model.")
            }
        }
    }

    /**
     * Entry point from the UI when speech-to-text finishes.
     */
    fun onSpeechInputReceived(text: String) {
        viewModelScope.launch {
            _uiState.value = AgentState.Processing
            Log.d(TAG, "User input: $text")

            // 1. Pass input to LLM
            val llmResponseJson = llmManager.generateResponse(text)

            // 2. Parse output strictly via ToolManager
            val command = toolManager.parseCommand(llmResponseJson)

            // 3. Handle Command
            when (command.action) {
                ToolManager.SecureAction.UNKNOWN -> {
                    _uiState.value = AgentState.Error("I'm sorry, I couldn't understand or perform that request.")
                }
                else -> {
                    // All other actions require Human-in-the-Loop validation.
                    // Push state to UI to trigger SecurityManager.
                    _uiState.value = AgentState.ActionRequiresAuth(command)
                }
            }
        }
    }

    /**
     * Called by the View layer after Biometric auth succeeds.
     */
    fun onAuthSucceeded(command: ToolManager.AgentCommand) {
        // 4. Execute the intent (abstracted here, would fire actual Android Intents)
        val msg = "Executing ${command.action.name} with target: ${command.target}"
        Log.i(TAG, msg)

        // TODO: Fire actual Android intents based on the command.action

        _uiState.value = AgentState.ActionExecuted(msg)
    }

    /**
     * Called by the View layer if Biometric auth fails.
     */
    fun onAuthFailed(reason: String) {
        Log.w(TAG, "Action blocked. Auth failed: $reason")
        _uiState.value = AgentState.Error("Action blocked by security: $reason")
    }

    override fun onCleared() {
        super.onCleared()
        llmManager.close()
    }
}
