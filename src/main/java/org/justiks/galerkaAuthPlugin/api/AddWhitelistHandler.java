package org.justiks.galerkaAuthPlugin.api;

import com.sun.net.httpserver.HttpExchange;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.justiks.galerkaAuthPlugin.GalerkaAuthPlugin;
import org.justiks.galerkaAuthPlugin.auth.AuthService;
import org.justiks.galerkaAuthPlugin.util.JsonUtils;

import java.io.IOException;

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
            long userId,
            String nickname
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

        if (whitelistAddRequestBody.nickname.isEmpty()) {
            HttpResponses.badRequest(exchange, "Missing required field: nickname");
            return;
        }

        long userId = whitelistAddRequestBody.userId;
        String nickname = whitelistAddRequestBody.nickname;


        // add player to database
        AuthService authService = plugin.getAuthService();

        if (authService.isRegistered(nickname)) {
            HttpResponses.badRequest(exchange, "Player already whitelisted");
            return;
        }

        authService.register(
                nickname,
                "",
                String.valueOf(userId)
        );

        // Add to whitelist

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(nickname);
            offlinePlayer.setWhitelisted(true);
        });

        HttpResponses.json(exchange, 200, "");
    }
}
