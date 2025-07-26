package main.utils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class IPLogger {
    private static final Map<String, String> ipMap = new ConcurrentHashMap<>();

    public static boolean verifyIP(String userId, String actualIP) {
        if (userId == null || userId.isEmpty())
            return false;

        String knownIP = ipMap.get(userId);

        if (knownIP == null) {
            ipMap.put(userId, actualIP); // first time seen, trust and log
            VerboseLogger.log("Logged IP for user " + userId + ": " + actualIP);
            return true;
        }

        boolean matches = knownIP.equals(actualIP);
        if (!matches) {
            VerboseLogger.drop("IP mismatch for user " + userId + ": expected " + knownIP + ", got " + actualIP);
        }
        return matches;
    }
}
