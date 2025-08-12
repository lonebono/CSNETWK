package main.handlers;

import java.net.InetAddress;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import main.UDPSocketManager;
import main.utils.IPLogger;
import main.utils.MessageParser;
import main.utils.TokenValidator;
import main.utils.VerboseLogger;

public class FollowHandler {
    private final UDPSocketManager socketManager;
    private final String currentUser;
    private final List<FollowedPeer> followedPeers = new CopyOnWriteArrayList<>();

    public FollowHandler(UDPSocketManager socketManager, String currentUser) {
        this.socketManager = socketManager;
        this.currentUser = currentUser;
    }

    public static class FollowedPeer {
        public final String userId; // username@ip
        public final String ip;
        public final int port;

        public FollowedPeer(String userId, String ip, int port) {
            this.userId = userId;
            this.ip = ip;
            this.port = port;
        }
    }

    public List<FollowedPeer> getFollowedPeers() {
        return Collections.unmodifiableList(followedPeers);
    }

    public void follow(String targetUserId, String targetIp, int targetPort, long ttlSeconds) throws Exception {
        long now = Instant.now().getEpochSecond();

        String localUserId = currentUser + "@" + InetAddress.getLocalHost().getHostAddress();
        String token = localUserId + "|" + (now + ttlSeconds) + "|follow";

        Map<String, String> msg = new LinkedHashMap<>();
        msg.put("TYPE", "FOLLOW");
        msg.put("MESSAGE_ID", UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        msg.put("FROM", localUserId);
        msg.put("TO", targetUserId);
        msg.put("TIMESTAMP", String.valueOf(now));
        msg.put("TOKEN", token);
        // include our listening port so receiver can know where to send posts (optional)
        msg.put("PORT", String.valueOf(socketManager.getPort()));

        // send message
        VerboseLogger.send(msg, targetIp);
        socketManager.sendMessage(MessageParser.serialize(msg), InetAddress.getByName(targetIp), targetPort);

        // update local followed list (if already present, replace)
        followedPeers.removeIf(p -> p.userId.equals(targetUserId));
        followedPeers.add(new FollowedPeer(targetUserId, targetIp, targetPort));
        System.out.println("You are now following " + targetUserId + " (" + targetIp + ":" + targetPort + ")");
    }

    public void unfollow(String targetUserId) throws Exception {
        FollowedPeer peer = null;
        for (FollowedPeer p : followedPeers) {
            if (p.userId.equals(targetUserId)) {
                peer = p;
                break;
            }
        }

        if (peer == null) {
            System.out.println("You are not following " + targetUserId);
            return;
        }

        long now = Instant.now().getEpochSecond();
        String localUserId = currentUser + "@" + InetAddress.getLocalHost().getHostAddress();
        String token = localUserId + "|" + (now + 3600) + "|follow";

        Map<String, String> msg = new LinkedHashMap<>();
        msg.put("TYPE", "UNFOLLOW");
        msg.put("MESSAGE_ID", UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        msg.put("FROM", localUserId);
        msg.put("TO", targetUserId);
        msg.put("TIMESTAMP", String.valueOf(now));
        msg.put("TOKEN", token);

        VerboseLogger.send(msg, peer.ip);
        socketManager.sendMessage(MessageParser.serialize(msg), InetAddress.getByName(peer.ip), peer.port);

        followedPeers.remove(peer);
        System.out.println("You have unfollowed " + targetUserId);
    }

    public void handle(Map<String, String> message, String fromIP) {
        VerboseLogger.recv(message, fromIP);

        if (!TokenValidator.validate(message, "follow")) {
            return;
        }

        String type = message.get("TYPE");
        String fromUser = message.get("FROM");
        String toUser = message.get("TO");

        // verify IP matches the FROM field (security check from spec)
        if (!IPLogger.verifyIP(fromUser, fromIP)) {
            return;
        }

        if ("FOLLOW".equals(type)) {
            System.out.println("User " + fromUser + " has followed you");

            int port = socketManager.getPort(); // default fallback
            if (message.containsKey("PORT")) {
                try {
                    port = Integer.parseInt(message.get("PORT"));
                } catch (NumberFormatException ignored) {}
            }

            // If follower already exists, update ip/port; otherwise add
            followedPeers.removeIf(p -> p.userId.equals(fromUser));
            followedPeers.add(new FollowedPeer(fromUser, fromIP, port));
        }
        else if ("UNFOLLOW".equals(type)) {
            System.out.println("User " + fromUser + " has unfollowed you");
            followedPeers.removeIf(p -> p.userId.equals(fromUser));
        }
    }
}
