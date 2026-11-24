package pack_1.service;

import pack_1.exception.AccountNotFoundException;
import pack_1.exception.BankingException;
import pack_1.exception.InactiveAccountException;
import pack_1.exception.UnauthorizedAccessException;
import pack_1.model.*;
import pack_1.repository.AccountDao;
import pack_1.repository.AuditLogDao;
import pack_1.repository.DatabaseManager;
import pack_1.repository.TransactionDao;
import pack_1.repository.UserDao;
import pack_1.util.ValidationUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Concrete implementation of TransactionService.
 * Demonstrates:
 * - ACID Database Transactions: Atomic money transfer with rollback on failure
 * - Polymorphism: Withdrawal rules dispatch dynamically per Account subclass
 * - Exception Handling and Comprehensive Auditing
 */
public class TransactionServiceImpl implements TransactionService {

    private static final Map<String, BigDecimal> STANDARD_BILLS = new LinkedHashMap<>();
    static {
        STANDARD_BILLS.put("ELECTRICITY", new BigDecimal("3500.00"));
        STANDARD_BILLS.put("WATER",       new BigDecimal("1200.00"));
        STANDARD_BILLS.put("INTERNET",    new BigDecimal("2999.00"));
        STANDARD_BILLS.put("PHONE",        new BigDecimal("999.00"));
        STANDARD_BILLS.put("INSURANCE",   new BigDecimal("5000.00"));
    }

    private final AccountDao accountDao;
    private final TransactionDao transactionDao;
    private final UserDao userDao;
    private final AuditLogDao auditLogDao;

    public TransactionServiceImpl(AccountDao accountDao, TransactionDao transactionDao, UserDao userDao, AuditLogDao auditLogDao) {
        this.accountDao = accountDao;
        this.transactionDao = transactionDao;
        this.userDao = userDao;
        this.auditLogDao = auditLogDao;
    }

    @Override
    public Map<String, BigDecimal> getStandardBills() {
        return STANDARD_BILLS;
    }

    @Override
    public synchronized void deposit(Customer customer, String accountNumber, BigDecimal amount) throws BankingException {
        Account account = requireCustomerAccount(customer, accountNumber);
        BigDecimal scaled = ValidationUtil.validateAmount(amount, "DEPOSIT");

        int previousCount = account.getTransactions().size();
        account.deposit(scaled, "Customer deposit", "DEP");

        // Persist updated account balance
        accountDao.update(account);

        // Persist the newly generated transaction
        if (account.getTransactions().size() > previousCount) {
            Transaction tx = account.getTransactions().get(account.getTransactions().size() - 1);
            transactionDao.save(tx);
        }

        auditLogDao.save(new AuditLog(customer.getUserId(), "DEPOSIT",
                "Deposited LKR " + scaled + " into account " + account.getAccountNumber(), "127.0.0.1"));
    }

    @Override
    public synchronized void withdraw(Customer customer, String accountNumber, BigDecimal amount) throws BankingException {
        Account account = requireCustomerAccount(customer, accountNumber);
        BigDecimal scaled = ValidationUtil.validateAmount(amount, "WITHDRAWAL");

        int previousCount = account.getTransactions().size();
        // Polymorphism: withdrawal rules (daily limit, overdraft, or penalty) execute dynamically
        account.withdraw(scaled, "Customer withdrawal", "WDR");

        // Persist updated account balance
        accountDao.update(account);

        // Persist transaction(s) — note FixedDepositAccount may create both WDR and FEE transactions
        for (int i = previousCount; i < account.getTransactions().size(); i++) {
            transactionDao.save(account.getTransactions().get(i));
        }

        auditLogDao.save(new AuditLog(customer.getUserId(), "WITHDRAWAL",
                "Withdrew LKR " + scaled + " from account " + account.getAccountNumber(), "127.0.0.1"));
    }

