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
        long timestamp;
        try {
            timestamp = Long.parseLong(msg.get("TIMESTAMP"));
        } catch (Exception e) {
            VerboseLogger.log("Invalid or missing TIMESTAMP on GROUP_CREATE");
            return;
        }

        List<String> members = new ArrayList<>();
        if (membersStr != null && !membersStr.isEmpty()) {
            for (String member : membersStr.split(",")) {
                member = member.trim();
                if (member.isEmpty())
                    continue;

                // Append sender IP if missing '@'
                if (!member.contains("@")) {
                    member = member + "@" + senderIP;
                }
                members.add(member);
            }
        }

        if (!creatorUserId.contains("@")) {
            creatorUserId = creatorUserId + "@" + senderIP;
        }
        if (!members.contains(creatorUserId)) {
            members.add(creatorUserId);
        }

        boolean created = groupManager.createGroup(groupId, groupName, members, creatorUserId, timestamp);
        TerminalDisplay.displayGroupCreate(groupName);
        if (created) {
            VerboseLogger.log("Group created: " + groupName + " (" + groupId + ")");
            if (members.contains(currentUserId)) {
                System.out.println("You’ve been added to " + groupName);
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
        TerminalDisplay.displayGroupUpdate(groupId, changed);
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

    public void sendGroupUpdate(String groupId, List<String> addMembers, List<String> removeMembers) {
        GroupStore.Group group = groupManager.getGroup(groupId);
        if (group == null) {
            System.err.println("Group " + groupId + " does not exist.");
            return;
        }

        // Update local group membership first
        boolean changed = groupManager.updateGroupMembers(groupId, addMembers, removeMembers);
        if (!changed) {
            System.out.println("No changes in group membership.");
            return;
        }

        // Prepare GROUP_UPDATE message
        Map<String, String> msg = new LinkedHashMap<>();
        msg.put("TYPE", "GROUP_UPDATE");
        msg.put("FROM", currentUserId);
        msg.put("GROUP_ID", groupId);
        if (addMembers != null && !addMembers.isEmpty())
            msg.put("ADD", String.join(",", addMembers));
        if (removeMembers != null && !removeMembers.isEmpty())
            msg.put("REMOVE", String.join(",", removeMembers));
        msg.put("TIMESTAMP", String.valueOf(System.currentTimeMillis() / 1000L));
        msg.put("TOKEN", TokenValidator.generate(currentUserId, 3600_000, "group"));

        String serialized = MessageParser.serialize(msg);

        // Send to all current members (after update)
        for (String member : group.getMembers()) {
            if (member.equals(currentUserId))
                continue; // skip self

            try {
                String userIp = member.split("@")[1];
                InetAddress addr = InetAddress.getByName(userIp);
                socketManager.sendMessage(serialized, addr, socketManager.getPort());
            } catch (Exception e) {
                VerboseLogger.log("Failed to send GROUP_UPDATE to " + member + ": " + e.getMessage());
            }
        }

        System.out.println("Group update sent for " + group.getGroupName());
    }
}
