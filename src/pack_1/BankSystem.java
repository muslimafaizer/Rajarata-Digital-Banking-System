package pack_1;

import pack_1.exception.BankingException;
import pack_1.model.*;
import pack_1.repository.*;
import pack_1.service.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * BankSystem — core unified banking facade.
 * Demonstrates: Facade Pattern, Composition, Exception Handling, Delegation.
 * Connects the UI/API layers to the underlying services and SQLite database.
 */
public class BankSystem {

    private final UserDao userDao;
    private final AccountDao accountDao;
    private final TransactionDao transactionDao;
    private final AuditLogDao auditLogDao;

    private final AuthenticationService authService;
    private final AccountService accountService;
    private final TransactionService transactionService;
    private final AdminService adminService;

    public BankSystem() {
        // Initialize SQLite database, create tables, migrate existing data if needed
        DatabaseManager.initializeDatabase();

        this.userDao = new UserDaoImpl();
        this.accountDao = new AccountDaoImpl();
        this.transactionDao = new TransactionDaoImpl();
        this.auditLogDao = new AuditLogDaoImpl();

        this.authService = new AuthenticationServiceImpl(userDao, accountDao, auditLogDao);
        this.accountService = new AccountServiceImpl(accountDao, transactionDao, auditLogDao);
        this.transactionService = new TransactionServiceImpl(accountDao, transactionDao, userDao, auditLogDao);
        this.adminService = new AdminServiceImpl(userDao, accountDao, transactionDao, auditLogDao);
    }

    // ── Authentication & Registration ─────────────────────────────────────────

    public Customer registerCustomer(String username, String password, String email, String phone,
                                    String fullName, String address, String nationalId) throws BankingException {
        return authService.register(username, password, email, phone, fullName, address, nationalId);
    }

    public User login(String username, String password) throws BankingException {
        return authService.login(username, password, "127.0.0.1");
    }

    public User login(String username, String password, String ipAddress) throws BankingException {
        return authService.login(username, password, ipAddress);
    }

    public void changePassword(User user, String oldPassword, String newPassword) throws BankingException {
        authService.changePassword(user, oldPassword, newPassword);
    }

    public void updateProfile(Customer customer, String email, String phone, String address) throws BankingException {
        authService.updateProfile(customer, email, phone, address);
    }

    // ── Banking Operations ────────────────────────────────────────────────────

    public void deposit(Customer customer, String accountNumber, BigDecimal amount) throws BankingException {
        transactionService.deposit(customer, accountNumber, amount);
    }

    public void deposit(Customer customer, String accountNumber, double amount) throws BankingException {
        deposit(customer, accountNumber, BigDecimal.valueOf(amount));
    }

    public void withdraw(Customer customer, String accountNumber, BigDecimal amount) throws BankingException {
        transactionService.withdraw(customer, accountNumber, amount);
    }

    public void withdraw(Customer customer, String accountNumber, double amount) throws BankingException {
        withdraw(customer, accountNumber, BigDecimal.valueOf(amount));
    }

    public void transfer(Customer sender, String fromAcc, String toAcc, BigDecimal amount) throws BankingException {
        transactionService.transfer(sender, fromAcc, toAcc, amount);
    }

    public void transfer(Customer sender, String fromAcc, String toAcc, double amount) throws BankingException {
        transfer(sender, fromAcc, toAcc, BigDecimal.valueOf(amount));
    }

    public Account openAccount(Customer customer, String accountType, BigDecimal initialDeposit, int fdMonths) throws BankingException {
        return accountService.openAccount(customer, accountType, initialDeposit, fdMonths);
    }

    public Account openAccount(Customer customer, String accountType, double initialDeposit, int fdMonths) throws BankingException {
        return openAccount(customer, accountType, BigDecimal.valueOf(initialDeposit), fdMonths);
    }

    public void payBill(Customer customer, String accountNumber, String billType) throws BankingException {
        transactionService.payBill(customer, accountNumber, billType);
    }

    public void applyLoan(Customer customer, String accountNumber, BigDecimal amount) throws BankingException {
        transactionService.applyLoan(customer, accountNumber, amount);
    }

    public void applyLoan(Customer customer, String accountNumber, double amount) throws BankingException {
        applyLoan(customer, accountNumber, BigDecimal.valueOf(amount));
    }

    public void repayLoan(Customer customer, String accountNumber, BigDecimal amount) throws BankingException {
        transactionService.repayLoan(customer, accountNumber, amount);
    }

    public void repayLoan(Customer customer, String accountNumber, double amount) throws BankingException {
        repayLoan(customer, accountNumber, BigDecimal.valueOf(amount));
    }

    // ── Queries & Lookups ─────────────────────────────────────────────────────

    public User findUserByUsername(String username) {
        User u = userDao.findByUsername(username);
        if (u instanceof Customer c) {
            c.setAccountsList(accountService.getCustomerAccounts(c.getUserId()));
        }
        return u;
    }

    public Customer findCustomerById(String id) {
        User u = userDao.findById(id);
        if (u instanceof Customer c) {
            c.setAccountsList(accountService.getCustomerAccounts(c.getUserId()));
            return c;
        }
        return null;
    }

    public Account findAccountByNumber(String accountNumber) {
        try {
            return accountService.getAccount(accountNumber);
        } catch (BankingException e) {
            return null;
        }
    }

    public List<Customer> getCustomers() {
        return adminService.getAllCustomers();
    }

    public List<Admin> getAdmins() {
        return userDao.findAllAdmins();
    }

    public List<Account> getCustomerAccounts(String userId) {
        return accountService.getCustomerAccounts(userId);
    }

    public static Map<String, BigDecimal> getBills() {
        return TransactionServiceImpl.class.cast(null) != null ? null :
                Map.of(
                        "ELECTRICITY", new BigDecimal("3500.00"),
                        "WATER",       new BigDecimal("1200.00"),
                        "INTERNET",    new BigDecimal("2999.00"),
                        "PHONE",        new BigDecimal("999.00"),
                        "INSURANCE",   new BigDecimal("5000.00")
                );
    }

    // ── Service Accessors ─────────────────────────────────────────────────────

    public AuthenticationService getAuthService() {
        return authService;
    }

    public AccountService getAccountService() {
        return accountService;
    }

    public TransactionService getTransactionService() {
        return transactionService;
    }

    public AdminService getAdminService() {
        return adminService;
    }
}
