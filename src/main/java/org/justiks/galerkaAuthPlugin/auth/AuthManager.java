package org.justiks.galerkaAuthPlugin.auth;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Менеджер сессий авторизации в оперативной памяти.
 * <p>
 * Отслеживает, какие игроки авторизованы в текущей сессии сервера,
 * ожидают подтверждения 2FA, и ведёт счётчик неудачных попыток входа.
 */
public final class AuthManager {

    private final Map<String, Boolean> authenticated = new ConcurrentHashMap<>();
    private final Map<String, String> pendingTwoFactorIp = new ConcurrentHashMap<>();

    /**
     * Проверяет, авторизован ли игрок в текущей сессии сервера.
     *
     * @param uuid UUID игрока
     * @return {@code true}, если игрок авторизован
     */
    public boolean isAuthenticated(UUID uuid) {
        return authenticated.getOrDefault(uuid, false);
    }

    /**
     * Проверяет, ожидает ли игрок подтверждения 2FA.
     *
     * @param username ник игрока
     * @return {@code true}, если пароль принят, но 2FA ещё не подтверждена
     */
    public boolean isPendingTwoFactor(String username) {
        return pendingTwoFactorIp.containsKey(username.toLowerCase());
    }

    /**
     * Помечает игрока как ожидающего подтверждения 2FA.
     *
     * @param username ник игрока
     * @param ip   IP-адрес для финализации сессии после подтверждения
     */
    public void setPendingTwoFactor(String username, String ip) {
        pendingTwoFactorIp.put(username.toLowerCase(), ip);
    }

    /**
     * Возвращает IP-адрес, сохранённый при начале ожидания 2FA.
     *
     * @param username ник игрока
     * @return IP-адрес или {@code null}
     */
    public String getPendingTwoFactorIp(String username) {
        return pendingTwoFactorIp.get(username.toLowerCase());
    }

    /**
     * Сбрасывает состояние ожидания 2FA.
     *
     * @param username ник игрока
     */
    public void clearPendingTwoFactor(String username) {
        pendingTwoFactorIp.remove(username.toLowerCase());
    }

    /**
     * Помечает игрока как авторизованного и сбрасывает счётчик попыток входа.
     *
     * @param username ник игрока
     */
    public void authenticate(String username) {
        authenticated.put(username, true);
        clearPendingTwoFactor(username);
    }

    /**
     * Сбрасывает авторизацию игрока и счётчик попыток входа.
     *
     * @param username ник игрока
     */
    public void unauthenticate(String username) {
        authenticated.remove(username);
        clearPendingTwoFactor(username);
    }

    /**
     * Очищает все данные сессий (при выключении плагина).
     */
    public void clear() {
        authenticated.clear();
        pendingTwoFactorIp.clear();
    }
}
