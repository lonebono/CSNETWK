package main;

import java.net.*;
import java.util.*;
import main.handlers.*;
import main.utils.ConsoleInput;
import main.utils.IPLogger;
import main.utils.InputManager;
import main.utils.MessageParser;
import main.utils.TokenValidator;
import main.utils.VerboseLogger;

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

            System.out.println("[DEBUG] Current user set to: " + currentUser);

            DMHandler dmHandler = new DMHandler(socketManager, currentUser);
            FileHandler fileHandler = new FileHandler(socketManager, currentUser);
            PingHandler pingHandler = new PingHandler(socketManager, currentUser);
            ProfileHandler profileHandler = new ProfileHandler(socketManager, currentUser, displayName, status);
            FollowHandler followHandler = new FollowHandler(socketManager, currentUser);
            PostHandler postHandler = new PostHandler(socketManager, currentUser, followHandler);


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

            System.out.println("[DEBUG] Starting listener thread...");
            new Thread(() -> startListener(socketManager, postHandler, dmHandler, fileHandler, profileHandler, followHandler)).start();

            runMenu(scanner, socketManager, postHandler, dmHandler, fileHandler, profileHandler, followHandler);
        } catch (Exception e) {
            System.err.println("LSNP Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void runMenu(Scanner scanner, UDPSocketManager socketManager,
                                 PostHandler postHandler, DMHandler dmHandler,
                                 FileHandler fileHandler, ProfileHandler profileHandler,
                                 FollowHandler followHandler) {
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
            System.out.println("[DEBUG] User selected menu option: " + input);

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
                    System.out.println("[DEBUG] Option 3 selected - Like a Post (not implemented yet)");
                    break;
                case "4":
                    System.out.println("[DEBUG] Option 4 selected - View Groups (not implemented yet)");
                    break;
                case "5":
                    System.out.println("[DEBUG] Option 5 selected - Create / Update Group (not implemented yet)");
                    break;
                case "6":
                    System.out.println("[DEBUG] Option 6 selected - Send Group Message (not implemented yet)");
                    break;
                case "7":
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
                case "8":
                    System.out.println("[DEBUG] Option 8 selected - Tic Tac Toe (not implemented yet)");
                    break;
                case "9":
                    System.out.println("\n=== KNOWN PROFILES ===");
                    profileHandler.getKnownProfiles().forEach((id, data) -> {
                        String name = data.getOrDefault("DISPLAY_NAME", id);
                        String stat = data.getOrDefault("STATUS", "");
                        System.out.println(name + " (" + id + ") - " + stat);
                    });
                    break;
                    case "10":
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
                
                case "11":
                    verbose = !verbose;
                    main.utils.VerboseLogger.setEnabled(verbose);
                    System.out.println("[DEBUG] Verbose mode toggled to: " + (verbose ? "ON" : "OFF"));
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
        System.out.println("5. Create / Update Group");
        System.out.println("6. Send Group Message");
        System.out.println("7. File Transfer");
        System.out.println("8. Play Tic Tac Toe");
        System.out.println("9. View Profiles");
        System.out.println("10. Follow / Unfollow User");
        System.out.println("11. Toggle Verbose Mode");
        System.out.println("0. Exit");
        System.out.print("Select option: ");
    }

    private static void startListener(UDPSocketManager socketManager, PostHandler postHandler, DMHandler dmHandler, FileHandler fileHandler, ProfileHandler profileHandler, FollowHandler followHandler) {
        try {
            System.out.println("[DEBUG] Listener started, waiting for messages...");
            while (true) {
                String msg = socketManager.receiveMessage();
                if (msg == null) continue;

                InetAddress senderIP = socketManager.getLastSenderAddress();

                Map<String, String> parsed = MessageParser.parse(msg);
                String userId = parsed.get("USER_ID");
                if (userId == null) {
                    userId = parsed.get("FROM");
                }

                String type = parsed.get("TYPE");

                if ("ACK".equals(type)) {
                    // Skip IP verification for ACK messages
                } else if (!IPLogger.verifyIP(userId, senderIP.getHostAddress())) {
                    VerboseLogger.drop("IP mismatch for user " + userId + " from " + senderIP.getHostAddress());
                    continue;
                }

                String expectedScope = getExpectedTokenScope(type);
                if (expectedScope != null) {
                    if (!TokenValidator.validate(parsed, expectedScope)) {
                        VerboseLogger.drop("Invalid or expired token or scope mismatch");
                        continue;
                    }
                }

                switch (type) {
                    case "POST" -> postHandler.handle(parsed, senderIP.getHostAddress());
                    case "DM" -> dmHandler.handle(parsed);
                    case "FILE_OFFER", "FILE_CHUNK", "FILE_RECEIVED" ->
                            fileHandler.handle(parsed, senderIP.getHostAddress());
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
        if (type == null) return null;
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
