package com.thatrico.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class DataStore {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private final Path dbPath = Paths.get("data", "db.json");

    public synchronized Db load() {
        ensureSeed();
        try {
            return mapper.readValue(dbPath.toFile(), Db.class);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read database file", exception);
        }
    }

    public synchronized void save(Db db) {
        ensureDirectory();
        try {
            mapper.writeValue(dbPath.toFile(), db);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save database file", exception);
        }
    }

    private void ensureSeed() {
        ensureDirectory();
        if (Files.exists(dbPath)) {
            return;
        }
        save(createInitialDb());
    }

    private void ensureDirectory() {
        try {
            Files.createDirectories(dbPath.getParent());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create data directory", exception);
        }
    }

    private Db createInitialDb() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        Db db = new Db();

        User admin = new User();
        admin.id = "1";
        admin.name = "admin";
        admin.email = "admin@thatrico.vn";
        admin.phone = "admin";
        admin.passwordHash = encoder.encode("123");
        admin.role = "admin";
        admin.verified = true;
        admin.createdAt = Instant.now().toString();
        db.users.add(admin);

        db.products.add(product("1", "NPK 20-20-15 Chuyen lua", 320000, "lua", "Tang de nhanh va phat trien than la", "🌾", 120));
        db.products.add(product("2", "Huu co vi sinh BioGrow", 275000, "rau", "Cai tao dat, tang do mui", "🥬", 90));
        db.products.add(product("3", "Kali K60 nang chat", 355000, "cay-an-trai", "Tang do ngot, dep mau trai", "🍊", 80));
        db.products.add(product("4", "Trung vi luong Cafe Plus", 410000, "cafe", "Giam vang la, phuc hoi re", "☕", 75));
        db.products.add(product("5", "NPK Giai doan dong", 295000, "lua", "Tang chac hat, giam lem lep", "🌱", 110));
        db.products.add(product("6", "Canxi-Bo Chong rung trai", 260000, "cay-an-trai", "Han che rung hoa va trai non", "🍋", 95));

        return db;
    }

    private Product product(String id, String name, int price, String crop, String benefit, String icon, int stock) {
        Product product = new Product();
        product.id = id;
        product.name = name;
        product.price = price;
        product.crop = crop;
        product.benefit = benefit;
        product.icon = icon;
        product.stock = stock;
        return product;
    }

    public static class Db {
        public List<User> users = new ArrayList<>();
        public List<PendingRegistration> pendingRegistrations = new ArrayList<>();
        public List<Product> products = new ArrayList<>();
        public List<Order> orders = new ArrayList<>();
    }

    public static class User {
        public String id;
        public String name;
        public String email;
        public String phone;
        public String passwordHash;
        public String role;
        public boolean verified;
        public String createdAt;
    }

    public static class PendingRegistration {
        public String id;
        public String name;
        public String email;
        public String phone;
        public String passwordHash;
        public String otpHash;
        public long expiresAt;
        public String createdAt;
    }

    public static class Product {
        public String id;
        public String name;
        public int price;
        public String crop;
        public String benefit;
        public String icon;
        public int stock;
    }

    public static class OrderItem {
        public String id;
        public String name;
        public int price;
        public int qty;
        public int lineTotal;
        public int stockBefore;
        public int stockAfter;
        public String crop;
        public String icon;
        public String benefit;
    }

    public static class Order {
        public String id;
        public String userId;
        public String userName;
        public String userEmail;
        public String customerName;
        public String customerPhone;
        public String customerArea;
        public String customerAddress;
        public String paymentMethod;
        public String status;
        public int totalItems;
        public int totalAmount;
        public List<OrderItem> items = new ArrayList<>();
        public String createdAt;
    }
}
