package main.handlers;

import java.io.IOException;
import java.net.InetAddress;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import main.UDPSocketManager;
import main.utils.TerminalDisplay;
import main.utils.TokenValidator;
import main.utils.VerboseLogger;

public class DMHandler {
    private final UDPSocketManager socketManager;
    private final String currentUser;

    public DMHandler(UDPSocketManager socketManager, String currentUser) {
        this.socketManager = socketManager;
        this.currentUser = currentUser;
    }

    public void send(String recipientId, String content, InetAddress recipientAddress) throws IOException {
        String messageId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        long timestamp = Instant.now().getEpochSecond();
        String token = TokenValidator.generate(currentUser, 3600_000L, "chat");

        String message = String.join("\n",
                "TYPE:DM",
                "FROM:" + currentUser,
                "TO:" + recipientId,
                "CONTENT:" + content,
                "TIMESTAMP:" + timestamp,
                "MESSAGE_ID:" + messageId,
                "TOKEN:" + token);

        socketManager.sendMessage(message, recipientAddress, socketManager.getPort());
        VerboseLogger.log("DM sent to " + recipientId);
    }

    public void handle(Map<String, String> message) {
        message.forEach((k, v) -> System.out.println(k + ": " + v));

        String from = message.get("FROM");
        String to = message.get("TO");
        String content = message.get("CONTENT");

        if (to.equals(currentUser)) {
            TerminalDisplay.displayDM(from, content);
            VerboseLogger.log("DM received from " + from);
        } else {
            VerboseLogger.drop("DM intended for " + to + " received by " + currentUser);
        }
    }
}
