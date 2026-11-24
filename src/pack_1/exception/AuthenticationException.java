package pack_1.exception;

/**
 * Thrown when user authentication fails or access is locked.
 */
public class AuthenticationException extends BankingException {
    private static final long serialVersionUID = 1L;

    public AuthenticationException(String message) {
        super("AUTH_FAILED", message);
    }

    public AuthenticationException(String errorCode, String message) {
        super(errorCode, message);
    }
}
