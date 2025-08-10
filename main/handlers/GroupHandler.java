package main.handlers;

import java.util.*;
import java.util.stream.Collectors;
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
            VerboseLogger.log("GroupHandler: Missing TYPE field");
            return;
        }

        switch (type) {
            case "GROUP_CREATE" -> handleGroupCreate(msg, senderIP);
            case "GROUP_UPDATE" -> handleGroupUpdate(msg, senderIP);
            case "GROUP_MESSAGE" -> handleGroupMessage(msg, senderIP);
            default -> VerboseLogger.log("GroupHandler: Unknown message type " + type);
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

        List<String> members = membersStr == null ? new ArrayList<>()
                : Arrays.stream(membersStr.split(","))
                        .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());

        // Add creator userId to members if not included
        if (!members.contains(creatorUserId)) {
            members.add(creatorUserId);
        }

        boolean created = groupManager.createGroup(groupId, groupName, members, creatorUserId, timestamp);
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
        long timestamp;
        try {
            timestamp = Long.parseLong(msg.get("TIMESTAMP"));
        } catch (Exception e) {
            VerboseLogger.log("Invalid or missing TIMESTAMP on GROUP_UPDATE");
            return;
        }

        GroupStore.Group group = groupManager.getGroup(groupId);
        if (group == null) {
            VerboseLogger.log("GROUP_UPDATE received for non-existing group " + groupId);
            return;
        }

        // Only creator or members allowed to update (example rule)
        if (!group.getCreatorUserId().equals(fromUser)) {
            VerboseLogger.drop("User " + fromUser + " not authorized to update group " + groupId);
            return;
        }

        List<String> addList = parseUserList(msg.get("ADD"));
        List<String> removeList = parseUserList(msg.get("REMOVE"));

        boolean changed = groupManager.updateGroupMembers(groupId, addList, removeList);
        if (changed) {
            VerboseLogger.log("Group \"" + group.getGroupName() + "\" member list was updated.");
            if (group.isMember(currentUserId)) {
                System.out.println("Group \"" + group.getGroupName() + "\" updated. You are still a member.");
            } else {
                System.out.println("You have been removed from group \"" + group.getGroupName() + "\".");
            }
        } else {
            VerboseLogger.log("Group update received but no membership changes made.");
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
            VerboseLogger.drop("User " + fromUser + " is not member of group " + groupId + " - dropping GROUP_MESSAGE");
            return;
        }

        System.out.println(fromUser + " sent to group \"" + groupId + "\": " + content);
        VerboseLogger.log("GROUP_MESSAGE from " + fromUser + " to group " + groupId + ": " + content);
    }

    private List<String> parseUserList(String commaSeparated) {
        if (commaSeparated == null || commaSeparated.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(commaSeparated.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
