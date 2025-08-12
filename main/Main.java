package main;

import java.net.*;
import java.util.*;
import main.data.GroupStore;
import main.handlers.*;
import main.utils.*;

public class Main {
    private static int PORT = 50999;
    private static boolean verbose = false;
    private static UDPSocketManager socketManager;
    private static String currentUser;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        try {
            if (args.length > 0) {
                try {
                    PORT = Integer.parseInt(args[0]);
                } catch (NumberFormatException e) {
                    System.err.println("Invalid port argument, defaulting to 50999");
                }
            }

            System.out.println("[DEBUG] Starting LSNP on port " + PORT);
            socketManager = new UDPSocketManager(PORT);
            InetAddress localIP = InetAddress.getLocalHost();
            System.out.println("Local IP: " + localIP.getHostAddress());

            currentUser = ConsoleInput.readLine(scanner, "Enter your username: ").trim();
            String displayName = ConsoleInput.readLine(scanner, "Enter display name: ").trim();
            String status = ConsoleInput.readLine(scanner, "Enter status message: ").trim();

            DMHandler dmHandler = new DMHandler(socketManager, currentUser);
            FileHandler fileHandler = new FileHandler(socketManager, currentUser);
            PingHandler pingHandler = new PingHandler(socketManager, currentUser);
            LikeHandler likeHandler = new LikeHandler(socketManager, currentUser);
            RevokeHandler revokeHandler = new RevokeHandler(socketManager, currentUser);

            ProfileHandler profileHandler = new ProfileHandler(socketManager, currentUser, displayName, status);
            FollowHandler followHandler = new FollowHandler(socketManager, currentUser);
            PostHandler postHandler = new PostHandler(socketManager, currentUser, followHandler);

            GroupStore groupStore = new GroupStore();
            GroupManager groupManager = new GroupManager(groupStore);
            GroupHandler groupHandler = new GroupHandler(socketManager, groupManager, currentUser);

            // Broadcast profile periodically
            new Thread(() -> {
                while (true) {
                    profileHandler.broadcastProfile();
                    try {
                        Thread.sleep(50_000);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }).start();

            new Thread(() -> startListener(socketManager, postHandler, dmHandler, fileHandler, profileHandler,
                    followHandler, groupHandler, likeHandler, revokeHandler)).start();

            runMenu(scanner, socketManager, postHandler, dmHandler, fileHandler, profileHandler, followHandler,
                    groupHandler, groupManager, groupStore, likeHandler, revokeHandler);

        } catch (Exception e) {
            System.err.println("LSNP Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void runMenu(Scanner scanner, UDPSocketManager socketManager,
            PostHandler postHandler, DMHandler dmHandler,
            FileHandler fileHandler, ProfileHandler profileHandler,
            FollowHandler followHandler, GroupHandler groupHandler,
            GroupManager groupManager, GroupStore groupStore,
            LikeHandler likeHandler, RevokeHandler revokeHandler) {

        while (true) {
            InputManager.InputRequest req = InputManager.getRequestQueue().poll();
            if (req != null) {
                System.out.print(req.prompt);
                String response = scanner.nextLine();
                try {
                    req.responseQueue.put(response);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.err.println("Input request interrupted.");
                    break;
                }
                continue;
            }

            if (!InputManager.getRequestQueue().isEmpty()) {
                continue;
            }

            printMenu();
            String input = scanner.nextLine();

            switch (input) {
                case "1":
                    try {
                        String content = ConsoleInput.readLine(scanner, "Enter message to broadcast: ");
                        postHandler.broadcast(content);
                    } catch (Exception e) {
                        System.err.println("Error sending post: " + e.getMessage());
                        e.printStackTrace();
                    }
                    break;
                case "2":
                    try {
                        String content = ConsoleInput.readLine(scanner, "Enter message to DM: ");
                        String recipientId = ConsoleInput.readLine(scanner, "Enter recipient ID: ");
                        String ip = ConsoleInput.readLine(scanner, "Enter recipient IP address: ");
                        InetAddress recipientAddress = InetAddress.getByName(ip);
                        int recipientPort = Integer.parseInt(ConsoleInput.readLine(scanner, "Enter recipient port: "));
                        dmHandler.send(recipientId, content, recipientAddress, recipientPort);
                    } catch (Exception e) {
                        System.err.println("Error sending DM: " + e.getMessage());
                        e.printStackTrace();
                    }
                    break;
                case "3":
                    try {
                        String likedMessageId = ConsoleInput.readLine(scanner,
                                "Enter MESSAGE_ID of the post to like: ");
                        likeHandler.sendLike(likedMessageId);
                    } catch (Exception e) {
                        System.err.println("Error sending like: " + e.getMessage());
                        e.printStackTrace();
                    }
                    break;
                case "4":
                    for (String gid : groupStore.getAllGroupIds()) {
                        GroupStore.Group g = groupStore.getGroup(gid);
                        System.out.println(
                                "Group ID: " + gid + ", Name: " + g.getGroupName() + ", Members: " + g.getMembers());
                    }
                    break;
                case "5":
                    try {
                        String groupId = ConsoleInput.readLine(scanner, "Enter new Group ID: ").trim();
                        String groupName = ConsoleInput.readLine(scanner, "Enter Group Name: ").trim();
                        String membersLine = ConsoleInput.readLine(scanner,
                                "Enter members with ports (user@ip:port), comma-separated (include yourself!): ")
                                .trim();

                        Map<String, InetSocketAddress> memberAddresses = new LinkedHashMap<>();
                        String[] entries = membersLine.split(",");
                        for (String entry : entries) {
                            entry = entry.trim();
                            if (entry.isEmpty())
                                continue;

                            int colonIndex = entry.lastIndexOf(':');
                            if (colonIndex == -1) {
                                System.out.println("Invalid member entry, missing port: " + entry);
                                continue;
                            }
                            String userAtIp = entry.substring(0, colonIndex);
                            String portStr = entry.substring(colonIndex + 1);
                            int port = Integer.parseInt(portStr);
                            int atIndex = userAtIp.indexOf('@');
                            if (atIndex == -1) {
                                System.out.println("Invalid member entry, missing '@': " + entry);
                                continue;
                            }

                            String userId = userAtIp + ":" + port;
                            String ipStr = userAtIp.substring(atIndex + 1);
                            InetAddress ip = InetAddress.getByName(ipStr);
                            InetSocketAddress socketAddr = new InetSocketAddress(ip, port);
                            memberAddresses.put(userId, socketAddr);
                        }

                        String selfUserId = currentUser + "@" + InetAddress.getLocalHost().getHostAddress();
                        if (!memberAddresses.containsKey(selfUserId)) {
                            InetSocketAddress selfAddr = new InetSocketAddress(InetAddress.getLocalHost(), PORT);
                            memberAddresses.put(selfUserId, selfAddr);
                        }

                        long timestamp = System.currentTimeMillis() / 1000L;
                        groupManager.createGroup(groupId, groupName, memberAddresses, currentUser, timestamp);

                        Map<String, String> createMsg = new LinkedHashMap<>();
                        createMsg.put("TYPE", "GROUP_CREATE");
                        createMsg.put("FROM", currentUser);
                        createMsg.put("GROUP_ID", groupId);
                        createMsg.put("GROUP_NAME", groupName);
                        createMsg.put("MEMBERS", String.join(",", memberAddresses.keySet()));
                        createMsg.put("TIMESTAMP", Long.toString(timestamp));
                        createMsg.put("TOKEN", TokenValidator.generate(currentUser, 3600, "group"));

                        String serialized = MessageParser.serialize(createMsg);
                        for (InetSocketAddress addr : memberAddresses.values()) {
                            socketManager.sendMessage(serialized, addr.getAddress(), addr.getPort());
                        }

                        VerboseLogger.log("Sent GROUP_CREATE for group " + groupName);
                    } catch (Exception e) {
                        System.err.println("Failed to send GROUP_CREATE: " + e.getMessage());
                        e.printStackTrace();
                    }
                    break;
                case "6":
                    try {
                        String groupId = ConsoleInput.readLine(scanner, "Enter Group ID to update: ").trim();
                        String addMembersLine = ConsoleInput
                                .readLine(scanner, "Enter comma-separated members to ADD (or leave blank): ").trim();
                        String removeMembersLine = ConsoleInput
                                .readLine(scanner, "Enter comma-separated members to REMOVE (or leave blank): ").trim();

                        List<String> addMembers = addMembersLine.isEmpty() ? Collections.emptyList()
                                : Arrays.stream(addMembersLine.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                                        .toList();
                        List<String> removeMembers = removeMembersLine.isEmpty() ? Collections.emptyList()
                                : Arrays.stream(removeMembersLine.split(",")).map(String::trim)
                                        .filter(s -> !s.isEmpty()).toList();

                        Map<String, InetSocketAddress> addMembersMap = new LinkedHashMap<>();
                        if (!addMembersLine.isEmpty()) {
                            String[] entries = addMembersLine.split(",");
                            for (String entry : entries) {
                                entry = entry.trim();
                                if (entry.isEmpty())
                                    continue;

                                int colonIndex = entry.lastIndexOf(':');
                                if (colonIndex == -1) {
                                    System.out.println("Invalid member entry, missing port: " + entry);
                                    continue;
                                }
                                String userAtIp = entry.substring(0, colonIndex);
                                String portStr = entry.substring(colonIndex + 1);
                                int port = Integer.parseInt(portStr);

                                int atIndex = userAtIp.indexOf('@');
                                if (atIndex == -1) {
                                    System.out.println("Invalid member entry, missing '@': " + entry);
                                    continue;
                                }

                                String userId = userAtIp + ":" + port;
                                InetAddress ip = InetAddress.getByName(userAtIp.substring(atIndex + 1));
                                InetSocketAddress socketAddr = new InetSocketAddress(ip, port);

                                addMembersMap.put(userId, socketAddr);
                            }
                        }

                        groupHandler.sendGroupUpdate(groupId, addMembersMap, removeMembers);
                    } catch (Exception e) {
                        System.err.println("Error updating group: " + e.getMessage());
                        e.printStackTrace();
                    }
                    break;
                case "7":
                    try {
                        String groupId = ConsoleInput.readLine(scanner, "Enter Group ID to send message to: ").trim();
                        String content = ConsoleInput.readLine(scanner, "Enter message content: ").trim();
                        groupHandler.sendGroupMessage(groupId, content);
                        VerboseLogger.log("Sent GROUP_MESSAGE to group " + groupId);
                    } catch (Exception e) {
                        System.err.println("Failed to send group message: " + e.getMessage());
                        e.printStackTrace();
                    }
                    break;
                case "8":
                    try {
                        String filePath = ConsoleInput.readLine(scanner, "Enter path to file: ").trim();
                        String recipientId = ConsoleInput.readLine(scanner, "Enter recipient ID: ").trim();
                        String ip = ConsoleInput.readLine(scanner, "Enter recipient IP address: ").trim();
                        InetAddress recipientAddress = InetAddress.getByName(ip);
                        int recipientPort = Integer.parseInt(ConsoleInput.readLine(scanner, "Enter recipient port: "));
                        fileHandler.sendFile(recipientId, filePath, "File transfer", recipientAddress, recipientPort);
                    } catch (Exception e) {
                        System.err.println("Error sending file: " + e.getMessage());
                        e.printStackTrace();
                    }
                    break;
                case "9":
                    System.out.println("[DEBUG] Option 9 selected - Play Tic Tac Toe (not implemented yet)");
                    break;
                case "10":
                    System.out.println("\n=== KNOWN PROFILES ===");
                    profileHandler.getKnownProfiles().forEach((id, data) -> {
                        String name = data.getOrDefault("DISPLAY_NAME", id);
                        String stat = data.getOrDefault("STATUS", "");
                        System.out.println(name + " (" + id + ") - " + stat);
                    });
                    break;
                case "11":
                    System.out.println("1. Follow user");
                    System.out.println("2. Unfollow user");
                    String choice = ConsoleInput.readLine(scanner, "Choose: ").trim();
                    try {
                        if (choice.equals("1")) {
                            String targetUserId = ConsoleInput.readLine(scanner, "Enter target user ID: ").trim();
                            String ip = ConsoleInput.readLine(scanner, "Enter target IP: ").trim();
                            int port = Integer.parseInt(ConsoleInput.readLine(scanner, "Enter target port: ").trim());
                            followHandler.follow(targetUserId, ip, port, 3600);
                        } else if (choice.equals("2")) {
                            String targetUserId = ConsoleInput.readLine(scanner, "Enter target user ID: ").trim();
                            followHandler.unfollow(targetUserId);
                        }
                    } catch (Exception e) {
                        System.err.println("Follow/Unfollow error: " + e.getMessage());
                        e.printStackTrace();
                    }
                    break;
                case "12":
                    verbose = !verbose;
                    main.utils.VerboseLogger.setEnabled(verbose);
                    break;
                case "13":
                    try {
                        String tokenToRevoke = ConsoleInput.readLine(scanner,
                                "Enter the exact token string to revoke: ");
                        revokeHandler.sendRevoke(tokenToRevoke);
                    } catch (Exception e) {
                        System.err.println("Error sending revoke request: " + e.getMessage());
                        e.printStackTrace();
                    }
                    break;
                case "0":
                    System.out.println("Goodbye.");
                    System.exit(0);
                    break;
                default:
                    System.out.println("Invalid choice.");
                    break;
            }
        }
    }

    private static void printMenu() {
        System.out.println("\n=== LSNP Main Menu ===");
        System.out.println("1. Send POST");
        System.out.println("2. Send DM");
        System.out.println("3. Like a Post");
        System.out.println("4. View Groups");
        System.out.println("5. Create Group");
        System.out.println("6. Update Group");
        System.out.println("7. Send Group Message");
        System.out.println("8. File Transfer");
        System.out.println("9. Play Tic Tac Toe");
        System.out.println("10. View Profiles");
        System.out.println("11. Follow / Unfollow User");
        System.out.println("12. Toggle Verbose Mode");
        System.out.println("13. Revoke Token");
        System.out.println("0. Exit");
        System.out.print("Select option: ");
    }

    private static void startListener(UDPSocketManager socketManager, PostHandler postHandler,
            DMHandler dmHandler, FileHandler fileHandler,
            ProfileHandler profileHandler, FollowHandler followHandler,
            GroupHandler groupHandler, LikeHandler likeHandler,
            RevokeHandler revokeHandler) {
        try {
            while (true) {
                String msg = socketManager.receiveMessage();
                if (msg == null)
                    continue;

                InetAddress senderIP = socketManager.getLastSenderAddress();
                Map<String, String> parsed = MessageParser.parse(msg);

                String userId = parsed.getOrDefault("USER_ID", parsed.get("FROM"));
                String type = parsed.get("TYPE");

                if (!"ACK".equals(type) && !IPLogger.verifyIP(userId, senderIP.getHostAddress())) {
                    VerboseLogger.drop("IP mismatch for user " + userId + " from " + senderIP.getHostAddress());
                    continue;
                }

                String expectedScope = getExpectedTokenScope(type);
                if (expectedScope != null && !TokenValidator.validate(parsed, expectedScope)) {
                    VerboseLogger.drop("Invalid or expired token or scope mismatch");
                    continue;
                }

                switch (type) {
                    case "POST" -> postHandler.handle(parsed, senderIP.getHostAddress());
                    case "DM" -> dmHandler.handle(parsed);
                    case "GROUP_CREATE", "GROUP_UPDATE", "GROUP_MESSAGE" ->
                        groupHandler.handle(parsed, senderIP.getHostAddress());
                    case "FILE_OFFER", "FILE_CHUNK", "FILE_RECEIVED" ->
                        fileHandler.handle(parsed, senderIP.getHostAddress());
                    case "LIKE" -> likeHandler.handle(parsed, senderIP.getHostAddress());
                    case "REVOKE" -> revokeHandler.handle(parsed, senderIP.getHostAddress());
                    case "ACK" -> {
                        fileHandler.handleAck(parsed);
                        dmHandler.handleAck(parsed);
                    }
                    case "PROFILE" -> profileHandler.handle(parsed, senderIP.getHostAddress());
                    case "FOLLOW", "UNFOLLOW" -> followHandler.handle(parsed, senderIP.getHostAddress());
                    default -> VerboseLogger.log("Unhandled TYPE: " + type);
                }
            }
        } catch (Exception e) {
            System.err.println("Listener error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String getExpectedTokenScope(String type) {
        if (type == null)
            return null;
        return switch (type) {
            case "POST", "LIKE" -> "broadcast";
            case "DM" -> "chat";
            case "FILE_OFFER", "FILE_CHUNK" -> "file";
            case "TICTACTOE_INVITE", "TICTACTOE_MOVE", "TICTACTOE_RESULT" -> "game";
            case "FOLLOW", "UNFOLLOW" -> "follow";
            case "GROUP_CREATE", "GROUP_UPDATE", "GROUP_MESSAGE" -> "group";
            case "REVOKE" -> "revoke";
            case "PING", "ACK", "PROFILE" -> null;
            default -> null;
        };
    }
}