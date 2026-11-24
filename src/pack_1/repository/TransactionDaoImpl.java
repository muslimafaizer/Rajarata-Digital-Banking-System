package pack_1.repository;

import pack_1.model.Transaction;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite JDBC implementation of TransactionDao.
 * Demonstrates: Transaction persistence, parameterized filtering, atomic operations.
 */
public class TransactionDaoImpl implements TransactionDao {

    @Override
    public void save(Transaction transaction) {
        try (Connection conn = DatabaseManager.getConnection()) {
            saveWithConnection(conn, transaction);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save transaction: " + e.getMessage(), e);
        }
    }

    @Override
    public void saveWithConnection(Connection conn, Transaction transaction) {
        String sql = """
            INSERT INTO transactions (transaction_id, account_number, type, amount, balance_after, description, timestamp)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, transaction.getTransactionId());
            ps.setString(2, transaction.getAccountNumber());
            ps.setString(3, transaction.getType());
            ps.setBigDecimal(4, transaction.getAmount());
            ps.setBigDecimal(5, transaction.getBalanceAfter());
            ps.setString(6, transaction.getDescription());
            ps.setString(7, transaction.getTimestamp());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error persisting transaction: " + e.getMessage(), e);
        }
    }

    @Override
    public Transaction findById(String transactionId) {
        if (transactionId == null) return null;
        String sql = "SELECT * FROM transactions WHERE transaction_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, transactionId.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapTransaction(rs);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error querying transaction by id: " + e.getMessage(), e);
        }
        return null;
    }

    @Override
    public List<Transaction> findByAccountNumber(String accountNumber) {
        List<Transaction> list = new ArrayList<>();
        String sql = "SELECT * FROM transactions WHERE UPPER(account_number) = UPPER(?) ORDER BY created_at DESC, transaction_id DESC";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, accountNumber.trim());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapTransaction(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error querying transactions by account: " + e.getMessage(), e);
        }
        return list;
    }

    @Override
    public List<Transaction> findAll(int limit) {
        List<Transaction> list = new ArrayList<>();
        int max = limit > 0 ? limit : 200;
        String sql = "SELECT * FROM transactions ORDER BY created_at DESC, transaction_id DESC LIMIT ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, max);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapTransaction(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error querying all transactions: " + e.getMessage(), e);
        }
        return list;
    }

    @Override
    public List<Transaction> findFiltered(String accountNumber, String type, String search, int limit) {
        List<Transaction> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM transactions WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (accountNumber != null && !accountNumber.trim().isEmpty()) {
            sql.append(" AND UPPER(account_number) = ?");
            params.add(accountNumber.trim().toUpperCase());
        }
        if (type != null && !type.trim().isEmpty()) {
            sql.append(" AND UPPER(type) = ?");
            params.add(type.trim().toUpperCase());
        }
        if (search != null && !search.trim().isEmpty()) {
            sql.append(" AND (LOWER(description) LIKE ? OR LOWER(transaction_id) LIKE ?)");
            String term = "%" + search.trim().toLowerCase() + "%";
            params.add(term);
            params.add(term);
        }

        sql.append(" ORDER BY created_at DESC, transaction_id DESC LIMIT ?");
        params.add(limit > 0 ? limit : 100);

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapTransaction(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error querying filtered transactions: " + e.getMessage(), e);
        }
        return list;
    }

    private Transaction mapTransaction(ResultSet rs) throws SQLException {
        return new Transaction(
                rs.getString("transaction_id"),
                rs.getString("type"),
                rs.getBigDecimal("amount"),
                rs.getBigDecimal("balance_after"),
                rs.getString("description"),
                rs.getString("account_number"),
                rs.getString("timestamp")
        );
    }
}
