package main.handlers;

import java.net.InetAddress;
import java.time.Instant;
import java.util.*;
import main.Main;
import main.UDPSocketManager;
import main.utils.ConsoleInput;
import main.utils.InputManager;
import main.utils.MessageParser;
import main.utils.VerboseLogger;

/**
 * TicTacToeHandler - full-featured tic-tac-toe handler for LSNP.
 */
public class TicTacToeHandler {
    private final UDPSocketManager socketManager;
    private final String currentUser;
    private final Scanner scanner;

    private static class GameState {
        final char[] board = new char[9];
        final String gameId;
        String mySymbol;
        String opponentSymbol;
        String opponentUserId;
        String opponentIp;
        int opponentPort;
        boolean myTurn;

        GameState(String gameId) {
            this.gameId = gameId;
            Arrays.fill(board, ' ');
        }
    }

    // Active games keyed by GAMEID
    private final Map<String, GameState> activeGames = Collections.synchronizedMap(new LinkedHashMap<>());

    public TicTacToeHandler(UDPSocketManager socketManager, String currentUser, Scanner scanner) {
        this.socketManager = socketManager;
        this.currentUser = currentUser;
        this.scanner = scanner;
    }

    public TicTacToeHandler(UDPSocketManager socketManager, String currentUser) {
        this(socketManager, currentUser, new Scanner(System.in));
    }

