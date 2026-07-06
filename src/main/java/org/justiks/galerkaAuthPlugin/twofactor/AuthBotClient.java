package org.justiks.galerkaAuthPlugin.twofactor;

import org.justiks.galerkaAuthPlugin.config.PluginConfig;
import org.justiks.galerkaAuthPlugin.util.JsonUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HTTP-клиент для взаимодействия с микросервисом AuthBot.
 * <p>
 * Отправляет запросы на инициацию двухфакторной аутентификации
 * (POST {@code /api/v1/2fa/} согласно спецификации AuthBot).
 */
public final class AuthBotClient {

    private final PluginConfig config;
    private final HttpClient httpClient;
    private final Logger logger;

    /**
     * Создаёт клиент AuthBot.
     *
     * @param config конфигурация плагина
     * @param logger логгер плагина
     */
    public AuthBotClient(PluginConfig config, Logger logger) {
        this.config = config;
        this.logger = logger;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config.getAuthBotRequestTimeoutSeconds()))
                .build();
    }

    /**
     * Отправляет запрос на инициацию 2FA в AuthBot.
     * <p>
     * POST {@code /api/v1/2fa/} с телом {@code {"userId": "...", "nickname": "..."}}.
     *
     * @param telegramId Telegram ID пользователя
     * @param nickname   ник игрока на сервере
     * @return {@code true}, если AuthBot принял запрос (HTTP 2xx)
     */
    public boolean requestTwoFactor(String telegramId, String nickname) {
        String baseUrl = config.getAuthBotBaseUrl();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        String requestBody = JsonUtils.object("userId", telegramId, "nickname", nickname);
        URI uri = URI.create(baseUrl + "/api/v1/2fa/");

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(config.getAuthBotRequestTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                logger.info("Запрос 2FA отправлен для " + nickname + " (telegramId=" + telegramId + ")");
                return true;
            }

            logger.warning("AuthBot вернул HTTP " + response.statusCode() + " для " + nickname
                    + ": " + response.body());
            return false;
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            logger.log(Level.SEVERE, "Не удалось отправить запрос 2FA для " + nickname, exception);
            return false;
        }
    }
}
