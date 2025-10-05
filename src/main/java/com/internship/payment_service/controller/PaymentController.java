package com.internship.payment_service.controller;

import com.internship.payment_service.dto.PaymentRequest;
import com.internship.payment_service.dto.PaymentResponse;
import com.internship.payment_service.dto.PaymentTotalSumResponse;
import com.internship.payment_service.entity.PaymentStatus;
import com.internship.payment_service.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<PaymentResponse> create(@Valid @RequestBody PaymentRequest request) {
        PaymentResponse created = paymentService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<List<PaymentResponse>> byOrder(@PathVariable Long orderId) {
        return ResponseEntity.ok(paymentService.getByOrderId(orderId));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<PaymentResponse>> byUser(@PathVariable Long userId) {
        return ResponseEntity.ok(paymentService.getByUserId(userId));
    }

    @GetMapping("/statuses")
    public ResponseEntity<List<PaymentResponse>> byStatuses(@RequestParam Set<PaymentStatus> statuses) {
        return ResponseEntity.ok(paymentService.getByStatuses(statuses));
    }

    @GetMapping("/total_sum")
    public ResponseEntity<PaymentTotalSumResponse> total(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        return ResponseEntity.ok(paymentService.getTotalBetween(from, to));
    }
}
