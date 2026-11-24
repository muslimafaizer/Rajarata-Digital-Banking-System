package pack_1.exception;

/**
 * Thrown when an account cannot be located by its account number.
 */
public class AccountNotFoundException extends BankingException {
    private static final long serialVersionUID = 1L;

    public AccountNotFoundException(String accountNumber) {
        super("ACCOUNT_NOT_FOUND", "Account not found: " + accountNumber);
    }
}
