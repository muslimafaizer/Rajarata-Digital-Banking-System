package pack_1.model;

import pack_1.exception.BankingException;
import pack_1.exception.InactiveAccountException;
import pack_1.exception.InsufficientFundsException;
import pack_1.util.ValidationUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * CurrentAccount — checking account for business/personal use with overdraft protection.
 * Demonstrates OOP:
 * - Inheritance: Extends Account
 * - Polymorphism: Overrides withdraw() method to support approved overdraft up to LKR 50,000.00
 */
public class CurrentAccount extends Account {

    private static final BigDecimal INTEREST_RATE = new BigDecimal("1.00");
    private static final BigDecimal OVERDRAFT_LIMIT = new BigDecimal("50000.00");

    public CurrentAccount(String accountNumber, String ownerId, BigDecimal initialBalance) {
        super(accountNumber, ownerId, initialBalance, "CURRENT");
    }

    @Override
    public BigDecimal getInterestRate() {
        return INTEREST_RATE;
    }

    @Override
    public String getAccountFeatures() {
        return "Interest: 1.0% p.a. | Overdraft Facility: Up to LKR 50,000.00 | Cheque Book Enabled";
    }

    @Override
    public BigDecimal calculateMonthlyInterest() {
        if (getBalance().compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return getBalance()
                .multiply(INTEREST_RATE)
                .divide(new BigDecimal("1200"), 2, RoundingMode.HALF_UP);
    }

    // Polymorphism: overrides withdraw so that a current account may draw into overdraft limit
    @Override
    public synchronized void withdraw(BigDecimal amount, String description, String type) throws BankingException {
        if (!isActive()) {
            throw new InactiveAccountException(getAccountNumber());
        }
        BigDecimal scaled = ValidationUtil.validateAmount(amount, "WITHDRAWAL");
        BigDecimal allowedMinimum = OVERDRAFT_LIMIT.negate();

        if (getBalance().subtract(scaled).compareTo(allowedMinimum) < 0) {
            throw new InsufficientFundsException(
                    "Withdrawal exceeds available funds plus maximum overdraft limit of LKR 50,000.00. Current balance: LKR " + getBalance());
        }

        setBalanceDirect(getBalance().subtract(scaled));
        addTransaction(new Transaction(type, scaled, getBalance(), description, getAccountNumber()));
    }
}
