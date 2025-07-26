package main.utils;

import java.util.LinkedHashMap;
import java.util.Map;

public class MessageParser {
    public static Map<String, String> parse(String rawMessage) {
        Map<String, String> map = new LinkedHashMap<>();

        if (rawMessage == null || rawMessage.trim().isEmpty())
            return map;

        String[] lines = rawMessage.split("\n");

        for (String line : lines) {
            int colonIndex = line.indexOf(':');
            if (colonIndex == -1)
                continue;
            String key = line.substring(0, colonIndex).trim().toUpperCase();
            String value = line.substring(colonIndex + 1).trim();
            map.put(key, value);
        }

        return map;
    }
}
