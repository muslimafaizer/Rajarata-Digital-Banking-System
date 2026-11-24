package pack_1.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * System audit log for security, administrative actions, and traceability.
 */
public class AuditLog {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    private long logId;
    private final String userId;
    private final String action;
    private final String details;
    private final String ipAddress;
    private final String timestamp;

    public AuditLog(String userId, String action, String details, String ipAddress) {
        this(0, userId, action, details, ipAddress, LocalDateTime.now().format(FMT));
    }

    public AuditLog(long logId, String userId, String action, String details, String ipAddress, String timestamp) {
        this.logId = logId;
        this.userId = userId;
        this.action = action;
        this.details = details;
        this.ipAddress = ipAddress;
        this.timestamp = timestamp;
    }

    public long getLogId() {
        return logId;
    }

    public void setLogId(long logId) {
        this.logId = logId;
    }

    public String getUserId() {
        return userId;
    }

    public String getAction() {
        return action;
    }

    public String getDetails() {
        return details;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getTimestamp() {
        return timestamp;
    }
}
