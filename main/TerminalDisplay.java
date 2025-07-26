package main.utils;

public class TerminalDisplay {
    public static void clearScreen() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }

    public static void displayPost(String author, String content) {
        System.out.println("\n=== NEW POST ===");
        System.out.println("From: " + author);
        System.out.println("Content: " + content);
        System.out.println("================");
    }

    public static void displayDM(String sender, String content) {
        System.out.println("\n=== DIRECT MESSAGE ===");
        System.out.println("From: " + sender);
        System.out.println("Content: " + content);
        System.out.println("=====================");
    }

    public static void displayPostsList(String[] posts) {
        clearScreen();
        System.out.println("\n=== RECENT POSTS ===");
        for (String post : posts) {
            System.out.println("- " + post);
            System.out.println("----------------");
        }
    }
}