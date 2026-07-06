package org.justiks.galerkaAuthPlugin.command;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.justiks.galerkaAuthPlugin.GalerkaAuthPlugin;
import org.justiks.galerkaAuthPlugin.auth.AuthService;
import org.justiks.galerkaAuthPlugin.database.entity.UserEntity;
import org.justiks.galerkaAuthPlugin.twofactor.TwoFactorService;
import org.justiks.galerkaAuthPlugin.util.PlayerIpResolver;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Обработчик команды {@code /login} (вход в аккаунт).
 * <p>
 * Использование: {@code /login <пароль>}
 * При включённой 2FA после проверки пароля отправляет запрос в AuthBot
 * и оставляет игрока замороженным до подтверждения в Telegram.
 */
public final class LoginCommand implements CommandExecutor, TabCompleter {

    private final GalerkaAuthPlugin plugin;
    private final AuthService authService;
    private final TwoFactorService twoFactorService;

    /**
     * Создаёт обработчик команды входа.
     *
     * @param plugin           экземпляр плагина
     * @param authService      сервис авторизации
     * @param twoFactorService сервис 2FA
     */
    public LoginCommand(
            GalerkaAuthPlugin plugin,
            AuthService authService,
            TwoFactorService twoFactorService
    ) {
        this.plugin = plugin;
        this.authService = authService;
        this.twoFactorService = twoFactorService;
    }

    /**
     * Обрабатывает команду входа.
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

        if (plugin.getAuthManager().isPendingTwoFactor(player.getUniqueId())) {
            player.sendMessage(plugin.getPluginConfig().message("two-factor-pending"));
            return true;
        }

        if (args.length != 1) {
            player.sendMessage(plugin.getPluginConfig().message("login-usage"));
            return true;
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            AuthService.LoginResult result = authService.login(
                    player.getName(),
                    player.getUniqueId(),
                    args[0]
            );

            if (result != AuthService.LoginResult.SUCCESS) {
                plugin.getServer().getScheduler().runTask(plugin, () -> handleResult(player, result));
                return;
            }

            Optional<UserEntity> userOptional = authService.findByUuid(player.getUniqueId());
            if (userOptional.isEmpty()) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        player.sendMessage(plugin.getPluginConfig().message("not-registered"))
                );
                return;
            }

            UserEntity user = userOptional.get();
            if (!twoFactorService.requiresTwoFactor(user)) {
                plugin.getServer().getScheduler().runTask(plugin, () -> completeLogin(player));
                return;
            }

            boolean requestSent = twoFactorService.requestConfirmation(user, player.getName());
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }

                if (requestSent) {
                    plugin.beginTwoFactorAuthentication(player);
                    player.sendMessage(plugin.getPluginConfig().message("two-factor-pending"));
                } else {
                    player.sendMessage(plugin.getPluginConfig().message("two-factor-request-failed"));
                }
            });
        });

        return true;
    }

    /**
     * Завершает вход без 2FA: создаёт сессию и снимает ограничения.
     *
     * @param player игрок
     */
    private void completeLogin(Player player) {
        authService.finalizeLogin(player.getUniqueId(), PlayerIpResolver.resolve(player));
        plugin.completeAuthentication(player);
        player.sendMessage(plugin.getPluginConfig().message("login-success"));
    }

    /**
     * Обрабатывает неуспешный результат проверки пароля.
     *
     * @param player игрок
     * @param result результат входа
     */
    private void handleResult(Player player, AuthService.LoginResult result) {
        switch (result) {
            case SUCCESS -> completeLogin(player);
            case NOT_REGISTERED -> player.sendMessage(plugin.getPluginConfig().message("not-registered"));
            case WRONG_PASSWORD -> handleWrongPassword(player);
            case WRONG_ACCOUNT -> player.sendMessage(plugin.getPluginConfig().message("wrong-account"));
        }
    }

    /**
     * Обрабатывает неверный пароль: увеличивает счётчик попыток и кикает при превышении лимита.
     *
     * @param player игрок
     */
    private void handleWrongPassword(Player player) {
        int attempts = plugin.getAuthManager().incrementLoginAttempts(player.getUniqueId());
        int maxAttempts = plugin.getPluginConfig().getMaxLoginAttempts();

        if (attempts >= maxAttempts) {
            player.kick(LegacyComponentSerializer.legacySection().deserialize(
                    plugin.getPluginConfig().message("too-many-attempts")
            ));
            return;
        }

        player.sendMessage(plugin.getPluginConfig().message(
                "wrong-password",
                "{attempts}",
                String.valueOf(attempts),
                "{max}",
                String.valueOf(maxAttempts)
        ));
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
