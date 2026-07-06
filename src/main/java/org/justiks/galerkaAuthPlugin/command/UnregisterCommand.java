package org.justiks.galerkaAuthPlugin.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.justiks.galerkaAuthPlugin.GalerkaAuthPlugin;
import org.justiks.galerkaAuthPlugin.auth.AuthService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Административная команда {@code /unregister} (удаление аккаунта игрока).
 * <p>
 * Использование: {@code /unregister <ник>}
 * Требует право {@code galerkaauth.admin.unregister}.
 */
public final class UnregisterCommand implements CommandExecutor, TabCompleter {

    private final GalerkaAuthPlugin plugin;
    private final AuthService authService;

    /**
     * Создаёт обработчик команды удаления аккаунта.
     *
     * @param plugin      экземпляр плагина
     * @param authService сервис авторизации
     */
    public UnregisterCommand(GalerkaAuthPlugin plugin, AuthService authService) {
        this.plugin = plugin;
        this.authService = authService;
    }

    /**
     * Обрабатывает команду удаления аккаунта.
     * Если цель онлайн — сбрасывает её авторизацию и применяет ограничения.
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
        if (!sender.hasPermission("galerkaauth.admin.unregister")) {
            sender.sendMessage(plugin.getPluginConfig().message("no-permission"));
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage(plugin.getPluginConfig().message("unregister-usage"));
            return true;
        }

        String targetName = args[0];

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            AuthService.UnregisterResult result = authService.unregister(targetName);

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (result == AuthService.UnregisterResult.NOT_FOUND) {
                    sender.sendMessage(plugin.getPluginConfig().message(
                            "unregister-not-found",
                            "{player}",
                            targetName
                    ));
                    return;
                }

                sender.sendMessage(plugin.getPluginConfig().message(
                        "unregister-success",
                        "{player}",
                        targetName
                ));

                Player onlineTarget = Bukkit.getPlayerExact(targetName);
                if (onlineTarget != null) {
                    plugin.requireAuthentication(onlineTarget);
                    onlineTarget.sendMessage(plugin.getPluginConfig().message("account-deleted"));
                }
            });
        });

        return true;
    }

    /**
     * Предлагает ники онлайн-игроков для автодополнения.
     *
     * @param sender  отправитель команды
     * @param command объект команды
     * @param alias   введённый алиас
     * @param args    текущие аргументы
     * @return список подходящих ников или пустой список
     */
    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        if (!sender.hasPermission("galerkaauth.admin.unregister") || args.length != 1) {
            return Collections.emptyList();
        }

        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> suggestions = new ArrayList<>();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                suggestions.add(player.getName());
            }
        }

        return suggestions;
    }
}
