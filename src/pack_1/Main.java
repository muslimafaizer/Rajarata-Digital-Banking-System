package pack_1;

import pack_1.exception.BankingException;
import pack_1.model.*;

import java.math.BigDecimal;
import java.util.Scanner;

/**
 * Main — CLI entry point and demonstration harness for university presentations.
 * Demonstrates:
 * - OOP Concepts: Encapsulation, Inheritance, Polymorphism, Abstraction, Composition
 * - Interactive console interface for quick demonstration
 * - Option to start the embedded Web Server
 */
public class Main {

    private static final Scanner scanner = new Scanner(System.in);
    private static BankSystem bank;

    public static void main(String[] args) {
        printBanner();
        bank = new BankSystem();

        boolean running = true;
        while (running) {
            printMainMenu();
            int choice = readInt("  Enter choice: ");
            switch (choice) {
                case 1 -> startWebServer();
                case 2 -> handleRegister();
                case 3 -> handleLogin();
                case 4 -> demonstrateOopConcepts();
                case 5 -> {
                    System.out.println("\n  Thank you for using Rajarata Digital Banking System.");
                    System.out.println("  Goodbye!\n");
                    running = false;
                }
                default -> System.out.println("  ❌ Invalid option. Please enter 1 to 5.");
            }
        }
        scanner.close();
    }

    private static void printBanner() {
        System.out.println("\n");
        System.out.println("  ╔══════════════════════════════════════════════════════════╗");
        System.out.println("  ║                                                          ║");
        System.out.println("  ║            RAJARATA DIGITAL BANKING SYSTEM               ║");
        System.out.println("  ║           Secure • Professional • Persistent             ║");
        System.out.println("  ║                                                          ║");
        System.out.println("  ║     Java OOP Architecture & SQLite JDBC Relational DB    ║");
        System.out.println("  ║                                                          ║");
        System.out.println("  ╚══════════════════════════════════════════════════════════╝");
    }

    private static void printMainMenu() {
        System.out.println("\n  ┌────────────────────────────────────────────────────────┐");
        System.out.println("  │                       MAIN MENU                        │");
        System.out.println("  ├────────────────────────────────────────────────────────┤");
        System.out.println("  │  1. Start Web Server (http://localhost:8080)           │");
        System.out.println("  │  2. Register New Customer                              │");
        System.out.println("  │  3. Customer / Admin Login                             │");
        System.out.println("  │  4. Demonstrate OOP Concepts (Polymorphism & Abstraction)│");
        System.out.println("  │  5. Exit                                               │");
        System.out.println("  └────────────────────────────────────────────────────────┘");
    }

    private static void startWebServer() {
        System.out.println("\n  Starting embedded web server...");
        try {
            pack_1.controller.WebServer.startServer(8080);
            System.out.println("  ✓ Web server is active. Open http://localhost:8080 in your browser.");
        } catch (Exception e) {
            System.out.println("  ❌ Web server startup notice: " + e.getMessage());
        }
    }

    private static void handleRegister() {
        System.out.println("\n  ── Customer Registration ─────────────────────────────");
        try {
            String username   = readString("  Username      : ");
            String password   = readString("  Password      : ");
            String email      = readString("  Email         : ");
            String phone      = readString("  Phone No      : ");
            String fullName   = readString("  Full Name     : ");
            String address    = readString("  Address       : ");
            String nationalId = readString("  National ID   : ");

            Customer customer = bank.registerCustomer(username, password, email, phone, fullName, address, nationalId);
            System.out.println("\n  ✅ Registration Successful!");
            System.out.println("  Customer ID : " + customer.getUserId());
            System.out.println("  Account No  : " + customer.getPrimaryAccount().getAccountNumber() + " (Savings Account)");
        } catch (BankingException e) {
            System.out.println("\n  ❌ Registration failed: " + e.getMessage());
        }
    }

    private static void handleLogin() {
        System.out.println("\n  ── User Login ────────────────────────────────────────");
        String username = readString("  Username : ");
        String password = readString("  Password : ");

        try {
            User user = bank.login(username, password);
            System.out.println("\n  ✅ Welcome back, " + user.getUsername() + " [" + user.getRole() + "]!");
            System.out.println("  " + user.getDashboardInfo());

            if (user instanceof Customer c) {
                customerSession(c);
            }
        } catch (BankingException e) {
            System.out.println("\n  ❌ Login failed: " + e.getMessage());
        }
    }

