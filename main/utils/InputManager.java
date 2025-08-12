package main.utils;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

public class InputManager {
    public static class InputRequest {
        public final String prompt;
        public final BlockingQueue<String> responseQueue = new LinkedBlockingQueue<>();
        public final Consumer<String> responseConsumer;

        public InputRequest(String prompt) {
            this.prompt = prompt;
            this.responseConsumer = null;
        }

        public InputRequest(String prompt, Consumer<String> consumer) {
            this.prompt = prompt;
            this.responseConsumer = consumer;
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

    public static void requestInput(String prompt, Consumer<String> consumer) {
        InputRequest req = new InputRequest(prompt, consumer);
        requestQueue.add(req);
    }

    public static InputRequest pollRequest() {
        return requestQueue.poll();
    }

    public static BlockingQueue<InputRequest> getRequestQueue() {
        return requestQueue;
    }
}
