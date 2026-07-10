package org.justiks.galerkaAuthPlugin;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.justiks.galerkaAuthPlugin.api.RestApiContext;
import org.justiks.galerkaAuthPlugin.api.RestApiEndpoints;
import org.justiks.galerkaAuthPlugin.api.RestApiRouter;
import org.justiks.galerkaAuthPlugin.api.RestApiServer;
import org.justiks.galerkaAuthPlugin.auth.AuthManager;
import org.justiks.galerkaAuthPlugin.auth.AuthService;
import org.justiks.galerkaAuthPlugin.auth.PlayerRestrictionService;
import org.justiks.galerkaAuthPlugin.command.LogoutCommand;
import org.justiks.galerkaAuthPlugin.config.PluginConfig;
import org.justiks.galerkaAuthPlugin.database.DatabaseManager;
import org.justiks.galerkaAuthPlugin.listener.AuthListener;
import org.justiks.galerkaAuthPlugin.twofactor.AuthBotClient;
import org.justiks.galerkaAuthPlugin.twofactor.TwoFactorService;
import org.justiks.galerkaAuthPlugin.util.PlayerIpResolver;

import java.io.File;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Главный класс плагина авторизации GalerkaAuthPlugin.
 * <p>
 * Инициализирует конфигурацию, базу данных SQLite через Hibernate,
 * REST API, интеграцию с AuthBot и регистрирует команды и слушатели событий.
 */
public final class GalerkaAuthPlugin extends JavaPlugin {

    private PluginConfig pluginConfig;
    private DatabaseManager databaseManager;
    private AuthManager authManager;
    private AuthService authService;
    private PlayerRestrictionService restrictionService;
    private TwoFactorService twoFactorService;
    private RestApiServer restApiServer;

