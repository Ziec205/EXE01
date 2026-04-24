const STORAGE_KEYS = {
  cart: 'thatrico_cart',
  auth: 'thatrico_auth'
};

const state = {
  products: [],
  cart: loadJson(STORAGE_KEYS.cart, []),
  auth: loadJson(STORAGE_KEYS.auth, null),
  quote: null,
  quoteSignature: ''
};

const nodes = {
  productGrid: document.getElementById('productGrid'),
  spotlightGrid: document.getElementById('spotlightGrid'),
  spotlightPrev: document.getElementById('spotlightPrev'),
  spotlightNext: document.getElementById('spotlightNext'),
  searchInput: document.getElementById('searchInput'),
  cropFilter: document.getElementById('cropFilter'),
  sortFilter: document.getElementById('sortFilter'),
  cartItems: document.getElementById('cartItems'),
  cartCount: document.getElementById('cartCount'),
  cartTotal: document.getElementById('cartTotal'),
  cartQuoteInfo: document.getElementById('cartQuoteInfo'),
  orderForm: document.getElementById('orderForm'),
  customerName: document.getElementById('customerName'),
  customerPhone: document.getElementById('customerPhone'),
  customerArea: document.getElementById('customerArea'),
  customerAddress: document.getElementById('customerAddress'),
  paymentMethod: document.getElementById('paymentMethod'),
  authBtn: document.getElementById('authBtn'),
  authStatus: document.getElementById('authStatus'),
  authModal: document.getElementById('authModal'),
  authTabButtons: document.querySelectorAll('[data-auth-tab]'),
  authPanels: document.querySelectorAll('[data-auth-panel]'),
  authCloseButtons: document.querySelectorAll('[data-auth-close]'),
  loginForm: document.getElementById('loginForm'),
  loginIdentifier: document.getElementById('loginIdentifier'),
  loginPassword: document.getElementById('loginPassword'),
  registerForm: document.getElementById('registerForm'),
  registerName: document.getElementById('registerName'),
  registerPhone: document.getElementById('registerPhone'),
  registerEmail: document.getElementById('registerEmail'),
  registerPassword: document.getElementById('registerPassword'),
  requestOtpBtn: document.getElementById('requestOtpBtn'),
  otpBlock: document.getElementById('otpBlock'),
  registerOtp: document.getElementById('registerOtp'),
  openCart: document.getElementById('openCart'),
  checkoutBtn: document.getElementById('checkoutBtn'),
  chatToggle: document.getElementById('chatToggle'),
  chatBox: document.getElementById('chatBox'),
  chatLog: document.getElementById('chatLog'),
  chatForm: document.getElementById('chatForm'),
  chatInput: document.getElementById('chatInput'),
  chips: document.querySelectorAll('.chip'),
  openChatFromHero: document.getElementById('openChatFromHero'),
  scrollButtons: document.querySelectorAll('[data-scroll]'),
  quickCropButtons: document.querySelectorAll('[data-quick-crop]')
};

function loadJson(key, fallback) {
  const raw = localStorage.getItem(key);
  if (!raw) return fallback;

  try {
    return JSON.parse(raw);
  } catch {
    return fallback;
  }
}

function saveJson(key, value) {
  localStorage.setItem(key, JSON.stringify(value));
}

function setAuth(auth) {
  state.auth = auth;
  if (auth) {
    saveJson(STORAGE_KEYS.auth, auth);
  } else {
    localStorage.removeItem(STORAGE_KEYS.auth);
  }
}

function setCart(cart) {
  state.cart = cart;
  saveJson(STORAGE_KEYS.cart, cart);
}

function updateAuthButtonLabel() {
  if (!nodes.authBtn) return;
  nodes.authBtn.textContent = state.auth?.user ? state.auth.user.name : 'Dang nhap';
}

function toVnd(value) {
  return Number(value).toLocaleString('vi-VN') + 'd';
}

function cropLabel(crop) {
  return {
    lua: 'Lua',
    cafe: 'Ca phe',
    rau: 'Rau mau',
    'cay-an-trai': 'Cay an trai'
  }[crop] || crop;
}

async function api(path, options = {}) {
  const headers = {
    'Content-Type': 'application/json',
    ...(options.headers || {})
  };

  if (state.auth?.token) {
    headers.Authorization = `Bearer ${state.auth.token}`;
  }

  const response = await fetch(path, {
    ...options,
    headers
  });

  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(data.message || 'API error');
  }
  return data;
}

