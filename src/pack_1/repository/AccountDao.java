package pack_1.repository;

import pack_1.model.Account;

import java.util.List;

/**
 * Data Access Object interface for Account entities.
 * Demonstrates: Abstraction and Separation of Concerns.
 */
public interface AccountDao {
    void save(Account account);
    void update(Account account);
    Account findByAccountNumber(String accountNumber);
    List<Account> findByUserId(String userId);
    List<Account> findAll();
    void toggleActiveStatus(String accountNumber, boolean active);
    int getNextAccountSequence();
}
