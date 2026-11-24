/**
 * Rajarata Digital Banking System — Core Frontend Framework
 * Provides: API client, Session storage, Layout shells, Modals, Toasts, Table rendering.
 */
const Portal = (() => {
  // Navigation structure for Customers
  const CUSTOMER_NAV = [
    ['dashboard.html',    '🏠', 'Dashboard'],
    ['accounts.html',     '💳', 'My Accounts'],
    ['deposit.html',      '⬇️', 'Deposit Funds'],
    ['withdraw.html',     '⬆️', 'Withdraw Funds'],
    ['transfer.html',     '🔁', 'Transfer Money'],
    ['transactions.html', '📄', 'Transactions'],
    ['profile.html',      '👤', 'My Profile'],
  ];

  // Navigation structure for Admins
  const ADMIN_NAV = [
    ['admin.html',        '📊', 'Admin Dashboard'],
    ['accounts.html',     '💳', 'Accounts Directory'],
    ['transactions.html', '📄', 'Master Ledger'],
    ['profile.html',      '👤', 'Admin Profile'],
  ];

  const TYPE_LABEL = {
    DEP: 'Deposit',
    WDR: 'Withdrawal',
    TRF_OUT: 'Transfer Sent',
    TRF_IN: 'Transfer Received',
    BILL: 'Bill Payment',
    LOAN_REP: 'Loan Repayment',
    INT: 'Interest Credit',
    FEE: 'Bank Fee'
  };

  const ACC_LABEL = {
    SAVINGS: 'Savings Account',
    CURRENT: 'Current Account',
    FIXED_DEPOSIT: 'Fixed Deposit'
  };

  // ── Text & Format Helpers ───────────────────────────────────────────────
  const esc = (v) => String(v ?? '').replace(/[&<>"']/g, c => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  }[c]));

  const money = (n) => {
    const val = Number(n || 0);
    return 'LKR ' + val.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  };

  const typeLabel = t => TYPE_LABEL[t] || t;
  const accLabel = t => ACC_LABEL[t] || t;

  // ── Session Storage ─────────────────────────────────────────────────────
  const session = {
    get token() { return sessionStorage.getItem('rb_token'); },
    get user() {
      try { return JSON.parse(sessionStorage.getItem('rb_user') || 'null'); }
      catch (e) { return null; }
    },
    save(token, user) {
      sessionStorage.setItem('rb_token', token);
      sessionStorage.setItem('rb_user', JSON.stringify(user));
    },
    clear() {
      sessionStorage.removeItem('rb_token');
      sessionStorage.removeItem('rb_user');
    }
  };

  // ── API Client ──────────────────────────────────────────────────────────
  const API_BASE = location.port === '8080' ? '' : 'http://localhost:8080';

  async function api(method, path, body) {
    const headers = { 'Content-Type': 'application/json' };
    if (session.token) {
      headers.Authorization = 'Bearer ' + session.token;
    }

    let response, data;
    try {
      response = await fetch(API_BASE + '/api/' + path, {
        method,
        headers,
        body: body ? JSON.stringify(body) : undefined
      });
    } catch (err) {
      throw new Error('Cannot connect to Rajarata Bank Server at http://localhost:8080. Please ensure the Java server is running.');
    }

    try {
      data = await response.json();
    } catch (err) {
      throw new Error('Invalid JSON response received from server (Status ' + response.status + ').');
    }

    // Auto-redirect on expired session
    if (response.status === 401 && !path.startsWith('login') && !path.startsWith('register')) {
      session.clear();
      location.href = 'login.html?expired=1';
      throw new Error('Session has expired. Please log in again.');
    }

    if (!response.ok || !data.success) {
      throw new Error(data.message || 'Operation failed on banking server.');
    }

    return data;
  }

  // ── Toast Notification System ───────────────────────────────────────────
  let toastContainer = null;
  function getToastContainer() {
    if (!toastContainer || !document.body.contains(toastContainer)) {
      toastContainer = document.createElement('div');
      toastContainer.className = 'toast-container';
      document.body.appendChild(toastContainer);
    }
    return toastContainer;
  }

  function toast(message, ok = true) {
    const container = getToastContainer();
    const t = document.createElement('div');
    t.className = 'toast ' + (ok === 'info' ? 'info' : (ok ? 'ok' : 'bad'));
    t.textContent = message;
    container.appendChild(t);
    setTimeout(() => {
      t.style.opacity = '0';
      t.style.transform = 'translateY(10px)';
      t.style.transition = 'all 0.3s ease';
      setTimeout(() => t.remove(), 300);
    }, 4000);
  }

  // ── Modal Dialogs ───────────────────────────────────────────────────────
  function modal(htmlContent) {
    const backdrop = document.createElement('div');
    backdrop.className = 'modal-backdrop';
    backdrop.innerHTML = `<div class="modal-box" role="dialog" aria-modal="true">${htmlContent}</div>`;

    const close = () => {
      backdrop.remove();
      document.removeEventListener('keydown', onKeyDown);
    };

    const onKeyDown = (e) => {
      if (e.key === 'Escape') close();
    };

    backdrop.addEventListener('click', (e) => {
      if (e.target === backdrop || e.target.closest('[data-close]')) {
        close();
      }
    });

    document.addEventListener('keydown', onKeyDown);
    document.body.appendChild(backdrop);
    return { element: backdrop, close };
  }

  function confirmDialog(title, message, onConfirm) {
    const m = modal(`
      <h2>${esc(title)}</h2>
      <p style="margin: 12px 0 20px; color: var(--charcoal-700);">${esc(message)}</p>
      <div class="modal-actions">
        <button class="btn btn-ghost" data-close>Cancel</button>
        <button class="btn btn-primary" id="confirmActionBtn">Confirm</button>
      </div>
    `);
    m.element.querySelector('#confirmActionBtn').onclick = async () => {
      m.close();
      await onConfirm();
    };
  }

  // ── Transaction Details & Table ─────────────────────────────────────────
  async function showTransactionDetails(id) {
    try {
      const { transaction: t } = await api('GET', 'transactions/' + encodeURIComponent(id));
      modal(`
        <h2>Receipt — ${esc(t.transactionId)}</h2>
        <dl class="kv">
          <dt>Reference ID</dt><dd class="mono">${esc(t.transactionId)}</dd>
          <dt>Transaction Type</dt><dd><b>${esc(typeLabel(t.type))}</b></dd>
          <dt>Amount</dt><dd class="${t.direction === 'CREDIT' ? 'credit' : 'debit'}" style="font-weight:700; font-size:1.1rem;">
            ${t.direction === 'CREDIT' ? '+' : '−'} ${money(t.amount)}
          </dd>
          <dt>Balance After</dt><dd class="mono">${money(t.balanceAfter)}</dd>
          <dt>Account Number</dt><dd class="mono">${esc(t.accountNumber)}</dd>
          <dt>Description</dt><dd>${esc(t.description)}</dd>
          <dt>Timestamp</dt><dd>${esc(t.timestamp)}</dd>
        </dl>
        <div class="modal-actions">
          <button class="btn btn-ghost btn-sm" onclick="window.print()">🖨️ Print</button>
          <button class="btn btn-primary btn-sm" data-close>Close</button>
        </div>
      `);
    } catch (e) {
      toast(e.message, false);
    }
  }

  function txnTable(list) {
    if (!list || !list.length) {
      return '<div style="padding: 24px; text-align: center; color: var(--charcoal-500);">No transactions found.</div>';
    }
    return `
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Date &amp; Time</th>
              <th>Description / Type</th>
              <th>Account</th>
              <th class="num">Amount</th>
            </tr>
          </thead>
          <tbody>
            ${list.map(t => `
              <tr class="clickable" data-txn="${esc(t.transactionId)}">
                <td><span style="font-size:0.88rem; color:var(--charcoal-600);">${esc(t.timestamp)}</span></td>
                <td>
                  <div style="font-weight:600; color:var(--charcoal-900);">${esc(t.description)}</div>
                  <span class="badge ${t.direction === 'CREDIT' ? 'badge-ok' : 'badge-off'}" style="font-size:0.72rem; margin-top:3px;">
                    ${esc(typeLabel(t.type))}
                  </span>
                </td>
                <td class="mono">${esc(t.accountNumber)}</td>
                <td class="num ${t.direction === 'CREDIT' ? 'credit' : 'debit'}" style="font-weight:700;">
                  ${t.direction === 'CREDIT' ? '+' : '−'} ${money(t.amount)}
                </td>
              </tr>
            `).join('')}
          </tbody>
        </table>
      </div>
    `;
  }

  function bindTxnRows(container) {
    container.querySelectorAll('[data-txn]').forEach(row => {
      row.addEventListener('click', () => showTransactionDetails(row.dataset.txn));
    });
  }

  // ── Authentication Logout ───────────────────────────────────────────────
  async function logout() {
    try {
      await api('POST', 'logout');
    } catch (e) {
      // Ignored if session already expired
    }
    session.clear();
    location.href = 'login.html?loggedout=1';
  }

  // ── App Shell Initializer ───────────────────────────────────────────────
  async function init(pageName, title, subtitle) {
    if (!session.token) {
      location.href = 'login.html';
      return null;
    }

    let user;
    try {
      const res = await api('GET', 'me');
      user = res.user;
    } catch (e) {
      session.clear();
      location.href = 'login.html?expired=1';
      return null;
    }

    const isAdmin = user.role === 'ADMIN';
    const navItems = isAdmin ? ADMIN_NAV : CUSTOMER_NAV;

    const sub = typeof subtitle === 'function' ? subtitle(user) : subtitle;
    const nameParts = (user.fullName || user.username).trim().split(/\s+/);
    const initials = (nameParts[0][0] + (nameParts.length > 1 ? nameParts[1][0] : '')).toUpperCase();

    const app = document.getElementById('app');
    app.innerHTML = `
      <div class="shell" id="shell">
        <aside class="sidebar">
          <a class="brand" href="dashboard.html">
            <span class="brand-icon">✦</span>
            RAJARATA<span>BANK</span>
          </a>
          <nav class="nav">
            ${navItems.map(([href, ico, label]) => `
              <a href="${href}" class="${href.startsWith(pageName) ? 'active' : ''}">
                <span class="ico">${ico}</span>${label}
              </a>
            `).join('')}
            <span class="spacer"></span>
            <button id="logoutBtn"><span class="ico">🚪</span>Log Out</button>
          </nav>
        </aside>

        <div class="scrim" id="scrim"></div>

        <div class="main">
          <header class="topbar">
            <div class="left">
              <button class="menu-btn" id="menuBtn" aria-label="Toggle navigation menu">☰</button>
              <h2>${esc(title)}</h2>
            </div>
            <div class="user-chip">
              <div style="text-align: right;">
                <div style="font-weight: 700; color: var(--charcoal-900); font-size: 0.95rem;">${esc(user.fullName || user.username)}</div>
                <div style="font-size: 0.78rem; color: var(--emerald-700); font-weight: 600;">${isAdmin ? 'ADMINISTRATOR' : 'CUSTOMER PORTAL'}</div>
              </div>
              <div class="avatar">${esc(initials)}</div>
            </div>
          </header>

          <main class="content">
            <div class="page-head">
              <h1>${esc(title)}</h1>
              ${sub ? `<p>${esc(sub)}</p>` : ''}
            </div>
            <div id="page"></div>
          </main>
        </div>
      </div>
    `;

    const shell = document.getElementById('shell');
    document.getElementById('menuBtn').onclick = () => shell.classList.toggle('open');
    document.getElementById('scrim').onclick = () => shell.classList.remove('open');
    document.getElementById('logoutBtn').onclick = logout;
    document.title = title + ' | Rajarata Digital Bank';

    return {
      el: document.getElementById('page'),
      user
    };
  }

  // ── Async Form Handler with Loading State ───────────────────────────────
  async function submit(form, action) {
    const btn = form.querySelector('button[type="submit"]');
    const msg = form.querySelector('.form-msg');
    if (msg) {
      msg.className = 'form-msg hidden';
      msg.textContent = '';
    }
    const originalText = btn ? btn.innerHTML : '';
    if (btn) {
      btn.disabled = true;
      btn.innerHTML = 'Processing…';
    }

    try {
      await action();
    } catch (e) {
      if (msg) {
        msg.className = 'form-msg alert alert-error';
        msg.textContent = e.message;
      } else {
        toast(e.message, false);
      }
    } finally {
      if (btn && !form.dataset.done) {
        btn.disabled = false;
        btn.innerHTML = originalText;
      }
    }
  }

  return {
    api,
    session,
    esc,
    money,
    typeLabel,
    accLabel,
    toast,
    modal,
    confirmDialog,
    txnTable,
    bindTxnRows,
    showTransactionDetails,
    init,
    submit,
    logout
  };
})();
