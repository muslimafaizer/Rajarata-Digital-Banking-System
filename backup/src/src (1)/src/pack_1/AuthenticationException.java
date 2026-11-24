package pack_1;

/** Thrown for invalid login credentials or a temporarily locked login. */
public class AuthenticationException extends BankingException {
    private static final long serialVersionUID = 1L;
    public AuthenticationException(String message)               { super("AUTH_FAILED", message); }
    public AuthenticationException(String code, String message)  { super(code, message); }
}
