package pack_1.repository;

import pack_1.model.*;
import pack_1.util.PasswordUtil;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages the SQLite database connection, table creation, and initial data migration.
 * Demonstrates: Database Persistence, JDBC, ACID Transactions, Relational Integrity.
 */
public class DatabaseManager {

    private static final String DB_DIR = "data";
    private static final String DB_FILE = DB_DIR + "/rajarata_bank.db";
    private static final String DB_URL = "jdbc:sqlite:" + DB_FILE;

    static {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            System.err.println("[ERROR] SQLite JDBC Driver not found in classpath: " + e.getMessage());
        }
    }

    public static Connection getConnection() throws SQLException {
        File dir = new File(DB_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        Connection conn = DriverManager.getConnection(DB_URL);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");
        }
        return conn;
    }

    /**
     * Initializes the database schema and performs data migration if needed.
     */
    public static synchronized void initializeDatabase() {
        try (Connection conn = getConnection()) {
            createTables(conn);
            migrateExistingTextFiles(conn);
            ensureDefaultAdmin(conn);
            System.out.println("  [✓] SQLite Database initialized successfully: " + DB_FILE);
        } catch (SQLException e) {
            System.err.println("  [ERROR] Failed to initialize SQLite database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void createTables(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // 1. Users table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    user_id VARCHAR(20) PRIMARY KEY,
                    username VARCHAR(50) UNIQUE NOT NULL,
                    password_hash VARCHAR(255) NOT NULL,
                    email VARCHAR(100) UNIQUE NOT NULL,
                    phone VARCHAR(20) NOT NULL,
                    role VARCHAR(20) NOT NULL CHECK(role IN ('CUSTOMER', 'ADMIN')),
                    full_name VARCHAR(100) NOT NULL,
                    address VARCHAR(200),
                    national_id VARCHAR(30) UNIQUE,
                    department VARCHAR(50),
                    access_level INTEGER DEFAULT 1,
                    loan_balance NUMERIC(15,2) DEFAULT 0.00,
                    loan_active BOOLEAN DEFAULT 0,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );
            """);

            // 2. Accounts table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS accounts (
                    account_number VARCHAR(30) PRIMARY KEY,
                    user_id VARCHAR(20) NOT NULL,
                    account_type VARCHAR(20) NOT NULL CHECK(account_type IN ('SAVINGS', 'CURRENT', 'FIXED_DEPOSIT')),
                    balance NUMERIC(15,2) NOT NULL DEFAULT 0.00,
                    interest_rate NUMERIC(5,2) NOT NULL DEFAULT 0.00,
                    date_opened VARCHAR(20) NOT NULL,
                    is_active BOOLEAN NOT NULL DEFAULT 1,
                    term_months INTEGER DEFAULT 0,
                    maturity_date VARCHAR(20),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
                );
            """);

            // 3. Transactions table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS transactions (
                    transaction_id VARCHAR(40) PRIMARY KEY,
                    account_number VARCHAR(30) NOT NULL,
                    type VARCHAR(20) NOT NULL CHECK(type IN ('DEP', 'WDR', 'TRF_OUT', 'TRF_IN', 'BILL', 'LOAN_REP', 'INT', 'FEE')),
                    amount NUMERIC(15,2) NOT NULL CHECK(amount > 0),
                    balance_after NUMERIC(15,2) NOT NULL,
                    description VARCHAR(255),
                    timestamp VARCHAR(30) NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (account_number) REFERENCES accounts(account_number) ON DELETE CASCADE
                );
            """);

            // 4. Beneficiaries table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS beneficiaries (
                    beneficiary_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id VARCHAR(20) NOT NULL,
                    beneficiary_account_no VARCHAR(30) NOT NULL,
                    beneficiary_name VARCHAR(100) NOT NULL,
                    nickname VARCHAR(50),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
                );
            """);

            // 5. Audit logs table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS audit_logs (
                    log_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id VARCHAR(20),
                    action VARCHAR(50) NOT NULL,
                    details TEXT,
                    ip_address VARCHAR(50),
                    timestamp VARCHAR(30) NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );
            """);

            // Indexes for fast lookup
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_accounts_user_id ON accounts(user_id);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_transactions_account ON transactions(account_number);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_transactions_created ON transactions(created_at);");
        }
    }

    /**
     * Seamlessly migrates existing customer and account data from flat files into the relational database.
     * Guarantees: Never deletes existing customer data; upgrades plain-text passwords to PBKDF2 hashes.
     */
    private static void migrateExistingTextFiles(Connection conn) {
        try {
            // Check if database already has users
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM users")) {
                if (rs.next() && rs.getInt(1) > 0) {
                    return; // Already populated
                }
            }

            File usersFile = new File("data/users.txt");
            if (!usersFile.exists()) {
                usersFile = new File("backup/data/users.txt");
            }
            if (!usersFile.exists()) {
                return;
            }

            System.out.println("  [i] Migrating existing customer records into SQLite relational database...");

            List<String[]> userLines = readPipedLines(usersFile);
            String insertUserSql = """
                INSERT INTO users (user_id, username, password_hash, email, phone, role, full_name, address, national_id, department, access_level, loan_balance, loan_active)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

            try (PreparedStatement ps = conn.prepareStatement(insertUserSql)) {
                for (String[] p : userLines) {
                    if (p.length < 6) continue;
                    String uid = p[0];
                    String username = p[1];
                    String pw = p[2];
                    String email = p[3];
                    String phone = p[4];
                    String role = p[5];

                    if (!PasswordUtil.isHashed(pw)) {
                        pw = PasswordUtil.hash(pw);
                    }

                    ps.setString(1, uid);
                    ps.setString(2, username);
                    ps.setString(3, pw);
                    ps.setString(4, email);
                    ps.setString(5, phone);
                    ps.setString(6, role);

                    if ("CUSTOMER".equalsIgnoreCase(role)) {
                        ps.setString(7, p.length > 6 ? p[6] : username);
                        ps.setString(8, p.length > 7 ? p[7] : "");
                        ps.setString(9, p.length > 8 ? p[8] : uid);
                        ps.setString(10, null);
                        ps.setInt(11, 1);
                        ps.setBigDecimal(12, p.length > 9 ? new BigDecimal(p[9]) : BigDecimal.ZERO);
                        ps.setBoolean(13, p.length > 10 && Boolean.parseBoolean(p[10]));
                    } else { // ADMIN
                        ps.setString(7, username);
                        ps.setString(8, "");
                        ps.setString(9, null);
                        ps.setString(10, p.length > 6 ? p[6] : "Operations");
                        ps.setInt(11, p.length > 7 ? Integer.parseInt(p[7]) : 5);
                        ps.setBigDecimal(12, BigDecimal.ZERO);
                        ps.setBoolean(13, false);
                    }
                    ps.executeUpdate();
                }
            }

            // 2. Migrate accounts
            File accsFile = new File("data/accounts.txt");
            if (!accsFile.exists()) accsFile = new File("backup/data/accounts.txt");
            if (accsFile.exists()) {
                List<String[]> accLines = readPipedLines(accsFile);
                String insertAccSql = """
                    INSERT INTO accounts (account_number, user_id, account_type, balance, interest_rate, date_opened, is_active, term_months, maturity_date)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
                try (PreparedStatement ps = conn.prepareStatement(insertAccSql)) {
                    for (String[] p : accLines) {
                        if (p.length < 4) continue;
                        String accNum = p[0];
                        String uid = p[1];
                        BigDecimal bal = new BigDecimal(p[2]).setScale(2, RoundingMode.HALF_UP);
                        String type = p[3].toUpperCase();
                        String dateOpened = p.length > 4 ? p[4] : "23-11-2026";
                        boolean active = p.length <= 5 || Boolean.parseBoolean(p[5]);
                        BigDecimal rate = "SAVINGS".equals(type) ? new BigDecimal("3.50") :
                                          "CURRENT".equals(type) ? new BigDecimal("1.00") : new BigDecimal("7.50");
                        int term = "FIXED_DEPOSIT".equals(type) ? 12 : 0;
                        String matDate = p.length > 6 ? p[6] : null;

                        ps.setString(1, accNum);
                        ps.setString(2, uid);
                        ps.setString(3, type);
                        ps.setBigDecimal(4, bal);
                        ps.setBigDecimal(5, rate);
                        ps.setString(6, dateOpened);
                        ps.setBoolean(7, active);
                        ps.setInt(8, term);
                        ps.setString(9, matDate);
                        ps.executeUpdate();
                    }
                }
            }

            // 3. Migrate transactions
            File txnsFile = new File("data/transactions.txt");
            if (!txnsFile.exists()) txnsFile = new File("backup/data/transactions.txt");
            if (txnsFile.exists()) {
                List<String[]> txnLines = readPipedLines(txnsFile);
                String insertTxnSql = """
                    INSERT INTO transactions (transaction_id, account_number, type, amount, balance_after, description, timestamp)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
                try (PreparedStatement ps = conn.prepareStatement(insertTxnSql)) {
                    for (String[] p : txnLines) {
                        if (p.length < 7) continue;
                        ps.setString(1, p[0]);
                        ps.setString(2, p[5]);
                        ps.setString(3, p[1]);
                        ps.setBigDecimal(4, new BigDecimal(p[2]).setScale(2, RoundingMode.HALF_UP));
                        ps.setBigDecimal(5, new BigDecimal(p[3]).setScale(2, RoundingMode.HALF_UP));
                        ps.setString(6, p[4]);
                        ps.setString(7, p[6]);
                        ps.executeUpdate();
                    }
                }
            }

            System.out.println("  [✓] Migration to SQLite completed successfully.");
        } catch (Exception e) {
            System.err.println("  [WARN] Data migration error: " + e.getMessage());
        }
    }

    private static void ensureDefaultAdmin(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM users WHERE role = 'ADMIN'")) {
            ResultSet rs = ps.executeQuery();
            if (rs.next() && rs.getInt(1) == 0) {
                String insertAdmin = """
                    INSERT INTO users (user_id, username, password_hash, email, phone, role, full_name, address, national_id, department, access_level, loan_balance, loan_active)
                    VALUES ('ADM001', 'admin', ?, 'admin@rajarata.lk', '0771234567', 'ADMIN', 'System Administrator', 'Rajarata Bank HQ', NULL, 'Operations', 5, 0.0, 0)
                """;
                try (PreparedStatement ins = conn.prepareStatement(insertAdmin)) {
                    ins.setString(1, PasswordUtil.hash("admin123"));
                    ins.executeUpdate();
                    System.out.println("  [✓] Default administrator created: admin / admin123");
                }
            }
        }
    }

    private static List<String[]> readPipedLines(File file) {
        List<String[]> lines = new ArrayList<>();
        if (!file.exists()) return lines;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    lines.add(line.split("\\|", -1));
                }
            }
        } catch (IOException e) {
            System.err.println("  [WARN] Failed reading file " + file.getPath() + ": " + e.getMessage());
        }
        return lines;
    }
}
