package pack_1;

import java.util.List;

/**
 * Admin — system administrator.
 * OOP: Inheritance (extends User) + Polymorphism (getDashboardInfo)
 */
public class Admin extends User {
    private static final long serialVersionUID = 1L;

    private String department;
    private int    accessLevel;

    public Admin(String userId, String username, String password,
                 String email,  String phone,
                 String department, int accessLevel) {
        super(userId, username, password, email, phone, "ADMIN");
        this.department  = department;
        this.accessLevel = accessLevel;
    }

    // Polymorphism: concrete version of abstract method from User
    @Override
    public String getDashboardInfo() {
        return "\n  ╔══════════════════════════════════════╗"
             + "\n  ║          ADMIN DASHBOARD             ║"
             + "\n  ╠══════════════════════════════════════╣"
             + "\n  ║  Admin   : " + pad(getUsername(),  26) + "║"
             + "\n  ║  Dept    : " + pad(department,     26) + "║"
             + "\n  ║  Access  : Level " + pad(String.valueOf(accessLevel), 21) + "║"
             + "\n  ╚══════════════════════════════════════╝";
    }

    private String pad(String s, int n) { return String.format("%-" + n + "s", s); }

    public void viewAllCustomers(List<Customer> customers) {
        System.out.println("\n  ── All Registered Customers ───────────────");
        if (customers.isEmpty()) { System.out.println("  None."); return; }
        System.out.printf("  %-8s %-20s %-25s %-10s%n",
                "ID", "Full Name", "Email", "Accounts");
        System.out.println("  " + "─".repeat(68));
        for (Customer c : customers)
            System.out.printf("  %-8s %-20s %-25s %-10d%n",
                c.getUserId(), c.getFullName(), c.getEmail(), c.getAccounts().size());
    }

    public void viewAllAccounts(List<Customer> customers) {
        System.out.println("\n  ── All Accounts ────────────────────────────");
        System.out.printf("  %-18s %-20s %-14s %14s%n",
                "Account No", "Owner", "Type", "Balance (LKR)");
        System.out.println("  " + "─".repeat(70));
        for (Customer c : customers)
            for (Account acc : c.getAccounts())
                System.out.printf("  %-18s %-20s %-14s %,14.2f%n",
                    acc.getAccountNumber(), c.getFullName(),
                    acc.getAccountType(),   acc.getBalance());
    }

    public String getDepartment()  { return department; }
    public int    getAccessLevel() { return accessLevel; }

    @Override
    public String toFileString() {
        return super.toFileString() + "|" + department + "|" + accessLevel;
    }
}