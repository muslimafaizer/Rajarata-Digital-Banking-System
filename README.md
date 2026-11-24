# RAJARATA DIGITAL BANKING SYSTEM

A complete, production-grade university Java Object-Oriented Programming (OOP) Digital Banking Web Application with persistent SQLite relational database integration, RESTful JSON APIs, and a modern responsive user interface.

Developed for **Rajarata University of Sri Lanka**, Faculty of Applied Sciences / Faculty of Technology.

---

## 1. System Architecture & Project Structure

The system is organized into a modular layered enterprise architecture cleanly separating concerns:

```
rajarataDigitalBankingApp/
├── .vscode/
│   ├── launch.json                   # F5 Run Configurations for VS Code
│   └── settings.json                 # Java source and library configuration
├── bin/                              # Compiled bytecode (.class files)
├── data/
│   ├── rajarata_bank.db              # SQLite relational persistent database
│   ├── users.txt                     # Legacy flat data backup
│   ├── accounts.txt                  # Legacy flat data backup
│   └── transactions.txt              # Legacy flat data backup
├── frontend/
│   └── public/                       # Static web assets served by embedded Java server
│       ├── index.html                # Public Landing / Welcome page
│       ├── login.html                # Customer & Admin authentication page
│       ├── register.html             # New customer registration with strength meter
│       ├── dashboard.html            # Customer financial dashboard
│       ├── accounts.html             # Accounts directory & opening facility
│       ├── deposit.html              # Funds deposit page
│       ├── withdraw.html             # Funds withdrawal page
│       ├── transfer.html             # Instant atomic money transfer page
│       ├── transactions.html         # Statement ledger & transaction receipt modal
│       ├── profile.html              # Customer profile & security password manager
│       ├── admin.html                # Administrator operations & account freeze controls
│       ├── portal.css                # Master Emerald-Gold responsive stylesheet
│       ├── portal.js                 # API client, session manager, modals, toasts
│       ├── pages.js                  # Customer portal view logic
│       ├── admin.js                  # Administrator portal view logic
│       └── auth.js                   # Authentication & registration logic
├── lib/                              # External dependencies
│   ├── sqlite-jdbc-3.45.1.0.jar      # Self-contained SQLite JDBC driver
│   ├── slf4j-api-2.0.12.jar          # Logging API
│   └── slf4j-simple-2.0.12.jar       # Simple logger implementation
├── src/
│   └── pack_1/
│       ├── Main.java                 # Interactive CLI demonstration & server launcher
│       ├── WebServer.java            # Root entry point wrapper for WebServer
│       ├── BankSystem.java           # Unified system facade coordinating all services
│       ├── TestSystem.java           # 42-step automated verification test suite
│       ├── TestWebEndpoints.java     # HTTP REST API end-to-end integration test suite
│       ├── model/                    # Domain entities (OOP Encapsulation & Inheritance)
│       │   ├── User.java             # Abstract base user class
│       │   ├── Customer.java         # Extends User; has accounts (Composition)
│       │   ├── Admin.java            # Extends User; administrative privileges
│       │   ├── Account.java          # Abstract base account class
│       │   ├── SavingsAccount.java   # Extends Account; 3.5% p.a., daily limit
│       │   ├── CurrentAccount.java   # Extends Account; 1.0% p.a., LKR 50k overdraft
│       │   ├── FixedDepositAccount.java # Extends Account; 7.5% p.a., tenure lock & penalty
│       │   ├── Transaction.java      # Financial transaction entity
│       │   ├── AuditLog.java         # System security audit log entity
│       │   └── Beneficiary.java      # Saved recipient entity
│       ├── repository/               # Data Access Objects (DAO pattern & SQLite JDBC)
│       │   ├── DatabaseManager.java  # Connection pooling, tables, migration & foreign keys
│       │   ├── UserDao.java          # User DAO interface
│       │   ├── UserDaoImpl.java      # User DAO SQLite implementation
│       │   ├── AccountDao.java       # Account DAO interface
│       │   ├── AccountDaoImpl.java   # Account DAO SQLite implementation
│       │   ├── TransactionDao.java   # Transaction DAO interface
│       │   ├── TransactionDaoImpl.java # Transaction DAO SQLite implementation
│       │   ├── AuditLogDao.java      # Audit log DAO interface
│       │   └── AuditLogDaoImpl.java  # Audit log DAO SQLite implementation
│       ├── service/                  # Business Logic layer (Abstraction & Services)
│       │   ├── AuthenticationService.java     # Auth service interface
│       │   ├── AuthenticationServiceImpl.java # PBKDF2 hashing & lockout logic
│       │   ├── AccountService.java            # Account service interface
│       │   ├── AccountServiceImpl.java        # Account opening & status control
│       │   ├── TransactionService.java        # Transaction service interface
│       │   ├── TransactionServiceImpl.java    # Atomic transfers & withdrawal checks
│       │   ├── AdminService.java              # Admin reporting & KPI service interface
│       │   └── AdminServiceImpl.java          # Admin KPI implementation
│       ├── controller/
│       │   └── WebServer.java        # High-performance embedded HTTP server & REST API
│       ├── exception/                # Exception handling hierarchy
│       │   ├── BankingException.java          # Custom base checked exception
│       │   ├── AccountNotFoundException.java  # 404 Not Found exception
│       │   ├── AuthenticationException.java   # 401 Unauthorized / 429 Locked
│       │   ├── DuplicateRegistrationException.java # 409 Conflict
│       │   ├── InactiveAccountException.java  # 400 Inactive Account
│       │   ├── InsufficientFundsException.java# 400 Insufficient Funds
│       │   ├── InvalidAmountException.java    # 400 Invalid Numeric Amount
│       │   └── UnauthorizedAccessException.java # 403 Forbidden
│       └── util/
│           ├── PasswordUtil.java     # PBKDF2-HMAC-SHA256 password hashing
│           └── ValidationUtil.java   # RegEx input sanitization & BigDecimal checks
├── start-server.bat                  # One-click Windows Command Prompt launcher
├── start-server.ps1                  # PowerShell launcher
├── run-tests.bat                     # Test suite launcher
└── README.md                         # Project documentation
```

