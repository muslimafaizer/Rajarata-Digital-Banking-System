package pack_1.exception;

/**
 * Thrown when trying to register with a username, email, or NIC that already exists.
 */
public class DuplicateRegistrationException extends BankingException {
    private static final long serialVersionUID = 1L;

    public DuplicateRegistrationException(String message) {
        super("DUPLICATE_REGISTRATION", message);
    }
}
