package pack_1.service;

import pack_1.exception.AuthenticationException;
import pack_1.exception.BankingException;
import pack_1.exception.DuplicateRegistrationException;
import pack_1.model.AuditLog;
import pack_1.model.Customer;
import pack_1.model.SavingsAccount;
import pack_1.model.User;
import pack_1.repository.AccountDao;
import pack_1.repository.AuditLogDao;
import pack_1.repository.UserDao;
import pack_1.util.PasswordUtil;
import pack_1.util.ValidationUtil;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Concrete implementation of AuthenticationService.
 * Demonstrates: Encapsulation, Business Logic Validation, Auditing, Brute-force Throttling.
 */
public class AuthenticationServiceImpl implements AuthenticationService {

    private final UserDao userDao;
    private final AccountDao accountDao;
    private final AuditLogDao auditLogDao;

    // In-memory lockout cache: username -> [failCount, lockUntilEpochMs]
    private final Map<String, long[]> failureTracker = new ConcurrentHashMap<>();
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCKOUT_DURATION_MS = 5 * 60 * 1000L; // 5 minutes

    public AuthenticationServiceImpl(UserDao userDao, AccountDao accountDao, AuditLogDao auditLogDao) {
        this.userDao = userDao;
        this.accountDao = accountDao;
        this.auditLogDao = auditLogDao;
    }

    @Override
    public synchronized Customer register(String username, String password, String email, String phone,
                                          String fullName, String address, String nationalId) throws BankingException {

        String validUser = ValidationUtil.validateUsername(username);
        ValidationUtil.validatePasswordStrength(password);
        String validEmail = ValidationUtil.validateEmail(email);
        String validPhone = ValidationUtil.validatePhone(phone);
        String validName = ValidationUtil.validateFullName(fullName);
        String validAddress = ValidationUtil.validateAddress(address);
        String validNic = ValidationUtil.validateNationalId(nationalId);

        // Check for duplicates
        if (userDao.findByUsername(validUser) != null) {
            throw new DuplicateRegistrationException("Username '" + validUser + "' is already taken.");
        }
        if (userDao.findByEmail(validEmail) != null) {
            throw new DuplicateRegistrationException("Email '" + validEmail + "' is already registered.");
        }
        if (userDao.findByNationalId(validNic) != null) {
            throw new DuplicateRegistrationException("National ID (NIC) '" + validNic + "' is already registered.");
        }

        String userId = "CUS" + userDao.getNextUserSequence();
        String passwordHash = PasswordUtil.hash(password);

        Customer customer = new Customer(userId, validUser, passwordHash, validEmail, validPhone, validName, validAddress, validNic);
        userDao.save(customer);

        // Open an initial default Savings Account
        String accNum = "SAV" + String.format("%07d", accountDao.getNextAccountSequence());
        SavingsAccount defaultAccount = new SavingsAccount(accNum, userId, BigDecimal.ZERO);
        accountDao.save(defaultAccount);
        customer.addAccount(defaultAccount);

        auditLogDao.save(new AuditLog(userId, "REGISTRATION", "Registered customer account with primary savings account " + accNum, "127.0.0.1"));
        return customer;
    }

    @Override
    public User login(String username, String password, String ipAddress) throws BankingException {
        if (username == null || username.trim().isEmpty()) {
            throw new AuthenticationException("Username is required.");
        }
        if (password == null || password.isEmpty()) {
            throw new AuthenticationException("Password is required.");
        }

        String key = username.trim().toLowerCase();
        long[] lockInfo = failureTracker.get(key);
        if (lockInfo != null && lockInfo[1] > System.currentTimeMillis()) {
            long remainingMins = (lockInfo[1] - System.currentTimeMillis()) / 60000 + 1;
            throw new AuthenticationException("LOCKED",
                    "Account is temporarily locked due to multiple failed login attempts. Try again in " + remainingMins + " minutes.");
        }

        User user = userDao.findByUsername(key);
        boolean valid = (user != null) && user.validatePassword(password);

        if (!valid) {
            long[] state = failureTracker.computeIfAbsent(key, k -> new long[2]);
            state[0]++;
            if (state[0] >= MAX_FAILED_ATTEMPTS) {
                state[0] = 0;
                state[1] = System.currentTimeMillis() + LOCKOUT_DURATION_MS;
            }
            auditLogDao.save(new AuditLog(user != null ? user.getUserId() : "UNKNOWN", "LOGIN_FAILED", "Failed login for username: " + username, ipAddress));
            throw new AuthenticationException("Invalid username or password.");
        }

        failureTracker.remove(key);
        auditLogDao.save(new AuditLog(user.getUserId(), "LOGIN_SUCCESS", "User logged in successfully", ipAddress));
        return user;
    }

    @Override
    public synchronized void changePassword(User user, String oldPassword, String newPassword) throws BankingException {
        if (user == null) {
            throw new AuthenticationException("User is not authenticated.");
        }
        if (!user.validatePassword(oldPassword)) {
            throw new AuthenticationException("Current password is incorrect.");
        }
        ValidationUtil.validatePasswordStrength(newPassword);
        user.changePassword(newPassword);
        userDao.update(user);
        auditLogDao.save(new AuditLog(user.getUserId(), "PASSWORD_CHANGE", "User changed account password", "127.0.0.1"));
    }

    @Override
    public synchronized void updateProfile(Customer customer, String email, String phone, String address) throws BankingException {
        if (customer == null) {
            throw new AuthenticationException("Customer is not authenticated.");
        }
        String validEmail = ValidationUtil.validateEmail(email);
        String validPhone = ValidationUtil.validatePhone(phone);
        String validAddress = ValidationUtil.validateAddress(address);

        User existingEmail = userDao.findByEmail(validEmail);
        if (existingEmail != null && !existingEmail.getUserId().equals(customer.getUserId())) {
            throw new DuplicateRegistrationException("Email '" + validEmail + "' is already in use by another account.");
        }

        customer.setEmail(validEmail);
        customer.setPhone(validPhone);
        customer.setAddress(validAddress);
        userDao.update(customer);
        auditLogDao.save(new AuditLog(customer.getUserId(), "PROFILE_UPDATE", "Customer updated email/phone/address", "127.0.0.1"));
    }
}
