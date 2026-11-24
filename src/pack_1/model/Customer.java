package pack_1.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Customer class representing a bank customer.
 * Demonstrates OOP:
 * - Inheritance: Extends User
 * - Composition / Aggregation: Customer has a List of Accounts
 * - Polymorphism: Concrete implementation of getDashboardInfo()
 * - Encapsulation: Balances and accounts accessed via controlled methods
 */
public class Customer extends User {
    private static final long serialVersionUID = 1L;

    private String fullName;
    private String address;
    private String nationalId;
    private final List<Account> accounts; // Composition: Customer has Accounts
    private BigDecimal loanBalance;
    private boolean loanActive;

    public Customer(String userId, String username, String passwordHash,
                    String email, String phone,
                    String fullName, String address, String nationalId) {
        super(userId, username, passwordHash, email, phone, "CUSTOMER");
        this.fullName = fullName;
        this.address = address;
        this.nationalId = nationalId;
        this.accounts = new ArrayList<>();
        this.loanBalance = BigDecimal.ZERO;
        this.loanActive = false;
    }

    // Polymorphism: Concrete implementation of abstract method from User
    @Override
    public String getDashboardInfo() {
        return "Customer: " + fullName + " | ID: " + getUserId()
                + " | Accounts: " + accounts.size()
                + " | Total Balance: LKR " + getTotalBalance()
                + (loanActive ? " | Active Loan: LKR " + loanBalance : "");
    }

    // Account management
    public void addAccount(Account account) {
        if (account != null && !accounts.contains(account)) {
            accounts.add(account);
        }
    }

    public void removeAccount(Account account) {
        accounts.remove(account);
    }

    public Account getAccountByNumber(String number) {
        if (number == null) return null;
        for (Account a : accounts) {
            if (a.getAccountNumber().equalsIgnoreCase(number.trim())) {
                return a;
            }
        }
        return null;
    }

    public Account getPrimaryAccount() {
        return accounts.isEmpty() ? null : accounts.get(0);
    }

    public BigDecimal getTotalBalance() {
        BigDecimal total = BigDecimal.ZERO;
        for (Account a : accounts) {
            total = total.add(a.getBalance());
        }
        return total;
    }

    // Getters and Setters
    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getNationalId() {
        return nationalId;
    }

    public void setNationalId(String nationalId) {
        this.nationalId = nationalId;
    }

    public List<Account> getAccounts() {
        return Collections.unmodifiableList(accounts);
    }

    public void setAccountsList(List<Account> newAccounts) {
        this.accounts.clear();
        if (newAccounts != null) {
            this.accounts.addAll(newAccounts);
        }
    }

    public BigDecimal getLoanBalance() {
        return loanBalance != null ? loanBalance : BigDecimal.ZERO;
    }

    public void setLoanBalance(BigDecimal loanBalance) {
        this.loanBalance = loanBalance != null ? loanBalance : BigDecimal.ZERO;
    }

    public boolean isLoanActive() {
        return loanActive;
    }

    public void setLoanActive(boolean loanActive) {
        this.loanActive = loanActive;
    }
}
