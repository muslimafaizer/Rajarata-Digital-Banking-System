/**
 * Rajarata Digital Banking System — Authentication Handlers
 * Manages customer registration, login with role auto-detection, demo credentials, and password toggling.
 */
(() => {
  const { api, session, submit } = Portal;
  const page = document.body.dataset.page;
  const banner = document.getElementById('banner');

  const showBanner = (text, kind) => {
    if (!banner) return;
    banner.textContent = text;
    banner.className = 'alert alert-' + kind;
  };

  // If already logged in, redirect to appropriate destination
  if (session.token && page === 'login') {
    const user = session.user;
    if (user && user.role === 'ADMIN') {
      location.href = 'admin.html';
    } else {
      location.href = 'dashboard.html';
    }
  }

  // ═════════════════════ LOGIN PAGE ═════════════════════
  if (page === 'login') {
    const q = new URLSearchParams(location.search);
    if (q.has('registered')) showBanner('Registration completed successfully! Please sign in with your credentials.', 'success');
    if (q.has('expired'))    showBanner('Your banking session has expired. Please sign in again.', 'info');
    if (q.has('loggedout'))  showBanner('You have been securely logged out.', 'info');

    // Demo pills click handlers
    document.querySelectorAll('.demo-pill').forEach(pill => {
      pill.addEventListener('click', () => {
        document.getElementById('username').value = pill.dataset.user;
        document.getElementById('password').value = pill.dataset.pass;
      });
    });

    // Password show/hide toggle
    const togglePw = document.getElementById('togglePassword');
    if (togglePw) {
      togglePw.addEventListener('click', () => {
        const pwField = document.getElementById('password');
        const isText = pwField.type === 'text';
        pwField.type = isText ? 'password' : 'text';
        togglePw.textContent = isText ? '👁️' : '🙈';
      });
    }

    document.getElementById('loginForm').addEventListener('submit', (e) => {
      e.preventDefault();
      submit(e.target, async () => {
        const u = document.getElementById('username').value.trim();
        const p = document.getElementById('password').value;
        const res = await api('POST', 'login', { username: u, password: p });

        session.save(res.token, res.user);

        if (res.user.role === 'ADMIN') {
          location.href = 'admin.html';
        } else {
          location.href = 'dashboard.html';
        }
      });
    });
  }

  // ═════════════════════ REGISTRATION PAGE ═════════════════════
  if (page === 'register') {
    const pw = document.getElementById('password');
    const cpw = document.getElementById('confirm');
    const meter = document.getElementById('strengthMeter');

    if (pw && meter) {
      pw.addEventListener('input', () => {
        const val = pw.value;
        let score = 0;
        if (val.length >= 8) score++;
        if (/[A-Z]/.test(val) || /[a-z]/.test(val)) score++;
        if (/[0-9]/.test(val)) score++;
        if (/[^A-Za-z0-9]/.test(val)) score++;

        const colors = ['#dc2626', '#f59e0b', '#10b981', '#059669'];
        const labels = ['Weak', 'Fair', 'Good', 'Strong'];
        meter.style.width = (score * 25) + '%';
        meter.style.backgroundColor = colors[Math.max(0, score - 1)] || '#dc2626';
        const lbl = document.getElementById('strengthLabel');
        if (lbl) lbl.textContent = val ? labels[Math.max(0, score - 1)] : '';
      });
    }

    document.getElementById('registerForm').addEventListener('submit', (e) => {
      e.preventDefault();
      submit(e.target, async () => {
        const val = id => document.getElementById(id).value.trim();
        if (val('password') !== val('confirm')) {
          throw new Error('Passwords do not match. Please verify both password fields.');
        }

        const res = await api('POST', 'register', {
          fullName: val('fullName'),
          username: val('username'),
          email: val('email'),
          phone: val('phone'),
          nationalId: val('nationalId'),
          address: val('address'),
          password: val('password')
        });

        showBanner(`${res.message} Your new Customer ID is ${res.userId} and Primary Savings Account is ${res.accountNumber}. Redirecting to sign in…`, 'success');
        e.target.dataset.done = '1';
        setTimeout(() => {
          location.href = 'login.html?registered=1';
        }, 2200);
      });
    });
  }
})();
