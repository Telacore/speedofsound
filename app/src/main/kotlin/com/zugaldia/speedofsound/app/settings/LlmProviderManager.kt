package com.zugaldia.speedofsound.app.settings

import com.zugaldia.speedofsound.core.desktop.settings.SettingsClient
import com.zugaldia.speedofsound.core.desktop.settings.DEFAULT_SELECTED_TEXT_MODEL_PROVIDER_ID
import com.zugaldia.speedofsound.core.plugins.AppPluginCategory
import com.zugaldia.speedofsound.core.plugins.AppPluginRegistry
import com.zugaldia.speedofsound.core.plugins.llm.LlmProvider
import com.zugaldia.speedofsound.core.plugins.llm.AnthropicLlm
import com.zugaldia.speedofsound.core.plugins.llm.AnthropicLlmOptions
import com.zugaldia.speedofsound.core.plugins.llm.GoogleLlm
import com.zugaldia.speedofsound.core.plugins.llm.GoogleLlmOptions
import com.zugaldia.speedofsound.core.plugins.llm.LlmPluginOptions
import com.zugaldia.speedofsound.core.plugins.llm.OpenAiLlm
import com.zugaldia.speedofsound.core.plugins.llm.OpenAiLlmOptions
import com.zugaldia.speedofsound.core.plugins.llm.pluginIdForProvider
import org.slf4j.LoggerFactory

/**
 * Manages LLM provider selection, configuration, and plugin activation.
 */
class LlmProviderManager(
    private val registry: AppPluginRegistry,
    private val settingsClient: SettingsClient
) {
    private val logger = LoggerFactory.getLogger(LlmProviderManager::class.java)

    fun registerLlmPlugins() {
        registry.register(AppPluginCategory.LLM, AnthropicLlm())
        registry.register(AppPluginCategory.LLM, GoogleLlm())
        registry.register(AppPluginCategory.LLM, OpenAiLlm())
    }

    /**
     * Activates the currently selected LLM provider from settings.
     */
    fun activateSelectedProvider() {
        applySelectedProviderConfig(setActive = true)
    }

    /**
     * Refreshes the configuration for the currently selected provider.
     * Called when provider settings or credentials change.
     */
    fun refreshProviderConfiguration() {
        applySelectedProviderConfig(setActive = false)
    }

    /**
     * Applies configuration for the currently selected provider.
     * Optionally activates the provider if setActive is true.
     */
    private fun applySelectedProviderConfig(setActive: Boolean) {
        val selectedProviderId = settingsClient.peekSelectedTextModelProviderId()
        val selectedProvider = settingsClient.peekSelectedTextModelProvider()
        if (selectedProvider == null) {
            logger.warn(
                "Selected LLM provider {} is missing; disabling text processing",
                selectedProviderId.ifBlank { "<empty>" }
            )
            val disableSucceeded = disableTextProcessing(
                reason = "while removing missing LLM provider ${selectedProviderId.ifBlank { "<empty>" }}",
                successMessage = "Disabled text processing while removing missing LLM provider ${selectedProviderId.ifBlank { "<empty>" }}",
            )
            if (!disableSucceeded) {
                return
            }
            runCatching { registry.clearActive(AppPluginCategory.LLM) }
                .onFailure { error ->
                    logger.error(
                        "Failed to clear active LLM while disabling missing provider {}: {}",
                        selectedProviderId.ifBlank { "<empty>" },
                        error.message,
                        error,
                    )
                }
            if (selectedProviderId.isNotEmpty()) {
                if (!settingsClient.setSelectedTextModelProviderId(DEFAULT_SELECTED_TEXT_MODEL_PROVIDER_ID)) {
                    logger.warn(
                        "Could not persist default LLM selection while removing missing provider {}",
                        selectedProviderId,
                    )
                }
            }
            return
        }

        val currentActiveId = registry.getActive(AppPluginCategory.LLM)?.id
        val textProcessingEnabled = settingsClient.peekTextProcessingEnabled()
        if (!textProcessingEnabled && !setActive) {
            if (currentActiveId != null) {
                runCatching { registry.clearActive(AppPluginCategory.LLM) }
                    .onFailure { error ->
                        logger.error(
                            "Failed to clear active LLM while disabling text processing: {}",
                            error.message,
                            error,
                        )
                    }
            }
            return
        }
        val pluginId = pluginIdForProvider(selectedProvider.provider)
        val options = settingsClient.resolveTextProviderOptions(selectedProvider)
        applyLlmOptions(pluginId, options)

        if (setActive || currentActiveId != pluginId) {
            runCatching { registry.setActiveById(AppPluginCategory.LLM, pluginId) }
                .onFailure { error ->
                    logger.error(
                        "Failed to activate LLM provider {}: {}",
                        pluginId,
                        error.message,
                        error,
                    )
                }
        }

        if (registry.getActive(AppPluginCategory.LLM) != null) {
            return
        }

        if (!settingsClient.peekTextProcessingEnabled()) {
            return
        }

        logger.warn(
            "No active LLM provider after applying {}; disabling text processing",
            pluginId
        )
        if (!disableTextProcessing(
                reason = "after LLM activation left no active provider",
                successMessage = "Disabled text processing after LLM activation left no active provider"
            )
        ) {
            return
        }
        if (selectedProviderId.isNotBlank()) {
            if (!settingsClient.setSelectedTextModelProviderId(DEFAULT_SELECTED_TEXT_MODEL_PROVIDER_ID)) {
                logger.warn(
                    "Could not persist default LLM selection after activation left no active provider",
                )
            }
        }
    }

    private fun disableTextProcessing(reason: String, successMessage: String): Boolean {
        if (!settingsClient.peekTextProcessingEnabled()) {
            return true
        }
        return if (settingsClient.setTextProcessingEnabled(false)) {
            logger.warn(successMessage)
            true
        } else {
            logger.warn("Could not persist text processing disable {}", reason)
            false
        }
    }

    private fun applyLlmOptions(pluginId: String, options: LlmPluginOptions) {
        val plugin = registry.getPluginById(AppPluginCategory.LLM, pluginId) ?: return
        when (plugin) {
            is AnthropicLlm -> plugin.updateOptions(options as AnthropicLlmOptions)
            is GoogleLlm -> plugin.updateOptions(options as GoogleLlmOptions)
            is OpenAiLlm -> plugin.updateOptions(options as OpenAiLlmOptions)
        }
    }

    /**
     * Gets the name of the currently selected LLM provider.
     */
    fun peekCurrentProviderName(runtimeTextProcessingEnabled: Boolean? = null): String {
        if (runtimeTextProcessingEnabled == false) {
            return ""
        }
        val selectedProvider = settingsClient.peekSelectedTextModelProvider()
        if (selectedProvider != null) {
            return selectedProvider.name
        }
        val activeProviderId = registry.getActive(AppPluginCategory.LLM)?.id ?: return ""
        return LlmProvider.entries.firstOrNull { pluginIdForProvider(it) == activeProviderId }?.displayName ?: ""
    }
}
