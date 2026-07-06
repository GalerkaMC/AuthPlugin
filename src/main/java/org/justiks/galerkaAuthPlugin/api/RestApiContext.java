package org.justiks.galerkaAuthPlugin.api;

import org.justiks.galerkaAuthPlugin.GalerkaAuthPlugin;

/**
 * Контекст REST API с доступом к экземпляру плагина и его сервисам.
 */
public final class RestApiContext {

    private final GalerkaAuthPlugin plugin;

    /**
     * Создаёт контекст REST API.
     *
     * @param plugin экземпляр плагина
     */
    public RestApiContext(GalerkaAuthPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * @return экземпляр плагина
     */
    public GalerkaAuthPlugin getPlugin() {
        return plugin;
    }
}