---

## 2. Object-Oriented Programming (OOP) Implementation

This project was built to clearly demonstrate all core OOP principles for academic evaluation:

### A. Encapsulation
- **Private State**: Attributes in `User`, `Customer`, `Account`, `Transaction`, and `AuditLog` are strictly `private`.
- **Controlled Access**: Balances in `Account` can **never** be set arbitrarily from outside. Balance changes occur solely through validated `deposit()` and `withdraw()` business methods.
- **Defensive Precision**: Monetary amounts are encapsulated and calculated using `java.math.BigDecimal` with `HALF_UP` rounding to prevent floating-point inaccuracies.

### B. Inheritance
- **User Hierarchy**:
  - `User` (abstract base class)
    - `Customer` (subclass extending User with full legal name, national ID, address, accounts list, and loan facilities)
    - `Admin` (subclass extending User with department and security access level)
- **Account Hierarchy**:
  - `Account` (abstract base class encapsulating account number, balance, date opened, and active status)
    - `SavingsAccount` (subclass implementing 3.5% p.a. interest and daily withdrawal limits)
    - `CurrentAccount` (subclass implementing 1.0% p.a. interest and LKR 50,000 overdraft protection)
    - `FixedDepositAccount` (subclass implementing 7.5% p.a. high yield, fixed tenure, and early-withdrawal penalty)

### C. Polymorphism (Dynamic Method Dispatch)
- **Overridden Method `withdraw()`**:
  - In `SavingsAccount`: enforces daily maximum withdrawal of LKR 100,000.
  - In `CurrentAccount`: allows account balance to dip into approved overdraft limit of up to LKR 50,000.
  - In `FixedDepositAccount`: calculates maturity dates and deducts an automatic 2% penalty fee if funds are withdrawn prematurely.
- **Overridden Method `getInterestRate()` and `calculateMonthlyInterest()`**: Each account type dynamically calculates and returns its respective annual and monthly yields.
- **Overridden Method `getDashboardInfo()`**: Subclasses of `User` display role-customized dashboard outputs.

