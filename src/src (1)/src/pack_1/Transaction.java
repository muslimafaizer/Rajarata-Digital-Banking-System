package pack_1;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents one banking transaction.
 * OOP: Encapsulation, Composition (used inside Account)
 */
public class Transaction {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
    // Unique, ever-increasing ids (System.currentTimeMillis() alone produced duplicates)
    private static final AtomicLong SEQ = new AtomicLong(System.currentTimeMillis());

    private String transactionId;
    private String type;          // DEP, WDR, TRF_OUT, TRF_IN, BILL, LOAN_REP, INT, FEE
    private double amount;
    private double balanceAfter;
    private String description;
    private String accountNumber;
    private String timestamp;

    public Transaction(String type, double amount, double balanceAfter,
                       String description, String accountNumber) {
        this("TXN" + SEQ.incrementAndGet(), type, amount, balanceAfter, description,
             accountNumber, LocalDateTime.now().format(FMT));
    }

    /** Used when loading saved transactions from file. */
    public Transaction(String transactionId, String type, double amount, double balanceAfter,
                       String description, String accountNumber, String timestamp) {
        this.transactionId = transactionId;
        this.type          = type;
        this.amount        = amount;
        this.balanceAfter  = balanceAfter;
        this.description   = description == null ? "" : description.replace('|', '/');
        this.accountNumber = accountNumber;
        this.timestamp     = timestamp;
        SEQ.accumulateAndGet(getSequence(), Math::max);   // keep new ids above loaded ids
    }

    /** True when money came INTO the account. */
    public boolean isCredit() {
        return switch (type) {
            case "DEP", "TRF_IN", "INT" -> true;
            default -> false;
        };
    }

    public long getSequence() {
        try { return Long.parseLong(transactionId.substring(3)); }
        catch (RuntimeException e) { return 0; }
    }

    public String toTableRow() {
        String label = switch (type) {
            case "DEP"      -> "  Credit  ";
            case "WDR"      -> "  Debit   ";
            case "TRF_OUT"  -> "  Sent    ";
            case "TRF_IN"   -> "  Received";
            case "BILL"     -> "  Bill Pay";
            case "LOAN_REP" -> "  Loan Rep";
            case "INT"      -> "  Interest";
            case "FEE"      -> "  Fee     ";
            default         -> "  " + type;
        };
        return String.format("%-14s %-11s %,12.2f  %,12.2f  %-30s",
                timestamp, label, amount, balanceAfter, description);
    }

    public String toFileString() {
        return transactionId + "|" + type      + "|" + amount + "|"
             + balanceAfter  + "|" + description + "|" + accountNumber + "|" + timestamp;
    }

    // Getters
    public String getTransactionId() { return transactionId; }
    public String getType()          { return type; }
    public double getAmount()        { return amount; }
    public double getBalanceAfter()  { return balanceAfter; }
    public String getDescription()   { return description; }
    public String getAccountNumber() { return accountNumber; }
    public String getTimestamp()     { return timestamp; }
}
