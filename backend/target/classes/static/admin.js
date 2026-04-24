const ADMIN_TOKEN_KEY = 'thatrico_auth';

const adminNodes = {
  adminGate: document.getElementById('adminGate'),
  adminApp: document.getElementById('adminApp'),
  logoutBtn: document.getElementById('logoutBtn'),
  goHomeBtn: document.getElementById('goHomeBtn'),
  goHomeBtnInline: document.getElementById('goHomeBtnInline'),
  refreshAdminBtn: document.getElementById('refreshAdminBtn'),
  metricRevenue: document.getElementById('metricRevenue'),
  metricOrders: document.getElementById('metricOrders'),
  metricProducts: document.getElementById('metricProducts'),
  metricCustomers: document.getElementById('metricCustomers'),
  metricStock: document.getElementById('metricStock'),
  ordersTable: document.getElementById('ordersTable'),
  productsTable: document.getElementById('productsTable'),
  usersTable: document.getElementById('usersTable'),
  productForm: document.getElementById('productForm'),
  productName: document.getElementById('productName'),
  productPrice: document.getElementById('productPrice'),
  productCrop: document.getElementById('productCrop'),
  productStock: document.getElementById('productStock'),
  productBenefit: document.getElementById('productBenefit'),
  productIcon: document.getElementById('productIcon')
};