    /**
     * Вызывается при включении плагина.
     * Загружает конфигурацию, подключается к БД и регистрирует команды и слушатели.
     */
    @Override
    public void onEnable() {
        pluginConfig = new PluginConfig(this);
        pluginConfig.load();

        File databaseFile = new File(getDataFolder(), "auth.db");
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().severe("Не удалось создать папку плагина.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            databaseManager = new DatabaseManager(databaseFile);
        } catch (Exception exception) {
            getLogger().severe("Не удалось подключиться к базе данных: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        authManager = new AuthManager();
        authService = new AuthService(databaseManager, pluginConfig);
        restrictionService = new PlayerRestrictionService();
        twoFactorService = new TwoFactorService(pluginConfig, new AuthBotClient(pluginConfig, getLogger()));

        if (!startRestApi()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        registerCommands();
        getServer().getPluginManager().registerEvents(new AuthListener(this), this);

        getLogger().info("GalerkaAuthPlugin успешно включён.");
    }

    /**
     * Вызывается при выключении плагина.
     * Очищает сессии, снимает ограничения с игроков и закрывает соединение с БД.
     */
    @Override
    public void onDisable() {
        if (restApiServer != null) {
            restApiServer.close();
            restApiServer = null;
        }

        if (authManager != null) {
            authManager.clear();
        }

        if (restrictionService != null) {
            restrictionService.clear();
        }

        if (databaseManager != null) {
            databaseManager.close();
        }

        getLogger().info("GalerkaAuthPlugin выключен.");
    }

    /**
     * Завершает авторизацию игрока: помечает как вошедшего и снимает игровые ограничения.
     *
     * @param player игрок, прошедший авторизацию
     */
    public void completeAuthentication(Player player) {
        authManager.authenticate(player.getUniqueId());
        restrictionService.unrestrict(player);
    }

    /**
     * Переводит игрока в состояние ожидания подтверждения 2FA.
     * Игрок остаётся замороженным до вызова {@link #completeTwoFactorAuthentication(UUID)}.
     *
     * @param player игрок, ожидающий подтверждения 2FA
     */
    public void beginTwoFactorAuthentication(Player player) {
        authManager.setPendingTwoFactor(player.getUniqueId(), PlayerIpResolver.resolve(player));
        restrictionService.restrict(player);
    }

    /**
     * Завершает двухфакторную аутентификацию и снимает ограничения с игрока.
     * <p>
     * Предназначен для вызова из REST callback AuthBot после подтверждения входа в Telegram.
     *
     * @param uuid UUID игрока
     * @return {@code true}, если игрок был в ожидании 2FA и успешно авторизован
     */
    public boolean completeTwoFactorAuthentication(UUID uuid) {
        if (!authManager.isPendingTwoFactor(uuid)) {
            return false;
        }

        String ip = authManager.getPendingTwoFactorIp(uuid);
        authService.finalizeLogin(uuid, ip);
        authManager.authenticate(uuid);

        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            restrictionService.unrestrict(player);
            player.sendMessage(pluginConfig.message("two-factor-success"));
        }

        return true;
    }

    /**
     * Отклоняет 2FA и снимает состояние ожидания подтверждения.
     * Игрок остаётся замороженным и должен повторить вход.
     *
     * @param uuid UUID игрока
     */
    public void rejectTwoFactorAuthentication(UUID uuid) {
        if (!authManager.isPendingTwoFactor(uuid)) {
            return;
        }

        authManager.clearPendingTwoFactor(uuid);

        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            player.sendMessage(pluginConfig.message("two-factor-denied"));
        }
    }

    /**
     * Требует авторизацию от игрока: сбрасывает сессию и применяет игровые ограничения.
     *
     * @param player игрок, которому требуется авторизация
     */
    public void requireAuthentication(Player player) {
        authManager.unauthenticate(player.getUniqueId());
        restrictionService.restrict(player);
    }

    /**
     * Запускает встроенный REST API в отдельном пуле потоков.
     *
     * @return {@code false}, если сервер не удалось запустить
     */
    private boolean startRestApi() {
        if (!pluginConfig.isRestApiEnabled()) {
            getLogger().info("REST API отключён в конфигурации.");
            return true;
        }

        try {
            RestApiContext context = new RestApiContext(this);
            RestApiRouter router = new RestApiRouter(context, getLogger());
            RestApiEndpoints.registerDefaults(this, router);
            restApiServer = new RestApiServer(
                    pluginConfig.getRestApiHost(),
                    pluginConfig.getRestApiPort(),
                    router,
                    getLogger()
            );
            restApiServer.start();
            return true;
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "Не удалось запустить REST API", exception);
            return false;
        }
    }

    /**
     * Регистрирует все команды плагина в Bukkit.
     */
    private void registerCommands() {
        LogoutCommand logoutCommand = new LogoutCommand(this, authService);
        register("logout", logoutCommand);
    }

    /**
     * Привязывает исполнителя команды к записи в {@code plugin.yml}.
     *
     * @param name     имя команды
     * @param executor обработчик команды
     */
    private void register(String name, org.bukkit.command.CommandExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning("Команда /" + name + " не найдена в plugin.yml");
            return;
        }

        command.setExecutor(executor);
        if (executor instanceof org.bukkit.command.TabCompleter tabCompleter) {
            command.setTabCompleter(tabCompleter);
        }
    }

    /**
     * @return конфигурация плагина
     */
    public PluginConfig getPluginConfig() {
        return pluginConfig;
    }

    /**
     * @return менеджер сессий авторизации в памяти
     */
    public AuthManager getAuthManager() {
        return authManager;
    }

    /**
     * @return сервис бизнес-логики авторизации
     */
    public AuthService getAuthService() {
        return authService;
    }

    /**
     * @return сервис ограничений для неавторизованных игроков
     */
    public PlayerRestrictionService getRestrictionService() {
        return restrictionService;
    }

    /**
     * @return сервис двухфакторной аутентификации
     */
    public TwoFactorService getTwoFactorService() {
        return twoFactorService;
    }
}
