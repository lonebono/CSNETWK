package main.handlers;

import java.io.IOException;
import java.net.InetAddress;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import main.UDPSocketManager;
import main.utils.*;

public class PostHandler {
    private final UDPSocketManager socketManager;
    private final String currentUser;

    private static final long DEFAULT_TTL_SECONDS = 3600;

    public PostHandler(UDPSocketManager socketManager, String currentUser) {
        this.socketManager = socketManager;
        this.currentUser = currentUser;
        System.out.println("[DEBUG] PostHandler created for user: " + currentUser);
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

        System.out.println("[DEBUG] Sending broadcast message:");
        message.forEach((k, v) -> System.out.println("  " + k + ": " + v));

        VerboseLogger.send(message, "255.255.255.255");
        socketManager.sendMessage(MessageParser.serialize(message),
                InetAddress.getByName("255.255.255.255"),
                socketManager.getPort());
    }

    public void handle(Map<String, String> message, String fromIP) {
        VerboseLogger.recv(message, fromIP);

        if (!TokenValidator.validate(message, "broadcast")) {
            return;
        }

        if (!IPLogger.verifyIP(message.get("USER_ID"), fromIP)) {
            return;
        }

        String user = message.getOrDefault("USER_ID", "Unknown");
        String content = message.getOrDefault("CONTENT", "(no content)");
        TerminalDisplay.displayPost(user, content);
    }
}
