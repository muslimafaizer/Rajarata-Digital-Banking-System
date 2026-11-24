package pack_1;

/** Thrown when an account number is malformed or does not exist. */
public class AccountNotFoundException extends BankingException {
    private static final long serialVersionUID = 1L;
    public AccountNotFoundException(String message) { super("ACCOUNT_NOT_FOUND", message); }
}
