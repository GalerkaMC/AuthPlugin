package org.justiks.galerkaAuthPlugin.util;

/**
 * Утилита для формирования простых JSON-строк без внешних зависимостей.
 */
public final class JsonUtils {

    private JsonUtils() {
    }

    /**
     * Экранирует строку для использования в JSON.
     *
     * @param value исходная строка
     * @return экранированная строка без кавычек
     */
    public static String escape(String value) {
        if (value == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder(value.length() + 8);
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> builder.append(character);
            }
        }
        return builder.toString();
    }

    /**
     * Формирует JSON-объект с двумя строковыми полями.
     *
     * @param firstKey   ключ первого поля
     * @param firstValue значение первого поля
     * @param secondKey  ключ второго поля
     * @param secondValue значение второго поля
     * @return JSON-объект в виде строки
     */
    public static String object(
            String firstKey,
            String firstValue,
            String secondKey,
            String secondValue
    ) {
        return "{"
                + "\"" + escape(firstKey) + "\":\"" + escape(firstValue) + "\","
                + "\"" + escape(secondKey) + "\":\"" + escape(secondValue) + "\""
                + "}";
    }
}
