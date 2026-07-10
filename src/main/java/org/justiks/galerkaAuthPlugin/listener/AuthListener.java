package org.justiks.galerkaAuthPlugin.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.justiks.galerkaAuthPlugin.GalerkaAuthPlugin;
import org.justiks.galerkaAuthPlugin.util.PlayerIpResolver;

/**
 * Слушатель событий, обеспечивающий защиту неавторизованных игроков.
 * <p>
 * Блокирует движение, взаимодействие, урон, чат и команды до прохождения авторизации.
 * При входе на сервер пытается выполнить авто-логин по IP-сессии.
 */
public final class AuthListener implements Listener {

    private final GalerkaAuthPlugin plugin;

    /**
     * Создаёт слушатель событий авторизации.
     *
     * @param plugin экземпляр плагина
     */
    public AuthListener(GalerkaAuthPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Обрабатывает вход игрока: применяет ограничения и пытается авто-логин.
     *
     * @param event событие входа игрока
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        plugin.requireAuthentication(player);

        String ip = PlayerIpResolver.resolve(player);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean autoLoggedIn = plugin.getAuthService().tryAutoLogin(player.getUniqueId(), ip);
            boolean registered = autoLoggedIn || plugin.getAuthService().isRegistered(player.getUniqueId());

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }

                if (autoLoggedIn) {
                    plugin.completeAuthentication(player);
                    player.sendMessage(plugin.getPluginConfig().message("auto-login-success"));
                    return;
                }

                if (registered) {
                    player.sendMessage(plugin.getPluginConfig().message("two-factor-pending"));
                    plugin.getTwoFactorService().requestConfirmation(
                            plugin.getAuthService().findByUsername(player.getName()).get(),
                            player.getName()
                    );
                }
            });
        });
    }

    /**
     * Обрабатывает выход игрока: сбрасывает авторизацию и снимает ограничения.
     *
     * @param event событие выхода игрока
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getAuthManager().unauthenticate(player.getUniqueId());
        plugin.getRestrictionService().unrestrict(player);
    }

    /**
     * Блокирует перемещение неавторизованного игрока и возвращает на точку заморозки.
     *
     * @param event событие перемещения
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!shouldRestrict(event.getPlayer())) {
            return;
        }

        if (!hasMoved(event)) {
            return;
        }

        event.setCancelled(true);
        teleportToFrozenLocation(event.getPlayer());
    }

    /**
     * Дополнительная проверка перемещения на этапе MONITOR для надёжной телепортации.
     *
     * @param event событие перемещения
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMoveMonitor(PlayerMoveEvent event) {
        if (!shouldRestrict(event.getPlayer()) || !hasMoved(event)) {
            return;
        }

        teleportToFrozenLocation(event.getPlayer());
    }

    /**
     * Блокирует чат неавторизованного игрока.
     *
     * @param event событие чата
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (shouldRestrict(event.getPlayer())) {
            event.setCancelled(true);
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    event.getPlayer().sendMessage(plugin.getPluginConfig().message("must-auth"))
            );
        }
    }

    /**
     * Разрешает только команды авторизации до входа в аккаунт.
     *
     * @param event событие ввода команды
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!shouldRestrict(event.getPlayer())) {
            return;
        }

        String command = event.getMessage().substring(1).split("\\s+")[0].toLowerCase();
        if (plugin.getPluginConfig().getAllowedCommands().contains(command)) {
            return;
        }

        event.setCancelled(true);
        event.getPlayer().sendMessage(plugin.getPluginConfig().message("must-auth"));
    }

    /**
     * Блокирует взаимодействие с блоками и предметами.
     *
     * @param event событие взаимодействия
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (shouldRestrict(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /**
     * Блокирует выбрасывание предметов.
     *
     * @param event событие выбрасывания предмета
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (shouldRestrict(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /**
     * Блокирует смену предметов между руками.
     *
     * @param event событие смены предметов в руках
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        if (shouldRestrict(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /**
     * Блокирует переключение режима полёта.
     *
     * @param event событие переключения полёта
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        if (shouldRestrict(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /**
     * Блокирует ломание блоков.
     *
     * @param event событие ломания блока
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (shouldRestrict(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /**
     * Блокирует начало разрушения блока (трещины на блоке).
     *
     * @param event событие повреждения блока
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        if (event.getPlayer() != null && shouldRestrict(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /**
     * Блокирует установку блоков.
     *
     * @param event событие установки блока
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (shouldRestrict(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /**
     * Блокирует получение урона неавторизованным игроком.
     *
     * @param event событие урона
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && shouldRestrict(player)) {
            event.setCancelled(true);
        }
    }

    /**
     * Блокирует урон от и к неавторизованным игрокам.
     *
     * @param event событие урона от сущности
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player player && shouldRestrict(player)) {
            event.setCancelled(true);
            return;
        }

        if (event.getDamager() instanceof Player player && shouldRestrict(player)) {
            event.setCancelled(true);
        }
    }

    /**
     * Блокирует открытие инвентарей (сундуки, верстаки и т.д.).
     *
     * @param event событие открытия инвентаря
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && shouldRestrict(player)) {
            event.setCancelled(true);
        }
    }

    /**
     * Определяет, нужно ли применять ограничения к игроку.
     *
     * @param player проверяемый игрок
     * @return {@code true}, если игрок не авторизован и не имеет права обхода
     */
    private boolean shouldRestrict(Player player) {
        return !player.hasPermission("galerkaauth.bypass")
                && !plugin.getAuthManager().isAuthenticated(player.getUniqueId());
    }

    /**
     * Проверяет, изменилась ли позиция игрока (игнорирует поворот головы).
     *
     * @param event событие перемещения
     * @return {@code true}, если игрок сместился хотя бы на один блок
     */
    private boolean hasMoved(PlayerMoveEvent event) {
        return event.getFrom().getBlockX() != event.getTo().getBlockX()
                || event.getFrom().getBlockY() != event.getTo().getBlockY()
                || event.getFrom().getBlockZ() != event.getTo().getBlockZ();
    }

    /**
     * Телепортирует игрока обратно на точку заморозки.
     * Если точка не сохранена — повторно применяет ограничения.
     *
     * @param player игрок для телепортации
     */
    private void teleportToFrozenLocation(Player player) {
        Location frozenLocation = plugin.getRestrictionService().getFrozenLocation(player.getUniqueId());
        if (frozenLocation == null) {
            plugin.getRestrictionService().restrict(player);
            frozenLocation = plugin.getRestrictionService().getFrozenLocation(player.getUniqueId());
        }

        if (frozenLocation != null) {
            player.teleport(frozenLocation);
        }

        player.setFallDistance(0.0f);
    }
}
