package org.justiks.galerkaAuthPlugin.database.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA-сущность зарегистрированного игрока.
 * <p>
 * Хранит ник, UUID, хеш пароля, Telegram ID, даты входа и данные IP-сессии.
 */
@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @Column(name = "username", nullable = false, length = 16)
    private String username;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @Column(name = "last_login")
    private Instant lastLogin;

    @Column(name = "last_ip", length = 45)
    private String lastIp;

    @Column(name = "session_expires_at")
    private Instant sessionExpiresAt;

    @Column(name = "telegram_id", length = 32)
    private String telegramId;

    /**
     * Конструктор по умолчанию, требуемый JPA.
     */
    protected UserEntity() {
    }

    /**
     * Создаёт новую запись пользователя при регистрации.
     *
     * @param username     нормализованный ник игрока (первичный ключ)
     * @param registeredAt   момент регистрации
     * @param telegramId    user Id пользователя в telegram
     */
    public UserEntity(String username, Instant registeredAt, String telegramId) {
        this.username = username;
        this.registeredAt = registeredAt;
        this.telegramId = telegramId;
    }

    /**
     * @return нормализованный ник игрока
     */
    public String getUsername() {
        return username;
    }

    /**
     * @param username нормализованный ник игрока
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * @return момент регистрации аккаунта
     */
    public Instant getRegisteredAt() {
        return registeredAt;
    }

    /**
     * @param registeredAt момент регистрации аккаунта
     */
    public void setRegisteredAt(Instant registeredAt) {
        this.registeredAt = registeredAt;
    }

    /**
     * @return момент последнего успешного входа
     */
    public Instant getLastLogin() {
        return lastLogin;
    }

    /**
     * @param lastLogin момент последнего успешного входа
     */
    public void setLastLogin(Instant lastLogin) {
        this.lastLogin = lastLogin;
    }

    /**
     * @return IP-адрес при последней авторизации
     */
    public String getLastIp() {
        return lastIp;
    }

    /**
     * @param lastIp IP-адрес при последней авторизации
     */
    public void setLastIp(String lastIp) {
        this.lastIp = lastIp;
    }

    /**
     * @return момент истечения IP-сессии
     */
    public Instant getSessionExpiresAt() {
        return sessionExpiresAt;
    }

    /**
     * @param sessionExpiresAt момент истечения IP-сессии
     */
    public void setSessionExpiresAt(Instant sessionExpiresAt) {
        this.sessionExpiresAt = sessionExpiresAt;
    }

    /**
     * @return Telegram ID пользователя для 2FA или {@code null}, если не привязан
     */
    public String getTelegramId() {
        return telegramId;
    }

    /**
     * @param telegramId Telegram ID пользователя
     */
    public void setTelegramId(String telegramId) {
        this.telegramId = telegramId;
    }
}
