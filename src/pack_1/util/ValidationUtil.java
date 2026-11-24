package pack_1.util;

import pack_1.exception.BankingException;
import pack_1.exception.InvalidAmountException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Pattern;

/**
 * Validation utilities for banking inputs.
 * Demonstrates: Encapsulation, Defensive Programming, Data Validation.
 */
public final class ValidationUtil {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_.]{3,20}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[0-9]{9,15}$");
    private static final Pattern NIC_PATTERN = Pattern.compile("^[A-Za-z0-9]{9,15}$");
    private static final Pattern ACCOUNT_NUM_PATTERN = Pattern.compile("^(SAV|CUR|FD)\\d{7}$");

    public static final BigDecimal MAX_DEPOSIT_LIMIT = new BigDecimal("5000000.00");
    public static final BigDecimal MAX_TRANSFER_LIMIT = new BigDecimal("2000000.00");

    private ValidationUtil() {
        // Prevent instantiation
    }

    public static String validateUsername(String username) throws BankingException {
        String u = requireNonEmpty(username, "Username");
        if (!USERNAME_PATTERN.matcher(u).matches()) {
            throw new BankingException("Username must be 3-20 characters long and contain only letters, numbers, '.', or '_'.");
        }
        return u;
    }

    public static void validatePasswordStrength(String password) throws BankingException {
        if (password == null || password.length() < 8) {
            throw new BankingException("Password must be at least 8 characters long.");
        }
        if (password.length() > 100) {
            throw new BankingException("Password must not exceed 100 characters.");
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (char ch : password.toCharArray()) {
            if (Character.isLetter(ch)) hasLetter = true;
            if (Character.isDigit(ch)) hasDigit = true;
        }
        if (!hasLetter || !hasDigit) {
            throw new BankingException("Password must contain at least one letter and at least one number.");
        }
    }

    public static String validateEmail(String email) throws BankingException {
        String e = requireNonEmpty(email, "Email");
        if (!EMAIL_PATTERN.matcher(e).matches()) {
            throw new BankingException("Invalid email format.");
        }
        return e.toLowerCase();
    }

    public static String validatePhone(String phone) throws BankingException {
        String p = requireNonEmpty(phone, "Phone number").replaceAll("[\\s-]", "");
        if (!PHONE_PATTERN.matcher(p).matches()) {
            throw new BankingException("Phone number must have between 9 and 15 digits (e.g., 0771234567 or +94771234567).");
        }
        return p;
    }

    public static String validateNationalId(String nic) throws BankingException {
        String n = requireNonEmpty(nic, "National ID (NIC)");
        if (!NIC_PATTERN.matcher(n).matches()) {
            throw new BankingException("National ID must be between 9 and 15 alphanumeric characters.");
        }
        return n.toUpperCase();
    }

    public static String validateFullName(String fullName) throws BankingException {
        String f = requireNonEmpty(fullName, "Full name");
        if (f.length() < 2 || f.length() > 80) {
            throw new BankingException("Full name must be between 2 and 80 characters.");
        }
        return f;
    }

    public static String validateAddress(String address) throws BankingException {
        String a = requireNonEmpty(address, "Address");
        if (a.length() < 3 || a.length() > 150) {
            throw new BankingException("Address must be between 3 and 150 characters.");
        }
        return a;
    }

    public static String validateAccountNumber(String accNum) throws BankingException {
        String num = requireNonEmpty(accNum, "Account number").toUpperCase();
        if (!ACCOUNT_NUM_PATTERN.matcher(num).matches()) {
            throw new BankingException("Invalid account number format. Expected format: SAV0001001, CUR0001001, etc.");
        }
        return num;
    }

    public static BigDecimal validateAmount(BigDecimal amount, String operation) throws InvalidAmountException {
        if (amount == null) {
            throw new InvalidAmountException("Amount cannot be null.");
        }
        // Round to 2 decimal places with HALF_UP
        BigDecimal scaled = amount.setScale(2, RoundingMode.HALF_UP);
        if (scaled.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidAmountException("Amount must be greater than zero.");
        }
        if (scaled.scale() > 2) {
            throw new InvalidAmountException("Amount can have at most 2 decimal places.");
        }
        if ("DEPOSIT".equalsIgnoreCase(operation) && scaled.compareTo(MAX_DEPOSIT_LIMIT) > 0) {
            throw new InvalidAmountException("Deposit exceeds maximum single transaction limit of LKR 5,000,000.00.");
        }
        if ("TRANSFER".equalsIgnoreCase(operation) && scaled.compareTo(MAX_TRANSFER_LIMIT) > 0) {
            throw new InvalidAmountException("Transfer exceeds maximum single transaction limit of LKR 2,000,000.00.");
        }
        return scaled;
    }

    public static String requireNonEmpty(String text, String fieldName) throws BankingException {
        if (text == null || text.trim().isEmpty()) {
            throw new BankingException(fieldName + " is required.");
        }
        return text.trim();
    }
}
