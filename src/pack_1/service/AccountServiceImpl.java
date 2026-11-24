package pack_1.service;

import pack_1.exception.AccountNotFoundException;
import pack_1.exception.BankingException;
import pack_1.exception.InvalidAmountException;
import pack_1.model.*;
import pack_1.repository.AccountDao;
import pack_1.repository.AuditLogDao;
import pack_1.repository.TransactionDao;
import pack_1.util.ValidationUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Concrete implementation of AccountService.
 * Demonstrates: Polymorphism (instantiating different account subclasses based on type), Business Rule Validation.
 */
public class AccountServiceImpl implements AccountService {

    private static final int MAX_ACCOUNTS_PER_CUSTOMER = 5;
    private static final BigDecimal MIN_FD_DEPOSIT = new BigDecimal("10000.00");

    private final AccountDao accountDao;
    private final TransactionDao transactionDao;
    private final AuditLogDao auditLogDao;

    public AccountServiceImpl(AccountDao accountDao, TransactionDao transactionDao, AuditLogDao auditLogDao) {
        this.accountDao = accountDao;
        this.transactionDao = transactionDao;
        this.auditLogDao = auditLogDao;
    }

    @Override
    public synchronized Account openAccount(Customer customer, String accountType, BigDecimal initialDeposit, int fdMonths) throws BankingException {
        if (customer == null) {
            throw new BankingException("Customer cannot be null.");
        }
        if (accountType == null || accountType.trim().isEmpty()) {
            throw new BankingException("Account type is required.");
        }

        List<Account> existing = accountDao.findByUserId(customer.getUserId());
        if (existing.size() >= MAX_ACCOUNTS_PER_CUSTOMER) {
            throw new BankingException("Maximum account limit (" + MAX_ACCOUNTS_PER_CUSTOMER + ") reached for this customer.");
        }

        BigDecimal depositAmount = initialDeposit != null ? initialDeposit.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        if (depositAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidAmountException("Initial deposit cannot be negative.");
        }

        String typeUpper = accountType.trim().toUpperCase();
        int nextSeq = accountDao.getNextAccountSequence();
        Account newAccount;

        switch (typeUpper) {
            case "SAVINGS" -> {
                String accNum = "SAV" + String.format("%07d", nextSeq);
                newAccount = new SavingsAccount(accNum, customer.getUserId(), BigDecimal.ZERO);
            }
            case "CURRENT" -> {
                String accNum = "CUR" + String.format("%07d", nextSeq);
                newAccount = new CurrentAccount(accNum, customer.getUserId(), BigDecimal.ZERO);
            }
            case "FIXED", "FIXED_DEPOSIT" -> {
                if (depositAmount.compareTo(MIN_FD_DEPOSIT) < 0) {
                    throw new BankingException("Fixed Deposit requires a minimum deposit of LKR 10,000.00.");
                }
                if (fdMonths != 3 && fdMonths != 6 && fdMonths != 12 && fdMonths != 24 && fdMonths != 36) {
                    throw new BankingException("Fixed Deposit term must be 3, 6, 12, 24, or 36 months.");
                }
                String accNum = "FD" + String.format("%07d", nextSeq);
                newAccount = new FixedDepositAccount(accNum, customer.getUserId(), BigDecimal.ZERO, fdMonths);
            }
            default -> throw new BankingException("Unknown account type: " + accountType + ". Supported: SAVINGS, CURRENT, FIXED_DEPOSIT");
        }

        if (depositAmount.compareTo(BigDecimal.ZERO) > 0) {
            newAccount.deposit(depositAmount, "Opening deposit", "DEP");
        }

        accountDao.save(newAccount);
        customer.addAccount(newAccount);
        if (!newAccount.getTransactions().isEmpty()) {
            for (Transaction tx : newAccount.getTransactions()) {
                transactionDao.save(tx);
            }
        }

        auditLogDao.save(new AuditLog(customer.getUserId(), "ACCOUNT_OPEN",
                "Opened " + newAccount.getAccountType() + " account " + newAccount.getAccountNumber()
                        + " with initial deposit LKR " + depositAmount, "127.0.0.1"));

        return newAccount;
    }

    @Override
    public Account getAccount(String accountNumber) throws BankingException {
        String num = ValidationUtil.validateAccountNumber(accountNumber);
        Account acc = accountDao.findByAccountNumber(num);
        if (acc == null) {
            throw new AccountNotFoundException(num);
        }
        // Attach transaction history
        List<Transaction> txns = transactionDao.findByAccountNumber(num);
        for (Transaction tx : txns) {
            acc.addTransaction(tx);
        }
        return acc;
    }

    @Override
    public List<Account> getCustomerAccounts(String userId) {
        List<Account> accounts = accountDao.findByUserId(userId);
        for (Account a : accounts) {
            List<Transaction> txns = transactionDao.findByAccountNumber(a.getAccountNumber());
            for (Transaction tx : txns) {
                a.addTransaction(tx);
            }
        }
        return accounts;
    }

    @Override
    public synchronized void toggleAccountStatus(String accountNumber, boolean active) throws BankingException {
        String num = ValidationUtil.validateAccountNumber(accountNumber);
        Account acc = accountDao.findByAccountNumber(num);
        if (acc == null) {
            throw new AccountNotFoundException(num);
        }
        accountDao.toggleActiveStatus(num, active);
        auditLogDao.save(new AuditLog(acc.getOwnerId(), "ACCOUNT_STATUS_CHANGE",
                "Account " + num + " status changed to: " + (active ? "ACTIVE" : "INACTIVE"), "127.0.0.1"));
    }
}
