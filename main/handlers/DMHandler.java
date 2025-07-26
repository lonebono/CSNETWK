package main.handlers;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Map;
import main.UDPSocketManager;
import main.utils.TerminalDisplay;
import main.utils.VerboseLogger;

public class DMHandler {
    private final UDPSocketManager socketManager;
    private final String currentUser;

    public DMHandler(UDPSocketManager socketManager, String currentUser) {
        this.socketManager = socketManager;
        this.currentUser = currentUser;
    }

    public void send(String recipientId, String content, InetAddress recipientAddress) throws IOException {
        String message = "TYPE:DM\nFROM:" + currentUser + "\nTO:" + recipientId + "\nCONTENT:" + content;
        socketManager.sendMessage(message, recipientAddress, socketManager.getPort());
        VerboseLogger.log("DM sent to " + recipientId);
    }

    public void handle(Map<String, String> message) {
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
