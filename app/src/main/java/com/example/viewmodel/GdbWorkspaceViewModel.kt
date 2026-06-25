package com.example.viewmodel

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.GeminiCompilerSimulator
import com.example.model.DebugStep
import com.example.model.TerminalLine
import com.example.model.TerminalLineType
import com.example.model.VariableInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ExecutionMode {
    object Idle : ExecutionMode
    object Compiling : ExecutionMode
    object Running : ExecutionMode
    object Debugging : ExecutionMode
    object PausedForInput : ExecutionMode
    data class Error(val message: String) : ExecutionMode
}

class GdbWorkspaceViewModel : ViewModel() {

    private val simulator = GeminiCompilerSimulator()

    // Editor Text State (using TextFieldValue to manage selection & bracket auto-pairing cursor positioning)
    private val _editorTextState = MutableStateFlow(TextFieldValue(defaultCppCode))
    val editorTextState: StateFlow<TextFieldValue> = _editorTextState.asStateFlow()

    // Breakpoints (Set of 1-indexed line numbers)
    private val _breakpoints = MutableStateFlow<Set<Int>>(setOf(8, 11)) // default sample breakpoints
    val breakpoints: StateFlow<Set<Int>> = _breakpoints.asStateFlow()

    // Execution Mode
    private val _executionMode = MutableStateFlow<ExecutionMode>(ExecutionMode.Idle)
    val executionMode: StateFlow<ExecutionMode> = _executionMode.asStateFlow()

    // Terminal History Lines
    private val _terminalLines = MutableStateFlow<List<TerminalLine>>(
        listOf(
            TerminalLine("OnlineGDB C++ Editor Environment Initialized.", TerminalLineType.SYSTEM),
            TerminalLine("Type C++ code above, set breakpoints, and click [Run] or [Debug].", TerminalLineType.SYSTEM)
        )
    )
    val terminalLines: StateFlow<List<TerminalLine>> = _terminalLines.asStateFlow()

    // Terminal current input buffer (for typing in console)
    private val _terminalInputBuffer = MutableStateFlow("")
    val terminalInputBuffer: StateFlow<String> = _terminalInputBuffer.asStateFlow()

    // Debugging Trace Details
    private var allDebugSteps: List<DebugStep> = emptyList()
    
    private val _currentStepIndex = MutableStateFlow(-1)
    val currentStepIndex: StateFlow<Int> = _currentStepIndex.asStateFlow()

    private val _activeHighlightLine = MutableStateFlow<Int?>(null)
    val activeHighlightLine: StateFlow<Int?> = _activeHighlightLine.asStateFlow()

    private val _activeVariables = MutableStateFlow<List<VariableInfo>>(emptyList())
    val activeVariables: StateFlow<List<VariableInfo>> = _activeVariables.asStateFlow()

    // Tracks STDIN inputs entered in the active execution run
    private val stdinHistory = mutableListOf<String>()

    fun updateEditorState(newValue: TextFieldValue) {
        val oldValue = _editorTextState.value
        
        // Detect typed pairing characters to auto-pair brackets
        if (newValue.text.length == oldValue.text.length + 1) {
            val typedCharIndex = newValue.selection.start - 1
            if (typedCharIndex >= 0 && typedCharIndex < newValue.text.length) {
                val typedChar = newValue.text[typedCharIndex]
                val closingChar = when (typedChar) {
                    '{' -> '}'
                    '(' -> ')'
                    '[' -> ']'
                    '"' -> '"'
                    '\'' -> '\''
                    else -> null
                }
                if (closingChar != null) {
                    val pairedText = newValue.text.substring(0, typedCharIndex + 1) + 
                            closingChar + 
                            newValue.text.substring(typedCharIndex + 1)
                    
                    _editorTextState.value = newValue.copy(
                        text = pairedText,
                        selection = TextRange(newValue.selection.start) // keep cursor inside
                    )
                    return
                }
            }
        }
        _editorTextState.value = newValue
    }

    fun toggleBreakpoint(lineNumber: Int) {
        val current = _breakpoints.value
        if (current.contains(lineNumber)) {
            _breakpoints.value = current - lineNumber
        } else {
            _breakpoints.value = current + lineNumber
        }
    }

