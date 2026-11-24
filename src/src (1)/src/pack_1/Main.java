package pack_1;

import java.util.Scanner;

/**
 * Main — entry point for Digital Banking System.
 * Run this class in Eclipse: Right-click → Run As → Java Application
 */
public class Main {

    private static final Scanner sc   = new Scanner(System.in);
    private static       BankSystem bank;

    public static void main(String[] args) {
        printBanner();
        bank = new BankSystem();
        FileHandler.restoreSystem(bank);

        boolean running = true;
        while (running) {
            printMainMenu();
            int choice = readInt("  Enter choice: ");
            switch (choice) {
                case 1 -> handleRegister();
                case 2 -> handleLogin();
                case 3 -> {
                    System.out.println("\n  Thank you for using Digital Banking System.");
                    System.out.println("  Goodbye!\n");
                    running = false;
                }
                default -> System.out.println("  ❌  Invalid option. Please enter 1, 2, or 3.");
            }
        }
        sc.close();
    }

    // ─── BANNER ───────────────────────────────────────────────────────────

    private static void printBanner() {
        System.out.println("\n");
        System.out.println("  ╔══════════════════════════════════════════════════════╗");
        System.out.println("  ║                                                      ║");
        System.out.println("  ║        DIGITAL BANKING SYSTEM                        ║");
        System.out.println("  ║        Secure • Fast • Reliable                      ║");
        System.out.println("  ║                                                      ║");
        System.out.println("  ║   Features: Register | Login | Deposit | Withdraw    ║");
        System.out.println("  ║             Transfer | Loans | Bills | History       ║");
        System.out.println("  ║                                                      ║");
        System.out.println("  ╚══════════════════════════════════════════════════════╝");
    }

    private static void printMainMenu() {
        System.out.println("\n  ┌─────────────────────────────┐");
        System.out.println("  │         MAIN MENU           │");
        System.out.println("  ├─────────────────────────────┤");
        System.out.println("  │  1. Register                │");
        System.out.println("  │  2. Login                   │");
        System.out.println("  │  3. Exit                    │");
        System.out.println("  └─────────────────────────────┘");
    }

    // ─── REGISTER ─────────────────────────────────────────────────────────

    private static void handleRegister() {
        System.out.println("\n  ── Register New Customer ───────────────────────────");
        try {
            String username   = readString("  Username      : ");
            String password   = readString("  Password      : ");
            String email      = readString("  Email         : ");
            String phone      = readString("  Phone No      : ");
            String fullName   = readString("  Full Name     : ");
            String address    = readString("  Address       : ");
            String nationalId = readString("  National ID   : ");

            Customer c = bank.registerCustomer(
                    username, password, email, phone, fullName, address, nationalId);

            System.out.println("\n  ✅  Registration successful!");
            System.out.println("  ─────────────────────────────────────────────────");
            System.out.println("  Customer ID  : " + c.getUserId());
            System.out.println("  Account No   : "
                    + c.getPrimaryAccount().getAccountNumber()
                    + "  (Savings Account — auto created)");
            System.out.println("  ─────────────────────────────────────────────────");

        } catch (BankingException e) {
            System.out.println("\n  ❌  Registration failed: " + e.getMessage());
        }
    }

    // ─── LOGIN ────────────────────────────────────────────────────────────

    private static void handleLogin() {
        System.out.println("\n  ── Login ────────────────────────────────────────────");
        String username = readString("  Username : ");
        String password = readString("  Password : ");

        try {
            User user = bank.login(username, password);
            System.out.println("\n  ✅  Welcome back, " + user.getUsername() + "!");
            System.out.println(user.getDashboardInfo());

            if      (user instanceof Customer c) customerMenu(c);
            else if (user instanceof Admin    a) adminMenu(a);

        } catch (BankingException e) {
            System.out.println("\n  ❌  Login failed: " + e.getMessage());
        }
    }

    // ─── CUSTOMER MENU ────────────────────────────────────────────────────

    private static void customerMenu(Customer c) {
        boolean running = true;
        while (running) {
            System.out.println("\n  ┌─────────────────────────────────┐");
            System.out.println("  │         CUSTOMER MENU           │");
            System.out.println("  ├─────────────────────────────────┤");
            System.out.println("  │   1.  View Balance              │");
            System.out.println("  │   2.  Deposit                   │");
            System.out.println("  │   3.  Withdraw                  │");
            System.out.println("  │   4.  Transfer                  │");
            System.out.println("  │   5.  Transaction History       │");
            System.out.println("  │   6.  Open New Account          │");
            System.out.println("  │   7.  Apply for Loan            │");
            System.out.println("  │   8.  Repay Loan                │");
            System.out.println("  │   9.  Pay Bill                  │");
            System.out.println("  │  10.  Notifications             │");
            System.out.println("  │  11.  All My Accounts           │");
            System.out.println("  │   0.  Logout                    │");
            System.out.println("  └─────────────────────────────────┘");

            int choice = readInt("  Choice: ");
            switch (choice) {
                case 1  -> viewBalance(c);
                case 2  -> doDeposit(c);
                case 3  -> doWithdraw(c);
                case 4  -> doTransfer(c);
                case 5  -> viewHistory(c);
                case 6  -> openAccount(c);
                case 7  -> applyLoan(c);
                case 8  -> repayLoan(c);
                case 9  -> payBill(c);
                case 10 -> c.printNotifications();
                case 11 -> listAllAccounts(c);
                case 0  -> { System.out.println("\n  Logged out successfully."); running = false; }
                default -> System.out.println("  ❌  Invalid option.");
            }
        }
    }

