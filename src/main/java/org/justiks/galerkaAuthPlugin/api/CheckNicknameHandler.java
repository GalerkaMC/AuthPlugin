package org.justiks.galerkaAuthPlugin.api;

import com.sun.net.httpserver.HttpExchange;
import org.justiks.galerkaAuthPlugin.GalerkaAuthPlugin;
import org.justiks.galerkaAuthPlugin.auth.AuthService;
import org.justiks.galerkaAuthPlugin.util.JsonUtils;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Эндпоинт для проверки наличия никнейма в вайтлисте
 */
public class CheckNicknameHandler {
    private CheckNicknameHandler() {
    }

    public static RestEndpointHandler create() {
        return (exchange, context) -> handle(exchange, context.getPlugin());
    }

    public static void handle(HttpExchange exchange, GalerkaAuthPlugin plugin) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            HttpResponses.methodNotAllowed(exchange);
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

        if (params.get("nickname") == null) {
            HttpResponses.json(exchange, 400, "Missing req param: nickname");
            return;
        }

        String nickname = params.get("nickname");
        System.out.println(nickname);

        AuthService authService = plugin.getAuthService();
        boolean exists = authService.isRegistered(nickname);

        // create response
        Map<String, Object> responseMap = new LinkedHashMap<>();
        responseMap.put("exists", exists);

        HttpResponses.json(
                exchange,
                200,
                JsonUtils.GSON.toJson(responseMap));
    }
}
