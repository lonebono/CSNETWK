package main.utils;

import java.util.LinkedHashMap;
import java.util.Map;

public class MessageParser {
    public static Map<String, String> parse(String rawMessage) {
        Map<String, String> map = new LinkedHashMap<>();

        if (rawMessage == null || rawMessage.trim().isEmpty()) {
            VerboseLogger.log("Empty message received");
            return map;
        }

        String[] lines = rawMessage.split("\n");
        for (String line : lines) {
            parseLine(line, map);
        }

        return map;
    }

    private static void parseLine(String line, Map<String, String> map) {
        int colonIndex = line.indexOf(':');
        if (colonIndex == -1) {
            VerboseLogger.log("Invalid message line (missing colon): " + line);
            return;
        }
        
        String key = line.substring(0, colonIndex).trim().toUpperCase();
        String value = line.substring(colonIndex + 1).trim();
        
        if (key.isEmpty() || value.isEmpty()) {
            VerboseLogger.log("Empty key or value in message line: " + line);
            return;
        }
        
        map.put(key, value);
        VerboseLogger.log("Parsed message field: " + key + " = " + value);
    }

    public static String serialize(Map<String, String> message) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : message.entrySet()) {
            sb.append(entry.getKey())
              .append(":")
              .append(entry.getValue())
              .append("\n");
        }
        return sb.toString().trim();
    }
}
