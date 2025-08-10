package main.utils;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

    public class TokenValidator {
        private static final Set<String> revokedTokens = ConcurrentHashMap.newKeySet();

        public static String generate(String userId, long durationMillis, String scope) {
            long expiry = Instant.now().getEpochSecond() + durationMillis / 1000;
            return userId + "|" + expiry + "|" + scope;
        }

        public static boolean validate(Map<String, String> msg, String expectedScope) {
            String userId = msg.getOrDefault("FROM", msg.getOrDefault("USER_ID", null));
            String token = msg.get("TOKEN");

            if (userId == null || token == null) {
                VerboseLogger.token("UNKNOWN", false);
                return false;
            }

            try {
                String[] parts = token.split("\\|");
                if (parts.length != 3) {
                    VerboseLogger.token(userId, false);
                    return false;
                }

                String tokenUser = parts[0];
                long expiry = Long.parseLong(parts[1]);
                String tokenScope = parts[2];

                long now = Instant.now().getEpochSecond();

                boolean valid = now <= expiry
                        && tokenScope.equals(expectedScope)
                        && !revokedTokens.contains(token); // Check against revoked tokens

                VerboseLogger.token(userId, valid);

                return valid;
            } catch (Exception e) {
                VerboseLogger.token(userId, false);
                return false;
            }
        }

        public static void revoke(String token) {
            if (token != null && !token.isEmpty()) {
                revokedTokens.add(token);
                VerboseLogger.log("Token revoked: " + token);
            }
        }

        public static boolean isRevoked(String token) {
            return revokedTokens.contains(token);
        }
    }
    
