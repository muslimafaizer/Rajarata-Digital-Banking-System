package pack_1.repository;

import pack_1.model.Admin;
import pack_1.model.Customer;
import pack_1.model.User;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite JDBC implementation of UserDao.
 * Demonstrates: Prepared Statements, Resource Management (try-with-resources), and Data Mapping.
 */
public class UserDaoImpl implements UserDao {

    @Override
    public void save(User user) {
        String sql = """
            INSERT INTO users (user_id, username, password_hash, email, phone, role, full_name, address, national_id, department, access_level, loan_balance, loan_active)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, user.getUserId());
            ps.setString(2, user.getUsername());
            ps.setString(3, user.getPasswordHash());
            ps.setString(4, user.getEmail());
            ps.setString(5, user.getPhone());
            ps.setString(6, user.getRole());

            if (user instanceof Customer c) {
                ps.setString(7, c.getFullName());
                ps.setString(8, c.getAddress());
                ps.setString(9, c.getNationalId());
                ps.setString(10, null);
                ps.setInt(11, 1);
                ps.setBigDecimal(12, c.getLoanBalance());
                ps.setBoolean(13, c.isLoanActive());
            } else if (user instanceof Admin a) {
                ps.setString(7, a.getUsername());
                ps.setString(8, "");
                ps.setString(9, null);
                ps.setString(10, a.getDepartment());
                ps.setInt(11, a.getAccessLevel());
                ps.setBigDecimal(12, BigDecimal.ZERO);
                ps.setBoolean(13, false);
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save user: " + e.getMessage(), e);
        }
    }

    @Override
    public void update(User user) {
        String sql = """
            UPDATE users SET username = ?, password_hash = ?, email = ?, phone = ?, full_name = ?, address = ?,
                             national_id = ?, department = ?, access_level = ?, loan_balance = ?, loan_active = ?
            WHERE user_id = ?
        """;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPasswordHash());
            ps.setString(3, user.getEmail());
            ps.setString(4, user.getPhone());

            if (user instanceof Customer c) {
                ps.setString(5, c.getFullName());
                ps.setString(6, c.getAddress());
                ps.setString(7, c.getNationalId());
                ps.setString(8, null);
                ps.setInt(9, 1);
                ps.setBigDecimal(10, c.getLoanBalance());
                ps.setBoolean(11, c.isLoanActive());
            } else if (user instanceof Admin a) {
                ps.setString(5, a.getUsername());
                ps.setString(6, "");
                ps.setString(7, null);
                ps.setString(8, a.getDepartment());
                ps.setInt(9, a.getAccessLevel());
                ps.setBigDecimal(10, BigDecimal.ZERO);
                ps.setBoolean(11, false);
            }
            ps.setString(12, user.getUserId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update user: " + e.getMessage(), e);
        }
    }

    @Override
    public User findById(String userId) {
        String sql = "SELECT * FROM users WHERE user_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapUser(rs);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error querying user by ID: " + e.getMessage(), e);
        }
        return null;
    }

    @Override
    public User findByUsername(String username) {
        if (username == null) return null;
        String sql = "SELECT * FROM users WHERE LOWER(username) = LOWER(?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapUser(rs);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error querying user by username: " + e.getMessage(), e);
        }
        return null;
    }

    @Override
    public User findByEmail(String email) {
        if (email == null) return null;
        String sql = "SELECT * FROM users WHERE LOWER(email) = LOWER(?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapUser(rs);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error querying user by email: " + e.getMessage(), e);
        }
        return null;
    }

    @Override
    public User findByNationalId(String nationalId) {
        if (nationalId == null) return null;
        String sql = "SELECT * FROM users WHERE UPPER(national_id) = UPPER(?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nationalId.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapUser(rs);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error querying user by national ID: " + e.getMessage(), e);
        }
        return null;
    }

    @Override
    public List<Customer> findAllCustomers() {
        List<Customer> list = new ArrayList<>();
        String sql = "SELECT * FROM users WHERE role = 'CUSTOMER' ORDER BY created_at ASC";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add((Customer) mapUser(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error querying customers: " + e.getMessage(), e);
        }
        return list;
    }

    @Override
    public List<Admin> findAllAdmins() {
        List<Admin> list = new ArrayList<>();
        String sql = "SELECT * FROM users WHERE role = 'ADMIN' ORDER BY created_at ASC";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add((Admin) mapUser(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error querying admins: " + e.getMessage(), e);
        }
        return list;
    }

    @Override
    public int getNextUserSequence() {
        String sql = "SELECT user_id FROM users WHERE user_id LIKE 'CUS%'";
        int max = 100;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String id = rs.getString(1);
                try {
                    int val = Integer.parseInt(id.substring(3));
                    if (val > max) max = val;
                } catch (NumberFormatException ignored) {}
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error reading next sequence: " + e.getMessage(), e);
        }
        return max + 1;
    }

    private User mapUser(ResultSet rs) throws SQLException {
        String role = rs.getString("role");
        String uid = rs.getString("user_id");
        String username = rs.getString("username");
        String pwHash = rs.getString("password_hash");
        String email = rs.getString("email");
        String phone = rs.getString("phone");

        if ("CUSTOMER".equalsIgnoreCase(role)) {
            Customer c = new Customer(uid, username, pwHash, email, phone,
                    rs.getString("full_name"), rs.getString("address"), rs.getString("national_id"));
            c.setLoanBalance(rs.getBigDecimal("loan_balance"));
            c.setLoanActive(rs.getBoolean("loan_active"));
            return c;
        } else {
            return new Admin(uid, username, pwHash, email, phone,
                    rs.getString("department"), rs.getInt("access_level"));
        }
    }
}
