package org.justiks.galerkaAuthPlugin.api;

import com.sun.net.httpserver.HttpExchange;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.justiks.galerkaAuthPlugin.GalerkaAuthPlugin;
import org.justiks.galerkaAuthPlugin.auth.AuthService;
import org.justiks.galerkaAuthPlugin.util.JsonUtils;

import java.io.IOException;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * Add to whitelist handler
 */
public final class AddWhitelistHandler {

    private AddWhitelistHandler() {
    }

    /**
     * Модель тела запроса, который приходит с клиента на эндпоинт
     *
     * @param userId   telegram user id
     * @param nickname minecraft nickname
     */
    private record WhitelistAddRequestBody(
            OptionalLong userId,
            Optional<String> nickname
    ) {
    }

    public static RestEndpointHandler create() {
        return (exchange, context) -> handle(exchange, context.getPlugin());
    }

    public static void handle(HttpExchange exchange, GalerkaAuthPlugin plugin) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            HttpResponses.methodNotAllowed(exchange);
            return;
        }

        String requestString = RequestBodies.readString(exchange);

        WhitelistAddRequestBody whitelistAddRequestBody = JsonUtils.GSON.fromJson(
                requestString,
                WhitelistAddRequestBody.class);

        if (whitelistAddRequestBody.userId.isEmpty()) {
            HttpResponses.badRequest(exchange, "Missing required field: userId");
            return;
        }

        if (whitelistAddRequestBody.nickname.isEmpty()) {
            HttpResponses.badRequest(exchange, "Missing required field: nickname");
            return;
        }

        long userId = whitelistAddRequestBody.userId.getAsLong();
        String nickname = whitelistAddRequestBody.nickname.get();


        // add player to database
        AuthService authService = plugin.getAuthService();

        if (authService.isRegistered(nickname)) {
            HttpResponses.badRequest(exchange, "Player already whitelisted");
            return;
        }

        authService.register(
                nickname,
                UUID.randomUUID(), // TOOD: remove it
                ""
        );

        // Add to whitelist
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(nickname);
        offlinePlayer.setWhitelisted(true);

        HttpResponses.json(exchange, 200, "");
    }
}
