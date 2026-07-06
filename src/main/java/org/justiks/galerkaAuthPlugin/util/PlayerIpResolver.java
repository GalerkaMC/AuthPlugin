package org.justiks.galerkaAuthPlugin.util;

import org.bukkit.entity.Player;

/**
 * Утилита для получения IP-адреса подключённого игрока.
 */
public final class PlayerIpResolver {

    /**
     * Приватный конструктор — класс содержит только статические методы.
     */
    private PlayerIpResolver() {
    }

    /**
     * Определяет IP-адрес игрока из сетевого подключения.
     *
     * @param player игрок
     * @return IP-адрес в виде строки или {@code null}, если адрес недоступен
     */
    public static String resolve(Player player) {
        if (player.getAddress() == null || player.getAddress().getAddress() == null) {
            return null;
        }
        return player.getAddress().getAddress().getHostAddress();
    }
}