    @Override
    public synchronized void transfer(Customer sender, String fromAcc, String toAcc, BigDecimal amount) throws BankingException {
        String sourceNum = ValidationUtil.validateAccountNumber(fromAcc);
        String targetNum = ValidationUtil.validateAccountNumber(toAcc);

        if (sourceNum.equalsIgnoreCase(targetNum)) {
            throw new BankingException("Cannot transfer funds to the same account.");
        }

        BigDecimal scaled = ValidationUtil.validateAmount(amount, "TRANSFER");

        Account from = requireCustomerAccount(sender, sourceNum);
        Account to = accountDao.findByAccountNumber(targetNum);
        if (to == null) {
            throw new AccountNotFoundException(targetNum);
        }
        if (!to.isActive()) {
            throw new InactiveAccountException(targetNum);
        }

        // Validate receiving side deposit rules BEFORE debiting sender
        to.validateDeposit(scaled);

        // Execute withdrawal in memory first
        BigDecimal fromPrevBalance = from.getBalance();
        int fromPrevCount = from.getTransactions().size();

        from.withdraw(scaled, "Transfer to " + targetNum, "TRF_OUT");
        Transaction fromTx = from.getTransactions().get(from.getTransactions().size() - 1);

        to.deposit(scaled, "Transfer from " + sourceNum, "TRF_IN");
        Transaction toTx = to.getTransactions().get(to.getTransactions().size() - 1);

        // Atomic multi-statement database transaction with rollback protection
        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. Update source account balance
                String updateAccSql = "UPDATE accounts SET balance = ? WHERE account_number = ?";
                try (PreparedStatement ps = conn.prepareStatement(updateAccSql)) {
                    ps.setBigDecimal(1, from.getBalance());
                    ps.setString(2, from.getAccountNumber());
                    ps.executeUpdate();

                    ps.setBigDecimal(1, to.getBalance());
                    ps.setString(2, to.getAccountNumber());
                    ps.executeUpdate();
                }

                // 2. Insert debit transaction
                transactionDao.saveWithConnection(conn, fromTx);

                // 3. Insert credit transaction
                transactionDao.saveWithConnection(conn, toTx);

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                from.rollbackTo(fromPrevBalance, fromPrevCount);
                throw new BankingException("Atomic transfer failed and was rolled back: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            from.rollbackTo(fromPrevBalance, fromPrevCount);
            throw new BankingException("Database error during transfer: " + e.getMessage(), e);
        }

        auditLogDao.save(new AuditLog(sender.getUserId(), "TRANSFER",
                "Transferred LKR " + scaled + " from " + sourceNum + " to " + targetNum, "127.0.0.1"));
    }

    @Override
    public synchronized void payBill(Customer customer, String accountNumber, String billType) throws BankingException {
        if (billType == null || !STANDARD_BILLS.containsKey(billType.toUpperCase().trim())) {
            throw new BankingException("Invalid bill type. Supported: " + STANDARD_BILLS.keySet());
        }
        String billKey = billType.toUpperCase().trim();
        BigDecimal billAmount = STANDARD_BILLS.get(billKey);

        Account account = requireCustomerAccount(customer, accountNumber);
        int prevCount = account.getTransactions().size();

        account.withdraw(billAmount, "Bill payment: " + billKey, "BILL");
        accountDao.update(account);

        if (account.getTransactions().size() > prevCount) {
            transactionDao.save(account.getTransactions().get(account.getTransactions().size() - 1));
        }

        auditLogDao.save(new AuditLog(customer.getUserId(), "BILL_PAYMENT",
                "Paid " + billKey + " bill of LKR " + billAmount + " via " + account.getAccountNumber(), "127.0.0.1"));
    }

