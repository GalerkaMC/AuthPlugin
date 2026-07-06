package org.justiks.galerkaAuthPlugin.database;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.justiks.galerkaAuthPlugin.database.entity.UserEntity;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Менеджер подключения к базе данных SQLite через Hibernate ORM.
 * <p>
 * Предоставляет методы для выполнения операций в сессии и в транзакции.
 */
public final class DatabaseManager implements AutoCloseable {

    private final SessionFactory sessionFactory;

    /**
     * Инициализирует Hibernate {@link SessionFactory} для указанного файла SQLite.
     *
     * @param databaseFile файл базы данных SQLite
     */
    public DatabaseManager(File databaseFile) {
        Map<String, Object> settings = new HashMap<>();
        settings.put(AvailableSettings.JAKARTA_JDBC_URL, "jdbc:sqlite:" + databaseFile.getAbsolutePath());
        settings.put(AvailableSettings.JAKARTA_JDBC_DRIVER, "org.sqlite.JDBC");
        settings.put(AvailableSettings.DIALECT, "org.hibernate.community.dialect.SQLiteDialect");
        settings.put(AvailableSettings.HBM2DDL_AUTO, "update");
        settings.put(AvailableSettings.SHOW_SQL, false);

        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySettings(settings)
                .build();

        sessionFactory = new MetadataSources(registry)
                .addAnnotatedClass(UserEntity.class)
                .buildMetadata()
                .buildSessionFactory();
    }

    /**
     * Выполняет работу внутри транзакции Hibernate.
     * При ошибке выполняется откат транзакции.
     *
     * @param work функция, выполняемая в открытой сессии
     * @param <T>  тип возвращаемого значения
     * @return результат выполнения функции
     */
    public <T> T inTransaction(Function<Session, T> work) {
        try (Session session = sessionFactory.openSession()) {
            Transaction transaction = session.beginTransaction();
            try {
                T result = work.apply(session);
                transaction.commit();
                return result;
            } catch (RuntimeException exception) {
                transaction.rollback();
                throw exception;
            }
        }
    }

    /**
     * Выполняет работу в открытой сессии Hibernate без явной транзакции.
     *
     * @param work функция, выполняемая в сессии
     * @param <T>  тип возвращаемого значения
     * @return результат выполнения функции
     */
    public <T> T inSession(Function<Session, T> work) {
        try (Session session = sessionFactory.openSession()) {
            return work.apply(session);
        }
    }

    /**
     * Закрывает фабрику сессий Hibernate и освобождает ресурсы.
     */
    @Override
    public void close() {
        if (sessionFactory != null && !sessionFactory.isClosed()) {
            sessionFactory.close();
        }
    }
}
