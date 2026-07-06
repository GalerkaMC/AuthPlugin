package org.justiks.galerkaAuthPlugin.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;

/**
 * Обёртка над {@code config.yml} плагина.
 * <p>
 * Хранит настройки авторизации, сессий и предоставляет локализованные сообщения.
 */
public final class PluginConfig {

    private final JavaPlugin plugin;
    private int minPasswordLength;
    private int maxPasswordLength;
    private int maxLoginAttempts;
    private Set<String> allowedCommands;
    private boolean ipSessionEnabled;
    private boolean requireSameIpForSession;
    private int sessionDurationMinutes;
    private boolean twoFactorEnabled;
    private String authBotBaseUrl;
    private int authBotRequestTimeoutSeconds;
    private boolean restApiEnabled;
    private String restApiHost;
    private int restApiPort;
    private String restApiKey;

    /**
     * Создаёт экземпляр конфигурации, привязанный к плагину.
     *
     * @param plugin экземпляр плагина
     */
    public PluginConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Загружает или создаёт {@code config.yml} и считывает все настройки.
     */
    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        minPasswordLength = config.getInt("settings.min-password-length", 6);
        maxPasswordLength = config.getInt("settings.max-password-length", 32);
        maxLoginAttempts = config.getInt("settings.max-login-attempts", 5);
        allowedCommands = new HashSet<>(config.getStringList("settings.allowed-commands-before-auth"));
        ipSessionEnabled = config.getBoolean("settings.ip-session-enabled", true);
        requireSameIpForSession = config.getBoolean("settings.require-same-ip-for-session", true);
        sessionDurationMinutes = config.getInt("settings.session-duration-minutes", 30);
        twoFactorEnabled = config.getBoolean("two-factor.enabled", true);
        authBotBaseUrl = config.getString("two-factor.authbot-base-url", "http://localhost:8000");
        authBotRequestTimeoutSeconds = config.getInt("two-factor.request-timeout-seconds", 10);
        restApiEnabled = config.getBoolean("rest-api.enabled", true);
        restApiHost = config.getString("rest-api.host", "127.0.0.1");
        restApiPort = config.getInt("rest-api.port", 8080);
        restApiKey = config.getString("rest-api.api-key", "");
    }

    /**
     * @return минимальная допустимая длина пароля
     */
    public int getMinPasswordLength() {
        return minPasswordLength;
    }

    /**
     * @return максимальная допустимая длина пароля
     */
    public int getMaxPasswordLength() {
        return maxPasswordLength;
    }

    /**
     * @return максимальное количество неудачных попыток входа до кика
     */
    public int getMaxLoginAttempts() {
        return maxLoginAttempts;
    }

    /**
     * @return набор команд, разрешённых до авторизации (в нижнем регистре)
     */
    public Set<String> getAllowedCommands() {
        return allowedCommands;
    }

    /**
     * @return {@code true}, если авто-логин по IP-сессии включён
     */
    public boolean isIpSessionEnabled() {
        return ipSessionEnabled;
    }

    /**
     * @return {@code true}, если для авто-логина требуется совпадение IP-адреса
     */
    public boolean isRequireSameIpForSession() {
        return requireSameIpForSession;
    }

    /**
     * @return длительность IP-сессии в минутах
     */
    public int getSessionDurationMinutes() {
        return sessionDurationMinutes;
    }

    /**
     * @return {@code true}, если двухфакторная аутентификация включена
     */
    public boolean isTwoFactorEnabled() {
        return twoFactorEnabled;
    }

    /**
     * @return базовый URL микросервиса AuthBot
     */
    public String getAuthBotBaseUrl() {
        return authBotBaseUrl;
    }

    /**
     * @return таймаут HTTP-запросов к AuthBot в секундах
     */
    public int getAuthBotRequestTimeoutSeconds() {
        return authBotRequestTimeoutSeconds;
    }

    /**
     * @return {@code true}, если встроенный REST API включён
     */
    public boolean isRestApiEnabled() {
        return restApiEnabled;
    }

    /**
     * @return адрес привязки REST API
     */
    public String getRestApiHost() {
        return restApiHost;
    }

    /**
     * @return порт REST API
     */
    public int getRestApiPort() {
        return restApiPort;
    }

    /**
     * @return общий API-ключ для авторизации запросов от микросервисов (пустая строка — проверка отключена)
     */
    public String getRestApiKey() {
        return restApiKey;
    }

    /**
     * Возвращает сообщение из конфигурации с заменой {@code &} на цветовые коды.
     *
     * @param key ключ сообщения в секции {@code messages}
     * @return локализованное сообщение или сам ключ, если сообщение не найдено
     */
    public String message(String key) {
        String value = plugin.getConfig().getString("messages." + key);
        if (value == null) {
            return key;
        }
        return value.replace('&', '\u00A7');
    }

    /**
     * Возвращает сообщение с подстановкой плейсхолдеров.
     * <p>
     * Плейсхолдеры передаются парами: {@code "{min}", "6", "{max}", "32"}.
     *
     * @param key          ключ сообщения
     * @param replacements пары «плейсхолдер — значение»
     * @return сообщение с подставленными значениями
     */
    public String message(String key, String... replacements) {
        String message = message(key);
        for (int index = 0; index + 1 < replacements.length; index += 2) {
            message = message.replace(replacements[index], replacements[index + 1]);
        }
        return message;
    }
}
