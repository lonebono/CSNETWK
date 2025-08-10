package main.handlers;

import java.net.InetAddress;
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
        long timestamp = parseTimestamp(msg.get("TIMESTAMP"));

        List<String> members = parseMembers(membersStr);

        // Add creator to members if missing
        if (!members.contains(creatorUserId)) {
            members.add(creatorUserId);
        }

        boolean created = groupManager.createGroup(groupId, groupName, members, creatorUserId, timestamp);
        if (created) {
            VerboseLogger.log("Group created: " + groupName + " (" + groupId + ")");
            if (members.contains(currentUserId)) {
                TerminalDisplay.displayGroupCreate(groupName);
            }
        } else {
            VerboseLogger.log("Group creation failed: groupId exists " + groupId);
        }
    }

    private void handleGroupUpdate(Map<String, String> msg, String senderIP) {
        if (!TokenValidator.validate(msg, "group")) {
            VerboseLogger.token(msg.getOrDefault("FROM", "UNKNOWN"), false);
            VerboseLogger.drop("Invalid token on GROUP_UPDATE");
            return;
        }

        String groupId = msg.get("GROUP_ID");
        String fromUser = msg.get("FROM");
        long timestamp = parseTimestamp(msg.get("TIMESTAMP"));

        GroupStore.Group group = groupManager.getGroup(groupId);
        if (group == null) {
            VerboseLogger.log("GROUP_UPDATE for non-existing group " + groupId);
            return;
        }

        // Only creator or members allowed to update (example rule)
        if (!group.getCreatorUserId().equals(fromUser)) {
            VerboseLogger.drop("User " + fromUser + " unauthorized to update group " + groupId);
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
        String token = currentUserId + "|" + (timestamp + 3600) + "|group";

        for (String member : group.getMembers()) {
            // Skip sending to self (optional)
            if (member.equals(currentUserId))
                continue;

            StringBuilder sb = new StringBuilder();
            sb.append("TYPE: GROUP_MESSAGE\n");
            sb.append("FROM: ").append(currentUserId).append("\n");
            sb.append("GROUP_ID: ").append(groupId).append("\n");
            sb.append("CONTENT: ").append(content).append("\n");
            sb.append("TIMESTAMP: ").append(timestamp).append("\n");
            sb.append("TOKEN: ").append(token).append("\n\n");

            try {
                // Extract IP from userId (expected format: username@ip)
                String userIp = member.split("@")[1];
                InetAddress addr = InetAddress.getByName(userIp);
                int port = socketManager.getPort(); // Or your logic to get destination port

                socketManager.sendMessage(sb.toString(), addr, port);
                VerboseLogger.log("Sent GROUP_MESSAGE to " + member);
            } catch (Exception e) {
                VerboseLogger.log("Failed to send GROUP_MESSAGE to " + member + ": " + e.getMessage());
            }
        }
    }

}
