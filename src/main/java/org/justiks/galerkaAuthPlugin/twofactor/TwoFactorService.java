package org.justiks.galerkaAuthPlugin.twofactor;

import org.justiks.galerkaAuthPlugin.config.PluginConfig;
import org.justiks.galerkaAuthPlugin.database.entity.UserEntity;

/**
 * Сервис двухфакторной аутентификации через Telegram-бота.
 */
public final class TwoFactorService {

    private final PluginConfig config;
    private final AuthBotClient authBotClient;

    /**
     * Создаёт сервис 2FA.
     *
     * @param config        конфигурация плагина
     * @param authBotClient HTTP-клиент AuthBot
     */
    public TwoFactorService(PluginConfig config, AuthBotClient authBotClient) {
        this.config = config;
        this.authBotClient = authBotClient;
    }

    /**
     * Проверяет, требуется ли 2FA для данного пользователя.
     *
     * @param user сущность пользователя
     * @return {@code true}, если 2FA включена и Telegram ID привязан
     */
    public boolean requiresTwoFactor(UserEntity user) {
        if (!config.isTwoFactorEnabled()) {
            return false;
        }

        String telegramId = user.getTelegramId();
        return telegramId != null && !telegramId.isBlank();
    }

    /**
     * Отправляет запрос на подтверждение входа в AuthBot.
     *
     * @param user     сущность пользователя
     * @param nickname ник игрока на сервере
     * @return {@code true}, если запрос успешно отправлен
     */
    public boolean requestConfirmation(UserEntity user, String nickname) {
        return authBotClient.requestTwoFactor(user.getTelegramId(), nickname);
    }
}
