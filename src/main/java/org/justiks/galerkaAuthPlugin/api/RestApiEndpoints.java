package org.justiks.galerkaAuthPlugin.api;

import com.sun.net.httpserver.HttpExchange;
import org.justiks.galerkaAuthPlugin.GalerkaAuthPlugin;

import java.io.IOException;

/**
 * Фабрика базовых эндпоинтов REST API плагина.
 * <p>
 * Содержит стартовый набор маршрутов. Дополнительные эндпоинты (например, callback 2FA)
 * регистрируются через {@link #registerDefaults(GalerkaAuthPlugin, RestApiRouter)}.
 */
public final class RestApiEndpoints {

    private RestApiEndpoints() {
    }

    /**
     * Регистрирует базовые эндпоинты REST API.
     *
     * @param plugin экземпляр плагина
     * @param router маршрутизатор
     */
    public static void registerDefaults(GalerkaAuthPlugin plugin, RestApiRouter router) {
        router.register("GET", "/api/v1/health/", health());
        router.register("POST", "/api/v1/2fa/confirm/", TwoFactorConfirmHandler.create());
    }

    /**
     * @return обработчик проверки работоспособности сервера
     */
    public static RestEndpointHandler health() {
        return (exchange, context) -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                HttpResponses.methodNotAllowed(exchange);
                return;
            }
            HttpResponses.healthy(exchange);
        };
    }
}
