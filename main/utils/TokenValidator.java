package main.utils;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TokenValidator {
    private static final Set<String> revokedTokens = ConcurrentHashMap.newKeySet();

    // Generate token: format "userId:expiryTimestamp"
    public static String generate(String userId, long durationMillis) {
        long expiry = Instant.now().toEpochMilli() + durationMillis;
        return userId + ":" + expiry;
    }

    public static boolean validate(Map<String, String> msg) {
        String userId = msg.getOrDefault("FROM", msg.getOrDefault("USER_ID", null));
        String token = msg.get("TOKEN");

        if (userId == null || token == null) {
            VerboseLogger.token("UNKNOWN", false);
            return false;
        }

        try {
            String[] parts = token.split(":");
            if (parts.length != 2) {
                VerboseLogger.token(userId, false);
                return false;
            }

            long expiry = Long.parseLong(parts[1]);
            long now = Instant.now().toEpochMilli();

            if (revokedTokens.contains(token)) {
                if (now > expiry) {
                    revokedTokens.remove(token);
                } else {
                    VerboseLogger.token(userId, false);
                    return false;
                }
            }

            boolean valid = now <= expiry;
            VerboseLogger.token(userId, valid);
            return valid;
        } catch (Exception e) {
            VerboseLogger.token(userId, false);
            return false;
        }
    }

    public static void revoke(String token) {
        if (token != null)
            revokedTokens.add(token);
    }
}
