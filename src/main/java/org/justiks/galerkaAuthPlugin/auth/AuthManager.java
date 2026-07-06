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

    private final Map<UUID, Integer> loginAttempts = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> authenticated = new ConcurrentHashMap<>();
    private final Map<UUID, String> pendingTwoFactorIp = new ConcurrentHashMap<>();

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
     * @param uuid UUID игрока
     * @return {@code true}, если пароль принят, но 2FA ещё не подтверждена
     */
    public boolean isPendingTwoFactor(UUID uuid) {
        return pendingTwoFactorIp.containsKey(uuid);
    }

    /**
     * Помечает игрока как ожидающего подтверждения 2FA.
     *
     * @param uuid UUID игрока
     * @param ip   IP-адрес для финализации сессии после подтверждения
     */
    public void setPendingTwoFactor(UUID uuid, String ip) {
        pendingTwoFactorIp.put(uuid, ip);
    }

    /**
     * Возвращает IP-адрес, сохранённый при начале ожидания 2FA.
     *
     * @param uuid UUID игрока
     * @return IP-адрес или {@code null}
     */
    public String getPendingTwoFactorIp(UUID uuid) {
        return pendingTwoFactorIp.get(uuid);
    }

    /**
     * Сбрасывает состояние ожидания 2FA.
     *
     * @param uuid UUID игрока
     */
    public void clearPendingTwoFactor(UUID uuid) {
        pendingTwoFactorIp.remove(uuid);
    }

    /**
     * Помечает игрока как авторизованного и сбрасывает счётчик попыток входа.
     *
     * @param uuid UUID игрока
     */
    public void authenticate(UUID uuid) {
        authenticated.put(uuid, true);
        loginAttempts.remove(uuid);
        clearPendingTwoFactor(uuid);
    }

    /**
     * Сбрасывает авторизацию игрока и счётчик попыток входа.
     *
     * @param uuid UUID игрока
     */
    public void unauthenticate(UUID uuid) {
        authenticated.remove(uuid);
        loginAttempts.remove(uuid);
        clearPendingTwoFactor(uuid);
    }

    /**
     * Увеличивает счётчик неудачных попыток входа на единицу.
     *
     * @param uuid UUID игрока
     * @return текущее количество неудачных попыток
     */
    public int incrementLoginAttempts(UUID uuid) {
        return loginAttempts.merge(uuid, 1, Integer::sum);
    }

    /**
     * Сбрасывает счётчик неудачных попыток входа.
     *
     * @param uuid UUID игрока
     */
    public void resetLoginAttempts(UUID uuid) {
        loginAttempts.remove(uuid);
    }

    /**
     * @param uuid UUID игрока
     * @return текущее количество неудачных попыток входа
     */
    public int getLoginAttempts(UUID uuid) {
        return loginAttempts.getOrDefault(uuid, 0);
    }

    /**
     * Очищает все данные сессий (при выключении плагина).
     */
    public void clear() {
        authenticated.clear();
        loginAttempts.clear();
        pendingTwoFactorIp.clear();
    }
}
