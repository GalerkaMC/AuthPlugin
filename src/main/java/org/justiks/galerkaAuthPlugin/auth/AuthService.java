package org.justiks.galerkaAuthPlugin.auth;

import org.hibernate.Session;
import org.justiks.galerkaAuthPlugin.config.PluginConfig;
import org.justiks.galerkaAuthPlugin.database.DatabaseManager;
import org.justiks.galerkaAuthPlugin.database.entity.UserEntity;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Сервис бизнес-логики авторизации.
 * <p>
 * Обрабатывает регистрацию, вход, смену пароля, авто-логин по IP-сессии
 * и удаление аккаунтов.
 */
public final class AuthService {

    private final DatabaseManager databaseManager;
    private final PluginConfig config;

    /**
     * Создаёт сервис авторизации.
     *
     * @param databaseManager менеджер базы данных
     * @param config          конфигурация плагина
     */
    public AuthService(DatabaseManager databaseManager, PluginConfig config) {
        this.databaseManager = databaseManager;
        this.config = config;
    }

    /**
     * Ищет пользователя по UUID.
     *
     * @param uuid UUID игрока
     * @return найденная сущность или пустой {@link Optional}
     */
    public Optional<UserEntity> findByUuid(UUID uuid) {
        return databaseManager.inSession(session -> findByUuidField(session, uuid));
    }

    /**
     * Ищет пользователя по нику (регистронезависимо).
     *
     * @param username ник игрока
     * @return найденная сущность или пустой {@link Optional}
     */
    public Optional<UserEntity> findByUsername(String username) {
        String normalized = normalizeUsername(username);
        return databaseManager.inSession(session ->
                Optional.ofNullable(session.get(UserEntity.class, normalized)));
    }

    /**
     * Ищет пользователя по привязанному Telegram ID.
     *
     * @param telegramId Telegram ID пользователя
     * @return найденная сущность или пустой {@link Optional}
     */
    public Optional<UserEntity> findByTelegramId(String telegramId) {
        if (telegramId == null || telegramId.isBlank()) {
            return Optional.empty();
        }

        return databaseManager.inSession(session -> session.createQuery(
                        "FROM UserEntity u WHERE u.telegramId = :telegramId",
                        UserEntity.class
                )
                .setParameter("telegramId", telegramId)
                .uniqueResultOptional());
    }

    /**
     * Проверяет, зарегистрирован ли игрок с указанным UUID.
     *
     * @param uuid UUID игрока
     * @return {@code true}, если аккаунт существует
     */
    public boolean isRegistered(UUID uuid) {
        return findByUuid(uuid).isPresent();
    }

    /**
     * Проверяет, занят ли указанный ник.
     *
     * @param username ник игрока
     * @return {@code true}, если аккаунт с таким ником существует
     */
    public boolean isRegistered(String username) {
        return findByUsername(username).isPresent();
    }

    /**
     * Регистрирует новый аккаунт игрока.
     *
     * @param username        ник игрока
     * @param uuid            UUID игрока
     * @param ip              IP-адрес игрока для создания сессии
     * @return результат регистрации
     */
    public RegisterResult register(
            String username,
            UUID uuid,
            String ip
    ) {
        String normalized = normalizeUsername(username);

        if (isRegistered(uuid)) {
            return RegisterResult.ALREADY_REGISTERED;
        }

        if (isRegistered(username)) {
            return RegisterResult.USERNAME_TAKEN;
        }

        UserEntity user = new UserEntity(
                normalized,
                uuid.toString(),
                Instant.now()
        );
        createSession(user, ip);

        databaseManager.inTransaction(session -> {
            session.persist(user);
            return null;
        });

        return RegisterResult.SUCCESS;
    }

    /**
     * Завершает вход: обновляет время последнего входа и создаёт IP-сессию.
     * Вызывается после успешной проверки пароля (и 2FA, если требуется).
     *
     * @param uuid UUID игрока
     * @param ip   IP-адрес игрока
     */
    public void finalizeLogin(UUID uuid, String ip) {
        Optional<UserEntity> userOptional = findByUuid(uuid);
        if (userOptional.isEmpty()) {
            return;
        }

        UserEntity user = userOptional.get();
        user.setLastLogin(Instant.now());
        createSession(user, ip);
        saveUser(user);
    }

