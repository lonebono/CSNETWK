package main.handlers;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import main.UDPSocketManager;
import main.utils.MessageParser;
import main.utils.VerboseLogger;


public class ProfileHandler {

    private final UDPSocketManager socketManager;
    private final String username;
    private final String displayName;
    private String status;
    private String avatarType;      
    private String avatarEncoding;  
    private String avatarData;      

    private final Map<String, Map<String, String>> knownProfiles = new HashMap<>();

    public ProfileHandler(UDPSocketManager socketManager, String username, String displayName, String status) {
        this.socketManager = socketManager;
        this.username = username;
        this.displayName = displayName;
        this.status = status;
    }

    public void broadcastProfile() {
    try {
        String localIp = getLocalIp();

        Map<String, String> profileMsg = new LinkedHashMap<>();
        profileMsg.put("STATUS", status);
        profileMsg.put("USER_ID", username + "@" + localIp);
        profileMsg.put("TYPE", "PROFILE");
        profileMsg.put("DISPLAY_NAME", username);

        String serialized = MessageParser.serialize(profileMsg);
        InetAddress broadcastAddress = InetAddress.getByName("192.168.100.255"); // or dynamic
        socketManager.sendMessage(serialized, broadcastAddress, 50999);

        /*if (VerboseLogger.isEnabled()) {
            System.out.println("[DEBUG] Sending PROFILE to " + broadcastAddress.getHostAddress() + ":50999");
            System.out.println("[DEBUG] PROFILE message:\n" + serialized);
        }*/
    } catch (SocketException e) {
        System.err.println("[ERROR] Could not get local IP: " + e.getMessage());
    } catch (IOException e) {
        System.err.println("[ERROR] Could not send PROFILE: " + e.getMessage());
    }
}


    public void handle(Map<String, String> parsed, String fromIP) {
        String userId = parsed.get("USER_ID");
        if (userId == null) return;

        if (userId.startsWith(username + "@")) {
            return;
        }

        knownProfiles.put(userId, new HashMap<>(parsed));

        VerboseLogger.recv(parsed, fromIP);

        String dispName = parsed.getOrDefault("DISPLAY_NAME", userId);
        String userStatus = parsed.getOrDefault("STATUS", "");
    }

    public Map<String, Map<String, String>> getKnownProfiles() {
        return knownProfiles;
    }

    public void updateStatus(String newStatus) {
        this.status = newStatus;
    }

    public void setAvatar(String type, String encoding, String data) {
        this.avatarType = type;
        this.avatarEncoding = encoding;
        this.avatarData = data;
    }

    private String getLocalIp() throws SocketException {
        for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
            for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                    return addr.getHostAddress();
                }
            }
        }
        return "127.0.0.1"; // fallback
    }

    private InetAddress getBroadcastAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                    continue;
                }
                // fully-qualified java.net.InterfaceAddress avoids name conflicts
                for (java.net.InterfaceAddress ifaceAddress : networkInterface.getInterfaceAddresses()) {
                    InetAddress broadcast = ifaceAddress.getBroadcast();
                    if (broadcast != null) {
                        return broadcast;
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        return null;
    }
}
