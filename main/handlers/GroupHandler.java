package main.handlers;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.*;
import main.UDPSocketManager;
import main.data.GroupStore;
import main.utils.*;

public class GroupHandler {
    private final UDPSocketManager socketManager;
    private final GroupManager groupManager;
    private final String currentUserId;

    public GroupHandler(UDPSocketManager socketManager, GroupManager groupManager, String currentUserId) {
        this.socketManager = socketManager;
        this.groupManager = groupManager;
        this.currentUserId = currentUserId;
    }

    public void handle(Map<String, String> msg, String senderIP) {
        String type = msg.get("TYPE");
        if (type == null) {
            VerboseLogger.log("GroupHandler: Missing TYPE");
            return;
        }

        switch (type) {
            case "GROUP_CREATE" -> handleGroupCreate(msg, senderIP);
            case "GROUP_UPDATE" -> handleGroupUpdate(msg, senderIP);
            case "GROUP_MESSAGE" -> handleGroupMessage(msg, senderIP);
            default -> VerboseLogger.log("GroupHandler: Unknown type " + type);
        }
    }

    private void handleGroupCreate(Map<String, String> msg, String senderIP) {
        if (!TokenValidator.validate(msg, "group")) {
            VerboseLogger.token(msg.getOrDefault("FROM", "UNKNOWN"), false);
            VerboseLogger.drop("Invalid token on GROUP_CREATE");
            return;
        }

        String groupId = msg.get("GROUP_ID");
        String groupName = msg.get("GROUP_NAME");
        String membersStr = msg.get("MEMBERS");
        String creatorUserId = msg.get("FROM");
        long timestamp;
        try {
            timestamp = Long.parseLong(msg.get("TIMESTAMP"));
        } catch (Exception e) {
            VerboseLogger.log("Invalid or missing TIMESTAMP on GROUP_CREATE");
            return;
        }

        Map<String, InetSocketAddress> members = parseMembersWithPorts(membersStr);

        if (!creatorUserId.contains("@")) {
            // If no port provided for creator, append default port, e.g., 50999
            creatorUserId = creatorUserId + "@" + senderIP + ":" + socketManager.getPort();
        }
        if (!members.containsKey(creatorUserId.split("@")[0])) {
            String user = creatorUserId.split("@")[0];
            String[] ipPort = creatorUserId.split("@")[1].split(":");
            InetSocketAddress creatorAddr = new InetSocketAddress(ipPort[0], Integer.parseInt(ipPort[1]));
            members.put(user, creatorAddr);
        }

        boolean created = groupManager.createGroup(groupId, groupName, members, creatorUserId.split("@")[0], timestamp);
        if (created) {
            VerboseLogger.log("Group created: " + groupName + " (" + groupId + ")");
            if (members.containsKey(currentUserId.split("@")[0])) {
                TerminalDisplay.displayGroupCreate(groupName);
            }
        } else {
            VerboseLogger.log("Group creation failed: group ID already exists: " + groupId);
        }
    }

    private void handleGroupUpdate(Map<String, String> msg, String senderIP) {
        if (!TokenValidator.validate(msg, "group")) {
            VerboseLogger.token(msg.getOrDefault("FROM", "UNKNOWN"), false);
            VerboseLogger.drop("Invalid token on GROUP_UPDATE");
            return;
        }

        String groupId = msg.get("GROUP_ID");
        String fromUserId = msg.get("FROM").split("@")[0]; // just userId
        long timestamp = parseTimestamp(msg.get("TIMESTAMP"));

        GroupStore.Group group = groupManager.getGroup(groupId);
        if (group == null) {
            VerboseLogger.log("GROUP_UPDATE for non-existing group " + groupId);
            return;
        }

        // Only creator or members allowed to update (example rule)
        if (!group.getCreatorUserId().equals(fromUserId)) {
            VerboseLogger.drop("User " + fromUserId + " unauthorized to update group " + groupId);
            return;
        }

        List<String> addList = parseMembers(msg.get("ADD"));
        List<String> removeList = parseMembers(msg.get("REMOVE"));

        boolean changed = groupManager.updateGroupMembers(groupId, addList, removeList);
        if (changed) {
            VerboseLogger.log("Group \"" + group.getGroupName() + "\" member list updated.");
            TerminalDisplay.displayGroupUpdate(group.getGroupName(), group.isMember(currentUserId));
        } else {
            VerboseLogger.log("Group update received but no changes.");
        }
    }

    private void handleGroupMessage(Map<String, String> msg, String senderIP) {
        if (!TokenValidator.validate(msg, "group")) {
            VerboseLogger.token(msg.getOrDefault("FROM", "UNKNOWN"), false);
            VerboseLogger.drop("Invalid token on GROUP_MESSAGE");
            return;
        }

        String groupId = msg.get("GROUP_ID");
        String fromUser = msg.get("FROM");
        String content = msg.get("CONTENT");

        if (!groupManager.isUserMember(groupId, fromUser)) {
            VerboseLogger.drop("User " + fromUser + " not a member of group " + groupId);
            return;
        }

        TerminalDisplay.displayGroupMessage(fromUser, groupId, content);
        VerboseLogger.log("GROUP_MESSAGE from " + fromUser + " to group " + groupId + ": " + content);
    }

