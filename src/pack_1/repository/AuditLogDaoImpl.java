package pack_1.repository;

import pack_1.model.AuditLog;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite JDBC implementation of AuditLogDao.
 */
public class AuditLogDaoImpl implements AuditLogDao {

    @Override
    public void save(AuditLog log) {
        String sql = "INSERT INTO audit_logs (user_id, action, details, ip_address, timestamp) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, log.getUserId());
            ps.setString(2, log.getAction());
            ps.setString(3, log.getDetails());
            ps.setString(4, log.getIpAddress());
            ps.setString(5, log.getTimestamp());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("  [WARN] Failed to write audit log: " + e.getMessage());
        }
    }

    @Override
    public List<AuditLog> findAll(int limit) {
        List<AuditLog> list = new ArrayList<>();
        String sql = "SELECT * FROM audit_logs ORDER BY created_at DESC, log_id DESC LIMIT ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit > 0 ? limit : 100);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new AuditLog(
                            rs.getLong("log_id"),
                            rs.getString("user_id"),
                            rs.getString("action"),
                            rs.getString("details"),
                            rs.getString("ip_address"),
                            rs.getString("timestamp")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error querying audit logs: " + e.getMessage(), e);
        }
        return list;
    }
}