function scrollToSection(selector) {
  const section = document.querySelector(selector);
  if (section) {
    section.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }
}

function openAuthModal(tab = 'login', status = '') {
  if (!nodes.authModal) return;
  nodes.authModal.classList.remove('hidden');
  nodes.authModal.setAttribute('aria-hidden', 'false');
  switchAuthTab(tab);
  renderAuthStatus(status);
}

function closeAuthModal() {
  if (!nodes.authModal) return;
  nodes.authModal.classList.add('hidden');
  nodes.authModal.setAttribute('aria-hidden', 'true');
}

function switchAuthTab(tab) {
  nodes.authTabButtons.forEach((button) => {
    button.classList.toggle('active', button.dataset.authTab === tab);
  });

  nodes.authPanels.forEach((panel) => {
    panel.classList.toggle('hidden', panel.dataset.authPanel !== tab);
    panel.classList.toggle('active', panel.dataset.authPanel === tab);
  });
}

function cartSignature() {
  return state.cart
    .map((item) => `${item.id}:${item.qty}`)
    .sort()
    .join('|');
}

function filteredProducts() {
  const query = nodes.searchInput.value.trim().toLowerCase();
  const crop = nodes.cropFilter.value;
  const sort = nodes.sortFilter.value;

  const list = state.products.filter((product) => {
    const matchQuery = `${product.name} ${product.benefit}`.toLowerCase().includes(query);
    const matchCrop = crop === 'all' || product.crop === crop;
    return matchQuery && matchCrop;
  });

  if (sort === 'priceAsc') list.sort((a, b) => a.price - b.price);
  if (sort === 'priceDesc') list.sort((a, b) => b.price - a.price);

  return list;
}

function renderProducts() {
  const products = filteredProducts();

  if (!products.length) {
    nodes.productGrid.innerHTML = '<div class="muted">Khong tim thay san pham phu hop.</div>';
    return;
  }

  nodes.productGrid.innerHTML = products
    .map((product) => `
      <article class="product">
        <div class="product-cover">${product.icon || '🌿'}</div>
        <div class="product-body">
          <span class="tag">${cropLabel(product.crop)}</span>
          <strong>${product.name}</strong>
          <div class="muted">${product.benefit}</div>
          <div class="muted">Kho: ${product.stock}</div>
          <div class="price">${toVnd(product.price)}</div>
          <button class="btn btn-primary" data-add="${product.id}" ${product.stock <= 0 ? 'disabled' : ''}>
            ${product.stock <= 0 ? 'Het hang' : 'Them vao gio'}
          </button>
        </div>
      </article>
    `)
    .join('');

  nodes.productGrid.querySelectorAll('[data-add]').forEach((button) => {
    button.addEventListener('click', () => addToCart(button.dataset.add));
  });
}

function renderSpotlightProducts() {
  if (!nodes.spotlightGrid) return;

  const spotlight = [...state.products]
    .filter((product) => Number(product.stock || 0) > 0)
    .sort((a, b) => Number(b.stock || 0) - Number(a.stock || 0))
    .slice(0, 4);

  if (!spotlight.length) {
    nodes.spotlightGrid.innerHTML = '<div class="muted">San pham noi bat se xuat hien khi co ton kho.</div>';
    return;
  }

  nodes.spotlightGrid.innerHTML = spotlight
    .map((product) => `
      <article class="spotlight-card">
        <div class="spotlight-top">
          <span class="spotlight-icon">${product.icon || '🌿'}</span>
          <span class="stock-pill">Con ${product.stock}</span>
        </div>
        <strong>${product.name}</strong>
        <div class="muted">${cropLabel(product.crop)} | ${product.benefit}</div>
        <div class="price">${toVnd(product.price)}</div>
        <button class="btn btn-primary" data-spotlight-add="${product.id}">Mua nhanh</button>
      </article>
    `)
    .join('');

  nodes.spotlightGrid.querySelectorAll('[data-spotlight-add]').forEach((button) => {
    button.addEventListener('click', () => {
      addToCart(button.dataset.spotlightAdd);
      scrollToSection('.flow-layout');
    });
  });
}

function applyQuickCropFilter(crop) {
  if (!nodes.cropFilter) return;
  nodes.cropFilter.value = crop;
  renderProducts();
}

