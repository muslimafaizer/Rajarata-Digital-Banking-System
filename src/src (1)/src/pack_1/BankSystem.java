package pack_1;

import java.util.*;
import java.util.regex.Pattern;

/**
 * BankSystem — core banking engine.
 * OOP: Encapsulation + Exception Handling + File Handling + Collections
 * Every method that changes data is synchronized and saves to the data files.
 */
public class BankSystem {

    private final List<Customer> customers = new ArrayList<>();
    private final List<Admin>    admins    = new ArrayList<>();
    private int accCounter  = 1000;
    private int userCounter = 100;

    // Failed-login tracking: username -> [failure count, locked-until (ms)]
    private final Map<String, long[]> loginFailures = new HashMap<>();
    private static final int  MAX_FAILURES = 5;
    private static final long LOCK_MS      = 5 * 60 * 1000L;
    private static final String DUMMY_HASH = PasswordUtil.hash("timing-equaliser");

    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_.]{3,20}");
    private static final Pattern EMAIL    = Pattern.compile("[^@\\s|]+@[^@\\s|]+\\.[^@\\s|]+");
    private static final Pattern PHONE    = Pattern.compile("\\+?[0-9]{9,15}");
    private static final Pattern NIC      = Pattern.compile("[A-Za-z0-9]{9,15}");
    private static final Pattern ACC_NO   = Pattern.compile("(SAV|CUR|FD)\\d{7}");
    private static final int MAX_ACCOUNTS = 5;

    private static final Map<String, Double> BILLS = new LinkedHashMap<>();
    static {
        BILLS.put("ELECTRICITY", 3500.00);
        BILLS.put("WATER",       1200.00);
        BILLS.put("INTERNET",    2999.00);
        BILLS.put("PHONE",        999.00);
        BILLS.put("INSURANCE",   5000.00);
    }

    // ── FIRST-RUN ADMIN ───────────────────────────────────────────────────

    /** No hard-coded credentials: if there is no admin yet, create one with a random password. */
    public synchronized void ensureDefaultAdmin() {
        if (!admins.isEmpty()) return;
        String password = PasswordUtil.randomPassword(12);
        admins.add(new Admin("ADM001", "admin", PasswordUtil.hash(password),
                "admin@rajarata.lk", "0000000000", "Operations", 5));
        FileHandler.saveAll(customers, admins);
        System.out.println("  ┌────────────────────────────────────────────┐");
        System.out.println("  │ First run: admin account created           │");
        System.out.println("  │ username: admin                            │");
        System.out.println("  │ password: " + String.format("%-33s", password) + "│");
        System.out.println("  │ (shown once only — please note it down)    │");
        System.out.println("  └────────────────────────────────────────────┘");
    }

    /** After loading files, continue numbering after the highest existing id / account number. */
    public synchronized void syncCounters() {
        for (Customer c : customers) {
            userCounter = Math.max(userCounter, trailingNumber(c.getUserId()));
            for (Account a : c.getAccounts())
                accCounter = Math.max(accCounter, trailingNumber(a.getAccountNumber()));
        }
    }

    private static int trailingNumber(String s) {
        int i = s.length();
        while (i > 0 && Character.isDigit(s.charAt(i - 1))) i--;
        try { return Integer.parseInt(s.substring(i)); } catch (NumberFormatException e) { return 0; }
    }

    // ── REGISTER ─────────────────────────────────────────────────────────

    public synchronized Customer registerCustomer(String username, String password, String email,
                                                  String phone,    String fullName,
                                                  String address,  String nationalId)
            throws BankingException {

        username   = requireText(username,   "Username");
        email      = requireText(email,      "Email");
        phone      = requireText(phone,      "Phone number");
        fullName   = requireText(fullName,   "Full name");
        address    = requireText(address,    "Address");
        nationalId = requireText(nationalId, "National ID");

        if (!USERNAME.matcher(username).matches())
            throw new BankingException("Username must be 3-20 characters: letters, numbers, '_' or '.'.");
        checkPasswordStrength(password);
        if (!EMAIL.matcher(email).matches())
            throw new BankingException("Invalid email address.");
        if (!PHONE.matcher(phone).matches())
            throw new BankingException("Phone number must have 9-15 digits (optional leading +).");
        if (fullName.length() < 2 || fullName.length() > 60)
            throw new BankingException("Full name must be 2-60 characters.");
        if (address.length() > 120)
            throw new BankingException("Address is too long (max 120 characters).");
        if (!NIC.matcher(nationalId).matches())
            throw new BankingException("National ID must be 9-15 letters/numbers.");

        if (findUserByUsername(username) != null)
            throw new DuplicateRegistrationException("Username '" + username + "' is already taken.");
        for (Customer c : customers) {
            if (c.getEmail().equalsIgnoreCase(email))
                throw new DuplicateRegistrationException("This email is already registered.");
            if (c.getNationalId().equalsIgnoreCase(nationalId))
                throw new DuplicateRegistrationException("This National ID is already registered.");
        }

        String   cid      = "CUS" + (++userCounter);
        Customer customer = new Customer(cid, username, PasswordUtil.hash(password),
                                         email, phone, fullName, address, nationalId);

        // Auto-open a Savings account on registration
        customer.addAccount(new SavingsAccount(generateAccNum("SAV"), cid, 0.0));

        customers.add(customer);
        FileHandler.saveAll(customers, admins);
        return customer;
    }

    // ── LOGIN ─────────────────────────────────────────────────────────────

    public synchronized User login(String username, String password) throws BankingException {
        String key = username == null ? "" : username.trim().toLowerCase();
        long[] f = loginFailures.get(key);
        if (f != null && f[1] > System.currentTimeMillis())
            throw new AuthenticationException("LOCKED",
                    "Too many failed attempts. Please try again in a few minutes.");

        User user = findUserByUsername(key);
        boolean ok;
        if (user == null) { PasswordUtil.verify(password == null ? "" : password, DUMMY_HASH); ok = false; }
        else              { ok = user.validatePassword(password); }

        if (!ok) {
            long[] rec = loginFailures.computeIfAbsent(key, k -> new long[2]);
            if (++rec[0] >= MAX_FAILURES) { rec[0] = 0; rec[1] = System.currentTimeMillis() + LOCK_MS; }
            // one generic message: never reveal whether the username exists
            throw new AuthenticationException("Invalid username or password.");
        }
        loginFailures.remove(key);
        return user;
    }

    // ── DEPOSIT / WITHDRAW ────────────────────────────────────────────────

    public synchronized void deposit(Customer c, String accNum, double amount)
            throws BankingException {
        Account acc = requireAccount(c, accNum);
        acc.deposit(amount, "Customer deposit", "DEP");
        c.addNotification("Deposited LKR " + String.format("%,.2f", amount) + " → " + acc.getAccountNumber());
        FileHandler.saveAll(customers, admins);
    }

    public synchronized void withdraw(Customer c, String accNum, double amount)
            throws BankingException {
        Account acc = requireAccount(c, accNum);
        acc.withdraw(amount, "Customer withdrawal", "WDR");
        c.addNotification("Withdrew LKR " + String.format("%,.2f", amount) + " ← " + acc.getAccountNumber());
        FileHandler.saveAll(customers, admins);
    }

    // ── TRANSFER (all-or-nothing) ─────────────────────────────────────────

    public synchronized void transfer(Customer sender, String fromNum, String toNum, double amount)
            throws BankingException {
        fromNum = normalizeAccountNumber(fromNum);
        toNum   = normalizeAccountNumber(toNum);
        if (fromNum.equals(toNum))
            throw new BankingException("Cannot transfer to the same account.");
        Account.checkAmount(amount);

        Account from = requireAccount(sender, fromNum);
        Account to   = findAccountByNumber(toNum);
        if (to == null)
            throw new AccountNotFoundException("Destination account not found.");

        // 1) Check everything that could fail on the receiving side BEFORE touching any balance
        to.validateDeposit(amount);

        double fromBalance = from.getBalance();
        int    fromTxns    = from.getTransactions().size();
        try {
            from.withdraw(amount, "Transfer to " + toNum, "TRF_OUT");       // fails cleanly if not allowed
            try {
                to.deposit(amount, "Transfer from " + fromNum, "TRF_IN");
            } catch (BankingException | RuntimeException e) {
                from.rollbackTo(fromBalance, fromTxns);                       // undo: no partial update
                throw e;
            }
        } catch (RuntimeException e) {
            throw new BankingException("Transfer failed and was cancelled.", e);
        }

        sender.addNotification("Transferred LKR " + String.format("%,.2f", amount)
                + "  " + fromNum + " → " + toNum);
        Customer receiver = findCustomerByAccountNumber(toNum);
        if (receiver != null && !receiver.getUserId().equals(sender.getUserId()))
            receiver.addNotification("Received LKR " + String.format("%,.2f", amount) + " from " + fromNum);

        FileHandler.saveAll(customers, admins);
    }

    // ── OPEN NEW ACCOUNT ─────────────────────────────────────────────────

    public synchronized Account openAccount(Customer c, String type,
                                            double initialDeposit, int fdMonths)
            throws BankingException {
        if (type == null) throw new BankingException("Account type is required.");
        if (c.getAccounts().size() >= MAX_ACCOUNTS)
            throw new BankingException("You can have at most " + MAX_ACCOUNTS + " accounts.");
        if (Double.isNaN(initialDeposit) || initialDeposit < 0)
            throw new InvalidAmountException("Initial deposit cannot be negative.");
        if (initialDeposit > 0) Account.checkAmount(initialDeposit);

        Account acc = switch (type.trim().toUpperCase()) {
            case "SAVINGS" -> new SavingsAccount(generateAccNum("SAV"), c.getUserId(), 0);
            case "CURRENT" -> new CurrentAccount(generateAccNum("CUR"), c.getUserId(), 0);
            case "FIXED", "FIXED_DEPOSIT" -> {
                if (initialDeposit < 10_000)
                    throw new BankingException("Fixed Deposit requires a minimum of LKR 10,000.");
                if (fdMonths != 3 && fdMonths != 6 && fdMonths != 12 && fdMonths != 24 && fdMonths != 36)
                    throw new BankingException("Fixed Deposit term must be 3, 6, 12, 24 or 36 months.");
                yield new FixedDepositAccount(generateAccNum("FD"), c.getUserId(), 0, fdMonths);
            }
            default -> throw new BankingException("Unknown account type: " + type);
        };
        if (initialDeposit > 0) acc.deposit(initialDeposit, "Account opening deposit", "DEP");

        c.addAccount(acc);
        FileHandler.saveAll(customers, admins);
        return acc;
    }

    // ── LOAN ─────────────────────────────────────────────────────────────

    public synchronized void applyLoan(Customer c, String accNum, double amount)
            throws BankingException {
        Account.checkAmount(amount);
        if (amount > 1_000_000)
            throw new BankingException("Loan amount must be between LKR 1 and LKR 1,000,000.");
        if (c.isLoanActive())
            throw new BankingException("You already have an active loan.");

        Account acc = requireAccount(c, accNum);
        acc.deposit(amount, "Loan disbursement", "DEP");   // validate + credit first
        c.applyForLoan(amount);                            // then record the loan
        FileHandler.saveAll(customers, admins);
    }

    public synchronized void repayLoan(Customer c, String accNum, double amount)
            throws BankingException {
        if (!c.isLoanActive())
            throw new BankingException("You have no active loan to repay.");
        Account.checkAmount(amount);
        if (amount > c.getLoanBalance())
            throw new BankingException("Amount exceeds outstanding loan balance: LKR "
                    + String.format("%,.2f", c.getLoanBalance()));

        Account acc = requireAccount(c, accNum);
        acc.withdraw(amount, "Loan repayment", "LOAN_REP");
        c.repayLoan(amount);
        FileHandler.saveAll(customers, admins);
    }

    // ── BILL PAYMENT ──────────────────────────────────────────────────────

    public static Map<String, Double> getBills() { return BILLS; }

    public synchronized void payBill(Customer c, String accNum, String billType)
            throws BankingException {
        String key = billType == null ? "" : billType.toUpperCase().trim();
        if (!BILLS.containsKey(key))
            throw new BankingException("Unknown bill type: " + billType + ". Valid: " + BILLS.keySet());

        double amount = BILLS.get(key);
        Account acc   = requireAccount(c, accNum);
        acc.withdraw(amount, "Bill payment: " + key, "BILL");
        c.addNotification("Paid " + key + " bill: LKR " + String.format("%,.2f", amount));
        FileHandler.saveAll(customers, admins);
    }

    // ── PROFILE ───────────────────────────────────────────────────────────

    public synchronized void updateProfile(Customer c, String email, String phone, String address)
            throws BankingException {
        email   = requireText(email,   "Email");
        phone   = requireText(phone,   "Phone number");
        address = requireText(address, "Address");
        if (!EMAIL.matcher(email).matches())  throw new BankingException("Invalid email address.");
        if (!PHONE.matcher(phone).matches())  throw new BankingException("Phone number must have 9-15 digits (optional leading +).");
        if (address.length() > 120)           throw new BankingException("Address is too long (max 120 characters).");
        for (Customer other : customers)
            if (other != c && other.getEmail().equalsIgnoreCase(email))
                throw new DuplicateRegistrationException("This email is already registered.");

        c.setEmail(email);
        c.setPhone(phone);
        c.setAddress(address);
        FileHandler.saveAll(customers, admins);
    }

    public synchronized void changePassword(User u, String oldPassword, String newPassword)
            throws BankingException {
        if (!u.validatePassword(oldPassword))
            throw new AuthenticationException("Current password is incorrect.");
        checkPasswordStrength(newPassword);
        u.changePassword(newPassword);
        FileHandler.saveAll(customers, admins);
    }

    // ── HELPERS ───────────────────────────────────────────────────────────

    private static void checkPasswordStrength(String p) throws BankingException {
        if (p == null || p.length() < 8)
            throw new BankingException("Password must be at least 8 characters.");
        if (p.length() > 100)
            throw new BankingException("Password is too long.");
        boolean letter = false, digit = false;
        for (char ch : p.toCharArray()) {
            if (Character.isLetter(ch)) letter = true;
            if (Character.isDigit(ch))  digit  = true;
        }
        if (!letter || !digit)
            throw new BankingException("Password must contain at least one letter and one number.");
    }

    /** Trims, rejects empty values and characters that would corrupt the '|' separated data files. */
    private static String requireText(String v, String label) throws BankingException {
        String t = v == null ? "" : v.trim();
        if (t.isEmpty()) throw new BankingException(label + " is required.");
        for (char ch : t.toCharArray())
            if (ch == '|' || Character.isISOControl(ch))
                throw new BankingException(label + " contains an invalid character.");
        return t;
    }

    private static String normalizeAccountNumber(String n) throws BankingException {
        String t = n == null ? "" : n.trim().toUpperCase();
        if (!ACC_NO.matcher(t).matches())
            throw new AccountNotFoundException("Invalid account number: " + (t.isEmpty() ? "(empty)" : t));
        return t;
    }

    /** The customer must OWN the account, and it must be active. */
    private Account requireAccount(Customer c, String accNum) throws BankingException {
        String num = normalizeAccountNumber(accNum);
        Account acc = c.getAccountByNumber(num);
        if (acc == null)
            throw new AccountNotFoundException("Account not found: " + num);
        if (!acc.isActive())
            throw new InactiveAccountException(num);
        return acc;
    }

    private String generateAccNum(String prefix) {
        return prefix + String.format("%07d", ++accCounter);
    }

    // ── FINDERS ───────────────────────────────────────────────────────────

    public User findUserByUsername(String username) {
        if (username == null) return null;
        for (Customer c : customers) if (c.getUsername().equalsIgnoreCase(username.trim())) return c;
        for (Admin    a : admins)    if (a.getUsername().equalsIgnoreCase(username.trim())) return a;
        return null;
    }

    public Customer findCustomerById(String id) {
        for (Customer c : customers) if (c.getUserId().equals(id)) return c;
        return null;
    }

    public Account findAccountByNumber(String num) {
        for (Customer c : customers)
            for (Account a : c.getAccounts())
                if (a.getAccountNumber().equals(num)) return a;
        return null;
    }

    public Customer findCustomerByAccountNumber(String num) {
        for (Customer c : customers)
            for (Account a : c.getAccounts())
                if (a.getAccountNumber().equals(num)) return c;
        return null;
    }

    // Used by FileHandler to rebuild state
    public void addCustomerDirect(Customer c) { customers.add(c); }
    public void addAdminDirect(Admin a)       { admins.add(a); }

    public List<Customer> getCustomers() { return customers; }
    public List<Admin>    getAdmins()    { return admins; }
}
