package pack_1;

/** Thrown when a withdrawal or transfer would overdraw an account. */
public class InsufficientFundsException extends BankingException {
    private static final long serialVersionUID = 1L;
    public InsufficientFundsException(String message) { super("INSUFFICIENT_FUNDS", message); }
}
