package pack_1;

/**
 * CurrentAccount — business account with overdraft facility.
 * OOP: Inheritance + Polymorphism (overrides withdraw)
 */
public class CurrentAccount extends Account {

    private static final double INTEREST_RATE   = 1.0;
    private static final double OVERDRAFT_LIMIT = 50_000.0;

    public CurrentAccount(String accountNumber, String ownerId, double initialBalance) {
        super(accountNumber, ownerId, initialBalance, "CURRENT");
    }

    @Override
    public double getInterestRate() { return INTEREST_RATE; }

    @Override
    public String getAccountFeatures() {
        return "Interest: 1.0% p.a. | Overdraft Facility: LKR 50,000";
    }

    // Polymorphism: overrides withdraw so that a current account may go into its overdraft limit
    @Override
    public synchronized void withdraw(double amount, String description, String type)
            throws BankingException {
        if (!isActive()) throw new InactiveAccountException(getAccountNumber());
        checkAmount(amount);
        if (getBalance() - amount < -OVERDRAFT_LIMIT)
            throw new InsufficientFundsException(
                "Exceeds overdraft limit. Max overdraft: LKR 50,000.");

        setBalance(getBalance() - amount);
        addTransaction(new Transaction(type, amount, getBalance(),
                description, getAccountNumber()));
    }
}
