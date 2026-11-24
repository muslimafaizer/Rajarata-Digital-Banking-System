package pack_1;

import java.util.ArrayList;
import java.util.List;

/**
 * Customer — a bank customer.
 * OOP: Inheritance (extends User) + Composition (has Accounts + Notifications)
 */
public class Customer extends User {
    private static final long serialVersionUID = 1L;

    private String       fullName;
    private String       address;
    private String       nationalId;
    private List<Account> accounts;       // Composition: Customer HAS Accounts
    private List<String>  notifications;  // Composition
    private double        loanBalance;
    private boolean       loanActive;

    public Customer(String userId, String username, String password,
                    String email,  String phone,
                    String fullName, String address, String nationalId) {
        super(userId, username, password, email, phone, "CUSTOMER");
        this.fullName      = fullName;
        this.address       = address;
        this.nationalId    = nationalId;
        this.accounts      = new ArrayList<>();
        this.notifications = new ArrayList<>();
        this.loanBalance   = 0.0;
        this.loanActive    = false;
    }

    // Polymorphism: concrete version of abstract method from User
    @Override
    public String getDashboardInfo() {
        return "\n  ╔══════════════════════════════════════╗"
             + "\n  ║        CUSTOMER DASHBOARD            ║"
             + "\n  ╠══════════════════════════════════════╣"
             + "\n  ║  Name    : " + pad(fullName,   26) + "║"
             + "\n  ║  User ID : " + pad(getUserId(), 26) + "║"
             + "\n  ║  Email   : " + pad(getEmail(),  26) + "║"
             + "\n  ║  Accounts: " + pad(String.valueOf(accounts.size()), 26) + "║"
             + (loanActive
               ? "\n  ║  Loan    : LKR " + pad(String.format("%,.2f", loanBalance), 21) + "║"
               : "")
             + "\n  ╚══════════════════════════════════════╝";
    }

    private String pad(String s, int n) { return String.format("%-" + n + "s", s); }

    // ── Account management ────────────────────────────────────────────────

    public void addAccount(Account account) {
        accounts.add(account);
        addNotification("New account opened: " + account.getAccountNumber()
                + " (" + account.getAccountType() + ")");
    }

    public Account getAccountByNumber(String number) {
        for (Account a : accounts)
            if (a.getAccountNumber().equals(number)) return a;
        return null;
    }

    public Account getPrimaryAccount() {
        return accounts.isEmpty() ? null : accounts.get(0);
    }

    // ── Notifications ─────────────────────────────────────────────────────

    public void addNotification(String message) {
        String ts = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"));
        notifications.add("[" + ts + "] " + message);
    }

    public void printNotifications() {
        System.out.println("\n  ── Notifications ──────────────────────────");
        if (notifications.isEmpty()) { System.out.println("  No notifications."); return; }
        for (String n : notifications) System.out.println("  • " + n);
    }

    // ── Loan management ───────────────────────────────────────────────────

    public boolean applyForLoan(double amount) {
        if (loanActive) return false;
        loanBalance = amount;
        loanActive  = true;
        addNotification("Loan approved: LKR " + String.format("%,.2f", amount));
        return true;
    }

    public boolean repayLoan(double amount) {
        if (!loanActive || amount <= 0) return false;
        loanBalance -= amount;
        if (loanBalance <= 0) {
            loanBalance = 0;
            loanActive  = false;
            addNotification("Loan fully repaid! ✓");
        } else {
            addNotification("Loan repayment LKR " + String.format("%,.2f", amount)
                    + " — Remaining: LKR " + String.format("%,.2f", loanBalance));
        }
        return true;
    }

    // ── Getters ───────────────────────────────────────────────────────────

    public String        getFullName()       { return fullName; }
    public String        getAddress()        { return address; }
    public String        getNationalId()     { return nationalId; }
    public List<Account> getAccounts()       { return accounts; }
    public double        getLoanBalance()    { return loanBalance; }
    public boolean       isLoanActive()      { return loanActive; }
    public List<String>  getNotifications()  { return notifications; }

    public void setAddress(String address)   { this.address = address; }
    public void setLoanBalance(double lb)    { this.loanBalance = lb; }
    public void setLoanActive(boolean la)    { this.loanActive  = la; }

    // ── File serialization ────────────────────────────────────────────────

    @Override
    public String toFileString() {
        return super.toFileString() + "|" + fullName  + "|" + address
             + "|" + nationalId + "|" + loanBalance + "|" + loanActive;
    }
}