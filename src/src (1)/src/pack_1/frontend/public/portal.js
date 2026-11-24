/* Shared helpers for every logged-in page: API client, layout shell, formatting, modal, toast. */
const Portal = (() => {
  const NAV = [
    ['dashboard.html',    '🏠', 'Dashboard'],
    ['accounts.html',     '💳', 'My Accounts'],
    ['deposit.html',      '⬇️', 'Deposit'],
    ['withdraw.html',     '⬆️', 'Withdraw'],
    ['transfer.html',     '🔁', 'Transfer'],
    ['transactions.html', '📄', 'Transactions'],
    ['profile.html',      '👤', 'Profile'],
  ];
  const TYPE_LABEL = { DEP: 'Deposit', WDR: 'Withdrawal', TRF_OUT: 'Transfer sent', TRF_IN: 'Transfer received',
                       BILL: 'Bill payment', LOAN_REP: 'Loan repayment', INT: 'Interest', FEE: 'Fee' };
  const ACC_LABEL  = { SAVINGS: 'Savings', CURRENT: 'Current', FIXED_DEPOSIT: 'Fixed Deposit' };

  // ---- text helpers (all dynamic text goes through esc() to prevent HTML injection) ----
  const esc = v => String(v ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const money = n => 'LKR ' + Number(n).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  const typeLabel = t => TYPE_LABEL[t] || t;
  const accLabel  = t => ACC_LABEL[t] || t;

  // ---- API client: every call goes to the Java backend; nothing is simulated here ----
  const session = {
    get token() { return sessionStorage.getItem('token'); },
    save(token, user) { sessionStorage.setItem('token', token); sessionStorage.setItem('user', JSON.stringify(user)); },
    clear() { sessionStorage.removeItem('token'); sessionStorage.removeItem('user'); },
  };

  // Java backend address. When the page itself is served by the Java server (port 8080) the
  // same-origin path is used; when opened any other way (Live Server, file) it points to :8080.
  const API_BASE = location.port === '8080' ? '' : 'http://localhost:8080';

  async function api(method, path, body) {
    const headers = { 'Content-Type': 'application/json' };
    if (session.token) headers.Authorization = 'Bearer ' + session.token;
    let res, data;
    try {
      res = await fetch(API_BASE + '/api/' + path, { method, headers, body: body ? JSON.stringify(body) : undefined });
    } catch (e) {
      throw new Error('Cannot reach the bank server at http://localhost:8080. Please start it (java -cp out pack_1.WebServer).');
    }
    try {
      data = await res.json();
    } catch (e) {
      throw new Error('The server sent an unexpected reply (HTTP ' + res.status + '). Open this site at http://localhost:8080');
    }
    if (res.status === 401 && !path.startsWith('login') && !path.startsWith('password')) {
      session.clear();
      location.href = 'login.html?expired=1';
      throw new Error('Session expired');
    }
    if (!res.ok || !data.success) throw new Error(data.message || 'Something went wrong');
    return data;
  }

  // ---- UI helpers ----
  function toast(message, ok = true) {
    const t = document.createElement('div');
    t.className = 'toast ' + (ok ? 'ok' : 'bad');
    t.textContent = message;
    document.body.appendChild(t);
    setTimeout(() => t.remove(), 3500);
  }

  function modal(html) {
    const back = document.createElement('div');
    back.className = 'modal-back';
    back.innerHTML = '<div class="modal" role="dialog" aria-modal="true">' + html + '</div>';
    back.addEventListener('click', e => { if (e.target === back || e.target.dataset.close !== undefined) back.remove(); });
    document.body.appendChild(back);
    return back;
  }

  async function showTransaction(id) {
    try {
      const { transaction: t } = await api('GET', 'transactions/' + encodeURIComponent(id));
      modal(`<h2>Transaction details</h2>
        <dl class="kv">
          <dt>Reference</dt><dd class="mono">${esc(t.transactionId)}</dd>
          <dt>Type</dt><dd>${esc(typeLabel(t.type))}</dd>
          <dt>Amount</dt><dd class="${t.direction === 'CREDIT' ? 'credit' : 'debit'}">${t.direction === 'CREDIT' ? '+' : '−'} ${money(t.amount)}</dd>
          <dt>Balance after</dt><dd>${money(t.balanceAfter)}</dd>
          <dt>Account</dt><dd class="mono">${esc(t.accountNumber)}</dd>
          <dt>Description</dt><dd>${esc(t.description)}</dd>
          <dt>Date &amp; time</dt><dd>${esc(t.timestamp)}</dd>
        </dl>
        <div class="actions"><button class="btn btn-primary btn-sm" data-close>Close</button></div>`);
    } catch (e) { toast(e.message, false); }
  }

  /** Renders a transactions table; clicking a row opens the details dialog. */
  function txnTable(list) {
    if (!list.length) return '<div class="empty">No transactions yet.</div>';
    return `<div class="table-wrap"><table><thead><tr><th>Date</th><th>Description</th><th>Account</th><th class="num">Amount</th></tr></thead><tbody>` +
      list.map(t => `<tr class="clickable" data-txn="${esc(t.transactionId)}">
        <td>${esc(t.timestamp)}</td><td>${esc(t.description)}<div class="muted">${esc(typeLabel(t.type))}</div></td>
        <td class="mono">${esc(t.accountNumber)}</td>
        <td class="num ${t.direction === 'CREDIT' ? 'credit' : 'debit'}">${t.direction === 'CREDIT' ? '+' : '−'} ${money(t.amount)}</td></tr>`).join('') +
      '</tbody></table></div>';
  }

  function bindTxnRows(root) {
    root.querySelectorAll('[data-txn]').forEach(r => r.addEventListener('click', () => showTransaction(r.dataset.txn)));
  }

  async function logout() {
    try { await api('POST', 'logout'); } catch (e) { /* session may already be gone */ }
    session.clear();
    location.href = 'login.html?loggedout=1';
  }

  /** Builds sidebar + top bar around the page and returns the content element. */
  async function init(page, title, subtitle) {
    if (!session.token) { location.href = 'login.html'; return null; }
    let user;
    try { user = (await api('GET', 'me')).user; } catch (e) { return null; }
    if (user.role !== 'CUSTOMER') {
      session.clear();
      location.href = 'login.html?admin=1';
      return null;
    }
    const sub = typeof subtitle === 'function' ? subtitle(user) : subtitle;
    const initials = user.fullName.split(/\s+/).map(w => w[0]).slice(0, 2).join('').toUpperCase();
    const app = document.getElementById('app');
    app.innerHTML = `
      <div class="shell" id="shell">
        <aside class="sidebar">
          <div class="brand">RAJARATA<span>BANK</span></div>
          <nav class="nav">
            ${NAV.map(([href, ico, label]) => `<a href="${href}" class="${href.startsWith(page) ? 'active' : ''}"><span class="ico">${ico}</span>${label}</a>`).join('')}
            <span class="spacer"></span>
            <button id="logoutBtn"><span class="ico">🚪</span>Logout</button>
          </nav>
        </aside>
        <div class="scrim" id="scrim"></div>
        <div class="main">
          <header class="topbar">
            <div class="left"><button class="menu-btn" id="menuBtn" aria-label="Open menu">☰</button><h2>${esc(title)}</h2></div>
            <div class="user-chip"><span class="name">${esc(user.fullName)}</span><span class="avatar">${esc(initials)}</span></div>
          </header>
          <main class="content" id="content">
            <div class="page-head"><h1>${esc(title)}</h1>${sub ? '<p>' + esc(sub) + '</p>' : ''}</div>
            <div id="page"></div>
          </main>
        </div>
      </div>`;
    const shell = document.getElementById('shell');
    document.getElementById('menuBtn').onclick = () => shell.classList.toggle('open');
    document.getElementById('scrim').onclick   = () => shell.classList.remove('open');
    document.getElementById('logoutBtn').onclick = logout;
    document.title = title + ' | Rajarata Digital Bank';
    return { el: document.getElementById('page'), user };
  }

  /** Disables a button while a request runs and shows errors inside the form. */
  async function submit(form, work) {
    const btn = form.querySelector('button[type=submit]');
    const msg = form.querySelector('.form-msg');
    msg.className = 'form-msg hidden';
    btn.disabled = true;
    try { await work(); }
    catch (e) { msg.textContent = e.message; msg.className = 'form-msg alert alert-error'; }
    finally { if (!form.dataset.done) btn.disabled = false; }
  }

  return { api, session, esc, money, typeLabel, accLabel, toast, modal, txnTable, bindTxnRows, showTransaction, init, submit, logout };
})();