function bindSpotlightControls() {
  if (!nodes.spotlightGrid || !nodes.spotlightPrev || !nodes.spotlightNext) return;

  const scrollByCard = (direction) => {
    const firstCard = nodes.spotlightGrid.querySelector('.spotlight-card');
    const distance = firstCard ? firstCard.getBoundingClientRect().width + 12 : 320;
    nodes.spotlightGrid.scrollBy({ left: direction * distance, behavior: 'smooth' });
  };

  nodes.spotlightPrev.addEventListener('click', () => scrollByCard(-1));
  nodes.spotlightNext.addEventListener('click', () => scrollByCard(1));
}

function addToCart(productId) {
  const product = state.products.find((item) => String(item.id) === String(productId));
  if (!product) return;

  const inCartQty = state.cart.find((item) => String(item.id) === String(productId))?.qty || 0;
  if (inCartQty >= Number(product.stock || 0)) {
    alert('So luong trong gio khong duoc vuot qua ton kho hien tai.');
    return;
  }

  const existing = state.cart.find((item) => String(item.id) === String(productId));
  if (existing) {
    existing.qty += 1;
  } else {
    state.cart.push({ ...product, qty: 1 });
  }

  setCart(state.cart);
  renderCart();
}

function changeQuantity(productId, delta) {
  const item = state.cart.find((entry) => String(entry.id) === String(productId));
  if (!item) return;

  const product = state.products.find((entry) => String(entry.id) === String(productId));
  const nextQty = item.qty + delta;

  if (nextQty <= 0) {
    state.cart = state.cart.filter((entry) => String(entry.id) !== String(productId));
  } else if (product && nextQty > Number(product.stock || 0)) {
    alert('Khong the vuot qua ton kho.');
    return;
  } else {
    item.qty = nextQty;
  }

  setCart(state.cart);
  renderCart();
}

function removeCartItem(productId) {
  state.cart = state.cart.filter((item) => String(item.id) !== String(productId));
  setCart(state.cart);
  renderCart();
}

function calcCartTotal() {
  return state.cart.reduce((sum, item) => sum + Number(item.price) * Number(item.qty), 0);
}

function renderCart() {
  if (!state.cart.length) {
    nodes.cartItems.innerHTML = '<div class="muted">Gio hang dang trong.</div>';
    nodes.cartQuoteInfo.textContent = '';
  } else {
    nodes.cartItems.innerHTML = state.cart
      .map((item) => `
        <div class="row-item">
          <div>
            <strong>${item.name}</strong>
            <div class="muted">${toVnd(item.price)} x ${item.qty}</div>
          </div>
          <div class="row-actions">
            <button class="qty-btn" data-qty="minus" data-id="${item.id}">-</button>
            <button class="qty-btn" data-qty="plus" data-id="${item.id}">+</button>
            <button class="remove-btn" data-remove="${item.id}">Xoa</button>
          </div>
        </div>
      `)
      .join('');

    nodes.cartItems.querySelectorAll('[data-qty]').forEach((button) => {
      const id = button.dataset.id;
      const delta = button.dataset.qty === 'plus' ? 1 : -1;
      button.addEventListener('click', () => changeQuantity(id, delta));
    });

    nodes.cartItems.querySelectorAll('[data-remove]').forEach((button) => {
      button.addEventListener('click', () => removeCartItem(button.dataset.remove));
    });
  }

  nodes.cartCount.textContent = String(state.cart.reduce((sum, item) => sum + Number(item.qty), 0));
  nodes.cartTotal.textContent = toVnd(calcCartTotal());
  updateCartQuote().catch((error) => {
    nodes.cartQuoteInfo.textContent = error.message;
  });
}

async function updateCartQuote() {
  const signature = cartSignature();
  state.quoteSignature = signature;

  if (!state.cart.length) {
    nodes.cartQuoteInfo.textContent = 'Backend se ghi nhan gio hang va ton kho khi ban tien hanh dat don.';
    return;
  }

  const payload = state.cart.map((item) => ({ productId: item.id, qty: item.qty }));
  const result = await api('/api/cart/quote', {
    method: 'POST',
    body: JSON.stringify({ items: payload })
  });

  if (signature !== state.quoteSignature) return;

  state.quote = result;
  const remainingText = result.items
    .map((item) => `${item.name}: con ${item.stockAfter}`)
    .join(' | ');

  nodes.cartQuoteInfo.textContent = `Tong da chon: ${result.totalItems} san pham | ${remainingText}`;
}