function readAuth() {
  const raw = localStorage.getItem(ADMIN_TOKEN_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

function authToken() {
  return readAuth()?.token || '';
}

function saveAuth(data) {
  localStorage.setItem(ADMIN_TOKEN_KEY, JSON.stringify(data));
}

function clearAuth() {
  localStorage.removeItem(ADMIN_TOKEN_KEY);
}

function vnd(value) {
  return Number(value).toLocaleString('vi-VN') + 'd';
}

async function api(path, options = {}) {
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };
  const token = authToken();
  if (token) {
    headers.Authorization = `Bearer ${token}`;
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

function hideGate(showDashboard) {
  adminNodes.adminGate.classList.toggle('hidden', showDashboard);
  adminNodes.adminApp.classList.toggle('hidden', !showDashboard);
}

async function verifyAdminSession() {
  const token = authToken();
  if (!token) {
    hideGate(false);
    return null;
  }

  try {
    const result = await api('/api/auth/me');
    if (result.user.role !== 'admin') {
      throw new Error('Not admin');
    }
    hideGate(true);
    return result.user;
  } catch {
    clearAuth();
    hideGate(false);
    return null;
  }
}

function logout() {
  clearAuth();
  hideGate(false);
}

function renderOrders(orders) {
  if (!orders.length) {
    adminNodes.ordersTable.innerHTML = '<div class="muted-box">Chua co don hang nao.</div>';
    return;
  }

  const rows = orders.map((order) => `
    <tr>
      <td>${order.id}</td>
      <td>${order.userName}<br><span class="muted">${order.userEmail}</span></td>
      <td>${order.customerName}<br><span class="muted">${order.customerPhone}</span></td>
      <td>${order.totalItems}</td>
      <td>${vnd(order.totalAmount)}</td>
      <td><span class="status-pill">${order.status}</span></td>
      <td>
        <select data-order-status="${order.id}">
          <option value="Moi tao" ${order.status === 'Moi tao' ? 'selected' : ''}>Moi tao</option>
          <option value="Da xac nhan" ${order.status === 'Da xac nhan' ? 'selected' : ''}>Da xac nhan</option>
          <option value="Dang giao" ${order.status === 'Dang giao' ? 'selected' : ''}>Dang giao</option>
          <option value="Hoan tat" ${order.status === 'Hoan tat' ? 'selected' : ''}>Hoan tat</option>
          <option value="Da huy" ${order.status === 'Da huy' ? 'selected' : ''}>Da huy</option>
        </select>
      </td>
    </tr>
  `).join('');

  adminNodes.ordersTable.innerHTML = `
    <table>
      <thead>
        <tr>
          <th>Ma don</th>
          <th>Nguoi mua</th>
          <th>Khach hang</th>
          <th>SL</th>
          <th>Tong tien</th>
          <th>Trang thai</th>
          <th>Cap nhat</th>
        </tr>
      </thead>
      <tbody>${rows}</tbody>
    </table>
  `;

  adminNodes.ordersTable.querySelectorAll('[data-order-status]').forEach((select) => {
    select.addEventListener('change', async () => {
      await api(`/api/admin/orders/${select.dataset.orderStatus}`, {
        method: 'PATCH',
        body: JSON.stringify({ status: select.value })
      });
      await loadDashboard();
    });
  });
}

function renderProducts(products) {
  if (!products.length) {
    adminNodes.productsTable.innerHTML = '<div class="muted-box">Chua co san pham nao.</div>';
    return;
  }

  const rows = products.map((product) => `
    <tr>
      <td>${product.name}</td>
      <td>${product.crop}</td>
      <td>${vnd(product.price)}</td>
      <td>${product.stock}</td>
      <td><input data-product-name="${product.id}" value="${product.name}" /></td>
      <td><input data-product-price="${product.id}" type="number" value="${product.price}" /></td>
      <td><input data-product-stock="${product.id}" type="number" value="${product.stock}" /></td>
      <td>
        <div class="inline-actions">
          <button class="btn btn-ghost" data-update-product="${product.id}">Cap nhat</button>
          <button class="btn btn-secondary" data-delete-product="${product.id}">Xoa</button>
        </div>
      </td>
    </tr>
  `).join('');

  adminNodes.productsTable.innerHTML = `
    <table>
      <thead>
        <tr>
          <th>Ten</th>
          <th>Nhom</th>
          <th>Gia</th>
          <th>Ton kho</th>
          <th>Chinh sua ten</th>
          <th>Chinh sua gia</th>
          <th>Chinh sua ton kho</th>
          <th>Thao tac</th>
        </tr>
      </thead>
      <tbody>${rows}</tbody>
    </table>
  `;

  adminNodes.productsTable.querySelectorAll('[data-update-product]').forEach((button) => {
    button.addEventListener('click', async () => {
      const id = button.dataset.updateProduct;
      const name = adminNodes.productsTable.querySelector(`[data-product-name="${id}"]`).value;
      const price = adminNodes.productsTable.querySelector(`[data-product-price="${id}"]`).value;
      const stock = adminNodes.productsTable.querySelector(`[data-product-stock="${id}"]`).value;
      await api(`/api/admin/products/${id}`, {
        method: 'PATCH',
        body: JSON.stringify({ name, price, stock })
      });
      await loadDashboard();
    });
  });

  adminNodes.productsTable.querySelectorAll('[data-delete-product]').forEach((button) => {
    button.addEventListener('click', async () => {
      if (!confirm('Ban co chac muon xoa san pham nay?')) return;
      await api(`/api/admin/products/${button.dataset.deleteProduct}`, { method: 'DELETE' });
      await loadDashboard();
    });
  });
}

function renderUsers(users) {
  if (!users.length) {
    adminNodes.usersTable.innerHTML = '<div class="muted-box">Chua co tai khoan nao.</div>';
    return;
  }

  adminNodes.usersTable.innerHTML = `
    <table>
      <thead>
        <tr>
          <th>Ten</th>
          <th>Email</th>
          <th>Vai tro</th>
          <th>Xac thuc</th>
        </tr>
      </thead>
      <tbody>
        ${users.map((user) => `
          <tr>
            <td>${user.name}</td>
            <td>${user.email}</td>
            <td>${user.role}</td>
            <td>${user.verified ? 'Da xac thuc' : 'Chua xac thuc'}</td>
          </tr>
        `).join('')}
      </tbody>
    </table>
  `;
}

async function loadDashboard() {
  const dashboard = await api('/api/admin/dashboard');
  const orders = await api('/api/admin/orders');
  const products = await api('/api/admin/products');
  const users = await api('/api/admin/users');

  adminNodes.metricRevenue.textContent = vnd(dashboard.metrics.revenue);
  adminNodes.metricOrders.textContent = String(dashboard.metrics.orders);
  adminNodes.metricProducts.textContent = String(dashboard.metrics.products);
  adminNodes.metricCustomers.textContent = String(dashboard.metrics.customers);
  adminNodes.metricStock.textContent = String(dashboard.metrics.stockTotal);

  renderOrders(orders.orders);
  renderProducts(products.products);
  renderUsers(users.users);
}

async function addProduct(event) {
  event.preventDefault();

  await api('/api/admin/products', {
    method: 'POST',
    body: JSON.stringify({
      name: adminNodes.productName.value.trim(),
      price: adminNodes.productPrice.value,
      crop: adminNodes.productCrop.value.trim(),
      stock: adminNodes.productStock.value,
      benefit: adminNodes.productBenefit.value.trim(),
      icon: adminNodes.productIcon.value.trim() || '🌿'
    })
  });

  adminNodes.productForm.reset();
  adminNodes.productIcon.value = '🌿';
  await loadDashboard();
}

function bindEvents() {
  adminNodes.logoutBtn.addEventListener('click', logout);
  if (adminNodes.goHomeBtn) {
    adminNodes.goHomeBtn.addEventListener('click', () => {
      window.location.href = 'index.html';
    });
  }
  if (adminNodes.goHomeBtnInline) {
    adminNodes.goHomeBtnInline.addEventListener('click', () => {
      window.location.href = 'index.html';
    });
  }
  if (adminNodes.refreshAdminBtn) {
    adminNodes.refreshAdminBtn.addEventListener('click', () => window.location.reload());
  }
  adminNodes.productForm.addEventListener('submit', (event) => {
    addProduct(event).catch((error) => alert(error.message));
  });
}

(async function init() {
  bindEvents();
  const user = await verifyAdminSession();
  if (user?.role === 'admin') {
    await loadDashboard();
  }
})();
