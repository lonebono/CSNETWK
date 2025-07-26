package main.handlers;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Map;
import main.UDPSocketManager;
import main.utils.TerminalDisplay;
import main.utils.VerboseLogger;

public class PostHandler {
    private final UDPSocketManager socketManager;
    private final String currentUser;

    public PostHandler(UDPSocketManager socketManager, String currentUser) {
        this.socketManager = socketManager;
        this.currentUser = currentUser;
    }

    public void send(String content, InetAddress recipient) throws IOException {
        String message = "TYPE:POST\nFROM:" + currentUser + "\nCONTENT:" + content;
        socketManager.sendMessage(message, recipient, socketManager.getPort());
        VerboseLogger.log("Post sent to " + recipient.getHostAddress());
    }

    public void broadcast(String content) throws IOException {
        send(content, InetAddress.getByName("255.255.255.255"));
    }

    public void handle(Map<String, String> message) {
        String from = message.get("FROM");
        String content = message.get("CONTENT");
        TerminalDisplay.displayPost(from, content);
        VerboseLogger.log("Post received from " + from);
    }
}