    private List<String> parseMembers(String membersStr) {
        if (membersStr == null || membersStr.isBlank()) {
            return new ArrayList<>();
        }
        String[] parts = membersStr.split(",");
        List<String> list = new ArrayList<>();
        for (String s : parts) {
            String trimmed = s.trim();
            if (!trimmed.isEmpty()) {
                list.add(trimmed);
            }
        }
        return list;
    }

    private long parseTimestamp(String tsStr) {
        try {
            return Long.parseLong(tsStr);
        } catch (Exception e) {
            VerboseLogger.log("Invalid timestamp: " + tsStr);
            return System.currentTimeMillis() / 1000L;
        }
    }

    public void sendGroupMessage(String groupId, String content) {
        GroupStore.Group group = groupManager.getGroup(groupId);
        if (group == null) {
            System.err.println("Group " + groupId + " does not exist.");
            return;
        }

        long timestamp = System.currentTimeMillis() / 1000;
        String token = TokenValidator.generate(currentUserId, 3600, "group");

        Map<String, InetSocketAddress> membersWithPorts = group.getMembersWithPorts();
        for (Map.Entry<String, InetSocketAddress> entry : membersWithPorts.entrySet()) {
            String memberId = entry.getKey();
            InetSocketAddress addr = entry.getValue();

            if (memberId.equals(currentUserId))
                continue; // skip self

            StringBuilder sb = new StringBuilder();
            sb.append("TYPE: GROUP_MESSAGE\n");
            sb.append("FROM: ").append(currentUserId).append("\n");
            sb.append("GROUP_ID: ").append(groupId).append("\n");
            sb.append("CONTENT: ").append(content).append("\n");
            sb.append("TIMESTAMP: ").append(timestamp).append("\n");
            sb.append("TOKEN: ").append(token).append("\n\n");

            try {
                socketManager.sendMessage(sb.toString(), addr.getAddress(), addr.getPort());
                VerboseLogger.log("Sent GROUP_MESSAGE to " + memberId + " at " + addr);
            } catch (Exception e) {
                VerboseLogger.log("Failed to send GROUP_MESSAGE to " + memberId + ": " + e.getMessage());
            }
        }
    }

    public void sendGroupUpdate(String groupId, List<String> addMembers, List<String> removeMembers) {
        GroupStore.Group group = groupManager.getGroup(groupId);
        if (group == null) {
            System.err.println("Group " + groupId + " does not exist.");
            return;
        }

        boolean changed = groupManager.updateGroupMembers(groupId, addMembers, removeMembers);
        if (!changed) {
            System.out.println("No changes in group membership.");
            return;
        }

        Map<String, String> msg = new LinkedHashMap<>();
        msg.put("TYPE", "GROUP_UPDATE");
        msg.put("FROM", currentUserId);
        msg.put("GROUP_ID", groupId);
        if (addMembers != null && !addMembers.isEmpty())
            msg.put("ADD", String.join(",", addMembers));
        if (removeMembers != null && !removeMembers.isEmpty())
            msg.put("REMOVE", String.join(",", removeMembers));
        msg.put("TIMESTAMP", String.valueOf(System.currentTimeMillis() / 1000L));
        msg.put("TOKEN", TokenValidator.generate(currentUserId, 3600, "group"));

        String serialized = MessageParser.serialize(msg);

        Map<String, InetSocketAddress> membersWithPorts = group.getMembersWithPorts();
        for (Map.Entry<String, InetSocketAddress> entry : membersWithPorts.entrySet()) {
            String memberId = entry.getKey();
            InetSocketAddress addr = entry.getValue();

            if (memberId.equals(currentUserId))
                continue; // skip self

            try {
                socketManager.sendMessage(serialized, addr.getAddress(), addr.getPort());
                VerboseLogger.log("Sent GROUP_UPDATE to " + memberId + " at " + addr);
            } catch (Exception e) {
                VerboseLogger.log("Failed to send GROUP_UPDATE to " + memberId + ": " + e.getMessage());
            }
        }
        TerminalDisplay.displayGroupUpdate(groupId, changed);
    }

    private Map<String, InetSocketAddress> parseMembersWithPorts(String membersStr) {
        Map<String, InetSocketAddress> membersMap = new LinkedHashMap<>();
        if (membersStr == null || membersStr.isBlank())
            return membersMap;

        for (String member : membersStr.split(",")) {
            member = member.trim();
            if (member.isEmpty())
                continue;

            // Expecting format user@ip:port
            try {
                String[] userAndAddr = member.split("@");
                if (userAndAddr.length != 2)
                    continue; // invalid format

                String userId = userAndAddr[0];
                String[] ipPort = userAndAddr[1].split(":");
                if (ipPort.length != 2)
                    continue; // invalid format

                String ip = ipPort[0];
                int port = Integer.parseInt(ipPort[1]);

                InetSocketAddress addr = new InetSocketAddress(ip, port);
                membersMap.put(userId, addr);
            } catch (Exception e) {
                VerboseLogger.log("Failed to parse member: " + member + " - " + e.getMessage());
            }
        }
        return membersMap;
    }
}
