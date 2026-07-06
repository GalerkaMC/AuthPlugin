package org.justiks.galerkaAuthPlugin.api;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;

/**
 * Обработчик одного REST-эндпоинта плагина.
 */
@FunctionalInterface
public interface RestEndpointHandler {

    /**
     * Обрабатывает HTTP-запрос.
     *
     * @param exchange HTTP-обмен
     * @param context  контекст плагина для доступа к сервисам
     * @throws IOException при ошибке чтения или записи ответа
     */
    void handle(HttpExchange exchange, RestApiContext context) throws IOException;
}
