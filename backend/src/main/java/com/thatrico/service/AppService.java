package com.thatrico.service;

import com.thatrico.service.DataStore.Db;
import com.thatrico.service.DataStore.Order;
import com.thatrico.service.DataStore.OrderItem;
import com.thatrico.service.DataStore.PendingRegistration;
import com.thatrico.service.DataStore.Product;
import com.thatrico.service.DataStore.User;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

@Service
public class AppService {
    private static final Pattern GMAIL_PATTERN = Pattern.compile(".+@(gmail\\.com|googlemail\\.com)$", Pattern.CASE_INSENSITIVE);
    private static final long OTP_TTL_MILLIS = 10L * 60L * 1000L;

    private final DataStore store;
    private final TokenService tokenService;
    private final MailService mailService;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AppService(DataStore store, TokenService tokenService, MailService mailService) {
        this.store = store;
        this.tokenService = tokenService;
        this.mailService = mailService;
    }

    public Map<String, Object> listProducts() {
        Db db = store.load();
        return mapOf("products", db.products);
    }

    public Map<String, Object> quote(List<Map<String, Object>> items) {
        Db db = store.load();
        QuoteResult quote = buildQuote(db, items);
        return mapOf(
                "items", quote.items,
                "totalItems", quote.totalItems,
                "totalAmount", quote.totalAmount
        );
    }

    public Map<String, Object> requestOtp(Map<String, Object> body) {
        String name = normalizedString(body.get("name"));
        String phone = normalizedPhone(body.get("phone"));
        String email = normalizedEmail(body.get("email"));
        String password = normalizedString(body.get("password"));

        if (name.isBlank() || phone.isBlank() || email.isBlank() || password.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing registration fields");
        }
        if (!GMAIL_PATTERN.matcher(email).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui long dang ky bang Gmail");
        }
        if (password.length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mat khau phai co it nhat 6 ky tu");
        }

        Db db = store.load();
        if (findUserByEmail(db, email) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email da duoc su dung");
        }
        if (findUserByPhone(db, phone) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "So dien thoai da duoc su dung");
        }

        String otp = generateOtp();
        PendingRegistration pending = new PendingRegistration();
        pending.id = UUID.randomUUID().toString();
        pending.name = name;
        pending.phone = phone;
        pending.email = email;
        pending.passwordHash = encoder.encode(password);
        pending.otpHash = encoder.encode(otp);
        pending.expiresAt = Instant.now().toEpochMilli() + OTP_TTL_MILLIS;
        pending.createdAt = Instant.now().toString();

        db.pendingRegistrations.removeIf(entry -> entry.email != null && entry.email.equalsIgnoreCase(email));
        db.pendingRegistrations.add(pending);
        store.save(db);

