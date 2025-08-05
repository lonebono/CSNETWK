package main.utils;

import java.net.InetAddress;

public class ChunkMetadata {
    public final String fileId;
    public final int chunkIndex;
    public final String messageId;
    public final String base64Data;
    public final int totalChunks;
    public final int chunkSize;
    public final InetAddress recipientAddress;
    public final int recipientPort;
    public final String toUserId;

    public int retryCount = 0;
    public long lastSentTime;

    public volatile boolean acknowledged = false;

    public ChunkMetadata(String fileId, int chunkIndex, String messageId,
            String base64Data, int totalChunks, int chunkSize,
            InetAddress recipientAddress, int recipientPort, String toUserId) {
        this.fileId = fileId;
        this.chunkIndex = chunkIndex;
        this.messageId = messageId;
        this.base64Data = base64Data;
        this.totalChunks = totalChunks;
        this.chunkSize = chunkSize;
        this.recipientAddress = recipientAddress;
        this.recipientPort = recipientPort;
        this.toUserId = toUserId;
        this.lastSentTime = System.currentTimeMillis();
    }
}