    fun runCode() {
        val code = _editorTextState.value.text
        if (code.isBlank()) return

        _executionMode.value = ExecutionMode.Compiling
        _terminalLines.value = listOf(
            TerminalLine("g++ -O2 main.cpp -o main", TerminalLineType.SYSTEM),
            TerminalLine("Compiling source code...", TerminalLineType.SYSTEM)
        )
        stdinHistory.clear()
        allDebugSteps = emptyList()
        _currentStepIndex.value = -1
        _activeHighlightLine.value = null
        _activeVariables.value = emptyList()

        viewModelScope.launch {
            val response = simulator.simulateExecution(code, stdinHistory)
            if (!response.success || response.steps.isEmpty()) {
                _executionMode.value = ExecutionMode.Error(response.errorMessage ?: "Compilation failed")
                _terminalLines.value = _terminalLines.value + TerminalLine(
                    response.errorMessage ?: "g++: error: compilation failed with status 1", 
                    TerminalLineType.ERROR
                )
            } else {
                _executionMode.value = ExecutionMode.Running
                _terminalLines.value = _terminalLines.value + listOf(
                    TerminalLine("./main", TerminalLineType.SYSTEM),
                    TerminalLine("--- Process Executing ---", TerminalLineType.SYSTEM)
                )
                allDebugSteps = response.steps
                playRunSteps()
            }
        }
    }

    fun debugCode() {
        val code = _editorTextState.value.text
        if (code.isBlank()) return

        _executionMode.value = ExecutionMode.Compiling
        _terminalLines.value = listOf(
            TerminalLine("g++ -g main.cpp -o main", TerminalLineType.SYSTEM),
            TerminalLine("Compiling with debug symbols...", TerminalLineType.SYSTEM)
        )
        stdinHistory.clear()
        allDebugSteps = emptyList()
        _currentStepIndex.value = -1
        _activeHighlightLine.value = null
        _activeVariables.value = emptyList()

        viewModelScope.launch {
            val response = simulator.simulateExecution(code, stdinHistory)
            if (!response.success || response.steps.isEmpty()) {
                _executionMode.value = ExecutionMode.Error(response.errorMessage ?: "Compilation failed")
                _terminalLines.value = _terminalLines.value + TerminalLine(
                    response.errorMessage ?: "g++ debug build failed", 
                    TerminalLineType.ERROR
                )
            } else {
                _executionMode.value = ExecutionMode.Debugging
                _terminalLines.value = _terminalLines.value + listOf(
                    TerminalLine("gdb ./main", TerminalLineType.SYSTEM),
                    TerminalLine("--- GDB Debugger Session Started ---", TerminalLineType.SYSTEM),
                    TerminalLine("Type [Step] or [Continue] or hit breakpoints.", TerminalLineType.SYSTEM)
                )
                allDebugSteps = response.steps
                
                // Point GDB to step 0
                stepTo(0)
            }
        }
    }

    fun stopExecution() {
        _executionMode.value = ExecutionMode.Idle
        _activeHighlightLine.value = null
        _activeVariables.value = emptyList()
        _terminalLines.value = _terminalLines.value + TerminalLine(
            "--- Program Terminated ---", 
            TerminalLineType.SYSTEM
        )
    }

    fun updateTerminalInput(input: String) {
        _terminalInputBuffer.value = input
    }

    fun submitTerminalInput() {
        val input = _terminalInputBuffer.value
        _terminalInputBuffer.value = ""

        // Append to terminal history
        _terminalLines.value = _terminalLines.value + TerminalLine(input, TerminalLineType.STDIN)
        stdinHistory.add(input)

        // Resume simulation with updated inputs
        val currentMode = _executionMode.value
        _executionMode.value = ExecutionMode.Compiling
        
        _terminalLines.value = _terminalLines.value + TerminalLine(
            "Resuming execution with input: \"$input\"...", 
            TerminalLineType.SYSTEM
        )

        val code = _editorTextState.value.text
        viewModelScope.launch {
            val response = simulator.simulateExecution(code, stdinHistory)
            if (!response.success) {
                _executionMode.value = ExecutionMode.Error(response.errorMessage ?: "Simulation failure")
                _terminalLines.value = _terminalLines.value + TerminalLine(
                    response.errorMessage ?: "Failed to resume process", 
                    TerminalLineType.ERROR
                )
            } else {
                allDebugSteps = response.steps
                if (currentMode is ExecutionMode.Debugging) {
                    _executionMode.value = ExecutionMode.Debugging
                    // Find where the program left off (matching the last step index before input)
                    val resumeIndex = (allDebugSteps.indexOfLast { !it.isWaitingInput }).coerceAtLeast(0)
                    stepTo(resumeIndex)
                } else {
                    _executionMode.value = ExecutionMode.Running
                    playRunSteps()
                }
            }
        }
    }