function populateCheckoutFromAuth() {
  if (!state.auth?.user) return;

  if (!nodes.customerName.value) {
    nodes.customerName.value = state.auth.user.name || '';
  }
  updateAuthButtonLabel();
}

function renderAuthStatus(message = '') {
  if (!nodes.authStatus) return;

  if (state.auth?.user) {
    nodes.authStatus.textContent = message || `Da dang nhap: ${state.auth.user.name} (${state.auth.user.role})`;
  } else {
    nodes.authStatus.textContent = message || 'Ban chua dang nhap.';
  }
}

async function syncAuthState() {
  if (!state.auth?.token) {
    setAuth(null);
    updateAuthButtonLabel();
    renderAuthStatus('Ban chua dang nhap.');
    return;
  }

  try {
    const result = await api('/api/auth/me');
    state.auth = { ...state.auth, user: result.user };
    setAuth(state.auth);
    renderAuthStatus();
    populateCheckoutFromAuth();
  } catch {
    setAuth(null);
    renderAuthStatus('Ban chua dang nhap.');
  }
}

async function loadProducts() {
  const result = await fetch('/api/products').then(async (response) => {
    const data = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(data.message || 'Khong the tai san pham');
    return data;
  });

  state.products = result.products || [];
  renderProducts();
  renderSpotlightProducts();
}

async function placeOrder(event) {
  event.preventDefault();

  if (!state.auth?.token) {
    alert('Ban can dang nhap truoc khi dat hang.');
    openAuthModal('login', 'Vui long dang nhap truoc khi dat hang.');
    return;
  }

  if (!state.cart.length) {
    alert('Gio hang dang trong.');
    return;
  }

  const customer = {
    name: nodes.customerName.value.trim(),
    phone: nodes.customerPhone.value.trim(),
    area: nodes.customerArea.value.trim(),
    address: nodes.customerAddress.value.trim()
  };

  if (!customer.name || !customer.phone || !customer.address) {
    alert('Vui long nhap day du thong tin bat buoc.');
    return;
  }

  const items = state.cart.map((item) => ({ productId: item.id, qty: item.qty }));

  const result = await api('/api/orders', {
    method: 'POST',
    body: JSON.stringify({
      items,
      customer,
      paymentMethod: nodes.paymentMethod.value
    })
  });

  state.cart = [];
  setCart([]);
  renderCart();
  await loadProducts();
  nodes.orderForm.reset();
  populateCheckoutFromAuth();
  alert(`Dat hang thanh cong. Ma don: ${result.order.id}`);
}

async function requestOtp(event) {
  event.preventDefault();

  const name = nodes.registerName.value.trim();
  const phone = nodes.registerPhone.value.trim();
  const email = nodes.registerEmail.value.trim();
  const password = nodes.registerPassword.value.trim();

  const result = await fetch('/api/auth/register/request-otp', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name, phone, email, password })
  }).then(async (response) => {
    const data = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(data.message || 'Khong the gui OTP');
    return data;
  });

  nodes.otpBlock.classList.remove('hidden');
  renderAuthStatus(result.developmentOtp ? `OTP dev: ${result.developmentOtp}` : 'OTP da duoc gui den Gmail cua ban.');
}

async function verifyRegister(event) {
  event.preventDefault();

  const email = nodes.registerEmail.value.trim();
  const otp = nodes.registerOtp.value.trim();

  const result = await fetch('/api/auth/register/verify', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, otp })
  }).then(async (response) => {
    const data = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(data.message || 'Xac thuc that bai');
    return data;
  });

  setAuth({ token: result.token, user: result.user });
  renderAuthStatus(`Dang ky thanh cong: ${result.user.name}`);
  populateCheckoutFromAuth();
  nodes.registerForm.reset();
  nodes.otpBlock.classList.add('hidden');
}

async function login(event) {
  event.preventDefault();

  const identifier = nodes.loginIdentifier.value.trim();
  const password = nodes.loginPassword.value.trim();

  const result = await fetch('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ identifier, password })
  }).then(async (response) => {
    const data = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(data.message || 'Dang nhap that bai');
    return data;
  });

  setAuth({ token: result.token, user: result.user });
  populateCheckoutFromAuth();
  updateAuthButtonLabel();

  if (result.user.role === 'admin') {
    window.location.href = 'admin.html';
    return;
  }

  renderAuthStatus(`Dang nhap thanh cong: ${result.user.name}`);
  closeAuthModal();
}

