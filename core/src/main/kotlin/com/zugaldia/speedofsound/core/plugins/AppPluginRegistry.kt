package com.zugaldia.speedofsound.core.plugins

import org.slf4j.LoggerFactory

/**
 * Manages the lifecycle of all plugins in the system.
 */
class AppPluginRegistry {
    private val log = LoggerFactory.getLogger(this::class.java)

    private val plugins = mutableMapOf<AppPluginCategory, MutableList<AppPlugin<*>>>()
    private val activePlugins = mutableMapOf<AppPluginCategory, String>()
    private var isShutdown = false

    /**
     * Registers a plugin for a given category and initializes it.
     */
    @Synchronized
    fun register(category: AppPluginCategory, plugin: AppPlugin<*>) {
        check(!isShutdown) { "Registry has been shut down" }
        log.info("Registering plugin ${plugin.id} for category $category")
        plugin.initialize()
        plugins.getOrPut(category) { mutableListOf() }.add(plugin)
    }

    /**
     * Sets a specific plugin as active for a given category by its ID.
     * Disables the currently active plugin (if any) and enables the new one.
     */
    @Synchronized
    fun setActiveById(category: AppPluginCategory, pluginId: String) {
        check(!isShutdown) { "Registry has been shut down" }
        if (activePlugins[category] == pluginId) {
            log.info("Plugin $pluginId is already active for category $category, skipping")
            return
        }

        val plugin = getPluginById(category, pluginId)
            ?: error("Plugin with ID $pluginId not found in category $category")
        val previousActiveId = activePlugins[category]

        // Enable the new plugin
        log.info("Setting plugin ${plugin.id} as active for category $category")
        plugin.enable()

        previousActiveId
            ?.takeIf { it != plugin.id }
            ?.let { currentActiveId ->
                val currentActive = getPluginById(category, currentActiveId)
                if (currentActive != null) {
                    runCatching {
                        log.info("Disabling currently active plugin ${currentActive.id}")
                        currentActive.disable()
                    }.onFailure { disableError ->
                        log.error(
                            "Failed to disable currently active plugin ${currentActive.id}: {}",
                            disableError.message,
                            disableError,
                        )
                        runCatching {
                            plugin.disable()
                        }.onFailure { rollbackError ->
                            log.error(
                                "Failed to roll back newly enabled plugin ${plugin.id}: {}",
                                rollbackError.message,
                                rollbackError,
                            )
                        }
                        throw IllegalStateException(
                            "Failed to disable currently active plugin ${currentActive.id} " +
                                "while switching to ${plugin.id}",
                            disableError,
                        )
                    }
                }
            }
        activePlugins[category] = plugin.id
    }

    /**
     * Gets a specific plugin by its ID within a category.
     * Returns null if not found.
     */
    @Synchronized
    fun getPluginById(category: AppPluginCategory, pluginId: String): AppPlugin<*>? {
        if (isShutdown) return null
        return plugins[category]?.find { it.id == pluginId }
    }

    /**
     * Gets the currently active plugin for a given category.
     * Returns null if no plugin is active for this category.
     */
    @Synchronized
    fun getActive(category: AppPluginCategory): AppPlugin<*>? {
        if (isShutdown) return null
        val activeId = activePlugins[category] ?: return null
        return getPluginById(category, activeId)
    }

    /**
     * Clears the active plugin for a category without selecting a replacement.
     * If an active plugin exists, it is disabled first.
     */
    @Synchronized
    fun clearActive(category: AppPluginCategory) {
        check(!isShutdown) { "Registry has been shut down" }
        val activeId = activePlugins.remove(category) ?: return
        val activePlugin = plugins[category]?.find { it.id == activeId } ?: return

        runCatching {
            activePlugin.disable()
        }.onFailure { error ->
            log.error(
                "Failed to disable active plugin ${activePlugin.id} while clearing category $category: {}",
                error.message,
                error,
            )
        }
    }

    @Synchronized
    fun isShutdown(): Boolean = isShutdown

    /**
     * Shuts down all registered plugins. Called when the application is shutting down.
     * Only disables plugins that are currently active.
     */
    @Synchronized
    fun shutdownAll() {
        if (isShutdown) {
            log.info("Shutting down all plugins skipped: registry already shut down")
            return
        }
        isShutdown = true
        log.info("Shutting down all plugins")
        val activePluginsSnapshot = activePlugins.toMap()
        plugins.forEach { (category, categoryPlugins) ->
            val activePluginId = activePluginsSnapshot[category]
            categoryPlugins.forEach { plugin ->
                if (plugin.id == activePluginId) {
                    runCatching {
                        plugin.disable()
                    }.onFailure { error ->
                        log.error(
                            "Failed to disable plugin ${plugin.id} during shutdown: {}",
                            error.message,
                            error,
                        )
                    }
                }
                runCatching {
                    plugin.shutdown()
                }.onFailure { error ->
                    log.error(
                        "Failed to shutdown plugin ${plugin.id}: {}",
                        error.message,
                        error,
                    )
                }
            }
        }
        activePlugins.clear()
        plugins.clear()
    }
}
