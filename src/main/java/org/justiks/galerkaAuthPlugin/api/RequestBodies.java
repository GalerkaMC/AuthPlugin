package org.justiks.galerkaAuthPlugin.api;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Утилита для чтения тела HTTP-запроса.
 */
public final class RequestBodies {

    private RequestBodies() {
    }

    /**
     * Читает тело запроса как UTF-8 строку.
     *
     * @param exchange HTTP-обмен
     * @return тело запроса
     * @throws IOException при ошибке чтения
     */
    public static String readString(HttpExchange exchange) throws IOException {
        try (InputStream inputStream = exchange.getRequestBody()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
