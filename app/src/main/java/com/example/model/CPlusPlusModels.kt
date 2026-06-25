package com.example.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class VariableInfo(
    val name: String,
    val type: String,
    val value: String
)

@JsonClass(generateAdapter = true)
data class DebugStep(
    val lineNumber: Int,
    val variables: List<VariableInfo> = emptyList(),
    val stdout: String = "",
    val isWaitingInput: Boolean = false,
    val inputPrompt: String? = null
)

@JsonClass(generateAdapter = true)
data class SimulatedRunResponse(
    val success: Boolean,
    val errorMessage: String? = null,
    val steps: List<DebugStep> = emptyList()
)

enum class TerminalLineType {
    SYSTEM,  // e.g., "Compiling...", "Process exited with code 0"
    STDOUT,  // C++ program output
    STDIN,   // User typed input
    ERROR    // Compiler error or runtime panic
}

data class TerminalLine(
    val text: String,
    val type: TerminalLineType
)
