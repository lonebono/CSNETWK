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

    public void send(String recipientId, String content, InetAddress recipientAddress, int recipientPort)
            throws IOException {
        String messageId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        long timestamp = Instant.now().getEpochSecond();
        String token = TokenValidator.generate(currentUser, 3600_000L, "chat");

        InetAddress localAddress = InetAddress.getLocalHost();
        String localIp = localAddress.getHostAddress();

        String fromField = currentUser + "@" + localIp;

        String message = String.join("\n",
                "TYPE:DM",
                "FROM:" + fromField,
                "TO:" + recipientId,
                "CONTENT:" + content,
                "TIMESTAMP:" + timestamp,
                "MESSAGE_ID:" + messageId,
                "TOKEN:" + token);

        socketManager.sendMessage(message, recipientAddress, recipientPort);
        VerboseLogger
                .log("DM sent to " + recipientId + " at " + recipientAddress.getHostAddress() + ":" + recipientPort);
    }

    public void handle(Map<String, String> message) {
        message.forEach((k, v) -> System.out.println(k + ": " + v));

        String from = message.get("FROM");
        String to = message.get("TO");
        String content = message.get("CONTENT");

        String toUser = (to != null && to.contains("@")) ? to.split("@")[0] : to;

        if (toUser.equals(currentUser)) {
            String senderUser = (from != null && from.contains("@")) ? from.split("@")[0] : from;
            TerminalDisplay.displayDM(senderUser, content);
            VerboseLogger.log("DM received from " + from);

            String messageId = message.get("MESSAGE_ID");
            if (messageId != null && from.contains("@")) {
                String[] parts = from.split("@");
                String senderIp = parts[1];
                try {
                    InetAddress senderAddress = InetAddress.getByName(senderIp);
                    sendAck(messageId, senderAddress, 50999); // Replace with actual sender port if needed
                } catch (IOException e) {
                    VerboseLogger.log("Failed to send ACK to " + senderIp);
                }
            }
        } else {
            VerboseLogger.drop("DM intended for " + to + " received by " + currentUser);
        }
    }

    private void sendAck(String messageId, InetAddress recipientAddress, int recipientPort) throws IOException {
        String ack = String.join("\n",
                "TYPE:ACK",
                "MESSAGE_ID:" + messageId,
                "STATUS:RECEIVED");

        socketManager.sendMessage(ack, recipientAddress, recipientPort);
        VerboseLogger.log("Sent ACK for message ID: " + messageId + " to " + recipientAddress.getHostAddress());
    }

    public void handleAck(Map<String, String> message) {
        String messageId = message.get("MESSAGE_ID");
        String status = message.get("STATUS");

        if ("RECEIVED".equalsIgnoreCase(status)) {
            VerboseLogger.log("ACK received for DM with ID: " + messageId);
            // TODO: Mark message as acknowledged if retry logic is added
        }
    }
}
