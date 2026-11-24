package pack_1.repository;

import pack_1.model.Account;
import pack_1.model.CurrentAccount;
import pack_1.model.FixedDepositAccount;
import pack_1.model.SavingsAccount;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite JDBC implementation of AccountDao.
 * Demonstrates: Polymorphic instantiation based on account_type, prepared statements.
 */
public class AccountDaoImpl implements AccountDao {

    @Override
    public void save(Account account) {
        String sql = """
            INSERT INTO accounts (account_number, user_id, account_type, balance, interest_rate, date_opened, is_active, term_months, maturity_date)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, account.getAccountNumber());
            ps.setString(2, account.getOwnerId());
            ps.setString(3, account.getAccountType());
            ps.setBigDecimal(4, account.getBalance());
            ps.setBigDecimal(5, account.getInterestRate());
            ps.setString(6, account.getDateOpened());
            ps.setBoolean(7, account.isActive());

            if (account instanceof FixedDepositAccount fd) {
                ps.setInt(8, fd.getTermMonths());
                ps.setString(9, fd.getMaturityDate() != null ? fd.getMaturityDate().toString() : null);
            } else {
                ps.setInt(8, 0);
                ps.setString(9, null);
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save account: " + e.getMessage(), e);
        }
    }

    @Override
    public void update(Account account) {
        String sql = """
            UPDATE accounts SET balance = ?, is_active = ?, term_months = ?, maturity_date = ?
            WHERE account_number = ?
        """;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setBigDecimal(1, account.getBalance());
            ps.setBoolean(2, account.isActive());
            if (account instanceof FixedDepositAccount fd) {
                ps.setInt(3, fd.getTermMonths());
                ps.setString(4, fd.getMaturityDate() != null ? fd.getMaturityDate().toString() : null);
            } else {
                ps.setInt(3, 0);
                ps.setString(4, null);
            }
            ps.setString(5, account.getAccountNumber());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update account: " + e.getMessage(), e);
        }
    }

    @Override
    public Account findByAccountNumber(String accountNumber) {
        if (accountNumber == null) return null;
        String sql = "SELECT * FROM accounts WHERE UPPER(account_number) = UPPER(?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, accountNumber.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapAccount(rs);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error querying account by number: " + e.getMessage(), e);
        }
        return null;
    }

    @Override
    public List<Account> findByUserId(String userId) {
        List<Account> list = new ArrayList<>();
        String sql = "SELECT * FROM accounts WHERE user_id = ? ORDER BY date_opened ASC, account_number ASC";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapAccount(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error querying accounts for user: " + e.getMessage(), e);
        }
        return list;
    }

    @Override
    public List<Account> findAll() {
        List<Account> list = new ArrayList<>();
        String sql = "SELECT * FROM accounts ORDER BY created_at ASC";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapAccount(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error querying all accounts: " + e.getMessage(), e);
        }
        return list;
    }

    @Override
    public void toggleActiveStatus(String accountNumber, boolean active) {
        String sql = "UPDATE accounts SET is_active = ? WHERE UPPER(account_number) = UPPER(?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, active);
            ps.setString(2, accountNumber.trim());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update account active status: " + e.getMessage(), e);
        }
    }

    @Override
    public int getNextAccountSequence() {
        String sql = "SELECT account_number FROM accounts";
        int max = 1000;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String num = rs.getString(1);
                if (num != null && num.length() > 3) {
                    try {
                        int val = Integer.parseInt(num.substring(3));
                        if (val > max) max = val;
                    } catch (NumberFormatException ignored) {}
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error reading account sequence: " + e.getMessage(), e);
        }
        return max + 1;
    }

    private Account mapAccount(ResultSet rs) throws SQLException {
        String accNum = rs.getString("account_number");
        String userId = rs.getString("user_id");
        BigDecimal balance = rs.getBigDecimal("balance");
        String type = rs.getString("account_type");
        String dateOpened = rs.getString("date_opened");
        boolean active = rs.getBoolean("is_active");
        int termMonths = rs.getInt("term_months");
        String maturityStr = rs.getString("maturity_date");

        Account acc;
        switch (type.toUpperCase()) {
            case "CURRENT" -> acc = new CurrentAccount(accNum, userId, balance);
            case "FIXED_DEPOSIT" -> {
                FixedDepositAccount fd = new FixedDepositAccount(accNum, userId, balance, termMonths > 0 ? termMonths : 12);
                if (maturityStr != null && !maturityStr.trim().isEmpty()) {
                    try {
                        fd.setMaturityDate(LocalDate.parse(maturityStr.trim()));
                    } catch (Exception ignored) {}
                }
                acc = fd;
            }
            default -> acc = new SavingsAccount(accNum, userId, balance);
        }
        acc.restoreState(dateOpened, active);
        return acc;
    }
}
