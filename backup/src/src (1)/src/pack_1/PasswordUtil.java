package pack_1;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Password hashing with PBKDF2-HMAC-SHA256 (built into the JDK, no libraries needed).
 * Stored format:  pbkdf2$iterations$salt$hash   (never contains '|', so it is safe in the data files)
 */
public final class PasswordUtil {
    private static final String PREFIX     = "pbkdf2";
    private static final int    ITERATIONS = 120_000;
    private static final SecureRandom RNG  = new SecureRandom();
    private static final String CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";

    private PasswordUtil() { }

    public static String hash(String password) {
        byte[] salt = new byte[16];
        RNG.nextBytes(salt);
        return PREFIX + "$" + ITERATIONS + "$" + enc(salt) + "$" + enc(derive(password, salt, ITERATIONS));
    }

    public static boolean isHashed(String stored) {
        return stored != null && stored.startsWith(PREFIX + "$");
    }

    public static boolean verify(String password, String stored) {
        if (password == null || !isHashed(stored)) return false;
        try {
            String[] p = stored.split("\\$");
            if (p.length != 4) return false;
            byte[] expected = Base64.getDecoder().decode(p[3]);
            byte[] actual   = derive(password, Base64.getDecoder().decode(p[2]), Integer.parseInt(p[1]));
            return MessageDigest.isEqual(expected, actual);   // constant-time comparison
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Random password for the first-run admin account. */
    public static String randomPassword(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) sb.append(CHARS.charAt(RNG.nextInt(CHARS.length())));
        return sb.toString();
    }

    private static byte[] derive(String password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, 256);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("Password hashing unavailable", e);
        }
    }

    private static String enc(byte[] b) { return Base64.getEncoder().encodeToString(b); }
}
