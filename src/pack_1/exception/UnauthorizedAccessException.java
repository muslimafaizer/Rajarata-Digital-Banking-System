package pack_1.exception;

/**
 * Thrown when a user attempts an action outside their role or on another customer's resources.
 */
public class UnauthorizedAccessException extends BankingException {
    private static final long serialVersionUID = 1L;

    public UnauthorizedAccessException(String message) {
        super("FORBIDDEN", message);
    }
}
