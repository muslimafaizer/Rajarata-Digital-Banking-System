package pack_1.exception;

/**
 * Thrown when an operation is attempted on an account that is closed or inactive.
 */
public class InactiveAccountException extends BankingException {
    private static final long serialVersionUID = 1L;

    public InactiveAccountException(String accountNumber) {
        super("INACTIVE_ACCOUNT", "Account " + accountNumber + " is deactivated or inactive.");
    }
}
