package main.utils;

import java.util.Scanner;

public class ConsoleInput {
    public static String readLine(Scanner scanner, String prompt) {
        System.out.print(prompt);
        return scanner.nextLine();
    }
}
