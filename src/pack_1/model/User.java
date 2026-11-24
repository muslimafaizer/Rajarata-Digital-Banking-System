package pack_1.model;

import pack_1.util.PasswordUtil;

import java.io.Serializable;

/**
 * Abstract base class representing a system user.
 * Demonstrates OOP:
 * - Abstraction: Cannot be instantiated directly; defines abstract getDashboardInfo()
 * - Encapsulation: Private attributes with controlled getters and setters
 */
public abstract class User implements Serializable {
    private static final long serialVersionUID = 1L;

    // Encapsulation: private attributes
    private String userId;
    private String username;
    private String passwordHash; // Never store plain-text passwords
    private String email;
    private String phone;
    private String role; // "CUSTOMER" or "ADMIN"

    public User(String userId, String username, String passwordHash,
                String email, String phone, String role) {
        this.userId = userId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.email = email;
        this.phone = phone;
        this.role = role;
    }

    // Abstraction: subclasses must implement their role-specific dashboard summary
    public abstract String getDashboardInfo();

    // Encapsulation: Getters
    public String getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getRole() {
        return role;
    }

    // Controlled setters
    public void setEmail(String email) {
        this.email = email;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /**
     * Hashes and securely updates the user's password.
     */
    public void changePassword(String newPlainPassword) {
        this.passwordHash = PasswordUtil.hash(newPlainPassword);
    }

    /**
     * Verifies the input password against the stored PBKDF2 hash.
     */
    public boolean validatePassword(String plainInput) {
        return PasswordUtil.verify(plainInput, this.passwordHash);
    }

    @Override
    public String toString() {
        return "User[" + role + "] " + username + " (" + email + ")";
    }
}
