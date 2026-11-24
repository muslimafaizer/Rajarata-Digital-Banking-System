package pack_1.model;

import pack_1.exception.BankingException;
import pack_1.exception.InsufficientFundsException;
import pack_1.util.ValidationUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * FixedDepositAccount — term deposit offering 7.5% p.a. locked for a fixed tenure.
 * Demonstrates OOP:
 * - Inheritance: Subclass of Account
 * - Polymorphism: Enforces tenure maturity checks and 2% early-withdrawal penalty deduction
 */
public class FixedDepositAccount extends Account {

    private static final BigDecimal INTEREST_RATE = new BigDecimal("7.50");
    private static final BigDecimal PENALTY_RATE = new BigDecimal("0.02"); // 2% penalty for premature withdrawal

    private int termMonths;
    private LocalDate maturityDate;

    public FixedDepositAccount(String accountNumber, String ownerId, BigDecimal initialBalance, int termMonths) {
        super(accountNumber, ownerId, initialBalance, "FIXED_DEPOSIT");
        this.termMonths = termMonths > 0 ? termMonths : 12;
        this.maturityDate = LocalDate.now().plusMonths(this.termMonths);
    }

    @Override
    public BigDecimal getInterestRate() {
        return INTEREST_RATE;
    }

    @Override
    public String getAccountFeatures() {
        return "Interest: 7.5% p.a. | Term: " + termMonths + " Months | Locked until: "
                + maturityDate.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
                + " | Early withdrawal subject to 2% penalty";
    }

    @Override
    public BigDecimal calculateMonthlyInterest() {
        return getBalance()
                .multiply(INTEREST_RATE)
                .divide(new BigDecimal("1200"), 2, RoundingMode.HALF_UP);
    }

    public boolean isMatured() {
        return !LocalDate.now().isBefore(maturityDate);
    }

    public LocalDate getMaturityDate() {
        return maturityDate;
    }

    public void setMaturityDate(LocalDate maturityDate) {
        this.maturityDate = maturityDate;
    }

    public int getTermMonths() {
        return termMonths;
    }

    public void setTermMonths(int termMonths) {
        this.termMonths = termMonths;
    }

    // Polymorphism: Early withdrawals incur a 2% penalty, recorded as an automatic FEE transaction
    @Override
    public synchronized void withdraw(BigDecimal amount, String description, String type) throws BankingException {
        BigDecimal scaled = ValidationUtil.validateAmount(amount, "WITHDRAWAL");
        BigDecimal penalty = isMatured() ? BigDecimal.ZERO : scaled.multiply(PENALTY_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalRequired = scaled.add(penalty);

        if (totalRequired.compareTo(getBalance()) > 0) {
            throw new InsufficientFundsException(
                    "Insufficient funds for withdrawal plus 2% early withdrawal penalty of LKR " + penalty + ". Required: LKR " + totalRequired);
        }

        super.withdraw(scaled, description, type);

        if (penalty.compareTo(BigDecimal.ZERO) > 0) {
            setBalanceDirect(getBalance().subtract(penalty));
            addTransaction(new Transaction("FEE", penalty, getBalance(), "Early withdrawal penalty (2%)", getAccountNumber()));
        }
    }
}
