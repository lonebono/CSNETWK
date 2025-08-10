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

    public static void displayFileOffer(String sender, String filename) {
        System.out.println("\n=== FILE OFFER ===");
        System.out.println("\nUser " + sender + " is sending you a file: " + filename + ". Do you accept?");
        System.out.println("----------------");
    }

    public static void displayGroupCreate(String groupName) {
        System.out.println("\n=== GROUP CREATE ===");
        System.out.println("You’ve been added to " + groupName);
        System.out.println("----------------");
    }

    public static void displayGroupUpdate(String groupName) {
        System.out.println("\n=== GROUP UPDATE ===");
        System.out.println("The group \"" + groupName + "\" member list was updated.");
        System.out.println("----------------");
    }

    public static void displayGroupMessage(String from, String content) {
        System.out.println("\n=== GROUP MESSAGE ===");
        System.out.println(from + " sent \"" + content + "\"");
        System.out.println("----------------");
    }

}