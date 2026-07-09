package org.justiks.galerkaAuthPlugin.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Утилита для формирования простых JSON-строк без внешних зависимостей.
 */
public final class JsonUtils {

    /**
     * GSON builder instance
     */
    public static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .create();


    private JsonUtils() {
    }
}
