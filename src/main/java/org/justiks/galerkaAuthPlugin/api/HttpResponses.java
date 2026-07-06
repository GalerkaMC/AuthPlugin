package org.justiks.galerkaAuthPlugin.api;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Утилита для формирования HTTP-ответов REST API.
 */
public final class HttpResponses {

    private HttpResponses() {
    }

    /**
     * Отправляет JSON-ответ с указанным HTTP-кодом.
     *
     * @param exchange   HTTP-обмен
     * @param statusCode код ответа
     * @param body       тело ответа в JSON
     * @throws IOException при ошибке записи
     */
    public static void json(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);

        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }

    /**
     * Отправляет ответ 200 OK с JSON {@code {"status":"healthy","timestamp":"..."}}.
     *
     * @param exchange HTTP-обмен
     * @throws IOException при ошибке записи
     */
    public static void healthy(HttpExchange exchange) throws IOException {
        json(exchange, 200, "{\"status\":\"healthy\",\"timestamp\":\""
                + Instant.now().toString() + "\"}");
    }

    /**
     * Отправляет ответ 404 Not Found.
     *
     * @param exchange HTTP-обмен
     * @throws IOException при ошибке записи
     */
    public static void notFound(HttpExchange exchange) throws IOException {
        json(exchange, 404, "{\"error\":\"not_found\"}");
    }

    /**
     * Отправляет ответ 405 Method Not Allowed.
     *
     * @param exchange HTTP-обмен
     * @throws IOException при ошибке записи
     */
    public static void methodNotAllowed(HttpExchange exchange) throws IOException {
        json(exchange, 405, "{\"error\":\"method_not_allowed\"}");
    }

    /**
     * Отправляет ответ 500 Internal Server Error.
     *
     * @param exchange HTTP-обмен
     * @throws IOException при ошибке записи
     */
    public static void internalError(HttpExchange exchange) throws IOException {
        json(exchange, 500, "{\"error\":\"internal_server_error\"}");
    }

    /**
     * Отправляет ответ 400 Bad Request с описанием ошибки.
     *
     * @param exchange HTTP-обмен
     * @param message  описание ошибки
     * @throws IOException при ошибке записи
     */
    public static void badRequest(HttpExchange exchange, String message) throws IOException {
        json(exchange, 400, "{\"error\":\"bad_request\",\"message\":\""
                + escapeJson(message) + "\"}");
    }

    /**
     * Отправляет ответ 401 Unauthorized.
     *
     * @param exchange HTTP-обмен
     * @throws IOException при ошибке записи
     */
    public static void unauthorized(HttpExchange exchange) throws IOException {
        json(exchange, 401, "{\"error\":\"unauthorized\"}");
    }

    /**
     * Отправляет ответ 404 — аккаунт не найден.
     *
     * @param exchange HTTP-обмен
     * @throws IOException при ошибке записи
     */
    public static void accountNotFound(HttpExchange exchange) throws IOException {
        json(exchange, 404, "{\"error\":\"account_not_found\"}");
    }

    /**
     * Отправляет ответ 409 — игрок не ожидает подтверждения 2FA.
     *
     * @param exchange HTTP-обмен
     * @throws IOException при ошибке записи
     */
    public static void notPending(HttpExchange exchange) throws IOException {
        json(exchange, 409, "{\"error\":\"not_pending\","
                + "\"message\":\"Player is not awaiting 2FA confirmation\"}");
    }

    private static String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