    // Runs through the steps automatically for a normal "Run" session
    private fun playRunSteps() {
        var stdoutAccumulator = ""
        var isWaiting = false
        
        for (step in allDebugSteps) {
            if (step.stdout.length > stdoutAccumulator.length) {
                val newOutput = step.stdout.substring(stdoutAccumulator.length)
                stdoutAccumulator = step.stdout
                appendStdout(newOutput)
            }
            if (step.isWaitingInput) {
                isWaiting = true
                _executionMode.value = ExecutionMode.PausedForInput
                step.inputPrompt?.let { appendStdout(it) }
                break
            }
        }

        if (!isWaiting) {
            _executionMode.value = ExecutionMode.Idle
            _terminalLines.value = _terminalLines.value + TerminalLine(
                "\nProcess exited with status 0", 
                TerminalLineType.SYSTEM
            )
        }
    }

    private fun appendStdout(text: String) {
        val currentLines = _terminalLines.value.toMutableList()
        if (currentLines.isNotEmpty() && currentLines.last().type == TerminalLineType.STDOUT) {
            // Append directly to the last stdout block to simulate flowing stdout text
            val last = currentLines.removeAt(currentLines.lastIndex)
            currentLines.add(TerminalLine(last.text + text, TerminalLineType.STDOUT))
        } else {
            currentLines.add(TerminalLine(text, TerminalLineType.STDOUT))
        }
        _terminalLines.value = currentLines
    }

    // Navigation for step-by-step debug session
    fun stepDebugNext() {
        if (allDebugSteps.isEmpty()) return
        val nextIndex = _currentStepIndex.value + 1
        if (nextIndex < allDebugSteps.size) {
            stepTo(nextIndex)
        } else {
            finishDebugSession()
        }
    }

    fun continueDebug() {
        if (allDebugSteps.isEmpty()) return
        val startIndex = _currentStepIndex.value + 1
        var nextBreakpointIndex = -1
        
        for (i in startIndex until allDebugSteps.size) {
            val step = allDebugSteps[i]
            if (_breakpoints.value.contains(step.lineNumber) || step.isWaitingInput) {
                nextBreakpointIndex = i
                break
            }
        }

        if (nextBreakpointIndex != -1) {
            stepTo(nextBreakpointIndex)
        } else {
            // No breakpoints left, execute to end
            stepTo(allDebugSteps.lastIndex)
            finishDebugSession()
        }
    }

    private fun stepTo(index: Int) {
        if (index !in allDebugSteps.indices) return
        _currentStepIndex.value = index
        
        val step = allDebugSteps[index]
        _activeHighlightLine.value = step.lineNumber
        _activeVariables.value = step.variables

        // Synchronize stdout to this step
        _terminalLines.value = _terminalLines.value + TerminalLine(
            "Breakpoint hit/step: line ${step.lineNumber}", 
            TerminalLineType.SYSTEM
        )
        
        // Output standard out generated at this step
        if (step.stdout.isNotEmpty()) {
            _terminalLines.value = _terminalLines.value + TerminalLine(step.stdout, TerminalLineType.STDOUT)
        }

        if (step.isWaitingInput) {
            _executionMode.value = ExecutionMode.PausedForInput
            step.inputPrompt?.let { appendStdout(it) }
        }
    }

    private fun finishDebugSession() {
        _executionMode.value = ExecutionMode.Idle
        _activeHighlightLine.value = null
        _activeVariables.value = emptyList()
        _terminalLines.value = _terminalLines.value + TerminalLine(
            "--- Debugger Session Exited ---", 
            TerminalLineType.SYSTEM
        )
    }

    companion object {
        val defaultCppCode = """
#include <iostream>

int main() {
    std::cout << "=== Interactive Factorial ===\n";
    std::cout << "Enter a positive number: ";
    int num;
    std::cin >> num;
    
    long long factorial = 1;
    for (int i = 1; i <= num; ++i) {
        factorial *= i;
        std::cout << "i = " << i << ", factorial = " << factorial << "\n";
    }
    
    std::cout << "\nFinal Result: " << factorial << "\n";
    return 0;
}
        """.trimIndent()
    }
}
