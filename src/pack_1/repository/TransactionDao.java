package pack_1.repository;

import pack_1.model.Transaction;

import java.sql.Connection;
import java.util.List;

/**
 * Data Access Object interface for Transaction entities.
 */
public interface TransactionDao {
    void save(Transaction transaction);
    void saveWithConnection(Connection conn, Transaction transaction);
    Transaction findById(String transactionId);
    List<Transaction> findByAccountNumber(String accountNumber);
    List<Transaction> findAll(int limit);
    List<Transaction> findFiltered(String accountNumber, String type, String search, int limit);
}
