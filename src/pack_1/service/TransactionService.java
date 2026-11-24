package pack_1.service;

import pack_1.exception.BankingException;
import pack_1.model.Customer;
import pack_1.model.Transaction;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Service interface for transactional banking operations: deposits, withdrawals, transfers, bills, and loans.
 */
public interface TransactionService {
    void deposit(Customer customer, String accountNumber, BigDecimal amount) throws BankingException;
    void withdraw(Customer customer, String accountNumber, BigDecimal amount) throws BankingException;
    void transfer(Customer sender, String fromAcc, String toAcc, BigDecimal amount) throws BankingException;
    void payBill(Customer customer, String accountNumber, String billType) throws BankingException;
    void applyLoan(Customer customer, String accountNumber, BigDecimal amount) throws BankingException;
    void repayLoan(Customer customer, String accountNumber, BigDecimal amount) throws BankingException;
    List<Transaction> getTransactions(Customer customer, String accountNumber, String type, String search, int limit);
    Transaction getTransactionById(String transactionId) throws BankingException;
    Map<String, BigDecimal> getStandardBills();
}
