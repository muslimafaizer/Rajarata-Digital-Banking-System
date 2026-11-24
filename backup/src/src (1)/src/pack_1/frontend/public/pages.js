/* Page logic for the logged-in pages. Every number shown comes from the Java backend. */
(() => {
  const { api, esc, money, accLabel, toast, txnTable, bindTxnRows, init, submit } = Portal;
  const page = document.body.dataset.page;

  const accountOptions = list => list.filter(a => a.active).map(a =>
    `<option value="${esc(a.accountNumber)}">${esc(accLabel(a.accountType))} · ${esc(a.accountNumber)} · ${money(a.balance)}</option>`).join('');

  // ───────────── Dashboard ─────────────
  async function dashboard() {
    const ctx = await init('dashboard', 'Dashboard', u => 'Welcome back, ' + u.fullName.split(' ')[0] + '. Here is your financial overview.');
    if (!ctx) return;
    try {
      const d = await api('GET', 'dashboard');
      const p = d.primaryAccount;
      ctx.el.innerHTML = `
        <div class="grid grid-3">
          <div class="card balance-card">
            <div class="label">Total balance</div>
            <div class="big">${money(d.totalBalance)}</div>
            <div class="meta">${p ? `<span>Account <b class="mono">${esc(p.accountNumber)}</b></span><span>${esc(accLabel(p.accountType))}</span>` : ''}</div>
          </div>
          <div class="card stat"><div class="label">Money in (all time)</div><div class="value credit">${money(d.summary.totalCredits)}</div></div>
          <div class="card stat"><div class="label">Money out (all time)</div><div class="value debit">${money(d.summary.totalDebits)}</div>
            <div class="muted">${d.summary.transactionCount} transactions</div></div>
        </div>
        <div class="grid grid-main" id="dashRow">
          <div class="card"><div class="card-head"><h2>Recent transactions</h2><a href="transactions.html">View all →</a></div>${txnTable(d.recentTransactions)}</div>
          <div>
            <div class="card"><h2>Quick actions</h2><div class="quick" id="quick">
              <a href="deposit.html"><span class="ico">⬇️</span>Deposit</a><a href="withdraw.html"><span class="ico">⬆️</span>Withdraw</a>
              <a href="transfer.html"><span class="ico">🔁</span>Transfer</a><a href="accounts.html"><span class="ico">💳</span>Accounts</a></div></div>
            <div class="card"><h2>Your accounts</h2>${d.accounts.map(a => `
              <div class="card-head"><div><b>${esc(accLabel(a.accountType))}</b><div class="muted mono">${esc(a.accountNumber)}</div></div><b>${money(a.balance)}</b></div>`).join('')}
              ${d.loanActive ? `<div class="alert alert-info">Outstanding loan: ${money(d.loanBalance)}</div>` : ''}</div>
          </div>
        </div>`;
      bindTxnRows(ctx.el);
    } catch (e) { ctx.el.innerHTML = `<div class="alert alert-error">${esc(e.message)}</div>`; }
  }

  // ───────────── My Accounts ─────────────
  async function accounts() {
    const ctx = await init('accounts', 'My Accounts', 'Details and features of each of your accounts.');
    if (!ctx) return;
    async function load() {
      const { accounts } = await api('GET', 'accounts');
      document.getElementById('accList').innerHTML = accounts.map(a => `
        <div class="card account-card"><h3>${esc(accLabel(a.accountType))} <span class="badge ${a.active ? 'ok' : 'off'}">${a.active ? 'Active' : 'Inactive'}</span></h3>
          <div class="stat"><div class="value">${money(a.balance)}</div></div>
          <dl class="kv"><dt>Account no.</dt><dd class="mono">${esc(a.accountNumber)}</dd>
            <dt>Interest rate</dt><dd>${a.interestRate}% p.a.</dd><dt>Opened</dt><dd>${esc(a.dateOpened)}</dd>
            <dt>Features</dt><dd>${esc(a.features)}</dd><dt>Transactions</dt><dd>${a.transactionCount}</dd></dl>
          <div class="card-head"><span></span><a class="btn btn-ghost btn-sm" href="transactions.html#${esc(a.accountNumber)}">View transactions</a></div></div>`).join('');
    }
    ctx.el.innerHTML = `<div class="grid grid-2" id="accList"><div class="skeleton">Loading…</div></div>
      <div class="card"><h2>Open a new account</h2><p class="muted">Choose a type; you can add an opening deposit.</p><br>
        <form id="openForm" novalidate><div class="form-row">
          <div class="field"><label for="atype">Account type</label><select id="atype"><option value="SAVINGS">Savings (3.5% p.a.)</option><option value="CURRENT">Current (overdraft LKR 50,000)</option><option value="FIXED">Fixed Deposit (7.5% p.a.)</option></select></div>
          <div class="field"><label for="aamt">Opening deposit (LKR)</label><input id="aamt" type="number" min="0" step="0.01" placeholder="0.00"><span class="hint" id="ahint">Optional for Savings and Current.</span></div></div>
          <div class="field hidden" id="termField"><label for="aterm">Fixed deposit term</label><select id="aterm"><option value="3">3 months</option><option value="6">6 months</option><option value="12" selected>12 months</option><option value="24">24 months</option><option value="36">36 months</option></select></div>
          <div class="form-msg hidden"></div><button class="btn btn-primary" type="submit">Open account</button></form></div>`;
    const type = document.getElementById('atype');
    type.onchange = () => {
      const fd = type.value === 'FIXED';
      document.getElementById('termField').classList.toggle('hidden', !fd);
      document.getElementById('ahint').textContent = fd ? 'Minimum LKR 10,000 for a Fixed Deposit.' : 'Optional for Savings and Current.';
    };
    document.getElementById('openForm').addEventListener('submit', e => {
      e.preventDefault();
      submit(e.target, async () => {
        const amt = document.getElementById('aamt').value;
        const r = await api('POST', 'open-account', { accountType: type.value, amount: amt === '' ? 0 : Number(amt), fdMonths: Number(document.getElementById('aterm').value) });
        toast('Account ' + r.account.accountNumber + ' opened');
        e.target.reset(); type.onchange();
        await load();
      });
    });
    try { await load(); } catch (e) { document.getElementById('accList').innerHTML = `<div class="alert alert-error">${esc(e.message)}</div>`; }
  }

  // ───────────── Deposit / Withdraw / Transfer ─────────────
  async function moneyPage(pageName, title, sub, endpoint, label, extraFields) {
    const ctx = await init(pageName, title, sub);
    if (!ctx) return;
    let list = [];
    ctx.el.innerHTML = `<div class="card narrow"><form id="moneyForm" novalidate>
        <div class="field"><label for="acc">${pageName === 'transfer' ? 'From account' : 'Account'}</label><select id="acc"></select><span class="hint" id="balHint"></span></div>
        ${extraFields || ''}
        <div class="field"><label for="amt">Amount (LKR)</label><input id="amt" type="number" min="0.01" step="0.01" inputmode="decimal" placeholder="0.00" required></div>
        <div class="form-msg hidden"></div><button class="btn btn-primary btn-block" type="submit">${label}</button></form></div>`;
    const sel = document.getElementById('acc');
    const hint = () => { const a = list.find(x => x.accountNumber === sel.value); document.getElementById('balHint').textContent = a ? 'Available balance: ' + money(a.balance) : ''; };
    async function refresh() {
      list = (await api('GET', 'accounts')).accounts;
      const keep = sel.value;
      sel.innerHTML = accountOptions(list);
      if (keep) sel.value = keep;
      hint();
    }
    sel.onchange = hint;
    try { await refresh(); } catch (e) { ctx.el.innerHTML = `<div class="alert alert-error">${esc(e.message)}</div>`; return; }
    document.getElementById('moneyForm').addEventListener('submit', ev => {
      ev.preventDefault();
      submit(ev.target, async () => {
        const body = { accountNumber: sel.value, amount: Number(document.getElementById('amt').value) };
        const to = document.getElementById('to');
        if (to) body.toAccount = to.value.trim();
        const r = await api('POST', endpoint, body);
        toast(r.message + (r.balance != null ? ' — new balance ' + money(r.balance) : ''));
        document.getElementById('amt').value = '';
        if (to) to.value = '';
        await refresh();
      });
    });
  }

  // ───────────── Transactions ─────────────
  async function transactions() {
    const ctx = await init('transactions', 'Transaction History', 'Select any transaction to see its full details.');
    if (!ctx) return;
    const { accounts: accs } = await api('GET', 'accounts').catch(e => { ctx.el.innerHTML = `<div class="alert alert-error">${esc(e.message)}</div>`; return {}; });
    if (!accs) return;
    ctx.el.innerHTML = `<div class="card"><div class="filters">
        <div class="field"><label for="fAcc">Account</label><select id="fAcc"><option value="">All accounts</option>${accs.map(a => `<option value="${esc(a.accountNumber)}">${esc(accLabel(a.accountType))} · ${esc(a.accountNumber)}</option>`).join('')}</select></div>
        <div class="field"><label for="fType">Type</label><select id="fType"><option value="">All types</option><option value="DEP">Deposits</option><option value="WDR">Withdrawals</option><option value="TRF_OUT">Transfers sent</option><option value="TRF_IN">Transfers received</option><option value="BILL">Bills</option><option value="LOAN_REP">Loan repayments</option><option value="INT">Interest</option><option value="FEE">Fees</option></select></div>
        <div class="field"><label for="fText">Search</label><input id="fText" type="search" placeholder="Description…"></div></div>
        <div id="txnBox"><div class="skeleton">Loading…</div></div></div>`;
    const hash = decodeURIComponent(location.hash.slice(1));
    if (accs.some(a => a.accountNumber === hash)) document.getElementById('fAcc').value = hash;
    let all = [];
    const draw = () => {
      const q = document.getElementById('fText').value.trim().toLowerCase();
      const rows = all.filter(t => !q || t.description.toLowerCase().includes(q) || t.transactionId.toLowerCase().includes(q));
      document.getElementById('txnBox').innerHTML = txnTable(rows);
      bindTxnRows(document.getElementById('txnBox'));
    };
    const load = async () => {
      try {
        const qs = new URLSearchParams({ account: document.getElementById('fAcc').value, type: document.getElementById('fType').value });
        all = (await api('GET', 'transactions?' + qs)).transactions; draw();
      } catch (e) { document.getElementById('txnBox').innerHTML = `<div class="alert alert-error">${esc(e.message)}</div>`; }
    };
    document.getElementById('fAcc').onchange = load;
    document.getElementById('fType').onchange = load;
    document.getElementById('fText').oninput = draw;
    load();
  }

  // ───────────── Profile ─────────────
  async function profile() {
    const ctx = await init('profile', 'Profile', 'Your personal information and security settings.');
    if (!ctx) return;
    let p;
    try { p = (await api('GET', 'profile')).profile; } catch (e) { ctx.el.innerHTML = `<div class="alert alert-error">${esc(e.message)}</div>`; return; }
    const show = pr => `<dl class="kv"><dt>Full name</dt><dd>${esc(pr.fullName)}</dd><dt>Username</dt><dd>${esc(pr.username)}</dd><dt>Customer ID</dt><dd class="mono">${esc(pr.userId)}</dd>
        <dt>National ID</dt><dd class="mono">${esc(pr.nationalId)}</dd><dt>Email</dt><dd>${esc(pr.email)}</dd><dt>Phone</dt><dd>${esc(pr.phone)}</dd>
        <dt>Address</dt><dd>${esc(pr.address)}</dd><dt>Accounts</dt><dd>${pr.accountCount}</dd></dl>`;
    ctx.el.innerHTML = `<div class="grid grid-2">
      <div class="card"><h2>Personal information</h2><div id="info">${show(p)}</div></div>
      <div class="card"><h2>Update contact details</h2><br><form id="editForm" novalidate>
        <div class="field"><label for="pe">Email</label><input id="pe" type="email" value="${esc(p.email)}"></div>
        <div class="field"><label for="pp">Phone</label><input id="pp" value="${esc(p.phone)}"></div>
        <div class="field"><label for="pa">Address</label><input id="pa" value="${esc(p.address)}"></div>
        <div class="form-msg hidden"></div><button class="btn btn-primary" type="submit">Save changes</button></form></div></div>
      <div class="card" id="pwCard"><h2>Change password</h2><br><form id="pwForm" novalidate>
        <div class="form-row"><div class="field"><label for="cp">Current password</label><input id="cp" type="password" autocomplete="current-password"></div>
        <div class="field"><label for="np">New password</label><input id="np" type="password" autocomplete="new-password"><span class="hint">At least 8 characters, with a letter and a number.</span></div></div>
        <div class="form-msg hidden"></div><button class="btn btn-primary" type="submit">Change password</button></form></div>`;
    document.getElementById('editForm').addEventListener('submit', e => { e.preventDefault(); submit(e.target, async () => {
      const r = await api('PUT', 'profile', { email: document.getElementById('pe').value, phone: document.getElementById('pp').value, address: document.getElementById('pa').value });
      document.getElementById('info').innerHTML = show(r.profile); toast(r.message); }); });
    document.getElementById('pwForm').addEventListener('submit', e => { e.preventDefault(); submit(e.target, async () => {
      const r = await api('PUT', 'password', { currentPassword: document.getElementById('cp').value, newPassword: document.getElementById('np').value });
      e.target.reset(); toast(r.message); }); });
  }

  const pages = {
    dashboard, accounts, transactions, profile,
    deposit:  () => moneyPage('deposit',  'Deposit',  'Add money to one of your accounts.', 'deposit',  'Deposit'),
    withdraw: () => moneyPage('withdraw', 'Withdraw', 'Take money out of one of your accounts.', 'withdraw', 'Withdraw'),
    transfer: () => moneyPage('transfer', 'Transfer', 'Send money to any Rajarata Bank account instantly.', 'transfer', 'Send money',
      '<div class="field"><label for="to">To account number</label><input id="to" class="mono" placeholder="e.g. SAV0001002" autocomplete="off" maxlength="10" required></div>'),
  };
  (pages[page] || (() => {}))();
})();
