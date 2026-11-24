/**
 * Rajarata Digital Banking System — Customer Portal Page Handlers
 * Every figure and account status is dynamically rendered from the Java backend.
 */
(() => {
  const { api, esc, money, accLabel, toast, confirmDialog, txnTable, bindTxnRows, init, submit } = Portal;
  const page = document.body.dataset.page;

  const buildAccountOptions = (accounts) => {
    return accounts.filter(a => a.active).map(a =>
      `<option value="${esc(a.accountNumber)}">${esc(accLabel(a.accountType))} — ${esc(a.accountNumber)} (Bal: ${money(a.balance)})</option>`
    ).join('');
  };

  // ═════════════════════ 1. CUSTOMER DASHBOARD ═════════════════════
  async function renderDashboard() {
    const ctx = await init('dashboard', 'Financial Dashboard', u =>
      `Welcome back, ${esc((u.fullName || u.username).split(' ')[0])}. Here is your financial overview.`
    );
    if (!ctx) return;

    try {
      const d = await api('GET', 'dashboard');
      const p = d.primaryAccount;

      ctx.el.innerHTML = `
        <div class="grid grid-3">
          <div class="card balance-card">
            <div class="label">Total Available Net Worth</div>
            <div class="big">${money(d.totalBalance)}</div>
            <div class="meta">
              ${p ? `<span>Primary: <b class="mono">${esc(p.accountNumber)}</b></span> <span>${esc(accLabel(p.accountType))}</span>` : '<span>Active Accounts: ' + d.accounts.length + '</span>'}
            </div>
          </div>
          <div class="card stat-card">
            <div class="label">Total Credits (Money In)</div>
            <div class="value credit">+ ${money(d.summary.totalCredits)}</div>
            <div class="muted" style="margin-top: 8px;">Across all your active accounts</div>
          </div>
          <div class="card stat-card">
            <div class="label">Total Debits (Money Out)</div>
            <div class="value debit">− ${money(d.summary.totalDebits)}</div>
            <div class="muted" style="margin-top: 8px;">${d.summary.transactionCount} transactions recorded</div>
          </div>
        </div>

        <div class="grid grid-main" style="margin-top: 24px;">
          <!-- Left Column: Recent Transactions -->
          <div class="card">
            <div class="card-head">
              <h2>Recent Activity</h2>
              <a href="transactions.html" style="font-weight: 600; font-size: 0.9rem;">View All Transactions →</a>
            </div>
            ${txnTable(d.recentTransactions)}
          </div>

          <!-- Right Column: Quick Actions & Account Cards -->
          <div>
            <div class="card">
              <h2>Quick Banking Actions</h2>
              <div class="quick-grid" style="margin-top: 14px;">
                <a class="quick-btn" href="deposit.html"><span class="ico">⬇️</span>Deposit</a>
                <a class="quick-btn" href="withdraw.html"><span class="ico">⬆️</span>Withdraw</a>
                <a class="quick-btn" href="transfer.html"><span class="ico">🔁</span>Transfer</a>
                <button class="quick-btn" id="payBillQuickBtn" style="cursor:pointer;"><span class="ico">💡</span>Pay Bill</button>
              </div>
            </div>

            <div class="card">
              <div class="card-head">
                <h2>Your Accounts</h2>
                <a href="accounts.html" style="font-size:0.85rem; font-weight:600;">+ Open New</a>
              </div>
              <div style="display: flex; flex-direction: column; gap: 12px;">
                ${d.accounts.map(a => `
                  <div style="display:flex; justify-content:space-between; align-items:center; padding: 12px; background:var(--soft-neutral); border-radius:var(--radius-md);">
                    <div>
                      <div style="font-weight:700; font-size:0.92rem; color:var(--charcoal-900);">${esc(accLabel(a.accountType))}</div>
                      <div class="mono muted" style="font-size:0.82rem;">${esc(a.accountNumber)}</div>
                    </div>
                    <div style="text-align:right;">
                      <div style="font-weight:700; color:var(--emerald-800);">${money(a.balance)}</div>
                      <span class="badge ${a.active ? 'badge-ok' : 'badge-off'}" style="font-size:0.7rem;">${a.active ? 'Active' : 'Inactive'}</span>
                    </div>
                  </div>
                `).join('')}
              </div>

              ${d.loanActive ? `
                <div class="alert alert-info" style="margin-top: 16px;">
                  <b>Active Loan Facility:</b> Outstanding balance is <b>${money(d.loanBalance)}</b>.
                </div>
              ` : ''}
            </div>
          </div>
        </div>
      `;

      bindTxnRows(ctx.el);

      // Bind Pay Bill quick modal
      const billBtn = document.getElementById('payBillQuickBtn');
      if (billBtn) {
        billBtn.onclick = () => openPayBillModal(d.accounts);
      }

    } catch (e) {
      ctx.el.innerHTML = `<div class="alert alert-error">${esc(e.message)}</div>`;
    }
  }

  // ═════════════════════ 2. MY ACCOUNTS ═════════════════════
  async function renderAccounts() {
    const ctx = await init('accounts', 'My Bank Accounts', 'Manage your savings, current, and term deposit accounts.');
    if (!ctx) return;

    async function loadAccountsList() {
      const { accounts } = await api('GET', 'accounts');
      const listEl = document.getElementById('accList');
      if (!accounts.length) {
        listEl.innerHTML = '<div class="alert alert-info">No accounts found. Open a new account below.</div>';
        return;
      }
      listEl.innerHTML = accounts.map(a => `
        <div class="card account-card">
          <div class="card-head">
            <h3>${esc(accLabel(a.accountType))}</h3>
            <span class="badge ${a.active ? 'badge-ok' : 'badge-off'}">${a.active ? 'Active' : 'Inactive'}</span>
          </div>
          <div style="margin: 10px 0 16px;">
            <div class="muted" style="font-size:0.8rem;">Available Balance</div>
            <div style="font-size:1.85rem; font-weight:800; color:var(--emerald-900);">${money(a.balance)}</div>
          </div>
          <dl class="kv">
            <dt>Account Number</dt><dd class="mono">${esc(a.accountNumber)}</dd>
            <dt>Interest Yield</dt><dd><b>${a.interestRate}%</b> per annum</dd>
            <dt>Date Opened</dt><dd>${esc(a.dateOpened)}</dd>
            <dt>Features</dt><dd style="font-size:0.86rem;">${esc(a.features)}</dd>
          </dl>
          <div class="card-head" style="margin-top: 16px; margin-bottom: 0;">
            <a class="btn btn-ghost btn-sm" href="transactions.html?account=${esc(a.accountNumber)}">View Transactions</a>
            <a class="btn btn-primary btn-sm" href="deposit.html?account=${esc(a.accountNumber)}">Deposit</a>
          </div>
        </div>
      `).join('');
    }

    ctx.el.innerHTML = `
      <div class="grid grid-2" id="accList"><div class="card">Loading your accounts…</div></div>

      <div class="card" style="margin-top: 28px;">
        <h2>Open an Additional Bank Account</h2>
        <p class="muted" style="margin-bottom: 20px;">Choose an account type suitable for your financial goals.</p>

        <form id="openAccountForm" novalidate>
          <div class="form-row">
            <div class="field">
              <label for="atype">Account Type</label>
              <select id="atype">
                <option value="SAVINGS">Savings Account (3.5% p.a. • Daily Limit LKR 100,000)</option>
                <option value="CURRENT">Current Account (1.0% p.a. • Overdraft up to LKR 50,000)</option>
                <option value="FIXED">Fixed Deposit (7.5% p.a. • High Yield Term Deposit)</option>
              </select>
            </div>
            <div class="field">
              <label for="aamt">Initial Opening Deposit (LKR)</label>
              <input id="aamt" type="number" min="0" step="0.01" placeholder="0.00">
              <span class="hint" id="ahint">Optional for Savings and Current accounts.</span>
            </div>
          </div>

          <div class="field hidden" id="termField">
            <label for="aterm">Fixed Deposit Tenure</label>
            <select id="aterm">
              <option value="3">3 Months (Short Term)</option>
              <option value="6">6 Months</option>
              <option value="12" selected>12 Months (1 Year Standard)</option>
              <option value="24">24 Months (2 Years)</option>
              <option value="36">36 Months (3 Years Long Term)</option>
            </select>
          </div>

          <div class="form-msg hidden"></div>
          <button class="btn btn-primary" type="submit">Open Account Now</button>
        </form>
      </div>
    `;

    const typeSelect = document.getElementById('atype');
    typeSelect.onchange = () => {
      const isFd = typeSelect.value === 'FIXED';
      document.getElementById('termField').classList.toggle('hidden', !isFd);
      document.getElementById('ahint').textContent = isFd
        ? 'Minimum initial deposit of LKR 10,000.00 is required for Fixed Deposits.'
        : 'Optional for Savings and Current accounts.';
      if (isFd && Number(document.getElementById('aamt').value || 0) < 10000) {
        document.getElementById('aamt').value = '10000.00';
      }
    };

    document.getElementById('openAccountForm').addEventListener('submit', (e) => {
      e.preventDefault();
      submit(e.target, async () => {
        const amt = document.getElementById('aamt').value;
        const res = await api('POST', 'open-account', {
          accountType: typeSelect.value,
          amount: amt === '' ? 0 : Number(amt),
          fdMonths: Number(document.getElementById('aterm').value)
        });
        toast('New account opened successfully: ' + res.account.accountNumber);
        e.target.reset();
        typeSelect.onchange();
        await loadAccountsList();
      });
    });

    try { await loadAccountsList(); } catch (e) { toast(e.message, false); }
  }

  // ═════════════════════ 3. DEPOSIT / WITHDRAW / TRANSFER ═════════════════════
  async function renderMoneyOperation(opType, title, subtitle, endpoint, actionLabel, extraFieldsHtml) {
    const ctx = await init(opType, title, subtitle);
    if (!ctx) return;

    let accountList = [];

    ctx.el.innerHTML = `
      <div class="card narrow">
        <form id="moneyOperationForm" novalidate>
          <div class="field">
            <label for="accSelect">${opType === 'transfer' ? 'Source Account (From)' : 'Target Account'}</label>
            <select id="accSelect"></select>
            <span class="hint" id="balHint" style="font-weight: 600; color: var(--emerald-800);"></span>
          </div>

          ${extraFieldsHtml || ''}

          <div class="field">
            <label for="amtInput">Amount (LKR)</label>
            <input id="amtInput" type="number" min="0.01" step="0.01" inputmode="decimal" placeholder="0.00" required>
            <span class="hint" id="amtWarning"></span>
          </div>

          ${opType === 'deposit' ? `
            <div style="display:flex; gap:8px; margin-bottom:18px; flex-wrap:wrap;">
              <button type="button" class="btn btn-ghost btn-sm quick-amt" data-val="1000">+ 1,000</button>
              <button type="button" class="btn btn-ghost btn-sm quick-amt" data-val="5000">+ 5,000</button>
              <button type="button" class="btn btn-ghost btn-sm quick-amt" data-val="10000">+ 10,000</button>
              <button type="button" class="btn btn-ghost btn-sm quick-amt" data-val="50000">+ 50,000</button>
            </div>
          ` : ''}

          <div class="form-msg hidden"></div>
          <button class="btn btn-primary btn-block" type="submit">${actionLabel}</button>
        </form>
      </div>
    `;

    const sel = document.getElementById('accSelect');
    const amtInput = document.getElementById('amtInput');
    const balHint = document.getElementById('balHint');

    const updateBalanceHint = () => {
      const selectedAccount = accountList.find(x => x.accountNumber === sel.value);
      if (selectedAccount) {
        balHint.textContent = 'Current Available Balance: ' + money(selectedAccount.balance) +
          (selectedAccount.accountType === 'SAVINGS' ? ' (Daily withdraw limit: LKR 100,000)' : '') +
          (selectedAccount.accountType === 'CURRENT' ? ' (Overdraft facility enabled: LKR 50,000)' : '');
      } else {
        balHint.textContent = '';
      }
    };

    async function refreshAccounts() {
      const res = await api('GET', 'accounts');
      accountList = res.accounts;
      const keepVal = sel.value;
      sel.innerHTML = buildAccountOptions(accountList);

      // Pre-select account from URL query if provided
      const urlAcc = new URLSearchParams(location.search).get('account');
      if (urlAcc && accountList.some(a => a.accountNumber === urlAcc)) {
        sel.value = urlAcc;
      } else if (keepVal) {
        sel.value = keepVal;
      }
      updateBalanceHint();
    }

    sel.onchange = updateBalanceHint;

    // Quick amount pills
    ctx.el.querySelectorAll('.quick-amt').forEach(btn => {
      btn.onclick = () => {
        const cur = Number(amtInput.value || 0);
        amtInput.value = (cur + Number(btn.dataset.val)).toFixed(2);
      };
    });

    try { await refreshAccounts(); } catch (e) {
      ctx.el.innerHTML = `<div class="alert alert-error">${esc(e.message)}</div>`;
      return;
    }

    document.getElementById('moneyOperationForm').addEventListener('submit', (ev) => {
      ev.preventDefault();
      const amountVal = Number(amtInput.value);
      if (isNaN(amountVal) || amountVal <= 0) {
        toast('Please enter a valid amount greater than zero.', false);
        return;
      }

      const body = {
        accountNumber: sel.value,
        amount: amountVal
      };

      const toInput = document.getElementById('toAccountInput');
      if (toInput) {
        body.toAccount = toInput.value.trim().toUpperCase();
        if (!body.toAccount) {
          toast('Please enter the recipient account number.', false);
          return;
        }
        if (body.accountNumber === body.toAccount) {
          toast('Sender and destination accounts cannot be identical.', false);
          return;
        }
      }

      const executeOp = () => {
        submit(ev.target, async () => {
          const res = await api('POST', endpoint, body);
          toast(res.message);
          amtInput.value = '';
          if (toInput) toInput.value = '';
          await refreshAccounts();
        });
      };

      if (opType === 'transfer') {
        confirmDialog(
          'Confirm Money Transfer',
          `Are you sure you want to transfer ${money(amountVal)} from ${body.accountNumber} to account ${body.toAccount}?`,
          executeOp
        );
      } else {
        executeOp();
      }
    });
  }

  // ═════════════════════ 4. TRANSACTIONS HISTORY ═════════════════════
  async function renderTransactions() {
    const ctx = await init('transactions', 'Transaction History & Statements', 'Search, filter, and inspect detailed receipts of your account activity.');
    if (!ctx) return;

    const { accounts } = await api('GET', 'accounts').catch(e => {
      ctx.el.innerHTML = `<div class="alert alert-error">${esc(e.message)}</div>`;
      return {};
    });
    if (!accounts) return;

    ctx.el.innerHTML = `
      <div class="card">
        <div class="filters-row">
          <div class="field" style="margin-bottom:0;">
            <label for="filterAccount">Account</label>
            <select id="filterAccount">
              <option value="">All Accounts</option>
              ${accounts.map(a => `<option value="${esc(a.accountNumber)}">${esc(accLabel(a.accountType))} (${esc(a.accountNumber)})</option>`).join('')}
            </select>
          </div>
          <div class="field" style="margin-bottom:0;">
            <label for="filterType">Transaction Type</label>
            <select id="filterType">
              <option value="">All Types</option>
              <option value="DEP">Deposits</option>
              <option value="WDR">Withdrawals</option>
              <option value="TRF_OUT">Transfers Sent</option>
              <option value="TRF_IN">Transfers Received</option>
              <option value="BILL">Bill Payments</option>
              <option value="LOAN_REP">Loan Repayments</option>
              <option value="INT">Interest Earned</option>
              <option value="FEE">Bank Fees</option>
            </select>
          </div>
          <div class="field" style="margin-bottom:0;">
            <label for="filterSearch">Search Statement</label>
            <input id="filterSearch" type="search" placeholder="Search by description or TXN reference…">
          </div>
        </div>

        <div class="card-head" style="margin-top: 18px;">
          <span class="muted" id="txnCounter">Loading ledger…</span>
          <button class="btn btn-ghost btn-sm" onclick="window.print()">🖨️ Print Statement</button>
        </div>

        <div id="txnContainer">
          <div style="padding:20px; text-align:center;">Loading transactions…</div>
        </div>
      </div>
    `;

    // Pre-populate account filter from URL hash or query if present
    const qAccount = new URLSearchParams(location.search).get('account') || decodeURIComponent(location.hash.slice(1));
    if (qAccount && accounts.some(a => a.accountNumber === qAccount)) {
      document.getElementById('filterAccount').value = qAccount;
    }

    let loadedTxns = [];

    const drawFiltered = () => {
      const q = document.getElementById('filterSearch').value.trim().toLowerCase();
      const filtered = loadedTxns.filter(t =>
        !q || t.description.toLowerCase().includes(q) || t.transactionId.toLowerCase().includes(q)
      );
      document.getElementById('txnCounter').textContent = `Showing ${filtered.length} transaction(s)`;
      document.getElementById('txnContainer').innerHTML = txnTable(filtered);
      bindTxnRows(document.getElementById('txnContainer'));
    };

    const fetchTransactions = async () => {
      try {
        const queryParams = new URLSearchParams({
          account: document.getElementById('filterAccount').value,
          type: document.getElementById('filterType').value
        });
        const res = await api('GET', 'transactions?' + queryParams.toString());
        loadedTxns = res.transactions;
        drawFiltered();
      } catch (e) {
        document.getElementById('txnContainer').innerHTML = `<div class="alert alert-error">${esc(e.message)}</div>`;
      }
    };

    document.getElementById('filterAccount').onchange = fetchTransactions;
    document.getElementById('filterType').onchange = fetchTransactions;
    document.getElementById('filterSearch').oninput = drawFiltered;

    fetchTransactions();
  }

  // ═════════════════════ 5. PROFILE & SECURITY ═════════════════════
  async function renderProfile() {
    const ctx = await init('profile', 'Personal Profile & Security', 'Manage your verified contact details and security password.');
    if (!ctx) return;

    let profileData;
    try {
      const res = await api('GET', 'profile');
      profileData = res.profile;
    } catch (e) {
      ctx.el.innerHTML = `<div class="alert alert-error">${esc(e.message)}</div>`;
      return;
    }

    const renderProfileSummary = (p) => `
      <dl class="kv">
        <dt>Full Legal Name</dt><dd><b>${esc(p.fullName)}</b></dd>
        <dt>Username</dt><dd class="mono">${esc(p.username)}</dd>
        <dt>Customer ID</dt><dd class="mono">${esc(p.userId)}</dd>
        <dt>National ID (NIC)</dt><dd class="mono">${esc(p.nationalId)}</dd>
        <dt>Registered Email</dt><dd>${esc(p.email)}</dd>
        <dt>Mobile Phone</dt><dd>${esc(p.phone)}</dd>
        <dt>Residential Address</dt><dd>${esc(p.address)}</dd>
        <dt>Total Accounts</dt><dd>${p.accountCount}</dd>
      </dl>
    `;

    ctx.el.innerHTML = `
      <div class="grid grid-2">
        <div class="card">
          <h2>Account Details</h2>
          <div id="profileSummaryBox" style="margin-top:14px;">${renderProfileSummary(profileData)}</div>
        </div>

        <div class="card">
          <h2>Update Contact Details</h2>
          <p class="muted" style="margin-bottom:16px;">Keep your verified contact details up to date.</p>
          <form id="updateProfileForm" novalidate>
            <div class="field">
              <label for="peEmail">Email Address</label>
              <input id="peEmail" type="email" value="${esc(profileData.email)}" required>
            </div>
            <div class="field">
              <label for="pePhone">Mobile Phone</label>
              <input id="pePhone" type="tel" value="${esc(profileData.phone)}" required>
            </div>
            <div class="field">
              <label for="peAddress">Postal Address</label>
              <input id="peAddress" type="text" value="${esc(profileData.address)}" required>
            </div>
            <div class="form-msg hidden"></div>
            <button class="btn btn-primary" type="submit">Save Changes</button>
          </form>
        </div>
      </div>

      <div class="card" style="margin-top: 24px;">
        <h2>Change Security Password</h2>
        <p class="muted" style="margin-bottom:16px;">Protect your account with a strong, distinct password.</p>
        <form id="changePasswordForm" style="max-width: 600px;" novalidate>
          <div class="form-row">
            <div class="field">
              <label for="cpInput">Current Password</label>
              <input id="cpInput" type="password" autocomplete="current-password" required>
            </div>
            <div class="field">
              <label for="npInput">New Password</label>
              <input id="npInput" type="password" autocomplete="new-password" required>
              <span class="hint">Minimum 8 characters with at least one letter and one number.</span>
            </div>
          </div>
          <div class="form-msg hidden"></div>
          <button class="btn btn-primary" type="submit">Update Password</button>
        </form>
      </div>
    `;

    document.getElementById('updateProfileForm').addEventListener('submit', (e) => {
      e.preventDefault();
      submit(e.target, async () => {
        const res = await api('PUT', 'profile', {
          email: document.getElementById('peEmail').value,
          phone: document.getElementById('pePhone').value,
          address: document.getElementById('peAddress').value
        });
        document.getElementById('profileSummaryBox').innerHTML = renderProfileSummary(res.profile);
        toast(res.message);
      });
    });

    document.getElementById('changePasswordForm').addEventListener('submit', (e) => {
      e.preventDefault();
      submit(e.target, async () => {
        const res = await api('PUT', 'password', {
          currentPassword: document.getElementById('cpInput').value,
          newPassword: document.getElementById('npInput').value
        });
        e.target.reset();
        toast(res.message);
      });
    });
  }

  // ═════════════════════ 6. PAY BILL MODAL ═════════════════════
  async function openPayBillModal(accounts) {
    let bills = {};
    try {
      const res = await api('GET', 'bills');
      bills = res.bills;
    } catch (e) {
      toast('Failed to load bill providers: ' + e.message, false);
      return;
    }

    const modalHtml = `
      <h2>Instant Utility Bill Payment</h2>
      <p class="muted" style="margin-bottom: 18px;">Select provider and debiting account.</p>
      <form id="billForm" novalidate>
        <div class="field">
          <label for="bProvider">Utility / Service Provider</label>
          <select id="bProvider">
            ${Object.entries(bills).map(([k, v]) => `<option value="${esc(k)}">${esc(k)} — LKR ${v}</option>`).join('')}
          </select>
        </div>
        <div class="field">
          <label for="bAccount">Debit Account</label>
          <select id="bAccount">
            ${buildAccountOptions(accounts)}
          </select>
        </div>
        <div class="form-msg hidden"></div>
        <div class="modal-actions">
          <button type="button" class="btn btn-ghost" data-close>Cancel</button>
          <button type="submit" class="btn btn-primary">Pay Bill Now</button>
        </div>
      </form>
    `;

    const m = Portal.modal(modalHtml);
    m.element.querySelector('#billForm').addEventListener('submit', (e) => {
      e.preventDefault();
      submit(e.target, async () => {
        const provider = document.getElementById('bProvider').value;
        const acc = document.getElementById('bAccount').value;
        const res = await api('POST', 'pay-bill', {
          accountNumber: acc,
          billType: provider
        });
        toast(res.message);
        m.close();
        // Refresh page if on dashboard
        if (page === 'dashboard') renderDashboard();
      });
    });
  }

  // ═════════════════════ ROUTE DISPATCHER ═════════════════════
  const routes = {
    dashboard: renderDashboard,
    accounts: renderAccounts,
    transactions: renderTransactions,
    profile: renderProfile,
    deposit: () => renderMoneyOperation(
      'deposit', 'Deposit Funds', 'Credit funds securely into your Rajarata Bank account.',
      'deposit', 'Complete Deposit'
    ),
    withdraw: () => renderMoneyOperation(
      'withdraw', 'Withdraw Funds', 'Withdraw funds from your savings or current account.',
      'withdraw', 'Complete Withdrawal'
    ),
    transfer: () => renderMoneyOperation(
      'transfer', 'Instant Money Transfer', 'Send money to any Rajarata Bank account in real-time.',
      'transfer', 'Review & Transfer',
      `<div class="field">
         <label for="toAccountInput">Recipient Account Number</label>
         <input id="toAccountInput" class="mono" placeholder="e.g. SAV0001002" maxlength="10" autocomplete="off" required>
         <span class="hint">Enter valid 10-character account number (SAV..., CUR..., or FD...).</span>
       </div>`
    )
  };

  if (routes[page]) {
    routes[page]();
  }
})();
