package pack_1.model;

/**
 * Admin class representing a bank administrator.
 * Demonstrates OOP:
 * - Inheritance: Extends User
 * - Polymorphism: Concrete implementation of getDashboardInfo()
 */
public class Admin extends User {
    private static final long serialVersionUID = 1L;

    private String department;
    private int accessLevel;

    public Admin(String userId, String username, String passwordHash,
                 String email, String phone,
                 String department, int accessLevel) {
        super(userId, username, passwordHash, email, phone, "ADMIN");
        this.department = department;
        this.accessLevel = accessLevel;
    }

    @Override
    public String getDashboardInfo() {
        return "Admin: " + getUsername() + " | Dept: " + department + " | Access Level: " + accessLevel;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public int getAccessLevel() {
        return accessLevel;
    }

    public void setAccessLevel(int accessLevel) {
        this.accessLevel = accessLevel;
    }
}
