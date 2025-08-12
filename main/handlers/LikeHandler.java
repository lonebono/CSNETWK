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
import main.utils.TerminalDisplay;
import main.utils.TokenValidator;
import main.utils.VerboseLogger;

public class LikeHandler {
    private final UDPSocketManager socketManager;
    private final String currentUser;

    public LikeHandler(UDPSocketManager socketManager, String currentUser) {
        this.socketManager = socketManager;
        this.currentUser = currentUser;
        VerboseLogger.log("LikeHandler created for user: " + currentUser);
    }

    /**
     * Sends a LIKE message to broadcast, indicating a user likes a specific post.
     *
     * @param likedMessageId The MESSAGE_ID of the post being liked.
     * @throws IOException If an I/O error occurs while sending the message.
     */
    public void sendLike(String likedMessageId) throws IOException {
        long now = Instant.now().getEpochSecond();
        InetAddress localIP = InetAddress.getLocalHost();
        String userIdWithIP = currentUser + "@" + localIP.getHostAddress();

        // Token for 'broadcast' scope, similar to POST messages
        String token = TokenValidator.generate(currentUser, 3600_000L, "broadcast"); // Token valid for 1 hour

        Map<String, String> message = new HashMap<>();
        message.put("TYPE", "LIKE");
        message.put("FROM", userIdWithIP); // Using FROM as per DMHandler for sender identification
        message.put("MESSAGE_ID", UUID.randomUUID().toString().replace("-", "").substring(0, 16)); // Unique ID for the LIKE message itself
        message.put("LIKED_MESSAGE_ID", likedMessageId); // The ID of the message being liked
        message.put("TIMESTAMP", String.valueOf(now));
        message.put("TOKEN", token);

        VerboseLogger.send(message, "255.255.255.255");
        socketManager.sendMessage(MessageParser.serialize(message),
                InetAddress.getByName("255.255.255.255"),
                socketManager.getPort());
        VerboseLogger.log("Sent LIKE for message ID: " + likedMessageId);
    }

    /**
     * Handles an incoming LIKE message.
     *
     * @param message The parsed map of the incoming LIKE message.
     * @param fromIP The IP address of the sender.
     */
    public void handle(Map<String, String> message, String fromIP) {
        VerboseLogger.recv(message, fromIP);

        String senderId = message.get("FROM");
        String likedMessageId = message.get("LIKED_MESSAGE_ID");

        if (senderId == null || likedMessageId == null) {
            VerboseLogger.drop("Malformed LIKE message: missing FROM or LIKED_MESSAGE_ID");
            return;
        }

        // Validate token and IP as per other handlers
        if (!TokenValidator.validate(message, "broadcast")) {
            VerboseLogger.drop("Invalid or expired token for LIKE message from " + senderId);
            return;
        }

        // Extract user ID from senderId (e.g., "user@ip" -> "user")
        String user = senderId.split("@")[0];

        if (!IPLogger.verifyIP(senderId, fromIP)) {
            VerboseLogger.drop("IP mismatch for user " + senderId + " from " + fromIP + " for LIKE message");
            return;
        }

        // In a real application, you would store this like information
        // (e.g., in a database or in-memory map associated with the original post).
        // For this example, we'll just display it.
        TerminalDisplay.displayLikeNotification(user, likedMessageId);
        VerboseLogger.log("Received LIKE from " + user + " for message ID: " + likedMessageId);
    }
}
