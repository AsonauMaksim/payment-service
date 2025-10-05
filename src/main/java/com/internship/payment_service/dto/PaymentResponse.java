package com.internship.payment_service.dto;

import com.internship.payment_service.entity.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponse {

    private String id;
    private Long orderId;
    private Long userId;
    private PaymentStatus status;
    private Instant timestamp;
    private BigDecimal paymentAmount;
    private String eventId;
}
