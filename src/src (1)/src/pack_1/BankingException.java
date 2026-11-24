package pack_1;

/**
 * Base class for all banking errors.
 * OOP: Exception Handling + Inheritance (specific exceptions extend this class)
 */
public class BankingException extends Exception {
    private static final long serialVersionUID = 1L;

    private final String code;   // machine-readable error code sent to the frontend

    public BankingException(String message) {
        this("BANKING_ERROR", message);
    }

    public BankingException(String code, String message) {
        super(message);
        this.code = code;
    }

    public BankingException(String message, Throwable cause) {
        super(message, cause);
        this.code = "BANKING_ERROR";
    }

    public String getCode() { return code; }
}
