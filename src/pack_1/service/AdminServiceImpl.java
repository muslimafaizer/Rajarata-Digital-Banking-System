package pack_1.service;

import pack_1.exception.AccountNotFoundException;
import pack_1.exception.BankingException;
import pack_1.model.Account;
import pack_1.model.AuditLog;
import pack_1.model.Customer;
import pack_1.model.Transaction;
import pack_1.repository.AccountDao;
import pack_1.repository.AuditLogDao;
import pack_1.repository.TransactionDao;
import pack_1.repository.UserDao;
import pack_1.util.ValidationUtil;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Concrete implementation of AdminService.
 * Demonstrates: Role-based operations, reporting, account status control, and security logging.
 */
public class AdminServiceImpl implements AdminService {

    private final UserDao userDao;
    private final AccountDao accountDao;
    private final TransactionDao transactionDao;
    private final AuditLogDao auditLogDao;

    public AdminServiceImpl(UserDao userDao, AccountDao accountDao, TransactionDao transactionDao, AuditLogDao auditLogDao) {
        this.userDao = userDao;
        this.accountDao = accountDao;
        this.transactionDao = transactionDao;
        this.auditLogDao = auditLogDao;
    }

    @Override
    public List<Customer> getAllCustomers() {
        List<Customer> customers = userDao.findAllCustomers();
        for (Customer c : customers) {
            List<Account> accounts = accountDao.findByUserId(c.getUserId());
            c.setAccountsList(accounts);
        }
        return customers;
    }

    @Override
    public List<Account> getAllAccounts() {
        return accountDao.findAll();
    }

    @Override
    public List<Transaction> getAllTransactions(int limit) {
        return transactionDao.findAll(limit > 0 ? limit : 200);
    }

    @Override
    public List<AuditLog> getAuditLogs(int limit) {
        return auditLogDao.findAll(limit > 0 ? limit : 100);
    }

    @Override
    public Map<String, Object> getSystemStatistics() {
        List<Customer> customers = getAllCustomers();
        List<Account> accounts = getAllAccounts();
        List<Transaction> transactions = getAllTransactions(1000);

        BigDecimal totalDeposits = BigDecimal.ZERO;
        int activeAccountsCount = 0;
        int totalLoansCount = 0;
        BigDecimal totalLoanAmount = BigDecimal.ZERO;

        for (Account a : accounts) {
            totalDeposits = totalDeposits.add(a.getBalance());
            if (a.isActive()) activeAccountsCount++;
        }

        for (Customer c : customers) {
            if (c.isLoanActive()) {
                totalLoansCount++;
                totalLoanAmount = totalLoanAmount.add(c.getLoanBalance());
            }
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalCustomers", customers.size());
        stats.put("totalAccounts", accounts.size());
        stats.put("activeAccounts", activeAccountsCount);
        stats.put("totalDeposits", totalDeposits);
        stats.put("totalTransactions", transactions.size());
        stats.put("activeLoansCount", totalLoansCount);
        stats.put("totalLoanAmount", totalLoanAmount);
        return stats;
    }

    @Override
    public synchronized void setAccountActiveStatus(String accountNumber, boolean active) throws BankingException {
        String num = ValidationUtil.validateAccountNumber(accountNumber);
        Account acc = accountDao.findByAccountNumber(num);
        if (acc == null) {
            throw new AccountNotFoundException(num);
        }
        accountDao.toggleActiveStatus(num, active);
        auditLogDao.save(new AuditLog("ADMIN", "ACCOUNT_STATUS_CHANGE",
                "Account " + num + " status changed to: " + (active ? "ACTIVE" : "INACTIVE"), "127.0.0.1"));
    }
}
