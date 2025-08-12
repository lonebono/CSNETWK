package main.handlers;

import java.io.IOException;
import java.net.InetAddress;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import main.UDPSocketManager;
import main.utils.IPLogger;
import main.utils.MessageParser;
import main.utils.TokenValidator;
import main.utils.VerboseLogger;

public class RevokeHandler {
    private final UDPSocketManager socketManager;
    private final String currentUser;

    public RevokeHandler(UDPSocketManager socketManager, String currentUser) {
        this.socketManager = socketManager;
        this.currentUser = currentUser;
        VerboseLogger.log("[DEBUG] RevokeHandler created for user: " + currentUser);
    }

    /**
     * Sends a REVOKE message to broadcast, invalidating a specific token.
     *
     * @param tokenToRevoke The exact token string to be revoked.
     * @throws IOException If an I/O error occurs while sending the message.
     */
    public void sendRevoke(String tokenToRevoke) throws IOException {
        long now = Instant.now().getEpochSecond();
        InetAddress localIP = InetAddress.getLocalHost();
        String userIdWithIP = currentUser + "@" + localIP.getHostAddress();

        String revokeToken = TokenValidator.generate(currentUser, 3600_000L, "revoke"); // Valid for 1 hour

        Map<String, String> message = new HashMap<>();
        message.put("TYPE", "REVOKE");
        message.put("FROM", userIdWithIP);
        message.put("MESSAGE_ID", UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        message.put("TOKEN_TO_REVOKE", tokenToRevoke);
        message.put("TIMESTAMP", String.valueOf(now));
        message.put("TOKEN", revokeToken);

        VerboseLogger.send(message, "255.255.255.255");
        socketManager.sendMessage(MessageParser.serialize(message),
                InetAddress.getByName("255.255.255.255"),
                socketManager.getPort());
        VerboseLogger.log("Sent REVOKE for token: " + tokenToRevoke);
    }

    /**
     * Handles an incoming REVOKE message.
     *
     * @param message The parsed map of the incoming REVOKE message.
     * @param fromIP The IP address of the sender.
     */
    public void handle(Map<String, String> message, String fromIP) {
        VerboseLogger.recv(message, fromIP);

        String senderId = message.get("FROM");
        String tokenToRevoke = message.get("TOKEN_TO_REVOKE");

        if (senderId == null || tokenToRevoke == null) {
            VerboseLogger.drop("Malformed REVOKE message: missing FROM or TOKEN_TO_REVOKE");
            return;
        }

        if (!TokenValidator.validate(message, "revoke")) {
            VerboseLogger.drop("Invalid or expired token for REVOKE message from " + senderId);
            return;
        }

        if (!IPLogger.verifyIP(senderId, fromIP)) {
            VerboseLogger.drop("IP mismatch for user " + senderId + " from " + fromIP + " for REVOKE message");
            return;
        }

        try {
            String[] tokenParts = tokenToRevoke.split("\\|");
            if (tokenParts.length < 1 || !tokenParts[0].equals(senderId.split("@")[0])) {
                VerboseLogger.drop("REVOKE message sender does not match token owner: " + senderId + " vs " + tokenParts[0]);
                return;
            }
        } catch (Exception e) {
            VerboseLogger.drop("Invalid TOKEN_TO_REVOKE format: " + tokenToRevoke);
            return;
        }


        TokenValidator.revoke(tokenToRevoke);
        VerboseLogger.log("Successfully processed REVOKE for token: " + tokenToRevoke + " from " + senderId);
    }
}
