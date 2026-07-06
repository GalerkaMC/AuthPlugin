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
import org.justiks.galerkaAuthPlugin.util.PlayerIpResolver;

import java.util.Collections;
import java.util.List;

/**
 * Обработчик команды {@code /reg} (регистрация нового аккаунта).
 * <p>
 * Использование: {@code /reg <пароль> <повтор пароля>}
 */
public final class RegisterCommand implements CommandExecutor, TabCompleter {

    private final GalerkaAuthPlugin plugin;
    private final AuthService authService;

    /**
     * Создаёт обработчик команды регистрации.
     *
     * @param plugin      экземпляр плагина
     * @param authService сервис авторизации
     */
    public RegisterCommand(GalerkaAuthPlugin plugin, AuthService authService) {
        this.plugin = plugin;
        this.authService = authService;
    }

    /**
     * Обрабатывает команду регистрации.
     * Операция с БД выполняется асинхронно, результат — в главном потоке.
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

        if (plugin.getAuthManager().isAuthenticated(player.getUniqueId())) {
            player.sendMessage(plugin.getPluginConfig().message("already-logged-in"));
            return true;
        }

        if (args.length != 2) {
            player.sendMessage(plugin.getPluginConfig().message("register-usage"));
            return true;
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            AuthService.RegisterResult result = authService.register(
                    player.getName(),
                    player.getUniqueId(),
                    args[0],
                    args[1],
                    PlayerIpResolver.resolve(player)
            );

            plugin.getServer().getScheduler().runTask(plugin, () -> handleResult(player, result));
        });

        return true;
    }

    /**
     * Обрабатывает результат регистрации и отправляет соответствующее сообщение игроку.
     *
     * @param player игрок
     * @param result результат регистрации
     */
    private void handleResult(Player player, AuthService.RegisterResult result) {
        switch (result) {
            case SUCCESS -> {
                plugin.completeAuthentication(player);
                player.sendMessage(plugin.getPluginConfig().message("register-success"));
            }
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
            case ALREADY_REGISTERED -> player.sendMessage(plugin.getPluginConfig().message("already-registered"));
            case USERNAME_TAKEN -> player.sendMessage(plugin.getPluginConfig().message("username-taken"));
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
