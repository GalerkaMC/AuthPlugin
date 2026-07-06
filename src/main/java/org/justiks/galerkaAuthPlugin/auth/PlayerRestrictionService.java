package org.justiks.galerkaAuthPlugin.auth;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Сервис ограничений для неавторизованных игроков.
 * <p>
 * Замораживает игрока на месте, делает его неуязвимым и обнуляет скорость передвижения.
 */
public final class PlayerRestrictionService {

    private static final float DEFAULT_WALK_SPEED = 0.2f;
    private static final float DEFAULT_FLY_SPEED = 0.1f;

    private final Map<UUID, Location> frozenLocations = new ConcurrentHashMap<>();
    private final Map<UUID, Float> savedWalkSpeed = new ConcurrentHashMap<>();
    private final Map<UUID, Float> savedFlySpeed = new ConcurrentHashMap<>();

    /**
     * Применяет ограничения к игроку: сохраняет позицию, скорости и делает неуязвимым.
     *
     * @param player игрок, которому применяются ограничения
     */
    public void restrict(Player player) {
        UUID uuid = player.getUniqueId();
        frozenLocations.put(uuid, player.getLocation().clone());
        savedWalkSpeed.put(uuid, player.getWalkSpeed());
        savedFlySpeed.put(uuid, player.getFlySpeed());

        player.setInvulnerable(true);
        player.setWalkSpeed(0.0f);
        player.setFlySpeed(0.0f);
        player.setFallDistance(0.0f);
    }

    /**
     * Снимает все ограничения с игрока и восстанавливает сохранённые скорости.
     *
     * @param player игрок, с которого снимаются ограничения
     */
    public void unrestrict(Player player) {
        UUID uuid = player.getUniqueId();

        player.setInvulnerable(false);
        player.setWalkSpeed(savedWalkSpeed.getOrDefault(uuid, DEFAULT_WALK_SPEED));
        player.setFlySpeed(savedFlySpeed.getOrDefault(uuid, DEFAULT_FLY_SPEED));
        player.setFallDistance(0.0f);

        frozenLocations.remove(uuid);
        savedWalkSpeed.remove(uuid);
        savedFlySpeed.remove(uuid);
    }

    /**
     * Возвращает точку, на которой игрок был заморожен.
     *
     * @param uuid UUID игрока
     * @return клон локации заморозки или {@code null}, если игрок не ограничен
     */
    public Location getFrozenLocation(UUID uuid) {
        Location location = frozenLocations.get(uuid);
        return location == null ? null : location.clone();
    }

    /**
     * Проверяет, находится ли игрок под ограничениями.
     *
     * @param uuid UUID игрока
     * @return {@code true}, если игрок заморожен
     */
    public boolean isRestricted(UUID uuid) {
        return frozenLocations.containsKey(uuid);
    }

    /**
     * Очищает все сохранённые данные ограничений (при выключении плагина).
     */
    public void clear() {
        frozenLocations.clear();
        savedWalkSpeed.clear();
        savedFlySpeed.clear();
    }
}
