package org.justiks.galerkaAuthPlugin.api;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Маршрутизатор REST API плагина.
 * <p>
 * Сопоставляет пару «HTTP-метод + путь» с обработчиком.
 * Новые эндпоинты регистрируются через {@link #register(String, String, RestEndpointHandler)}.
 */
public final class RestApiRouter {

    private final Map<String, RestEndpointHandler> routes = new ConcurrentHashMap<>();
    private final RestApiContext context;
    private final Logger logger;

    /**
     * Создаёт маршрутизатор REST API.
     *
     * @param context контекст плагина
     * @param logger  логгер
     */
    public RestApiRouter(RestApiContext context, Logger logger) {
        this.context = context;
        this.logger = logger;
    }

    /**
     * Регистрирует обработчик для указанного метода и пути.
     *
     * @param method  HTTP-метод (GET, POST, ...)
     * @param path    путь эндпоинта (например, {@code /api/v1/health/})
     * @param handler обработчик запроса
     */
    public void register(String method, String path, RestEndpointHandler handler) {
        routes.put(routeKey(method, path), handler);
    }

    /**
     * Обрабатывает входящий HTTP-запрос.
     *
     * @param exchange HTTP-обмен
     */
    public void handle(HttpExchange exchange) {
        String method = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
        String path = normalizePath(exchange.getRequestURI().getPath());
        RestEndpointHandler handler = routes.get(routeKey(method, path));

        if (handler == null) {
            try {
                HttpResponses.notFound(exchange);
            } catch (IOException exception) {
                logger.log(Level.WARNING, "Не удалось отправить ответ 404", exception);
            }
            return;
        }

        try {
            handler.handle(exchange, context);
        } catch (Exception exception) {
            logger.log(Level.SEVERE, "Ошибка обработки " + method + " " + path, exception);
            try {
                HttpResponses.internalError(exchange);
            } catch (IOException ioException) {
                logger.log(Level.WARNING, "Не удалось отправить ответ 500", ioException);
            }
        }
    }

    /**
     * Формирует ключ маршрута из метода и пути.
     *
     * @param method HTTP-метод
     * @param path   путь
     * @return ключ маршрута
     */
    private static String routeKey(String method, String path) {
        return method.toUpperCase(Locale.ROOT) + " " + normalizePath(path);
    }

    /**
     * Нормализует путь: гарантирует ведущий слэш и завершающий слэш.
     *
     * @param path исходный путь
     * @return нормализованный путь
     */
    private static String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }

        String normalized = path.startsWith("/") ? path : "/" + path;
        if (!normalized.endsWith("/")) {
            normalized = normalized + "/";
        }
        return normalized;
    }
}
