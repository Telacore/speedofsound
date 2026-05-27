package com.zugaldia.speedofsound.core.plugins.llm

import com.zugaldia.speedofsound.core.models.text.TextModel

const val DEFAULT_LLM_GOOGLE_MODEL_ID = "gemini-3.1-flash-lite"

val SUPPORTED_GOOGLE_TEXT_MODELS = mapOf(
    "gemini-3.5-flash" to TextModel(
        id = "gemini-3.5-flash",
        name = "Gemini 3.5 Flash",
        provider = LlmProvider.GOOGLE
    ),
    "gemini-3.1-flash-lite" to TextModel(
        id = "gemini-3.1-flash-lite",
        name = "Gemini 3.1 Flash Lite",
        provider = LlmProvider.GOOGLE
    ),
)

data class GoogleLlmOptions(
    override val baseUrl: String? = null,
    override val apiKey: String? = null,
    override val modelId: String = DEFAULT_LLM_GOOGLE_MODEL_ID,
    override val disableThinking: Boolean = false,
) : LlmPluginOptions {
    companion object {
        val Default = GoogleLlmOptions()
    }
}
