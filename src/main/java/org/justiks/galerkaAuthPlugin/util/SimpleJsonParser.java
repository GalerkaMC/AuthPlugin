package org.justiks.galerkaAuthPlugin.util;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Минимальный парсер плоских JSON-объектов со строковыми значениями.
 */
public final class SimpleJsonParser {

    private static final Pattern STRING_FIELD = Pattern.compile(
            "\"([^\"]+)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\""
    );

    private SimpleJsonParser() {
    }

    /**
     * Извлекает строковое значение поля из JSON-объекта.
     *
     * @param json тело JSON
     * @param key  имя поля
     * @return значение поля или пустой {@link Optional}
     */
    public static Optional<String> getString(String json, String key) {
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }

        Matcher matcher = STRING_FIELD.matcher(json);
        while (matcher.find()) {
            if (key.equals(matcher.group(1))) {
                return Optional.of(unescape(matcher.group(2)));
            }
        }

        return Optional.empty();
    }

    private static String unescape(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '\\' && index + 1 < value.length()) {
                char next = value.charAt(index + 1);
                switch (next) {
                    case '"' -> builder.append('"');
                    case '\\' -> builder.append('\\');
                    case 'n' -> builder.append('\n');
                    case 'r' -> builder.append('\r');
                    case 't' -> builder.append('\t');
                    default -> builder.append(next);
                }
                index++;
            } else {
                builder.append(character);
            }
        }
        return builder.toString();
    }
}
