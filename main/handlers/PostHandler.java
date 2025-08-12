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

public class PostHandler {
    private final UDPSocketManager socketManager;
    private final String currentUser;
    private final FollowHandler followHandler;

    private static final long DEFAULT_TTL_SECONDS = 3600;

    public PostHandler(UDPSocketManager socketManager, String currentUser, FollowHandler followHandler) {
        this.socketManager = socketManager;
        this.currentUser = currentUser;
        this.followHandler = followHandler;
    }

    public void broadcast(String content) throws IOException {
        broadcast(content, DEFAULT_TTL_SECONDS);
    }

    public void broadcast(String content, long ttlSeconds) throws IOException {
        long now = Instant.now().getEpochSecond();
        InetAddress localIP = InetAddress.getLocalHost();
        String userIdWithIP = currentUser + "@" + localIP.getHostAddress();

        String token = userIdWithIP + "|" + (now + ttlSeconds) + "|broadcast";

        Map<String, String> message = new HashMap<>();
        message.put("TYPE", "POST");
        message.put("USER_ID", userIdWithIP);
        message.put("CONTENT", content);
        message.put("TTL", String.valueOf(ttlSeconds));
        message.put("MESSAGE_ID", UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        message.put("TOKEN", token);

        if (VerboseLogger.isEnabled()) {
            System.out.println("[DEBUG] Sending post to followed peers:");
            message.forEach((k, v) -> System.out.println("  " + k + ": " + v));
        }

        // Send only to followed peers
        for (FollowHandler.FollowedPeer peer : followHandler.getFollowedPeers()) {
            socketManager.sendMessage(
                MessageParser.serialize(message),
                InetAddress.getByName(peer.ip),
                peer.port
            );
        }
    }

    public void handle(Map<String, String> message, String fromIP) {
        VerboseLogger.recv(message, fromIP);

        if (!TokenValidator.validate(message, "broadcast")) {
            return;
        }

        if (!IPLogger.verifyIP(message.get("USER_ID"), fromIP)) {
            return;
        }

        String senderUserId = message.getOrDefault("USER_ID", "Unknown");

        // Only display if sender is followed
        boolean isFollowed = followHandler.getFollowedPeers().stream()
                .anyMatch(peer -> peer.userId.equals(senderUserId));

        if (!isFollowed) {
            if (VerboseLogger.isEnabled()) {
                System.out.println("[DEBUG] Ignoring post from non-followed user: " + senderUserId);
            }
            return;
        }

        String content = message.getOrDefault("CONTENT", "(no content)");
        TerminalDisplay.displayPost(senderUserId, content);
    }
}
