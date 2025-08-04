package main.handlers;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.util.*;
import main.UDPSocketManager;
import main.data.FileChunkStore;
import main.utils.ConsoleInput;
import main.utils.FileChunker;
import main.utils.TerminalDisplay;
import main.utils.VerboseLogger;

public class FileHandler {
    private final UDPSocketManager socketManager;
    private final String currentUserId;
    private final FileChunkStore chunkStore = new FileChunkStore();
    private final Scanner scanner = new Scanner(System.in);

    public FileHandler(UDPSocketManager socketManager, String currentUserId) {
        this.socketManager = socketManager;
        this.currentUserId = currentUserId;
    }

    public void handle(Map<String, String> msg, String senderIP) {
        String type = msg.get("TYPE");
        if (type == null)
            return;

        switch (type) {
            case "FILE_OFFER" -> handleFileOffer(msg, senderIP);
            case "FILE_CHUNK" -> handleFileChunk(msg, senderIP);
            case "FILE_RECEIVED" -> handleFileReceived(msg);
            default -> VerboseLogger.log("FileHandler: Unknown message type " + type);
        }
    }

    private void handleFileOffer(Map<String, String> msg, String senderIP) {
        String from = msg.get("FROM");
        String filename = msg.get("FILENAME");
        String fileId = msg.get("FILEID");

        TerminalDisplay.displayFileOffer(from, filename);

        String response = ConsoleInput.readLine("Accept file? (y/n): ").trim().toLowerCase();
        while (!response.equals("y") && !response.equals("n")) {
            response = ConsoleInput.readLine("Please enter 'y' or 'n': ").trim().toLowerCase();
        }
        if (!response.equals("y")) {
            VerboseLogger.log("File offer from " + from + " for fileId " + fileId + " declined.");
            return;
        }

        VerboseLogger.log("File offer accepted for fileId " + fileId);
    }

    private void handleFileChunk(Map<String, String> msg, String senderIP) {
        String fileId = msg.get("FILEID");
        int chunkIndex = Integer.parseInt(msg.get("CHUNK_INDEX"));
        int totalChunks = Integer.parseInt(msg.get("TOTAL_CHUNKS"));
        String base64Data = msg.get("DATA");

        chunkStore.addChunk(fileId, chunkIndex, totalChunks, base64Data);
        String messageId = msg.get("MESSAGE_ID");
        if (chunkStore.isComplete(fileId)) {
            byte[] fullFile = chunkStore.reassemble(fileId);
            if (fullFile != null) {
                System.out.println("[INFO] File transfer of " + fileId + " is complete.");
                int toPort = socketManager.getLastSenderPort();
                sendFileReceived(msg.get("TO"), msg.get("FROM"), fileId, toPort);
                chunkStore.removeFile(fileId);
            }
        }
        if (messageId != null) {
            try {
                InetAddress senderAddress = InetAddress.getByName(senderIP);
                sendAck(messageId, senderAddress, socketManager.getLastSenderPort());
            } catch (Exception e) {
                VerboseLogger.log("Failed to send ACK for chunk " + messageId + ": " + e.getMessage());
            }
        }
    }

    private void handleFileReceived(Map<String, String> msg) {
        String fileId = msg.get("FILEID");
        String status = msg.get("STATUS");
        VerboseLogger.log("Received FILE_RECEIVED for fileId " + fileId + " with status: " + status);
    }

    public void sendFileOffer(String toUserId, InetAddress toAddress, String filename, long filesize, String filetype,
            String fileId, String description, int toPort) {
        try {
            StringBuilder sb = new StringBuilder();
            String messageId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            sb.append("TYPE: FILE_OFFER\n");
            sb.append("MESSAGE_ID: ").append(messageId).append("\n");
            sb.append("FROM: ").append(currentUserId).append("\n");
            sb.append("TO: ").append(toUserId).append("\n");
            sb.append("FILENAME: ").append(filename).append("\n");
            sb.append("FILESIZE: ").append(filesize).append("\n");
            sb.append("FILETYPE: ").append(filetype).append("\n");
            sb.append("FILEID: ").append(fileId).append("\n");
            sb.append("DESCRIPTION: ").append(description).append("\n");
            sb.append("TIMESTAMP: ").append(System.currentTimeMillis() / 1000).append("\n");
            sb.append("TOKEN: ").append(currentUserId).append("|")
                    .append(System.currentTimeMillis() / 1000 + 3600).append("|file\n");
            sb.append("\n");

            socketManager.sendMessage(sb.toString(), toAddress, toPort);
            VerboseLogger.log("Sent FILE_OFFER to " + toUserId);
        } catch (Exception e) {
            VerboseLogger.log("Failed to send FILE_OFFER: " + e.getMessage());
        }
    }

