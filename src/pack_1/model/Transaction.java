package pack_1.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents an individual financial transaction on an account.
 * Demonstrates OOP:
 * - Encapsulation: Immutable attributes after creation, getters for all properties
 * - Composition: Used inside Account class
 */
public class Transaction {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
    private static final AtomicLong SEQ = new AtomicLong(System.currentTimeMillis());

    private final String transactionId;
    private final String type; // DEP, WDR, TRF_OUT, TRF_IN, BILL, LOAN_REP, INT, FEE
    private final BigDecimal amount;
    private final BigDecimal balanceAfter;
    private final String description;
    private final String accountNumber;
    private final String timestamp;

    public Transaction(String type, BigDecimal amount, BigDecimal balanceAfter,
                       String description, String accountNumber) {
        this("TXN" + SEQ.incrementAndGet(), type, amount, balanceAfter, description,
                accountNumber, LocalDateTime.now().format(FMT));
    }

    public Transaction(String transactionId, String type, BigDecimal amount, BigDecimal balanceAfter,
                       String description, String accountNumber, String timestamp) {
        this.transactionId = transactionId;
        this.type = type;
        this.amount = amount != null ? amount.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        this.balanceAfter = balanceAfter != null ? balanceAfter.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        this.description = description == null ? "" : description.replace('|', '/');
        this.accountNumber = accountNumber;
        this.timestamp = timestamp;
        SEQ.accumulateAndGet(extractSequence(transactionId), Math::max);
    }

    private static long extractSequence(String id) {
        if (id != null && id.startsWith("TXN")) {
            try {
                return Long.parseLong(id.substring(3));
            } catch (NumberFormatException ignored) {}
        }
        return 0L;
    }

    public boolean isCredit() {
        return switch (type) {
            case "DEP", "TRF_IN", "INT" -> true;
            default -> false;
        };
    }

    public long getSequence() {
        return extractSequence(transactionId);
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public String getDescription() {
        return description;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String toTableRow() {
        String label = switch (type) {
            case "DEP" -> "Credit";
            case "WDR" -> "Debit";
            case "TRF_OUT" -> "Sent";
            case "TRF_IN" -> "Received";
            case "BILL" -> "Bill Pay";
            case "LOAN_REP" -> "Loan Rep";
            case "INT" -> "Interest";
            case "FEE" -> "Fee";
            default -> type;
        };
        return String.format("%-16s %-12s LKR %,12.2f  LKR %,12.2f  %-30s",
                timestamp, label, amount, balanceAfter, description);
    }
}