    @Override
    public synchronized void applyLoan(Customer customer, String accountNumber, BigDecimal amount) throws BankingException {
        Account account = requireCustomerAccount(customer, accountNumber);
        BigDecimal scaled = ValidationUtil.validateAmount(amount, "LOAN");

        if (scaled.compareTo(new BigDecimal("1000000.00")) > 0) {
            throw new BankingException("Maximum loan amount is LKR 1,000,000.00.");
        }
        if (customer.isLoanActive()) {
            throw new BankingException("You already have an active loan of LKR " + customer.getLoanBalance() + ".");
        }

        int prevCount = account.getTransactions().size();
        account.deposit(scaled, "Loan disbursement", "DEP");
        accountDao.update(account);

        if (account.getTransactions().size() > prevCount) {
            transactionDao.save(account.getTransactions().get(account.getTransactions().size() - 1));
        }

        customer.setLoanBalance(scaled);
        customer.setLoanActive(true);
        userDao.update(customer);

        auditLogDao.save(new AuditLog(customer.getUserId(), "LOAN_APPLIED",
                "Approved loan for LKR " + scaled + " deposited to " + account.getAccountNumber(), "127.0.0.1"));
    }

    @Override
    public synchronized void repayLoan(Customer customer, String accountNumber, BigDecimal amount) throws BankingException {
        if (!customer.isLoanActive()) {
            throw new BankingException("You have no active loan to repay.");
        }
        Account account = requireCustomerAccount(customer, accountNumber);
        BigDecimal scaled = ValidationUtil.validateAmount(amount, "LOAN_REPAY");

        if (scaled.compareTo(customer.getLoanBalance()) > 0) {
            throw new BankingException("Repayment amount exceeds outstanding loan balance of LKR " + customer.getLoanBalance());
        }

        int prevCount = account.getTransactions().size();
        account.withdraw(scaled, "Loan repayment", "LOAN_REP");
        accountDao.update(account);

        if (account.getTransactions().size() > prevCount) {
            transactionDao.save(account.getTransactions().get(account.getTransactions().size() - 1));
        }

        BigDecimal remaining = customer.getLoanBalance().subtract(scaled);
        customer.setLoanBalance(remaining);
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            customer.setLoanBalance(BigDecimal.ZERO);
            customer.setLoanActive(false);
        }
        userDao.update(customer);

        auditLogDao.save(new AuditLog(customer.getUserId(), "LOAN_REPAID",
                "Repaid LKR " + scaled + " toward loan. Remaining: LKR " + customer.getLoanBalance(), "127.0.0.1"));
    }

    @Override
    public List<Transaction> getTransactions(Customer customer, String accountNumber, String type, String search, int limit) {
        if (accountNumber != null && !accountNumber.trim().isEmpty()) {
            Account acc = customer.getAccountByNumber(accountNumber);
            if (acc == null) {
                // If the customer doesn't own this account, return empty list or throw
                return List.of();
            }
            return transactionDao.findFiltered(accountNumber.trim(), type, search, limit);
        } else {
            // Customer's all accounts
            return transactionDao.findFiltered(null, type, search, limit).stream()
                    .filter(t -> customer.getAccountByNumber(t.getAccountNumber()) != null)
                    .toList();
        }
    }

    @Override
    public Transaction getTransactionById(String transactionId) throws BankingException {
        if (transactionId == null) {
            throw new BankingException("Transaction ID is required.");
        }
        Transaction tx = transactionDao.findById(transactionId.trim());
        if (tx == null) {
            throw new BankingException("NOT_FOUND", "Transaction not found: " + transactionId);
        }
        return tx;
    }

    private Account requireCustomerAccount(Customer customer, String accountNumber) throws BankingException {
        String num = ValidationUtil.validateAccountNumber(accountNumber);
        Account acc = customer.getAccountByNumber(num);
        if (acc == null) {
            acc = accountDao.findByAccountNumber(num);
            if (acc == null) {
                throw new AccountNotFoundException(num);
            }
            if (!acc.getOwnerId().equals(customer.getUserId())) {
                throw new UnauthorizedAccessException("You are not authorized to access account: " + num);
            }
            customer.addAccount(acc);
        }
        if (!acc.isActive()) {
            throw new InactiveAccountException(num);
        }
        return acc;
    }
}
