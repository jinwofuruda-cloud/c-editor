package com.example.api

import com.example.BuildConfig
import com.example.model.SimulatedRunResponse
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// --- Gemini API Request Models ---

@JsonClass(generateAdapter = true)
data class Part(
    val text: String? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    val responseMimeType: String? = null,
    val temperature: Float? = null
)

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    val contents: List<Content>,
    val systemInstruction: Content? = null,
    val generationConfig: GenerationConfig? = null
)

// --- Gemini API Response Models ---

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: Content
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    val candidates: List<Candidate> = emptyList()
)

// --- Retrofit API Service ---

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val service: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }

    val moshiParser: Moshi = moshi
}

// --- Compiler Simulation Engine ---

class GeminiCompilerSimulator {

    private val systemInstructionText = """
        You are OnlineGDB C++ compiler, execution runner, and interactive debugger simulator.
        Your goal is to parse the user's C++ code, check for syntax or compilation errors, and simulate its step-by-step runtime execution, outputting a precise JSON trace.

        INPUT SPECIFICATION:
        The user will provide the C++ source code and an array of STDIN strings representing interactive keyboard inputs.
        
        SIMULATION ALGORITHM:
        1. Compile the code: If there are any compilation issues, missing brackets, undeclared variables, or invalid C++ syntax, return a response where success is false and errorMessage contains the compiler diagnostic errors (simulate standard gcc/g++ output). Do not generate any steps.
        2. Execute the code step-by-step:
           - Track line-by-line execution sequentially (following conditional branches, loop counters, function calls).
           - At each executed line (statement), record the 1-indexed lineNumber.
           - Record all variables currently active in scope, their type (e.g. int, double, std::string), and their current state/value AFTER executing that line.
           - Record the stdout output buffer printed up to that execution point.
           - Handling STDIN: When encountering a line containing standard input (e.g. 'std::cin >> x' or 'scanf("%d", &x)'), you MUST check if there is an available element in the provided STDIN array.
             - If yes: consume it, parse it to the appropriate variable type, update the variable in scope, and continue execution.
             - If no: halt execution AT THIS LINE. Set 'isWaitingInput' to true. Do not execute any further lines or generate any steps beyond this line. This will prompt the user for input in their terminal, and we will resume once they type it.
        
        JSON SCHEMA RESPONSE:
        You must return a JSON object conforming to this schema:
        {
          "success": boolean,
          "errorMessage": string or null,
          "steps": [
            {
              "lineNumber": integer,
              "variables": [
                { "name": "varName", "type": "varType", "value": "varValue" }
              ],
              "stdout": "cumulative printed stdout up to this step",
              "isWaitingInput": boolean,
              "inputPrompt": "optional hint string, e.g., 'Enter age: '"
            }
          ]
        }
        
        Ensure stdout text uses proper escaped newlines (\n). Avoid skipping steps. Output each sequential statement.
    """.trimIndent()

    suspend fun simulateExecution(code: String, stdinHistory: List<String>): SimulatedRunResponse = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext SimulatedRunResponse(
                success = false,
                errorMessage = "API key not configured. Please open the Secrets panel in AI Studio and enter your GEMINI_API_KEY."
            )
        }

        // Format inputs
        val inputsString = stdinHistory.joinToString(separator = "\n") { "\"$it\"" }
        val prompt = """
            C++ SOURCE CODE:
            ```cpp
            $code
            ```
            
            STDIN INPUT BUFFER HISTORY:
            [$inputsString]
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            systemInstruction = Content(parts = listOf(Part(text = systemInstructionText))),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.1f // low temperature for precise code execution simulation
            )
        )

        try {
            val response = RetrofitClient.service.generateContent(apiKey, request)
            val jsonString = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: return@withContext SimulatedRunResponse(success = false, errorMessage = "Error: Simulator returned empty response.")

            val adapter = RetrofitClient.moshiParser.adapter(SimulatedRunResponse::class.java)
            val result = adapter.fromJson(jsonString)
            result ?: SimulatedRunResponse(success = false, errorMessage = "Error: Failed to parse simulator response.")
        } catch (e: Exception) {
            SimulatedRunResponse(success = false, errorMessage = "Compilation/Simulation failed: ${e.message}")
        }
    }
}