    // ─── CUSTOMER ACTIONS ─────────────────────────────────────────────────

    private static void viewBalance(Customer c) {
        System.out.println("\n  ── Account Balances ────────────────────────────────");
        if (c.getAccounts().isEmpty()) { System.out.println("  No accounts."); return; }
        for (Account a : c.getAccounts()) a.printInfo();
        if (c.isLoanActive())
            System.out.printf("%n  ⚠  Outstanding Loan: LKR %,.2f%n", c.getLoanBalance());
    }

    private static void listAllAccounts(Customer c) {
        System.out.println("\n  ── All Your Accounts ───────────────────────────────");
        for (Account a : c.getAccounts()) {
            a.printInfo();
            System.out.println("     Features: " + a.getAccountFeatures());
        }
    }

    private static void doDeposit(Customer c) {
        String accNum = selectAccount(c);
        if (accNum == null) return;
        double amount = readDouble("  Deposit Amount (LKR): ");
        try {
            bank.deposit(c, accNum, amount);
            System.out.printf("  ✅  Deposited LKR %,.2f successfully.%n", amount);
        } catch (BankingException e) {
            System.out.println("  ❌  " + e.getMessage());
        }
    }

    private static void doWithdraw(Customer c) {
        String accNum = selectAccount(c);
        if (accNum == null) return;
        double amount = readDouble("  Withdrawal Amount (LKR): ");
        try {
            bank.withdraw(c, accNum, amount);
            System.out.printf("  ✅  Withdrawn LKR %,.2f successfully.%n", amount);
        } catch (BankingException e) {
            System.out.println("  ❌  " + e.getMessage());
        }
    }

    private static void doTransfer(Customer c) {
        System.out.println("\n  ── Transfer Funds ──────────────────────────────────");
        String fromNum = selectAccount(c);
        if (fromNum == null) return;
        String toNum = readString("  Destination Account No: ");
        double amount = readDouble("  Transfer Amount (LKR): ");
        try {
            bank.transfer(c, fromNum, toNum, amount);
            System.out.printf("  ✅  Transferred LKR %,.2f to %s.%n", amount, toNum);
        } catch (BankingException e) {
            System.out.println("  ❌  " + e.getMessage());
        }
    }

    private static void viewHistory(Customer c) {
        String accNum = selectAccount(c);
        if (accNum == null) return;
        Account acc = c.getAccountByNumber(accNum);
        if (acc != null) acc.printStatement();
    }

    private static void openAccount(Customer c) {
        System.out.println("\n  ── Open New Account ────────────────────────────────");
        System.out.println("  1. Savings       (3.5% p.a., daily limit LKR 100,000)");
        System.out.println("  2. Current       (1.0% p.a., overdraft LKR 50,000)");
        System.out.println("  3. Fixed Deposit (7.5% p.a., minimum LKR 10,000)");
        int choice = readInt("  Account Type (1/2/3): ");
        String type = switch (choice) {
            case 1 -> "SAVINGS";
            case 2 -> "CURRENT";
            case 3 -> "FIXED";
            default -> "";
        };
        if (type.isEmpty()) { System.out.println("  ❌  Invalid type."); return; }

        double initial  = readDouble("  Initial Deposit (LKR): ");
        int    fdMonths = 12;
        if ("FIXED".equals(type)) fdMonths = readInt("  Term in months: ");

        try {
            Account acc = bank.openAccount(c, type, initial, fdMonths);
            System.out.println("  ✅  Account opened: " + acc.getAccountNumber()
                    + " | " + acc.getAccountType()
                    + " | Balance: LKR " + String.format("%,.2f", acc.getBalance()));
        } catch (BankingException e) {
            System.out.println("  ❌  " + e.getMessage());
        }
    }

