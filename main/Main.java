package main;

import java.net.*;
import java.util.*;
import main.handlers.*;
import main.utils.IPLogger;
import main.utils.MessageParser;
import main.utils.TokenValidator;
import main.utils.VerboseLogger;

public class Main {
    private static final int PORT = 50999;
    private static boolean verbose = false;
    private static final Scanner scanner = new Scanner(System.in);
    private static UDPSocketManager socketManager;
    private static String currentUser;

    public static void main(String[] args) {
        try {
            System.out.println("[DEBUG] Starting LSNP on port " + PORT);
            socketManager = new UDPSocketManager(PORT);
            System.err.println("Enter your username: ");
            currentUser = scanner.nextLine().trim();
            System.out.println("[DEBUG] Current user set to: " + currentUser);

            PostHandler postHandler = new PostHandler(socketManager, currentUser);
            DMHandler dmHandler = new DMHandler(socketManager, currentUser);

            System.out.println("[DEBUG] Starting listener thread...");
            new Thread(() -> startListener(socketManager, postHandler, dmHandler)).start();
            runMenu(socketManager, postHandler, dmHandler);
        } catch (Exception e) {
            System.err.println("LSNP Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void runMenu(UDPSocketManager socketManager, PostHandler postHandler, DMHandler dmHandler) {
        while (true) {
            printMenu();
            String input = scanner.nextLine();
            System.out.println("[DEBUG] User selected menu option: " + input);

            switch (input) {
                case "1":
                    try {
                        System.out.print("Enter message to broadcast: ");
                        String content = scanner.nextLine();
                        System.out.println("[DEBUG] Broadcasting POST with content: " + content);
                        postHandler.broadcast(content);
                    } catch (Exception e) {
                        System.err.println("Error sending post: " + e.getMessage());
                        e.printStackTrace();
                    }
                    break;
                case "2":
                    try {
                        System.out.print("Enter message to DM: ");
                        String content = scanner.nextLine();

                        System.out.print("Enter recipient ID: ");
                        String recipientId = scanner.nextLine();

                        System.out.print("Enter recipient IP address: ");
                        String ip = scanner.nextLine();
                        InetAddress recipientAddress = InetAddress.getByName(ip);

                        System.out.println(
                                "[DEBUG] Sending DM to " + recipientId + " at " + ip + " with content: " + content);
                        dmHandler.send(recipientId, content, recipientAddress);
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
                    System.out.println("[DEBUG] Option 7 selected - File Transfer (not implemented yet)");
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

    private static void startListener(UDPSocketManager socketManager, PostHandler postHandler, DMHandler dmHandler) {
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
                System.out.println("[DEBUG] USER_ID field = " + userId);

                if (!IPLogger.verifyIP(userId, senderIP.getHostAddress())) {
                    VerboseLogger.drop("IP mismatch for user " + userId + " from " + senderIP.getHostAddress());
                    continue;
                }

                String type = parsed.get("TYPE");
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
            case "REVOKE" -> "revoke"; // or maybe no scope, depends on your spec
            case "PING", "ACK" -> null; // these might not require token validation
            default -> null;
        };
    }
}
