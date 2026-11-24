package pack_1.exception;

/**
 * Thrown when an account does not have sufficient balance or overdraft coverage.
 */
public class InsufficientFundsException extends BankingException {
    private static final long serialVersionUID = 1L;

    public InsufficientFundsException(String message) {
        super("INSUFFICIENT_FUNDS", message);
    }
}
