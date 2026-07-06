package org.justiks.galerkaAuthPlugin.api;

import com.sun.net.httpserver.HttpExchange;
import org.bukkit.Bukkit;
import org.justiks.galerkaAuthPlugin.GalerkaAuthPlugin;
import org.justiks.galerkaAuthPlugin.database.entity.UserEntity;
import org.justiks.galerkaAuthPlugin.util.JsonUtils;
import org.justiks.galerkaAuthPlugin.util.SimpleJsonParser;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Обработчик callback-запроса подтверждения 2FA от микросервиса AuthBot.
 * <p>
 * Эндпоинт: {@code POST /api/v1/2fa/confirm/}
 */
public final class TwoFactorConfirmHandler {

    private static final long MAIN_THREAD_TIMEOUT_SECONDS = 5;

    private TwoFactorConfirmHandler() {
    }

    /**
     * @return обработчик подтверждения 2FA
     */
    public static RestEndpointHandler create() {
        return (exchange, context) -> handle(exchange, context.getPlugin());
    }

    private static void handle(HttpExchange exchange, GalerkaAuthPlugin plugin) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            HttpResponses.methodNotAllowed(exchange);
            return;
        }

        if (!isAuthorized(exchange, plugin)) {
            HttpResponses.unauthorized(exchange);
            return;
        }

        String body = RequestBodies.readString(exchange);
        Optional<String> userIdOptional = SimpleJsonParser.getString(body, "userId");
        Optional<String> nicknameOptional = SimpleJsonParser.getString(body, "nickname");
        Optional<String> statusOptional = SimpleJsonParser.getString(body, "status");

        if (userIdOptional.isEmpty()) {
            HttpResponses.badRequest(exchange, "Missing required field: userId");
            return;
        }

        if (nicknameOptional.isEmpty()) {
            HttpResponses.badRequest(exchange, "Missing required field: nickname");
            return;
        }

        if (statusOptional.isEmpty()) {
            HttpResponses.badRequest(exchange, "Missing required field: status");
            return;
        }

        String userId = userIdOptional.get();
        String nickname = nicknameOptional.get();
        String status = statusOptional.get().toLowerCase();

        if (!"approved".equals(status) && !"denied".equals(status)) {
            HttpResponses.badRequest(exchange, "Invalid status. Allowed values: approved, denied");
            return;
        }

        Optional<UserEntity> userOptional = plugin.getAuthService().findByTelegramId(userId);
        if (userOptional.isEmpty()) {
            HttpResponses.accountNotFound(exchange);
            return;
        }

        UserEntity user = userOptional.get();
        if (!user.getUsername().equalsIgnoreCase(nickname)) {
            HttpResponses.badRequest(exchange, "Nickname does not match linked account");
            return;
        }

        UUID playerUuid = UUID.fromString(user.getUuid());

        if ("denied".equals(status)) {
            runOnMainThread(plugin, () -> {
                plugin.rejectTwoFactorAuthentication(playerUuid);
                return true;
            });
            HttpResponses.json(exchange, 200, JsonUtils.object(
                    "status", "rejected",
                    "nickname", nickname
            ));
            return;
        }

        if (!plugin.getAuthManager().isPendingTwoFactor(playerUuid)) {
            HttpResponses.notPending(exchange);
            return;
        }

        boolean confirmed = runOnMainThread(plugin, () ->
                plugin.completeTwoFactorAuthentication(playerUuid)
        );

        if (!confirmed) {
            HttpResponses.notPending(exchange);
            return;
        }

        boolean online = Bukkit.getPlayer(playerUuid) != null;
        HttpResponses.json(exchange, 200,
                "{"
                        + "\"status\":\"confirmed\","
                        + "\"nickname\":\"" + JsonUtils.escape(nickname) + "\","
                        + "\"online\":" + online
                        + "}"
        );
    }

    private static boolean isAuthorized(HttpExchange exchange, GalerkaAuthPlugin plugin) {
        String configuredKey = plugin.getPluginConfig().getRestApiKey();
        if (configuredKey == null || configuredKey.isBlank()) {
            return true;
        }

        String providedKey = exchange.getRequestHeaders().getFirst("X-Api-Key");
        return configuredKey.equals(providedKey);
    }

    private static boolean runOnMainThread(GalerkaAuthPlugin plugin, Supplier<Boolean> action) throws IOException {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                future.complete(action.get());
            } catch (Exception exception) {
                future.completeExceptionally(exception);
            }
        });

        try {
            return future.get(MAIN_THREAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException exception) {
            throw new IOException("Timed out waiting for main thread", exception);
        } catch (Exception exception) {
            throw new IOException("Failed to execute on main thread", exception);
        }
    }
}
