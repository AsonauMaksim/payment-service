package com.internship.payment_service.service;

import com.internship.payment_service.dto.PaymentRequest;
import com.internship.payment_service.dto.PaymentResponse;
import com.internship.payment_service.dto.PaymentTotalSumResponse;
import com.internship.payment_service.entity.PaymentStatus;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public interface PaymentService {

    PaymentResponse create(PaymentRequest request);

    List<PaymentResponse> getByOrderId(Long orderId);

    List<PaymentResponse> getByUserId(Long userId);

    List<PaymentResponse> getByStatuses(Set<PaymentStatus> statuses);

    PaymentTotalSumResponse getTotalBetween(Instant from, Instant to);
}
