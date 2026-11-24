package pack_1.service;

import pack_1.exception.BankingException;
import pack_1.model.Account;
import pack_1.model.AuditLog;
import pack_1.model.Customer;
import pack_1.model.Transaction;

import java.util.List;
import java.util.Map;

/**
 * Service interface for administrative reporting and control.
 */
public interface AdminService {
    List<Customer> getAllCustomers();
    List<Account> getAllAccounts();
    List<Transaction> getAllTransactions(int limit);
    List<AuditLog> getAuditLogs(int limit);
    Map<String, Object> getSystemStatistics();
    void setAccountActiveStatus(String accountNumber, boolean active) throws BankingException;
}
