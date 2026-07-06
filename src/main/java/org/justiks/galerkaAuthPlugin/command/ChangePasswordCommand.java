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
 * Обработчик команды {@code /changepassword} (смена пароля).
 * <p>
 * Использование: {@code /changepassword <старый> <новый> <повтор>}
 */
public final class ChangePasswordCommand implements CommandExecutor, TabCompleter {

    private final GalerkaAuthPlugin plugin;
    private final AuthService authService;

    /**
     * Создаёт обработчик команды смены пароля.
     *
     * @param plugin      экземпляр плагина
     * @param authService сервис авторизации
     */
    public ChangePasswordCommand(GalerkaAuthPlugin plugin, AuthService authService) {
        this.plugin = plugin;
        this.authService = authService;
    }

    /**
     * Обрабатывает команду смены пароля.
     * Доступна только авторизованным игрокам. Операция с БД — асинхронно.
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
            player.sendMessage(plugin.getPluginConfig().message("must-login"));
            return true;
        }

        if (args.length != 3) {
            player.sendMessage(plugin.getPluginConfig().message("changepassword-usage"));
            return true;
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            AuthService.ChangePasswordResult result = authService.changePassword(
                    player.getUniqueId(),
                    args[0],
                    args[1],
                    args[2]
            );

            plugin.getServer().getScheduler().runTask(plugin, () -> handleResult(player, result));
        });

        return true;
    }

    /**
     * Обрабатывает результат смены пароля и отправляет соответствующее сообщение игроку.
     *
     * @param player игрок
     * @param result результат смены пароля
     */
    private void handleResult(Player player, AuthService.ChangePasswordResult result) {
        switch (result) {
            case SUCCESS -> player.sendMessage(plugin.getPluginConfig().message("changepassword-success"));
            case NOT_REGISTERED -> player.sendMessage(plugin.getPluginConfig().message("not-registered"));
            case PASSWORDS_MISMATCH -> player.sendMessage(plugin.getPluginConfig().message("passwords-mismatch"));
            case PASSWORD_TOO_SHORT -> player.sendMessage(plugin.getPluginConfig().message(
                    "password-too-short",
                    "{min}",
                    String.valueOf(plugin.getPluginConfig().getMinPasswordLength())
            ));
            case PASSWORD_TOO_LONG -> player.sendMessage(plugin.getPluginConfig().message(
                    "password-too-long",
                    "{max}",
                    String.valueOf(plugin.getPluginConfig().getMaxPasswordLength())
            ));
            case WRONG_PASSWORD -> player.sendMessage(plugin.getPluginConfig().message("wrong-old-password"));
        }
    }

    /**
     * Автодополнение не предоставляется (пароли не должны подсказываться).
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
