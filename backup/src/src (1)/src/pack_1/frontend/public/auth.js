/* Login and registration pages. */
(() => {
  const { api, session, submit } = Portal;
  const page = document.body.dataset.page;
  const banner = document.getElementById('banner');
  const show = (text, kind) => { banner.textContent = text; banner.className = 'alert alert-' + kind; };

  if (session.token && page === 'login') location.href = 'dashboard.html';

  if (page === 'login') {
    const q = new URLSearchParams(location.search);
    if (q.has('registered')) show('Account created successfully. Please log in.', 'success');
    if (q.has('expired'))    show('Your session has expired. Please log in again.', 'info');
    if (q.has('loggedout'))  show('You have been logged out.', 'info');
    if (q.has('admin'))      show('Administrator accounts use the bank\'s console. Please log in with a customer account.', 'info');
    document.getElementById('loginForm').addEventListener('submit', e => {
      e.preventDefault();
      submit(e.target, async () => {
        const r = await api('POST', 'login', { username: document.getElementById('username').value.trim(), password: document.getElementById('password').value });
        if (r.user.role !== 'CUSTOMER') { throw new Error('Administrator accounts cannot use the customer website.'); }
        session.save(r.token, r.user);
        location.href = 'dashboard.html';
      });
    });
  }

  if (page === 'register') {
    document.getElementById('registerForm').addEventListener('submit', e => {
      e.preventDefault();
      submit(e.target, async () => {
        const v = id => document.getElementById(id).value;
        if (v('password') !== v('confirm')) throw new Error('Passwords do not match.');
        const r = await api('POST', 'register', { fullName: v('fullName'), username: v('username'), email: v('email'), phone: v('phone'),
                                                  nationalId: v('nationalId'), address: v('address'), password: v('password') });
        // success: show the backend's message and the new customer id, then go to login
        show(r.message + ' Your customer ID is ' + r.userId + '.', 'success');
        e.target.dataset.done = '1';
        setTimeout(() => { location.href = 'login.html?registered=1'; }, 1800);
      });
    });
  }
})();
