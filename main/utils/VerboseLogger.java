package main.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public class VerboseLogger {
    private static boolean enabled = false;

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    private static String timestamp() {
        return "[" + LocalDateTime.now().format(formatter) + "]";
    }

    public static void log(String msg) {
        if (!enabled)
            return;
        System.out.println(timestamp() + " LOG > " + msg);
    }

    public static void recv(Map<String, String> msg, String fromIP) {
        if (!enabled)
            return;

        String type = msg.getOrDefault("TYPE", "UNKNOWN");
        System.out.println(timestamp() + " RECV < From: " + fromIP + ", TYPE: " + type);
        msg.forEach((k, v) -> System.out.println("   " + k + ": " + v));
    }

    public static void send(Map<String, String> msg, String toIP) {
        if (!enabled)
            return;

        String type = msg.getOrDefault("TYPE", "UNKNOWN");
        System.out.println(timestamp() + " SEND > To: " + toIP + ", TYPE: " + type);
        msg.forEach((k, v) -> System.out.println("   " + k + ": " + v));
    }

    public static void drop(String reason) {
        if (!enabled)
            return;
        System.out.println(timestamp() + " DROP ! " + reason);
    }

    public static void retry(String context, int attempt) {
        if (!enabled)
            return;
        System.out.println(timestamp() + " RETRY ~ " + context + " (attempt " + attempt + ")");
    }

    public static void ack(String ackInfo) {
        if (!enabled)
            return;
        System.out.println(timestamp() + " ACK ✔ " + ackInfo);
    }

    public static void token(String userId, boolean valid) {
        if (!enabled)
            return;
        System.out.println(timestamp() + " TOKEN ? " + userId + " → " + (valid ? "VALID" : "EXPIRED/INVALID"));
    }
}
