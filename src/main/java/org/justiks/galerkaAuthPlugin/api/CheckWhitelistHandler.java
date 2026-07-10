package org.justiks.galerkaAuthPlugin.api;

import com.sun.net.httpserver.HttpExchange;
import org.justiks.galerkaAuthPlugin.GalerkaAuthPlugin;
import org.justiks.galerkaAuthPlugin.auth.AuthService;
import org.justiks.galerkaAuthPlugin.database.entity.UserEntity;
import org.justiks.galerkaAuthPlugin.util.JsonUtils;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Check player in whitelist exists endpoint
 */
public class CheckWhitelistHandler {

    private CheckWhitelistHandler() {
    }

    public static RestEndpointHandler create() {
        return (exchange, context) -> handle(exchange, context.getPlugin());
    }

    public static void handle(HttpExchange exchange, GalerkaAuthPlugin plugin) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            HttpResponses.methodNotAllowed(exchange);
            return;
        }

        if (!isAuthorized(exchange, plugin)) {
            HttpResponses.unauthorized(exchange);
            return;
        }

        // Extract params
        String query = exchange.getRequestURI().getQuery();
        Map<String, String> params = new HashMap<>();

        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            // Decode keys and values to handle spaces and special characters
            String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
            params.put(key, value);
        }

        if (params.get("userId") == null) {
            HttpResponses.json(exchange, 400, "Missing req param: userId");
            return;
        }

        String userId = params.get("userId");

        AuthService authService = plugin.getAuthService();
        Optional<UserEntity> userEntity = authService.findByTelegramId(userId);

        // create response
        Map<String, Object> responseMap = new LinkedHashMap<>();
        if (userEntity.isEmpty()) {
            responseMap.put("exists", false);
        }
        else {
            responseMap.put("exists", true);
        }

        HttpResponses.json(
                exchange,
                200,
                JsonUtils.GSON.toJson(responseMap));
    }

    private static boolean isAuthorized(HttpExchange exchange, GalerkaAuthPlugin plugin) {
        String configuredKey = plugin.getPluginConfig().getRestApiKey();
        if (configuredKey == null || configuredKey.isBlank()) {
            return true;
        }

        String providedKey = exchange.getRequestHeaders().getFirst("X-Api-Key");
        return configuredKey.equals(providedKey);
    }
}
