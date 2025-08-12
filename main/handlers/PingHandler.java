package main.handlers;

import java.net.InetAddress;
import main.UDPSocketManager;

public class PingHandler {
    private final UDPSocketManager socketManager;
    private final String userId;

    public PingHandler(UDPSocketManager socketManager, String userId) {
        this.socketManager = socketManager;
        this.userId = userId;
    }

    public void broadcastPing() {
        try {
            InetAddress localIP = InetAddress.getLocalHost();
            String userIdWithIP = userId + "@" + localIP.getHostAddress();
            String message = "TYPE: PING\nUSER_ID: " + userIdWithIP;
            InetAddress broadcastAddress = InetAddress.getByName("255.255.255.255");
            socketManager.sendMessage(message, broadcastAddress, socketManager.getPort());
        } catch (Exception e) {
            System.err.println("[ERROR] Failed to send PING: " + e.getMessage());
        }
    }
}
