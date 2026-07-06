package org.justiks.galerkaAuthPlugin.api;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * Встроенный HTTP-сервер REST API плагина.
 * <p>
 * Работает в отдельном пуле потоков и принимает обратные вызовы от микросервисов
 * (например, подтверждение 2FA от AuthBot).
 */
public final class RestApiServer implements AutoCloseable {

    private final HttpServer server;
    private final ExecutorService executor;
    private final Logger logger;

    /**
     * Создаёт и настраивает HTTP-сервер.
     *
     * @param host   адрес привязки (например, {@code 127.0.0.1})
     * @param port   порт
     * @param router маршрутизатор эндпоинтов
     * @param logger логгер
     * @throws IOException если сервер не удалось запустить
     */
    public RestApiServer(String host, int port, RestApiRouter router, Logger logger) throws IOException {
        this.logger = logger;
        this.server = HttpServer.create(new InetSocketAddress(host, port), 0);
        this.server.createContext("/", router::handle);
        this.executor = Executors.newCachedThreadPool(thread -> {
            Thread worker = new Thread(thread, "GalerkaAuth-RestApi");
            worker.setDaemon(true);
            return worker;
        });
        this.server.setExecutor(executor);
    }

    /**
     * Запускает HTTP-сервер.
     */
    public void start() {
        server.start();
        logger.info("REST API запущен на " + server.getAddress().getHostString()
                + ":" + server.getAddress().getPort());
    }

    /**
     * Останавливает HTTP-сервер и завершает пул потоков.
     */
    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
        logger.info("REST API остановлен.");
    }
}
