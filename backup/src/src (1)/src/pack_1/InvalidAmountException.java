package pack_1;

/** Thrown when an amount is zero, negative, not a number or has too many decimals. */
public class InvalidAmountException extends BankingException {
    private static final long serialVersionUID = 1L;
    public InvalidAmountException(String message) { super("INVALID_AMOUNT", message); }
}
