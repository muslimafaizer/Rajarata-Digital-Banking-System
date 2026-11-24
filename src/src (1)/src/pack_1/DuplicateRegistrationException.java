package pack_1;

/** Thrown when a username, email or national ID is already registered. */
public class DuplicateRegistrationException extends BankingException {
    private static final long serialVersionUID = 1L;
    public DuplicateRegistrationException(String message) { super("DUPLICATE_REGISTRATION", message); }
}
