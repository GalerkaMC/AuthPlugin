package org.justiks.galerkaAuthPlugin.auth;

import at.favre.lib.crypto.bcrypt.BCrypt;

/**
 * Утилита для хеширования и проверки паролей с использованием BCrypt.
 */
public final class PasswordHasher {

    private static final int COST = 12;

    /**
     * Приватный конструктор — класс содержит только статические методы.
     */
    private PasswordHasher() {
    }

    /**
     * Создаёт BCrypt-хеш для переданного пароля.
     *
     * @param password пароль в открытом виде
     * @return строка BCrypt-хеша
     */
    public static String hash(String password) {
        return BCrypt.withDefaults().hashToString(COST, password.toCharArray());
    }

    /**
     * Проверяет соответствие пароля сохранённому BCrypt-хешу.
     *
     * @param password пароль в открытом виде
     * @param hash     сохранённый BCrypt-хеш
     * @return {@code true}, если пароль верный
     */
    public static boolean verify(String password, String hash) {
        BCrypt.Result result = BCrypt.verifyer().verify(password.toCharArray(), hash);
        return result.verified;
    }
}
