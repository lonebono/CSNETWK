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
                    System.err.println("Invalid port arguement, defaulting to 50999");
                }
            }

            System.out.println("[DEBUG] Starting LSNP on port " + PORT);
            socketManager = new UDPSocketManager(PORT);
            InetAddress localIP = InetAddress.getLocalHost();
            System.out.println("Local IP: " + localIP.getHostAddress());
            currentUser = ConsoleInput.readLine(scanner, "Enter your username: ").trim();
            System.out.println("[DEBUG] Current user set to: " + currentUser);

            PostHandler postHandler = new PostHandler(socketManager, currentUser);
            DMHandler dmHandler = new DMHandler(socketManager, currentUser);
            FileHandler fileHandler = new FileHandler(socketManager, currentUser);
            PingHandler pingHandler = new PingHandler(socketManager, currentUser);
            LikeHandler likeHandler = new LikeHandler(socketManager, currentUser);
            RevokeHandler revokeHandler = new RevokeHandler(socketManager, currentUser);

            /*
             * new Thread(() -> {
             * while (true) {
             * pingHandler.broadcastPing();
             * try {
             * Thread.sleep(10000); // Ping every 5 seconds
             * } catch (InterruptedException e) {
             * break;
             * }
             * }
             * }).start();
             */

            System.out.println("[DEBUG] Starting listener thread...");
            new Thread(() -> startListener(socketManager, postHandler, dmHandler, fileHandler, likeHandler, revokeHandler)).start();
            
            runMenu(scanner, socketManager, postHandler, dmHandler, fileHandler, likeHandler, revokeHandler);
        } catch (Exception e) {
            System.err.println("LSNP Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void runMenu(Scanner scanner, UDPSocketManager socketManager, PostHandler postHandler,
            DMHandler dmHandler, FileHandler fileHandler, LikeHandler likeHandler, RevokeHandler revokeHandler) {
        while (true) {
            InputManager.InputRequest req = InputManager.getRequestQueue().poll();
            if (req != null) {
                // There's a prompt waiting for input, handle it first
                System.out.print(req.prompt);
                String response = scanner.nextLine();
                try {
                    req.responseQueue.put(response);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.err.println("Input request interrupted.");
                    break;
                }
                continue; // Skip the menu for this loop iteration
            }

            // If there are still prompts waiting, do NOT show the menu or read input
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
                        System.out.println("[DEBUG] Broadcasting POST with content: " + content);
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
                        System.out.println(
                                "[DEBUG] Sending DM to " + recipientId + " at " + ip + " with content: " + content);
                        dmHandler.send(recipientId, content, recipientAddress, recipientPort);
                    } catch (Exception e) {
                        System.err.println("Error sending DM: " + e.getMessage());
                        e.printStackTrace();
                    }
                    break;
                    case "3":
                try {
                    String likedMessageId = ConsoleInput.readLine(scanner, "Enter MESSAGE_ID of the post to like: ");
                    System.out.println("[DEBUG] Liking post with MESSAGE_ID: " + likedMessageId);
                    likeHandler.sendLike(likedMessageId);
                } catch (Exception e) {
                    System.err.println("Error sending like: " + e.getMessage());
                    e.printStackTrace();
                }
                break;
                case "4":
                    System.out.println("[DEBUG] Option 3 selected - Like a Post (not implemented yet)");
                    break;
                case "5":
                    System.out.println("[DEBUG] Option 4 selected - View Groups (not implemented yet)");
                    break;
                case "6":
                    System.out.println("[DEBUG] Option 5 selected - Create / Update Group (not implemented yet)");
                    break;
                case "7":
                    System.out.println("[DEBUG] Option 6 selected - Send Group Message (not implemented yet)");
                    break;
                case "8":
                    try {
                        String filePath = ConsoleInput.readLine(scanner, "Enter path to file: ").trim();
                        String recipientId = ConsoleInput.readLine(scanner, "Enter recipient ID: ").trim();
                        String ip = ConsoleInput.readLine(scanner, "Enter recipient IP address: ").trim();
                        InetAddress recipientAddress = InetAddress.getByName(ip);
                        int recipientPort = Integer.parseInt(ConsoleInput.readLine(scanner, "Enter recipient port: "));
                        System.out.println("[DEBUG] Initiating file transfer to " + recipientId + " at " + ip
                                + " for file " + filePath);
                        fileHandler.sendFile(recipientId, filePath, "File transfer", recipientAddress, recipientPort);
                    } catch (Exception e) {
                        System.err.println("Error sending file: " + e.getMessage());
                        e.printStackTrace();
                    }
                    break;
                case "9":
                    try {
                        String tokenToRevoke = ConsoleInput.readLine(scanner, "Enter the exact token string to revoke: ");
                        System.out.println("[DEBUG] Revoking token: " + tokenToRevoke);
                        revokeHandler.sendRevoke(tokenToRevoke);
                    } catch (Exception e) {
                        System.err.println("Error sending revoke request: " + e.getMessage());
                        e.printStackTrace();
                    }
                    break;

                case "10":
                    System.out.println("[DEBUG] Option 8 selected - Tic Tac Toe (not implemented yet)");
                    break;
                case "11":
                    System.out.println("[DEBUG] Option 9 selected - View a Profile (not implemented yet)");
                    break;
                case "12":
                    System.out.println("[DEBUG] Option 10 selected - Follow / Unfollow User (not implemented yet)");
                    break;
                case "13":
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
        System.out.println("12. Revoke Token");
        System.out.println("0. Exit");
        System.out.print("Select option: ");
    }

        private static void startListener(UDPSocketManager socketManager, PostHandler postHandler, DMHandler dmHandler,
            FileHandler fileHandler, LikeHandler likeHandler, RevokeHandler revokeHandler) {
        try {
            System.out.println("[DEBUG] Listener started, waiting for messages...");
            while (true) {
                String msg = socketManager.receiveMessage();
                if (msg == null) {
                    // Timeout or no data, can print debug optionally
                    // System.out.println("[DEBUG] No message received (timeout).");
                    continue;
                }

                InetAddress senderIP = socketManager.getLastSenderAddress();

                System.out.println("[DEBUG] Received message from " + senderIP.getHostAddress() + ":");
                System.out.println(msg);

                Map<String, String> parsed = MessageParser.parse(msg);

                System.out.println("[DEBUG] Parsed message fields:");
                for (Map.Entry<String, String> entry : parsed.entrySet()) {
                    System.out.println("  " + entry.getKey() + " = " + entry.getValue());
                }

                String userId = parsed.get("USER_ID");
                if (userId == null) {
                    userId = parsed.get("FROM");
                }
                System.out.println("[DEBUG] USER_ID field = " + userId);

                String type = parsed.get("TYPE");
                if ("ACK".equals(type)) {
                    // Skip IP verification for ACK messages
                } else if (!IPLogger.verifyIP(userId, senderIP.getHostAddress())) {
                    VerboseLogger.drop("IP mismatch for user " + userId + " from " + senderIP.getHostAddress());
                    continue;
                }
                // temporarily disables ping during file transfer test
                if ("PING".equals(type)) {
                    continue; // skip PING messages completely
                }
                // temporarily disables ping during file transfer test

                String expectedScope = getExpectedTokenScope(type);

                if (expectedScope != null) {
                    if (!TokenValidator.validate(parsed, expectedScope)) {
                        VerboseLogger.drop("Invalid or expired token or scope mismatch");
                        continue;
                    }
                } else {
                    // Optionally skip token validation if scope is null (e.g., PING, ACK)
                }

                switch (type) {
                    case "POST":
                        postHandler.handle(parsed, senderIP.getHostAddress());
                        break;
                    case "DM":
                        dmHandler.handle(parsed);
                        break;
                    case "FILE_OFFER", "FILE_CHUNK", "FILE_RECEIVED":
                        fileHandler.handle(parsed, senderIP.getHostAddress());
                        break;
                    case "LIKE": // New case for LIKE messages
                        likeHandler.handle(parsed, senderIP.getHostAddress());
                        break;
                    case "ACK":
                        fileHandler.handleAck(parsed);
                        dmHandler.handleAck(parsed);
                        break;
                    case "REVOKE":
                        revokeHandler.handle(parsed, senderIP.getHostAddress());
                        break;
                    default:
                        VerboseLogger.log("Unhandled TYPE: " + type);
                        break;
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
            case "PING", "ACK" -> null;
            default -> null;
        };
    }
    
}