### D. Abstraction
- **Abstract Classes**: `User` and `Account` cannot be instantiated directly and define core contracts.
- **Interface Segregation**:
  - `AuthenticationService`
  - `AccountService`
  - `TransactionService`
  - `AdminService`
  - `UserDao`, `AccountDao`, `TransactionDao`, `AuditLogDao`

### E. Composition & Aggregation
- **Customer HAS Accounts**: A `Customer` instance maintains a `List<Account>`.
- **Account HAS Transactions**: An `Account` maintains a `List<Transaction>`.
- **Services Compose DAOs**: Services rely on injected Data Access Objects to interact with persistence.

### F. Exception Handling Hierarchy
- Custom base checked exception: `BankingException`.
- Distinct specialized subclasses for proper HTTP status mapping:
  - `AccountNotFoundException` (404)
  - `AuthenticationException` (401 / 429)
  - `DuplicateRegistrationException` (409)
  - `UnauthorizedAccessException` (403)
  - `InsufficientFundsException` (400)
  - `InvalidAmountException` (400)
  - `InactiveAccountException` (400)

---

## 3. Relational Database Schema & Persistence

The application uses an embedded **SQLite relational database** located at `data/rajarata_bank.db`. Data persists automatically across server restarts.

### Relational Schema:
1. **`users` Table**:
   - `user_id` (VARCHAR PK)
   - `username` (VARCHAR UNIQUE NOT NULL)
   - `password_hash` (VARCHAR NOT NULL)
   - `email` (VARCHAR UNIQUE NOT NULL)
   - `phone` (VARCHAR NOT NULL)
   - `role` (VARCHAR NOT NULL CHECK role IN ('CUSTOMER', 'ADMIN'))
   - `full_name`, `address`, `national_id` (UNIQUE), `department`, `access_level`, `loan_balance`, `loan_active`, `created_at`
2. **`accounts` Table**:
   - `account_number` (VARCHAR PK)
   - `user_id` (VARCHAR FK -> `users.user_id`)
   - `account_type` (VARCHAR CHECK IN ('SAVINGS', 'CURRENT', 'FIXED_DEPOSIT'))
   - `balance` (NUMERIC(15,2) NOT NULL)
   - `interest_rate`, `date_opened`, `is_active`, `term_months`, `maturity_date`, `created_at`
3. **`transactions` Table**:
   - `transaction_id` (VARCHAR PK)
   - `account_number` (VARCHAR FK -> `accounts.account_number`)
   - `type` (VARCHAR CHECK IN ('DEP', 'WDR', 'TRF_OUT', 'TRF_IN', 'BILL', 'LOAN_REP', 'INT', 'FEE'))
   - `amount` (NUMERIC(15,2) NOT NULL CHECK(amount > 0))
   - `balance_after`, `description`, `timestamp`, `created_at`
4. **`audit_logs` Table**:
   - `log_id` (INTEGER PK AUTOINCREMENT)
   - `user_id`, `action`, `details`, `ip_address`, `timestamp`, `created_at`

### ACID Atomic Money Transfers:
Money transfers between accounts execute in an atomic transaction:
```java
conn.setAutoCommit(false);
try {
    // 1. Debit sender account
    // 2. Credit recipient account
    // 3. Insert TRF_OUT transaction record
    // 4. Insert TRF_IN transaction record
    conn.commit();
} catch (SQLException e) {
    conn.rollback();
    throw new BankingException("Transfer failed and was rolled back.", e);
}
```

---

## 4. REST API Documentation

