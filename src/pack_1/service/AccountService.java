package pack_1.service;

import pack_1.exception.BankingException;
import pack_1.model.Account;
import pack_1.model.Customer;

import java.math.BigDecimal;
import java.util.List;

/**
 * Service interface for bank account lifecycle management.
 */
public interface AccountService {
    Account openAccount(Customer customer, String accountType, BigDecimal initialDeposit, int fdMonths) throws BankingException;
    Account getAccount(String accountNumber) throws BankingException;
    List<Account> getCustomerAccounts(String userId);
    void toggleAccountStatus(String accountNumber, boolean active) throws BankingException;
}
