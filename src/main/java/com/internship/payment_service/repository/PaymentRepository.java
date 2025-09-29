package com.internship.payment_service.repository;

import com.internship.payment_service.entity.Payment;
import com.internship.payment_service.entity.PaymentStatus;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends MongoRepository<Payment, String> {

    Optional<Payment> findByEventId(String eventId);

    List<Payment> findByOrderId(Long orderId);
    List<Payment> findByUserId(Long userId);
    List<Payment> findByStatusIn(Collection<PaymentStatus> statuses);

    boolean existsByOrderIdAndStatus(Long orderId, PaymentStatus status);

    @Aggregation(pipeline = {
            "{ $match: { status: 'SUCCESS', timestamp: { $gte: ?0, $lte: ?1 } } }",
            "{ $group: { _id: null, total: { $sum: '$payment_amount' } } }"
    })
    List<TotalAmountProjection> sumSuccessfulBetween(Instant from, Instant to);

    interface TotalAmountProjection {
        BigDecimal getTotal();
    }
}