        String devOtp = mailService.sendOtp(email, name, otp);
        return mapOf(
                "message", "OTP da duoc gui den email cua ban",
                "developmentOtp", devOtp
        );
    }

    public Map<String, Object> verifyOtp(Map<String, Object> body) {
        String email = normalizedEmail(body.get("email"));
        String otp = normalizedString(body.get("otp"));
        if (email.isBlank() || otp.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing verification fields");
        }

        Db db = store.load();
        PendingRegistration pending = db.pendingRegistrations.stream()
                .filter(entry -> entry.email != null && entry.email.equalsIgnoreCase(email))
                .findFirst()
                .orElse(null);

        if (pending == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Khong tim thay yeu cau OTP");
        }
        if (pending.expiresAt < Instant.now().toEpochMilli()) {
            db.pendingRegistrations.remove(pending);
            store.save(db);
            throw new ResponseStatusException(HttpStatus.GONE, "OTP da het han");
        }
        if (!encoder.matches(otp, pending.otpHash)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP khong dung");
        }

        User user = new User();
        user.id = UUID.randomUUID().toString();
        user.name = pending.name;
        user.phone = pending.phone;
        user.email = pending.email;
        user.passwordHash = pending.passwordHash;
        user.role = "user";
        user.verified = true;
        user.createdAt = Instant.now().toString();

        db.users.add(user);
        db.pendingRegistrations.remove(pending);
        store.save(db);

        return authResponse(user, "Dang ky thanh cong");
    }

    public Map<String, Object> login(Map<String, Object> body) {
        String identifier = normalizedString(body.get("identifier"));
        String password = normalizedString(body.get("password"));

        if (identifier.isBlank() || password.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing login fields");
        }

        Db db = store.load();

        if (identifier.equalsIgnoreCase("admin") && password.equals("123")) {
            User admin = ensureAdmin(db);
            store.save(db);
            return authResponse(admin, "Dang nhap thanh cong");
        }

        User user = findUserByIdentifier(db, identifier);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sai so dien thoai/email hoac mat khau");
        }
        if (!user.verified) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tai khoan chua duoc xac thuc");
        }
        if (!encoder.matches(password, user.passwordHash)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sai so dien thoai/email hoac mat khau");
        }

        return authResponse(user, "Dang nhap thanh cong");
    }

    public Map<String, Object> me(String token) {
        User user = requireUser(token);
        return mapOf("user", sanitizeUser(user));
    }

    public Map<String, Object> createOrder(String token, Map<String, Object> body) {
        User user = requireUser(token);
        Db db = store.load();

        List<Map<String, Object>> items = castList(body.get("items"));
        CustomerPayload customer = toCustomer(body.get("customer"));
        String paymentMethod = normalizedString(body.getOrDefault("paymentMethod", "COD"));

        if (customer.name.isBlank() || customer.phone.isBlank() || customer.address.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing customer information");
        }

        QuoteResult quote = buildQuote(db, items);
        for (OrderItem item : quote.items) {
            Product product = findProduct(db, item.id);
            product.stock -= item.qty;
        }

        Order order = new Order();
        order.id = UUID.randomUUID().toString();
        order.userId = user.id;
        order.userName = user.name;
        order.userEmail = user.email;
        order.customerName = customer.name;
        order.customerPhone = customer.phone;
        order.customerArea = customer.area;
        order.customerAddress = customer.address;
        order.paymentMethod = paymentMethod;
        order.status = "Moi tao";
        order.items = new ArrayList<>(quote.items);
        order.totalItems = quote.totalItems;
        order.totalAmount = quote.totalAmount;
        order.createdAt = Instant.now().toString();

        db.orders.add(0, order);
        store.save(db);

        return mapOf(
                "message", "Tao don hang thanh cong",
                "order", order,
                "remainingProducts", db.products
        );
    }

    public Map<String, Object> myOrders(String token) {
        User user = requireUser(token);
        Db db = store.load();
        List<Order> orders = db.orders.stream().filter(order -> Objects.equals(order.userId, user.id)).toList();
        return mapOf("orders", orders);
    }

    public Map<String, Object> adminDashboard(String token) {
        requireAdmin(token);
        Db db = store.load();
        int revenue = db.orders.stream()
                .filter(order -> "Hoan tat".equals(order.status))
                .mapToInt(order -> order.totalAmount)
                .sum();
        return mapOf(
                "metrics", mapOf(
                        "orders", db.orders.size(),
                        "products", db.products.size(),
                        "customers", (int) db.users.stream().filter(user -> "user".equals(user.role)).count(),
                        "revenue", revenue,
                        "stockTotal", db.products.stream().mapToInt(product -> product.stock).sum()
                ),
                "recentOrders", db.orders.stream().limit(10).toList()
        );
    }

    public Map<String, Object> adminOrders(String token) {
        requireAdmin(token);
        Db db = store.load();
        return mapOf("orders", db.orders);
    }

    public Map<String, Object> updateOrder(String token, String orderId, Map<String, Object> body) {
        requireAdmin(token);
        Db db = store.load();
        Order order = db.orders.stream().filter(entry -> Objects.equals(entry.id, orderId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        String status = normalizedString(body.get("status"));
        if (!status.isBlank()) {
            order.status = status;
            store.save(db);
        }
        return mapOf("message", "Order updated", "order", order);
    }

    public Map<String, Object> adminProducts(String token) {
        requireAdmin(token);
        Db db = store.load();
        return mapOf("products", db.products);
    }

    public Map<String, Object> createProduct(String token, Map<String, Object> body) {
        requireAdmin(token);
        Db db = store.load();
        Product product = new Product();
        product.id = UUID.randomUUID().toString();
        product.name = normalizedString(body.get("name"));
        product.price = toInt(body.get("price"));
        product.crop = normalizedString(body.get("crop"));
        product.benefit = normalizedString(body.get("benefit"));
        product.icon = normalizedString(body.getOrDefault("icon", "🌿"));
        product.stock = Math.max(0, toInt(body.get("stock")));

        if (product.name.isBlank() || product.crop.isBlank() || product.benefit.isBlank() || product.price <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing product fields");
        }

        db.products.add(0, product);
        store.save(db);
        return mapOf("message", "Product created", "product", product);
    }

    public Map<String, Object> updateProduct(String token, String productId, Map<String, Object> body) {
        requireAdmin(token);
        Db db = store.load();
        Product product = findProduct(db, productId);
        if (body.containsKey("name")) product.name = normalizedString(body.get("name"));
        if (body.containsKey("price")) product.price = toInt(body.get("price"));
        if (body.containsKey("crop")) product.crop = normalizedString(body.get("crop"));
        if (body.containsKey("benefit")) product.benefit = normalizedString(body.get("benefit"));
        if (body.containsKey("icon")) product.icon = normalizedString(body.get("icon"));
        if (body.containsKey("stock")) product.stock = Math.max(0, toInt(body.get("stock")));
        store.save(db);
        return mapOf("message", "Product updated", "product", product);
    }

    public Map<String, Object> deleteProduct(String token, String productId) {
        requireAdmin(token);
        Db db = store.load();
        boolean removed = db.products.removeIf(product -> Objects.equals(product.id, productId));
        if (!removed) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }
        store.save(db);
        return mapOf("message", "Product deleted");
    }

    public Map<String, Object> adminUsers(String token) {
        requireAdmin(token);
        Db db = store.load();
        return mapOf("users", db.users.stream().map(this::sanitizeUser).toList());
    }

    public Map<String, Object> health() {
        return mapOf("ok", true, "service", "thatrico-api-java");
    }

    private Map<String, Object> authResponse(User user, String message) {
        return mapOf(
                "message", message,
                "token", tokenService.createToken(user),
                "user", sanitizeUser(user)
        );
    }

    private Map<String, Object> sanitizeUser(User user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", user.id);
        map.put("name", user.name);
        map.put("email", user.email);
        map.put("phone", user.phone);
        map.put("role", user.role);
        map.put("verified", user.verified);
        map.put("createdAt", user.createdAt);
        return map;
    }

    private User requireUser(String token) {
        Map<String, Object> claims = tokenService.verifyToken(extractBearerToken(token));
        String userId = normalizedString(claims.get("sub"));
        Db db = store.load();
        User user = db.users.stream().filter(entry -> Objects.equals(entry.id, userId)).findFirst().orElse(null);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        return user;
    }

    private User requireAdmin(String token) {
        User user = requireUser(token);
        if (!"admin".equals(user.role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin only");
        }
        return user;
    }

    private User ensureAdmin(Db db) {
        User admin = db.users.stream().filter(user -> "admin".equals(user.role)).findFirst().orElse(null);
        if (admin != null) {
            return admin;
        }
        admin = new User();
        admin.id = UUID.randomUUID().toString();
        admin.name = "admin";
        admin.email = "admin@thatrico.vn";
        admin.phone = "admin";
        admin.passwordHash = encoder.encode("123");
        admin.role = "admin";
        admin.verified = true;
        admin.createdAt = Instant.now().toString();
        db.users.add(admin);
        return admin;
    }

    private User findUserByIdentifier(Db db, String identifier) {
        String key = normalizedString(identifier).toLowerCase(Locale.ROOT);
        return db.users.stream()
                .filter(user -> matchesIdentifier(user, key))
                .findFirst()
                .orElse(null);
    }

    private User findUserByEmail(Db db, String email) {
        String key = normalizedEmail(email);
        return db.users.stream().filter(user -> user.email != null && user.email.equalsIgnoreCase(key)).findFirst().orElse(null);
    }

    private User findUserByPhone(Db db, String phone) {
        String key = normalizedPhone(phone);
        return db.users.stream().filter(user -> normalizedPhone(user.phone).equals(key)).findFirst().orElse(null);
    }

    private boolean matchesIdentifier(User user, String identifier) {
        return normalizedEmail(user.email).equals(identifier)
                || normalizedPhone(user.phone).equals(identifier)
                || normalizedString(user.name).equals(identifier);
    }

    private Product findProduct(Db db, String productId) {
        return db.products.stream().filter(product -> Objects.equals(product.id, productId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found: " + productId));
    }

    private QuoteResult buildQuote(Db db, List<Map<String, Object>> rawItems) {
        if (rawItems == null || rawItems.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cart is empty");
        }

        Map<String, Integer> aggregated = new LinkedHashMap<>();
        for (Map<String, Object> rawItem : rawItems) {
            String productId = normalizedString(rawItem.getOrDefault("productId", rawItem.get("id")));
            int qty = Math.max(0, toInt(rawItem.get("qty")));
            if (productId.isBlank() || qty <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid cart item");
            }
            aggregated.put(productId, aggregated.getOrDefault(productId, 0) + qty);
        }

        List<OrderItem> items = new ArrayList<>();
        int totalAmount = 0;
        int totalItems = 0;

        for (Map.Entry<String, Integer> entry : aggregated.entrySet()) {
            Product product = findProduct(db, entry.getKey());
            int qty = entry.getValue();
            if (qty > product.stock) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not enough stock for " + product.name);
            }

            OrderItem item = new OrderItem();
            item.id = product.id;
            item.name = product.name;
            item.price = product.price;
            item.qty = qty;
            item.lineTotal = product.price * qty;
            item.stockBefore = product.stock;
            item.stockAfter = product.stock - qty;
            item.crop = product.crop;
            item.icon = product.icon;
            item.benefit = product.benefit;
            items.add(item);
            totalAmount += item.lineTotal;
            totalItems += item.qty;
        }

        QuoteResult result = new QuoteResult();
        result.items = items;
        result.totalAmount = totalAmount;
        result.totalItems = totalItems;
        return result;
    }

    private CustomerPayload toCustomer(Object rawCustomer) {
        if (!(rawCustomer instanceof Map<?, ?>)) {
            return new CustomerPayload("", "", "", "");
        }
        Map<?, ?> map = (Map<?, ?>) rawCustomer;
        return new CustomerPayload(
                normalizedString(map.get("name")),
                normalizedString(map.get("phone")),
                normalizedString(map.get("area")),
                normalizedString(map.get("address"))
        );
    }

    private String extractBearerToken(String token) {
        if (token == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing authorization token");
        }
        String value = token.trim();
        if (value.toLowerCase(Locale.ROOT).startsWith("bearer ")) {
            return value.substring(7).trim();
        }
        return value;
    }

    private Map<String, Object> mapOf(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return map;
    }

    private String generateOtp() {
        return String.valueOf(ThreadLocalRandom.current().nextInt(100000, 1000000));
    }

    private String normalizedString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String normalizedEmail(Object value) {
        return normalizedString(value).toLowerCase(Locale.ROOT);
    }

    private String normalizedPhone(Object value) {
        return normalizedString(value).replaceAll("[^0-9+]", "");
    }

    private int toInt(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number) {
            Number number = (Number) value;
            return number.intValue();
        }
        try {
            return Integer.parseInt(normalizedString(value));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private List<Map<String, Object>> castList(Object value) {
        if (value instanceof List<?>) {
            List<?> list = (List<?>) value;
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?>) {
                    Map<?, ?> map = (Map<?, ?>) item;
                    Map<String, Object> copy = new HashMap<>();
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        copy.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                    result.add(copy);
                }
            }
            return result;
        }
        return List.of();
    }

    private static class QuoteResult {
        public List<OrderItem> items;
        public int totalAmount;
        public int totalItems;
    }

    private static class CustomerPayload {
        public final String name;
        public final String phone;
        public final String area;
        public final String address;

        private CustomerPayload(String name, String phone, String area, String address) {
            this.name = name;
            this.phone = phone;
            this.area = area;
            this.address = address;
        }
    }
}
