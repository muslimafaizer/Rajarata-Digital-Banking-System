package pack_1;

import java.io.Serializable;

/**
 * Abstract base class representing a system user.
 * OOP: Abstraction + Encapsulation
 */
public abstract class User implements Serializable {
    private static final long serialVersionUID = 1L;

    // Encapsulation: private fields
    private String userId;
    private String username;
    private String passwordHash;   // never the plain-text password
    private String email;
    private String phone;
    private String role;

    public User(String userId, String username, String passwordHash,
                String email, String phone, String role) {
        this.userId       = userId;
        this.username     = username;
        this.passwordHash = passwordHash;
        this.email        = email;
        this.phone        = phone;
        this.role         = role;
    }

    // Abstraction: subclasses MUST implement this
    public abstract String getDashboardInfo();

    // Encapsulation: Getters & Setters
    public String getUserId()            { return userId; }
    public String getUsername()          { return username; }
    public String getPasswordHash()      { return passwordHash; }
    public String getEmail()             { return email; }
    public String getPhone()             { return phone; }
    public String getRole()              { return role; }
    public void   setEmail(String e)     { this.email = e; }
    public void   setPhone(String p)     { this.phone = p; }

    /** Hashes and stores a new password. */
    public void changePassword(String newPlainPassword) {
        this.passwordHash = PasswordUtil.hash(newPlainPassword);
    }

    public boolean validatePassword(String input) {
        return PasswordUtil.verify(input, this.passwordHash);
    }

    public String toFileString() {
        return userId + "|" + username + "|" + passwordHash + "|"
             + email  + "|" + phone    + "|" + role;
    }

    @Override
    public String toString() {
        return "User[" + role + "] " + username + " (" + email + ")";
    }
}
