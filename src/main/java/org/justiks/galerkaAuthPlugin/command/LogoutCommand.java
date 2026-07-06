package org.justiks.galerkaAuthPlugin.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.justiks.galerkaAuthPlugin.GalerkaAuthPlugin;
import org.justiks.galerkaAuthPlugin.auth.AuthService;

import java.util.Collections;
import java.util.List;

/**
 * Обработчик команды {@code /logout} (выход из аккаунта).
 * <p>
 * Сбрасывает сессию в БД и применяет игровые ограничения.
 */
public final class LogoutCommand implements CommandExecutor, TabCompleter {

    private final GalerkaAuthPlugin plugin;
    private final AuthService authService;

    /**
     * Создаёт обработчик команды выхода.
     *
     * @param plugin      экземпляр плагина
     * @param authService сервис авторизации
     */
    public LogoutCommand(GalerkaAuthPlugin plugin, AuthService authService) {
        this.plugin = plugin;
        this.authService = authService;
    }

    /**
     * Обрабатывает команду выхода из аккаунта.
     * Сброс сессии в БД выполняется асинхронно.
     *
     * @param sender  отправитель команды
     * @param command объект команды
     * @param label   введённый алиас
     * @param args    аргументы команды
     * @return всегда {@code true}
     */
    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getPluginConfig().message("player-only"));
            return true;
        }

        if (!plugin.getAuthManager().isAuthenticated(player.getUniqueId())) {
            player.sendMessage(plugin.getPluginConfig().message("not-logged-in"));
            return true;
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            authService.clearSession(player.getUniqueId());

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                plugin.requireAuthentication(player);
                player.sendMessage(plugin.getPluginConfig().message("logout-success"));
            });
        });

        return true;
    }

    /**
     * Автодополнение не предоставляется.
     *
     * @param sender  отправитель команды
     * @param command объект команды
     * @param alias   введённый алиас
     * @param args    текущие аргументы
     * @return пустой список
     */
    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        return Collections.emptyList();
    }
}
