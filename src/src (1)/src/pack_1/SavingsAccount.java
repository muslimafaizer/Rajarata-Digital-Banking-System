package pack_1;

/**
 * SavingsAccount — 3.5% interest, daily withdrawal limit LKR 100,000.
 * OOP: Inheritance (extends Account) + Polymorphism (overrides methods)
 */
public class SavingsAccount extends Account {

    private static final double INTEREST_RATE      = 3.5;
    private static final double MAX_DAILY_WITHDRAW = 100_000.0;

    private double dailyWithdrawn  = 0;
    private String lastWithdrawDate = "";

    public SavingsAccount(String accountNumber, String ownerId, double initialBalance) {
        super(accountNumber, ownerId, initialBalance, "SAVINGS");
    }

    @Override
    public double getInterestRate() { return INTEREST_RATE; }

    @Override
    public String getAccountFeatures() {
        return "Interest: 3.5% p.a. | Daily Withdrawal Limit: LKR 100,000";
    }

    // Polymorphism: overrides parent hook to enforce daily limit
    @Override
    protected void checkWithdrawalLimit(double amount) throws BankingException {
        String today = java.time.LocalDate.now().toString();
        if (!today.equals(lastWithdrawDate)) {
            dailyWithdrawn   = 0;
            lastWithdrawDate = today;
        }
        if (dailyWithdrawn + amount > MAX_DAILY_WITHDRAW)
            throw new BankingException("LIMIT_EXCEEDED",
                "Daily withdrawal limit exceeded (LKR 100,000/day).");
        dailyWithdrawn += amount;
    }

    public void applyMonthlyInterest() {
        double interest = round2(getBalance() * (INTEREST_RATE / 100.0 / 12.0));
        if (interest <= 0) return;
        try {
            deposit(interest, "Monthly interest credit", "INT");
        } catch (BankingException ignored) { }
    }
}
