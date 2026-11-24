package pack_1.exception;

/**
 * Base custom checked exception for all banking operations.
 * Demonstrates: Exception Handling Hierarchy and Encapsulation.
 */
public class BankingException extends Exception {
    private static final long serialVersionUID = 1L;
    private final String errorCode;

    public BankingException(String message) {
        super(message);
        this.errorCode = "BANKING_ERROR";
    }

    public BankingException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public BankingException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "BANKING_ERROR";
    }

    public String getErrorCode() {
        return errorCode;
    }
}
