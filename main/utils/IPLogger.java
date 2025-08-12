package main.utils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class IPLogger {
    private static final Map<String, String> ipMap = new ConcurrentHashMap<>();

    public static boolean verifyIP(String userId, String actualIP) {

        if (userId == null || userId.isEmpty()) {
            return false;
        }

        String normalizedActualIP = actualIP.trim();
        if (normalizedActualIP.startsWith("/")) {
            normalizedActualIP = normalizedActualIP.substring(1);
        }

        String knownIP = ipMap.get(userId);
        if (knownIP != null) {
            knownIP = knownIP.trim();
            if (knownIP.startsWith("/")) {
                knownIP = knownIP.substring(1);
            }
        }

        if (knownIP == null) {
            ipMap.put(userId, normalizedActualIP); // first time seen, trust and log
            VerboseLogger.log("Logged IP for user " + userId + ": " + normalizedActualIP);
            return true;
        }

        boolean matches = knownIP.equals(normalizedActualIP);
        if (!matches) {
            VerboseLogger.drop("IP mismatch for user " + userId + ": expected " + knownIP + ", got " + actualIP);
        }
        return matches;
    }
}