function sendChat(event) {
  event.preventDefault();
  const question = nodes.chatInput.value.trim();
  if (!question) return;

  addMessage(question, 'user');
  nodes.chatInput.value = '';
  addBotMessage(answerByIntent(question));
}

function addMessage(text, role) {
  const el = document.createElement('div');
  el.className = `msg ${role}`;
  el.textContent = text;
  nodes.chatLog.appendChild(el);
  nodes.chatLog.scrollTop = nodes.chatLog.scrollHeight;
}

function addBotMessage(text) {
  setTimeout(() => addMessage(text, 'bot'), 250);
}

function answerByIntent(text) {
  const query = text.toLowerCase();
  if (query.includes('lua')) return 'Voi lua, ban nen uu tien NPK can doi va bo sung kali o giai doan lam dong.';
  if (query.includes('cafe') || query.includes('ca phe')) return 'Ca phe thuong can trung vi luong va huu co de phuc hoi bo re.';
  if (query.includes('rau')) return 'Rau mau nen chia nho lieu bon va ket hop huu co de cay hap thu deu.';
  if (query.includes('combo')) return 'Combo de xuat: cai tao dat + tang sinh truong + vi luong theo giai doan.';
  if (query.includes('nang suat')) return 'De toi uu nang suat, can dung dung san pham, dung lieu va dung thoi diem.';
  return 'Ban cho minh them thong tin cay trong, dien tich va tinh trang hien tai de tu van sat hon.';
}

function toggleChat(forceOpen = null) {
  const next = typeof forceOpen === 'boolean' ? forceOpen : nodes.chatBox.classList.contains('hidden');
  nodes.chatBox.classList.toggle('hidden', !next);
}

function bindEvents() {
  nodes.searchInput.addEventListener('input', renderProducts);
  nodes.cropFilter.addEventListener('change', renderProducts);
  nodes.sortFilter.addEventListener('change', renderProducts);
  nodes.orderForm.addEventListener('submit', (event) => {
    placeOrder(event).catch((error) => alert(error.message));
  });
  nodes.loginForm.addEventListener('submit', (event) => {
    login(event).catch((error) => alert(error.message));
  });
  nodes.registerForm.addEventListener('submit', (event) => {
    verifyRegister(event).catch((error) => alert(error.message));
  });
  nodes.requestOtpBtn.addEventListener('click', (event) => {
    requestOtp(event).catch((error) => alert(error.message));
  });
  nodes.checkoutBtn.addEventListener('click', () => {
    nodes.orderForm.scrollIntoView({ behavior: 'smooth', block: 'start' });
  });
  if (nodes.openCart) {
    nodes.openCart.addEventListener('click', () => {
      scrollToSection('.flow-layout');
    });
  }
  nodes.authBtn.addEventListener('click', () => openAuthModal('login'));
  nodes.scrollButtons.forEach((button) => {
    button.addEventListener('click', () => {
      const target = button.dataset.scroll;
      if (target) scrollToSection(target);
    });
  });
  nodes.quickCropButtons.forEach((button) => {
    button.addEventListener('click', () => {
      applyQuickCropFilter(button.dataset.quickCrop || 'all');
    });
  });
  nodes.authTabButtons.forEach((button) => {
    button.addEventListener('click', () => switchAuthTab(button.dataset.authTab));
  });
  nodes.authCloseButtons.forEach((button) => {
    button.addEventListener('click', closeAuthModal);
  });
  if (nodes.authModal) {
    nodes.authModal.addEventListener('click', (event) => {
      if (event.target?.dataset?.authClose !== undefined) {
        closeAuthModal();
      }
    });
  }
  nodes.chatToggle.addEventListener('click', () => toggleChat());
  nodes.openChatFromHero.addEventListener('click', () => toggleChat(true));
  nodes.chatForm.addEventListener('submit', sendChat);

  nodes.chips.forEach((chip) => {
    chip.addEventListener('click', () => {
      const question = chip.dataset.question;
      toggleChat(true);
      addMessage(question, 'user');
      addBotMessage(answerByIntent(question));
    });
  });
}

async function init() {
  addMessage('Xin chao, toi co the goi y phan bon theo cay trong va muc tieu nang suat.', 'bot');
  bindSpotlightControls();
  bindEvents();
  await loadProducts();
  renderCart();
  await syncAuthState();
  populateCheckoutFromAuth();
  updateAuthButtonLabel();
}

init().catch((error) => {
  console.error(error);
  renderAuthStatus(error.message);
});
