package main.utils;

import java.util.*;

public class FileChunker {
    public static List<String> chunkFile(byte[] fileBytes, int chunkSize) {
        List<String> chunks = new ArrayList<>();
        int totalLength = fileBytes.length;
        int offset = 0;
        int chunkNum = 1;

        System.out.println(
                "Starting to chunk file of size " + totalLength + " bytes with chunk size " + chunkSize);

        while (offset < totalLength) {
            int end = Math.min(offset + chunkSize, totalLength);
            byte[] chunk = new byte[end - offset];
            System.arraycopy(fileBytes, offset, chunk, 0, chunk.length);
            String base64Chunk = Base64.getEncoder().encodeToString(chunk);
            chunks.add(base64Chunk);
            System.out.println("Created chunk #" + chunkNum + " with byte size " + chunk.length);
            offset += chunkSize;
            chunkNum++;
        }

        System.out.println("Completed chunking into " + chunks.size() + " chunks.");

        return chunks;
    }
}