    public void sendFileChunk(String toUserId, InetAddress toAddress, String fileId, int chunkIndex, int totalChunks,
            int chunkSize, String base64Data, int toPort) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("TYPE: FILE_CHUNK\n");
            sb.append("FROM: ").append(currentUserId).append("\n");
            sb.append("TO: ").append(toUserId).append("\n");
            sb.append("FILEID: ").append(fileId).append("\n");
            sb.append("CHUNK_INDEX: ").append(chunkIndex).append("\n");
            sb.append("TOTAL_CHUNKS: ").append(totalChunks).append("\n");
            sb.append("CHUNK_SIZE: ").append(chunkSize).append("\n");
            String messageId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            sb.append("MESSAGE_ID: ").append(messageId).append("\n");
            sb.append("TOKEN: ").append(currentUserId).append("|").append(System.currentTimeMillis() / 1000 + 3600)
                    .append("|file\n");
            sb.append("DATA: ").append(base64Data).append("\n");
            sb.append("\n");

            String msg = sb.toString();
            socketManager.sendMessage(msg, toAddress, toPort);
            VerboseLogger.log("Sent FILE_CHUNK " + chunkIndex + "/" + (totalChunks - 1) + " for fileId " + fileId);
        } catch (Exception e) {
            VerboseLogger.log("Failed to send FILE_CHUNK: " + e.getMessage());
        }
    }

    public void sendFileReceived(String toUserId, String fromUserId, String fileId, int toPort) {
        try {
            StringBuilder sb = new StringBuilder();
            String messageId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            sb.append("TYPE: FILE_RECEIVED\n");
            sb.append("MESSAGE_ID: ").append(messageId).append("\n");
            sb.append("FROM: ").append(currentUserId).append("\n");
            sb.append("TO: ").append(fromUserId).append("\n");

            sb.append("FILEID: ").append(fileId).append("\n");
            sb.append("STATUS: COMPLETE\n");
            sb.append("TIMESTAMP: ").append(System.currentTimeMillis() / 1000).append("\n");
            sb.append("\n");

            InetAddress toAddress = InetAddress.getByName(fromUserId.split("@")[1]);
            socketManager.sendMessage(sb.toString(), toAddress, toPort);
            VerboseLogger.log("Sent FILE_RECEIVED COMPLETE for fileId " + fileId);
        } catch (Exception e) {
            VerboseLogger.log("Failed to send FILE_RECEIVED: " + e.getMessage());
        }
    }

    public void sendFile(String toUserId, String filePath, String description, InetAddress toAddress, int toPort) {
        try {
            File file = new File(filePath);
            if (!file.exists() || !file.isFile()) {
                System.err.println("Invalid file path: " + filePath);
                return;
            }

            byte[] fileBytes = Files.readAllBytes(file.toPath());
            String fileId = UUID.randomUUID().toString();
            String fileType = Files.probeContentType(file.toPath());
            long fileSize = fileBytes.length;

            List<String> chunks = FileChunker.chunkFile(fileBytes, 1024);
            int totalChunks = chunks.size();

            sendFileOffer(toUserId, toAddress, file.getName(), fileSize, fileType, fileId, description, toPort);

            for (int i = 0; i < totalChunks; i++) {
                String base64Chunk = chunks.get(i);
                int chunkSize = Base64.getDecoder().decode(base64Chunk).length;

                sendFileChunk(toUserId, toAddress, fileId, i, totalChunks, chunkSize, base64Chunk, toPort);

                Thread.sleep(20);
            }

            VerboseLogger.log("Completed sending all FILE_CHUNKs for fileId " + fileId);
        } catch (Exception e) {
            System.err.println("Failed to send file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void sendAck(String messageId, InetAddress recipientAddress, int recipientPort) throws IOException {
        String ack = String.join("\n",
                "TYPE:ACK",
                "MESSAGE_ID:" + messageId,
                "STATUS:RECEIVED");

        socketManager.sendMessage(ack, recipientAddress, recipientPort);
        VerboseLogger.log("Sent ACK for message ID: " + messageId + " to " + recipientAddress.getHostAddress());
    }

    public void handleAck(Map<String, String> msg) {
        String messageId = msg.get("MESSAGE_ID");
        String status = msg.get("STATUS");

        if ("RECEIVED".equalsIgnoreCase(status)) {
            VerboseLogger.log("ACK received for file message ID: " + messageId);
            // TODO: Update your chunk resend logic or mark chunk as acknowledged
        }
    }
}
