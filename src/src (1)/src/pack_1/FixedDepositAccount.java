package pack_1;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * FixedDepositAccount — high interest, locked for a fixed term.
 * OOP: Inheritance + Polymorphism (early withdrawal penalty)
 */
public class FixedDepositAccount extends Account {

    private static final double INTEREST_RATE = 7.5;
    private static final double PENALTY_RATE  = 0.02;

    private LocalDate maturityDate;

    public FixedDepositAccount(String accountNumber, String ownerId,
                               double initialBalance, int termMonths) {
        super(accountNumber, ownerId, initialBalance, "FIXED_DEPOSIT");
        this.maturityDate = LocalDate.now().plusMonths(termMonths);
    }

    @Override
    public double getInterestRate() { return INTEREST_RATE; }

    @Override
    public String getAccountFeatures() {
        return "Interest: 7.5% p.a. | Locked until: "
             + maturityDate.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
             + " | Early withdrawal: 2% penalty";
    }

    // Polymorphism: early withdrawals are charged a real 2% penalty (recorded as a FEE transaction)
    @Override
    public synchronized void withdraw(double amount, String description, String type)
            throws BankingException {
        double penalty = isMatured() ? 0 : round2(amount * PENALTY_RATE);
        if (!Double.isNaN(amount) && amount > 0 && amount + penalty > getBalance())
            throw new InsufficientFundsException(
                "Insufficient funds for withdrawal plus 2% early-withdrawal penalty (LKR "
                + String.format("%,.2f", penalty) + ").");
        super.withdraw(amount, description, type);
        if (penalty > 0) {
            setBalance(getBalance() - penalty);
            addTransaction(new Transaction("FEE", penalty, getBalance(),
                    "Early withdrawal penalty (2%)", getAccountNumber()));
        }
    }

    public boolean   isMatured()       { return !LocalDate.now().isBefore(maturityDate); }
    public LocalDate getMaturityDate() { return maturityDate; }
    void setMaturityDate(LocalDate d)  { this.maturityDate = d; }

    @Override
    public String toFileString() {
        return super.toFileString() + "|" + maturityDate;
    }
}
