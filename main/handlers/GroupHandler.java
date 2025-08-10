package main.handlers;

import java.net.InetAddress;
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
}