    private static void customerSession(Customer c) {
        boolean inSession = true;
        while (inSession) {
            System.out.println("\n  ── Customer Actions (" + c.getUsername() + ") ──");
            System.out.println("  1. View Accounts & Balances");
            System.out.println("  2. Deposit Funds");
            System.out.println("  3. Withdraw Funds");
            System.out.println("  4. Transfer Money");
            System.out.println("  5. Return to Main Menu");
            int act = readInt("  Select: ");
            try {
                switch (act) {
                    case 1 -> {
                        for (Account a : bank.getCustomerAccounts(c.getUserId())) {
                            System.out.printf("  • %-12s | %-14s | Balance: LKR %,12.2f | Status: %s%n",
                                    a.getAccountNumber(), a.getAccountType(), a.getBalance(), a.isActive() ? "ACTIVE" : "INACTIVE");
                        }
                    }
                    case 2 -> {
                        String acc = readString("  Account Number: ");
                        double amt = readDouble("  Amount (LKR): ");
                        bank.deposit(c, acc, amt);
                        System.out.println("  ✅ Deposit successful.");
                    }
                    case 3 -> {
                        String acc = readString("  Account Number: ");
                        double amt = readDouble("  Amount (LKR): ");
                        bank.withdraw(c, acc, amt);
                        System.out.println("  ✅ Withdrawal successful.");
                    }
                    case 4 -> {
                        String from = readString("  From Account : ");
                        String to   = readString("  To Account   : ");
                        double amt  = readDouble("  Amount (LKR) : ");
                        bank.transfer(c, from, to, amt);
                        System.out.println("  ✅ Transfer successful.");
                    }
                    case 5 -> inSession = false;
                }
            } catch (BankingException e) {
                System.out.println("  ❌ Operation failed: " + e.getMessage());
            }
        }
    }

    private static void demonstrateOopConcepts() {
        System.out.println("\n  ═════════════════════════════════════════════════════════════");
        System.out.println("            OBJECT-ORIENTED PROGRAMMING (OOP) DEMONSTRATION     ");
        System.out.println("  ═════════════════════════════════════════════════════════════");

        // 1. Abstraction & Inheritance
        System.out.println("\n  1. INHERITANCE & ABSTRACTION:");
        System.out.println("     Abstract class User -> Subclasses Customer & Admin");
        System.out.println("     Abstract class Account -> Subclasses SavingsAccount, CurrentAccount, FixedDepositAccount");

        // 2. Polymorphism Demonstration
        System.out.println("\n  2. POLYMORPHISM (Dynamic Method Dispatch):");
        Account[] accounts = {
                new SavingsAccount("SAV9990001", "CUS999", new BigDecimal("10000.00")),
                new CurrentAccount("CUR9990001", "CUS999", new BigDecimal("5000.00")),
                new FixedDepositAccount("FD9990001", "CUS999", new BigDecimal("50000.00"), 12)
        };

        for (Account a : accounts) {
            System.out.println("\n     [Account Instance: " + a.getClass().getSimpleName() + "]");
            System.out.println("     • Account Number : " + a.getAccountNumber());
            System.out.println("     • Interest Rate  : " + a.getInterestRate() + "% p.a. (Polymorphic method getInterestRate)");
            System.out.println("     • Monthly Yield  : LKR " + a.calculateMonthlyInterest() + " (Polymorphic method calculateMonthlyInterest)");
            System.out.println("     • Features       : " + a.getAccountFeatures());
        }

        // 3. Encapsulation
        System.out.println("\n  3. ENCAPSULATION:");
        System.out.println("     Account balance cannot be modified directly (balance field is private).");
        System.out.println("     Changes occur strictly via validated deposit() and withdraw() business logic.");
        System.out.println("  ═════════════════════════════════════════════════════════════");
    }

    private static int readInt(String prompt) {
        System.out.print(prompt);
        try {
            return Integer.parseInt(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static double readDouble(String prompt) {
        System.out.print(prompt);
        try {
            return Double.parseDouble(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            return -1.0;
        }
    }

    private static String readString(String prompt) {
        System.out.print(prompt);
        return scanner.nextLine().trim();
    }
}
