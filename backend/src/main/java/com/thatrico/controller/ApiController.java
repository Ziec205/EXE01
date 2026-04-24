package com.thatrico.controller;

import com.thatrico.service.AppService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ApiController {
    private final AppService service;

    public ApiController(AppService service) {
        this.service = service;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(service.health());
    }

    @GetMapping("/products")
    public ResponseEntity<Map<String, Object>> products() {
        return ResponseEntity.ok(service.listProducts());
    }

    @PostMapping("/cart/quote")
    public ResponseEntity<Map<String, Object>> quote(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.getOrDefault("items", List.of());
        return ResponseEntity.ok(service.quote(items));
    }

    @PostMapping("/auth/register/request-otp")
    public ResponseEntity<Map<String, Object>> requestOtp(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(service.requestOtp(body));
    }

    @PostMapping("/auth/register/verify")
    public ResponseEntity<Map<String, Object>> verifyOtp(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(service.verifyOtp(body));
    }

    @PostMapping("/auth/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(service.login(body));
    }

    @GetMapping("/auth/me")
    public ResponseEntity<Map<String, Object>> me(@RequestHeader("Authorization") String authorization) {
        return ResponseEntity.ok(service.me(authorization));
    }

    @PostMapping("/orders")
    public ResponseEntity<Map<String, Object>> createOrder(
            @RequestHeader("Authorization") String authorization,
            @RequestBody Map<String, Object> body
    ) {
        return ResponseEntity.ok(service.createOrder(authorization, body));
    }

    @GetMapping("/orders/me")
    public ResponseEntity<Map<String, Object>> myOrders(@RequestHeader("Authorization") String authorization) {
        return ResponseEntity.ok(service.myOrders(authorization));
    }

    @GetMapping("/admin/dashboard")
    public ResponseEntity<Map<String, Object>> dashboard(@RequestHeader("Authorization") String authorization) {
        return ResponseEntity.ok(service.adminDashboard(authorization));
    }

    @GetMapping("/admin/orders")
    public ResponseEntity<Map<String, Object>> adminOrders(@RequestHeader("Authorization") String authorization) {
        return ResponseEntity.ok(service.adminOrders(authorization));
    }

    @PatchMapping("/admin/orders/{id}")
    public ResponseEntity<Map<String, Object>> updateOrder(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String id,
            @RequestBody Map<String, Object> body
    ) {
        return ResponseEntity.ok(service.updateOrder(authorization, id, body));
    }

    @GetMapping("/admin/products")
    public ResponseEntity<Map<String, Object>> adminProducts(@RequestHeader("Authorization") String authorization) {
        return ResponseEntity.ok(service.adminProducts(authorization));
    }

    @PostMapping("/admin/products")
    public ResponseEntity<Map<String, Object>> createProduct(
            @RequestHeader("Authorization") String authorization,
            @RequestBody Map<String, Object> body
    ) {
        return ResponseEntity.ok(service.createProduct(authorization, body));
    }

    @PatchMapping("/admin/products/{id}")
    public ResponseEntity<Map<String, Object>> updateProduct(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String id,
            @RequestBody Map<String, Object> body
    ) {
        return ResponseEntity.ok(service.updateProduct(authorization, id, body));
    }

    @DeleteMapping("/admin/products/{id}")
    public ResponseEntity<Map<String, Object>> deleteProduct(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String id
    ) {
        return ResponseEntity.ok(service.deleteProduct(authorization, id));
    }

    @GetMapping("/admin/users")
    public ResponseEntity<Map<String, Object>> adminUsers(@RequestHeader("Authorization") String authorization) {
        return ResponseEntity.ok(service.adminUsers(authorization));
    }
}
