package main.utils;

import java.util.Scanner;

public class ConsoleInput {
    private static final Scanner scanner = new Scanner(System.in);

    public static synchronized String readLine(String prompt) {
        System.out.print(prompt);
        return scanner.nextLine();
    }
}
