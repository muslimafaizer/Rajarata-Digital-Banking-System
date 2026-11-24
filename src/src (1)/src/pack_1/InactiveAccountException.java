package pack_1;

/** Thrown when an operation is attempted on an inactive account. */
public class InactiveAccountException extends BankingException {
    private static final long serialVersionUID = 1L;
    public InactiveAccountException(String accountNumber) {
        super("ACCOUNT_INACTIVE", "Account " + accountNumber + " is inactive.");
    }
}
