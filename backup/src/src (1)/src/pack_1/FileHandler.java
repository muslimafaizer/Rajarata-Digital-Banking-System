package pack_1;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

/**
 * FileHandler — reads and writes users.txt, accounts.txt, transactions.txt.
 * OOP: File Handling. Files are written to a temp file first and then moved into place,
 * so a crash in the middle of saving can never leave a half-written data file.
 */
public class FileHandler {

    private static final String DATA_DIR  = "data/";
    public  static final String USERS_FILE = DATA_DIR + "users.txt";
    public  static final String ACCS_FILE  = DATA_DIR + "accounts.txt";
    public  static final String TXNS_FILE  = DATA_DIR + "transactions.txt";

    static {
        new File(DATA_DIR).mkdirs(); // create data/ folder automatically
    }

    // ── SAVE ─────────────────────────────────────────────────────────────

    private static void writeAtomically(String file, List<String> lines) {
        Path target = Paths.get(file);
        Path tmp    = Paths.get(file + ".tmp");
        try {
            Files.write(tmp, lines, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            System.err.println("  [ERROR] Saving " + file + ": " + e.getMessage());
        }
    }

    public static void saveAllUsers(List<Customer> customers, List<Admin> admins) {
        List<String> lines = new ArrayList<>();
        for (Customer c : customers) lines.add(c.toFileString());
        for (Admin    a : admins)    lines.add(a.toFileString());
        writeAtomically(USERS_FILE, lines);
    }

    public static void saveAllAccounts(List<Customer> customers) {
        List<String> lines = new ArrayList<>();
        for (Customer c : customers)
            for (Account a : c.getAccounts()) lines.add(a.toFileString());
        writeAtomically(ACCS_FILE, lines);
    }

    public static void saveAllTransactions(List<Customer> customers) {
        List<String> lines = new ArrayList<>();
        for (Customer c : customers)
            for (Account a : c.getAccounts())
                for (Transaction t : a.getTransactions()) lines.add(t.toFileString());
        writeAtomically(TXNS_FILE, lines);
    }

    public static void saveAll(List<Customer> customers, List<Admin> admins) {
        saveAllUsers(customers, admins);
        saveAllAccounts(customers);
        saveAllTransactions(customers);
    }

    // ── LOAD ─────────────────────────────────────────────────────────────

    private static List<String[]> readLines(String filePath) {
        List<String[]> result = new ArrayList<>();
        File f = new File(filePath);
        if (!f.exists()) return result;
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) result.add(line.split("\\|", -1));
            }
        } catch (IOException e) {
            System.err.println("  [ERROR] Reading " + filePath + ": " + e.getMessage());
        }
        return result;
    }

    // ── RESTORE SYSTEM ────────────────────────────────────────────────────

    public static void restoreSystem(BankSystem bank) {
        List<String[]> userLines = readLines(USERS_FILE);
        List<String[]> acctLines = readLines(ACCS_FILE);
        List<String[]> txnLines  = readLines(TXNS_FILE);
        boolean migrated = false;

        // 1. Restore users (plain-text passwords from older versions are hashed automatically)
        for (String[] p : userLines) {
            try {
                String role = p[5];
                String pw   = p[2];
                if (!PasswordUtil.isHashed(pw)) { pw = PasswordUtil.hash(pw); migrated = true; }

                if ("CUSTOMER".equals(role) && p.length >= 9) {
                    Customer c = new Customer(p[0], p[1], pw, p[3], p[4], p[6], p[7], p[8]);
                    if (p.length >= 11) {
                        c.setLoanBalance(Double.parseDouble(p[9]));
                        c.setLoanActive(Boolean.parseBoolean(p[10]));
                    }
                    bank.addCustomerDirect(c);
                } else if ("ADMIN".equals(role) && p.length >= 8) {
                    bank.addAdminDirect(new Admin(p[0], p[1], pw, p[3], p[4],
                            p[6], Integer.parseInt(p[7])));
                }
            } catch (Exception e) {
                System.err.println("  [WARN] Skipping bad user record: " + e.getMessage());
            }
        }

        // 2. Restore accounts (type, balance, date opened, active flag, FD maturity date)
        Map<String, Account> byNumber = new HashMap<>();
        for (String[] p : acctLines) {
            try {
                String accNum  = p[0];
                String ownerId = p[1];
                double balance = Double.parseDouble(p[2]);
                String type    = p[3];

                Customer owner = bank.findCustomerById(ownerId);
                if (owner == null) continue;

                Account acc = switch (type) {
                    case "CURRENT"       -> new CurrentAccount(accNum, ownerId, balance);
                    case "FIXED_DEPOSIT" -> new FixedDepositAccount(accNum, ownerId, balance, 12);
                    default              -> new SavingsAccount(accNum, ownerId, balance);
                };
                acc.restoreState(p.length > 4 ? p[4] : null,
                                 p.length > 5 ? Boolean.parseBoolean(p[5]) : true);
                if (acc instanceof FixedDepositAccount fd && p.length > 6 && !p[6].isEmpty())
                    fd.setMaturityDate(LocalDate.parse(p[6]));

                owner.getAccounts().add(acc);   // restored silently: no "new account" notification
                byNumber.put(accNum, acc);
            } catch (Exception e) {
                System.err.println("  [WARN] Skipping bad account record.");
            }
        }

        // 3. Restore transaction history
        for (String[] p : txnLines) {
            try {
                Account acc = byNumber.get(p[5]);
                if (acc == null) continue;
                acc.addTransaction(new Transaction(p[0], p[1], Double.parseDouble(p[2]),
                        Double.parseDouble(p[3]), p[4], p[5], p[6]));
            } catch (Exception e) {
                System.err.println("  [WARN] Skipping bad transaction record.");
            }
        }

        bank.ensureDefaultAdmin();
        bank.syncCounters();
        if (migrated) {
            bank.getCustomers();   // data is in memory; write the hashed passwords back to disk
            saveAll(bank.getCustomers(), bank.getAdmins());
            System.out.println("  [✓] Old plain-text passwords were converted to secure hashes.");
        }
        if (!userLines.isEmpty())
            System.out.println("  [✓] Data restored from files.");
    }
}
