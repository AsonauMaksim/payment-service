package com.internship.payment_service.integration;

import com.internship.payment_service.entity.Payment;
import com.internship.payment_service.entity.PaymentStatus;
import com.internship.payment_service.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentRepositoryIT extends BaseIntegrationTest {

    @Autowired
    private PaymentRepository repo;

    @BeforeEach
    void clean() {
        repo.deleteAll();
    }

    @Test
    void sumSuccessfulBetween_ShouldSumOnlySuccessAndOnlyInRange() {
        Instant now = Instant.now();

        repo.save(Payment.builder()
                .orderId(1L).userId(1L).status(PaymentStatus.SUCCESS)
                .paymentAmount(new BigDecimal("10.00"))
                .timestamp(now.minusSeconds(100))
                .build());

        repo.save(Payment.builder()
                .orderId(1L).userId(1L).status(PaymentStatus.FAILED)
                .paymentAmount(new BigDecimal("20.00"))
                .timestamp(now.minusSeconds(90))
                .build());

        repo.save(Payment.builder()
                .orderId(2L).userId(1L).status(PaymentStatus.SUCCESS)
                .paymentAmount(new BigDecimal("30.00"))
                .timestamp(now.minusSeconds(10_000))
                .build());

        var rows = repo.sumSuccessfulBetween(now.minusSeconds(200), now);
        BigDecimal total = rows.isEmpty() ? BigDecimal.ZERO : rows.get(0).getTotal();

        assertThat(total).isEqualByComparingTo(new BigDecimal("10.00"));
    }
}

