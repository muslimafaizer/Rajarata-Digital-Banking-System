package pack_1;

import java.util.ArrayList;
import java.util.List;

/**
 * Abstract Account — base for all account types.
 * OOP: Abstraction + Encapsulation + Composition (has Transactions)
 */
public abstract class Account {

    private static final double MAX_DEPOSIT = 5_000_000;

    // Encapsulation: private fields
    private String            accountNumber;
    private String            ownerId;
    private double            balance;
    private String            accountType;
    private String            dateOpened;
    private boolean           active;
    private List<Transaction> transactionHistory; // Composition

    public Account(String accountNumber, String ownerId,
                   double initialBalance, String accountType) {
        this.accountNumber      = accountNumber;
        this.ownerId            = ownerId;
        this.balance            = initialBalance;
        this.accountType        = accountType;
        this.active             = true;
        this.transactionHistory = new ArrayList<>();
        this.dateOpened = java.time.LocalDate.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"));
    }

    // Abstraction: subclasses must define these
    public abstract double getInterestRate();
    public abstract String getAccountFeatures();

    // ── Validation helpers ───────────────────────────────────────────────

    public static double round2(double v) { return Math.round(v * 100.0) / 100.0; }

    /** Rejects NaN, infinity, zero, negatives and more than 2 decimal places. */
    public static void checkAmount(double amount) throws InvalidAmountException {
        if (Double.isNaN(amount) || Double.isInfinite(amount))
            throw new InvalidAmountException("Amount must be a valid number.");
        if (amount <= 0)
            throw new InvalidAmountException("Amount must be greater than zero.");
        if (Math.abs(amount * 100 - Math.rint(amount * 100)) > 1e-6)
            throw new InvalidAmountException("Amount can have at most 2 decimal places.");
    }

    /** Checks a deposit WITHOUT changing anything (used to make transfers safe). */
    public void validateDeposit(double amount) throws BankingException {
        if (!active) throw new InactiveAccountException(accountNumber);
        checkAmount(amount);
        if (amount > MAX_DEPOSIT)
            throw new InvalidAmountException("Amount exceeds single deposit limit (LKR 5,000,000).");
    }

    // ── Core Operations ──────────────────────────────────────────────────

    public synchronized void deposit(double amount, String description)
            throws BankingException {
        deposit(amount, description, "DEP");
    }

    public synchronized void deposit(double amount, String description, String type)
            throws BankingException {
        validateDeposit(amount);
        balance = round2(balance + amount);
        transactionHistory.add(new Transaction(type, amount, balance, description, accountNumber));
    }

    public synchronized void withdraw(double amount, String description)
            throws BankingException {
        withdraw(amount, description, "WDR");
    }

    // Subclasses override this method to change withdrawal rules (Polymorphism)
    public synchronized void withdraw(double amount, String description, String type)
            throws BankingException {
        if (!active) throw new InactiveAccountException(accountNumber);
        checkAmount(amount);
        if (amount > balance) throw new InsufficientFundsException(
                "Insufficient funds. Balance: LKR " + String.format("%,.2f", balance));
        checkWithdrawalLimit(amount); // subclass hook

        balance = round2(balance - amount);
        transactionHistory.add(new Transaction(type, amount, balance, description, accountNumber));
    }

    // Subclasses override to add custom withdrawal rules
    protected void checkWithdrawalLimit(double amount) throws BankingException {
        // default: no extra limit
    }

    public void addTransaction(Transaction t) {
        transactionHistory.add(t);
    }

    /** Undo helper so a failed transfer never leaves a half-finished update. */
    synchronized void rollbackTo(double previousBalance, int transactionCount) {
        this.balance = previousBalance;
        while (transactionHistory.size() > transactionCount)
            transactionHistory.remove(transactionHistory.size() - 1);
    }

    /** Used by FileHandler when loading saved data. */
    void restoreState(String dateOpened, boolean active) {
        if (dateOpened != null && !dateOpened.isEmpty()) this.dateOpened = dateOpened;
        this.active = active;
    }

    // ── Display ───────────────────────────────────────────────────────────

    public void printStatement() {
        System.out.println("\n  ─── Statement: " + accountNumber
                + " (" + accountType + ") ─────────────────────────────────────");
        System.out.printf("  %-14s %-11s %14s  %14s  %-30s%n",
                "Date/Time", "Type", "Amount (LKR)", "Balance (LKR)", "Description");
        System.out.println("  " + "─".repeat(80));
        if (transactionHistory.isEmpty()) {
            System.out.println("  No transactions yet.");
        } else {
            for (Transaction t : transactionHistory)
                System.out.println("  " + t.toTableRow());
        }
        System.out.println("  " + "─".repeat(80));
        System.out.printf("  Current Balance: LKR %,.2f%n", balance);
    }

    public void printInfo() {
        System.out.printf("  %-18s | %-14s | Balance: LKR %,12.2f | Rate: %.2f%%%n",
                accountNumber, accountType, balance, getInterestRate());
    }

    // ── File serialization ────────────────────────────────────────────────

    public String toFileString() {
        return accountNumber + "|" + ownerId + "|" + balance + "|"
             + accountType   + "|" + dateOpened + "|" + active;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────

    public String            getAccountNumber()  { return accountNumber; }
    public String            getOwnerId()        { return ownerId; }
    public double            getBalance()        { return balance; }
    public String            getAccountType()    { return accountType; }
    public String            getDateOpened()     { return dateOpened; }
    public boolean           isActive()          { return active; }
    public List<Transaction> getTransactions()   { return transactionHistory; }

    protected void setBalance(double b)          { this.balance = round2(b); }
    public    void setActive(boolean a)          { this.active  = a; }
}
