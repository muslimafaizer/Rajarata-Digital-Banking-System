package pack_1.exception;

/**
 * Thrown when an invalid transaction amount is supplied (negative, zero, NaN, or improper scale).
 */
public class InvalidAmountException extends BankingException {
    private static final long serialVersionUID = 1L;

    public InvalidAmountException(String message) {
        super("INVALID_AMOUNT", message);
    }
}
