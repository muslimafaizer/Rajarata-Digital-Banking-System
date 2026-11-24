package pack_1.model;

import pack_1.exception.BankingException;
import pack_1.exception.InactiveAccountException;
import pack_1.exception.InsufficientFundsException;
import pack_1.exception.InvalidAmountException;
import pack_1.util.ValidationUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Abstract Account — base class for all bank account types.
 * Demonstrates OOP:
 * - Abstraction: Abstract class defining common contract (getInterestRate, getAccountFeatures, calculateMonthlyInterest)
 * - Encapsulation: Private balance, mutations only via validated banking operations
 * - Composition: Account maintains a collection of Transactions
 * - Polymorphism: Withdrawal behavior and interest calculation vary by subclass
 */
public abstract class Account {

    private final String accountNumber;
    private final String ownerId;
    private BigDecimal balance; // Encapsulation: strictly protected from direct public modification
    private final String accountType;
    private String dateOpened;
    private boolean active;
    private final List<Transaction> transactionHistory; // Composition

    public Account(String accountNumber, String ownerId, BigDecimal initialBalance, String accountType) {
        this.accountNumber = accountNumber;
        this.ownerId = ownerId;
        this.balance = initialBalance != null ? initialBalance.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        this.accountType = accountType;
        this.active = true;
        this.transactionHistory = new ArrayList<>();
        this.dateOpened = LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
    }

    // ── Abstraction: Subclasses must provide these implementations ────────────
    public abstract BigDecimal getInterestRate();
    public abstract String getAccountFeatures();
    public abstract BigDecimal calculateMonthlyInterest();

    // ── Core Banking Operations (Encapsulation + Validation) ───────────────────

    public synchronized void validateDeposit(BigDecimal amount) throws BankingException {
        if (!active) {
            throw new InactiveAccountException(accountNumber);
        }
        ValidationUtil.validateAmount(amount, "DEPOSIT");
    }

    public synchronized void deposit(BigDecimal amount, String description) throws BankingException {
        deposit(amount, description, "DEP");
    }

    public synchronized void deposit(BigDecimal amount, String description, String type) throws BankingException {
        validateDeposit(amount);
        BigDecimal scaled = amount.setScale(2, RoundingMode.HALF_UP);
        this.balance = this.balance.add(scaled);
        Transaction tx = new Transaction(type, scaled, this.balance, description, accountNumber);
        transactionHistory.add(tx);
    }

    public synchronized void withdraw(BigDecimal amount, String description) throws BankingException {
        withdraw(amount, description, "WDR");
    }

    /**
     * Polymorphic method: Subclasses can override or utilize checkWithdrawalRules.
     */
    public synchronized void withdraw(BigDecimal amount, String description, String type) throws BankingException {
        if (!active) {
            throw new InactiveAccountException(accountNumber);
        }
        BigDecimal scaled = ValidationUtil.validateAmount(amount, "WITHDRAWAL");
        if (scaled.compareTo(this.balance) > 0) {
            throw new InsufficientFundsException("Insufficient funds. Available balance: LKR " + this.balance);
        }
        checkWithdrawalRules(scaled); // Subclass hook

        this.balance = this.balance.subtract(scaled);
        Transaction tx = new Transaction(type, scaled, this.balance, description, accountNumber);
        transactionHistory.add(tx);
    }

    /**
     * Protected hook for subclass polymorphism.
     */
    protected void checkWithdrawalRules(BigDecimal amount) throws BankingException {
        // Base account has no additional restrictions beyond balance check
    }

    public synchronized void addTransaction(Transaction tx) {
        if (tx != null) {
            this.transactionHistory.add(tx);
        }
    }

    /**
     * Atomic transaction rollback helper to support all-or-nothing money transfers.
     */
    public synchronized void rollbackTo(BigDecimal previousBalance, int transactionCount) {
        this.balance = previousBalance;
        while (transactionHistory.size() > transactionCount) {
            transactionHistory.remove(transactionHistory.size() - 1);
        }
    }

    public void restoreState(String dateOpened, boolean active) {
        if (dateOpened != null && !dateOpened.trim().isEmpty()) {
            this.dateOpened = dateOpened;
        }
        this.active = active;
    }

    // ── Getters and Controlled Setters ────────────────────────────────────────

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    protected void setBalanceDirect(BigDecimal newBalance) {
        this.balance = newBalance.setScale(2, RoundingMode.HALF_UP);
    }

    public String getAccountType() {
        return accountType;
    }

    public String getDateOpened() {
        return dateOpened;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public List<Transaction> getTransactions() {
        return Collections.unmodifiableList(transactionHistory);
    }

    @Override
    public String toString() {
        return String.format("%s [%s] - Balance: LKR %,.2f", accountNumber, accountType, balance);
    }
}
