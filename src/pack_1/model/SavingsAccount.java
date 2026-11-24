package pack_1.model;

import pack_1.exception.BankingException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * SavingsAccount — standard savings account offering 3.5% p.a. interest.
 * Demonstrates OOP:
 * - Inheritance: Subclass of Account
 * - Polymorphism: Overrides getInterestRate, getAccountFeatures, calculateMonthlyInterest, and checkWithdrawalRules
 */
public class SavingsAccount extends Account {

    private static final BigDecimal INTEREST_RATE = new BigDecimal("3.50");
    private static final BigDecimal DAILY_WITHDRAWAL_LIMIT = new BigDecimal("100000.00");

    private BigDecimal dailyWithdrawn = BigDecimal.ZERO;
    private String lastWithdrawDate = "";

    public SavingsAccount(String accountNumber, String ownerId, BigDecimal initialBalance) {
        super(accountNumber, ownerId, initialBalance, "SAVINGS");
    }

    @Override
    public BigDecimal getInterestRate() {
        return INTEREST_RATE;
    }

    @Override
    public String getAccountFeatures() {
        return "Interest: 3.5% p.a. | Daily Withdrawal Limit: LKR 100,000.00 | Free Online Banking";
    }

    @Override
    public BigDecimal calculateMonthlyInterest() {
        // Monthly interest = Balance * (Rate / 100 / 12)
        return getBalance()
                .multiply(INTEREST_RATE)
                .divide(new BigDecimal("1200"), 2, RoundingMode.HALF_UP);
    }

    // Polymorphism: overrides parent hook to enforce daily limit
    @Override
    protected void checkWithdrawalRules(BigDecimal amount) throws BankingException {
        String today = LocalDate.now().toString();
        if (!today.equals(lastWithdrawDate)) {
            dailyWithdrawn = BigDecimal.ZERO;
            lastWithdrawDate = today;
        }
        if (dailyWithdrawn.add(amount).compareTo(DAILY_WITHDRAWAL_LIMIT) > 0) {
            throw new BankingException("LIMIT_EXCEEDED",
                    "Daily withdrawal limit of LKR 100,000.00 exceeded for Savings Account. Withdrawn today: LKR " + dailyWithdrawn);
        }
        dailyWithdrawn = dailyWithdrawn.add(amount);
    }
}
