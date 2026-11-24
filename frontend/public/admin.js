/**
 * Rajarata Digital Banking System — Administrator Dashboard Controller
 * Connects administrative KPI analytics, customer directory, account freeze/unfreeze, and audit logs.
 */
(() => {
  const { api, esc, money, accLabel, toast, init } = Portal;

  async function renderAdminDashboard() {
    const ctx = await init('admin', 'System Administration & Control', u =>
      `Logged in as System Administrator: ${esc(u.username)} (${esc(u.email)})`
    );
    if (!ctx) return;

    if (ctx.user.role !== 'ADMIN') {
      toast('Access Denied. Administrator credentials required.', false);
      location.href = 'dashboard.html';
      return;
    }

    ctx.el.innerHTML = `
      <!-- KPI Stats Grid -->
      <div class="grid grid-4" id="statsGrid">
        <div class="card stat-card"><div class="label">Total Customers</div><div class="value" id="statCustomers">…</div></div>
        <div class="card stat-card"><div class="label">Total Bank Accounts</div><div class="value" id="statAccounts">…</div></div>
        <div class="card stat-card"><div class="label">Total Bank Deposits</div><div class="value credit" id="statDeposits">…</div></div>
        <div class="card stat-card"><div class="label">Total Transactions</div><div class="value" id="statTxns">…</div></div>
      </div>

      <!-- Section: Registered Customers Directory -->
      <div class="card" style="margin-top: 28px;">
        <div class="card-head">
          <h2>Registered Customers Directory</h2>
          <span class="muted" id="customerCountHint">Loading…</span>
        </div>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Customer ID</th>
                <th>Full Name</th>
                <th>Username</th>
                <th>Contact</th>
                <th>NIC</th>
                <th class="num">Accounts</th>
                <th class="num">Total Balance</th>
                <th>Loan Status</th>
              </tr>
            </thead>
            <tbody id="customersTableBody">
              <tr><td colspan="8" style="text-align:center; padding:20px;">Loading customer records…</td></tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- Section: Bank Accounts Management & Status Control -->
      <div class="card" style="margin-top: 28px;">
        <div class="card-head">
          <h2>Accounts Directory &amp; Status Controls</h2>
          <span class="muted" id="accountCountHint">Loading…</span>
        </div>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Account Number</th>
                <th>Customer ID</th>
                <th>Account Type</th>
                <th class="num">Balance (LKR)</th>
                <th>Opened Date</th>
                <th>Status</th>
                <th>Administrative Action</th>
              </tr>
            </thead>
            <tbody id="accountsTableBody">
              <tr><td colspan="7" style="text-align:center; padding:20px;">Loading account records…</td></tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- Section: Master Audit & Security Log -->
      <div class="card" style="margin-top: 28px;">
        <div class="card-head">
          <h2>Security &amp; Operations Audit Trail</h2>
          <span class="muted">Recent security events</span>
        </div>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Timestamp</th>
                <th>User ID</th>
                <th>Action</th>
                <th>Details</th>
                <th>IP Address</th>
              </tr>
            </thead>
            <tbody id="auditTableBody">
              <tr><td colspan="5" style="text-align:center; padding:20px;">Loading security audit log…</td></tr>
            </tbody>
          </table>
        </div>
      </div>
    `;

    async function loadStats() {
      try {
        const stats = await api('GET', 'admin/stats');
        document.getElementById('statCustomers').textContent = stats.totalCustomers;
        document.getElementById('statAccounts').textContent = `${stats.activeAccounts} / ${stats.totalAccounts}`;
        document.getElementById('statDeposits').textContent = money(stats.totalDeposits);
        document.getElementById('statTxns').textContent = stats.totalTransactions;
      } catch (e) {
        toast('Failed loading system stats: ' + e.message, false);
      }
    }

    async function loadCustomers() {
      try {
        const res = await api('GET', 'admin/customers');
        document.getElementById('customerCountHint').textContent = `${res.count} verified customers`;
        const tbody = document.getElementById('customersTableBody');
        if (!res.customers.length) {
          tbody.innerHTML = '<tr><td colspan="8" style="text-align:center; padding:20px;">No registered customers.</td></tr>';
          return;
        }
        tbody.innerHTML = res.customers.map(c => `
          <tr>
            <td class="mono"><b>${esc(c.userId)}</b></td>
            <td><b>${esc(c.fullName)}</b></td>
            <td class="mono">${esc(c.username)}</td>
            <td>
              <div>${esc(c.email)}</div>
              <div class="muted" style="font-size:0.8rem;">${esc(c.phone)}</div>
            </td>
            <td class="mono">${esc(c.nationalId)}</td>
            <td class="num">${c.accountCount}</td>
            <td class="num" style="font-weight:700; color:var(--emerald-800);">${money(c.totalBalance)}</td>
            <td>
              ${c.loanActive
                ? `<span class="badge badge-gold">Active: ${money(c.loanBalance)}</span>`
                : '<span class="badge badge-ok">No Loan</span>'}
            </td>
          </tr>
        `).join('');
      } catch (e) {
        toast('Failed loading customers: ' + e.message, false);
      }
    }

    async function loadAccounts() {
      try {
        const res = await api('GET', 'admin/accounts');
        document.getElementById('accountCountHint').textContent = `${res.count} total accounts`;
        const tbody = document.getElementById('accountsTableBody');
        if (!res.accounts.length) {
          tbody.innerHTML = '<tr><td colspan="7" style="text-align:center; padding:20px;">No accounts found.</td></tr>';
          return;
        }
        tbody.innerHTML = res.accounts.map(a => `
          <tr>
            <td class="mono"><b>${esc(a.accountNumber)}</b></td>
            <td class="mono">${esc(a.ownerId)}</td>
            <td>${esc(accLabel(a.accountType))}</td>
            <td class="num" style="font-weight:700;">${money(a.balance)}</td>
            <td>${esc(a.dateOpened)}</td>
            <td>
              <span class="badge ${a.active ? 'badge-ok' : 'badge-off'}">
                ${a.active ? 'Active' : 'Inactive'}
              </span>
            </td>
            <td>
              <button class="btn btn-sm ${a.active ? 'btn-danger' : 'btn-primary'} toggle-status-btn"
                      data-acc="${esc(a.accountNumber)}" data-active="${a.active ? 'false' : 'true'}">
                ${a.active ? 'Deactivate' : 'Activate'}
              </button>
            </td>
          </tr>
        `).join('');

        // Bind activation toggles
        tbody.querySelectorAll('.toggle-status-btn').forEach(btn => {
          btn.onclick = async () => {
            const accNum = btn.dataset.acc;
            const targetState = btn.dataset.active === 'true';
            try {
              btn.disabled = true;
              await api('POST', 'admin/accounts', { accountNumber: accNum, active: targetState });
              toast(`Account ${accNum} is now ${targetState ? 'Activated' : 'Deactivated'}.`);
              await loadAccounts();
              await loadStats();
            } catch (err) {
              toast(err.message, false);
              btn.disabled = false;
            }
          };
        });
      } catch (e) {
        toast('Failed loading accounts: ' + e.message, false);
      }
    }

    async function loadAuditLogs() {
      try {
        const res = await api('GET', 'admin/audit-logs');
        const tbody = document.getElementById('auditTableBody');
        if (!res.logs.length) {
          tbody.innerHTML = '<tr><td colspan="5" style="text-align:center; padding:20px;">No audit events recorded.</td></tr>';
          return;
        }
        tbody.innerHTML = res.logs.map(l => `
          <tr>
            <td style="font-size:0.85rem; color:var(--charcoal-600);">${esc(l.timestamp)}</td>
            <td class="mono"><b>${esc(l.userId)}</b></td>
            <td><span class="badge badge-gold" style="font-size:0.75rem;">${esc(l.action)}</span></td>
            <td>${esc(l.details)}</td>
            <td class="mono muted" style="font-size:0.82rem;">${esc(l.ipAddress)}</td>
          </tr>
        `).join('');
      } catch (e) {
        toast('Failed loading audit logs: ' + e.message, false);
      }
    }

    await loadStats();
    await loadCustomers();
    await loadAccounts();
    await loadAuditLogs();
  }

  if (document.body.dataset.page === 'admin') {
    renderAdminDashboard();
  }
})();