    /**
     * Пытается автоматически авторизовать игрока по действующей IP-сессии.
     *
     * @param uuid UUID игрока
     * @param ip   текущий IP-адрес игрока
     * @return {@code true}, если авто-логин выполнен успешно
     */
    public boolean tryAutoLogin(UUID uuid, String ip) {
        if (!config.isIpSessionEnabled()) {
            return false;
        }

        Optional<UserEntity> userOptional = findByUuid(uuid);
        if (userOptional.isEmpty()) {
            return false;
        }

        UserEntity user = userOptional.get();
        if (!isSessionValid(user, ip)) {
            return false;
        }

        user.setLastLogin(Instant.now());
        createSession(user, ip);
        saveUser(user);
        return true;
    }

    /**
     * Сбрасывает IP-сессию игрока (при выходе из аккаунта).
     *
     * @param uuid UUID игрока
     */
    public void clearSession(UUID uuid) {
        Optional<UserEntity> userOptional = findByUuid(uuid);
        if (userOptional.isEmpty()) {
            return;
        }

        UserEntity user = userOptional.get();
        user.setSessionExpiresAt(null);
        saveUser(user);
    }

    /**
     * Проверяет, действительна ли IP-сессия пользователя.
     *
     * @param user сущность пользователя
     * @param ip   текущий IP-адрес
     * @return {@code true}, если сессия не истекла и IP совпадает (при включённой проверке)
     */
    private boolean isSessionValid(UserEntity user, String ip) {
        if (user.getSessionExpiresAt() == null || Instant.now().isAfter(user.getSessionExpiresAt())) {
            return false;
        }

        if (!config.isRequireSameIpForSession()) {
            return true;
        }

        return ip != null && ip.equals(user.getLastIp());
    }

    /**
     * Создаёт или продлевает IP-сессию для пользователя.
     *
     * @param user сущность пользователя
     * @param ip   IP-адрес игрока
     */
    private void createSession(UserEntity user, String ip) {
        if (!config.isIpSessionEnabled()) {
            return;
        }

        user.setLastIp(ip);
        user.setSessionExpiresAt(Instant.now().plusSeconds(config.getSessionDurationMinutes() * 60L));
    }

    /**
     * Сохраняет изменения сущности пользователя в базе данных.
     *
     * @param user сущность пользователя
     */
    private void saveUser(UserEntity user) {
        databaseManager.inTransaction(session -> {
            session.merge(user);
            return null;
        });
    }

    /**
     * Ищет пользователя по UUID внутри открытой Hibernate-сессии.
     *
     * @param session открытая сессия Hibernate
     * @param uuid    UUID игрока
     * @return найденная сущность или пустой {@link Optional}
     */
    private Optional<UserEntity> findByUuidField(Session session, UUID uuid) {
        return session.createQuery(
                        "FROM UserEntity u WHERE u.uuid = :uuid",
                        UserEntity.class
                )
                .setParameter("uuid", uuid.toString())
                .uniqueResultOptional();
    }

    /**
     * Приводит ник к нижнему регистру для единообразного хранения и поиска.
     *
     * @param username исходный ник
     * @return нормализованный ник
     */
    private static String normalizeUsername(String username) {
        return username.toLowerCase();
    }

    /**
     * Результат попытки регистрации аккаунта.
     */
    public enum RegisterResult {
        /** Регистрация прошла успешно. */
        SUCCESS,
        /** Пароль и подтверждение не совпадают. */
        PASSWORDS_MISMATCH,
        /** Пароль короче минимально допустимой длины. */
        PASSWORD_TOO_SHORT,
        /** Пароль длиннее максимально допустимой длины. */
        PASSWORD_TOO_LONG,
        /** Игрок с таким UUID уже зарегистрирован. */
        ALREADY_REGISTERED,
        /** Ник уже занят другим аккаунтом. */
        USERNAME_TAKEN
    }
}
