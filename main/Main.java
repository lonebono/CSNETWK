package main;

import java.net.*;
import java.util.*;

import handlers.DMHandler;
import handlers.FileTransferHandler;
import handlers.GameHandler;
import handlers.GroupHandler;
import handlers.PostHandler;

public class Main {
    private static final int PORT = 50999;
    private static boolean verbose = false;
    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        try {
            DatagramSocket socket = new DatagramSocket(PORT);
            new Thread(() -> startListener(socket)).start(); // Background listener
            runMenu(socket);
        } catch (Exception e) {
            System.err.println("LSNP Error: " + e.getMessage());
        }
    }

    private static void runMenu(DatagramSocket socket) {
        while (true) {
            printMenu();
            String input = scanner.nextLine();

            switch (input) {
                case "1":
                    handlers.PostHandler.send(socket);
                    break;
                case "2":
                    handlers.DMHandler.send(socket);
                    break;
                case "3":
                    handlers.GroupHandler.show();
                    break;
                case "4":
                    handlers.GroupHandler.createOrUpdate(socket);
                    break;
                case "5":
                    handlers.GroupHandler.sendMessage(socket);
                    break;
                case "6":
                    handlers.FileTransferHandler.initiate(socket);
                    break;
                case "7":
                    handlers.GameHandler.start(socket);
                    break;
                case "8":
                    verbose = !verbose;
                    handlers.VerboseLogger.setEnabled(verbose);
                    System.out.println("Verbose mode: " + (verbose ? "ON" : "OFF"));
                    break;
                case "0":
                    System.out.println("Goodbye.");
                    System.exit(0);
                default:
                    System.out.println("Invalid choice.");
            }
        }
    }

    private static void printMenu() {
        System.out.println("\n=== LSNP Main Menu ===");
        System.out.println("1. Send POST");
        System.out.println("2. Send DM");
        System.out.println("3. View Groups");
        System.out.println("4. Create / Update Group");
        System.out.println("5. Send Group Message");
        System.out.println("6. File Transfer");
        System.out.println("7. Play Tic Tac Toe");
        System.out.println("8. Toggle Verbose Mode");
        System.out.println("0. Exit");
        System.out.print("Select option: ");
    }

    private static void startListener(DatagramSocket socket) {
        try {
            byte[] buffer = new byte[65535];

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String msg = new String(packet.getData(), 0, packet.getLength(), "UTF-8");
                InetAddress senderIP = packet.getAddress();

                Map<String, String> parsed = MessageParser.parse(msg);
                String from = parsed.getOrDefault("FROM", parsed.getOrDefault("USER_ID", ""));

                if (!IPLogger.verifyIP(from, senderIP.getHostAddress())) {
                    handlers.VerboseLogger.drop("IP mismatch from " + senderIP);
                    continue;
                }

                if (!TokenValidator.validate(parsed)) {
                    handlers.VerboseLogger.drop("Token invalid or expired.");
                    continue;
                }

                handlers.VerboseLogger.recv(parsed, senderIP.getHostAddress());

                String type = parsed.get("TYPE");
                switch (type) {
                    case "FILE_OFFER":
                    case "FILE_CHUNK":
                    case "FILE_RECEIVED":
                        handlers.FileTransferHandler.handle(parsed);
                        break;

                    case "GROUP_CREATE":
                    case "GROUP_UPDATE":
                    case "GROUP_MESSAGE":
                        handlers.GroupHandler.handle(parsed);
                        break;

                    case "POST":
                        handlers.PostHandler.handle(parsed);
                        break;

                    case "DM":
                        handlers.DMHandler.handle(parsed);
                        break;

                    case "TICTACTOE_INVITE":
                    case "TICTACTOE_MOVE":
                    case "TICTACTOE_RESULT":
                        handlers.GameHandler.handle(parsed);
                        break;

                    default:
                        handlers.VerboseLogger.log("Unhandled TYPE: " + type);
                }
            }
        } catch (Exception e) {
            System.err.println("Listener error: " + e.getMessage());
        }
    }
}