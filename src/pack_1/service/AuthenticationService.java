package pack_1.service;

import pack_1.exception.BankingException;
import pack_1.model.Customer;
import pack_1.model.User;

/**
 * Service interface for customer and administrator authentication, registration, and credentials.
 * Demonstrates: Abstraction and Service Layer Pattern.
 */
public interface AuthenticationService {
    Customer register(String username, String password, String email, String phone,
                      String fullName, String address, String nationalId) throws BankingException;

    User login(String username, String password, String ipAddress) throws BankingException;

    void changePassword(User user, String oldPassword, String newPassword) throws BankingException;

    void updateProfile(Customer customer, String email, String phone, String address) throws BankingException;
}
