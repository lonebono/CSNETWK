package main.data;

import java.util.*;
import main.utils.VerboseLogger;

public class FileChunkStore {
    private final Map<String, Map<Integer, String>> store = new HashMap<>();
    private final Map<String, Integer> totalChunksMap = new HashMap<>();

    public synchronized void addChunk(String fileId, int chunkIndex, int totalChunks, String base64Chunk) {
        if (!store.containsKey(fileId)) {
            store.put(fileId, new HashMap<>());
            totalChunksMap.put(fileId, totalChunks);
            VerboseLogger.log("Created new chunk map for fileId " + fileId + " expecting " + totalChunks + " chunks");
        }

        Map<Integer, String> chunks = store.get(fileId);
        if (!chunks.containsKey(chunkIndex)) {
            chunks.put(chunkIndex, base64Chunk);
            VerboseLogger.log("Stored chunk " + chunkIndex + "/" + (totalChunks - 1) + " for fileId " + fileId);
        } else {
            VerboseLogger.log("Chunk " + chunkIndex + " for fileId " + fileId + " is already stored, ignoring");
        }
    }

    public synchronized boolean isComplete(String fileId) {
        if (!store.containsKey(fileId)) {
            return false;
        }
        Map<Integer, String> chunks = store.get(fileId);
        int expected = totalChunksMap.getOrDefault(fileId, -1);
        boolean complete = expected != -1 && chunks.size() == expected;

        VerboseLogger.log("FileId " + fileId + " completion check: " + chunks.size() + "/" + expected
                + " chunks received. Complete? " + complete);
        return complete;
    }

    public synchronized byte[] reassemble(String fileId) {
        if (!isComplete(fileId)) {
            VerboseLogger.log("Cannot reassemble fileId " + fileId + ": incomplete chunks");
            return null;
        }

        Map<Integer, String> chunks = store.get(fileId);
        int totalChunks = totalChunksMap.get(fileId);

        List<byte[]> decodedChunks = new ArrayList<>(totalChunks);
        int totalSize = 0;

        // Assemble chunks in order from 0 to totalChunks-1
        for (int i = 0; i < totalChunks; i++) {
            String base64Chunk = chunks.get(i);
            if (base64Chunk == null) {
                VerboseLogger.log("Missing chunk " + i + " for fileId " + fileId + ", cannot reassemble");
                return null;
            }
            byte[] decoded = Base64.getDecoder().decode(base64Chunk);
            decodedChunks.add(decoded);
            totalSize += decoded.length;
        }

        // Combine all chunk byte arrays into one
        byte[] fileBytes = new byte[totalSize];
        int pos = 0;
        for (byte[] chunk : decodedChunks) {
            System.arraycopy(chunk, 0, fileBytes, pos, chunk.length);
            pos += chunk.length;
        }

        VerboseLogger.log("Successfully reassembled fileId " + fileId + " with size " + totalSize + " bytes");

        return fileBytes;
    }

    public synchronized void removeFile(String fileId) {
        store.remove(fileId);
        totalChunksMap.remove(fileId);
        VerboseLogger.log("Removed file data for fileId " + fileId);
    }

}