    // =========================
    // Public API
    // =========================
    public void startGame() {
        try {
            String opponentUserId = ConsoleInput.readLine(scanner, "Enter opponent's USER_ID: ").trim();
            String opponentIp = ConsoleInput.readLine(scanner, "Enter opponent's IP: ").trim();
            int opponentPort = Integer.parseInt(ConsoleInput.readLine(scanner, "Enter opponent's port: ").trim());
            sendInvite(opponentUserId, opponentIp, opponentPort);
        } catch (Exception e) {
            System.err.println("Error starting game: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void sendInvite(String opponentUserId, String opponentIp, int opponentPort) {
        try {
            String gameId = "g" + new Random().nextInt(256);
            String symbol = "X"; // inviter is X
            long now = Instant.now().getEpochSecond();
            String token = currentUser + "@" + InetAddress.getLocalHost().getHostAddress() + "|" + (now + 3600) + "|game";

            Map<String, String> invite = new LinkedHashMap<>();
            invite.put("TYPE", "TICTACTOE_INVITE");
            invite.put("FROM", currentUser + "@" + InetAddress.getLocalHost().getHostAddress());
            invite.put("TO", opponentUserId);
            invite.put("GAMEID", gameId);
            invite.put("MESSAGE_ID", UUID.randomUUID().toString().replace("-", "").substring(0, 16));
            invite.put("SYMBOL", symbol);
            invite.put("FROM_PORT", String.valueOf(socketManager.getPort()));
            invite.put("TIMESTAMP", String.valueOf(now));
            invite.put("TOKEN", token);

            socketManager.sendMessage(MessageParser.serialize(invite), InetAddress.getByName(opponentIp), opponentPort);
            VerboseLogger.log("TicTacToe INVITE sent to " + opponentUserId + " @ " + opponentIp + ":" + opponentPort);

            GameState game = new GameState(gameId);
            game.mySymbol = "X";
            game.opponentSymbol = "O";
            game.opponentUserId = opponentUserId;
            game.opponentIp = opponentIp;
            game.opponentPort = opponentPort;
            game.myTurn = true;
            activeGames.put(gameId, game);

            Main.inGame = true;
            System.out.println("Invite sent. You are X and will go first.");
            waitForMove(game);
        } catch (Exception e) {
            System.err.println("Error in sendInvite: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void handle(Map<String, String> msg, String fromIP) {
        if (msg == null) return;
        String type = msg.get("TYPE");
        if (type == null) return;

        switch (type) {
            case "TICTACTOE_INVITE":
                handleInviteAsync(msg, fromIP);
                break;
            case "TICTACTOE_MOVE":
                handleMove(msg);
                break;
            case "TICTACTOE_RESULT":
                handleResult(msg);
                break;
            default:
                VerboseLogger.log("TicTacToeHandler received unknown type: " + type);
        }
    }

    // =========================
    // Async Invite Handling
    // =========================
    private void handleInviteAsync(Map<String, String> msg, String fromIP) {
        String fromUser = msg.get("FROM");
        String gameId = msg.get("GAMEID");
        String symbol = msg.get("SYMBOL");
        String fromPortStr = msg.get("FROM_PORT");
        int fromPort = -1;
        try {
            fromPort = Integer.parseInt(fromPortStr);
        } catch (NumberFormatException ignored) { }

        final int finalFromPort = fromPort;

        InputManager.requestInput(
            fromUser + " is inviting you to play tic-tac-toe.\nAccept? (y/n): ",
            response -> {
                if (!response.trim().equalsIgnoreCase("y")) {
                    System.out.println("Declined game invite.");
                    return;
                }

                GameState game = new GameState(gameId);
                game.mySymbol = symbol.equals("X") ? "O" : "X";
                game.opponentSymbol = symbol;
                game.opponentUserId = fromUser;
                game.opponentIp = fromIP;
                game.opponentPort = (finalFromPort > 0 ? finalFromPort : socketManager.getPort());
                game.myTurn = symbol.equals("O");
                activeGames.put(gameId, game);

                Main.inGame = true;
                System.out.println("Accepted invite. You are " + game.mySymbol);

                if (game.myTurn) {
                    waitForMove(game);
                } else {
                    System.out.println("Waiting for opponent's move...");
                }
            }
        );
    }

    // =========================
    // Move & Result handling
    // =========================
    private void handleMove(Map<String, String> msg) {
        try {
            String gameId = msg.get("GAMEID");
            if (gameId == null) return;
            GameState game = activeGames.get(gameId);
            if (game == null) {
                VerboseLogger.log("Received move for unknown game: " + gameId);
                return;
            }

            int pos = Integer.parseInt(msg.getOrDefault("POSITION", "-1"));
            String symbol = msg.get("SYMBOL");
            if (pos < 0 || pos > 8 || symbol == null || symbol.isEmpty()) {
                VerboseLogger.log("Invalid move message");
                return;
            }

            synchronized (game) {
                game.board[pos] = symbol.charAt(0);
                printBoard(game.board);

                String winner = checkWinner(game.board);
                if (winner != null) {
                    if (winner.charAt(0) == game.mySymbol.charAt(0)) {
                        System.out.println("You win!");
                        sendResultAndFinish(game, "WIN");
                    } else {
                        System.out.println("You lose!");
                        sendResultAndFinish(game, "LOSS");
                    }
                    return;
                }

                if (isBoardFull(game.board)) {
                    System.out.println("It's a draw!");
                    sendResultAndFinish(game, "DRAW");
                    return;
                }

                game.myTurn = true;
            }
            waitForMove(game);
        } catch (Exception e) {
            System.err.println("Error in handleMove: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleResult(Map<String, String> msg) {
        try {
            String gameId = msg.get("GAMEID");
            if (gameId == null) return;
            GameState game = activeGames.remove(gameId);
            if (game == null) return;

            printBoard(game.board);
            String result = msg.getOrDefault("RESULT", "UNKNOWN");
            System.out.println("Game Over: " + result);
            Main.inGame = false;
        } catch (Exception e) {
            System.err.println("Error handling result: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // =========================
    // Networking helpers
    // =========================
    private void sendMove(GameState game, int pos) {
        try {
            long now = Instant.now().getEpochSecond();
            String token = currentUser + "@" + InetAddress.getLocalHost().getHostAddress() + "|" + (now + 3600) + "|game";

            Map<String, String> move = new LinkedHashMap<>();
            move.put("TYPE", "TICTACTOE_MOVE");
            move.put("FROM", currentUser + "@" + InetAddress.getLocalHost().getHostAddress());
            move.put("TO", game.opponentUserId);
            move.put("GAMEID", game.gameId);
            move.put("MESSAGE_ID", UUID.randomUUID().toString().replace("-", "").substring(0, 16));
            move.put("POSITION", String.valueOf(pos));
            move.put("SYMBOL", game.mySymbol);
            move.put("TIMESTAMP", String.valueOf(now));
            move.put("TOKEN", token);

            socketManager.sendMessage(MessageParser.serialize(move), InetAddress.getByName(game.opponentIp), game.opponentPort);
            VerboseLogger.log("Sent TICTACTOE_MOVE game=" + game.gameId + " pos=" + pos);
        } catch (Exception e) {
            System.err.println("Error sending move: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void sendResultAndFinish(GameState game, String result) {
        try {
            long now = Instant.now().getEpochSecond();
            String token = currentUser + "@" + InetAddress.getLocalHost().getHostAddress() + "|" + (now + 3600) + "|game";

            Map<String, String> res = new LinkedHashMap<>();
            res.put("TYPE", "TICTACTOE_RESULT");
            res.put("FROM", currentUser + "@" + InetAddress.getLocalHost().getHostAddress());
            res.put("TO", game.opponentUserId);
            res.put("GAMEID", game.gameId);
            res.put("MESSAGE_ID", UUID.randomUUID().toString().replace("-", "").substring(0, 16));
            res.put("RESULT", result);
            res.put("SYMBOL", game.mySymbol);
            res.put("TIMESTAMP", String.valueOf(now));
            res.put("TOKEN", token);

            socketManager.sendMessage(MessageParser.serialize(res), InetAddress.getByName(game.opponentIp), game.opponentPort);
            activeGames.remove(game.gameId);
            Main.inGame = false;
            System.out.println("Game finished (" + result + "). Returning to main menu.");
        } catch (Exception e) {
            System.err.println("Error sending result: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // =========================
    // Local input for moves
    // =========================
    private void waitForMove(GameState game) {
        synchronized (game) {
            if (!game.myTurn) return;
        }
        try {
            printBoard(game.board);
            int pos;
            while (true) {
                String line = ConsoleInput.readLine(scanner, "Enter position (0-8): ").trim();
                int candidate;
                try {
                    candidate = Integer.parseInt(line);
                } catch (NumberFormatException nfe) {
                    System.out.println("Invalid input. Enter integer 0-8.");
                    continue;
                }
                synchronized (game) {
                    if (candidate < 0 || candidate > 8) {
                        System.out.println("Position must be 0..8.");
                        continue;
                    }
                    if (game.board[candidate] != ' ') {
                        System.out.println("Cell already taken.");
                        continue;
                    }
                    pos = candidate;
                    game.board[pos] = game.mySymbol.charAt(0);
                    printBoard(game.board);
                    String winner = checkWinner(game.board);
                    if (winner != null) {
                        if (winner.charAt(0) == game.mySymbol.charAt(0)) {
                            sendResultAndFinish(game, "WIN");
                        } else {
                            sendResultAndFinish(game, "LOSS");
                        }
                        return;
                    }  else if (isBoardFull(game.board)) {
                        sendResultAndFinish(game, "DRAW");
                        return;
                    } else {
                        sendMove(game, pos);
                        game.myTurn = false;
                        System.out.println("Move sent; waiting for opponent...");
                        return;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error during waitForMove: " + e.getMessage());
            e.printStackTrace();
            Main.inGame = false;
        }
    }

    // =========================
    // Board helpers
    // =========================
    private void printBoard(char[] b) {
        System.out.println(
            " " + b[0] + " | " + b[1] + " | " + b[2] + "\n" +
            "---+---+---\n" +
            " " + b[3] + " | " + b[4] + " | " + b[5] + "\n" +
            "---+---+---\n" +
            " " + b[6] + " | " + b[7] + " | " + b[8]
        );
    }

    private String checkWinner(char[] b) {
        int[][] lines = {{0,1,2},{3,4,5},{6,7,8},{0,3,6},{1,4,7},{2,5,8},{0,4,8},{2,4,6}};
        for (int[] line : lines) {
            if (b[line[0]] != ' ' && b[line[0]] == b[line[1]] && b[line[1]] == b[line[2]]) {
                return String.valueOf(b[line[0]]);
            }
        }
        return null;
    }

    private boolean isBoardFull(char[] b) {
        for (char c : b) if (c == ' ') return false;
        return true;
    }
}
