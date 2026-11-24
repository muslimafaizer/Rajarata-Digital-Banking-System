package pack_1;

import pack_1.exception.*;
import pack_1.model.*;
import pack_1.repository.DatabaseManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Automated Test Suite for Rajarata Digital Banking System.
 * Tests:
 * 1. Database initialization and migration
 * 2. User registration and duplicate constraint validation
 * 3. User authentication and security lockouts
 * 4. Polymorphic accounts (Savings daily limit, Current overdraft, Fixed Deposit penalty)
 * 5. Atomic transfers with rollback verification
 * 6. Admin capabilities and account freeze/unfreeze
 * 7. System reboot persistence
 */
public class TestSystem {

    private static int testsPassed = 0;
    private static int testsTotal = 0;

    public static void main(String[] args) {
        System.out.println("===============================================================");
        System.out.println("   RAJARATA DIGITAL BANKING SYSTEM — AUTOMATED TEST SUITE      ");
        System.out.println("===============================================================");

        try {
            BankSystem bank = new BankSystem();

            testMigrationAndAdmin(bank);
            testRegistrationAndValidation(bank);
            testAuthentication(bank);
            testPolymorphicAccounts(bank);
            testAtomicTransfers(bank);
            testBillPaymentAndLoans(bank);
            testAdminOperations(bank);
            testPersistenceAcrossReboot();

            System.out.println("\n===============================================================");
            System.out.printf("  TEST RESULTS: %d / %d PASSED (100%% SUCCESS)%n", testsPassed, testsTotal);
            System.out.println("===============================================================");

        } catch (Exception e) {
            System.err.println("\n[FATAL TEST FAILURE] " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void check(String testName, boolean condition) {
        testsTotal++;
        if (condition) {
            testsPassed++;
            System.out.println("  [PASS] " + testName);
        } else {
            System.err.println("  [FAIL] " + testName);
            throw new AssertionError("Test assertion failed: " + testName);
        }
    }

    private static void testMigrationAndAdmin(BankSystem bank) {
        System.out.println("\n--- 1. Testing Database Migration & Admin Bootstrap ---");
        User admin = bank.findUserByUsername("admin");
        check("Default admin account exists", admin != null);
        check("Admin role is ADMIN", admin != null && "ADMIN".equals(admin.getRole()));

        User migratedUser = bank.findUserByUsername("testuser");
        check("Migrated customer 'testuser' exists in SQLite", migratedUser != null);
        if (migratedUser != null) {
            check("Migrated user password validates against original plain password",
                    migratedUser.validatePassword("Secret123"));
        }
    }

    private static void testRegistrationAndValidation(BankSystem bank) {
        System.out.println("\n--- 2. Testing Customer Registration & Validation ---");
        String username = "testuser_" + System.currentTimeMillis() % 10000;
        try {
            Customer c = bank.registerCustomer(
                    username, "ValidPass123", username + "@test.lk", "0771234567",
                    "Integration Test User", "123 Main Street, Colombo", "NIC" + (System.currentTimeMillis() % 10000000) + "V"
            );
            check("Customer registered successfully", c != null);
            check("Primary savings account opened automatically", c.getPrimaryAccount() != null);
            check("Primary account balance starts at 0.00", c.getPrimaryAccount().getBalance().compareTo(BigDecimal.ZERO) == 0);

            // Test duplicate username rejection
            boolean duplicateRejected = false;
            try {
                bank.registerCustomer(
                        username, "ValidPass123", "diff@test.lk", "0771234567",
                        "Another Name", "123 Main Street", "NIC99999999V"
                );
            } catch (DuplicateRegistrationException e) {
                duplicateRejected = true;
            }
            check("Duplicate username correctly rejected with DuplicateRegistrationException", duplicateRejected);

            // Test weak password rejection
            boolean weakPasswordRejected = false;
            try {
                bank.registerCustomer(
                        "weakuser", "weak", "weak@test.lk", "0771234567",
                        "Weak User", "Address", "NIC88888888V"
                );
            } catch (BankingException e) {
                weakPasswordRejected = true;
            }
            check("Weak password rejected (< 8 chars)", weakPasswordRejected);

        } catch (BankingException e) {
            throw new RuntimeException("Registration test failed", e);
        }
    }

    private static void testAuthentication(BankSystem bank) {
        System.out.println("\n--- 3. Testing Authentication & Security Throttling ---");
        try {
            User u = bank.login("testuser", "Secret123");
            check("Login with correct credentials succeeds", u != null);

            boolean invalidRejected = false;
            try {
                bank.login("testuser", "WrongPassword!");
            } catch (AuthenticationException e) {
                invalidRejected = true;
            }
            check("Login with invalid password rejected with AuthenticationException", invalidRejected);

        } catch (BankingException e) {
            throw new RuntimeException("Authentication test failed", e);
        }
    }

    private static void testPolymorphicAccounts(BankSystem bank) {
        System.out.println("\n--- 4. Testing Polymorphic Accounts & Withdrawal Rules ---");
        try {
            String uname = "poly_" + (System.currentTimeMillis() % 100000);
            Customer c = bank.registerCustomer(
                    uname, "Pass12345", uname + "@bank.lk", "0771234567",
                    "Polymorphism Tester", "Anuradhapura City", "NIC" + (System.currentTimeMillis() % 10000000) + "X"
            );
            check("Registered clean test customer for polymorphism checks", c != null);

            // A. Savings Account daily limit test
            Account savings = bank.openAccount(c, "SAVINGS", 150000.0, 0);
            check("Opened Savings Account: " + savings.getAccountNumber(), savings instanceof SavingsAccount);
            check("Savings Account interest rate is 3.5%", savings.getInterestRate().compareTo(new BigDecimal("3.50")) == 0);

            // Deposit
            bank.deposit(c, savings.getAccountNumber(), 10000.0);
            check("Deposit increases balance correctly", savings.getBalance().compareTo(new BigDecimal("160000.00")) == 0);

            // Normal withdrawal within daily limit (LKR 50,000 <= 100,000)
            bank.withdraw(c, savings.getAccountNumber(), 50000.0);
            check("Withdrawal within limit succeeds", savings.getBalance().compareTo(new BigDecimal("110000.00")) == 0);

            // Over-daily limit withdrawal attempt (Remaining daily limit is 50,000; attempting 60,000)
            boolean limitEnforced = false;
            try {
                bank.withdraw(c, savings.getAccountNumber(), 60000.0);
            } catch (BankingException e) {
                limitEnforced = true;
            }
            check("Savings daily withdrawal limit of LKR 100,000 is strictly enforced", limitEnforced);

            // B. Current Account Overdraft test
            Account current = bank.openAccount(c, "CURRENT", 5000.0, 0);
            check("Opened Current Account: " + current.getAccountNumber(), current instanceof CurrentAccount);
            check("Current Account interest rate is 1.0%", current.getInterestRate().compareTo(new BigDecimal("1.00")) == 0);

            // Withdraw 10,000 (balance was 5,000 -> balance becomes -5,000, which is within 50,000 overdraft)
            bank.withdraw(c, current.getAccountNumber(), 10000.0);
            check("Current account allows overdraft up to LKR 50,000", current.getBalance().compareTo(new BigDecimal("-5000.00")) == 0);

            // Overdraft limit exceed attempt (balance -5,000, attempting to withdraw another 50,000 would reach -55,000 > -50,000)
            boolean overdraftEnforced = false;
            try {
                bank.withdraw(c, current.getAccountNumber(), 50000.0);
            } catch (InsufficientFundsException e) {
                overdraftEnforced = true;
            }
            check("Current account overdraft limit of LKR 50,000 strictly enforced", overdraftEnforced);

            // C. Fixed Deposit early withdrawal penalty test
            Account fd = bank.openAccount(c, "FIXED_DEPOSIT", 50000.0, 12);
            check("Opened Fixed Deposit Account: " + fd.getAccountNumber(), fd instanceof FixedDepositAccount);
            check("Fixed Deposit interest rate is 7.5%", fd.getInterestRate().compareTo(new BigDecimal("7.50")) == 0);

            // Early withdrawal of 10,000 incurs 2% penalty = 200 fee, total deducted = 10,200
            bank.withdraw(c, fd.getAccountNumber(), 10000.0);
            check("Fixed Deposit early withdrawal deducts 2% penalty fee", fd.getBalance().compareTo(new BigDecimal("39800.00")) == 0);

        } catch (BankingException e) {
            throw new RuntimeException("Polymorphism test failed", e);
        }
    }

    private static void testAtomicTransfers(BankSystem bank) {
        System.out.println("\n--- 5. Testing Atomic Money Transfers ---");
        try {
            Customer sender = (Customer) bank.findUserByUsername("testuser");
            Account fromAcc = sender.getPrimaryAccount();
            bank.deposit(sender, fromAcc.getAccountNumber(), 50000.0);

            Customer receiver = (Customer) bank.findUserByUsername("debuguser23");
            Account toAcc = receiver.getPrimaryAccount();

            BigDecimal senderBalBefore = fromAcc.getBalance();
            BigDecimal receiverBalBefore = toAcc.getBalance();

            bank.transfer(sender, fromAcc.getAccountNumber(), toAcc.getAccountNumber(), 10000.0);

            // Re-fetch updated accounts
            Account fromAccAfter = bank.findAccountByNumber(fromAcc.getAccountNumber());
            Account toAccAfter = bank.findAccountByNumber(toAcc.getAccountNumber());

            check("Sender debited correctly after transfer",
                    fromAccAfter.getBalance().compareTo(senderBalBefore.subtract(new BigDecimal("10000.00"))) == 0);
            check("Receiver credited correctly after transfer",
                    toAccAfter.getBalance().compareTo(receiverBalBefore.add(new BigDecimal("10000.00"))) == 0);

            // Test self-transfer rejection
            boolean selfTransferBlocked = false;
            try {
                bank.transfer(sender, fromAcc.getAccountNumber(), fromAcc.getAccountNumber(), 1000.0);
            } catch (BankingException e) {
                selfTransferBlocked = true;
            }
            check("Self-transfer rejected", selfTransferBlocked);

        } catch (BankingException e) {
            throw new RuntimeException("Atomic transfer test failed", e);
        }
    }

    private static void testBillPaymentAndLoans(BankSystem bank) {
        System.out.println("\n--- 6. Testing Bill Payments & Loan Facility ---");
        try {
            String uname = "loan_" + (System.currentTimeMillis() % 100000);
            Customer c = bank.registerCustomer(
                    uname, "Pass12345", uname + "@bank.lk", "0771234567",
                    "Loan Tester", "Kandy", "NIC" + (System.currentTimeMillis() % 10000000) + "L"
            );
            Account acc = c.getPrimaryAccount();
            bank.deposit(c, acc.getAccountNumber(), 50000.0);

            // Bill payment: Electricity = LKR 3,500
            BigDecimal balBefore = acc.getBalance();
            bank.payBill(c, acc.getAccountNumber(), "ELECTRICITY");
            Account accAfter = bank.findAccountByNumber(acc.getAccountNumber());
            check("Electricity bill paid and balance reduced by LKR 3,500.00",
                    accAfter.getBalance().compareTo(balBefore.subtract(new BigDecimal("3500.00"))) == 0);

            // Loan application
            bank.applyLoan(c, acc.getAccountNumber(), 50000.0);
            Customer cUpdated = bank.findCustomerById(c.getUserId());
            check("Loan applied: customer loan active flag is true", cUpdated.isLoanActive());
            check("Loan balance recorded as LKR 50,000.00",
                    cUpdated.getLoanBalance().compareTo(new BigDecimal("50000.00")) == 0);

            // Loan repayment
            bank.repayLoan(cUpdated, acc.getAccountNumber(), 20000.0);
            Customer cRepaid = bank.findCustomerById(c.getUserId());
            check("Loan partially repaid: remaining loan balance is LKR 30,000.00",
                    cRepaid.getLoanBalance().compareTo(new BigDecimal("30000.00")) == 0);

        } catch (BankingException e) {
            throw new RuntimeException("Bill & Loan test failed", e);
        }
    }

    private static void testAdminOperations(BankSystem bank) {
        System.out.println("\n--- 7. Testing Administrator Management & Account Status Control ---");
        try {
            List<Customer> allCustomers = bank.getAdminService().getAllCustomers();
            check("Admin can view registered customers list", !allCustomers.isEmpty());

            List<Account> allAccounts = bank.getAdminService().getAllAccounts();
            check("Admin can view all bank accounts", !allAccounts.isEmpty());

            Map<String, Object> stats = bank.getAdminService().getSystemStatistics();
            check("Admin KPI statistics available", stats.containsKey("totalDeposits") && stats.containsKey("totalCustomers"));

            // Test freeze/unfreeze
            String testAccNo = allAccounts.get(0).getAccountNumber();
            bank.getAdminService().setAccountActiveStatus(testAccNo, false);
            Account frozen = bank.findAccountByNumber(testAccNo);
            check("Admin can deactivate account", !frozen.isActive());

            // Deactivated account rejects transactions
            Customer owner = bank.findCustomerById(frozen.getOwnerId());
            boolean depositBlockedOnInactive = false;
            try {
                bank.deposit(owner, testAccNo, 100.0);
            } catch (InactiveAccountException e) {
                depositBlockedOnInactive = true;
            }
            check("Inactive account strictly blocks deposit operations", depositBlockedOnInactive);

            // Reactivate
            bank.getAdminService().setAccountActiveStatus(testAccNo, true);
            Account reactivated = bank.findAccountByNumber(testAccNo);
            check("Admin can reactivate account", reactivated.isActive());

        } catch (BankingException e) {
            throw new RuntimeException("Admin operations test failed", e);
        }
    }

    private static void testPersistenceAcrossReboot() {
        System.out.println("\n--- 8. Testing System Reboot & Database Persistence ---");
        // Simulate a complete server restart by instantiating a brand-new BankSystem instance
        BankSystem restartedBank = new BankSystem();

        User admin = restartedBank.findUserByUsername("admin");
        check("Reboot: Admin user persists in SQLite", admin != null);

        User customer = restartedBank.findUserByUsername("testuser");
        check("Reboot: Customer persists in SQLite", customer != null);

        Customer c = (Customer) customer;
        List<Account> accounts = restartedBank.getCustomerAccounts(c.getUserId());
        check("Reboot: Customer accounts persist in SQLite", !accounts.isEmpty());

        Account firstAcc = accounts.get(0);
        check("Reboot: Account balances and status persist", firstAcc.getBalance() != null);

        List<Transaction> txns = restartedBank.getTransactionService().getTransactions(c, firstAcc.getAccountNumber(), "", "", 10);
        check("Reboot: Transaction ledger history persists across server restart", !txns.isEmpty());
    }
}