| Method | Endpoint | Access | Description |
|---|---|---|---|
| `GET` | `/api/health` | Public | System status check |
| `POST` | `/api/register` | Public | Register new customer account |
| `POST` | `/api/login` | Public | Authenticate user & return session token |
| `POST` | `/api/logout` | Authenticated | Invalidate authenticated session |
| `GET` | `/api/me` | Authenticated | Retrieve current user profile |
| `PUT` | `/api/password` | Authenticated | Change current password |
| `GET` | `/api/dashboard` | Customer | Retrieve balances, summary, and recent activity |
| `GET` | `/api/profile` | Customer | View customer profile |
| `PUT` | `/api/profile` | Customer | Update email, phone, and address |
| `GET` | `/api/accounts` | Customer | List all accounts owned by customer |
| `POST` | `/api/open-account` | Customer | Open Savings, Current, or Fixed Deposit |
| `POST` | `/api/deposit` | Customer | Deposit funds into specified account |
| `POST` | `/api/withdraw` | Customer | Withdraw funds from specified account |
| `POST` | `/api/transfer` | Customer | Instant atomic transfer to another account |
| `GET` | `/api/transactions` | Customer | Filter and search transaction history |
| `POST` | `/api/pay-bill` | Customer | Pay utility bill (Electricity, Water, etc.) |
| `POST` | `/api/apply-loan` | Customer | Apply for personal loan facility |
| `POST` | `/api/repay-loan` | Customer | Repay outstanding loan |
| `GET` | `/api/admin/stats` | Admin | System statistics & financial KPI metrics |
| `GET` | `/api/admin/customers`| Admin | View all registered customer accounts |
| `GET` | `/api/admin/accounts` | Admin | View all bank accounts across system |
| `POST` | `/api/admin/accounts` | Admin | Activate or Deactivate (Freeze) an account |
| `GET` | `/api/admin/audit-logs`| Admin | Security & operations audit trail |

---

## 5. Visual Design System

The frontend follows an institutional, sophisticated visual identity:
- **Primary Color**: Deep Emerald Green (`#064e3b`, `#047857`, `#022c22`)
- **Accent Color**: Subtle Royal Gold (`#c59b27`, `#d4af37`, `#e6ca65`)
- **Neutral Backgrounds**: Warm Off-White (`#fcfbf8`, `#f6f5f0`)
- **Typography & Cards**: Charcoal (`#0f172a`, `#1e293b`) with clean Segoe UI / System sans-serif typography, subtle card elevations, and responsive sidebar navigation.

---

## 6. How to Compile and Run

### Requirements
- **Java Development Kit (JDK)**: JDK 17, 21, or 25 installed.
- **Operating System**: Windows (PowerShell or Command Prompt).

### Option A: Using Helper Launchers (Easiest)
1. Double-click **`start-server.bat`** (or execute `./start-server.ps1` in PowerShell).
2. The server will compile all classes and launch at **`http://localhost:8080`**.
3. Open your browser and navigate to:
   ```
   http://localhost:8080
   ```

### Option B: Manual Command Line in PowerShell
```powershell
# 1. Compile all Java sources
$files = Get-ChildItem -Path src/pack_1 -Filter *.java -Recurse | Select-Object -ExpandProperty FullName
javac -encoding UTF-8 -d bin -cp "lib/*" $files

# 2. Run the Web Server
java "-Dfile.encoding=UTF-8" -cp "bin;lib/*" pack_1.WebServer

# 3. Or run the Interactive CLI
java "-Dfile.encoding=UTF-8" -cp "bin;lib/*" pack_1.Main
```

### Option C: In VS Code
1. Open this workspace in Visual Studio Code.
2. Press `Ctrl + Shift + D` to open the Run & Debug pane.
3. Select **"Start Banking Web Server (port 8080)"** from the dropdown and press **F5** (or click the green Play button).

---

## 7. Demo Credentials

| Role | Username | Password | Purpose |
|---|---|---|---|
| **Customer** | `testuser` | `Secret123` | Demo customer with multiple accounts and transactions |
| **Customer** | `debuguser23` | `Test12345` | Secondary customer for transfer recipient testing |
| **Admin** | `admin` | `admin123` | Administrator for managing accounts & viewing audit logs |

---

## 8. Automated Test Suite

Run the 42-step automated verification test suite:
```powershell
# In PowerShell:
java "-Dfile.encoding=UTF-8" -cp "bin;lib/*" pack_1.TestSystem

# Or run the HTTP API test suite:
java "-Dfile.encoding=UTF-8" -cp "bin;lib/*" pack_1.TestWebEndpoints
```
Both test suites pass 100% with zero errors!
