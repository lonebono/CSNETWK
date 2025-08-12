package main.utils;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class InputManager {
    public static class InputRequest {
        public final String prompt;
        public final BlockingQueue<String> responseQueue = new LinkedBlockingQueue<>();

        public InputRequest(String prompt) {
            this.prompt = prompt;
        }
    }

    private static final BlockingQueue<InputRequest> requestQueue = new LinkedBlockingQueue<>();

    public static String requestInput(String prompt) {
        InputRequest req = new InputRequest(prompt);
        requestQueue.add(req);
        try {
            return req.responseQueue.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    public static BlockingQueue<InputRequest> getRequestQueue() {
        return requestQueue;
    }
}