    private static void applyLoan(Customer c) {
        System.out.println("\n  ── Apply for Loan ──────────────────────────────────");
        if (c.isLoanActive()) {
            System.out.printf("  ⚠  You already have an active loan: LKR %,.2f remaining.%n",
                    c.getLoanBalance());
            return;
        }
        String accNum = selectAccount(c);
        if (accNum == null) return;
        double amount = readDouble("  Loan Amount (LKR, max 1,000,000): ");
        try {
            bank.applyLoan(c, accNum, amount);
            System.out.printf("  ✅  Loan of LKR %,.2f approved and credited to %s.%n",
                    amount, accNum);
        } catch (BankingException e) {
            System.out.println("  ❌  " + e.getMessage());
        }
    }

    private static void repayLoan(Customer c) {
        System.out.println("\n  ── Repay Loan ──────────────────────────────────────");
        if (!c.isLoanActive()) {
            System.out.println("  No active loan to repay.");
            return;
        }
        System.out.printf("  Outstanding Loan: LKR %,.2f%n", c.getLoanBalance());
        String accNum = selectAccount(c);
        if (accNum == null) return;
        double amount = readDouble("  Repayment Amount (LKR): ");
        try {
            bank.repayLoan(c, accNum, amount);
            if (c.isLoanActive())
                System.out.printf("  ✅  Repaid LKR %,.2f. Remaining: LKR %,.2f%n",
                        amount, c.getLoanBalance());
            else
                System.out.println("  ✅  Loan fully repaid! Congratulations.");
        } catch (BankingException e) {
            System.out.println("  ❌  " + e.getMessage());
        }
    }

    private static void payBill(Customer c) {
        System.out.println("\n  ── Pay Bill ────────────────────────────────────────");
        System.out.println("  Available bills:");
        BankSystem.getBills().forEach((k, v) ->
                System.out.printf("    %-15s : LKR %,.2f%n", k, v));
        String billType = readString("  Enter Bill Type: ");
        String accNum   = selectAccount(c);
        if (accNum == null) return;
        try {
            bank.payBill(c, accNum, billType);
            System.out.println("  ✅  Bill payment successful.");
        } catch (BankingException e) {
            System.out.println("  ❌  " + e.getMessage());
        }
    }

    // ─── ADMIN MENU ───────────────────────────────────────────────────────

    private static void adminMenu(Admin admin) {
        boolean running = true;
        while (running) {
            System.out.println("\n  ┌─────────────────────────────────┐");
            System.out.println("  │          ADMIN MENU             │");
            System.out.println("  ├─────────────────────────────────┤");
            System.out.println("  │   1.  View All Customers        │");
            System.out.println("  │   2.  View All Accounts         │");
            System.out.println("  │   3.  Lookup Customer           │");
            System.out.println("  │   0.  Logout                    │");
            System.out.println("  └─────────────────────────────────┘");

            int choice = readInt("  Choice: ");
            switch (choice) {
                case 1 -> admin.viewAllCustomers(bank.getCustomers());
                case 2 -> admin.viewAllAccounts(bank.getCustomers());
                case 3 -> {
                    String uname = readString("  Customer Username: ");
                    User   u     = bank.findUserByUsername(uname);
                    if (u instanceof Customer cust) {
                        System.out.println(cust.getDashboardInfo());
                        cust.printNotifications();
                        cust.getAccounts().forEach(Account::printInfo);
                    } else {
                        System.out.println("  Customer not found: " + uname);
                    }
                }
                case 0 -> { System.out.println("\n  Admin logged out."); running = false; }
                default -> System.out.println("  ❌  Invalid option.");
            }
        }
    }

    // ─── HELPERS ──────────────────────────────────────────────────────────

    private static String selectAccount(Customer c) {
        if (c.getAccounts().isEmpty()) {
            System.out.println("  No accounts available.");
            return null;
        }
        if (c.getAccounts().size() == 1)
            return c.getPrimaryAccount().getAccountNumber();

        System.out.println("\n  Select account:");
        for (int i = 0; i < c.getAccounts().size(); i++) {
            Account a = c.getAccounts().get(i);
            System.out.printf("  %d.  %-18s %-14s LKR %,.2f%n",
                    i + 1, a.getAccountNumber(), a.getAccountType(), a.getBalance());
        }
        int idx = readInt("  Account (1-" + c.getAccounts().size() + "): ") - 1;
        if (idx < 0 || idx >= c.getAccounts().size()) {
            System.out.println("  ❌  Invalid selection.");
            return null;
        }
        return c.getAccounts().get(idx).getAccountNumber();
    }

    private static String readString(String prompt) {
        System.out.print(prompt);
        return sc.nextLine().trim();
    }

    private static int readInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            try {
                return Integer.parseInt(sc.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("  ❌  Please enter a valid number.");
            }
        }
    }

    private static double readDouble(String prompt) {
        while (true) {
            System.out.print(prompt);
            try {
                double v = Double.parseDouble(sc.nextLine().trim());
                if (v < 0) { System.out.println("  ❌  Amount cannot be negative."); continue; }
                return v;
            } catch (NumberFormatException e) {
                System.out.println("  ❌  Please enter a valid amount.");
            }
        }
    }
}