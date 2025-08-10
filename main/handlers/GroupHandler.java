package main.handlers;

import java.util.*;
import main.utils.*;
import main.UDPSocketManager;

public class GroupHandler {
    public void handleMessage(String rawMessage, String fromIP) {
        Map<String, String> fields = new MessageParser().parse(rawMessage);
        if (fields.isEmpty() || !fields.containsKey("TYPE")) {
            VerboseLogger.drop("Missing or empty TYPE field");
            return;
        }
        VerboseLogger.recv(fields, fromIP);
        String type = fields.get("TYPE").toUpperCase(Locale.ROOT);

        switch (type) {
            case "GROUP_CREATE":
                handleGroupCreate(fields);
                break;
            case "GROUP_UPDATE":
                handleGroupUpdate(fields);
                break;
            case "GROUP_MESSAGE":
                handleGroupMessage(fields);
                break;
            default:
                VerboseLogger.log("Unknown group message TYPE: " + type);
        }
    }

    private void handleGroupCreate(Map<String, String> fields) {
        String fromUser = fields.get("FROM");
        if (!TokenValidator.validate(fields, "group")) {
            VerboseLogger.token(fromUser, false);
            System.out.println("Invalid or expired token.");
            return;
        }
        VerboseLogger.token(fromUser, true);

        String groupId = fields.get("GROUP_ID");
        String groupName = fields.get("GROUP_NAME");
        String membersCsv = fields.getOrDefault("MEMBERS", "");
        long timestamp = parseTimestamp(fields.get("TIMESTAMP"));

        Collection<String> members = parseCsv(membersCsv);

        boolean created = GroupManager.createGroup(groupId, groupName, members, timestamp);
        if (created) {
            System.out.println("You’ve been added to " + groupName);
            VerboseLogger.log("Group created: " + groupName + " (" + groupId + ")");
        } else {
            VerboseLogger.log("Group creation failed: group already exists with ID " + groupId);
        }
    }
}
