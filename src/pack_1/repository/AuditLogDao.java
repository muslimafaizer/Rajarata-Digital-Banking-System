package pack_1.repository;

import pack_1.model.AuditLog;

import java.util.List;

/**
 * Data Access Object interface for AuditLog entities.
 */
public interface AuditLogDao {
    void save(AuditLog log);
    List<AuditLog> findAll(int limit);
}
