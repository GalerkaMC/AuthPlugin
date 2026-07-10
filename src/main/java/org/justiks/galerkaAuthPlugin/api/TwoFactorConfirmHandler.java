package org.justiks.galerkaAuthPlugin.api;

import com.sun.net.httpserver.HttpExchange;
import org.bukkit.Bukkit;
import org.justiks.galerkaAuthPlugin.GalerkaAuthPlugin;
import org.justiks.galerkaAuthPlugin.database.entity.UserEntity;
import org.justiks.galerkaAuthPlugin.util.JsonUtils;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
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

    private record TwoFactorConfirmationBody(
        String userId,
        String nickname,
        String status
    ) {}

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

        String requestString = RequestBodies.readString(exchange);

        TwoFactorConfirmationBody requestBody = JsonUtils.GSON.fromJson(requestString, TwoFactorConfirmationBody.class);

        if (requestBody.userId.isEmpty()) {
            HttpResponses.badRequest(exchange, "Missing required field: userId");
            return;
        }

        if (requestBody.nickname.isEmpty()) {
            HttpResponses.badRequest(exchange, "Missing required field: nickname");
            return;
        }

        if (requestBody.status.isEmpty()) {
            HttpResponses.badRequest(exchange, "Missing required field: status");
            return;
        }

        String userId = requestBody.userId;
        String nickname = requestBody.nickname;
        String status = requestBody.status.toLowerCase();

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

        String username = user.getUsername();

        if ("denied".equals(status)) {
            runOnMainThread(plugin, () -> {
                plugin.rejectTwoFactorAuthentication(username);
                return true;
            });

            // Формируем ответный json
            Map<String, Object> responseData = new LinkedHashMap<>();
            responseData.put("status", "rejected");
            responseData.put("nickname", nickname);

            String formattedString = JsonUtils.GSON.toJson(responseData);

            HttpResponses.json(exchange, 200, formattedString);
            return;
        }

        if (!plugin.getAuthManager().isPendingTwoFactor(username)) {
            HttpResponses.notPending(exchange);
            return;
        }

        runOnMainThread(plugin, () -> {
                    plugin.getAuthListener().interruptThreadByNickname(nickname);
                    return true;
                }
        );

        boolean online = Bukkit.getPlayer(username) != null;

        Map<String, Object> responseData = new LinkedHashMap<>();
        responseData.put("status", "confirmed");
        responseData.put("nickname", nickname);
        responseData.put("online", online);

        String formattedString = JsonUtils.GSON.toJson(responseData);

        HttpResponses.json(exchange, 200, formattedString);
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
