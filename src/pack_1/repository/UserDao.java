package pack_1.repository;

import pack_1.model.Admin;
import pack_1.model.Customer;
import pack_1.model.User;

import java.util.List;

/**
 * Data Access Object interface for User entities.
 * Demonstrates: Abstraction and Repository Pattern.
 */
public interface UserDao {
    void save(User user);
    void update(User user);
    User findById(String userId);
    User findByUsername(String username);
    User findByEmail(String email);
    User findByNationalId(String nationalId);
    List<Customer> findAllCustomers();
    List<Admin> findAllAdmins();
    int getNextUserSequence();
}
