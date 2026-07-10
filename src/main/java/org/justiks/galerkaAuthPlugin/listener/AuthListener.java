package org.justiks.galerkaAuthPlugin.listener;

import com.google.common.collect.ImmutableList;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.connection.PlayerConnection;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.justiks.galerkaAuthPlugin.GalerkaAuthPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class AuthListener implements Listener {
    private final Map<String, Thread> threadsMap = new HashMap<>();
    private static final GalerkaAuthPlugin plugin = GalerkaAuthPlugin.getPlugin(GalerkaAuthPlugin.class);
    private static final int MAX_AUTH_TIME_SECONDS = 30;
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthListener.class);
    private static final Component CANCELLED = Component.text("Авторизация отменена.", NamedTextColor.RED);
    private static final Component LOITERING_KICK = Component.text("Время истекло.", NamedTextColor.RED);
    private static final Component ERROR = Component.text("Ошибк.", NamedTextColor.RED);
    private static final Dialog[] REMAINING_TIME_DIALOGS = new Dialog[MAX_AUTH_TIME_SECONDS];

    static {
        final DialogActionCallback callback = (_, audience) -> {
            audience.closeDialog();
            if (!(audience instanceof PlayerConnection connection)) return;
            connection.disconnect(CANCELLED);
        };
        final DialogType type = DialogType.notice(ActionButton.builder(Component.text("Отключиться",
                NamedTextColor.WHITE)).action(DialogAction.customClick(callback, ClickCallback.Options.builder()
                .uses(ClickCallback.UNLIMITED_USES).lifetime(Duration.ofSeconds(Long.MAX_VALUE)).build())).build());
        final Component title = Component.text("Авторизация", NamedTextColor.WHITE);
        final Component messagePrefix = Component.text("Подтвердите вход через бота в ", NamedTextColor.WHITE)
                .append(Component.text("Telegram", NamedTextColor.AQUA))
                .append(Component.text(". Осталось времени: ", NamedTextColor.WHITE));
        final Component messageSuffix = Component.text(" сек.", NamedTextColor.WHITE);
        for (int seconds = 0; seconds < MAX_AUTH_TIME_SECONDS; seconds++) {
            final Component messageInfix = Component.text((seconds + 1), NamedTextColor.AQUA);
            REMAINING_TIME_DIALOGS[seconds] = Dialog.create(builder -> builder.empty()
                    .type(type)
                    .base(DialogBase.builder(title)
                            .body(ImmutableList.of(DialogBody.plainMessage(messagePrefix
                                    .append(messageInfix)
                                    .append(messageSuffix))))
                            .build()
                    )
            );
        }
    }

    @EventHandler(ignoreCancelled = true)
    private void onConfigure(final AsyncPlayerConnectionConfigureEvent event) {
        final PlayerConfigurationConnection connection = event.getConnection();
        final Audience audience = connection.getAudience();
        try {
            this.beginAuthentication(connection, Thread.currentThread());
            try {
                for (int seconds = MAX_AUTH_TIME_SECONDS; seconds >= 0; seconds--) {
                    Thread.sleep(1000L);
                    if (!connection.isConnected()) break;
                    if (seconds == 0) {
                        audience.closeDialog();
                        connection.disconnect(LOITERING_KICK);
                        break;
                    }
                    final Dialog dialog = REMAINING_TIME_DIALOGS[seconds - 1];
                    audience.showDialog(dialog);
                }
            } catch (final InterruptedException ignored) {
                this.authenticationSuccess(connection);
                return;
            }
            this.authenticationFailure(connection);
        } catch (final Throwable t) {
            LOGGER.error("Unable to authenticate the player: {}", connection, t);
            audience.closeDialog();
            connection.disconnect(ERROR);
            throw new RuntimeException("Unable to authenticate the player: " + connection, t);
        }
    }

    private void beginAuthentication(final PlayerConfigurationConnection connection, final Thread thread) {
        threadsMap.put(connection.getProfile().getName(), thread);

        String ip = connection.getAddress().toString();
        boolean autoLoggedIn = plugin.getAuthService().tryAutoLogin(connection.getProfile().getName(), ip);

        if (autoLoggedIn) {
            authenticationSuccess(connection);
            return;
        }

        plugin.getTwoFactorService().requestConfirmation(
                plugin.getAuthService().findByUsername(connection.getProfile().getName()).get(),
                connection.getProfile().getName()
        );
        plugin.getAuthManager().setPendingTwoFactor(
                connection.getProfile().getName(),
                ip
        );
    }

    private void authenticationSuccess(final PlayerConfigurationConnection connection) {
        threadsMap.remove(connection.getProfile().getName());
    }

    private void authenticationFailure(final PlayerConfigurationConnection connection) {
        threadsMap.remove(connection.getProfile().getName());
        Component desc = Component.text("Ты не успел авторизоваться за указанное время!");
        connection.disconnect(desc);
    }

    public void interruptThreadByNickname(String nickname) {
        Thread thread = threadsMap.get(nickname);
        if (thread == null) {
            throw new NullPointerException("");
        }
        thread.interrupt();
    }
}
